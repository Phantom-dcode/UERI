package com.example.beacon

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.FirebaseUeriRepository
import com.example.data.FirestoreEmergencyRepository
import com.example.model.Responder
import com.example.model.ueri.AuditLogDoc
import com.example.model.ueri.BeaconDetectionDoc
import com.example.model.ueri.BeaconTokenDoc
import com.example.model.ueri.EmergencyEventDoc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal Emergency Response Infrastructure (UERI) Emergency Beacon Module
 *
 * Implements the UERI Bluetooth Low Energy (BLE) emergency beacon prototype for Android:
 * 1. Offline Victim Beacon: Generates cryptographically secure rotating tokens and advertises
 *    minimal protocol metadata over BLE when normal network connectivity is lost or impaired.
 *    Does NOT transmit personal identity, victim name, phone number, or exact victim GPS.
 * 2. Responder Beacon Relay: Nearby participating UERI responder devices scan for UERI beacons,
 *    estimate proximity via signal strength (RSSI), and relay detection telemetry to the backend.
 * 3. Platform Compliance: Respects Android Bluetooth permissions, battery policies, and background execution limits.
 */
class UeriBeaconManager(
    private val context: Context,
    private val firestoreRepository: FirestoreEmergencyRepository
) {
    companion object {
        private const val TAG = "UeriBeaconManager"

        // Dedicated 16-bit UUID expanded for UERI Emergency Beacon Protocol
        val UERI_SERVICE_UUID: UUID = UUID.fromString("0000FA01-0000-1000-8000-00805F9B34FB")
        val PARCEL_SERVICE_UUID = ParcelUuid(UERI_SERVICE_UUID)

        const val PROTOCOL_VERSION: Byte = 0x01
        const val TOKEN_BYTE_LENGTH = 16
        const val TOKEN_ROTATION_INTERVAL_MS = 60_000L // 60s rotation for active beacon
        const val DEDUPLICATION_WINDOW_MS = 12_000L // Replay protection deduplication window

        /**
         * Android platform rules enforce strict battery limits and background restrictions.
         * Continuous background scanning when app is stopped or deep sleep requires OEM / System OS integration.
         */
        const val REQUIRES_OEM_SYSTEM_INTEGRATION_FOR_CONTINUOUS_BACKGROUND = true
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val secureRandom = SecureRandom()
    private val ueriRepo: FirebaseUeriRepository = firestoreRepository.ueriRepo

    // Bluetooth Hardware Stack
    private val bluetoothManager: BluetoothManager? = try {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    } catch (_: Exception) { null }

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    // State Tracking
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _activeToken = MutableStateFlow<String?>(null)
    val activeToken: StateFlow<String?> = _activeToken.asStateFlow()

    private val _lastRelayedDetection = MutableStateFlow<BeaconDetectionDoc?>(null)
    val lastRelayedDetection: StateFlow<BeaconDetectionDoc?> = _lastRelayedDetection.asStateFlow()

    private val _beaconStatusMessage = MutableStateFlow("STANDBY")
    val beaconStatusMessage: StateFlow<String> = _beaconStatusMessage.asStateFlow()

    private var tokenRotationJob: Job? = null
    private var activeAdvertiseCallback: AdvertiseCallback? = null
    private var activeScanCallback: ScanCallback? = null

    // Replay protection & deduplication cache (Token -> LastProcessedTimestamp)
    private val recentDetections = ConcurrentHashMap<String, Long>()

    // =========================================================================
    // 1. VICTIM DEVICE: EMERGENCY BEACON ADVERTISING
    // =========================================================================

    /**
     * Activates the short-lived BLE emergency beacon for an ongoing active emergency.
     * Starts automatic periodic rotating token generation and BLE transmission.
     */
    fun startEmergencyBeacon(emergencyId: String) {
        if (_isAdvertising.value) {
            Log.d(TAG, "Emergency beacon already active for emergency $emergencyId")
            return
        }

        _isAdvertising.value = true
        _beaconStatusMessage.value = "BEACON BROADCASTING (ROTATING TOKEN)"
        Log.i(TAG, "Starting UERI Emergency Beacon for incident: $emergencyId")

        // Start token generation and periodic rotation
        tokenRotationJob?.cancel()
        tokenRotationJob = scope.launch {
            while (isActive && _isAdvertising.value) {
                rotateAndAdvertiseToken(emergencyId)
                delay(TOKEN_ROTATION_INTERVAL_MS)
            }
        }
    }

    /**
     * Halts BLE beacon advertising when the emergency is resolved or marked SAFE.
     */
    fun stopEmergencyBeacon(emergencyId: String? = null) {
        tokenRotationJob?.cancel()
        tokenRotationJob = null

        stopBleAdvertising()

        _isAdvertising.value = false
        _activeToken.value = null
        _beaconStatusMessage.value = "BEACON DEACTIVATED"
        Log.i(TAG, "UERI Emergency Beacon stopped.")

        if (emergencyId != null) {
            scope.launch {
                ueriRepo.revokeBeaconTokensForEmergency(emergencyId)
            }
        }
    }

    private suspend fun rotateAndAdvertiseToken(emergencyId: String) {
        // 1. Generate cryptographically random 16-byte token
        val tokenBytes = ByteArray(TOKEN_BYTE_LENGTH)
        secureRandom.nextBytes(tokenBytes)
        val tokenHex = tokenBytes.joinToString("") { "%02x".format(it) }

        _activeToken.value = tokenHex
        Log.d(TAG, "Generated rotating beacon token: ${tokenHex.take(8)}... (Length: ${tokenHex.length})")

        // 2. Register token in backend beaconTokens/{tokenId}
        val tokenDoc = BeaconTokenDoc(
            tokenId = "tok-${System.currentTimeMillis()}-${tokenHex.take(6)}",
            emergencyId = emergencyId,
            rotatingToken = tokenHex,
            issuedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (1000 * 60 * 15), // 15 min expiry
            status = "ACTIVE"
        )
        ueriRepo.issueBeaconToken(tokenDoc)

        // 3. Transmit via Bluetooth Low Energy advertiser
        startBleAdvertising(tokenBytes)
    }

    private fun startBleAdvertising(tokenBytes: ByteArray) {
        if (!hasAdvertisePermission()) {
            Log.w(TAG, "BLUETOOTH_ADVERTISE permission not granted. Running in simulated beacon mode.")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth hardware adapter disabled or unavailable.")
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "BLE Peripheral Advertising mode not supported on this chipset.")
            return
        }

        // Build payload: [PROTOCOL_VERSION (1 byte) + TOKEN_BYTES (16 bytes)] = 17 bytes total
        // Strict Privacy Rule: NEVER transmit personal identity, name, phone, or exact victim coordinates!
        val serviceData = ByteArray(1 + TOKEN_BYTE_LENGTH)
        serviceData[0] = PROTOCOL_VERSION
        System.arraycopy(tokenBytes, 0, serviceData, 1, TOKEN_BYTE_LENGTH)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0) // Run until rotated
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(PARCEL_SERVICE_UUID)
            .addServiceData(PARCEL_SERVICE_UUID, serviceData)
            .setIncludeDeviceName(false) // Zero personal identifier exposure
            .setIncludeTxPowerLevel(false)
            .build()

        stopBleAdvertising()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "BLE Advertising active with rotating token: UUID=$UERI_SERVICE_UUID")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.w(TAG, "BLE Advertising failed with errorCode: $errorCode")
            }
        }
        activeAdvertiseCallback = callback

        try {
            advertiser?.startAdvertising(settings, data, callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE advertising: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE advertising: ${e.message}")
        }
    }

    private fun stopBleAdvertising() {
        activeAdvertiseCallback?.let { cb ->
            try {
                advertiser?.stopAdvertising(cb)
            } catch (_: Exception) {}
            activeAdvertiseCallback = null
        }
    }

    // =========================================================================
    // 2. RESPONDER DEVICE: EMERGENCY BEACON SCANNING & RELAY
    // =========================================================================

    /**
     * Activates BLE scanning on responder device to detect nearby offline emergency beacons.
     * Complies with Android BLE background and permission restrictions.
     */
    fun startBeaconScanner(responder: Responder, onBeaconDetected: ((BeaconDetectionDoc) -> Unit)? = null) {
        if (_isScanning.value) {
            Log.d(TAG, "Beacon scanner already running.")
            return
        }

        if (!hasScanPermission()) {
            Log.w(TAG, "BLUETOOTH_SCAN permission not granted.")
            _beaconStatusMessage.value = "SCAN PERMISSION NEEDED"
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth disabled. Cannot start BLE scanner.")
            _beaconStatusMessage.value = "BLUETOOTH DISABLED"
            return
        }

        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BluetoothLeScanner unavailable.")
            return
        }

        _isScanning.value = true
        _beaconStatusMessage.value = "SCANNING FOR BEACONS"
        Log.i(TAG, "UERI Responder BLE scanner active. Filtering on UUID: $UERI_SERVICE_UUID")

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(PARCEL_SERVICE_UUID)
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { handleScanResult(it, responder, onBeaconDetected) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { handleScanResult(it, responder, onBeaconDetected) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE Scan failed with errorCode: $errorCode")
                _beaconStatusMessage.value = "SCAN FAILED ($errorCode)"
            }
        }
        activeScanCallback = callback

        try {
            scanner?.startScan(listOf(scanFilter), scanSettings, callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE scan: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE scan: ${e.message}")
        }
    }

    /**
     * Stops the BLE beacon scanner.
     */
    fun stopBeaconScanner() {
        activeScanCallback?.let { cb ->
            try {
                scanner?.stopScan(cb)
            } catch (_: Exception) {}
            activeScanCallback = null
        }
        _isScanning.value = false
        _beaconStatusMessage.value = "SCANNER STOPPED"
        Log.i(TAG, "UERI Responder BLE scanner stopped.")
    }

    private fun handleScanResult(
        result: ScanResult,
        responder: Responder,
        onBeaconDetected: ((BeaconDetectionDoc) -> Unit)?
    ) {
        val record = result.scanRecord ?: return
        val serviceData = record.getServiceData(PARCEL_SERVICE_UUID) ?: return

        // Verify minimal payload: [version (1 byte) + token (16 bytes)]
        if (serviceData.size < 1 + TOKEN_BYTE_LENGTH) return
        val version = serviceData[0]
        if (version != PROTOCOL_VERSION) return

        val tokenBytes = ByteArray(TOKEN_BYTE_LENGTH)
        System.arraycopy(serviceData, 1, tokenBytes, 0, TOKEN_BYTE_LENGTH)
        val rotatingTokenHex = tokenBytes.joinToString("") { "%02x".format(it) }

        val rssi = result.rssi

        // Replay protection: deduplicate identical tokens within DEDUPLICATION_WINDOW_MS
        val now = System.currentTimeMillis()
        val lastSeen = recentDetections[rotatingTokenHex]
        if (lastSeen != null && (now - lastSeen) < DEDUPLICATION_WINDOW_MS) {
            // Already processed this token recently
            return
        }
        recentDetections[rotatingTokenHex] = now

        Log.i(TAG, "📡 Eligible UERI Emergency Beacon Detected! Token=${rotatingTokenHex.take(8)}... RSSI=$rssi dBm")

        // Relay detection to backend
        scope.launch {
            relayBeaconDetection(
                rotatingToken = rotatingTokenHex,
                rssi = rssi,
                responder = responder,
                onBeaconDetected = onBeaconDetected
            )
        }
    }

    /**
     * Resolves the detected rotating token to its authorized emergency session
     * and uploads proximity telemetry to Cloud Firestore without exposing victim identity.
     */
    suspend fun relayBeaconDetection(
        rotatingToken: String,
        rssi: Int,
        responder: Responder,
        onBeaconDetected: ((BeaconDetectionDoc) -> Unit)? = null
    ): Boolean {
        // 1. Resolve token in Firestore: no public lookup, backend authenticated check
        val tokenDoc = ueriRepo.resolveActiveBeaconToken(rotatingToken)
        if (tokenDoc == null) {
            Log.w(TAG, "Detected token could not be resolved to an active emergency session.")
            return false
        }

        val emergencyId = tokenDoc.emergencyId

        // 2. Estimate approximate proximity based on RSSI
        // NOTE: Signal strength is an approximate proximity signal, not exact distance.
        val (proximityEstimate, confidence) = estimateProximityFromRssi(rssi)

        // 3. Build BeaconDetectionDoc
        val detectionId = "det-${System.currentTimeMillis()}-${(1000..9999).random()}"
        val detection = BeaconDetectionDoc(
            detectionId = detectionId,
            tokenId = tokenDoc.tokenId,
            emergencyId = emergencyId,
            detectorUid = responder.id,
            detectorLatitude = responder.latitude,
            detectorLongitude = responder.longitude,
            detectorAccuracy = 8.0,
            rssi = rssi,
            detectedAt = System.currentTimeMillis(),
            source = "BLE",
            confidence = confidence
        )

        // 4. Save to beaconDetections/{detectionId}
        ueriRepo.recordBeaconDetection(detection)
        _lastRelayedDetection.value = detection
        _beaconStatusMessage.value = "RELAYED BEACON ($proximityEstimate)"

        // 5. Update tracking mode to NETWORK_ASSISTED and record events
        firestoreRepository.updateEmergencyStatus(
            sessionId = emergencyId,
            status = com.example.model.EmergencyStatus.ACTIVE,
            responderName = responder.name
        )

        // Record auditable event
        ueriRepo.addEmergencyEvent(
            emergencyId = emergencyId,
            event = EmergencyEventDoc(
                eventId = "evt-${System.currentTimeMillis()}",
                type = "BEACON_DETECTED",
                timestamp = System.currentTimeMillis(),
                actorUid = responder.id,
                actorRole = "RESPONDER",
                latitude = responder.latitude,
                longitude = responder.longitude,
                radiusMeters = 0.0,
                metadata = mapOf(
                    "rssi" to rssi,
                    "proximity" to proximityEstimate,
                    "confidence" to confidence,
                    "source" to "BLE_RELAY",
                    "trackingMode" to "NETWORK_ASSISTED"
                )
            )
        )

        // Record immutable audit log
        ueriRepo.recordAuditLog(
            AuditLogDoc(
                logId = "log-${System.currentTimeMillis()}",
                actorUid = responder.id,
                actorRole = "RESPONDER",
                action = "BEACON_RELAY_PROCESSED",
                emergencyId = emergencyId,
                timestamp = System.currentTimeMillis(),
                source = "BLE_MESH_RELAY",
                metadata = mapOf(
                    "rssi" to rssi,
                    "proximity" to proximityEstimate,
                    "trackingMode" to "NETWORK_ASSISTED"
                )
            )
        )

        onBeaconDetected?.invoke(detection)
        Log.i(TAG, "Successfully relayed beacon detection for emergency $emergencyId. Proximity: $proximityEstimate")
        return true
    }

    /**
     * Estimates device proximity category based on BLE RSSI signal strength.
     * Note: Not an exact distance calculation.
     */
    fun estimateProximityFromRssi(rssi: Int): Pair<String, Double> {
        return when {
            rssi >= -60 -> "IMMEDIATE (< 2m approx)" to 0.95
            rssi >= -75 -> "NEAR (2 - 8m approx)" to 0.85
            rssi >= -88 -> "APPROACHING (8 - 20m approx)" to 0.70
            else -> "FAR / WEAK (> 20m approx)" to 0.50
        }
    }

    /**
     * Prototype / Demo Simulation:
     * Simulates an in-range BLE beacon detection to allow full testing on devices
     * where hardware BLE advertising or scanning is restricted by OS or virtual container.
     */
    suspend fun simulateBeaconRelay(emergencyId: String, responder: Responder): BeaconDetectionDoc {
        val simulatedTokenBytes = ByteArray(TOKEN_BYTE_LENGTH)
        secureRandom.nextBytes(simulatedTokenBytes)
        val tokenHex = simulatedTokenBytes.joinToString("") { "%02x".format(it) }

        // Register token in Firestore
        val tokenDoc = BeaconTokenDoc(
            tokenId = "tok-sim-${System.currentTimeMillis()}",
            emergencyId = emergencyId,
            rotatingToken = tokenHex,
            issuedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (1000 * 60 * 15),
            status = "ACTIVE"
        )
        ueriRepo.issueBeaconToken(tokenDoc)

        val simRssi = -68
        val (proximity, confidence) = estimateProximityFromRssi(simRssi)

        val detection = BeaconDetectionDoc(
            detectionId = "det-sim-${System.currentTimeMillis()}",
            tokenId = tokenDoc.tokenId,
            emergencyId = emergencyId,
            detectorUid = responder.id,
            detectorLatitude = responder.latitude,
            detectorLongitude = responder.longitude,
            detectorAccuracy = 5.0,
            rssi = simRssi,
            detectedAt = System.currentTimeMillis(),
            source = "BLE",
            confidence = confidence
        )
        ueriRepo.recordBeaconDetection(detection)
        _lastRelayedDetection.value = detection
        _beaconStatusMessage.value = "SIMULATED BEACON RELAYED ($proximity)"

        ueriRepo.addEmergencyEvent(
            emergencyId = emergencyId,
            event = EmergencyEventDoc(
                type = "BEACON_DETECTED",
                timestamp = System.currentTimeMillis(),
                actorUid = responder.id,
                actorRole = "RESPONDER",
                latitude = responder.latitude,
                longitude = responder.longitude,
                metadata = mapOf(
                    "rssi" to simRssi,
                    "proximity" to proximity,
                    "trackingMode" to "NETWORK_ASSISTED"
                )
            )
        )

        return detection
    }

    // =========================================================================
    // PERMISSIONS & HARDWARE CHECKS
    // =========================================================================

    fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
}

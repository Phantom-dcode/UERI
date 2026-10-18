package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.model.CachedSosRequest
import com.example.model.EmergencySession
import com.example.model.SosSyncStatus
import com.example.model.ueri.AuditLogDoc
import com.example.model.ueri.BeaconTokenDoc
import com.example.model.ueri.EmergencyDoc
import com.example.model.ueri.EmergencyEventDoc
import com.example.transport.CloudEmergencyTransport
import com.example.transport.EmergencyTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository managing local Room caching of SOS requests when the device is offline
 * and automatic broadcasting of queued requests as soon as network connectivity is restored.
 */
class OfflineSosRepository(
    private val offlineSosDao: OfflineSosDao,
    private val emergencyDao: EmergencyDao? = null,
    private val transport: EmergencyTransport = CloudEmergencyTransport(),
    private val firestoreRepository: FirestoreEmergencyRepository = FirestoreEmergencyRepository(),
    context: Context? = null,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val onLogEvent: ((category: String, message: String) -> Unit)? = null
) {
    private val syncMutex = Mutex()

    private val _systemNetworkAvailable = MutableStateFlow(checkInitialNetworkState(context))
    private val _simulatedOffline = MutableStateFlow(false)

    private val _isOnline = MutableStateFlow(_systemNetworkAvailable.value && !_simulatedOffline.value)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow<String?>(null)
    val lastSyncMessage: StateFlow<String?> = _lastSyncMessage.asStateFlow()

    val allCachedRequests: Flow<List<CachedSosRequest>> = offlineSosDao.observeAllCachedRequests()
    val pendingSosRequests: Flow<List<CachedSosRequest>> = offlineSosDao.observePendingSosRequests()
    val pendingCount: Flow<Int> = offlineSosDao.observePendingCount()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        registerNetworkMonitor(context)
    }

    private fun checkInitialNetworkState(context: Context?): Boolean {
        if (context == null) return true
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun registerNetworkMonitor(context: Context?) {
        if (context == null) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            connectivityManager = cm
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _systemNetworkAvailable.value = true
                    updateEffectiveConnectivity()
                }

                override fun onLost(network: Network) {
                    val stillConnected = checkInitialNetworkState(context)
                    _systemNetworkAvailable.value = stillConnected
                    updateEffectiveConnectivity()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val hasInternet =
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    _systemNetworkAvailable.value = hasInternet
                    updateEffectiveConnectivity()
                }
            }
            networkCallback = callback
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            // In unit test or restricted environments, fallback to manual / initial state
        }
    }

    private fun updateEffectiveConnectivity() {
        val previouslyOnline = _isOnline.value
        val nowOnline = _systemNetworkAvailable.value && !_simulatedOffline.value
        _isOnline.value = nowOnline

        if (!previouslyOnline && nowOnline) {
            onLogEvent?.invoke(
                "🌐 NETWORK RESTORED",
                "Connectivity restored. Checking Room cache for pending offline SOS requests..."
            )
            externalScope.launch {
                flushPendingSosQueue()
            }
        } else if (previouslyOnline && !nowOnline) {
            onLogEvent?.invoke(
                "📴 OFFLINE MODE",
                "Network connection lost. SOS requests will be cached locally in Room and auto-broadcasted when online."
            )
        }
    }

    /**
     * Allows toggling or simulating offline/online state for field testing and verification.
     * When transitioning back to online, automatically broadcasts all cached SOS requests.
     */
    fun setOfflineMode(offline: Boolean) {
        _simulatedOffline.value = offline
        updateEffectiveConnectivity()
    }

    fun isSimulatedOffline(): Boolean = _simulatedOffline.value

    /**
     * Persists an SOS request in Room. If the device is currently online, broadcasts immediately
     * and marks the Room record as BROADCASTED. If offline (or if the broadcast fails), keeps
     * the record in PENDING_SYNC state to be automatically broadcasted upon reconnection.
     */
    suspend fun cacheAndBroadcastSos(
        session: EmergencySession,
        victimUid: String,
        actorRole: String = "CITIZEN"
    ): CachedSosRequest {
        emergencyDao?.insertSession(session)

        val initialRecord = CachedSosRequest.fromEmergencySession(
            session = session,
            victimUid = victimUid,
            syncStatus = SosSyncStatus.PENDING_SYNC
        )
        offlineSosDao.upsertCachedSos(initialRecord)

        if (!_isOnline.value) {
            val msg = "Device offline: SOS ${session.emergencyId} cached locally in Room DB for auto-broadcast."
            _lastSyncMessage.value = msg
            onLogEvent?.invoke("💾 OFFLINE CACHE", msg)
            return initialRecord
        }

        val broadcasted = performBroadcast(initialRecord, actorRole)
        return if (broadcasted) {
            val now = System.currentTimeMillis()
            offlineSosDao.markBroadcasted(initialRecord.requestId, now)
            val syncedMsg = "SOS ${session.emergencyId} broadcasted to Cloud & Mesh."
            _lastSyncMessage.value = syncedMsg
            initialRecord.copy(
                syncStatus = SosSyncStatus.BROADCASTED.name,
                broadcastedAt = now,
                updatedAt = now
            )
        } else {
            val errMsg = "Broadcast unreachable: SOS ${session.emergencyId} retained in Room cache for retry."
            offlineSosDao.updateSyncAttempt(
                requestId = initialRecord.requestId,
                status = SosSyncStatus.PENDING_SYNC.name,
                error = errMsg
            )
            _lastSyncMessage.value = errMsg
            onLogEvent?.invoke("💾 OFFLINE CACHE", errMsg)
            initialRecord
        }
    }

    /**
     * Updates dynamic radius expansion for a cached SOS request so when connection is restored,
     * the latest expanded radius and notified counts are broadcasted.
     */
    suspend fun updateCachedRadiusIfPending(
        emergencyId: String,
        radiusMeters: Double,
        peopleNotified: Int,
        respondersCount: Int
    ) {
        offlineSosDao.updatePendingRadius(
            emergencyId = emergencyId,
            radiusMeters = radiusMeters,
            peopleNotified = peopleNotified,
            respondersCount = respondersCount
        )
    }

    /**
     * Automatically broadcasts all pending offline SOS requests stored in Room once connectivity is restored.
     * Returns the number of SOS requests successfully broadcasted.
     */
    suspend fun flushPendingSosQueue(): Int = syncMutex.withLock {
        if (!_isOnline.value) return 0

        val pendingList = offlineSosDao.getPendingSosRequests()
        if (pendingList.isEmpty()) return 0

        _isSyncing.value = true
        var syncedCount = 0

        try {
            for (cached in pendingList) {
                if (!_isOnline.value) break

                offlineSosDao.updateSyncAttempt(
                    requestId = cached.requestId,
                    status = SosSyncStatus.SYNCING.name
                )

                val success = performBroadcast(cached, "CITIZEN")
                if (success) {
                    val now = System.currentTimeMillis()
                    offlineSosDao.markBroadcasted(cached.requestId, now)
                    syncedCount++
                    onLogEvent?.invoke(
                        "📡 AUTO-BROADCAST SYNC",
                        "Cached offline SOS ${cached.emergencyId} automatically broadcasted after connection restored."
                    )
                } else {
                    offlineSosDao.updateSyncAttempt(
                        requestId = cached.requestId,
                        status = SosSyncStatus.FAILED.name,
                        error = "Transport broadcast failed during auto-sync"
                    )
                }
            }

            if (syncedCount > 0) {
                _lastSyncMessage.value =
                    "Auto-broadcasted $syncedCount cached SOS ${if (syncedCount == 1) "request" else "requests"} on reconnect."
            }
        } finally {
            _isSyncing.value = false
        }

        return syncedCount
    }

    private suspend fun performBroadcast(
        cached: CachedSosRequest,
        actorRole: String
    ): Boolean {
        return try {
            val session = cached.toEmergencySession()
            val transportOk = transport.broadcastEmergency(session)
            if (!transportOk) return false

            if (firestoreRepository.isAvailable()) {
                firestoreRepository.publishEmergencyEvent(session)
                val ueri = firestoreRepository.ueriRepo
                val now = System.currentTimeMillis()

                val doc = EmergencyDoc(
                    emergencyId = session.emergencyId,
                    victimUid = cached.victimUid,
                    status = session.status.name,
                    priority = session.priority,
                    emergencyType = session.emergencyType,
                    latitude = session.latitude,
                    longitude = session.longitude,
                    accuracy = session.locationAccuracyMeters.toDouble(),
                    radiusMeters = session.radiusMeters,
                    createdAt = session.createdAt,
                    updatedAt = now,
                    lastLocationAt = now,
                    nearbyResponderCount = session.respondersCount,
                    notifiedResponderCount = session.peopleNotified,
                    trackingMode = session.trackingMode,
                    evidenceEnabled = true,
                    policeEscalated = true
                )
                ueri.saveEmergency(doc)

                ueri.addEmergencyEvent(
                    session.emergencyId,
                    EmergencyEventDoc(
                        type = if (cached.retryCount > 0) "OFFLINE_CACHE_SYNCED" else "CREATED",
                        timestamp = now,
                        actorUid = cached.victimUid,
                        actorRole = actorRole,
                        latitude = session.latitude,
                        longitude = session.longitude,
                        radiusMeters = session.radiusMeters,
                        metadata = mapOf(
                            "priority" to session.priority,
                            "notifiedResponders" to session.respondersCount,
                            "cachedCreatedAt" to session.createdAt
                        )
                    )
                )

                val rotatingToken = "BEACON-${(100000..999999).random()}"
                ueri.issueBeaconToken(
                    BeaconTokenDoc(
                        emergencyId = session.emergencyId,
                        rotatingToken = rotatingToken,
                        issuedAt = now,
                        expiresAt = now + (15 * 60 * 1000L),
                        status = "ACTIVE"
                    )
                )

                ueri.recordAuditLog(
                    AuditLogDoc(
                        actorUid = cached.victimUid,
                        actorRole = actorRole,
                        action = if (cached.retryCount > 0) "OFFLINE_SOS_AUTO_BROADCASTED" else "SOS_TRIGGERED",
                        emergencyId = session.emergencyId,
                        timestamp = now,
                        source = "ROOM_OFFLINE_SYNC_QUEUE",
                        metadata = mapOf(
                            "radius" to session.radiusMeters,
                            "lat" to session.latitude,
                            "lon" to session.longitude
                        )
                    )
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

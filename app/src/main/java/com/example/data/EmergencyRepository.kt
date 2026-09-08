package com.example.data

import com.example.config.EmergencyConfig
import com.example.model.EmergencyLog
import com.example.model.EmergencySession
import com.example.model.EmergencyStatus
import com.example.model.EvidenceItem
import com.example.model.EvidenceType
import com.example.model.Responder
import com.example.model.ResponderStatus
import com.example.transport.CloudEmergencyTransport
import com.example.transport.EmergencyTransport
import com.example.util.GeoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

class EmergencyRepository(
    private val emergencyDao: EmergencyDao,
    private val transport: EmergencyTransport = CloudEmergencyTransport()
) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _session = MutableStateFlow<EmergencySession>(createDefaultSafeSession())
    val session: StateFlow<EmergencySession> = _session.asStateFlow()

    private val _responders = MutableStateFlow<List<Responder>>(emptyList())
    val responders: StateFlow<List<Responder>> = _responders.asStateFlow()

    private val _evidenceList = MutableStateFlow<List<EvidenceItem>>(emptyList())
    val evidenceList: StateFlow<List<EvidenceItem>> = _evidenceList.asStateFlow()

    private val _logs = MutableStateFlow<List<EmergencyLog>>(emptyList())
    val logs: StateFlow<List<EmergencyLog>> = _logs.asStateFlow()

    private val _isFastDemoMode = MutableStateFlow<Boolean>(true) // default fast (5s) for hackathon demo convenience
    val isFastDemoMode: StateFlow<Boolean> = _isFastDemoMode.asStateFlow()

    private var radiusTickerJob: Job? = null

    init {
        initDefaultResponders()
        addLog("SYSTEM INITIALIZED", "UERI Ready. All monitoring systems active.")
    }

    private fun createDefaultSafeSession(): EmergencySession {
        return EmergencySession(
            id = UUID.randomUUID().toString(),
            emergencyId = "UERI-IDLE",
            status = EmergencyStatus.SAFE,
            emergencyType = "STANDBY",
            priority = "NORMAL",
            latitude = EmergencyConfig.DEFAULT_LATITUDE,
            longitude = EmergencyConfig.DEFAULT_LONGITUDE,
            locationStatus = "ACTIVE",
            locationAccuracyMeters = 4.2f,
            radiusMeters = 0.0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            peopleNotified = 0,
            respondersCount = 0
        )
    }

    fun initDefaultResponders(
        victimLat: Double = EmergencyConfig.DEFAULT_LATITUDE,
        victimLon: Double = EmergencyConfig.DEFAULT_LONGITUDE
    ) {
        val initial = GeoUtils.createInitialResponders(victimLat, victimLon)
        _responders.value = initial
    }

    fun setFastDemoMode(enabled: Boolean) {
        _isFastDemoMode.value = enabled
        addLog("CONFIG", "Demo speed set to ${if (enabled) "Fast (5s steps)" else "Standard (10s steps)"}")
    }

    /**
     * ONE EMERGENCY ACTION: Citizen presses SOS
     * Immediate alert creation without confirmation dialogs or delays.
     */
    fun triggerSos(
        latitude: Double? = null,
        longitude: Double? = null,
        locationStatus: String = "ACTIVE",
        locationAccuracy: Float = 5.0f
    ) {
        val currentLat = latitude ?: _session.value.latitude
        val currentLon = longitude ?: _session.value.longitude

        val emergencyUniqueId = "UERI-${(100000..999999).random()}"
        val initialRadius = 500.0
        val now = System.currentTimeMillis()

        // Recalculate responder distances relative to victim location
        updateResponderDistances(currentLat, currentLon, initialRadius)

        val alertedResponders = _responders.value.filter { it.distanceMeters <= initialRadius }
        val estimatedCivilians = (alertedResponders.size * 3) + 2

        val newSession = EmergencySession(
            id = UUID.randomUUID().toString(),
            emergencyId = emergencyUniqueId,
            status = EmergencyStatus.ACTIVE,
            emergencyType = "CRITICAL / UNKNOWN",
            priority = "CRITICAL",
            latitude = currentLat,
            longitude = currentLon,
            locationStatus = locationStatus,
            locationAccuracyMeters = locationAccuracy,
            radiusMeters = initialRadius,
            createdAt = now,
            updatedAt = now,
            peopleNotified = estimatedCivilians,
            respondersCount = alertedResponders.size
        )

        _session.value = newSession
        _evidenceList.value = emptyList()

        addLog("🚨 ALERT", "EMERGENCY TRIGGERED: $emergencyUniqueId. Priority: CRITICAL. Initial radius: 500m.")
        addLog("BROADCAST", "Alerted ${alertedResponders.size} responders within 500m radius.")

        // Persist to Room
        scope.launch {
            try {
                emergencyDao.insertSession(newSession)
                transport.broadcastEmergency(newSession)
            } catch (e: Exception) {
                // Room fallback handled gracefully
            }
        }

        // Start dynamic radius expansion ticker
        startRadiusExpansionTicker(now)
    }

    private fun startRadiusExpansionTicker(startTime: Long) {
        radiusTickerJob?.cancel()
        radiusTickerJob = scope.launch {
            while (isActive) {
                delay(1000L) // tick every 1 second
                val current = _session.value
                if (current.status != EmergencyStatus.ACTIVE && current.status != EmergencyStatus.RESPONDER_FOUND) {
                    break
                }

                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000L
                val radiusStage = EmergencyConfig.getRadiusForDuration(elapsedSeconds, _isFastDemoMode.value)

                if (radiusStage.radiusMeters > current.radiusMeters) {
                    val newRadius = radiusStage.radiusMeters
                    updateResponderDistances(current.latitude, current.longitude, newRadius)

                    val activeRespCount = _responders.value.count { it.distanceMeters <= newRadius }
                    val estimatedPeople = (activeRespCount * 4) + (newRadius / 150.0).roundToInt()

                    val updatedSession = current.copy(
                        radiusMeters = newRadius,
                        updatedAt = System.currentTimeMillis(),
                        respondersCount = activeRespCount,
                        peopleNotified = estimatedPeople
                    )

                    _session.value = updatedSession
                    addLog(
                        "RADIUS EXPANSION",
                        "Radius expanded to ${radiusStage.displayLabel}. Responders eligible: $activeRespCount. People notified: $estimatedPeople"
                    )

                    try {
                        emergencyDao.updateSession(updatedSession)
                        transport.updateRadius(updatedSession, newRadius)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun updateResponderDistances(victimLat: Double, victimLon: Double, currentRadius: Double) {
        _responders.update { currentList ->
            currentList.map { responder ->
                val dist = GeoUtils.haversineDistanceMeters(victimLat, victimLon, responder.latitude, responder.longitude)
                val isEligible = dist <= currentRadius
                val newStatus = when {
                    responder.status == ResponderStatus.RESPONDING -> ResponderStatus.RESPONDING
                    isEligible -> ResponderStatus.ALERTED
                    else -> ResponderStatus.AVAILABLE
                }
                responder.copy(
                    distanceMeters = dist,
                    status = newStatus,
                    etaMinutes = maxOf(1, (dist / 400.0).roundToInt())
                )
            }
        }
    }

    /**
     * Responder clicks "I'M RESPONDING"
     */
    fun responderRespond(responderId: String) {
        val responder = _responders.value.find { it.id == responderId } ?: return

        _responders.update { list ->
            list.map {
                if (it.id == responderId) it.copy(status = ResponderStatus.RESPONDING) else it
            }
        }

        _session.update { current ->
            current.copy(
                status = EmergencyStatus.RESPONDER_FOUND,
                activeResponderName = responder.name,
                activeResponderDistanceMeters = responder.distanceMeters,
                activeResponderEtaMinutes = responder.etaMinutes,
                updatedAt = System.currentTimeMillis()
            )
        }

        addLog(
            "🤝 RESPONDER EN ROUTE",
            "${responder.name} (${responder.role}) accepted the emergency call. Distance: ${EmergencyConfig.formatDistance(responder.distanceMeters)}, ETA: ${responder.etaMinutes} min."
        )

        scope.launch {
            try {
                emergencyDao.updateSession(_session.value)
            } catch (_: Exception) {}
        }
    }

    fun responderArrived(responderId: String) {
        val responder = _responders.value.find { it.id == responderId } ?: return
        _responders.update { list ->
            list.map {
                if (it.id == responderId) it.copy(status = ResponderStatus.RESPONDING, etaMinutes = 0, distanceMeters = 0.0) else it
            }
        }
        _session.update { current ->
            current.copy(
                activeResponderDistanceMeters = 0.0,
                activeResponderEtaMinutes = 0,
                updatedAt = System.currentTimeMillis()
            )
        }
        addLog("🚨 ARRIVED ON SCENE", "${responder.name} (${responder.role}) has arrived at the victim's location.")
    }

    fun forceNextRadiusHop() {
        val current = _session.value
        val currentRadius = current.radiusMeters
        val nextRadius = when {
            currentRadius < 500.0 -> 500.0
            currentRadius < 1500.0 -> 1500.0
            currentRadius < 3000.0 -> 3000.0
            else -> currentRadius + 1500.0
        }
        updateResponderDistances(current.latitude, current.longitude, nextRadius)
        val activeRespCount = _responders.value.count { it.distanceMeters <= nextRadius }
        val estimatedPeople = (activeRespCount * 4) + (nextRadius / 150.0).roundToInt()
        val updated = current.copy(
            radiusMeters = nextRadius,
            respondersCount = activeRespCount,
            peopleNotified = estimatedPeople,
            updatedAt = System.currentTimeMillis()
        )
        _session.value = updated
        addLog("MANUAL RADIUS HOP", "Dispatcher expanded radius to ${EmergencyConfig.formatDistance(nextRadius)}. Responders reached: $activeRespCount")
    }

    fun dispatchAllResponders() {
        _responders.update { list ->
            list.map { it.copy(status = ResponderStatus.RESPONDING) }
        }
        val first = _responders.value.firstOrNull()
        _session.update { current ->
            current.copy(
                status = EmergencyStatus.RESPONDER_FOUND,
                activeResponderName = first?.name ?: "All Units",
                activeResponderDistanceMeters = first?.distanceMeters ?: 120.0,
                activeResponderEtaMinutes = first?.etaMinutes ?: 1,
                updatedAt = System.currentTimeMillis()
            )
        }
        addLog("MASS DISPATCH", "Central Control dispatched all available tactical units.")
    }

    /**
     * Citizen marks themselves SAFE
     * Immediately halts dynamic radius and broadcasts resolution.
     */
    fun markSafe() {
        radiusTickerJob?.cancel()
        radiusTickerJob = null

        val current = _session.value
        val now = System.currentTimeMillis()
        val durationSeconds = (now - current.createdAt) / 1000L

        val resolvedSession = current.copy(
            status = EmergencyStatus.RESOLVED,
            resolvedAt = now,
            updatedAt = now,
            resolutionDurationSeconds = durationSeconds
        )

        _session.value = resolvedSession

        _responders.update { list ->
            list.map {
                if (it.status == ResponderStatus.RESPONDING || it.status == ResponderStatus.ALERTED) {
                    it.copy(status = ResponderStatus.AVAILABLE)
                } else it
            }
        }

        addLog(
            "🟢 EMERGENCY RESOLVED",
            "Victim marked SAFE. Session ${current.emergencyId} closed. Total duration: ${durationSeconds}s. Responders stood down."
        )

        scope.launch {
            try {
                emergencyDao.updateSession(resolvedSession)
                transport.broadcastResolution(resolvedSession)
            } catch (_: Exception) {}
        }
    }

    /**
     * Add timestamped evidence (photo, audio, video)
     */
    fun addEvidence(
        type: EvidenceType,
        description: String,
        uri: String? = null,
        durationSeconds: Int = 0
    ) {
        val current = _session.value
        val evidenceItem = EvidenceItem(
            id = UUID.randomUUID().toString(),
            emergencyId = current.emergencyId,
            type = type,
            timestamp = System.currentTimeMillis(),
            latitude = current.latitude,
            longitude = current.longitude,
            description = description,
            uri = uri,
            durationSeconds = durationSeconds
        )

        _evidenceList.update { listOf(evidenceItem) + it }
        addLog("📸 EVIDENCE SAVED", "${type.name} recorded at coordinate (${String.format(java.util.Locale.US, "%.5f", current.latitude)}, ${String.format(java.util.Locale.US, "%.5f", current.longitude)})")
    }

    fun updateLiveLocation(lat: Double, lon: Double, accuracy: Float = 5.0f) {
        _session.update {
            it.copy(
                latitude = lat,
                longitude = lon,
                locationStatus = "ACTIVE",
                locationAccuracyMeters = accuracy,
                updatedAt = System.currentTimeMillis()
            )
        }
        if (_session.value.status == EmergencyStatus.ACTIVE || _session.value.status == EmergencyStatus.RESPONDER_FOUND) {
            updateResponderDistances(lat, lon, _session.value.radiusMeters)
        }
    }

    fun setLocationUnavailable() {
        _session.update { it.copy(locationStatus = "UNAVAILABLE") }
        addLog("GPS WARNING", "Location unavailable. Emergency broadcast maintains last known grid.")
    }

    fun resetDemo() {
        radiusTickerJob?.cancel()
        radiusTickerJob = null

        _session.value = createDefaultSafeSession()
        initDefaultResponders()
        _evidenceList.value = emptyList()
        _logs.value = emptyList()
        addLog("DEMO RESET", "System returned to clean standby state.")
    }

    private fun addLog(category: String, message: String) {
        val log = EmergencyLog(
            id = UUID.randomUUID().toString(),
            emergencyId = _session.value.emergencyId,
            timestamp = System.currentTimeMillis(),
            message = message,
            category = category
        )
        _logs.update { listOf(log) + it.take(49) }
    }
}

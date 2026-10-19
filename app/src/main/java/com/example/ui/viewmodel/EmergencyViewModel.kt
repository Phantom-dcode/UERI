package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.AppDatabase
import com.example.data.EmergencyRepository
import com.example.data.FirestoreEmergencyRepository
import com.example.data.OfflineSosRepository
import com.example.model.CachedSosRequest
import com.example.model.EmergencyLog
import com.example.model.EmergencySession
import com.example.model.EvidenceItem
import com.example.model.EvidenceType
import com.example.model.Responder
import com.example.model.Sos24hAnalytics
import com.example.transport.BackendNotificationService
import com.example.util.AudioRecorderHelper
import com.example.util.LocationService
import com.example.util.LocationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import com.example.auth.FirebaseAuthManager
import com.example.auth.OtpChannel
import com.example.auth.OtpDispatchResult
import com.example.auth.OtpPurpose
import com.example.beacon.UeriBeaconManager
import com.example.model.EmergencyStatus
import com.example.model.ueri.BeaconDetectionDoc
import com.example.model.ueri.UserRole
import com.example.storage.FirebaseStorageService
import com.example.util.GeoUtils
import kotlin.math.roundToInt

class EmergencyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EmergencyRepository
    val locationService: LocationService = LocationService(application)
    private val audioRecorderHelper: AudioRecorderHelper = AudioRecorderHelper(application)
    val backendNotificationService: BackendNotificationService = BackendNotificationService(application)
    val beaconManager: UeriBeaconManager

    val session: StateFlow<EmergencySession>
    val responderSession: StateFlow<EmergencySession>
    val responders: StateFlow<List<Responder>>
    val evidenceList: StateFlow<List<EvidenceItem>>
    val logs: StateFlow<List<EmergencyLog>>
    val sosAnalytics24h: StateFlow<Sos24hAnalytics>
    val isFastDemoMode: StateFlow<Boolean>
    val firestoreRepository: FirestoreEmergencyRepository
    val offlineSosRepository: OfflineSosRepository
    val isOnline: StateFlow<Boolean>
    val pendingOfflineSosList: StateFlow<List<CachedSosRequest>>
    val pendingOfflineSosCount: StateFlow<Int>
    val isSyncingOfflineSos: StateFlow<Boolean>
    val lastOfflineSyncMessage: StateFlow<String?>
    val authManager: FirebaseAuthManager
    val isBeaconAdvertising: StateFlow<Boolean>
    val isBeaconScanning: StateFlow<Boolean>
    val beaconStatusMessage: StateFlow<String>
    val lastRelayedDetection: StateFlow<BeaconDetectionDoc?>
    val authState: StateFlow<com.example.auth.AuthState>
    val currentUser: StateFlow<com.example.model.ueri.UserAccount>
    val activeRole: StateFlow<com.example.model.ueri.UserRole>
    private val storageService: FirebaseStorageService

    private val _locationState = MutableStateFlow<LocationState>(LocationState.Idle)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    // UI state for Responder selector
    private val _selectedResponderId = MutableStateFlow("resp-1")
    val selectedResponderId: StateFlow<String> = _selectedResponderId.asStateFlow()

    // Audio recording state for evidence section
    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private var audioRecordingJob: Job? = null
    private var locationUpdatesJob: Job? = null

    init {
        val database = AppDatabase.getInstance(application)
        val fRepo = FirestoreEmergencyRepository()
        firestoreRepository = fRepo
        authManager = FirebaseAuthManager.getInstance(application)
        beaconManager = UeriBeaconManager(application, fRepo)

        var repoRef: EmergencyRepository? = null
        val offlineRepo = OfflineSosRepository(
            offlineSosDao = database.offlineSosDao(),
            emergencyDao = database.emergencyDao(),
            firestoreRepository = fRepo,
            context = application,
            externalScope = viewModelScope,
            onLogEvent = { category, msg ->
                repoRef?.addLog(category, msg)
            }
        )
        offlineSosRepository = offlineRepo

        repository = EmergencyRepository(
            emergencyDao = database.emergencyDao(),
            firestoreRepository = fRepo,
            authManager = authManager,
            beaconManager = beaconManager,
            offlineSosRepository = offlineRepo
        )
        repoRef = repository

        isOnline = offlineRepo.isOnline
        pendingOfflineSosList = offlineRepo.pendingSosRequests.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList()
        )
        pendingOfflineSosCount = offlineRepo.pendingCount.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            0
        )
        isSyncingOfflineSos = offlineRepo.isSyncing
        lastOfflineSyncMessage = offlineRepo.lastSyncMessage

        isBeaconAdvertising = beaconManager.isAdvertising
        isBeaconScanning = beaconManager.isScanning
        beaconStatusMessage = beaconManager.beaconStatusMessage
        lastRelayedDetection = beaconManager.lastRelayedDetection
        authState = authManager.authState
        currentUser = authManager.currentUser
        activeRole = authManager.activeRole

        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                if (user.role == UserRole.RESPONDER.name && user.verifiedResponder && user.responderId.isNotBlank()) {
                    repository.syncAuthenticatedResponder(user)
                    _selectedResponderId.value = user.responderId.trim()
                }
            }
        }

        session = repository.session.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repository.session.value
        )
        responders = repository.responders.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repository.responders.value
        )

        // Reactive stream calculating the eligible emergency for the selected field responder
        responderSession = combine(
            session,
            responders,
            _selectedResponderId
        ) { currentSession, responderList, selectedId ->
            if (currentSession.status == EmergencyStatus.SAFE || currentSession.status == EmergencyStatus.RESOLVED) {
                currentSession
            } else {
                val responder = responderList.find { it.id == selectedId } ?: responderList.firstOrNull()
                if (responder == null) {
                    currentSession.copy(status = EmergencyStatus.SAFE)
                } else {
                    val distance = GeoUtils.haversineDistanceMeters(
                        responder.latitude,
                        responder.longitude,
                        currentSession.latitude,
                        currentSession.longitude
                    )
                    val isAcceptedByMe = currentSession.acceptedResponderUid == responder.id ||
                        (currentSession.status == EmergencyStatus.RESPONDER_FOUND && currentSession.activeResponderName == responder.name)
                    val isWithinRadius = distance <= currentSession.radiusMeters

                    // Only expose incident to responder if within dynamic radius or already accepted by them
                    if ((isWithinRadius && currentSession.status == EmergencyStatus.ACTIVE) || isAcceptedByMe) {
                        currentSession.copy(
                            activeResponderDistanceMeters = distance,
                            activeResponderEtaMinutes = maxOf(1, (distance / 400.0).roundToInt())
                        )
                    } else {
                        // Beyond dynamic radius: keep standby mode to avoid exposing unrelated emergencies
                        EmergencySession(
                            id = "SAFE-STANDBY",
                            emergencyId = "UERI-IDLE",
                            status = EmergencyStatus.SAFE,
                            priority = "NORMAL",
                            emergencyType = "STANDBY",
                            latitude = responder.latitude,
                            longitude = responder.longitude,
                            radiusMeters = 0.0
                        )
                    }
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repository.session.value
        )
        evidenceList = repository.evidenceList.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repository.evidenceList.value
        )
        sosAnalytics24h = repository.sosAnalytics24h.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repository.sosAnalytics24h.value
        )
        logs = repository.logs.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repository.logs.value
        )
        isFastDemoMode = repository.isFastDemoMode.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repository.isFastDemoMode.value
        )
        storageService = FirebaseStorageService(fRepo.ueriRepo)

        // Attempt initial location capture if permissions already granted
        checkAndStartLocationTracking()
    }

    /**
     * Checks if location permission is granted. If so, starts tracking and captures initial fix.
     * If denied, gracefully switches to offline fallback.
     */
    fun checkAndStartLocationTracking() {
        if (locationService.hasLocationPermission()) {
            startLocationUpdates()
        } else {
            _locationState.value = LocationState.PermissionDenied()
        }
    }

    /**
     * Handles permission request results from UI.
     */
    fun onLocationPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            _locationState.value = LocationState.Loading
            startLocationUpdates()
        } else {
            _locationState.value = LocationState.PermissionDenied(
                "GPS permission denied. UERI offline mesh coordinates active."
            )
            repository.updateLiveLocation(
                lat = session.value.latitude,
                lon = session.value.longitude,
                accuracy = 100.0f
            )
        }
    }

    /**
     * One-shot refresh for GPS position.
     */
    fun refreshCurrentLocation() {
        viewModelScope.launch {
            _locationState.value = LocationState.Loading
            val result = locationService.getCurrentLocation()
            _locationState.value = result
            if (result is LocationState.Success) {
                repository.updateLiveLocation(
                    lat = result.latitude,
                    lon = result.longitude,
                    accuracy = result.accuracy
                )
            }
        }
    }

    private fun startLocationUpdates() {
        locationUpdatesJob?.cancel()
        locationUpdatesJob = viewModelScope.launch {
            _locationState.value = LocationState.Loading
            locationService.getLocationUpdates(intervalMs = 4000L).collect { state ->
                _locationState.value = state
                if (state is LocationState.Success) {
                    repository.updateLiveLocation(
                        lat = state.latitude,
                        lon = state.longitude,
                        accuracy = state.accuracy
                    )
                }
            }
        }
    }

    fun triggerSos(lat: Double? = null, lon: Double? = null, locationStatus: String? = null) {
        viewModelScope.launch {
            val resolvedLat: Double
            val resolvedLon: Double
            val resolvedStatus: String
            val resolvedAccuracy: Float

            val loc = _locationState.value
            if (lat != null && lon != null) {
                resolvedLat = lat
                resolvedLon = lon
                resolvedStatus = locationStatus ?: "ACTIVE"
                resolvedAccuracy = 5.0f
            } else if (loc is LocationState.Success) {
                resolvedLat = loc.latitude
                resolvedLon = loc.longitude
                resolvedStatus = "GPS_VERIFIED"
                resolvedAccuracy = loc.accuracy
            } else if (loc is LocationState.PermissionDenied) {
                resolvedLat = session.value.latitude
                resolvedLon = session.value.longitude
                resolvedStatus = "OFFLINE_FALLBACK"
                resolvedAccuracy = 150.0f
            } else {
                resolvedLat = session.value.latitude
                resolvedLon = session.value.longitude
                resolvedStatus = locationStatus ?: "ACTIVE"
                resolvedAccuracy = 10.0f
            }

            repository.triggerSos(
                latitude = resolvedLat,
                longitude = resolvedLon,
                locationStatus = resolvedStatus,
                locationAccuracy = resolvedAccuracy
            )

            // Asynchronously build and dispatch backend notification payload
            backendNotificationService.broadcastSos(
                session = session.value,
                locationService = locationService,
                evidenceList = evidenceList.value
            )
        }
    }

    fun markSafe() {
        if (_isRecordingAudio.value) {
            stopAudioRecording()
        }
        repository.markSafe()
    }

    fun responderRespond(responderId: String, onConflict: ((String) -> Unit)? = null) {
        repository.responderRespond(responderId, onConflict)
    }

    fun responderArrived(responderId: String) {
        repository.responderArrived(responderId)
    }

    fun forceNextRadiusHop() {
        repository.forceNextRadiusHop()
    }

    fun dispatchAllResponders() {
        repository.dispatchAllResponders()
    }

    fun selectResponder(responderId: String) {
        _selectedResponderId.value = responderId
        val resp = responders.value.find { it.id == responderId }
        if (resp != null) {
            viewModelScope.launch {
                authManager.ensureResponderVerified(resp)
            }
        }
    }

    fun setFastDemoMode(enabled: Boolean) {
        repository.setFastDemoMode(enabled)
    }

    fun resetDemo() {
        if (_isRecordingAudio.value) {
            stopAudioRecording()
        }
        repository.resetDemo()
        refreshCurrentLocation()
    }

    fun updateLiveLocation(lat: Double, lon: Double) {
        repository.updateLiveLocation(lat, lon)
    }

    fun setLocationUnavailable() {
        repository.setLocationUnavailable()
    }

    fun startBeaconScanner(responderId: String? = null) {
        val targetId = responderId ?: selectedResponderId.value
        val responder = responders.value.find { it.id == targetId } ?: responders.value.firstOrNull() ?: return
        beaconManager.startBeaconScanner(responder) { detection ->
            repository.onBeaconDetectionRelayed(detection)
        }
    }

    fun stopBeaconScanner() {
        beaconManager.stopBeaconScanner()
    }

    fun simulateBeaconRelay() {
        viewModelScope.launch {
            val responder = responders.value.find { it.id == selectedResponderId.value } ?: responders.value.firstOrNull() ?: return@launch
            val detection = beaconManager.simulateBeaconRelay(session.value.emergencyId, responder)
            repository.onBeaconDetectionRelayed(detection)
        }
    }

    /**
     * Saves captured camera photo bitmap to local application cache and adds to Evidence store.
     */
    fun addPhotoEvidence(bitmap: Bitmap? = null, uri: String? = null) {
        viewModelScope.launch {
            var photoPath = uri
            if (bitmap != null) {
                try {
                    val app = getApplication<Application>()
                    val photoDir = File(app.cacheDir, "evidence_photos").apply { if (!exists()) mkdirs() }
                    val file = File(photoDir, "photo_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    photoPath = file.absolutePath
                    Log.d("EmergencyViewModel", "Photo evidence cached: $photoPath")
                } catch (e: Exception) {
                    Log.e("EmergencyViewModel", "Error saving photo evidence", e)
                }
            }

            repository.addEvidence(
                type = EvidenceType.PHOTO,
                description = "On-scene photo with GPS coordinates verification",
                uri = photoPath
            )

            // Background upload to Firebase Cloud Storage if file exists
            if (photoPath != null) {
                val f = File(photoPath)
                if (f.exists()) {
                    launch {
                        try {
                            storageService.uploadEvidenceFile(
                                localFile = f,
                                emergencyId = session.value.emergencyId,
                                victimUid = authManager.getEffectiveUid(),
                                type = "PHOTO",
                                latitude = session.value.latitude,
                                longitude = session.value.longitude,
                                actorRole = authManager.activeRole.value.name
                            )
                        } catch (e: Exception) {
                            Log.w("EmergencyViewModel", "Evidence cloud storage upload deferred: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    /**
     * Starts native microphone audio recording using AudioRecorderHelper.
     */
    fun startAudioRecording() {
        if (_isRecordingAudio.value) return
        audioRecorderHelper.startRecording()
        _isRecordingAudio.value = true
        _recordingSeconds.value = 0

        audioRecordingJob = viewModelScope.launch {
            while (_isRecordingAudio.value) {
                delay(1000L)
                _recordingSeconds.value += 1
            }
        }
    }

    /**
     * Stops native audio recording and saves file to evidence store.
     */
    fun stopAudioRecording() {
        if (!_isRecordingAudio.value) return
        val duration = _recordingSeconds.value
        _isRecordingAudio.value = false
        audioRecordingJob?.cancel()
        audioRecordingJob = null
        _recordingSeconds.value = 0

        val audioFile = audioRecorderHelper.stopRecording()
        val path = audioFile?.absolutePath

        repository.addEvidence(
            type = EvidenceType.AUDIO,
            description = "Ambient emergency audio recording ($duration sec)",
            uri = path,
            durationSeconds = duration
        )

        if (audioFile != null && audioFile.exists()) {
            viewModelScope.launch {
                try {
                    storageService.uploadEvidenceFile(
                        localFile = audioFile,
                        emergencyId = session.value.emergencyId,
                        victimUid = authManager.getEffectiveUid(),
                        type = "AUDIO",
                        latitude = session.value.latitude,
                        longitude = session.value.longitude,
                        actorRole = authManager.activeRole.value.name
                    )
                } catch (e: Exception) {
                    Log.w("EmergencyViewModel", "Audio cloud storage upload deferred: ${e.message}")
                }
            }
        }
    }

    /**
     * Attaches recorded video evidence.
     */
    fun addVideoEvidence(uri: Uri? = null) {
        addVideoEvidence(path = uri?.toString(), durationSeconds = 15)
    }

    fun addVideoEvidence(path: String?, durationSeconds: Int = 0) {
        repository.addEvidence(
            type = EvidenceType.VIDEO,
            description = "Incident video clip recorded with location telemetry (${if (durationSeconds > 0) "${durationSeconds}s" else "verified"})",
            uri = path,
            durationSeconds = durationSeconds
        )
    }

    fun login(email: String, pass: String, onResult: (Result<com.example.model.ueri.UserAccount>) -> Unit) {
        viewModelScope.launch {
            val res = authManager.login(email, pass)
            onResult(res)
        }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val res = authManager.sendPasswordResetEmail(email)
            onResult(res)
        }
    }

    // =========================================================================
    // OTP VERIFICATION & FORGOT PASSWORD WORKFLOWS
    // =========================================================================

    fun sendRegistrationOtp(
        target: String,
        channel: OtpChannel,
        displayName: String,
        activity: Activity? = null,
        onResult: (Result<OtpDispatchResult>) -> Unit
    ) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                authManager.sendRegistrationOtp(target, channel, displayName, activity)
            }
            onResult(res)
        }
    }

    fun verifyRegistrationOtp(
        target: String,
        code: String,
        onResult: (Result<Boolean>) -> Unit
    ) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                authManager.verifyRegistrationOtp(target, code)
            }
            onResult(res)
        }
    }

    fun sendForgotPasswordOtp(
        emailOrPhone: String,
        channel: OtpChannel,
        activity: Activity? = null,
        onResult: (Result<OtpDispatchResult>) -> Unit
    ) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                authManager.requestForgotPasswordOtp(emailOrPhone, channel, activity)
            }
            onResult(res)
        }
    }

    fun verifyForgotPasswordOtp(
        emailOrPhone: String,
        code: String,
        onResult: (Result<Boolean>) -> Unit
    ) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                authManager.verifyForgotPasswordOtp(emailOrPhone, code)
            }
            onResult(res)
        }
    }

    fun resetPasswordWithOtp(
        emailOrPhone: String,
        otpCode: String,
        newPassword: String,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                authManager.resetPasswordWithOtp(emailOrPhone, otpCode, newPassword)
            }
            onResult(res)
        }
    }


    fun registerCitizen(
        name: String,
        countryIsoCode: String,
        countryCallingCode: String,
        nationalPhoneNumber: String,
        email: String,
        pass: String,
        otpVerifiedTarget: String? = null,
        onResult: (Result<com.example.model.ueri.UserAccount>) -> Unit
    ) {
        viewModelScope.launch {
            val res = authManager.registerCitizen(
                name = name,
                countryIsoCode = countryIsoCode,
                countryCallingCode = countryCallingCode,
                nationalPhoneNumber = nationalPhoneNumber,
                email = email,
                pass = pass,
                otpVerifiedTarget = otpVerifiedTarget
            )
            onResult(res)
        }
    }

    fun registerCitizen(
        name: String,
        mobileNumber: String,
        email: String,
        pass: String,
        onResult: (Result<com.example.model.ueri.UserAccount>) -> Unit
    ) {
        viewModelScope.launch {
            val res = authManager.registerCitizen(name, mobileNumber, email, pass)
            onResult(res)
        }
    }

    fun registerResponder(
        name: String,
        countryIsoCode: String,
        countryCallingCode: String,
        nationalPhoneNumber: String,
        email: String,
        pass: String,
        responderId: String,
        otpVerifiedTarget: String? = null,
        onResult: (Result<com.example.model.ueri.UserAccount>) -> Unit
    ) {
        viewModelScope.launch {
            val res = authManager.registerResponder(
                name = name,
                countryIsoCode = countryIsoCode,
                countryCallingCode = countryCallingCode,
                nationalPhoneNumber = nationalPhoneNumber,
                email = email,
                pass = pass,
                responderId = responderId,
                otpVerifiedTarget = otpVerifiedTarget
            )
            onResult(res)
        }
    }

    fun registerResponder(
        name: String,
        mobileNumber: String,
        email: String,
        pass: String,
        responderId: String,
        onResult: (Result<com.example.model.ueri.UserAccount>) -> Unit
    ) {
        viewModelScope.launch {
            val res = authManager.registerResponder(name, mobileNumber, email, pass, responderId)
            onResult(res)
        }
    }

    /**
     * Registers and refreshes the FCM device token for authenticated responder and user accounts.
     */
    fun updateFcmToken(token: String) {
        viewModelScope.launch {
            authManager.updateFcmPushToken(token)
        }
    }

    /**
     * Deep-link receiver: selects the emergency incident when a responder taps an FCM notification.
     */
    fun handleEmergencyDeepLink(emergencyId: String) {
        if (emergencyId.isBlank()) return
        Log.d("EmergencyViewModel", "Navigating to deep-linked emergency: $emergencyId")
        repository.loadEmergencyById(emergencyId)
    }

    fun logout() {
        authManager.logout()
    }

    fun checkVerificationStatus(onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            val res = authManager.checkVerificationStatus()
            onResult(res)
        }
    }

    fun approvePendingResponder(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = authManager.approvePendingResponder()
            onResult(res)
        }
    }

    fun setOfflineSimulationMode(offline: Boolean) {
        offlineSosRepository.setOfflineMode(offline)
    }

    fun syncPendingOfflineSosNow() {
        viewModelScope.launch {
            offlineSosRepository.flushPendingSosQueue()
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationUpdatesJob?.cancel()
        locationService.stopLocationUpdates()
        audioRecorderHelper.cleanup()
    }
}

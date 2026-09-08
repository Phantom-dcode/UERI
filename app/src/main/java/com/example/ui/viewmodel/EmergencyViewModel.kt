package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.EmergencyRepository
import com.example.model.EmergencyLog
import com.example.model.EmergencySession
import com.example.model.EvidenceItem
import com.example.model.EvidenceType
import com.example.model.Responder
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class EmergencyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EmergencyRepository
    val locationService: LocationService = LocationService(application)
    private val audioRecorderHelper: AudioRecorderHelper = AudioRecorderHelper(application)
    val backendNotificationService: BackendNotificationService = BackendNotificationService(application)

    val session: StateFlow<EmergencySession>
    val responders: StateFlow<List<Responder>>
    val evidenceList: StateFlow<List<EvidenceItem>>
    val logs: StateFlow<List<EmergencyLog>>
    val isFastDemoMode: StateFlow<Boolean>

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
        repository = EmergencyRepository(database.emergencyDao())

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
        evidenceList = repository.evidenceList.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repository.evidenceList.value
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

    fun responderRespond(responderId: String) {
        repository.responderRespond(responderId)
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
    }

    /**
     * Attaches recorded video evidence.
     */
    fun addVideoEvidence(uri: Uri? = null) {
        val videoPath = uri?.toString()
        repository.addEvidence(
            type = EvidenceType.VIDEO,
            description = "Incident video clip recorded with location telemetry",
            uri = videoPath,
            durationSeconds = 15
        )
    }

    override fun onCleared() {
        super.onCleared()
        locationUpdatesJob?.cancel()
        locationService.stopLocationUpdates()
        audioRecorderHelper.cleanup()
    }
}

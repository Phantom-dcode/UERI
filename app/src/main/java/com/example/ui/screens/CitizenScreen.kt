package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.example.config.EmergencyConfig
import com.example.model.EmergencySession
import com.example.model.EmergencyStatus
import com.example.ui.components.CameraMode
import com.example.ui.components.CameraXCaptureDialog
import com.example.ui.components.EmergencyStatusBadge
import com.example.ui.components.EvidenceCapturePanel
import com.example.ui.components.MapComposable
import com.example.ui.components.TacticalRadarMap
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CleanCanvas
import com.example.ui.theme.CleanWhite
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedDark
import com.example.ui.theme.EmergencyRedGlow
import com.example.ui.theme.EmergencyRedLight
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyGreenDark
import com.example.ui.theme.SafetyGreenLight
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateBorderLight
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextSlate400
import com.example.ui.theme.TextSlate500
import com.example.ui.theme.TextSlate600
import com.example.ui.theme.TextSlate800
import com.example.ui.theme.TextSlate900
import com.example.ui.viewmodel.EmergencyViewModel
import com.example.util.LocationState

@Composable
fun CitizenScreen(
    viewModel: EmergencyViewModel,
    modifier: Modifier = Modifier,
    onOpenLiveCamera: (() -> Unit)? = null
) {
    val session by viewModel.session.collectAsState()
    val responders by viewModel.responders.collectAsState()
    val evidenceList by viewModel.evidenceList.collectAsState()
    val isRecordingAudio by viewModel.isRecordingAudio.collectAsState()
    val recordingSeconds by viewModel.recordingSeconds.collectAsState()
    val locationState by viewModel.locationState.collectAsState()

    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var showCameraDialog by remember { mutableStateOf(false) }
    var cameraDialogMode by remember { mutableStateOf(CameraMode.PHOTO) }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCameraDialog = true
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startAudioRecording()
        }
    }

    // Permission launcher for GPS / Location
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(fineGranted || coarseGranted)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CleanCanvas)
    ) {
        // Clean Minimal Header
        CleanHeader(session = session)

        // Main Scrollable Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Location Status Banner
            LocationStatusBanner(
                locationState = locationState,
                session = session,
                onRequestPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onRefreshLocation = { viewModel.refreshCurrentLocation() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Main Dynamic Status & Trigger Section
            when (session.status) {
                EmergencyStatus.SAFE -> {
                    SafeStandbyView(
                        session = session,
                        onSosClick = { viewModel.triggerSos() },
                        onMarkSafeClick = { viewModel.markSafe() }
                    )
                }
                EmergencyStatus.ACTIVE, EmergencyStatus.RESPONDER_FOUND -> {
                    ActiveEmergencyView(
                        session = session,
                        onMarkSafeClick = { viewModel.markSafe() }
                    )
                }
                EmergencyStatus.RESOLVED -> {
                    ResolvedEmergencyView(
                        session = session,
                        onResetToSafe = { viewModel.resetDemo() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tactical Radar Map Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderLight)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "LIVE SITUATION RADAR",
                                color = TextSlate900,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Dynamic Multi-Hop Geolocation",
                                color = TextSlate500,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = "RADIUS: ${EmergencyConfig.formatDistance(session.radiusMeters)}",
                            color = if (session.status != EmergencyStatus.SAFE) EmergencyRed else BrandBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    MapComposable(
                        session = session,
                        responders = responders,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        hasLocationPermission = viewModel.locationService.hasLocationPermission()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Incident Evidence Capture Panel (Sleek dark card per Design HTML)
            EvidenceCapturePanel(
                evidenceList = evidenceList,
                isRecordingAudio = isRecordingAudio,
                recordingSeconds = recordingSeconds,
                onCapturePhoto = {
                    if (onOpenLiveCamera != null) {
                        onOpenLiveCamera()
                    } else {
                        cameraDialogMode = CameraMode.PHOTO
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            showCameraDialog = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                },
                onOpenLiveCameraPreview = {
                    if (onOpenLiveCamera != null) {
                        onOpenLiveCamera()
                    } else {
                        cameraDialogMode = CameraMode.PHOTO
                        showCameraDialog = true
                    }
                },
                onStartAudioRecord = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.startAudioRecording()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopAudioRecord = {
                    viewModel.stopAudioRecording()
                },
                onCaptureVideo = {
                    cameraDialogMode = CameraMode.VIDEO
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        showCameraDialog = true
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            )

            if (showCameraDialog) {
                CameraXCaptureDialog(
                    initialMode = cameraDialogMode,
                    latitude = session.latitude,
                    longitude = session.longitude,
                    onPhotoCaptured = { bitmap, uri ->
                        showCameraDialog = false
                        viewModel.addPhotoEvidence(bitmap = bitmap, uri = uri)
                    },
                    onVideoCaptured = { uri, duration ->
                        showCameraDialog = false
                        viewModel.addVideoEvidence()
                    },
                    onDismiss = { showCameraDialog = false }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CleanHeader(session: EmergencySession) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp)
            .background(CleanWhite)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Column {
                Text(
                    text = "UERI",
                    color = TextSlate900,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "EMERGENCY INFRASTRUCTURE",
                    color = TextSlate500,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            EmergencyStatusBadge(status = session.status)
        }
    }
}

@Composable
private fun SafeStandbyView(
    session: EmergencySession,
    onSosClick: () -> Unit,
    onMarkSafeClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sosPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sosScale"
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Prominent Red SOS Button with Surface touch handling
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(vertical = 12.dp)
            .size(228.dp)
    ) {
        // Soft Glow Ring
        Box(
            modifier = Modifier
                .size(228.dp)
                .clip(CircleShape)
                .background(EmergencyRedLight.copy(alpha = 0.6f))
        )

        // Main SOS Interactive Button
        Surface(
            onClick = onSosClick,
            modifier = Modifier
                .size(210.dp)
                .scale(pulseScale)
                .testTag("sos_emergency_button"),
            shape = CircleShape,
            color = EmergencyRed,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(8.dp, CleanWhite)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                EmergencyRedGlow,
                                EmergencyRed,
                                EmergencyRedDark
                            )
                        )
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "SOS",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "ONE-TAP TRIGGER",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 2x2 Telemetry Cards (Rounded 3xl clean cards)
    TelemetryGrid(session = session)

    Spacer(modifier = Modifier.height(16.dp))

    // Clean Minimalist "I'm Safe" Outline Button
    OutlinedButton(
        onClick = onMarkSafeClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("safe_standby_button"),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CleanWhite,
            contentColor = SafetyGreenDark
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, SafetyGreen),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = SafetyGreen)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "I'M SAFE",
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = SafetyGreenDark
        )
    }
}

@Composable
private fun ActiveEmergencyView(
    session: EmergencySession,
    onMarkSafeClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = CleanWhite),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, EmergencyRed)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🚨 EMERGENCY BROADCAST",
                        color = EmergencyRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    text = session.emergencyId,
                    color = TextSlate500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Telemetry Grid
            TelemetryGrid(session = session)

            // Active Responder info
            session.activeResponderName?.let { responderName ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE0F2FE))
                        .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "RESPONDER EN ROUTE: $responderName",
                            color = Color(0xFF0369A1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ETA: ${session.activeResponderEtaMinutes ?: 2} min • Distance: ${EmergencyConfig.formatDistance(session.activeResponderDistanceMeters ?: 400.0)}",
                            color = Color(0xFF0284C7),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Prominent I'm Safe Button
            Button(
                onClick = onMarkSafeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("citizen_im_safe_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SafetyGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I'M SAFE (CANCEL SOS)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun ResolvedEmergencyView(
    session: EmergencySession,
    onResetToSafe: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = CleanWhite),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, SafetyGreen)
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafetyGreen, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "EMERGENCY RESOLVED",
                color = TextSlate900,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Victim confirmed safe. Incident session closed.",
                color = TextSlate600,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            TelemetryGrid(session = session)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onResetToSafe,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("reset_safe_button"),
                colors = ButtonDefaults.buttonColors(containerColor = TextSlate900),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("RETURN TO STANDBY", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LocationStatusBanner(
    locationState: LocationState,
    session: EmergencySession,
    onRequestPermission: () -> Unit,
    onRefreshLocation: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = when (locationState) {
                is LocationState.Success -> Color(0xFFF0FDF4)
                is LocationState.PermissionDenied -> Color(0xFFFFFBEB)
                is LocationState.GpsDisabled -> Color(0xFFFEF2F2)
                else -> CleanWhite
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (locationState) {
                is LocationState.Success -> Color(0xFFBBF7D0)
                is LocationState.PermissionDenied -> Color(0xFFFDE68A)
                is LocationState.GpsDisabled -> Color(0xFFFECACA)
                else -> SlateBorderLight
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when (locationState) {
                        is LocationState.Success -> Icons.Default.GpsFixed
                        is LocationState.PermissionDenied -> Icons.Default.GpsOff
                        is LocationState.GpsDisabled -> Icons.Default.GpsOff
                        else -> Icons.Default.LocationSearching
                    },
                    contentDescription = "Location Status Icon",
                    tint = when (locationState) {
                        is LocationState.Success -> SafetyGreenDark
                        is LocationState.PermissionDenied -> AlertAmber
                        is LocationState.GpsDisabled -> EmergencyRed
                        else -> BrandBlue
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    val statusTitle = when (locationState) {
                        is LocationState.Success -> "GPS LIVE • ±${locationState.accuracy.toInt()}m ACCURACY"
                        is LocationState.PermissionDenied -> "OFFLINE MESH FALLBACK (PERMISSION DENIED)"
                        is LocationState.GpsDisabled -> "GPS DISABLED • CELL/WIFI FALLBACK"
                        is LocationState.Loading -> "ACQUIRING GPS LOCK..."
                        is LocationState.Error -> "GPS UNAVAILABLE • MESH ACTIVE"
                        LocationState.Idle -> "LOCATION STANDBY"
                    }
                    Text(
                        text = statusTitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (locationState) {
                            is LocationState.Success -> SafetyGreenDark
                            is LocationState.PermissionDenied -> Color(0xFFB45309)
                            is LocationState.GpsDisabled -> EmergencyRed
                            else -> TextSlate800
                        }
                    )
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.5f", session.latitude)}, ${String.format(java.util.Locale.US, "%.5f", session.longitude)}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSlate600
                    )
                }
            }

            when (locationState) {
                is LocationState.PermissionDenied -> {
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("ENABLE GPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                else -> {
                    IconButton(
                        onClick = onRefreshLocation,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh GPS Location",
                            tint = TextSlate500,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryGrid(session: EmergencySession) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MinimalCard(
                title = "LOCATION FIX",
                value = session.locationStatus,
                valueColor = if (session.locationStatus.contains("GPS") || session.locationStatus == "ACTIVE") BrandBlue else AlertAmber,
                modifier = Modifier.weight(1f),
                hasDot = true
            )
            MinimalCard(
                title = "RESPONDERS",
                value = "${session.respondersCount} Nearby",
                valueColor = TextSlate800,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MinimalCard(
                title = "DYNAMIC RADIUS",
                value = EmergencyConfig.formatDistance(session.radiusMeters),
                valueColor = if (session.status != EmergencyStatus.SAFE) EmergencyRed else TextSlate800,
                modifier = Modifier.weight(1f)
            )
            MinimalCard(
                title = "ACCURACY",
                value = "±${session.locationAccuracyMeters.toInt()}m",
                valueColor = if (session.locationAccuracyMeters <= 15.0f) SafetyGreenDark else TextSlate800,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MinimalCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    hasDot: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CleanWhite)
            .border(1.dp, SlateBorderLight, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = title,
                color = TextSlate400,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasDot) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(valueColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.config.EmergencyConfig
import com.example.model.EmergencySession
import com.example.model.EmergencyStatus
import com.example.model.Responder
import com.example.model.ResponderStatus
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CleanWhite
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyGreenDark
import com.example.ui.theme.SlateBorderLight
import com.example.ui.theme.TextSlate400
import com.example.ui.theme.TextSlate500
import com.example.ui.theme.TextSlate600
import com.example.ui.theme.TextSlate800
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch

enum class MapDisplayMode {
    GOOGLE_MAPS,
    TACTICAL_RADAR
}

/**
 * Checks if a real, valid Google Maps API Key has been configured.
 */
fun isGoogleMapsKeyConfigured(): Boolean {
    return try {
        val key = BuildConfig.MAPS_API_KEY
        key.isNotBlank() &&
            !key.contains("Dummy", ignoreCase = true) &&
            !key.contains("AIzaSyDummy", ignoreCase = true)
    } catch (e: Exception) {
        false
    }
}

/**
 * High-reliability geolocation map interface integrating Google Maps MapView
 * and real-time responder tracking with dynamic geofence radius.
 * Automatically defaults to Tactical Radar when using a placeholder/unauthorized key
 * to guarantee an immediate, flawless operational UI while providing clear guidance on activating Google Maps.
 */
@Composable
fun MapComposable(
    session: EmergencySession,
    responders: List<Responder>,
    modifier: Modifier = Modifier,
    hasLocationPermission: Boolean = true,
    onResponderSelected: ((Responder) -> Unit)? = null
) {
    val isKeyConfigured = remember { isGoogleMapsKeyConfigured() }
    var displayMode by remember {
        mutableStateOf(if (isKeyConfigured) MapDisplayMode.GOOGLE_MAPS else MapDisplayMode.TACTICAL_RADAR)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, SlateBorderLight, RoundedCornerShape(20.dp))
    ) {
        when (displayMode) {
            MapDisplayMode.GOOGLE_MAPS -> {
                GoogleMapViewComponent(
                    session = session,
                    responders = responders,
                    modifier = Modifier.fillMaxSize(),
                    hasLocationPermission = hasLocationPermission,
                    isKeyConfigured = isKeyConfigured,
                    onResponderSelected = onResponderSelected,
                    onSwitchToRadar = { displayMode = MapDisplayMode.TACTICAL_RADAR }
                )
            }
            MapDisplayMode.TACTICAL_RADAR -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    TacticalRadarMap(
                        session = session,
                        responders = responders,
                        modifier = Modifier.fillMaxSize(),
                        onResponderSelected = onResponderSelected
                    )

                    // Mode switch badge and Google Maps launcher
                    Surface(
                        onClick = { displayMode = MapDisplayMode.GOOGLE_MAPS },
                        shape = RoundedCornerShape(12.dp),
                        color = CleanWhite.copy(alpha = 0.95f),
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, SlateBorderLight),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .testTag("switch_to_google_maps_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Switch to Google Maps",
                                tint = BrandBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GOOGLE MAPS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandBlue
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Google Maps MapView component displaying:
 * - Dynamic geofence coverage circle with live expanding radius
 * - Incident epicenter marker
 * - Real-time responder location pins with role-based colors, ETA, and distance
 * - Interactive camera controls and map layer selector
 */
@Composable
fun GoogleMapViewComponent(
    session: EmergencySession,
    responders: List<Responder>,
    modifier: Modifier = Modifier,
    hasLocationPermission: Boolean = true,
    isKeyConfigured: Boolean = true,
    onResponderSelected: ((Responder) -> Unit)? = null,
    onSwitchToRadar: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val incidentLatLng = remember(session.latitude, session.longitude) {
        LatLng(session.latitude, session.longitude)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(incidentLatLng, 14.5f)
    }

    var selectedMapType by remember { mutableStateOf(MapType.NORMAL) }
    var selectedResponder by remember { mutableStateOf<Responder?>(null) }
    var showApiKeyInfoDialog by remember { mutableStateOf(!isKeyConfigured) }

    // Smoothly animate camera when incident location updates or broadcast radius expands
    LaunchedEffect(session.latitude, session.longitude, session.radiusMeters) {
        val targetZoom = when {
            session.radiusMeters <= 750.0 -> 15.0f
            session.radiusMeters <= 1500.0 -> 14.0f
            session.radiusMeters <= 3000.0 -> 13.0f
            session.radiusMeters <= 6000.0 -> 12.0f
            session.radiusMeters <= 12000.0 -> 11.0f
            session.radiusMeters <= 25000.0 -> 10.0f
            session.radiusMeters <= 50000.0 -> 9.0f
            else -> 8.0f
        }
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(session.latitude, session.longitude),
                targetZoom
            )
        )
    }

    val mapProperties = remember(hasLocationPermission, selectedMapType) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapType = selectedMapType,
            isTrafficEnabled = true
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = true,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = true
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Core GoogleMap View
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapClick = {
                selectedResponder = null
            }
        ) {
            // 1. Dynamic Geofence Broadcast Circle (Expands in real-time)
            if (session.status == EmergencyStatus.ACTIVE || session.status == EmergencyStatus.RESPONDER_FOUND) {
                Circle(
                    center = incidentLatLng,
                    radius = session.radiusMeters,
                    strokeColor = EmergencyRed,
                    strokeWidth = 4f,
                    fillColor = EmergencyRed.copy(alpha = 0.12f)
                )
            }

            // 2. Incident Epicenter Marker
            val incidentMarkerState = rememberMarkerState(
                key = "incident_${session.id}",
                position = incidentLatLng
            )
            Marker(
                state = incidentMarkerState,
                title = "EMERGENCY: ${session.emergencyId}",
                snippet = "Priority: ${session.priority} • Radius: ${EmergencyConfig.formatDistance(session.radiusMeters)}",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )

            // 3. Real-Time Responder Markers
            responders.forEach { responder ->
                val responderMarkerState = rememberMarkerState(
                    key = "resp_${responder.id}",
                    position = LatLng(responder.latitude, responder.longitude)
                )
                LaunchedEffect(responder.latitude, responder.longitude) {
                    responderMarkerState.position = LatLng(responder.latitude, responder.longitude)
                }

                val markerHue = when (responder.status) {
                    ResponderStatus.RESPONDING -> BitmapDescriptorFactory.HUE_AZURE
                    ResponderStatus.ALERTED -> BitmapDescriptorFactory.HUE_ORANGE
                    ResponderStatus.AVAILABLE -> BitmapDescriptorFactory.HUE_GREEN
                    ResponderStatus.RESOLVED -> BitmapDescriptorFactory.HUE_VIOLET
                }

                Marker(
                    state = responderMarkerState,
                    title = "${responder.name} (${responder.role})",
                    snippet = "ETA: ${responder.etaMinutes}m • Dist: ${EmergencyConfig.formatDistance(responder.distanceMeters)} • ${responder.status.label}",
                    icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                    onClick = {
                        selectedResponder = responder
                        onResponderSelected?.invoke(responder)
                        false
                    }
                )
            }
        }

        // Top Status Header Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Status Capsule
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CleanWhite.copy(alpha = 0.95f),
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, SlateBorderLight)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(SafetyGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GOOGLE MAPS • ${responders.count { it.status == ResponderStatus.RESPONDING || it.status == ResponderStatus.ALERTED }} UNITS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = TextSlate800
                    )
                }
            }

            // Quick Layer & Radar Switcher
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Map Type Toggle (Normal vs Satellite)
                Surface(
                    onClick = {
                        selectedMapType = if (selectedMapType == MapType.NORMAL) {
                            MapType.HYBRID
                        } else {
                            MapType.NORMAL
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = CleanWhite.copy(alpha = 0.95f),
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, SlateBorderLight),
                    modifier = Modifier.testTag("toggle_satellite_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Map Style",
                            tint = BrandBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedMapType == MapType.HYBRID) "SAT" else "MAP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue
                        )
                    }
                }

                // Switch to Tactical Radar View
                if (onSwitchToRadar != null) {
                    Surface(
                        onClick = onSwitchToRadar,
                        shape = RoundedCornerShape(12.dp),
                        color = CleanWhite.copy(alpha = 0.95f),
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, SlateBorderLight),
                        modifier = Modifier.testTag("switch_to_radar_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = "Tactical Radar",
                                tint = TextSlate800,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "RADAR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSlate800
                            )
                        }
                    }
                }
            }
        }

        // Floating Re-center Action Button
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = {
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(incidentLatLng, 15f)
                        )
                    }
                },
                shape = CircleShape,
                color = CleanWhite.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, SlateBorderLight),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("recenter_map_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Re-center on incident",
                        tint = EmergencyRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Active Selected Responder Info Card
        selectedResponder?.let { responder ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.85f)
                    .padding(10.dp)
                    .shadow(4.dp, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SlateBorderLight)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                when (responder.status) {
                                    ResponderStatus.RESPONDING -> Color(0xFFE0F2FE)
                                    ResponderStatus.ALERTED -> Color(0xFFFEF3C7)
                                    else -> Color(0xFFDCFCE7)
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (responder.role) {
                                "Paramedic" -> Icons.Default.LocalHospital
                                "Police" -> Icons.Default.Shield
                                else -> Icons.Default.DirectionsCar
                            },
                            contentDescription = null,
                            tint = when (responder.status) {
                                ResponderStatus.RESPONDING -> BrandBlue
                                ResponderStatus.ALERTED -> AlertAmber
                                else -> SafetyGreenDark
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = responder.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextSlate800
                        )
                        Text(
                            text = "${responder.role} • ETA: ${responder.etaMinutes}m • ${EmergencyConfig.formatDistance(responder.distanceMeters)}",
                            fontSize = 11.sp,
                            color = TextSlate500
                        )
                    }

                    Text(
                        text = responder.status.label,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        color = when (responder.status) {
                            ResponderStatus.RESPONDING -> BrandBlue
                            ResponderStatus.ALERTED -> AlertAmber
                            else -> SafetyGreenDark
                        }
                    )
                }
            }
        }

        // Informative guidance banner when Google Maps API key is still using placeholder
        if (!isKeyConfigured && showApiKeyInfoDialog) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp)
                    .shadow(6.dp, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = EmergencyRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Maps API Key Required",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = EmergencyRed
                            )
                        }
                        Text(
                            text = "Dismiss",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSlate500,
                            modifier = Modifier
                                .clickable { showApiKeyInfoDialog = false }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Google Maps requires a valid MAPS_API_KEY from Google Cloud Console added into AI Studio Secrets. In the meantime, use Tactical Radar for full offline vector cartography.",
                        fontSize = 11.sp,
                        color = TextSlate600,
                        lineHeight = 15.sp
                    )

                    if (onSwitchToRadar != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onSwitchToRadar,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, EmergencyRed),
                            contentPadding = PaddingValues(vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = EmergencyRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SWITCH TO TACTICAL RADAR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmergencyRed
                            )
                        }
                    }
                }
            }
        }
    }
}

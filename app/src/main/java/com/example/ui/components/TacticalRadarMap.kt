package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.theme.TextSlate500
import com.example.ui.theme.TextSlate800
import kotlin.math.cos
import kotlin.math.sin

enum class MapLayerType {
    MAP,
    SATELLITE,
    TERRAIN
}

/**
 * Realistic Google Maps View for Real-World Emergency Situations.
 * Features realistic map cartography: road hierarchy, riverways, city parks,
 * Google Maps pin styling, dynamic radius circles, and responder vehicle markers.
 */
@Composable
fun TacticalRadarMap(
    session: EmergencySession,
    responders: List<Responder>,
    modifier: Modifier = Modifier,
    onResponderSelected: ((Responder) -> Unit)? = null
) {
    var zoomLevel by remember { mutableFloatStateOf(1.2f) } // 0.8f to 3.0f
    var mapLayer by remember { mutableStateOf(MapLayerType.MAP) }
    var selectedResponder by remember { mutableStateOf<Responder?>(null) }

    // Smooth animation for emergency radius expansion
    val animatedRadiusMeters by animateFloatAsState(
        targetValue = session.radiusMeters.toFloat(),
        animationSpec = tween(durationMillis = 800, easing = LinearEasing),
        label = "radiusAnim"
    )

    // Beacon pulse
    val infiniteTransition = rememberInfiniteTransition(label = "gpsPulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (mapLayer == MapLayerType.SATELLITE) Color(0xFF19232D) else Color(0xFFF4F3F0))
            .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(20.dp))
    ) {
        // High-Performance Vector Canvas Map Surface (Zero font layout allocations in onDraw)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(responders, zoomLevel) {
                    detectTapGestures { tapOffset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxDisplayRadiusMeters = 4000.0 / zoomLevel
                        val minDim = minOf(size.width, size.height).toFloat()
                        val pixelsPerMeter = (minDim * 0.45f) / maxDisplayRadiusMeters.toFloat()

                        // Check if user tapped a responder marker
                        val tapped = responders.firstOrNull { resp ->
                            val (rx, ry) = calculateResponderPixelOffset(
                                session.latitude, session.longitude,
                                resp.latitude, resp.longitude,
                                center, pixelsPerMeter
                            )
                            val distance = (Offset(rx, ry) - tapOffset).getDistance()
                            distance <= 36.dp.toPx()
                        }

                        selectedResponder = tapped
                        if (tapped != null) {
                            onResponderSelected?.invoke(tapped)
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxDisplayRadiusMeters = 4000.0 / zoomLevel
            val pixelsPerMeter = (size.minDimension * 0.45f) / maxDisplayRadiusMeters.toFloat()

            // 1. Draw Base Cartography (Water, Parks, Urban blocks)
            drawRealCartography(size.width, size.height, center, pixelsPerMeter, mapLayer)

            // 2. Draw Road Network (Highways, Arterials, Local Streets)
            drawRealisticRoadNetwork(size.width, size.height, center, pixelsPerMeter, mapLayer)

            // 3. Draw Dynamic Emergency Broadcast Radius Overlay
            if (session.status != EmergencyStatus.SAFE && animatedRadiusMeters > 0) {
                drawDynamicEmergencyRadius(
                    center = center,
                    radiusMeters = animatedRadiusMeters,
                    pixelsPerMeter = pixelsPerMeter,
                    pulseProgress = pulseProgress,
                    status = session.status
                )
            }

            // 4. Draw Responders
            drawGoogleResponders(
                victimLat = session.latitude,
                victimLon = session.longitude,
                responders = responders,
                center = center,
                pixelsPerMeter = pixelsPerMeter,
                selectedResponder = selectedResponder
            )

            // 5. Draw Google Maps Pin / Live Citizen Location
            drawGoogleLocationPin(
                center = center,
                status = session.status,
                pulseProgress = pulseProgress,
                accuracyMeters = session.locationAccuracyMeters,
                pixelsPerMeter = pixelsPerMeter
            )
        }

        // Top Floating Address & Layer Bar (Google Maps Search Card style)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth()
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(CleanWhite)
                .border(0.5.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (session.status != EmergencyStatus.SAFE) EmergencyRed else SafetyGreenDark)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Real-Time Emergency Geolocation",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSlate800
                    )
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.5f", session.latitude)}, ${String.format(java.util.Locale.US, "%.5f", session.longitude)} • ±${session.locationAccuracyMeters.toInt()}m",
                        fontSize = 9.sp,
                        color = TextSlate500,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Layer Selector (Map / Satellite / Terrain)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MapLayerChip(
                    label = "Map",
                    isSelected = mapLayer == MapLayerType.MAP,
                    onClick = { mapLayer = MapLayerType.MAP }
                )
                MapLayerChip(
                    label = "Satellite",
                    isSelected = mapLayer == MapLayerType.SATELLITE,
                    onClick = { mapLayer = MapLayerType.SATELLITE }
                )
            }
        }

        // Standard Google Maps Floating Zoom & Recenter Controls (Bottom Right)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { zoomLevel = (zoomLevel * 1.3f).coerceAtMost(3.0f) },
                modifier = Modifier
                    .size(36.dp)
                    .shadow(2.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(CleanWhite)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = TextSlate800, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = { zoomLevel = (zoomLevel / 1.3f).coerceAtLeast(0.6f) },
                modifier = Modifier
                    .size(36.dp)
                    .shadow(2.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(CleanWhite)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = TextSlate800, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = { zoomLevel = 1.2f },
                modifier = Modifier
                    .size(36.dp)
                    .shadow(2.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(CleanWhite)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location", tint = BrandBlue, modifier = Modifier.size(18.dp))
            }
        }

        // Compass Rose (Top Left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 64.dp, start = 12.dp)
                .size(32.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(CleanWhite),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Navigation, contentDescription = "Compass", tint = EmergencyRed, modifier = Modifier.size(18.dp))
        }

        // Bottom Left "Google" watermark & Radius badge
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Google",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (mapLayer == MapLayerType.SATELLITE) Color.White.copy(alpha = 0.7f) else Color(0xFF5F6368),
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Radius: ${EmergencyConfig.formatDistance(session.radiusMeters)}",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (session.status != EmergencyStatus.SAFE) EmergencyRed else BrandBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(CleanWhite.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun MapLayerChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) BrandBlue else Color(0xFFF3F4F6))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) CleanWhite else TextSlate800
        )
    }
}

// ----------------------------------------------------
// Drawing Realistic Google Maps Cartography
// ----------------------------------------------------

private fun DrawScope.drawRealCartography(
    width: Float,
    height: Float,
    center: Offset,
    pixelsPerMeter: Float,
    layer: MapLayerType
) {
    val waterColor = if (layer == MapLayerType.SATELLITE) Color(0xFF102A43) else Color(0xFFAAD3DF)
    val parkColor = if (layer == MapLayerType.SATELLITE) Color(0xFF1E3A2F) else Color(0xFFCBE6A3)
    val urbanColor = if (layer == MapLayerType.SATELLITE) Color(0xFF1A222D) else Color(0xFFF0EFEA)

    // Urban building blocks
    drawRect(urbanColor)

    // Curving River / Lake
    val riverPath = Path().apply {
        moveTo(-20f, height * 0.25f)
        cubicTo(
            width * 0.35f, height * 0.15f,
            width * 0.45f, height * 0.65f,
            width + 20f, height * 0.75f
        )
        lineTo(width + 20f, height * 0.88f)
        cubicTo(
            width * 0.45f, height * 0.78f,
            width * 0.35f, height * 0.28f,
            -20f, height * 0.38f
        )
        close()
    }
    drawPath(riverPath, color = waterColor, style = Fill)

    // City Parks / Green Spaces
    drawRoundRect(
        color = parkColor,
        topLeft = Offset(width * 0.08f, height * 0.55f),
        size = Size(width * 0.28f, height * 0.32f),
        cornerRadius = CornerRadius(16f, 16f)
    )

    drawRoundRect(
        color = parkColor,
        topLeft = Offset(width * 0.65f, height * 0.10f),
        size = Size(width * 0.28f, height * 0.22f),
        cornerRadius = CornerRadius(12f, 12f)
    )
}

private fun DrawScope.drawRealisticRoadNetwork(
    width: Float,
    height: Float,
    center: Offset,
    pixelsPerMeter: Float,
    layer: MapLayerType
) {
    val isSatellite = layer == MapLayerType.SATELLITE
    val roadCasing = if (isSatellite) Color(0xFF334155) else Color(0xFFCBD5E1)
    val localRoadColor = if (isSatellite) Color(0xFF475569) else Color(0xFFFFFFFF)
    val arterialRoadColor = if (isSatellite) Color(0xFF64748B) else Color(0xFFFFE082)
    val highwayRoadColor = if (isSatellite) Color(0xFFD97706) else Color(0xFFFFA726)

    // Grid of local streets (East-West & North-South)
    val step = 64f
    var y = center.y % step
    while (y < height) {
        drawLine(roadCasing, Offset(0f, y), Offset(width, y), strokeWidth = 5f)
        drawLine(localRoadColor, Offset(0f, y), Offset(width, y), strokeWidth = 3f)
        y += step
    }

    var x = center.x % step
    while (x < width) {
        drawLine(roadCasing, Offset(x, 0f), Offset(x, height), strokeWidth = 5f)
        drawLine(localRoadColor, Offset(x, 0f), Offset(x, height), strokeWidth = 3f)
        x += step
    }

    // Main Arterial Avenue (Diagonal Avenue)
    drawLine(roadCasing, Offset(0f, height * 0.85f), Offset(width, height * 0.15f), strokeWidth = 9f)
    drawLine(arterialRoadColor, Offset(0f, height * 0.85f), Offset(width, height * 0.15f), strokeWidth = 6.5f)

    // Major Highway with dual lane
    drawLine(roadCasing, Offset(center.x - 20f, 0f), Offset(center.x - 20f, height), strokeWidth = 11f)
    drawLine(highwayRoadColor, Offset(center.x - 20f, 0f), Offset(center.x - 20f, height), strokeWidth = 8f)
}

private fun DrawScope.drawDynamicEmergencyRadius(
    center: Offset,
    radiusMeters: Float,
    pixelsPerMeter: Float,
    pulseProgress: Float,
    status: EmergencyStatus
) {
    val pxRadius = radiusMeters * pixelsPerMeter
    val baseColor = if (status == EmergencyStatus.ACTIVE) EmergencyRed else BrandBlue

    // Translucent coverage zone
    drawCircle(
        color = baseColor.copy(alpha = 0.14f),
        radius = pxRadius,
        center = center
    )

    // Border stroke with dash
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
    drawCircle(
        color = baseColor.copy(alpha = 0.85f),
        radius = pxRadius,
        center = center,
        style = Stroke(width = 2.5f, pathEffect = dash)
    )

    // Expanding pulse ripple
    val rippleRadius = pxRadius + (pulseProgress * 28f)
    val rippleAlpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.45f
    drawCircle(
        color = baseColor.copy(alpha = rippleAlpha),
        radius = rippleRadius,
        center = center,
        style = Stroke(width = 1.5f)
    )
}

private fun DrawScope.drawGoogleLocationPin(
    center: Offset,
    status: EmergencyStatus,
    pulseProgress: Float,
    accuracyMeters: Float,
    pixelsPerMeter: Float
) {
    val isEmergency = status != EmergencyStatus.SAFE
    val color = if (isEmergency) EmergencyRed else BrandBlue

    // Accuracy Circle Halo (Standard Google Maps blue halo)
    val accuracyPx = (accuracyMeters * pixelsPerMeter).coerceIn(16f, 90f)
    drawCircle(
        color = color.copy(alpha = 0.12f),
        radius = accuracyPx,
        center = center
    )
    drawCircle(
        color = color.copy(alpha = 0.35f),
        radius = accuracyPx,
        center = center,
        style = Stroke(width = 1f)
    )

    // Pulse animation
    val pulseSize = 12.dp.toPx() + (pulseProgress * 24.dp.toPx())
    val pulseAlpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.6f
    drawCircle(
        color = color.copy(alpha = pulseAlpha),
        radius = pulseSize,
        center = center
    )

    // White outer border ring
    drawCircle(
        color = CleanWhite,
        radius = 9.dp.toPx(),
        center = center
    )

    // Solid Google Maps location dot
    drawCircle(
        color = color,
        radius = 7.dp.toPx(),
        center = center
    )

    // White center pinpoint
    drawCircle(
        color = CleanWhite,
        radius = 2.5.dp.toPx(),
        center = center
    )
}

private fun DrawScope.drawGoogleResponders(
    victimLat: Double,
    victimLon: Double,
    responders: List<Responder>,
    center: Offset,
    pixelsPerMeter: Float,
    selectedResponder: Responder?
) {
    for (resp in responders) {
        val (rx, ry) = calculateResponderPixelOffset(
            victimLat, victimLon,
            resp.latitude, resp.longitude,
            center, pixelsPerMeter
        )

        // Safety check: Only draw if within or reasonably near the visible canvas boundaries
        if (rx < -40f || rx > size.width + 40f || ry < -40f || ry > size.height + 40f) {
            continue
        }

        val pos = Offset(rx, ry)

        val pinColor = when (resp.status) {
            ResponderStatus.RESPONDING -> SafetyGreenDark
            ResponderStatus.ALERTED -> AlertAmber
            ResponderStatus.AVAILABLE -> BrandBlue
            ResponderStatus.RESOLVED -> Color(0xFF64748B)
        }

        val isSelected = selectedResponder?.id == resp.id

        // Selection ring
        if (isSelected) {
            drawCircle(
                color = BrandBlue.copy(alpha = 0.3f),
                radius = 18.dp.toPx(),
                center = pos
            )
        }

        // White base shadow
        drawCircle(
            color = CleanWhite,
            radius = 11.dp.toPx(),
            center = pos
        )

        // Responder Core Pin
        drawCircle(
            color = pinColor,
            radius = 9.dp.toPx(),
            center = pos
        )

        // Inner white dot
        drawCircle(
            color = CleanWhite,
            radius = 3.5.dp.toPx(),
            center = pos
        )

        // ETA Callout Pin Top Indicator
        val bubbleWidth = 24.dp.toPx()
        val bubbleHeight = 12.dp.toPx()
        val bubbleLeft = pos.x - bubbleWidth / 2f
        val bubbleTop = pos.y - 20.dp.toPx()

        drawRoundRect(
            color = CleanWhite,
            topLeft = Offset(bubbleLeft, bubbleTop),
            size = Size(bubbleWidth, bubbleHeight),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawRoundRect(
            color = pinColor,
            topLeft = Offset(bubbleLeft, bubbleTop),
            size = Size(bubbleWidth, bubbleHeight),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(1.5f)
        )
    }
}

private fun calculateResponderPixelOffset(
    victimLat: Double,
    victimLon: Double,
    respLat: Double,
    respLon: Double,
    center: Offset,
    pixelsPerMeter: Float
): Pair<Float, Float> {
    val latMeters = (respLat - victimLat) * 111320.0
    val lonMeters = (respLon - victimLon) * (111320.0 * cos(Math.toRadians(victimLat)))

    val pxX = center.x + (lonMeters * pixelsPerMeter).toFloat()
    val pxY = center.y - (latMeters * pixelsPerMeter).toFloat()

    return Pair(pxX, pxY)
}

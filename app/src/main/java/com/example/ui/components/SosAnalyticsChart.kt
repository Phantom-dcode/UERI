package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.EmergencyConfig
import com.example.model.EmergencyStatus
import com.example.model.GeographicSectorStat
import com.example.model.Sos24hAnalytics
import com.example.model.SosFrequencyBucket
import com.example.model.SosGeoIncidentPoint
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CleanWhite
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SlateBorderLight
import com.example.ui.theme.TextSlate400
import com.example.ui.theme.TextSlate500
import com.example.ui.theme.TextSlate600
import com.example.ui.theme.TextSlate800
import com.example.ui.theme.TextSlate900
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

enum class FrequencyChartStyle {
    AREA,
    BAR
}

/**
 * Interactive 24-Hour SOS Call Frequency & Geographic Distribution Data Visualization Card
 * for the UERI Control Hub.
 */
@Composable
fun SosAnalyticsDashboardCard(
    analytics: Sos24hAnalytics,
    centerLat: Double = EmergencyConfig.DEFAULT_LATITUDE,
    centerLon: Double = EmergencyConfig.DEFAULT_LONGITUDE,
    modifier: Modifier = Modifier
) {
    var chartStyle by remember { mutableStateOf(FrequencyChartStyle.AREA) }
    var selectedBucketIndex by remember(analytics.frequencyBuckets.size) {
        mutableIntStateOf((analytics.frequencyBuckets.size - 1).coerceAtLeast(0))
    }
    var selectedSectorId by remember { mutableStateOf<String?>(null) }

    val buckets = analytics.frequencyBuckets
    val activeBucket = buckets.getOrNull(selectedBucketIndex) ?: buckets.lastOrNull()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp))
            .testTag("control_hub_analytics_card"),
        colors = CardDefaults.cardColors(containerColor = CleanWhite),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, SlateBorderLight)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Header & Chart Style Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "24-Hour SOS Analytics",
                            tint = BrandBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "24-HOUR SOS TELEMETRY & GEO ANALYTICS",
                            color = TextSlate900,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp
                        )
                        Text(
                            text = "Real-time call frequency & spatial sector distribution",
                            color = TextSlate500,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Area vs Bar style toggle buttons
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(1.dp, SlateBorderLight, RoundedCornerShape(10.dp))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChartModeChip(
                        label = "Area",
                        icon = Icons.Default.ShowChart,
                        selected = chartStyle == FrequencyChartStyle.AREA,
                        onClick = { chartStyle = FrequencyChartStyle.AREA },
                        testTag = "analytics_mode_area"
                    )
                    ChartModeChip(
                        label = "Bar",
                        icon = Icons.Default.BarChart,
                        selected = chartStyle == FrequencyChartStyle.BAR,
                        onClick = { chartStyle = FrequencyChartStyle.BAR },
                        testTag = "analytics_mode_bar"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 24h Summary KPI Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsMiniStat(
                    label = "24H CALLS",
                    value = "${analytics.totalCalls24h}",
                    accentColor = BrandBlue,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMiniStat(
                    label = "CRITICAL SOS",
                    value = "${analytics.criticalCalls24h}",
                    accentColor = EmergencyRed,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMiniStat(
                    label = "PEAK WINDOW",
                    value = "${analytics.peakWindowCalls} calls",
                    subValue = analytics.peakWindowLabel,
                    accentColor = AlertAmber,
                    modifier = Modifier.weight(1.2f)
                )
                AnalyticsMiniStat(
                    label = "AVG ETA",
                    value = String.format(Locale.US, "%.1fm", analytics.avgResponseMinutes),
                    accentColor = SafetyGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SECTION 1: 24-HOUR SOS CALL FREQUENCY CHART (Recharts-style interactive Area/Bar)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "SOS CALL FREQUENCY (LAST 24 HOURS)",
                    color = TextSlate800,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LegendDot(color = EmergencyRed, label = "Critical")
                    LegendDot(color = BrandBlue, label = "Total / Standard")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Tooltip Banner for selected 2-hour bucket
            if (activeBucket != null) {
                SelectedBucketTooltipBar(bucket = activeBucket)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Recharts-style Canvas Frequency Chart
            SosFrequencyCanvasChart(
                buckets = buckets,
                chartStyle = chartStyle,
                selectedIndex = selectedBucketIndex,
                onSelectBucket = { idx -> selectedBucketIndex = idx },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp)
                    .testTag("sos_frequency_24h_chart")
            )

            // X-Axis Time Labels
            if (buckets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val labelIndices = listOf(0, 2, 4, 6, 8, 10, 11).filter { it < buckets.size }.distinct()
                    labelIndices.forEach { idx ->
                        val isSelected = idx == selectedBucketIndex
                        Text(
                            text = if (idx == buckets.lastIndex) "NOW" else buckets[idx].timeLabel,
                            color = if (isSelected) BrandBlue else TextSlate400,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SlateBorderLight)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 2: GEOGRAPHIC DISTRIBUTION OF SOS CALLS (24H)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Geographic Distribution",
                        tint = EmergencyRed,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GEOGRAPHIC DISTRIBUTION BY SECTOR (24H)",
                        color = TextSlate800,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "${analytics.geoIncidentPoints.size} Geo-Tagged SOS",
                    color = TextSlate500,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Side-by-side / Responsive Spatial Heatmap + Sector Bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sos_geographic_distribution_section"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 2D Spatial Quadrant Density Radar / Heatmap
                GeographicSectorHeatmapCanvas(
                    points = analytics.geoIncidentPoints,
                    sectors = analytics.geographicSectors,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    selectedSectorId = selectedSectorId,
                    onSectorTapped = { tappedId ->
                        selectedSectorId = if (selectedSectorId == tappedId) null else tappedId
                    },
                    modifier = Modifier
                        .size(145.dp)
                        .testTag("sos_geo_heatmap_canvas")
                )

                // Regional Sector Breakdown Bars
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    analytics.geographicSectors.forEach { sector ->
                        SectorDistributionRow(
                            sector = sector,
                            isSelected = selectedSectorId == sector.sectorId,
                            onClick = {
                                selectedSectorId =
                                    if (selectedSectorId == sector.sectorId) null else sector.sectorId
                            }
                        )
                    }
                }
            }

            // Selected Geographic Sector Detail Banner
            val activeSector = analytics.geographicSectors.find { it.sectorId == selectedSectorId }
            AnimatedVisibility(visible = activeSector != null) {
                if (activeSector != null) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, BrandBlue.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${activeSector.sectorName} (${activeSector.shortCode})",
                                    color = TextSlate900,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "Center: %.4f, %.4f • Avg Broadcast Radius: %s",
                                        activeSector.centerLat,
                                        activeSector.centerLon,
                                        EmergencyConfig.formatDistance(activeSector.avgRadiusMeters)
                                    ),
                                    color = TextSlate500,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "${activeSector.totalCalls} SOS (${activeSector.criticalCalls} Crit)",
                                color = if (activeSector.criticalCalls > 0) EmergencyRed else BrandBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CleanWhite else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) BrandBlue else TextSlate500,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = if (selected) TextSlate900 else TextSlate500,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun AnalyticsMiniStat(
    label: String,
    value: String,
    subValue: String? = null,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, SlateBorderLight, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = TextSlate400,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        if (subValue != null) {
            Text(
                text = subValue,
                color = TextSlate500,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = TextSlate600,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SelectedBucketTooltipBar(bucket: SosFrequencyBucket) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, SlateBorderLight, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (bucket.criticalCalls > 0) EmergencyRed else BrandBlue)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Window ${bucket.windowRangeLabel}",
                color = TextSlate900,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total: ${bucket.totalCalls}",
                color = BrandBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Crit: ${bucket.criticalCalls}",
                color = EmergencyRed,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = String.format(Locale.US, "ETA %.1fm", bucket.avgResponseMinutes),
                color = SafetyGreen,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SosFrequencyCanvasChart(
    buckets: List<SosFrequencyBucket>,
    chartStyle: FrequencyChartStyle,
    selectedIndex: Int,
    onSelectBucket: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "chartReveal"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, SlateBorderLight, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(buckets.size) {
                    detectTapGestures { tapOffset ->
                        if (buckets.isNotEmpty() && size.width > 0) {
                            val stepX = size.width / buckets.size.toFloat()
                            val tappedIdx = (tapOffset.x / stepX).toInt().coerceIn(0, buckets.lastIndex)
                            onSelectBucket(tappedIdx)
                        }
                    }
                }
        ) {
            if (buckets.isEmpty()) return@Canvas

            val chartWidth = size.width
            val chartHeight = size.height
            val maxCalls = (buckets.maxOfOrNull { it.totalCalls } ?: 1).coerceAtLeast(4)

            // Draw horizontal Recharts-style dashed reference grid lines
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            val gridRows = 4
            for (r in 0..gridRows) {
                val y = chartHeight * (r.toFloat() / gridRows.toFloat())
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (r == gridRows) null else dashEffect
                )
            }

            val count = buckets.size
            val slotWidth = chartWidth / count.toFloat()

            // Selected bucket vertical crosshair line
            if (selectedIndex in buckets.indices) {
                val selectedX = slotWidth * selectedIndex + slotWidth / 2f
                drawLine(
                    color = BrandBlue.copy(alpha = 0.35f),
                    start = Offset(selectedX, 0f),
                    end = Offset(selectedX, chartHeight),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dashEffect
                )
            }

            if (chartStyle == FrequencyChartStyle.BAR) {
                // Stacked / Grouped Bar Visualization
                val barWidth = (slotWidth * 0.56f).coerceAtLeast(6.dp.toPx())
                buckets.forEachIndexed { idx, bucket ->
                    val centerX = slotWidth * idx + slotWidth / 2f
                    val totalRatio = (bucket.totalCalls.toFloat() / maxCalls.toFloat()) * animProgress
                    val critRatio = (bucket.criticalCalls.toFloat() / maxCalls.toFloat()) * animProgress

                    val totalBarHeight = (chartHeight * totalRatio).coerceAtLeast(3.dp.toPx())
                    val critBarHeight = chartHeight * critRatio

                    val isSelected = idx == selectedIndex
                    // Background highlight pill for selected slot
                    if (isSelected) {
                        drawRoundRect(
                            color = BrandBlue.copy(alpha = 0.08f),
                            topLeft = Offset(centerX - slotWidth * 0.42f, 0f),
                            size = Size(slotWidth * 0.84f, chartHeight),
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                    }

                    // Total / Standard bar
                    drawRoundRect(
                        color = if (isSelected) BrandBlue else BrandBlue.copy(alpha = 0.72f),
                        topLeft = Offset(centerX - barWidth / 2f, chartHeight - totalBarHeight),
                        size = Size(barWidth, totalBarHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )

                    // Critical SOS overlay bar at bottom of stack
                    if (bucket.criticalCalls > 0) {
                        drawRoundRect(
                            color = EmergencyRed,
                            topLeft = Offset(centerX - barWidth / 2f, chartHeight - critBarHeight),
                            size = Size(barWidth, critBarHeight),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                    }
                }
            } else {
                // Smooth Recharts-style Area + Cubic Bezier Spline for Total & Critical SOS calls
                val totalPoints = buckets.mapIndexed { idx, b ->
                    val x = slotWidth * idx + slotWidth / 2f
                    val ratio = (b.totalCalls.toFloat() / maxCalls.toFloat()) * animProgress
                    val y = chartHeight - (chartHeight * 0.88f * ratio) - 4.dp.toPx()
                    Offset(x, y)
                }

                val critPoints = buckets.mapIndexed { idx, b ->
                    val x = slotWidth * idx + slotWidth / 2f
                    val ratio = (b.criticalCalls.toFloat() / maxCalls.toFloat()) * animProgress
                    val y = chartHeight - (chartHeight * 0.88f * ratio) - 4.dp.toPx()
                    Offset(x, y)
                }

                fun buildSmoothPath(pts: List<Offset>): Path {
                    val path = Path()
                    if (pts.isEmpty()) return path
                    path.moveTo(pts.first().x, pts.first().y)
                    for (i in 0 until pts.size - 1) {
                        val p0 = pts[i]
                        val p1 = pts[i + 1]
                        val controlX = (p0.x + p1.x) / 2f
                        path.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                    }
                    return path
                }

                val totalLinePath = buildSmoothPath(totalPoints)
                val totalFillPath = Path().apply {
                    addPath(totalLinePath)
                    lineTo(totalPoints.last().x, chartHeight)
                    lineTo(totalPoints.first().x, chartHeight)
                    close()
                }

                // Total Calls Area Gradient
                drawPath(
                    path = totalFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BrandBlue.copy(alpha = 0.30f),
                            BrandBlue.copy(alpha = 0.03f)
                        ),
                        startY = 0f,
                        endY = chartHeight
                    )
                )

                // Critical Calls Area + Line
                val critLinePath = buildSmoothPath(critPoints)
                val critFillPath = Path().apply {
                    addPath(critLinePath)
                    lineTo(critPoints.last().x, chartHeight)
                    lineTo(critPoints.first().x, chartHeight)
                    close()
                }

                drawPath(
                    path = critFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            EmergencyRed.copy(alpha = 0.24f),
                            EmergencyRed.copy(alpha = 0.02f)
                        ),
                        startY = 0f,
                        endY = chartHeight
                    )
                )

                // Draw Stroke Lines
                drawPath(
                    path = totalLinePath,
                    color = BrandBlue,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                drawPath(
                    path = critLinePath,
                    color = EmergencyRed,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw Data Nodes
                totalPoints.forEachIndexed { idx, pt ->
                    val isSelected = idx == selectedIndex
                    drawCircle(
                        color = CleanWhite,
                        radius = if (isSelected) 5.5.dp.toPx() else 3.5.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = BrandBlue,
                        radius = if (isSelected) 4.dp.toPx() else 2.5.dp.toPx(),
                        center = pt
                    )
                }

                critPoints.forEachIndexed { idx, pt ->
                    if (buckets[idx].criticalCalls > 0) {
                        val isSelected = idx == selectedIndex
                        drawCircle(
                            color = EmergencyRed,
                            radius = if (isSelected) 3.8.dp.toPx() else 2.2.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2D Spatial Density Radar & Quadrant Heatmap showing the geographic distribution
 * of SOS calls across the 5 regional sectors over the last 24 hours.
 */
@Composable
private fun GeographicSectorHeatmapCanvas(
    points: List<SosGeoIncidentPoint>,
    sectors: List<GeographicSectorStat>,
    centerLat: Double,
    centerLon: Double,
    selectedSectorId: String?,
    onSectorTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(sectors) {
                    detectTapGestures { tap ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = tap.x - cx
                        val dy = tap.y - cy
                        val minDim = minOf(size.width, size.height).toDouble().coerceAtLeast(1.0)
                        val distRatio = kotlin.math.hypot(dx.toDouble(), dy.toDouble()) / (minDim / 2.0)
                        val tappedSector = if (distRatio <= 0.28) {
                            "SECTOR_CENTRAL"
                        } else {
                            val angle = (Math.toDegrees(kotlin.math.atan2(dx.toDouble(), -dy.toDouble())) + 360.0) % 360.0
                            when {
                                angle >= 315.0 || angle < 45.0 -> "SECTOR_NORTH"
                                angle >= 45.0 && angle < 135.0 -> "SECTOR_EAST"
                                angle >= 135.0 && angle < 225.0 -> "SECTOR_SOUTH"
                                else -> "SECTOR_WEST"
                            }
                        }
                        onSectorTapped(tappedSector)
                    }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxRadiusPx = (size.minDimension / 2f) * 0.88f
            val maxGeoMeters = 5000.0

            // Concentric radar distance rings (1 km, 2.5 km, 5 km)
            val ringRadii = listOf(0.25f, 0.58f, 0.92f)
            ringRadii.forEach { frac ->
                drawCircle(
                    color = Color(0xFF334155),
                    radius = maxRadiusPx * frac,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Diagonal sector boundary lines (45, 135, 225, 315 deg)
            val diagAngles = listOf(45.0, 135.0, 225.0, 315.0)
            diagAngles.forEach { deg ->
                val rad = Math.toRadians(deg)
                val innerR = maxRadiusPx * 0.25f
                val startX = cx + (sin(rad) * innerR).toFloat()
                val startY = cy - (cos(rad) * innerR).toFloat()
                val endX = cx + (sin(rad) * maxRadiusPx * 0.92f).toFloat()
                val endY = cy - (cos(rad) * maxRadiusPx * 0.92f).toFloat()
                drawLine(
                    color = Color(0xFF334155).copy(alpha = 0.7f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Plot glowing heatmap blobs & points for each 24h SOS incident
            points.forEach { pt ->
                val dLatMeters = (pt.latitude - centerLat) * 111_320.0
                val dLonMeters = (pt.longitude - centerLon) * 111_320.0 * cos(Math.toRadians(centerLat))

                val pxX = (cx + ((dLonMeters / maxGeoMeters) * maxRadiusPx).toFloat())
                    .coerceIn(8.dp.toPx(), size.width - 8.dp.toPx())
                val pxY = (cy - ((dLatMeters / maxGeoMeters) * maxRadiusPx).toFloat())
                    .coerceIn(8.dp.toPx(), size.height - 8.dp.toPx())

                val isSelectedSector = selectedSectorId == null || selectedSectorId == pt.sectorId
                val isCritical = pt.priority.equals("CRITICAL", ignoreCase = true)
                val isActive = pt.status == EmergencyStatus.ACTIVE || pt.status == EmergencyStatus.RESPONDER_FOUND
                val baseColor = when {
                    isActive -> SafetyGreen
                    isCritical -> EmergencyRed
                    else -> BrandBlue
                }
                val alphaFactor = if (isSelectedSector) 1f else 0.25f

                // Outer heat glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            baseColor.copy(alpha = 0.42f * alphaFactor),
                            baseColor.copy(alpha = 0.0f)
                        ),
                        center = Offset(pxX, pxY),
                        radius = 14.dp.toPx()
                    ),
                    radius = 14.dp.toPx(),
                    center = Offset(pxX, pxY)
                )

                // Incident core node
                drawCircle(
                    color = baseColor.copy(alpha = 0.95f * alphaFactor),
                    radius = if (isActive) 4.dp.toPx() else 2.8.dp.toPx(),
                    center = Offset(pxX, pxY)
                )
            }

            // Center Command Hub reticle
            drawCircle(
                color = CleanWhite,
                radius = 3.dp.toPx(),
                center = Offset(cx, cy)
            )
        }

        // Cardinal compass labels overlay
        Text(
            text = "N",
            color = Color(0xFF94A3B8),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 3.dp)
        )
        Text(
            text = "S",
            color = Color(0xFF94A3B8),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 3.dp)
        )
        Text(
            text = "W",
            color = Color(0xFF94A3B8),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
        )
        Text(
            text = "E",
            color = Color(0xFF94A3B8),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
        )
    }
}

@Composable
private fun SectorDistributionRow(
    sector: GeographicSectorStat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val barColor = when {
        sector.activeCalls > 0 -> SafetyGreen
        sector.criticalCalls >= 3 -> EmergencyRed
        sector.criticalCalls > 0 -> AlertAmber
        else -> BrandBlue
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) BrandBlue.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sector.shortCode,
                    color = barColor,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = sector.sectorName,
                    color = TextSlate800,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = String.format(Locale.US, "%d (%d%%)", sector.totalCalls, sector.sharePercentage.toInt()),
                color = TextSlate900,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFE2E8F0))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((sector.sharePercentage / 100f).coerceIn(0.06f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.EmergencyConfig
import com.example.model.EmergencyLog
import com.example.model.EmergencyStatus
import com.example.model.Responder
import com.example.model.ResponderStatus
import com.example.ui.components.EmergencyStatusBadge
import com.example.ui.components.MapComposable
import com.example.ui.components.TacticalRadarMap
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CleanCanvas
import com.example.ui.theme.CleanWhite
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedLight
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyGreenLight
import com.example.ui.theme.SlateBorderLight
import com.example.ui.theme.TextSlate400
import com.example.ui.theme.TextSlate500
import com.example.ui.theme.TextSlate600
import com.example.ui.theme.TextSlate800
import com.example.ui.theme.TextSlate900
import com.example.ui.viewmodel.EmergencyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ControlCenterScreen(
    viewModel: EmergencyViewModel,
    modifier: Modifier = Modifier
) {
    val session by viewModel.session.collectAsState()
    val responders by viewModel.responders.collectAsState()
    val logs by viewModel.logs.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CleanCanvas)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CleanWhite)
                .shadow(2.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "CONTROL CENTER",
                        color = TextSlate900,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "UERI Multi-Hop Dispatch & Telemetry Hub",
                        color = TextSlate500,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                EmergencyStatusBadge(status = session.status)
            }
        }

        // Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Metrics KPI Grid (4 Cards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard(
                    title = "COVERAGE RADIUS",
                    value = EmergencyConfig.formatDistance(session.radiusMeters),
                    subtitle = if (session.status != EmergencyStatus.SAFE) "Expanding dynamic multi-hop" else "Standby perimeter",
                    icon = Icons.Default.CellTower,
                    iconTint = if (session.status != EmergencyStatus.SAFE) EmergencyRed else BrandBlue,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "PEOPLE NOTIFIED",
                    value = "${session.peopleNotified}",
                    subtitle = "Citizens within broadcast zone",
                    icon = Icons.Default.Group,
                    iconTint = SafetyGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard(
                    title = "AVAILABLE UNITS",
                    value = "${responders.count { it.status == ResponderStatus.AVAILABLE }} / ${responders.size}",
                    subtitle = "Ready for immediate dispatch",
                    icon = Icons.Default.Shield,
                    iconTint = BrandBlue,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "DISPATCH STATUS",
                    value = if (session.activeResponderName != null) "ASSIGNED" else if (session.status == EmergencyStatus.ACTIVE) "SEARCHING" else "STANDBY",
                    subtitle = session.activeResponderName ?: "No unit required",
                    icon = Icons.Default.Hub,
                    iconTint = if (session.activeResponderName != null) SafetyGreen else AlertAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tactical Command Operations Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderLight)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TACTICAL COMMAND ACTIONS",
                        color = TextSlate900,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (session.status == EmergencyStatus.SAFE) {
                        Button(
                            onClick = { viewModel.triggerSos() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("control_center_trigger_sos"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmergencyRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🚨 TRIGGER CENTRAL SOS BROADCAST", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.forceNextRadiusHop() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("control_center_radius_hop"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandBlue,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("📡 Force Radius Hop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.dispatchAllResponders() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("control_center_dispatch_all"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AlertAmber,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("🚑 Dispatch All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { viewModel.markSafe() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("control_center_resolve"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SafetyGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("✅ MARK ALL-CLEAR & STAND DOWN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tactical Map
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
                        Text(
                            text = "REGIONAL MESH TELEMETRY RADAR",
                            color = TextSlate900,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "LIVE FEED",
                            color = SafetyGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
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

            // Responder Fleet Status Table
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderLight)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FIELD RESPONDER NETWORK",
                        color = TextSlate900,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    responders.forEach { resp ->
                        ResponderRowItem(responder = resp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real-Time Audit Log Timeline
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderLight)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "REAL-TIME INCIDENT AUDIT LOG",
                        color = TextSlate900,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (logs.isEmpty()) {
                        Text(
                            text = "No audit log entries recorded yet.",
                            color = TextSlate500,
                            fontSize = 11.sp
                        )
                    } else {
                        logs.take(10).forEach { log ->
                            LogItemRow(log = log)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CleanWhite)
            .border(1.dp, SlateBorderLight, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    color = TextSlate400,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = TextSlate900,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSlate500,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ResponderRowItem(responder: Responder) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, SlateBorderLight, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (responder.status) {
                            ResponderStatus.RESPONDING -> SafetyGreen
                            ResponderStatus.ALERTED -> EmergencyRed
                            ResponderStatus.AVAILABLE -> BrandBlue
                            ResponderStatus.RESOLVED -> TextSlate400
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = responder.name,
                    color = TextSlate900,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${responder.role} • ${EmergencyConfig.formatDistance(responder.distanceMeters)}",
                    color = TextSlate500,
                    fontSize = 10.sp
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = responder.status.name,
                color = when (responder.status) {
                    ResponderStatus.RESPONDING -> SafetyGreen
                    ResponderStatus.ALERTED -> EmergencyRed
                    ResponderStatus.AVAILABLE -> BrandBlue
                    ResponderStatus.RESOLVED -> TextSlate400
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "ETA ${responder.etaMinutes}m",
                color = TextSlate500,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun LogItemRow(log: EmergencyLog) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    val timeStr = timeFormat.format(Date(log.timestamp))

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
            .padding(8.dp)
    ) {
        Text(
            text = timeStr,
            color = TextSlate400,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(60.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = log.message,
            color = TextSlate800,
            fontSize = 11.sp
        )
    }
}

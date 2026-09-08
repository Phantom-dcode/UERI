package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.config.EmergencyConfig
import com.example.model.EmergencyStatus
import com.example.model.Responder
import com.example.model.ResponderStatus
import androidx.compose.material3.OutlinedButton
import com.example.ui.components.EmergencyStatusBadge
import com.example.ui.components.MapComposable
import com.example.ui.components.PriorityBadge
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

@Composable
fun ResponderScreen(
    viewModel: EmergencyViewModel,
    modifier: Modifier = Modifier
) {
    val session by viewModel.session.collectAsState()
    val responders by viewModel.responders.collectAsState()
    val selectedId by viewModel.selectedResponderId.collectAsState()
    val currentResponder = responders.find { it.id == selectedId } ?: responders.firstOrNull()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CleanCanvas)
    ) {
        // Clean Minimal Header
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
                        text = "RESPONDER PORTAL",
                        color = TextSlate900,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "UERI Field Operations Unit",
                        color = TextSlate500,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                EmergencyStatusBadge(status = session.status)
            }
        }

        // Main Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Responder Profile Switcher
            Text(
                text = "SELECT ACTIVE FIELD RESPONDER",
                color = TextSlate500,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(responders) { resp ->
                    ResponderChip(
                        responder = resp,
                        isSelected = resp.id == selectedId,
                        onSelect = { viewModel.selectResponder(resp.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            currentResponder?.let { responder ->
                // Responder Status Banner
                ResponderProfileCard(
                    responder = responder,
                    session = session,
                    onRespondClick = { viewModel.responderRespond(responder.id) },
                    onSimulateSosClick = { viewModel.triggerSos() },
                    onArrivedClick = { viewModel.responderArrived(responder.id) },
                    onResolveClick = { viewModel.markSafe() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Map Section with Google Maps SDK and Radar Switcher
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
                            text = "INCIDENT LOCATION & PROXIMITY",
                            color = TextSlate900,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        currentResponder?.let {
                            Text(
                                text = "DISTANCE: ${EmergencyConfig.formatDistance(it.distanceMeters)}",
                                color = BrandBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
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

            // Incident Details & Victim Telemetry Card
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
                        text = "INCIDENT BRIEFING",
                        color = TextSlate900,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (session.status == EmergencyStatus.SAFE) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SafetyGreenLight)
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafetyGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "All sectors normal. No active SOS in your zone.",
                                color = SafetyGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                BriefItem("Victim Priority", session.priority, EmergencyRed)
                                BriefItem("Emergency Type", session.emergencyType, AlertAmber)
                                BriefItem("Alert Radius", EmergencyConfig.formatDistance(session.radiusMeters), BrandBlue)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                BriefItem("Est. Travel Time", "${currentResponder?.etaMinutes ?: 2} MIN", TextSlate900)
                                BriefItem("Direct Distance", EmergencyConfig.formatDistance(currentResponder?.distanceMeters ?: 400.0), TextSlate900)
                                BriefItem("Location Status", session.locationStatus, SafetyGreen)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ResponderChip(
    responder: Responder,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val bg = if (isSelected) BrandBlue else CleanWhite
    val textColor = if (isSelected) Color.White else TextSlate800
    val border = if (isSelected) BrandBlue else SlateBorderLight

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("responder_chip_${responder.id}")
    ) {
        Icon(
            imageVector = when {
                responder.role.contains("Patrol", true) -> Icons.Default.LocalPolice
                responder.role.contains("Paramedic", true) -> Icons.Default.LocalHospital
                else -> Icons.Default.Security
            },
            contentDescription = null,
            tint = if (isSelected) Color.White else BrandBlue,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = responder.name,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${responder.role} • ${EmergencyConfig.formatDistance(responder.distanceMeters)}",
                color = if (isSelected) Color.White.copy(alpha = 0.8f) else TextSlate400,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun ResponderProfileCard(
    responder: Responder,
    session: com.example.model.EmergencySession,
    onRespondClick: () -> Unit,
    onSimulateSosClick: () -> Unit,
    onArrivedClick: () -> Unit,
    onResolveClick: () -> Unit
) {
    val isResponding = responder.status == ResponderStatus.RESPONDING
    val isAlerted = responder.status == ResponderStatus.ALERTED && session.status != EmergencyStatus.SAFE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = CleanWhite),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isResponding) SafetyGreen else if (isAlerted) EmergencyRed else SlateBorderLight
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isResponding) SafetyGreenLight else if (isAlerted) EmergencyRedLight else Color(0xFFEFF6FF)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (responder.role.contains("Paramedic")) "🚑" else if (responder.role.contains("Police")) "🚓" else "🚒",
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = responder.name,
                            color = TextSlate900,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = responder.role,
                            color = TextSlate500,
                            fontSize = 11.sp
                        )
                    }
                }

                // Status Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            when (responder.status) {
                                ResponderStatus.RESPONDING -> SafetyGreenLight
                                ResponderStatus.ALERTED -> EmergencyRedLight
                                ResponderStatus.AVAILABLE -> Color(0xFFEFF6FF)
                                ResponderStatus.RESOLVED -> Color(0xFFF1F5F9)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = responder.status.name,
                        color = when (responder.status) {
                            ResponderStatus.RESPONDING -> SafetyGreen
                            ResponderStatus.ALERTED -> EmergencyRed
                            ResponderStatus.AVAILABLE -> BrandBlue
                            ResponderStatus.RESOLVED -> TextSlate500
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action / Status Details
            if (session.status != EmergencyStatus.SAFE) {
                if (isResponding) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SafetyGreenLight)
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = SafetyGreen, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (responder.etaMinutes == 0) "ARRIVED ON SCENE" else "EN ROUTE TO VICTIM",
                                    color = SafetyGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = if (responder.etaMinutes == 0) "Unit on scene with citizen" else "ETA: ${responder.etaMinutes} min • Broadcast acknowledged",
                                    color = SafetyGreen.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onArrivedClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("responder_arrived_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandBlue,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("📍 Arrived", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onResolveClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("responder_resolve_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafetyGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("✅ Resolve", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onRespondClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("responder_respond_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmergencyRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.DirectionsRun, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚡ I'M RESPONDING (ACCEPT SOS)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "System is in safe standby mode. You can trigger a live simulation or dispatch test below.",
                        color = TextSlate600,
                        fontSize = 11.sp
                    )

                    Button(
                        onClick = onSimulateSosClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("responder_simulate_sos_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmergencyRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "🚨 SIMULATE INBOUND SOS (TEST ALERT)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefItem(label: String, value: String, valueColor: Color) {
    Column {
        Text(
            text = label.uppercase(),
            color = TextSlate400,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

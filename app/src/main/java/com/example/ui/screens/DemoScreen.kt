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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.ui.components.EmergencyStatusBadge
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CleanCanvas
import com.example.ui.theme.CleanWhite
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedLight
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyGreenLight
import com.example.ui.theme.SlateBorderLight
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextSlate400
import com.example.ui.theme.TextSlate500
import com.example.ui.theme.TextSlate600
import com.example.ui.theme.TextSlate800
import com.example.ui.theme.TextSlate900
import com.example.ui.viewmodel.EmergencyViewModel

@Composable
fun DemoScreen(
    viewModel: EmergencyViewModel,
    modifier: Modifier = Modifier,
    onOpenLiveCamera: (() -> Unit)? = null
) {
    val session by viewModel.session.collectAsState()
    val isFastDemo by viewModel.isFastDemoMode.collectAsState()
    val responders by viewModel.responders.collectAsState()

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
                        text = "DEMO CONTROLS",
                        color = TextSlate900,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Hackathon Presentation & Scenario Simulator",
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
            // Speed Acceleration Switch Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderLight)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isFastDemo) EmergencyRedLight else Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                tint = if (isFastDemo) EmergencyRed else BrandBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "FAST DEMO SPEED (8X)",
                                color = TextSlate900,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isFastDemo) "Dynamic radius expands in ~10s" else "Standard real-world escalation interval",
                                color = TextSlate500,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Switch(
                        checked = isFastDemo,
                        onCheckedChange = { viewModel.setFastDemoMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmergencyRed
                        ),
                        modifier = Modifier.testTag("fast_demo_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Scenario Trigger Actions
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
                        text = "SCENARIO TRIGGER ACTIONS",
                        color = TextSlate900,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Trigger SOS
                    Button(
                        onClick = { viewModel.triggerSos() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("demo_trigger_sos"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("1. TRIGGER ACTIVE SOS (500M INITIAL)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Trigger Offline / Unavailable Location SOS
                    OutlinedButton(
                        onClick = { viewModel.setLocationUnavailable() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("demo_offline_sos"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CleanWhite,
                            contentColor = AlertAmber
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AlertAmber),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.LocationOff, contentDescription = null, modifier = Modifier.size(18.dp), tint = AlertAmber)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2. TEST OFFLINE / LOCATION UNAVAILABLE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Responder Response
                    Button(
                        onClick = { viewModel.responderRespond("resp-1") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("demo_responder_respond"),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DirectionsRun, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("3. SIMULATE RESPONDER JAMES ACCEPT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Mark Safe
                    Button(
                        onClick = { viewModel.markSafe() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("demo_mark_safe"),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("4. VICTIM CONFIRMED SAFE (STAND DOWN)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Reset Demo
                    OutlinedButton(
                        onClick = { viewModel.resetDemo() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("demo_reset_all"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CleanWhite,
                            contentColor = TextSlate800
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderLight),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextSlate800)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RESET DEMO TO INITIAL STANDBY", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (onOpenLiveCamera != null) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // CameraX Optics Verification Launcher
                        Button(
                            onClick = onOpenLiveCamera,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("demo_open_camerax"),
                            colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("VERIFY CAMERAX INCIDENT PREVIEW", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Technical Architecture Explainer Card
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
                        text = "UERI ARCHITECTURE HIGHLIGHTS",
                        color = TextSlate900,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    TechFeatureItem(
                        title = "1. Dynamic Radius Expansion",
                        desc = "Starts at 500m to minimize panic. If no responder accepts within timeout, auto-expands to 1km, 2km, then 5km maximum perimeter."
                    )
                    TechFeatureItem(
                        title = "2. Multi-Hop Mesh Relay",
                        desc = "When cellular is weak, devices act as relays, passing encrypted emergency packets peer-to-peer to the nearest connected gateway."
                    )
                    TechFeatureItem(
                        title = "3. Offline Fallback Mode",
                        desc = "If GPS is offline, UERI broadcasts using cellular tower triangulations & Wi-Fi BSSID beacons with prominent alert tags."
                    )
                    TechFeatureItem(
                        title = "4. Cryptographic Audit Chain",
                        desc = "All incident alerts, evidence timestamps, and responder acceptances are stored locally in Room DB with millisecond audit trails."
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TechFeatureItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            color = BrandBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = desc,
            color = TextSlate600,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

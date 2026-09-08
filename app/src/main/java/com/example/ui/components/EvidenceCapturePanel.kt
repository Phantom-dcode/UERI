package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EvidenceItem
import com.example.model.EvidenceType
import com.example.ui.theme.CleanWhite
import com.example.ui.theme.DarkNavy800
import com.example.ui.theme.DarkNavy900
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextSlate400
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EvidenceCapturePanel(
    evidenceList: List<EvidenceItem>,
    isRecordingAudio: Boolean,
    recordingSeconds: Int,
    onCapturePhoto: () -> Unit,
    onStartAudioRecord: () -> Unit,
    onStopAudioRecord: () -> Unit,
    onCaptureVideo: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLiveCameraPreview: (() -> Unit)? = null
) {
    var selectedEvidence by remember { mutableStateOf<EvidenceItem?>(null) }

    if (selectedEvidence != null) {
        EvidenceDetailDialog(
            item = selectedEvidence!!,
            onDismiss = { selectedEvidence = null }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkNavy900),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "INCIDENT EVIDENCE CAPTURE",
                color = TextSlate400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3 Clean Dark Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo Action
                EvidenceButton(
                    emoji = "📸",
                    icon = Icons.Default.CameraAlt,
                    label = "Photo",
                    onClick = onCapturePhoto,
                    tag = "evidence_photo_button"
                )

                // Audio Action (With Live Recording Toggle)
                if (isRecordingAudio) {
                    EvidenceButton(
                        emoji = "⏹️",
                        icon = Icons.Default.Stop,
                        label = "Stop (${recordingSeconds}s)",
                        onClick = onStopAudioRecord,
                        tag = "evidence_stop_audio_button",
                        isActive = true
                    )
                } else {
                    EvidenceButton(
                        emoji = "🎙️",
                        icon = Icons.Default.Mic,
                        label = "Audio",
                        onClick = onStartAudioRecord,
                        tag = "evidence_audio_button"
                    )
                }

                // Video Action
                EvidenceButton(
                    emoji = "🎥",
                    icon = Icons.Default.Videocam,
                    label = "Video",
                    onClick = onCaptureVideo,
                    tag = "evidence_video_button"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dedicated CameraX Preview & Verification Launcher Card
            Surface(
                onClick = { onOpenLiveCameraPreview?.invoke() ?: onCapturePhoto() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verify_camerax_preview_button"),
                shape = RoundedCornerShape(14.dp),
                color = DarkNavy800,
                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalCyan.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(TacticalCyan.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = TacticalCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "CameraX Incident Optics",
                                color = CleanWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Live Viewfinder • GPS Reticle Overlay",
                                color = TextSlate400,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        text = "VERIFY →",
                        color = TacticalCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Live Audio Waveform Notification
            AnimatedVisibility(visible = isRecordingAudio) {
                val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
                val barScale by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 400),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "barScale"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF7F1D1D))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "RECORDING AMBIENT AUDIO... ${recordingSeconds}s",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Attached Evidence Horizontal Thumbnails List
            if (evidenceList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(evidenceList) { item ->
                        EvidenceCard(
                            item = item,
                            onClick = { selectedEvidence = item }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceButton(
    emoji: String,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String,
    isActive: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isActive) EmergencyRed else DarkNavy800)
                .border(
                    1.dp,
                    if (isActive) EmergencyRed else Color(0xFF334155),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Text(text = emoji, fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label.uppercase(),
            color = if (isActive) Color(0xFFF87171) else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun EvidenceCard(
    item: EvidenceItem,
    onClick: () -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    val formattedTime = timeFormat.format(Date(item.timestamp))

    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkNavy800)
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = when (item.type) {
                    EvidenceType.PHOTO -> Icons.Default.CameraAlt
                    EvidenceType.AUDIO -> Icons.Default.Mic
                    EvidenceType.VIDEO -> Icons.Default.Videocam
                },
                contentDescription = null,
                tint = TacticalCyan,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = item.type.name,
                color = TacticalCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formattedTime,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${String.format(Locale.US, "%.3f", item.latitude)}, ${String.format(Locale.US, "%.3f", item.longitude)}",
            color = TextSlate400,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Tap to review",
            color = TacticalCyan.copy(alpha = 0.8f),
            fontSize = 9.sp
        )
    }
}

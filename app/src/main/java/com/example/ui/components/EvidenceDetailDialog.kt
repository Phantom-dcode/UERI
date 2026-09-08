package com.example.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.model.EvidenceItem
import com.example.model.EvidenceType
import com.example.ui.theme.DarkNavy800
import com.example.ui.theme.DarkNavy900
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.SafetyGreenDark
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextSlate400
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EvidenceDetailDialog(
    item: EvidenceItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val timeFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.US)
    val formattedDate = timeFormat.format(Date(item.timestamp))

    // Audio playback state
    var isPlayingAudio by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(item) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavy900),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (item.type) {
                                EvidenceType.PHOTO -> Icons.Default.PhotoCamera
                                EvidenceType.AUDIO -> Icons.Default.Mic
                                EvidenceType.VIDEO -> Icons.Default.Videocam
                            },
                            contentDescription = null,
                            tint = TacticalCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${item.type.name} EVIDENCE",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSlate400)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content View
                when (item.type) {
                    EvidenceType.PHOTO -> {
                        val file = item.uri?.let { File(it) }
                        if (file != null && file.exists()) {
                            Image(
                                painter = rememberAsyncImagePainter(file),
                                contentDescription = "Photo Evidence",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkNavy800)
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        tint = TacticalCyan,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "HIGH-RES SCENE SNAPSHOT ATTACHED",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    EvidenceType.AUDIO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkNavy800)
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        if (isPlayingAudio) {
                                            mediaPlayer?.pause()
                                            isPlayingAudio = false
                                        } else {
                                            val audioPath = item.uri
                                            if (audioPath != null && File(audioPath).exists()) {
                                                try {
                                                    if (mediaPlayer == null) {
                                                        mediaPlayer = MediaPlayer().apply {
                                                            setDataSource(audioPath)
                                                            prepare()
                                                            setOnCompletionListener {
                                                                isPlayingAudio = false
                                                            }
                                                        }
                                                    }
                                                    mediaPlayer?.start()
                                                    isPlayingAudio = true
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(if (isPlayingAudio) EmergencyRed else TacticalCyan)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play Audio",
                                        tint = Color.Black,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (isPlayingAudio) "PLAYING RECORDING..." else "DURATION: ${item.durationSeconds}s",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    EvidenceType.VIDEO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkNavy800)
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = EmergencyRed,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "VIDEO CLIP RECORDED (${item.durationSeconds}s)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // GPS & Timestamp Details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B1120))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "TIMESTAMP: $formattedDate",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "COORDINATES: ${String.format(Locale.US, "%.5f", item.latitude)}, ${String.format(Locale.US, "%.5f", item.longitude)}",
                        color = TacticalCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "STATUS: ${item.description}",
                        color = TextSlate400,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "DONE", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

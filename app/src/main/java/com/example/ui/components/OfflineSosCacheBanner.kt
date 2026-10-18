package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CleanWhite
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SlateBorderLight
import com.example.ui.theme.TextSlate500
import com.example.ui.theme.TextSlate800
import com.example.ui.theme.TextSlate900

/**
 * Displays the real-time Room database offline SOS cache & auto-broadcast status,
 * with a quick toggle to test offline caching and automatic broadcast upon reconnection.
 */
@Composable
fun OfflineSosCacheBanner(
    isOnline: Boolean,
    pendingCount: Int,
    isSyncing: Boolean,
    lastSyncMessage: String?,
    onToggleConnectivity: (Boolean) -> Unit,
    onForceSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = when {
        !isOnline -> AlertAmber
        isSyncing || pendingCount > 0 -> BrandBlue
        else -> SafetyGreen
    }

    val statusIcon = when {
        !isOnline -> Icons.Default.CloudOff
        isSyncing || pendingCount > 0 -> Icons.Default.CloudSync
        else -> Icons.Default.CloudDone
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CleanWhite)
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("offline_sos_cache_banner")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = if (isOnline) "Online" else "Offline Cache Active",
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                !isOnline -> "OFFLINE MODE • ROOM SOS CACHE ACTIVE"
                                isSyncing -> "BROADCASTING CACHED SOS QUEUE..."
                                pendingCount > 0 -> "ROOM CACHE: $pendingCount PENDING SOS"
                                else -> "ONLINE • ROOM OFFLINE CACHE READY"
                            },
                            color = TextSlate900,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.3.sp
                        )
                    }

                    Text(
                        text = when {
                            !isOnline -> "SOS triggers are cached locally in Room & auto-broadcasted when online"
                            pendingCount > 0 -> "$pendingCount offline SOS ${if (pendingCount == 1) "request" else "requests"} queued for broadcast"
                            else -> "All cached SOS requests synced ($pendingCount pending in Room DB)"
                        },
                        color = TextSlate500,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isOnline && pendingCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandBlue)
                            .clickable(onClick = onForceSyncNow)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("offline_sos_sync_now_button")
                    ) {
                        Text(
                            text = "Sync ($pendingCount)",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isOnline) Color(0xFFF1F5F9) else SafetyGreen.copy(alpha = 0.15f))
                        .border(
                            1.dp,
                            if (isOnline) SlateBorderLight else SafetyGreen.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onToggleConnectivity(isOnline) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("offline_mode_toggle_button")
                ) {
                    Text(
                        text = if (isOnline) "Simulate Offline" else "Restore Online",
                        color = if (isOnline) TextSlate800 else SafetyGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(visible = !lastSyncMessage.isNullOrBlank()) {
            if (!lastSyncMessage.isNullOrBlank()) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = lastSyncMessage,
                            color = TextSlate800,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

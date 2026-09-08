package com.example.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EmergencyStatus
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.AlertAmberLight
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedLight
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyGreenLight

@Composable
fun EmergencyStatusBadge(
    status: EmergencyStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, borderColor, dotColor) = when (status) {
        EmergencyStatus.SAFE -> Quad(
            SafetyGreenLight,
            SafetyGreen,
            SafetyGreen.copy(alpha = 0.3f),
            SafetyGreen
        )
        EmergencyStatus.ACTIVE -> Quad(
            EmergencyRedLight,
            EmergencyRed,
            EmergencyRed.copy(alpha = 0.4f),
            EmergencyRed
        )
        EmergencyStatus.RESPONDER_FOUND -> Quad(
            Color(0xFFE0F2FE),
            Color(0xFF0369A1),
            Color(0xFFBAE6FD),
            Color(0xFF0284C7)
        )
        EmergencyStatus.RESOLVED -> Quad(
            SafetyGreenLight,
            SafetyGreen,
            SafetyGreen.copy(alpha = 0.3f),
            SafetyGreen
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "badgePulse")
    val alphaAnim by infiniteTransition.animateColor(
        initialValue = dotColor.copy(alpha = 1.0f),
        targetValue = dotColor.copy(alpha = 0.2f),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (status == EmergencyStatus.ACTIVE) 600 else 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(50.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (status == EmergencyStatus.ACTIVE) alphaAnim else dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Status: ${status.label}",
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
fun PriorityBadge(
    priority: String,
    modifier: Modifier = Modifier
) {
    val isCritical = priority.contains("CRITICAL", ignoreCase = true)
    val color = if (isCritical) EmergencyRed else AlertAmber
    val bg = if (isCritical) EmergencyRedLight else AlertAmberLight

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = priority.uppercase(),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


package com.example.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * UERI Emergency Notification Manager.
 * Handles high-priority system alerts, heads-up sound & vibration patterns,
 * and deep links into the Responder Portal without depending on fragile GSF registrations.
 */
object UeriNotificationManager {
    private const val TAG = "UeriNotifManager"
    const val CHANNEL_ID = "ueri_emergency_channel"
    const val CHANNEL_NAME = "UERI Critical Emergency Alerts"
    const val EXTRA_EMERGENCY_ID = "EXTRA_EMERGENCY_ID"
    const val EXTRA_NAVIGATE_TAB = "EXTRA_NAVIGATE_TAB"
    const val EXTRA_EMERGENCY_STATUS = "EXTRA_EMERGENCY_STATUS"
    const val EXTRA_RADIUS_METERS = "EXTRA_RADIUS_METERS"

    fun sendNotification(
        context: Context,
        emergencyId: String,
        title: String,
        body: String,
        status: String = "ACTIVE",
        radiusMeters: String = "500"
    ) {
        Log.d(TAG, "Posting emergency notification: $title - $emergencyId")
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_OPEN_EMERGENCY"
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_EMERGENCY_ID, emergencyId)
            putExtra(EXTRA_NAVIGATE_TAB, "RESPONDER")
            putExtra(EXTRA_EMERGENCY_STATUS, status)
            putExtra(EXTRA_RADIUS_METERS, radiusMeters)
        }

        val requestCode = emergencyId.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mission-critical distress calls and verified tactical responder alerts"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                setVibrationPattern(vibrationPattern)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body.lines().firstOrNull() ?: body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(vibrationPattern)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)

        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}

/**
 * Backward-compatible alias for existing code referencing UeriFirebaseMessagingService.
 */
object UeriFirebaseMessagingService {
    const val CHANNEL_ID = UeriNotificationManager.CHANNEL_ID
    const val CHANNEL_NAME = UeriNotificationManager.CHANNEL_NAME
    const val EXTRA_EMERGENCY_ID = UeriNotificationManager.EXTRA_EMERGENCY_ID
    const val EXTRA_NAVIGATE_TAB = UeriNotificationManager.EXTRA_NAVIGATE_TAB
    const val EXTRA_EMERGENCY_STATUS = UeriNotificationManager.EXTRA_EMERGENCY_STATUS
    const val EXTRA_RADIUS_METERS = UeriNotificationManager.EXTRA_RADIUS_METERS

    fun sendNotification(
        context: Context,
        emergencyId: String,
        title: String,
        body: String,
        status: String = "ACTIVE",
        radiusMeters: String = "500"
    ) {
        UeriNotificationManager.sendNotification(context, emergencyId, title, body, status, radiusMeters)
    }
}

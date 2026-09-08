package com.example.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.example.model.EmergencySession
import com.example.model.EvidenceItem
import com.example.util.LocationService
import com.example.util.LocationState
import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured Data Payload for Backend Notification and Emergency Dispatch Systems.
 * Used for Push Notifications (FCM), WebSocket broadcast, Webhooks, and SMS dispatch gateways.
 */
data class LocationPayload(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double = 0.0,
    val locationProvider: String = "GPS_FUSED",
    val isMock: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("latitude", latitude)
        put("longitude", longitude)
        put("accuracyMeters", accuracyMeters)
        put("altitudeMeters", altitudeMeters)
        put("locationProvider", locationProvider)
        put("isMock", isMock)
        put("timestamp", timestamp)
    }
}

data class DeviceTelemetryPayload(
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val networkType: String,
    val osVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("batteryPercentage", batteryPercentage)
        put("isCharging", isCharging)
        put("networkType", networkType)
        put("osVersion", osVersion)
        put("deviceModel", deviceModel)
    }
}

data class EvidenceAttachmentPayload(
    val id: String,
    val type: String,
    val uriOrChecksum: String?,
    val durationSeconds: Int,
    val timestamp: Long
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("uri", uriOrChecksum ?: "")
        put("durationSeconds", durationSeconds)
        put("timestamp", timestamp)
    }
}

data class SosBroadcastPayload(
    val emergencyId: String,
    val citizenId: String,
    val timestamp: Long,
    val priority: String,
    val emergencyType: String,
    val dynamicRadiusMeters: Double,
    val location: LocationPayload,
    val telemetry: DeviceTelemetryPayload,
    val evidenceAttachments: List<EvidenceAttachmentPayload>,
    val alertedRespondersCount: Int,
    val broadcastChannel: String = "MULTI_HOP_ESCALATION"
) {
    /**
     * Serializes this payload into standard JSON format ready for HTTP POST / FCM notification payloads.
     */
    fun toJsonString(): String {
        val root = JSONObject().apply {
            put("emergencyId", emergencyId)
            put("citizenId", citizenId)
            put("timestamp", timestamp)
            put("priority", priority)
            put("emergencyType", emergencyType)
            put("dynamicRadiusMeters", dynamicRadiusMeters)
            put("broadcastChannel", broadcastChannel)
            put("alertedRespondersCount", alertedRespondersCount)
            put("location", location.toJsonObject())
            put("telemetry", telemetry.toJsonObject())

            val evidenceArray = JSONArray()
            evidenceAttachments.forEach { evidenceArray.put(it.toJsonObject()) }
            put("evidence", evidenceArray)
        }
        return root.toString(2)
    }
}

/**
 * Backend Notification Service boilerplate.
 * Retrieves location, gathers telemetry, formats the payload, and sends it to the notification gateway.
 */
class BackendNotificationService(private val context: Context) {

    /**
     * Core Boilerplate Function: Broadcasts SOS to the backend notification system.
     */
    suspend fun broadcastSos(
        session: EmergencySession,
        locationService: LocationService,
        evidenceList: List<EvidenceItem>
    ): Result<SosBroadcastPayload> {
        return try {
            Log.d(TAG, "Preparing SOS broadcast payload for incident ${session.emergencyId}...")

            // 1. Retrieve the latest high-accuracy location from LocationService (with fallback)
            val locationPayload = when (val locState = locationService.getCurrentLocation()) {
                is LocationState.Success -> {
                    LocationPayload(
                        latitude = locState.latitude,
                        longitude = locState.longitude,
                        accuracyMeters = locState.accuracy,
                        altitudeMeters = locState.altitude,
                        locationProvider = "GPS_HIGH_ACCURACY",
                        isMock = locState.isMock,
                        timestamp = locState.timestamp
                    )
                }
                else -> {
                    // Fallback to session coordinates if GPS lock is temporarily acquiring
                    LocationPayload(
                        latitude = session.latitude,
                        longitude = session.longitude,
                        accuracyMeters = session.locationAccuracyMeters,
                        locationProvider = "FALLBACK_MESH_BEACON",
                        timestamp = System.currentTimeMillis()
                    )
                }
            }

            // 2. Extract real device telemetry (Battery, Network state)
            val telemetryPayload = extractDeviceTelemetry()

            // 3. Map attached evidence attachments
            val evidencePayloads = evidenceList.map { item ->
                EvidenceAttachmentPayload(
                    id = item.id,
                    type = item.type.name,
                    uriOrChecksum = item.uri,
                    durationSeconds = item.durationSeconds,
                    timestamp = item.timestamp
                )
            }

            // 4. Assemble the complete SOS broadcast payload
            val payload = SosBroadcastPayload(
                emergencyId = session.emergencyId,
                citizenId = "CITIZEN-${Build.SERIAL.takeIf { it != "unknown" } ?: (1000..9999).random()}",
                timestamp = System.currentTimeMillis(),
                priority = session.priority,
                emergencyType = session.emergencyType,
                dynamicRadiusMeters = session.radiusMeters,
                location = locationPayload,
                telemetry = telemetryPayload,
                evidenceAttachments = evidencePayloads,
                alertedRespondersCount = session.respondersCount,
                broadcastChannel = "MULTI_HOP_ESCALATION_GATEWAY"
            )

            // 5. Transmit payload to backend endpoint / push notification queue
            transmitToBackend(payload)

            Result.success(payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating or transmitting SOS broadcast payload", e)
            Result.failure(e)
        }
    }

    private fun extractDeviceTelemetry(): DeviceTelemetryPayload {
        var batteryPct = 100
        var isCharging = false

        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            batteryPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            isCharging = batteryManager?.isCharging == true
        } catch (_: Exception) {}

        var networkType = "OFFLINE"
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)

            networkType = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR_LTE_5G"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true -> "BLE_MESH"
                else -> "OFFLINE_RELAY"
            }
        } catch (_: Exception) {}

        return DeviceTelemetryPayload(
            batteryPercentage = batteryPct,
            isCharging = isCharging,
            networkType = networkType
        )
    }

    private suspend fun transmitToBackend(payload: SosBroadcastPayload) {
        val jsonString = payload.toJsonString()
        Log.i(TAG, "TRANSMITTING SOS BROADCAST TO NOTIFICATION GATEWAY:\n$jsonString")
        // Ready for Retrofit / Ktor / FCM HTTP v1 API call:
        // emergencyApi.postSosNotification(payload)
    }

    companion object {
        private const val TAG = "BackendNotification"
    }
}

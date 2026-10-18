package com.example.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Synchronization state of an offline-cached SOS request in the Room database.
 */
enum class SosSyncStatus {
    PENDING_SYNC,
    SYNCING,
    BROADCASTED,
    FAILED
}

/**
 * Room Entity representing an emergency SOS request cached locally on the device.
 * Ensures SOS broadcasts are durably persisted when the device is offline and
 * automatically queued for broadcast as soon as network connectivity is restored.
 */
@Entity(
    tableName = "cached_sos_requests",
    indices = [
        Index(value = ["emergencyId"], unique = true),
        Index(value = ["syncStatus"])
    ]
)
data class CachedSosRequest(
    @PrimaryKey val requestId: String,
    val sessionId: String,
    val emergencyId: String,
    val victimUid: String,
    val status: String = EmergencyStatus.ACTIVE.name,
    val emergencyType: String = "CRITICAL / UNKNOWN",
    val priority: String = "CRITICAL",
    val latitude: Double = 37.7749,
    val longitude: Double = -122.4194,
    val locationStatus: String = "ACTIVE",
    val locationAccuracyMeters: Float = 5.0f,
    val radiusMeters: Double = 500.0,
    val peopleNotified: Int = 0,
    val respondersCount: Int = 0,
    val trackingMode: String = "DIRECT_GPS",
    val isBeaconActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = SosSyncStatus.PENDING_SYNC.name,
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val broadcastedAt: Long? = null,
    val errorMessage: String? = null
) {
    fun toEmergencySession(): EmergencySession {
        val parsedStatus = try {
            EmergencyStatus.valueOf(status)
        } catch (_: Exception) {
            EmergencyStatus.ACTIVE
        }
        return EmergencySession(
            id = sessionId,
            emergencyId = emergencyId,
            status = parsedStatus,
            emergencyType = emergencyType,
            priority = priority,
            latitude = latitude,
            longitude = longitude,
            locationStatus = locationStatus,
            locationAccuracyMeters = locationAccuracyMeters,
            radiusMeters = radiusMeters,
            createdAt = createdAt,
            updatedAt = updatedAt,
            peopleNotified = peopleNotified,
            respondersCount = respondersCount,
            trackingMode = trackingMode,
            isBeaconActive = isBeaconActive
        )
    }

    companion object {
        fun fromEmergencySession(
            session: EmergencySession,
            victimUid: String,
            syncStatus: SosSyncStatus = SosSyncStatus.PENDING_SYNC,
            broadcastedAt: Long? = null
        ): CachedSosRequest {
            return CachedSosRequest(
                requestId = "SOS-CACHE-${session.emergencyId}",
                sessionId = session.id,
                emergencyId = session.emergencyId,
                victimUid = victimUid,
                status = session.status.name,
                emergencyType = session.emergencyType,
                priority = session.priority,
                latitude = session.latitude,
                longitude = session.longitude,
                locationStatus = session.locationStatus,
                locationAccuracyMeters = session.locationAccuracyMeters,
                radiusMeters = session.radiusMeters,
                peopleNotified = session.peopleNotified,
                respondersCount = session.respondersCount,
                trackingMode = session.trackingMode,
                isBeaconActive = session.isBeaconActive,
                createdAt = session.createdAt,
                updatedAt = session.updatedAt,
                syncStatus = syncStatus.name,
                broadcastedAt = broadcastedAt
            )
        }
    }
}

package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_sessions")
data class EmergencySession(
    @PrimaryKey val id: String,
    val emergencyId: String,
    val status: EmergencyStatus = EmergencyStatus.SAFE,
    val emergencyType: String = "CRITICAL / UNKNOWN",
    val priority: String = "CRITICAL",
    val latitude: Double = 37.7749,
    val longitude: Double = -122.4194,
    val locationStatus: String = "ACTIVE",
    val locationAccuracyMeters: Float = 5.0f,
    val radiusMeters: Double = 500.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val peopleNotified: Int = 0,
    val respondersCount: Int = 0,
    val activeResponderName: String? = null,
    val activeResponderDistanceMeters: Double? = null,
    val activeResponderEtaMinutes: Int? = null,
    val resolutionDurationSeconds: Long? = null
)

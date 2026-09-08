package com.example.model

enum class EvidenceType {
    PHOTO,
    AUDIO,
    VIDEO
}

data class EvidenceItem(
    val id: String,
    val emergencyId: String,
    val type: EvidenceType,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val uri: String? = null,
    val durationSeconds: Int = 0
)

data class EmergencyLog(
    val id: String,
    val emergencyId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val category: String = "INFO" // ALERT, RADIUS, RESPONDER, RESOLUTION
)

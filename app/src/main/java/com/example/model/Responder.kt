package com.example.model

data class Responder(
    val id: String,
    val name: String,
    val role: String,
    val latitude: Double,
    val longitude: Double,
    val status: ResponderStatus = ResponderStatus.AVAILABLE,
    val distanceMeters: Double = 0.0,
    val etaMinutes: Int = 1
)

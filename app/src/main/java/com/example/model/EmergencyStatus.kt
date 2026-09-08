package com.example.model

enum class EmergencyStatus(val label: String) {
    SAFE("SAFE"),
    ACTIVE("ACTIVE"),
    RESPONDER_FOUND("RESPONDER FOUND"),
    RESOLVED("RESOLVED")
}

enum class ResponderStatus(val label: String) {
    AVAILABLE("AVAILABLE"),
    ALERTED("ALERTED"),
    RESPONDING("RESPONDING"),
    RESOLVED("RESOLVED")
}

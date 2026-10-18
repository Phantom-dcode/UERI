package com.example.model.ueri

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Role-Based Access Control roles for UERI.
 */
enum class UserRole {
    CITIZEN,
    RESPONDER,
    POLICE,
    CONTROL_OPERATOR,
    ADMIN
}

/**
 * Firestore: users/{uid}
 */
@IgnoreExtraProperties
data class UserAccount(
    var uid: String = "",
    var displayName: String = "",
    var email: String = "",
    var mobileNumber: String = "",
    var countryIsoCode: String = "",
    var countryCallingCode: String = "",
    var nationalPhoneNumber: String = "",
    var normalizedPhoneNumber: String = "",
    var role: String = UserRole.CITIZEN.name,
    var responderId: String = "",
    var responderStatus: String = "PENDING_VERIFICATION", // PENDING_VERIFICATION, VERIFIED, REJECTED
    var phoneVerified: Boolean = false,
    var emailVerified: Boolean = false,
    var verifiedResponder: Boolean = false,
    var responderAvailability: Boolean = false,
    var pushToken: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var lastSeenAt: Long = System.currentTimeMillis()
)

/**
 * Firestore: responders/{uid}
 */
@IgnoreExtraProperties
data class ResponderProfile(
    var uid: String = "",
    var verificationStatus: String = "VERIFIED", // VERIFIED, PENDING, REJECTED
    var availability: Boolean = true,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var accuracy: Double = 5.0,
    var lastLocationAt: Long = System.currentTimeMillis(),
    var deviceBeaconEnabled: Boolean = true,
    var pushToken: String = "",
    var responderType: String = "PATROL" // POLICE, PARAMEDIC, COMMUNITY, RESCUE, PATROL
)

/**
 * Firestore: emergencies/{emergencyId}
 */
@IgnoreExtraProperties
data class EmergencyDoc(
    var emergencyId: String = "",
    var victimUid: String = "",
    var status: String = "ACTIVE", // ACTIVE, RESPONDER_FOUND, SAFE, RESOLVED
    var priority: String = "CRITICAL",
    var emergencyType: String = "CRITICAL_UNKNOWN",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var accuracy: Double = 5.0,
    var radiusMeters: Double = 500.0,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var lastLocationAt: Long = System.currentTimeMillis(),
    var nearbyResponderCount: Int = 0,
    var notifiedResponderCount: Int = 0,
    var acceptedResponderUid: String? = null,
    var trackingMode: String = "DIRECT_GPS", // DIRECT_GPS, NETWORK_ASSISTED, LAST_KNOWN
    var evidenceEnabled: Boolean = true,
    var policeEscalated: Boolean = false
)

/**
 * Firestore: emergencies/{emergencyId}/events/{eventId}
 */
@IgnoreExtraProperties
data class EmergencyEventDoc(
    var eventId: String = "",
    var type: String = "", // CREATED, RADIUS_EXPANDED, RESPONDER_ALERTED, RESPONDER_ACCEPTED, MARKED_SAFE, RESOLVED, LOCATION_UPDATED, EVIDENCE_ATTACHED
    var timestamp: Long = System.currentTimeMillis(),
    var actorUid: String = "",
    var actorRole: String = "CITIZEN",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var radiusMeters: Double = 0.0,
    var metadata: Map<String, Any> = emptyMap()
)

/**
 * Firestore: emergencyResponses/{responseId}
 */
@IgnoreExtraProperties
data class EmergencyResponseDoc(
    var responseId: String = "",
    var emergencyId: String = "",
    var responderUid: String = "",
    var status: String = "NOTIFIED", // NOTIFIED, VIEWED, RESPONDING, ARRIVED, CANCELLED
    var responderLatitude: Double = 0.0,
    var responderLongitude: Double = 0.0,
    var distanceMeters: Double = 0.0,
    var timestamp: Long = System.currentTimeMillis()
)

/**
 * Firestore: beaconTokens/{tokenId}
 */
@IgnoreExtraProperties
data class BeaconTokenDoc(
    var tokenId: String = "",
    var emergencyId: String = "",
    var rotatingToken: String = "",
    var issuedAt: Long = System.currentTimeMillis(),
    var expiresAt: Long = System.currentTimeMillis() + (1000 * 60 * 15), // 15 min expiration
    var status: String = "ACTIVE" // ACTIVE, EXPIRED, REVOKED
)

/**
 * Firestore: beaconDetections/{detectionId}
 */
@IgnoreExtraProperties
data class BeaconDetectionDoc(
    var detectionId: String = "",
    var tokenId: String = "",
    var emergencyId: String = "",
    var detectorUid: String = "",
    var detectorLatitude: Double = 0.0,
    var detectorLongitude: Double = 0.0,
    var detectorAccuracy: Double = 10.0,
    var rssi: Int = -65,
    var detectedAt: Long = System.currentTimeMillis(),
    var source: String = "BLE",
    var confidence: Double = 0.95
)

/**
 * Firestore: evidence/{evidenceId}
 */
@IgnoreExtraProperties
data class EvidenceDoc(
    var evidenceId: String = "",
    var emergencyId: String = "",
    var victimUid: String = "",
    var type: String = "PHOTO", // PHOTO, VIDEO, AUDIO
    var storagePath: String = "",
    var uploadedAt: Long = System.currentTimeMillis(),
    var capturedAt: Long = System.currentTimeMillis(),
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var checksum: String = "",
    var uploadStatus: String = "COMPLETED", // PENDING, COMPLETED, FAILED
    var accessClassification: String = "CONFIDENTIAL_EVIDENCE" // CONFIDENTIAL_EVIDENCE, POLICE_ONLY, RESTRICTED
)

/**
 * Firestore: auditLogs/{logId}
 */
@IgnoreExtraProperties
data class AuditLogDoc(
    var logId: String = "",
    var actorUid: String = "",
    var actorRole: String = "",
    var action: String = "",
    var emergencyId: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var source: String = "ANDROID_CLIENT",
    var metadata: Map<String, Any> = emptyMap()
)

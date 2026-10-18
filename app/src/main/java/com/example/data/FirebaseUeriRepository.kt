package com.example.data

import android.util.Log
import com.example.model.ueri.AuditLogDoc
import com.example.model.ueri.BeaconDetectionDoc
import com.example.model.ueri.BeaconTokenDoc
import com.example.model.ueri.EmergencyDoc
import com.example.model.ueri.EmergencyEventDoc
import com.example.model.ueri.EmergencyResponseDoc
import com.example.model.ueri.EvidenceDoc
import com.example.model.ueri.ResponderProfile
import com.example.model.ueri.UserAccount
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await

/**
 * Clean, production-grade repository implementing all 9 Cloud Firestore collections
 * requested for the Universal Emergency Response Infrastructure (UERI):
 * 1. users/{uid}
 * 2. responders/{uid}
 * 3. emergencies/{emergencyId}
 * 4. emergencies/{emergencyId}/events/{eventId}
 * 5. emergencyResponses/{responseId}
 * 6. beaconTokens/{tokenId}
 * 7. beaconDetections/{detectionId}
 * 8. evidence/{evidenceId}
 * 9. auditLogs/{logId}
 */
class FirebaseUeriRepository(
    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseFirestore instance unavailable: ${e.message}")
        null
    }
) {
    companion object {
        private const val TAG = "FirebaseUeriRepo"
        const val COLL_USERS = "users"
        const val COLL_RESPONDERS = "responders"
        const val COLL_EMERGENCIES = "emergencies"
        const val COLL_EVENTS = "events"
        const val COLL_EMERGENCY_RESPONSES = "emergencyResponses"
        const val COLL_BEACON_TOKENS = "beaconTokens"
        const val COLL_BEACON_DETECTIONS = "beaconDetections"
        const val COLL_EVIDENCE = "evidence"
        const val COLL_AUDIT_LOGS = "auditLogs"
    }

    fun isAvailable(): Boolean = firestore != null

    // =========================================================================
    // 1. users/{uid}
    // =========================================================================

    suspend fun saveUser(user: UserAccount): Boolean {
        val db = firestore ?: return false
        return try {
            db.collection(COLL_USERS).document(user.uid).set(user, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user ${user.uid}: ${e.message}")
            false
        }
    }

    fun observeUser(uid: String): Flow<UserAccount?> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            val docRef = db.collection(COLL_USERS).document(uid)
            val reg: ListenerRegistration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing user $uid", error)
                    return@addSnapshotListener
                }
                val user = snapshot?.toObject(UserAccount::class.java)
                trySend(user)
            }
            awaitClose { reg.remove() }
        }
    }

    // =========================================================================
    // 2. responders/{uid}
    // =========================================================================

    suspend fun saveResponderProfile(profile: ResponderProfile): Boolean {
        val db = firestore ?: return false
        return try {
            db.collection(COLL_RESPONDERS).document(profile.uid).set(profile, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving responder profile ${profile.uid}: ${e.message}")
            false
        }
    }

    fun observeResponders(): Flow<List<ResponderProfile>> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            val query = db.collection(COLL_RESPONDERS)
                .whereEqualTo("availability", true)
            val reg: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing responders", error)
                    return@addSnapshotListener
                }
                val profiles = snapshot?.documents?.mapNotNull { it.toObject(ResponderProfile::class.java) } ?: emptyList()
                trySend(profiles)
            }
            awaitClose { reg.remove() }
        }
    }

    // =========================================================================
    // 3. emergencies/{emergencyId}
    // =========================================================================

    suspend fun saveEmergency(doc: EmergencyDoc): Boolean {
        val db = firestore ?: return false
        return try {
            db.collection(COLL_EMERGENCIES).document(doc.emergencyId).set(doc, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving emergency ${doc.emergencyId}: ${e.message}")
            false
        }
    }

    fun observeActiveEmergencies(): Flow<List<EmergencyDoc>> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            val query = db.collection(COLL_EMERGENCIES)
                .whereIn("status", listOf("ACTIVE", "RESPONDER_FOUND"))
            val reg: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing active emergencies", error)
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents?.mapNotNull { it.toObject(EmergencyDoc::class.java) } ?: emptyList()
                trySend(docs)
            }
            awaitClose { reg.remove() }
        }
    }

    fun observeEmergency(emergencyId: String): Flow<EmergencyDoc?> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            val docRef = db.collection(COLL_EMERGENCIES).document(emergencyId)
            val reg: ListenerRegistration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing emergency $emergencyId", error)
                    return@addSnapshotListener
                }
                val doc = snapshot?.toObject(EmergencyDoc::class.java)
                trySend(doc)
            }
            awaitClose { reg.remove() }
        }
    }

    suspend fun updateEmergencyStatus(
        emergencyId: String,
        status: String,
        acceptedResponderUid: String? = null
    ): Boolean {
        val db = firestore ?: return false
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "updatedAt" to System.currentTimeMillis()
            )
            if (acceptedResponderUid != null) {
                updates["acceptedResponderUid"] = acceptedResponderUid
            }
            db.collection(COLL_EMERGENCIES).document(emergencyId).update(updates).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status for emergency $emergencyId: ${e.message}")
            false
        }
    }

    suspend fun updateEmergencyRadius(
        emergencyId: String,
        radiusMeters: Double,
        notifiedCount: Int,
        nearbyCount: Int
    ): Boolean {
        val db = firestore ?: return false
        return try {
            val updates = mapOf<String, Any>(
                "radiusMeters" to radiusMeters,
                "notifiedResponderCount" to notifiedCount,
                "nearbyResponderCount" to nearbyCount,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(COLL_EMERGENCIES).document(emergencyId).update(updates).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating radius for emergency $emergencyId: ${e.message}")
            false
        }
    }

    // =========================================================================
    // 4. emergencies/{emergencyId}/events/{eventId}
    // =========================================================================

    suspend fun addEmergencyEvent(emergencyId: String, event: EmergencyEventDoc): Boolean {
        val db = firestore ?: return false
        return try {
            val docId = if (event.eventId.isNotBlank()) event.eventId else "evt-${System.currentTimeMillis()}"
            val finalEvent = event.copy(eventId = docId)
            db.collection(COLL_EMERGENCIES)
                .document(emergencyId)
                .collection(COLL_EVENTS)
                .document(docId)
                .set(finalEvent)
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding event to emergency $emergencyId: ${e.message}")
            false
        }
    }

    fun observeEmergencyEvents(emergencyId: String): Flow<List<EmergencyEventDoc>> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            val query = db.collection(COLL_EMERGENCIES)
                .document(emergencyId)
                .collection(COLL_EVENTS)
                .orderBy("timestamp", Query.Direction.ASCENDING)
            val reg: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing events for $emergencyId", error)
                    return@addSnapshotListener
                }
                val events = snapshot?.documents?.mapNotNull { it.toObject(EmergencyEventDoc::class.java) } ?: emptyList()
                trySend(events)
            }
            awaitClose { reg.remove() }
        }
    }

    // =========================================================================
    // 5. emergencyResponses/{responseId}
    // =========================================================================

    suspend fun recordEmergencyResponse(response: EmergencyResponseDoc): Boolean {
        val db = firestore ?: return false
        return try {
            val id = if (response.responseId.isNotBlank()) response.responseId else "${response.emergencyId}_${response.responderUid}"
            val finalDoc = response.copy(responseId = id)
            db.collection(COLL_EMERGENCY_RESPONSES).document(id).set(finalDoc, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error recording emergency response: ${e.message}")
            false
        }
    }

    fun observeResponsesForEmergency(emergencyId: String): Flow<List<EmergencyResponseDoc>> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            val query = db.collection(COLL_EMERGENCY_RESPONSES)
                .whereEqualTo("emergencyId", emergencyId)
            val reg: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing responses for $emergencyId", error)
                    return@addSnapshotListener
                }
                val responses = snapshot?.documents?.mapNotNull { it.toObject(EmergencyResponseDoc::class.java) } ?: emptyList()
                trySend(responses)
            }
            awaitClose { reg.remove() }
        }
    }

    // =========================================================================
    // 6. beaconTokens/{tokenId}
    // =========================================================================

    suspend fun issueBeaconToken(token: BeaconTokenDoc): Boolean {
        val db = firestore ?: return false
        return try {
            val id = if (token.tokenId.isNotBlank()) token.tokenId else "tok-${System.currentTimeMillis()}"
            val finalToken = token.copy(tokenId = id)
            db.collection(COLL_BEACON_TOKENS).document(id).set(finalToken).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error issuing beacon token: ${e.message}")
            false
        }
    }

    suspend fun resolveActiveBeaconToken(rotatingToken: String): BeaconTokenDoc? {
        val db = firestore ?: return null
        return try {
            val query = db.collection(COLL_BEACON_TOKENS)
                .whereEqualTo("rotatingToken", rotatingToken)
                .whereEqualTo("status", "ACTIVE")
                .limit(1)
            val snapshot = query.get().await()
            snapshot.documents.firstOrNull()?.toObject(BeaconTokenDoc::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving active beacon token: ${e.message}")
            null
        }
    }

    suspend fun revokeBeaconTokensForEmergency(emergencyId: String): Boolean {
        val db = firestore ?: return false
        return try {
            val snapshot = db.collection(COLL_BEACON_TOKENS)
                .whereEqualTo("emergencyId", emergencyId)
                .whereEqualTo("status", "ACTIVE")
                .get()
                .await()
            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.update(doc.reference, mapOf("status" to "EXPIRED"))
            }
            batch.commit().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error revoking beacon tokens for $emergencyId: ${e.message}")
            false
        }
    }

    // =========================================================================
    // 7. beaconDetections/{detectionId}
    // =========================================================================

    suspend fun recordBeaconDetection(detection: BeaconDetectionDoc): Boolean {
        val db = firestore ?: return false
        return try {
            val id = if (detection.detectionId.isNotBlank()) detection.detectionId else "det-${System.currentTimeMillis()}"
            val finalDetection = detection.copy(detectionId = id)
            db.collection(COLL_BEACON_DETECTIONS).document(id).set(finalDetection).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error recording beacon detection: ${e.message}")
            false
        }
    }

    // =========================================================================
    // 8. evidence/{evidenceId}
    // =========================================================================

    suspend fun saveEvidence(evidence: EvidenceDoc): Boolean {
        val db = firestore ?: return false
        return try {
            val id = if (evidence.evidenceId.isNotBlank()) evidence.evidenceId else "evi-${System.currentTimeMillis()}"
            val finalEvidence = evidence.copy(evidenceId = id)
            db.collection(COLL_EVIDENCE).document(id).set(finalEvidence).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving evidence doc: ${e.message}")
            false
        }
    }

    fun observeEvidence(emergencyId: String): Flow<List<EvidenceDoc>> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            val query = db.collection(COLL_EVIDENCE)
                .whereEqualTo("emergencyId", emergencyId)
                .orderBy("uploadedAt", Query.Direction.DESCENDING)
            val reg: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing evidence for $emergencyId", error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toObject(EvidenceDoc::class.java) } ?: emptyList()
                trySend(items)
            }
            awaitClose { reg.remove() }
        }
    }

    // =========================================================================
    // 9. auditLogs/{logId}
    // =========================================================================

    suspend fun recordAuditLog(log: AuditLogDoc): Boolean {
        val db = firestore ?: return false
        return try {
            val id = if (log.logId.isNotBlank()) log.logId else "log-${System.currentTimeMillis()}"
            val finalLog = log.copy(logId = id)
            db.collection(COLL_AUDIT_LOGS).document(id).set(finalLog).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error recording audit log: ${e.message}")
            false
        }
    }

    fun observeAuditLogs(emergencyId: String? = null): Flow<List<AuditLogDoc>> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            val baseQuery = db.collection(COLL_AUDIT_LOGS)
            val query = if (emergencyId != null) {
                baseQuery.whereEqualTo("emergencyId", emergencyId).orderBy("timestamp", Query.Direction.DESCENDING)
            } else {
                baseQuery.orderBy("timestamp", Query.Direction.DESCENDING).limit(50)
            }
            val reg: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing audit logs", error)
                    return@addSnapshotListener
                }
                val logs = snapshot?.documents?.mapNotNull { it.toObject(AuditLogDoc::class.java) } ?: emptyList()
                trySend(logs)
            }
            awaitClose { reg.remove() }
        }
    }
}

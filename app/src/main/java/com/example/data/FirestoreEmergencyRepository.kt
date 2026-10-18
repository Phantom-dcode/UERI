package com.example.data

import android.util.Log
import com.example.model.EmergencySession
import com.example.model.EmergencyStatus
import com.example.model.Responder
import com.example.model.ResponderStatus
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Data repository class handling real-time synchronization of emergency events
 * and tactical responder locations using Firebase Cloud Firestore.
 *
 * Provides real-time reactive [Flow] streams and suspend mutations for:
 * - Active emergency events, status updates, and dynamic radius expansion.
 * - Responder locations, status updates, telemetry, and live ETA tracking.
 */
class FirestoreEmergencyRepository(
    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseFirestore instance unavailable or FirebaseApp not initialized: ${e.message}")
        null
    }
) {

    companion object {
        private const val TAG = "FirestoreEmergencyRepo"
        const val COLLECTION_EMERGENCIES = "emergencies"
        const val COLLECTION_RESPONDERS = "responders"
    }

    val ueriRepo = FirebaseUeriRepository()

    /**
     * Returns true if Firebase Firestore has been initialized and is ready for use.
     */
    fun isAvailable(): Boolean = firestore != null

    // =========================================================================
    // EMERGENCY EVENTS - REAL-TIME FLOWS
    // =========================================================================

    /**
     * Real-time stream of all active emergency events (ACTIVE or RESPONDER_FOUND).
     * Automatically updates when new incidents are created, radius expands, or responders arrive.
     */
    fun observeActiveEmergencies(): Flow<List<EmergencySession>> {
        val db = firestore ?: run {
            Log.w(TAG, "Firestore unavailable, returning emptyFlow for active emergencies")
            return emptyFlow()
        }

        return callbackFlow {
            val query = db.collection(COLLECTION_EMERGENCIES)
                .whereIn("status", listOf(EmergencyStatus.ACTIVE.name, EmergencyStatus.RESPONDER_FOUND.name))

            val listener: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error in observeActiveEmergencies listener", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val emergencies = snapshot.documents.mapNotNull { doc ->
                        documentToEmergencySession(doc)
                    }
                    trySend(emergencies)
                }
            }

            awaitClose {
                Log.d(TAG, "Removing active emergencies listener")
                listener.remove()
            }
        }
    }

    /**
     * Real-time stream of a specific emergency incident by its unique ID.
     */
    fun observeEmergencyById(emergencyId: String): Flow<EmergencySession?> {
        val db = firestore ?: return emptyFlow()

        return callbackFlow {
            val docRef = db.collection(COLLECTION_EMERGENCIES).document(emergencyId)
            val listener: ListenerRegistration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing emergency $emergencyId", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    trySend(documentToEmergencySession(snapshot))
                } else {
                    trySend(null)
                }
            }

            awaitClose {
                listener.remove()
            }
        }
    }

    /**
     * Real-time stream of all emergency events sorted by updated timestamp descending.
     */
    fun observeAllEmergencies(): Flow<List<EmergencySession>> {
        val db = firestore ?: return emptyFlow()

        return callbackFlow {
            val query = db.collection(COLLECTION_EMERGENCIES)
                .orderBy("updatedAt", Query.Direction.DESCENDING)

            val listener: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error in observeAllEmergencies listener", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val emergencies = snapshot.documents.mapNotNull { doc ->
                        documentToEmergencySession(doc)
                    }
                    trySend(emergencies)
                }
            }

            awaitClose {
                listener.remove()
            }
        }
    }

    // =========================================================================
    // EMERGENCY EVENTS - MUTATIONS
    // =========================================================================

    /**
     * Publishes a new emergency incident to Firestore in real time.
     */
    suspend fun publishEmergencyEvent(session: EmergencySession): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        return try {
            val docRef = db.collection(COLLECTION_EMERGENCIES).document(session.id)
            docRef.set(sessionToMap(session), SetOptions.merge()).awaitTask()
            Log.d(TAG, "Successfully published emergency event: ${session.emergencyId} (${session.id})")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish emergency event: ${session.emergencyId}", e)
            Result.failure(e)
        }
    }

    /**
     * Updates status, active responder info, and timestamp of an emergency event.
     */
    suspend fun updateEmergencyStatus(
        sessionId: String,
        status: EmergencyStatus,
        responderName: String? = null,
        responderDistance: Double? = null,
        etaMinutes: Int? = null
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        return try {
            val updates = mutableMapOf<String, Any?>(
                "status" to status.name,
                "updatedAt" to System.currentTimeMillis()
            )
            if (responderName != null) updates["activeResponderName"] = responderName
            if (responderDistance != null) updates["activeResponderDistanceMeters"] = responderDistance
            if (etaMinutes != null) updates["activeResponderEtaMinutes"] = etaMinutes

            db.collection(COLLECTION_EMERGENCIES).document(sessionId)
                .set(updates, SetOptions.merge())
                .awaitTask()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update emergency status for $sessionId", e)
            Result.failure(e)
        }
    }

    /**
     * Updates dynamic radius expansion data for an ongoing incident.
     */
    suspend fun updateEmergencyRadius(
        sessionId: String,
        radiusMeters: Double,
        peopleNotified: Int,
        respondersCount: Int
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        return try {
            val updates = mapOf(
                "radiusMeters" to radiusMeters,
                "peopleNotified" to peopleNotified,
                "respondersCount" to respondersCount,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_EMERGENCIES).document(sessionId)
                .set(updates, SetOptions.merge())
                .awaitTask()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update emergency radius for $sessionId", e)
            Result.failure(e)
        }
    }

    /**
     * Marks an emergency incident as resolved in Firestore.
     */
    suspend fun markEmergencyResolved(
        sessionId: String,
        durationSeconds: Long
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        return try {
            val now = System.currentTimeMillis()
            val updates = mapOf(
                "status" to EmergencyStatus.RESOLVED.name,
                "resolvedAt" to now,
                "updatedAt" to now,
                "resolutionDurationSeconds" to durationSeconds
            )
            db.collection(COLLECTION_EMERGENCIES).document(sessionId)
                .set(updates, SetOptions.merge())
                .awaitTask()
            Log.d(TAG, "Marked emergency $sessionId as RESOLVED in Firestore")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark emergency $sessionId as resolved", e)
            Result.failure(e)
        }
    }

    /**
     * ATOMIC ACCEPTANCE TRANSACTION
     *
     * Prevents multiple responders from simultaneously becoming the primary accepted responder.
     * Uses backend-side Firestore transaction logic:
     * 1. Reads emergency status and acceptedResponderUid atomically.
     * 2. Checks if already claimed by another responder or resolved.
     * 3. Sets acceptedResponderUid and changes status to RESPONDER_FOUND.
     * 4. Creates emergencyResponses with status = RESPONDING.
     * 5. Creates event RESPONDER_ACCEPTED in emergencies/{emergencyId}/events/{eventId}.
     * 6. Records immutable auditLog in auditLogs/{logId}.
     * 7. Updates responder availability and status in responders/{uid}.
     */
    suspend fun atomicAcceptEmergency(
        emergencyId: String,
        responder: Responder
    ): Result<EmergencySession> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        return try {
            val emergencyRef = db.collection(COLLECTION_EMERGENCIES).document(emergencyId)
            val responseRef = db.collection("emergencyResponses").document("${emergencyId}_${responder.id}")
            val eventId = "evt-${System.currentTimeMillis()}"
            val eventRef = emergencyRef.collection("events").document(eventId)
            val logId = "log-${System.currentTimeMillis()}"
            val logRef = db.collection("auditLogs").document(logId)
            val responderRef = db.collection(COLLECTION_RESPONDERS).document(responder.id)

            val updatedSession = db.runTransaction<EmergencySession> { transaction ->
                val snapshot = transaction.get(emergencyRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Emergency $emergencyId not found in Firestore.")
                }

                val currentStatus = snapshot.getString("status") ?: EmergencyStatus.ACTIVE.name
                val acceptedUid = snapshot.getString("acceptedResponderUid")

                // Atomic Concurrency Guard: prevent simultaneous claims
                if (acceptedUid != null && acceptedUid.isNotBlank() && acceptedUid != responder.id && currentStatus == EmergencyStatus.RESPONDER_FOUND.name) {
                    throw IllegalStateException("CONFLICT: Emergency already accepted by responder '$acceptedUid'.")
                }
                if (currentStatus == EmergencyStatus.RESOLVED.name || currentStatus == EmergencyStatus.SAFE.name) {
                    throw IllegalStateException("CONFLICT: Emergency has already been marked SAFE or RESOLVED.")
                }

                val now = System.currentTimeMillis()

                // 1. Update emergency document
                val emergencyUpdates = mutableMapOf<String, Any?>(
                    "status" to EmergencyStatus.RESPONDER_FOUND.name,
                    "acceptedResponderUid" to responder.id,
                    "activeResponderName" to responder.name,
                    "activeResponderDistanceMeters" to responder.distanceMeters,
                    "activeResponderEtaMinutes" to responder.etaMinutes,
                    "updatedAt" to now
                )
                transaction.update(emergencyRef, emergencyUpdates)

                // 2. Create/update emergencyResponses doc
                transaction.set(
                    responseRef,
                    mapOf(
                        "responseId" to "${emergencyId}_${responder.id}",
                        "emergencyId" to emergencyId,
                        "responderUid" to responder.id,
                        "status" to "RESPONDING",
                        "responderLatitude" to responder.latitude,
                        "responderLongitude" to responder.longitude,
                        "distanceMeters" to responder.distanceMeters,
                        "timestamp" to now
                    ),
                    SetOptions.merge()
                )

                // 3. Create event doc: emergencies/{id}/events/{evtId}
                transaction.set(
                    eventRef,
                    mapOf(
                        "eventId" to eventId,
                        "type" to "RESPONDER_ACCEPTED",
                        "timestamp" to now,
                        "actorUid" to responder.id,
                        "actorRole" to "RESPONDER",
                        "latitude" to responder.latitude,
                        "longitude" to responder.longitude,
                        "radiusMeters" to (snapshot.getDouble("radiusMeters") ?: 500.0),
                        "metadata" to mapOf(
                            "responderName" to responder.name,
                            "responderRole" to responder.role,
                            "etaMinutes" to responder.etaMinutes,
                            "distanceMeters" to responder.distanceMeters
                        )
                    )
                )

                // 4. Record auditLog
                transaction.set(
                    logRef,
                    mapOf(
                        "logId" to logId,
                        "actorUid" to responder.id,
                        "actorRole" to "RESPONDER",
                        "action" to "DISPATCH_ACCEPTED",
                        "emergencyId" to emergencyId,
                        "timestamp" to now,
                        "source" to "RESPONDER_PORTAL",
                        "metadata" to mapOf(
                            "responderName" to responder.name,
                            "etaMinutes" to responder.etaMinutes,
                            "distanceMeters" to responder.distanceMeters
                        )
                    )
                )

                // 5. Update responder document
                transaction.set(
                    responderRef,
                    mapOf(
                        "id" to responder.id,
                        "uid" to responder.id,
                        "status" to ResponderStatus.RESPONDING.name,
                        "availability" to false,
                        "distanceMeters" to responder.distanceMeters,
                        "etaMinutes" to responder.etaMinutes,
                        "lastLocationAt" to now,
                        "updatedAt" to now
                    ),
                    SetOptions.merge()
                )

                val parsed = documentToEmergencySession(snapshot) ?: EmergencySession(
                    id = emergencyId,
                    emergencyId = emergencyId
                )
                parsed.copy(
                    status = EmergencyStatus.RESPONDER_FOUND,
                    acceptedResponderUid = responder.id,
                    activeResponderName = responder.name,
                    activeResponderDistanceMeters = responder.distanceMeters,
                    activeResponderEtaMinutes = responder.etaMinutes,
                    updatedAt = now
                )
            }.awaitTask()

            Log.d(TAG, "Atomic accept succeeded for emergency $emergencyId by ${responder.id}")
            Result.success(updatedSession)
        } catch (e: Exception) {
            Log.e(TAG, "Atomic accept failed for emergency $emergencyId: ${e.message}", e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // RESPONDER LOCATIONS - REAL-TIME FLOWS
    // =========================================================================

    /**
     * Real-time stream of all tactical responders and their live locations.
     */
    fun observeResponders(): Flow<List<Responder>> {
        val db = firestore ?: run {
            Log.w(TAG, "Firestore unavailable, returning emptyFlow for responders")
            return emptyFlow()
        }

        return callbackFlow {
            val collectionRef = db.collection(COLLECTION_RESPONDERS)
            val listener: ListenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error in observeResponders listener", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val responders = snapshot.documents.mapNotNull { doc ->
                        documentToResponder(doc)
                    }
                    trySend(responders)
                }
            }

            awaitClose {
                Log.d(TAG, "Removing responders listener")
                listener.remove()
            }
        }
    }

    /**
     * Real-time stream of a specific responder's location and telemetry by ID.
     */
    fun observeResponderLocation(responderId: String): Flow<Responder?> {
        val db = firestore ?: return emptyFlow()

        return callbackFlow {
            val docRef = db.collection(COLLECTION_RESPONDERS).document(responderId)
            val listener: ListenerRegistration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing responder $responderId", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    trySend(documentToResponder(snapshot))
                } else {
                    trySend(null)
                }
            }

            awaitClose {
                listener.remove()
            }
        }
    }

    // =========================================================================
    // RESPONDER LOCATIONS - MUTATIONS
    // =========================================================================

    /**
     * Publishes or updates a responder's location and status in Firestore.
     */
    suspend fun updateResponderLocation(responder: Responder): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        return try {
            val docRef = db.collection(COLLECTION_RESPONDERS).document(responder.id)
            docRef.set(responderToMap(responder), SetOptions.merge()).awaitTask()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update responder location for ${responder.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Updates telemetry fields for a responder (latitude, longitude, status, distance, ETA).
     */
    suspend fun updateResponderTelemetry(
        responderId: String,
        latitude: Double,
        longitude: Double,
        status: ResponderStatus? = null,
        distanceMeters: Double? = null,
        etaMinutes: Int? = null
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        return try {
            val updates = mutableMapOf<String, Any?>(
                "latitude" to latitude,
                "longitude" to longitude,
                "updatedAt" to System.currentTimeMillis()
            )
            if (status != null) updates["status"] = status.name
            if (distanceMeters != null) updates["distanceMeters"] = distanceMeters
            if (etaMinutes != null) updates["etaMinutes"] = etaMinutes

            db.collection(COLLECTION_RESPONDERS).document(responderId)
                .set(updates, SetOptions.merge())
                .awaitTask()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update responder telemetry for $responderId", e)
            Result.failure(e)
        }
    }

    /**
     * Batch synchronizes or seeds a list of initial responders.
     */
    suspend fun syncInitialResponders(responders: List<Responder>): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        return try {
            val batch = db.batch()
            for (responder in responders) {
                val docRef = db.collection(COLLECTION_RESPONDERS).document(responder.id)
                batch.set(docRef, responderToMap(responder), SetOptions.merge())
            }
            batch.commit().awaitTask()
            Log.d(TAG, "Batch synchronized ${responders.size} responders to Firestore")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to batch sync responders", e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // DOCUMENT PARSING & SERIALIZATION
    // =========================================================================

    private fun documentToEmergencySession(doc: DocumentSnapshot): EmergencySession? {
        return try {
            val statusString = doc.getString("status") ?: EmergencyStatus.ACTIVE.name
            val parsedStatus = try {
                EmergencyStatus.valueOf(statusString)
            } catch (_: Exception) {
                EmergencyStatus.ACTIVE
            }

            EmergencySession(
                id = doc.getString("id") ?: doc.id,
                emergencyId = doc.getString("emergencyId") ?: doc.id,
                status = parsedStatus,
                emergencyType = doc.getString("emergencyType") ?: "CRITICAL / UNKNOWN",
                priority = doc.getString("priority") ?: "CRITICAL",
                latitude = doc.getDouble("latitude") ?: 37.7749,
                longitude = doc.getDouble("longitude") ?: -122.4194,
                locationStatus = doc.getString("locationStatus") ?: "ACTIVE",
                locationAccuracyMeters = doc.getDouble("locationAccuracyMeters")?.toFloat() ?: 5.0f,
                radiusMeters = doc.getDouble("radiusMeters") ?: 500.0,
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                resolvedAt = doc.getLong("resolvedAt"),
                peopleNotified = doc.getLong("peopleNotified")?.toInt() ?: 0,
                respondersCount = doc.getLong("respondersCount")?.toInt() ?: 0,
                activeResponderName = doc.getString("activeResponderName"),
                activeResponderDistanceMeters = doc.getDouble("activeResponderDistanceMeters"),
                activeResponderEtaMinutes = doc.getLong("activeResponderEtaMinutes")?.toInt(),
                resolutionDurationSeconds = doc.getLong("resolutionDurationSeconds"),
                acceptedResponderUid = doc.getString("acceptedResponderUid"),
                trackingMode = doc.getString("trackingMode") ?: "DIRECT_GPS",
                isBeaconActive = doc.getBoolean("isBeaconActive") ?: false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse EmergencySession document: ${doc.id}", e)
            null
        }
    }

    private fun documentToResponder(doc: DocumentSnapshot): Responder? {
        return try {
            val statusString = doc.getString("status") ?: ResponderStatus.AVAILABLE.name
            val parsedStatus = try {
                ResponderStatus.valueOf(statusString)
            } catch (_: Exception) {
                ResponderStatus.AVAILABLE
            }

            Responder(
                id = doc.getString("id") ?: doc.id,
                name = doc.getString("name") ?: "Tactical Unit",
                role = doc.getString("role") ?: "Tactical Responder",
                latitude = doc.getDouble("latitude") ?: 37.7749,
                longitude = doc.getDouble("longitude") ?: -122.4194,
                status = parsedStatus,
                distanceMeters = doc.getDouble("distanceMeters") ?: 0.0,
                etaMinutes = doc.getLong("etaMinutes")?.toInt() ?: 1
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Responder document: ${doc.id}", e)
            null
        }
    }

    private fun sessionToMap(session: EmergencySession): Map<String, Any?> {
        return mapOf(
            "id" to session.id,
            "emergencyId" to session.emergencyId,
            "victimUid" to session.id,
            "status" to session.status.name,
            "emergencyType" to session.emergencyType,
            "priority" to session.priority,
            "latitude" to session.latitude,
            "longitude" to session.longitude,
            "accuracy" to session.locationAccuracyMeters.toDouble(),
            "locationStatus" to session.locationStatus,
            "locationAccuracyMeters" to session.locationAccuracyMeters,
            "radiusMeters" to session.radiusMeters,
            "createdAt" to session.createdAt,
            "updatedAt" to session.updatedAt,
            "lastLocationAt" to session.updatedAt,
            "resolvedAt" to session.resolvedAt,
            "peopleNotified" to session.peopleNotified,
            "notifiedResponderCount" to session.peopleNotified,
            "respondersCount" to session.respondersCount,
            "nearbyResponderCount" to session.respondersCount,
            "acceptedResponderUid" to (session.acceptedResponderUid ?: session.activeResponderName),
            "activeResponderName" to session.activeResponderName,
            "activeResponderDistanceMeters" to session.activeResponderDistanceMeters,
            "activeResponderEtaMinutes" to session.activeResponderEtaMinutes,
            "resolutionDurationSeconds" to session.resolutionDurationSeconds,
            "trackingMode" to session.trackingMode,
            "isBeaconActive" to session.isBeaconActive,
            "evidenceEnabled" to true,
            "policeEscalated" to (session.priority == "CRITICAL")
        )
    }

    private fun responderToMap(responder: Responder): Map<String, Any?> {
        return mapOf(
            "id" to responder.id,
            "uid" to responder.id,
            "name" to responder.name,
            "role" to responder.role,
            "responderType" to responder.role,
            "latitude" to responder.latitude,
            "longitude" to responder.longitude,
            "accuracy" to 5.0,
            "status" to responder.status.name,
            "verificationStatus" to "VERIFIED",
            "availability" to (responder.status == ResponderStatus.AVAILABLE || responder.status == ResponderStatus.ALERTED),
            "deviceBeaconEnabled" to true,
            "distanceMeters" to responder.distanceMeters,
            "etaMinutes" to responder.etaMinutes,
            "lastLocationAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis()
        )
    }

    private suspend fun <T> Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                continuation.resume(result)
            }
            addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
            addOnCanceledListener {
                continuation.cancel()
            }
        }
}

package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.CachedSosRequest
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for managing locally cached SOS requests when the device is offline
 * and querying pending requests for automatic broadcast upon connectivity restoration.
 */
@Dao
interface OfflineSosDao {

    @Query("SELECT * FROM cached_sos_requests ORDER BY createdAt DESC")
    fun observeAllCachedRequests(): Flow<List<CachedSosRequest>>

    @Query(
        "SELECT * FROM cached_sos_requests " +
            "WHERE syncStatus = 'PENDING_SYNC' OR syncStatus = 'FAILED' " +
            "ORDER BY createdAt ASC"
    )
    fun observePendingSosRequests(): Flow<List<CachedSosRequest>>

    @Query(
        "SELECT * FROM cached_sos_requests " +
            "WHERE syncStatus = 'PENDING_SYNC' OR syncStatus = 'FAILED' " +
            "ORDER BY createdAt ASC"
    )
    suspend fun getPendingSosRequests(): List<CachedSosRequest>

    @Query(
        "SELECT COUNT(*) FROM cached_sos_requests " +
            "WHERE syncStatus = 'PENDING_SYNC' OR syncStatus = 'FAILED'"
    )
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM cached_sos_requests WHERE emergencyId = :emergencyId LIMIT 1")
    suspend fun getByEmergencyId(emergencyId: String): CachedSosRequest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedSos(request: CachedSosRequest)

    @Update
    suspend fun updateCachedSos(request: CachedSosRequest)

    @Query(
        "UPDATE cached_sos_requests " +
            "SET syncStatus = 'BROADCASTED', broadcastedAt = :broadcastedAt, " +
            "updatedAt = :broadcastedAt, errorMessage = NULL " +
            "WHERE requestId = :requestId"
    )
    suspend fun markBroadcasted(requestId: String, broadcastedAt: Long = System.currentTimeMillis())

    @Query(
        "UPDATE cached_sos_requests " +
            "SET syncStatus = :status, retryCount = retryCount + 1, " +
            "lastAttemptAt = :attemptAt, updatedAt = :attemptAt, errorMessage = :error " +
            "WHERE requestId = :requestId"
    )
    suspend fun updateSyncAttempt(
        requestId: String,
        status: String,
        attemptAt: Long = System.currentTimeMillis(),
        error: String? = null
    )

    @Query(
        "UPDATE cached_sos_requests " +
            "SET radiusMeters = :radiusMeters, peopleNotified = :peopleNotified, " +
            "respondersCount = :respondersCount, updatedAt = :updatedAt " +
            "WHERE emergencyId = :emergencyId AND syncStatus != 'BROADCASTED'"
    )
    suspend fun updatePendingRadius(
        emergencyId: String,
        radiusMeters: Double,
        peopleNotified: Int,
        respondersCount: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE cached_sos_requests " +
            "SET status = :sessionStatus, updatedAt = :updatedAt " +
            "WHERE emergencyId = :emergencyId"
    )
    suspend fun updateSessionStatus(
        emergencyId: String,
        sessionStatus: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM cached_sos_requests WHERE syncStatus = 'BROADCASTED'")
    suspend fun clearBroadcasted()

    @Query("DELETE FROM cached_sos_requests")
    suspend fun clearAll()
}

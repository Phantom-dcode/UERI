package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.EmergencySession
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyDao {
    @Query("SELECT * FROM emergency_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<EmergencySession>>

    @Query("SELECT * FROM emergency_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): EmergencySession?

    @Query("SELECT * FROM emergency_sessions WHERE status = 'ACTIVE' OR status = 'RESPONDER_FOUND' LIMIT 1")
    fun getActiveSession(): Flow<EmergencySession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: EmergencySession)

    @Update
    suspend fun updateSession(session: EmergencySession)

    @Query("DELETE FROM emergency_sessions")
    suspend fun clearAll()
}

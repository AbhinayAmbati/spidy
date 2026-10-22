package com.example.agent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AuditEntity)

    @Query("SELECT * FROM audit_events ORDER BY timestampMillis DESC")
    suspend fun all(): List<AuditEntity>

    @Query("SELECT COUNT(*) FROM audit_events")
    fun observeCount(): Flow<Int>

    /** Deletes everything older than [cutoffMillis]; returns how many rows went. */
    @Query("DELETE FROM audit_events WHERE timestampMillis < :cutoffMillis")
    suspend fun pruneBefore(cutoffMillis: Long): Int

    @Query("DELETE FROM audit_events")
    suspend fun clear()
}

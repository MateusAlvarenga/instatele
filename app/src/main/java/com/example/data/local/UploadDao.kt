package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: UploadRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<UploadRecordEntity>)

    @Update
    suspend fun updateRecord(record: UploadRecordEntity)

    @Query("SELECT * FROM upload_records ORDER BY timestamp DESC LIMIT 300")
    fun getAllRecordsFlow(): Flow<List<UploadRecordEntity>>

    @Query("SELECT * FROM upload_records WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getRecordsForSessionFlow(sessionId: String): Flow<List<UploadRecordEntity>>

    @Query("SELECT COUNT(*) FROM upload_records WHERE status = 'SUCCESS'")
    fun getTotalSuccessCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM upload_records")
    fun getTotalFilesCountFlow(): Flow<Int>

    @Query("DELETE FROM upload_records")
    suspend fun clearAllRecords()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: UploadSessionEntity)

    @Update
    suspend fun updateSession(session: UploadSessionEntity)

    @Query("SELECT * FROM upload_sessions ORDER BY startTime DESC")
    fun getAllSessionsFlow(): Flow<List<UploadSessionEntity>>

    @Query("SELECT * FROM upload_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): UploadSessionEntity?

    @Query("DELETE FROM upload_sessions")
    suspend fun clearAllSessions()
}

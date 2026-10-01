package com.example.data.repository

import com.example.data.local.UploadDao
import com.example.data.local.UploadRecordEntity
import com.example.data.local.UploadSessionEntity
import kotlinx.coroutines.flow.Flow

class UploadRepository(private val uploadDao: UploadDao) {

    val allRecordsFlow: Flow<List<UploadRecordEntity>> = uploadDao.getAllRecordsFlow()
    val allSessionsFlow: Flow<List<UploadSessionEntity>> = uploadDao.getAllSessionsFlow()
    val totalSuccessCountFlow: Flow<Int> = uploadDao.getTotalSuccessCountFlow()
    val totalFilesCountFlow: Flow<Int> = uploadDao.getTotalFilesCountFlow()

    suspend fun insertRecord(record: UploadRecordEntity): Long =
        uploadDao.insertRecord(record)

    suspend fun updateRecord(record: UploadRecordEntity) =
        uploadDao.updateRecord(record)

    suspend fun insertSession(session: UploadSessionEntity) =
        uploadDao.insertSession(session)

    suspend fun updateSession(session: UploadSessionEntity) =
        uploadDao.updateSession(session)

    suspend fun clearHistory() {
        uploadDao.clearAllRecords()
        uploadDao.clearAllSessions()
    }
}

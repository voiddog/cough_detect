package org.voiddog.coughdetect.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CoughRecordDao {

    @Query("SELECT * FROM cough_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<CoughRecord>>

    @Query("SELECT * FROM cough_records WHERE id = :id")
    suspend fun getRecordById(id: Long): CoughRecord?

    @Query("SELECT COUNT(*) FROM cough_records")
    suspend fun getRecordCount(): Int

    @Query("SELECT COUNT(*) FROM cough_records WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getRecordCountInTimeRange(startTime: Long, endTime: Long): Int

    @Query("SELECT * FROM cough_records WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getRecordsInTimeRange(startTime: Long, endTime: Long): Flow<List<CoughRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CoughRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<CoughRecord>)

    @Update
    suspend fun updateRecord(record: CoughRecord)

    @Delete
    suspend fun deleteRecord(record: CoughRecord)

    @Query("DELETE FROM cough_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM cough_records")
    suspend fun deleteAllRecords()

    @Query("SELECT * FROM cough_records WHERE confidence >= :minConfidence ORDER BY timestamp DESC")
    fun getRecordsWithMinConfidence(minConfidence: Float): Flow<List<CoughRecord>>

    @Query("SELECT AVG(confidence) FROM cough_records")
    suspend fun getAverageConfidence(): Float?

    @Query("SELECT MAX(confidence) FROM cough_records")
    suspend fun getMaxConfidence(): Float?

    @Query("SELECT * FROM cough_records ORDER BY confidence DESC LIMIT :limit")
    suspend fun getTopConfidenceRecords(limit: Int): List<CoughRecord>

    @Query("UPDATE cough_records SET audioFilePath = '' WHERE audioFilePath IN (:filePaths)")
    suspend fun clearAudioFilePaths(filePaths: List<String>): Int
}

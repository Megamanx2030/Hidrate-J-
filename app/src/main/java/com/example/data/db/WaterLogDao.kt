package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: WaterLog): Long

    @Query("SELECT * FROM water_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<WaterLog>>

    @Query("SELECT * FROM water_logs WHERE dateString = :dateString ORDER BY timestamp DESC")
    fun getLogsForDate(dateString: String): Flow<List<WaterLog>>

    @Query("SELECT SUM(amountMl) FROM water_logs WHERE dateString = :dateString")
    fun getDailySumMl(dateString: String): Flow<Int?>

    @Query("SELECT SUM(amountMl) FROM water_logs WHERE timestamp >= :startTimeMs AND timestamp <= :endTimeMs")
    fun getSumMlForPeriod(startTimeMs: Long, endTimeMs: Long): Flow<Int?>

    @Query("SELECT * FROM water_logs WHERE dateString = :dateString")
    suspend fun getLogsForDateOnce(dateString: String): List<WaterLog>

    @Query("DELETE FROM water_logs WHERE dateString = :dateString")
    suspend fun deleteLogsForDate(dateString: String)

    @Query("DELETE FROM water_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)

    @Query("DELETE FROM water_logs")
    suspend fun deleteAllLogs()
}

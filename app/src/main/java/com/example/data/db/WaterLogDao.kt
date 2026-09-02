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

    /**
     * SO O PERIODO ESCOLHIDO, nunca a tabela inteira.
     *
     * Antes existia um getAllLogs() que a tela de Historico mantinha vivo o
     * tempo todo. Ele trazia TODOS os registros de agua desde a instalacao para
     * a memoria, e era reemitido a cada copo de agua registrado. Com um ano de
     * uso sao uns 3.600 registros; com cinco anos, 18 mil -- carregados sempre,
     * mesmo com a tela mostrando 7 dias.
     *
     * dateString e "yyyy-MM-dd", entao o BETWEEN de texto ja da a ordem certa.
     */
    @Query("SELECT * FROM water_logs WHERE dateString BETWEEN :inicio AND :fim ORDER BY timestamp DESC")
    fun getLogsForDateRange(inicio: String, fim: String): Flow<List<WaterLog>>

    @Query("SELECT COUNT(*) FROM water_logs")
    fun contarLogs(): Flow<Int>

    @Query("SELECT * FROM water_logs WHERE dateString BETWEEN :inicio AND :fim")
    suspend fun getLogsForDateRangeOnce(inicio: String, fim: String): List<WaterLog>

    @Query("DELETE FROM water_logs WHERE dateString BETWEEN :inicio AND :fim")
    suspend fun deleteLogsForDateRange(inicio: String, fim: String)

    @Query("SELECT * FROM water_logs WHERE dateString = :dateString ORDER BY timestamp DESC")
    fun getLogsForDate(dateString: String): Flow<List<WaterLog>>

    @Query("SELECT SUM(amountMl) FROM water_logs WHERE dateString = :dateString")
    fun getDailySumMl(dateString: String): Flow<Int?>

    /*
     * REMOVIDO o getSumMlForPeriod, que somava por timestamp.
     *
     * Era a UNICA consulta do app que nao usava dateString, e ninguem chamava
     * ela: servia so ao total mensal que nunca chegou a ser desenhado na tela.
     * Ver o comentario em MainViewModel. Somar por dois criterios diferentes no
     * mesmo banco e um convite a dois numeros diferentes para a mesma agua.
     */

    @Query("SELECT * FROM water_logs WHERE dateString = :dateString")
    suspend fun getLogsForDateOnce(dateString: String): List<WaterLog>

    @Query("DELETE FROM water_logs WHERE dateString = :dateString")
    suspend fun deleteLogsForDate(dateString: String)

    @Query("DELETE FROM water_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)

    @Query("DELETE FROM water_logs")
    suspend fun deleteAllLogs()
}

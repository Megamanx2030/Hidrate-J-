package com.example.data.repository

import com.example.data.db.Reminder
import com.example.data.db.ReminderDao
import com.example.data.db.UserSettings
import com.example.data.db.UserSettingsDao
import com.example.data.db.WaterLog
import com.example.data.db.WaterLogDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class WaterRepository(
    private val waterLogDao: WaterLogDao,
    private val reminderDao: ReminderDao,
    private val userSettingsDao: UserSettingsDao
) {
    private val spTimeZone = TimeZone.getTimeZone("America/Sao_Paulo")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = spTimeZone
    }
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
        timeZone = spTimeZone
    }

    fun getTodayDateString(): String {
        return dateFormat.format(Date())
    }

    fun getTodayDisplayDateString(): String {
        return displayDateFormat.format(Date())
    }

    val userSettings: Flow<UserSettings> = userSettingsDao.getSettings().map {
        it ?: UserSettings()
    }

    /** O Historico pede so os dias que esta mostrando. Ver WaterLogDao. */
    fun getLogsForDateRange(inicio: String, fim: String): Flow<List<WaterLog>> =
        waterLogDao.getLogsForDateRange(inicio, fim)

    val totalDeRegistros: Flow<Int> = waterLogDao.contarLogs()

    /**
     * O DIA VEM DE FORA, NAO E LIDO AQUI DENTRO.
     *
     * Antes estas funcoes chamavam getTodayDateString() na hora de montar a
     * consulta, e o resultado ficava GRAVADO no Flow. Como a MainViewModel cria
     * esses Flows uma unica vez, o app aberto durante a virada da meia-noite
     * continuava somando a agua de ONTEM: o contador nao zerava e a meta do dia
     * novo comecava ja cheia.
     *
     * Recebendo a data por parametro, quem manda no dia e a ViewModel -- que
     * observa a virada e refaz a consulta.
     */
    fun getLogsForDate(dateString: String): Flow<List<WaterLog>> {
        return waterLogDao.getLogsForDate(dateString)
    }

    fun getTotalMlForDate(dateString: String): Flow<Int> {
        return waterLogDao.getDailySumMl(dateString).map { it ?: 0 }
    }

    fun getMonthlyTotalMl(): Flow<Int> {
        val calendar = Calendar.getInstance(spTimeZone)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonthMs = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val endOfMonthMs = calendar.timeInMillis

        return waterLogDao.getSumMlForPeriod(startOfMonthMs, endOfMonthMs).map { it ?: 0 }
    }

    val allReminders: Flow<List<Reminder>> = reminderDao.getAllReminders()

    suspend fun clearHistory() {
        waterLogDao.deleteAllLogs()
        reminderDao.resetAllReminderStatus()
    }

    suspend fun clearEverything() {
        waterLogDao.deleteAllLogs()
        reminderDao.deleteAllReminders()
    }

    suspend fun addWaterLog(amountMl: Int): Long {
        if (amountMl <= 0) return 0L
        val log = WaterLog(
            amountMl = amountMl,
            timestamp = System.currentTimeMillis(),
            dateString = getTodayDateString()
        )
        return waterLogDao.insertLog(log)
    }

    suspend fun updateSettings(settings: UserSettings) {
        userSettingsDao.insertOrUpdateSettings(settings)
    }

    suspend fun updateReminder(reminder: Reminder) {
        reminderDao.updateReminder(reminder)
    }

    suspend fun addReminder(reminder: Reminder) {
        reminderDao.insertReminder(reminder)
    }

    suspend fun deleteReminder(reminder: Reminder) {
        reminderDao.deleteReminder(reminder)
    }

    suspend fun initDefaultDataIfNeeded() {
        val existingSettings = userSettingsDao.getSettingsOnce()
        if (existingSettings?.hasSeededDefaults == true) {
            return
        }

        if (reminderDao.countReminders() == 0) {
            val todayDisplay = getTodayDisplayDateString()
            val defaultReminders = listOf(
                Reminder(time = "06:00", date = todayDisplay, title = "Lembrete Matinal"),
                Reminder(time = "06:50", date = todayDisplay, title = "Hora da Água"),
                Reminder(time = "07:40", date = todayDisplay, title = "Hora da Água"),
                Reminder(time = "08:30", date = todayDisplay, title = "Hora da Água"),
                Reminder(time = "09:20", date = todayDisplay, title = "Hora da Água"),
                Reminder(time = "10:10", date = todayDisplay, title = "Hora da Água"),
                Reminder(time = "14:00", date = todayDisplay, title = "Lembrete da Tarde"),
                Reminder(time = "16:00", date = todayDisplay, title = "Hora da Água")
            )
            reminderDao.insertReminders(defaultReminders)
        }

        val updatedSettings = (existingSettings ?: UserSettings()).copy(hasSeededDefaults = true)
        userSettingsDao.insertOrUpdateSettings(updatedSettings)
    }
}

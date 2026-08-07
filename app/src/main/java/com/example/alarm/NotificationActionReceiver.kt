package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.db.AppDatabase
import com.example.data.db.WaterLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        com.example.alarm.WaterAlarmService.stop(context)

        val reminderId = intent.getIntExtra("reminder_id", -1)
        val time = intent.getStringExtra("time") ?: ""
        val chimeType = intent.getStringExtra("chime_type") ?: "Sino Suave"
        val title = intent.getStringExtra("title") ?: "Hora da Água"

        if (reminderId == -1) return

        val db = AppDatabase.getDatabase(context)
        val spTimeZone = TimeZone.getTimeZone("America/Sao_Paulo")

        when (intent.action) {
            "ACTION_DISMISS_WATER" -> {
                // Already stopped the service above, nothing else to do.
                return
            }
            "ACTION_CONFIRM_WATER" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    // Get current settings for glass size
                    val settings = db.userSettingsDao().getSettingsOnce()
                    val glassSizeMl = settings?.glassSizeMl ?: 250

                    // Add water log
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                        timeZone = spTimeZone
                    }
                    val waterLog = WaterLog(
                        amountMl = glassSizeMl,
                        timestamp = System.currentTimeMillis(),
                        dateString = dateFormat.format(Date())
                    )
                    db.waterLogDao().insertLog(waterLog)

                    // Mark reminder as completed
                    val reminders = db.reminderDao().getAllRemindersOnce()
                    val reminder = reminders.find { it.id == reminderId }
                    if (reminder != null) {
                        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                            timeZone = spTimeZone
                        }
                        val currentTimeStr = timeFormatter.format(Date())
                        db.reminderDao().updateReminder(
                            reminder.copy(
                                isCompleted = true,
                                completedTime = currentTimeStr,
                                isSkipped = false,
                                skippedDate = "",
                                skippedTime = ""
                            )
                        )
                    }
                }

                // Dismiss notification
                NotificationHelper(context).cancelNotification(reminderId)
            }

            "ACTION_IGNORE_WATER" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    // Mark reminder as skipped
                    val reminders = db.reminderDao().getAllRemindersOnce()
                    val reminder = reminders.find { it.id == reminderId }
                    if (reminder != null) {
                        val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                            timeZone = spTimeZone
                        }
                        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                            timeZone = spTimeZone
                        }
                        db.reminderDao().updateReminder(
                            reminder.copy(
                                isSkipped = true,
                                skippedDate = displayDateFormat.format(Date()),
                                skippedTime = timeFormatter.format(Date())
                            )
                        )
                    }
                }

                // Dismiss notification
                NotificationHelper(context).cancelNotification(reminderId)
            }
        }
    }
}

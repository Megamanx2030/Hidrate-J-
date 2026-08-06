package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.MainActivity
import com.example.utils.SoundAndVibrationManager
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WaterReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("reminder_id", -1)
        val chimeType = intent.getStringExtra("chime_type") ?: "Sino Suave"
        val title = intent.getStringExtra("title") ?: "Hora da Água"
        val time = intent.getStringExtra("time") ?: ""

        if (reminderId == -1) return

        // Attempt to launch the app immediately so the overlay shows (even when app is closed)
        // Note: On modern Android, this relies on the setFullScreenIntent in the notification
        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("reminder_id", reminderId)
                putExtra("from_notification", true)
            }
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Keep receiver alive for the duration of the sound (up to 10s)
        val pendingResult = goAsync()

        // Execute remaining logic in background coroutine to fetch settings
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(context)
            val settings = database.userSettingsDao().getSettingsOnce()
            
            val isAlertsEnabled = settings?.alertsEnabled ?: true
            val isVibrateOnly = settings?.vibrateOnly ?: false

            // Show notification (silent by default, we manage sound/vibration manually)
            val notificationHelper = NotificationHelper(context)
            notificationHelper.showWaterReminderNotification(
                reminderId = reminderId,
                title = title,
                time = time,
                chimeType = chimeType,
                vibrateOnly = true // Forces builder to be silent
            )

            // Reschedule alarm for tomorrow (daily recurring)
            val alarmScheduler = AlarmScheduler(context)
            alarmScheduler.rescheduleReminderForTomorrow(
                reminderId = reminderId,
                time = time,
                chimeType = chimeType,
                title = title
            )

            // Play custom alarm sound and/or vibrate for 10 seconds
            val soundManager = SoundAndVibrationManager(context)
            
            if (isVibrateOnly) {
                // If Vibrate Only is ON, we ONLY vibrate regardless of alertsEnabled
                soundManager.vibrateOnly(
                    durationSeconds = 10,
                    onFinished = { pendingResult.finish() }
                )
            } else if (isAlertsEnabled) {
                // If Alerts is ON (and Vibrate Only is OFF), we play sound AND vibrate
                soundManager.playGentleBellAndVibrate(
                    durationSeconds = 10,
                    chimeType = chimeType,
                    onFinished = { pendingResult.finish() }
                )
            } else {
                // Both OFF, do nothing but finish
                pendingResult.finish()
            }
        }
    }
}

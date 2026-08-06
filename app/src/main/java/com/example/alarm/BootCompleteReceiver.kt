package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BootCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Reschedule all reminders after device reboot
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val settings = db.userSettingsDao().getSettingsOnce()
                    val chimeType = settings?.chimeType ?: "Sino Suave"

                    val reminders = db.reminderDao().getAllRemindersOnce()
                    val alarmScheduler = AlarmScheduler(context)

                    reminders.forEach { reminder ->
                        if (!reminder.isCompleted) {
                            alarmScheduler.scheduleReminder(
                                reminderId = reminder.id,
                                time = reminder.time,
                                chimeType = chimeType,
                                title = reminder.title
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

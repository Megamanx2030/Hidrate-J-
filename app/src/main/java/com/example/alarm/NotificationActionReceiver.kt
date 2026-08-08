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

    private companion object {
        const val TAG = "HidrateJa"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("reminder_id", -1)
        android.util.Log.d(TAG, "NotificationAction acao=${intent.action} reminderId=$reminderId")
        com.example.alarm.WaterAlarmService.stopAndDismiss(context, reminderId)


        val time = intent.getStringExtra("time") ?: ""
        val chimeType = intent.getStringExtra("chime_type") ?: "Sino Suave"
        val title = intent.getStringExtra("title") ?: "Hora da Água"

        if (reminderId == -1) return

        val db = AppDatabase.getDatabase(context)
        val spTimeZone = TimeZone.getTimeZone("America/Sao_Paulo")

        when (intent.action) {
            "ACTION_DISMISS_WATER" -> {
                // Arrastar a notificacao para fora NAO marca como esquecido.
                //
                // O app e para idosos, e limpar a barra de notificacoes e gesto
                // reflexo -- nao e declaracao de que nao vai beber agua. O
                // lembrete continua PENDENTE e so vira "esquecido" pelo
                // markMissedRemindersAsSkipped, depois dos 15 minutos de
                // carencia. Marcar aqui na hora apagava toda chance de a tela
                // azul ainda aparecer, porque isSkipped bloqueia o
                // triggerWaterAlert, o checkMissedRecentAlert e o poll de 3s.
                //
                // Quem marca na hora e o botao "❌ Ignorar"
                // (ACTION_IGNORE_WATER), onde houve decisao explicita.
                android.util.Log.d(TAG, "Notificacao dispensada: segue PENDENTE (nao marca esquecido)")
                return
            }
            "ACTION_CONFIRM_WATER" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val reminders = db.reminderDao().getAllRemindersOnce()
                    val reminder = reminders.find { it.id == reminderId }
                    if (reminder != null && !reminder.isCompleted) {
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
                        val logId = db.waterLogDao().insertLog(waterLog)

                        // Mark reminder as completed
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
                                skippedTime = "",
                                waterLogId = logId.toInt()
                            )
                        )
                    }
                }
            }

            "ACTION_IGNORE_WATER" -> {
                // Decisao explicita do usuario: marca esquecido na hora.
                android.util.Log.d(TAG, "Botao Ignorar: marcando como esquecido agora")
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
            }
        }
    }
}

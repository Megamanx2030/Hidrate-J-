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

        // FECHA A NOTIFICACAO IMEDIATAMENTE, SEM DEPENDER DO SERVICO.
        //
        // Antes isso ficava so por conta do stopAndDismiss, que chama
        // startService a partir do background -- proibido desde o Android 8.
        // A excecao era engolida pelo try/catch la dentro e a notificacao
        // ficava presa na barra depois de tocar o Ignorar.
        //
        // O cancel direto no NotificationManager nao tem essa restricao.
        if (reminderId != -1) {
            NotificationHelper(context).cancelNotification(reminderId)
        }
        // Continua tentando parar o som/vibracao pelo servico. Se estiver
        // barrado, o alarme para sozinho no fim dos 10 segundos.
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
                // goAsync SEGURA O PROCESSO ATE A GRAVACAO TERMINAR.
                //
                // Sem ele, o onReceive retornava na hora e o Android podia
                // matar o processo antes do insert do WaterLog. Era por isso
                // que "Bebi Água" pela notificacao as vezes nao aparecia no
                // relatorio nem nas telas de consumo: o registro simplesmente
                // nao chegava a ser gravado.
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                  try {
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
                                // skippedDate e skippedTime ficam: e o que
                                // permite mostrar "Esqueceu as X, mas bebeu
                                // depois as Y" nas telas.
                                waterLogId = logId.toInt()
                            )
                        )
                        android.util.Log.d(
                            TAG,
                            "Bebi Agua registrado: ${glassSizeMl}ml em ${waterLog.dateString}, logId=$logId"
                        )
                    } else {
                        android.util.Log.d(TAG, "Bebi Agua ignorado: lembrete ausente ou ja concluido")
                    }
                  } catch (e: Exception) {
                    android.util.Log.e(TAG, "Falha ao registrar Bebi Agua: ${e.message}")
                    e.printStackTrace()
                  } finally {
                    pendingResult.finish()
                  }
                }
            }

            "ACTION_IGNORE_WATER" -> {
                // Decisao explicita do usuario: marca esquecido na hora.
                // Mesmo goAsync do confirmar, pelo mesmo motivo: sem ele a
                // gravacao podia nao chegar a acontecer.
                android.util.Log.d(TAG, "Botao Ignorar: marcando como esquecido agora")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                  try {
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
                  } catch (e: Exception) {
                    android.util.Log.e(TAG, "Falha ao marcar esquecido: ${e.message}")
                    e.printStackTrace()
                  } finally {
                    pendingResult.finish()
                  }
                }
            }
        }
    }
}

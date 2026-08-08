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

                    // MESMA REGRA DO scheduleAllReminders, E PELO MESMO MOTIVO.
                    //
                    // Antes aqui havia um "if (!reminder.isCompleted)", que
                    // deixava de rearmar qualquer lembrete ja confirmado no dia.
                    // Só que a proxima ocorrencia dele e AMANHA, quando o status
                    // ja tera sido zerado pelo reset diario -- ou seja, ele
                    // sumia ate o usuario abrir o app. Quem reinicia o celular a
                    // noite perdia os lembretes do dia seguinte.
                    //
                    // O unico caso que nao rearma e o concluido cujo horario
                    // ainda vai chegar hoje: tocaria para algo que o usuario ja
                    // marcou como bebido.
                    val spZone = java.time.ZoneId.of("America/Sao_Paulo")
                    val nowSp = java.time.LocalTime.now(spZone)
                    val currentHHmm = String.format("%02d:%02d", nowSp.hour, nowSp.minute)

                    reminders.forEach { reminder ->
                        val jaPassouHoje = reminder.time <= currentHHmm
                        val concluidoEAindaVaiChegar = reminder.isCompleted && !jaPassouHoje

                        if (!concluidoEAindaVaiChegar) {
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

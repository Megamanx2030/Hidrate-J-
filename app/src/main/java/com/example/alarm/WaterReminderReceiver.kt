package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Recebe o alarme exato e delega TUDO que demora para o WaterAlarmService.
 *
 * O que mudou em relacao a versao anterior:
 *
 * 1. REMOVIDO o context.startActivity(). Desde o Android 10 iniciar Activity
 *    a partir de background e bloqueado -- aquele bloco nao fazia nada alem
 *    de gastar tempo do orcamento apertado do receiver. Quem abre a tela azul
 *    agora e o setFullScreenIntent da notificacao.
 *
 * 2. REMOVIDO o som/vibracao daqui. Foi para o Foreground Service.
 *
 * 3. O receiver agora so faz duas coisas rapidas: dispara o servico e
 *    reagenda o alarme de amanha.
 */
class WaterReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("reminder_id", -1)
        if (reminderId == -1) return

        val chimeType = intent.getStringExtra("chime_type") ?: "Sino Suave"
        val title = intent.getStringExtra("title") ?: "Hora da Água"
        val time = intent.getStringExtra("time") ?: ""

        // Dispara o servico IMEDIATAMENTE e de forma sincrona.
        // Nao pode ir para dentro de coroutine: o Android exige que o FGS
        // seja iniciado ainda dentro da janela de execucao do receiver.
        try {
            val serviceIntent = Intent(context, WaterAlarmService::class.java).apply {
                putExtra(WaterAlarmService.EXTRA_REMINDER_ID, reminderId)
                putExtra(WaterAlarmService.EXTRA_CHIME_TYPE, chimeType)
                putExtra(WaterAlarmService.EXTRA_TITLE, title)
                putExtra(WaterAlarmService.EXTRA_TIME, time)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Reagenda para amanha. E rapido, cabe no goAsync sem risco.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AlarmScheduler(context).rescheduleReminderForTomorrow(
                    reminderId = reminderId,
                    time = time,
                    chimeType = chimeType,
                    title = title
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

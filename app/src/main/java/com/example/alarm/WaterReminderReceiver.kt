package com.example.alarm

import com.example.utils.Registro

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Recebe o alarme exato e delega TUDO que demora para o WaterAlarmService.
 *
 * SOBRE A TELA AZUL (overlay do AlertOverlay dentro da MainActivity):
 *
 * Com o celular BLOQUEADO quem traz a MainActivity para a frente e o
 * setFullScreenIntent da notificacao, e isso funciona. Com o celular
 * DESBLOQUEADO o Android NAO honra o full screen intent: ele rebaixa a
 * notificacao para heads-up. Resultado comprovado em logcat: o alarme tocava,
 * vibrava e postava a notificacao, mas nao havia nenhuma tentativa de abrir
 * Activity -- a tela azul nao tinha ninguem para traze-la a frente.
 *
 * Por isso, com a tela desbloqueada, chamamos startActivity explicitamente.
 * O alarme e agendado com setAlarmClock, que em tese concede a isencao de
 * background activity launch, mas varios fabricantes (Motorola, Samsung) nao
 * honram isso -- por isso a chamada e protegida e registrada no log, e o
 * setFullScreenIntent CONTINUA sendo o caminho alternativo.
 *
 * O som/vibracao ficam no WaterAlarmService (Foreground Service), senao o
 * processo morre no meio dos 10 segundos.
 */
class WaterReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("reminder_id", -1)
        Registro.d("Receiver.onReceive reminderId=$reminderId")
        if (reminderId == -1) return

        val chimeType = intent.getStringExtra("chime_type") ?: "Sino Suave"
        val title = intent.getStringExtra("title") ?: "Hora da Água"
        val time = intent.getStringExtra("time") ?: ""

        // Marca que ESTE alarme tocou. E a unica prova aceita pelo app para
        // abrir a tela azul depois -- ver DisparoAlarmePrefs.
        DisparoAlarmePrefs.registrar(context, reminderId)

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
            Registro.d("FGS iniciado OK")
        } catch (e: Exception) {
            Registro.e("FGS FALHOU: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }

        abrirTelaAzulSeDesbloqueado(context, reminderId)

        // Reagenda para amanha. E rapido, cabe no goAsync sem risco.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // ALARME ORFAO: se o lembrete foi apagado, o alarme dele podia
                // continuar disparando todo dia -- e era um dos jeitos da tela
                // azul aparecer "sem horario marcado". Reagendar so quando o
                // lembrete ainda existe corta a corrente de vez.
                val existe = com.example.data.db.AppDatabase.getDatabase(context)
                    .reminderDao().getAllRemindersOnce().any { it.id == reminderId }

                if (existe) {
                    AlarmScheduler(context).rescheduleReminderForTomorrow(
                        reminderId = reminderId,
                        time = time,
                        chimeType = chimeType,
                        title = title
                    )
                } else {
                    Registro.w("Lembrete $reminderId nao existe mais: cancelando o alarme orfao")
                    AlarmScheduler(context).cancelReminder(reminderId)
                    DisparoAlarmePrefs.limpar(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Com a tela BLOQUEADA nao mexemos em nada: o setFullScreenIntent ja
     * resolve, e esse cenario funciona hoje. Chamar startActivity aqui tambem
     * so criaria uma segunda transicao concorrente sem ganho nenhum.
     *
     * ATENCAO ao ler o log: quando o fabricante barra o background activity
     * launch, o startActivity normalmente NAO lanca excecao -- o sistema
     * apenas ignora e registra o bloqueio no logcat do ActivityTaskManager.
     * Ou seja, "startActivity retornou sem excecao" nao prova que a tela
     * abriu. A prova esta no log da MainActivity ("MainActivity.handleIntent").
     */
    private fun abrirTelaAzulSeDesbloqueado(context: Context, reminderId: Int) {
        // Modo silencioso (os dois interruptores desligados): so a notificacao
        // na barra, sem tela azul. Vale para bloqueado e desbloqueado.
        if (!AlertModePrefs.deveMostrarTelaAzul(context)) {
            Registro.d("Modo silencioso -> so notificacao na barra, sem tela azul")
            return
        }

        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val bloqueado = keyguard?.isKeyguardLocked ?: false

        if (bloqueado) {
            Registro.d("Tela bloqueada -> deixando com o setFullScreenIntent")
            return
        }

        // A permissao de "aviso por cima" e o que isenta o app do bloqueio de
        // background activity launch. Sem ela a tentativa quase certamente
        // sera barrada -- e barrada EM SILENCIO, sem excecao. Registramos o
        // caminho no log para nao confundir a leitura depois.
        val podeSobrepor = AlarmPermissionHelper.canDrawOverlays(context)
        if (podeSobrepor) {
            Registro.d("Tela desbloqueada, canDrawOverlays=true -> startActivity com isencao de BAL")
        } else {
            Registro.w("Tela desbloqueada, canDrawOverlays=FALSE -> startActivity deve ser bloqueado; so a notificacao vai aparecer")
        }

        try {
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("reminder_id", reminderId)
                putExtra("from_notification", true)
                putExtra("show_alert", true)
                putExtra("alert_timestamp", System.currentTimeMillis())
            }
            context.startActivity(activityIntent)
            Registro.d("startActivity retornou sem excecao (nao garante que abriu; a prova e o MainActivity.handleIntent)")
        } catch (e: Exception) {
            Registro.e("startActivity BLOQUEADO: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
    }
}

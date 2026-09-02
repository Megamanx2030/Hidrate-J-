package com.example.alarm

import com.example.utils.Registro

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.RequiresApi
import com.example.data.db.AppDatabase
import com.example.utils.SoundAndVibrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Toca o alarme (som + vibracao) com o app fechado.
 *
 * POR QUE ISSO EXISTE:
 * Um BroadcastReceiver tem ~10 segundos de orcamento total antes do Android
 * considerar ANR e matar o processo. O goAsync() nao estende isso de forma
 * confiavel. Como o alarme toca por 10 segundos, o processo morria no meio --
 * daí o "as vezes toca, as vezes nao".
 *
 * Um Foreground Service e a unica forma que o Android garante execucao de
 * audio em background. O tipo SHORT_SERVICE foi desenhado exatamente para
 * isso: trabalho curto, critico e nao adiavel.
 *
 * Iniciar FGS a partir do background normalmente e proibido, mas alarmes
 * exatos (setExactAndAllowWhileIdle com SCHEDULE_EXACT_ALARM)
 * sao uma das excecoes documentadas. Por isso funciona aqui.
 */
class WaterAlarmService : Service() {

    companion object {
        const val ACTION_STOP_ALARM = "com.example.alarm.ACTION_STOP_ALARM"
        const val ACTION_STOP_AND_DISMISS = "com.example.alarm.ACTION_STOP_AND_DISMISS"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_CHIME_TYPE = "chime_type"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TIME = "time"

        private const val ALARM_DURATION_SECONDS = 10
        private const val WAKELOCK_TIMEOUT_MS = 20_000L

        fun stop(context: android.content.Context) {
            val intent = Intent(context, WaterAlarmService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        fun stopAndDismiss(context: android.content.Context, reminderId: Int) {
            val intent = Intent(context, WaterAlarmService::class.java).apply {
                action = ACTION_STOP_AND_DISMISS
                putExtra(EXTRA_REMINDER_ID, reminderId)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var soundManager: SoundAndVibrationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val alreadyStopping = AtomicBoolean(false)
    private var isForegroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reminderId = intent?.getIntExtra(EXTRA_REMINDER_ID, -1) ?: -1

        // OS PEDIDOS DE PARADA VEM ANTES DA CONFERENCIA DO ID.
        //
        // Estavam depois, e por isso o ACTION_STOP_ALARM NUNCA chegava aqui: o
        // stop() nao poe reminder_id nenhum no intent, entao o id vinha -1 e o
        // servico saia pelo "return" de cima sem passar pelo finishAlarm. Na
        // pratica o som ate parava, mas por tabela -- pelo stopSelf e o
        // onDestroy -- e sem soltar o wakelock pelo caminho combinado.
        // Parar um alarme e justamente o que nao pode depender de sorte.
        if (intent?.action == ACTION_STOP_ALARM) {
            finishAlarm(removerNotificacao = false, reminderId = reminderId)
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP_AND_DISMISS) {
            finishAlarm(removerNotificacao = true, reminderId = reminderId)
            return START_NOT_STICKY
        }

        if (reminderId == -1) {
            stopSelf()
            return START_NOT_STICKY
        }

        // O SERVICO PODE SER REAPROVEITADO PARA UM SEGUNDO ALARME.
        //
        // O stopSelf() do alarme anterior nao destroi a instancia na hora. Se
        // outro lembrete chegar nessa fresta, o onStartCommand roda de novo no
        // MESMO objeto -- e com o alreadyStopping ainda marcado como true, todo
        // finishAlarm do alarme novo (inclusive o de seguranca dos 12 segundos)
        // sairia sem fazer nada: som e vibracao ficariam presos ate o sistema
        // matar o processo. Zerar a marca aqui e o que abre o novo ciclo.
        alreadyStopping.set(false)

        val chimeType = intent?.getStringExtra(EXTRA_CHIME_TYPE) ?: "Sino Suave"
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Hora da Água"
        val time = intent?.getStringExtra(EXTRA_TIME) ?: ""

        // O startForeground PRECISA acontecer nos primeiros 5 segundos,
        // entao e a primeira coisa que fazemos -- antes de qualquer I/O.
        // Esta mesma notificacao carrega o setFullScreenIntent que acende
        // a tela e abre a MainActivity com o celular bloqueado.
        // Leitura SINCRONA do modo (SharedPreferences). Nao da para consultar o
        // Room aqui: a resposta chegaria depois do prazo do startForeground.
        val comTelaCheia = AlertModePrefs.deveMostrarTelaAzul(this)

        val helper = NotificationHelper(this)
        val notification = helper.buildWaterReminderNotification(
            reminderId = reminderId,
            title = title,
            time = time,
            chimeType = chimeType,
            comTelaCheia = comTelaCheia
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                reminderId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(reminderId, notification)
        }
        isForegroundStarted = true

        // GARANTIA DA NOTIFICACAO NA BARRA, EM QUALQUER MODO.
        //
        // O Android pode ADIAR em ate 10 segundos a exibicao da notificacao de
        // um foreground service. Com os dois modos desligados (sem som e sem
        // vibracao) o servico termina em milissegundos, e essa corrida podia
        // deixar o usuario sem nenhum aviso visivel na barra.
        //
        // Postar a MESMA notificacao com o MESMO id explicitamente forca a
        // exibicao imediata e nao duplica: o sistema trata como atualizacao.
        // Vale para so vibrar, som mais vibracao e tudo desligado, com a tela
        // bloqueada ou desbloqueada.
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(reminderId, notification)
        } catch (e: Exception) {
            Registro.e("Falha ao postar notificacao: ${e.message}")
            e.printStackTrace()
        }

        acquireWakeLock()

        scope.launch {
            // ALARME ORFAO: lembrete apagado cujo alarme continuou armado.
            // Sem esta conferencia o app tocava e abria a tela azul para um
            // horario que nao existe mais na lista do usuario.
            val lembreteExiste = try {
                AppDatabase.getDatabase(applicationContext)
                    .reminderDao().getAllRemindersOnce().any { it.id == reminderId }
            } catch (e: Exception) {
                e.printStackTrace()
                true // na duvida, avisa: perder um lembrete e pior que avisar demais
            }
            if (!lembreteExiste) {
                Registro.w("Lembrete $reminderId nao existe mais: alarme ignorado")
                DisparoAlarmePrefs.limpar(applicationContext)
                finishAlarm(removerNotificacao = true, reminderId = reminderId)
                return@launch
            }

            val settings = try {
                AppDatabase.getDatabase(applicationContext).userSettingsDao().getSettingsOnce()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            val alertsEnabled = settings?.alertsEnabled ?: true
            val vibrateOnly = settings?.vibrateOnly ?: false

            val manager = SoundAndVibrationManager(applicationContext)
            soundManager = manager
            
            // Timeout de seguranca caso o callback nao seja chamado
            scope.launch {
                kotlinx.coroutines.delay(12_000)
                finishAlarm(removerNotificacao = false, reminderId = reminderId)
            }

            Registro.d("Service modo: vibrateOnly=$vibrateOnly alertsEnabled=$alertsEnabled"
            )

            when {
                vibrateOnly -> manager.vibrateOnly(ALARM_DURATION_SECONDS) { finishAlarm(removerNotificacao = false, reminderId = reminderId) }
                alertsEnabled -> manager.playGentleBellAndVibrate(
                    durationSeconds = ALARM_DURATION_SECONDS,
                    chimeType = chimeType
                ) { finishAlarm(removerNotificacao = false, reminderId = reminderId) }
                else -> finishAlarm(removerNotificacao = false, reminderId = reminderId)
            }
        }

        return START_NOT_STICKY
    }

    /** Callback do SHORT_SERVICE: o sistema avisa que o tempo acabou. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTimeout(startId: Int) {
        finishAlarm(removerNotificacao = false, reminderId = -1)
    }

    /**
     * Encerra o servico.
     * Quando removerNotificacao = true, remove a notificacao ativamente (STOP_FOREGROUND_REMOVE).
     * Quando false, MANTEM a notificacao na barra (STOP_FOREGROUND_DETACH),
     * para que os botoes continuem disponiveis depois que o som para.
     */
    private fun finishAlarm(removerNotificacao: Boolean, reminderId: Int) {
        if (!alreadyStopping.compareAndSet(false, true)) return

        releaseWakeLock()
        soundManager?.stopAlert()
        soundManager = null

        if (isForegroundStarted) {
            if (removerNotificacao) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        } else if (removerNotificacao && reminderId != -1) {
            // O servico nao estava em foreground, entao precisamos remover manualmente
            NotificationHelper(this).cancelNotification(reminderId)
        }
        stopSelf()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HidrateJa::AlarmWakeLock"
            ).apply { acquire(WAKELOCK_TIMEOUT_MS) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        soundManager?.stopAlert()
        scope.cancel()
        super.onDestroy()
    }
}

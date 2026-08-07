package com.example.alarm

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
 * exatos (setExactAndAllowWhileIdle com SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM)
 * sao uma das excecoes documentadas. Por isso funciona aqui.
 */
class WaterAlarmService : Service() {

    companion object {
        const val ACTION_STOP_ALARM = "com.example.alarm.ACTION_STOP_ALARM"
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
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var soundManager: SoundAndVibrationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alreadyStopping = false
    private var isForegroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            finishAlarm()
            return START_NOT_STICKY
        }

        val reminderId = intent?.getIntExtra(EXTRA_REMINDER_ID, -1) ?: -1
        if (reminderId == -1) {
            stopSelf()
            return START_NOT_STICKY
        }

        val chimeType = intent?.getStringExtra(EXTRA_CHIME_TYPE) ?: "Sino Suave"
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Hora da Água"
        val time = intent?.getStringExtra(EXTRA_TIME) ?: ""

        // O startForeground PRECISA acontecer nos primeiros 5 segundos,
        // entao e a primeira coisa que fazemos -- antes de qualquer I/O.
        // Esta mesma notificacao carrega o setFullScreenIntent que acende
        // a tela e abre a MainActivity com o celular bloqueado.
        val helper = NotificationHelper(this)
        val notification = helper.buildWaterReminderNotification(
            reminderId = reminderId,
            title = title,
            time = time,
            chimeType = chimeType
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

        acquireWakeLock()

        scope.launch {
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

            when {
                vibrateOnly -> manager.vibrateOnly(ALARM_DURATION_SECONDS) { finishAlarm() }
                alertsEnabled -> manager.playGentleBellAndVibrate(
                    durationSeconds = ALARM_DURATION_SECONDS,
                    chimeType = chimeType
                ) { finishAlarm() }
                else -> finishAlarm()
            }
        }

        return START_NOT_STICKY
    }

    /** Callback do SHORT_SERVICE: o sistema avisa que o tempo acabou. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTimeout(startId: Int) {
        finishAlarm()
    }

    /**
     * Encerra o servico MANTENDO a notificacao na barra.
     * STOP_FOREGROUND_DETACH desvincula a notificacao do servico, entao os
     * botoes "Bebi Água" e "Ignorar" continuam disponiveis depois que o som para.
     */
    private fun finishAlarm() {
        if (alreadyStopping) return
        alreadyStopping = true

        releaseWakeLock()
        soundManager?.stopAlert()
        soundManager = null

        if (isForegroundStarted) {
            stopForeground(STOP_FOREGROUND_DETACH)
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

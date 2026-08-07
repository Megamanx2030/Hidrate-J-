package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        // MUDOU de _v2 para _v3 DE PROPOSITO.
        // Um NotificationChannel e imutavel depois de criado: alterar
        // importancia, som ou vibracao em um canal existente nao tem efeito
        // nenhum. A unica forma de aplicar mudancas e criar um ID novo.
        const val CHANNEL_ID = "hydra_water_alarm_channel_v3"
        const val CHANNEL_NAME = "Lembretes de Água (Alarme)"
        const val CHANNEL_DESC = "Notificações para lembrar de beber água"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                // Som e vibracao do sistema desligados: quem toca e o
                // SoundAndVibrationManager dentro do WaterAlarmService.
                // IMPORTANCE_HIGH continua obrigatorio, senao o
                // setFullScreenIntent e ignorado.
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Monta a notificacao sem exibir. O WaterAlarmService precisa do objeto
     * Notification para passar ao startForeground().
     */
    fun buildWaterReminderNotification(
        reminderId: Int,
        title: String,
        time: String,
        chimeType: String
    ): Notification {

        // Intent que abre a tela azul. O extra show_alert e lido pela
        // MainActivity para mandar o ViewModel exibir o overlay.
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("reminder_id", reminderId)
            putExtra("from_notification", true)
            putExtra("show_alert", true)
            putExtra("alert_timestamp", System.currentTimeMillis())
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            reminderId * 100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confirmIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "ACTION_CONFIRM_WATER"
            putExtra("reminder_id", reminderId)
            putExtra("time", time)
            putExtra("chime_type", chimeType)
            putExtra("title", title)
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId * 100 + 1,
            confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ignoreIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "ACTION_IGNORE_WATER"
            putExtra("reminder_id", reminderId)
            putExtra("time", time)
            putExtra("chime_type", chimeType)
            putExtra("title", title)
        }
        val ignorePendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId * 100 + 2,
            ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "ACTION_DISMISS_WATER"
            putExtra("reminder_id", reminderId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId * 100 + 3,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("💧 $title")
            .setContentText("Hora de beber água! São $time")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("🚰 Está na hora de beber água!\n⏰ Horário: $time\n\nBeba agora e confirme abaixo.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // MUDOU de CATEGORY_REMINDER para CATEGORY_ALARM.
            // O Android da tratamento privilegiado a CATEGORY_ALARM em
            // tela de bloqueio, Nao Perturbe e full screen intent.
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setFullScreenIntent(openAppPendingIntent, true)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(android.R.drawable.ic_input_add, "✅ Bebi Água", confirmPendingIntent)
            .addAction(android.R.drawable.ic_delete, "❌ Ignorar", ignorePendingIntent)
            .build()
    }

    fun showWaterReminderNotification(
        reminderId: Int,
        title: String,
        time: String,
        chimeType: String
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            reminderId,
            buildWaterReminderNotification(reminderId, title, time, chimeType)
        )
    }

    fun cancelNotification(reminderId: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderId)
    }
}

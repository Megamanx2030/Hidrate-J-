package com.example.alarm

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
        const val CHANNEL_ID = "hydra_water_alarm_channel_v2"
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
                // Disable system sound and vibration on the channel so our custom SoundManager can handle it
                // IMPORTANT: We must still keep IMPORTANCE_HIGH so the FullScreenIntent can wake the screen
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showWaterReminderNotification(
        reminderId: Int,
        title: String,
        time: String,
        chimeType: String,
        vibrateOnly: Boolean = false
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open the app when notification is tapped
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("reminder_id", reminderId)
            putExtra("from_notification", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            reminderId * 100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: "Bebi Água ✓"
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

        // Action: "Ignorar"
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("💧 $title")
            .setContentText("Hora de beber água! São $time - Toque para abrir o app.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("🚰 Está na hora de beber água!\n⏰ Horário: $time\n\nSe você não estava presente quando o alarme tocou, beba agora e confirme abaixo. Manter-se hidratado é essencial para sua saúde!")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setFullScreenIntent(openAppPendingIntent, true)
            .addAction(
                android.R.drawable.ic_input_add,
                "✅ Bebi Água",
                confirmPendingIntent
            )
            .addAction(
                android.R.drawable.ic_delete,
                "❌ Ignorar",
                ignorePendingIntent
            )
            .build()

        notificationManager.notify(reminderId, notification)
    }

    fun cancelNotification(reminderId: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderId)
    }
}

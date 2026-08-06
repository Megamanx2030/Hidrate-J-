package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.db.Reminder
import java.util.Calendar
import java.util.TimeZone

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val spTimeZone: TimeZone = TimeZone.getTimeZone("America/Sao_Paulo")

    // ------------------------------------------------------------------
    // API publica
    // ------------------------------------------------------------------

    /** Agenda para a proxima ocorrencia do horario (hoje se ainda nao passou, senao amanha). */
    fun scheduleReminder(reminderId: Int, time: String, chimeType: String, title: String) {
        val triggerAt = nextOccurrenceMillis(time) ?: return
        schedule(triggerAt, reminderId, time, chimeType, title)
    }

    /** Chamado pelo receiver logo apos disparar, para manter a recorrencia diaria. */
    fun rescheduleReminderForTomorrow(reminderId: Int, time: String, chimeType: String, title: String) {
        val triggerAt = tomorrowMillis(time) ?: return
        schedule(triggerAt, reminderId, time, chimeType, title)
    }

    /**
     * IMPORTANTE: chame isto na abertura do app E no BootCompleteReceiver.
     *
     * POR QUE: o agendamento anterior dependia SO do encadeamento -- o alarme
     * de amanha era criado quando o de hoje disparava. Se um unico disparo
     * falhasse (celular desligado no horario, "forcar parada", sistema matando
     * o processo), a corrente quebrava e o lembrete morria para sempre, sem
     * nenhum aviso. Reagendar tudo periodicamente conserta a corrente sozinho.
     *
     * Reagendar um alarme ja existente e barato: o FLAG_UPDATE_CURRENT
     * simplesmente substitui o anterior, nao duplica.
     */
    fun scheduleAll(reminders: List<Reminder>, chimeType: String) {
        reminders.forEach { reminder ->
            scheduleReminder(
                reminderId = reminder.id,
                time = reminder.time,
                chimeType = chimeType,
                title = reminder.title
            )
        }
    }

    fun cancelReminder(reminderId: Int) {
        alarmManager.cancel(buildPendingIntent(reminderId, "", "", ""))
    }

    fun cancelAll(reminders: List<Reminder>) {
        reminders.forEach { cancelReminder(it.id) }
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    private fun parseTime(time: String): Pair<Int, Int>? {
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun nextOccurrenceMillis(time: String): Long? {
        val (hour, minute) = parseTime(time) ?: return null
        val calendar = Calendar.getInstance(spTimeZone).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Se ja passou hoje, joga para amanha.
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun tomorrowMillis(time: String): Long? {
        val (hour, minute) = parseTime(time) ?: return null
        val calendar = Calendar.getInstance(spTimeZone).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun buildPendingIntent(
        reminderId: Int,
        time: String,
        chimeType: String,
        title: String
    ): PendingIntent {
        val intent = Intent(context, WaterReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
            putExtra("chime_type", chimeType)
            putExtra("title", title)
            putExtra("time", time)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * setAlarmClock e a API mais forte do Android para alarmes.
     *
     * Diferenca pratica em relacao ao setExactAndAllowWhileIdle:
     *  - e tratada como alarme de despertador, com a menor interferencia
     *    possivel do Doze e das otimizacoes de fabricante
     *  - mostra o icone de alarme na barra de status, o que da confianca
     *    ao usuario de que o lembrete esta armado
     *  - o showIntent abre o app se o usuario tocar no alarme pelo relogio
     *
     * A cadeia de fallback abaixo cobre o caso da permissao de alarme exato
     * estar negada -- o lembrete atrasa alguns minutos, mas nao some.
     */
    private fun schedule(
        triggerAtMillis: Long,
        reminderId: Int,
        time: String,
        chimeType: String,
        title: String
    ) {
        val pendingIntent = buildPendingIntent(reminderId, time, chimeType, title)

        val showIntent = PendingIntent.getActivity(
            context,
            reminderId + 500_000,
            Intent(context, com.example.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val podeExato = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

        try {
            if (podeExato) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            } catch (e2: SecurityException) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        }
    }
}

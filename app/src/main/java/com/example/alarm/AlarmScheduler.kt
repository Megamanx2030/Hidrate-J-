package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.db.Reminder
import com.example.utils.Zona

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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
     * IMPORTANTE: chame isto na abertura do app E no ReagendarAlarmesReceiver.
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

    /**
     * A CONTA MUDOU DE LUGAR, NAO DE REGRA.
     *
     * Ela vivia aqui dentro, lendo o relogio do sistema por conta propria e
     * usando um fuso fixo de Sao Paulo. Agora mora no CalculoDeHorario, que
     * recebe o "agora" e o fuso de fora -- e por isso pode ser conferida no PC,
     * sem celular. Ver CalculoDeHorarioTest: virada de meia-noite, virada de
     * ano, 29 de fevereiro, horario invalido e o caso de Manaus.
     *
     * O FUSO AGORA E O DO APARELHO (Zona.fuso()), nao mais Sao Paulo fixo.
     * Ver o comentario do Zona: em Manaus o lembrete das 08:00 era armado para
     * as 07:00, e de manha cedo chegava a pular o dia inteiro -- que e uma das
     * causas do "as vezes nao toca".
     */
    private fun nextOccurrenceMillis(time: String): Long? =
        CalculoDeHorario.proximaOcorrenciaMillis(time, System.currentTimeMillis(), Zona.fuso())

    private fun tomorrowMillis(time: String): Long? =
        CalculoDeHorario.amanhaMillis(time, System.currentTimeMillis(), Zona.fuso())

    /**
     * O OPT-IN QUE FALTAVA PARA A TELA AZUL COM O CELULAR DESBLOQUEADO.
     *
     * No Android 15+ com targetSdk 35 ou maior, QUEM CRIA o PendingIntent
     * precisa declarar que autoriza abrir tela a partir do background. Sem
     * isso o sistema barra o startActivity do receiver. O logcat mostrou
     * exatamente esta exigencia:
     *
     *   Background activity launch blocked! ...
     *   balRequireOptInByPendingIntentCreator: true
     *
     * Isto vale para o PendingIntent do alarme (que acorda o receiver) e para
     * o da notificacao. Nao exige nenhuma permissao do usuario.
     */
    private fun balOptInBundle(): android.os.Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return try {
            android.app.ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                .toBundle()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
        // NAO da para passar o opt-in de BAL aqui: PendingIntent.getBroadcast
        // nao tem sobrecarga com Bundle de opcoes -- so getActivity tem.
        // Por isso o opt-in fica no showIntent e no PendingIntent da
        // notificacao, que sao os que abrem tela.
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            balOptInBundle()
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

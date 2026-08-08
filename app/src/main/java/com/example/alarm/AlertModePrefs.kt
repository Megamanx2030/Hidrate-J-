package com.example.alarm

import android.content.Context

/**
 * Espelho dos dois interruptores de aviso em SharedPreferences.
 *
 * POR QUE ISTO EXISTE:
 * O WaterReminderReceiver e o WaterAlarmService precisam saber o modo ANTES
 * de montar a notificacao -- o startForeground tem que acontecer nos primeiros
 * segundos, e o setFullScreenIntent ja vai dentro da notificacao. Ler o Room
 * ali nao serve: a consulta e suspensa e a resposta chegaria tarde demais.
 *
 * SharedPreferences resolve porque a leitura e sincrona e em memoria. O Room
 * continua sendo a fonte da verdade; aqui e so uma copia para decisao rapida,
 * regravada a cada mudanca de configuracao.
 */
object AlertModePrefs {

    private const val FILE = "hidrateja_alert_mode"
    private const val KEY_ALERTS = "alertsEnabled"
    private const val KEY_VIBRATE = "vibrateOnly"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(context: Context, alertsEnabled: Boolean, vibrateOnly: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ALERTS, alertsEnabled)
            .putBoolean(KEY_VIBRATE, vibrateOnly)
            .apply()
    }

    fun alertsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALERTS, true)

    fun vibrateOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATE, false)

    /**
     * Com os DOIS interruptores desligados o lembrete e silencioso: aparece
     * somente a notificacao na barra, sem som, sem vibracao e SEM a tela azul.
     *
     * Isso vale tambem com o celular bloqueado -- por isso o setFullScreenIntent
     * precisa sair da notificacao neste modo, senao o Android abre a
     * MainActivity por conta propria e a tela azul volta a aparecer.
     */
    fun deveMostrarTelaAzul(context: Context): Boolean =
        alertsEnabled(context) || vibrateOnly(context)
}

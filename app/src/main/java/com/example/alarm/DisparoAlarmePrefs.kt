package com.example.alarm

import android.content.Context

/**
 * Registro do ULTIMO alarme que realmente tocou.
 *
 * POR QUE ISTO EXISTE (o bug da tela azul abrindo sozinha):
 *
 * Antes, ao voltar para o app, o MainViewModel varria o banco procurando
 * "algum lembrete cujo horario passou ha menos de 15 minutos e que nao foi
 * confirmado" -- e abria a tela azul em cima disso. So que isso NAO e prova de
 * que o alarme tocou. Bastava o usuario abrir o app dentro daquela janela de 15
 * minutos, ou voltar da tela de permissoes, para a tela azul aparecer do nada,
 * sem lembrete nenhum tendo disparado. E como a varredura roda a cada onResume,
 * ela se repetia.
 *
 * Agora quem grava aqui e o WaterReminderReceiver, ou seja, so grava quando o
 * alarme DE VERDADE disparou. O app le esse registro uma unica vez e apaga.
 * Sem registro, nao ha tela azul -- ponto.
 */
object DisparoAlarmePrefs {

    private const val FILE = "hidrateja_disparo"
    private const val KEY_ID = "reminderId"
    private const val KEY_QUANDO = "quandoMs"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun registrar(context: Context, reminderId: Int) {
        prefs(context).edit()
            .putInt(KEY_ID, reminderId)
            .putLong(KEY_QUANDO, System.currentTimeMillis())
            .apply()
    }

    /**
     * Devolve o lembrete que disparou ha menos de [janelaMs] e APAGA o registro.
     *
     * Apagar faz parte: sem isso, cada volta ao app reabriria a mesma tela azul.
     */
    fun consumir(context: Context, janelaMs: Long): Int? {
        val p = prefs(context)
        val id = p.getInt(KEY_ID, -1)
        val quando = p.getLong(KEY_QUANDO, 0L)
        if (id == -1) return null

        limpar(context)

        val idade = System.currentTimeMillis() - quando
        if (idade < 0 || idade > janelaMs) return null
        return id
    }

    fun limpar(context: Context) {
        prefs(context).edit().remove(KEY_ID).remove(KEY_QUANDO).apply()
    }
}

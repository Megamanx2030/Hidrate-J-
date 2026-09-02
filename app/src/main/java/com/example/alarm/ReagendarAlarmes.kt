package com.example.alarm

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.utils.Registro
import com.example.utils.Zona
import java.time.LocalTime

/**
 * A UNICA REGRA DE REARME DOS ALARMES.
 *
 * ANTES ELA EXISTIA EM DUAS COPIAS: uma no BootCompleteReceiver e outra no
 * MainViewModel.scheduleAllReminders. As duas quase iguais -- e "quase" e o
 * problema: a do boot nao tinha a excecao do lembrete que esta disparando
 * NESTE minuto, entao reiniciar o celular na hora exata de um lembrete podia
 * rearmar o alarme para dali a zero segundo. Duas copias de uma regra sutil
 * viram duas regras diferentes na primeira vez que alguem mexe em uma so.
 *
 * O QUE E REARMADO: todo lembrete existente, para a proxima ocorrencia dele.
 * Duas excecoes, e so duas:
 *
 *  1. O lembrete JA CONCLUIDO cujo horario AINDA VAI CHEGAR hoje. Tocaria para
 *     algo que o usuario ja marcou como bebido.
 *
 *  2. O lembrete que esta disparando neste exato minuto. Rearmar agora criaria
 *     um alarme para o mesmo instante e ele tocaria duas vezes seguidas.
 *
 * REPARE NO QUE NAO E EXCECAO: o lembrete concluido cujo horario JA PASSOU
 * hoje E rearmado. A proxima ocorrencia dele e amanha, e ate la o reset diario
 * ja zerou o status. Sem isso, quem ficasse dias sem abrir o app ia perdendo os
 * lembretes um por um.
 *
 * Rearmar um alarme que ja existe e barato e nao duplica: o FLAG_UPDATE_CURRENT
 * do PendingIntent substitui o anterior.
 */
object ReagendarAlarmes {

    /**
     * [motivo] entra no log. Quando alguem for investigar um "nao tocou", a
     * primeira pergunta e sempre "os alarmes chegaram a ser armados?" -- e a
     * resposta precisa dizer tambem POR QUE foram armados (boot, atualizacao
     * do app, permissao concedida, abertura da tela).
     */
    suspend fun tudo(context: Context, motivo: String) {
        try {
            val db = AppDatabase.getDatabase(context.applicationContext)
            val chimeType = db.userSettingsDao().getSettingsOnce()?.chimeType ?: "Sino Suave"
            val lembretes = db.reminderDao().getAllRemindersOnce()
            val agendador = AlarmScheduler(context.applicationContext)

            val agora = LocalTime.now(Zona.id())
            val agoraHHmm = String.format("%02d:%02d", agora.hour, agora.minute)

            var rearmados = 0
            lembretes.forEach { lembrete ->
                val jaPassouHoje = lembrete.time <= agoraHHmm
                val concluidoEAindaVaiChegar = lembrete.isCompleted && !jaPassouHoje
                val disparandoNesteMinuto = lembrete.time == agoraHHmm

                if (!concluidoEAindaVaiChegar && !disparandoNesteMinuto) {
                    agendador.scheduleReminder(
                        reminderId = lembrete.id,
                        time = lembrete.time,
                        chimeType = chimeType,
                        title = lembrete.title
                    )
                    rearmados++
                }
            }
            Registro.d("Rearme ($motivo): $rearmados de ${lembretes.size} lembretes armados")
        } catch (e: Exception) {
            Registro.e("Rearme ($motivo) FALHOU: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
    }
}

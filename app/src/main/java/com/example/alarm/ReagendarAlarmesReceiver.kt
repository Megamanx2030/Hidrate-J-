package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.utils.Registro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * OS QUATRO MOMENTOS EM QUE O ANDROID APAGA OS ALARMES DO APP -- E NAO AVISA
 * NINGUEM.
 *
 * Este receiver se chamava BootCompleteReceiver e ouvia SO o boot. Os outros
 * tres casos abaixo passavam batido, e cada um deles aparece para o usuario da
 * mesma forma: "o lembrete simplesmente parou de tocar". Nao ha erro, nao ha
 * travamento, nao ha nada no log. O alarme deixou de existir, so isso.
 *
 * 1. BOOT_COMPLETED -- o celular foi desligado e ligado. Alarmes nao
 *    sobrevivem a um reinicio; e preciso armar tudo de novo.
 *
 * 2. MY_PACKAGE_REPLACED -- O APP FOI ATUALIZADO. Este e o mais grave e o que
 *    faltava por completo. Toda vez que a Play Store instala uma versao nova, o
 *    Android cancela TODOS os alarmes do pacote antigo. Sem escutar isto, quem
 *    recebesse a atualizacao ficava sem nenhum lembrete ate abrir o app na mao
 *    -- e a pessoa nem sabe que o app foi atualizado, porque a loja atualiza
 *    sozinha, de madrugada. Vale dizer: a propria atualizacao que leva esta
 *    correcao ainda vai apagar os alarmes uma ultima vez; a partir da proxima,
 *    o rearme e automatico.
 *
 * 3. SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -- o usuario mexeu na
 *    permissao de "alarmes e lembretes". Quando ele REVOGA, o sistema cancela
 *    todos os alarmes exatos do app; quando ele CONCEDE, os alarmes que ja
 *    estavam armados continuam sendo os inexatos de antes, que o Doze atrasa.
 *    Nos dois sentidos o rearme e necessario.
 *
 * 4. TIME_SET e TIMEZONE_CHANGED -- acertar o relogio ou trocar de fuso.
 *    Alarme e marcado num instante absoluto, entao mudar o relogio do aparelho
 *    NAO move o alarme junto: o lembrete das 08:00 continuaria armado para o
 *    instante que era 08:00 no fuso antigo. Ver o comentario do Zona.
 *
 * SOBRE O goAsync(): sem ele, assim que o onReceive retorna o processo fica sem
 * nenhum componente vivo e pode ser morto a qualquer momento -- no meio da
 * consulta ao banco, antes de armar coisa nenhuma. Logo depois do boot, que e
 * quando o sistema esta mais apertado de memoria, essa corrida e perdida com
 * facilidade. O goAsync segura o processo ate o finish().
 */
class ReagendarAlarmesReceiver : BroadcastReceiver() {

    companion object {
        /**
         * A constante do alarme exato so existe a partir da API 31; escrita
         * como texto ela vale em qualquer versao e nao exige guarda de API.
         * Confere com AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.
         */
        private const val ACAO_PERMISSAO_ALARME_EXATO =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        private val ACOES_ACEITAS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACAO_PERMISSAO_ALARME_EXATO
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val acao = intent.action ?: return

        // O receiver e exported (o sistema precisa alcancar ele), entao
        // qualquer app poderia mandar um intent qualquer para ca. Aceitar so
        // a lista acima resolve: no pior caso alguem forca um rearme, que e
        // exatamente o que o app ja faz sozinho toda vez que abre.
        if (acao !in ACOES_ACEITAS) {
            Registro.d("Reagendamento ignorado: acao desconhecida $acao")
            return
        }

        Registro.d("Reagendamento pedido por: $acao")

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReagendarAlarmes.tudo(context.applicationContext, acao)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

package com.example.alarm

import java.util.Calendar
import java.util.TimeZone

/**
 * A conta que decide QUANDO o alarme vai tocar, separada do AlarmManager.
 *
 * POR QUE ESTA NUM ARQUIVO SO DELA: dentro do AlarmScheduler esta conta lia o
 * relogio do sistema por conta propria (System.currentTimeMillis) e o fuso de
 * um campo da classe. Nao havia como conferir "o que acontece as 23:59", "e na
 * virada do horario de verao", "e se o usuario digitar 25:00" sem um celular na
 * mao e muita paciencia.
 *
 * Recebendo o "agora" e o fuso como parametro, a funcao vira uma conta pura:
 * mesma entrada, mesma saida, sempre. Os testes em
 * app/src/test/java/com/example/alarm/CalculoDeHorarioTest.kt rodam no PC em
 * milissegundos e cobrem justamente os casos que ninguem testa na mao.
 */
object CalculoDeHorario {

    /** "07:30" -> 7 e 30. Devolve null em qualquer coisa que nao seja HH:mm valido. */
    fun parse(horario: String): Pair<Int, Int>? {
        val partes = horario.split(":")
        if (partes.size != 2) return null
        val hora = partes[0].toIntOrNull() ?: return null
        val minuto = partes[1].toIntOrNull() ?: return null
        if (hora !in 0..23 || minuto !in 0..59) return null
        return hora to minuto
    }

    /**
     * A proxima vez que este horario vai acontecer: hoje, se ainda nao passou;
     * amanha, se ja passou.
     *
     * O "<=" (e nao "<") e de proposito: reagendar exatamente no minuto do
     * disparo tem que jogar para amanha, senao o alarme se reprograma para
     * daqui a zero segundo e toca duas vezes seguidas.
     */
    fun proximaOcorrenciaMillis(horario: String, agoraMillis: Long, fuso: TimeZone): Long? {
        val (hora, minuto) = parse(horario) ?: return null
        val calendario = calendarioNoHorario(agoraMillis, fuso, hora, minuto)
        if (calendario.timeInMillis <= agoraMillis) {
            calendario.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendario.timeInMillis
    }

    /**
     * O mesmo horario, amanha. Usado logo depois do alarme disparar, para
     * manter a recorrencia diaria.
     */
    fun amanhaMillis(horario: String, agoraMillis: Long, fuso: TimeZone): Long? {
        val (hora, minuto) = parse(horario) ?: return null
        val calendario = calendarioNoHorario(agoraMillis, fuso, hora, minuto)
        calendario.add(Calendar.DAY_OF_YEAR, 1)
        return calendario.timeInMillis
    }

    /**
     * O DIA de [agoraMillis] com a HORA trocada pela pedida.
     *
     * A ordem importa: primeiro o dia, depois a hora. Fazer ao contrario
     * (somar um dia e so entao acertar a hora) da resultados diferentes na
     * virada do horario de verao, quando um dia tem 23 ou 25 horas.
     */
    private fun calendarioNoHorario(
        agoraMillis: Long,
        fuso: TimeZone,
        hora: Int,
        minuto: Int
    ): Calendar = Calendar.getInstance(fuso).apply {
        timeInMillis = agoraMillis
        set(Calendar.HOUR_OF_DAY, hora)
        set(Calendar.MINUTE, minuto)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

package com.example.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CalculoDeHorarioTest {

    private val saoPaulo: TimeZone = TimeZone.getTimeZone("America/Sao_Paulo")
    private val manaus: TimeZone = TimeZone.getTimeZone("America/Manaus")

    /** Um instante exato, escrito de forma legivel. */
    private fun instante(
        fuso: TimeZone,
        ano: Int, mes: Int, dia: Int, hora: Int, minuto: Int
    ): Long = Calendar.getInstance(fuso).apply {
        clear()
        set(ano, mes - 1, dia, hora, minuto, 0)
    }.timeInMillis

    /** Le um instante de volta como "dd/MM HH:mm" no fuso pedido. */
    private fun leitura(millis: Long, fuso: TimeZone): String {
        val c = Calendar.getInstance(fuso).apply { timeInMillis = millis }
        return String.format(
            "%02d/%02d %02d:%02d",
            c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE)
        )
    }

    @Test
    fun `horario que ainda nao chegou fica no mesmo dia`() {
        val agora = instante(saoPaulo, 2026, 9, 2, 8, 0)
        val alvo = CalculoDeHorario.proximaOcorrenciaMillis("14:30", agora, saoPaulo)!!
        assertEquals("02/09 14:30", leitura(alvo, saoPaulo))
    }

    @Test
    fun `horario que ja passou pula para amanha`() {
        val agora = instante(saoPaulo, 2026, 9, 2, 20, 0)
        val alvo = CalculoDeHorario.proximaOcorrenciaMillis("14:30", agora, saoPaulo)!!
        assertEquals("03/09 14:30", leitura(alvo, saoPaulo))
    }

    @Test
    fun `reagendar no minuto exato do disparo joga para amanha, nao para agora`() {
        // Se voltasse "agora", o alarme se reprogramaria para daqui a zero
        // segundo e o lembrete tocaria duas vezes seguidas.
        val agora = instante(saoPaulo, 2026, 9, 2, 14, 30)
        val alvo = CalculoDeHorario.proximaOcorrenciaMillis("14:30", agora, saoPaulo)!!
        assertEquals("03/09 14:30", leitura(alvo, saoPaulo))
    }

    @Test
    fun `virada da meia-noite nao pula um dia`() {
        val agora = instante(saoPaulo, 2026, 9, 2, 23, 59)
        val alvo = CalculoDeHorario.proximaOcorrenciaMillis("00:05", agora, saoPaulo)!!
        assertEquals("03/09 00:05", leitura(alvo, saoPaulo))
    }

    @Test
    fun `ultimo dia do mes vira o dia 1 do mes seguinte`() {
        val agora = instante(saoPaulo, 2026, 9, 30, 22, 0)
        val alvo = CalculoDeHorario.proximaOcorrenciaMillis("08:00", agora, saoPaulo)!!
        assertEquals("01/10 08:00", leitura(alvo, saoPaulo))
    }

    @Test
    fun `31 de dezembro vira o dia 1 de janeiro`() {
        val agora = instante(saoPaulo, 2026, 12, 31, 23, 0)
        val alvo = CalculoDeHorario.proximaOcorrenciaMillis("07:00", agora, saoPaulo)!!
        assertEquals("01/01 07:00", leitura(alvo, saoPaulo))
    }

    @Test
    fun `29 de fevereiro de ano bissexto e um dia valido`() {
        val agora = instante(saoPaulo, 2028, 2, 28, 22, 0)
        val alvo = CalculoDeHorario.proximaOcorrenciaMillis("08:00", agora, saoPaulo)!!
        assertEquals("29/02 08:00", leitura(alvo, saoPaulo))
    }

    @Test
    fun `ESTE E O BUG DE MANAUS - 08h em Manaus e 08h em Manaus`() {
        // Uma pessoa em Manaus (UTC-4) marca o lembrete para as 08:00.
        // Sao 05:00 da manha do dia 2 no relogio dela.
        val agoraEmManaus = instante(manaus, 2026, 9, 2, 5, 0)
        val alvo = CalculoDeHorario.proximaOcorrenciaMillis("08:00", agoraEmManaus, manaus)!!

        // No relogio DELA, tem que ser 08:00.
        assertEquals("02/09 08:00", leitura(alvo, manaus))

        // Com o fuso fixo de Sao Paulo, que era o que o app fazia, o mesmo
        // pedido cai as 08:00 de Sao Paulo -- ou seja, 07:00 em Manaus.
        // Uma hora antes do que a pessoa marcou.
        val alvoErrado = CalculoDeHorario.proximaOcorrenciaMillis("08:00", agoraEmManaus, saoPaulo)!!
        assertEquals("02/09 07:00", leitura(alvoErrado, manaus))
    }

    @Test
    fun `fuso errado pode atrasar o lembrete em um dia inteiro`() {
        // O caso mais grave da conta em fuso fixo, e o mais dificil de
        // perceber: em Manaus, no comeco da manha, o horario pedido ja
        // "passou" em Sao Paulo. O alarme entao pula para amanha, e a pessoa
        // fica o dia inteiro sem nenhum lembrete daquele horario -- que e
        // exatamente a queixa "as vezes nao toca".
        val agoraEmManaus = instante(manaus, 2026, 9, 2, 7, 0)

        val certo = CalculoDeHorario.proximaOcorrenciaMillis("08:00", agoraEmManaus, manaus)!!
        assertEquals("02/09 08:00", leitura(certo, manaus))

        val errado = CalculoDeHorario.proximaOcorrenciaMillis("08:00", agoraEmManaus, saoPaulo)!!
        assertEquals("03/09 07:00", leitura(errado, manaus))
    }

    @Test
    fun `amanha e sempre o dia seguinte no mesmo horario`() {
        val agora = instante(saoPaulo, 2026, 9, 2, 14, 30)
        val alvo = CalculoDeHorario.amanhaMillis("14:30", agora, saoPaulo)!!
        assertEquals("03/09 14:30", leitura(alvo, saoPaulo))
    }

    @Test
    fun `horario invalido nao agenda nada em vez de estourar`() {
        val agora = instante(saoPaulo, 2026, 9, 2, 8, 0)
        assertNull(CalculoDeHorario.proximaOcorrenciaMillis("25:00", agora, saoPaulo))
        assertNull(CalculoDeHorario.proximaOcorrenciaMillis("08:99", agora, saoPaulo))
        assertNull(CalculoDeHorario.proximaOcorrenciaMillis("8h30", agora, saoPaulo))
        assertNull(CalculoDeHorario.proximaOcorrenciaMillis("", agora, saoPaulo))
        assertNull(CalculoDeHorario.proximaOcorrenciaMillis("08:00:00", agora, saoPaulo))
    }
}

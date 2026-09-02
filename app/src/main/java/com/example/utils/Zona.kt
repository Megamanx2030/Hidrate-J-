package com.example.utils

import java.time.ZoneId
import java.util.TimeZone

/**
 * O FUSO HORARIO DO APARELHO, NUM LUGAR SO.
 *
 * ANTES: dezoito pontos do codigo escreviam "America/Sao_Paulo" na mao --
 * o AlarmScheduler, o repositorio, a virada de dia, o historico, as telas.
 *
 * O ESTRAGO: quem mora em Manaus, Cuiaba, Porto Velho, Boa Vista, Rio Branco
 * ou Fernando de Noronha nao esta no fuso de Brasilia. Um lembrete marcado
 * para as 08:00 era calculado em horario de Sao Paulo e disparava as 07:00 no
 * relogio da pessoa (e as 06:00 no Acre). Do lado de la isso aparece como
 * "o alarme nao tocou na hora que eu marquei" -- e nao ha erro nenhum no log,
 * porque para o app tudo funcionou.
 *
 * O MESMO VALE PARA A DATA. A virada de dia, o total de hoje e o historico
 * tambem usavam Sao Paulo. Em Manaus, entre 23:00 e meia-noite, o app ja
 * considerava que era o dia seguinte: a jarra zerava antes da hora e a agua
 * bebida caia no dia errado do historico.
 *
 * POR ISSO A TROCA E TODA DE UMA VEZ. Misturar os dois (alarme no fuso do
 * aparelho, data em Sao Paulo) seria pior do que o problema original: o
 * lembrete das 23:30 tocaria certo, mas o registro dele iria para amanha.
 *
 * ZoneId.systemDefault() e TimeZone.getDefault() leem o fuso atual do sistema
 * a cada chamada, entao trocar de fuso (viagem, celular novo) passa a valer na
 * hora. Quem rearma os alarmes nesse caso e o ReagendarAlarmesReceiver, que
 * escuta TIMEZONE_CHANGED.
 */
object Zona {

    /** Para o codigo que usa java.time (LocalDate, LocalTime, ZonedDateTime). */
    fun id(): ZoneId = ZoneId.systemDefault()

    /** Para o codigo que usa Calendar e SimpleDateFormat. */
    fun fuso(): TimeZone = TimeZone.getDefault()
}

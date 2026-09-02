package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val time: String, // e.g., "06:00", "17:45"
    val date: String = "", // e.g., "03/08/2026"
    val title: String, // e.g. "Lembrete Matinal", "Hora da Água"
    val isCompleted: Boolean = false,
    val completedTime: String = "",
    val isSkipped: Boolean = false,
    val skippedDate: String = "", // e.g., "Segunda-feira, 03/08/2026"
    val skippedTime: String = "", // e.g., "17:45"
    val waterLogId: Int = 0,
    /**
     * QUANDO ESTE LEMBRETE PASSOU A EXISTIR.
     *
     * Serve para uma coisa so: nao chamar de "esquecido" um horario que ja
     * tinha passado no momento em que o lembrete foi criado. Sem isto, quem
     * instalava o app as 17:30 abria a tela e via seis avisos vermelhos de
     * "Esqueceu hoje as 10:20" -- por lembretes nascidos trinta segundos
     * antes. O app repreendia a pessoa por algo que ela nao tinha como ter
     * feito, logo na primeira tela.
     *
     * ZERO NOS LEMBRETES ANTIGOS, e esta certo assim: zero e menor que
     * qualquer horario de hoje, entao eles continuam sendo marcados como
     * esquecidos exatamente como antes. Ver markMissedRemindersAsSkipped.
     */
    val criadoEmMs: Long = 0L
)

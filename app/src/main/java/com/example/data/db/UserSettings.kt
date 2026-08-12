package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1,
    /**
     * VAZIO DE PROPOSITO.
     *
     * O padrao era "João". Quem baixasse o app da loja era recebido com
     * "Olá, João" -- o nome de um estranho, que a pessoa nao escolheu e nem
     * entende de onde veio. Num app para idoso isso passa a impressao de que o
     * aparelho ja estava em uso por outra pessoa.
     *
     * Vazio, a tela inicial cumprimenta sem nome ("Olá!") ate a pessoa se
     * apresentar em Configurar.
     */
    val userName: String = "",
    val dailyGoalMl: Int = 2500, // 2.5 Liters = 10 cups of 250ml
    val monthlyGoalLiters: Float = 60.0f, // 60.0 Liters monthly target
    val glassSizeMl: Int = 250, // 250ml cup size
    val intervalMinutes: Int = 50,
    val startTime: String = "06:00",
    val endTime: String = "21:00",
    val alertsEnabled: Boolean = true,
    val vibrateOnly: Boolean = false,
    val ringtoneDurationSeconds: Int = 10,
    val chimeType: String = "Sino Suave",
    val hasSeededDefaults: Boolean = false,
    // Data (dd/MM/yyyy) do ultimo reset diario dos lembretes.
    // Sem isso, isCompleted fica true para sempre e o lembrete
    // nunca volta para "Pendente" no dia seguinte.
    val lastResetDate: String = ""
)

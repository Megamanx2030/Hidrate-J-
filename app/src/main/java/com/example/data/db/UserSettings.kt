package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1,
    val userName: String = "João",
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

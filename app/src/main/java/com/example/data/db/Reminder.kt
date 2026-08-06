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
    val skippedTime: String = "" // e.g., "17:45"
)

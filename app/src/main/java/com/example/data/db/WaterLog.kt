package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * O indice em dateString e o que segura o desempenho no tempo.
 *
 * Praticamente toda consulta do app filtra por dia: o total de hoje, a lista do
 * dia, o periodo do historico. Sem indice, cada uma delas varre a tabela
 * inteira -- e essas consultas sao Flows, reavaliados a cada copo registrado.
 * Com indice o custo para de crescer junto com o historico.
 */
@Entity(
    tableName = "water_logs",
    indices = [Index(value = ["dateString"])]
)
data class WaterLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amountMl: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val dateString: String // e.g. "2026-08-03"
)

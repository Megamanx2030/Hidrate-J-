package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.db.UserSettings
import com.example.ui.theme.PrimaryBlue

@Composable
fun SettingsDialog(
    settings: UserSettings,
    onDismiss: () -> Unit,
    onSave: (name: String, dailyGoalMl: Int, monthlyGoalLiters: Float, glassSizeMl: Int, alertsEnabled: Boolean, chimeType: String) -> Unit
) {
    var name by remember { mutableStateOf(settings.userName) }
    var dailyGoalLitersStr by remember { mutableStateOf((settings.dailyGoalMl / 1000f).toString()) }
    var glassSizeMlStr by remember { mutableStateOf(settings.glassSizeMl.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configurações de Hidratação",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Seu Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = dailyGoalLitersStr,
                    onValueChange = { dailyGoalLitersStr = it },
                    label = { Text("Meta Diária (Litros, ex: 2.5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))



                OutlinedTextField(
                    value = glassSizeMlStr,
                    onValueChange = { glassSizeMlStr = it },
                    label = { Text("Tamanho do Copo (ml, ex: 250)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dailyMl = ((dailyGoalLitersStr.toFloatOrNull() ?: 2.5f) * 1000).toInt()
                    val glassMl = glassSizeMlStr.toIntOrNull() ?: 250
                    onSave(name, dailyMl, settings.monthlyGoalLiters, glassMl, settings.alertsEnabled, settings.chimeType)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Salvar", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

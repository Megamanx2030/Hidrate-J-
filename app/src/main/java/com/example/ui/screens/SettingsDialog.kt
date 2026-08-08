package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.db.UserSettings
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryContainer

/**
 * Uma escolha pronta, do tamanho de um botao de verdade.
 *
 * O campo de digitar continua existindo logo abaixo, mas ele nao pode ser o
 * unico caminho: escrever "2.5" num teclado numerico -- e acertar o PONTO, que
 * em portugues a pessoa escreveria com virgula -- e onde mais se erra. Tocar
 * em "2,5 L" nao tem como dar errado.
 */
@Composable
private fun EscolhaRapida(
    texto: String,
    selecionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selecionada) PrimaryBlue else Color.White)
            .border(
                width = 2.dp,
                color = if (selecionada) PrimaryBlue else SecondaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selecionada) Color.White else PrimaryBlue,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TituloDoCampo(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun SettingsDialog(
    settings: UserSettings,
    onDismiss: () -> Unit,
    onSave: (name: String, dailyGoalMl: Int, monthlyGoalLiters: Float, glassSizeMl: Int, alertsEnabled: Boolean, chimeType: String) -> Unit
) {
    var name by remember { mutableStateOf(settings.userName) }
    var metaMl by remember { mutableStateOf(settings.dailyGoalMl.coerceAtLeast(500)) }
    var copoMl by remember { mutableStateOf(settings.glassSizeMl.coerceAtLeast(50)) }

    // Os campos de digitar seguem as escolhas rapidas, e vice-versa.
    var metaDigitada by remember { mutableStateOf((settings.dailyGoalMl / 1000f).toString().replace('.', ',')) }
    var copoDigitado by remember { mutableStateOf(settings.glassSizeMl.toString()) }

    val ptBR = java.util.Locale("pt", "BR")
    val coposPorDia = (metaMl / copoMl.coerceAtLeast(1)).coerceAtLeast(1)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = "Configurar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TituloDoCampo("Como quer ser chamado")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                TituloDoCampo("Quanta água por dia")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1500, 2000, 2500, 3000).forEach { valor ->
                        EscolhaRapida(
                            texto = String.format(ptBR, "%.1f L", valor / 1000f),
                            selecionada = metaMl == valor,
                            onClick = {
                                metaMl = valor
                                metaDigitada = String.format(ptBR, "%.1f", valor / 1000f)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = metaDigitada,
                    onValueChange = { novo ->
                        metaDigitada = novo
                        // Aceita virgula E ponto: o teclado numerico do Android
                        // oferece um ou outro dependendo do aparelho, e a pessoa
                        // digita o que ve.
                        novo.replace(',', '.').toFloatOrNull()?.let { litros ->
                            if (litros > 0f) metaMl = (litros * 1000).toInt()
                        }
                    },
                    label = { Text("ou digite em litros") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                TituloDoCampo("Tamanho do seu copo")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(150, 200, 250, 300).forEach { valor ->
                        EscolhaRapida(
                            texto = "$valor",
                            selecionada = copoMl == valor,
                            onClick = {
                                copoMl = valor
                                copoDigitado = valor.toString()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = copoDigitado,
                    onValueChange = { novo ->
                        copoDigitado = novo
                        novo.toIntOrNull()?.let { ml -> if (ml > 0) copoMl = ml }
                    },
                    label = { Text("ou digite em ml") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // O resultado da conta, na hora. Sem isso a pessoa so descobre
                // quantos copos combinou depois de salvar e voltar para a tela.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecondaryContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "São $coposPorDia copos de $copoMl ml por dia",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name.ifBlank { settings.userName },
                        metaMl,
                        settings.monthlyGoalLiters,
                        copoMl,
                        settings.alertsEnabled,
                        settings.chimeType
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = "Salvar", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = "Cancelar", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

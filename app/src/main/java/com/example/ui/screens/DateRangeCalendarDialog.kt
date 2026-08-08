package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanAction
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryContainer
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Calendario de intervalo no estilo de site de hotel: toca no dia de entrada,
 * toca no dia de saida, e o miolo entre os dois fica pintado.
 *
 * Substituiu o DateRangePicker do Material 3, que num app para idosos ficava
 * apertado, cheio de affordance escondida e com o cabecalho de edicao por
 * texto que so atrapalhava.
 *
 * Regras de toque, iguais as de reserva de hotel:
 *  - primeiro toque marca o inicio e limpa o fim
 *  - toque numa data anterior ao inicio vira o novo inicio
 *  - toque numa data posterior fecha o intervalo
 *  - com o intervalo fechado, o proximo toque comeca tudo de novo
 *
 * Datas futuras ficam desabilitadas: nao existe consumo registrado la.
 */
@Composable
fun DateRangeCalendarDialog(
    inicialSelecionado: LocalDate,
    finalSelecionado: LocalDate,
    hoje: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    var mesExibido by remember { mutableStateOf(YearMonth.from(finalSelecionado)) }
    var inicio by remember { mutableStateOf<LocalDate?>(inicialSelecionado) }
    var fim by remember { mutableStateOf<LocalDate?>(finalSelecionado) }

    val nomesMeses = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )
    val fmtCurto = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    val podeAvancar = mesExibido < YearMonth.from(hoje)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Escolher datas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CyanAction
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {

                Text(
                    text = when {
                        inicio != null && fim != null ->
                            "De ${inicio!!.format(fmtCurto)} até ${fim!!.format(fmtCurto)}"
                        inicio != null ->
                            "Início ${inicio!!.format(fmtCurto)} — agora toque no último dia"
                        else -> "Toque no primeiro dia"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Cabecalho do mes, com as setas de navegacao
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { mesExibido = mesExibido.minusMonths(1) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Mês anterior",
                            tint = CyanAction,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "${nomesMeses[mesExibido.monthValue - 1]} ${mesExibido.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = { if (podeAvancar) mesExibido = mesExibido.plusMonths(1) },
                        enabled = podeAvancar,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Próximo mês",
                            tint = if (podeAvancar) CyanAction else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Cabecalho dos dias da semana, comecando na segunda
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("S", "T", "Q", "Q", "S", "S", "D").forEach { d ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = d,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val primeiroDia = mesExibido.atDay(1)
                // DayOfWeek.value: 1 = segunda ... 7 = domingo
                val brancosAntes = primeiroDia.dayOfWeek.value - 1
                val totalDias = mesExibido.lengthOfMonth()
                val celulas = brancosAntes + totalDias
                val linhas = (celulas + 6) / 7

                for (linha in 0 until linhas) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (coluna in 0 until 7) {
                            val indice = linha * 7 + coluna
                            val diaDoMes = indice - brancosAntes + 1

                            if (diaDoMes < 1 || diaDoMes > totalDias) {
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                val data = mesExibido.atDay(diaDoMes)
                                val futuro = data.isAfter(hoje)

                                val ehInicio = data == inicio
                                val ehFim = data == fim
                                val noMeio = inicio != null && fim != null &&
                                        data.isAfter(inicio) && data.isBefore(fim)

                                // A ponta e um circulo cheio; o miolo e uma
                                // faixa continua, para o intervalo ler como
                                // uma coisa so.
                                val corFundo = when {
                                    ehInicio || ehFim -> CyanAction
                                    noMeio -> SecondaryContainer
                                    else -> Color.Transparent
                                }
                                val forma = when {
                                    ehInicio || ehFim -> RoundedCornerShape(50)
                                    else -> RoundedCornerShape(0.dp)
                                }
                                val corTexto = when {
                                    ehInicio || ehFim -> Color.White
                                    futuro -> MaterialTheme.colorScheme.outlineVariant
                                    noMeio -> PrimaryBlue
                                    else -> MaterialTheme.colorScheme.onSurface
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(forma)
                                        .background(corFundo)
                                        .then(
                                            if (futuro) Modifier
                                            else Modifier.clickable {
                                                val i = inicio
                                                val f = fim
                                                when {
                                                    // Intervalo fechado ou nada
                                                    // marcado: recomeca.
                                                    i == null || f != null -> {
                                                        inicio = data
                                                        fim = null
                                                    }
                                                    // Voltou para tras: vira o
                                                    // novo inicio.
                                                    data.isBefore(i) -> {
                                                        inicio = data
                                                        fim = null
                                                    }
                                                    else -> fim = data
                                                }
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = diaDoMes.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = corTexto,
                                        fontWeight = if (ehInicio || ehFim || data == hoje) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val i = inicio
                    if (i != null) {
                        // So o inicio marcado vale como periodo de um dia.
                        onConfirm(i, fim ?: i)
                    }
                },
                enabled = inicio != null,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAction),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Confirmar", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancelar", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

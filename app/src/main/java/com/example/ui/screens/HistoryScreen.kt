package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.Reminder
import com.example.data.db.WaterLog
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SecondaryContainer
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun HistoryScreen(
    logs: List<WaterLog>,
    reminders: List<Reminder>,
    dailyGoalMl: Int = 2000,
    onClearHistory: () -> Unit = {},
    onClearEverything: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    val spTimeZone = TimeZone.getTimeZone("America/Sao_Paulo")
    val spZoneId = ZoneId.of("America/Sao_Paulo")

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var deleteRemindersToo by remember { mutableStateOf(false) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }

    // Month picker state (0 = Janeiro, 7 = Agosto, etc.)
    val monthsList = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )
    /**
     * A data era lida uma vez so, com remember { }. Com o app aberto virando
     * a meia-noite, o historico continuava mostrando a semana do dia anterior
     * ate o usuario fechar e reabrir.
     *
     * Agora ela e estado e e reavaliada a cada minuto. A recomposicao so
     * acontece quando o dia realmente muda.
     */
    var currentLocalDate by remember { mutableStateOf(LocalDate.now(spZoneId)) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            val hoje = LocalDate.now(spZoneId)
            if (hoje != currentLocalDate) currentLocalDate = hoje
        }
    }

    var selectedMonthIndex by remember { mutableStateOf(currentLocalDate.monthValue - 1) }
    var selectedYear by remember { mutableStateOf(currentLocalDate.year) }

    val selectedMonthText = "${monthsList[selectedMonthIndex]} de $selectedYear"

    // Time formatter for water logs (e.g., "14:30")
    val logTimeFormatter = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = spTimeZone
        }
    }

    // Process logs grouped by dateString ("YYYY-MM-DD")
    val groupedLogs = remember(logs) {
        logs.groupBy { it.dateString }
    }

    // Calendar week data for the chart and list (Sunday to Saturday)
    val pastDaysData = remember(logs, dailyGoalMl, selectedMonthIndex, selectedYear, currentLocalDate) {
        val list = mutableListOf<Triple<String, String, List<WaterLog>>>() // (formattedDateStr, dateKey, dayLogs)
        val cal = Calendar.getInstance(spTimeZone)
        // O DIA 1 TEM QUE VIR ANTES DO ANO E DO MES.
        //
        // Antes o ano e o mes eram definidos primeiro, com o dia ainda no
        // valor de hoje. Se hoje fosse 31 e o usuario escolhesse fevereiro,
        // o Calendar transbordava para marco antes mesmo do getActualMaximum
        // ser chamado, e a semana exibida era a errada.
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.YEAR, selectedYear)
        cal.set(Calendar.MONTH, selectedMonthIndex)
        // Set to current day of month or last day if in past
        if (selectedMonthIndex == currentLocalDate.monthValue - 1 && selectedYear == currentLocalDate.year) {
            cal.set(Calendar.DAY_OF_MONTH, currentLocalDate.dayOfMonth)
        } else {
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        }

        // Align to Monday
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = spTimeZone }
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault()).apply { timeZone = spTimeZone }
        val dayNames = arrayOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

        for (i in 0..6) {
            val key = keyFormat.format(cal.time)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val label = "${dayNames[dayOfWeek - 1]}, ${dateFormat.format(cal.time)}"
            val dayLogs = groupedLogs[key] ?: emptyList()
            list.add(Triple(label, key, dayLogs))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    // Chart fill ratios for past 7 days (oldest to newest)
    val chartRatios = remember(pastDaysData) {
        pastDaysData.map { (_, _, dayLogs) ->
            val totalMl = dayLogs.sumOf { it.amountMl }
            (totalMl.toFloat() / dailyGoalMl.toFloat()).coerceIn(0f, 1f)
        }
    }

    // Chart day labels (oldest to newest)
    val chartDays = remember(pastDaysData) {
        pastDaysData.map { (dateDisplay, _, _) ->
            dateDisplay.take(3)
        }
    }

    // Daily Average
    val dailyAvgText = remember(pastDaysData) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = spTimeZone }.format(Date())
        val elapsedDaysData = pastDaysData.filter { it.second <= todayStr }
        val sum = elapsedDaysData.sumOf { it.third.sumOf { l -> l.amountMl } }
        val daysCount = elapsedDaysData.size
        val avg = if (daysCount > 0) sum / daysCount else 0
        String.format(Locale("pt", "BR"), "%.1fL", avg / 1000f)
    }

    // Skipped Reminders
    val skippedReminders = remember(reminders, selectedMonthIndex, selectedYear) {
        reminders.filter { reminder ->
            if (reminder.skippedDate.isBlank()) false
            else {
                // skippedDate is "dd/MM/yyyy"
                val parts = reminder.skippedDate.split("/")
                if (parts.size == 3) {
                    val m = parts[1].toIntOrNull() ?: -1
                    val y = parts[2].toIntOrNull() ?: -1
                    (m - 1) == selectedMonthIndex && y == selectedYear
                } else false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Page Header
        Text(
            text = "Meu Histórico",
            style = MaterialTheme.typography.headlineMedium,
            color = PrimaryBlue,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Control Buttons: "Selecionar Mês" and "Limpar Histórico"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showMonthPickerDialog = true },
                shape = RoundedCornerShape(10.dp),
                // O botao padrao do Material3 tem 40dp de altura; 48dp e o
                // minimo recomendado de alvo de toque.
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = com.example.ui.theme.CyanAction
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Selecionar Mês",
                    fontWeight = FontWeight.Bold,
                    color = com.example.ui.theme.CyanAction,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = { showClearConfirmDialog = true },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = ErrorRed),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = ErrorRed
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Limpar",
                    fontWeight = FontWeight.Bold,
                    color = ErrorRed,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }

        // Selected Month Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 10.dp, bottom = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = com.example.ui.theme.CyanAction,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Mês Exibido: $selectedMonthText",
                style = MaterialTheme.typography.bodyMedium,
                color = com.example.ui.theme.CyanAction,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Weekly Summary Bento Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Resumo da Semana",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = selectedMonthText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Animated Weekly Water Drop Chart with real calculated ratios
                val todayDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply { timeZone = spTimeZone }.format(java.util.Date())
                com.example.ui.components.AnimatedWaterDropChart(
                    days = if (chartDays.size == 7) chartDays else listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"),
                    fillRatios = if (chartRatios.size == 7) chartRatios else listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f),
                    dates = pastDaysData.map { it.second },
                    totalsMl = pastDaysData.map { it.third.sumOf { l -> l.amountMl } },
                    dailyGoalMl = dailyGoalMl,
                    todayDate = todayDateStr,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Daily Average Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(com.example.ui.theme.CyanAction)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Média por dia:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            val goalL = String.format(java.util.Locale("pt", "BR"), "%.1fL", dailyGoalMl / 1000f)
                            Text(
                                text = "Meta: $goalL por dia",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Text(
                        text = dailyAvgText,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Days Section (Dynamically logged with timestamps)
        Text(
            // O titulo dizia "Dias do Mes", mas a lista so tem os 7 dias da
            // semana selecionada.
            text = "Dias da Semana (com horários)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        run {
            // pastDaysData sempre tem exatamente 7 itens, entao o antigo
            // "if (pastDaysData.isEmpty())" era ramo morto e saiu.
            val recentDaysWithLogs = pastDaysData.filter { it.third.isNotEmpty() }

            if (recentDaysWithLogs.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Nenhum registro recente de consumo de água.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                recentDaysWithLogs.forEach { (dateDisplay, _, dayLogs) ->
                    val totalMl = dayLogs.sumOf { it.amountMl }
                val goalReached = totalMl >= dailyGoalMl
                val litersStr = String.format(Locale("pt", "BR"), "%.1f Litros", totalMl / 1000f)

                // Format hours when water was taken on this day
                val timeListStr = if (dayLogs.isNotEmpty()) {
                    dayLogs.reversed().joinToString(", ") { log ->
                        "${logTimeFormatter.format(Date(log.timestamp))} (${log.amountMl}ml)"
                    }
                } else null

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            if (goalReached) SecondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.example.ui.components.MiniWaterGlassIcon(
                                        size = 30.dp,
                                        fillRatio = (totalMl.toFloat() / dailyGoalMl.toFloat()).coerceIn(0f, 1f)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dateDisplay,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        // A lista ja foi filtrada para dias com
                                        // registro, entao totalMl e sempre > 0.
                                        text = litersStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (goalReached) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(com.example.ui.theme.CyanAction)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Meta",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Display intake timestamps if any exist for this day
                        if (timeListStr != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SecondaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "⏰ Horários: $timeListStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = com.example.ui.theme.CyanAction,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Skipped Reminders Section
        Text(
            text = "Lembretes Esquecidos",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = "Mostrando o último registro de cada horário.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        if (skippedReminders.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Você não esqueceu nenhum lembrete. Parabéns!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            skippedReminders.forEach { reminder ->
                val dateText = reminder.skippedDate.ifBlank { reminder.date.ifBlank { "Hoje" } }
                val timeText = reminder.skippedTime.ifBlank { reminder.time }

                // O antigo "isRecovered" era inalcancavel: ao confirmar, o
                // confirmWaterAlert zera o skippedDate, e o lembrete cai fora
                // do filtro la em cima, que exige skippedDate preenchido.
                // A mensagem "Esqueceu as X, mas bebeu depois as Y" nunca
                // aparecia. Removido junto com as cores condicionais.
                val descText = "Esqueceu de beber em: $dateText às $timeText"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(ErrorContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                // O estado nao pode ser indicado so pela cor:
                                // leitores de tela precisam do rotulo.
                                contentDescription = "Lembrete esquecido",
                                tint = ErrorRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${reminder.title} ($timeText)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = descText,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    // Confirmation Dialog for Clearing History
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text("Limpar Dados?", fontWeight = FontWeight.Bold, color = ErrorRed)
            },
            text = {
                Column {
                    Text(
                        text = if (deleteRemindersToo)
                            "Os registros de consumo de água e TODOS os horários de lembretes salvos serão apagados do aplicativo."
                        else
                            "Os registros de consumo de água serão apagados e o status dos lembretes será zerado para pendente, mantendo os horários cadastrados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteRemindersToo = !deleteRemindersToo }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = deleteRemindersToo,
                            onCheckedChange = { deleteRemindersToo = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Apagar também os horários dos lembretes",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deleteRemindersToo) {
                            onClearEverything()
                        } else {
                            onClearHistory()
                        }
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Limpar", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Dialog for Selecting Month
    if (showMonthPickerDialog) {
        AlertDialog(
            onDismissRequest = { showMonthPickerDialog = false },
            title = {
                Text("Selecionar Mês do Histórico", fontWeight = FontWeight.Bold, color = com.example.ui.theme.CyanAction)
            },
            text = {
                Column {
                    Text(
                        text = "Escolha um mês para visualizar os dados de consumo:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    monthsList.chunked(2).forEachIndexed { rowIdx, pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            pair.forEachIndexed { colIdx, mName ->
                                val mIdx = rowIdx * 2 + colIdx
                                val isSelected = mIdx == selectedMonthIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) com.example.ui.theme.CyanAction else SecondaryContainer)
                                        .clickable {
                                            selectedMonthIndex = mIdx
                                        }
                                        // Antes so o padding de 10dp definia a
                                        // altura, dando cerca de 40dp.
                                        .heightIn(min = 48.dp)
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else PrimaryBlue
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showMonthPickerDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.CyanAction),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Concluir", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

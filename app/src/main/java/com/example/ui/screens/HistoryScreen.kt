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
import androidx.compose.material3.IconButton
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
import java.time.ZoneOffset
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
    onClearEverything: () -> Unit = {},
    onApagarConsumoDoDia: (String) -> Unit = {},
    onLimparRegistroEsquecido: (Reminder) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    val spTimeZone = TimeZone.getTimeZone("America/Sao_Paulo")
    val spZoneId = ZoneId.of("America/Sao_Paulo")

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var deleteRemindersToo by remember { mutableStateOf(false) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }

    // Alvos dos dois modais de exclusao. Guardam o item pendente de confirmacao.
    var diaParaApagar by remember { mutableStateOf<Pair<String, String>?>(null) } // (rotulo, chave)
    var esquecidoParaApagar by remember { mutableStateOf<Reminder?>(null) }

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

    /**
     * UM UNICO PERIODO MANDA NO GRAFICO E NA LISTA.
     *
     * Antes eram duas coisas diferentes na mesma tela: o grafico mostrava uma
     * semana e a lista mostrava o mes inteiro. Nao tinha como baterem.
     *
     * Agora o usuario escolhe um intervalo de datas no calendario e os dois
     * passam a mostrar exatamente os mesmos dias.
     *
     * Padrao: os ultimos 7 dias terminando hoje.
     */
    var rangeStart by remember { mutableStateOf(currentLocalDate.minusDays(6)) }
    var rangeEnd by remember { mutableStateOf(currentLocalDate) }

    val periodoTexto = remember(rangeStart, rangeEnd) {
        val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        if (rangeStart == rangeEnd) rangeStart.format(fmt)
        else "${rangeStart.format(fmt)} a ${rangeEnd.format(fmt)}"
    }

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

    /**
     * Os dias do periodo escolhido, do mais antigo para o mais novo.
     *
     * Usa java.time em vez de Calendar de proposito: o Calendar exigia cuidado
     * com a ordem dos set() para nao transbordar de mes, e ja tinha causado um
     * bug de data aqui.
     */
    val periodDaysData = remember(logs, rangeStart, rangeEnd) {
        val list = mutableListOf<Triple<String, String, List<WaterLog>>>()
        val keyFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val labelFmt = DateTimeFormatter.ofPattern("dd/MM")
        // DayOfWeek.value: 1 = segunda ... 7 = domingo
        val dayNames = arrayOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

        var dia = rangeStart
        while (!dia.isAfter(rangeEnd)) {
            val key = dia.format(keyFmt)
            val label = "${dayNames[dia.dayOfWeek.value - 1]}, ${dia.format(labelFmt)}"
            list.add(Triple(label, key, groupedLogs[key] ?: emptyList()))
            dia = dia.plusDays(1)
        }
        list
    }

    // Grafico e lista saem da MESMA fonte: e isso que garante a sincronia.
    val chartRatios = remember(periodDaysData, dailyGoalMl) {
        periodDaysData.map { (_, _, dayLogs) ->
            val totalMl = dayLogs.sumOf { it.amountMl }
            (totalMl.toFloat() / dailyGoalMl.toFloat()).coerceIn(0f, 1f)
        }
    }

    val chartDays = remember(periodDaysData) {
        periodDaysData.map { (dateDisplay, _, _) -> dateDisplay.take(3) }
    }

    // Media diaria: so conta dias que ja aconteceram.
    val dailyAvgText = remember(periodDaysData, currentLocalDate) {
        val hojeStr = currentLocalDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val diasPassados = periodDaysData.filter { it.second <= hojeStr }
        val soma = diasPassados.sumOf { it.third.sumOf { l -> l.amountMl } }
        val avg = if (diasPassados.isNotEmpty()) soma / diasPassados.size else 0
        String.format(Locale("pt", "BR"), "%.1fL", avg / 1000f)
    }

    // Esquecidos tambem seguem o periodo, e nao mais o mes.
    val skippedReminders = remember(reminders, rangeStart, rangeEnd) {
        reminders.filter { reminder ->
            if (reminder.skippedDate.isBlank()) false
            else {
                // skippedDate vem como "dd/MM/yyyy"
                val parts = reminder.skippedDate.split("/")
                if (parts.size == 3) {
                    val d = parts[0].toIntOrNull()
                    val m = parts[1].toIntOrNull()
                    val y = parts[2].toIntOrNull()
                    if (d != null && m != null && y != null) {
                        val data = runCatching { LocalDate.of(y, m, d) }.getOrNull()
                        data != null && !data.isBefore(rangeStart) && !data.isAfter(rangeEnd)
                    } else false
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
                    text = "Escolher Datas",
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
                text = "Período: $periodoTexto",
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
                        // Deixou de ser sempre uma semana: agora e o periodo
                        // escolhido, que pode ter 1 dia ou o mes inteiro.
                        text = "Resumo do Período",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${periodDaysData.size} dia(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Animated Weekly Water Drop Chart with real calculated ratios
                val todayDateStr = currentLocalDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                com.example.ui.components.AnimatedWaterDropChart(
                    days = chartDays,
                    fillRatios = chartRatios,
                    dates = periodDaysData.map { it.second },
                    totalsMl = periodDaysData.map { it.third.sumOf { l -> l.amountMl } },
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
            // Segue o mesmo periodo do grafico acima.
            text = "Dias do Período (com horários)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        run {
            // Os dias sem consumo NAO sao escondidos: era isso que dava a
            // impressao de que o historico pulava dias.
            // Mais recente primeiro, para hoje aparecer sem rolar a tela.
            val recentDaysWithLogs = periodDaysData.reversed()

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
                recentDaysWithLogs.forEach { (dateDisplay, dateKey, dayLogs) ->
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
                                        // A lista agora inclui dias sem consumo.
                                        text = if (totalMl > 0) litersStr else "Nenhum consumo registrado",
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
                                            contentDescription = "Meta do dia atingida",
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

                            // Lixeira do dia. So aparece quando ha o que apagar.
                            if (dayLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { diaParaApagar = dateDisplay to dateKey },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Apagar o consumo de $dateDisplay",
                                        tint = ErrorRed,
                                        modifier = Modifier.size(26.dp)
                                    )
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

                // O "bebeu depois" voltou a ser alcancavel.
                //
                // Ele era codigo morto porque o confirmWaterAlert zerava o
                // skippedDate ao confirmar, e o lembrete caia fora do filtro
                // que exige skippedDate preenchido. Agora esses campos sao
                // preservados, entao a hora em que a agua foi marcada na tela
                // de Lembretes aparece aqui, junto com a hora esquecida.
                val bebeuDepois = reminder.isCompleted
                val horaBebido = reminder.completedTime.ifBlank { reminder.time }

                val descText = if (bebeuDepois) {
                    "Esqueceu às $timeText, mas bebeu depois às $horaBebido"
                } else {
                    "Esqueceu de beber em: $dateText às $timeText"
                }

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
                        // O X virou lixeira e passou a ser tocavel: apaga o
                        // registro de esquecido para a lista nao acumular.
                        IconButton(
                            onClick = { esquecidoParaApagar = reminder },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (bebeuDepois) SecondaryContainer else ErrorContainer,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription =
                                        "Apagar o aviso de esquecido de ${reminder.title} das $timeText",
                                    tint = if (bebeuDepois) PrimaryBlue else ErrorRed,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
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
                                color = if (bebeuDepois) PrimaryBlue else ErrorRed,
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

    // Confirmacao para apagar o consumo de um dia
    diaParaApagar?.let { (rotulo, chave) ->
        AlertDialog(
            onDismissRequest = { diaParaApagar = null },
            title = { Text("Apagar este dia?", fontWeight = FontWeight.Bold, color = ErrorRed) },
            text = {
                Text(
                    text = "Os registros de água de $rotulo serão apagados. Esta ação não pode ser desfeita.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApagarConsumoDoDia(chave)
                        diaParaApagar = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Sim, apagar", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { diaParaApagar = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Não", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Confirmacao para apagar o aviso de esquecido
    esquecidoParaApagar?.let { reminder ->
        val hora = reminder.skippedTime.ifBlank { reminder.time }
        AlertDialog(
            onDismissRequest = { esquecidoParaApagar = null },
            title = { Text("Apagar este aviso?", fontWeight = FontWeight.Bold, color = ErrorRed) },
            text = {
                Text(
                    // Deixa explicito que o lembrete NAO some, senao o idoso
                    // pensa que apagou o alarme das $hora.
                    text = "O aviso de que você esqueceu de beber às $hora sai da lista.\n\n" +
                           "O lembrete das $hora continua salvo e vai tocar normalmente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLimparRegistroEsquecido(reminder)
                        esquecidoParaApagar = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Sim, apagar", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { esquecidoParaApagar = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Não", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
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

    /**
     * CALENDARIO COM INTERVALO, no lugar da grade de 12 meses.
     *
     * O DateRangePicker do Material 3 ja resolve tudo que foi pedido: mostra o
     * calendario de verdade, navega mes e ano, e o usuario toca no dia inicial
     * e no dia final para marcar o intervalo. Nao vale a pena desenhar um
     * calendario na mao.
     *
     * As datas do componente vem em milissegundos UTC a meia-noite, por isso a
     * conversao usa ZoneOffset.UTC -- usar o fuso local aqui deslocaria o dia.
     */
    if (showMonthPickerDialog) {
        DateRangeCalendarDialog(
            inicialSelecionado = rangeStart,
            finalSelecionado = rangeEnd,
            hoje = currentLocalDate,
            onDismiss = { showMonthPickerDialog = false },
            onConfirm = { inicio, fim ->
                rangeStart = inicio
                rangeEnd = fim
                showMonthPickerDialog = false
            }
        )
    }
}

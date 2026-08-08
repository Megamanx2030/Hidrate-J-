package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.Reminder
import com.example.data.db.UserSettings
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryContainer

/**
 * Chave liga/desliga com a palavra do estado EMBAIXO, nao ao lado.
 *
 * Antes era "Ligado" / "Desl." lado a lado com a chave. O "Desl." abreviado
 * existia so porque a palavra inteira nao cabia -- e num app para idoso
 * abreviacao e o pior desfecho possivel: quem le "Desl." nao tem certeza do que
 * esta valendo.
 *
 * Empilhado, a coluna fica MAIS ESTREITA do que a versao lado a lado (a largura
 * passa a ser a da maior das duas coisas, e nao a soma), entao sobra mais espaco
 * para o titulo a esquerda e ainda cabe "Desligado" por extenso. Isso resolve
 * tambem o corte que aparecia no Samsung, onde a fonte do sistema e maior.
 */
@Composable
private fun EstadoDoInterruptor(
    ligado: Boolean,
    onMudou: (Boolean) -> Unit,
    testTag: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Switch(
            checked = ligado,
            onCheckedChange = onMudou,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue
            ),
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (ligado) "Ligado" else "Desligado",
            style = MaterialTheme.typography.bodySmall,
            color = if (ligado) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun RemindersScreen(
    settings: UserSettings,
    reminders: List<Reminder>,
    onToggleAlerts: (Boolean) -> Unit,
    onToggleVibrateOnly: (Boolean) -> Unit,
    onSelectChime: (String) -> Unit,
    onTestAlert: () -> Unit,
    onUpdateReminder: (Reminder) -> Unit,
    onAddReminder: (String, String, String) -> Unit,
    onDeleteReminder: (Reminder) -> Unit,
    onToggleCompleted: (Reminder) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var isNewReminder by remember { mutableStateOf(false) }

    val chimeOptions = listOf("Sino Suave", "Gota D'Água", "Harpa Melódica", "Sino de Cristal")

    // Datas de referencia para escrever "hoje" e "amanhã" no lugar de
    // "09/08/2026". Mesmo formato e mesmo fuso que o resto do app grava.
    val hojeStr = remember {
        val zona = java.time.ZoneId.of("America/Sao_Paulo")
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .format(java.time.LocalDate.now(zona))
    }
    val amanhaStr = remember {
        val zona = java.time.ZoneId.of("America/Sao_Paulo")
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .format(java.time.LocalDate.now(zona).plusDays(1))
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
            text = "Meus Lembretes",
            style = MaterialTheme.typography.headlineMedium,
            color = PrimaryBlue,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Configure seus alertas para manter-se hidratado durante o dia.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Activation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ativar Alertas",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Das ${settings.startTime} às ${settings.endTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    EstadoDoInterruptor(
                        ligado = settings.alertsEnabled,
                        onMudou = onToggleAlerts,
                        testTag = "alert_toggle_switch"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Somente Vibrar",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Desativa o som e apenas vibra",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    EstadoDoInterruptor(
                        ligado = settings.vibrateOnly,
                        onMudou = onToggleVibrateOnly
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Alerta dura por ${settings.ringtoneDurationSeconds}s e para automaticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test button
                OutlinedButton(
                    onClick = onTestAlert,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("test_alert_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = PrimaryBlue
                    ),
                    border = androidx.compose.foundation.BorderStroke(2.dp, PrimaryBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Sem maxLines = 1: com a fonte do sistema aumentada o
                        // rotulo virava "Testar Campainha e Vibra...". Quebrar
                        // em duas linhas mostra a frase toda.
                        Text(
                            text = "Testar Campainha e Vibração",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Chime Choice Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tipo de Campainha",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Selecione o som do alerta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                chimeOptions.forEach { option ->
                    val isSelected = settings.chimeType.contains(option, ignoreCase = true)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) SecondaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { onSelectChime(option) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) com.example.ui.theme.CyanAction else SecondaryContainer,
                                    contentColor = if (isSelected) Color.White else PrimaryBlue
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = ButtonDefaults.ContentPadding
                            ) {
                                Text(
                                    // "Ouvir / Escolher" era a maior etiqueta da
                                    // tela e empurrava o nome da campainha para
                                    // fora. "Ouvir" diz a mesma coisa e devolve
                                    // o espaco para o nome.
                                    text = if (isSelected) "Selecionado" else "Ouvir",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    // Era 11sp por sobrescrita local, o que
                                    // anulava a escala do tema.
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Today's Schedule Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Horários de Hoje",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = {
                    editingReminder = null
                    isNewReminder = true
                    showEditDialog = true
                },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = PrimaryBlue
                ),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black),
                shape = RoundedCornerShape(12.dp),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Adicionar",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                if (reminders.isNotEmpty()) {
                    val inlineId = "checkIcon"
                    val text = buildAnnotatedString {
                        append("Esqueceu de marcar? Toque no ")
                        appendInlineContent(inlineId, "[icon]")
                        append(" para registrar que bebeu.")
                    }
                    val inlineContent = mapOf(
                        Pair(
                            inlineId,
                            InlineTextContent(
                                Placeholder(
                                    width = 20.sp,
                                    height = 20.sp,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                                )
                            ) {
                                Icon(Icons.Default.CheckCircle, "", tint = PrimaryBlue)
                            }
                        )
                    )
                    Text(
                        text = text,
                        inlineContent = inlineContent,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                // AS FRASES LONGAS FICAM NUMA FAIXA SO DELAS.
                //
                // Antes o horario, o titulo, o estado e a data disputavam a
                // mesma linha com os dois botoes. No Motorola cabia; no Samsung,
                // que roda com fonte e tela maiores, sobravam menos de 100dp
                // para o texto e as frases saiam cortadas: "Esqueceu de b...",
                // "Data: 09/08/20...". Nenhum ajuste de tamanho resolveria isso
                // para sempre -- o usuario pode aumentar a fonte a qualquer
                // momento, e num app para idoso ele provavelmente VAI aumentar.
                //
                // Divisao atual: horario, titulo e botoes na primeira linha
                // (o titulo ocupa a sobra do meio, que senao ficaria branca);
                // estado e data na linha de baixo, usando a largura inteira do
                // cartao. As frases compridas nunca mais competem por espaco.
                reminders.forEachIndexed { index, reminder ->
                    val timeParts = reminder.time.split(":")
                    val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
                    val isDay = hour in 6..17
                    val timeIcon = if (isDay) Icons.Default.WbSunny else Icons.Default.ModeNight
                    val timeIconColor = if (isDay) Color(0xFFFFC107) else Color(0xFF90CAF9)
                    val timeDesc = if (isDay) "Horário da manhã" else "Horário da noite"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = timeIcon,
                                contentDescription = timeDesc,
                                tint = timeIconColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // Time badge: solid blue when completed, bordered when pending
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 76.dp)
                                    .heightIn(min = 36.dp)
                                    .then(
                                        if (reminder.isCompleted) {
                                            Modifier.background(PrimaryBlue, RoundedCornerShape(18.dp))
                                        } else {
                                            Modifier
                                                .background(Color.White, RoundedCornerShape(18.dp))
                                                .border(2.dp, PrimaryBlue, RoundedCornerShape(18.dp))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = reminder.time,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (reminder.isCompleted) Color.White else PrimaryBlue,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            // O titulo fica AQUI, na sobra ao lado do horario.
                            // Numa linha propria ele deixava uma faixa branca
                            // vazia atravessando o cartao inteiro.
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = reminder.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = { onToggleCompleted(reminder) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (reminder.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = if (reminder.isCompleted) "Desmarcar" else "Marcar que bebeu água",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    editingReminder = reminder
                                    isNewReminder = false
                                    showEditDialog = true
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar horário",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        // AS DUAS INFORMACOES CONVIVEM.
                        //
                        // Antes era um when: marcar o circulo fazia o
                        // "Esqueceu de beber" desaparecer, como se o
                        // lembrete nunca tivesse sido perdido. O dado
                        // continuava no banco (o skippedTime nao e
                        // apagado), so nao era mostrado.
                        //
                        // Agora, se o lembrete foi esquecido E depois
                        // bebido, as duas linhas aparecem juntas.
                        val foiEsquecido = reminder.isSkipped || reminder.skippedTime.isNotBlank()
                        val horaEsquecido = reminder.skippedTime.ifBlank { reminder.time }
                        val horaBebido = reminder.completedTime.ifBlank { reminder.time }

                        // "hoje" em vez da data inteira: as duas coisas ficam
                        // no MESMO dia (o status e zerado na virada), entao
                        // repetir "09/08/2026" so gastava linha. Quando por
                        // algum motivo a data nao for a de hoje, ela aparece
                        // curta (dd/MM), sem o ano.
                        val diaEsquecido = when {
                            reminder.skippedDate.isBlank() -> "hoje"
                            reminder.skippedDate == hojeStr -> "hoje"
                            else -> "em ${reminder.skippedDate.take(5)}"
                        }

                        if (reminder.isCompleted) {
                            Text(
                                text = "Bebido hoje às $horaBebido",
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (foiEsquecido) {
                            Text(
                                text = "Esqueceu $diaEsquecido às $horaEsquecido",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (!reminder.isCompleted && !foiEsquecido) {
                            Text(
                                text = "Ainda não bebeu",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        /**
                         * "Data: 09/08/2026" ERA A INFORMACAO MAIS ENGANOSA DA
                         * TELA.
                         *
                         * Parecia a data do registro que estava logo acima --
                         * "esqueceu de beber às 16:03" e, embaixo, uma data de
                         * amanha. Na verdade esse campo guarda a PROXIMA vez
                         * que o alarme toca. Dizer isso com todas as letras, e
                         * so o dia (nunca o ano), resolve a leitura e ainda
                         * encurta a linha.
                         */
                        val proximoToque = when {
                            reminder.date.isBlank() -> null
                            reminder.date == hojeStr -> "Toca hoje"
                            reminder.date == amanhaStr -> "Toca de novo amanhã"
                            else -> "Toca em ${reminder.date.take(5)}"
                        }
                        if (proximoToque != null) {
                            Text(
                                text = proximoToque,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Edit Reminder Dialog
        if (showEditDialog) {
            EditReminderDialog(
                reminder = editingReminder,
                onDismiss = {
                    showEditDialog = false
                    editingReminder = null
                },
                onSave = { time, date, title ->
                    if (isNewReminder) {
                        onAddReminder(time, date, title)
                    } else {
                        editingReminder?.let { target ->
                            onUpdateReminder(target.copy(time = time, date = date, title = title))
                        }
                    }
                    showEditDialog = false
                    editingReminder = null
                },
                onDelete = if (!isNewReminder && editingReminder != null) {
                    {
                        editingReminder?.let { onDeleteReminder(it) }
                        showEditDialog = false
                        editingReminder = null
                    }
                } else null
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

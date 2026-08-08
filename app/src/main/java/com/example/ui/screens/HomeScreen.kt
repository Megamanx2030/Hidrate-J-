package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.UserSettings
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SecondaryContainer

@Composable
fun HomeScreen(
    settings: UserSettings,
    todayTotalMl: Int,
    monthlyTotalMl: Int,
    reminders: List<com.example.data.db.Reminder>,
    onAddWater: (Int) -> Unit,
    onOpenAddDialog: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Use safe division to avoid divide-by-zero
    val glassSizeMl = settings.glassSizeMl.coerceAtLeast(1)
    val dailyGoalMl = settings.dailyGoalMl.coerceAtLeast(1)

    val cupsDrunk = todayTotalMl / glassSizeMl
    val totalCupsTarget = (dailyGoalMl / glassSizeMl).coerceAtLeast(1)
    val dailyProgress = (todayTotalMl.toFloat() / dailyGoalMl.toFloat()).coerceIn(0f, 1f)

    /**
     * BRINCAR COM A JARRA E LIVRE, E NAO REGISTRA NADA.
     *
     * Enquanto o dedo estiver encostado, a agua sobe ate encher. Assim que
     * solta, ela desce sozinha ate o nivel de verdade -- o que a pessoa bebeu
     * e marcou no lembrete.
     *
     * O cuidado que motivou o limite anterior (10% acima do real, voltando
     * depois de 4 segundos) continua valendo: NUMERO NA TELA NAO PODE MENTIR.
     * So que ele nao precisa ser resolvido travando a agua. Os numeros logo
     * abaixo vem sempre de todayTotalMl e nao se mexem, e enquanto o dedo esta
     * na jarra a frase de apoio avisa, com todas as letras, que aquilo e so
     * uma brincadeira.
     */
    var jarraPressionada by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val nivelDaJarra by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (jarraPressionada) 1f else dailyProgress,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (jarraPressionada) 2200 else 900,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "nivel_jarra"
    )

    val monthlyGoalMl = (settings.monthlyGoalLiters * 1000).toInt().coerceAtLeast(1)
    val monthlyProgress = (monthlyTotalMl.toFloat() / monthlyGoalMl.toFloat()).coerceIn(0f, 1f)
    val monthlyDrunkLiters = String.format("%.1f", monthlyTotalMl / 1000f)
    val monthlyTargetLiters = String.format("%.1f", settings.monthlyGoalLiters)
    val monthlyRemainingLiters = String.format("%.1f", ((monthlyGoalMl - monthlyTotalMl).coerceAtLeast(0)) / 1000f)

    val spZone = java.time.ZoneId.of("America/Sao_Paulo")

    /**
     * O "Proximo lembrete" congelava.
     *
     * O remember dependia so da lista, e a hora era lida DENTRO do bloco.
     * Enquanto os lembretes nao mudassem, o texto nunca era recalculado --
     * o app ficava anunciando um horario que ja tinha passado.
     *
     * Agora a hora atual e estado, atualizado a cada 30s. O recalculo so
     * acontece quando o minuto vira, porque a chave e a string "HH:mm".
     */
    var currentHHmm by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            java.time.LocalTime.now(spZone).let { String.format("%02d:%02d", it.hour, it.minute) }
        )
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            currentHHmm = java.time.LocalTime.now(spZone)
                .let { String.format("%02d:%02d", it.hour, it.minute) }
        }
    }

    // Calculate next upcoming reminder in São Paulo time
    val nextReminderText = androidx.compose.runtime.remember(reminders, currentHHmm) {
        if (reminders.isEmpty()) {
            "Nenhum agendado"
        } else {
            val upcoming = reminders.sortedBy { it.time }.firstOrNull { it.time > currentHHmm }
            if (upcoming != null) {
                "às ${upcoming.time} - ${upcoming.title}"
            } else {
                val first = reminders.minByOrNull { it.time }
                if (first != null) "Amanhã às ${first.time}" else "Nenhum agendado"
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
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.example.ui.components.MiniJarraECopo(
                    size = 32.dp,
                    fillRatio = 0.5f
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hidrate Já",
                    style = MaterialTheme.typography.titleLarge,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        // Welcome Greeting
        Text(
            text = "Olá, ${settings.userName}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Vamos manter a hidratação hoje.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        /**
         * ERA UM LAPIS SOZINHO NO CANTO, SEM NOME.
         *
         * Icone sem rotulo obriga a pessoa a adivinhar, e quem nao adivinha
         * nunca abre os ajustes: fica para sempre com a meta e o copo que
         * vieram de fabrica.
         *
         * Virou a linha de ajuste que todo mundo ja conhece do proprio Android
         * -- icone redondo, titulo, subtitulo e a setinha da direita. E o
         * subtitulo mostra os VALORES DE AGORA: alem de dizer onde se mexe, ele
         * ja responde "quanto esta valendo hoje" sem precisar abrir.
         */
        val metaLitrosStr = String.format(java.util.Locale("pt", "BR"), "%.1f", dailyGoalMl / 1000f)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("settings_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(PrimaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Configurar",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Meta: $metaLitrosStr L  ·  Copo: ${settings.glassSizeMl} ml",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Abrir configurações",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        /**
         * A JARRA E OS NUMEROS DELA MORAM NO MESMO CARTAO.
         *
         * Antes o desenho e as frases ficavam soltos sobre o fundo azul, um
         * embaixo do outro, sem nada dizendo que aquilo era um bloco so. Num
         * app para idoso isso pesa: a pessoa nao sabe onde uma informacao
         * comeca e a outra acaba.
         *
         * Agora e um cartao branco unico -- desenho, porcentagem, barra e as
         * duas frases -- do mesmo jeito que os cartoes de lembrete logo abaixo.
         * A tela inteira passa a ser uma pilha de blocos, que e o que se le
         * mais rapido.
         */
        val ptBR = java.util.Locale("pt", "BR")
        val bebidoL = String.format(ptBR, "%.1f", todayTotalMl / 1000f)
        val faltaMl = (dailyGoalMl - todayTotalMl).coerceAtLeast(0)
        val coposQueFaltam = ((faltaMl + glassSizeMl - 1) / glassSizeMl).coerceAtLeast(0)
        val metaAlcancada = todayTotalMl >= dailyGoalMl

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                com.example.ui.components.JarraDeAguaAnimada(
                    progresso = nivelDaJarra,
                    modifier = Modifier
                        .size(230.dp, 230.dp)
                        // onPress + tryAwaitRelease em vez de clickable: aqui
                        // interessa o dedo ENCOSTADO, nao o toque completo. Com
                        // clickable a agua so reagiria depois de soltar.
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    jarraPressionada = true
                                    tryAwaitRelease()
                                    jarraPressionada = false
                                }
                            )
                        }
                )

                Text(
                    text = "${(dailyProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineLarge,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 46.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Barra desenhada com duas caixas em vez do
                // LinearProgressIndicator: a altura e o arredondamento ficam
                // exatamente como se quer, e o desenho e o mesmo em qualquer
                // versao do Material.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryContainer)
                ) {
                    if (dailyProgress > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(dailyProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (metaAlcancada) com.example.ui.theme.SuccessGreen else PrimaryBlue
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Você bebeu $bebidoL L de $metaLitrosStr L hoje",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (metaAlcancada)
                        "Parabéns, você bateu a meta de hoje!"
                    else
                        "Faltam $coposQueFaltam copo(s) de ${settings.glassSizeMl} ml",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (metaAlcancada) com.example.ui.theme.SuccessGreen else PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (jarraPressionada)
                        "É só uma brincadeira, nada foi registrado"
                    else
                        "Segure o dedo na jarra para ver a água subir",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (jarraPressionada) com.example.ui.theme.CyanAction
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        // Next Reminder Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(PrimaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Próximo lembrete",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = nextReminderText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Synchronized Active Reminders List Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Lembretes Salvos (${reminders.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (reminders.isEmpty()) {
                    Text(
                        text = "Nenhum lembrete salvo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    reminders.sortedBy { it.time }.forEach { reminder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(PrimaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WaterDrop,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                val timeParts = reminder.time.split(":")
                                val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
                                val isDay = hour in 6..17
                                val timeIcon = if (isDay) Icons.Default.WbSunny else Icons.Default.ModeNight
                                val timeIconColor = if (isDay) Color(0xFFFFC107) else Color(0xFF90CAF9)
                                val timeDesc = if (isDay) "Horário da manhã" else "Horário da noite"
                                
                                Icon(
                                    imageVector = timeIcon,
                                    contentDescription = timeDesc,
                                    tint = timeIconColor,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = reminder.time,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = reminder.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (reminder.date.isNotBlank()) {
                                Text(
                                    text = reminder.date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

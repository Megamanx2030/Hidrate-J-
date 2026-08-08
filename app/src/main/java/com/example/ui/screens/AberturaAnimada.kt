package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.JarraDeAguaAnimada
import kotlinx.coroutines.delay

/** Mesma cor do windowSplashScreenBackground: a troca de telas fica invisivel. */
val FundoAbertura = Color(0xFFB4E4F3)

/**
 * Tela de abertura do app -- a "logo viva".
 *
 * POR QUE ELA EXISTE:
 * o Android nunca anima o icone do launcher: ali so cabe imagem parada (PNG ou
 * WEBP), e o pouco de animacao que o sistema aceita fica dentro de um circulo
 * pequeno no meio da tela -- foi por isso que a logo aparecia miudinha na
 * abertura. Instagram, Inter e iFood resolvem do mesmo jeito que aqui: o icone
 * do launcher continua sendo imagem, e a marca "viva" e uma TELA DE ABERTURA
 * desenhada pelo proprio app, ocupando a tela inteira.
 *
 * A jarra e o copo sao os mesmos da tela inicial, entao a abertura emenda com o
 * app em vez de parecer um cartaz colado na frente.
 */
@Composable
fun AberturaAnimada(onTerminou: () -> Unit) {
    var comecou by remember { mutableStateOf(false) }
    var saindo by remember { mutableStateOf(false) }

    val nivelDaAgua by animateFloatAsState(
        targetValue = if (comecou) 0.76f else 0f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "enche"
    )
    val escala by animateFloatAsState(
        targetValue = if (comecou) 1f else 0.78f,
        animationSpec = tween(durationMillis = 780, easing = LinearOutSlowInEasing),
        label = "escala"
    )
    val alfaJarra by animateFloatAsState(
        targetValue = if (comecou) 1f else 0f,
        animationSpec = tween(durationMillis = 480),
        label = "alfa_jarra"
    )
    val alfaNome by animateFloatAsState(
        targetValue = if (comecou) 1f else 0f,
        animationSpec = tween(durationMillis = 620, delayMillis = 620),
        label = "alfa_nome"
    )
    val alfaTela by animateFloatAsState(
        targetValue = if (saindo) 0f else 1f,
        animationSpec = tween(durationMillis = 420),
        label = "alfa_tela",
        finishedListener = { if (saindo) onTerminou() }
    )

    LaunchedEffect(Unit) {
        comecou = true
        delay(2300)
        saindo = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alfaTela)
            .background(FundoAbertura),
        contentAlignment = Alignment.Center
    ) {
        // Clarao atras da jarra, para ela nao ficar chapada no fundo liso.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x99FFFFFF), Color(0x00FFFFFF)),
                        center = Offset.Unspecified,
                        radius = 620f
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            JarraDeAguaAnimada(
                progresso = nivelDaAgua,
                modifier = Modifier
                    .size(250.dp)
                    .scale(escala)
                    .alpha(alfaJarra)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(alfaNome)
            ) {
                // Cores mais fechadas do que as da logo original: ali o texto
                // fica sobre branco, aqui sobre azul claro. Mantendo o tom
                // original o nome quase sumia no fundo.
                Text(
                    text = "HIDRATE",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0E8CBB),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = Color(0xFF0E6BAE),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "JÁ",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0A4E8C),
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

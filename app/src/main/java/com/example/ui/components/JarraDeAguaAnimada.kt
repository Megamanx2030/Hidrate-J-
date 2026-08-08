package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Geometria compartilhada
// ---------------------------------------------------------------------------

/**
 * As formas da jarra e do copo, em pixels, para uma area de desenho w x h.
 *
 * Fica separado dos composables de proposito: a jarra grande da tela inicial e
 * a miniatura das listas precisam ser IDENTICAS. Quando a geometria estava
 * duplicada, qualquer ajuste numa esquecia a outra.
 */
private class FormasJarraECopo(
    val corpoJarra: Path,
    val bocaFrenteJarra: Path,
    val asa: Path,
    val copo: Path,
    val bocaFrenteCopo: Path,
    val topoJarra: Float,
    val fundoJarra: Float,
    val centroJarra: Float,
    val larguraJarra: Float,
    val topoCopo: Float,
    val fundoCopo: Float,
    val centroCopo: Float,
    val larguraCopo: Float
)

private fun construirFormas(w: Float, h: Float): FormasJarraECopo {
    // ---- Jarra ----
    val bocaEsq = w * 0.400f
    val bocaDir = w * 0.815f
    val topo = h * 0.170f
    val fundo = h * 0.930f
    val ryBoca = h * 0.028f
    val cxBoca = (bocaEsq + bocaDir) / 2f
    val rxBoca = (bocaDir - bocaEsq) / 2f
    val bojoEsq = bocaEsq - w * 0.058f
    val bojoDir = bocaDir + w * 0.058f
    val baseEsq = cxBoca - w * 0.190f
    val baseDir = cxBoca + w * 0.190f
    val raioBase = h * 0.040f

    /**
     * O BICO E A PROPRIA BOCA PUXADA PARA FORA.
     *
     * Duas tentativas anteriores trataram o bico como uma peca separada,
     * encostada na borda: na primeira sobrou uma fresta no encontro, na segunda
     * ele virou uma argolinha no canto. Numa jarra de verdade -- como na foto
     * de referencia -- nao existe peca colada nenhuma: a BOCA e que deixa de
     * ser um oval perfeito e se estica num labio de um lado so.
     *
     * Entao a boca aqui e um oval de quatro curvas cujo lado esquerdo termina
     * num ponto (L) mais para fora. Sem emenda, sem cruzamento de tracos e sem
     * vao: e uma curva so, continua.
     */
    val bico = w * 0.048f
    val pontaBico = Offset(bocaEsq - bico, topo)

    // Costas da boca: da ponta do bico, por tras, ate a direita.
    fun Path.costasDaBoca() {
        cubicTo(
            pontaBico.x + bico * 0.55f, topo - ryBoca * 0.72f,
            cxBoca - rxBoca * 0.50f, topo - ryBoca * 1.02f,
            cxBoca, topo - ryBoca
        )
        cubicTo(
            cxBoca + rxBoca * 0.55f, topo - ryBoca,
            bocaDir, topo - ryBoca * 0.55f,
            bocaDir, topo
        )
    }

    // Frente da boca: da direita, pela frente, de volta a ponta do bico.
    fun Path.frenteDaBoca() {
        cubicTo(
            bocaDir, topo + ryBoca * 0.55f,
            cxBoca + rxBoca * 0.55f, topo + ryBoca,
            cxBoca, topo + ryBoca
        )
        cubicTo(
            cxBoca - rxBoca * 0.50f, topo + ryBoca * 1.02f,
            pontaBico.x + bico * 0.55f, topo + ryBoca * 0.72f,
            pontaBico.x, pontaBico.y
        )
    }

    /**
     * O topo do corpo e o lado de TRAS da boca, nunca uma linha reta.
     *
     * Antes o contorno fechava o topo com um segmento de uma borda a outra
     * (o close() do Path) e a elipse era desenhada por cima. As duas apareciam
     * juntas: era a "linha dividindo a boca" -- jarra nao tem tampa.
     */
    val corpoJarra = Path().apply {
        moveTo(pontaBico.x, pontaBico.y)
        costasDaBoca()
        cubicTo(bocaDir + w * 0.008f, h * 0.40f, bojoDir, h * 0.50f, bojoDir, h * 0.655f)
        cubicTo(bojoDir, h * 0.800f, baseDir, h * 0.855f, baseDir, fundo - raioBase)
        quadraticTo(baseDir, fundo, baseDir - raioBase, fundo)
        lineTo(baseEsq + raioBase, fundo)
        quadraticTo(baseEsq, fundo, baseEsq, fundo - raioBase)
        cubicTo(baseEsq, h * 0.855f, bojoEsq, h * 0.800f, bojoEsq, h * 0.655f)
        // A parede esquerda so abre para fora nos ultimos centimetros, que e o
        // que forma o labio: o controle fica em bocaEsq e o fim, em pontaBico.
        cubicTo(bojoEsq, h * 0.50f, bocaEsq, h * 0.40f, pontaBico.x, pontaBico.y)
        close()
    }

    // A borda de perto, vista por dentro do vidro.
    val bocaFrenteJarra = Path().apply {
        moveTo(bocaDir, topo)
        frenteDaBoca()
    }

    val asa = Path().apply {
        moveTo(bocaDir - w * 0.004f, topo + h * 0.065f)
        cubicTo(w * 0.975f, h * 0.235f, w * 0.985f, h * 0.470f, w * 0.885f, h * 0.560f)
        cubicTo(w * 0.862f, h * 0.583f, w * 0.858f, h * 0.594f, bojoDir - w * 0.006f, h * 0.600f)
    }

    // ---- Copo ----
    //
    // AFASTADO DA JARRA DE PROPOSITO. Antes a borda direita do copo entrava no
    // bojo da jarra: como os dois sao de vidro translucido, os contornos se
    // cruzavam e eles pareciam uma peca so, colada. Agora ha uma folga entre as
    // duas silhuetas em toda a altura.
    val copoTopoEsq = w * 0.045f
    val copoTopoDir = w * 0.285f
    val copoBaseEsq = w * 0.072f
    val copoBaseDir = w * 0.262f
    val copoTopo = h * 0.455f
    val copoFundo = h * 0.930f
    val ryCopo = h * 0.019f
    val raioCopo = h * 0.020f
    val retBocaCopo = Rect(copoTopoEsq, copoTopo - ryCopo, copoTopoDir, copoTopo + ryCopo)

    val copo = Path().apply {
        moveTo(copoTopoEsq, copoTopo)
        arcTo(retBocaCopo, 180f, 180f, false)
        lineTo(copoBaseDir, copoFundo - raioCopo)
        quadraticTo(copoBaseDir, copoFundo, copoBaseDir - raioCopo, copoFundo)
        lineTo(copoBaseEsq + raioCopo, copoFundo)
        quadraticTo(copoBaseEsq, copoFundo, copoBaseEsq, copoFundo - raioCopo)
        close()
    }

    val bocaFrenteCopo = Path().apply { arcTo(retBocaCopo, 0f, 180f, true) }

    return FormasJarraECopo(
        corpoJarra = corpoJarra,
        bocaFrenteJarra = bocaFrenteJarra,
        asa = asa,
        copo = copo,
        bocaFrenteCopo = bocaFrenteCopo,
        topoJarra = topo + ryBoca,
        fundoJarra = fundo,
        centroJarra = cxBoca,
        larguraJarra = bojoDir - bojoEsq,
        topoCopo = copoTopo + ryCopo,
        fundoCopo = copoFundo,
        centroCopo = (copoTopoEsq + copoTopoDir) / 2f,
        larguraCopo = copoTopoDir - copoTopoEsq
    )
}

private val CorContorno = Color(0x8C1B5E9E)
private val CorVidro = Color(0x38FFFFFF)

// ---------------------------------------------------------------------------
// Jarra grande, animada
// ---------------------------------------------------------------------------

/**
 * A jarra e o copo da logo do app, desenhados em codigo e com a agua se
 * mexendo de verdade.
 *
 * POR QUE DESENHADO E NAO UMA IMAGEM:
 * a logo original e um PNG. PNG e uma foto parada -- nao tem como a agua ondular
 * nem as bolhas subirem. Aqui a mesma jarra e feita de curvas, entao o nivel da
 * agua acompanha o consumo do dia, a superficie ondula e as bolhas sobem, em
 * qualquer tamanho de tela e sem serrilhado.
 *
 * As medidas sao todas em FRACAO da area de desenho, nunca em pixels fixos.
 *
 * @param progresso 0f a 1f -- quanto da meta do dia ja foi bebido.
 */
@Composable
fun JarraDeAguaAnimada(
    progresso: Float,
    modifier: Modifier = Modifier,
    mostrarCopo: Boolean = true
) {
    val nivel = progresso.coerceIn(0f, 1f)

    val transicao = rememberInfiniteTransition(label = "jarra")

    val faseOnda by transicao.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fase_onda"
    )

    // Segunda onda, mais lenta e defasada: e o que tira o ar de "onda de
    // desenho animado" e deixa a superficie parecendo agua mesmo.
    val faseOnda2 by transicao.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fase_onda_2"
    )

    val subidaBolhas by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bolhas"
    )

    val brilho by transicao.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "brilho"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val traco = w * 0.012f
            val f = construirFormas(w, h)

            // ---- Jarra ----
            drawPath(f.corpoJarra, color = CorVidro)

            desenharAgua(
                recipiente = f.corpoJarra,
                topoInterno = f.topoJarra,
                fundoInterno = f.fundoJarra - traco,
                nivel = nivel,
                faseOnda = faseOnda,
                faseOnda2 = faseOnda2,
                amplitude = h * 0.016f,
                subidaBolhas = subidaBolhas,
                centro = f.centroJarra,
                largura = f.larguraJarra
            )

            drawLine(
                color = Color.White.copy(alpha = brilho),
                start = Offset(w * 0.435f, h * 0.245f),
                end = Offset(w * 0.398f, h * 0.790f),
                strokeWidth = w * 0.022f,
                cap = StrokeCap.Round
            )

            drawPath(
                f.asa,
                color = CorContorno,
                style = Stroke(width = traco * 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                f.asa,
                color = Color(0x59FFFFFF),
                style = Stroke(width = traco * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            drawPath(f.corpoJarra, color = CorContorno, style = Stroke(width = traco, join = StrokeJoin.Round))
            drawPath(f.bocaFrenteJarra, color = CorContorno, style = Stroke(width = traco))

            // ---- Copo ----
            if (mostrarCopo) {
                val tracoCopo = traco * 0.9f
                drawPath(f.copo, color = CorVidro)

                desenharAgua(
                    recipiente = f.copo,
                    topoInterno = f.topoCopo,
                    fundoInterno = f.fundoCopo - tracoCopo,
                    nivel = nivel,
                    faseOnda = faseOnda + 1.2f,
                    faseOnda2 = faseOnda2,
                    amplitude = h * 0.010f,
                    subidaBolhas = (subidaBolhas + 0.42f) % 1f,
                    centro = f.centroCopo,
                    largura = f.larguraCopo
                )

                drawLine(
                    color = Color.White.copy(alpha = brilho),
                    start = Offset(w * 0.082f, h * 0.510f),
                    end = Offset(w * 0.098f, h * 0.860f),
                    strokeWidth = w * 0.016f,
                    cap = StrokeCap.Round
                )

                drawPath(f.copo, color = CorContorno, style = Stroke(width = tracoCopo, join = StrokeJoin.Round))
                drawPath(f.bocaFrenteCopo, color = CorContorno, style = Stroke(width = tracoCopo))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Miniatura
// ---------------------------------------------------------------------------

/**
 * A mesma jarra com o copo, do tamanho dos antigos copinhos.
 *
 * Substituiu o MiniWaterGlassIcon (um copo com carinha) nas listas, no grafico
 * e no cabecalho: a marca do app e uma jarra com copo, entao um copo sozinho
 * era outro desenho competindo com ela na mesma tela.
 *
 * Sem onda, sem bolha e sem brilho pulsando: estes icones aparecem varias vezes
 * por tela, inclusive dentro de listas, e animar todos custaria caro sem
 * ninguem enxergar a diferenca em 30dp.
 */
@Composable
fun MiniJarraECopo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    fillRatio: Float = 0.85f
) {
    val nivel = fillRatio.coerceIn(0f, 1f)

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Traco proporcionalmente mais grosso que na jarra grande: em 30dp um
        // fio de 1px desaparece contra o fundo claro.
        val traco = w * 0.028f
        val f = construirFormas(w, h)

        drawPath(f.corpoJarra, color = CorVidro)
        desenharAguaParada(f.corpoJarra, f.topoJarra, f.fundoJarra - traco, nivel)

        drawPath(f.asa, color = CorContorno, style = Stroke(width = traco * 1.6f, cap = StrokeCap.Round))
        drawPath(f.corpoJarra, color = CorContorno, style = Stroke(width = traco, join = StrokeJoin.Round))
        drawPath(f.bocaFrenteJarra, color = CorContorno, style = Stroke(width = traco * 0.8f))

        drawPath(f.copo, color = CorVidro)
        desenharAguaParada(f.copo, f.topoCopo, f.fundoCopo - traco, nivel)
        drawPath(f.copo, color = CorContorno, style = Stroke(width = traco * 0.9f, join = StrokeJoin.Round))
        drawPath(f.bocaFrenteCopo, color = CorContorno, style = Stroke(width = traco * 0.7f))
    }
}

// ---------------------------------------------------------------------------
// Agua
// ---------------------------------------------------------------------------

private val GradienteDaAgua = listOf(
    Color(0xFF6EC6F0),
    Color(0xFF2E8BD8),
    Color(0xFF12539E)
)

private fun DrawScope.desenharAguaParada(
    recipiente: Path,
    topoInterno: Float,
    fundoInterno: Float,
    nivel: Float
) {
    if (nivel <= 0.02f) return
    val linhaDagua = fundoInterno - (fundoInterno - topoInterno) * nivel
    clipPath(recipiente) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = GradienteDaAgua,
                startY = linhaDagua,
                endY = fundoInterno
            ),
            topLeft = Offset(0f, linhaDagua),
            size = androidx.compose.ui.geometry.Size(size.width, fundoInterno - linhaDagua)
        )
    }
}

/**
 * Bolhas em posicoes fixas: sorteio a cada quadro faria elas piscarem de lugar.
 * Cada trio e (fracao horizontal, atraso da subida, raio relativo).
 */
private val BOLHAS = listOf(
    Triple(0.30f, 0.00f, 0.030f),
    Triple(0.52f, 0.28f, 0.022f),
    Triple(0.70f, 0.55f, 0.026f),
    Triple(0.42f, 0.72f, 0.018f),
    Triple(0.62f, 0.12f, 0.016f)
)

private fun DrawScope.desenharAgua(
    recipiente: Path,
    topoInterno: Float,
    fundoInterno: Float,
    nivel: Float,
    faseOnda: Float,
    faseOnda2: Float,
    amplitude: Float,
    subidaBolhas: Float,
    centro: Float,
    largura: Float
) {
    if (nivel <= 0.005f) return

    val altura = (fundoInterno - topoInterno) * nivel
    val linhaDagua = fundoInterno - altura
    val w = size.width
    val h = size.height

    clipPath(recipiente) {
        val onda = Path().apply {
            moveTo(0f, h)
            lineTo(0f, linhaDagua)
            var x = 0f
            val passo = w / 48f
            while (x <= w) {
                val t = x / w
                val y = linhaDagua +
                    sin(t * 2.4f * Math.PI + faseOnda).toFloat() * amplitude +
                    sin(t * 1.3f * Math.PI + faseOnda2).toFloat() * amplitude * 0.45f
                lineTo(x, y)
                x += passo
            }
            lineTo(w, h)
            close()
        }

        drawPath(
            path = onda,
            brush = Brush.verticalGradient(
                colors = GradienteDaAgua,
                startY = linhaDagua,
                endY = fundoInterno
            )
        )

        // Faixa clara logo abaixo da superficie: da a impressao de profundidade.
        drawPath(
            path = onda,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x80FFFFFF), Color(0x00FFFFFF)),
                startY = linhaDagua - amplitude,
                endY = linhaDagua + amplitude * 3.5f
            )
        )

        if (altura > h * 0.06f) {
            BOLHAS.forEach { (fracaoX, atraso, raio) ->
                val avanco = (subidaBolhas + atraso) % 1f
                val y = fundoInterno - altura * avanco
                if (y > linhaDagua + amplitude) {
                    val desvio = sin((avanco * 6.0 * Math.PI).toDouble()).toFloat() * w * 0.010f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f * (1f - avanco)),
                        radius = w * raio * (0.6f + avanco * 0.5f),
                        center = Offset(centro - largura / 2f + largura * fracaoX + desvio, y)
                    )
                }
            }
        }
    }
}

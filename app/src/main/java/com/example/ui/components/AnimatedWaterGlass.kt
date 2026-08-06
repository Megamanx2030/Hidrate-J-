package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryBlue
import java.util.Locale
import kotlin.math.sin

@Composable
fun AnimatedWaterGlass(
    progress: Float, // 0.0 to 1.0+
    cupsDrunk: Int,
    totalCupsTarget: Int,
    todayTotalMl: Int = 0,
    dailyGoalMl: Int = 2000,
    modifier: Modifier = Modifier
) {
    val fillLevel = progress.coerceIn(0f, 1f)
    val percent = (progress * 100).toInt()

    val currentLitersStr = String.format(Locale("pt", "BR"), "%.1fL", todayTotalMl / 1000f)
    val goalLitersStr = String.format(Locale("pt", "BR"), "%.1fL", dailyGoalMl / 1000f)

    // Infinite transition for wave animation
    val transition = rememberInfiniteTransition(label = "water_wave")
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val bubbleY1 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubble1"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Wooden Sign and Arms are now drawn on the Canvas

        // Glass Cup Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Dimensions matching MiniWaterGlassIcon geometry proportionally
            val topW = width * 0.60f
            val botW = width * 0.45f
            val glassH = height * 0.75f

            val leftTop = (width - topW) / 2f + 30f // Shift right
            val rightTop = leftTop + topW
            val leftBot = (width - botW) / 2f + 30f
            val rightBot = leftBot + botW
            val topY = height * 0.15f
            val botY = topY + glassH

            // Stick
            val stickX = leftTop - 45f
            val stickTopY = topY + glassH * 0.1f
            drawRect(
                color = Color(0xFF5D4037),
                topLeft = Offset(stickX - 5f, stickTopY),
                size = Size(10f, glassH * 0.7f)
            )

            // Left Arm (holding stick)
            val leftArmPath = Path().apply {
                moveTo(leftTop + 10f, botY - glassH * 0.45f)
                quadraticTo(leftTop - 15f, botY - glassH * 0.30f, stickX + 5f, botY - glassH * 0.45f)
            }
            drawPath(leftArmPath, color = PrimaryBlue, style = Stroke(width = 12f, cap = StrokeCap.Round))
            drawCircle(color = PrimaryBlue, radius = 9f, center = Offset(stickX + 5f, botY - glassH * 0.45f)) // Hand

            // Right Arm (waving)
            val rightArmPath = Path().apply {
                moveTo(rightTop - 10f, botY - glassH * 0.45f)
                quadraticTo(rightTop + 35f, botY - glassH * 0.40f, rightTop + 45f, botY - glassH * 0.65f)
            }
            drawPath(rightArmPath, color = PrimaryBlue, style = Stroke(width = 12f, cap = StrokeCap.Round))
            drawCircle(color = PrimaryBlue, radius = 9f, center = Offset(rightTop + 45f, botY - glassH * 0.65f)) // Hand

            // Path defining the outer Glass Cup shape
            val glassPath = Path().apply {
                moveTo(leftTop, topY)
                lineTo(leftBot + 6f, botY - 4f)
                quadraticTo(leftBot + 6f, botY, leftBot + 12f, botY)
                lineTo(rightBot - 12f, botY)
                quadraticTo(rightBot - 6f, botY, rightBot - 6f, botY - 4f)
                lineTo(rightTop, topY)
                close()
            }

            // Glass inner fill path
            val glassInnerPath = Path().apply {
                moveTo(leftTop + 5f, topY + 3f)
                lineTo(leftBot + 8f, botY - 5f)
                quadraticTo(leftBot + 8f, botY - 3f, leftBot + 14f, botY - 3f)
                lineTo(rightBot - 14f, botY - 3f)
                quadraticTo(rightBot - 8f, botY - 3f, rightBot - 8f, botY - 5f)
                lineTo(rightTop - 5f, topY + 3f)
                close()
            }

            // 1. Draw subtle background inside empty glass
            drawPath(
                path = glassInnerPath,
                color = Color(0x1F004CB5)
            )

            // 2. Draw Animated Water Fill inside the glass
            clipPath(glassInnerPath) {
                if (fillLevel > 0.01f) {
                    val waterHeight = (botY - topY - 8f) * fillLevel
                    val currentWaterY = botY - 3f - waterHeight

                    val wavePath = Path().apply {
                        moveTo(0f, height)
                        lineTo(0f, currentWaterY)

                        val waveAmplitude = 8f * (1f - (fillLevel - 0.5f) * (fillLevel - 0.5f) * 2f).coerceIn(0.2f, 1f)
                        var x = 0f
                        while (x <= width) {
                            val y = currentWaterY + sin((x / width * 2 * Math.PI + wavePhase).toDouble()).toFloat() * waveAmplitude
                            lineTo(x, y)
                            x += 8f
                        }

                        lineTo(width, height)
                        close()
                    }

                    drawPath(
                        path = wavePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF42A5F5), // Bright aqua blue
                                PrimaryBlue,       // Deep blue
                                Color(0xFF003080)
                            ),
                            startY = currentWaterY,
                            endY = botY
                        )
                    )

                    // Floating bubbles
                    if (fillLevel > 0.15f) {
                        val bubble1Y = currentWaterY + (botY - currentWaterY) * bubbleY1
                        val bubble2Y = currentWaterY + (botY - currentWaterY) * ((bubbleY1 + 0.5f) % 1f)

                        drawCircle(
                            color = Color(0x66FFFFFF),
                            radius = 6f,
                            center = Offset(width * 0.42f + 30f, bubble1Y)
                        )
                        drawCircle(
                            color = Color(0x66FFFFFF),
                            radius = 4f,
                            center = Offset(width * 0.58f + 30f, bubble2Y)
                        )
                    }
                }
            }

            // 3. Glass Outline & Top Oval Rim
            drawPath(
                path = glassPath,
                color = PrimaryBlue,
                style = Stroke(width = 8f)
            )

            drawOval(
                color = PrimaryBlue,
                topLeft = Offset(leftTop - 4f, topY - 8f),
                size = Size(topW + 8f, 16f),
                style = Stroke(width = 6.5f)
            )

            // Shine Highlight Line
            drawLine(
                color = Color(0x66FFFFFF),
                start = Offset(leftTop + 14f, topY + 16f),
                end = Offset(leftBot + 14f, botY - 20f),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
            
            // Dynamic Smiley Face
            val faceY = botY - glassH * 0.40f
            val isFaceUnderwater = if (fillLevel > 0.01f) {
                val waterHeight = (botY - topY - 8f) * fillLevel
                val currentWaterY = botY - 3f - waterHeight
                currentWaterY < faceY
            } else false
            
            val faceColor = if (isFaceUnderwater) Color.White else Color(0xFF001533)
            val eyeRadius = 7.5f + (fillLevel * 1.5f) // Eyes get slightly larger
            
            // Left eye
            drawCircle(color = faceColor, radius = eyeRadius, center = Offset(width * 0.40f + 30f, faceY))
            // Right eye
            drawCircle(color = faceColor, radius = eyeRadius, center = Offset(width * 0.60f + 30f, faceY))
            
            // Smile gets wider and happier
            val smileControlY = botY - glassH * (0.16f - (fillLevel * 0.05f))
            val smilePath = Path().apply {
                moveTo(width * 0.36f + 30f, botY - glassH * 0.28f)
                quadraticTo(width * 0.50f + 30f, smileControlY, width * 0.64f + 30f, botY - glassH * 0.28f)
            }
            drawPath(smilePath, color = faceColor, style = Stroke(width = 7f + (fillLevel * 2f), cap = StrokeCap.Round))
            
            // Sign Board
            val boardW = 95f
            val boardH = 55f
            val boardLeft = stickX - boardW / 2f
            val boardTop = stickTopY - 10f
            
            drawRoundRect(
                color = Color(0xFF8D6E63),
                topLeft = Offset(boardLeft, boardTop),
                size = Size(boardW, boardH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFF5D4037),
                topLeft = Offset(boardLeft, boardTop),
                size = Size(boardW, boardH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                style = Stroke(width = 4f)
            )
            
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 20f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText("HORA DE", stickX, boardTop + boardH * 0.45f, paint)
            drawContext.canvas.nativeCanvas.drawText("BEBER!", stickX, boardTop + boardH * 0.85f, paint)
        }
    }
}


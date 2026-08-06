package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PrimaryBlue

@Composable
fun MiniWaterGlassIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    fillRatio: Float = 0.85f
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val topW = w * 0.72f
        val botW = w * 0.52f
        val glassH = h * 0.78f

        val leftTop = (w - topW) / 2f
        val rightTop = leftTop + topW
        val leftBot = (w - botW) / 2f
        val rightBot = leftBot + botW
        val topY = h * 0.12f
        val botY = topY + glassH

        val glassInnerPath = Path().apply {
            moveTo(leftTop + 3f, topY + 2f)
            lineTo(leftBot + 4f, botY - 3f)
            quadraticTo(leftBot + 4f, botY - 2f, leftBot + 8f, botY - 2f)
            lineTo(rightBot - 8f, botY - 2f)
            quadraticTo(rightBot - 4f, botY - 2f, rightBot - 4f, botY - 3f)
            lineTo(rightTop - 3f, topY + 2f)
            close()
        }

        // Draw empty background
        drawPath(
            path = glassInnerPath,
            color = Color(0x1F004CB5)
        )

        // Draw liquid fill
        clipPath(glassInnerPath) {
            val waterH = (botY - topY) * fillRatio.coerceIn(0.1f, 1.0f)
            val waterY = botY - waterH

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4285F4),
                        PrimaryBlue
                    ),
                    startY = waterY,
                    endY = botY
                ),
                topLeft = Offset(0f, waterY),
                size = androidx.compose.ui.geometry.Size(w, botY - waterY)
            )
        }

        // Outer rim and outline
        val glassOutline = Path().apply {
            moveTo(leftTop, topY)
            lineTo(leftBot + 4f, botY)
            lineTo(rightBot - 4f, botY)
            lineTo(rightTop, topY)
        }
        drawPath(
            path = glassOutline,
            color = PrimaryBlue,
            style = Stroke(width = 3.5f)
        )

        // Oval rim
        drawOval(
            color = PrimaryBlue,
            topLeft = Offset(leftTop - 2f, topY - 4f),
            size = androidx.compose.ui.geometry.Size(topW + 4f, 8f),
            style = Stroke(width = 3f)
        )

        // Shine line
        drawLine(
            color = Color(0x66FFFFFF),
            start = Offset(leftTop + 6f, topY + 10f),
            end = Offset(leftBot + 8f, botY - 10f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // Smiley Face
        val faceColor = PrimaryBlue
        val eyeRadius = w * 0.04f
        // Left eye
        drawCircle(color = faceColor, radius = eyeRadius, center = Offset(w * 0.40f, botY - glassH * 0.35f))
        // Right eye
        drawCircle(color = faceColor, radius = eyeRadius, center = Offset(w * 0.60f, botY - glassH * 0.35f))
        
        // Smile
        val smilePath = Path().apply {
            moveTo(w * 0.38f, botY - glassH * 0.25f)
            quadraticTo(w * 0.50f, botY - glassH * 0.15f, w * 0.62f, botY - glassH * 0.25f)
        }
        drawPath(smilePath, color = faceColor, style = Stroke(width = w * 0.05f, cap = StrokeCap.Round))
    }
}

package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryBlue
import kotlin.math.sin

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun AnimatedWaterDropChart(
    days: List<String>,
    fillRatios: List<Float>,
    dates: List<String>,
    totalsMl: List<Int>,
    dailyGoalMl: Int,
    todayDate: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "glass_wave")
    var selectedIndex by remember(dates, todayDate) { mutableStateOf<Int?>(dates.indexOf(todayDate).takeIf { it >= 0 }) }

    Column(modifier = modifier.fillMaxWidth()) {
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // ROLAGEM HORIZONTAL COM LARGURA FIXA POR COLUNA.
    //
    // Antes cada coluna usava weight(1f), o que so funcionava com exatamente 7
    // dias: o copo tem 44dp fixos, entao com um periodo maior as colunas
    // encolhiam e o desenho estourava. Com largura fixa e rolagem, o grafico
    // aguenta de 1 a 31 dias sem deformar.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEachIndexed { index, dayLabel ->
            val fill = fillRatios.getOrElse(index) { 0f }.coerceIn(0f, 1f)
            val isToday = dates.getOrNull(index) == todayDate
            val isSelected = selectedIndex == index

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(50.dp)
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable {
                        if (selectedIndex == index) selectedIndex = null else selectedIndex = index
                    }
            ) {
                // Percentage badge above glass
                Text(
                    text = "${(fill * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = if (fill > 0.8f) com.example.ui.theme.CyanAction else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(4.dp))

                val glassModifier = if (isSelected) {
                    Modifier
                        .border(2.dp, PrimaryBlue, RoundedCornerShape(6.dp))
                        .padding(2.dp)
                } else {
                    Modifier
                }

                Box(modifier = glassModifier) {
                    com.example.ui.components.MiniJarraECopo(
                        size = 44.dp,
                        fillRatio = fill
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                    color = if (isToday) PrimaryBlue else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (selectedIndex != null) {
        val idx = selectedIndex!!
        val selectedDateStr = dates.getOrNull(idx)
        val selectedTotal = totalsMl.getOrNull(idx) ?: 0
        if (selectedDateStr != null) {
            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(selectedDateStr)
            val fullDateStr = parsed?.let {
                java.text.SimpleDateFormat("EEEE, dd/MM", java.util.Locale("pt", "BR")).format(it)
                    .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(java.util.Locale("pt", "BR")) else char.toString() }
            } ?: selectedDateStr
            
            val ratio = if (dailyGoalMl > 0) (selectedTotal.toFloat() / dailyGoalMl.toFloat()).coerceIn(0f, 1f) else 0f
            val ratioPercent = (ratio * 100).toInt()
            val totalL = String.format(java.util.Locale("pt", "BR"), "%.1fL", selectedTotal / 1000f)
            val goalL = String.format(java.util.Locale("pt", "BR"), "%.1fL", dailyGoalMl / 1000f)
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$fullDateStr — $totalL de $goalL ($ratioPercent%)",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
    }
}

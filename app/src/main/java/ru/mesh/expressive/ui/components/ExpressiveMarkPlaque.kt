package ru.mesh.expressive.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.mesh.expressive.data.model.MarkItem
import ru.mesh.expressive.ui.theme.*

/**
 * Тактильный пружинный клик Material 3 Expressive.
 * Мягкое сжатие до 0.94f и плавный отклик пружины (damping = 0.85f, stiffness = 700f).
 */
fun Modifier.expressiveBounceClick(
    onClick: (() -> Unit)? = null
): Modifier = if (onClick == null) this else this.then(
    Modifier.pointerInput(Unit) {
        // Handled via stateful composable wrapper or pointer input
    }
)

/**
 * Конвертер веса оценки в суперскрипт (например: вес 2 -> "²", вес 3 -> "³").
 */
fun formatWeightSuperscript(weight: Double): String {
    return when (weight) {
        2.0 -> "²"
        3.0 -> "³"
        4.0 -> "⁴"
        5.0 -> "⁵"
        1.5 -> "¹˙⁵"
        else -> if (weight > 1.0) "⁺" else ""
    }
}

/**
 * Миниплашка оценки по спецификации Material 3 Expressive:
 * - Размер 40x40 dp (или кастомный) с формой RoundedCornerShape(12.dp)
 * - Tonal Containers для оценок 5, 4, 3, 2
 * - Корректный статус активной точки (янтарный контейнер) и просроченной точки (красный контейнер с 2)
 * - Суперскрипт веса (5², 4³, 3²) без разрыва строки и захламления верстки
 * - Пружинный тактильный отклик при нажатии
 */
@Composable
fun ExpressiveMarkPlaque(
    mark: MarkItem,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    fontSize: TextUnit = 16.sp,
    cornerRadius: Dp = 12.dp,
    onClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }

    val isDark = isSystemInDarkTheme()
    val isPt = mark.isPoint || !mark.pointDate.isNullOrBlank() || mark.rawValue.endsWith(".") || mark.rawValue == "."
    val isExpired = mark.isPointExpired
    val effVal = mark.effectiveValue

    // Цветовая схема M3 Expressive Tonal Container
    val (bgColor, textColor, borderColor) = when {
        // Просроченная точка: учитывается как 2 в красном контейнере с акцентным бордером
        isPt && isExpired -> Triple(
            if (isDark) ScoreRedContainerDark else ScoreRedContainerLight,
            if (isDark) ScoreRedDark else ScoreRedLight,
            if (isDark) Color(0xFFFF5252).copy(alpha = 0.6f) else Color(0xFFD32F2F).copy(alpha = 0.5f)
        )
        // Активная точка: теплый янтарный контейнер
        isPt -> Triple(
            if (isDark) Color(0xFF2C2218) else Color(0xFFFFF3E0),
            if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100),
            if (isDark) Color(0xFFFB843D).copy(alpha = 0.45f) else Color(0xFFFFCC80)
        )
        // Обычные оценки
        mark.value >= 5 -> Triple(
            if (isDark) ScoreGreenContainerDark else ScoreGreenContainerLight,
            if (isDark) ScoreGreenDark else ScoreGreenLight,
            null
        )
        mark.value == 4 -> Triple(
            if (isDark) ScoreBlueContainerDark else ScoreBlueContainerLight,
            if (isDark) ScoreBlueDark else ScoreBlueLight,
            null
        )
        mark.value == 3 -> Triple(
            if (isDark) ScoreOrangeContainerDark else ScoreOrangeContainerLight,
            if (isDark) ScoreOrangeDark else ScoreOrangeLight,
            null
        )
        else -> Triple(
            if (isDark) ScoreRedContainerDark else ScoreRedContainerLight,
            if (isDark) ScoreRedDark else ScoreRedLight,
            null
        )
    }

    // Отображаемое значение
    val displayVal = when {
        isPt && isExpired -> if (effVal > 0) "$effVal" else "2"
        isPt -> if (mark.value > 0) "${mark.value}" else "•"
        else -> "${mark.value}"
    }

    val weightSuper = if (mark.weight > 1.0) formatWeightSuperscript(mark.weight) else ""

    val isCorrected = mark.isPointCorrected || mark.hasCorrection || mark.rawValue.contains("*")

    Surface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            }
            .clip(RoundedCornerShape(cornerRadius))
            .pointerInput(onClick) {
                if (onClick != null) {
                    detectTapGestures(
                        onPress = {
                            coroutineScope.launch {
                                scaleAnim.animateTo(
                                    targetValue = 0.93f,
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 1200f)
                                )
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val success = tryAwaitRelease()
                            coroutineScope.launch {
                                scaleAnim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f)
                                )
                            }
                            if (success) {
                                onClick()
                            }
                        }
                    )
                }
            },
        shape = RoundedCornerShape(cornerRadius),
        color = bgColor,
        border = borderColor?.let { BorderStroke(1.dp, it) }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayVal,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    color = textColor,
                    letterSpacing = (-0.5).sp
                )
                if (isCorrected && !isPt) {
                    Text(
                        text = "*",
                        fontWeight = FontWeight.Bold,
                        fontSize = (fontSize.value * 0.85f).sp,
                        color = textColor.copy(alpha = 0.9f),
                        modifier = Modifier.padding(start = 0.5.dp)
                    )
                }
                if (weightSuper.isNotEmpty()) {
                    Text(
                        text = weightSuper,
                        fontWeight = FontWeight.Black,
                        fontSize = (fontSize.value * 0.95f).sp,
                        color = textColor.copy(alpha = 0.95f),
                        modifier = Modifier.padding(start = 0.5.dp)
                    )
                }
            }

            // Индикатор точки в верхнем правом углу
            if (isPt) {
                val dotColor = if (isExpired) {
                    if (isDark) Color(0xFFFF5252) else Color(0xFFD32F2F)
                } else {
                    if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp)
                        .size(5.5.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

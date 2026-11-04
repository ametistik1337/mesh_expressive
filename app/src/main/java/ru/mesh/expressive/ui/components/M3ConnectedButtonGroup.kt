package ru.mesh.expressive.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 Connected Button Group (https://m3.material.io/components/button-groups/overview)
 * Официальная замена Segmented Buttons в M3 Expressive:
 * - Зазор 2dp между кнопками
 * - Контейнерные скругления (16dp на внешних краях, 4dp на внутренних)
 * - Анимированная иконка выбора Checkmark
 * - Тональные контейнеры M3 (secondaryContainer / onSecondaryContainer)
 * - Пружинная анимация смены состояний
 */
@Composable
fun <T> M3ConnectedButtonGroup(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String,
    icon: ((T) -> ImageVector)? = null,
    showCheckmark: Boolean = true,
    showIndicatorDot: ((T) -> Boolean)? = null,
    scrollable: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    val rowModifier = if (scrollable) {
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    } else {
        Modifier.fillMaxWidth()
    }

    Row(
        modifier = modifier.then(rowModifier),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = item == selectedItem
            val isFirst = index == 0
            val isLast = index == items.size - 1

            val shape: Shape = when {
                items.size == 1 -> RoundedCornerShape(percent = 50)
                isFirst -> RoundedCornerShape(
                    topStart = CornerSize(50),
                    bottomStart = CornerSize(50),
                    topEnd = CornerSize(8.dp),
                    bottomEnd = CornerSize(8.dp)
                )
                isLast -> RoundedCornerShape(
                    topStart = CornerSize(8.dp),
                    bottomStart = CornerSize(8.dp),
                    topEnd = CornerSize(50),
                    bottomEnd = CornerSize(50)
                )
                else -> RoundedCornerShape(8.dp)
            }

            val isDark = isSystemInDarkTheme()

            val containerColor by animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> if (isDark) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f),
                label = "m3_btn_bg"
            )

            val contentColor by animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f),
                label = "m3_btn_fg"
            )

            Surface(
                modifier = Modifier
                    .height(40.dp)
                    .clip(shape)
                    .clickable {
                        if (!isSelected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onItemSelected(item)
                        }
                    },
                shape = shape,
                color = containerColor,
                border = null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Checkmark indicator as defined in M3 Connected Button Groups specs
                    if (showCheckmark) {
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Row {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                        }
                    }

                    if (icon != null && (!showCheckmark || !isSelected)) {
                        val itemIcon = icon(item)
                        Icon(
                            imageVector = itemIcon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (showIndicatorDot != null && showIndicatorDot(item)) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) contentColor else MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = label(item),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

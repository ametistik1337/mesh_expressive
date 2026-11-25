package com.droid.dolphy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val PillShape = RoundedCornerShape(percent = 50)

data class DockNavItem(
    val id: Int,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
    val label: String
)

@Composable
fun ExpressiveFloatingToolbar(
    modifier: Modifier? = null,
    accentColor: Color,
    isBluetooth: Boolean,
    isOther: Boolean,
    isSettings: Boolean,
    onBluetoothClick: () -> Unit,
    onOtherClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val tabs = remember {
        listOf(
            DockNavItem(0, Icons.Filled.Bluetooth, Icons.Outlined.Bluetooth, "Bluetooth"),
            DockNavItem(1, Icons.Filled.Extension, Icons.Outlined.Extension, "Прочее"),
            DockNavItem(2, Icons.Filled.Settings, Icons.Outlined.Settings, "Настройки")
        )
    }

    val activeIndex = when {
        isBluetooth -> 0
        isOther -> 1
        isSettings -> 2
        else -> 0
    }

    val onTabSelected: (Int) -> Unit = { idx ->
        when (idx) {
            0 -> onBluetoothClick()
            1 -> onOtherClick()
            2 -> onSettingsClick()
        }
    }

    val currentTabState by rememberUpdatedState(activeIndex)
    val onTabSelectedState by rememberUpdatedState(onTabSelected)

    val coroutineScope = rememberCoroutineScope()

    val dropletWidthPx = density.run { 68.dp.toPx() }
    val dropletHeightPx = density.run { 42.dp.toPx() }
    val padOverPx = density.run { 20.dp.toPx() }

    val tab0Width = density.run { 120.dp.toPx() }
    val tab1Width = density.run { 108.dp.toPx() }
    val tab2Width = density.run { 118.dp.toPx() }

    val animDropletLeft = remember { Animatable(0f) }
    val settleProgress = remember { Animatable(1f) }

    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    val isDropletActive = isDragging || isSettling

    var releaseLeft by remember { mutableFloatStateOf(0f) }
    var releaseWidth by remember { mutableFloatStateOf(0f) }

    var fingerX by remember { mutableFloatStateOf(0f) }
    var lastDeltaX by remember { mutableFloatStateOf(0f) }
    var dockWidth by remember { mutableFloatStateOf(0f) }

    val targetLeft = density.run { (activeIndex * 50).dp.toPx() }
    val targetWidth = when (activeIndex) {
        0 -> tab0Width
        1 -> tab1Width
        2 -> tab2Width
        else -> dropletWidthPx
    }

    val curDropletLeft = if (isDragging) {
        animDropletLeft.value
    } else {
        releaseLeft + (targetLeft - releaseLeft) * settleProgress.value
    }

    val curDropletWidth = if (isDragging) {
        dropletWidthPx
    } else {
        releaseWidth + (targetWidth - releaseWidth) * settleProgress.value
    }

    val curScale = if (isDragging) {
        1.12f
    } else {
        1.12f - (0.12f * settleProgress.value)
    }

    val curElevation = if (isDragging) {
        density.run { 8.dp.toPx() }
    } else {
        density.run { 8.dp.toPx() } * (1f - settleProgress.value)
    }

    // Liquid squish & stretch physics (active only during active drag)
    val stretchX = if (isDragging) {
        1f + (abs(lastDeltaX.coerceIn(-20f, 20f)) / 90f)
    } else 1.0f
    val stretchY = 1f / sqrt(stretchX)

    val mod = modifier ?: Modifier

    Box(
        modifier = mod
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            Box(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        dockWidth = coords.size.width.toFloat()
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val curTab = currentTabState
                                val activeLeft = density.run { (curTab * 50).dp.toPx() }
                                val activeW = when (curTab) {
                                    0 -> tab0Width
                                    1 -> tab1Width
                                    2 -> tab2Width
                                    else -> dropletWidthPx
                                }

                                val isOverActive = down.position.x >= activeLeft - padOverPx &&
                                    down.position.x <= activeLeft + activeW + padOverPx

                                if (!isOverActive) {
                                    continue
                                }

                                val touchStartX = down.position.x
                                fingerX = touchStartX
                                lastDeltaX = 0f

                                var dragActive = false

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                    if (!change.pressed) {
                                        break
                                    }

                                    val dx = change.position.x - touchStartX
                                    val uptime = change.uptimeMillis - down.uptimeMillis

                                    // Hold threshold for liquid drag: strictly on active tab, requires genuine drag or hold
                                    if (!dragActive && (uptime > 200L || abs(dx) > 14f)) {
                                        dragActive = true
                                        isDragging = true
                                        isSettling = false
                                        fingerX = change.position.x
                                        val curDropX = (fingerX - dropletWidthPx / 2f).coerceIn(4f, (dockWidth - dropletWidthPx - 4f).coerceAtLeast(4f))
                                        coroutineScope.launch {
                                            animDropletLeft.snapTo(curDropX)
                                            settleProgress.snapTo(0f)
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }

                                    if (dragActive) {
                                        change.consume()
                                        lastDeltaX = change.position.x - fingerX
                                        fingerX = change.position.x
                                        val curDropX = (fingerX - dropletWidthPx / 2f).coerceIn(4f, (dockWidth - dropletWidthPx - 4f).coerceAtLeast(4f))

                                        coroutineScope.launch {
                                            animDropletLeft.snapTo(curDropX)
                                        }

                                        // Use static slot boundaries to prevent hysteresis vibration
                                        if (dockWidth > 0f) {
                                            val slotWidth = dockWidth / tabs.size.toFloat()
                                            val slotIndex = (fingerX / slotWidth).toInt().coerceIn(0, tabs.size - 1)

                                            if (slotIndex != currentTabState) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onTabSelectedState(slotIndex)
                                            }
                                        }
                                    }
                                }

                                if (dragActive) {
                                    releaseLeft = animDropletLeft.value
                                    releaseWidth = dropletWidthPx
                                    isDragging = false
                                    isSettling = true
                                    lastDeltaX = 0f

                                    coroutineScope.launch {
                                        settleProgress.snapTo(0f)
                                        settleProgress.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(
                                                dampingRatio = 0.76f,
                                                stiffness = 320f
                                            )
                                        )
                                        isSettling = false
                                    }
                                }
                            }
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                // 1. Fluid Droplet Selection Card — rendered during drag or spring settle
                if (isDropletActive) {
                    val wDp = density.run { curDropletWidth.toDp() }
                    val hDp = density.run { dropletHeightPx.toDp() }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(curDropletLeft.roundToInt(), 0) }
                            .size(width = wDp, height = hDp)
                            .graphicsLayer {
                                this.scaleX = curScale * stretchX
                                this.scaleY = curScale * stretchY
                                this.shadowElevation = curElevation
                                this.shape = PillShape
                            }
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                }

                // 2. Tab Items Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val isSelected = activeIndex == tab.id

                        val animBgColor by animateColorAsState(
                            targetValue = if (isSelected && !isDragging) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
                            label = "dockItemBg"
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                            label = "dockItemColor"
                        )

                        Surface(
                            shape = PillShape,
                            color = animBgColor,
                            modifier = Modifier
                                .clip(PillShape)
                                .clickable { onTabSelected(tab.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = if (isSelected) 14.dp else 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.iconSelected else tab.iconUnselected,
                                    contentDescription = tab.label,
                                    tint = contentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    Row {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

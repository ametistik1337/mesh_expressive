package ru.mesh.expressive.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Строит единый «жидкий» контур: верхняя шапочка вокруг кнопки + плавная вогнутая шейка +
 * вытекающий нижний пузырь.
 */
private fun buildLiquidDropletPath(
    totalWidth: Float,
    totalHeight: Float,
    capSize: Float,
    neckHeight: Float,
    bubbleWidth: Float,
    bubbleHeight: Float,
    anchorCenterX: Float,
    progress: Float,
    expandUpwards: Boolean = false
): Path {
    val p = progress.coerceIn(0f, 1.15f)
    val path = Path()
    if (p <= 0.01f) return path

    val capRadius = capSize * 0.48f
    val capLeft = (anchorCenterX - capSize / 2f).coerceIn(0f, max(0f, totalWidth - capSize))
    val capRight = capLeft + capSize
    val capCenterX = (capLeft + capRight) / 2f

    // Единая непрерывная кривая (без пороговых переключений) для идеально плавной анимации
    val ease = p * p * (3f - 2f * p)

    val targetBubbleW = max(capSize, bubbleWidth)
    val targetBubbleLeft = ((totalWidth - targetBubbleW) / 2f).coerceIn(0f, max(0f, totalWidth - targetBubbleW))
    val targetBubbleRight = targetBubbleLeft + targetBubbleW

    val bLeft = lerpFloat(capLeft, targetBubbleLeft, ease)
    val bRight = lerpFloat(capRight, targetBubbleRight, ease)

    val bTop = lerpFloat(capSize * 0.5f, capSize + neckHeight, ease)
    val bBottom = lerpFloat(capSize, capSize + neckHeight + bubbleHeight, ease)

    val maxBubbleR = lerpFloat(capRadius, capSize * 0.44f, ease)
    val bRadius = min((bRight - bLeft) * 0.49f, min((bBottom - bTop) * 0.49f, maxBubbleR))

    // Верхняя шапочка (по часовой стрелке от левого экватора)
    val neckJoinY = lerpFloat(capSize * 0.5f, capSize * 0.72f, ease)
    path.moveTo(capLeft, neckJoinY)
    path.lineTo(capLeft, capRadius)
    path.cubicTo(
        capLeft, capRadius * 0.4f,
        capLeft + capRadius * 0.4f, 0f,
        capLeft + capRadius, 0f
    )
    path.lineTo(capRight - capRadius, 0f)
    path.cubicTo(
        capRight - capRadius * 0.4f, 0f,
        capRight, capRadius * 0.4f,
        capRight, capRadius
    )
    path.lineTo(capRight, neckJoinY)

    // Единый плавный правый переход от стенки шапочки к правому боку пузыря (без захода внутрь)
    val rOut = (bRight - capRight).coerceAtLeast(0f)
    val rFilletT = smoothStep(0f, bRadius * 1.6f + 1f, rOut)
    val rightJoinX = lerpFloat(capRight, bRight - bRadius, rFilletT)
    val rightCornerTopY = lerpFloat(neckJoinY, bTop, rFilletT)
    if (rFilletT > 0.01f) {
        path.cubicTo(
            capRight, lerpFloat(neckJoinY, rightCornerTopY, 0.85f),
            lerpFloat(capRight, rightJoinX, 0.42f), rightCornerTopY,
            rightJoinX, rightCornerTopY
        )
        path.cubicTo(
            lerpFloat(rightJoinX, bRight, 0.55f), rightCornerTopY,
            bRight, lerpFloat(rightCornerTopY, bTop + bRadius, 0.45f),
            bRight, bTop + bRadius
        )
    }
    path.lineTo(bRight, bBottom - bRadius)
    path.cubicTo(
        bRight, bBottom - bRadius * 0.45f,
        bRight - bRadius * 0.45f, bBottom,
        bRight - bRadius, bBottom
    )

    // Нижняя грань пузыря
    path.lineTo(bLeft + bRadius, bBottom)

    // Единый плавный левый переход обратно к левой стенке шапочки (без захода внутрь)
    val lOut = (capLeft - bLeft).coerceAtLeast(0f)
    val lFilletT = smoothStep(0f, bRadius * 1.6f + 1f, lOut)
    val leftJoinX = lerpFloat(capLeft, bLeft + bRadius, lFilletT)
    val leftCornerTopY = lerpFloat(neckJoinY, bTop, lFilletT)
    path.cubicTo(
        bLeft + bRadius * 0.45f, bBottom,
        bLeft, bBottom - bRadius * 0.45f,
        bLeft, bBottom - bRadius
    )
    if (lFilletT > 0.01f) {
        path.lineTo(bLeft, bTop + bRadius)
        path.cubicTo(
            bLeft, lerpFloat(leftCornerTopY, bTop + bRadius, 0.45f),
            lerpFloat(leftJoinX, bLeft, 0.55f), leftCornerTopY,
            leftJoinX, leftCornerTopY
        )
        path.cubicTo(
            lerpFloat(capLeft, leftJoinX, 0.42f), leftCornerTopY,
            capLeft, lerpFloat(neckJoinY, leftCornerTopY, 0.85f),
            capLeft, neckJoinY
        )
    } else {
        path.lineTo(capLeft, neckJoinY)
    }

    path.close()
    if (expandUpwards) {
        val m = Matrix()
        m.scale(1f, -1f)
        m.translate(0f, -totalHeight)
        path.transform(m)
    }
    return path
}

/**
 * Кнопка-иконка с «жидкой» вытекающей капелькой объяснения при долгом нажатии.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    tooltipText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    var isDropletOpen by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }
    var expandUpwards by remember { mutableStateOf(false) }
    var anchorCenterInPopupX by remember { mutableIntStateOf(-1) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val screenHeightPx = LocalConfiguration.current.screenHeightDp * density.density

    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(isDropletOpen, targetExpanded) {
        if (!isDropletOpen) return@LaunchedEffect
        if (targetExpanded) {
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.66f,
                    stiffness = 380f
                )
            )
            delay(2100L)
            targetExpanded = false
        } else {
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            isDropletOpen = false
            anchorCenterInPopupX = -1
        }
    }

    val baseContentColor = if (contentColor != Color.Unspecified) contentColor else LocalContentColor.current
    val resolvedContentColor = if (enabled) {
        baseContentColor
    } else {
        baseContentColor.copy(alpha = 0.38f)
    }

    val capSizeDp = 48.dp
    val neckHeightDp = 8.dp
    val edgeMarginPx = with(density) { 4.dp.roundToPx() }
    val capSizePx = with(density) { capSizeDp.toPx() }
    val neckHeightPx = with(density) { neckHeightDp.toPx() }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                expandUpwards = bounds.center.y > screenHeightPx * 0.55f
            }
            .clip(CircleShape)
            .then(if (containerColor != Color.Unspecified) Modifier.background(containerColor, CircleShape) else Modifier)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    targetExpanded = false
                    onClick()
                },
                onLongClick = {
                    if (tooltipText.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isDropletOpen = true
                        targetExpanded = true
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
            content()
        }

        if (isDropletOpen && tooltipText.isNotBlank()) {
            val positionProvider = remember(edgeMarginPx, capSizePx) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        val idealX = anchorBounds.center.x - popupContentSize.width / 2
                        val maxX = max(edgeMarginPx, windowSize.width - popupContentSize.width - edgeMarginPx)
                        val clampedX = idealX.coerceIn(edgeMarginPx, maxX)
                        anchorCenterInPopupX = anchorBounds.center.x - clampedX

                        val spaceBelow = windowSize.height - anchorBounds.bottom
                        val minBottomClearancePx = (48f * density.density).roundToInt()
                        val shouldGoUp = expandUpwards ||
                            ((spaceBelow < popupContentSize.height + minBottomClearancePx ||
                                anchorBounds.center.y > (windowSize.height * 0.55f).roundToInt()) &&
                                anchorBounds.top > popupContentSize.height)
                        expandUpwards = shouldGoUp

                        val popupY = if (shouldGoUp) {
                            (anchorBounds.center.y + (capSizePx / 2f).roundToInt() - popupContentSize.height)
                                .coerceAtLeast(edgeMarginPx)
                        } else {
                            (anchorBounds.center.y - (capSizePx / 2f).roundToInt())
                                .coerceAtLeast(0)
                        }
                        return IntOffset(clampedX, popupY)
                    }
                }
            }

            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { targetExpanded = false },
                properties = PopupProperties(focusable = false, clippingEnabled = false)
            ) {
                var popupWidthPx by remember { mutableIntStateOf(0) }
                val dropletColor = MaterialTheme.colorScheme.primary
                val dropletContentColor = MaterialTheme.colorScheme.onPrimary
                val p = animProgress.value
                val colorFade = smoothStep(0f, 0.22f, p)
                val animatedDropletColor = dropletColor.copy(alpha = colorFade)
                val animatedCapContentColor = androidx.compose.ui.graphics.lerp(
                    resolvedContentColor,
                    dropletContentColor,
                    colorFade
                )
                val textAlpha = smoothStep(0.60f, 1f, p)
                val textScale = lerpFloat(0.78f, 1f, smoothStep(0.60f, 1f, p))

                Box(
                    modifier = Modifier
                        .widthIn(min = capSizeDp)
                        .onSizeChanged { popupWidthPx = it.width }
                ) {
                    val readyToDraw = popupWidthPx > 0 && anchorCenterInPopupX >= 0
                    val totalW = if (popupWidthPx > 0) popupWidthPx.toFloat() else capSizePx
                    val centerX = if (anchorCenterInPopupX >= 0) anchorCenterInPopupX.toFloat() else totalW / 2f

                    Canvas(modifier = Modifier.matchParentSize()) {
                        if (!readyToDraw) return@Canvas
                        val bHeight = (size.height - capSizePx - neckHeightPx).coerceAtLeast(0f)
                        val path = buildLiquidDropletPath(
                            totalWidth = size.width,
                            totalHeight = size.height,
                            capSize = capSizePx,
                            neckHeight = neckHeightPx,
                            bubbleWidth = size.width,
                            bubbleHeight = bHeight,
                            anchorCenterX = centerX,
                            progress = p,
                            expandUpwards = expandUpwards
                        )
                        drawPath(path = path, color = animatedDropletColor)
                    }

                    // Текст объяснения в пузыре (сам задает высоту и ширину Popup)
                    Box(
                        modifier = Modifier
                            .align(if (expandUpwards) Alignment.BottomCenter else Alignment.TopCenter)
                            .padding(
                                top = if (expandUpwards) 0.dp else capSizeDp + neckHeightDp,
                                bottom = if (expandUpwards) capSizeDp + neckHeightDp else 0.dp
                            )
                            .widthIn(min = capSizeDp, max = 230.dp)
                            .graphicsLayer {
                                alpha = if (readyToDraw) textAlpha else 0f
                                scaleX = textScale
                                scaleY = textScale
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tooltipText,
                            color = dropletContentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Кнопка-шапочка (сверху или снизу в зависимости от направления)
                    val capLeftDp = with(density) {
                        (centerX - capSizePx / 2f).coerceIn(0f, max(0f, totalW - capSizePx)).toDp()
                    }
                    Box(
                        modifier = Modifier
                            .align(if (expandUpwards) Alignment.BottomStart else Alignment.TopStart)
                            .offset(x = capLeftDp)
                            .size(capSizeDp)
                            .graphicsLayer { alpha = if (readyToDraw) 1f else 0f },
                        contentAlignment = Alignment.Center
                    ) {
                        CompositionLocalProvider(LocalContentColor provides animatedCapContentColor) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidDropletStageSample(
    progress: Float,
    label: String,
    tooltipText: String = "Импорт в JSON",
    expandUpwards: Boolean = false,
    dropletColor: Color = MaterialTheme.colorScheme.primary,
    dropletContentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val density = LocalDensity.current
    val capSizeDp = 42.dp
    val neckHeightDp = 7.dp
    val bubbleW = 104.dp
    val bubbleH = 32.dp
    val capSizePx = with(density) { capSizeDp.toPx() }
    val neckHeightPx = with(density) { neckHeightDp.toPx() }
    val bubbleWPx = with(density) { bubbleW.toPx() }
    val bubbleHPx = with(density) { bubbleH.toPx() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier.size(width = bubbleW, height = capSizeDp + neckHeightDp + bubbleH),
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                if (progress <= 0.01f) {
                    // Базовая кнопка без капли
                    val topY = if (expandUpwards) size.height - capSizePx else 0f
                    drawRoundRect(
                        color = dropletColor.copy(alpha = 0.12f),
                        topLeft = androidx.compose.ui.geometry.Offset((size.width - capSizePx) / 2f, topY),
                        size = androidx.compose.ui.geometry.Size(capSizePx, capSizePx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(capSizePx * 0.34f)
                    )
                } else {
                    val path = buildLiquidDropletPath(
                        totalWidth = size.width,
                        totalHeight = size.height,
                        capSize = capSizePx,
                        neckHeight = neckHeightPx,
                        bubbleWidth = bubbleWPx,
                        bubbleHeight = bubbleHPx,
                        anchorCenterX = size.width / 2f,
                        progress = progress,
                        expandUpwards = expandUpwards
                    )
                    drawPath(path = path, color = dropletColor)
                }
            }
            Box(
                modifier = Modifier
                    .align(if (expandUpwards) Alignment.BottomCenter else Alignment.TopCenter)
                    .size(capSizeDp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = if (progress <= 0.01f) MaterialTheme.colorScheme.onSurface else dropletContentColor
                )
            }
            if (progress > 0.62f) {
                val textT = smoothStep(0.62f, 1f, progress)
                Box(
                    modifier = Modifier
                        .align(if (expandUpwards) Alignment.BottomCenter else Alignment.TopCenter)
                        .padding(
                            top = if (expandUpwards) 0.dp else capSizeDp + neckHeightDp,
                            bottom = if (expandUpwards) capSizeDp + neckHeightDp else 0.dp
                        )
                        .width(bubbleW)
                        .height(bubbleH)
                        .graphicsLayer {
                            alpha = textT
                            scaleX = lerpFloat(0.72f, 1f, textT)
                            scaleY = lerpFloat(0.72f, 1f, textT)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tooltipText,
                        color = dropletContentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 340)
@Composable
fun ExpressiveIconButtonRightEdgePreview() {
    val stages = listOf(0.10f to "10%", 0.25f to "25%", 0.50f to "50%", 1f to "100%")
    MaterialTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                stages.forEach { (p, label) ->
                    Row(
                        modifier = Modifier.width(320.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = label, style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        val density = LocalDensity.current
                        val capSizeDp = 48.dp
                        val neckHeightDp = 8.dp
                        val bubbleW = 140.dp
                        val bubbleH = 34.dp
                        val capSizePx = with(density) { capSizeDp.toPx() }
                        val neckHeightPx = with(density) { neckHeightDp.toPx() }
                        val bubbleWPx = with(density) { bubbleW.toPx() }
                        val bubbleHPx = with(density) { bubbleH.toPx() }
                        val dropletColor = MaterialTheme.colorScheme.primary
                        val onDropletColor = MaterialTheme.colorScheme.onPrimary

                        // Кнопка ровно у правого края: anchorCenterX = bubbleW - capSize/2
                        Box(
                            modifier = Modifier.size(width = bubbleW, height = capSizeDp + neckHeightDp + bubbleH)
                        ) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val anchorCenterX = size.width - capSizePx / 2f
                                val path = buildLiquidDropletPath(
                                    totalWidth = size.width,
                                    totalHeight = size.height,
                                    capSize = capSizePx,
                                    neckHeight = neckHeightPx,
                                    bubbleWidth = bubbleWPx,
                                    bubbleHeight = bubbleHPx,
                                    anchorCenterX = anchorCenterX,
                                    progress = p,
                                    expandUpwards = false
                                )
                                drawPath(path = path, color = dropletColor)
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(capSizeDp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = onDropletColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 480, heightDp = 280)
@Composable
fun ExpressiveIconButtonDropletStagesPreview() {
    val stages = listOf(0.12f to "12%", 0.20f to "20%", 0.35f to "35%", 0.70f to "70%", 1f to "100%")
    MaterialTheme {
        Surface(modifier = Modifier.padding(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    stages.forEach { (progress, label) ->
                        LiquidDropletStageSample(progress = progress, label = label)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    stages.forEach { (progress, label) ->
                        LiquidDropletStageSample(progress = progress, label = "$label ↑", expandUpwards = true)
                    }
                }
            }
        }
    }
}

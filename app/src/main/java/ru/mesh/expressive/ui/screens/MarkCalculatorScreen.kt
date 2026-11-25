package ru.mesh.expressive.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mesh.expressive.data.model.MarkItem
import ru.mesh.expressive.data.model.RoundingLimits
import ru.mesh.expressive.data.model.SubjectSummary
import ru.mesh.expressive.ui.components.M3ConnectedButtonGroup
import ru.mesh.expressive.ui.components.formatWeightSuperscript
import ru.mesh.expressive.ui.theme.MeshExpressiveTheme
import ru.mesh.expressive.ui.theme.PillShape
import ru.mesh.expressive.ui.viewmodel.MeshMainViewModel
import ru.mesh.expressive.util.MarkCalculatorEngine
import ru.mesh.expressive.util.MarkCombinationHint
import ru.mesh.expressive.util.SimulatedExistingMark
import ru.mesh.expressive.util.SimulationResult
import ru.mesh.expressive.util.VirtualMark
import java.util.Locale
import kotlin.math.abs

@Composable
fun MarkCalculatorScreen(
    subject: SubjectSummary,
    viewModel: MeshMainViewModel,
    onBack: () -> Unit
) {
    val roundingLimits by viewModel.roundingLimits.collectAsState()
    val defaultWeighted by viewModel.showWeightedGpa.collectAsState()

    MarkCalculatorContent(
        subject = subject,
        roundingLimits = roundingLimits,
        initialWeighted = defaultWeighted,
        onBack = onBack,
        onUpdateManualLimits = { t3, t4, t5 -> viewModel.updateManualRoundingLimits(t3, t4, t5) },
        onResetLimitsToServer = { viewModel.resetRoundingLimitsToServer() }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MarkCalculatorContent(
    subject: SubjectSummary,
    roundingLimits: RoundingLimits,
    initialWeighted: Boolean = true,
    onBack: () -> Unit = {},
    onUpdateManualLimits: (Double, Double, Double) -> Unit = { _, _, _ -> },
    onResetLimitsToServer: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    var showWeighted by remember(subject.subjectId, initialWeighted) {
        mutableStateOf(initialWeighted)
    }
    var selectedWeight by remember { mutableDoubleStateOf(1.0) }
    var nextVirtualId by remember { mutableLongStateOf(1L) }

    var existingMarks by remember(subject) {
        mutableStateOf(MarkCalculatorEngine.buildInitialExistingMarks(subject))
    }
    var virtualMarks by remember(subject) {
        mutableStateOf<List<VirtualMark>>(emptyList())
    }
    var editingExistingMark by remember { mutableStateOf<SimulatedExistingMark?>(null) }
    var showLimitsDialog by remember { mutableStateOf(false) }

    val simResult = remember(subject, existingMarks, virtualMarks, showWeighted, roundingLimits) {
        MarkCalculatorEngine.calculate(
            subject = subject,
            existingMarks = existingMarks,
            virtualMarks = virtualMarks,
            showWeighted = showWeighted,
            limits = roundingLimits
        )
    }

    val hasChanges = virtualMarks.isNotEmpty() || existingMarks.any { it.isModified }

    val resetAll = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        existingMarks = MarkCalculatorEngine.buildInitialExistingMarks(subject)
        virtualMarks = emptyList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = subject.subject,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val limitsLabel = when {
                            roundingLimits.isManualOverride -> "Свои пороги округления"
                            roundingLimits.isFromServer -> "Школьные пороги МЭШ"
                            else -> "Стандарт МЭШ (${formatScore(roundingLimits.three)} / ${formatScore(roundingLimits.four)} / ${formatScore(roundingLimits.five)})"
                        }
                        Text(
                            text = limitsLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (hasChanges) {
                        IconButton(onClick = resetAll) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Сбросить песочницу",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { showLimitsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Пороги округления"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hero Score Card
            HeroSimulationCard(
                simResult = simResult,
                limits = roundingLimits,
                showWeighted = showWeighted,
                onToggleWeighted = { showWeighted = it },
                isDark = isDark
            )

            // 2. Quick Add Sandbox
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Добавить оценки в песочницу",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (virtualMarks.isNotEmpty()) {
                            Text(
                                text = "+${virtualMarks.size} шт.",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (showWeighted) {
                        val weights = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
                        M3ConnectedButtonGroup(
                            items = weights,
                            selectedItem = selectedWeight,
                            onItemSelected = { selectedWeight = it },
                            label = { "x${it.toInt()}" },
                            showCheckmark = false,
                            scrollable = false
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(5, 4, 3, 2).forEach { value ->
                            val (bg, fg) = gradeColors(value, isDark)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = bg,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val w = if (showWeighted) selectedWeight else 1.0
                                        virtualMarks = virtualMarks + VirtualMark(
                                            id = nextVirtualId++,
                                            value = value,
                                            weight = w
                                        )
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (showWeighted && selectedWeight > 1.0) {
                                            "$value${formatWeightSuperscript(selectedWeight)}"
                                        } else {
                                            value.toString()
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = fg
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = virtualMarks.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                virtualMarks.forEach { vm ->
                                    val (bg, fg) = gradeColors(vm.value, isDark)
                                    Surface(
                                        shape = PillShape,
                                        color = bg,
                                        modifier = Modifier
                                            .clip(PillShape)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                virtualMarks = virtualMarks.filterNot { it.id == vm.id }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = if (showWeighted && vm.weight > 1.0) {
                                                    "${vm.value}${formatWeightSuperscript(vm.weight)}"
                                                } else {
                                                    vm.value.toString()
                                                },
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = fg
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Удалить",
                                                tint = fg.copy(alpha = 0.75f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Outcomes Matrix & Safety Margin
            if (simResult.goalHints.isNotEmpty() || simResult.safetyMargin != null) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Матрица исходов",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        simResult.goalHints.forEach { (grade, hints) ->
                            val threshold = roundingLimits.thresholdFor(grade)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "До «$grade» (от ${formatScore(threshold)}) — нажми для добавления:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    hints.forEach { hint ->
                                        HintActionChip(
                                            hint = hint,
                                            isDark = isDark,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val added = mutableListOf<VirtualMark>()
                                                repeat(hint.fivesCount) {
                                                    added.add(VirtualMark(id = nextVirtualId++, value = 5, weight = hint.weight))
                                                }
                                                repeat(hint.foursCount) {
                                                    added.add(VirtualMark(id = nextVirtualId++, value = 4, weight = hint.weight))
                                                }
                                                virtualMarks = virtualMarks + added
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        simResult.safetyMargin?.let { margin ->
                            if (simResult.goalHints.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Запас прочности для «${margin.currentGrade}» (от ${formatScore(margin.threshold)})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val twosText = if (margin.safeTwosX1 == Int.MAX_VALUE) "сколько угодно" else "${margin.safeTwosX1} шт."
                                        val threesText = if (margin.safeThreesX1 == Int.MAX_VALUE) "сколько угодно" else "${margin.safeThreesX1} шт."
                                        Text(
                                            text = "Можно получить с весом x1: двоек — $twosText, троек — $threesText",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Interactive Existing Marks
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Текущие оценки (${existingMarks.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (simResult.activePointsCount > 0 || simResult.expiredPointsCount > 0) {
                            Text(
                                text = buildString {
                                    if (simResult.activePointsCount > 0) append("Точек: ${simResult.activePointsCount}")
                                    if (simResult.expiredPointsCount > 0) {
                                        if (isNotEmpty()) append(" • ")
                                        append("Сгорело: ${simResult.expiredPointsCount}")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    if (existingMarks.isEmpty()) {
                        Text(
                            text = "Список отдельных оценок пуст — расчёт идёт от среднего балла.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Нажмите на оценку или точку, чтобы закрыть, заменить или временно исключить её.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            existingMarks.forEach { item ->
                                ExistingSimMarkPlaque(
                                    item = item,
                                    showWeighted = showWeighted,
                                    isDark = isDark,
                                    onClick = { editingExistingMark = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editingExistingMark?.let { target ->
        ExistingMarkEditDialog(
            item = target,
            onDismiss = { editingExistingMark = null },
            onSelectOverride = { newVal ->
                existingMarks = existingMarks.map {
                    if (it.index == target.index) it.copy(overrideValue = newVal, isExcluded = false) else it
                }
                editingExistingMark = null
            },
            onToggleExclude = {
                existingMarks = existingMarks.map {
                    if (it.index == target.index) it.copy(isExcluded = !it.isExcluded, overrideValue = null) else it
                }
                editingExistingMark = null
            },
            onRestore = {
                existingMarks = existingMarks.map {
                    if (it.index == target.index) it.copy(overrideValue = null, isExcluded = false) else it
                }
                editingExistingMark = null
            }
        )
    }

    if (showLimitsDialog) {
        RoundingLimitsDialog(
            currentLimits = roundingLimits,
            onDismiss = { showLimitsDialog = false },
            onSave = { t3, t4, t5 ->
                onUpdateManualLimits(t3, t4, t5)
                showLimitsDialog = false
            },
            onResetToServer = {
                onResetLimitsToServer()
                showLimitsDialog = false
            }
        )
    }
}

@Composable
private fun HeroSimulationCard(
    simResult: SimulationResult,
    limits: RoundingLimits,
    showWeighted: Boolean,
    onToggleWeighted: (Boolean) -> Unit,
    isDark: Boolean
) {
    val animatedAvg by animateFloatAsState(
        targetValue = simResult.simAverage.toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "simAvg"
    )
    val finalMark = simResult.simFinalMark
    val (gradeBg, gradeFg) = if (finalMark in 2..5) {
        gradeColors(finalMark, isDark)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
    }
    val animGradeBg by animateColorAsState(targetValue = gradeBg, label = "gradeBg")
    val animGradeFg by animateColorAsState(targetValue = gradeFg, label = "gradeFg")

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Прогноз среднего балла",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (simResult.simAverage > 0.0) formatScore(animatedAvg.toDouble()) else "—",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (abs(simResult.delta) >= 0.005) {
                            val isPositive = simResult.delta > 0
                            val deltaColor = if (isPositive) {
                                if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                            Surface(
                                shape = PillShape,
                                color = deltaColor.copy(alpha = 0.14f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = (if (isPositive) "+" else "") + formatScore(simResult.delta),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = deltaColor
                                )
                            }
                        }
                    }
                    if (simResult.baseAverage > 0.0) {
                        Text(
                            text = "База: ${formatScore(simResult.baseAverage)} (итог ${simResult.baseFinalMark})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Final mark badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = animGradeBg,
                    modifier = Modifier.size(68.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (finalMark in 2..5) finalMark.toString() else "—",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = animGradeFg
                        )
                        Text(
                            text = "итог",
                            style = MaterialTheme.typography.labelSmall,
                            color = animGradeFg.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Threshold track 2.00 .. 5.00
            ThresholdProgressTrack(
                average = animatedAvg.toDouble(),
                limits = limits,
                barColor = animGradeFg,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            // Weighted vs Simple toggle
            M3ConnectedButtonGroup(
                items = listOf(true, false),
                selectedItem = showWeighted,
                onItemSelected = onToggleWeighted,
                label = { if (it) "Взвешенный" else "Без весов" },
                showCheckmark = true,
                scrollable = false
            )
        }
    }
}

@Composable
private fun ThresholdProgressTrack(
    average: Double,
    limits: RoundingLimits,
    barColor: Color,
    trackColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val h = 8.dp.toPx()
            val y = (size.height - h) / 2f
            val r = CornerRadius(h / 2f, h / 2f)

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, y),
                size = Size(size.width, h),
                cornerRadius = r
            )

            val norm = ((average - 2.0) / 3.0).coerceIn(0.0, 1.0).toFloat()
            if (norm > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(0f, y),
                    size = Size(size.width * norm, h),
                    cornerRadius = r
                )
            }

            listOf(limits.three, limits.four, limits.five).forEach { t ->
                val tx = (((t - 2.0) / 3.0).coerceIn(0.0, 1.0).toFloat()) * size.width
                drawLine(
                    color = Color.White.copy(alpha = 0.65f),
                    start = Offset(tx, 0f),
                    end = Offset(tx, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("2.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3: ${formatScore(limits.three)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("4: ${formatScore(limits.four)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("5: ${formatScore(limits.five)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("5.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HintActionChip(
    hint: MarkCombinationHint,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val label = buildString {
        if (hint.fivesCount > 0) append("${hint.fivesCount}× [5]")
        if (hint.foursCount > 0) {
            if (isNotEmpty()) append(" + ")
            append("${hint.foursCount}× [4]")
        }
        if (hint.weight > 1.0) append(" x${hint.weight.toInt()}")
        append(" -> ${formatScore(hint.resultingAverage)}")
    }
    val (bg, fg) = gradeColors(hint.targetGrade, isDark)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg.copy(alpha = 0.85f),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

@Composable
private fun ExistingSimMarkPlaque(
    item: SimulatedExistingMark,
    showWeighted: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val displayText = when {
        item.isExcluded -> item.original.rawValue.ifBlank { item.original.effectiveValue.toString() }
        item.overrideValue != null -> "${item.overrideValue}"
        item.isPointLike && item.original.isPointExpired -> "2"
        item.isPointLike -> "•"
        else -> item.original.rawValue.ifBlank { item.original.value.toString() }
    }

    val (bg, fg) = when {
        item.isExcluded -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        item.overrideValue != null -> gradeColors(item.overrideValue, isDark)
        item.isPointLike && item.original.isPointExpired -> gradeColors(2, isDark)
        item.isPointLike -> {
            val amberBg = if (isDark) Color(0xFF4E342E) else Color(0xFFFFF3E0)
            val amberFg = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
            amberBg to amberFg
        }
        item.original.value in 2..5 -> gradeColors(item.original.value, isDark)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
    }

    val border = when {
        item.overrideValue != null -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        item.isPointLike && !item.isExcluded -> BorderStroke(1.dp, fg.copy(alpha = 0.5f))
        else -> null
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = border,
        modifier = Modifier
            .size(width = 46.dp, height = 42.dp)
            .alpha(if (item.isExcluded) 0.42f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (showWeighted && item.original.weight > 1.0) {
                    "$displayText${formatWeightSuperscript(item.original.weight)}"
                } else {
                    displayText
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                textDecoration = if (item.isExcluded) TextDecoration.LineThrough else null
            )
            if (item.overrideValue != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ExistingMarkEditDialog(
    item: SimulatedExistingMark,
    onDismiss: () -> Unit,
    onSelectOverride: (Int) -> Unit,
    onToggleExclude: () -> Unit,
    onRestore: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val title = when {
        item.isPointLike && item.original.isPointExpired -> "Просроченная точка (вес x${item.original.weight.toInt()})"
        item.isPointLike -> "Активная точка (вес x${item.original.weight.toInt()})"
        else -> "Оценка «${item.original.rawValue}» (вес x${item.original.weight.toInt()})"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (item.isPointLike) "Закрыть точку в симуляции как:" else "Временно заменить в симуляции на:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 4, 3, 2).forEach { v ->
                        val (bg, fg) = gradeColors(v, isDark)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = bg,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectOverride(v) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(v.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = fg)
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = onToggleExclude,
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (item.isExcluded) "Вернуть в расчёт" else "Исключить из расчёта")
                }
                if (item.isModified) {
                    TextButton(
                        onClick = onRestore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сбросить к исходной")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun RoundingLimitsDialog(
    currentLimits: RoundingLimits,
    onDismiss: () -> Unit,
    onSave: (Double, Double, Double) -> Unit,
    onResetToServer: () -> Unit
) {
    var l3 by remember { mutableFloatStateOf(currentLimits.three.toFloat()) }
    var l4 by remember { mutableFloatStateOf(currentLimits.four.toFloat()) }
    var l5 by remember { mutableFloatStateOf(currentLimits.five.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пороги округления", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LimitSliderRow("Оценка «3» от", l3, 2.30f..2.90f) { l3 = it }
                LimitSliderRow("Оценка «4» от", l4, 3.30f..3.90f) { l4 = it }
                LimitSliderRow("Оценка «5» от", l5, 4.30f..4.90f) { l5 = it }
                if (currentLimits.isManualOverride) {
                    TextButton(onClick = onResetToServer, modifier = Modifier.fillMaxWidth()) {
                        Text("Сбросить к школьным порогам")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
        confirmButton = {
            Button(
                onClick = { onSave(l3.toDouble(), l4.toDouble(), l5.toDouble()) },
                shape = PillShape
            ) {
                Text("Применить")
            }
        }
    )
}

@Composable
private fun LimitSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(formatScore(value.toDouble()), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = { onChange(((it * 20).toInt()) / 20f) },
            valueRange = range
        )
    }
}

private fun gradeColors(value: Int, isDark: Boolean): Pair<Color, Color> {
    return when (value) {
        5 -> if (isDark) Color(0xFF1B3B22) to Color(0xFF81C784) else Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        4 -> if (isDark) Color(0xFF1A3248) to Color(0xFF64B5F6) else Color(0xFFE3F2FD) to Color(0xFF1565C0)
        3 -> if (isDark) Color(0xFF423015) to Color(0xFFFFB74D) else Color(0xFFFFF8E1) to Color(0xFFEF6C00)
        else -> if (isDark) Color(0xFF4A1E21) to Color(0xFFE57373) else Color(0xFFFFEBEE) to Color(0xFFC62828)
    }
}

private fun formatScore(value: Double): String = String.format(Locale.US, "%.2f", value)

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun MarkCalculatorPreviewLight() {
    MeshExpressiveTheme(darkTheme = false, dynamicColor = false) {
        MarkCalculatorContent(
            subject = SubjectSummary(
                subject = "Алгебра",
                averageMark = 4.25,
                marks = listOf(
                    MarkItem(id = "1", value = 5, rawValue = "5", weight = 1.0),
                    MarkItem(id = "2", value = 4, rawValue = "4", weight = 2.0),
                    MarkItem(id = "3", value = 0, rawValue = ".", isPoint = true, pointDate = "2099-01-01", weight = 2.0),
                    MarkItem(id = "4", value = 4, rawValue = "4", weight = 1.0)
                )
            ),
            roundingLimits = RoundingLimits(three = 2.60, four = 3.60, five = 4.60, isFromServer = true)
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun MarkCalculatorPreviewDark() {
    MeshExpressiveTheme(darkTheme = true, dynamicColor = false) {
        MarkCalculatorContent(
            subject = SubjectSummary(
                subject = "Физика",
                averageMark = 3.50,
                marks = listOf(
                    MarkItem(id = "1", value = 3, rawValue = "3", weight = 1.0),
                    MarkItem(id = "2", value = 4, rawValue = "4", weight = 1.0),
                    MarkItem(id = "3", value = 0, rawValue = ".", isPoint = true, pointDate = "2020-01-01", weight = 1.0)
                )
            ),
            roundingLimits = RoundingLimits(three = 2.60, four = 3.60, five = 4.60, isFromServer = false)
        )
    }
}

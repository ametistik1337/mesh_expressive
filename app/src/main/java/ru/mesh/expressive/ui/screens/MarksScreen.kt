package ru.mesh.expressive.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mesh.expressive.data.model.MarkItem
import ru.mesh.expressive.data.model.StudyPeriodOption
import ru.mesh.expressive.data.model.SubjectPeriodInfo
import ru.mesh.expressive.data.model.SubjectSummary
import ru.mesh.expressive.ui.components.ExpressiveChipSegmentedToggle
import ru.mesh.expressive.ui.components.ExpressiveMarkPlaque
import ru.mesh.expressive.ui.components.ExpressivePullToRefreshBox
import ru.mesh.expressive.ui.components.M3ConnectedButtonGroup
import ru.mesh.expressive.ui.components.expressiveBounceClick
import ru.mesh.expressive.ui.theme.*
import ru.mesh.expressive.ui.viewmodel.MarksViewMode
import ru.mesh.expressive.ui.viewmodel.MeshMainViewModel
import kotlin.math.ceil

@Composable
fun MarksScreen(viewModel: MeshMainViewModel) {
    val subjectSummaries by viewModel.subjectSummaries.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val viewMode by viewModel.marksViewMode.collectAsState()
    val showWeightedGpa by viewModel.showWeightedGpa.collectAsState()
    var showCalculatorDialog by remember { mutableStateOf(false) }
    var selectedSubjectForCalc by remember { mutableStateOf<SubjectSummary?>(null) }

    val periodsSchedules by viewModel.periodsSchedules.collectAsState()

    // 1. Вычисляем список учебных периодов (четвертей/семестров) из API с автоопределением активного
    val studyPeriods = remember(periodsSchedules) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val groups = mutableMapOf<String, MutableList<String>>()
        periodsSchedules.forEach { item ->
            val t = item.title.trim()
            if (t.contains("четверть", ignoreCase = true) ||
                t.contains("семестр", ignoreCase = true) ||
                t.contains("триместр", ignoreCase = true)) {
                groups.getOrPut(t) { mutableListOf() }.add(item.date)
            }
        }
        val list = if (groups.isEmpty()) {
            listOf(
                StudyPeriodOption("1", "1 четверть", "1 четв.", "2026-09-01", "2026-10-23", false),
                StudyPeriodOption("2", "2 четверть", "2 четв.", "2026-11-04", "2026-12-30", true),
                StudyPeriodOption("3", "3 четверть", "3 четв.", "2027-01-11", "2027-03-12", false),
                StudyPeriodOption("4", "4 четверть", "4 четв.", "2027-03-22", "2027-05-31", false)
            )
        } else {
            val sorted = groups.map { (title, dates) ->
                val start = dates.minOrNull() ?: ""
                val end = dates.maxOrNull() ?: ""
                val isCur = today in start..end
                val shortT = title.replace(" четверть", " четв.").replace(" семестр", " сем.").replace(" триместр", " трим.")
                StudyPeriodOption(
                    id = title,
                    title = title,
                    shortTitle = shortT,
                    startDate = start,
                    endDate = end,
                    isCurrent = isCur
                )
            }.sortedBy { it.startDate }

            val hasCurrent = sorted.any { it.isCurrent }
            if (!hasCurrent && sorted.isNotEmpty()) {
                val nextOrLast = sorted.firstOrNull { it.startDate > today } ?: sorted.last()
                sorted.map { if (it.id == nextOrLast.id) it.copy(isCurrent = true) else it }
            } else {
                sorted
            }
        }
        list
    }

    val allPeriodsOption = remember {
        StudyPeriodOption(id = "all", title = "Все четверти", shortTitle = "Все", startDate = "", endDate = "", isCurrent = false)
    }

    val periodOptions = remember(studyPeriods) {
        studyPeriods + allPeriodsOption
    }

    val todayDateStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val latestCompletedPeriod = remember(studyPeriods, todayDateStr) {
        studyPeriods.filter { it.endDate.isNotBlank() && it.endDate < todayDateStr }.maxByOrNull { it.endDate }
    }

    // Автоопределение активного периода при запуске или загрузке расписания:
    // если текущая четверть еще не началась или в ней 0 оценок, открываем последнюю завершенную с оценками
    var selectedPeriod by remember(studyPeriods, subjectSummaries) {
        val current = studyPeriods.firstOrNull { it.isCurrent }
        val currentHasMarks = current != null && subjectSummaries.any { subj ->
            subj.marks.any { m ->
                val d = m.date.substringBefore("T").trim()
                d in current.startDate..current.endDate
            }
        }
        val initial = if (current != null && currentHasMarks) {
            current
        } else {
            latestCompletedPeriod ?: current ?: studyPeriods.firstOrNull() ?: allPeriodsOption
        }
        mutableStateOf(initial)
    }

    // Фильтрация предметов и пересчет среднего балла и итоговых оценок под выбранный период
    val displaySubjectSummaries = remember(subjectSummaries, selectedPeriod, showWeightedGpa) {
        val today = todayDateStr

        if (selectedPeriod.id == "all") {
            subjectSummaries
        } else {
            subjectSummaries.map { subj ->
                val matchingPeriod = subj.periods.firstOrNull { p ->
                    p.title.contains(selectedPeriod.title, ignoreCase = true) ||
                    selectedPeriod.title.contains(p.title, ignoreCase = true)
                }

                val periodMarks = subj.marks.filter { m ->
                    val mDate = m.date.substringBefore("T").trim()
                    mDate.isNotBlank() && mDate in selectedPeriod.startDate..selectedPeriod.endDate
                }

                val periodAvg = matchingPeriod?.average?.takeIf { it > 0.0 }
                    ?: if (periodMarks.isNotEmpty()) {
                        val valid = periodMarks.filter { (!it.isPoint || it.isPointExpired) && it.effectiveValue in 2..5 }
                        if (valid.isNotEmpty()) {
                            if (showWeightedGpa) {
                                val wSum = valid.sumOf { it.effectiveValue * it.weight }
                                val wTotal = valid.sumOf { it.weight }
                                if (wTotal > 0.0) wSum / wTotal else 0.0
                            } else {
                                valid.map { it.effectiveValue }.average()
                            }
                        } else 0.0
                    } else 0.0

                // Финальная аттестационная оценка:
                // 1) Из конкретного периода в API (matchingPeriod.fixedValue)
                // 2) Либо, если periods не возвращены API, финальная оценка принадлежит последней завершенной четверти
                val isCompletedPeriod = selectedPeriod.endDate.isNotBlank() && selectedPeriod.endDate < today
                val periodFinalMark = matchingPeriod?.fixedValue
                    ?: if (isCompletedPeriod && (latestCompletedPeriod == null || selectedPeriod.id == latestCompletedPeriod.id)) {
                        subj.finalMark
                    } else null

                subj.copy(
                    averageMark = periodAvg,
                    finalMark = periodFinalMark,
                    marks = periodMarks
                )
            }
        }
    }

    // Все оценки текущего выбранного периода, сгруппированные по датам
    val allMarksGroupedByDate = remember(displaySubjectSummaries) {
        displaySubjectSummaries.flatMap { it.marks }
            .groupBy { it.date }
            .toList()
            .sortedByDescending { it.first }
    }

    // Оценки, требующие исправления (только активные точки с неистекшим сроком сдачи)
    val fixableMarks = remember(displaySubjectSummaries) {
        displaySubjectSummaries.flatMap { it.marks }
            .filter { mark ->
                val hasPoint = mark.isPoint || !mark.pointDate.isNullOrBlank() || mark.rawValue.endsWith(".") || mark.rawValue == "."
                hasPoint && !mark.isPointExpired
            }
            .distinctBy { if (it.id.isNotBlank()) it.id else "${it.subject}_${it.date}_${it.value}" }
            .sortedWith(
                compareBy<MarkItem> { it.pointDate ?: "9999-99-99" }
                    .thenByDescending { it.date }
            )
    }

    val activeGpa by viewModel.activeGpa.collectAsState()
    val displayGpa = remember(displaySubjectSummaries, showWeightedGpa, activeGpa, selectedPeriod) {
        if (selectedPeriod.id == "all") {
            activeGpa
        } else {
            val allMarks = displaySubjectSummaries.flatMap { it.marks }
                .filter { (!it.isPoint || it.isPointExpired) && it.effectiveValue in 2..5 }
            if (allMarks.isNotEmpty()) {
                if (showWeightedGpa) {
                    val wTotal = allMarks.sumOf { it.weight }
                    if (wTotal > 0.0) allMarks.sumOf { it.effectiveValue * it.weight } / wTotal else 0.0
                } else {
                    allMarks.map { it.effectiveValue }.average()
                }
            } else {
                val nonZeroAverages = displaySubjectSummaries.map { it.averageMark }.filter { it > 0.0 }
                if (nonZeroAverages.isNotEmpty()) nonZeroAverages.average() else 0.0
            }
        }
    }

    val periodSubtitle = remember(selectedPeriod) {
        if (selectedPeriod.id == "all") {
            "Все учебные периоды 2026/2027 учебного года"
        } else {
            val startFormatted = ru.mesh.expressive.util.DateUtils.formatPointDate(selectedPeriod.startDate)
            val endFormatted = ru.mesh.expressive.util.DateUtils.formatPointDate(selectedPeriod.endDate)
            if (startFormatted.isNotBlank() && endFormatted.isNotBlank()) {
                "${selectedPeriod.title} ($startFormatted — $endFormatted)"
            } else {
                selectedPeriod.title
            }
        }
    }

    val gpaLabel = if (showWeightedGpa) "Средневзвешенный балл" else "Средний балл (арифм.)"

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshData() },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
            ) {
                // 1. GPA Hero Card (динамический средний балл за выбранный период)
                item(key = "gpa_hero_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveHeroShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = gpaLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = if (displayGpa > 0.0) "Средний балл: ${String.format(java.util.Locale.US, "%.2f", displayGpa)}" else "Средний балл: —",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = periodSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // 2. Плашка выбора четверти / семестра (Material 3 Expressive Chips с автоопределением)
                item(key = "study_period_selector") {
                    StudyPeriodSelectorRow(
                        periods = periodOptions,
                        selectedPeriod = selectedPeriod,
                        onPeriodSelected = { selectedPeriod = it }
                    )
                }

                // 3. Mode Header with Segmented Selector (Предметы / Даты)
                item(key = "mode_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Оценки",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (viewMode == MarksViewMode.BY_SUBJECT) "${displaySubjectSummaries.size} предметов" else "${allMarksGroupedByDate.sumOf { it.second.size }} оценок",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Expressive Chip Segmented Selector (Чипсины)
                        ExpressiveChipSegmentedToggle(
                            items = listOf(MarksViewMode.BY_SUBJECT, MarksViewMode.BY_DATE),
                            selectedItem = viewMode,
                            onItemSelected = { viewModel.setMarksViewMode(it) },
                            label = { when (it) { MarksViewMode.BY_SUBJECT -> "Предметы"; MarksViewMode.BY_DATE -> "Даты" } },
                            icon = { when (it) { MarksViewMode.BY_SUBJECT -> Icons.AutoMirrored.Filled.Subject; MarksViewMode.BY_DATE -> Icons.Default.CalendarMonth } }
                        )
                    }
                }

                // 4. Content by Mode
                if (displaySubjectSummaries.isEmpty()) {
                    item(key = "marks_empty") {
                        ru.mesh.expressive.ui.components.ExpressiveEmptyState(
                            title = "Здесь ничего нет",
                            subtitle = "В выбранном учебном периоде выставленных оценок нет",
                            icon = Icons.Default.Inbox
                        )
                    }
                } else if (viewMode == MarksViewMode.BY_SUBJECT) {
                    items(
                        items = displaySubjectSummaries,
                        key = { "${it.subjectId}_${it.subject}" },
                        contentType = { "subject" }
                    ) { subject ->
                        SubjectMarksCard(
                            subjectSummary = subject,
                            showWeightedGpa = showWeightedGpa,
                            onOpenCalculator = {
                                selectedSubjectForCalc = subject
                                showCalculatorDialog = true
                            },
                            onMarkClick = { viewModel.openMarkDetails(it) }
                        )
                    }
                } else {
                    // 1. Сверху отображаем карточку оценок, которые можно исправить (точки и задолженности)
                    if (fixableMarks.isNotEmpty()) {
                        item(key = "fixable_marks_top") {
                            FixableMarksGroupCard(
                                fixableMarks = fixableMarks,
                                onMarkClick = { viewModel.openMarkDetails(it) }
                            )
                        }
                    }

                    // 2. Grouped by Date (по числу)
                    items(
                        items = allMarksGroupedByDate,
                        key = { it.first },
                        contentType = { "dateGroup" }
                    ) { (date, marks) ->
                        // Внутри дня также поднимаем наверх оценки, требующие исправления
                        val sortedDayMarks = remember(marks) {
                            marks.sortedWith(
                                compareByDescending<MarkItem> { it.isPoint || !it.pointDate.isNullOrBlank() || it.rawValue.endsWith(".") }
                                    .thenByDescending { it.createdAt ?: it.id }
                            )
                        }
                        DateMarksGroupCard(
                            date = date,
                            marks = sortedDayMarks,
                            onMarkClick = { viewModel.openMarkDetails(it) }
                        )
                    }
                }
            }
        }
    }

    if (showCalculatorDialog && selectedSubjectForCalc != null) {
        val subject = selectedSubjectForCalc!!
        var targetGpa by remember { mutableFloatStateOf(4.60f) }
        val neededFives = calculateNeededFives(subject, targetGpa)

        AlertDialog(
            onDismissRequest = { showCalculatorDialog = false },
            icon = { Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
            title = { Text("Калькулятор оценок", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Предмет: ${subject.subject}", fontWeight = FontWeight.SemiBold)
                    Text(text = "Текущий средний балл: ${if (subject.averageMark > 0.0) String.format(java.util.Locale.US, "%.2f", subject.averageMark) else "—"}")

                    // Target presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = PillShape,
                            color = if (targetGpa == 3.60f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { targetGpa = 3.60f }
                        ) {
                            Text(
                                text = "На «4» (3.60)",
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (targetGpa == 3.60f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = PillShape,
                            color = if (targetGpa == 4.60f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { targetGpa = 4.60f }
                        ) {
                            Text(
                                text = "На «5» (4.60)",
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (targetGpa == 4.60f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Целевой балл:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", targetGpa),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = targetGpa,
                        onValueChange = { targetGpa = (it * 20).toInt() / 20f },
                        valueRange = 3.50f..4.80f
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = if (subject.averageMark >= targetGpa)
                                    "Цель уже достигнута!"
                                else
                                    "Нужно получить пятёрок (с весом 1.0): $neededFives шт.",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "В МЭШ средний балл от 4.60 округляется в итоговую «5» за четверть",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCalculatorDialog = false },
                    shape = PillShape
                ) {
                    Text("Понятно")
                }
            }
        )
    }
}



/**
 * Селектор учебных периодов (четвертей/семестров)
 * на базе официальной спецификации Material 3 Connected Button Groups (https://m3.material.io/components/button-groups/overview)
 */
@Composable
fun StudyPeriodSelectorRow(
    periods: List<StudyPeriodOption>,
    selectedPeriod: StudyPeriodOption,
    onPeriodSelected: (StudyPeriodOption) -> Unit,
    modifier: Modifier = Modifier
) {
    M3ConnectedButtonGroup(
        items = periods,
        selectedItem = selectedPeriod,
        onItemSelected = onPeriodSelected,
        label = { it.title },
        showCheckmark = true,
        showIndicatorDot = { it.isCurrent },
        scrollable = true,
        modifier = modifier
    )
}

@Composable
fun FixableMarksGroupCard(
    fixableMarks: List<MarkItem>,
    onMarkClick: ((MarkItem) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(true) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
        label = "fixable_arrow_rotation"
    )

    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val itemBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    val redContent = if (isDark) Color(0xFFFF7C83) else Color(0xFFC62828)
    val redPillBg = if (isDark) Color(0xFFE94255).copy(alpha = 0.22f) else Color(0xFFFFEBEE)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveCardShape,
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        border = BorderStroke(1.dp, redContent.copy(alpha = if (isDark) 0.3f else 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PriorityHigh,
                        contentDescription = null,
                        tint = redContent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Можно исправить",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = PillShape,
                        color = redPillBg
                    ) {
                        Text(
                            text = "${fixableMarks.size} ${if (fixableMarks.size == 1) "оценка" else "оценок"}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = redContent
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(arrowRotation)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fixableMarks.forEach { mark ->
                        val formattedPointDate = if (!mark.pointDate.isNullOrBlank()) ru.mesh.expressive.util.DateUtils.formatPointDate(mark.pointDate) else null
                        val markRelativeDate = ru.mesh.expressive.util.DateUtils.formatRelativeDate(mark.date)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(itemBg)
                                .then(if (onMarkClick != null) Modifier.clickable { onMarkClick(mark) } else Modifier)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = mark.subject,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (mark.topic.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mark.topic,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val pointBadgeBg = if (isDark) Color(0xFF2C2218) else Color(0xFFFFF3E0)
                                    val pointBadgeFg = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
                                    val pointBadgeText = if (!formattedPointDate.isNullOrBlank()) "Точка до $formattedPointDate" else "Точка"
                                    Surface(
                                        shape = PillShape,
                                        color = pointBadgeBg
                                    ) {
                                        Text(
                                            text = pointBadgeText,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = pointBadgeFg
                                        )
                                    }
                                    if (markRelativeDate.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = markRelativeDate,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (mark.weight > 1.0 && mark.weight != 2.0) {
                                    Surface(
                                        shape = PillShape,
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = "×${mark.weight}",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }

                                ExpressiveMarkPlaque(
                                    mark = mark,
                                    size = 40.dp,
                                    fontSize = 16.sp,
                                    cornerRadius = 12.dp,
                                    onClick = if (onMarkClick != null) { { onMarkClick(mark) } } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateMarksGroupCard(
    date: String,
    marks: List<MarkItem>,
    onMarkClick: ((MarkItem) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ru.mesh.expressive.util.DateUtils.formatRelativeDate(date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${marks.size} ${if (marks.size == 1) "оценка" else "оценки"}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                marks.forEach { mark ->
                    val isPoint = mark.isPoint || !mark.pointDate.isNullOrBlank() || mark.rawValue.endsWith(".") || mark.rawValue == "."
                    val isExpired = mark.isPointExpired
                    val formattedPointDate = if (isPoint && !mark.pointDate.isNullOrBlank()) ru.mesh.expressive.util.DateUtils.formatPointDate(mark.pointDate) else null
                    val isDark = isSystemInDarkTheme()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .then(if (onMarkClick != null) Modifier.clickable { onMarkClick(mark) } else Modifier)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = mark.subject,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (mark.isExam) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = PillShape,
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = "КР",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                            if (mark.topic.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mark.topic,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            if (isPoint) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val badgeBg = if (isExpired) {
                                    if (isDark) Color(0xFF3E1E22) else Color(0xFFFFEBEE)
                                } else {
                                    if (isDark) Color(0xFF2C2218) else Color(0xFFFFF3E0)
                                }
                                val badgeFg = if (isExpired) {
                                    if (isDark) Color(0xFFFF7C83) else Color(0xFFC62828)
                                } else {
                                    if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
                                }
                                val badgeText = when {
                                    isExpired -> "Точка истекла"
                                    !formattedPointDate.isNullOrBlank() -> "Точка до $formattedPointDate"
                                    else -> "Точка"
                                }
                                Surface(
                                    shape = PillShape,
                                    color = badgeBg
                                ) {
                                    Text(
                                        text = badgeText,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeFg
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (mark.weight > 1.0 && mark.weight != 2.0) {
                                Surface(
                                    shape = PillShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = "×${mark.weight}",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }

                            // Expressive M3 Plaque с пружинным откликом и суперскриптом веса
                            ExpressiveMarkPlaque(
                                mark = mark,
                                size = 40.dp,
                                fontSize = 16.sp,
                                cornerRadius = 12.dp,
                                onClick = if (onMarkClick != null) { { onMarkClick(mark) } } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getAttestationColors(value: String?, isDark: Boolean): Pair<Color, Color> {
    val clean = value?.trim()?.uppercase() ?: return (Color.Transparent to Color.Unspecified)
    val num = clean.toIntOrNull()
    return when {
        clean in listOf("5", "А", "З", "ЗАЧЕТ", "ЗАЧТЕНО", "АТТЕСТОВАН", "АТТ") || (num != null && num >= 5) ->
            (if (isDark) ScoreGreenContainerDark else ScoreGreenContainerLight) to (if (isDark) ScoreGreenDark else ScoreGreenLight)
        clean == "4" || (num == 4) ->
            (if (isDark) ScoreBlueContainerDark else ScoreBlueContainerLight) to (if (isDark) ScoreBlueDark else ScoreBlueLight)
        clean == "3" || (num == 3) ->
            (if (isDark) ScoreOrangeContainerDark else ScoreOrangeContainerLight) to (if (isDark) ScoreOrangeDark else ScoreOrangeLight)
        clean in listOf("2", "1", "Н/А", "Н/З", "НЕАТТЕСТОВАН", "НЕЗАЧЕТ", "НЕ АТТЕСТОВАН", "НЕ ЗАЧТЕНО") || (num != null && num <= 2) ->
            (if (isDark) ScoreRedContainerDark else ScoreRedContainerLight) to (if (isDark) ScoreRedDark else ScoreRedLight)
        clean in listOf("ОСВ", "ОСВОБОЖДЕН", "ОСВОБОЖДЕНА", "ОСВ.") ->
            (if (isDark) Color(0xFF33383F) else Color(0xFFE0E3E8)) to (if (isDark) Color(0xFFB0B8C4) else Color(0xFF5A606A))
        else ->
            (if (isDark) ScoreBlueContainerDark else ScoreBlueContainerLight) to (if (isDark) ScoreBlueDark else ScoreBlueLight)
    }
}

@Composable
fun SubjectMarksCard(
    subjectSummary: SubjectSummary,
    showWeightedGpa: Boolean = true,
    onOpenCalculator: () -> Unit,
    onMarkClick: ((MarkItem) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    val effAvg = subjectSummary.getEffectiveAverage(showWeightedGpa)
    val hasFinalMark = !subjectSummary.finalMark.isNullOrBlank()
    val finalVal = subjectSummary.finalMark.orEmpty()

    val isDark = isSystemInDarkTheme()

    val markColor = when {
        effAvg <= 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
        effAvg >= 4.5 -> if (isDark) ScoreGreenDark else ScoreGreenLight
        effAvg >= 3.6 -> if (isDark) ScoreBlueDark else ScoreBlueLight
        effAvg >= 2.7 -> if (isDark) ScoreOrangeDark else ScoreOrangeLight
        else -> if (isDark) ScoreRedDark else ScoreRedLight
    }

    val markBgColor = when {
        effAvg <= 0.0 -> MaterialTheme.colorScheme.surfaceVariant
        effAvg >= 4.5 -> if (isDark) ScoreGreenContainerDark else ScoreGreenContainerLight
        effAvg >= 3.6 -> if (isDark) ScoreBlueContainerDark else ScoreBlueContainerLight
        effAvg >= 2.7 -> if (isDark) ScoreOrangeContainerDark else ScoreOrangeContainerLight
        else -> if (isDark) ScoreRedContainerDark else ScoreRedContainerLight
    }

    val (finalBgColor, finalFgColor) = getAttestationColors(finalVal, isDark)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = ExpressiveCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = subjectSummary.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val predictedMark = if (effAvg >= 4.60) 5 else if (effAvg >= 3.60) 4 else if (effAvg >= 2.60) 3 else 2
                    val subtitleText = when {
                        hasFinalMark -> "${subjectSummary.marks.size} оценок • Четвертная оценка выставлена"
                        effAvg > 0.0 -> "${subjectSummary.marks.size} оценок • Прогноз: $predictedMark"
                        else -> "Оценок пока нет"
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasFinalMark) {
                        // Механика итоговой аттестационной оценки с иконкой Verified
                        Surface(
                            shape = PillShape,
                            color = finalBgColor,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = finalFgColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Итог: $finalVal",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = finalFgColor
                                )
                            }
                        }

                        if (effAvg > 0.0) {
                            Surface(
                                shape = PillShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%.2f", effAvg),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Текущий средний балл
                        Surface(
                            shape = PillShape,
                            color = markBgColor,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = if (effAvg > 0.0) String.format(java.util.Locale.US, "%.2f", effAvg) else "—",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = markColor
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Сводная плашка четвертных аттестаций (если есть данные периодов)
                    if (subjectSummary.periods.isNotEmpty()) {
                        Text(
                            text = "Итоговые отметки по периодам",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            subjectSummary.periods.forEach { p ->
                                val pHasFixed = !p.fixedValue.isNullOrBlank()
                                val pVal = p.fixedValue.orEmpty()
                                val (pBg, pFg) = when {
                                    pHasFixed -> getAttestationColors(pVal, isDark)
                                    p.average > 0.0 -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.surfaceContainerLowest to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = pBg
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        val pShortTitle = p.title.replace(" четверть", " ч.").replace(" семестр", " сем.")
                                        Text(
                                            text = pShortTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (pHasFixed) "$pVal" else if (p.average > 0.0) String.format(java.util.Locale.US, "%.2f", p.average) else "—",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = pFg
                                        )
                                        if (pHasFixed) {
                                            Text(
                                                text = "итог",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = pFg.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "История оценок",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        TextButton(
                            onClick = onOpenCalculator,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Калькулятор")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val sortedSubjectMarks = remember(subjectSummary.marks) {
                            subjectSummary.marks.sortedWith(
                                compareByDescending<MarkItem> { it.isPoint || !it.pointDate.isNullOrBlank() || it.rawValue.endsWith(".") }
                                    .thenByDescending { it.createdAt ?: it.date }
                            )
                        }
                        sortedSubjectMarks.forEach { mark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .then(if (onMarkClick != null) Modifier.clickable { onMarkClick(mark) } else Modifier)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val isPoint = mark.isPoint || !mark.pointDate.isNullOrBlank() || mark.rawValue.endsWith(".") || mark.rawValue == "."
                                val isExpired = mark.isPointExpired
                                val formattedPointDate = if (isPoint && !mark.pointDate.isNullOrBlank()) ru.mesh.expressive.util.DateUtils.formatPointDate(mark.pointDate) else null

                                val redPillBg = if (isDark) Color(0xFFE94255).copy(alpha = 0.22f) else Color(0xFFFFEBEE)
                                val redContent = if (isDark) Color(0xFFFF7C83) else Color(0xFFC62828)

                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = mark.topic.ifBlank { if (mark.isExam) "Контрольная работа" else "Оценка за урок" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val markTimeStr = if (!mark.createdAt.isNullOrBlank()) ru.mesh.expressive.util.DateUtils.formatRelativeDateTime(mark.createdAt) else ru.mesh.expressive.util.DateUtils.formatRelativeDate(mark.date)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isPoint) {
                                            val ptBg = if (isExpired) redPillBg else (if (isDark) Color(0xFF2C2218) else Color(0xFFFFF3E0))
                                            val ptFg = if (isExpired) redContent else (if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100))
                                            val ptText = when {
                                                isExpired -> "Точка истекла"
                                                !formattedPointDate.isNullOrBlank() -> "Точка до $formattedPointDate"
                                                else -> "Точка"
                                            }
                                            Surface(
                                                shape = PillShape,
                                                color = ptBg,
                                                modifier = Modifier.padding(end = 6.dp)
                                            ) {
                                                Text(
                                                    text = ptText,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ptFg
                                                )
                                            }
                                        }
                                        Text(
                                            text = "$markTimeStr${if (mark.weight > 1.0) " • Вес ${mark.weight}" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                ExpressiveMarkPlaque(
                                    mark = mark,
                                    size = 36.dp,
                                    fontSize = 15.sp,
                                    cornerRadius = 10.dp,
                                    onClick = if (onMarkClick != null) { { onMarkClick(mark) } } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calculateNeededFives(subject: SubjectSummary, targetGpa: Float): Int {
    val currentAvg = subject.getEffectiveAverage(true)
    if (currentAvg >= targetGpa) return 0

    val validMarks = subject.marks.filter { (!it.isPoint || it.isPointExpired) && it.effectiveValue in 2..5 }
    val (weightedSum, totalWeight) = if (validMarks.isNotEmpty()) {
        val sum = validMarks.sumOf { it.effectiveValue * it.weight }
        val weight = validMarks.sumOf { it.weight }
        sum to weight
    } else {
        (subject.averageMark * 1.0) to 1.0
    }

    val effectiveTarget = targetGpa.coerceAtMost(4.85f).toDouble()
    val denominator = 5.0 - effectiveTarget
    if (denominator <= 0.001) return 0

    val needed = ceil((effectiveTarget * totalWeight - weightedSum) / denominator).toInt()
    return needed.coerceIn(0, 99)
}

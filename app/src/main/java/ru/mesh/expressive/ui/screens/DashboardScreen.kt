package ru.mesh.expressive.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mesh.expressive.data.model.*
import ru.mesh.expressive.ui.components.ExpressiveChipSegmentedToggle
import ru.mesh.expressive.ui.components.ExpressivePullToRefreshBox
import ru.mesh.expressive.ui.components.expressiveBounceClick
import ru.mesh.expressive.ui.theme.*
import ru.mesh.expressive.ui.viewmodel.DashboardDay
import ru.mesh.expressive.ui.viewmodel.MainTab
import ru.mesh.expressive.ui.viewmodel.MeshMainViewModel

@Composable
fun DashboardScreen(
    viewModel: MeshMainViewModel,
    onNavigate: (MainTab) -> Unit
) {
    val profile by viewModel.studentProfile.collectAsState()
    val scheduleToday by viewModel.scheduleToday.collectAsState()
    val scheduleTomorrow by viewModel.scheduleTomorrow.collectAsState()
    val homeworkList by viewModel.homeworkList.collectAsState()
    val meals by viewModel.mealsBalance.collectAsState()
    val dashboardDay by viewModel.dashboardDay.collectAsState()
    val isCompactSchedule by viewModel.isCompactSchedule.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val activeGpa by viewModel.activeGpa.collectAsState()

    DashboardContent(
        profile = profile,
        scheduleToday = scheduleToday,
        scheduleTomorrow = scheduleTomorrow,
        homeworkList = homeworkList,
        meals = meals,
        dashboardDay = dashboardDay,
        isCompactSchedule = isCompactSchedule,
        isRefreshing = isRefreshing,
        activeGpa = activeGpa,
        isLoggedIn = viewModel.isLoggedIn,
        onRefresh = { viewModel.refreshData() },
        onNavigate = onNavigate,
        onSelectDashboardDay = { viewModel.selectDashboardDay(it) },
        onOpenLessonDetails = { viewModel.openLessonDetails(it) },
        onOpenHomeworkDetails = { viewModel.openHomeworkDetails(it) },
        onToggleHomework = { viewModel.toggleHomework(it) },
        vacationsCard = {
            ru.mesh.expressive.ui.components.VacationsCard(viewModel = viewModel)
        }
    )
}

@Composable
fun DashboardContent(
    profile: StudentProfile,
    scheduleToday: List<LessonScheduleItem>,
    scheduleTomorrow: List<LessonScheduleItem>,
    homeworkList: List<HomeworkItem>,
    meals: MealsBalance,
    dashboardDay: DashboardDay,
    isCompactSchedule: Boolean,
    isRefreshing: Boolean,
    activeGpa: Double,
    isLoggedIn: Boolean,
    onRefresh: () -> Unit,
    onNavigate: (MainTab) -> Unit,
    onSelectDashboardDay: (DashboardDay) -> Unit,
    onOpenLessonDetails: (LessonScheduleItem) -> Unit,
    onOpenHomeworkDetails: (HomeworkItem) -> Unit,
    onToggleHomework: (String) -> Unit,
    vacationsCard: @Composable () -> Unit = {}
) {
    val nextSchoolDayInfo = remember {
        val cal = java.util.Calendar.getInstance()
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val daysToAdd = when (dow) {
            java.util.Calendar.FRIDAY -> 3
            java.util.Calendar.SATURDAY -> 2
            java.util.Calendar.SUNDAY -> 1
            else -> 1
        }
        val targetCal = (cal.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_MONTH, daysToAdd)
        }
        val sdfIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val sdfReadable = java.text.SimpleDateFormat("d MMMM", java.util.Locale("ru"))
        val sdfTomorrow = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(
            java.util.Date(System.currentTimeMillis() + 86400000L)
        )
        val isWeekendJump = dow == java.util.Calendar.FRIDAY || dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY
        val dayTitle = if (isWeekendJump) "на понедельник" else "на завтра"
        val tomorrowFormatted = java.text.SimpleDateFormat("d MMMM", java.util.Locale("ru")).format(
            java.util.Date(System.currentTimeMillis() + 86400000L)
        )
        Triple(
            Triple(sdfIso.format(targetCal.time), sdfReadable.format(targetCal.time), sdfTomorrow),
            tomorrowFormatted,
            dayTitle
        )
    }

    val (dateTriples, tomorrowFormatted, dayTitle) = nextSchoolDayInfo
    val (targetIso, targetReadable, tomorrowIso) = dateTriples

    val tomorrowHw = remember(homeworkList, targetIso, tomorrowIso, targetReadable, tomorrowFormatted) {
        homeworkList.filter { hw ->
            !hw.isDone && (
                // 1. Задано к уроку на целевой день (на завтра / понедельник)
                (hw.rawTargetDate.isNotBlank() && (hw.rawTargetDate == targetIso || hw.rawTargetDate == tomorrowIso)) ||
                hw.targetDate == "Завтра" || hw.targetDate.equals(targetReadable, ignoreCase = true) ||
                // 2. Либо дедлайн сдачи истекает на целевой день
                (hw.rawDueDate.isNotBlank() && (hw.rawDueDate == targetIso || hw.rawDueDate == tomorrowIso)) ||
                hw.dueDate == "Завтра" || hw.dueDate.equals(targetReadable, ignoreCase = true) ||
                hw.dueDate.equals(tomorrowFormatted, ignoreCase = true)
            )
        }
    }

    val hasTomorrowHw = tomorrowHw.isNotEmpty()
    val sectionTitle = if (hasTomorrowHw) "Домашка $dayTitle" else "Ближайшие задания"
    val displayedHw = if (hasTomorrowHw) tomorrowHw.take(3) else homeworkList.filter { !it.isDone }.take(3)

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
        ) {
            // Demo Mode Banner
            if (!isLoggedIn) {
                item(key = "demo_banner") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ExpressiveCardShape)
                            .clickable { onNavigate(MainTab.AUTH) },
                        shape = ExpressiveCardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Демонстрационный режим",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "Данные вымышлены. Нажмите для входа через mos.ru",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // 1. Expressive Header
            item(key = "profile_header") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveHeroShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val initials = "${profile.firstName.firstOrNull() ?: 'М'}${profile.lastName.firstOrNull() ?: 'Э'}"
                    // 7-Sided Cookie Avatar
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(M3Cookie7Shape(7))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (profile.firstName.isNotBlank()) "Привет, ${profile.firstName}!" else "Дневник МЭШ",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (profile.className.isNotBlank() || profile.schoolName.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (profile.className.isNotBlank()) {
                                    Surface(
                                        shape = PillShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = profile.className,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                if (profile.schoolName.isNotBlank()) {
                                    Text(
                                        text = profile.schoolName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Московская электронная школа",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Overview Row: GPA & Moskvionok Balance
        item(key = "overview_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // GPA Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .expressiveBounceClick { onNavigate(MainTab.MARKS) },
                    shape = ExpressiveCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ср. балл",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = ScoreGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (activeGpa > 0.0) String.format(java.util.Locale.getDefault(), "%.2f", activeGpa) else "—",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (activeGpa > 0.0) ScoreGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = if (activeGpa > 0.0) "Текущий период" else "Оценок пока нет",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Москвёнок (Питание) Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .expressiveBounceClick { onNavigate(MainTab.MEALS) },
                    shape = ExpressiveCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Москвёнок",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icons.Default.Restaurant,
                                    contentDescription = null,
                                    tint = MoskvionokBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = String.format(java.util.Locale.getDefault(), "%.2f ₽", meals.clientBalanceRub),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = if (meals.hotMealSubscribed) "Горячее питание" else "Баланс счёта",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 4. Schedule Section with Segmented Toggle (Сегодня / Завтра)
        item(key = "schedule_header") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Расписание",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Expressive Chip Segmented Selector (Чипсины)
                    ExpressiveChipSegmentedToggle(
                        items = listOf(DashboardDay.TODAY, DashboardDay.TOMORROW),
                        selectedItem = dashboardDay,
                        onItemSelected = onSelectDashboardDay,
                        label = { when (it) { DashboardDay.TODAY -> "Сегодня"; DashboardDay.TOMORROW -> "Завтра" } }
                    )
                }
            }
        }

        val currentSchedule = if (dashboardDay == DashboardDay.TODAY) scheduleToday else scheduleTomorrow
        if (currentSchedule.isEmpty()) {
            item(key = "schedule_empty") {
                ru.mesh.expressive.ui.components.ExpressiveEmptyState(
                    title = "Здесь ничего нет",
                    subtitle = "На выбранный день уроков в расписании нет",
                    icon = Icons.Default.EventBusy
                )
            }
        } else {
            items(
                items = currentSchedule,
                key = { it.id.ifBlank { "${dashboardDay.name}_${it.lessonNumber}_${it.startTime}_${it.subject}" } },
                contentType = { "lesson" }
            ) { lesson ->
                LessonCard(
                    lesson = lesson,
                    isCompact = isCompactSchedule,
                    onClick = { onOpenLessonDetails(lesson) }
                )
            }
        }


        item(key = "homework_header") {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (homeworkList.isNotEmpty()) {
                    TextButton(onClick = { onNavigate(MainTab.HOMEWORK) }) {
                        Text("Все (${homeworkList.size})")
                    }
                }
            }
        }

        if (displayedHw.isEmpty()) {
            item(key = "homework_empty") {
                ru.mesh.expressive.ui.components.ExpressiveEmptyState(
                    title = "Здесь ничего нет",
                    subtitle = if (homeworkList.isEmpty()) "Домашних заданий не найдено" else "Все задания выполнены",
                    icon = Icons.Default.CheckCircleOutline
                )
            }
        } else {
            items(
                items = displayedHw,
                key = { it.id },
                contentType = { "homework" }
            ) { hw ->
                HomeworkCard(
                    homework = hw,
                    onClick = { onOpenHomeworkDetails(hw) },
                    onToggle = { onToggleHomework(hw.id) }
                )
            }
        }

        // Каникулы и учебные периоды внизу экрана
        item(key = "vacations_card") {
            Spacer(modifier = Modifier.height(6.dp))
            vacationsCard()
        }
    }
}
}

@Composable
fun SegmentedPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "pillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillText"
    )

    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

private fun isLessonCurrentlyOngoing(lesson: LessonScheduleItem): Boolean {
    if (lesson.isCanceled) return false
    if (lesson.isOngoing) return true
    return try {
        val now = java.util.Calendar.getInstance()
        if (lesson.date.isNotBlank()) {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(now.time)
            if (lesson.date != today) return false
        }
        val currentSeconds = now.get(java.util.Calendar.HOUR_OF_DAY) * 3600 +
            now.get(java.util.Calendar.MINUTE) * 60 +
            now.get(java.util.Calendar.SECOND)
        val startParts = lesson.startTime.split(":")
        val endParts = lesson.endTime.split(":")
        if (startParts.size >= 2 && endParts.size >= 2) {
            val startSeconds = (startParts[0].trim().toInt() * 60 + startParts[1].trim().toInt()) * 60
            val endSeconds = (endParts[0].trim().toInt() * 60 + endParts[1].trim().toInt()) * 60
            currentSeconds in startSeconds until endSeconds
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}

private fun calculateLessonProgress(startTime: String, endTime: String): Float {
    return try {
        val now = java.util.Calendar.getInstance()
        val currentSeconds = now.get(java.util.Calendar.HOUR_OF_DAY) * 3600 +
            now.get(java.util.Calendar.MINUTE) * 60 +
            now.get(java.util.Calendar.SECOND)
        val startParts = startTime.split(":")
        val endParts = endTime.split(":")
        if (startParts.size >= 2 && endParts.size >= 2) {
            val startSeconds = (startParts[0].trim().toInt() * 60 + startParts[1].trim().toInt()) * 60
            val endSeconds = (endParts[0].trim().toInt() * 60 + endParts[1].trim().toInt()) * 60
            if (endSeconds > startSeconds) {
                return ((currentSeconds - startSeconds).toFloat() / (endSeconds - startSeconds).toFloat())
                    .coerceIn(0f, 1f)
            }
        }
        0f
    } catch (_: Exception) {
        0f
    }
}

@Composable
private fun rememberLessonProgress(startTime: String, endTime: String): Float {
    var rawProgress by remember(startTime, endTime) {
        mutableFloatStateOf(calculateLessonProgress(startTime, endTime))
    }
    LaunchedEffect(startTime, endTime) {
        while (true) {
            rawProgress = calculateLessonProgress(startTime, endTime)
            kotlinx.coroutines.delay(10_000L)
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "lesson_progress"
    )
    return animatedProgress
}

@Composable
fun LessonCard(
    lesson: LessonScheduleItem,
    isCompact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    var isOngoing by remember(lesson.id, lesson.date, lesson.startTime, lesson.endTime, lesson.isOngoing, lesson.isCanceled) {
        mutableStateOf(isLessonCurrentlyOngoing(lesson))
    }
    LaunchedEffect(lesson.id, lesson.date, lesson.startTime, lesson.endTime, lesson.isOngoing, lesson.isCanceled) {
        while (true) {
            isOngoing = isLessonCurrentlyOngoing(lesson)
            kotlinx.coroutines.delay(10_000L)
        }
    }
    if (isCompact) {
        val compactShape = RoundedCornerShape(14.dp)
        val compactColor = if (isOngoing)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(compactShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            shape = compactShape,
            colors = CardDefaults.cardColors(
                containerColor = if (isOngoing)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (isOngoing) {
                    val progress = rememberLessonProgress(lesson.startTime, lesson.endTime)
                    Box(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = progress)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        )
                    }
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = progress)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)
                                    )
                            )
                        }
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (lesson.isOngoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${lesson.lessonNumber}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (lesson.isOngoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${lesson.startTime}–${lesson.endTime}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (lesson.room.isNotBlank() && lesson.room != "—") {
                            Text(
                                text = " • ${lesson.room}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = lesson.subject,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val hasMark = lesson.mark != null || lesson.isPoint || !lesson.rawMark.isNullOrBlank()
                    if (hasMark) {
                        val isPt = lesson.isPoint || lesson.rawMark?.endsWith(".") == true || lesson.rawMark == "."
                        val markText = when {
                            !lesson.rawMark.isNullOrBlank() -> lesson.rawMark!!
                            isPt -> if (lesson.mark != null) "${lesson.mark}." else "•"
                            lesson.mark != null -> "${lesson.mark}"
                            else -> "•"
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isPt) ScoreOrangeContainer else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = markText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isPt) ScoreOrange else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (!lesson.homework.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ДЗ: ${lesson.homework}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    } else {
        // Standard view: cabinet moved to time row, teacher removed completely
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ExpressiveCardShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            shape = ExpressiveCardShape,
            colors = CardDefaults.cardColors(
                containerColor = if (isOngoing)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (isOngoing) {
                    val progress = rememberLessonProgress(lesson.startTime, lesson.endTime)
                    Box(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = progress)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        )
                    }
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.5.dp)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = progress)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)
                                    )
                            )
                        }
                    }
                }
                Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (lesson.isOngoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${lesson.lessonNumber}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (lesson.isOngoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        val timeAndRoom = buildString {
                            append("${lesson.startTime} – ${lesson.endTime}")
                            if (lesson.room.isNotBlank() && lesson.room != "—") {
                                append(" • ${lesson.room}")
                            }
                        }
                        Text(
                            text = timeAndRoom,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val hasDetailedMark = lesson.mark != null || lesson.isPoint || !lesson.rawMark.isNullOrBlank()
                    if (hasDetailedMark) {
                        val isPt = lesson.isPoint || lesson.rawMark?.endsWith(".") == true || lesson.rawMark == "."
                        val markText = when {
                            !lesson.rawMark.isNullOrBlank() -> lesson.rawMark!!
                            isPt -> if (lesson.mark != null) "${lesson.mark}." else "•"
                            lesson.mark != null -> "${lesson.mark}"
                            else -> "•"
                        }
                        val prefix = if (isPt) "Точка: " else "Оценка: "
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isPt) ScoreOrangeContainer else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$prefix$markText${if (lesson.markWeight > 1.0) " (вес ${lesson.markWeight.toInt()})" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPt) ScoreOrange else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lesson.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (lesson.isOngoing) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "ИДЕТ СЕЙЧАС",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                if (!lesson.homework.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ДЗ: ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = lesson.homework,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
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
fun HomeworkCard(
    homework: HomeworkItem,
    onClick: () -> Unit = {},
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveBounceClick(onClick = onClick),
        shape = ExpressiveCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (homework.isDone)
                MaterialTheme.colorScheme.surfaceContainerLowest
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = homework.isDone,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = homework.subject,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val hasDifferentDates = homework.rawTargetDate.isNotBlank() && homework.rawDueDate.isNotBlank() && homework.rawTargetDate != homework.rawDueDate
                    val dateBadgeText = if (hasDifferentDates) {
                        "На ${homework.targetDate} • До ${homework.dueDate}"
                    } else {
                        val displayDate = if (homework.targetDate.isNotBlank()) homework.targetDate else homework.dueDate
                        if (displayDate.isNotBlank()) displayDate else ""
                    }
                    if (dateBadgeText.isNotBlank()) {
                        val isUrgent = homework.dueDate == "Завтра" || homework.targetDate == "Завтра" || homework.dueDate == "Сегодня"
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = PillShape,
                            color = if (isUrgent) ScoreOrangeContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = dateBadgeText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isUrgent) ScoreOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                }
                Text(
                    text = homework.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (homework.isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (homework.hasDigitalTest) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Цифровой тест ЦДЗ",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_8")
@Composable
fun DashboardScreenPreview() {
    MeshExpressiveTheme(dynamicColor = false) {
        DashboardContent(
            profile = StudentProfile(
                firstName = "Александр",
                lastName = "Смирнов",
                className = "10 \"А\"",
                schoolName = "Школа № 1502",
                gpa = 4.82
            ),
            scheduleToday = listOf(
                LessonScheduleItem(
                    id = "1",
                    subject = "Алгебра и начала анализа",
                    room = "304",
                    startTime = "08:30",
                    endTime = "09:15",
                    teacherName = "Иванова Е. В.",
                    isOngoing = false
                ),
                LessonScheduleItem(
                    id = "2",
                    subject = "Информатика",
                    room = "214",
                    startTime = "09:30",
                    endTime = "10:15",
                    teacherName = "Петров С. А.",
                    isOngoing = true
                ),
                LessonScheduleItem(
                    id = "3",
                    subject = "Физика",
                    room = "108",
                    startTime = "10:30",
                    endTime = "11:15",
                    teacherName = "Сидорова М. Н.",
                    isOngoing = false
                )
            ),
            scheduleTomorrow = emptyList(),
            homeworkList = listOf(
                HomeworkItem(
                    id = "101",
                    subject = "Алгебра",
                    description = "Параграф 14, № 14.1 - 14.8 решить в тетради",
                    dueDate = "Завтра",
                    isDone = false
                ),
                HomeworkItem(
                    id = "102",
                    subject = "Физика",
                    description = "Подготовиться к лабораторной работе №3",
                    dueDate = "Завтра",
                    isDone = false
                )
            ),
            meals = MealsBalance(
                clientBalanceRub = 450.50
            ),
            dashboardDay = DashboardDay.TODAY,
            isCompactSchedule = false,
            isRefreshing = false,
            activeGpa = 4.82,
            isLoggedIn = true,
            onRefresh = {},
            onNavigate = {},
            onSelectDashboardDay = {},
            onOpenLessonDetails = {},
            onOpenHomeworkDetails = {},
            onToggleHomework = {},
            vacationsCard = {}
        )
    }
}

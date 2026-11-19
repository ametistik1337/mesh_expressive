package ru.mesh.expressive.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mesh.expressive.data.model.HomeworkItem
import ru.mesh.expressive.ui.components.ExpressiveEmptyState
import ru.mesh.expressive.ui.components.ExpressivePullToRefreshBox
import ru.mesh.expressive.ui.components.M3ConnectedButtonGroup
import ru.mesh.expressive.ui.components.expressiveBounceClick
import ru.mesh.expressive.ui.theme.*
import ru.mesh.expressive.ui.viewmodel.MeshMainViewModel

@Composable
fun HomeworkScreen(viewModel: MeshMainViewModel) {
    val haptic = LocalHapticFeedback.current
    val homeworkList by viewModel.homeworkList.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var filter by remember { mutableStateOf("На завтра") }
    val filters = listOf("На завтра", "Все", "Невыполненные", "с ЦДЗ")

    val tomorrowDateFormatted = remember {
        java.text.SimpleDateFormat("d MMMM", java.util.Locale("ru")).format(java.util.Date(System.currentTimeMillis() + 86400000L))
    }

    val nextSchoolDateInfo = remember {
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
        Triple(sdfIso.format(targetCal.time), sdfReadable.format(targetCal.time), sdfTomorrow)
    }

    val hideCompletedHomework by viewModel.hideCompletedHomework.collectAsState()

    val filteredList = remember(homeworkList, filter, tomorrowDateFormatted, nextSchoolDateInfo, hideCompletedHomework) {
        val baseList = if (hideCompletedHomework) homeworkList.filter { !it.isDone } else homeworkList
        when (filter) {
            "На завтра" -> {
                val (targetIso, targetReadable, tomorrowIso) = nextSchoolDateInfo
                baseList.filter {
                    // 1. Задано к уроку на целевой день (на завтра / понедельник)
                    (it.rawTargetDate.isNotBlank() && (it.rawTargetDate == targetIso || it.rawTargetDate == tomorrowIso)) ||
                    it.targetDate == "Завтра" || it.targetDate.equals(targetReadable, ignoreCase = true) ||
                    // 2. Либо дедлайн сдачи истекает на целевой день
                    (it.rawDueDate.isNotBlank() && (it.rawDueDate == targetIso || it.rawDueDate == tomorrowIso)) ||
                    it.dueDate == "Завтра" || it.dueDate.equals(targetReadable, ignoreCase = true) ||
                    it.dueDate.equals(tomorrowDateFormatted, ignoreCase = true)
                }
            }
            "Невыполненные" -> baseList.filter { !it.isDone }
            "с ЦДЗ" -> baseList.filter { it.hasDigitalTest }
            else -> baseList
        }
    }

    val totalCount = homeworkList.size

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshData() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)
        ) {
            // Compact Header
            item(key = "homework_header") {
                val uncompletedCount = remember(homeworkList) { homeworkList.count { !it.isDone } }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Домашние задания",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val taskText = when {
                            uncompletedCount == 0 && totalCount > 0 -> "Все задания выполнены"
                            totalCount == 0 -> "Нет активных заданий"
                            uncompletedCount % 10 == 1 && uncompletedCount % 100 != 11 -> "$uncompletedCount осталось сдать"
                            uncompletedCount % 10 in 2..4 && uncompletedCount % 100 !in 12..14 -> "$uncompletedCount осталось сдать"
                            else -> "$uncompletedCount осталось сдать"
                        }
                        Text(
                            text = taskText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (totalCount > 0) {
                        Surface(
                            shape = PillShape,
                            color = if (uncompletedCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = if (uncompletedCount > 0) "$uncompletedCount из $totalCount" else "$totalCount",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (uncompletedCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Filters M3 Connected Button Group
            item(key = "homework_filters") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    M3ConnectedButtonGroup(
                        items = filters,
                        selectedItem = filter,
                        onItemSelected = { filter = it },
                        label = { it },
                        showCheckmark = true,
                        scrollable = true
                    )
                }
            }

            // List of Homework Items
            if (filteredList.isEmpty()) {
                item {
                    val emptySubtitle = when (filter) {
                        "На завтра" -> "На ближайший учебный день заданий не найдено"
                        "Невыполненные" -> "Все домашние задания выполнены!"
                        "с ЦДЗ" -> "Заданий с ЦДЗ не найдено"
                        else -> "Домашние задания отсутствуют"
                    }
                    ExpressiveEmptyState(
                        title = "Здесь ничего нет",
                        subtitle = emptySubtitle,
                        icon = if (filter == "Невыполненные") Icons.Default.CheckCircleOutline else Icons.Default.Inbox
                    )
                }
            } else {
                items(filteredList, key = { it.id }) { hw ->
                    val hwColor = if (hw.isDone)
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    else
                        MaterialTheme.colorScheme.surfaceContainerLow

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = hwColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Leading interactive Checkbox
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleHomework(hw.id)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (hw.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = if (hw.isDone) "Выполнено" else "Отметить",
                                    tint = if (hw.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Main homework content
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.openHomeworkDetails(hw) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = hw.subject,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hw.isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    val isUrgent = hw.dueDate == "Завтра" || hw.targetDate == "Завтра" || hw.dueDate == "Сегодня"
                                    val displayDate = if (hw.targetDate.isNotBlank()) hw.targetDate else hw.dueDate
                                    val dateBadgeText = if (displayDate.isNotBlank()) displayDate else "Срок"

                                    Surface(
                                        shape = PillShape,
                                        color = if (isUrgent) ScoreOrangeContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Text(
                                            text = dateBadgeText,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isUrgent) ScoreOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = hw.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (hw.isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                )

                                if (hw.hasDigitalTest) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        shape = PillShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Тест в ЦДЗ",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
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
}

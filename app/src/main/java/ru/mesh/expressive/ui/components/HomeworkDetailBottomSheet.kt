package ru.mesh.expressive.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import ru.mesh.expressive.ui.theme.ExpressiveCardShape
import ru.mesh.expressive.ui.theme.PillShape
import ru.mesh.expressive.ui.viewmodel.MeshMainViewModel
import ru.mesh.expressive.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkDetailBottomSheet(viewModel: MeshMainViewModel) {
    val selectedHw by viewModel.selectedHomeworkForDetails.collectAsState()

    if (selectedHw != null) {
        val hw = selectedHw!!
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeHomeworkDetails() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            windowInsets = WindowInsets(0, 0, 0, 0),
            shape = ExpressiveCardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (hw.isDone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = null,
                                tint = if (hw.isDone) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hw.subject,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val targetDisplay = if (hw.targetDate.isNotBlank()) hw.targetDate else hw.dueDate
                        val dueDisplay = if (hw.dueDate.isNotBlank()) hw.dueDate else targetDisplay
                        val hasDiff = hw.rawTargetDate.isNotBlank() && hw.rawDueDate.isNotBlank() && hw.rawTargetDate != hw.rawDueDate
                        if (hasDiff) {
                            Text(
                                text = "На урок: $targetDisplay • Истекает: $dueDisplay",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (targetDisplay.isNotBlank()) {
                            Text(
                                text = "Срок сдачи: $targetDisplay",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        shape = PillShape,
                        color = if (hw.isDone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = if (hw.isDone) "Выполнено" else "Не выполнено",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (hw.isDone) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Assignment Description Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveCardShape,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Задание",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = hw.description.ifBlank { "Описание задания отсутствует" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Info card: На какой день задано и когда истекает
                val assignedRaw = hw.assignedDate.ifBlank { hw.date }
                val assignedFormatted = DateUtils.formatRelativeDateTime(assignedRaw)
                val targetText = if (hw.targetDate.isNotBlank()) hw.targetDate else hw.rawTargetDate
                val dueText = if (hw.dueDate.isNotBlank()) hw.dueDate else hw.rawDueDate

                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = ExpressiveCardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (targetText.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Задано на урок: $targetText",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (dueText.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Срок сдачи (истекает): $dueText",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (assignedFormatted.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Выдано учителем: $assignedFormatted",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Digital test / attached material
                if (hw.hasDigitalTest || !hw.digitalTestUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveCardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        Icons.Default.Quiz,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Цифровой тест",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Задание из библиотеки МЭШ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        val url = hw.digitalTestUrl ?: "https://uchebnik.mos.ru"
                                        viewModel.openTestExecution(url, "${hw.subject}: Тест")
                                    },
                                    shape = PillShape
                                ) {
                                    Text("Выполнить", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // Файлы от учителя
                val allTeacherFiles = (hw.teacherFiles + hw.attachments.map { att ->
                    val cleanTitle = att.name.ifBlank { "Файл задания" }
                    val ext = cleanTitle.substringAfterLast('.', "").lowercase()
                    val cleanTypeName = when (ext) {
                        "pdf" -> "PDF документ"
                        "odt" -> "Документ ODT"
                        "doc", "docx" -> "Документ Word"
                        "ppt", "pptx" -> "Презентация PowerPoint"
                        "xls", "xlsx" -> "Таблица Excel"
                        "zip", "rar", "7z" -> "Архив"
                        "png", "jpg", "jpeg" -> "Изображение"
                        else -> "Файл задания"
                    }
                    ru.mesh.expressive.data.model.LessonMaterialItem(
                        title = cleanTitle,
                        typeName = cleanTypeName,
                        url = att.url,
                        isFile = true
                    )
                }).distinctBy { it.url ?: it.title }

                if (allTeacherFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TeacherAttachmentsSection(
                        files = allTeacherFiles,
                        viewModel = viewModel
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status toggle button
                Button(
                    onClick = {
                        viewModel.toggleHomework(hw.id)
                        viewModel.closeHomeworkDetails()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hw.isDone) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (hw.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        if (hw.isDone) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hw.isDone) "Вернуть в невыполненные" else "Отметить выполненным",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

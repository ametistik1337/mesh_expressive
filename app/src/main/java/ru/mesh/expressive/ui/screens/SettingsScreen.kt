package ru.mesh.expressive.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mesh.expressive.ui.theme.*
import ru.mesh.expressive.ui.viewmodel.MeshMainViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MeshMainViewModel,
    isMonetEnabled: Boolean,
    onToggleMonet: (Boolean) -> Unit,
    onNavigateToAuth: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val profile by viewModel.studentProfile.collectAsState()
    val isCompactSchedule by viewModel.isCompactSchedule.collectAsState()
    val showWeightedGpa by viewModel.showWeightedGpa.collectAsState()
    val hideCompletedQuests by viewModel.hideCompletedQuests.collectAsState()
    val enableSpringPhysics by viewModel.enableSpringPhysics.collectAsState()
    val hideEmptyScheduleDays by viewModel.hideEmptyScheduleDays.collectAsState()
    val gpaTargetScore by viewModel.gpaTargetScore.collectAsState()
    val autoRefreshMinutes by viewModel.autoRefreshMinutes.collectAsState()
    val gamification by viewModel.gamificationProfile.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isAmoledDark by viewModel.isAmoledDark.collectAsState()
    val studyPeriodMode by viewModel.studyPeriodMode.collectAsState()
    val startTab by viewModel.startTab.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val hideCompletedHomework by viewModel.hideCompletedHomework.collectAsState()
    val isAutoUpdatesAndCounterEnabled by viewModel.isAutoUpdatesAndCounterEnabled.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val notifyConfig by viewModel.marksNotifyConfig.collectAsState()

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.updateMarksNotifyConfig(context) { it.copy(enabled = true) }
        } else {
            Toast.makeText(context, "Разрешение на уведомления не выдано", Toast.LENGTH_SHORT).show()
        }
    }

    var showTokenDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showClassInfoDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var isAvatarLoading by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    isAvatarLoading = true
                    viewModel.uploadAvatar(bytes, "avatar.jpg") {
                        isAvatarLoading = false
                    }
                }
            } catch (_: Exception) {
                isAvatarLoading = false
            }
        }
    }

    val settingsTabs = remember {
        listOf(
            "GENERAL" to "Общее",
            "APPEARANCE" to "Вид",
            "STUDY" to "Учёба",
            "NOTIFICATIONS" to "Уведомления"
        )
    }
    var selectedSettingsTab by remember { mutableStateOf(settingsTabs.first()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            ru.mesh.expressive.ui.components.M3ConnectedButtonGroup(
                items = settingsTabs,
                selectedItem = selectedSettingsTab,
                onItemSelected = { selectedSettingsTab = it },
                label = { it.second },
                showCheckmark = true,
                scrollable = true
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp)
        ) {
        if (selectedSettingsTab.first == "GENERAL") {
        // 1. Account & Profile Card (M3 Expressive Segmented Hero)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clickable { showAvatarDialog = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .align(Alignment.Center)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!profile.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = profile.avatarUrl,
                                        contentDescription = "Аватар",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    val initials = "${profile.firstName.firstOrNull() ?: 'М'}${profile.lastName.firstOrNull() ?: 'Э'}"
                                    Text(
                                        text = initials,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.BottomEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Сменить аватар",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (viewModel.isLoggedIn) "${profile.firstName} ${profile.lastName}" else "Демо-режим",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (viewModel.isLoggedIn) "${profile.className} • ${profile.schoolName}" else "Локальные демо-данные",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        ru.mesh.expressive.ui.components.ExpressiveIconButton(
                            tooltipText = if (viewModel.isLoggedIn) "Выйти из аккаунта" else "Войти",
                            onClick = {
                                if (viewModel.isLoggedIn) {
                                    viewModel.logout()
                                } else {
                                    onNavigateToAuth()
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (viewModel.isLoggedIn) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login,
                                contentDescription = if (viewModel.isLoggedIn) "Выйти" else "Войти",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Нижний сегмент: быстрые действия профиля в стиле M3 Connected Group
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        onClick = { showAvatarDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(
                            topStart = 6.dp,
                            topEnd = 6.dp,
                            bottomStart = 24.dp,
                            bottomEnd = if (viewModel.isLoggedIn) 6.dp else 24.dp
                        ),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Аватар", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }

                    if (viewModel.isLoggedIn) {
                        Surface(
                            onClick = { showClassInfoDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Класс", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }

                        Surface(
                            onClick = { showTokenDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Токен", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // 2. Privacy & Security Banner (Monet tonal)
        item {
            ExpressiveSegmentedCard(index = 0, count = 1, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsIconBadge(icon = Icons.Default.Shield, accent = true)

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Приватность 100% — Чистый стек",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "0 трекеров, 0 фонового GPS, прямой TLS к school.mos.ru",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        } // end GENERAL top

        if (selectedSettingsTab.first == "APPEARANCE") {
        // 3. Section: Внешний вид и интерфейс
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSectionHeader("Внешний вид и интерфейс")
                ExpressiveSegmentedCard(index = 0, count = 5) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconBadge(icon = Icons.Default.DarkMode)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Тема оформления", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Светлый, тёмный или системный режим", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val themeOptions = remember {
                        listOf("SYSTEM" to "Системная", "DARK" to "Тёмная", "LIGHT" to "Светлая")
                    }
                    val selectedThemeOption = themeOptions.find { it.first == themeMode } ?: themeOptions.first()
                    ru.mesh.expressive.ui.components.M3ConnectedButtonGroup(
                        items = themeOptions,
                        selectedItem = selectedThemeOption,
                        onItemSelected = { viewModel.setThemeMode(it.first) },
                        label = { it.second },
                        showCheckmark = true,
                        scrollable = false
                    )
                }
                ExpressiveSegmentedCard(index = 1, count = 5) {
                    SettingsToggleRow(
                        icon = Icons.Default.Contrast,
                        title = "AMOLED чёрный",
                        subtitle = "Чистый чёрный фон (#000000) для OLED-экранов в тёмной теме",
                        isChecked = isAmoledDark,
                        onCheckedChange = { viewModel.toggleAmoledDark(it) }
                    )
                }
                ExpressiveSegmentedCard(index = 2, count = 5) {
                    SettingsToggleRow(
                        icon = Icons.Default.Palette,
                        title = "Monet Dynamic Color",
                        subtitle = "Адаптивные цвета под обои устройства (Material You)",
                        isChecked = isMonetEnabled,
                        accentIcon = true,
                        onCheckedChange = onToggleMonet
                    )
                }
                ExpressiveSegmentedCard(index = 3, count = 5) {
                    SettingsToggleRow(
                        icon = Icons.Default.TouchApp,
                        title = "Пружинная физика анимаций",
                        subtitle = "Эффект упругости Material Expressive при нажатиях",
                        isChecked = enableSpringPhysics,
                        onCheckedChange = { viewModel.toggleEnableSpringPhysics(it) }
                    )
                }
                ExpressiveSegmentedCard(index = 4, count = 5) {
                    SettingsToggleRow(
                        icon = Icons.Default.Vibration,
                        title = "Тактильный отклик (Вибрация)",
                        subtitle = "Мягкий виброотклик при переключении экранов и действий",
                        isChecked = hapticFeedbackEnabled,
                        onCheckedChange = { viewModel.toggleHapticFeedback(it) }
                    )
                }
            }
        }

        // Section: Навигация и стартовый экран
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSectionHeader("Навигация и экран запуска")
                ExpressiveSegmentedCard(index = 0, count = 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconBadge(icon = Icons.Default.Home)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Стартовый экран", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Какой раздел открывать первым при входе в приложение", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val startTabOptions = remember {
                        listOf(
                            "DASHBOARD" to "Главная",
                            "SCHEDULE" to "Расписание",
                            "HOMEWORK" to "Задания",
                            "MARKS" to "Оценки"
                        )
                    }
                    val selectedStartTabOption = startTabOptions.find { it.first == startTab } ?: startTabOptions.first()
                    ru.mesh.expressive.ui.components.M3ConnectedButtonGroup(
                        items = startTabOptions,
                        selectedItem = selectedStartTabOption,
                        onItemSelected = { viewModel.setStartTab(it.first) },
                        label = { it.second },
                        showCheckmark = true,
                        scrollable = true
                    )
                }
            }
        }

        } // end APPEARANCE

        if (selectedSettingsTab.first == "STUDY") {
        // Section: Расписание и Домашние задания
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSectionHeader("Расписание и Задания")
                ExpressiveSegmentedCard(index = 0, count = 3) {
                    SettingsToggleRow(
                        icon = Icons.Default.ViewAgenda,
                        title = "Компактный вид расписания",
                        subtitle = "Уменьшенные карточки уроков для плотного отображения",
                        isChecked = isCompactSchedule,
                        onCheckedChange = { viewModel.toggleCompactSchedule(it) }
                    )
                }
                ExpressiveSegmentedCard(index = 1, count = 3) {
                    SettingsToggleRow(
                        icon = Icons.Default.EventBusy,
                        title = "Скрывать пустые дни",
                        subtitle = "Не показывать в полоске дни без запланированных уроков",
                        isChecked = hideEmptyScheduleDays,
                        onCheckedChange = { viewModel.toggleHideEmptyScheduleDays(it) }
                    )
                }
                ExpressiveSegmentedCard(index = 2, count = 3) {
                    SettingsToggleRow(
                        icon = Icons.Default.CheckCircleOutline,
                        title = "Скрывать сделанную домашку",
                        subtitle = "Не показывать уже выполненные домашние задания в списке",
                        isChecked = hideCompletedHomework,
                        onCheckedChange = { viewModel.toggleHideCompletedHomework(it) }
                    )
                }
            }
        }

        // 4. Section: Оценки и Успеваемость
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSectionHeader("Оценки и Цели успеваемости")
                ExpressiveSegmentedCard(index = 0, count = 3) {
                    SettingsToggleRow(
                        icon = Icons.Default.Calculate,
                        title = "Средневзвешенный балл",
                        subtitle = "Учитывать коэффициенты веса контрольных и экзаменов",
                        isChecked = showWeightedGpa,
                        accentIcon = true,
                        onCheckedChange = { viewModel.toggleShowWeightedGpa(it) }
                    )
                }
                ExpressiveSegmentedCard(index = 1, count = 3) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconBadge(icon = Icons.Default.CalendarMonth)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Оценочные периоды", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Автоопределение по API МЭШ или ручной выбор", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val periodOptions = remember {
                        listOf(
                            "AUTO" to "Авто",
                            "SEMESTERS" to "Полугодия",
                            "QUARTERS" to "Четверти",
                            "TRIMESTERS" to "Триместры",
                            "MODULES" to "Модули"
                        )
                    }
                    val selectedPair = periodOptions.find { it.first == studyPeriodMode } ?: periodOptions.first()
                    ru.mesh.expressive.ui.components.M3ConnectedButtonGroup(
                        items = periodOptions,
                        selectedItem = selectedPair,
                        onItemSelected = { viewModel.setStudyPeriodMode(it.first) },
                        label = { it.second },
                        scrollable = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ExpressiveSegmentedCard(index = 2, count = 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingsIconBadge(icon = Icons.Default.Grade)
                            Spacer(modifier = Modifier.width(14.dp))
                            Text("Целевой средний балл", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", gpaTargetScore),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = gpaTargetScore,
                        onValueChange = { viewModel.setGpaTargetScore((it * 100).roundToInt() / 100f) },
                        valueRange = 4.00f..5.00f,
                        steps = 9
                    )
                }
            }
        }

        // 5. Section: Звезды и Автоматизация
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSectionHeader("Звезды и Автоматизация")
                ExpressiveSegmentedCard(index = 0, count = 2) {
                    SettingsToggleRow(
                        icon = Icons.Default.Star,
                        title = "Режим бесконечных звезд",
                        subtitle = "Разблокирует все визуальные награды профиля (Dev)",
                        isChecked = gamification.infiniteStarsOverride,
                        onCheckedChange = { viewModel.toggleInfiniteStars(it) }
                    )
                }
                ExpressiveSegmentedCard(index = 1, count = 2) {
                    SettingsToggleRow(
                        icon = Icons.Default.CheckCircleOutline,
                        title = "Скрывать сданные задания",
                        subtitle = "Оставлять в списке только активные задания за звезды",
                        isChecked = hideCompletedQuests,
                        onCheckedChange = { viewModel.toggleHideCompletedQuests(it) }
                    )
                }
            }
        }

        } // end STUDY

        if (selectedSettingsTab.first == "NOTIFICATIONS") {
        // 5.5. Section: Уведомления об оценках
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSectionHeader("Уведомления об оценках")
                ExpressiveSegmentedCard(index = 0, count = 1) {
                    SettingsToggleRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Уведомления об оценках",
                        subtitle = "Фоновый мониторинг выставления, исправлений и точек",
                        isChecked = notifyConfig.enabled,
                        accentIcon = true,
                        onCheckedChange = { enable ->
                            if (enable && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                !ru.mesh.expressive.util.MarksNotificationManager.hasNotificationPermission(context)
                            ) {
                                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.updateMarksNotifyConfig(context) { it.copy(enabled = enable) }
                            }
                        }
                    )
                }
            }
        }

        if (notifyConfig.enabled) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsSectionHeader("События")
                    ExpressiveSegmentedCard(index = 0, count = 5) {
                        SettingsToggleRow(
                            icon = Icons.Default.AddTask,
                            title = "Новые оценки",
                            subtitle = "При выставлении новой оценки в журнал",
                            isChecked = notifyConfig.notifyNew,
                            onCheckedChange = { v -> viewModel.updateMarksNotifyConfig(context) { it.copy(notifyNew = v) } }
                        )
                    }
                    ExpressiveSegmentedCard(index = 1, count = 5) {
                        SettingsToggleRow(
                            icon = Icons.Default.EditNote,
                            title = "Исправления и изменения",
                            subtitle = "Когда оценка изменена или закрыта точка (2. → 5*)",
                            isChecked = notifyConfig.notifyChanged,
                            onCheckedChange = { v -> viewModel.updateMarksNotifyConfig(context) { it.copy(notifyChanged = v) } }
                        )
                    }
                    ExpressiveSegmentedCard(index = 2, count = 5) {
                        SettingsToggleRow(
                            icon = Icons.Default.FiberManualRecord,
                            title = "Выставление точки",
                            subtitle = "Уведомлять о новой точке и дате её сдачи",
                            isChecked = notifyConfig.notifyPoint,
                            onCheckedChange = { v -> viewModel.updateMarksNotifyConfig(context) { it.copy(notifyPoint = v) } }
                        )
                    }
                    ExpressiveSegmentedCard(index = 3, count = 5) {
                        SettingsToggleRow(
                            icon = Icons.Default.Alarm,
                            title = "Истекающие точки",
                            subtitle = "Напоминание за 2 дня до дедлайна сдачи",
                            isChecked = notifyConfig.notifyPointExpiring,
                            onCheckedChange = { v -> viewModel.updateMarksNotifyConfig(context) { it.copy(notifyPointExpiring = v) } }
                        )
                    }
                    ExpressiveSegmentedCard(index = 4, count = 5) {
                        SettingsToggleRow(
                            icon = Icons.Default.Verified,
                            title = "Итоговые оценки",
                            subtitle = "При выставлении оценки за четверть или год",
                            isChecked = notifyConfig.notifyFinal,
                            onCheckedChange = { v -> viewModel.updateMarksNotifyConfig(context) { it.copy(notifyFinal = v) } }
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsSectionHeader("Фильтр по баллам")
                    ExpressiveSegmentedCard(index = 0, count = 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(5, 4, 3, 2).forEach { markVal ->
                                val selected = markVal in notifyConfig.notifyValues
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        viewModel.updateMarksNotifyConfig(context) { cfg ->
                                            val updated = if (selected) cfg.notifyValues - markVal else cfg.notifyValues + markVal
                                            cfg.copy(notifyValues = updated)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "$markVal",
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    },
                                    shape = PillShape,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsSectionHeader("Формат и расписание")
                    ExpressiveSegmentedCard(index = 0, count = 5) {
                        SettingsToggleRow(
                            icon = Icons.Default.Subject,
                            title = "Детали работы и вес",
                            subtitle = "Показывать форму контроля, тему и вес оценки",
                            isChecked = notifyConfig.showDetails,
                            onCheckedChange = { v -> viewModel.updateMarksNotifyConfig(context) { it.copy(showDetails = v) } }
                        )
                    }
                    ExpressiveSegmentedCard(index = 1, count = 5) {
                        SettingsToggleRow(
                            icon = Icons.Default.TrendingUp,
                            title = "Сдвиг среднего балла",
                            subtitle = "Показывать изменение балла предмета (4.33 → 4.50)",
                            isChecked = notifyConfig.showGpaDelta,
                            onCheckedChange = { v -> viewModel.updateMarksNotifyConfig(context) { it.copy(showGpaDelta = v) } }
                        )
                    }
                    ExpressiveSegmentedCard(index = 2, count = 5) {
                        SettingsToggleRow(
                            icon = Icons.Default.NightsStay,
                            title = "Тихие часы (22:00 – 08:00)",
                            subtitle = "Не присылать уведомления ночью",
                            isChecked = notifyConfig.quietHours,
                            onCheckedChange = { v -> viewModel.updateMarksNotifyConfig(context) { it.copy(quietHours = v) } }
                        )
                    }
                    ExpressiveSegmentedCard(index = 3, count = 5) {
                        Text(
                            text = "Частота проверки ленты событий МЭШ",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        val intervals = remember {
                            listOf(1 to "1 м", 5 to "5 м", 15 to "15 м", 30 to "30 м")
                        }
                        val selectedInterval = intervals.find { it.first == notifyConfig.intervalMin } ?: intervals[1]
                        ru.mesh.expressive.ui.components.M3ConnectedButtonGroup(
                            items = intervals,
                            selectedItem = selectedInterval,
                            onItemSelected = { opt ->
                                viewModel.updateMarksNotifyConfig(context) { it.copy(intervalMin = opt.first) }
                            },
                            label = { it.second },
                            showCheckmark = false,
                            scrollable = false
                        )
                    }
                    ExpressiveSegmentedCard(index = 4, count = 5) {
                        FilledTonalButton(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                    !ru.mesh.expressive.util.MarksNotificationManager.hasNotificationPermission(context)
                                ) {
                                    notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.sendTestMarksNotification(context)
                                    Toast.makeText(context, "Тестовое уведомление отправлено", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = PillShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Отправить тестовое уведомление")
                        }
                    }
                }
            }
        }

        } // end NOTIFICATIONS

        if (selectedSettingsTab.first == "GENERAL") {
        // 6. Section: Синхронизация и обновления
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSectionHeader("Обновления и синхронизация")
                ExpressiveSegmentedCard(index = 0, count = 2) {
                    SettingsActionRow(
                        icon = Icons.Default.SystemUpdate,
                        title = if (isCheckingUpdate) "Проверка..." else "Проверить обновления",
                        subtitle = "Свежие сборки и релизы на GitHub",
                        accentIcon = true,
                        onClick = {
                            viewModel.checkForUpdatesManual { _, message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                ExpressiveSegmentedCard(index = 1, count = 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingsIconBadge(icon = Icons.Default.Sync)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Период авто-обновления", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Интервал актуализации данных", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "$autoRefreshMinutes мин",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = { viewModel.refreshData() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = PillShape,
                        enabled = !isRefreshing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isRefreshing) "Обновление..." else "Обновить данные сейчас")
                    }
                }
            }
        }

        // 7. Section: Разделы и обучение
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSectionHeader("Помощь и разделы")
                ExpressiveSegmentedCard(index = 0, count = 2) {
                    SettingsActionRow(
                        icon = Icons.Default.School,
                        title = "Обучение и подсказки",
                        subtitle = "Пройти интерактивный гид по приложению заново",
                        onClick = {
                            viewModel.restartOnboardingGuide()
                            viewModel.selectTab(ru.mesh.expressive.ui.viewmodel.MainTab.DASHBOARD)
                            Toast.makeText(context, "Интерактивный гид запущен", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                ExpressiveSegmentedCard(index = 1, count = 2) {
                    SettingsActionRow(
                        icon = Icons.Default.EmojiEvents,
                        title = "Портфолио и Олимпиады",
                        subtitle = "Все дипломы, ВсОШ, МОШ и награды ученика",
                        accentIcon = true,
                        onClick = {
                            viewModel.selectTab(ru.mesh.expressive.ui.viewmodel.MainTab.PORTFOLIO)
                        }
                    )
                }
            }
        }

        // 8. Section: Система и о приложении
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSectionHeader("Система")
                ExpressiveSegmentedCard(
                    index = 0,
                    count = 2,
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Сброс данных и сессии",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Очищает токен, профиль и кэш",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = { showResetConfirmDialog = true },
                            shape = PillShape,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Очистить")
                        }
                    }
                }
                ExpressiveSegmentedCard(
                    index = 1,
                    count = 2,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Text(
                        text = "Дневник МЭШ Expressive • v${ru.mesh.expressive.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Material Design 3 Expressive • Open Source • Без трекеров и рекламы",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "semi vibecoded by gemini",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
        } // end GENERAL bottom
    }
    } // end Column

    if (showClassInfoDialog) {
        AlertDialog(
            onDismissRequest = { showClassInfoDialog = false },
            icon = { Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
            title = { Text("Параметры класса", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Класс: ${profile.className}", fontWeight = FontWeight.SemiBold)
                    Text("Class Unit ID: ${profile.classUnitId}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Text("Class UID: ${profile.classUid}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("Contingent GUID: ${profile.contingentGuid}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(profile.classUid))
                        Toast.makeText(context, "Class UID скопирован", Toast.LENGTH_SHORT).show()
                        showClassInfoDialog = false
                    },
                    shape = PillShape
                ) {
                    Text("Скопировать Class UID")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClassInfoDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    if (showTokenDialog) {
        val token = viewModel.currentAuthToken.orEmpty()
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            icon = { Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
            title = { Text("Активный токен МЭШ", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Используется для прямого доступа к API:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (token.length > 80) "${token.take(40)}...${token.takeLast(30)}" else token,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(token))
                        Toast.makeText(context, "Токен скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
                        showTokenDialog = false
                    },
                    shape = PillShape
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Скопировать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("Сбросить данные?", fontWeight = FontWeight.Bold) },
            text = { Text("Вы выйдете из аккаунта, а локальные настройки и кэш будут полностью удалены.") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.clearCache()
                    },
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Да, сбросить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { if (!isAvatarLoading) showAvatarDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Аватар профиля",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!profile.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = profile.avatarUrl,
                                contentDescription = "Аватар",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            val initials = "${profile.firstName.firstOrNull() ?: 'М'}${profile.lastName.firstOrNull() ?: 'Э'}"
                            Text(
                                text = initials,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isAvatarLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (profile.firstName.isNotBlank()) "${profile.firstName} ${profile.lastName}" else "Ученик МЭШ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (profile.className.isNotBlank()) "${profile.className} класс" else "Московская электронная школа",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { photoPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = PillShape,
                        enabled = !isAvatarLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (profile.avatarUrl.isNullOrBlank()) "Загрузить фото" else "Сменить аватарку")
                    }

                    if (!profile.avatarUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                isAvatarLoading = true
                                viewModel.deleteAvatar {
                                    isAvatarLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = PillShape,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            enabled = !isAvatarLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Удалить аватарку")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAvatarDialog = false },
                    enabled = !isAvatarLoading
                ) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun ExpressiveSegmentedCard(
    index: Int,
    count: Int,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = segmentedItemShape(index, count),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsIconBadge(
    icon: ImageVector,
    accent: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (accent) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    accentIcon: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            SettingsIconBadge(icon = icon, accent = accentIcon)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        ru.mesh.expressive.ui.components.ExpressiveSwitch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentIcon: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            SettingsIconBadge(icon = icon, accent = accentIcon)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun segmentedItemShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 20.dp
    val inner = 6.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

@Composable
private fun ExpressiveSegmentedToggleRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    accentIcon: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = segmentedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (accentIcon) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (accentIcon) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(10.dp))
            ru.mesh.expressive.ui.components.ExpressiveSwitch(checked = isChecked, onCheckedChange = {})
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
fun SettingsRedesignGreenMonetPreview() {
    val greenMonet = darkColorScheme(
        primary = Color(0xFF8CD6A4),
        onPrimary = Color(0xFF00391D),
        primaryContainer = Color(0xFF0A522D),
        onPrimaryContainer = Color(0xFFA7F3BF),
        secondary = Color(0xFFB5CCB9),
        onSecondary = Color(0xFF213527),
        secondaryContainer = Color(0xFF284D37),
        onSecondaryContainer = Color(0xFFC6EBD0),
        background = Color(0xFF111411),
        onBackground = Color(0xFFE1E3DF),
        surface = Color(0xFF111411),
        onSurface = Color(0xFFE1E3DF),
        surfaceVariant = Color(0xFF414942),
        onSurfaceVariant = Color(0xFFC1C9C0),
        surfaceContainerLowest = Color(0xFF0C0F0C),
        surfaceContainerLow = Color(0xFF191D19),
        surfaceContainer = Color(0xFF1D211D),
        surfaceContainerHigh = Color(0xFF272B27),
        surfaceContainerHighest = Color(0xFF323632)
    )
    MaterialTheme(colorScheme = greenMonet) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Настройки",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ИА",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Иван Алексеев",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "ГБОУ Школа № 315 • 8В",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Внешний вид",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = segmentedItemShape(0, 4),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("Тема оформления", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Системная, тёмная или светлая", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val opts = listOf("SYS" to "Системная", "DARK" to "Тёмная", "LIGHT" to "Светлая")
                            ru.mesh.expressive.ui.components.M3ConnectedButtonGroup(
                                items = opts,
                                selectedItem = opts[1],
                                onItemSelected = {},
                                label = { it.second },
                                showCheckmark = true,
                                scrollable = false
                            )
                        }
                    }
                    ExpressiveSegmentedToggleRow(
                        index = 1, count = 4,
                        icon = Icons.Default.Palette,
                        title = "Monet Dynamic Color",
                        subtitle = "Цвета под обои устройства",
                        isChecked = true,
                        accentIcon = true
                    )
                    ExpressiveSegmentedToggleRow(
                        index = 2, count = 4,
                        icon = Icons.Default.Contrast,
                        title = "AMOLED чёрный",
                        subtitle = "Чистый чёрный фон (#000000)",
                        isChecked = false
                    )
                    ExpressiveSegmentedToggleRow(
                        index = 3, count = 4,
                        icon = Icons.Default.TouchApp,
                        title = "Пружинная физика",
                        subtitle = "Упругие переходы Material Expressive",
                        isChecked = true
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Учёба и уведомления",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
                    )
                    ExpressiveSegmentedToggleRow(
                        index = 0, count = 2,
                        icon = Icons.Default.NotificationsActive,
                        title = "Уведомления об оценках",
                        subtitle = "Мгновенный пуш о новых баллах",
                        isChecked = true,
                        accentIcon = true
                    )
                    ExpressiveSegmentedToggleRow(
                        index = 1, count = 2,
                        icon = Icons.Default.Calculate,
                        title = "Учитывать просроченные точки",
                        subtitle = "Строгий расчёт GPA по формуле МЭШ",
                        isChecked = true
                    )
                }
            }
        }
    }
}

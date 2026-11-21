package ru.mesh.expressive.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.mesh.expressive.data.local.RootTokenExtractor
import ru.mesh.expressive.ui.components.M3CircularWavyLoader
import ru.mesh.expressive.ui.theme.*
import ru.mesh.expressive.ui.viewmodel.MeshMainViewModel

enum class AuthScreenState {
    WELCOME_LANDING, OAUTH_WAITING, ROOT_AND_MANUAL_TOKEN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: MeshMainViewModel,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    var screenState by remember { mutableStateOf(AuthScreenState.WELCOME_LANDING) }
    var manualToken by remember { mutableStateOf("") }
    var isExtractingRoot by remember { mutableStateOf(false) }
    var rootDialogMessage by remember { mutableStateOf<String?>(null) }
    var showHowItWorksDialog by remember { mutableStateOf(false) }

    val isOAuthLoading by viewModel.isOAuthLoading.collectAsState()
    val oAuthError by viewModel.oAuthError.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            if (screenState != AuthScreenState.WELCOME_LANDING || viewModel.sessionManager.isLoggedIn) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (screenState) {
                                AuthScreenState.OAUTH_WAITING -> "Вход через mos.ru"
                                AuthScreenState.ROOT_AND_MANUAL_TOKEN -> "Импорт токена"
                                else -> "Вход в МЭШ"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (screenState != AuthScreenState.WELCOME_LANDING) {
                                viewModel.resetOAuthState()
                                screenState = AuthScreenState.WELCOME_LANDING
                            } else {
                                onAuthSuccess()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showHowItWorksDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Инструкция")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AnimatedContent(
                targetState = screenState,
                label = "AuthScreenStateAnimation"
            ) { targetState ->
                when (targetState) {
                    AuthScreenState.WELCOME_LANDING -> {
                        WelcomeLandingView(
                            onLoginMosRuClick = {
                                screenState = AuthScreenState.OAUTH_WAITING
                                viewModel.startOAuthLogin(context)
                            },
                            onRootTokenClick = { screenState = AuthScreenState.ROOT_AND_MANUAL_TOKEN },
                            onContinueDemoClick = onAuthSuccess
                        )
                    }

                    AuthScreenState.OAUTH_WAITING -> {
                        OAuthWaitingView(
                            isLoading = isOAuthLoading,
                            error = oAuthError,
                            onRetry = { viewModel.startOAuthLogin(context) },
                            onCancel = {
                                viewModel.resetOAuthState()
                                screenState = AuthScreenState.WELCOME_LANDING
                            }
                        )
                    }

                    AuthScreenState.ROOT_AND_MANUAL_TOKEN -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Root Extractor Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ExpressiveCardShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(M3Cookie7Shape(7))
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AdminPanelSettings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Импорт токена через Root",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Извлекает активную сессию из официального ru.mes.dnevnik",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    if (isExtractingRoot) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            M3CircularWavyLoader(size = 32.dp, color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Поиск токена в ru.mes.dnevnik...")
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isExtractingRoot = true
                                                    val res = RootTokenExtractor.extractTokenFromOfficialApp(context.cacheDir)
                                                    isExtractingRoot = false
                                                    if (res.isSuccess && !res.token.isNullOrBlank()) {
                                                        manualToken = res.token
                                                        viewModel.saveAuthToken(res.token)
                                                        rootDialogMessage = "Токен успешно извлечен через Root! Загружаю дневник..."
                                                    } else {
                                                        rootDialogMessage = res.message
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = PillShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Извлечь токен через Root (Авто)")
                                        }
                                    }
                                }
                            }

                            // Manual Input Section
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ExpressiveCardShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Ручной ввод токена",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Вставьте JWT токен (Bearer eyJ...) или auth_token вручную.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = manualToken,
                                        onValueChange = { manualToken = it },
                                        label = { Text("Токен МЭШ (Bearer / JWT)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        minLines = 2,
                                        maxLines = 4
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            if (manualToken.isNotBlank()) {
                                                coroutineScope.launch {
                                                    viewModel.saveAuthToken(manualToken.trim())
                                                    onAuthSuccess()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = PillShape,
                                        enabled = manualToken.isNotBlank()
                                    ) {
                                        Text("Авторизоваться")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (rootDialogMessage != null) {
        AlertDialog(
            onDismissRequest = {
                val msg = rootDialogMessage
                rootDialogMessage = null
                if (msg?.contains("успешно") == true) {
                    onAuthSuccess()
                }
            },
            icon = { Icon(Icons.Default.Done, contentDescription = null, tint = ScoreGreen, modifier = Modifier.size(32.dp)) },
            title = { Text("Импорт через Root", fontWeight = FontWeight.Bold) },
            text = { Text(rootDialogMessage!!) },
            confirmButton = {
                Button(
                    onClick = {
                        val msg = rootDialogMessage
                        rootDialogMessage = null
                        if (msg?.contains("успешно") == true) {
                            onAuthSuccess()
                        }
                    },
                    shape = PillShape
                ) {
                    Text("OK")
                }
            }
        )
    }

    if (showHowItWorksDialog) {
        AlertDialog(
            onDismissRequest = { showHowItWorksDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Как работает вход?",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HowItWorksInfoItem(
                        number = "1",
                        title = "Официальный OAuth 2.0 PKCE",
                        description = "Вход происходит через системный браузер (Chrome Custom Tabs). Ваши пароли вводятся только на защищенной странице mos.ru."
                    )
                    HowItWorksInfoItem(
                        number = "2",
                        title = "Поддержка автозаполнения и биометрии",
                        description = "В браузере работают все ваши сохраненные пароли Google Chrome, ключи доступа (Passkeys) и двухфакторная аутентификация."
                    )
                    HowItWorksInfoItem(
                        number = "3",
                        title = "Мгновенный автоматический возврат",
                        description = "После подтверждения входа браузер автоматически передает код приложению и возвращает вас в дневник."
                    )
                    HowItWorksInfoItem(
                        number = "4",
                        title = "0 трекеров и открытость",
                        description = "Ключ сессии сохраняется строго локально на вашем устройстве. Никаких серверов-посредников, рекламы или слежки."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHowItWorksDialog = false },
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Понятно")
                }
            }
        )
    }
}

@Composable
private fun OAuthWaitingView(
    @Suppress("UNUSED_PARAMETER") isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ExpressiveCardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (error == null) {
                    M3CircularWavyLoader(size = 56.dp, color = MaterialTheme.colorScheme.primary)

                    Text(
                        text = "Ожидание авторизации",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "В открывшемся окне браузера выполните вход на mos.ru. После завершения вы автоматически вернетесь в приложение.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )

                    Text(
                        text = "Ошибка авторизации",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = PillShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = onRetry,
                        shape = PillShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (error != null) "Повторить" else "Открыть снова")
                    }
                }
            }
        }
    }
}

@Composable
private fun HowItWorksInfoItem(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WelcomeLandingView(
    onLoginMosRuClick: () -> Unit,
    onRootTokenClick: () -> Unit,
    onContinueDemoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // App Emblem
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(M3Cookie7Shape(7))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Дневник МЭШ",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Современный клиент электронного дневника в стиле Material 3 Expressive",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Feature Highlights
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WelcomeFeatureRow(
                    icon = Icons.Default.DoneAll,
                    title = "Авто-сдача заданий за звезды",
                    subtitle = "Мгновенное выполнение ЦДЗ и получение наград в 1 клик"
                )
                WelcomeFeatureRow(
                    icon = Icons.Default.Shield,
                    title = "0 трекеров и чистый трафик",
                    subtitle = "Без AppMetrica, геотрекинга «Спутник» и телеметрии"
                )
                WelcomeFeatureRow(
                    icon = Icons.Default.Palette,
                    title = "Material 3 Expressive & Monet",
                    subtitle = "Адаптивная тема обоев, пружинная физика и чистый UI"
                )
                WelcomeFeatureRow(
                    icon = Icons.Default.Restaurant,
                    title = "Москвёнок и Рейтинг щедрости",
                    subtitle = "Точный баланс карты, лимиты и рейтинг потраченных звезд"
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onLoginMosRuClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Войти через mos.ru",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onRootTokenClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = PillShape
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Импорт через Root / Токен",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            TextButton(
                onClick = onContinueDemoClick,
                modifier = Modifier.fillMaxWidth(),
                shape = PillShape
            ) {
                Text(
                    text = "Открыть в демонстрационном режиме",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "semi vibecoded by gemini",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun WelcomeFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

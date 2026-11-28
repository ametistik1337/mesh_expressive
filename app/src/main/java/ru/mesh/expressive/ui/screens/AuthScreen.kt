package ru.mesh.expressive.ui.screens

import ru.mesh.expressive.ui.components.ExpressiveIconButton

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import ru.mesh.expressive.data.local.RootTokenExtractor
import ru.mesh.expressive.ui.components.M3CircularWavyLoader
import ru.mesh.expressive.ui.theme.*
import ru.mesh.expressive.ui.viewmodel.MeshMainViewModel

enum class AuthScreenState {
    WELCOME_LANDING, WEBVIEW_LOGIN, OAUTH_WAITING, ROOT_AND_MANUAL_TOKEN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: MeshMainViewModel,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    var screenState by rememberSaveable { mutableStateOf(AuthScreenState.WELCOME_LANDING) }
    var manualToken by remember { mutableStateOf("") }
    var isExtractingRoot by remember { mutableStateOf(false) }
    var rootDialogMessage by remember { mutableStateOf<String?>(null) }
    var showHowItWorksDialog by remember { mutableStateOf(false) }

    val isOAuthLoading by viewModel.isOAuthLoading.collectAsState()
    val oAuthError by viewModel.oAuthError.collectAsState()
    val authWebViewUrl by viewModel.authWebViewUrl.collectAsState()
    val selectedRegion by viewModel.selectedRegion.collectAsState()
    var lastUseEsia by rememberSaveable { mutableStateOf(true) }
    var showRegionPicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            if (screenState != AuthScreenState.WELCOME_LANDING || viewModel.sessionManager.isLoggedIn) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (screenState) {
                                AuthScreenState.WEBVIEW_LOGIN,
                                AuthScreenState.OAUTH_WAITING -> if (selectedRegion.usesMosRuOAuth) "Вход через mos.ru" else "Вход: ${selectedRegion.shortName}"
                                AuthScreenState.ROOT_AND_MANUAL_TOKEN -> "Импорт токена"
                                else -> "Вход в МЭШ"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        ExpressiveIconButton(tooltipText = "Назад", onClick = {
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
                        if (screenState == AuthScreenState.WEBVIEW_LOGIN) {
                            ExpressiveIconButton(tooltipText = "Открыть в браузере", onClick = {
                                screenState = AuthScreenState.OAUTH_WAITING
                                viewModel.startOAuthLogin(context, useEsia = lastUseEsia)
                            }) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = "Открыть в браузере")
                            }
                        }
                        ExpressiveIconButton(tooltipText = "Инструкция", onClick = { showHowItWorksDialog = true }) {
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
                transitionSpec = {
                    (fadeIn(animationSpec = spring(dampingRatio = 1f, stiffness = 1600f)) +
                        slideInHorizontally(animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f)) { it / 12 }) togetherWith
                        fadeOut(animationSpec = spring(dampingRatio = 1f, stiffness = 1600f))
                },
                label = "AuthScreenStateAnimation"
            ) { targetState ->
                when (targetState) {
                    AuthScreenState.WELCOME_LANDING -> {
                        WelcomeLandingView(
                            selectedRegion = selectedRegion,
                            onSelectRegionClick = { showRegionPicker = true },
                            onLoginPrimaryClick = {
                                lastUseEsia = true
                                screenState = AuthScreenState.OAUTH_WAITING
                                viewModel.startOAuthLogin(context, useEsia = true)
                            },
                            onLoginKauthClick = {
                                lastUseEsia = false
                                screenState = AuthScreenState.OAUTH_WAITING
                                viewModel.startOAuthLogin(context, useEsia = false)
                            },
                            onContinueDemoClick = onAuthSuccess
                        )
                    }

                    AuthScreenState.WEBVIEW_LOGIN -> {
                        InAppAuthWebView(
                            url = authWebViewUrl,
                            isLoading = isOAuthLoading,
                            error = oAuthError,
                            onDeepLinkCaptured = { uri ->
                                screenState = AuthScreenState.OAUTH_WAITING
                                viewModel.handleAuthDeepLink(uri, onSuccess = onAuthSuccess)
                            },
                            onRetry = { viewModel.prepareAuthWebViewUrl(useEsia = lastUseEsia) },
                            onOpenExternalBrowser = {
                                screenState = AuthScreenState.OAUTH_WAITING
                                viewModel.startOAuthLogin(context, useEsia = lastUseEsia)
                            }
                        )
                    }

                    AuthScreenState.OAUTH_WAITING -> {
                        OAuthWaitingView(
                            isLoading = isOAuthLoading,
                            error = oAuthError,
                            onRetry = { viewModel.startOAuthLogin(context, useEsia = lastUseEsia) },
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
                                                text = "Извлекает активную сессию из ru.mes.dnevnik или Моя школа",
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

    if (showRegionPicker) {
        RegionPickerModalSheet(
            selectedRegion = selectedRegion,
            initialCustomUrl = viewModel.sessionManager.customRegionUrl ?: "",
            onSelectPreset = { rgn ->
                viewModel.selectRegion(rgn)
                showRegionPicker = false
            },
            onSelectCustom = { customUrl ->
                viewModel.selectRegion(ru.mesh.expressive.data.remote.RegionRegistry.REGION_CUSTOM, customUrl)
                showRegionPicker = false
            },
            onDismiss = { showRegionPicker = false }
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
    selectedRegion: ru.mesh.expressive.data.remote.RegionConfig,
    onSelectRegionClick: () -> Unit,
    onLoginPrimaryClick: () -> Unit,
    onLoginKauthClick: () -> Unit,
    onContinueDemoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top: universal region selector
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PillShape)
                .clickable(onClick = onSelectRegionClick),
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedRegion.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = selectedRegion.apiHost,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Выбрать регион",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Center: compact hero emblem + title
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(M3Cookie7Shape(7))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Дневник МЭШ",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Material 3 Expressive клиент без трекеров и телеметрии",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Bottom: primary action buttons without scroll
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onLoginPrimaryClick,
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
                    text = if (selectedRegion.usesMosRuOAuth) "Войти через mos.ru" else "Войти через Госуслуги (ЕСИА)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!selectedRegion.usesMosRuOAuth) {
                OutlinedButton(
                    onClick = onLoginKauthClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = PillShape
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Логин региона (${selectedRegion.shortName})",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            TextButton(
                onClick = onContinueDemoClick,
                modifier = Modifier.fillMaxWidth(),
                shape = PillShape
            ) {
                Text(
                    text = "Демо-режим",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionPickerModalSheet(
    selectedRegion: ru.mesh.expressive.data.remote.RegionConfig,
    initialCustomUrl: String,
    onSelectPreset: (Int) -> Unit,
    onSelectCustom: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCustomInput by remember {
        mutableStateOf(selectedRegion.rgn == ru.mesh.expressive.data.remote.RegionRegistry.REGION_CUSTOM)
    }
    var customUrlText by remember { mutableStateOf(initialCustomUrl) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Выберите регион",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Москва (МЭШ) или региональная система ФГИС «Моя школа»",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ru.mesh.expressive.data.remote.RegionRegistry.PRESET_REGIONS.forEach { region ->
                val isSelected = selectedRegion.rgn == region.rgn && !showCustomInput
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ExpressiveCardShape)
                        .clickable { onSelectPreset(region.rgn) },
                    shape = ExpressiveCardShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = region.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = region.apiHost,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ExpressiveCardShape)
                    .clickable { showCustomInput = !showCustomInput },
                shape = ExpressiveCardShape,
                color = if (showCustomInput) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Другой регион (свой домен)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showCustomInput) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Указать адрес регионального сервера ФГИС вручную",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (showCustomInput) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (showCustomInput) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showCustomInput) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = customUrlText,
                    onValueChange = { customUrlText = it },
                    label = { Text("Адрес сервера (например myschool.yanao.ru)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = { onSelectCustom(customUrlText) },
                    enabled = ru.mesh.expressive.data.remote.RegionRegistry.normalizeBaseUrl(customUrlText) != null,
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Применить сервер")
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InAppAuthWebView(
    url: String?,
    isLoading: Boolean,
    error: String?,
    onDeepLinkCaptured: (Uri) -> Unit,
    onRetry: () -> Unit,
    onOpenExternalBrowser: () -> Unit
) {
    val context = LocalContext.current
    var pageProgress by remember { mutableIntStateOf(0) }
    var isPageLoading by remember { mutableStateOf(true) }
    var webViewError by remember { mutableStateOf<String?>(null) }
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }
    val webViewStateBundle = rememberSaveable { android.os.Bundle() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.saveState(webViewStateBundle)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val displayError = error ?: webViewError
        when {
            displayError != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        webViewError = null
                        lastLoadedUrl = null
                        onRetry()
                    }, shape = PillShape) {
                        Text("Повторить")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onOpenExternalBrowser) {
                        Text("Открыть в браузере")
                    }
                }
            }

            url.isNullOrBlank() || isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    M3CircularWavyLoader(size = 56.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Подготовка страницы входа...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                javaScriptCanOpenWindowsAutomatically = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                userAgentString = userAgentString.replace("; wv", "")
                            }
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val reqUri = request?.url ?: return false
                                    if (reqUri.scheme == "dnevnik-mes") {
                                        onDeepLinkCaptured(reqUri)
                                        return true
                                    }
                                    if (reqUri.scheme != "http" && reqUri.scheme != "https") {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, reqUri).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                        }
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isPageLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isPageLoading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        isPageLoading = false
                                        webViewError = "Ошибка загрузки (${error?.errorCode}): ${error?.description}"
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    handler?.cancel()
                                    val failedUrl = error?.url.orEmpty()
                                    val currentMainUrl = view?.url.orEmpty()
                                    val isMainFrameSslError = failedUrl.isNotBlank() &&
                                        (failedUrl == currentMainUrl ||
                                            failedUrl.contains("login.mos.ru") ||
                                            failedUrl.contains("gosuslugi.ru") ||
                                            ru.mesh.expressive.data.remote.RegionRegistry.isKnownSchoolHost(
                                                Uri.parse(failedUrl).host.orEmpty()
                                            ))
                                    if (isMainFrameSslError) {
                                        isPageLoading = false
                                        webViewError = "Ошибка SSL сертификата (${error?.primaryError}): $failedUrl"
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress
                                    if (newProgress >= 100) isPageLoading = false
                                }
                            }

                            webViewRef = this
                            lastLoadedUrl = url
                            if (!webViewStateBundle.isEmpty) {
                                restoreState(webViewStateBundle)
                            } else {
                                loadUrl(url)
                            }
                        }
                    },
                    update = { webView ->
                        if (url.isNotBlank() && lastLoadedUrl != url) {
                            lastLoadedUrl = url
                            webView.loadUrl(url)
                        }
                    }
                )

                if (isPageLoading && pageProgress < 100) {
                    LinearProgressIndicator(
                        progress = { pageProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun AuthWelcomeMoscowPreview() {
    MeshExpressiveTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            WelcomeLandingView(
                selectedRegion = ru.mesh.expressive.data.remote.RegionRegistry.MOSCOW,
                onSelectRegionClick = {},
                onLoginPrimaryClick = {},
                onLoginKauthClick = {},
                onContinueDemoClick = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun AuthWelcomeRegionPreview() {
    MeshExpressiveTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            WelcomeLandingView(
                selectedRegion = ru.mesh.expressive.data.remote.RegionRegistry.PRESET_REGIONS[1],
                onSelectRegionClick = {},
                onLoginPrimaryClick = {},
                onLoginKauthClick = {},
                onContinueDemoClick = {}
            )
        }
    }
}

package com.droid.dolphy.nfc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.droid.dolphy.MonetHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcPassScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val accent = MonetHelper.getMonetPrimaryColor(context)

    var passes by remember { mutableStateOf<List<NfcPassProfile>>(java.util.Collections.emptyList()) }
    var activePass by remember { mutableStateOf<NfcPassProfile?>(null) }
    var isRootMode by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPass by remember { mutableStateOf<NfcPassProfile?>(null) }
    var isOperating by remember { mutableStateOf(false) }

    fun refreshState() {
        passes = NfcPassStorage.getPasses(context)
        activePass = NfcPassStorage.getActivePass(context)
    }

    LaunchedEffect(Unit) {
        refreshState()
        withContext(Dispatchers.IO) {
            val rootOk = NfcPassRootBackend.isRootAvailable()
            withContext(Dispatchers.Main) {
                isRootMode = rootOk
            }
            if (rootOk) {
                val current = activePass
                if (current != null && !NfcPassRootBackend.isUidEmulationMounted()) {
                    NfcPassRootBackend.applyPassUid(context, current)
                }
            }
        }
    }

    fun activatePass(pass: NfcPassProfile) {
        if (isOperating) return
        isOperating = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                NfcPassRootBackend.applyPassUid(context, pass)
            }
            statusMessage = result.second
            refreshState()
            isOperating = false
            Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun disablePass() {
        if (isOperating) return
        isOperating = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            withContext(Dispatchers.IO) {
                NfcPassRootBackend.disablePassUid(context)
            }
            statusMessage = "Эмуляция отключена"
            refreshState()
            isOperating = false
            Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun togglePass(pass: NfcPassProfile) {
        if (activePass?.id == pass.id) {
            disablePass()
        } else {
            activatePass(pass)
        }
    }

    fun triggerPreset(nameKeyword: String) {
        val found = passes.find { it.name.contains(nameKeyword, ignoreCase = true) }
        if (found != null) {
            togglePass(found)
        } else {
            val defaultPass = NfcPassStorage.getDefaultPasses().firstOrNull { it.name.contains(nameKeyword, ignoreCase = true) }
            if (defaultPass != null) {
                NfcPassStorage.savePass(context, defaultPass)
                refreshState()
                togglePass(defaultPass)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Эмуляция пропусков",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Club, Work, Intercom UID",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isRootMode) Color(0xFF1E382B) else Color(0xFF1D283A),
                        border = BorderStroke(
                            1.dp,
                            if (isRootMode) Color(0xFF22C55E).copy(alpha = 0.5f) else Color(0xFF3B82F6).copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isRootMode) Color(0xFF22C55E) else Color(0xFF3B82F6))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRootMode) "Root ST21NFC" else "HCE Fallback",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRootMode) Color(0xFF86EFAC) else Color(0xFF93C5FD)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    editingPass = null
                    showAddDialog = true
                },
                containerColor = accent,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить пропуск")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Active Status Hero Card
            item {
                ActiveEmulationCard(
                    activePass = activePass,
                    accent = accent,
                    isOperating = isOperating,
                    onToggleMaster = {
                        val current = activePass
                        if (current != null) {
                            togglePass(current)
                        } else {
                            val first = passes.firstOrNull()
                            if (first != null) {
                                togglePass(first)
                            } else {
                                editingPass = null
                                showAddDialog = true
                            }
                        }
                    }
                )
            }

            // 2. Presets Header & Fast Access
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Быстрые пресеты",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetChip(
                            label = "Работа",
                            subLabel = "DE:AD:BE:EF",
                            modifier = Modifier.weight(1f),
                            accent = accent,
                            onClick = { triggerPreset("Работа") }
                        )
                        PresetChip(
                            label = "Клуб",
                            subLabel = "04:5A:2B:1C...",
                            modifier = Modifier.weight(1f),
                            accent = accent,
                            onClick = { triggerPreset("Клуб") }
                        )
                        PresetChip(
                            label = "Домофон",
                            subLabel = "55:AA:33:CC",
                            modifier = Modifier.weight(1f),
                            accent = accent,
                            onClick = { triggerPreset("Домофон") }
                        )
                    }
                }
            }

            // 3. Saved Passes List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Сохраненные пропуска (${passes.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "UID 4-байта / 7-байт",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Passes List
            items(passes, key = { it.id }) { pass ->
                val isActive = activePass?.id == pass.id
                PassProfileCard(
                    pass = pass,
                    isActive = isActive,
                    accent = accent,
                    isOperating = isOperating,
                    onToggle = { togglePass(pass) },
                    onEdit = {
                        editingPass = pass
                        showAddDialog = true
                    },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("NFC UID", pass.getFormattedUid()))
                        Toast.makeText(context, "UID ${pass.getFormattedUid()} скопирован", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        NfcPassStorage.deletePass(context, pass.id)
                        if (isActive) {
                            scope.launch(Dispatchers.IO) {
                                NfcPassRootBackend.disablePassUid(context)
                            }
                        }
                        refreshState()
                        Toast.makeText(context, "Пропуск удален", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }

    if (showAddDialog) {
        AddEditPassDialog(
            passToEdit = editingPass,
            accent = accent,
            onDismiss = {
                showAddDialog = false
                editingPass = null
            },
            onSave = { savedPass, activateNow ->
                NfcPassStorage.savePass(context, savedPass)
                refreshState()
                showAddDialog = false
                editingPass = null
                if (activateNow) {
                    activatePass(savedPass)
                } else if (activePass?.id == savedPass.id) {
                    disablePass()
                }
            }
        )
    }
}

@Composable
private fun ActiveEmulationCard(
    activePass: NfcPassProfile?,
    accent: Color,
    isOperating: Boolean,
    onToggleMaster: () -> Unit
) {
    val isActive = activePass != null
    val containerColor by animateColorAsState(
        if (isActive) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        label = "activeContainer"
    )
    val borderColor by animateColorAsState(
        if (isActive) accent else MaterialTheme.colorScheme.outlineVariant,
        label = "activeBorder"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isActive) accent else MaterialTheme.colorScheme.outline)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isActive) "ЭМУЛЯЦИЯ АКТИВНА" else "ЭМУЛЯЦИЯ ВЫКЛЮЧЕНА",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggleMaster() },
                    enabled = !isOperating,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent
                    )
                )
            }

            if (activePass != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = activePass.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activePass.getFormattedUid(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.ExtraBold,
                        color = accent
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activePass.cardType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(text = "•", color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = "SAK: 0x" + String.format("%02X", activePass.sak),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = "Выберите пропуск ниже или нажмите тумблер для эмуляции",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    subLabel: String,
    modifier: Modifier = Modifier,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                fontSize = 10.sp,
                color = accent
            )
        }
    }
}

@Composable
private fun PassProfileCard(
    pass: NfcPassProfile,
    isActive: Boolean,
    accent: Color,
    isOperating: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
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
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEdit() }
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isActive) accent else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = pass.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = pass.getFormattedUid(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) accent else MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pass.cardType,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(text = "•", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = when {
                                pass.is4Byte -> "4B"
                                pass.is7Byte -> "7B"
                                pass.is10Byte -> "10B"
                                else -> "${pass.getCleanHex().length / 2}B"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            fontSize = 11.sp,
                            color = accent
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Редактировать",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Копировать UID",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggle,
                    enabled = !isOperating
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isActive) "Остановить" else "Активировать",
                        tint = if (isActive) Color(0xFFEF4444) else accent
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEditPassDialog(
    passToEdit: NfcPassProfile? = null,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (NfcPassProfile, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(passToEdit?.name ?: "") }
    var uidHex by remember { mutableStateOf(passToEdit?.uidHex ?: "") }
    var cardType by remember { mutableStateOf(passToEdit?.cardType ?: "MIFARE Classic 1K") }
    var sak by remember { mutableStateOf(passToEdit?.sak ?: 0x08) }
    var activateImmediately by remember { mutableStateOf(passToEdit?.isActive ?: true) }

    val cleanHex = uidHex.replace(":", "").replace(" ", "").replace("-", "").trim().uppercase()
    val isValidLength = cleanHex.length == 8 || cleanHex.length == 14 || cleanHex.length == 20
    val isValidHex = cleanHex.all { it in "0123456789ABCDEF" }
    val canSave = name.isNotBlank() && isValidLength && isValidHex

    fun generateRandomUid(bytesCount: Int) {
        val bytes = ByteArray(bytesCount)
        SecureRandom().nextBytes(bytes)
        if (bytesCount == 7 || bytesCount == 10) {
            bytes[0] = 0x04.toByte() // standard NXP manufacturer byte
        }
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X", b.toInt() and 0xFF))
        }
        uidHex = sb.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (passToEdit != null) "Редактировать пропуск" else "Новый пропуск",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Preset quick tags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Работа", "Клуб", "Домофон", "Офис").forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { name = preset }
                        ) {
                            Text(
                                text = preset,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название пропуска") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uidHex,
                    onValueChange = { input ->
                        uidHex = input.filter { it in "0123456789abcdefABCDEF:- " }.uppercase()
                    },
                    label = { Text("UID (HEX, 4, 7 или 10 байт)") },
                    placeholder = { Text("A1:B2:C3:D4") },
                    singleLine = true,
                    isError = cleanHex.isNotEmpty() && (!isValidLength || !isValidHex),
                    supportingText = {
                        val text = when {
                            cleanHex.isEmpty() -> "4 байта (8 симв.), 7 байт (14 симв.) или 10 байт (20 симв.)"
                            cleanHex.length == 8 -> "4 байта (OK, Single UID)"
                            cleanHex.length == 14 -> "7 байт (OK, Double UID)"
                            cleanHex.length == 20 -> "10 байт (OK, Triple UID)"
                            else -> "Введено символов: ${cleanHex.length} (нужно 8, 14 или 20)"
                        }
                        Text(text = text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { generateRandomUid(4) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Рандом 4B", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = { generateRandomUid(7) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Рандом 7B", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = { generateRandomUid(10) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Рандом 10B", fontSize = 10.sp)
                    }
                }

                Text(
                    text = "Тип карты:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                cardType = "MIFARE Classic 1K"
                                sak = 0x08
                            }
                    ) {
                        RadioButton(
                            selected = cardType == "MIFARE Classic 1K",
                            onClick = {
                                cardType = "MIFARE Classic 1K"
                                sak = 0x08
                            }
                        )
                        Text(text = "MIFARE Classic (SAK 0x08)", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                cardType = "MIFARE Ultralight"
                                sak = 0x00
                            }
                    ) {
                        RadioButton(
                            selected = cardType == "MIFARE Ultralight",
                            onClick = {
                                cardType = "MIFARE Ultralight"
                                sak = 0x00
                            }
                        )
                        Text(text = "MIFARE Ultralight (SAK 0x00)", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                cardType = "ISO-DEP (DESFire)"
                                sak = 0x20
                            }
                    ) {
                        RadioButton(
                            selected = cardType == "ISO-DEP (DESFire)",
                            onClick = {
                                cardType = "ISO-DEP (DESFire)"
                                sak = 0x20
                            }
                        )
                        Text(text = "ISO-DEP (SAK 0x20)", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Switch(
                        checked = activateImmediately,
                        onCheckedChange = { activateImmediately = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Активировать сразу", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canSave) {
                        val atqaToSave = if (passToEdit != null && cardType == passToEdit.cardType) {
                            passToEdit.atqa
                        } else {
                            when (cardType) {
                                "MIFARE Ultralight" -> "00:44"
                                "ISO-DEP (DESFire)" -> "03:44"
                                else -> if (cleanHex.length == 14 || cleanHex.length == 20) "00:44" else "00:04"
                            }
                        }
                        val profile = NfcPassProfile(
                            id = passToEdit?.id ?: System.currentTimeMillis(),
                            name = name.trim(),
                            uidHex = cleanHex,
                            cardType = cardType,
                            sak = sak,
                            atqa = atqaToSave,
                            isActive = false
                        )
                        onSave(profile, activateImmediately)
                    }
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(if (passToEdit != null) "Обновить" else "Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

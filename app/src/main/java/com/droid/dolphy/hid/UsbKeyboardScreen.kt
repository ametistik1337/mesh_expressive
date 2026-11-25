package com.droid.dolphy.hid

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.dolphy.MonetHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val KeyBgNormal = Color(0xFF232521)
private val KeyBgSpecial = Color(0xFF1A1B18)
private val KeyBorderColor = Color(0x33FFFFFF)
private val KeyShape = RoundedCornerShape(6.dp)

private object KeyboardTab {
    const val QWERTY = 0
    const val NUMPAD = 1
}

@Composable
fun UsbKeyboardScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val monetColor = remember { MonetHelper.getMonetPrimaryColor(context) }
    val configuration = LocalConfiguration.current

    var isReady by remember { mutableStateOf(UsbHidDriver.isDevicePresent()) }
    var statusText by remember { mutableStateOf(if (isReady) "Готово (/dev/hidg0)" else "Требуется root/init") }

    var currentTab by remember { mutableStateOf(KeyboardTab.QWERTY) }

    // Modifier states
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var winActive by remember { mutableStateOf(false) }
    var shiftActive by remember { mutableStateOf(false) }
    var capsActive by remember { mutableStateOf(false) }

    // Quick text input
    var showTextInput by remember { mutableStateOf(false) }
    var typedText by remember { mutableStateOf("") }
    var isSendingText by remember { mutableStateOf(false) }

    fun refreshStatus() {
        val present = UsbHidDriver.isDevicePresent()
        val udcState = UsbHidDriver.queryUdcState()
        val hostOk = udcState == "configured" || udcState == "addressed"
        isReady = present && hostOk
        val err = UsbHidDriver.lastError
        statusText = when {
            !present -> "Узел /dev/hidg0 не найден"
            hostOk -> "Готово (ПК подключен)"
            udcState == "not attached" -> "USB кабель не подключен к ПК"
            err != null -> err
            else -> "Ожидание хоста ($udcState)"
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            UsbHidDriver.ensureGadget()
        }
        while (true) {
            refreshStatus()
            delay(1500)
        }
    }

    fun computeModifiers(): Byte {
        var mask = 0
        if (ctrlActive) mask = mask or UsbHidKeyCodes.MOD_LCTRL.toInt()
        if (altActive) mask = mask or UsbHidKeyCodes.MOD_LALT.toInt()
        if (winActive) mask = mask or UsbHidKeyCodes.MOD_LMETA.toInt()
        if (shiftActive) mask = mask or UsbHidKeyCodes.MOD_LSHIFT.toInt()
        return mask.toByte()
    }

    fun onKeyPress(keyCode: Byte) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val mods = computeModifiers()
        val hadModifiers = (mods != 0.toByte())
        if (hadModifiers) {
            shiftActive = false
            ctrlActive = false
            altActive = false
            winActive = false
        }
        scope.launch(Dispatchers.IO) {
            val ok = UsbHidDriver.pressKey(mods, keyCode)
            if (hadModifiers) {
                UsbHidDriver.clearModifiers()
            }
            if (!ok) {
                withContext(Dispatchers.Main) {
                    refreshStatus()
                }
            }
        }
    }

    fun onComboPress(mods: Byte, keyCode: Byte) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        ctrlActive = false
        altActive = false
        winActive = false
        shiftActive = false
        scope.launch(Dispatchers.IO) {
            val ok = UsbHidDriver.sendCombo(mods, keyCode)
            UsbHidDriver.clearModifiers()
            if (!ok) {
                withContext(Dispatchers.Main) {
                    refreshStatus()
                }
            }
        }
    }

    val isUpper = shiftActive xor capsActive

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF131412)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            val totalHeight = maxHeight
            val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

            val headerShortcutsHeight = if (isPortrait) 90.dp else 66.dp
            val rawKeyHeight = (totalHeight - headerShortcutsHeight) / 7
            val keyHeight = when {
                isPortrait -> 36.dp
                rawKeyHeight < 32.dp -> 32.dp
                rawKeyHeight > 52.dp -> 52.dp
                else -> rawKeyHeight
            }

            val rawNumpadHeight = (totalHeight - headerShortcutsHeight) / 5
            val numpadKeyHeight = when {
                isPortrait -> 44.dp
                rawNumpadHeight < 36.dp -> 36.dp
                rawNumpadHeight > 64.dp -> 64.dp
                else -> rawNumpadHeight
            }

            val labelFontSize = if (isPortrait) 10.sp else 12.sp
            val subLabelFontSize = if (isPortrait) 7.sp else 9.sp
            val rowSpacing = 2.dp
            val screenPadding = if (isPortrait) 4.dp else 6.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = screenPadding, vertical = 2.dp)
            ) {
            // Header Bar
            if (isPortrait) {
                // Two compact rows in portrait mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "USB КЛАВИАТУРА",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isReady) Color(0xFF4CAF50) else Color(0xFFFF9800))
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showTextInput = !showTextInput },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Keyboard,
                                contentDescription = "Ввод текста",
                                tint = if (showTextInput) monetColor else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    UsbHidDriver.ensureGadget()
                                    withContext(Dispatchers.Main) {
                                        refreshStatus()
                                        Toast.makeText(context, "Статус обновлен", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Обновить узел",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Mode Tabs
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1F201D))
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (currentTab == KeyboardTab.QWERTY) monetColor else Color.Transparent)
                                .clickable { currentTab = KeyboardTab.QWERTY }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "QWERTY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentTab == KeyboardTab.QWERTY) Color.Black else Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (currentTab == KeyboardTab.NUMPAD) monetColor else Color.Transparent)
                                .clickable { currentTab = KeyboardTab.NUMPAD }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "NUMPAD",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentTab == KeyboardTab.NUMPAD) Color.Black else Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Text(
                        text = statusText,
                        color = if (isReady) Color(0xFFAAAAAA) else Color(0xFFFFB74D),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            ctrlActive = false
                            altActive = false
                            winActive = false
                            shiftActive = false
                            capsActive = false
                            scope.launch(Dispatchers.IO) {
                                UsbHidDriver.releaseAll()
                                withContext(Dispatchers.Main) {
                                    refreshStatus()
                                }
                            }
                            Toast.makeText(context, "Все клавиши сброшены", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2D1616),
                            contentColor = Color(0xFFFF5252)
                        ),
                        border = BorderStroke(1.dp, Color(0x66FF5252)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("СБРОС", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            } else {
                // Landscape Header (Single Row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "USB КЛАВИАТУРА",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isReady) Color(0xFF4CAF50) else Color(0xFFFF9800))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = statusText,
                                    color = if (isReady) Color(0xFFAAAAAA) else Color(0xFFFFB74D),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1F201D))
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (currentTab == KeyboardTab.QWERTY) monetColor else Color.Transparent)
                                    .clickable { currentTab = KeyboardTab.QWERTY }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "QWERTY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentTab == KeyboardTab.QWERTY) Color.Black else Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (currentTab == KeyboardTab.NUMPAD) monetColor else Color.Transparent)
                                    .clickable { currentTab = KeyboardTab.NUMPAD }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "NUMPAD",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentTab == KeyboardTab.NUMPAD) Color.Black else Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                ctrlActive = false
                                altActive = false
                                winActive = false
                                shiftActive = false
                                capsActive = false
                                scope.launch(Dispatchers.IO) {
                                    UsbHidDriver.releaseAll()
                                    withContext(Dispatchers.Main) {
                                        refreshStatus()
                                    }
                                }
                                Toast.makeText(context, "Все клавиши сброшены", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2D1616),
                                contentColor = Color(0xFFFF5252)
                            ),
                            border = BorderStroke(1.dp, Color(0x66FF5252)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = ButtonDefaults.ContentPadding,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("СБРОС", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { showTextInput = !showTextInput },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Keyboard,
                                contentDescription = "Ввод текста",
                                tint = if (showTextInput) monetColor else Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    UsbHidDriver.ensureGadget()
                                    withContext(Dispatchers.Main) {
                                        refreshStatus()
                                        Toast.makeText(context, "Статус /dev/hidg0 обновлен", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Обновить узел",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Quick PC Shortcuts Bar (Horizontal Scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ShortcutChip("Win", "Пуск") {
                    onComboPress(UsbHidKeyCodes.MOD_LMETA, UsbHidKeyCodes.KEY_NONE)
                }
                ShortcutChip("Win+R", "Выполнить") {
                    onComboPress(UsbHidKeyCodes.MOD_LMETA, UsbHidKeyCodes.KEY_R)
                }
                ShortcutChip("Alt+Tab", "Окна") {
                    onComboPress(UsbHidKeyCodes.MOD_LALT, UsbHidKeyCodes.KEY_TAB)
                }
                ShortcutChip("Alt+Shift", "Язык") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    ctrlActive = false; altActive = false; winActive = false; shiftActive = false
                    scope.launch(Dispatchers.IO) {
                        UsbHidDriver.sendSequentialModifiers(UsbHidKeyCodes.MOD_LALT, UsbHidKeyCodes.MOD_LSHIFT)
                    }
                }
                ShortcutChip("Ctrl+Shift", "Язык") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    ctrlActive = false; altActive = false; winActive = false; shiftActive = false
                    scope.launch(Dispatchers.IO) {
                        UsbHidDriver.sendSequentialModifiers(UsbHidKeyCodes.MOD_LCTRL, UsbHidKeyCodes.MOD_LSHIFT)
                    }
                }
                ShortcutChip("Win+D", "Рабочий стол") {
                    onComboPress(UsbHidKeyCodes.MOD_LMETA, UsbHidKeyCodes.KEY_D)
                }
                ShortcutChip("Win+E", "Проводник") {
                    onComboPress(UsbHidKeyCodes.MOD_LMETA, UsbHidKeyCodes.KEY_E)
                }
                ShortcutChip("Win+L", "Блокировка") {
                    onComboPress(UsbHidKeyCodes.MOD_LMETA, UsbHidKeyCodes.KEY_L)
                }
                ShortcutChip("Ctrl+Alt+Del", "Безопасность") {
                    onComboPress((UsbHidKeyCodes.MOD_LCTRL.toInt() or UsbHidKeyCodes.MOD_LALT.toInt()).toByte(), UsbHidKeyCodes.KEY_DELETE)
                }
                ShortcutChip("Ctrl+Shift+Esc", "Диспетчер") {
                    onComboPress((UsbHidKeyCodes.MOD_LCTRL.toInt() or UsbHidKeyCodes.MOD_LSHIFT.toInt()).toByte(), UsbHidKeyCodes.KEY_ESC)
                }
                ShortcutChip("Alt+F4", "Закрыть") {
                    onComboPress(UsbHidKeyCodes.MOD_LALT, UsbHidKeyCodes.KEY_F4)
                }
                ShortcutChip("Ctrl+C", "Копия") {
                    onComboPress(UsbHidKeyCodes.MOD_LCTRL, UsbHidKeyCodes.KEY_C)
                }
                ShortcutChip("Ctrl+V", "Вставка") {
                    onComboPress(UsbHidKeyCodes.MOD_LCTRL, UsbHidKeyCodes.KEY_V)
                }
                ShortcutChip("Ctrl+Z", "Отмена") {
                    onComboPress(UsbHidKeyCodes.MOD_LCTRL, UsbHidKeyCodes.KEY_Z)
                }
                ShortcutChip("Ctrl+A", "Все") {
                    onComboPress(UsbHidKeyCodes.MOD_LCTRL, UsbHidKeyCodes.KEY_A)
                }
            }

            // Expandable Quick Text Bar
            AnimatedVisibility(visible = showTextInput) {
                Surface(
                    color = Color(0xFF1B1D19),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = typedText,
                            onValueChange = { typedText = it },
                            placeholder = { Text("Печать текста на ПК (RU / EN)...", fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = monetColor,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (typedText.isNotEmpty() && !isSendingText) {
                                    isSendingText = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val textToSend = typedText
                                    scope.launch(Dispatchers.IO) {
                                        val ok = UsbHidDriver.typeText(textToSend)
                                        withContext(Dispatchers.Main) {
                                            isSendingText = false
                                            if (ok) {
                                                typedText = ""
                                                Toast.makeText(context, "Отправлено в USB", Toast.LENGTH_SHORT).show()
                                            } else {
                                                refreshStatus()
                                                Toast.makeText(context, UsbHidDriver.lastError ?: "Ошибка ввода", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = typedText.isNotEmpty() && !isSendingText,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = monetColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = "Печатать",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Function Keys Row (Horizontal Scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing)
            ) {
                KeyCap(label = "ESC", modifier = Modifier.width(if (isPortrait) 46.dp else 54.dp), bg = Color(0xFF382222), textColor = Color(0xFFFF8A80), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_ESC) })
                KeyCap(label = "F1", modifier = Modifier.width(if (isPortrait) 40.dp else 50.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F1) })
                KeyCap(label = "F2", modifier = Modifier.width(if (isPortrait) 40.dp else 50.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F2) })
                KeyCap(label = "F3", modifier = Modifier.width(if (isPortrait) 40.dp else 50.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F3) })
                KeyCap(label = "F4", modifier = Modifier.width(if (isPortrait) 40.dp else 50.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F4) })
                KeyCap(label = "F5", modifier = Modifier.width(if (isPortrait) 40.dp else 50.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F5) })
                KeyCap(label = "F6", modifier = Modifier.width(if (isPortrait) 40.dp else 50.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F6) })
                KeyCap(label = "F7", modifier = Modifier.width(if (isPortrait) 40.dp else 50.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F7) })
                KeyCap(label = "F8", modifier = Modifier.width(if (isPortrait) 40.dp else 50.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F8) })
                KeyCap(label = "F9", modifier = Modifier.width(if (isPortrait) 40.dp else 50.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F9) })
                KeyCap(label = "F10", modifier = Modifier.width(if (isPortrait) 42.dp else 50.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F10) })
                KeyCap(label = "F11", modifier = Modifier.width(if (isPortrait) 42.dp else 50.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F11) })
                KeyCap(label = "F12", modifier = Modifier.width(if (isPortrait) 42.dp else 50.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F12) })
                KeyCap(label = "PRTSC", modifier = Modifier.width(if (isPortrait) 48.dp else 56.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_SYSRQ) })
                KeyCap(label = "SCRLK", modifier = Modifier.width(if (isPortrait) 48.dp else 56.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_SCROLLLOCK) })
                KeyCap(label = "PAUSE", modifier = Modifier.width(if (isPortrait) 48.dp else 56.dp), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_PAUSE) })
                KeyCap(label = "DEL", modifier = Modifier.width(if (isPortrait) 46.dp else 54.dp), isRepeatable = true, bg = Color(0xFF382222), textColor = Color(0xFFFF8A80), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_DELETE) })
            }

            if (currentTab == KeyboardTab.QWERTY) {
                // Navigation row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(rowSpacing)
                ) {
                    KeyCap(label = "INS", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_INSERT) })
                    KeyCap(label = if (isPortrait) "HM" else "HOME", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_HOME) })
                    KeyCap(label = "END", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_END) })
                    KeyCap(label = if (isPortrait) "PU" else "PGUP", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_PAGEUP) })
                    KeyCap(label = if (isPortrait) "PD" else "PGDN", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_PAGEDOWN) })
                    KeyCap(label = "←", modifier = Modifier.weight(1f), isRepeatable = true, bg = KeyBgSpecial, keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_LEFT) })
                    KeyCap(label = "↑", modifier = Modifier.weight(1f), isRepeatable = true, bg = KeyBgSpecial, keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_UP) })
                    KeyCap(label = "↓", modifier = Modifier.weight(1f), isRepeatable = true, bg = KeyBgSpecial, keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_DOWN) })
                    KeyCap(label = "→", modifier = Modifier.weight(1f), isRepeatable = true, bg = KeyBgSpecial, keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_RIGHT) })
                }

                // Number Row: ` 1 2 3 4 5 6 7 8 9 0 - = Backspace
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(rowSpacing)
                ) {
                    KeyCap(label = if (shiftActive) "~" else "`", subLabel = if (isUpper) "Ё" else "ё", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_GRAVE) })
                    KeyCap(label = if (shiftActive) "!" else "1", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_1) })
                    KeyCap(label = if (shiftActive) "@" else "2", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_2) })
                    KeyCap(label = if (shiftActive) "#" else "3", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_3) })
                    KeyCap(label = if (shiftActive) "$" else "4", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_4) })
                    KeyCap(label = if (shiftActive) "%" else "5", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_5) })
                    KeyCap(label = if (shiftActive) "^" else "6", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_6) })
                    KeyCap(label = if (shiftActive) "&" else "7", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_7) })
                    KeyCap(label = if (shiftActive) "*" else "8", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_8) })
                    KeyCap(label = if (shiftActive) "(" else "9", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_9) })
                    KeyCap(label = if (shiftActive) ")" else "0", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_0) })
                    KeyCap(label = if (shiftActive) "_" else "-", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_MINUS) })
                    KeyCap(label = if (shiftActive) "+" else "=", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_EQUAL) })
                    KeyCap(label = "BACK", modifier = Modifier.weight(if (isPortrait) 1.3f else 1.5f), isRepeatable = true, bg = KeyBgSpecial, keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_BACKSPACE) })
                }

                // QWERTY Row 1: Tab Q W E R T Y U I O P [ ] \
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(rowSpacing)
                ) {
                    KeyCap(label = if (isPortrait) "⇥" else "TAB", modifier = Modifier.weight(if (isPortrait) 1.2f else 1.3f), bg = KeyBgSpecial, keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_TAB) })
                    KeyCap(label = if (isUpper) "Q" else "q", subLabel = if (isUpper) "Й" else "й", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_Q) })
                    KeyCap(label = if (isUpper) "W" else "w", subLabel = if (isUpper) "Ц" else "ц", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_W) })
                    KeyCap(label = if (isUpper) "E" else "e", subLabel = if (isUpper) "У" else "у", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_E) })
                    KeyCap(label = if (isUpper) "R" else "r", subLabel = if (isUpper) "К" else "к", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_R) })
                    KeyCap(label = if (isUpper) "T" else "t", subLabel = if (isUpper) "Е" else "е", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_T) })
                    KeyCap(label = if (isUpper) "Y" else "y", subLabel = if (isUpper) "Н" else "н", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_Y) })
                    KeyCap(label = if (isUpper) "U" else "u", subLabel = if (isUpper) "Г" else "г", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_U) })
                    KeyCap(label = if (isUpper) "I" else "i", subLabel = if (isUpper) "Ш" else "ш", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_I) })
                    KeyCap(label = if (isUpper) "O" else "o", subLabel = if (isUpper) "Щ" else "щ", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_O) })
                    KeyCap(label = if (isUpper) "P" else "p", subLabel = if (isUpper) "З" else "з", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_P) })
                    KeyCap(label = if (shiftActive) "{" else "[", subLabel = if (isUpper) "Х" else "х", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_LEFTBRACE) })
                    KeyCap(label = if (shiftActive) "}" else "]", subLabel = if (isUpper) "Ъ" else "ъ", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_RIGHTBRACE) })
                    KeyCap(label = if (shiftActive) "|" else "\\", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_BACKSLASH) })
                }

                // QWERTY Row 2: Caps A S D F G H J K L ; ' Enter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(rowSpacing)
                ) {
                    KeyCap(
                        label = if (isPortrait) "⇪" else "CAPS",
                        modifier = Modifier.weight(if (isPortrait) 1.2f else 1.4f),
                        bg = if (capsActive) monetColor else KeyBgSpecial,
                        textColor = if (capsActive) Color.Black else Color.White,
                        keyHeight = keyHeight,
                        labelFontSize = labelFontSize,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            capsActive = !capsActive
                            scope.launch(Dispatchers.IO) {
                                UsbHidDriver.pressKey(0, UsbHidKeyCodes.KEY_CAPSLOCK)
                            }
                        }
                    )
                    KeyCap(label = if (isUpper) "A" else "a", subLabel = if (isUpper) "Ф" else "ф", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_A) })
                    KeyCap(label = if (isUpper) "S" else "s", subLabel = if (isUpper) "Ы" else "ы", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_S) })
                    KeyCap(label = if (isUpper) "D" else "d", subLabel = if (isUpper) "В" else "в", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_D) })
                    KeyCap(label = if (isUpper) "F" else "f", subLabel = if (isUpper) "А" else "а", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_F) })
                    KeyCap(label = if (isUpper) "G" else "g", subLabel = if (isUpper) "П" else "п", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_G) })
                    KeyCap(label = if (isUpper) "H" else "h", subLabel = if (isUpper) "Р" else "р", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_H) })
                    KeyCap(label = if (isUpper) "J" else "j", subLabel = if (isUpper) "О" else "о", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_J) })
                    KeyCap(label = if (isUpper) "K" else "k", subLabel = if (isUpper) "Л" else "л", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_K) })
                    KeyCap(label = if (isUpper) "L" else "l", subLabel = if (isUpper) "Д" else "д", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_L) })
                    KeyCap(label = if (shiftActive) ":" else ";", subLabel = if (isUpper) "Ж" else "ж", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_SEMICOLON) })
                    KeyCap(label = if (shiftActive) "\"" else "'", subLabel = if (isUpper) "Э" else "э", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_APOSTROPHE) })
                    KeyCap(label = if (isPortrait) "⏎" else "⏎ ENTER", modifier = Modifier.weight(if (isPortrait) 1.4f else 1.6f), bg = Color(0xFF1E382A), textColor = Color(0xFF69F0AE), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_ENTER) })
                }

                // QWERTY Row 3: Shift Z X C V B N M , . / Shift
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(rowSpacing)
                ) {
                    KeyCap(
                        label = if (isPortrait) "⇧" else "SHIFT",
                        modifier = Modifier.weight(if (isPortrait) 1.3f else 1.5f),
                        bg = if (shiftActive) monetColor else KeyBgSpecial,
                        textColor = if (shiftActive) Color.Black else Color.White,
                        keyHeight = keyHeight,
                        labelFontSize = labelFontSize,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            shiftActive = !shiftActive
                            scope.launch(Dispatchers.IO) {
                                UsbHidDriver.setModifier(UsbHidKeyCodes.MOD_LSHIFT, shiftActive)
                            }
                        }
                    )
                    KeyCap(label = if (isUpper) "Z" else "z", subLabel = if (isUpper) "Я" else "я", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_Z) })
                    KeyCap(label = if (isUpper) "X" else "x", subLabel = if (isUpper) "Ч" else "ч", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_X) })
                    KeyCap(label = if (isUpper) "C" else "c", subLabel = if (isUpper) "С" else "с", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_C) })
                    KeyCap(label = if (isUpper) "V" else "v", subLabel = if (isUpper) "М" else "м", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_V) })
                    KeyCap(label = if (isUpper) "B" else "b", subLabel = if (isUpper) "И" else "и", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_B) })
                    KeyCap(label = if (isUpper) "N" else "n", subLabel = if (isUpper) "Т" else "т", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_N) })
                    KeyCap(label = if (isUpper) "M" else "m", subLabel = if (isUpper) "Ь" else "ь", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_M) })
                    KeyCap(label = if (shiftActive) "<" else ",", subLabel = if (isUpper) "Б" else "б", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_COMMA) })
                    KeyCap(label = if (shiftActive) ">" else ".", subLabel = if (isUpper) "Ю" else "ю", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, subLabelFontSize = subLabelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_DOT) })
                    KeyCap(label = if (shiftActive) "?" else "/", modifier = Modifier.weight(1f), keyHeight = keyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_SLASH) })
                    KeyCap(
                        label = if (isPortrait) "⇧" else "SHIFT",
                        modifier = Modifier.weight(if (isPortrait) 1.3f else 1.5f),
                        bg = if (shiftActive) monetColor else KeyBgSpecial,
                        textColor = if (shiftActive) Color.Black else Color.White,
                        keyHeight = keyHeight,
                        labelFontSize = labelFontSize,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            shiftActive = !shiftActive
                            scope.launch(Dispatchers.IO) {
                                UsbHidDriver.setModifier(UsbHidKeyCodes.MOD_LSHIFT, shiftActive)
                            }
                        }
                    )
                }

                // Bottom Row: Ctrl, Win, Alt, Space, Alt, Menu, Ctrl
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(rowSpacing)
                ) {
                    KeyCap(
                        label = if (isPortrait) "CTL" else "CTRL",
                        modifier = Modifier.weight(if (isPortrait) 1.1f else 1.3f),
                        bg = if (ctrlActive) monetColor else KeyBgSpecial,
                        textColor = if (ctrlActive) Color.Black else Color.White,
                        keyHeight = keyHeight,
                        labelFontSize = labelFontSize,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            ctrlActive = !ctrlActive
                            scope.launch(Dispatchers.IO) {
                                UsbHidDriver.setModifier(UsbHidKeyCodes.MOD_LCTRL, ctrlActive)
                            }
                        }
                    )
                    KeyCap(
                        label = "WIN",
                        modifier = Modifier.weight(1.0f),
                        bg = if (winActive) monetColor else KeyBgSpecial,
                        textColor = if (winActive) Color.Black else Color.White,
                        keyHeight = keyHeight,
                        labelFontSize = labelFontSize,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            winActive = !winActive
                            scope.launch(Dispatchers.IO) {
                                UsbHidDriver.setModifier(UsbHidKeyCodes.MOD_LMETA, winActive)
                            }
                        }
                    )
                    KeyCap(
                        label = "ALT",
                        modifier = Modifier.weight(1.0f),
                        bg = if (altActive) monetColor else KeyBgSpecial,
                        textColor = if (altActive) Color.Black else Color.White,
                        keyHeight = keyHeight,
                        labelFontSize = labelFontSize,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            altActive = !altActive
                            scope.launch(Dispatchers.IO) {
                                UsbHidDriver.setModifier(UsbHidKeyCodes.MOD_LALT, altActive)
                            }
                        }
                    )
                    // Space bar
                    KeyCap(
                        label = "SPACE",
                        modifier = Modifier.weight(if (isPortrait) 3.5f else 4.0f),
                        isRepeatable = true,
                        bg = KeyBgNormal,
                        keyHeight = keyHeight,
                        labelFontSize = labelFontSize,
                        onClick = { onKeyPress(UsbHidKeyCodes.KEY_SPACE) }
                    )
                    KeyCap(
                        label = "ALT",
                        modifier = Modifier.weight(1.0f),
                        bg = if (altActive) monetColor else KeyBgSpecial,
                        textColor = if (altActive) Color.Black else Color.White,
                        keyHeight = keyHeight,
                        labelFontSize = labelFontSize,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            altActive = !altActive
                            scope.launch(Dispatchers.IO) {
                                UsbHidDriver.setModifier(UsbHidKeyCodes.MOD_RALT, altActive)
                            }
                        }
                    )
                    KeyCap(
                        label = if (isPortrait) "MNU" else "MENU",
                        modifier = Modifier.weight(1.0f),
                        bg = KeyBgSpecial,
                        keyHeight = keyHeight,
                        labelFontSize = labelFontSize,
                        onClick = { onKeyPress(UsbHidKeyCodes.KEY_APPLICATION) }
                    )
                    KeyCap(
                        label = if (isPortrait) "CTL" else "CTRL",
                        modifier = Modifier.weight(if (isPortrait) 1.1f else 1.3f),
                        bg = if (ctrlActive) monetColor else KeyBgSpecial,
                        textColor = if (ctrlActive) Color.Black else Color.White,
                        keyHeight = keyHeight,
                        labelFontSize = labelFontSize,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            ctrlActive = !ctrlActive
                            scope.launch(Dispatchers.IO) {
                                UsbHidDriver.setModifier(UsbHidKeyCodes.MOD_RCTRL, ctrlActive)
                            }
                        }
                    )
                }
            } else {
                // NUMPAD Tab: 17-Key NumPad + Navigation Block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left: Navigation & Edit cluster
                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            KeyCap(label = "INS", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_INSERT) })
                            KeyCap(label = if (isPortrait) "HM" else "HOME", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_HOME) })
                            KeyCap(label = if (isPortrait) "PU" else "PGUP", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_PAGEUP) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            KeyCap(label = "DEL", modifier = Modifier.weight(1f), isRepeatable = true, bg = Color(0xFF382222), textColor = Color(0xFFFF8A80), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_DELETE) })
                            KeyCap(label = "END", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_END) })
                            KeyCap(label = if (isPortrait) "PD" else "PGDN", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_PAGEDOWN) })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Arrow Cluster
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Spacer(modifier = Modifier.weight(1f))
                            KeyCap(label = "↑", modifier = Modifier.weight(1f), isRepeatable = true, bg = KeyBgSpecial, keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_UP) })
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            KeyCap(label = "←", modifier = Modifier.weight(1f), isRepeatable = true, bg = KeyBgSpecial, keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_LEFT) })
                            KeyCap(label = "↓", modifier = Modifier.weight(1f), isRepeatable = true, bg = KeyBgSpecial, keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_DOWN) })
                            KeyCap(label = "→", modifier = Modifier.weight(1f), isRepeatable = true, bg = KeyBgSpecial, keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_RIGHT) })
                        }
                    }

                    // Right: Standard 4x5 Mechanical NumPad
                    Column(
                        modifier = Modifier.weight(1.8f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            KeyCap(label = "NUM", modifier = Modifier.weight(1f), bg = KeyBgSpecial, keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_NUMLOCK) })
                            KeyCap(label = "/", modifier = Modifier.weight(1f), bg = KeyBgSpecial, keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KPSLASH) })
                            KeyCap(label = "*", modifier = Modifier.weight(1f), bg = KeyBgSpecial, keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KPASTERISK) })
                            KeyCap(label = "-", modifier = Modifier.weight(1f), bg = KeyBgSpecial, keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KPMINUS) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            KeyCap(label = "7", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KP7) })
                            KeyCap(label = "8", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KP8) })
                            KeyCap(label = "9", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KP9) })
                            KeyCap(label = "+", modifier = Modifier.weight(1f), bg = KeyBgSpecial, keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KPPLUS) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            KeyCap(label = "4", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KP4) })
                            KeyCap(label = "5", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KP5) })
                            KeyCap(label = "6", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KP6) })
                            KeyCap(label = "BACK", modifier = Modifier.weight(1f), isRepeatable = true, bg = KeyBgSpecial, keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_BACKSPACE) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            KeyCap(label = "1", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KP1) })
                            KeyCap(label = "2", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KP2) })
                            KeyCap(label = "3", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KP3) })
                            KeyCap(label = if (isPortrait) "⏎" else "⏎ ENTER", modifier = Modifier.weight(1f), bg = Color(0xFF1E382A), textColor = Color(0xFF69F0AE), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KPENTER) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            KeyCap(label = "0", modifier = Modifier.weight(2f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KP0) })
                            KeyCap(label = ".", modifier = Modifier.weight(1f), keyHeight = numpadKeyHeight, labelFontSize = labelFontSize, onClick = { onKeyPress(UsbHidKeyCodes.KEY_KPDOT) })
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ShortcutChip(
    shortcut: String,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF1D1F1B),
        border = BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = shortcut,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD54F),
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color(0xFFBBBBBB),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    isRepeatable: Boolean = false,
    bg: Color = KeyBgNormal,
    textColor: Color = Color.White,
    keyHeight: Dp = 30.dp,
    labelFontSize: TextUnit = 11.sp,
    subLabelFontSize: TextUnit = 8.sp,
    onClick: () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    DisposableEffect(isPressed, isRepeatable) {
        var repeatRunnable: Runnable? = null
        if (isPressed && isRepeatable) {
            repeatRunnable = object : Runnable {
                override fun run() {
                    currentOnClick()
                    handler.postDelayed(this, 50)
                }
            }
            handler.postDelayed(repeatRunnable, 350)
        }
        onDispose {
            handler.removeCallbacksAndMessages(null)
        }
    }

    Box(
        modifier = modifier
            .height(keyHeight)
            .clip(KeyShape)
            .background(bg)
            .border(BorderStroke(1.dp, KeyBorderColor), KeyShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (subLabel != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    color = textColor,
                    fontSize = labelFontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = subLabel,
                    color = Color(0x99FFFFFF),
                    fontSize = subLabelFontSize,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = label,
                color = textColor,
                fontSize = labelFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

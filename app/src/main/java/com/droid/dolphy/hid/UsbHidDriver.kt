package com.droid.dolphy.hid

import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

object UsbHidDriver {
    private const val TAG = "UsbHidDriver"
    const val HID_DEV = "/dev/hidg0"

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var isCableConnected: Boolean = false
        private set

    @Volatile
    var activeModifiers: Byte = 0
        private set

    private val pressedKeys = LinkedHashSet<Byte>()
    private var cachedFos: FileOutputStream? = null

    fun isDevicePresent(): Boolean {
        return File(HID_DEV).exists()
    }

    fun queryUdcState(): String {
        try {
            val f = File("/dev/udc_state")
            if (f.exists()) {
                val s = f.readText().trim()
                if (s.isNotEmpty()) return s
            }
        } catch (_: Throwable) {}
        return "unknown"
    }

    fun isHostConnected(): Boolean {
        val state = queryUdcState()
        return state == "configured" || state == "addressed"
    }

    fun ensureGadget(): Boolean {
        if (isDevicePresent()) {
            // Ensure permissions are open (chmod 666 and chcon null_device)
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 666 $HID_DEV 2>/dev/null; chcon u:object_r:null_device:s0 $HID_DEV 2>/dev/null")).waitFor()
            } catch (_: Exception) {}
            return true
        }
        return try {
            val script = """
                if [ ! -c /dev/hidg0 ]; then
                    mkdir -p /config/usb_gadget/g1/functions/hid.usb0
                    echo 1 > /config/usb_gadget/g1/functions/hid.usb0/protocol
                    echo 1 > /config/usb_gadget/g1/functions/hid.usb0/subclass
                    echo 8 > /config/usb_gadget/g1/functions/hid.usb0/report_length
                    printf "\x05\x01\x09\x06\xa1\x01\x05\x07\x19\xe0\x29\xe7\x15\x00\x25\x01\x75\x01\x95\x08\x81\x02\x95\x01\x75\x08\x81\x03\x95\x05\x75\x01\x05\x08\x19\x01\x29\x05\x91\x02\x95\x01\x75\x03\x91\x03\x95\x06\x75\x08\x15\x00\x25\x65\x05\x07\x19\x00\x29\x65\x81\x00\xc0" > /config/usb_gadget/g1/functions/hid.usb0/report_desc
                    UDC=${'$'}(cat /config/usb_gadget/g1/UDC 2>/dev/null)
                    if [ -z "${'$'}UDC" ]; then
                        UDC=${'$'}(ls /sys/class/udc 2>/dev/null | head -n 1)
                    fi
                    if [ -n "${'$'}UDC" ]; then
                        echo "" > /config/usb_gadget/g1/UDC 2>/dev/null
                        ln -s /config/usb_gadget/g1/functions/hid.usb0 /config/usb_gadget/g1/configs/b.1/hid.usb0 2>/dev/null
                        echo "${'$'}UDC" > /config/usb_gadget/g1/UDC
                    fi
                fi
                chmod 666 /dev/hidg0 2>/dev/null
                chcon u:object_r:null_device:s0 /dev/hidg0 2>/dev/null
            """.trimIndent()

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            process.waitFor()
            isDevicePresent()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize gadget", e)
            lastError = e.message
            false
        }
    }

    @Synchronized
    fun sendReport(report: ByteArray): Boolean {
        if (!isDevicePresent()) {
            lastError = "Узел /dev/hidg0 не найден"
            isCableConnected = false
            return false
        }
        if (report.size != 8) {
            lastError = "Неверная длина HID отчета (ожидается 8 байт)"
            return false
        }

        try {
            var fos = cachedFos
            if (fos == null) {
                fos = FileOutputStream(HID_DEV)
                cachedFos = fos
            }
            fos.write(report)
            fos.flush()
            lastError = null
            isCableConnected = true
            return true
        } catch (e: IOException) {
            try { cachedFos?.close() } catch (_: Exception) {}
            cachedFos = null

            val msg = (e.message ?: e.toString()).lowercase(Locale.ROOT)
            if (msg.contains("shutdown") || msg.contains("108") || msg.contains("broken pipe") || msg.contains("not connected")) {
                lastError = "USB кабель не подключен к ПК"
                isCableConnected = false
                return false
            }

            if (msg.contains("permission") || msg.contains("denied") || msg.contains("access")) {
                // Attempt one-time root chmod repair
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 666 $HID_DEV 2>/dev/null; chcon u:object_r:null_device:s0 $HID_DEV 2>/dev/null")).waitFor()
                    val retryFos = FileOutputStream(HID_DEV)
                    retryFos.write(report)
                    retryFos.flush()
                    cachedFos = retryFos
                    lastError = null
                    isCableConnected = true
                    return true
                } catch (retryEx: Exception) {
                    val retryMsg = (retryEx.message ?: retryEx.toString()).lowercase(Locale.ROOT)
                    if (retryMsg.contains("shutdown") || retryMsg.contains("108")) {
                        lastError = "USB кабель не подключен к ПК"
                        isCableConnected = false
                    } else {
                        lastError = "Ошибка доступа к /dev/hidg0"
                    }
                    return false
                }
            }

            lastError = e.message ?: "Ошибка записи"
            return false
        }
    }

    @Synchronized
    private fun updateReport(): Boolean {
        val report = ByteArray(8)
        report[0] = activeModifiers
        report[1] = 0
        var idx = 2
        for (k in pressedKeys) {
            if (idx < 8) {
                report[idx++] = k
            }
        }
        return sendReport(report)
    }

    @Synchronized
    fun setModifier(modifierMask: Byte, pressed: Boolean): Boolean {
        activeModifiers = if (pressed) {
            (activeModifiers.toInt() or modifierMask.toInt()).toByte()
        } else {
            (activeModifiers.toInt() and modifierMask.toInt().inv()).toByte()
        }
        return updateReport()
    }

    @Synchronized
    fun clearModifiers(): Boolean {
        activeModifiers = 0
        return updateReport()
    }

    @Synchronized
    fun keyDown(keyCode: Byte, extraModifiers: Byte = 0): Boolean {
        if (extraModifiers != 0.toByte()) {
            activeModifiers = (activeModifiers.toInt() or extraModifiers.toInt()).toByte()
        }
        if (keyCode != UsbHidKeyCodes.KEY_NONE) {
            pressedKeys.add(keyCode)
        }
        return updateReport()
    }

    @Synchronized
    fun keyUp(keyCode: Byte, modifiersToClear: Byte = 0): Boolean {
        if (keyCode != UsbHidKeyCodes.KEY_NONE) {
            pressedKeys.remove(keyCode)
        }
        if (modifiersToClear != 0.toByte()) {
            activeModifiers = (activeModifiers.toInt() and modifiersToClear.toInt().inv()).toByte()
        }
        return updateReport()
    }

    @Synchronized
    fun pressKey(modifiers: Byte, keyCode: Byte, delayMs: Long = 20): Boolean {
        val prevMods = activeModifiers
        val totalMods = (modifiers.toInt() or prevMods.toInt()).toByte()
        activeModifiers = totalMods
        if (keyCode != UsbHidKeyCodes.KEY_NONE) {
            pressedKeys.add(keyCode)
        }
        val downOk = updateReport()
        SystemClock.sleep(delayMs)

        if (keyCode != UsbHidKeyCodes.KEY_NONE) {
            pressedKeys.remove(keyCode)
        }
        activeModifiers = prevMods
        updateReport()
        return downOk
    }

    @Synchronized
    fun sendCombo(modifiers: Byte, keyCode: Byte, delayMs: Long = 25): Boolean {
        val prevMods = activeModifiers
        val totalMods = (modifiers.toInt() or prevMods.toInt()).toByte()
        activeModifiers = totalMods
        if (keyCode != UsbHidKeyCodes.KEY_NONE) {
            pressedKeys.add(keyCode)
        }
        val downOk = updateReport()
        SystemClock.sleep(delayMs)

        if (keyCode != UsbHidKeyCodes.KEY_NONE) {
            pressedKeys.remove(keyCode)
        }
        activeModifiers = prevMods
        updateReport()
        return downOk
    }

    @Synchronized
    fun sendSequentialModifiers(firstMod: Byte, secondMod: Byte, delayMs: Long = 20): Boolean {
        // Step 1: First modifier down
        activeModifiers = firstMod
        var ok = updateReport()
        SystemClock.sleep(delayMs)

        // Step 2: Second modifier down (both held)
        activeModifiers = (firstMod.toInt() or secondMod.toInt()).toByte()
        ok = updateReport() && ok
        SystemClock.sleep(delayMs + 10)

        // Step 3: Second modifier released, first modifier held
        activeModifiers = firstMod
        ok = updateReport() && ok
        SystemClock.sleep(delayMs)

        // Step 4: All modifiers released
        activeModifiers = 0
        ok = updateReport() && ok
        return ok
    }

    @Synchronized
    fun releaseAll(): Boolean {
        pressedKeys.clear()
        activeModifiers = 0
        try { cachedFos?.close() } catch (_: Exception) {}
        cachedFos = null
        return sendReport(ByteArray(8))
    }

    fun typeText(text: String, delayPerCharMs: Long = 15): Boolean {
        val isRussian = text.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        var allOk = true
        for (ch in text) {
            val mapping = UsbHidKeyCodes.charToHid(ch, isRussianLayout = isRussian)
            if (mapping != null) {
                val ok = pressKey(mapping.first, mapping.second, delayPerCharMs)
                if (!ok) allOk = false
                SystemClock.sleep(delayPerCharMs)
            }
        }
        releaseAll()
        return allOk
    }
}

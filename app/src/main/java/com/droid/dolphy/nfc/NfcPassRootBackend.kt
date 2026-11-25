package com.droid.dolphy.nfc

import android.content.Context

object NfcPassRootBackend {

    private fun getSuCommand(): String {
        return try {
            val p = ProcessBuilder("su", "-mm", "-c", "id").start()
            if (p.waitFor() == 0) "su -mm -c" else "su -c"
        } catch (_: Throwable) {
            "su -c"
        }
    }

    fun isRootAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id").start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Throwable) {
            false
        }
    }

    fun applyPassUid(context: Context, pass: NfcPassProfile): Pair<Boolean, String> {
        val cleanUid = pass.getCleanHex()
        val uidLen = cleanUid.length / 2
        if (uidLen != 4 && uidLen != 7 && uidLen != 10) {
            return Pair(false, "UID должен быть 4 байта (8 hex), 7 байт (14 hex) или 10 байт (20 hex)")
        }

        val uidBytesList = ArrayList<String>()
        val colonSb = StringBuilder()
        for (i in 0 until uidLen) {
            val hex = cleanUid.substring(i * 2, i * 2 + 2).lowercase()
            uidBytesList.add(hex)
            if (i > 0) colonSb.append(":")
            colonSb.append(hex.uppercase())
        }
        val uidCommaHex = uidBytesList.joinToString(", ")
        val uidColonHex = colonSb.toString()

        val sakHex = String.format("%02x", pass.sak and 0xFF)
        val uidLenHex = String.format("%02x", uidLen)
        val totalLenHex = String.format("%02x", 18 + uidLen)

        // Robust NCI LA_BIT_FRAME_SDD extraction: never allow 0x00
        val rawParts = pass.atqa.replace("-", ":").replace(" ", ":").split(":")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it.length <= 2 }
        val sddCandidates = rawParts.filter { it != "00" && it != "0" }
        val sddHex = when (uidLen) {
            7 -> sddCandidates.firstOrNull { it.startsWith("4") } ?: sddCandidates.firstOrNull() ?: "44"
            10 -> sddCandidates.firstOrNull { it.startsWith("8") || it.startsWith("4") } ?: sddCandidates.firstOrNull() ?: "88"
            else -> sddCandidates.firstOrNull { it.endsWith("4") || it.startsWith("0") } ?: sddCandidates.firstOrNull() ?: "04"
        }.padStart(2, '0').lowercase()

        NfcPassStorage.setActivePass(context, pass)
        try {
            val hcePrefs = context.getSharedPreferences("nfc_emulation_prefs", Context.MODE_PRIVATE)
            val json = org.json.JSONObject()
            json.put("id", pass.id)
            json.put("name", pass.name)
            json.put("url", "uid://${pass.getFormattedUid()}")
            hcePrefs.edit().putString("active_tag", json.toString()).apply()
        } catch (_: Throwable) {}

        if (isRootAvailable()) {
            try {
                val suCmd = getSuCommand()
                val script = """
                    mkdir -p /data/vendor/nfc
                    if [ ! -f /data/vendor/nfc/libnfc-hal-st.orig.conf ]; then
                        for _ in 1 2 3 4 5; do
                            grep -q "/vendor/etc/libnfc-hal-st.conf" /proc/mounts || break
                            umount -l /vendor/etc/libnfc-hal-st.conf 2>/dev/null || break
                        done
                        cp /vendor/etc/libnfc-hal-st.conf /data/vendor/nfc/libnfc-hal-st.orig.conf
                    fi
                    sed "s/CORE_CONF_PROP={ 20, 02, [0-9a-fA-F]*, [0-9a-fA-F]*,/CORE_CONF_PROP={ 20, 02, $totalLenHex, 06,\n        30, 01, $sddHex,\n        32, 01, $sakHex,\n        33, $uidLenHex, $uidCommaHex,/g" /data/vendor/nfc/libnfc-hal-st.orig.conf > /data/vendor/nfc/libnfc-hal-st.conf
                    chmod 644 /data/vendor/nfc/libnfc-hal-st.conf
                    chown nfc:nfc /data/vendor/nfc/libnfc-hal-st.conf 2>/dev/null
                    chcon u:object_r:vendor_configs_file:s0 /data/vendor/nfc/libnfc-hal-st.conf 2>/dev/null
                    for _ in 1 2 3 4 5; do
                        grep -q "/vendor/etc/libnfc-hal-st.conf" /proc/mounts || break
                        umount -l /vendor/etc/libnfc-hal-st.conf 2>/dev/null || break
                    done
                    mount -o bind /data/vendor/nfc/libnfc-hal-st.conf /vendor/etc/libnfc-hal-st.conf
                    echo "LA_NFCID1={$uidColonHex}" > /data/vendor/nfc/libnfc-nci-update.conf
                    echo "LA_SEL_INFO=0x$sakHex" >> /data/vendor/nfc/libnfc-nci-update.conf
                    echo "LA_BIT_FRAME_SDD=0x$sddHex" >> /data/vendor/nfc/libnfc-nci-update.conf
                    chmod 660 /data/vendor/nfc/libnfc-nci-update.conf
                    chown nfc:nfc /data/vendor/nfc/libnfc-nci-update.conf 2>/dev/null
                    kill -9 ${'$'}(pidof android.hardware.nfc-service.st com.android.nfc) 2>/dev/null || true
                    grep -q "33, $uidLenHex, $uidCommaHex" /vendor/etc/libnfc-hal-st.conf
                """.trimIndent()

                val args = if (suCmd.contains("-mm")) listOf("su", "-mm", "-c", script) else listOf("su", "-c", script)
                val process = ProcessBuilder(args).start()
                val exit = process.waitFor()
                if (exit == 0) {
                    return Pair(true, "UID ${pass.getFormattedUid()} активен (Root ST-NFC Hardware)")
                }
            } catch (_: Throwable) {}
        }

        return Pair(true, "UID ${pass.getFormattedUid()} активен (HCE Fallback)")
    }

    fun disablePassUid(context: Context): Boolean {
        NfcPassStorage.setActivePass(context, null)
        try {
            val hcePrefs = context.getSharedPreferences("nfc_emulation_prefs", Context.MODE_PRIVATE)
            hcePrefs.edit().remove("active_tag").apply()
        } catch (_: Throwable) {}

        if (isRootAvailable()) {
            try {
                val suCmd = getSuCommand()
                val script = """
                    for _ in 1 2 3 4 5; do
                        grep -q "/vendor/etc/libnfc-hal-st.conf" /proc/mounts || break
                        umount -l /vendor/etc/libnfc-hal-st.conf 2>/dev/null || break
                    done
                    rm -f /data/vendor/nfc/libnfc-hal-st.conf /data/vendor/nfc/libnfc-nci-update.conf
                    kill -9 ${'$'}(pidof android.hardware.nfc-service.st com.android.nfc) 2>/dev/null || true
                    ! grep -q "/vendor/etc/libnfc-hal-st.conf" /proc/mounts
                """.trimIndent()
                val args = if (suCmd.contains("-mm")) listOf("su", "-mm", "-c", script) else listOf("su", "-c", script)
                val process = ProcessBuilder(args).start()
                process.waitFor()
            } catch (_: Throwable) {}
        }
        return true
    }

    fun isUidEmulationMounted(): Boolean {
        return try {
            val suCmd = getSuCommand()
            val script = "grep -q /vendor/etc/libnfc-hal-st.conf /proc/mounts"
            val args = if (suCmd.contains("-mm")) listOf("su", "-mm", "-c", script) else listOf("su", "-c", script)
            val process = ProcessBuilder(args).start()
            process.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }
}

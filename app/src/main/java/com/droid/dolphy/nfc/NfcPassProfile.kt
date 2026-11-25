package com.droid.dolphy.nfc

data class NfcPassProfile(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val uidHex: String,
    val cardType: String = "MIFARE Classic 1K",
    val sak: Int = 0x08,
    val atqa: String = "00:04",
    val isActive: Boolean = false
) {
    fun getCleanHex(): String {
        return uidHex.filter { it in "0123456789ABCDEFabcdef" }.uppercase()
    }

    fun getUidBytes(): ByteArray {
        val clean = getCleanHex()
        if (clean.length % 2 != 0 || !clean.all { it in "0123456789ABCDEF" }) return ByteArray(0)
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val byteStr = clean.substring(i * 2, i * 2 + 2)
            result[i] = (byteStr.toIntOrNull(16) ?: 0).toByte()
        }
        return result
    }

    fun getFormattedUid(): String {
        val clean = getCleanHex()
        val sb = StringBuilder()
        for (i in clean.indices) {
            if (i > 0 && i % 2 == 0) sb.append(":")
            sb.append(clean[i])
        }
        return sb.toString()
    }

    val is4Byte: Boolean
        get() = getCleanHex().length == 8

    val is7Byte: Boolean
        get() = getCleanHex().length == 14

    val is10Byte: Boolean
        get() = getCleanHex().length == 20
}

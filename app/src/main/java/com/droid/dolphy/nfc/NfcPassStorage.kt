package com.droid.dolphy.nfc

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object NfcPassStorage {
    private const val PREFS_NAME = "dolphy_nfc_pass_prefs"
    private const val KEY_PASSES = "passes_list"
    private const val KEY_ACTIVE_PASS_ID = "active_pass_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPasses(context: Context): List<NfcPassProfile> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_PASSES, null)
        val activeId = prefs.getLong(KEY_ACTIVE_PASS_ID, -1L)

        if (jsonStr.isNullOrEmpty()) {
            val defaults = getDefaultPasses()
            savePasses(context, defaults)
            return defaults
        }

        return try {
            val array = JSONArray(jsonStr)
            val list = ArrayList<NfcPassProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optLong("id", System.currentTimeMillis() + i)
                val name = obj.optString("name", "Пропуск")
                val uid = obj.optString("uidHex", "DE:AD:BE:EF")
                val cardType = obj.optString("cardType", "MIFARE Classic 1K")
                val sak = obj.optInt("sak", 0x08)
                val atqa = obj.optString("atqa", "00:04")
                list.add(
                    NfcPassProfile(
                        id = id,
                        name = name,
                        uidHex = uid,
                        cardType = cardType,
                        sak = sak,
                        atqa = atqa,
                        isActive = (id == activeId)
                    )
                )
            }
            if (list.isEmpty()) {
                val defaults = getDefaultPasses()
                savePasses(context, defaults)
                defaults
            } else {
                list
            }
        } catch (_: Throwable) {
            getDefaultPasses()
        }
    }

    fun getDefaultPasses(): List<NfcPassProfile> {
        return listOf(
            NfcPassProfile(
                id = 1001L,
                name = "Работа (Офис)",
                uidHex = "DE:AD:BE:EF",
                cardType = "MIFARE Classic 1K",
                sak = 0x08,
                atqa = "00:04",
                isActive = false
            ),
            NfcPassProfile(
                id = 1002L,
                name = "Клуб (Вход)",
                uidHex = "04:5A:2B:1C:8D:7E:9F",
                cardType = "MIFARE Ultralight",
                sak = 0x00,
                atqa = "00:44",
                isActive = false
            ),
            NfcPassProfile(
                id = 1003L,
                name = "Домофон (Подъезд)",
                uidHex = "55:AA:33:CC",
                cardType = "MIFARE Classic 1K",
                sak = 0x08,
                atqa = "00:04",
                isActive = false
            ),
            NfcPassProfile(
                id = 1004L,
                name = "СКУД / Паркинг (10B)",
                uidHex = "04:1A:2B:3C:4D:5E:6F:70:81:92",
                cardType = "ISO-DEP (DESFire)",
                sak = 0x20,
                atqa = "03:44",
                isActive = false
            )
        )
    }

    fun savePass(context: Context, profile: NfcPassProfile) {
        val current = ArrayList(getPasses(context))
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        savePasses(context, current)
    }

    fun deletePass(context: Context, id: Long) {
        val current = getPasses(context).filter { it.id != id }
        val prefs = getPrefs(context)
        if (prefs.getLong(KEY_ACTIVE_PASS_ID, -1L) == id) {
            prefs.edit().remove(KEY_ACTIVE_PASS_ID).apply()
        }
        savePasses(context, current)
    }

    fun getActivePass(context: Context): NfcPassProfile? {
        val passes = getPasses(context)
        val activeId = getPrefs(context).getLong(KEY_ACTIVE_PASS_ID, -1L)
        return passes.firstOrNull { it.id == activeId }
    }

    fun setActivePass(context: Context, profile: NfcPassProfile?) {
        val editor = getPrefs(context).edit()
        if (profile == null) {
            editor.remove(KEY_ACTIVE_PASS_ID)
        } else {
            editor.putLong(KEY_ACTIVE_PASS_ID, profile.id)
        }
        editor.apply()
    }

    private fun savePasses(context: Context, list: List<NfcPassProfile>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("uidHex", item.uidHex)
            obj.put("cardType", item.cardType)
            obj.put("sak", item.sak)
            obj.put("atqa", item.atqa)
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_PASSES, array.toString()).apply()
    }
}

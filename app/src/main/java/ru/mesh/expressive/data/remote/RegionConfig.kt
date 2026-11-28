package ru.mesh.expressive.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Конфигурация региона ФГИС «Моя школа» / МЭШ.
 */
data class RegionConfig(
    val rgn: Int,
    val name: String,
    val shortName: String,
    val apiSchoolUrl: String,
    val apiBaseUrl: String,
    val authBaseUrl: String = apiSchoolUrl,
    val usesMosRuOAuth: Boolean = false
) {
    val apiHost: String
        get() = apiSchoolUrl.toHttpUrlOrNull()?.host ?: "school.mos.ru"

    val portalHost: String
        get() = apiBaseUrl.toHttpUrlOrNull()?.host ?: apiHost
}

object RegionRegistry {
    const val REGION_MOSCOW = 77
    const val REGION_CUSTOM = 0

    val MOSCOW = RegionConfig(
        rgn = 77,
        name = "Москва (МЭШ)",
        shortName = "Москва",
        apiSchoolUrl = "https://school.mos.ru",
        apiBaseUrl = "https://dnevnik.mos.ru",
        authBaseUrl = "https://school.mos.ru",
        usesMosRuOAuth = true
    )

    val PRESET_REGIONS: List<RegionConfig> = listOf(
        MOSCOW,
        RegionConfig(
            rgn = 50,
            name = "Московская область",
            shortName = "Подмосковье",
            apiSchoolUrl = "https://authedu.mosreg.ru",
            apiBaseUrl = "https://myschool.mosreg.ru"
        ),
        RegionConfig(
            rgn = 16,
            name = "Республика Татарстан",
            shortName = "Татарстан",
            apiSchoolUrl = "https://ms-edu.tatar.ru",
            apiBaseUrl = "https://school-edu.tatar.ru"
        ),
        RegionConfig(
            rgn = 72,
            name = "Тюменская область",
            shortName = "Тюмень",
            apiSchoolUrl = "https://myschool.72to.ru",
            apiBaseUrl = "https://education.72to.ru"
        ),
        RegionConfig(
            rgn = 40,
            name = "Калужская область",
            shortName = "Калуга",
            apiSchoolUrl = "https://education.admoblkaluga.ru",
            apiBaseUrl = "https://dnevnik.admoblkaluga.ru"
        ),
        RegionConfig(
            rgn = 5,
            name = "Республика Дагестан",
            shortName = "Дагестан",
            apiSchoolUrl = "https://myschool.05edu.ru",
            apiBaseUrl = "https://education.05edu.ru"
        )
    )

    @Volatile
    var currentRegion: RegionConfig = MOSCOW
        private set

    fun findById(rgn: Int, customUrl: String? = null): RegionConfig {
        if (rgn == REGION_CUSTOM) {
            val normalized = normalizeBaseUrl(customUrl)
            if (normalized != null) {
                return RegionConfig(
                    rgn = REGION_CUSTOM,
                    name = "Другой регион",
                    shortName = normalized.toHttpUrlOrNull()?.host ?: "Свой сервер",
                    apiSchoolUrl = normalized,
                    apiBaseUrl = normalized,
                    authBaseUrl = normalized,
                    usesMosRuOAuth = false
                )
            }
        }
        return PRESET_REGIONS.firstOrNull { it.rgn == rgn } ?: MOSCOW
    }

    fun setActiveRegion(rgn: Int, customUrl: String? = null): RegionConfig {
        val resolved = findById(rgn, customUrl)
        currentRegion = resolved
        return resolved
    }

    fun normalizeBaseUrl(raw: String?): String? {
        val trimmed = raw?.trim()?.trimEnd('/') ?: return null
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        return "${parsed.scheme}://${parsed.host}"
    }

    fun rewriteUrl(url: String): String {
        val target = currentRegion.apiSchoolUrl.trimEnd('/')
        if (target == "https://school.mos.ru") return url
        return when {
            url.startsWith("https://school.mos.ru") -> target + url.removePrefix("https://school.mos.ru")
            url.startsWith("http://school.mos.ru") -> target + url.removePrefix("http://school.mos.ru")
            url.startsWith("/") -> "$target$url"
            else -> url
        }
    }

    fun isKnownSchoolHost(host: String): Boolean {
        if (host.contains("school.mos.ru") || host.contains("mos.ru")) return true
        if (host == currentRegion.apiHost || host == currentRegion.portalHost) return true
        return PRESET_REGIONS.any { host == it.apiHost || host == it.portalHost }
    }
}

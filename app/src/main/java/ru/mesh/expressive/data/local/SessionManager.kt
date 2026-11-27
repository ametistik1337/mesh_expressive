package ru.mesh.expressive.data.local

import android.content.Context
import android.content.SharedPreferences

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.mesh.expressive.data.model.*

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mesh_expressive_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var authToken: String?
        get() = prefs.getString("auth_token", null)
        set(value) = prefs.edit().putString("auth_token", value).apply()

    var jwtToken: String?
        get() = authToken
        set(value) { authToken = value }

    var oauthClientId: String?
        get() = prefs.getString("oauth_client_id", null)
        set(value) = prefs.edit().putString("oauth_client_id", value).apply()

    var oauthClientSecret: String?
        get() = prefs.getString("oauth_client_secret", null)
        set(value) = prefs.edit().putString("oauth_client_secret", value).apply()

    var oauthCodeVerifier: String?
        get() = prefs.getString("oauth_code_verifier", null)
        set(value) = prefs.edit().putString("oauth_code_verifier", value).apply()

    var oauthState: String?
        get() = prefs.getString("oauth_state", null)
        set(value) = prefs.edit().putString("oauth_state", value).apply()

    var mosAccessToken: String?
        get() = prefs.getString("mos_access_token", null)
        set(value) = prefs.edit().putString("mos_access_token", value).apply()

    var mosRefreshToken: String?
        get() = prefs.getString("mos_refresh_token", null)
        set(value) = prefs.edit().putString("mos_refresh_token", value).apply()

    var meshRefreshToken: String?
        get() = prefs.getString("mesh_refresh_token", null)
        set(value) = prefs.edit().putString("mesh_refresh_token", value).apply()

    var personId: Long
        get() = prefs.getLong("person_id", 0L)
        set(value) = prefs.edit().putLong("person_id", value).apply()

    var profileId: String
        get() = prefs.getString("profile_id", "") ?: ""
        set(value) = prefs.edit().putString("profile_id", value).apply()

    var studentId: Long
        get() = prefs.getLong("student_id", 0L)
        set(value) = prefs.edit().putLong("student_id", value).apply()

    var classUid: String
        get() = prefs.getString("class_uid", "") ?: ""
        set(value) = prefs.edit().putString("class_uid", value).apply()

    var classUnitId: Long
        get() = prefs.getLong("class_unit_id", 0L)
        set(value) = prefs.edit().putLong("class_unit_id", value).apply()

    val isLoggedIn: Boolean
        get() = !authToken.isNullOrBlank()

    var infiniteStarsOverride: Boolean
        get() = prefs.getBoolean("infinite_stars_override", false)
        set(value) = prefs.edit().putBoolean("infinite_stars_override", value).apply()

    var isMonetEnabled: Boolean
        get() = prefs.getBoolean("is_monet_enabled", true)
        set(value) = prefs.edit().putBoolean("is_monet_enabled", value).apply()

    var isCompactSchedule: Boolean
        get() = prefs.getBoolean("is_compact_schedule", false)
        set(value) = prefs.edit().putBoolean("is_compact_schedule", value).apply()

    var showWeightedGpa: Boolean
        get() = prefs.getBoolean("show_weighted_gpa", true)
        set(value) = prefs.edit().putBoolean("show_weighted_gpa", value).apply()

    var hideCompletedQuests: Boolean
        get() = prefs.getBoolean("hide_completed_quests", false)
        set(value) = prefs.edit().putBoolean("hide_completed_quests", value).apply()

    var enableSpringPhysics: Boolean
        get() = prefs.getBoolean("enable_spring_physics", true)
        set(value) = prefs.edit().putBoolean("enable_spring_physics", value).apply()


    var hideEmptyScheduleDays: Boolean
        get() = prefs.getBoolean("hide_empty_schedule_days", false)
        set(value) = prefs.edit().putBoolean("hide_empty_schedule_days", value).apply()

    var gpaTargetScore: Float
        get() = prefs.getFloat("gpa_target_score", 4.60f)
        set(value) = prefs.edit().putFloat("gpa_target_score", value).apply()

    var serverRoundingLimit3: Float
        get() = prefs.getFloat("server_rounding_limit_3", 2.60f)
        set(value) = prefs.edit().putFloat("server_rounding_limit_3", value).apply()

    var serverRoundingLimit4: Float
        get() = prefs.getFloat("server_rounding_limit_4", 3.60f)
        set(value) = prefs.edit().putFloat("server_rounding_limit_4", value).apply()

    var serverRoundingLimit5: Float
        get() = prefs.getFloat("server_rounding_limit_5", 4.60f)
        set(value) = prefs.edit().putFloat("server_rounding_limit_5", value).apply()

    var hasServerRoundingLimits: Boolean
        get() = prefs.getBoolean("has_server_rounding_limits", false)
        set(value) = prefs.edit().putBoolean("has_server_rounding_limits", value).apply()

    var isManualRoundingOverride: Boolean
        get() = prefs.getBoolean("is_manual_rounding_override", false)
        set(value) = prefs.edit().putBoolean("is_manual_rounding_override", value).apply()

    var manualRoundingLimit3: Float
        get() = prefs.getFloat("manual_rounding_limit_3", 2.60f)
        set(value) = prefs.edit().putFloat("manual_rounding_limit_3", value).apply()

    var manualRoundingLimit4: Float
        get() = prefs.getFloat("manual_rounding_limit_4", 3.60f)
        set(value) = prefs.edit().putFloat("manual_rounding_limit_4", value).apply()

    var manualRoundingLimit5: Float
        get() = prefs.getFloat("manual_rounding_limit_5", 4.60f)
        set(value) = prefs.edit().putFloat("manual_rounding_limit_5", value).apply()

    var autoRefreshIntervalMinutes: Int
        get() = prefs.getInt("auto_refresh_interval_minutes", 15)
        set(value) = prefs.edit().putInt("auto_refresh_interval_minutes", value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean("is_onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("is_onboarding_completed", value).apply()

    var isAutoUpdatesAndCounterEnabled: Boolean
        get() = prefs.getBoolean("is_auto_updates_counter_enabled", true)
        set(value) = prefs.edit().putBoolean("is_auto_updates_counter_enabled", value).apply()

    var dismissedUpdateVersion: String
        get() = prefs.getString("dismissed_update_version", "") ?: ""
        set(value) = prefs.edit().putString("dismissed_update_version", value).apply()

    var marksViewMode: String
        get() = prefs.getString("marks_view_mode", "BY_SUBJECT") ?: "BY_SUBJECT"
        set(value) = prefs.edit().putString("marks_view_mode", value).apply()

    var startTab: String
        get() = prefs.getString("start_tab", "DASHBOARD") ?: "DASHBOARD"
        set(value) = prefs.edit().putString("start_tab", value).apply()

    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean("haptic_feedback_enabled", true)
        set(value) = prefs.edit().putBoolean("haptic_feedback_enabled", value).apply()

    var hideCompletedHomework: Boolean
        get() = prefs.getBoolean("hide_completed_homework", false)
        set(value) = prefs.edit().putBoolean("hide_completed_homework", value).apply()

    var themeMode: String
        get() = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    var isAmoledDark: Boolean
        get() = prefs.getBoolean("is_amoled_dark", false)
        set(value) = prefs.edit().putBoolean("is_amoled_dark", value).apply()

    var studyPeriodMode: String
        get() = prefs.getString("study_period_mode", "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString("study_period_mode", value).apply()

    // ======================== Cache Storage ========================

    private fun saveJson(key: String, value: Any?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
        } else {
            try {
                val json = gson.toJson(value)
                prefs.edit().putString(key, json).apply()
            } catch (_: Exception) {}
        }
    }

    private inline fun <reified T> getJson(key: String): T? {
        val json = prefs.getString(key, null) ?: return null
        return try {
            val type = object : TypeToken<T>() {}.type
            gson.fromJson<T>(json, type)
        } catch (_: Exception) {
            null
        }
    }

    var cachedProfile: StudentProfile?
        get() = getJson("cached_profile")
        set(value) = saveJson("cached_profile", value)

    var cachedScheduleToday: List<LessonScheduleItem>?
        get() {
            val json = prefs.getString("cached_schedule_today", null) ?: return null
            return try {
                val type = object : TypeToken<List<LessonScheduleItem>>() {}.type
                gson.fromJson(json, type)
            } catch (_: Exception) {
                null
            }
        }
        set(value) = saveJson("cached_schedule_today", value)

    var cachedScheduleTomorrow: List<LessonScheduleItem>?
        get() {
            val json = prefs.getString("cached_schedule_tomorrow", null) ?: return null
            return try {
                val type = object : TypeToken<List<LessonScheduleItem>>() {}.type
                gson.fromJson(json, type)
            } catch (_: Exception) {
                null
            }
        }
        set(value) = saveJson("cached_schedule_tomorrow", value)

    var cachedWeekSchedule: Map<String, List<LessonScheduleItem>>?
        get() {
            val json = prefs.getString("cached_week_schedule", null) ?: return null
            return try {
                val type = object : TypeToken<Map<String, List<LessonScheduleItem>>>() {}.type
                gson.fromJson(json, type)
            } catch (_: Exception) {
                null
            }
        }
        set(value) = saveJson("cached_week_schedule", value)

    var cachedHomeworkList: List<HomeworkItem>?
        get() = getJson("cached_homework_list")
        set(value) = saveJson("cached_homework_list", value)

    var cachedSubjectSummaries: List<SubjectSummary>?
        get() = getJson("cached_subject_summaries")
        set(value) = saveJson("cached_subject_summaries", value)

    var cachedGamificationProfile: GamificationProfile?
        get() = getJson("cached_gamification_profile")
        set(value) = saveJson("cached_gamification_profile", value)

    var cachedStarLeaders: List<StarLeaderItem>?
        get() = getJson("cached_star_leaders")
        set(value) = saveJson("cached_star_leaders", value)

    var cachedClassmates: List<ClassmateItem>?
        get() = getJson("cached_classmates")
        set(value) = saveJson("cached_classmates", value)

    var cachedWorks: List<WorkItem>?
        get() = getJson("cached_works")
        set(value) = saveJson("cached_works", value)

    var cachedRewards: List<RewardItem>?
        get() = getJson("cached_rewards")
        set(value) = saveJson("cached_rewards", value)

    var cachedProfileRewards: List<ProfileRewardItem>?
        get() = getJson("cached_profile_rewards")
        set(value) = saveJson("cached_profile_rewards", value)

    var cachedMealsBalance: MealsBalance?
        get() = getJson("cached_meals_balance")
        set(value) = saveJson("cached_meals_balance", value)

    var cachedRatingInfo: RatingInfo?
        get() = getJson("cached_rating_info")
        set(value) = saveJson("cached_rating_info", value)

    var cachedAcademicClassRanks: List<AcademicClassRankItem>?
        get() = getJson("cached_academic_ranks")
        set(value) = saveJson("cached_academic_ranks", value)

    var cachedAttendance: AttendanceSummary?
        get() = getJson("cached_attendance")
        set(value) = saveJson("cached_attendance", value)

    var cachedPeriodsSchedules: List<PeriodScheduleItemDTO>?
        get() = getJson("cached_periods_schedules")
        set(value) = saveJson("cached_periods_schedules", value)

    var cachedPortfolioAchievements: List<PortfolioAchievementItem>?
        get() = getJson("cached_portfolio_achievements")
        set(value) = saveJson("cached_portfolio_achievements", value)

    // --- Настройки уведомлений об оценках ---
    var marksNotifyEnabled: Boolean
        get() = prefs.getBoolean("marks_notify_enabled", false)
        set(value) = prefs.edit().putBoolean("marks_notify_enabled", value).apply()

    var marksNotifyNew: Boolean
        get() = prefs.getBoolean("marks_notify_new", true)
        set(value) = prefs.edit().putBoolean("marks_notify_new", value).apply()

    var marksNotifyChanged: Boolean
        get() = prefs.getBoolean("marks_notify_changed", true)
        set(value) = prefs.edit().putBoolean("marks_notify_changed", value).apply()

    var marksNotifyPoint: Boolean
        get() = prefs.getBoolean("marks_notify_point", true)
        set(value) = prefs.edit().putBoolean("marks_notify_point", value).apply()

    var marksNotifyPointExpiring: Boolean
        get() = prefs.getBoolean("marks_notify_point_expiring", true)
        set(value) = prefs.edit().putBoolean("marks_notify_point_expiring", value).apply()

    var marksNotifyFinal: Boolean
        get() = prefs.getBoolean("marks_notify_final", true)
        set(value) = prefs.edit().putBoolean("marks_notify_final", value).apply()

    var marksNotifyValues: Set<Int>
        get() {
            val raw = prefs.getString("marks_notify_values", "2,3,4,5") ?: "2,3,4,5"
            return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        }
        set(value) {
            prefs.edit().putString("marks_notify_values", value.sorted().joinToString(",")).apply()
        }

    var marksNotifyShowDetails: Boolean
        get() = prefs.getBoolean("marks_notify_show_details", true)
        set(value) = prefs.edit().putBoolean("marks_notify_show_details", value).apply()

    var marksNotifyShowGpaDelta: Boolean
        get() = prefs.getBoolean("marks_notify_show_gpa_delta", true)
        set(value) = prefs.edit().putBoolean("marks_notify_show_gpa_delta", value).apply()

    var marksNotifyQuietHours: Boolean
        get() = prefs.getBoolean("marks_notify_quiet_hours", true)
        set(value) = prefs.edit().putBoolean("marks_notify_quiet_hours", value).apply()

    var marksNotifyIntervalMin: Int
        get() = prefs.getInt("marks_notify_interval_min", 5)
        set(value) = prefs.edit().putInt("marks_notify_interval_min", value).apply()

    var marksNotifySeenIds: Set<String>
        get() = prefs.getStringSet("marks_notify_seen_ids", emptySet()) ?: emptySet()
        set(value) {
            val trimmed = value.toList().takeLast(200).toSet()
            prefs.edit().putStringSet("marks_notify_seen_ids", trimmed).apply()
        }

    var marksNotifyBaselineInitialized: Boolean
        get() = prefs.getBoolean("marks_notify_baseline_init", false)
        set(value) = prefs.edit().putBoolean("marks_notify_baseline_init", value).apply()

    var marksNotifyLastExpiringDate: String?
        get() = prefs.getString("marks_notify_last_expiring_date", null)
        set(value) = prefs.edit().putString("marks_notify_last_expiring_date", value).apply()

    fun logout() {
        prefs.edit()
            .remove("auth_token")
            .remove("profile_id")
            .remove("person_id")
            .remove("student_id")
            .remove("class_uid")
            .remove("class_unit_id")
            .remove("mos_access_token")
            .remove("mos_refresh_token")
            .remove("mesh_refresh_token")
            .remove("oauth_code_verifier")
            .remove("oauth_state")
            .apply()
        clearCache()
    }

    fun clearCache() {
        prefs.edit()
            .remove("cached_profile")
            .remove("cached_schedule_today")
            .remove("cached_schedule_tomorrow")
            .remove("cached_homework_list")
            .remove("cached_subject_summaries")
            .remove("cached_gamification_profile")
            .remove("cached_star_leaders")
            .remove("cached_classmates")
            .remove("cached_works")
            .remove("cached_rewards")
            .remove("cached_profile_rewards")
            .remove("cached_meals_balance")
            .remove("cached_rating_info")
            .remove("cached_attendance")
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

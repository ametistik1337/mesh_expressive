package ru.mesh.expressive.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.mesh.expressive.MainActivity
import ru.mesh.expressive.R
import ru.mesh.expressive.data.local.SessionManager
import ru.mesh.expressive.data.model.MarkItem
import ru.mesh.expressive.data.model.SubjectSummary
import ru.mesh.expressive.service.MarksSyncJobService
import ru.mesh.expressive.service.MarksSyncReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object MarksNotificationManager {

    private const val CHANNEL_UPDATES = "marks_updates"
    private const val CHANNEL_ALERTS = "marks_alerts"
    private const val JOB_ID = 4201

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val updatesChannel = NotificationChannel(
                CHANNEL_UPDATES,
                "Оценки",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых и исправленных оценках"
            }
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Точки и итоги",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Напоминания об истекающих точках и итоговых оценках"
            }
            nm.createNotificationChannel(updatesChannel)
            nm.createNotificationChannel(alertsChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun schedulePeriodicSync(context: Context, sessionManager: SessionManager) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            JOB_ID,
            Intent(context, MarksSyncReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!sessionManager.marksNotifyEnabled) {
            scheduler?.cancel(JOB_ID)
            alarmMgr?.cancel(alarmIntent)
            return
        }

        val intervalMin = sessionManager.marksNotifyIntervalMin.coerceAtLeast(1)
        val intervalMs = intervalMin * 60_000L

        // Точный быстрый триггер через AlarmManager (работает и для 1-5 минут)
        alarmMgr?.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + intervalMs,
            alarmIntent
        )

        // Резервный JobScheduler (минимум 15 мин на уровне Android OS)
        val jobIntervalMs = maxOf(intervalMs, 15 * 60_000L)
        val component = ComponentName(context, MarksSyncJobService::class.java)
        val jobInfo = JobInfo.Builder(JOB_ID, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(jobIntervalMs)
            .setPersisted(true)
            .build()
        scheduler?.schedule(jobInfo)
    }

    fun cancelPeriodicSync(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
        scheduler?.cancel(JOB_ID)
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            JOB_ID,
            Intent(context, MarksSyncReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmMgr?.cancel(alarmIntent)
    }

    /**
     * Быстрая проверка официальной ленты уведомлений МЭШ:
     * GET /api/family/mobile/v1/notifications/search?student_id=...
     * (точно как в оригинальном клиенте МЭШ, без скачивания всего дневника)
     */
    fun pollNotificationsSearch(context: Context, sessionManager: SessionManager) {
        if (!sessionManager.marksNotifyEnabled) return
        val token = sessionManager.authToken?.takeIf { it.isNotBlank() } ?: return
        val studentId = sessionManager.studentId.takeIf { it > 0L } ?: return
        val profileId = sessionManager.profileId

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url("https://school.mos.ru/api/family/mobile/v1/notifications/search?student_id=$studentId")
                .get()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Auth-Token", token)
                .addHeader("Profile-Id", profileId)
                .addHeader("X-Mes-Subsystem", "familymp")
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || body.isBlank()) return

            val parsed = JsonParser.parseString(body)
            val arr = when {
                parsed.isJsonArray -> parsed.asJsonArray
                parsed.isJsonObject && parsed.asJsonObject.has("payload") -> parsed.asJsonObject.getAsJsonArray("payload")
                parsed.isJsonObject && parsed.asJsonObject.has("items") -> parsed.asJsonObject.getAsJsonArray("items")
                else -> return
            }

            val seen = sessionManager.marksNotifySeenIds.toMutableSet()
            val isInitialBaseline = !sessionManager.marksNotifyBaselineInitialized
            val allowedValues = sessionManager.marksNotifyValues
            val showDetails = sessionManager.marksNotifyShowDetails
            val eventsToShow = mutableListOf<Triple<String, String, String>>()

            for (el in arr) {
                if (!el.isJsonObject) continue
                val obj = el.asJsonObject
                val eventId = obj.get("id")?.asString
                    ?: obj.get("event_id")?.asString
                    ?: "${obj.get("event_type")?.asString}_${obj.get("datetime")?.asString}_${obj.get("subject_name")?.asString}"
                if (eventId in seen) continue
                seen.add(eventId)

                if (isInitialBaseline) continue

                val eventType = obj.get("event_type")?.asString ?: continue
                val subject = obj.get("subject_name")?.asString?.takeIf { it.isNotBlank() } ?: "Предмет"
                val controlForm = obj.get("control_form_name")?.asString?.takeIf { it.isNotBlank() }
                val newValStr = obj.get("new_mark_value")?.asString?.trim().orEmpty()
                val oldValStr = obj.get("old_mark_value")?.asString?.trim().orEmpty()
                val newWeight = obj.get("new_mark_weight")?.asDouble ?: 1.0
                val isPt = obj.get("new_mark_is_point")?.asBoolean == true || newValStr.endsWith(".")
                val numericVal = newValStr.removeSuffix(".").removeSuffix("*").toIntOrNull()

                val detailLine = if (showDetails) {
                    val parts = mutableListOf<String>()
                    if (controlForm != null) parts.add(controlForm)
                    if (newWeight > 1.0) parts.add("вес ×$newWeight")
                    if (parts.isNotEmpty()) "\n${parts.joinToString(" • ")}" else ""
                } else ""

                when (eventType) {
                    "create_mark" -> {
                        if (numericVal != null && numericVal in 2..5 && numericVal !in allowedValues) continue
                        if (isPt && sessionManager.marksNotifyPoint) {
                            eventsToShow.add(Triple(CHANNEL_ALERTS, "$subject: точка $newValStr", "Выставлена точка$detailLine"))
                        } else if (!isPt && sessionManager.marksNotifyNew) {
                            eventsToShow.add(Triple(CHANNEL_UPDATES, "$subject: оценка $newValStr", "Новая оценка$detailLine"))
                        }
                    }
                    "update_mark" -> {
                        if (!sessionManager.marksNotifyChanged) continue
                        if (numericVal != null && numericVal in 2..5 && numericVal !in allowedValues) continue
                        val transition = if (oldValStr.isNotBlank()) "$oldValStr → $newValStr" else newValStr
                        eventsToShow.add(Triple(CHANNEL_UPDATES, "$subject: исправлено $transition", "Оценка изменена$detailLine"))
                    }
                    "create_mark_comment", "update_mark_comment" -> {
                        if (!sessionManager.marksNotifyChanged) continue
                        val commentText = obj.get("new_mark_comment")?.asString?.takeIf { it.isNotBlank() } ?: "Новый комментарий учителя"
                        eventsToShow.add(Triple(CHANNEL_ALERTS, "$subject: комментарий к оценке", commentText))
                    }
                }
            }

            sessionManager.marksNotifySeenIds = seen
            if (isInitialBaseline) {
                sessionManager.marksNotifyBaselineInitialized = true
                return
            }

            if (eventsToShow.isNotEmpty() && hasNotificationPermission(context) && !isQuietHoursNow(sessionManager)) {
                ensureChannels(context)
                val nm = NotificationManagerCompat.from(context)
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("open_tab", "MARKS")
                }
                val pi = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                eventsToShow.take(8).forEachIndexed { idx, (channel, title, bodyText) ->
                    val notif = NotificationCompat.Builder(context, channel)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(title)
                        .setContentText(bodyText)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
                        .setPriority(if (channel == CHANNEL_UPDATES) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .setContentIntent(pi)
                        .build()
                    try {
                        nm.notify(6000 + idx + (System.currentTimeMillis() % 1000).toInt(), notif)
                    } catch (_: SecurityException) {}
                }
            }
        } catch (_: Exception) {}
    }

    private fun isQuietHoursNow(sessionManager: SessionManager): Boolean {
        if (!sessionManager.marksNotifyQuietHours) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 8
    }

    private fun markUniqueKey(subjName: String, mark: MarkItem): String {
        return if (mark.id.isNotBlank()) "id:${mark.id}"
        else "k:${subjName}_${mark.date}_${mark.controlFormName}_${mark.topic}"
    }

    fun processDiffAndNotify(
        context: Context,
        sessionManager: SessionManager,
        oldSummaries: List<SubjectSummary>,
        newSummaries: List<SubjectSummary>
    ) {
        if (!sessionManager.marksNotifyEnabled) return
        if (!hasNotificationPermission(context)) return
        if (isQuietHoursNow(sessionManager)) return
        if (oldSummaries.isEmpty() || newSummaries.isEmpty()) return

        ensureChannels(context)

        val oldBySubj = oldSummaries.associateBy { it.subject }
        val allowedValues = sessionManager.marksNotifyValues
        val showDetails = sessionManager.marksNotifyShowDetails
        val showGpaDelta = sessionManager.marksNotifyShowGpaDelta
        val showWeighted = sessionManager.showWeightedGpa

        val events = mutableListOf<Triple<String, String, String>>() // channel, title, body

        for (newSubj in newSummaries) {
            val oldSubj = oldBySubj[newSubj.subject]
            val oldMarksMap = oldSubj?.marks?.associateBy { markUniqueKey(newSubj.subject, it) }.orEmpty()

            val oldAvg = oldSubj?.getEffectiveAverage(showWeighted) ?: 0.0
            val newAvg = newSubj.getEffectiveAverage(showWeighted)
            val gpaDeltaStr = if (showGpaDelta && oldAvg > 0.0 && newAvg > 0.0 && abs(newAvg - oldAvg) >= 0.005) {
                val sign = if (newAvg > oldAvg) "↑" else "↓"
                " • Ср. балл: ${String.format(Locale.US, "%.2f", oldAvg)} → ${String.format(Locale.US, "%.2f", newAvg)} $sign"
            } else if (showGpaDelta && newAvg > 0.0) {
                " • Ср. балл: ${String.format(Locale.US, "%.2f", newAvg)}"
            } else ""

            // 1. Новые и исправленные/изменённые оценки
            for (mark in newSubj.marks) {
                val key = markUniqueKey(newSubj.subject, mark)
                val oldMark = oldMarksMap[key]
                val isPt = mark.isPoint || !mark.pointDate.isNullOrBlank() || mark.rawValue.endsWith(".") || mark.rawValue == "."
                val valStr = mark.rawValue.ifBlank { if (mark.value in 2..5) "${mark.value}" else "?" }

                val detailPart = if (showDetails) {
                    val parts = mutableListOf<String>()
                    mark.controlFormName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
                    if (mark.topic.isNotBlank()) parts.add(mark.topic)
                    if (mark.weight > 1.0) parts.add("вес ×${mark.weight}")
                    if (parts.isNotEmpty()) "\n${parts.joinToString(" • ")}" else ""
                } else ""

                if (oldMark == null) {
                    // Новая оценка или точка
                    if (isPt) {
                        if (sessionManager.marksNotifyPoint && (mark.value !in 2..5 || mark.value in allowedValues)) {
                            val deadline = mark.pointDate?.takeIf { it.isNotBlank() }?.let { " (до ${DateUtils.formatPointDate(it)})" } ?: ""
                            val title = "${newSubj.subject}: точка $valStr$deadline"
                            val body = "Выставлена точка$gpaDeltaStr$detailPart"
                            events.add(Triple(CHANNEL_ALERTS, title, body))
                        }
                    } else if (sessionManager.marksNotifyNew) {
                        if (mark.value !in 2..5 || mark.value in allowedValues) {
                            val title = "${newSubj.subject}: оценка $valStr"
                            val body = "Новая оценка$gpaDeltaStr$detailPart"
                            events.add(Triple(CHANNEL_UPDATES, title, body))
                        }
                    }
                } else {
                    // Проверка на исправление / изменение
                    val oldIsPt = oldMark.isPoint || !oldMark.pointDate.isNullOrBlank() || oldMark.rawValue.endsWith(".") || oldMark.rawValue == "."
                    val changedVal = oldMark.value != mark.value || oldMark.rawValue != mark.rawValue || oldIsPt != isPt || (!oldMark.isPointCorrected && mark.isPointCorrected)
                    if (changedVal && sessionManager.marksNotifyChanged) {
                        if (mark.value !in 2..5 || mark.value in allowedValues) {
                            val oldValStr = oldMark.rawValue.ifBlank { "${oldMark.value}" }
                            val title = "${newSubj.subject}: исправлено $oldValStr → $valStr"
                            val body = "Оценка изменена$gpaDeltaStr$detailPart"
                            events.add(Triple(CHANNEL_UPDATES, title, body))
                        }
                    }
                }
            }

            // 2. Итоговая оценка
            if (sessionManager.marksNotifyFinal && !newSubj.finalMark.isNullOrBlank() && newSubj.finalMark != oldSubj?.finalMark) {
                val title = "${newSubj.subject}: итог ${newSubj.finalMark}"
                val body = "Выставлена итоговая оценка за период$gpaDeltaStr"
                events.add(Triple(CHANNEL_ALERTS, title, body))
            }
        }

        // 3. Напоминание об истекающих точках (раз в день)
        if (sessionManager.marksNotifyPointExpiring) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if (sessionManager.marksNotifyLastExpiringDate != todayStr) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, 2)
                val limitStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                val expiring = newSummaries.flatMap { it.marks }.filter { m ->
                    val isPt = m.isPoint || !m.pointDate.isNullOrBlank() || m.rawValue.endsWith(".")
                    val pd = m.pointDate?.substringBefore("T")?.trim().orEmpty()
                    isPt && !m.isPointExpired && pd.isNotBlank() && pd >= todayStr && pd <= limitStr
                }
                if (expiring.isNotEmpty()) {
                    sessionManager.marksNotifyLastExpiringDate = todayStr
                    val subjectsList = expiring.map { it.subject }.distinct().take(3).joinToString(", ")
                    events.add(
                        Triple(
                            CHANNEL_ALERTS,
                            "Скоро истекают точки (${expiring.size})",
                            "Предметы: $subjectsList. Успейте сдать до дедлайна."
                        )
                    )
                }
            }
        }

        if (events.isEmpty()) return

        val nm = NotificationManagerCompat.from(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "MARKS")
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        events.take(8).forEachIndexed { index, (channel, title, body) ->
            val notif = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(if (channel == CHANNEL_UPDATES) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            try {
                nm.notify(5000 + index + (System.currentTimeMillis() % 1000).toInt(), notif)
            } catch (_: SecurityException) {}
        }
    }

    fun sendTestNotification(context: Context, sessionManager: SessionManager) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val nm = NotificationManagerCompat.from(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "MARKS")
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val detail = if (sessionManager.marksNotifyShowDetails) "\nКонтрольная работа • вес ×1.5" else ""
        val delta = if (sessionManager.marksNotifyShowGpaDelta) " • Ср. балл: 4.33 → 4.50 ↑" else ""
        val body = "Оценка изменена$delta$detail"
        val notif = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Алгебра: исправлено 2. → 5*")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(4999, notif)
        } catch (_: SecurityException) {}
    }
}

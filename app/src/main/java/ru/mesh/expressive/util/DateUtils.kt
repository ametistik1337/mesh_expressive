package ru.mesh.expressive.util

import ru.mesh.expressive.data.model.PeriodScheduleItemDTO
import ru.mesh.expressive.data.model.StudyPeriodOption
import ru.mesh.expressive.data.model.SubjectSummary
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object DateUtils {

    /**
     * Преобразует дату в относительный формат:
     * 0 дней -> "Сегодня, 3 сентября"
     * 1 день -> "Вчера, 2 сентября"
     * 2 дня -> "Позавчера, 1 сентября"
     * > 2 дней -> "31 августа 2026"
     */
    fun formatRelativeDate(isoDateOrDateStr: String?): String {
        if (isoDateOrDateStr.isNullOrBlank()) return ""
        return try {
            val dateOnly = isoDateOrDateStr.substringBefore("T").substringBefore(" ").trim()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateOnly) ?: return isoDateOrDateStr

            val todayCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val targetCal = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffMillis = todayCal.timeInMillis - targetCal.timeInMillis
            val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

            val dayMonthFormat = SimpleDateFormat("d MMMM", Locale("ru"))
            val fullFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))

            when (diffDays) {
                0L -> "Сегодня, ${dayMonthFormat.format(date)}"
                1L -> "Вчера, ${dayMonthFormat.format(date)}"
                2L -> "Позавчера, ${dayMonthFormat.format(date)}"
                -1L -> "Завтра, ${dayMonthFormat.format(date)}"
                -2L -> "Послезавтра, ${dayMonthFormat.format(date)}"
                else -> fullFormat.format(date)
            }
        } catch (_: Exception) {
            isoDateOrDateStr
        }
    }

    /**
     * Преобразует полную дату со временем в относительный формат с секундами:
     * Например: "Позавчера, 1 сентября в 10:19:00"
     */
    fun formatRelativeDateTime(isoDateTimeStr: String?): String {
        if (isoDateTimeStr.isNullOrBlank()) return ""
        return try {
            val clean = isoDateTimeStr.substringBefore("+").substringBefore("Z").replace(" ", "T")
            val parts = clean.split("T")
            val datePart = parts[0]
            val timePart = if (parts.size > 1) parts[1] else ""

            val relativeDate = formatRelativeDate(datePart)
            if (timePart.isNotBlank()) {
                val timeSub = timePart.split(":")
                val h = timeSub.getOrNull(0) ?: "00"
                val m = timeSub.getOrNull(1) ?: "00"
                val s = timeSub.getOrNull(2) ?: "00"
                "$relativeDate в $h:$m:$s"
            } else {
                relativeDate
            }
        } catch (_: Exception) {
            isoDateTimeStr
        }
    }

    /**
     * Форматирует дату фиксации оценки с точкой в читаемый русский вид (например: "28 сентября" или "28 сентября 2026").
     */
    fun formatPointDate(isoDateStr: String?): String {
        if (isoDateStr.isNullOrBlank()) return ""
        return try {
            val clean = isoDateStr.substringBefore("T").substringBefore(" ").trim()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(clean) ?: return clean

            val cal = Calendar.getInstance().apply { time = date }
            val now = Calendar.getInstance()
            val pattern = if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "d MMMM" else "d MMMM yyyy"
            val ruFormat = SimpleDateFormat(pattern, Locale("ru"))
            ruFormat.format(date)
        } catch (_: Exception) {
            isoDateStr
        }
    }

    fun getAcademicStartYear(now: Calendar = Calendar.getInstance()): Int {
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) // 0-based (7 = August)
        return if (month >= Calendar.AUGUST) year else year - 1
    }

    fun shortenPeriodTitle(title: String): String {
        return title
            .replace(" четверть", " четв.", ignoreCase = true)
            .replace(" полугодие", " полуг.", ignoreCase = true)
            .replace(" семестр", " сем.", ignoreCase = true)
            .replace(" триместр", " трим.", ignoreCase = true)
            .replace(" модуль", " мод.", ignoreCase = true)
    }

    private fun isRecognizedPeriodTitle(title: String): Boolean {
        return title.contains("четверть", ignoreCase = true) ||
            title.contains("полугодие", ignoreCase = true) ||
            title.contains("семестр", ignoreCase = true) ||
            title.contains("триместр", ignoreCase = true) ||
            title.contains("модуль", ignoreCase = true)
    }

    private fun buildFallbackPeriods(mode: String, startYear: Int, today: String): List<StudyPeriodOption> {
        val nextYear = startYear + 1
        val raw = when (mode) {
            "SEMESTERS" -> listOf(
                StudyPeriodOption("sem_1", "1 полугодие", "1 полуг.", "$startYear-09-01", "$startYear-12-30", false),
                StudyPeriodOption("sem_2", "2 полугодие", "2 полуг.", "$nextYear-01-11", "$nextYear-05-31", false)
            )
            "TRIMESTERS" -> listOf(
                StudyPeriodOption("trim_1", "1 триместр", "1 трим.", "$startYear-09-01", "$startYear-11-20", false),
                StudyPeriodOption("trim_2", "2 триместр", "2 трим.", "$startYear-11-21", "$nextYear-02-20", false),
                StudyPeriodOption("trim_3", "3 триместр", "3 трим.", "$nextYear-02-21", "$nextYear-05-31", false)
            )
            "MODULES" -> listOf(
                StudyPeriodOption("mod_1", "1 модуль", "1 мод.", "$startYear-09-01", "$startYear-10-04", false),
                StudyPeriodOption("mod_2", "2 модуль", "2 мод.", "$startYear-10-12", "$startYear-11-15", false),
                StudyPeriodOption("mod_3", "3 модуль", "3 мод.", "$startYear-11-23", "$startYear-12-30", false),
                StudyPeriodOption("mod_4", "4 модуль", "4 мод.", "$nextYear-01-11", "$nextYear-02-21", false),
                StudyPeriodOption("mod_5", "5 модуль", "5 мод.", "$nextYear-03-01", "$nextYear-04-11", false),
                StudyPeriodOption("mod_6", "6 модуль", "6 мод.", "$nextYear-04-19", "$nextYear-05-31", false)
            )
            else -> listOf(
                StudyPeriodOption("q_1", "1 четверть", "1 четв.", "$startYear-09-01", "$startYear-10-23", false),
                StudyPeriodOption("q_2", "2 четверть", "2 четв.", "$startYear-11-04", "$startYear-12-30", false),
                StudyPeriodOption("q_3", "3 четверть", "3 четв.", "$nextYear-01-11", "$nextYear-03-12", false),
                StudyPeriodOption("q_4", "4 четверть", "4 четв.", "$nextYear-03-22", "$nextYear-05-31", false)
            )
        }
        return markCurrentPeriod(raw, today)
    }

    private fun markCurrentPeriod(periods: List<StudyPeriodOption>, today: String): List<StudyPeriodOption> {
        if (periods.isEmpty()) return emptyList()
        val withCurrent = periods.map { p ->
            val isCur = p.startDate.isNotBlank() && p.endDate.isNotBlank() && today in p.startDate..p.endDate
            p.copy(isCurrent = isCur)
        }
        if (withCurrent.any { it.isCurrent }) return withCurrent
        val nextOrLast = withCurrent.firstOrNull { it.startDate > today } ?: withCurrent.last()
        return withCurrent.map { if (it.id == nextOrLast.id) it.copy(isCurrent = true) else it }
    }

    /**
     * Вычисляет список учебных периодов (четверти / триместры / полугодия / модули).
     * В режиме AUTO:
     *  1) приоритетно берет аттестационные периоды из subjectSummaries.periods (/subject_marks/short);
     *  2) если их нет — парсит periodsSchedules (/periods_schedules), включая полугодия и модули;
     *  3) fallback по номеру класса (10-11 классы -> полугодия, остальные -> четверти).
     */
    fun resolveStudyPeriods(
        periodsSchedules: List<PeriodScheduleItemDTO>,
        subjectSummaries: List<SubjectSummary>,
        periodMode: String = "AUTO",
        className: String = ""
    ): List<StudyPeriodOption> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val startYear = getAcademicStartYear()

        if (periodMode != "AUTO") {
            return buildFallbackPeriods(periodMode, startYear, today)
        }

        // 1. Аттестационные периоды напрямую из /subject_marks/short
        val fromSubjects = linkedMapOf<String, Pair<String, String>>()
        subjectSummaries.forEach { subj ->
            subj.periods.forEach { p ->
                val title = p.title.trim()
                val start = p.startDate.substringBefore("T").trim()
                val end = p.endDate.substringBefore("T").trim()
                if (title.isNotBlank() && isRecognizedPeriodTitle(title) && start.isNotBlank() && end.isNotBlank()) {
                    val existing = fromSubjects[title]
                    if (existing == null) {
                        fromSubjects[title] = start to end
                    } else {
                        val minStart = minOf(existing.first, start)
                        val maxEnd = maxOf(existing.second, end)
                        fromSubjects[title] = minStart to maxEnd
                    }
                }
            }
        }
        if (fromSubjects.isNotEmpty()) {
            val sorted = fromSubjects.map { (title, range) ->
                StudyPeriodOption(
                    id = title,
                    title = title,
                    shortTitle = shortenPeriodTitle(title),
                    startDate = range.first,
                    endDate = range.second,
                    isCurrent = false
                )
            }.sortedBy { it.startDate }
            return markCurrentPeriod(sorted, today)
        }

        // 2. Периоды из /periods_schedules (включая полугодия и модули)
        val groups = mutableMapOf<String, MutableList<String>>()
        periodsSchedules.forEach { item ->
            val t = item.title.trim()
            if (isRecognizedPeriodTitle(t)) {
                groups.getOrPut(t) { mutableListOf() }.add(item.date.substringBefore("T").trim())
            }
        }
        if (groups.isNotEmpty()) {
            val sorted = groups.map { (title, dates) ->
                val start = dates.minOrNull() ?: ""
                val end = dates.maxOrNull() ?: ""
                StudyPeriodOption(
                    id = title,
                    title = title,
                    shortTitle = shortenPeriodTitle(title),
                    startDate = start,
                    endDate = end,
                    isCurrent = false
                )
            }.sortedBy { it.startDate }
            return markCurrentPeriod(sorted, today)
        }

        // 3. Умный fallback по классу (10-11 классы в МЭШ обычно на полугодиях)
        val gradeNum = Regex("^(\\d+)").find(className.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()
        val fallbackMode = if (gradeNum != null && gradeNum >= 10) "SEMESTERS" else "QUARTERS"
        return buildFallbackPeriods(fallbackMode, startYear, today)
    }

    /**
     * Возвращает начальную дату учебного года в формате YYYY-08-01
     */
    fun getAcademicYearStart(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return if (month >= 8) "$year-08-01" else "${year - 1}-08-01"
    }

    /**
     * Возвращает конечную дату учебного года в формате YYYY-06-30
     */
    fun getAcademicYearEnd(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return if (month >= 8) "${year + 1}-06-30" else "$year-06-30"
    }

    /**
     * Возвращает строковую метку учебного года, например "2026 / 2027"
     */
    fun getAcademicYearLabel(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return if (month >= 8) "$year / ${year + 1}" else "${year - 1} / $year"
    }
}

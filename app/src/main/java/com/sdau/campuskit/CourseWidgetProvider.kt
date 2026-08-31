package com.sdau.campuskit

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.concurrent.thread

private data class WidgetCourse(
    val day: Int,
    val startSlot: Int,
    val slotCount: Int,
    val name: String,
    val room: String,
    val teacher: String,
    val weeks: String,
    val background: Int
)

private data class CourseOccurrence(
    val course: WidgetCourse,
    val start: Calendar,
    val end: Calendar,
    val dayOffset: Int
)

class CourseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateResponsiveWidget(context, manager, it) }
        scheduleNetworkRefresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        updateResponsiveWidget(context, manager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) updateAll(context)
    }

    companion object {
        private const val WIDGET_REFRESH_JOB_ID = 4402
        private const val PREFS_NAME = "offline_login"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TERM = "term"
        private const val KEY_COURSES = "courses_cache"
        private const val KEY_CUSTOM_COURSES_PREFIX = "custom_courses_cache"
        private const val KEY_WIDGET_LAST_NETWORK_REFRESH = "widget_last_network_refresh"
        private const val WIDGET_NETWORK_REFRESH_INTERVAL = 24L * 60L * 60L * 1000L
        private const val OFFICIAL_TERM = "2026-2027-1"
        private const val OFFICIAL_TERM_START_YEAR = 2026
        private const val OFFICIAL_TERM_START_MONTH = Calendar.SEPTEMBER
        private const val OFFICIAL_TERM_START_DAY = 7
        private val FALLBACK_COLORS = intArrayOf(
            Color.rgb(130, 173, 247), Color.rgb(237, 184, 119),
            Color.rgb(120, 225, 208), Color.rgb(232, 138, 117)
        )
        private val COURSE_ROW_IDS = intArrayOf(
            R.id.widget_course_1, R.id.widget_course_2, R.id.widget_course_3,
            R.id.widget_course_4, R.id.widget_course_5
        )
        private val COURSE_NAME_IDS = intArrayOf(
            R.id.widget_course_1_name, R.id.widget_course_2_name, R.id.widget_course_3_name,
            R.id.widget_course_4_name, R.id.widget_course_5_name
        )
        private val COURSE_META_IDS = intArrayOf(
            R.id.widget_course_1_meta, R.id.widget_course_2_meta, R.id.widget_course_3_meta,
            R.id.widget_course_4_meta, R.id.widget_course_5_meta
        )
        private val COURSE_START_IDS = intArrayOf(
            R.id.widget_course_1_start, R.id.widget_course_2_start, R.id.widget_course_3_start,
            R.id.widget_course_4_start, R.id.widget_course_5_start
        )
        private val COURSE_END_IDS = intArrayOf(
            R.id.widget_course_1_end, R.id.widget_course_2_end, R.id.widget_course_3_end,
            R.id.widget_course_4_end, R.id.widget_course_5_end
        )
        private val COURSE_ACCENT_IDS = intArrayOf(
            R.id.widget_course_1_accent, R.id.widget_course_2_accent, R.id.widget_course_3_accent,
            R.id.widget_course_4_accent, R.id.widget_course_5_accent
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CourseWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateResponsiveWidget(context, manager, it) }
            CompactCourseWidgetProvider.updateAll(context)
        }

        internal fun updateResponsiveWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle = manager.getAppWidgetOptions(appWidgetId)
        ) {
            val compact = isCompactSize(options)
            updateWidget(
                context, manager, appWidgetId,
                compact = compact,
                courseLimit = courseLimitForSize(options, compact)
            )
        }

        private fun isCompactSize(options: Bundle): Boolean {
            val minimumWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val maximumWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            val effectiveWidth = when {
                minimumWidth > 0 -> minimumWidth
                maximumWidth > 0 -> maximumWidth
                else -> Int.MAX_VALUE
            }
            return effectiveWidth < 200
        }

        private fun courseLimitForSize(options: Bundle, compact: Boolean): Int {
            if (compact) return 2
            val minimumHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val maximumHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            val effectiveHeight = when {
                minimumHeight > 0 -> minimumHeight
                maximumHeight > 0 -> maximumHeight
                else -> 110
            }
            return when {
                effectiveHeight < 145 -> 2
                effectiveHeight < 215 -> 3
                effectiveHeight < 285 -> 4
                else -> 5
            }
        }

        internal fun refreshCourseCache(context: Context): Boolean {
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
            val password = preferences.getString(KEY_PASSWORD, "").orEmpty()
            val term = preferences.getString(KEY_TERM, OFFICIAL_TERM).orEmpty().ifBlank { OFFICIAL_TERM }
            if (account == "114514") return true
            if (account.isBlank() || password.isBlank()) return false

            val oldColors = loadCourses(context).associate { it.name to it.background }
            val remote = SdauCourseRepository().queryCourses(account, password, term)
            val rows = JSONArray()
            remote.forEachIndexed { index, course ->
                rows.put(JSONObject().apply {
                    put("day", course.day)
                    put("startSlot", course.startSlot)
                    put("slotCount", course.slotCount)
                    put("name", course.name)
                    put("room", course.room)
                    put("teacher", course.teacher)
                    put("weeks", course.weeks)
                    put("background", oldColors[course.name] ?: FALLBACK_COLORS[index % FALLBACK_COLORS.size])
                    put("foreground", Color.WHITE)
                })
            }
            preferences.edit().putString(KEY_COURSES, rows.toString()).apply()
            CourseReminderScheduler.scheduleNext(context)
            return true
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            compact: Boolean,
            courseLimit: Int
        ) {
            val layout = if (compact) R.layout.widget_course_compact else R.layout.widget_course
            val views = RemoteViews(context.packageName, layout)
            val now = Calendar.getInstance()
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val term = preferences.getString(KEY_TERM, OFFICIAL_TERM).orEmpty().ifBlank { OFFICIAL_TERM }
            val start = termStartDate(term)
            val week = weekForDate(now, start)
            val courses = loadCourses(context)
            val occurrences = upcomingOccurrences(context, courses, start, now, courseLimit)
            val headerMoment = if (!compact && occurrences.isNotEmpty()) occurrences.first().start else now
            val headerWeek = weekForDate(headerMoment, start)

            views.setTextViewText(R.id.widget_header_date, SimpleDateFormat("M.d", Locale.CHINA).format(headerMoment.time))
            views.setTextViewText(R.id.widget_header_week, when {
                headerWeek <= 0 -> "未开学"
                headerWeek > 20 -> "已结束"
                else -> "第${headerWeek}周"
            })
            views.setTextViewText(R.id.widget_header_weekday, SimpleDateFormat("E", Locale.CHINA).format(headerMoment.time))

            if (occurrences.isEmpty()) {
                views.setViewVisibility(R.id.widget_courses, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_empty, when {
                    courses.isEmpty() -> "打开 APP 同步课程表"
                    week <= 0 -> "开学第一天没有课程"
                    week > 20 -> "本学期课程已结束"
                    else -> "近期没有课程安排"
                })
            } else {
                views.setViewVisibility(R.id.widget_courses, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                val availableRows = if (compact) 2 else COURSE_ROW_IDS.size
                for (position in 0 until availableRows) {
                    val visible = position < courseLimit && position < occurrences.size
                    views.setViewVisibility(COURSE_ROW_IDS[position], if (visible) View.VISIBLE else View.GONE)
                    if (visible) bindCourse(views, occurrences[position], position)
                }
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)
            val availableRows = if (compact) 2 else COURSE_ROW_IDS.size
            for (position in 0 until availableRows) {
                views.setOnClickPendingIntent(COURSE_ROW_IDS[position], openPending)
            }

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun bindCourse(views: RemoteViews, occurrence: CourseOccurrence, position: Int) {
            val clock = SimpleDateFormat("HH:mm", Locale.CHINA)
            views.setTextViewText(COURSE_NAME_IDS[position], occurrence.course.name)
            val room = occurrence.course.room.trim().trimStart('@').takeIf { it.isNotBlank() }?.let { "@$it" } ?: "@教室待定"
            val meta = listOf(room, occurrence.course.teacher.trim()).filter { it.isNotBlank() }.joinToString("丨")
            views.setTextViewText(COURSE_META_IDS[position], meta)
            views.setTextViewText(COURSE_START_IDS[position], clock.format(occurrence.start.time))
            views.setTextViewText(COURSE_END_IDS[position], clock.format(occurrence.end.time))
            views.setInt(COURSE_ACCENT_IDS[position], "setColorFilter", occurrence.course.background)
        }

        private fun upcomingOccurrences(
            context: Context,
            courses: List<WidgetCourse>,
            termStart: Calendar,
            now: Calendar,
            limit: Int
        ): List<CourseOccurrence> {
            if (courses.isEmpty()) return emptyList()
            val ranges = timeRanges()
            if (weekForDate(now, termStart) <= 0) {
                val openingDay = dayStart(termStart)
                if (CampusHolidayCalendar.isHoliday(openingDay)) return emptyList()
                val weekday = (openingDay.get(Calendar.DAY_OF_WEEK) + 5) % 7
                return courses.asSequence()
                    .filter { it.day == weekday && courseVisibleInWeek(it, 1) }
                    .filter { it.startSlot in ranges.indices && it.slotCount > 0 && it.startSlot + it.slotCount - 1 in ranges.indices }
                    .map { course ->
                        val first = ranges[course.startSlot]
                        val last = ranges[course.startSlot + course.slotCount - 1]
                        val start = (openingDay.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, first.first / 60)
                            set(Calendar.MINUTE, first.first % 60)
                        }
                        val end = (openingDay.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, last.second / 60)
                            set(Calendar.MINUTE, last.second % 60)
                        }
                        CourseOccurrence(course, start, end, 0)
                    }
                    .sortedBy { it.start.timeInMillis }
                    .take(limit)
                    .toList()
            }
            val today = dayStart(now)
            val result = mutableListOf<CourseOccurrence>()
            for (dayOffset in 0..14) {
                val date = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, dayOffset) }
                if (CampusHolidayCalendar.isHoliday(date)) continue
                val week = weekForDate(date, termStart)
                if (week !in 1..20) continue
                val weekday = (date.get(Calendar.DAY_OF_WEEK) + 5) % 7
                courses.asSequence()
                    .filter { it.day == weekday && courseVisibleInWeek(it, week) }
                    .filter { it.startSlot in ranges.indices && it.slotCount > 0 && it.startSlot + it.slotCount - 1 in ranges.indices }
                    .forEach { course ->
                        val first = ranges[course.startSlot]
                        val last = ranges[course.startSlot + course.slotCount - 1]
                        val start = (date.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, first.first / 60)
                            set(Calendar.MINUTE, first.first % 60)
                        }
                        val end = (date.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, last.second / 60)
                            set(Calendar.MINUTE, last.second % 60)
                        }
                        if (end.timeInMillis > now.timeInMillis) result += CourseOccurrence(course, start, end, dayOffset)
                    }
                if (result.size >= limit) break
            }
            return result.sortedBy { it.start.timeInMillis }.take(limit)
        }

        private fun loadCourses(context: Context): List<WidgetCourse> {
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val courses = mutableListOf<WidgetCourse>()
            val term = preferences.getString(KEY_TERM, OFFICIAL_TERM).orEmpty().ifBlank { OFFICIAL_TERM }
            val customKey = "${KEY_CUSTOM_COURSES_PREFIX}_${term.replace(Regex("[^A-Za-z0-9_-]"), "_")}" 
            listOf(KEY_COURSES, customKey).forEach { key ->
                val raw = preferences.getString(key, null) ?: return@forEach
                runCatching {
                    val rows = JSONArray(raw)
                    for (index in 0 until rows.length()) {
                        val row = rows.optJSONObject(index) ?: continue
                        courses += WidgetCourse(
                            day = row.optInt("day", -1),
                            startSlot = row.optInt("startSlot", -1),
                            slotCount = row.optInt("slotCount", 0),
                            name = row.optString("name"),
                            room = row.optString("room"),
                            teacher = row.optString("teacher"),
                            weeks = row.optString("weeks"),
                            background = row.optInt("background", FALLBACK_COLORS[index % FALLBACK_COLORS.size])
                        )
                    }
                }
            }
            return courses.filter { it.day in 0..6 && it.name.isNotBlank() }
        }

        private fun courseVisibleInWeek(course: WidgetCourse, week: Int): Boolean {
            if (week <= 0) return false
            val normalized = course.weeks.replace("周", "").replace("—", "-").replace("至", "-")
            val ranges = Regex("(\\d+)(?:\\s*-\\s*(\\d+))?").findAll(normalized).toList()
            if (ranges.isEmpty()) return true
            return ranges.any { match ->
                val first = match.groupValues[1].toIntOrNull() ?: return@any false
                val last = match.groupValues[2].toIntOrNull() ?: first
                week in first.coerceAtLeast(1)..last
            }
        }

        private fun timeRanges(): Array<Pair<Int, Int>> {
            val starts = if (ScheduleTimePolicy.currentMode() == ScheduleMode.SPRING) {
                intArrayOf(480, 535, 600, 655, 840, 895, 960, 1015, 1140, 1195)
            } else {
                intArrayOf(480, 535, 600, 655, 870, 925, 990, 1045, 1170, 1225)
            }
            return Array(10) { index -> starts[index] to starts[index] + 45 }
        }

        private fun termStartDate(term: String): Calendar = Calendar.getInstance().apply {
            when (term) {
                OFFICIAL_TERM -> set(OFFICIAL_TERM_START_YEAR, OFFICIAL_TERM_START_MONTH, OFFICIAL_TERM_START_DAY, 0, 0, 0)
                "2026-2027-2" -> set(2027, Calendar.FEBRUARY, 22, 0, 0, 0)
                else -> {
                    val parts = term.split("-")
                    val year = parts.firstOrNull()?.toIntOrNull() ?: get(Calendar.YEAR)
                    if (parts.getOrNull(2) == "2") set(year + 1, Calendar.FEBRUARY, 1, 0, 0, 0)
                    else set(year, Calendar.SEPTEMBER, 1, 0, 0, 0)
                    while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            set(Calendar.MILLISECOND, 0)
        }

        private fun weekForDate(date: Calendar, start: Calendar): Int {
            val days = ((dayStart(date).timeInMillis - dayStart(start).timeInMillis) / 86_400_000L).toInt()
            return if (days < 0) 0 else days / 7 + 1
        }

        private fun dayStart(value: Calendar): Calendar = (value.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        internal fun scheduleNetworkRefresh(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastRefresh = preferences.getLong(KEY_WIDGET_LAST_NETWORK_REFRESH, 0L)
            val refreshAge = System.currentTimeMillis() - lastRefresh
            if (refreshAge in 0 until WIDGET_NETWORK_REFRESH_INTERVAL) return
            if (scheduler.getPendingJob(WIDGET_REFRESH_JOB_ID) != null) return
            val job = JobInfo.Builder(WIDGET_REFRESH_JOB_ID, ComponentName(context, CourseWidgetRefreshJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(0L)
                .setBackoffCriteria(60L * 60L * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            scheduler.schedule(job)
        }

        internal fun recordNetworkRefreshAttempt(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_WIDGET_LAST_NETWORK_REFRESH, System.currentTimeMillis())
                .apply()
        }
    }
}

class CompactCourseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { CourseWidgetProvider.updateResponsiveWidget(context, manager, it) }
        CourseWidgetProvider.scheduleNetworkRefresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        CourseWidgetProvider.updateResponsiveWidget(context, manager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) updateAll(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CompactCourseWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach {
                CourseWidgetProvider.updateResponsiveWidget(context, manager, it)
            }
        }
    }
}

class CourseWidgetRefreshJobService : JobService() {
    private val workerLock = Any()
    private var worker: Thread? = null
    private var activeParams: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val refreshThread = thread(name = "course-widget-refresh", start = false) {
            val result = runCatching { CourseWidgetProvider.refreshCourseCache(applicationContext) }
            val isStillActive = synchronized(workerLock) {
                activeParams === params && worker === Thread.currentThread()
            }
            if (isStillActive && !Thread.currentThread().isInterrupted && result.isSuccess) {
                CourseWidgetProvider.recordNetworkRefreshAttempt(applicationContext)
                CourseWidgetProvider.updateAll(applicationContext)
            }
            val shouldFinish = synchronized(workerLock) {
                if (activeParams === params && worker === Thread.currentThread()) {
                    activeParams = null
                    worker = null
                    true
                } else {
                    false
                }
            }
            if (shouldFinish) jobFinished(params, result.isFailure)
        }
        synchronized(workerLock) {
            worker?.interrupt()
            activeParams = params
            worker = refreshThread
        }
        refreshThread.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        synchronized(workerLock) {
            if (activeParams === params) {
                activeParams = null
                worker?.interrupt()
                worker = null
            }
        }
        return true
    }

    override fun onDestroy() {
        synchronized(workerLock) {
            activeParams = null
            worker?.interrupt()
            worker = null
        }
        super.onDestroy()
    }
}

package com.sdau.campuskit

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import java.util.Calendar

object CourseNotification {
    const val CHANNEL_ID = "course_reminders"
    private const val NOTIFICATION_ID = 4101

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel("course_reminders_silent_v2")
        manager.deleteNotificationChannel("course_reminders_silent_v3")
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null && (existing.sound != null || existing.shouldVibrate())) {
            manager.deleteNotificationChannel(CHANNEL_ID)
        }
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "课程提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "下一节课程提醒"
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = longArrayOf(0L)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, name: String, room: String, time: String) {
        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            4103,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val normalizedRoom = room.replace(Regex("\\s+"), "")
        val text = "${name}丨@${normalizedRoom}丨$time"
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logout)
            .setContentTitle("WeSDAU课程表")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setSubText("课程提醒")
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_NAME) ?: return
        val room = intent.getStringExtra(EXTRA_ROOM) ?: ""
        val time = intent.getStringExtra(EXTRA_TIME) ?: ""
        try {
            CourseNotification.show(context, name, room, time)
        } finally {
            CourseReminderScheduler.scheduleNext(context)
        }
    }

    companion object {
        const val EXTRA_NAME = "course_name"
        const val EXTRA_ROOM = "course_room"
        const val EXTRA_TIME = "course_time"
    }
}

class CourseReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CourseReminderScheduler.scheduleNext(context)
        ScoreUpdateScheduler.restoreIfEnabled(context, forceAlarm = true)
    }
}

private data class ReminderCourse(
    val day: Int,
    val startSlot: Int,
    val slotCount: Int,
    val name: String,
    val room: String,
    val weeks: String
)

object CourseReminderScheduler {
    private const val PREFS_NAME = "offline_login"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_COURSES = "courses_cache"
    private const val KEY_COURSES_PREFIX = "courses_cache_account"
    private const val KEY_CUSTOM_COURSES_PREFIX = "custom_courses_cache"
    private const val KEY_PUSH_ENABLED = "push_enabled"
    private const val KEY_TERM = "term"
    private const val REMINDER_REQUEST_CODE = 3002
    private const val OFFICIAL_TERM = "2026-2027-1"
    private const val MINIMUM_LEAD_TIME = 5_000L

    fun scheduleNext(context: Context) {
        val applicationContext = context.applicationContext
        val preferences = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_PUSH_ENABLED, false)) {
            cancel(applicationContext)
            return
        }
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val term = preferences.getString(KEY_TERM, OFFICIAL_TERM).orEmpty().ifBlank { OFFICIAL_TERM }
        val imported = account.takeIf { it.isNotBlank() }
            ?.let { preferences.getString(courseCacheKey(it, term), null) }
            ?: preferences.getString(KEY_COURSES, null)
        val custom = account.takeIf { it.isNotBlank() }
            ?.let { preferences.getString(customCourseCacheKey(it, term), null) }
            ?: preferences.getString(legacyCustomCourseCacheKey(term), null)
        val courses = loadCourses(
            imported,
            custom
        )
        val next = findNextReminder(courses, term, Calendar.getInstance())
        cancel(applicationContext)
        if (next == null) return

        val pending = PendingIntent.getBroadcast(
            applicationContext,
            REMINDER_REQUEST_CODE,
            Intent(applicationContext, CourseReminderReceiver::class.java).apply {
                putExtra(CourseReminderReceiver.EXTRA_NAME, next.course.name)
                putExtra(CourseReminderReceiver.EXTRA_ROOM, next.course.room)
                putExtra(CourseReminderReceiver.EXTRA_TIME, next.timeLabel)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            preferences.edit().putBoolean(KEY_PUSH_ENABLED, false).apply()
            cancel(applicationContext)
            return
        }
        runCatching {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAt, pending)
        }
    }

    fun cancel(context: Context) {
        val applicationContext = context.applicationContext
        val pending = PendingIntent.getBroadcast(
            applicationContext,
            REMINDER_REQUEST_CODE,
            Intent(applicationContext, CourseReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val alarm = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending)
        pending.cancel()
    }

    private data class NextReminder(
        val course: ReminderCourse,
        val triggerAt: Long,
        val timeLabel: String
    )

    private fun courseCacheKey(account: String, term: String): String {
        val safeAccount = account.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeTerm = term.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${KEY_COURSES_PREFIX}_${safeAccount}_$safeTerm"
    }

    private fun customCourseCacheKey(account: String, term: String): String {
        val safeAccount = account.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeTerm = term.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${KEY_CUSTOM_COURSES_PREFIX}_${safeAccount}_$safeTerm"
    }

    private fun legacyCustomCourseCacheKey(term: String): String =
        "${KEY_CUSTOM_COURSES_PREFIX}_${term.replace(Regex("[^A-Za-z0-9_-]"), "_")}"

    private fun findNextReminder(
        courses: List<ReminderCourse>,
        term: String,
        now: Calendar
    ): NextReminder? {
        if (courses.isEmpty()) return null
        val termStart = termStartDate(term)
        val today = dayStart(now)
        val currentWeek = weekForDate(today, termStart)
        val todayIndex = weekdayIndex(today)
        val todayHasCourses = !CampusHolidayCalendar.isHoliday(today) && courses.any {
            it.day == todayIndex && courseVisibleInWeek(it, currentWeek)
        }
        for (dayOffset in 0..147) {
            val date = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, dayOffset) }
            if (CampusHolidayCalendar.isHoliday(date)) continue
            val starts = if (ScheduleTimePolicy.modeFor(date) == ScheduleMode.SUMMER) {
                SUMMER_START_MINUTES
            } else {
                SPRING_START_MINUTES
            }
            val week = weekForDate(date, termStart)
            if (week !in 1..20) continue
            val dayCourses = courses
                .filter { it.day == weekdayIndex(date) && courseVisibleInWeek(it, week) }
                .sortedBy { it.startSlot }
            dayCourses.forEachIndexed { index, course ->
                if (course.startSlot !in starts.indices || course.slotCount <= 0) return@forEachIndexed
                val start = (date.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, starts[course.startSlot] / 60)
                    set(Calendar.MINUTE, starts[course.startSlot] % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                var triggerAt = start.timeInMillis - if (index == 0) 30L * 60_000L else 20L * 60_000L
                if (dayOffset == 1 && !todayHasCourses && index == 0) {
                    val previousNight = (start.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_MONTH, -1)
                        set(Calendar.HOUR_OF_DAY, 22)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    triggerAt = previousNight
                }
                if (triggerAt > now.timeInMillis + MINIMUM_LEAD_TIME) {
                    val lastSlot = (course.startSlot + course.slotCount - 1).coerceIn(course.startSlot, starts.lastIndex)
                    return NextReminder(
                        course,
                        triggerAt,
                        "${formatMinutes(starts[course.startSlot])}-${formatMinutes(starts[lastSlot] + 45)}"
                    )
                }
            }
        }
        return null
    }

    private fun loadCourses(vararg rawCaches: String?): List<ReminderCourse> {
        val courses = mutableListOf<ReminderCourse>()
        rawCaches.filterNotNull().forEach { raw ->
            runCatching {
                val rows = JSONArray(raw)
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    courses += ReminderCourse(
                        day = row.optInt("day", -1),
                        startSlot = row.optInt("startSlot", -1),
                        slotCount = row.optInt("slotCount", 0),
                        name = row.optString("name"),
                        room = normalizeClassroomName(row.optString("room")),
                        weeks = row.optString("weeks")
                    )
                }
            }
        }
        return courses.filter { it.day in 0..6 && it.name.isNotBlank() }
    }

    private fun courseVisibleInWeek(course: ReminderCourse, week: Int): Boolean {
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

    private fun termStartDate(term: String): Calendar = AcademicTermCalendar.startDate(term)

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

    private fun weekdayIndex(value: Calendar): Int = (value.get(Calendar.DAY_OF_WEEK) + 5) % 7

    private fun formatMinutes(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

    private val SPRING_START_MINUTES = intArrayOf(480, 535, 600, 655, 840, 895, 960, 1015, 1140, 1195)
    private val SUMMER_START_MINUTES = intArrayOf(480, 535, 600, 655, 870, 925, 990, 1045, 1170, 1225)
}

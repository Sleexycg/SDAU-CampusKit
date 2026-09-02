package com.sdau.campuskit

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.security.MessageDigest
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Keeps one monotonic score-record snapshot per account and term. The snapshot only
 * contains record identities; score components such as usual/final scores are never
 * queried by this background path.
 */
internal class ScoreUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ScoreUpdateNotification.foregroundInfo(applicationContext)

    override suspend fun doWork(): Result {
        if (!ScoreUpdateScheduler.isEnabled(applicationContext)) return Result.success()

        val credentials = ScoreUpdateScheduler.credentials(applicationContext)
            ?: return Result.success()
        // The monitor is deliberately independent from the term currently viewed in UI.
        val term = ScoreUpdateScheduler.latestTermForAccount(credentials.first)

        return try {
            val records = withContext(Dispatchers.IO) {
                SdauCourseRepository().queryScoreRecords(
                    account = credentials.first,
                    password = credentials.second,
                    term = term
                )
            }
            val current = records.mapTo(linkedSetOf()) { record ->
                record.scoreRecordId.trim().ifBlank {
                    listOf(term, record.courseCode.trim(), record.courseName.trim())
                        .joinToString("|")
                }
            }
            val previous = ScoreUpdateSnapshotStore.read(
                applicationContext,
                credentials.first,
                term
            )
            val hasNewPublishedScore = when {
                previous == null -> current.isNotEmpty()
                else -> current.any { it !in previous }
            }
            ScoreUpdateScheduler.recordQueryResult(
                context = applicationContext,
                term = term,
                publishedCount = current.size,
                publishedChanged = hasNewPublishedScore
            )
            if (previous == null) {
                ScoreUpdateSnapshotStore.write(
                    applicationContext,
                    credentials.first,
                    term,
                    current
                )
                return Result.success()
            }

            if (hasNewPublishedScore) {
                ScoreUpdateNotification.showUpdated(applicationContext)
            }
            ScoreUpdateSnapshotStore.write(
                applicationContext,
                credentials.first,
                term,
                previous + current
            )
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error.isTransientScoreCheckFailure()) Result.retry() else Result.failure()
        }
    }
}

/**
 * Low-frequency, local-only repair pass. It does not query the campus portal by
 * itself; it only restores a missing alarm and requests a check when the last
 * successful query is stale.
 */
internal class ScoreUpdateWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ScoreUpdateScheduler.runWatchdog(applicationContext)
        return Result.success()
    }
}

private val TRANSIENT_SCORE_HTTP_STATUS = Regex("HTTP\\s*(?:408|429|5\\d\\d)\\b", RegexOption.IGNORE_CASE)

private fun Throwable.isTransientScoreCheckFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is IOException) return true
        if (TRANSIENT_SCORE_HTTP_STATUS.containsMatchIn(current.message.orEmpty())) return true
        current = current.cause
    }
    return false
}

internal data class ScoreUpdateQueryStatus(
    val term: String,
    val publishedCount: Int,
    val lastCheckAt: Long,
    val lastPublishedAt: Long
)

internal object ScoreUpdateScheduler {
    private const val APP_PREFS = "offline_login"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_PASSWORD = "password"
    private const val KEY_ENABLED = "score_update_monitor_enabled"
    private const val KEY_LAST_CHECK_AT = "score_update_monitor_last_check_at"
    private const val KEY_LAST_TERM = "score_update_monitor_last_term"
    private const val KEY_PUBLISHED_COUNT = "score_update_monitor_published_count"
    private const val KEY_LAST_PUBLISHED_AT = "score_update_monitor_last_published_at"
    private const val KEY_NEXT_ALARM_AT = "score_update_monitor_next_alarm_at"
    private const val CHECK_WORK = "score_update_monitor_check"
    private const val WATCHDOG_WORK = "score_update_monitor_watchdog"
    private const val LEGACY_INITIAL_WORK = "score_update_monitor_initial"
    private const val LEGACY_PERIODIC_WORK = "score_update_monitor_periodic"
    private const val ALARM_REQUEST_CODE = 4203
    private const val CHECK_INTERVAL_MILLIS = 30L * 60L * 1_000L
    private const val WATCHDOG_INTERVAL_HOURS = 6L
    private const val SUCCESS_STALE_MILLIS = 90L * 60L * 1_000L
    private const val ALARM_STALE_TOLERANCE_MILLIS = 5_000L
    private const val FOREGROUND_CATCH_UP_MILLIS = 45L * 60L * 1_000L

    private val connectedConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun queryStatus(context: Context): ScoreUpdateQueryStatus {
        val preferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty().trim()
        return ScoreUpdateQueryStatus(
            term = preferences.getString(KEY_LAST_TERM, "").orEmpty().ifBlank {
                account.takeIf(String::isNotBlank)?.let(::latestTermForAccount).orEmpty()
            },
            publishedCount = preferences.getInt(KEY_PUBLISHED_COUNT, 0),
            lastCheckAt = preferences.getLong(KEY_LAST_CHECK_AT, 0L),
            lastPublishedAt = preferences.getLong(KEY_LAST_PUBLISHED_AT, 0L)
        )
    }

    fun recordQueryResult(
        context: Context,
        term: String,
        publishedCount: Int,
        publishedChanged: Boolean,
        checkedAt: Long = System.currentTimeMillis()
    ) {
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_TERM, term)
            .putInt(KEY_PUBLISHED_COUNT, publishedCount.coerceAtLeast(0))
            .putLong(KEY_LAST_CHECK_AT, checkedAt)
            .apply {
                if (publishedChanged) putLong(KEY_LAST_PUBLISHED_AT, checkedAt)
            }
            .apply()
    }

    fun credentials(context: Context): Pair<String, String>? {
        val preferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty().trim()
        val password = preferences.getString(KEY_PASSWORD, "").orEmpty()
        return if (account.isBlank() || password.isBlank()) null else account to password
    }

    fun enable(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!canScheduleExactAlarms(appContext)) {
            disable(appContext)
            return false
        }
        appContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .apply()
        credentials(appContext)?.let { (account, _) ->
            ScoreUpdateSnapshotStore.clear(appContext, account, latestTermForAccount(account))
        }
        cancelLegacyWork(appContext)
        if (!scheduleNextAlarm(appContext)) {
            disable(appContext)
            return false
        }
        scheduleWatchdog(appContext)
        enqueueCheck(appContext)
        return true
    }

    fun disable(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .remove(KEY_NEXT_ALARM_AT)
            .apply()
        cancelAlarm(appContext)
        WorkManager.getInstance(appContext).apply {
            cancelUniqueWork(CHECK_WORK)
            cancelUniqueWork(WATCHDOG_WORK)
            cancelUniqueWork(LEGACY_INITIAL_WORK)
            cancelUniqueWork(LEGACY_PERIODIC_WORK)
        }
    }

    /** Restores the one-shot alarm after boot/update and performs a stale foreground catch-up. */
    fun restoreIfEnabled(context: Context, forceAlarm: Boolean = false) {
        val appContext = context.applicationContext
        cancelLegacyWork(appContext)
        if (!isEnabled(appContext)) return
        if (!canScheduleExactAlarms(appContext)) {
            disable(appContext)
            return
        }
        scheduleWatchdog(appContext)

        val preferences = appContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val nextAlarmAt = preferences.getLong(KEY_NEXT_ALARM_AT, 0L)
        if (
            forceAlarm ||
            nextAlarmAt <= now + ALARM_STALE_TOLERANCE_MILLIS
        ) {
            if (!scheduleNextAlarm(appContext)) {
                disable(appContext)
                return
            }
        }

        val lastCheckAt = preferences.getLong(KEY_LAST_CHECK_AT, 0L)
        if (lastCheckAt == 0L || now - lastCheckAt >= FOREGROUND_CATCH_UP_MILLIS) {
            enqueueCheck(appContext)
        }
    }

    fun onAlarm(context: Context): Operation? {
        val appContext = context.applicationContext
        if (!isEnabled(appContext)) return null
        if (!canScheduleExactAlarms(appContext)) {
            disable(appContext)
            return null
        }
        if (!scheduleNextAlarm(appContext)) {
            disable(appContext)
            return null
        }
        return enqueueCheck(appContext, expedited = true)
    }

    fun runWatchdog(context: Context) {
        val appContext = context.applicationContext
        if (!isEnabled(appContext)) return
        if (!canScheduleExactAlarms(appContext)) {
            disable(appContext)
            return
        }

        val preferences = appContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val nextAlarmAt = preferences.getLong(KEY_NEXT_ALARM_AT, 0L)
        val hasAlarmToken = alarmPendingIntent(
            appContext,
            PendingIntent.FLAG_NO_CREATE
        ) != null
        if (!hasAlarmToken || nextAlarmAt <= now + ALARM_STALE_TOLERANCE_MILLIS) {
            if (!scheduleNextAlarm(appContext)) {
                disable(appContext)
                return
            }
        }

        val lastCheckAt = preferences.getLong(KEY_LAST_CHECK_AT, 0L)
        if (lastCheckAt == 0L || now - lastCheckAt >= SUCCESS_STALE_MILLIS) {
            enqueueCheck(appContext)
        }
    }

    private fun enqueueCheck(context: Context, expedited: Boolean = false): Operation {
        val requestBuilder = OneTimeWorkRequestBuilder<ScoreUpdateWorker>()
            .setConstraints(connectedConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
        if (expedited) {
            requestBuilder.setExpedited(
                OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST
            )
        }
        val request = requestBuilder.build()
        return WorkManager.getInstance(context).enqueueUniqueWork(
            CHECK_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleWatchdog(context: Context) {
        val request = PeriodicWorkRequestBuilder<ScoreUpdateWatchdogWorker>(
            WATCHDOG_INTERVAL_HOURS,
            TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WATCHDOG_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleNextAlarm(context: Context): Boolean {
        if (!isEnabled(context) || !canScheduleExactAlarms(context)) return false
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerElapsed = SystemClock.elapsedRealtime() + CHECK_INTERVAL_MILLIS
        return runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerElapsed,
                alarmPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT)
                    ?: error("Unable to create score update alarm")
            )
            context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEXT_ALARM_AT, System.currentTimeMillis() + CHECK_INTERVAL_MILLIS)
                .apply()
            true
        }.getOrDefault(false)
    }

    private fun cancelAlarm(context: Context) {
        val pendingIntent = alarmPendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun alarmPendingIntent(context: Context, creationFlag: Int): PendingIntent? {
        val intent = Intent(context, ScoreUpdateAlarmReceiver::class.java).apply {
            action = ScoreUpdateAlarmReceiver.ACTION_CHECK_SCORES
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            creationFlag or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .canScheduleExactAlarms()
    }

    private fun cancelLegacyWork(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(LEGACY_INITIAL_WORK)
            cancelUniqueWork(LEGACY_PERIODIC_WORK)
        }
    }

    fun currentTerm(now: Calendar = Calendar.getInstance()): String {
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val day = now.get(Calendar.DAY_OF_MONTH)
        return when {
            month > 7 || (month == 7 && day >= 20) -> "$year-${year + 1}-1"
            month > 2 || (month == 2 && day >= 16) -> "${year - 1}-$year-2"
            else -> "${year - 1}-$year-1"
        }
    }

    fun latestTermForAccount(
        account: String,
        now: Calendar = Calendar.getInstance()
    ): String {
        val current = currentTerm(now)
        val currentStartYear = current.substringBefore('-').toIntOrNull() ?: return current
        val enrollmentYear = account.take(4).toIntOrNull()
            ?.takeIf { it in 2000..currentStartYear }
            ?: return current
        val lastUndergraduateTerm = "${enrollmentYear + 3}-${enrollmentYear + 4}-2"
        return if (termOrder(current) > termOrder(lastUndergraduateTerm)) {
            lastUndergraduateTerm
        } else {
            current
        }
    }

    private fun termOrder(term: String): Int {
        val parts = term.split('-')
        val startYear = parts.getOrNull(0)?.toIntOrNull() ?: return Int.MIN_VALUE
        val semester = parts.getOrNull(2)?.toIntOrNull() ?: return Int.MIN_VALUE
        return startYear * 2 + semester
    }
}

private object ScoreUpdateSnapshotStore {
    private const val PREFS = "score_update_snapshots"

    fun read(context: Context, account: String, term: String): Set<String>? {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = key(account, term)
        if (!preferences.contains(key)) return null
        return runCatching {
            val array = JSONArray(preferences.getString(key, "[]"))
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.getOrNull()
    }

    fun write(context: Context, account: String, term: String, identities: Set<String>) {
        val array = JSONArray()
        identities.sorted().forEach(array::put)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(account, term), array.toString())
            .apply()
    }

    fun clear(context: Context, account: String, term: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key(account, term))
            .apply()
    }

    private fun key(account: String, term: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(account.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return "${digest}_$term"
    }
}

internal object ScoreUpdateNotification {
    private const val CHANNEL_ID = "score_updates"
    private const val BACKGROUND_CHANNEL_ID = "score_update_background"
    private const val TEST_NOTIFICATION_ID = 4201
    private const val UPDATE_NOTIFICATION_ID = 4202
    private const val BACKGROUND_NOTIFICATION_ID = 4204

    fun foregroundInfo(context: Context): ForegroundInfo {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    BACKGROUND_CHANNEL_ID,
                    "成绩更新后台检查",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "用于在旧版 Android 上执行短时成绩更新检查"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
                }
            )
        }
        val notification = NotificationCompat.Builder(context, BACKGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_test)
            .setContentTitle("WeSDAU课程表")
            .setContentText("正在检查成绩更新")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .build()
        return ForegroundInfo(BACKGROUND_NOTIFICATION_ID, notification)
    }

    fun showTest(context: Context) {
        show(
            context = context,
            id = TEST_NOTIFICATION_ID,
            text = "成绩更新提醒测试通知"
        )
    }

    fun showUpdated(context: Context) {
        show(
            context = context,
            id = UPDATE_NOTIFICATION_ID,
            text = "成绩已更新"
        )
    }

    private fun show(context: Context, id: Int, text: String) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "成绩更新提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "每 30 分钟检查是否发布了新成绩"
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_test)
            .setContentTitle("WeSDAU课程表")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        manager.notify(id, notification)
    }
}

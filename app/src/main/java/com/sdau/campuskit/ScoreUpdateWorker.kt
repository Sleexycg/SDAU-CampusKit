package com.sdau.campuskit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
        } catch (_: Exception) {
            Result.retry()
        }
    }
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
    private const val INITIAL_WORK = "score_update_monitor_initial"
    private const val PERIODIC_WORK = "score_update_monitor_periodic"

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

    fun enable(context: Context) {
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .apply()
        credentials(context)?.let { (account, _) ->
            ScoreUpdateSnapshotStore.clear(context, account, latestTermForAccount(account))
        }
        enqueue(context, replaceInitial = true)
    }

    fun disable(context: Context) {
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .apply()
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(INITIAL_WORK)
            cancelUniqueWork(PERIODIC_WORK)
        }
    }

    /** Reasserts the unique periodic work after an application update. */
    fun restoreIfEnabled(context: Context) {
        if (isEnabled(context)) enqueue(context, replaceInitial = false)
    }

    private fun enqueue(context: Context, replaceInitial: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (replaceInitial) {
            val initial = OneTimeWorkRequestBuilder<ScoreUpdateWorker>()
                .setConstraints(connectedConstraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniqueWork(
                INITIAL_WORK,
                ExistingWorkPolicy.REPLACE,
                initial
            )
        }

        val periodic = PeriodicWorkRequestBuilder<ScoreUpdateWorker>(30, TimeUnit.MINUTES)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .setConstraints(connectedConstraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
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
    private const val TEST_NOTIFICATION_ID = 4201
    private const val UPDATE_NOTIFICATION_ID = 4202

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
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        manager.notify(id, notification)
    }
}

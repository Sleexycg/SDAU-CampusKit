package com.sdau.campuskit

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.ContentValues
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.RenderNode
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.JsonWriter
import android.util.TypedValue
import android.view.Gravity
import android.view.PixelCopy
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.WindowManager
import android.net.Uri
import android.provider.Settings
import android.provider.MediaStore
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.concurrent.Executors
import java.nio.charset.StandardCharsets
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val activeThemeColors: CampusAndroidColors
        get() = campusAndroidColors(this)

    private class EmptyRoomPriorityScrollView(context: Context) : ScrollView(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val density = resources.displayMetrics.density
        private val scrollThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(126, 91, 108, 165)
        }
        private var downY = 0f
        private var lastY = 0f

        init {
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.y
                    lastY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = lastY - event.y
                    if (kotlin.math.abs(event.y - downY) > touchSlop && deltaY != 0f) {
                        val direction = if (deltaY > 0f) 1 else -1
                        parent?.requestDisallowInterceptTouchEvent(canScrollVertically(direction))
                    }
                    lastY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            return super.dispatchTouchEvent(event)
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            val content = getChildAt(0) ?: return
            val viewportHeight = (height - paddingTop - paddingBottom).coerceAtLeast(1)
            val contentHeight = content.height
            if (contentHeight <= viewportHeight) return

            val thumbWidth = 2.5f * density
            val thumbMargin = 3f * density
            val minimumThumbHeight = 22f * density
            val thumbHeight = kotlin.math.max(
                minimumThumbHeight,
                viewportHeight.toFloat() * viewportHeight / contentHeight
            ).coerceAtMost(viewportHeight.toFloat())
            val scrollRange = (contentHeight - viewportHeight).coerceAtLeast(1)
            val travelRange = viewportHeight - thumbHeight
            val thumbTop = scrollY + paddingTop +
                travelRange * (scrollY.toFloat() / scrollRange).coerceIn(0f, 1f)
            val thumbRight = scrollX + width - thumbMargin
            val cornerRadius = thumbWidth / 2f
            canvas.drawRoundRect(
                thumbRight - thumbWidth,
                thumbTop,
                thumbRight,
                thumbTop + thumbHeight,
                cornerRadius,
                cornerRadius,
                scrollThumbPaint
            )
        }
    }

    private lateinit var pageHost: FrameLayout
    private lateinit var studentIdBox: TextInputLayout
    private lateinit var passwordBox: TextInputLayout
    private lateinit var studentId: TextInputEditText
    private lateinit var password: TextInputEditText
    private lateinit var semesterInput: MaterialAutoCompleteTextView
    private val loginUiState = LoginUiState()
    private var loginMode: LoginMode
        get() = loginUiState.mode
        set(value) { loginUiState.mode = value }
    private var publicCollegeSelection: String
        get() = loginUiState.college
        set(value) { loginUiState.college = value }
    private var publicGradeSelection: String
        get() = loginUiState.grade
        set(value) { loginUiState.grade = value }
    private var publicMajorSelection: String
        get() = loginUiState.major
        set(value) { loginUiState.major = value }
    private var publicClassSelection: String
        get() = loginUiState.className
        set(value) { loginUiState.className = value }
    private var viewingPublicSchedule = false
    private var publicScheduleCourses: List<Course> = emptyList()
    private var publicScheduleTerm = ""
    private var publicScheduleLabel = ""
    private var publicScheduleClassName = ""
    private var publicSyncRunning = false
    private val publicScheduleIndexCache =
        java.util.concurrent.ConcurrentHashMap<String, PublicScheduleIndex>()
    private val publicScheduleLookupLock = Any()
    private var onLoginPage = false
    private lateinit var publicCollegeInput: MaterialAutoCompleteTextView
    private lateinit var publicGradeInput: MaterialAutoCompleteTextView
    private lateinit var publicMajorInput: MaterialAutoCompleteTextView
    private lateinit var publicClassInput: MaterialAutoCompleteTextView
    private var loginButton: LiquidTintedActionButtonView? = null
    private var scheduleHeader: ScheduleHeaderComposeView? = null
    private var scheduleRefreshRunning = false
    private var scheduleRefreshGeneration = 0
    private var academicSessionGeneration = 0
    private var loginStatus: TextView? = null
    private var scheduleVersion: ScheduleVersionComposeView? = null
    private var scheduleGrid: ScheduleGridView? = null
    private var scheduleTextPalette = ScheduleTextPalette(
        primary = Color.rgb(28, 34, 48),
        secondary = Color.rgb(73, 80, 94),
        halo = Color.TRANSPARENT,
        adaptive = false,
        usesDarkForeground = true
    )
    private var currentPageBackgroundBitmap: Bitmap? = null
    private var liveScheduleBackgroundBitmap: Bitmap? = null
    private var liveScheduleBackgroundCrop: BackgroundCropSpec? = null
    private var liveScheduleBackgroundScrimColor: Int? = null
    private var pendingSchedulePalettePreview: SchedulePalettePreview? = null
    private var schedulePalettePreviewFramePosted = false
    private var schedulePageRoot: FrameLayout? = null
    private var schedulePageBackgroundImage: ImageView? = null
    private var schedulePageBackgroundScrim: View? = null
    private var mainSectionHost: FrameLayout? = null
    private var currentMainSection = 0
    private var mainSectionTransitionGeneration = 0
    private var detailOverlay: LiquidCourseDialogView? = null
    private var scoreTermOverlay: LiquidScoreTermDropdownView? = null
    private val scoreTermSelectorExpanded = mutableStateOf(false)
    private var scoreDetailOverlay: LiquidScoreDetailDialogView? = null
    private var emptyRoomFilterOverlay: LiquidPickerDialogView? = null
    private var publicOptionOverlay: LiquidPickerDialogView? = null
    private var appearanceOverlay: LiquidAppearanceDialogView? = null
    private var refreshScheduleConfirmOverlay: LiquidConfirmDialogView? = null
    private var shareOverlay: View? = null
    private var actionMenuOverlay: LiquidActionMenuView? = null
    private var backgroundEditorOverlay: LiquidBackgroundEditorView? = null
    private var backgroundEditorPendingSource: File? = null
    private var backgroundEditorPreviewBitmap: Bitmap? = null
    private var updateOverlay: View? = null
    private var liquidToastOverlay: LiquidAppToastView? = null
    private var liquidToastCapturePending = false
    private var pendingLiquidToast: PendingLiquidToast? = null
    private var liquidToastDismissRunnable: Runnable? = null
    private var liquidToastDeferredRunnable: Runnable? = null
    private var liquidToastLoadingStartedAt = 0L
    private var forceUpdateActive = false
    private var updateDialogView: LiquidUpdateDialogView? = null
    private var updateDialogCapturePending = false
    private var pickerDialogCapturePending = false
    private var actionMenuCapturePending = false
    private var scoreTermMenuCapturePending = false
    private var scoreDetailCapturePending = false
    private var courseDialogCapturePending = false
    private var emptyRoomFilterCapturePending = false
    private var publicOptionPickerCapturePending = false
    private var appearanceCapturePending = false
    private var refreshScheduleConfirmCapturePending = false
    private var updateDownloadId: Long? = null
    private var updateDownloadReceiverRegistered = false
    private val updateDownloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId <= 0L || downloadId != updateDownloadId) return
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var successful = false
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    successful = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    ) == DownloadManager.STATUS_SUCCESSFUL
                }
            }
            val uri = if (successful) manager.getUriForDownloadedFile(downloadId) else null
            clearUpdateDownloadReceiver()
            runOnUiThread {
                updateDownloadId = null
                if (uri != null) {
                    installDownloadedApk(uri)
                } else {
                    updateDialogView?.setDownloading(false)
                    Toast.makeText(this@MainActivity, "更新包下载失败，请重试", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private var pendingPushEnable = false
    private var bottomNavigation: CampusLiquidBottomTabsView? = null
    private var scoresLoading = false
    private var scoreExporting = false
    private var scheduleExporting = false
    private var scoreLoadError: String? = null
    private var examsLoading = false
    private var examLoadError: String? = null
    private var emptyRoomsLoading = false
    private var emptyRoomLoadError: String? = null
    private var emptyRoomResult: RemoteEmptyRoomResult? = null
    private var emptyRoomRequestGeneration = 0
    private var emptyRoomCampus = "泮河校区"
    private var emptyRoomWeek = 1
    private var emptyRoomWeekday = Calendar.getInstance().let {
        val day = it.get(Calendar.DAY_OF_WEEK)
        if (day == Calendar.SUNDAY) 7 else day - 1
    }
    private var emptyRoomSectionCode = "0102"
    private var emptyRoomQueryExpanded = true
    private val collapsedEmptyRoomGroups = mutableSetOf<String>()
    private var pushEnabled = false
    private var scheduleMode = ScheduleTimePolicy.currentMode()
    private var currentWeek = 1
    private var pendingApkUrl = APK_URL
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val publicSyncExecutor = Executors.newSingleThreadExecutor()
    private val updateExecutor = Executors.newSingleThreadExecutor()
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val backgroundPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::prepareCustomBackground)
    }
    @Suppress("DEPRECATION")
    private val currentVersionCode: Int by lazy {
        packageManager.getPackageInfo(packageName, 0).let { info ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
            else info.versionCode
        }
    }
    @Suppress("DEPRECATION")
    private val appDisplayVersion: String by lazy {
        val installedName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty().ifBlank {
            currentVersionCode.toString()
        }
        if (installedName.startsWith("V", ignoreCase = true)) installedName else "V$installedName"
    }
    private data class RemoteUpdate(
        val code: Int,
        val name: String,
        val changelog: String,
        val url: String,
        val forceUpdate: Boolean = false
    )
    private data class PendingLiquidToast(
        val message: String,
        val visual: LiquidToastVisual,
        val durationMillis: Long
    )
    private data class ExamCache(val term: String, val records: List<RemoteExam>)
    private data class EmptyRoomGroup(val title: String, val accent: Int, val rooms: List<String>)
    private data class SchedulePalettePreview(
        val bitmap: Bitmap,
        val crop: BackgroundCropSpec,
        val scrimColor: Int
    )
    private data class PublicScheduleIndex(
        val hierarchy: Map<String, Map<String, Map<String, Set<String>>>>,
        val recordCount: Int,
        val sourceSha256: String
    )
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        CampusThemeController.initialize(this)
        pushEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_PUSH_ENABLED, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        }
        pageHost = FrameLayout(this).apply { setBackgroundColor(campusAndroidColors(this@MainActivity).pageBackground) }
        setContent {
            CampusComposeTheme {
                CampusAppRoot(pageHost)
            }
        }
        CourseWidgetProvider.cancelLegacyNetworkRefresh(this)
        startPublicScheduleSyncIfNeeded(inferredCurrentTerm())
        if (hasLocalCourseCache()) showSchedulePage() else showLoginPage(false)
        checkForOnlineUpdate()
        window.decorView.post(::hideSystemNavigationBar)
    }

    private fun checkForOnlineUpdate() {
        updateExecutor.execute {
            try {
                val update = readRemoteUpdate() ?: return@execute
                if (update.code <= currentVersionCode) return@execute
                val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                if (!update.forceUpdate && preferences.getInt(KEY_UPDATE_STARTED_CODE, 0) >= update.code) return@execute
                pendingApkUrl = update.url
                runOnUiThread {
                    showUpdateDialog(update)
                }
            } catch (_: Exception) {
                // 网络不可用时保持离线使用，不打扰课表页面。
            }
        }
    }

    private fun readRemoteUpdate(): RemoteUpdate? {
        val connection = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 5000; requestMethod = "GET"; useCaches = false
        }
        val remoteText = connection.inputStream.bufferedReader().use { it.readText() }.trim()
        connection.disconnect()
        val json = runCatching { JSONObject(remoteText) }.getOrNull()
        if (json == null) return remoteText.trim('"').toIntOrNull()?.let { RemoteUpdate(it, "", "", APK_URL) }
        val code = json.optInt("latestVersionCode", json.optInt("versionCode", json.optInt("version", 0)))
        val name = json.optString("latestVersionName", json.optString("versionName", ""))
        val url = json.optString("downloadUrl", APK_URL).ifBlank { APK_URL }
        val forceUpdate = json.optInt(
            "forceupdate",
            json.optInt("forcedupdate", json.optInt("forceUpdate", 0))
        ) != 0
        val changelog = when {
            json.optJSONArray("changelog") != null -> {
                val items = json.optJSONArray("changelog")!!
                (0 until items.length()).joinToString("\n") { "• ${items.optString(it)}" }
            }
            else -> json.optString("changelog", "")
        }
        return RemoteUpdate(code, name, changelog, url, forceUpdate)
    }

    private fun downloadLatestApk(
        apkUrl: String,
        remoteVersion: Int,
        remoteVersionName: String = "",
        notifyStarted: Boolean = false
    ) {
        try {
            val fileName = updateApkFileName(remoteVersion, remoteVersionName)
            val existing = findDownloadedUpdateApk(remoteVersion, fileName)
            if (existing != null && installDownloadedApk(existing)) {
                return
            }
            val destination = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (destination.exists() && !isExpectedUpdateApk(destination, remoteVersion)) {
                destination.delete()
            }
            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("WeSDAU课程表更新")
                setDescription("正在下载最新安装包")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            registerUpdateDownloadReceiver()
            updateDownloadId = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putInt(KEY_UPDATE_STARTED_CODE, remoteVersion).apply()
            if (notifyStarted) {
                Toast.makeText(this, "检测到新版本，请在右上角“检查更新”处安装", Toast.LENGTH_LONG).show()
            }
        } catch (error: Exception) {
            // 下次启动时继续检查并重试。
        }
    }

    private fun updateApkFileName(remoteVersion: Int, remoteVersionName: String): String {
        val versionLabel = remoteVersionName.trim()
            .removePrefix("V")
            .removePrefix("v")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { remoteVersion.toString() }
        return "WeSDAU课程表_V$versionLabel.apk"
    }

    private fun findDownloadedUpdateApk(remoteVersion: Int, expectedFileName: String): File? {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return listOf(
            File(downloads, expectedFileName),
            File(downloads, UPDATE_FILE_NAME)
        ).distinctBy(File::getAbsolutePath).firstOrNull { candidate ->
            candidate.isFile && candidate.length() > 0L &&
                isExpectedUpdateApk(candidate, remoteVersion)
        }
    }

    @Suppress("DEPRECATION")
    private fun isExpectedUpdateApk(file: File, remoteVersion: Int): Boolean {
        return runCatching {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
                ?: return@runCatching false
            if (archive.packageName != packageName) return@runCatching false
            val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archive.longVersionCode.toInt()
            } else {
                archive.versionCode
            }
            if (archiveVersion != remoteVersion) return@runCatching false

            val installed = packageManager.getPackageInfo(packageName, flags)
            val archiveSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }.orEmpty()
            } else {
                archive.signatures?.map { it.toCharsString() }.orEmpty()
            }
            val installedSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }.orEmpty()
            } else {
                installed.signatures?.map { it.toCharsString() }.orEmpty()
            }
            archiveSignatures.isNotEmpty() && archiveSignatures.toSet() == installedSignatures.toSet()
        }.getOrDefault(false)
    }

    private fun registerUpdateDownloadReceiver() {
        if (updateDownloadReceiverRegistered) return
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            this,
            updateDownloadReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        updateDownloadReceiverRegistered = true
    }

    private fun clearUpdateDownloadReceiver() {
        if (!updateDownloadReceiverRegistered) return
        runCatching { unregisterReceiver(updateDownloadReceiver) }
        updateDownloadReceiverRegistered = false
    }

    private fun requestInstallPermissionIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        if (packageManager.canRequestPackageInstalls()) return true
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            Toast.makeText(this, "请允许安装应用，返回后再次点击检查更新", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "请在系统设置中允许安装应用", Toast.LENGTH_LONG).show()
        }
        return false
    }

    private fun installDownloadedApk(file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            installDownloadedApk(uri)
        } catch (error: Exception) {
            Toast.makeText(this, "无法打开安装包：${error.message ?: "请手动在下载目录安装"}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun installDownloadedApk(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (error: Exception) {
            Toast.makeText(this, "无法打开安装包：${error.message ?: "请手动安装"}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun showUpdateDialog(update: RemoteUpdate) {
        if (updateOverlay != null || updateDialogCapturePending) return
        pendingApkUrl = update.url
        forceUpdateActive = update.forceUpdate
        val versionName = update.name.ifBlank { "V${update.code}" }
        val changelogText = update.changelog.ifBlank { "本次更新包含体验优化与问题修复。" }
        updateDialogCapturePending = true
        captureUpdateBackdrop { pageSnapshot ->
            updateDialogCapturePending = false
            if (isFinishing || isDestroyed || updateOverlay != null) {
                pageSnapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
                return@captureUpdateBackdrop
            }
            lateinit var dialog: LiquidUpdateDialogView
            dialog = LiquidUpdateDialogView(
                context = this,
                pageSnapshot = pageSnapshot,
                versionName = versionName,
                changelog = changelogText,
                forced = update.forceUpdate,
                onDismiss = { hideUpdateDialog() },
                onUpdate = updateAction@{
                    if (!requestInstallPermissionIfNeeded()) return@updateAction
                    dialog.setDownloading(true)
                    downloadLatestApk(update.url, update.code, update.name)
                    if (!update.forceUpdate) hideUpdateDialog()
                }
            )
            pageHost.addView(dialog, matchParentParams())
            updateDialogView = dialog
            updateOverlay = dialog
            dialog.alpha = 0f
            dialog.animate().alpha(1f).setDuration(180).start()
        }
    }

    private fun captureUpdateBackdrop(onCaptured: (Bitmap?) -> Unit) {
        val width = pageHost.width
        val height = pageHost.height
        if (width <= 0 || height <= 0 || !pageHost.isAttachedToWindow) {
            onCaptured(null)
            return
        }
        val location = IntArray(2)
        pageHost.getLocationInWindow(location)
        val sourceRect = Rect(
            location[0],
            location[1],
            location[0] + width,
            location[1] + height
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(
                window,
                sourceRect,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        onCaptured(bitmap)
                    } else {
                        bitmap.recycle()
                        onCaptured(null)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }.onFailure {
            bitmap.recycle()
            onCaptured(null)
        }
    }

    private fun showLiquidToast(
        message: String,
        visual: LiquidToastVisual,
        durationMillis: Long = 2_200L
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { showLiquidToast(message, visual, durationMillis) }
            return
        }
        if (visual == LiquidToastVisual.LOADING) {
            liquidToastDeferredRunnable?.let(pageHost::removeCallbacks)
            liquidToastDeferredRunnable = null
            liquidToastLoadingStartedAt = SystemClock.uptimeMillis()
        } else if (liquidToastLoadingStartedAt > 0L) {
            val elapsed = SystemClock.uptimeMillis() - liquidToastLoadingStartedAt
            val remaining = (900L - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) {
                liquidToastDeferredRunnable?.let(pageHost::removeCallbacks)
                lateinit var deferred: Runnable
                deferred = Runnable {
                    if (liquidToastDeferredRunnable !== deferred) return@Runnable
                    liquidToastDeferredRunnable = null
                    liquidToastLoadingStartedAt = 0L
                    showLiquidToast(message, visual, durationMillis)
                }
                liquidToastDeferredRunnable = deferred
                pageHost.postDelayed(deferred, remaining)
                return
            }
            liquidToastLoadingStartedAt = 0L
        }
        val request = PendingLiquidToast(message, visual, durationMillis)
        pendingLiquidToast = request
        liquidToastDismissRunnable?.let(pageHost::removeCallbacks)
        liquidToastDismissRunnable = null

        liquidToastOverlay?.let { overlay ->
            pendingLiquidToast = null
            overlay.update(message, visual)
            scheduleLiquidToastDismiss(overlay, durationMillis)
            return
        }
        if (liquidToastCapturePending) return

        liquidToastCapturePending = true
        captureUpdateBackdrop { pageSnapshot ->
            liquidToastCapturePending = false
            val latest = pendingLiquidToast
            pendingLiquidToast = null
            if (latest == null || isFinishing || isDestroyed) {
                pageSnapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
                return@captureUpdateBackdrop
            }
            val overlay = LiquidAppToastView(
                context = this,
                pageSnapshot = pageSnapshot,
                initialMessage = latest.message,
                initialVisual = latest.visual
            )
            pageHost.addView(overlay, matchParentParams())
            liquidToastOverlay = overlay
            scheduleLiquidToastDismiss(overlay, latest.durationMillis)
        }
    }

    private fun scheduleLiquidToastDismiss(
        overlay: LiquidAppToastView,
        durationMillis: Long
    ) {
        if (durationMillis <= 0L) return
        val dismissRunnable = Runnable {
            if (liquidToastOverlay === overlay) dismissLiquidToast()
        }
        liquidToastDismissRunnable = dismissRunnable
        pageHost.postDelayed(dismissRunnable, durationMillis)
    }

    private fun dismissLiquidToast() {
        pendingLiquidToast = null
        liquidToastDeferredRunnable?.let(pageHost::removeCallbacks)
        liquidToastDeferredRunnable = null
        liquidToastLoadingStartedAt = 0L
        liquidToastDismissRunnable?.let(pageHost::removeCallbacks)
        liquidToastDismissRunnable = null
        val overlay = liquidToastOverlay ?: return
        overlay.dismiss {
            if (liquidToastOverlay === overlay) {
                pageHost.removeView(overlay)
                overlay.releaseSnapshot()
                liquidToastOverlay = null
            }
        }
    }

    private fun clearLiquidToastImmediately() {
        pendingLiquidToast = null
        liquidToastDeferredRunnable?.let(pageHost::removeCallbacks)
        liquidToastDeferredRunnable = null
        liquidToastLoadingStartedAt = 0L
        liquidToastDismissRunnable?.let(pageHost::removeCallbacks)
        liquidToastDismissRunnable = null
        liquidToastOverlay?.let { overlay ->
            pageHost.removeView(overlay)
            overlay.releaseSnapshot()
        }
        liquidToastOverlay = null
    }

    private fun hideUpdateDialog() {
        val overlay = updateOverlay ?: return
        overlay.animate().alpha(0f).setDuration(140).withEndAction {
            pageHost.removeView(overlay)
            updateDialogView?.releaseSnapshot()
            updateOverlay = null
            updateDialogView = null
            forceUpdateActive = false
        }.start()
    }

    private fun showLoginPage(animate: Boolean) {
        migrateActiveAcademicCaches()
        academicSessionGeneration++
        scheduleRefreshGeneration++
        scheduleRefreshRunning = false
        scheduleHeader = null
        scoresLoading = false
        examsLoading = false
        scoreLoadError = null
        examLoadError = null
        WindowCompat.setDecorFitsSystemWindows(window, true)
        loginMode = LoginMode.PERSONAL
        viewingPublicSchedule = false
        publicScheduleCourses = emptyList()
        publicScheduleTerm = ""
        publicScheduleLabel = ""
        publicScheduleClassName = ""
        loginUiState.resetPublicSelection()
        onLoginPage = true
        setSystemBars(campusAndroidColors(this).pageBackground)
        cancelSystemCourseReminder()
        emptyRoomRequestGeneration++
        emptyRoomsLoading = false
        emptyRoomLoadError = null
        emptyRoomResult = null
        bottomNavigation = null
        detailOverlay?.releaseSnapshot()
        detailOverlay = null
        courseDialogCapturePending = false
        scoreTermOverlay?.releaseSnapshot()
        scoreTermOverlay = null
        scoreTermSelectorExpanded.value = false
        scoreTermMenuCapturePending = false
        scoreDetailOverlay?.releaseSnapshot()
        scoreDetailOverlay = null
        emptyRoomFilterOverlay?.releaseSnapshot()
        emptyRoomFilterOverlay = null
        emptyRoomFilterCapturePending = false
        publicOptionOverlay?.releaseSnapshot()
        publicOptionOverlay = null
        publicOptionPickerCapturePending = false
        shareOverlay = null
        swapPage(buildLoginPage(), false, animate)
    }

    private fun migrateActiveAcademicCaches() {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (preferences.getString(KEY_ACCOUNT, "").orEmpty().isBlank()) return
        loadExamCache()
        loadScoreCache()
    }

    private fun showSchedulePage(animate: Boolean = true) {
        scheduleMode = ScheduleTimePolicy.currentMode()
        onLoginPage = false
        hideKeyboard()
        val account = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_ACCOUNT, "").orEmpty()
        if (account == "114514") {
            // 演示数据随版本代码更新，避免旧安装继续读取之前缓存的地点。
            saveCourseCache(sampleCourses())
            if (loadScoreCache() == null) saveScoreCache(sampleScoreResult(selectedScoreTerm()))
            saveExamCache(selectedTerm(), sampleExams())
        }
        currentWeek = weekForTerm(if (viewingPublicSchedule) publicScheduleTerm else selectedTerm())
        if (emptyRoomResult == null) syncEmptyRoomDefaultsToNow()
        currentMainSection = 0
        val themeColors = campusAndroidColors(this)
        setSystemBars(Color.TRANSPARENT)
        window.navigationBarColor = themeColors.gradient.last()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val schedulePage = buildSchedulePage()
        applyScheduleStatusBarAppearance()
        swapPage(schedulePage, true, animate)
    }

    private fun selectedTerm(): String = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        .getString(KEY_TERM, inferredCurrentTerm()) ?: inferredCurrentTerm()

    private fun activeScheduleTerm(): String = if (viewingPublicSchedule) publicScheduleTerm else selectedTerm()

    private fun activeScheduleCourses(): List<Course> = if (viewingPublicSchedule) publicScheduleCourses else loadCourseCache()

    private fun termStartDate(term: String): Calendar {
        val date = Calendar.getInstance()
        when (term) {
            OFFICIAL_TERM -> date.set(
                OFFICIAL_TERM_START_YEAR,
                OFFICIAL_TERM_START_MONTH,
                OFFICIAL_TERM_START_DAY,
                0, 0, 0
            )
            "2026-2027-2" -> date.set(2027, Calendar.FEBRUARY, 22, 0, 0, 0)
            else -> {
                val parts = term.split("-")
                val start = parts.firstOrNull()?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                if (parts.getOrNull(2) == "2") date.set(start + 1, Calendar.FEBRUARY, 1, 0, 0, 0)
                else date.set(start, Calendar.SEPTEMBER, 1, 0, 0, 0)
                while (date.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) date.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        date.set(Calendar.MILLISECOND, 0)
        return date
    }

    private fun weekForTerm(term: String): Int {
        val start = termStartDate(term)
        val today = Calendar.getInstance()
        start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0)
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0)
        val days = ((today.timeInMillis - start.timeInMillis) / 86_400_000L).toInt()
        return if (days < 0) 0 else (days / 7 + 1).coerceIn(1, 20)
    }

    private fun inferredCurrentTerm(): String {
        val today = Calendar.getInstance()
        val year = today.get(Calendar.YEAR)
        val month = today.get(Calendar.MONTH) + 1
        val day = today.get(Calendar.DAY_OF_MONTH)
        return when {
            month > 7 || (month == 7 && day >= 20) -> "$year-${year + 1}-1"
            month > 2 || (month == 2 && day >= 16) -> "${year - 1}-$year-2"
            else -> "${year - 1}-$year-1"
        }
    }

    private fun nextTerm(term: String): String {
        val parts = term.split("-")
        if (parts.size != 3) return term
        val start = parts[0].toIntOrNull() ?: return term
        return if (parts[2] == "1") "${start}-${start + 1}-2" else "${start + 1}-${start + 2}-1"
    }

    private fun scoreTermOptions(): List<String> {
        val account = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_ACCOUNT, "")
            .orEmpty()
        return scoreTermsForAccount(account).asReversed()
    }

    private fun allScoreTerms(account: String): List<String> {
        return scoreTermsForAccount(account)
    }

    private fun scoreTermsForAccount(account: String): List<String> {
        val current = inferredCurrentTerm()
        val currentStartYear = current.substringBefore('-').toIntOrNull()
            ?: Calendar.getInstance().get(Calendar.YEAR)
        val enrollmentYear = account.take(4).toIntOrNull()
            ?.takeIf { it in 2000..currentStartYear }
            ?: return listOf(current)
        val result = mutableListOf<String>()
        var term = "$enrollmentYear-${enrollmentYear + 1}-1"
        while (termOrder(term) <= termOrder(current)) {
            result += term
            val next = nextTerm(term)
            if (next == term) break
            term = next
        }
        return result
    }

    private fun selectedScoreTerm(): String {
        val options = scoreTermOptions()
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SCORE_TERM, options.first()).orEmpty()
            .takeIf { it in options }
            ?: options.first()
    }

    private fun semesterOptions(): Array<String> {
        val result = mutableListOf<String>()
        val current = inferredCurrentTerm()
        val base = OFFICIAL_TERM
        var term = if (termOrder(current) < termOrder(base)) current else base
        while (termOrder(term) <= termOrder(current)) {
            result += term
            val next = nextTerm(term)
            if (next == term) break
            term = next
        }
        return result.takeLast(8).toTypedArray()
    }

    private fun termOrder(term: String): Int {
        val parts = term.split("-")
        val start = parts.getOrNull(0)?.toIntOrNull() ?: return Int.MIN_VALUE
        val number = parts.getOrNull(2)?.toIntOrNull() ?: return 0
        return start * 2 + number - 1
    }

    private fun setSystemBars(color: Int) {
        window.statusBarColor = color
        window.navigationBarColor = color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.navigationBarDividerColor = color
        }
        var flags = if (activeThemeColors.isDark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activeThemeColors.isDark) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
        hideSystemNavigationBar()
    }

    private fun hideSystemNavigationBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    private fun swapPage(next: View, forward: Boolean, animate: Boolean) {
        clearLiquidToastImmediately()
        val previous = pageHost.getChildAt(0)
        if (!animate || previous == null) {
            pageHost.removeAllViews()
            pageHost.addView(next, matchParentParams())
            return
        }
        val distance = dp(36).toFloat() * if (forward) 1f else -1f
        next.alpha = 0f
        next.translationX = distance
        pageHost.addView(next, matchParentParams())
        next.animate().alpha(1f).translationX(0f).setDuration(220).start()
        previous.animate().alpha(0f).translationX(-distance * 0.55f).setDuration(180)
            .withEndAction { pageHost.removeView(previous) }.start()
    }

    private fun buildLoginPage(): View = composeHostView(this) {
        LegacyScreenHost(::buildLegacyLoginPage)
    }

    private fun buildLegacyLoginPage(): View {
        val scroll = ScheduleScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            background = SilkyGradientDrawable()
        }
        val viewport = verticalLayout().apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), 0, dp(20), dp(24))
        }
        scroll.addView(viewport, FrameLayout.LayoutParams(-1, -2))

        val card = surfaceCard(dp(28f).toFloat()).apply {
            setCardBackgroundColor(campusAndroidColors(this@MainActivity).surface)
            setStrokeColor(campusAndroidColors(this@MainActivity).cardOutline)
            cardElevation = if (activeThemeColors.isDark) 0f else dp(2).toFloat()
        }
        lateinit var modeToggle: LoginModeToggle
        val body = LoginSwipeLayout(
            this,
            loginMode,
            onPositionChanged = { position -> modeToggle.setSelectionPosition(position) },
            createModeForm = { nextMode ->
                val currentTerm = semesterInput.text?.toString().orEmpty()
                if (loginMode != nextMode) academicSessionGeneration++
                loginMode = nextMode
                hideKeyboard()
                if (nextMode == LoginMode.PUBLIC) {
                    startPublicScheduleSyncIfNeeded(currentTerm)
                }
                buildLoginModeForm(nextMode)
            },
            onModeSettled = { mode -> modeToggle.setSettledMode(mode) }
        ).apply { setPadding(dp(24), dp(24), dp(24), dp(22)) }
        body.addView(text("登录", 28f, campusAndroidColors(this).primaryText, Typeface.BOLD), spacedParams(dp(8)))

        modeToggle = LoginModeToggle(
            context = this,
            initialMode = loginMode,
            onDragPosition = { position -> body.updateFromModeToggle(position) },
            onDragFinished = { position, velocityX ->
                body.finishModeToggleDrag(position, velocityX)
            }
        ) { nextMode, _ -> body.animateToMode(nextMode) }
        body.addView(modeToggle, LinearLayout.LayoutParams(-1, dp(60)).apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
            bottomMargin = dp(20)
        })
        body.attachInitialForm(buildLoginModeForm(loginMode))
        card.addView(body)
        viewport.addView(card, matchWrapParams())
        viewport.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val available = view.width - view.paddingLeft - view.paddingRight
            val width = minOf(available, dp(480))
            val params = card.layoutParams
            if (width > 0 && params.width != width) { params.width = width; card.layoutParams = params }
        }
        return scroll
    }

    private fun buildLoginModeForm(mode: LoginMode): View {
        val form = verticalLayout()
        if (mode == LoginMode.PERSONAL) {
            studentIdBox = inputBox("学号")
            studentId = input(InputType.TYPE_CLASS_NUMBER).apply {
                imeOptions = EditorInfo.IME_ACTION_NEXT
                val savedAccount = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(KEY_ACCOUNT, "").orEmpty()
                setText(if (savedAccount.isNotEmpty() && savedAccount.all { it.isDigit() }) savedAccount else "")
            }
            studentIdBox.addView(studentId)
            form.addView(studentIdBox, spacedParams(dp(14)))

            passwordBox = inputBox("密码")
            password = input(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
                imeOptions = EditorInfo.IME_ACTION_DONE
                setText(getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_PASSWORD, ""))
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) { attemptLogin(); true } else false
                }
            }
            passwordBox.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            passwordBox.addView(password)
            form.addView(passwordBox, spacedParams(dp(20)))

            val semesterOptions = semesterOptions()
            val savedTerm = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_TERM, "")
            val selectedTerm = savedTerm?.takeIf { it in semesterOptions } ?: semesterOptions.first()
            semesterInput = MaterialAutoCompleteTextView(this)
            form.addView(
                publicFormField("学期", semesterInput, semesterOptions.toList(), selectedTerm) { },
                spacedParams(dp(18))
            )
        } else {
            semesterInput = MaterialAutoCompleteTextView(this)
            val semesterOptions = semesterOptions().toList()
            val savedTerm = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_TERM, "")
            val selectedTerm = savedTerm?.takeIf { it in semesterOptions } ?: semesterOptions.first()
            semesterInput.setText(selectedTerm, false)
            form.addView(publicFormField("学期", semesterInput, semesterOptions, selectedTerm) { semester ->
                loginUiState.resetPublicSelection()
                startPublicScheduleSyncIfNeeded(semester)
                swapPage(buildLoginPage(), false, false)
            }, spacedParams(dp(14)))
            buildPublicFilterFields(form, selectedTerm)
        }

        loginStatus = text("", 13f, campusAndroidColors(this).error, Typeface.NORMAL).apply {
            visibility = View.GONE
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        form.addView(loginStatus, spacedParams(dp(12)))

        val loginHeight = 56
        val login = LiquidTintedActionButtonView(
            context = this,
            initialText = if (mode == LoginMode.PERSONAL) "进入个人课表" else "查询班级课表",
            buttonHeightDp = loginHeight,
            onClick = {
                if (mode == LoginMode.PERSONAL) attemptLogin() else attemptPublicScheduleLookup()
            }
        )
        if (mode == LoginMode.PUBLIC &&
            (!hasPublicScheduleCache(semesterInput.text?.toString().orEmpty()) ||
                loadStoredPublicScheduleIndex(semesterInput.text?.toString().orEmpty()) == null ||
                !hasPublicScheduleLookup(semesterInput.text?.toString().orEmpty()))
        ) {
            login.setButtonEnabled(false)
            login.text = "正在准备全校课表…"
        }
        loginButton = login
        form.addView(login, LinearLayout.LayoutParams(-1, dp(loginHeight)).apply {
            if (mode == LoginMode.PUBLIC) topMargin = dp(6)
        })
        return form
    }

    private fun attemptLogin() {
        studentIdBox.error = null
        passwordBox.error = null
        semesterInput.error = null
        loginStatus?.visibility = View.GONE
        val id = studentId.text?.toString()?.trim().orEmpty()
        val pwd = password.text?.toString().orEmpty()
        val selectedSemester = semesterInput.text?.toString()?.trim().orEmpty()
        if (id.isEmpty()) { studentIdBox.error = "请输入学号"; studentId.requestFocus(); return }
        if (pwd.isEmpty()) { passwordBox.error = "请输入密码"; password.requestFocus(); return }
        if (selectedSemester.isEmpty()) { semesterInput.error = "请选择学期"; return }
        if (id == "114514") {
            if (pwd != "admin") {
                passwordBox.error = "密码不正确"
                password.requestFocus()
                return
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_ACCOUNT, id)
                .putString(KEY_PASSWORD, pwd)
                .putString(KEY_TERM, selectedSemester)
                .putString(KEY_SCORE_TERM, selectedSemester)
                .putString(KEY_STUDENT_NAME, "演示用户")
                .remove(KEY_SCORES)
                .remove(KEY_EXAMS)
                .apply()
            savePasswordCache(id, pwd)
            saveCourseCache(sampleCourses())
            saveScoreCache(sampleScoreResult(selectedSemester))
            saveExamCache(selectedSemester, sampleExams())
            showSchedulePage()
            return
        }
        val localPassword = cachedPassword(id)
        if (hasCourseCache(id, selectedSemester) && localPassword != null && pwd == localPassword) {
            activateCourseCache(id, selectedSemester)
            val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val previousAccount = preferences.getString(KEY_ACCOUNT, "").orEmpty()
            val previousTerm = preferences.getString(KEY_TERM, "").orEmpty()
            val accountChanged = previousAccount != id
            val studentName = cachedStudentName(id)
            val loginEdit = preferences.edit()
                .putString(KEY_ACCOUNT, id)
                .putString(KEY_PASSWORD, localPassword)
                .putString(KEY_TERM, selectedSemester)
                .putString(KEY_SCORE_TERM, selectedSemester)
                .putString(KEY_STUDENT_NAME, studentName)
            if (accountChanged || previousTerm != selectedSemester) {
                loginEdit.remove(KEY_SCORES).remove(KEY_EXAMS)
            }
            loginEdit.apply()
            savePasswordCache(id, localPassword)
            saveStudentNameCache(id, studentName)
            notifyCourseDataChanged()
            showSchedulePage()
            return
        }
        val requestGeneration = academicSessionGeneration
        loginButton?.setButtonEnabled(false)
        loginButton?.text = "正在查询课程…"
        networkExecutor.execute {
            try {
                val repository = SdauCourseRepository()
                val remoteCourses = repository.queryCourses(id, pwd, selectedSemester)
                // 个人主页中的 infoContentTitle 是教务系统显示“姓名-学号”的来源。
                // 姓名获取失败不影响课程登录，成绩导出会回退为仅显示学号。
                val profile = runCatching { repository.queryStudentProfile(id, pwd) }.getOrNull()
                val courses = recolorCourses(remoteCourses.map { remote ->
                    Course(remote.day, remote.startSlot, remote.slotCount, remote.name, remote.room, remote.teacher, COURSE_COLORS.first(), Color.WHITE, remote.weeks)
                })
                runOnUiThread {
                    if (
                        requestGeneration != academicSessionGeneration ||
                        !onLoginPage ||
                        loginMode != LoginMode.PERSONAL
                    ) return@runOnUiThread
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString(KEY_ACCOUNT, id)
                        .putString(KEY_PASSWORD, pwd)
                        .putString(KEY_TERM, selectedSemester)
                        .putString(KEY_SCORE_TERM, selectedSemester)
                        .putString(KEY_STUDENT_NAME, profile?.name.orEmpty())
                        .remove(KEY_SCORES)
                        .remove(KEY_EXAMS)
                        .apply()
                    savePasswordCache(id, pwd)
                    saveStudentNameCache(id, profile?.name.orEmpty())
                    saveCourseCache(courses)
                    loginButton?.setButtonEnabled(true)
                    loginButton?.text = "进入课程表"
                    showSchedulePage()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (
                        requestGeneration != academicSessionGeneration ||
                        !onLoginPage ||
                        loginMode != LoginMode.PERSONAL
                    ) return@runOnUiThread
                    showLoginError(error)
                    loginButton?.setButtonEnabled(true)
                    loginButton?.text = "进入课程表"
                }
            }
        }
    }

    private fun publicGradeLabel(value: String): String {
        val normalized = value.trim().removeSuffix("级")
        return if (normalized.isBlank()) "未标注年级" else "${normalized}级"
    }

    private fun publicSelectorBox(
        label: String,
        options: List<String>,
        selected: String,
        onSelected: (String) -> Unit
    ): TextInputLayout = publicSelectorBox(label, options, selected, onSelected, true)

    private fun publicSelectorBox(
        label: String,
        options: List<String>,
        selected: String,
        onSelected: (String) -> Unit,
        enabled: Boolean
    ): TextInputLayout {
        val selectorEnabled = enabled && when (label) {
            "年级" -> publicCollegeSelection.isNotBlank()
            "专业" -> publicGradeSelection.isNotBlank()
            "班级" -> publicMajorSelection.isNotBlank()
            else -> true
        }
        val box = selectorInputBox(label).apply {
            isEnabled = selectorEnabled
            alpha = if (selectorEnabled) 1f else .55f
        }
        val field = MaterialAutoCompleteTextView(this).apply {
            setText(selected.takeIf { it in options }.orEmpty(), false)
            setTextColor(campusAndroidColors(this@MainActivity).primaryText)
            textSize = 18f
            inputType = InputType.TYPE_NULL
            isFocusable = false
            minHeight = dp(54)
            setPadding(dp(16), dp(6), dp(16), dp(2))
            background = selectorFieldBackground(selectorEnabled)
            gravity = Gravity.BOTTOM
            isEnabled = selectorEnabled
            setOnClickListener {
                if (!selectorEnabled) return@setOnClickListener
                showPublicOptionPicker(label, options, text?.toString().orEmpty()) { value ->
                    setText(value, false)
                    onSelected(value)
                }
            }
        }
        box.addView(field)
        return box
    }

    private fun showPublicOptionPicker(
        title: String,
        options: List<String>,
        selected: String,
        onSelected: (String) -> Unit
    ) {
        if (
            publicOptionOverlay != null ||
            publicOptionPickerCapturePending ||
            options.isEmpty()
        ) return
        publicOptionPickerCapturePending = true
        captureUpdateBackdrop { pageSnapshot ->
            publicOptionPickerCapturePending = false
            if (isFinishing || isDestroyed || !onLoginPage || publicOptionOverlay != null) {
                pageSnapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
                return@captureUpdateBackdrop
            }
            val dialog = LiquidPickerDialogView(
                context = this,
                pageSnapshot = pageSnapshot,
                title = "选择$title",
                options = options.map { option ->
                    LiquidPickerOption(
                        title = option,
                        selected = option == selected,
                        onClick = {
                            hidePublicOptionPicker()
                            onSelected(option)
                        }
                    )
                },
                highFrost = true,
                onDismiss = ::hidePublicOptionPicker
            )
            pageHost.addView(dialog, matchParentParams())
            publicOptionOverlay = dialog
            dialog.alpha = 0f
            dialog.animate().alpha(1f).setDuration(180).start()
        }
    }

    private fun hidePublicOptionPicker() {
        val overlay = publicOptionOverlay ?: return
        overlay.animate().alpha(0f).setDuration(130).withEndAction {
            pageHost.removeView(overlay)
            overlay.releaseSnapshot()
            if (publicOptionOverlay === overlay) publicOptionOverlay = null
        }.start()
    }

    private fun choosePublicOption(current: String, options: List<String>, preferred: List<String>): String {
        return current.takeIf { it in options }
            ?: preferred.firstOrNull { it in options }
            ?: ""
    }

    private fun buildPublicFilterFields(body: LinearLayout, term: String) {
        val index = loadStoredPublicScheduleIndex(term)
        if (index == null) {
            body.addView(text(
                "正在后台准备全校课表筛选，完成后会自动显示",
                13f,
                campusAndroidColors(this).secondaryText,
                Typeface.NORMAL
            ).apply {
                setLineSpacing(dp(3).toFloat(), 1f)
            }, spacedParams(dp(8)))
            startPublicScheduleSyncIfNeeded(term)
            return
        }
        val colleges = index.hierarchy.keys.sorted()
        val college = choosePublicOption(publicCollegeSelection, colleges, listOf("农学院"))
        val gradeMap = index.hierarchy[college].orEmpty()
        val grades = gradeMap.keys.sorted()
        val grade = choosePublicOption(publicGradeSelection, grades, listOf("2026级"))
        val majorMap = gradeMap[grade].orEmpty()
        val majors = majorMap.keys.sorted()
        val major = choosePublicOption(
            publicMajorSelection,
            majors,
            listOf("农业（拔尖基地班）", "农学（拔尖基地班）")
        )
        val classes = majorMap[major].orEmpty().sorted()
        val className = choosePublicOption(publicClassSelection, classes, listOf("农基2601"))

        loginUiState.resolvePublicSelection(college, grade, major, className)

        publicCollegeInput = MaterialAutoCompleteTextView(this)
        body.addView(publicFormField("学院", publicCollegeInput, colleges, college) {
            loginUiState.selectCollege(it)
            swapPage(buildLoginPage(), false, false)
        }, spacedParams(dp(14)))
        publicGradeInput = MaterialAutoCompleteTextView(this)
        body.addView(publicFormField("年级", publicGradeInput, grades, grade) {
            loginUiState.selectGrade(it)
            swapPage(buildLoginPage(), false, false)
        }, spacedParams(dp(14)))
        publicMajorInput = MaterialAutoCompleteTextView(this)
        body.addView(publicFormField("专业", publicMajorInput, majors, major) {
            loginUiState.selectMajor(it)
            swapPage(buildLoginPage(), false, false)
        }, spacedParams(dp(14)))
        publicClassInput = MaterialAutoCompleteTextView(this)
        body.addView(publicFormField("班级", publicClassInput, classes, className) {
            loginUiState.selectClass(it)
            swapPage(buildLoginPage(), false, false)
        }, spacedParams(dp(14)))
        if (index.recordCount == 0) {
            body.addView(text("全校课表正在准备，准备完成后可筛选查询", 13f, campusAndroidColors(this).secondaryText, Typeface.NORMAL).apply {
                setLineSpacing(dp(3).toFloat(), 1f)
            }, spacedParams(dp(8)))
        }
    }

    private fun publicFormField(
        label: String,
        field: MaterialAutoCompleteTextView,
        options: List<String>,
        selected: String = "",
        onSelected: (String) -> Unit
    ): View {
        val enabled = options.isNotEmpty()
        val resolved = selected.takeIf { it in options }.orEmpty()
        return selectionFilterCard(label, resolved, field, enabled, true) {
            showPublicOptionPicker(label, options, field.text?.toString().orEmpty()) { value ->
                field.setText(value, false)
                onSelected(value)
            }
        }
    }

    private fun attemptPublicScheduleLookup() {
        val term = semesterInput.text?.toString()?.trim().orEmpty()
        if (!hasPublicScheduleCache(term)) {
            loginStatus?.text = "暂无本地全校课表缓存，请先使用个人账号登录"
            loginStatus?.visibility = View.VISIBLE
            return
        }
        if (!hasPublicScheduleLookup(term)) {
            loginStatus?.text = "正在准备班级课表查询，请稍候"
            loginStatus?.visibility = View.VISIBLE
            startPublicScheduleSyncIfNeeded(term)
            return
        }
        if (publicCollegeSelection.isBlank() || publicGradeSelection.isBlank() ||
            publicMajorSelection.isBlank() || publicClassSelection.isBlank()
        ) {
            loginStatus?.text = "请选择学院、年级、专业和班级"
            loginStatus?.visibility = View.VISIBLE
            return
        }
        val selected = loadSelectedPublicScheduleCourses(term)
        if (selected.isEmpty()) {
            loginStatus?.text = "未找到该班级的课程信息"
            loginStatus?.visibility = View.VISIBLE
            return
        }
        publicScheduleCourses = buildPublicScheduleCourses(selected, term)
        publicScheduleTerm = term
        publicScheduleLabel = "$publicCollegeSelection · $publicGradeSelection · $publicMajorSelection · $publicClassSelection"
        publicScheduleClassName = publicClassSelection
        viewingPublicSchedule = true
        currentWeek = weekForTerm(term)
        showSchedulePage()
    }

    private fun showLoginError(error: Exception) {
        val detail = error.message?.replace(Regex("\\s+"), " ")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(180)
            ?: "教务系统暂时不可用，请稍后重试"
        loginStatus?.apply {
            text = "查询失败：$detail"
            visibility = View.VISIBLE
        }
    }

    private fun buildSchedulePage(): View {
        val page = FrameLayout(this).apply { background = SilkyGradientDrawable() }
        schedulePageRoot = page
        liveScheduleBackgroundBitmap = null
        liveScheduleBackgroundCrop = null
        liveScheduleBackgroundScrimColor = null
        pendingSchedulePalettePreview = null
        schedulePageBackgroundImage = null
        schedulePageBackgroundScrim = null
        val customBackground = loadCustomBackgroundBitmap()
        val backgroundScrim = customBackgroundScrimColor()
        currentPageBackgroundBitmap = customBackground
        scheduleTextPalette = resolveScheduleTextPalette(customBackground, backgroundScrim)
        customBackground?.let { backgroundBitmap ->
            val backgroundImage = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(backgroundBitmap)
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val scrimView = View(this).apply {
                setBackgroundColor(backgroundScrim)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            schedulePageBackgroundImage = backgroundImage
            schedulePageBackgroundScrim = scrimView
            page.addView(backgroundImage, FrameLayout.LayoutParams(-1, -1))
            page.addView(scrimView, FrameLayout.LayoutParams(-1, -1))
        }
        val navigationVisibleHeight = dp(54)
        val navigationHostHeight = dp(116)
        val navigationBottomMargin = dp(16)
        val initialSection = when (currentMainSection) {
            1 -> buildExamSection()
            2 -> buildGradesSection()
            3 -> buildEmptyRoomSection()
            else -> buildScheduleSection()
        }
        val sectionHost = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(initialSection, FrameLayout.LayoutParams(-1, -1))
        }
        mainSectionHost = sectionHost
        val sectionBottomMargin = navigationVisibleHeight + navigationBottomMargin + dp(4)
        val sectionLayoutParams = FrameLayout.LayoutParams(-1, -1).apply {
            bottomMargin = sectionBottomMargin
        }
        page.addView(sectionHost, sectionLayoutParams)
        val navigation = createCampusLiquidBottomTabsView(
            this,
            currentMainSection,
            customBackground,
            backgroundScrim
        ) { index, _ -> showMainSection(index) }
        bottomNavigation = navigation
        val navigationLayoutParams = FrameLayout.LayoutParams(
            dp(288), navigationHostHeight, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = 0
        }
        page.addView(navigation, navigationLayoutParams)
        ViewCompat.setOnApplyWindowInsetsListener(page) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            sectionHost.setPadding(0, systemBars.top, 0, 0)
            sectionLayoutParams.bottomMargin = sectionBottomMargin + systemBars.bottom
            navigationLayoutParams.bottomMargin = systemBars.bottom
            sectionHost.layoutParams = sectionLayoutParams
            navigation.layoutParams = navigationLayoutParams
            insets
        }
        page.post { ViewCompat.requestApplyInsets(page) }
        return page
    }

    private fun buildScheduleSection(): View {
        val section = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        val content = verticalLayout().apply { setBackgroundColor(Color.TRANSPARENT) }
        content.addView(buildScheduleHeader(), matchWrapParams())
        scheduleGrid = ScheduleGridView(this, activeScheduleCourses())
        scheduleGrid?.setScheduleMode(scheduleMode)
        scheduleGrid?.setWeekIndex(currentWeek)
        content.addView(scheduleGrid, LinearLayout.LayoutParams(-1, 0, 1f))
        section.addView(content, FrameLayout.LayoutParams(-1, -1))
        val versionLabel = ScheduleVersionComposeView(
            context = this,
            version = appDisplayVersion,
            initialPalette = scheduleTextPalette
        )
        scheduleVersion = versionLabel
        section.addView(versionLabel, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(10)
            bottomMargin = dp(6)
        })
        return section
    }

    private fun showMainSection(index: Int) {
        if (index == currentMainSection) {
            if (index == 0) jumpToCurrentWeek()
            if (index == 1) refreshExams()
            if (index == 2) refreshScores()
            return
        }
        val host = mainSectionHost ?: return
        val previousIndex = currentMainSection
        currentMainSection = index
        if (index == 3 && emptyRoomResult == null && !emptyRoomsLoading) {
            syncEmptyRoomDefaultsToNow()
        }
        val next = when (index) {
            1 -> buildExamSection()
            2 -> buildGradesSection()
            3 -> buildEmptyRoomSection()
            else -> buildScheduleSection()
        }
        // Exam and score pages contain their own full-page backdrop source for
        // LiquidGlass sampling. Sliding that source over the fixed custom
        // wallpaper makes the image appear to jump during section changes.
        val customBackdropTransition = currentPageBackgroundBitmap != null &&
            (index == 1 || index == 2 || previousIndex == 1 || previousIndex == 2)
        val distance = if (index == 2 || customBackdropTransition) {
            0f
        } else {
            dp(42).toFloat() * if (index > previousIndex) 1f else -1f
        }
        replaceMainSection(host, next, index, distance, 230L)
    }

    /**
     * 始终只保留“当前页 + 正在进入页”两层，并用代次阻止旧动画清理新页面。
     * 这同时覆盖底栏快速切换和教务数据异步刷新，避免多个半透明页面偶发叠加。
     */
    private fun replaceMainSection(
        host: FrameLayout,
        next: View,
        sectionIndex: Int,
        enterTranslationX: Float = 0f,
        enterDuration: Long = 180L
    ) {
        if (host !== mainSectionHost || currentMainSection != sectionIndex) return
        val generation = ++mainSectionTransitionGeneration

        // 最上层子 View 才是用户当前看到的页面；更早的残留层立即移除。
        val previous = host.getChildAt(host.childCount - 1)
        for (childIndex in host.childCount - 1 downTo 0) {
            val child = host.getChildAt(childIndex)
            child.animate().setListener(null).withEndAction(null).cancel()
            if (child !== previous) host.removeViewAt(childIndex)
        }

        next.animate().setListener(null).withEndAction(null).cancel()
        next.alpha = 0f
        next.translationX = enterTranslationX
        host.addView(next, FrameLayout.LayoutParams(-1, -1))

        next.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(enterDuration)
            .withEndAction {
                if (
                    generation == mainSectionTransitionGeneration &&
                    host === mainSectionHost &&
                    currentMainSection == sectionIndex &&
                    next.parent === host
                ) {
                    // 动画完成后强制恢复单层，防止刷新回调与导航回调交错留下旧页。
                    for (childIndex in host.childCount - 1 downTo 0) {
                        val child = host.getChildAt(childIndex)
                        if (child !== next) {
                            child.animate().setListener(null).withEndAction(null).cancel()
                            host.removeViewAt(childIndex)
                        }
                    }
                    next.alpha = 1f
                    next.translationX = 0f
                }
            }
            .start()

        previous?.animate()
            ?.alpha(0f)
            ?.translationX(-enterTranslationX * .55f)
            ?.setDuration(minOf(160L, enterDuration))
            ?.withEndAction {
                if (previous.parent === host) host.removeView(previous)
            }
            ?.start()
    }

    private fun buildExamSection(refresh: Boolean = true): View {
        if (viewingPublicSchedule) {
            return buildExamStateSection(
                activeScheduleTerm(), hasLoaded = true, error = null,
                emptyDescription = "此功能暂不可用\n请切换回个人账号重新查询"
            )
        }
        val term = activeScheduleTerm()
        val cached = loadExamCache()?.takeIf { it.term == term }
        val section = if (!cached?.records.isNullOrEmpty()) {
            buildExamResultSection(term, cached!!.records)
        } else {
            buildExamStateSection(term, hasLoaded = cached != null, error = examLoadError)
        }
        if (refresh && !examsLoading) section.post { refreshExams() }
        return section
    }

    /** Gray supporting copy needs extra contrast only when it is drawn over a custom image. */
    private fun secondaryTextTypeface(): Int =
        if (currentPageBackgroundBitmap != null) Typeface.BOLD else Typeface.NORMAL

    private fun refreshExams() {
        if (viewingPublicSchedule) return
        if (examsLoading) return
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val password = preferences.getString(KEY_PASSWORD, "").orEmpty()
        val term = selectedTerm()
        val cached = loadExamCache()?.takeIf { it.term == term }
        if (account.isBlank() || password.isBlank()) {
            examLoadError = if (cached == null) "登录信息不完整，请重新登录后再查询考试安排。" else null
            if (cached == null) refreshVisibleExams()
            return
        }
        if (account == "114514") {
            examLoadError = null
            val records = sampleExams()
            if (cached?.records != records) {
                saveExamCache(term, records)
                refreshVisibleExams()
            }
            return
        }
        examsLoading = true
        examLoadError = null
        if (cached == null) refreshVisibleExams()
        val sessionGeneration = academicSessionGeneration
        networkExecutor.execute {
            try {
                val records = SdauCourseRepository().queryExams(account, password, term)
                runOnUiThread {
                    if (
                        sessionGeneration != academicSessionGeneration ||
                        !isActiveAcademicSession(account, term)
                    ) return@runOnUiThread
                    val changed = cached?.records != records
                    saveExamCache(term, records, account)
                    examsLoading = false
                    examLoadError = null
                    if (changed) refreshVisibleExams()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (
                        sessionGeneration != academicSessionGeneration ||
                        !isActiveAcademicSession(account, term)
                    ) return@runOnUiThread
                    examsLoading = false
                    examLoadError = if (cached == null) {
                        error.message?.replace(Regex("\\s+"), " ")?.take(160)
                            ?: "教务系统暂时无法访问，请稍后重试。"
                    } else null
                    if (cached == null) refreshVisibleExams()
                }
            }
        }
    }

    private fun refreshVisibleExams() {
        if (currentMainSection != 1) return
        val host = mainSectionHost ?: return
        replaceMainSection(host, buildExamSection(refresh = false), 1, 0f, 170L)
    }

    private fun buildExamStateSection(
        term: String,
        hasLoaded: Boolean,
        error: String?,
        emptyTitle: String = "暂无考试安排",
        emptyDescription: String = "本学期暂未发布考试信息\n后续安排会在查询后显示在这里"
    ): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
        }
        val body = verticalLayout().apply { setPadding(dp(20), dp(18), dp(20), dp(28)) }
        body.addView(text("考试安排", 28f, scheduleTextPalette.primary, Typeface.BOLD).apply {
            applyScheduleTextHalo()
        }, spacedParams(dp(7)))
        body.addView(text(
            "$term 学期",
            13f,
            scheduleTextPalette.secondary,
            secondaryTextTypeface()
        ).apply {
            applyScheduleTextHalo()
        }, matchWrapParams())
        val stateView = when {
            examsLoading || (!hasLoaded && error.isNullOrBlank()) -> verticalLayout().apply {
                gravity = Gravity.CENTER
                addView(ProgressBar(this@MainActivity).apply {
                    indeterminateTintList = ColorStateList.valueOf(activeThemeColors.accent)
                    contentDescription = "正在加载考试安排"
                }, LinearLayout.LayoutParams(dp(34), dp(34)))
            }
            !error.isNullOrBlank() -> verticalLayout().apply {
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(20), dp(22), dp(20))
                addView(text("!", 20f, activeThemeColors.error, Typeface.BOLD).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(48), dp(42)))
                addView(text(error, 14f, scheduleTextPalette.secondary, secondaryTextTypeface()).apply {
                    gravity = Gravity.CENTER
                    setLineSpacing(dp(3).toFloat(), 1f)
                    applyScheduleTextHalo()
                }, matchWrapParams())
                isClickable = true
                contentDescription = "考试安排加载失败，点击重试"
                setOnClickListener { refreshExams() }
            }
            else -> buildAcademicEmptyState(
                EmptyAcademicState.EXAMS,
                emptyTitle,
                emptyDescription
            )
        }
        body.addView(stateView, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll.addView(body, FrameLayout.LayoutParams(-1, -1))
        return scroll
    }

    private fun buildAcademicEmptyState(
        type: EmptyAcademicState,
        title: String,
        description: String
    ): View = verticalLayout().apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(20), dp(14), dp(24))
        addView(AcademicEmptyIllustration(this@MainActivity, type), LinearLayout.LayoutParams(dp(158), dp(132)).apply {
            bottomMargin = dp(14)
        })
        addView(text(title, 18f, scheduleTextPalette.primary, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            applyScheduleTextHalo()
        }, spacedParams(dp(10)))
        addView(text(
            description,
            13f,
            scheduleTextPalette.secondary,
            secondaryTextTypeface()
        ).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            applyScheduleTextHalo()
        }, matchWrapParams())
    }

    private fun buildExamResultSection(term: String, records: List<RemoteExam>): View {
        return createExamLiquidScrollPageView(
            context = this,
            term = term,
            records = records,
            pageBackgroundBitmap = currentPageBackgroundBitmap,
            pageBackgroundScrim = customBackgroundScrimColor(),
            textPalette = scheduleTextPalette
        )
    }

    private fun buildGradesSection(refresh: Boolean = true): View {
        if (viewingPublicSchedule) {
            return buildGradeStateSection(
                activeScheduleTerm(), hasLoadedResult = true, error = null,
                emptyTitle = "暂无成绩信息",
                emptyDescription = "此功能暂不可用\n请切换回个人账号重新查询"
            )
        }
        val selectedTerm = selectedScoreTerm()
        val cached = loadScoreCache()?.takeIf { it.term == selectedTerm }
        val result = if (cached != null && cached.records.isNotEmpty()) {
            buildScoreResultSection(cached)
        } else {
            buildGradeStateSection(selectedTerm, cached != null, scoreLoadError)
        }
        if (refresh && !scoresLoading) result.post { refreshScores() }
        return result
    }

    private fun refreshScores() {
        if (viewingPublicSchedule) return
        if (scoresLoading) return
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val password = preferences.getString(KEY_PASSWORD, "").orEmpty()
        val term = selectedScoreTerm()
        val cached = loadScoreCache()?.takeIf { it.term == term }
        if (account.isBlank() || password.isBlank()) {
            scoreLoadError = if (cached == null) "登录信息不完整，请重新登录后再查询成绩。" else null
            if (cached == null) refreshVisibleGrades()
            return
        }
        if (account == "114514") {
            scoreLoadError = null
            if (preferences.getString(KEY_STUDENT_NAME, "").orEmpty().isBlank()) {
                preferences.edit().putString(KEY_STUDENT_NAME, "演示用户").apply()
            }
            val result = sampleScoreResult(term)
            if (cached != result) {
                saveScoreCache(result)
                refreshVisibleGrades()
            }
            return
        }
        scoresLoading = true
        scoreLoadError = null
        if (cached == null) refreshVisibleGrades()
        val sessionGeneration = academicSessionGeneration
        networkExecutor.execute {
            try {
                val repository = SdauCourseRepository()
                val profile = if (preferences.getString(KEY_STUDENT_NAME, "").orEmpty().isBlank()) {
                    runCatching { repository.queryStudentProfile(account, password) }.getOrNull()
                } else {
                    null
                }
                val result = repository.queryScores(account, password, term, allScoreTerms(account))
                val changed = cached != result
                runOnUiThread {
                    if (
                        sessionGeneration != academicSessionGeneration ||
                        !isActiveAcademicSession(account)
                    ) return@runOnUiThread
                    profile?.let { saveStudentName(account, it.name) }
                    saveScoreCache(result, account)
                    scoresLoading = false
                    scoreLoadError = null
                    if (changed) refreshVisibleGrades()
                    if (selectedScoreTerm() != term) refreshScores()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (
                        sessionGeneration != academicSessionGeneration ||
                        !isActiveAcademicSession(account)
                    ) return@runOnUiThread
                    scoresLoading = false
                    scoreLoadError = if (cached == null) {
                        error.message?.replace(Regex("\\s+"), " ")?.take(160)
                            ?: "教务系统暂时无法访问，请稍后重试。"
                    } else null
                    if (cached == null) refreshVisibleGrades()
                    if (selectedScoreTerm() != term) refreshScores()
                }
            }
        }
    }

    private fun refreshVisibleGrades() {
        if (currentMainSection != 2) return
        val host = mainSectionHost ?: return
        replaceMainSection(host, buildGradesSection(refresh = false), 2, 0f, 170L)
    }

    private fun buildEmptyRoomSection(): View {
        val visibleResult = emptyRoomResult?.takeIf {
            it.campus == emptyRoomCampus && it.week == emptyRoomWeek &&
                it.weekday == emptyRoomWeekday && it.sectionCode == emptyRoomSectionCode
        }
        val visibleGroups = visibleResult?.let(::groupEmptyRooms).orEmpty()
        val shouldShowResultSection = emptyRoomsLoading ||
            !emptyRoomLoadError.isNullOrBlank() || visibleResult != null
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
        }
        val body = verticalLayout().apply { setPadding(dp(20), dp(18), dp(20), dp(28)) }
        body.addView(text("空教室查询", 28f, scheduleTextPalette.primary, Typeface.BOLD).apply {
            applyScheduleTextHalo()
        }, spacedParams(dp(18)))
        body.addView(buildEmptyRoomQueryPanel(), spacedParams(dp(26)))

        if (shouldShowResultSection) {
            val resultHeader = horizontalLayout().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
            }
            resultHeader.addView(text(
                "查询结果",
                20f,
                scheduleTextPalette.primary,
                Typeface.BOLD
            ).apply {
                applyScheduleTextHalo()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            body.addView(resultHeader, spacedParams(dp(7)))
            body.addView(text(
                "${emptyRoomCampus} · 第${emptyRoomWeek}周 · ${emptyRoomWeekdayLabel(emptyRoomWeekday)} · ${emptyRoomSectionLabel(emptyRoomSectionCode)}",
                12f,
                scheduleTextPalette.secondary,
                secondaryTextTypeface()
            ).apply {
                setPadding(dp(16), 0, dp(16), 0)
                applyScheduleTextHalo()
            }, spacedParams(dp(15)))

            val state = when {
                emptyRoomsLoading -> emptyRoomResultSurface().apply {
                    minimumHeight = dp(260)
                    val loading = verticalLayout().apply {
                        gravity = Gravity.CENTER
                        addView(ProgressBar(this@MainActivity).apply {
                            isIndeterminate = true
                            indeterminateTintList = ColorStateList.valueOf(activeThemeColors.accent)
                            contentDescription = "正在查询空教室"
                        }, LinearLayout.LayoutParams(dp(36), dp(36)))
                        addView(text(
                            "正在整理空闲教室",
                            13f,
                            scheduleTextPalette.secondary,
                            secondaryTextTypeface()
                        ).apply {
                            gravity = Gravity.CENTER
                            applyScheduleTextHalo()
                        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
                    }
                    addView(loading, FrameLayout.LayoutParams(-1, dp(260)))
                }
                !emptyRoomLoadError.isNullOrBlank() -> emptyRoomResultSurface().apply {
                    minimumHeight = dp(260)
                    val errorView = verticalLayout().apply {
                        gravity = Gravity.CENTER
                        setPadding(dp(22), dp(32), dp(22), dp(32))
                        addView(text("!", 21f, activeThemeColors.error, Typeface.BOLD).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(50), dp(44)).apply {
                            bottomMargin = dp(8)
                        })
                        addView(text(
                            emptyRoomLoadError.orEmpty(),
                            14f,
                            scheduleTextPalette.secondary,
                            secondaryTextTypeface()
                        ).apply {
                            gravity = Gravity.CENTER
                            setLineSpacing(dp(4).toFloat(), 1f)
                            applyScheduleTextHalo()
                        }, matchWrapParams())
                    }
                    addView(errorView, FrameLayout.LayoutParams(-1, dp(260)))
                    isClickable = true
                    contentDescription = "空教室查询失败，点击重试"
                    setOnClickListener { refreshEmptyRooms() }
                }
                visibleGroups.isEmpty() -> emptyRoomResultSurface().apply {
                    addView(buildAcademicEmptyState(
                        EmptyAcademicState.ROOMS,
                        "暂无空闲教室",
                        "当前校区与时段暂未找到可用教室\n可以更换星期或节次后再查询"
                    ), FrameLayout.LayoutParams(-1, dp(310)))
                }
                else -> buildEmptyRoomResults(requireNotNull(visibleResult), visibleGroups)
            }
            body.addView(state, matchWrapParams())
        }
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        return scroll
    }

    private fun buildEmptyRoomQueryPanel(): View = MaterialCardView(this).apply {
        radius = dp(25f).toFloat()
        cardElevation = 0f
        strokeWidth = 0
        setCardBackgroundColor(Color.TRANSPARENT)
        val panel = verticalLayout().apply {
            setPadding(dp(16), dp(13), dp(16), dp(16))
            background = ColorDrawable(Color.TRANSPARENT)
        }
        val titleRow = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(text(
            "查询条件",
            20f,
            scheduleTextPalette.primary,
            Typeface.BOLD
        ).apply {
            applyScheduleTextHalo()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val queryButton = ImageButton(this@MainActivity).apply {
            setImageResource(R.drawable.ic_search_room)
            imageTintList = ColorStateList.valueOf(activeThemeColors.accent)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            contentDescription = if (emptyRoomsLoading) "正在查询空教室" else "查询空教室"
            isEnabled = !emptyRoomsLoading
            alpha = if (emptyRoomsLoading) .58f else 1f
            background = ColorDrawable(Color.TRANSPARENT)
            visibility = if (emptyRoomQueryExpanded) View.VISIBLE else View.GONE
            setOnClickListener { refreshEmptyRooms() }
        }
        titleRow.addView(queryButton, LinearLayout.LayoutParams(dp(34), dp(34)))
        val toggle = emptyRoomCollapseButton(emptyRoomQueryExpanded, "查询条件")
        titleRow.addView(toggle, LinearLayout.LayoutParams(dp(34), dp(34)).apply { leftMargin = dp(2) })
        titleRow.isClickable = true
        titleRow.isFocusable = true
        titleRow.contentDescription = if (emptyRoomQueryExpanded) "折叠查询条件" else "展开查询条件"
        panel.addView(titleRow, matchWrapParams())

        val queryControls = verticalLayout()

        val firstFilters = horizontalLayout()
        firstFilters.addView(emptyRoomFilterCard("校区", emptyRoomCampus) {
            showEmptyRoomFilterPicker(
                "选择校区",
                listOf("岱宗校区", "泮河校区", "西北片区").map { it to it },
                emptyRoomCampus
            ) {
                emptyRoomCampus = it
                onEmptyRoomFilterChanged()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        firstFilters.addView(emptyRoomFilterCard("周次", "第${emptyRoomWeek}周") {
            showEmptyRoomFilterPicker(
                "选择周次",
                (1..20).map { "第${it}周" to it.toString() },
                emptyRoomWeek.toString()
            ) {
                emptyRoomWeek = it.toIntOrNull()?.coerceIn(1, 20) ?: emptyRoomWeek
                onEmptyRoomFilterChanged()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        queryControls.addView(firstFilters, spacedParams(dp(10)))

        val secondFilters = horizontalLayout()
        secondFilters.addView(emptyRoomFilterCard("星期", emptyRoomWeekdayLabel(emptyRoomWeekday)) {
            showEmptyRoomFilterPicker(
                "选择星期",
                (1..7).map { emptyRoomWeekdayLabel(it) to it.toString() },
                emptyRoomWeekday.toString()
            ) {
                emptyRoomWeekday = it.toIntOrNull()?.coerceIn(1, 7) ?: emptyRoomWeekday
                onEmptyRoomFilterChanged()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        secondFilters.addView(emptyRoomFilterCard("节次", emptyRoomSectionLabel(emptyRoomSectionCode)) {
            val options = listOf(
                "第一大节" to "0102", "第二大节" to "0304", "中午" to "中午",
                "第三大节" to "0506", "第四大节" to "0708", "第五大节" to "0910",
                "晚间" to "晚间"
            )
            showEmptyRoomFilterPicker("选择节次", options, emptyRoomSectionCode) {
                emptyRoomSectionCode = it
                onEmptyRoomFilterChanged()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        queryControls.addView(secondFilters, matchWrapParams())
        panel.addView(queryControls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })
        if (!emptyRoomQueryExpanded) queryControls.visibility = View.GONE
        val toggleQuery: () -> Unit = toggle@ {
            if (!toggle.isClickable) return@toggle
            val expanding = !emptyRoomQueryExpanded
            emptyRoomQueryExpanded = expanding
            titleRow.contentDescription = if (expanding) "折叠查询条件" else "展开查询条件"
            queryButton.animate().cancel()
            if (expanding) {
                queryButton.visibility = View.VISIBLE
                queryButton.alpha = 0f
                queryButton.animate()
                    .alpha(if (emptyRoomsLoading) .58f else 1f)
                    .setDuration(180L)
                    .setStartDelay(55L)
                    .start()
            } else {
                queryButton.animate()
                    .alpha(0f)
                    .setDuration(110L)
                    .withEndAction {
                        if (!emptyRoomQueryExpanded) queryButton.visibility = View.GONE
                        queryButton.alpha = if (emptyRoomsLoading) .58f else 1f
                    }
                    .start()
            }
            animateEmptyRoomCollapsible(panel, queryControls, toggle, expanding, "查询条件")
        }
        titleRow.setOnClickListener { toggleQuery() }
        toggle.setOnClickListener { toggleQuery() }
        addView(panel)
    }

    private fun emptyRoomCollapseButton(expanded: Boolean, targetName: String): ImageButton = ImageButton(this).apply {
        setImageResource(R.drawable.ic_expand_chevron)
        imageTintList = ColorStateList.valueOf(activeThemeColors.accent)
        scaleType = ImageView.ScaleType.CENTER
        setPadding(dp(5), dp(5), dp(5), dp(5))
        background = ColorDrawable(Color.TRANSPARENT)
        rotation = if (expanded) 180f else 0f
        contentDescription = if (expanded) "折叠$targetName" else "展开$targetName"
        isClickable = true
        isFocusable = true
    }

    private fun animateEmptyRoomCollapsible(
        panel: LinearLayout,
        controls: View,
        toggle: ImageButton,
        expanding: Boolean,
        targetName: String
    ) {
        toggle.isClickable = false
        controls.animate().cancel()
        toggle.animate().cancel()
        toggle.animate()
            .rotation(if (expanding) 180f else 0f)
            .setDuration(230L)
            .setInterpolator(PathInterpolator(.2f, .78f, .2f, 1f))
            .start()

        val layoutParams = controls.layoutParams
        val expandedLayoutHeight = layoutParams.height
        val startHeight: Int
        val endHeight: Int
        if (expanding) {
            controls.visibility = View.VISIBLE
            controls.alpha = 0f
            controls.translationY = -dp(5f).toFloat()
            startHeight = 0
            endHeight = if (expandedLayoutHeight > 0) {
                expandedLayoutHeight
            } else {
                val availableWidth = (panel.width - panel.paddingLeft - panel.paddingRight).coerceAtLeast(1)
                controls.measure(
                    View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                controls.measuredHeight
            }
            layoutParams.height = 0
        } else {
            startHeight = controls.height.coerceAtLeast(controls.measuredHeight)
            endHeight = 0
        }
        controls.layoutParams = layoutParams

        ValueAnimator.ofInt(startHeight, endHeight).apply {
            duration = 260L
            interpolator = PathInterpolator(.2f, .78f, .2f, 1f)
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                layoutParams.height = animator.animatedValue as Int
                controls.layoutParams = layoutParams
                controls.alpha = if (expanding) fraction else 1f - fraction
                controls.translationY = if (expanding) -dp(5f) * (1f - fraction) else -dp(5f) * fraction
                panel.requestLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (expanding) {
                        layoutParams.height = expandedLayoutHeight
                        controls.layoutParams = layoutParams
                        controls.alpha = 1f
                        controls.translationY = 0f
                        controls.visibility = View.VISIBLE
                    } else {
                        // Hide the zero-height view before restoring WRAP_CONTENT. Restoring
                        // the expanded height while it is still visible produces a one-frame
                        // layout jump at the end of the collapse animation.
                        controls.visibility = View.GONE
                        controls.alpha = 1f
                        controls.translationY = 0f
                        layoutParams.height = expandedLayoutHeight
                        controls.layoutParams = layoutParams
                    }
                    toggle.contentDescription = if (expanding) "折叠$targetName" else "展开$targetName"
                    toggle.isClickable = true
                }
            })
            start()
        }
    }

    private fun emptyRoomFilterCard(label: String, value: String, onClick: () -> Unit): View =
        createEmptyRoomLiquidFilterCardView(
            context = this,
            label = label,
            value = value,
            pageBackgroundBitmap = currentPageBackgroundBitmap,
            pageBackgroundScrim = customBackgroundScrimColor(),
            textPalette = scheduleTextPalette,
            onClick = onClick
        )

    private fun selectionFilterCard(
        label: String,
        value: String,
        field: MaterialAutoCompleteTextView?,
        enabled: Boolean,
        showBottomDivider: Boolean,
        onClick: () -> Unit
    ): View = MaterialCardView(this).apply {
        radius = dp(15f).toFloat()
        cardElevation = 0f
        maxCardElevation = 0f
        translationZ = 0f
        stateListAnimator = null
        strokeWidth = 0
        if (activeThemeColors.isDark) {
            // 与账号输入框统一：不做悬浮色块，仅保留下方分隔线。
            setCardBackgroundColor(Color.TRANSPARENT)
        } else {
            setCardBackgroundColor(Color.argb(102, 255, 255, 255))
        }
        isClickable = enabled
        isEnabled = enabled
        alpha = if (enabled) 1f else .55f
        contentDescription = "$label，当前$value"
        setOnClickListener { if (enabled) onClick() }
        val content = verticalLayout().apply { setPadding(dp(13), dp(10), dp(11), dp(10)) }
        val labelTextSize = if (showBottomDivider) 13f else 11f
        val valueTextSize = if (showBottomDivider) 15.5f else 13.5f
        content.addView(text(label, labelTextSize, activeThemeColors.secondaryText, Typeface.NORMAL), spacedParams(dp(5)))
        val valueRow = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
        val displayValue = value.ifBlank { "请选择" }
        val valueView = field?.apply {
            setText(value, false)
            setTextColor(activeThemeColors.primaryText)
            textSize = valueTextSize
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            inputType = InputType.TYPE_NULL
            isFocusable = false
            isClickable = enabled
            isEnabled = enabled
            minHeight = 0
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(Color.TRANSPARENT)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { if (enabled) onClick() }
        } ?: text(displayValue, valueTextSize, activeThemeColors.primaryText, Typeface.BOLD).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        valueRow.addView(valueView, LinearLayout.LayoutParams(0, -2, 1f))
        if (!showBottomDivider) {
            valueRow.addView(text("⌄", 14f, activeThemeColors.secondaryText, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(18), -2))
        }
        content.addView(valueRow, matchWrapParams())
        if (showBottomDivider) {
            content.addView(View(this@MainActivity).apply {
                setBackgroundColor(activeThemeColors.fieldDivider)
            }, LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(8)
            })
        }
        addView(content)
    }

    private fun emptyRoomResultSurface(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(23f).toFloat()
        cardElevation = 0f
        if (activeThemeColors.isDark) {
            strokeWidth = 0
            setCardBackgroundColor(Color.rgb(31, 31, 33))
        } else {
            strokeWidth = dp(1)
            strokeColor = Color.argb(92, 255, 255, 255)
            setCardBackgroundColor(Color.argb(104, 216, 225, 242))
        }
    }

    private fun buildEmptyRoomResults(
        result: RemoteEmptyRoomResult,
        groups: List<EmptyRoomGroup>
    ): View = verticalLayout().apply {
        contentDescription = "${result.campus}空闲教室列表"
        groups.forEachIndexed { groupIndex, group ->
            val groupStateKey = "${result.campus}:${group.title}"
            val groupExpanded = groupStateKey !in collapsedEmptyRoomGroups
            val groupCard = createEmptyRoomLiquidGroupCardView(
                context = this@MainActivity,
                groupKey = groupStateKey,
                title = group.title,
                accentColor = group.accent,
                rooms = group.rooms,
                initiallyExpanded = groupExpanded,
                pageBackgroundBitmap = currentPageBackgroundBitmap,
                pageBackgroundScrim = customBackgroundScrimColor(),
                textPalette = scheduleTextPalette
            ) { expanded ->
                if (expanded) collapsedEmptyRoomGroups.remove(groupStateKey)
                else collapsedEmptyRoomGroups.add(groupStateKey)
            }
            addView(
                groupCard,
                if (groupIndex == groups.lastIndex) matchWrapParams() else spacedParams(dp(11))
            )
        }
    }

    private fun groupEmptyRooms(result: RemoteEmptyRoomResult): List<EmptyRoomGroup> {
        val rooms = result.rooms.asSequence()
            .map { it.trim().removePrefix("@").trim() }
            .filter { it.isNotBlank() && shouldDisplayEmptyRoom(result.campus, it) }
            .distinctBy { emptyRoomMatchKey(it) }
            .sortedBy(::emptyRoomNaturalSortKey)
            .toList()

        val buckets = when (result.campus) {
            "岱宗校区" -> linkedMapOf(
                ("5N" to Color.rgb(103, 151, 214)) to mutableListOf<String>(),
                ("5S" to Color.rgb(92, 181, 164)) to mutableListOf(),
                ("文理大楼" to Color.rgb(139, 132, 199)) to mutableListOf(),
                ("12号楼" to Color.rgb(220, 156, 96)) to mutableListOf(),
                ("其他教室" to Color.rgb(116, 137, 174)) to mutableListOf()
            )
            "泮河校区" -> linkedMapOf(
                ("中央片区" to Color.rgb(102, 146, 211)) to mutableListOf<String>(),
                ("东南片区" to Color.rgb(219, 132, 116)) to mutableListOf(),
                ("其他教室" to Color.rgb(91, 176, 166)) to mutableListOf()
            )
            else -> linkedMapOf(
                ("22号楼" to Color.rgb(137, 129, 198)) to mutableListOf<String>(),
                ("其他教室" to Color.rgb(103, 151, 190)) to mutableListOf()
            )
        }

        rooms.forEach { room ->
            val key = emptyRoomMatchKey(room)
            val groupKey = when (result.campus) {
                "岱宗校区" -> when {
                    key.startsWith("5N") -> "5N"
                    key.startsWith("5S") -> "5S"
                    key.contains("文理大楼") -> "文理大楼"
                    key.contains("12号楼") -> "12号楼"
                    else -> "其他教室"
                }
                "泮河校区" -> when {
                    key.startsWith("19#") -> "东南片区"
                    key.firstOrNull() in setOf('N', 'W', 'E', 'S') -> "中央片区"
                    else -> "其他教室"
                }
                else -> if (key.startsWith("22#")) "22号楼" else "其他教室"
            }
            buckets.entries.firstOrNull { it.key.first == groupKey }?.value?.add(room)
        }

        return buckets.mapNotNull { (definition, groupedRooms) ->
            groupedRooms.takeIf { it.isNotEmpty() }?.let {
                EmptyRoomGroup(definition.first, definition.second, it)
            }
        }
    }

    private fun shouldDisplayEmptyRoom(campus: String, room: String): Boolean {
        val key = emptyRoomMatchKey(room)
        if (key.contains("线上教学")) return false
        if (campus == "泮河校区") {
            if (key.contains("南校区体育羽毛球馆")) return false
            if (key.contains("南校实践环节地点") && key.contains("化学实践S")) return false
        }
        return true
    }

    private fun emptyRoomMatchKey(room: String): String = room
        .replace(Regex("\\s+"), "")
        .replace('＃', '#')
        .replace('Ｓ', 'S')
        .uppercase(Locale.ROOT)

    private fun emptyRoomNaturalSortKey(room: String): String = Regex("\\d+")
        .replace(emptyRoomMatchKey(room)) { match -> match.value.padStart(7, '0') }

    private fun onEmptyRoomFilterChanged() {
        emptyRoomRequestGeneration++
        emptyRoomsLoading = false
        emptyRoomLoadError = null
        refreshVisibleEmptyRooms()
    }

    private fun collapseEmptyRoomResultGroups(result: RemoteEmptyRoomResult) {
        val campusPrefix = "${result.campus}:"
        collapsedEmptyRoomGroups.removeAll { it.startsWith(campusPrefix) }
        groupEmptyRooms(result).forEach { group ->
            collapsedEmptyRoomGroups.add("${result.campus}:${group.title}")
        }
    }

    private fun refreshEmptyRooms() {
        if (currentMainSection != 3) return
        if (emptyRoomsLoading) return
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val password = preferences.getString(KEY_PASSWORD, "").orEmpty()
        val campus = emptyRoomCampus
        val week = emptyRoomWeek
        val weekday = emptyRoomWeekday
        val section = emptyRoomSectionCode
        val generation = ++emptyRoomRequestGeneration

        if (account.isBlank() || password.isBlank()) {
            emptyRoomsLoading = false
            emptyRoomLoadError = "登录信息不完整，请重新登录后再查询空教室。"
            emptyRoomQueryExpanded = false
            refreshVisibleEmptyRooms()
            return
        }

        emptyRoomsLoading = true
        emptyRoomLoadError = null
        refreshVisibleEmptyRooms()
        if (account == "114514") {
            val result = sampleEmptyRoomResult(campus, week, weekday, section)
            collapseEmptyRoomResultGroups(result)
            emptyRoomResult = result
            emptyRoomsLoading = false
            emptyRoomQueryExpanded = false
            refreshVisibleEmptyRooms()
            return
        }

        networkExecutor.execute {
            try {
                val result = SdauCourseRepository().queryEmptyRooms(
                    account, password, campus, week, weekday, section
                )
                runOnUiThread {
                    if (generation != emptyRoomRequestGeneration) return@runOnUiThread
                    collapseEmptyRoomResultGroups(result)
                    emptyRoomResult = result
                    emptyRoomsLoading = false
                    emptyRoomLoadError = null
                    emptyRoomQueryExpanded = false
                    refreshVisibleEmptyRooms()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (generation != emptyRoomRequestGeneration) return@runOnUiThread
                    emptyRoomsLoading = false
                    emptyRoomLoadError = error.message?.replace(Regex("\\s+"), " ")?.take(180)
                        ?: "教务系统暂时无法查询空教室，请稍后重试。"
                    emptyRoomQueryExpanded = false
                    refreshVisibleEmptyRooms()
                }
            }
        }
    }

    private fun refreshVisibleEmptyRooms() {
        if (currentMainSection != 3) return
        val host = mainSectionHost ?: return
        replaceMainSection(host, buildEmptyRoomSection(), 3, 0f, 170L)
    }

    private fun sampleEmptyRoomResult(
        campus: String,
        week: Int,
        weekday: Int,
        sectionCode: String
    ): RemoteEmptyRoomResult {
        val rooms = when (campus) {
            "泮河校区" -> listOf(
                "N104", "W205", "E308", "S514", "19#201", "19#403",
                "线上教学", "南校区体育羽毛球馆", "南校实践环节地点化学实践S"
            )
            "西北片区" -> listOf("22#205", "22#302", "22#402")
            else -> listOf(
                "5N101", "5N202", "5N306", "5S111", "5S416",
                "文理大楼503", "北校12号楼310", "线上教学"
            )
        }
        return RemoteEmptyRoomResult(selectedTerm(), week, campus, weekday, sectionCode, rooms)
    }

    private fun showEmptyRoomFilterPicker(
        title: String,
        options: List<Pair<String, String>>,
        selected: String,
        onSelected: (String) -> Unit
    ) {
        if (emptyRoomFilterOverlay != null || emptyRoomFilterCapturePending || options.isEmpty()) return
        emptyRoomFilterCapturePending = true
        captureUpdateBackdrop { pageSnapshot ->
            emptyRoomFilterCapturePending = false
            if (
                isFinishing ||
                isDestroyed ||
                onLoginPage ||
                currentMainSection != 3 ||
                emptyRoomFilterOverlay != null
            ) {
                pageSnapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
                return@captureUpdateBackdrop
            }
            val dialog = LiquidPickerDialogView(
                context = this,
                pageSnapshot = pageSnapshot,
                title = title,
                options = options.map { (label, value) ->
                    LiquidPickerOption(
                        title = label,
                        selected = value == selected,
                        onClick = {
                            hideEmptyRoomFilterPicker()
                            onSelected(value)
                        }
                    )
                },
                highFrost = true,
                onDismiss = ::hideEmptyRoomFilterPicker
            )
            pageHost.addView(dialog, matchParentParams())
            emptyRoomFilterOverlay = dialog
            dialog.alpha = 0f
            // 该 View 包含全屏壁纸快照，缩放会让快照与真实壁纸错位并产生抖动。
            dialog.animate().alpha(1f).setDuration(180).start()
        }
    }

    private fun hideEmptyRoomFilterPicker() {
        val overlay = emptyRoomFilterOverlay ?: return
        overlay.animate().alpha(0f).setDuration(130).withEndAction {
            pageHost.removeView(overlay)
            overlay.releaseSnapshot()
            if (emptyRoomFilterOverlay === overlay) emptyRoomFilterOverlay = null
        }.start()
    }

    private fun emptyRoomWeekdayLabel(day: Int): String = when (day) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        else -> "星期日"
    }

    private fun emptyRoomSectionLabel(code: String): String = when (code) {
        "0102" -> "第一大节"
        "0304" -> "第二大节"
        "0506" -> "第三大节"
        "0708" -> "第四大节"
        "0910" -> "第五大节"
        else -> code
    }

    private fun defaultEmptyRoomSection(now: Calendar = Calendar.getInstance()): String {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val starts = currentStartMinutes()
        val codes = arrayOf("0102", "0304", "0506", "0708", "0910")
        codes.indices.forEach { sectionIndex ->
            val secondSlot = sectionIndex * 2 + 1
            val sectionEnd = starts[secondSlot] + 45
            if (minute <= sectionEnd) return codes[sectionIndex]
        }
        return "晚间"
    }

    private fun syncEmptyRoomDefaultsToNow() {
        val now = Calendar.getInstance()
        val termWeek = weekForTerm(selectedTerm())
        emptyRoomCampus = defaultEmptyRoomCampus()
        emptyRoomWeek = if (termWeek <= 0) 1 else termWeek
        emptyRoomWeekday = now.get(Calendar.DAY_OF_WEEK).let { day ->
            if (day == Calendar.SUNDAY) 7 else day - 1
        }
        emptyRoomSectionCode = defaultEmptyRoomSection(now)
    }

    private fun defaultEmptyRoomCampus(): String {
        val campusCounts = linkedMapOf(
            "泮河校区" to 0,
            "西北片区" to 0,
            "岱宗校区" to 0
        )
        activeScheduleCourses().forEach { course ->
            emptyRoomCampusForRoom(course.room)?.let { campus ->
                campusCounts[campus] = campusCounts.getValue(campus) + 1
            }
        }

        val highestCount = campusCounts.values.maxOrNull() ?: 0
        if (highestCount == 0) return "泮河校区"
        val mostLikelyCampuses = campusCounts.filterValues { it == highestCount }.keys
        return mostLikelyCampuses.singleOrNull() ?: "泮河校区"
    }

    private fun emptyRoomCampusForRoom(room: String): String? {
        val normalizedRoom = room
            .filterNot { it.isWhitespace() }
            .uppercase(Locale.ROOT)
        return when {
            normalizedRoom.startsWith("22#") -> "西北片区"
            normalizedRoom.startsWith("北校12号楼") ||
                normalizedRoom.startsWith("5N") ||
                normalizedRoom.startsWith("5S") ||
                normalizedRoom.startsWith("文理大楼") ||
                normalizedRoom.startsWith("北校文理大楼") -> "岱宗校区"
            normalizedRoom.startsWith("19#") ||
                normalizedRoom.startsWith("S") ||
                normalizedRoom.startsWith("N") ||
                normalizedRoom.startsWith("W") ||
                normalizedRoom.startsWith("E") -> "泮河校区"
            else -> null
        }
    }

    private fun buildScoreResultSection(result: RemoteScoreResult): View {
        return createScoreLiquidScrollPageView(
            context = this,
            result = result,
            scoreColors = result.records.map { scoreColor(it.score) },
            pageBackgroundBitmap = currentPageBackgroundBitmap,
            pageBackgroundScrim = customBackgroundScrimColor(),
            textPalette = scheduleTextPalette,
            termSelectorExpanded = scoreTermSelectorExpanded,
            onTermClick = ::requestScoreTermPicker,
            onScoreClick = ::showScoreDetail,
            onExport = { exportScoreImage(result.term) }
        )
    }

    private fun requestScoreTermPicker(bounds: android.graphics.Rect) {
        if (scoreTermOverlay != null || scoreTermMenuCapturePending) return
        scoreTermSelectorExpanded.value = true
        showScoreTermPicker(bounds)
    }

    private fun scoreColor(value: String): Int {
        val number = value.trim().toDoubleOrNull()
        return when {
            number != null && number < 60 -> activeThemeColors.error
            number != null && number >= 80 -> Color.rgb(41, 132, 91)
            number != null -> Color.rgb(177, 117, 28)
            value.contains("不及格") || value.contains("不合格") -> activeThemeColors.error
            else -> activeThemeColors.primary
        }
    }

    private fun exportScoreImage(term: String) {
        if (scoreExporting) return
        val result = loadScoreCache()?.takeIf { it.term == term && it.records.isNotEmpty() }
        if (result == null) {
            Toast.makeText(this, "暂无可导出的成绩", Toast.LENGTH_SHORT).show()
            return
        }

        scoreExporting = true
        showLiquidToast(
            message = "正在生成成绩图片…",
            visual = LiquidToastVisual.LOADING,
            durationMillis = 0L
        )
        networkExecutor.execute {
            var bitmap: Bitmap? = null
            try {
                bitmap = createScoreBitmap(result)
                saveScoreBitmap(bitmap, result.term)
                runOnUiThread {
                    showLiquidToast(
                        message = "成绩图片已保存到 Pictures/WeSDAU",
                        visual = LiquidToastVisual.SUCCESS,
                        durationMillis = 2_600L
                    )
                }
            } catch (error: Exception) {
                runOnUiThread {
                    dismissLiquidToast()
                    Toast.makeText(this, "保存成绩图片失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            } finally {
                bitmap?.recycle()
                scoreExporting = false
            }
        }
    }

    private fun createScoreBitmap(result: RemoteScoreResult): Bitmap {
        val width = 1600
        val padding = 44
        val headerHeight = 170
        val summaryHeight = 120
        val tableTitleHeight = 48
        val tableHeaderHeight = 56
        val rowHeight = 68
        val rows = result.records.ifEmpty {
            listOf(RemoteScore("-", "当前开课时间暂无成绩记录", "-", "-", "-"))
        }
        val height = padding * 2 + headerHeight + 18 + summaryHeight + 18 +
            tableTitleHeight + tableHeaderHeight + rows.size * rowHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Bitmap 默认是透明的，深色图片查看器会将透明区域显示为黑色，
        // 从而让深色标题和表格内容看起来像“丢失”。导出图使用固定浅色底，
        // 保证在浅色/深色系统主题和不同图片查看器中都保持一致。
        canvas.drawColor(Color.rgb(243, 249, 252))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        val contentWidth = width - padding * 2

        fun roundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, paint)
        }

        fun drawText(value: String, x: Float, baseline: Float, size: Float, color: Int, style: Int) {
            paint.style = Paint.Style.FILL
            paint.color = color
            paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", style)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(value, x, baseline, paint)
        }

        fun wrap(value: String, maxWidth: Float, size: Float, style: Int): List<String> {
            val safe = value.ifBlank { "-" }
            paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", style)
            val lines = mutableListOf<String>()
            var current = ""
            safe.forEach { character ->
                val next = current + character
                if (current.isNotEmpty() && paint.measureText(next) > maxWidth) {
                    lines += current
                    current = character.toString()
                } else {
                    current = next
                }
            }
            if (current.isNotEmpty()) lines += current
            return lines.ifEmpty { listOf("-") }
        }

        paint.shader = LinearGradient(
            padding.toFloat(), padding.toFloat(),
            (padding + contentWidth).toFloat(), (padding + headerHeight).toFloat(),
            Color.rgb(232, 248, 255), Color.rgb(245, 239, 255), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(padding.toFloat(), padding.toFloat(), (padding + contentWidth).toFloat(), (padding + headerHeight).toFloat()),
            24f, 24f, paint
        )
        paint.shader = null

        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty().ifBlank { "-" }
        val studentName = preferences.getString(KEY_STUDENT_NAME, "").orEmpty().trim()
        val displayName = if (studentName.isBlank() || account == "-") account else "$studentName-$account"
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.textSize = 24f
        paint.color = Color.rgb(79, 107, 121)
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        canvas.drawText("WeSDAU-成绩单", (padding + 24).toFloat(), (padding + 44).toFloat(), paint)
        drawText(displayName, (padding + 24).toFloat(), (padding + 98).toFloat(), 40f, Color.rgb(23, 51, 63), Typeface.BOLD)
        drawText("学期：${result.term.ifBlank { "-" }}", (padding + 24).toFloat(), (padding + 136).toFloat(), 24f, Color.rgb(87, 115, 130), Typeface.NORMAL)

        var y = padding + headerHeight + 18
        val summaryGap = 18
        val summaryWidth = (contentWidth - summaryGap * 2) / 3f
        roundedRect(padding.toFloat(), y.toFloat(), summaryWidth, summaryHeight.toFloat(), 18f, Color.rgb(255, 245, 248))
        drawText("平均成绩", (padding + 20).toFloat(), (y + 38).toFloat(), 22f, Color.rgb(108, 128, 144), Typeface.NORMAL)
        drawText(result.averageScore.ifBlank { "-" }, (padding + 20).toFloat(), (y + 92).toFloat(), 44f, Color.rgb(245, 108, 126), Typeface.BOLD)

        val gpaX = padding + summaryWidth + summaryGap
        roundedRect(gpaX, y.toFloat(), summaryWidth, summaryHeight.toFloat(), 18f, Color.rgb(245, 244, 255))
        drawText("平均学分绩点", gpaX + 20, (y + 38).toFloat(), 22f, Color.rgb(108, 128, 144), Typeface.NORMAL)
        drawText(result.averageCreditGpa.ifBlank { "-" }, gpaX + 20, (y + 92).toFloat(), 44f, Color.rgb(131, 140, 199), Typeface.BOLD)

        val metaX = gpaX + summaryWidth + summaryGap
        roundedRect(metaX, y.toFloat(), summaryWidth, summaryHeight.toFloat(), 18f, Color.rgb(247, 252, 255))
        drawText("课程统计", metaX + 20, (y + 38).toFloat(), 22f, Color.rgb(95, 119, 131), Typeface.NORMAL)
        val countText = "门数：${result.records.size}"
        drawText(countText, metaX + 20, (y + 88).toFloat(), 28f, Color.rgb(31, 61, 75), Typeface.BOLD)
        paint.textSize = 28f
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        val countWidth = paint.measureText(countText)
        drawText("总学分：${result.totalCredits.ifBlank { "-" }}", metaX + 20 + countWidth + 22, (y + 88).toFloat(), 20f, Color.rgb(31, 61, 75), Typeface.NORMAL)

        y += summaryHeight + 18
        drawText("课程成绩", (padding + 2).toFloat(), (y + 34).toFloat(), 30f, Color.rgb(36, 70, 86), Typeface.BOLD)
        y += tableTitleHeight

        val ratios = floatArrayOf(1.2f, 2.3f, .8f, .8f, .8f)
        val ratioSum = ratios.sum()
        val columnWidths = ratios.map { contentWidth * it / ratioSum }
        val headers = listOf("课程代码", "课程名", "学分", "总成绩", "绩点")
        roundedRect(padding.toFloat(), y.toFloat(), contentWidth.toFloat(), tableHeaderHeight.toFloat(), 14f, Color.rgb(234, 244, 250))
        var x = padding.toFloat()
        headers.forEachIndexed { index, header ->
            drawText(header, x + 14, (y + 35).toFloat(), 21f, Color.rgb(80, 105, 119), Typeface.BOLD)
            x += columnWidths[index]
        }
        y += tableHeaderHeight

        rows.forEachIndexed { index, record ->
            val rowY = y + index * rowHeight
            roundedRect(padding.toFloat(), (rowY + 3).toFloat(), contentWidth.toFloat(), (rowHeight - 6).toFloat(), 12f, if (index % 2 == 0) Color.WHITE else Color.rgb(248, 252, 255))
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = Color.rgb(230, 239, 244)
            canvas.drawLine(padding.toFloat(), (rowY + rowHeight).toFloat(), (padding + contentWidth).toFloat(), (rowY + rowHeight).toFloat(), paint)

            val values = listOf(record.courseCode, record.courseName, record.credit, record.score, record.gpa)
            var cellX = padding.toFloat()
            values.forEachIndexed { column, rawValue ->
                val value = rawValue.ifBlank { "-" }
                val columnWidth = columnWidths[column]
                if (column == 1) {
                    val lines = wrap(value, columnWidth - 28, 22f, Typeface.BOLD).take(2)
                    val startY = rowY + if (lines.size == 2) 27 else 42
                    lines.forEachIndexed { lineIndex, line ->
                        drawText(line, cellX + 14, (startY + lineIndex * 24).toFloat(), 22f, Color.rgb(25, 55, 68), Typeface.BOLD)
                    }
                } else {
                    val color = if (column == 3) scoreColor(value) else Color.rgb(31, 61, 75)
                    val size = if (column == 0) 20f else 22f
                    val line = wrap(value, columnWidth - 28, size, Typeface.BOLD).first()
                    drawText(line, cellX + 14, (rowY + 42).toFloat(), size, color, Typeface.BOLD)
                }
                cellX += columnWidth
            }
        }
        return bitmap
    }

    private fun saveScoreBitmap(bitmap: Bitmap, term: String) {
        val safeTerm = term.replace(Regex("[^\\w\\u4e00-\\u9fa5-]"), "_")
        val displayName = "课程成绩-$safeTerm.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/WeSDAU")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建图片文件")
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片编码失败" }
                } ?: error("无法写入图片文件")
                contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            } catch (error: Exception) {
                contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.apply { mkdirs() }
                ?: error("无法访问图片目录")
            File(directory, displayName).outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片编码失败" }
            }
        }
    }

    private fun showScoreDetail(record: RemoteScore) {
        if (scoreDetailOverlay != null || scoreDetailCapturePending) return
        scoreDetailCapturePending = true
        captureUpdateBackdrop { pageSnapshot ->
            scoreDetailCapturePending = false
            if (
                isFinishing ||
                isDestroyed ||
                onLoginPage ||
                currentMainSection != 2 ||
                scoreDetailOverlay != null
            ) {
                pageSnapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
                return@captureUpdateBackdrop
            }

            val dialog = LiquidScoreDetailDialogView(
                context = this,
                pageSnapshot = pageSnapshot,
                courseName = record.courseName,
                onDismiss = ::hideScoreDetail
            )
            pageHost.addView(dialog, matchParentParams())
            scoreDetailOverlay = dialog
            dialog.alpha = 0f
            // 弹层包含一张全屏页面快照；缩放整个 View 会连同自定义壁纸一起缩放，
            // 在真实页面与快照切换时产生明显抖动，因此这里只做淡入。
            dialog.animate().alpha(1f).setDuration(190).start()

            val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
            val password = preferences.getString(KEY_PASSWORD, "").orEmpty()
            networkExecutor.execute {
                val result = if (account == "114514") {
                    Result.success(sampleScoreDetail(record))
                } else {
                    runCatching { SdauCourseRepository().queryScoreDetail(account, password, record) }
                }
                runOnUiThread {
                    if (scoreDetailOverlay !== dialog) return@runOnUiThread
                    result.onSuccess { detail ->
                        dialog.showDetail(detail, scoreColor(detail.totalScore))
                    }.onFailure { error ->
                        dialog.showError(
                            error.message
                                ?.replace(Regex("\\s+"), " ")
                                ?.take(150)
                                ?: "成绩构成查询失败"
                        )
                    }
                }
            }
        }
    }

    private fun hideScoreDetail() {
        val overlay = scoreDetailOverlay ?: return
        // 保持全屏快照与底层页面像素对齐，关闭时同样只做淡出。
        overlay.animate().alpha(0f).setDuration(140).withEndAction {
            pageHost.removeView(overlay)
            overlay.releaseSnapshot()
            if (scoreDetailOverlay === overlay) scoreDetailOverlay = null
        }.start()
    }


    private fun addGradeHeader(body: LinearLayout, term: String) {
        val header = horizontalLayout().apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("成绩", 28f, scheduleTextPalette.primary, Typeface.BOLD).apply {
            applyScheduleTextHalo()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val selector = createScoreTermSelectorView(
            context = this,
            term = term,
            pageBackgroundBitmap = currentPageBackgroundBitmap,
            pageBackgroundScrim = customBackgroundScrimColor(),
            textPalette = scheduleTextPalette,
            termSelectorExpanded = scoreTermSelectorExpanded,
            onTermClick = ::requestScoreTermPicker
        )
        header.addView(selector, LinearLayout.LayoutParams(-2, -2))
        body.addView(header, spacedParams(dp(18)))
    }

    private fun buildGradeStateSection(
        term: String,
        hasLoadedResult: Boolean,
        error: String?,
        emptyTitle: String = "暂无成绩信息",
        emptyDescription: String = "本学期暂未发布课程成绩\n成绩公布后会自动显示在这里"
    ): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
        }
        val body = verticalLayout().apply { setPadding(dp(20), dp(18), dp(20), dp(28)) }
        addGradeHeader(body, term)
        val state = when {
            error != null -> verticalLayout().apply {
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(20), dp(22), dp(20))
                addView(text("!", 22f, activeThemeColors.error, Typeface.BOLD).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(52), dp(44)).apply { bottomMargin = dp(10) })
                addView(text(error, 14f, scheduleTextPalette.secondary, secondaryTextTypeface()).apply {
                    gravity = Gravity.CENTER
                    setLineSpacing(dp(4).toFloat(), 1f)
                    applyScheduleTextHalo()
                }, matchWrapParams())
            }
            hasLoadedResult -> buildAcademicEmptyState(
                EmptyAcademicState.GRADES,
                emptyTitle,
                emptyDescription
            )
            else -> verticalLayout().apply {
                gravity = Gravity.CENTER
                addView(ProgressBar(this@MainActivity).apply {
                    isIndeterminate = true
                    indeterminateTintList = ColorStateList.valueOf(activeThemeColors.accent)
                    contentDescription = "加载成绩"
                }, LinearLayout.LayoutParams(dp(38), dp(38)))
            }
        }
        body.addView(state, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll.addView(body, FrameLayout.LayoutParams(-1, -1))
        return scroll
    }

    private fun showScoreTermPicker(anchor: View) {
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        showScoreTermPicker(
            Rect(
                location[0],
                location[1],
                location[0] + anchor.width,
                location[1] + anchor.height
            )
        )
    }

    private fun showScoreTermPicker(anchorBoundsInWindow: Rect) {
        if (scoreTermOverlay != null || scoreTermMenuCapturePending) return
        val terms = scoreTermOptions()
        val hostLocation = IntArray(2)
        pageHost.getLocationInWindow(hostLocation)
        val menuWidth = dp(176)
        val horizontalMargin = dp(12)
        val anchorLeft = anchorBoundsInWindow.left - hostLocation[0]
        val anchorTop = anchorBoundsInWindow.top - hostLocation[1]
        val anchorRight = anchorBoundsInWindow.right - hostLocation[0]
        val anchorBottom = anchorBoundsInWindow.bottom - hostLocation[1]
        val menuX = (anchorRight - menuWidth).coerceIn(
            horizontalMargin,
            (pageHost.width - menuWidth - horizontalMargin).coerceAtLeast(horizontalMargin)
        )
        val desiredHeight = dp(terms.size * 44 + 12).coerceAtMost(dp(364))
        val belowY = anchorBottom + dp(4)
        val belowSpace = pageHost.height - belowY - dp(12)
        val useBelow = belowSpace >= minOf(desiredHeight, dp(154))
        val maxMenuHeight: Int
        val menuY: Int
        if (useBelow) {
            menuY = belowY.coerceAtLeast(dp(12))
            maxMenuHeight = minOf(desiredHeight, belowSpace).coerceAtLeast(dp(88))
        } else {
            maxMenuHeight = minOf(desiredHeight, anchorTop - dp(16)).coerceAtLeast(dp(88))
            menuY = (anchorTop - dp(4) - maxMenuHeight).coerceAtLeast(dp(12))
        }

        scoreTermMenuCapturePending = true
        captureUpdateBackdrop { pageSnapshot ->
            scoreTermMenuCapturePending = false
            if (isFinishing || isDestroyed || scoreTermOverlay != null) {
                scoreTermSelectorExpanded.value = false
                pageSnapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
                return@captureUpdateBackdrop
            }
            val menu = LiquidScoreTermDropdownView(
                context = this,
                pageSnapshot = pageSnapshot,
                menuX = menuX,
                menuY = menuY,
                menuWidth = menuWidth,
                maxMenuHeight = maxMenuHeight,
                expandDownward = useBelow,
                terms = terms,
                selectedTerm = selectedScoreTerm(),
                onTermSelected = { term ->
                    hideScoreTermPicker {
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .putString(KEY_SCORE_TERM, term)
                            .apply()
                        scoreLoadError = null
                        refreshVisibleGrades()
                        refreshScores()
                    }
                },
                onDismiss = { hideScoreTermPicker() }
            )
            pageHost.addView(menu, matchParentParams())
            scoreTermOverlay = menu
        }
    }

    private fun hideScoreTermPicker(afterDismiss: (() -> Unit)? = null) {
        scoreTermSelectorExpanded.value = false
        val overlay = scoreTermOverlay
        if (overlay == null) {
            afterDismiss?.invoke()
            return
        }
        overlay.collapse {
            if (scoreTermOverlay === overlay) scoreTermOverlay = null
            pageHost.removeView(overlay)
            overlay.releaseSnapshot()
            afterDismiss?.invoke()
        }
    }

    private fun buildScheduleHeader(): View {
        return ScheduleHeaderComposeView(
            context = this,
            initialDate = if (viewingPublicSchedule) publicScheduleClassName else todayLabel(),
            initialWeek = formatWeekLabel(currentWeek),
            initialPalette = scheduleTextPalette,
            showRefresh = !viewingPublicSchedule,
            onLogout = { showLoginPage(true) },
            onRefresh = { showRefreshScheduleConfirmation() },
            onMore = ::showUpdateMenu
        ).also { scheduleHeader = it }
    }

    private fun showRefreshScheduleConfirmation() {
        if (
            scheduleRefreshRunning || viewingPublicSchedule ||
            refreshScheduleConfirmOverlay != null || refreshScheduleConfirmCapturePending
        ) return
        refreshScheduleConfirmCapturePending = true
        captureUpdateBackdrop { pageSnapshot ->
            refreshScheduleConfirmCapturePending = false
            if (
                isFinishing || isDestroyed || viewingPublicSchedule || scheduleRefreshRunning ||
                refreshScheduleConfirmOverlay != null
            ) {
                pageSnapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
                return@captureUpdateBackdrop
            }
            val dialog = LiquidConfirmDialogView(
                context = this,
                pageSnapshot = pageSnapshot,
                title = "更新课表",
                message = "是否从教务系统重新获取并更新当前学期课表？",
                cancelLabel = "取消",
                confirmLabel = "确认更新",
                onDismiss = { hideRefreshScheduleConfirmation() },
                onConfirm = {
                    hideRefreshScheduleConfirmation {
                        refreshPersonalSchedule()
                    }
                }
            )
            pageHost.addView(dialog, matchParentParams())
            refreshScheduleConfirmOverlay = dialog
            dialog.alpha = 0f
            dialog.animate().alpha(1f).setDuration(180).start()
        }
    }

    private fun hideRefreshScheduleConfirmation(afterDismiss: (() -> Unit)? = null) {
        val overlay = refreshScheduleConfirmOverlay
        if (overlay == null) {
            afterDismiss?.invoke()
            return
        }
        overlay.animate().alpha(0f).setDuration(140).withEndAction {
            pageHost.removeView(overlay)
            overlay.releaseSnapshot()
            if (refreshScheduleConfirmOverlay === overlay) {
                refreshScheduleConfirmOverlay = null
            }
            afterDismiss?.invoke()
        }.start()
    }

    private fun refreshPersonalSchedule() {
        if (scheduleRefreshRunning || viewingPublicSchedule) return
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val password = preferences.getString(KEY_PASSWORD, "").orEmpty()
        val term = preferences.getString(KEY_TERM, "").orEmpty()
        if (account.isBlank() || password.isBlank() || term.isBlank()) {
            showLiquidToast(
                message = "登录信息不完整，请重新登录后再刷新课表",
                visual = LiquidToastVisual.ERROR,
                durationMillis = 2_800L
            )
            return
        }
        if (account == "114514") {
            val refreshedCourses = recolorCourses(
                sampleCourses() + loadCustomCourseCache(),
                term = term,
                refreshMapping = true
            )
            saveCourseCache(refreshedCourses, account, term)
            if (refreshedCourses.any(Course::isCustom)) {
                saveCustomCourseCache(refreshedCourses)
            }
            scheduleGrid?.setCourses(refreshedCourses)
            showLiquidToast("课表已更新", LiquidToastVisual.SUCCESS, 1_800L)
            return
        }

        val requestGeneration = ++scheduleRefreshGeneration
        val sessionGeneration = academicSessionGeneration
        scheduleRefreshRunning = true
        scheduleHeader?.setRefreshRunning(true)
        showLiquidToast("正在从教务系统更新课表…", LiquidToastVisual.LOADING, 0L)
        networkExecutor.execute {
            try {
                val coursesFromSystem = SdauCourseRepository()
                    .queryCourses(account, password, term)
                    .map { remote ->
                        Course(
                            remote.day,
                            remote.startSlot,
                            remote.slotCount,
                            remote.name,
                            remote.room,
                            remote.teacher,
                            COURSE_COLORS.first(),
                            Color.WHITE,
                            remote.weeks
                        )
                    }
                runOnUiThread {
                    if (
                        requestGeneration != scheduleRefreshGeneration ||
                        sessionGeneration != academicSessionGeneration ||
                        !isActiveAcademicSession(account, term)
                    ) return@runOnUiThread
                    val refreshedCourses = recolorCourses(
                        coursesFromSystem + loadCustomCourseCache(),
                        term = term,
                        refreshMapping = true
                    )
                    saveCourseCache(refreshedCourses, account, term)
                    if (refreshedCourses.any(Course::isCustom)) {
                        saveCustomCourseCache(refreshedCourses)
                    }
                    scheduleGrid?.setCourses(refreshedCourses)
                    showLiquidToast("课表已更新", LiquidToastVisual.SUCCESS, 2_000L)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (
                        requestGeneration != scheduleRefreshGeneration ||
                        sessionGeneration != academicSessionGeneration ||
                        !isActiveAcademicSession(account, term)
                    ) return@runOnUiThread
                    showLiquidToast(
                        message = "课表更新失败：${error.message ?: "未知错误"}",
                        visual = LiquidToastVisual.ERROR,
                        durationMillis = 3_000L
                    )
                }
            } finally {
                runOnUiThread {
                    if (requestGeneration != scheduleRefreshGeneration) return@runOnUiThread
                    scheduleRefreshRunning = false
                    scheduleHeader?.setRefreshRunning(false)
                }
            }
        }
    }

    private fun showUpdateMenu(anchorBoundsInWindow: Rect) {
        if (actionMenuOverlay != null || actionMenuCapturePending) return
        val hostLocation = IntArray(2)
        pageHost.getLocationInWindow(hostLocation)
        val menuWidth = dp(204)
        val menuX = (anchorBoundsInWindow.right - hostLocation[0] - menuWidth)
            .coerceIn(dp(12), (pageHost.width - menuWidth - dp(12)).coerceAtLeast(dp(12)))
        val menuY = (anchorBoundsInWindow.bottom - hostLocation[1] + dp(4)).coerceAtLeast(dp(12))

        actionMenuCapturePending = true
        captureUpdateBackdrop { pageSnapshot ->
            actionMenuCapturePending = false
            if (isFinishing || isDestroyed || actionMenuOverlay != null) {
                pageSnapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
                return@captureUpdateBackdrop
            }
            lateinit var menu: LiquidActionMenuView
            val customBackgroundAvailable = hasCustomBackground()
            val actions = buildList {
                add(
                    LiquidMenuAction(
                        title = "检查更新",
                        iconRes = R.drawable.ic_update_lightning,
                        isUpdateAction = true,
                        dividerAfter = true,
                        onClick = {
                            menu.setUpdateStatus("正在检查更新…", true)
                            networkExecutor.execute {
                                val update = runCatching { readRemoteUpdate() }.getOrNull()
                                runOnUiThread {
                                    when {
                                        update == null -> menu.setUpdateStatus("检查失败，请稍后重试", false)
                                        update.code <= currentVersionCode ->
                                            menu.setUpdateStatus("已是最新版本 $appDisplayVersion", false)
                                        else -> hideActionMenu { showUpdateDialog(update) }
                                    }
                                }
                            }
                        }
                    )
                )
                if (!viewingPublicSchedule) {
                    add(
                        LiquidMenuAction(
                            title = if (pushEnabled) "关闭课程通知" else "开启课程通知",
                            iconRes = if (pushEnabled) R.drawable.ic_push_on else R.drawable.ic_push_off,
                            isPushAction = true,
                            onClick = {
                                togglePushNotifications()
                                menu.setPushState(pushEnabled)
                            }
                        )
                    )
                }
                add(
                    LiquidMenuAction(
                        title = "分享",
                        iconRes = R.drawable.ic_share,
                        onClick = { hideActionMenu { showSharePicker() } }
                    )
                )
                add(
                    LiquidMenuAction(
                        title = "外观",
                        iconRes = R.drawable.ic_menu_appearance,
                        onClick = { hideActionMenu { showAppearanceDialog() } }
                    )
                )
                add(
                    LiquidMenuAction(
                        title = "设置背景",
                        iconRes = R.drawable.ic_menu_background,
                        hasSubmenu = true,
                        onClick = { menu.showBackgroundActions() }
                    )
                )
            }
            val backgroundActions = listOf(
                LiquidMenuAction(
                    title = "背景设置",
                    iconRes = R.drawable.ic_expand_chevron,
                    isBackAction = true,
                    dividerAfter = true,
                    onClick = { menu.showRootActions() }
                ),
                LiquidMenuAction(
                    title = "选择背景图片",
                    iconRes = R.drawable.ic_menu_background,
                    onClick = {
                        hideActionMenu { backgroundPicker.launch(arrayOf("image/*")) }
                    }
                ),
                LiquidMenuAction(
                    title = "调整背景",
                    iconRes = R.drawable.ic_menu_background_adjust,
                    enabled = customBackgroundAvailable,
                    onClick = { hideActionMenu { showExistingBackgroundEditor() } }
                ),
                LiquidMenuAction(
                    title = "恢复默认背景",
                    iconRes = R.drawable.ic_menu_restore,
                    enabled = customBackgroundAvailable,
                    onClick = { hideActionMenu { clearCustomBackground() } }
                )
            )
            menu = LiquidActionMenuView(
                context = this,
                pageSnapshot = pageSnapshot,
                menuX = menuX,
                menuY = menuY,
                actions = actions,
                backgroundActions = backgroundActions,
                hasCustomBackground = customBackgroundAvailable,
                onDismiss = { hideActionMenu() }
            )
            pageHost.addView(menu, matchParentParams())
            actionMenuOverlay = menu
        }
    }

    private fun hideActionMenu(afterDismiss: (() -> Unit)? = null) {
        val overlay = actionMenuOverlay
        if (overlay == null) {
            afterDismiss?.invoke()
            return
        }
        overlay.collapse {
            if (actionMenuOverlay === overlay) actionMenuOverlay = null
            pageHost.removeView(overlay)
            overlay.releaseSnapshot()
            afterDismiss?.invoke()
        }
    }

    private fun showAppearanceDialog() {
        if (appearanceOverlay != null || appearanceCapturePending) return
        appearanceCapturePending = true
        captureUpdateBackdrop { pageSnapshot ->
            appearanceCapturePending = false
            if (isFinishing || isDestroyed || appearanceOverlay != null) {
                pageSnapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
                return@captureUpdateBackdrop
            }
            val dialog = LiquidAppearanceDialogView(
                context = this,
                pageSnapshot = pageSnapshot,
                initialMode = CampusThemeController.mode,
                initialSystemDark = CampusThemeController.isSystemDark(this),
                onApply = { mode ->
                    hideAppearanceDialog {
                        applyThemeMode(mode)
                    }
                },
                onDismiss = { hideAppearanceDialog() }
            )
            pageHost.addView(dialog, matchParentParams())
            appearanceOverlay = dialog
            applyAppearanceDialogStatusBarScrim()
            dialog.alpha = 0f
            dialog.animate().alpha(1f).setDuration(180).start()
        }
    }

    private fun hideAppearanceDialog(afterDismiss: (() -> Unit)? = null) {
        val overlay = appearanceOverlay
        if (overlay == null) {
            afterDismiss?.invoke()
            return
        }
        overlay.animate().alpha(0f).setDuration(130).withEndAction {
            pageHost.removeView(overlay)
            overlay.releaseSnapshot()
            if (appearanceOverlay === overlay) appearanceOverlay = null
            restorePageSystemBars()
            afterDismiss?.invoke()
        }.start()
    }

    private fun applyThemeMode(mode: CampusThemeMode) {
        if (CampusThemeController.mode == mode) return
        CampusThemeController.setMode(this, mode)
        rebuildCurrentPageForTheme()
    }

    private fun rebuildCurrentPageForTheme() {
        pageHost.setBackgroundColor(activeThemeColors.pageBackground)

        if (onLoginPage) {
            setSystemBars(activeThemeColors.pageBackground)
            swapPage(buildLoginPage(), forward = false, animate = false)
            return
        }

        setSystemBars(Color.TRANSPARENT)
        window.navigationBarColor = activeThemeColors.gradient.last()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (viewingPublicSchedule && publicScheduleCourses.isNotEmpty()) {
            publicScheduleCourses = recolorCourses(
                publicScheduleCourses,
                term = publicScheduleTerm,
                persistMapping = false
            )
        }
        val themedPage = buildSchedulePage()
        applyScheduleStatusBarAppearance()
        swapPage(themedPage, forward = true, animate = false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (CampusThemeController.mode == CampusThemeMode.SYSTEM) {
            rebuildCurrentPageForTheme()
        }
    }

    private fun customBackgroundFile(): File = File(filesDir, CUSTOM_BACKGROUND_FILE_NAME)
    private fun customBackgroundSourceFile(): File = File(filesDir, CUSTOM_BACKGROUND_SOURCE_FILE_NAME)
    private fun customBackgroundPendingSourceFile(): File =
        File(filesDir, "$CUSTOM_BACKGROUND_SOURCE_FILE_NAME.tmp")

    private fun hasCustomBackground(): Boolean {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_CUSTOM_BACKGROUND, false) && customBackgroundFile().isFile
    }

    private fun customBackgroundScrimColor(): Int {
        val clarity = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getFloat(KEY_CUSTOM_BACKGROUND_CLARITY, DEFAULT_BACKGROUND_CLARITY)
            .coerceIn(MIN_BACKGROUND_CLARITY, 1f)
        val alpha = ((1f - clarity) * 255f).roundToInt()
        return if (activeThemeColors.isDark) {
            Color.argb(alpha, 12, 12, 13)
        } else {
            Color.argb(alpha, 238, 241, 248)
        }
    }

    private fun resolveScheduleTextPalette(
        background: Bitmap?,
        scrimColor: Int,
        crop: BackgroundCropSpec? = null,
        previousUsesDarkForeground: Boolean? = null
    ): ScheduleTextPalette {
        if (background == null || background.isRecycled) {
            return ScheduleTextPalette(
                primary = activeThemeColors.primaryText,
                secondary = activeThemeColors.secondaryText,
                halo = Color.TRANSPARENT,
                adaptive = false,
                usesDarkForeground = !activeThemeColors.isDark
            )
        }

        val darkPrimary = Color.rgb(24, 25, 28)
        val darkSecondary = Color.rgb(47, 49, 54)
        val lightPrimary = Color.rgb(248, 248, 249)
        val lightSecondary = Color.rgb(230, 230, 234)
        var darkContrastTotal = 0.0
        var lightContrastTotal = 0.0
        val columns = 24
        val rows = 36
        val darkContrastSamples = DoubleArray(columns * rows)
        val lightContrastSamples = DoubleArray(columns * rows)
        var sampleIndex = 0
        val cropLeft = crop?.left?.coerceIn(0f, 1f) ?: 0f
        val cropTop = crop?.top?.coerceIn(0f, 1f) ?: 0f
        val cropRight = crop?.right?.coerceIn(cropLeft + 0.0001f, 1f) ?: 1f
        val cropBottom = crop?.bottom?.coerceIn(cropTop + 0.0001f, 1f) ?: 1f
        for (row in 0 until rows) {
            val yRatio = cropTop + (row + 0.5f) / rows * (cropBottom - cropTop)
            val y = (yRatio * background.height)
                .roundToInt().coerceIn(0, background.height - 1)
            val gradientColors = activeThemeColors.gradient
            val gradientColor = gradientColors[
                (row * gradientColors.size / rows).coerceIn(gradientColors.indices)
            ]
            for (column in 0 until columns) {
                val xRatio = cropLeft + (column + 0.5f) / columns * (cropRight - cropLeft)
                val x = (xRatio * background.width)
                    .roundToInt().coerceIn(0, background.width - 1)
                val wallpaperColor = ColorUtils.compositeColors(
                    background.getPixel(x, y),
                    gradientColor
                )
                val finalColor = ColorUtils.compositeColors(scrimColor, wallpaperColor)
                val darkContrast =
                    ColorUtils.calculateContrast(darkPrimary, finalColor) * 0.62 +
                        ColorUtils.calculateContrast(darkSecondary, finalColor) * 0.38
                val lightContrast =
                    ColorUtils.calculateContrast(lightPrimary, finalColor) * 0.62 +
                        ColorUtils.calculateContrast(lightSecondary, finalColor) * 0.38
                darkContrastSamples[sampleIndex] = darkContrast
                lightContrastSamples[sampleIndex] = lightContrast
                darkContrastTotal += darkContrast
                lightContrastTotal += lightContrast
                sampleIndex++
            }
        }
        darkContrastSamples.sort()
        lightContrastSamples.sort()
        val lowContrastIndex = ((sampleIndex - 1) * 0.20f).roundToInt().coerceAtLeast(0)
        val darkAverage = darkContrastTotal / sampleIndex.coerceAtLeast(1)
        val lightAverage = lightContrastTotal / sampleIndex.coerceAtLeast(1)
        val darkScore = darkContrastSamples[lowContrastIndex] * 0.64 + darkAverage * 0.36
        // 深色模式的自定义壁纸优先使用浅色前景；只有壁纸整体足够明亮、
        // 深色文字的低分位对比度明显更好时才切回深色前景。
        val lightScore = lightContrastSamples[lowContrastIndex] * 0.64 +
            lightAverage * 0.36 + if (activeThemeColors.isDark) 0.32 else 0.0
        val contrastDelta = (darkScore - lightScore) /
            (kotlin.math.abs(darkScore) + kotlin.math.abs(lightScore)).coerceAtLeast(1.0)
        val useDarkForeground = when (previousUsesDarkForeground) {
            true -> contrastDelta >= -0.025
            false -> contrastDelta > 0.025
            null -> contrastDelta >= 0.0
        }
        return if (useDarkForeground) {
            ScheduleTextPalette(
                primary = darkPrimary,
                secondary = darkSecondary,
                halo = Color.argb(184, 255, 255, 255),
                adaptive = true,
                usesDarkForeground = true
            )
        } else {
            ScheduleTextPalette(
                primary = lightPrimary,
                secondary = lightSecondary,
                halo = Color.argb(172, 0, 0, 0),
                adaptive = true,
                usesDarkForeground = false
            )
        }
    }

    private fun enqueueSchedulePalettePreview(
        bitmap: Bitmap,
        crop: BackgroundCropSpec,
        scrimColor: Int
    ) {
        pendingSchedulePalettePreview = SchedulePalettePreview(bitmap, crop, scrimColor)
        if (schedulePalettePreviewFramePosted) return
        val page = schedulePageRoot ?: return
        schedulePalettePreviewFramePosted = true
        page.postOnAnimation {
            schedulePalettePreviewFramePosted = false
            val preview = pendingSchedulePalettePreview
            pendingSchedulePalettePreview = null
            if (
                preview == null ||
                backgroundEditorOverlay == null ||
                preview.bitmap.isRecycled
            ) return@postOnAnimation
            val previousForeground = scheduleTextPalette
                .takeIf(ScheduleTextPalette::adaptive)
                ?.usesDarkForeground
            val palette = resolveScheduleTextPalette(
                background = preview.bitmap,
                scrimColor = preview.scrimColor,
                crop = preview.crop,
                previousUsesDarkForeground = previousForeground
            )
            applyScheduleTextPalette(palette)
        }
    }

    private fun applyScheduleTextPalette(palette: ScheduleTextPalette) {
        if (palette == scheduleTextPalette) return
        scheduleTextPalette = palette
        scheduleHeader?.updatePalette(palette)
        scheduleVersion?.updatePalette(palette)
        scheduleGrid?.refreshTextPalette()
        applyScheduleStatusBarAppearance()
    }

    private fun clearLiveScheduleBackgroundPreview() {
        liveScheduleBackgroundBitmap = null
        liveScheduleBackgroundCrop = null
        liveScheduleBackgroundScrimColor = null
        pendingSchedulePalettePreview = null
    }

    private fun TextView.applyScheduleTextHalo() {
        if (scheduleTextPalette.adaptive) {
            setShadowLayer(dp(1.05f).toFloat(), 0f, 0f, scheduleTextPalette.halo)
        } else {
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
    }

    private fun refreshScheduleSystemBars() {
        setSystemBars(Color.TRANSPARENT)
        window.navigationBarColor = activeThemeColors.gradient.last()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        schedulePageRoot?.let { page ->
            page.post { ViewCompat.requestApplyInsets(page) }
        }
        applyScheduleStatusBarAppearance()
    }

    private fun applyAppearanceDialogStatusBarScrim() {
        window.statusBarColor = Color.TRANSPARENT
        applyScheduleStatusBarAppearance()
    }

    private fun restorePageSystemBars() {
        if (onLoginPage) {
            setSystemBars(activeThemeColors.pageBackground)
        } else {
            refreshScheduleSystemBars()
        }
    }

    private fun applyScheduleStatusBarAppearance() {
        var flags = window.decorView.systemUiVisibility
        flags = if (scheduleTextPalette.usesDarkForeground) {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = if (scheduleTextPalette.usesDarkForeground) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun prepareCustomBackground(uri: Uri) {
        val temporaryFile = customBackgroundPendingSourceFile()
        try {
            temporaryFile.delete()
            var copiedBytes = 0L
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temporaryFile, false).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copiedBytes += count
                        if (copiedBytes > MAX_CUSTOM_BACKGROUND_BYTES) {
                            throw IllegalArgumentException("图片文件不能超过 30 MB")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IllegalArgumentException("无法读取所选图片")

            val sourceBitmap = loadBackgroundBitmap(temporaryFile)
            if (sourceBitmap == null) {
                throw IllegalArgumentException("所选文件不是受支持的图片")
            }
            showBackgroundEditor(
                sourceFile = temporaryFile,
                sourceBitmap = sourceBitmap,
                pendingSelection = true
            )
        } catch (error: Exception) {
            temporaryFile.delete()
            Toast.makeText(
                this,
                error.message?.takeIf { it.isNotBlank() } ?: "背景图片设置失败",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showExistingBackgroundEditor() {
        val source = customBackgroundSourceFile()
        if (!source.isFile) {
            val current = customBackgroundFile()
            if (!current.isFile) return
            runCatching { current.copyTo(source, overwrite = true) }.onFailure {
                Toast.makeText(this, "无法读取当前背景图片", Toast.LENGTH_LONG).show()
                return
            }
        }
        val bitmap = loadBackgroundBitmap(source)
        if (bitmap == null) {
            Toast.makeText(this, "无法读取当前背景图片", Toast.LENGTH_LONG).show()
            return
        }
        showBackgroundEditor(source, bitmap, pendingSelection = false)
    }

    private fun showBackgroundEditor(
        sourceFile: File,
        sourceBitmap: Bitmap,
        pendingSelection: Boolean
    ) {
        hideBackgroundEditor(deletePendingSource = true)
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val initialClarity = preferences.getFloat(
            KEY_CUSTOM_BACKGROUND_CLARITY,
            DEFAULT_BACKGROUND_CLARITY
        ).coerceIn(MIN_BACKGROUND_CLARITY, 1f)
        val initialCrop = if (pendingSelection) null else savedBackgroundCrop(preferences)
        lateinit var editor: LiquidBackgroundEditorView
        editor = LiquidBackgroundEditorView(
            context = this,
            sourceBitmap = sourceBitmap,
            initialClarity = initialClarity,
            initialCrop = initialCrop,
            onPreview = { crop, clarity ->
                updateLiveBackgroundPreview(sourceBitmap, crop, clarity)
            },
            onCancel = {
                hideBackgroundEditor(
                    deletePendingSource = pendingSelection,
                    restoreScheduleBackground = true
                )
            },
            onApply = { crop, clarity ->
                val selectedSource = sourceFile
                editor.beginApplySequence {
                    persistCustomBackground(selectedSource, pendingSelection, crop, clarity) { success ->
                        if (success) {
                            editor.setApplyToastState(WallpaperApplyToastState.SUCCESS)
                            editor.postDelayed({
                                if (backgroundEditorOverlay === editor) {
                                    hideBackgroundEditor(
                                        deletePendingSource = false,
                                        preserveLivePreview = true
                                    ) {
                                        showSchedulePage(animate = false)
                                    }
                                }
                            }, 850L)
                        } else if (backgroundEditorOverlay === editor) {
                            hideBackgroundEditor(
                                deletePendingSource = pendingSelection,
                                restoreScheduleBackground = true
                            )
                        } else {
                            showSchedulePage(animate = false)
                        }
                    }
                }
            }
        )
        backgroundEditorPendingSource = if (pendingSelection) sourceFile else null
        backgroundEditorOverlay = editor
        pageHost.addView(editor, matchParentParams())
        refreshScheduleSystemBars()
    }

    private fun updateLiveBackgroundPreview(
        sourceBitmap: Bitmap,
        crop: BackgroundCropSpec,
        clarity: Float
    ) {
        val page = schedulePageRoot ?: return
        val backgroundImage = schedulePageBackgroundImage ?: ImageView(this).apply {
            scaleType = ImageView.ScaleType.MATRIX
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            page.addView(this, 0, FrameLayout.LayoutParams(-1, -1))
            schedulePageBackgroundImage = this
        }
        val scrimView = schedulePageBackgroundScrim ?: View(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val imageIndex = page.indexOfChild(backgroundImage)
            page.addView(this, (imageIndex + 1).coerceAtLeast(1), FrameLayout.LayoutParams(-1, -1))
            schedulePageBackgroundScrim = this
        }
        if (backgroundEditorPreviewBitmap !== sourceBitmap) {
            backgroundEditorPreviewBitmap = sourceBitmap
            backgroundImage.scaleType = ImageView.ScaleType.MATRIX
            backgroundImage.setImageBitmap(sourceBitmap)
        }
        val scrimAlpha = ((1f - clarity.coerceIn(MIN_BACKGROUND_CLARITY, 1f)) * 255f)
            .roundToInt()
        val previewScrim = if (activeThemeColors.isDark) {
            Color.argb(scrimAlpha, 12, 12, 13)
        } else {
            Color.argb(scrimAlpha, 238, 241, 248)
        }
        scrimView.setBackgroundColor(previewScrim)
        liveScheduleBackgroundBitmap = sourceBitmap
        liveScheduleBackgroundCrop = crop
        liveScheduleBackgroundScrimColor = previewScrim
        enqueueSchedulePalettePreview(sourceBitmap, crop, previewScrim)
        bottomNavigation?.updatePageBackground(sourceBitmap, previewScrim, crop)

        fun updateMatrix() {
            val viewportWidth = backgroundImage.width.takeIf { it > 0 }
                ?: page.width.takeIf { it > 0 }
                ?: resources.displayMetrics.widthPixels
            val viewportHeight = backgroundImage.height.takeIf { it > 0 }
                ?: page.height.takeIf { it > 0 }
                ?: resources.displayMetrics.heightPixels
            val sourceWidth = sourceBitmap.width.coerceAtLeast(1).toFloat()
            val sourceHeight = sourceBitmap.height.coerceAtLeast(1).toFloat()
            val cropWidth = ((crop.right - crop.left) * sourceWidth).coerceAtLeast(1f)
            val cropHeight = ((crop.bottom - crop.top) * sourceHeight).coerceAtLeast(1f)
            val cropCenterX = (crop.left + crop.right) * sourceWidth / 2f
            val cropCenterY = (crop.top + crop.bottom) * sourceHeight / 2f
            val scale = maxOf(viewportWidth / cropWidth, viewportHeight / cropHeight)
            backgroundImage.imageMatrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    viewportWidth / 2f - cropCenterX * scale,
                    viewportHeight / 2f - cropCenterY * scale
                )
            }
        }
        if (backgroundImage.width > 0 && backgroundImage.height > 0) updateMatrix()
        else backgroundImage.post(::updateMatrix)
    }

    private fun hideBackgroundEditor(
        deletePendingSource: Boolean,
        restoreScheduleBackground: Boolean = false,
        preserveLivePreview: Boolean = false,
        afterDismiss: (() -> Unit)? = null
    ) {
        val editor = backgroundEditorOverlay
        val pendingSource = backgroundEditorPendingSource
        if (editor == null) {
            if (deletePendingSource) pendingSource?.delete()
            backgroundEditorPendingSource = null
            afterDismiss?.invoke()
            return
        }
        backgroundEditorOverlay = null
        backgroundEditorPendingSource = null
        editor.dismiss {
            if (restoreScheduleBackground) {
                restoreScheduleBackgroundAfterEditor()
            } else if (!preserveLivePreview) {
                schedulePageBackgroundImage?.setImageDrawable(null)
            }
            clearLiveScheduleBackgroundPreview()
            backgroundEditorPreviewBitmap = null
            pageHost.removeView(editor)
            if (deletePendingSource) pendingSource?.delete()
            refreshScheduleSystemBars()
            afterDismiss?.invoke()
            editor.releaseBitmap()
        }
    }

    private fun restoreScheduleBackgroundAfterEditor() {
        val page = schedulePageRoot ?: return
        clearLiveScheduleBackgroundPreview()
        val restoredBitmap = currentPageBackgroundBitmap
        if (restoredBitmap != null && !restoredBitmap.isRecycled) {
            val restoredScrim = customBackgroundScrimColor()
            val backgroundImage = schedulePageBackgroundImage ?: ImageView(this).apply {
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                page.addView(this, 0, FrameLayout.LayoutParams(-1, -1))
                schedulePageBackgroundImage = this
            }
            backgroundImage.scaleType = ImageView.ScaleType.CENTER_CROP
            backgroundImage.setImageBitmap(restoredBitmap)

            val scrimView = schedulePageBackgroundScrim ?: View(this).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                val imageIndex = page.indexOfChild(backgroundImage)
                page.addView(this, imageIndex + 1, FrameLayout.LayoutParams(-1, -1))
                schedulePageBackgroundScrim = this
            }
            scrimView.setBackgroundColor(restoredScrim)
            bottomNavigation?.updatePageBackground(
                restoredBitmap,
                restoredScrim
            )
            applyScheduleTextPalette(resolveScheduleTextPalette(restoredBitmap, restoredScrim))
        } else {
            schedulePageBackgroundImage?.let(page::removeView)
            schedulePageBackgroundScrim?.let(page::removeView)
            schedulePageBackgroundImage = null
            schedulePageBackgroundScrim = null
            bottomNavigation?.updatePageBackground(null, Color.TRANSPARENT)
            applyScheduleTextPalette(resolveScheduleTextPalette(null, Color.TRANSPARENT))
        }
    }

    private fun persistCustomBackground(
        sourceFile: File,
        pendingSelection: Boolean,
        crop: BackgroundCropSpec,
        clarity: Float,
        onComplete: (Boolean) -> Unit
    ) {
        backgroundExecutor.execute {
            val output = File(filesDir, "$CUSTOM_BACKGROUND_FILE_NAME.tmp")
            val destinationBackup = File(filesDir, "$CUSTOM_BACKGROUND_FILE_NAME.bak")
            val sourceBackup = File(filesDir, "$CUSTOM_BACKGROUND_SOURCE_FILE_NAME.bak")
            val result = runCatching {
                output.delete()
                destinationBackup.delete()
                sourceBackup.delete()
                writeProcessedBackground(sourceFile, output, crop)
                if (pendingSelection) {
                    val sourceDestination = customBackgroundSourceFile()
                    if (sourceDestination.exists() && !sourceDestination.renameTo(sourceBackup)) {
                        throw IllegalStateException("无法备份原始背景图片")
                    }
                    if (!sourceFile.renameTo(sourceDestination)) {
                        if (sourceBackup.isFile) sourceBackup.renameTo(sourceDestination)
                        throw IllegalStateException("无法保存原始背景图片")
                    }
                }
                val destination = customBackgroundFile()
                if (destination.exists() && !destination.renameTo(destinationBackup)) {
                    if (pendingSelection) {
                        customBackgroundSourceFile().delete()
                        if (sourceBackup.isFile) sourceBackup.renameTo(customBackgroundSourceFile())
                    }
                    throw IllegalStateException("无法备份原背景图片")
                }
                if (!output.renameTo(destination)) {
                    if (destinationBackup.isFile) destinationBackup.renameTo(destination)
                    if (pendingSelection) {
                        customBackgroundSourceFile().delete()
                        if (sourceBackup.isFile) sourceBackup.renameTo(customBackgroundSourceFile())
                    }
                    throw IllegalStateException("无法保存背景图片")
                }
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(KEY_CUSTOM_BACKGROUND, true)
                    .putFloat(
                        KEY_CUSTOM_BACKGROUND_CLARITY,
                        clarity.coerceIn(MIN_BACKGROUND_CLARITY, 1f)
                    )
                    .putFloat(KEY_CUSTOM_BACKGROUND_CROP_LEFT, crop.left)
                    .putFloat(KEY_CUSTOM_BACKGROUND_CROP_TOP, crop.top)
                    .putFloat(KEY_CUSTOM_BACKGROUND_CROP_RIGHT, crop.right)
                    .putFloat(KEY_CUSTOM_BACKGROUND_CROP_BOTTOM, crop.bottom)
                    .commit()
                destinationBackup.delete()
                sourceBackup.delete()
            }
            output.delete()
            destinationBackup.delete()
            sourceBackup.delete()
            if (pendingSelection && sourceFile.isFile) sourceFile.delete()
            runOnUiThread {
                result.onSuccess {
                    onComplete(true)
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        error.message?.takeIf(String::isNotBlank) ?: "背景图片设置失败",
                        Toast.LENGTH_LONG
                    ).show()
                    onComplete(false)
                }
            }
        }
    }

    private fun writeProcessedBackground(
        sourceFile: File,
        outputFile: File,
        crop: BackgroundCropSpec
    ) {
        val source = loadBackgroundBitmap(sourceFile)
            ?: throw IllegalArgumentException("无法读取所选图片")
        try {
            val left = (crop.left.coerceIn(0f, 1f) * source.width).roundToInt()
                .coerceIn(0, source.width - 1)
            val top = (crop.top.coerceIn(0f, 1f) * source.height).roundToInt()
                .coerceIn(0, source.height - 1)
            val right = (crop.right.coerceIn(0f, 1f) * source.width).roundToInt()
                .coerceIn(left + 1, source.width)
            val bottom = (crop.bottom.coerceIn(0f, 1f) * source.height).roundToInt()
                .coerceIn(top + 1, source.height)
            val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
            val output = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
            try {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                Canvas(output).drawBitmap(cropped, 0f, 0f, paint)
                FileOutputStream(outputFile, false).use { stream ->
                    if (!output.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw IllegalStateException("无法保存背景图片")
                    }
                }
            } finally {
                output.recycle()
                if (cropped !== source && !cropped.isRecycled) cropped.recycle()
            }
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    private fun savedBackgroundCrop(
        preferences: android.content.SharedPreferences
    ): BackgroundCropSpec? {
        if (!preferences.contains(KEY_CUSTOM_BACKGROUND_CROP_LEFT)) return null
        val crop = BackgroundCropSpec(
            preferences.getFloat(KEY_CUSTOM_BACKGROUND_CROP_LEFT, 0f),
            preferences.getFloat(KEY_CUSTOM_BACKGROUND_CROP_TOP, 0f),
            preferences.getFloat(KEY_CUSTOM_BACKGROUND_CROP_RIGHT, 1f),
            preferences.getFloat(KEY_CUSTOM_BACKGROUND_CROP_BOTTOM, 1f)
        )
        return crop.takeIf {
            it.left in 0f..1f && it.top in 0f..1f &&
                it.right in 0f..1f && it.bottom in 0f..1f &&
                it.right > it.left && it.bottom > it.top
        }
    }

    private fun clearCustomBackground() {
        customBackgroundFile().delete()
        customBackgroundSourceFile().delete()
        customBackgroundPendingSourceFile().delete()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .remove(KEY_CUSTOM_BACKGROUND)
            .remove(KEY_CUSTOM_BACKGROUND_CLARITY)
            .remove(KEY_CUSTOM_BACKGROUND_CROP_LEFT)
            .remove(KEY_CUSTOM_BACKGROUND_CROP_TOP)
            .remove(KEY_CUSTOM_BACKGROUND_CROP_RIGHT)
            .remove(KEY_CUSTOM_BACKGROUND_CROP_BOTTOM)
            .apply()
        Toast.makeText(this, "已恢复默认背景", Toast.LENGTH_SHORT).show()
        showSchedulePage()
    }

    private fun loadCustomBackgroundBitmap(): Bitmap? {
        if (!hasCustomBackground()) return null
        return loadBackgroundBitmap(customBackgroundFile())
    }

    private fun loadBackgroundBitmap(file: File): Bitmap? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val sourceWidth = info.size.width.coerceAtLeast(1)
                    val sourceHeight = info.size.height.coerceAtLeast(1)
                    val longestSide = maxOf(sourceWidth, sourceHeight)
                    if (longestSide > MAX_CUSTOM_BACKGROUND_DIMENSION) {
                        val scale = MAX_CUSTOM_BACKGROUND_DIMENSION.toFloat() / longestSide
                        decoder.setTargetSize(
                            (sourceWidth * scale).toInt().coerceAtLeast(1),
                            (sourceHeight * scale).toInt().coerceAtLeast(1)
                        )
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_CUSTOM_BACKGROUND_DIMENSION) {
                    sampleSize *= 2
                }
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize }
                )
            }
        }.getOrNull()

    private fun buildExportCoursePlacements(visibleCourses: List<Course>): List<CoursePlacement> {
        val result = mutableListOf<CoursePlacement>()

        fun overlaps(first: Course, second: Course): Boolean {
            if (first.day != second.day) return false
            val firstEnd = first.startSlot + first.slotCount
            val secondEnd = second.startSlot + second.slotCount
            return first.startSlot < secondEnd && second.startSlot < firstEnd
        }

        visibleCourses.filter { it.day in 0..6 && it.startSlot in 0..9 }
            .groupBy { it.day }
            .values
            .forEach { dayCourses ->
                val sorted = dayCourses.sortedWith(
                    compareBy<Course> { it.startSlot }
                        .thenByDescending { it.slotCount }
                )
                val component = mutableListOf<Course>()

                fun flushComponent() {
                    if (component.isEmpty()) return
                    val columnEnds = mutableListOf<Int>()
                    val assigned = mutableListOf<Pair<Course, Int>>()
                    component.forEach { course ->
                        val column = columnEnds.indexOfFirst { end -> end <= course.startSlot }
                            .let { if (it >= 0) it else columnEnds.size }
                        if (column == columnEnds.size) columnEnds += 0
                        columnEnds[column] = course.startSlot + course.slotCount
                        assigned += course to column
                    }
                    val columnCount = columnEnds.size
                    assigned.forEach { (course, column) ->
                        result += CoursePlacement(course, column, columnCount)
                    }
                    component.clear()
                }

                sorted.forEach { course ->
                    if (component.isNotEmpty() && component.none { overlaps(it, course) }) {
                        flushComponent()
                    }
                    component += course
                }
                flushComponent()
            }
        return result
    }

    private fun createScheduleBitmap(
        term: String,
        week: Int,
        mode: ScheduleMode,
        courses: List<Course>,
        includeAllWeeks: Boolean = false
    ): Bitmap {
        val width = 2048
        val height = 1152
        val padding = 40f
        val gridTop = 150f
        val gridHeight = 1000f
        val gridWidth = width - padding * 2
        val timeColumnWidth = 196f
        val dayColumnWidth = (gridWidth - timeColumnWidth) / 7f
        val headerHeight = 166f
        val rowHeight = (gridHeight - headerHeight) / 5f
        val background = Color.rgb(243, 249, 252)
        val gridFill = Color.rgb(250, 253, 255)
        val headerFill = Color.rgb(239, 248, 252)
        val gridLine = Color.rgb(211, 229, 238)
        val primary = Color.rgb(23, 51, 63)
        val secondary = Color.rgb(87, 115, 130)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        canvas.drawColor(background)

        fun setText(size: Float, color: Int, style: Int, align: Paint.Align = Paint.Align.LEFT) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.textSize = size
            paint.color = color
            paint.typeface = Typeface.create("sans-serif", style)
            paint.textAlign = align
        }

        fun drawText(value: String, x: Float, baseline: Float, size: Float, color: Int, style: Int) {
            setText(size, color, style)
            canvas.drawText(value, x, baseline, paint)
        }

        fun drawCenteredText(value: String, centerX: Float, centerY: Float, size: Float, color: Int, style: Int) {
            setText(size, color, style, Paint.Align.CENTER)
            val metrics = paint.fontMetrics
            canvas.drawText(value, centerX, centerY - (metrics.ascent + metrics.descent) / 2f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        fun roundedRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = color
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
        }

        fun wrapText(value: String, maxWidth: Float, size: Float, style: Int): List<String> {
            setText(size, primary, style)
            val result = mutableListOf<String>()
            value.ifBlank { "-" }.split('\n').forEach { paragraph ->
                var remaining = paragraph.ifBlank { "-" }
                while (remaining.isNotEmpty()) {
                    val count = paint.breakText(remaining, true, maxWidth, null)
                    if (count <= 0) break
                    result += remaining.substring(0, count)
                    remaining = remaining.substring(count)
                }
            }
            return result.ifEmpty { listOf("-") }
        }

        fun timeLabel(value: String): String = value.removePrefix("0")

        fun lightCourseColor(color: Int): Int = Color.rgb(
            (Color.red(color) + (255 - Color.red(color)) * .84f).toInt(),
            (Color.green(color) + (255 - Color.green(color)) * .84f).toInt(),
            (Color.blue(color) + (255 - Color.blue(color)) * .84f).toInt()
        )

        fun courseTextColor(color: Int): Int = Color.rgb(
            (Color.red(color) * .42f).toInt().coerceIn(30, 110),
            (Color.green(color) * .42f).toInt().coerceIn(30, 110),
            (Color.blue(color) * .42f).toInt().coerceIn(30, 110)
        )

        val gridLeft = padding
        val gridRight = padding + gridWidth
        val gridBottom = gridTop + gridHeight
        roundedRect(gridLeft, gridTop, gridRight, gridBottom, 16f, gridFill)
        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(RectF(gridLeft, gridTop, gridRight, gridBottom), 16f, 16f, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        paint.style = Paint.Style.FILL
        paint.color = headerFill
        canvas.drawRect(gridLeft, gridTop, gridRight, gridTop + headerHeight, paint)
        canvas.restore()

        // 标题和副标题沿用示例图的宽屏留白比例；全校模式导出班级完整课表，
        // 因此副标题显示班级名而不是当前周次。
        setText(54f, primary, Typeface.NORMAL)
        paint.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        canvas.drawText("WeSDAU-课程表", padding, 96f, paint)
        drawText(
            if (includeAllWeeks) "$term    $publicScheduleClassName"
            else "$term    ${if (week > 0) "第${week}周" else "学期未开始"}",
            padding,
            136f,
            30f,
            secondary,
            Typeface.NORMAL
        )

        // 网格线
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = gridLine
        for (column in 0..7) {
            val x = gridLeft + if (column == 0) 0f else timeColumnWidth + (column - 1) * dayColumnWidth
            canvas.drawLine(x, gridTop, x, gridBottom, paint)
        }
        for (row in 0..5) {
            val y = gridTop + if (row == 0) 0f else headerHeight + (row - 1) * rowHeight
            canvas.drawLine(gridLeft, y, gridRight, y, paint)
        }
        canvas.drawRoundRect(RectF(gridLeft, gridTop, gridRight, gridBottom), 16f, 16f, paint)

        val headerCenterY = gridTop + headerHeight / 2f
        drawCenteredText("节次", gridLeft + timeColumnWidth / 2f, headerCenterY, 31f, Color.rgb(42, 77, 92), Typeface.BOLD)
        arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").forEachIndexed { index, label ->
            val centerX = gridLeft + timeColumnWidth + index * dayColumnWidth + dayColumnWidth / 2f
            drawCenteredText(label, centerX, headerCenterY, 31f, Color.rgb(42, 77, 92), Typeface.BOLD)
        }

        val timeRanges = if (mode == ScheduleMode.SPRING) springTimeRanges() else summerTimeRanges()
        for (row in 0 until 5) {
            val top = gridTop + headerHeight + row * rowHeight
            val centerX = gridLeft + timeColumnWidth / 2f
            drawCenteredText("第${row + 1}大节", centerX, top + rowHeight * .42f, 32f, Color.rgb(48, 82, 96), Typeface.BOLD)
            drawCenteredText(
                "${timeLabel(timeRanges[row * 2].first)}-${timeLabel(timeRanges[row * 2 + 1].second)}",
                centerX,
                top + rowHeight * .66f,
                22f,
                secondary,
                Typeface.NORMAL
            )
        }

        val visibleCourses = if (includeAllWeeks) courses else courses.filter {
            courseVisibleOnScheduleDate(it, term, week)
        }
        buildExportCoursePlacements(visibleCourses).forEach { placement ->
            val course = placement.course
            val start = course.startSlot / 2f
            val end = ((course.startSlot + course.slotCount).coerceAtMost(10)) / 2f
            val baseLeft = gridLeft + timeColumnWidth + course.day * dayColumnWidth + 7f
            val totalWidth = dayColumnWidth - 14f
            val cardGap = if (placement.columnCount > 1) 4f else 0f
            val cardWidth = (totalWidth - cardGap * (placement.columnCount - 1)) /
                placement.columnCount.coerceAtLeast(1)
            val left = baseLeft + placement.column * (cardWidth + cardGap)
            val right = left + cardWidth
            val top = gridTop + headerHeight + start * rowHeight + 9f
            val bottom = gridTop + headerHeight + end * rowHeight - 9f
            if (bottom <= top) return@forEach

            val fillColor = lightCourseColor(course.background)
            val textColor = courseTextColor(course.background)
            roundedRect(left, top, right, bottom, 14f, fillColor)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = lightCourseColor(course.background).let {
                Color.rgb(
                    (Color.red(it) * .92f).toInt(),
                    (Color.green(it) * .92f).toInt(),
                    (Color.blue(it) * .92f).toInt()
                )
            }
            canvas.drawRoundRect(RectF(left, top, right, bottom), 14f, 14f, paint)

            val textLeft = left + 11f
            val maxTextWidth = right - left - 22f
            // 课程名保持示例图的醒目粗体；教室和教师连续排列，避免导出图浪费空间。
            val titleLines = wrapText(course.name, maxTextWidth, 25f, Typeface.BOLD).take(2)
            val detailLines = buildList {
                addAll(formatExportRoom(course.room).split('\n').filter { it.isNotBlank() })
                // 完整专业课表中，周数比教师名更重要；空间不足时 take(6) 会优先保留周数。
                if (includeAllWeeks && course.weeks.isNotBlank()) add("第${course.weeks}周")
                if (course.teacher.isNotBlank()) add(course.teacher)
            }.flatMap { line ->
                if (line.isBlank()) listOf("") else wrapText(line, maxTextWidth, 19f, Typeface.NORMAL)
            }.take(6)
            var baseline = top + 31f
            titleLines.forEach { line ->
                if (baseline <= bottom - 10f) {
                    drawText(line, textLeft, baseline, 25f, textColor, Typeface.BOLD)
                    baseline += 28f
                }
            }
            detailLines.forEach { line ->
                if (baseline <= bottom - 9f) {
                    if (line.isNotBlank()) {
                        drawText(line, textLeft, baseline, 19f, textColor, Typeface.NORMAL)
                    }
                    baseline += 24f
                }
            }
        }
        return bitmap
    }

    private fun createCourseFiles(): Pair<File, File>? {
        try {
            val directory = prepareShareCache()
            val pngFile = File(directory, "课程表.png")
            val csvFile = File(directory, "课程表.csv")
            val bitmap = createScheduleBitmap(
                activeScheduleTerm(), currentWeek, scheduleMode, activeScheduleCourses(), viewingPublicSchedule
            )
            try {
                FileOutputStream(pngFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
            writeCourseCsv(csvFile, activeScheduleCourses())
            return pngFile to csvFile
        } catch (error: Exception) {
            showLiquidToast(
                message = "导出失败：${error.message ?: "未知错误"}",
                visual = LiquidToastVisual.ERROR,
                durationMillis = 2_800L
            )
            return null
        }
    }

    private fun saveSchedulePng() {
        if (scheduleExporting) return
        val term = activeScheduleTerm()
        val week = currentWeek
        val mode = scheduleMode
        val courses = activeScheduleCourses()
        if (courses.isEmpty()) {
            Toast.makeText(this, "课表尚未准备好", Toast.LENGTH_SHORT).show()
            return
        }
        scheduleExporting = true
        showLiquidToast(
            message = "正在保存课表图片…",
            visual = LiquidToastVisual.LOADING,
            durationMillis = 0L
        )
        networkExecutor.execute {
            var bitmap: Bitmap? = null
            try {
                bitmap = createScheduleBitmap(term, week, mode, courses, viewingPublicSchedule)
                val displayName = if (viewingPublicSchedule) {
                    "专业课表-$term-$publicScheduleClassName.png"
                } else {
                    val weekName = if (week > 0) "第${week}周" else "学期未开始"
                    "课表-$term-$weekName.png"
                }
                saveScheduleBitmapToPictures(bitmap, displayName)
                runOnUiThread {
                    showLiquidToast(
                        message = "课表图片已保存到 Pictures/WeSDAU",
                        visual = LiquidToastVisual.SUCCESS,
                        durationMillis = 2_600L
                    )
                }
            } catch (error: Exception) {
                runOnUiThread {
                    dismissLiquidToast()
                    Toast.makeText(this, "保存课表图片失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            } finally {
                bitmap?.recycle()
                scheduleExporting = false
            }
        }
    }

    private fun saveScheduleBitmapToPictures(bitmap: Bitmap, displayName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/WeSDAU")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建图片文件")
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片编码失败" }
                } ?: error("无法写入图片文件")
                contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            } catch (error: Exception) {
                contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.apply { mkdirs() }
                ?: error("无法访问图片目录")
            File(directory, displayName).outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片编码失败" }
            }
        }
    }

    private fun writeCourseCsv(file: File, courses: List<Course>) {
        val lines = mutableListOf(listOf("课程名称", "星期", "开始节数", "结束节数", "老师", "地点", "周数").joinToString(",") { csvEscape(it) })
        courses.forEach { course ->
            lines += listOf(
                course.name,
                (course.day + 1).toString(),
                (course.startSlot + 1).toString(),
                (course.startSlot + course.slotCount).toString(),
                course.teacher,
                course.room,
                course.weeks
            ).joinToString(",") { csvEscape(it) }
        }
        val content = lines.joinToString("\r\n") + "\r\n"
        FileOutputStream(file).use { output ->
            output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            output.write(content.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun shareSingleFile(file: File, mimeType: String, title: String) {
        val authority = "$packageName.fileprovider"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this@MainActivity, authority, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    private fun prepareShareCache(): File = File(cacheDir, "share").apply {
        mkdirs()
        listFiles()?.forEach { stale ->
            if (stale.isFile && System.currentTimeMillis() - stale.lastModified() > 60 * 60 * 1000L) {
                stale.delete()
            }
        }
    }

    private fun recolorCourses(
        courses: List<Course>,
        term: String = selectedTerm(),
        persistMapping: Boolean = true,
        refreshMapping: Boolean = false
    ): List<Course> {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        // 浅色与深色的最终显示色板不同，因此分别保存映射，避免切换主题时
        // 一套主题为了满足自己的色差约束而破坏另一套主题已经稳定的配色。
        val colorMapKey = if (activeThemeColors.isDark) KEY_DARK_COLOR_MAP else KEY_COLOR_MAP
        val previousMapping = if (persistMapping) {
            runCatching {
                JSONObject(preferences.getString(colorMapKey, "{}") ?: "{}")
            }.getOrDefault(JSONObject())
        } else {
            JSONObject()
        }
        val stored = if (refreshMapping) JSONObject() else previousMapping
        val allNames = courses.map { it.name }.distinct().sorted()
        val actualWeek = weekForTerm(term).coerceAtLeast(1)
        val activeCourses = courses.filter { latestCourseWeek(it) >= actualWeek }
        val activeNames = activeCourses.map { it.name }.distinct()
        val activeNameSet = activeNames.toSet()
        val visibleCourseCounts = activeCourses
            .filter { courseVisibleInWeek(it, actualWeek) }
            .groupingBy { it.name }
            .eachCount()
        val relations = buildCourseColorRelations(activeCourses, actualWeek)
        val usedActiveIndices = mutableSetOf<Int>()
        val indexByName = mutableMapOf<String, Int>()

        // 深色模式优先给当前周可见、占用卡片较多的课程分配锚点色，避免未来周
        // 的隐藏课程先占满色相空间，迫使当前页面上的不同课程落入同一色系。
        val allocationOrder = activeNames.sortedWith(
            compareByDescending<String> {
                if (activeThemeColors.isDark) visibleCourseCounts[it] ?: 0 else 0
            }
                .thenByDescending { relations[it]?.values?.sum() ?: 0.0 }
                .thenByDescending { relations[it]?.size ?: 0 }
                .thenBy { it }
        )
        val preferredOnly = activeNames.size <= COURSE_COLORS.size
        val candidateCount = when {
            activeThemeColors.isDark -> maxOf(DARK_COURSE_CANDIDATE_COUNT, activeNames.size + 16)
            preferredOnly -> COURSE_COLORS.size
            else -> maxOf(COURSE_COLORS.size, activeNames.size + 8)
        }
        allocationOrder.forEach { name ->
            val previousIndex = previousMapping.optInt(name, -1)
            val storedIndex = if (refreshMapping) -1 else previousIndex
            val availableCandidates = buildList {
                addAll(0 until candidateCount)
                if (storedIndex >= candidateCount) add(storedIndex)
            }.filterNot { it in usedActiveIndices }
            val candidateIndices = if (
                refreshMapping && previousIndex >= 0 && availableCandidates.size > 1
            ) {
                availableCandidates.filterNot { it == previousIndex }.ifEmpty { availableCandidates }
            } else {
                availableCandidates
            }
            // 连续色差不足以表达“同色系”：例如橄榄绿与深绿在数值上相隔
            // 不小，人眼仍会把它们都归为绿色。深色模式先按人眼色系占位，
            // 当前周不同课程只有在色系确实不足时才允许复用。
            val familySeparatedCandidates = if (activeThemeColors.isDark) {
                candidateIndices.filter { candidate ->
                    !hasCurrentWeekColorFamilyCollision(
                        name = name,
                        candidateIndex = candidate,
                        assignedIndices = indexByName,
                        relations = relations
                    )
                }
            } else {
                candidateIndices
            }
            val preferredCandidates = familySeparatedCandidates.ifEmpty { candidateIndices }
            // 先硬性排除同一页面上过于接近的颜色，再用综合评分选择最优项。
            // 课程很多、色板空间不足时逐级放宽阈值，避免所有候选都被淘汰。
            val distanceThresholds = if (activeThemeColors.isDark) {
                listOf(9.0, 7.0, 6.0, 0.0)
            } else {
                listOf(
                    MIN_SAME_PAGE_COLOR_DISTANCE,
                    MIN_SAME_PAGE_COLOR_DISTANCE - 4.0,
                    MIN_SAME_PAGE_COLOR_DISTANCE - 8.0,
                    0.0
                )
            }
            val eligibleCandidates = distanceThresholds.firstNotNullOfOrNull { threshold ->
                preferredCandidates.filter { candidate ->
                    minimumRelatedColorDistance(
                        name = name,
                        candidateIndex = candidate,
                        assignedIndices = indexByName,
                        relations = relations
                    ) >= threshold
                }.takeIf { it.isNotEmpty() }
            } ?: preferredCandidates
            val chosen = eligibleCandidates.maxByOrNull { candidate ->
                courseColorAssignmentScore(
                    name = name,
                    candidateIndex = candidate,
                    assignedIndices = indexByName,
                    relations = relations,
                    storedIndex = storedIndex
                )
            } ?: generateSequence(0) { it + 1 }.first { it !in usedActiveIndices }
            indexByName[name] = chosen
            usedActiveIndices += chosen
            stored.put(name, chosen)
        }

        // 已结束课程不再占用颜色名额，但保留自己的历史映射；因此它们可以与当前课程复用颜色。
        allNames.filterNot { it in activeNameSet }.forEach { name ->
            val previousIndex = previousMapping.optInt(name, -1)
            val historical = if (refreshMapping && previousIndex >= 0) {
                (previousIndex + 1).mod(candidateCount)
            } else {
                stored.optInt(name, -1).takeIf { it >= 0 } ?: 0
            }
            indexByName[name] = historical
            stored.put(name, historical)
        }
        val activeMap = JSONObject()
        allNames.forEach { name -> activeMap.put(name, indexByName[name] ?: 0) }
        if (persistMapping) {
            preferences.edit()
                .putString(colorMapKey, activeMap.toString())
                .apply()
        }
        return courses.map { it.copy(background = courseColorAt(indexByName[it.name] ?: 0)) }
    }

    private fun hasCurrentWeekColorFamilyCollision(
        name: String,
        candidateIndex: Int,
        assignedIndices: Map<String, Int>,
        relations: Map<String, Map<String, Double>>
    ): Boolean {
        val candidateFamily = darkCourseColorFamily(courseColorAt(candidateIndex))
        val courseRelations = relations[name].orEmpty()
        return assignedIndices.any { (otherName, otherIndex) ->
            val relationWeight = courseRelations[otherName] ?: return@any false
            relationWeight >= CURRENT_WEEK_COURSE_COLOR_WEIGHT &&
                darkCourseColorFamily(courseColorAt(otherIndex)) == candidateFamily
        }
    }

    /**
     * 深色课程使用 8 个人眼色系。绿色范围刻意加宽，避免橄榄绿、草绿和深绿
     * 虽然色相数值不同，却在同一页面上仍被感知为两种近似绿色。
     */
    private fun darkCourseColorFamily(color: Int): Int {
        val lab = colorToOklab(color)
        val hue = Math.toDegrees(kotlin.math.atan2(lab[2], lab[1])).let {
            if (it < 0.0) it + 360.0 else it
        }
        return when {
            hue < 25.0 || hue >= 345.0 -> 0 // 红
            hue < 65.0 -> 1                 // 橙
            hue < 90.0 -> 2                 // 黄 / 琥珀
            hue < 170.0 -> 3                // 绿（包含橄榄绿）
            hue < 210.0 -> 4                // 青
            hue < 255.0 -> 5                // 蓝
            hue < 300.0 -> 6                // 紫
            else -> 7                       // 品红 / 粉
        }
    }

    private fun minimumRelatedColorDistance(
        name: String,
        candidateIndex: Int,
        assignedIndices: Map<String, Int>,
        relations: Map<String, Map<String, Double>>
    ): Double {
        val courseRelations = relations[name].orEmpty()
        return assignedIndices.asSequence()
            .mapNotNull { (otherName, otherIndex) ->
                val relationWeight = courseRelations[otherName] ?: return@mapNotNull null
                // 深色硬约束只针对当前周真正同时可见的课程。若把未来所有课程
                // 一起塞进硬约束，课程较多时阈值必然降到 0，反而放过当前页撞色。
                val requiresHardSeparation = !activeThemeColors.isDark ||
                    relationWeight >= CURRENT_WEEK_COURSE_COLOR_WEIGHT
                if (requiresHardSeparation) {
                    colorDistance(courseColorAt(candidateIndex), courseColorAt(otherIndex))
                } else {
                    null
                }
            }
            .minOrNull() ?: 100.0
    }

    private fun courseColorAssignmentScore(
        name: String,
        candidateIndex: Int,
        assignedIndices: Map<String, Int>,
        relations: Map<String, Map<String, Double>>,
        storedIndex: Int
    ): Double {
        val candidateColor = courseColorAt(candidateIndex)
        var globalMinimum = Double.POSITIVE_INFINITY
        var sameWeekMinimum = Double.POSITIVE_INFINITY
        var currentWeekMinimum = Double.POSITIVE_INFINITY
        var nearbyMinimum = Double.POSITIVE_INFINITY
        var weightedDistance = 0.0
        var relationWeightTotal = 0.0
        val courseRelations = relations[name].orEmpty()

        assignedIndices.forEach { (otherName, otherIndex) ->
            val distance = colorDistance(candidateColor, courseColorAt(otherIndex))
            globalMinimum = minOf(globalMinimum, distance)
            val relationWeight = courseRelations[otherName] ?: return@forEach
            sameWeekMinimum = minOf(sameWeekMinimum, distance)
            if (relationWeight >= CURRENT_WEEK_COURSE_COLOR_WEIGHT) {
                currentWeekMinimum = minOf(currentWeekMinimum, distance)
            }
            weightedDistance += distance * relationWeight
            relationWeightTotal += relationWeight
            val spatiallyNearby = relationWeight == NEARBY_COURSE_COLOR_WEIGHT ||
                relationWeight >= CURRENT_WEEK_NEARBY_COURSE_COLOR_WEIGHT
            if (spatiallyNearby) {
                nearbyMinimum = minOf(nearbyMinimum, distance)
            }
        }

        val fallbackDistance = globalMinimum.takeIf(Double::isFinite) ?: 100.0
        val termDistance = sameWeekMinimum.takeIf(Double::isFinite) ?: fallbackDistance
        val pageDistance = if (activeThemeColors.isDark) {
            currentWeekMinimum.takeIf(Double::isFinite) ?: termDistance
        } else {
            termDistance
        }
        val nearDistance = nearbyMinimum.takeIf(Double::isFinite) ?: pageDistance
        val pageAverage = if (relationWeightTotal > 0.0) {
            weightedDistance / relationWeightTotal
        } else {
            fallbackDistance
        }
        val stabilityBonus = if (candidateIndex == storedIndex) 4.0 else 0.0
        val generatedPenalty = if (candidateIndex >= COURSE_COLORS.size) 6.0 else 0.0

        // 深色模式以当前周真实可见色差为首要目标；未来周关系用于平均分打破
        // 相近方案。历史颜色仅作为轻量偏好，不能压过当前页面辨识度。
        return pageDistance * 5.0 +
            nearDistance * 1.8 +
            pageAverage * 0.7 +
            fallbackDistance * 0.45 +
            stabilityBonus -
            generatedPenalty -
            candidateIndex * 0.001
    }

    private fun latestCourseWeek(course: Course): Int {
        val normalized = course.weeks.replace("周", "").replace("—", "-").replace("至", "-")
        val ranges = Regex("(\\d+)(?:\\s*-\\s*(\\d+))?").findAll(normalized).toList()
        if (ranges.isEmpty()) return 20
        return ranges.maxOfOrNull { match ->
            match.groupValues[2].toIntOrNull() ?: match.groupValues[1].toIntOrNull() ?: 20
        }?.coerceIn(1, 20) ?: 20
    }

    private fun buildCourseColorRelations(
        courses: List<Course>,
        fromWeek: Int
    ): Map<String, Map<String, Double>> {
        val graph = courses.map { it.name }.distinct()
            .associateWith { mutableMapOf<String, Double>() }
        for (firstIndex in courses.indices) {
            val first = courses[firstIndex]
            for (secondIndex in firstIndex + 1 until courses.size) {
                val second = courses[secondIndex]
                if (first.name == second.name) continue
                val coexist = (fromWeek..20).any { week ->
                    courseVisibleInWeek(first, week) && courseVisibleInWeek(second, week)
                }
                if (!coexist) continue
                val visibleThisWeek = courseVisibleInWeek(first, fromWeek) &&
                    courseVisibleInWeek(second, fromWeek)
                val nearby = courseBlocksAreNear(first, second)
                val weight = when {
                    visibleThisWeek && nearby -> CURRENT_WEEK_NEARBY_COURSE_COLOR_WEIGHT
                    visibleThisWeek -> CURRENT_WEEK_COURSE_COLOR_WEIGHT
                    nearby -> NEARBY_COURSE_COLOR_WEIGHT
                    else -> SAME_WEEK_COURSE_COLOR_WEIGHT
                }
                graph[first.name]?.merge(second.name, weight, ::maxOf)
                graph[second.name]?.merge(first.name, weight, ::maxOf)
            }
        }
        return graph.mapValues { it.value.toMap() }
    }

    private fun courseBlocksAreNear(first: Course, second: Course): Boolean {
        val dayGap = kotlin.math.abs(first.day - second.day)
        if (dayGap > 1) return false
        val firstEnd = first.startSlot + first.slotCount
        val secondEnd = second.startSlot + second.slotCount
        val slotGap = when {
            firstEnd < second.startSlot -> second.startSlot - firstEnd
            secondEnd < first.startSlot -> first.startSlot - secondEnd
            else -> 0
        }
        // 同一天上下相邻、或相邻两天处在同一时间带的卡片，都属于视觉邻居。
        return if (dayGap == 0) slotGap <= 1 else slotGap == 0
    }

    /** OKLab 欧氏距离；深色模式直接比较最终显示的前景色。 */
    private fun colorDistance(first: Int, second: Int): Double {
        val firstCompared = if (activeThemeColors.isDark) {
            darkCourseTone(
                first,
                lightness = DARK_COURSE_FOREGROUND_LIGHTNESS,
                chroma = DARK_COURSE_FOREGROUND_CHROMA,
                alpha = 255
            )
        } else {
            first
        }
        val secondCompared = if (activeThemeColors.isDark) {
            darkCourseTone(
                second,
                lightness = DARK_COURSE_FOREGROUND_LIGHTNESS,
                chroma = DARK_COURSE_FOREGROUND_CHROMA,
                alpha = 255
            )
        } else {
            second
        }
        val firstLab = colorToOklab(firstCompared)
        val secondLab = colorToOklab(secondCompared)
        val lightness = firstLab[0] - secondLab[0]
        val greenRed = firstLab[1] - secondLab[1]
        val blueYellow = firstLab[2] - secondLab[2]
        return Math.sqrt(
            lightness * lightness + greenRed * greenRed + blueYellow * blueYellow
        ) * 100.0
    }

    private fun colorToOklab(color: Int): DoubleArray {
        fun linearChannel(channel: Int): Double {
            val normalized = channel / 255.0
            return if (normalized <= 0.04045) {
                normalized / 12.92
            } else {
                Math.pow((normalized + 0.055) / 1.055, 2.4)
            }
        }

        val red = linearChannel(Color.red(color))
        val green = linearChannel(Color.green(color))
        val blue = linearChannel(Color.blue(color))
        val l = 0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue
        val m = 0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue
        val s = 0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue
        val lRoot = Math.cbrt(l)
        val mRoot = Math.cbrt(m)
        val sRoot = Math.cbrt(s)
        return doubleArrayOf(
            0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot,
            1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot,
            0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot
        )
    }

    private fun courseColorAt(index: Int): Int {
        if (index < COURSE_COLORS.size) return COURSE_COLORS[index]
        return Color.HSVToColor(floatArrayOf((index * 137.508f) % 360f, 0.48f, 0.90f))
    }

    /**
     * 深色课程色保留浅色色板的基础色相，仅统一 OKLab 明度与彩度。
     * 深色模式拥有独立映射，并按照这里生成的最终颜色计算色差，因此不再需要
     * 按色板索引二次洗牌色相，也不会让同一课程在两种主题下变成完全无关的颜色。
     */
    private fun darkCourseTone(color: Int, lightness: Double, chroma: Double, alpha: Int): Int {
        val sourceLab = colorToOklab(color)
        val hue = kotlin.math.atan2(sourceLab[2], sourceLab[1])
        return oklabToColor(
            lightness = lightness,
            greenRed = kotlin.math.cos(hue) * chroma,
            blueYellow = kotlin.math.sin(hue) * chroma,
            alpha = alpha
        )
    }

    private fun oklabToColor(
        lightness: Double,
        greenRed: Double,
        blueYellow: Double,
        alpha: Int
    ): Int {
        val lRoot = lightness + 0.3963377774 * greenRed + 0.2158037573 * blueYellow
        val mRoot = lightness - 0.1055613458 * greenRed - 0.0638541728 * blueYellow
        val sRoot = lightness - 0.0894841775 * greenRed - 1.2914855480 * blueYellow
        val l = lRoot * lRoot * lRoot
        val m = mRoot * mRoot * mRoot
        val s = sRoot * sRoot * sRoot

        fun gamma(channel: Double): Int {
            val encoded = if (channel <= 0.0031308) {
                12.92 * channel
            } else {
                1.055 * Math.pow(channel.coerceAtLeast(0.0), 1.0 / 2.4) - 0.055
            }
            return (encoded.coerceIn(0.0, 1.0) * 255.0).roundToInt()
        }

        return Color.argb(
            alpha.coerceIn(0, 255),
            gamma(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            gamma(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            gamma(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s)
        )
    }

    /** 所有深色卡片使用相同感知明度，避免蓝、红、黄看起来深浅不一。 */
    private fun displayedCourseColor(color: Int): Int {
        if (!activeThemeColors.isDark) return color
        return darkCourseTone(
            color,
            lightness = DARK_COURSE_BACKGROUND_LIGHTNESS,
            chroma = DARK_COURSE_BACKGROUND_CHROMA,
            alpha = 232
        )
    }

    /** 前景与卡片共享色相，以柔和的浅彩色保持辨识度，避免高彩度霓虹感。 */
    private fun displayedCourseForeground(color: Int, fallback: Int): Int {
        if (!activeThemeColors.isDark) return fallback
        return darkCourseTone(
            color,
            lightness = DARK_COURSE_FOREGROUND_LIGHTNESS,
            chroma = DARK_COURSE_FOREGROUND_CHROMA,
            alpha = 255
        )
    }

    private fun shareWeekPng() {
        saveSchedulePng()
        hideSharePicker()
    }

    private fun shareCsv() {
        createCourseFiles()?.second?.let { shareSingleFile(it, "text/csv", "分享课程 CSV") }
        hideSharePicker()
    }

    private fun shareApp() {
        try {
            val directory = prepareShareCache()
            val apk = File(directory, "WeSDAU课程表.apk")
            File(applicationInfo.sourceDir).copyTo(apk, overwrite = true)
            shareSingleFile(apk, "application/vnd.android.package-archive", "分享 WeSDAU课程表")
        } catch (error: Exception) {
            showLiquidToast(
                message = "分享 APP 失败：${error.message ?: "未知错误"}",
                visual = LiquidToastVisual.ERROR,
                durationMillis = 2_800L
            )
        }
        hideSharePicker()
    }

    private fun showSharePicker() {
        if (shareOverlay != null || pickerDialogCapturePending) return
        pickerDialogCapturePending = true
        captureUpdateBackdrop { pageSnapshot ->
            pickerDialogCapturePending = false
            if (isFinishing || isDestroyed || shareOverlay != null) {
                pageSnapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
                return@captureUpdateBackdrop
            }
            val dialog = LiquidPickerDialogView(
                context = this,
                pageSnapshot = pageSnapshot,
                title = "分享",
                options = listOf(
                    LiquidPickerOption(
                        title = if (viewingPublicSchedule) "导出本专业课表为PNG" else "导出本周课表为PNG",
                        subtitle = if (viewingPublicSchedule) "包含课程周数" else "保存当前周课表图片",
                        iconRes = R.drawable.ic_share_image,
                        onClick = ::shareWeekPng
                    ),
                    LiquidPickerOption(
                        title = "分享CSV文件",
                        subtitle = "可直接导入WakeUp课程表",
                        iconRes = R.drawable.ic_share_spreadsheet,
                        onClick = ::shareCsv
                    ),
                    LiquidPickerOption(
                        title = "分享 APP",
                        subtitle = "WeSDAU课程表安装包",
                        iconRes = R.drawable.ic_share_app,
                        onClick = ::shareApp
                    )
                ),
                onDismiss = ::hideSharePicker
            )
            pageHost.addView(dialog, matchParentParams())
            shareOverlay = dialog
            dialog.alpha = 0f
            dialog.animate().alpha(1f).setDuration(180).start()
        }
    }

    private fun hideSharePicker() {
        val overlay = shareOverlay ?: return
        overlay.animate().alpha(0f).setDuration(140).withEndAction {
            pageHost.removeView(overlay)
            (overlay as? LiquidPickerDialogView)?.releaseSnapshot()
            shareOverlay = null
        }.start()
    }

    private fun nextCourseForNow(): Course? {
        val courses = loadCourseCache()
        val actualWeek = weekForTerm(selectedTerm())
        if (actualWeek <= 0) return firstCourseForOpening(courses)
        val now = Calendar.getInstance()
        val today = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val starts = currentStartMinutes()
        val next = courses.filter { courseVisibleInWeek(it, actualWeek) }
            .mapNotNull { course ->
                val dayDelta = (course.day - today + 7) % 7
                val courseDate = (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_MONTH, dayDelta)
                }
                if (CampusHolidayCalendar.isHoliday(courseDate)) return@mapNotNull null
                Triple(dayDelta, starts[course.startSlot], course)
            }
            .filter { it.first > 0 || it.second > minute }
            .minWithOrNull(compareBy<Triple<Int, Int, Course>> { it.first }.thenBy { it.second })
            ?.third
        return next ?: firstCourseForOpening(courses)
    }

    private fun firstCourseForOpening(courses: List<Course>): Course? {
        return courses.filter { courseVisibleInWeek(it, 1) }
            .minWithOrNull(compareBy<Course> { it.day }.thenBy { it.startSlot })
            ?: courses.minWithOrNull(compareBy<Course> { it.day }.thenBy { it.startSlot })
    }

    private fun courseVisibleInWeek(course: Course, week: Int): Boolean {
        // 第 0 周专门表示“学期尚未开始”。即使课程没有填写周次范围，
        // 也不能在开学日期之前绘制或响应点击。
        if (week <= 0) return false
        val normalized = course.weeks.replace("周", "").replace("—", "-").replace("至", "-")
        val ranges = Regex("(\\d+)(?:\\s*-\\s*(\\d+))?").findAll(normalized).toList()
        if (ranges.isEmpty()) return true
        return ranges.any { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return@any false
            val end = match.groupValues[2].toIntOrNull() ?: start
            week in start.coerceAtLeast(1)..end
        }
    }

    private fun courseVisibleOnScheduleDate(course: Course, term: String, week: Int): Boolean {
        if (!courseVisibleInWeek(course, week)) return false
        val courseDate = termStartDate(term).apply {
            add(Calendar.DAY_OF_MONTH, (week - 1) * 7 + course.day)
        }
        return !CampusHolidayCalendar.isHoliday(courseDate)
    }

    private fun jumpToCurrentWeek() {
        val actualWeek = weekForTerm(selectedTerm())
        if (actualWeek == currentWeek) return
        val direction = if (actualWeek > currentWeek) -1 else 1
        currentWeek = actualWeek
        scheduleHeader?.updateWeek(formatWeekLabel(currentWeek))
        val grid = scheduleGrid ?: return
        val distance = dp(72f).toFloat() * direction
        grid.animate().translationX(distance).alpha(0f).setDuration(140).withEndAction {
            grid.setWeekIndex(currentWeek)
            grid.translationX = -distance
            grid.animate().translationX(0f).alpha(1f).setDuration(190).start()
        }.start()
    }

    private fun currentStartMinutes() = if (scheduleMode == ScheduleMode.SPRING) {
        intArrayOf(480, 535, 600, 655, 840, 895, 960, 1015, 1140, 1195)
    } else {
        intArrayOf(480, 535, 600, 655, 870, 925, 990, 1045, 1170, 1225)
    }

    private fun togglePushNotifications() {
        if (pushEnabled) {
            pushEnabled = false
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_PUSH_ENABLED, false).apply()
            cancelSystemCourseReminder()
            showLiquidToast(
                message = "课程提醒已关闭",
                visual = LiquidToastVisual.BELL_OFF,
                durationMillis = 1_800L
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingPushEnable = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
            return
        }
        enablePushNotifications()
    }

    private fun enablePushNotifications() {
        pushEnabled = true
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_PUSH_ENABLED, true).apply()
        showLiquidToast(
            message = "课程提醒已开启",
            visual = LiquidToastVisual.BELL_ON,
            durationMillis = 1_800L
        )
        requestBatteryOptimizationExemption()
        schedulePushNotifications()
        nextCourseForNow()?.let { course -> CourseNotification.show(this, course.name, course.room, courseTimeLabel(course)) }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (power.isIgnoringBatteryOptimizations(packageName)) {
            preferences.edit().putBoolean(KEY_BATTERY_PROMPTED, true).apply()
            return
        }
        if (preferences.getBoolean(KEY_BATTERY_PROMPTED, false)) return
        preferences.edit().putBoolean(KEY_BATTERY_PROMPTED, true).apply()
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: ActivityNotFoundException) {
            // Some OEMs do not expose the standard battery optimization screen.
        }
    }

    private fun scheduleSystemCourseReminder() {
        schedulePushNotifications()
    }

    private fun schedulePushNotifications() {
        CourseReminderScheduler.scheduleNext(this)
    }

    private fun courseTimeLabel(course: Course): String {
        val ranges = if (scheduleMode == ScheduleMode.SPRING) springTimeRanges() else summerTimeRanges()
        return "${ranges[course.startSlot].first}-${ranges[course.startSlot + course.slotCount - 1].second}"
    }

    private fun springTimeRanges() = arrayOf(
        "8:00" to "8:45", "8:55" to "9:40", "10:00" to "10:45", "10:55" to "11:40", "14:00" to "14:45",
        "14:55" to "15:40", "16:00" to "16:45", "16:55" to "17:40", "19:00" to "19:45", "19:55" to "20:40"
    )

    private fun summerTimeRanges() = arrayOf(
        "8:00" to "8:45", "8:55" to "9:40", "10:00" to "10:45", "10:55" to "11:40", "14:30" to "15:15",
        "15:25" to "16:10", "16:30" to "17:15", "17:25" to "18:10", "19:30" to "20:15", "20:25" to "21:10"
    )

    private fun cancelSystemCourseReminder() {
        if (!::pageHost.isInitialized) return
        cancelPushAlarmsOnly()
        CourseNotification.cancel(this)
    }

    private fun cancelPushAlarmsOnly() {
        CourseReminderScheduler.cancel(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (pendingPushEnable) {
                pendingPushEnable = false
                enablePushNotifications()
                actionMenuOverlay?.setPushState(pushEnabled)
                return
            }
            scheduleSystemCourseReminder()
        }
    }

    private inner class ScheduleScrollView(context: Context) : ScrollView(context) {
        private var downX = 0f
        private var downY = 0f
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) return false
                }
            }
            return super.onInterceptTouchEvent(event)
        }
    }

    private fun todayLabel(): String = SimpleDateFormat("yyyy/M/d", Locale.CHINA).format(Calendar.getInstance().time)

    private fun daysUntilTermStart(): Int {
        val start = termStartDate(selectedTerm()).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        return ((start.timeInMillis - today.timeInMillis) / 86_400_000L).toInt().coerceAtLeast(0)
    }

    private fun formatWeekLabel(week: Int): String = if (week > 0) "第 $week 周" else "学期未开始"

    private fun sampleCourses() = listOf(
        Course(0, 0, 2, "高等数学 A", "5N201", "张老师", Color.rgb(232, 126, 158), Color.WHITE),
        Course(0, 2, 2, "人工智能通识基础", "5S416", "高老师", Color.rgb(231, 142, 168), Color.WHITE),
        Course(0, 4, 2, "大学英语", "北校12号楼310", "王老师", Color.rgb(230, 157, 126), Color.WHITE),
        Course(1, 0, 2, "大学化学", "E308", "陈老师", Color.rgb(181, 145, 226), Color.WHITE),
        Course(1, 2, 2, "程序设计基础", "N104", "陈老师", Color.rgb(103, 205, 191), Color.WHITE),
        Course(1, 6, 2, "体育", "S514", "孟老师", Color.rgb(109, 153, 222), Color.WHITE),
        Course(2, 0, 2, "计算机导论", "图信楼413", "李老师", Color.rgb(182, 147, 224), Color.WHITE),
        Course(2, 2, 2, "大学英语 B1", "图信楼大厅A区", "曹老师", Color.rgb(100, 158, 206), Color.WHITE),
        Course(2, 4, 2, "思想道德与法治", "北校文理大楼503", "赵老师", Color.rgb(91, 167, 205), Color.WHITE),
        Course(3, 0, 2, "习近平新时代中国特色社会主义思想概论", "19#408", "周老师", Color.rgb(235, 177, 101), Color.WHITE),
        Course(3, 2, 2, "大学物理", "南校区体育北足球场", "周老师", Color.rgb(236, 132, 107), Color.WHITE),
        Course(3, 6, 2, "新时代实践教育", "22#402", "李老师", Color.rgb(230, 128, 160), Color.WHITE),
        Course(4, 0, 2, "数据结构", "W205", "高老师", Color.rgb(100, 201, 187), Color.WHITE),
        Course(4, 2, 2, "高等数学 A1", "西北区体育N", "张老师", Color.rgb(97, 202, 188), Color.WHITE),
        Course(4, 4, 2, "线性代数", "南校区实验楼C楼C241", "张老师", Color.rgb(182, 147, 224), Color.WHITE)
    )

    private fun sampleScoreResult(term: String): RemoteScoreResult {
        val records = listOf(
            RemoteScore("BK000101", "高等数学 A", "5", "91", "-"),
            RemoteScore("BK000205", "人工智能通识基础", "2", "88", "-"),
            RemoteScore("BK000307", "大学英语", "2", "86", "-"),
            RemoteScore("BK090102", "程序设计基础", "3", "94", "-"),
            RemoteScore("BK090201", "数据结构", "3", "92", "-"),
            RemoteScore("BK000408", "大学物理", "3", "84", "-"),
            RemoteScore("BK000512", "思想道德与法治", "3", "90", "-")
        )
        return recalculateScoreResult(RemoteScoreResult(
            term = term,
            records = records,
            averageScore = "89.29",
            averageCreditGpa = "-",
            totalCredits = "21"
        ))
    }

    private fun sampleScoreDetail(record: RemoteScore): RemoteScoreDetail {
        val total = record.score.toDoubleOrNull() ?: 90.0
        val usual = (total + 4.0).coerceAtMost(100.0)
        val final = (total - usual * .4) / .6
        fun display(value: Double): String {
            val oneDecimal = String.format(Locale.US, "%.1f", value)
            return oneDecimal.removeSuffix(".0")
        }
        return RemoteScoreDetail(
            usualScore = display(usual),
            usualRatio = "40",
            finalScore = display(final),
            finalRatio = "60",
            totalScore = record.score
        )
    }

    private fun sampleExams() = listOf(
        RemoteExam(
            courseName = "高等数学A",
            examWeek = "10",
            examWeekday = "7",
            examSessions = "1-2",
            classroom = "E307B"
        ),
        RemoteExam(
            courseName = "C语言程序设计",
            examWeek = "17",
            examWeekday = "7",
            examSessions = "9-10",
            classroom = "N302"
        )
    )

    private fun hasLocalCourseCache(): Boolean {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val term = preferences.getString(KEY_TERM, "").orEmpty()
        return account.isNotBlank() && term.isNotBlank() && activateCourseCache(account, term)
    }

    private fun publicScheduleFile(term: String): File {
        val safeTerm = term.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(filesDir, "public_schedule_$safeTerm.json.gz")
    }

    private fun publicScheduleIndexFile(term: String): File {
        val safeTerm = term.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(filesDir, "public_schedule_index_$safeTerm.json.gz")
    }

    private fun publicScheduleLookupFile(term: String): File {
        val safeTerm = term.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(filesDir, "public_schedule_lookup_$safeTerm.db")
    }

    private fun hasPublicScheduleCache(term: String): Boolean {
        val file = publicScheduleFile(term)
        return file.isFile && file.length() > 0L
    }

    private fun publicScheduleHashKey(term: String): String {
        val safeTerm = term.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${KEY_PUBLIC_SCHEDULE_HASH_PREFIX}$safeTerm"
    }

    private fun publicScheduleLookupHashKey(term: String): String {
        return "${publicScheduleHashKey(term)}_lookup"
    }

    private fun hasPublicScheduleLookup(term: String): Boolean {
        if (term.isBlank()) return false
        val file = publicScheduleLookupFile(term)
        if (!file.isFile || file.length() == 0L) return false
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!preferences.contains(publicScheduleLookupHashKey(term))) return false
        return preferences.getString(publicScheduleLookupHashKey(term), null).orEmpty()
            .equals(
                preferences.getString(publicScheduleHashKey(term), null).orEmpty(),
                ignoreCase = true
            )
    }

    private fun savePublicScheduleCacheFromDownload(
        term: String,
        source: File,
        charsetName: String,
        sha256: String,
        repository: SdauCourseRepository
    ): PublicScheduleIndex {
        val target = publicScheduleFile(term)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        val hierarchy = linkedMapOf<String, MutableMap<String, MutableMap<String, MutableSet<String>>>>()
        var recordCount = 0
        try {
            JsonWriter(OutputStreamWriter(
                GZIPOutputStream(FileOutputStream(temporary)),
                StandardCharsets.UTF_8
            )).use { writer ->
                writer.beginArray()
                InputStreamReader(FileInputStream(source), Charset.forName(charsetName)).use { input ->
                    recordCount = repository.streamPublicCourses(input) { course ->
                        addPublicScheduleIndexEntry(hierarchy, course)
                        writer.beginObject()
                        writer.name("college").value(course.college)
                        writer.name("grade").value(course.grade)
                        writer.name("major").value(course.major)
                        writer.name("className").value(course.className)
                        writer.name("day").value(course.day.toLong())
                        writer.name("startSlot").value(course.startSlot.toLong())
                        writer.name("slotCount").value(course.slotCount.toLong())
                        writer.name("name").value(course.name)
                        writer.name("room").value(course.room)
                        writer.name("teacher").value(course.teacher)
                        writer.name("weeks").value(course.weeks)
                        writer.name("courseCode").value(course.courseCode)
                        writer.endObject()
                    }
                }
                writer.endArray()
            }
            check(recordCount > 0) { "课程镜像中没有可用课程" }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
        val index = immutablePublicScheduleIndex(hierarchy, recordCount, sha256)
        savePublicScheduleIndex(term, index)
        buildPublicScheduleLookupDatabase(term, sha256)
        publicScheduleIndexCache.clear()
        publicScheduleIndexCache[term] = index
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_PUBLIC_SCHEDULE_SYNCED_TERM, term)
            .putString(publicScheduleHashKey(term), sha256)
            .apply()
        return index
    }

    private fun streamCachedPublicSchedule(
        term: String,
        onCourse: (RemotePublicCourse) -> Unit
    ): Int {
        val file = publicScheduleFile(term)
        if (!file.isFile) return 0
        return InputStreamReader(
            GZIPInputStream(FileInputStream(file)),
            StandardCharsets.UTF_8
        ).use { input ->
            SdauCourseRepository().streamPublicCourses(input, onCourse)
        }
    }

    private fun addPublicScheduleIndexEntry(
        hierarchy: MutableMap<String, MutableMap<String, MutableMap<String, MutableSet<String>>>>,
        course: RemotePublicCourse
    ) {
        val grade = publicGradeLabel(course.grade)
        if (course.college.isBlank() || grade.isBlank() ||
            course.major.isBlank() || course.className.isBlank()
        ) return
        hierarchy.getOrPut(course.college) { linkedMapOf() }
            .getOrPut(grade) { linkedMapOf() }
            .getOrPut(course.major) { linkedSetOf() }
            .add(course.className)
    }

    private fun immutablePublicScheduleIndex(
        hierarchy: Map<String, Map<String, Map<String, Set<String>>>>,
        recordCount: Int,
        sourceSha256: String
    ): PublicScheduleIndex {
        val immutableHierarchy = hierarchy.mapValues { (_, grades) ->
            grades.mapValues { (_, majors) ->
                majors.mapValues { (_, classes) -> classes.toSet() }
            }
        }
        return PublicScheduleIndex(immutableHierarchy, recordCount, sourceSha256)
    }

    private fun buildPublicScheduleIndex(term: String, sourceSha256: String): PublicScheduleIndex {
        val hierarchy = linkedMapOf<String, MutableMap<String, MutableMap<String, MutableSet<String>>>>()
        val recordCount = streamCachedPublicSchedule(term) { course ->
            addPublicScheduleIndexEntry(hierarchy, course)
        }
        return immutablePublicScheduleIndex(hierarchy, recordCount, sourceSha256)
    }

    private fun deletePublicScheduleLookupArtifacts(file: File) {
        file.delete()
        File("${file.path}-journal").delete()
        File("${file.path}-wal").delete()
        File("${file.path}-shm").delete()
    }

    private fun buildPublicScheduleLookupDatabase(term: String, sourceSha256: String) {
        val target = publicScheduleLookupFile(term)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        deletePublicScheduleLookupArtifacts(temporary)
        val database = SQLiteDatabase.openOrCreateDatabase(temporary, null)
        try {
            database.rawQuery("PRAGMA journal_mode=DELETE", null).use { cursor ->
                cursor.moveToFirst()
            }
            database.execSQL("PRAGMA synchronous=OFF")
            database.execSQL(
                """CREATE TABLE courses (
                    college TEXT NOT NULL,
                    grade TEXT NOT NULL,
                    major TEXT NOT NULL,
                    class_name TEXT NOT NULL,
                    day INTEGER NOT NULL,
                    start_slot INTEGER NOT NULL,
                    slot_count INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    room TEXT NOT NULL,
                    teacher TEXT NOT NULL,
                    weeks TEXT NOT NULL,
                    course_code TEXT NOT NULL
                )""".trimIndent()
            )
            val insert = database.compileStatement(
                "INSERT INTO courses VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )
            database.beginTransaction()
            val recordCount = streamCachedPublicSchedule(term) { course ->
                insert.clearBindings()
                insert.bindString(1, course.college)
                insert.bindString(2, publicGradeLabel(course.grade))
                insert.bindString(3, course.major)
                insert.bindString(4, course.className)
                insert.bindLong(5, course.day.toLong())
                insert.bindLong(6, course.startSlot.toLong())
                insert.bindLong(7, course.slotCount.toLong())
                insert.bindString(8, course.name)
                insert.bindString(9, course.room)
                insert.bindString(10, course.teacher)
                insert.bindString(11, course.weeks)
                insert.bindString(12, course.courseCode)
                insert.executeInsert()
            }
            check(recordCount > 0) { "本地全校课表中没有可用课程" }
            database.setTransactionSuccessful()
            database.endTransaction()
            insert.close()
            database.execSQL(
                "CREATE INDEX class_lookup ON courses (college, grade, major, class_name)"
            )
            database.close()
            synchronized(publicScheduleLookupLock) {
                deletePublicScheduleLookupArtifacts(target)
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(publicScheduleLookupHashKey(term), sourceSha256)
                .apply()
        } catch (error: Exception) {
            if (database.inTransaction()) database.endTransaction()
            if (database.isOpen) database.close()
            deletePublicScheduleLookupArtifacts(temporary)
            throw error
        }
    }

    private fun savePublicScheduleIndex(term: String, index: PublicScheduleIndex) {
        val target = publicScheduleIndexFile(term)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            JsonWriter(OutputStreamWriter(
                GZIPOutputStream(FileOutputStream(temporary)),
                StandardCharsets.UTF_8
            )).use { writer ->
                writer.beginObject()
                writer.name("sourceSha256").value(index.sourceSha256)
                writer.name("recordCount").value(index.recordCount.toLong())
                writer.name("colleges").beginObject()
                index.hierarchy.toSortedMap().forEach { (college, grades) ->
                    writer.name(college).beginObject()
                    grades.toSortedMap().forEach { (grade, majors) ->
                        writer.name(grade).beginObject()
                        majors.toSortedMap().forEach { (major, classes) ->
                            writer.name(major).beginArray()
                            classes.sorted().forEach(writer::value)
                            writer.endArray()
                        }
                        writer.endObject()
                    }
                    writer.endObject()
                }
                writer.endObject()
                writer.endObject()
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun loadStoredPublicScheduleIndex(term: String): PublicScheduleIndex? {
        publicScheduleIndexCache[term]?.let { return it }
        if (term.isBlank()) return null
        val expectedSha256 = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(publicScheduleHashKey(term), null)
            .orEmpty()
        val file = publicScheduleIndexFile(term)
        if (!file.isFile || file.length() == 0L) return null
        return runCatching {
            val root = InputStreamReader(
                GZIPInputStream(FileInputStream(file)),
                StandardCharsets.UTF_8
            ).use { JSONObject(it.readText()) }
            val sourceSha256 = root.optString("sourceSha256")
            if (expectedSha256.isNotBlank() &&
                !sourceSha256.equals(expectedSha256, ignoreCase = true)
            ) return@runCatching null
            val hierarchy = linkedMapOf<String, Map<String, Map<String, Set<String>>>>()
            val colleges = root.optJSONObject("colleges") ?: JSONObject()
            val collegeNames = colleges.keys()
            while (collegeNames.hasNext()) {
                val college = collegeNames.next()
                val gradeObject = colleges.optJSONObject(college) ?: continue
                val grades = linkedMapOf<String, Map<String, Set<String>>>()
                val gradeNames = gradeObject.keys()
                while (gradeNames.hasNext()) {
                    val grade = gradeNames.next()
                    val majorObject = gradeObject.optJSONObject(grade) ?: continue
                    val majors = linkedMapOf<String, Set<String>>()
                    val majorNames = majorObject.keys()
                    while (majorNames.hasNext()) {
                        val major = majorNames.next()
                        val classes = majorObject.optJSONArray(major) ?: JSONArray()
                        majors[major] = buildSet {
                            for (index in 0 until classes.length()) {
                                classes.optString(index).takeIf(String::isNotBlank)?.let(::add)
                            }
                        }
                    }
                    grades[grade] = majors
                }
                hierarchy[college] = grades
            }
            PublicScheduleIndex(
                hierarchy,
                root.optInt("recordCount"),
                sourceSha256
            ).takeIf { it.recordCount > 0 && it.hierarchy.isNotEmpty() }
        }.getOrNull()?.also {
            publicScheduleIndexCache.clear()
            publicScheduleIndexCache[term] = it
        }
    }

    private fun loadSelectedPublicScheduleCourses(term: String): List<RemotePublicCourse> {
        return runCatching {
            synchronized(publicScheduleLookupLock) {
                SQLiteDatabase.openDatabase(
                    publicScheduleLookupFile(term).path,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                ).use { database ->
                    database.query(
                        "courses",
                        arrayOf(
                            "day", "start_slot", "slot_count", "name",
                            "room", "teacher", "weeks", "course_code"
                        ),
                        "college = ? AND grade = ? AND major = ? AND class_name = ?",
                        arrayOf(
                            publicCollegeSelection,
                            publicGradeSelection,
                            publicMajorSelection,
                            publicClassSelection
                        ),
                        null,
                        null,
                        null
                    ).use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                add(RemotePublicCourse(
                                    publicCollegeSelection,
                                    publicGradeSelection,
                                    publicMajorSelection,
                                    publicClassSelection,
                                    cursor.getInt(0),
                                    cursor.getInt(1),
                                    cursor.getInt(2),
                                    cursor.getString(3),
                                    cursor.getString(4),
                                    cursor.getString(5),
                                    cursor.getString(6),
                                    cursor.getString(7)
                                ))
                            }
                        }.distinct()
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun buildPublicScheduleCourses(
        records: List<RemotePublicCourse>,
        term: String
    ): List<Course> {
        val courses = records.map {
            Course(it.day, it.startSlot, it.slotCount, it.name, it.room, it.teacher,
                COURSE_COLORS.first(), Color.WHITE, it.weeks)
        }
        return recolorCourses(courses, term = term, persistMapping = false)
    }

    private fun startPublicScheduleSyncIfNeeded(term: String) {
        if (term.isBlank()) return
        if (publicSyncRunning) return
        publicSyncRunning = true
        publicSyncExecutor.execute {
            val safeTerm = term.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val downloadFile = File(cacheDir, "public_schedule_$safeTerm.download")
            try {
                val cacheReady = hasPublicScheduleCache(term)
                val knownSha256 = if (cacheReady) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .getString(publicScheduleHashKey(term), null)
                } else {
                    null
                }
                var localArtifactsPrepared = false
                if (cacheReady) {
                    if (loadStoredPublicScheduleIndex(term) == null) {
                        val localIndex = buildPublicScheduleIndex(term, knownSha256.orEmpty())
                        if (localIndex.recordCount > 0 && localIndex.hierarchy.isNotEmpty()) {
                            savePublicScheduleIndex(term, localIndex)
                            publicScheduleIndexCache.clear()
                            publicScheduleIndexCache[term] = localIndex
                            localArtifactsPrepared = true
                        }
                    }
                    if (!hasPublicScheduleLookup(term)) {
                        buildPublicScheduleLookupDatabase(term, knownSha256.orEmpty())
                        localArtifactsPrepared = true
                    }
                    if (localArtifactsPrepared) {
                        runOnUiThread {
                            if (onLoginPage && loginMode == LoginMode.PUBLIC) {
                                swapPage(buildLoginPage(), false, false)
                            }
                        }
                    }
                }
                val repository = SdauCourseRepository()
                val download = repository.downloadPublicScheduleMirror(term, downloadFile)
                val changed = knownSha256.isNullOrBlank() ||
                    !download.sha256.equals(knownSha256, ignoreCase = true)
                if (changed) {
                    savePublicScheduleCacheFromDownload(
                        term,
                        downloadFile,
                        download.charsetName,
                        download.sha256,
                        repository
                    )
                }
                runOnUiThread {
                    publicSyncRunning = false
                    if (!changed) return@runOnUiThread
                    when {
                        onLoginPage && loginMode == LoginMode.PUBLIC -> {
                            swapPage(buildLoginPage(), false, false)
                        }
                        viewingPublicSchedule && publicScheduleTerm == term -> {
                            val selected = loadSelectedPublicScheduleCourses(term)
                            if (selected.isNotEmpty()) {
                                publicScheduleCourses = buildPublicScheduleCourses(selected, term)
                                scheduleGrid?.setCourses(publicScheduleCourses)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { publicSyncRunning = false }
            } finally {
                downloadFile.delete()
            }
        }
    }

    private fun saveCourseCache(
        courses: List<Course>,
        account: String = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_ACCOUNT, "").orEmpty(),
        term: String = selectedTerm()
    ) {
        val imported = courses.filterNot(Course::isCustom)
        val scoped = account.isNotBlank() && term.isNotBlank()
        if (scoped) {
            saveCoursesToPreference(courseCacheKey(account, term), imported)
        }
        if (!scoped || isActiveAcademicSession(account, term)) {
            saveCoursesToPreference(KEY_COURSES, imported)
            notifyCourseDataChanged()
        }
    }

    private fun saveCustomCourseCache(courses: List<Course>) {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val term = selectedTerm()
        val customCourses = courses.filter(Course::isCustom).map { it.copy(isCustom = true) }
        if (account.isNotBlank()) {
            saveCoursesToPreference(customCourseCacheKey(account, term), customCourses)
        }
        saveCoursesToPreference(legacyCustomCourseCacheKey(term), customCourses)
        preferences.edit().putString(customCourseOwnerKey(term), account).apply()
        notifyCourseDataChanged()
    }

    private fun saveCoursesToPreference(key: String, courses: List<Course>) {
        val array = JSONArray()
        courses.forEach { course ->
            array.put(JSONObject().apply {
                put("day", course.day)
                put("startSlot", course.startSlot)
                put("slotCount", course.slotCount)
                put("name", course.name)
                put("room", course.room)
                put("teacher", course.teacher)
                put("weeks", course.weeks)
                put("background", course.background)
                put("foreground", course.foreground)
                put("isCustom", course.isCustom)
            })
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(key, array.toString())
            .apply()
    }

    private fun notifyCourseDataChanged() {
        CourseWidgetProvider.updateAll(this)
        CourseReminderScheduler.scheduleNext(this)
    }

    private fun loadCourseCache(): List<Course> {
        return recolorCourses(loadImportedCourseCache() + loadCustomCourseCache())
    }

    private fun loadImportedCourseCache(): List<Course> {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val term = preferences.getString(KEY_TERM, "").orEmpty()
        if (account.isNotBlank() && term.isNotBlank()) {
            val key = courseCacheKey(account, term)
            if (preferences.contains(key)) return loadCoursesFromPreference(key, false)
            if (preferences.contains(KEY_COURSES)) {
                preferences.getString(KEY_COURSES, null)?.let { legacy ->
                    preferences.edit().putString(key, legacy).apply()
                }
            }
        }
        return loadCoursesFromPreference(KEY_COURSES, false)
    }

    private fun activateCourseCache(account: String, term: String): Boolean {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val key = courseCacheKey(account, term)
        val cached = preferences.getString(key, null) ?: run {
            val legacyBelongsToAccount = preferences.getString(KEY_ACCOUNT, "").orEmpty() == account &&
                preferences.getString(KEY_TERM, "").orEmpty() == term
            if (!legacyBelongsToAccount) return false
            preferences.getString(KEY_COURSES, null)?.also { legacy ->
                preferences.edit().putString(key, legacy).apply()
            } ?: return false
        }
        preferences.edit().putString(KEY_COURSES, cached).apply()
        activateCustomCourseCache(account, term)
        return true
    }

    private fun hasCourseCache(account: String, term: String): Boolean {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (preferences.contains(courseCacheKey(account, term))) return true
        return preferences.getString(KEY_ACCOUNT, "").orEmpty() == account &&
            preferences.getString(KEY_TERM, "").orEmpty() == term &&
            preferences.contains(KEY_COURSES)
    }

    private fun isActiveAcademicSession(account: String, term: String? = null): Boolean {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (preferences.getString(KEY_ACCOUNT, "").orEmpty() != account) return false
        return term == null || preferences.getString(KEY_TERM, "").orEmpty() == term
    }

    private fun courseCacheKey(account: String, term: String): String {
        val safeAccount = account.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeTerm = term.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${KEY_COURSES_PREFIX}_${safeAccount}_$safeTerm"
    }

    private fun studentNameCacheKey(account: String): String {
        val safeAccount = account.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${KEY_STUDENT_NAME_PREFIX}_$safeAccount"
    }

    private fun passwordCacheKey(account: String): String {
        val safeAccount = account.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${KEY_PASSWORD_PREFIX}_$safeAccount"
    }

    private fun savePasswordCache(account: String, password: String) {
        if (account.isBlank() || password.isBlank()) return
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(passwordCacheKey(account), password)
            .apply()
    }

    private fun cachedPassword(account: String): String? {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return preferences.getString(passwordCacheKey(account), null)
            ?.takeIf { it.isNotBlank() }
            ?: preferences.getString(KEY_PASSWORD, null)
                ?.takeIf {
                    it.isNotBlank() &&
                        preferences.getString(KEY_ACCOUNT, "").orEmpty() == account
                }
    }

    private fun saveStudentNameCache(account: String, name: String) {
        if (account.isBlank()) return
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(studentNameCacheKey(account), name)
            .apply()
    }

    private fun cachedStudentName(account: String): String {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return preferences.getString(studentNameCacheKey(account), null)
            ?: preferences.getString(KEY_STUDENT_NAME, "").orEmpty()
                .takeIf { preferences.getString(KEY_ACCOUNT, "").orEmpty() == account }
            ?: ""
    }

    private fun loadCustomCourseCache(): List<Course> {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val term = selectedTerm()
        if (account.isBlank()) return emptyList()
        activateCustomCourseCache(account, term)
        return loadCoursesFromPreference(customCourseCacheKey(account, term), true)
    }

    private fun activateCustomCourseCache(account: String, term: String) {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val accountKey = customCourseCacheKey(account, term)
        val legacyKey = legacyCustomCourseCacheKey(term)
        val ownerKey = customCourseOwnerKey(term)
        val cached = preferences.getString(accountKey, null)
        if (cached != null) {
            preferences.edit()
                .putString(legacyKey, cached)
                .putString(ownerKey, account)
                .apply()
            return
        }

        val legacy = preferences.getString(legacyKey, null)
        val legacyOwner = preferences.getString(ownerKey, null)
        val legacyBelongsToAccount = legacyOwner == account ||
            (legacyOwner == null && preferences.getString(KEY_ACCOUNT, "").orEmpty() == account)
        if (legacy != null && legacyBelongsToAccount) {
            preferences.edit()
                .putString(accountKey, legacy)
                .putString(ownerKey, account)
                .apply()
        } else {
            preferences.edit()
                .putString(accountKey, "[]")
                .putString(legacyKey, "[]")
                .putString(ownerKey, account)
                .apply()
        }
    }

    private fun customCourseCacheKey(account: String, term: String): String {
        val safeAccount = account.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeTerm = term.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${KEY_CUSTOM_COURSES_PREFIX}_${safeAccount}_$safeTerm"
    }

    private fun legacyCustomCourseCacheKey(term: String): String =
        "${KEY_CUSTOM_COURSES_PREFIX}_${term.replace(Regex("[^A-Za-z0-9_-]"), "_")}" 

    private fun customCourseOwnerKey(term: String): String =
        "${KEY_CUSTOM_COURSES_OWNER_PREFIX}_${term.replace(Regex("[^A-Za-z0-9_-]"), "_")}"

    private fun loadCoursesFromPreference(key: String, isCustom: Boolean): List<Course> {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(Course(
                        item.getInt("day"), item.getInt("startSlot"), item.getInt("slotCount"),
                        item.getString("name"), item.getString("room"), item.getString("teacher"),
                        item.getInt("background"), item.getInt("foreground"), item.optString("weeks", ""),
                        isCustom = isCustom
                    ))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveExamCache(
        term: String,
        records: List<RemoteExam>,
        account: String = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_ACCOUNT, "").orEmpty()
    ) {
        val array = JSONArray()
        records.forEach { exam ->
            array.put(JSONObject().apply {
                put("courseName", exam.courseName)
                put("examWeek", exam.examWeek)
                put("examWeekday", exam.examWeekday)
                put("examSessions", exam.examSessions)
                put("classroom", exam.classroom)
            })
        }
        val payload = JSONObject().apply {
            put("account", account)
            put("term", term)
            put("records", array)
        }
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val raw = payload.toString()
        val edit = preferences.edit()
        if (account.isNotBlank()) edit.putString(examCacheKey(account, term), raw)
        if (account.isBlank() || isActiveAcademicSession(account, term)) {
            edit.putString(KEY_EXAMS, raw)
        }
        edit.apply()
    }

    private fun loadExamCache(): ExamCache? {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val term = selectedTerm()
        if (account.isBlank()) return null
        val key = examCacheKey(account, term)
        val raw = preferences.getString(key, null) ?: run {
            val legacy = preferences.getString(KEY_EXAMS, null) ?: return null
            val legacyPayload = runCatching { JSONObject(legacy) }.getOrNull() ?: return null
            val legacyAccount = legacyPayload.optString("account")
            if (
                legacyPayload.optString("term") != term ||
                (legacyAccount.isNotBlank() && legacyAccount != account)
            ) return null
            preferences.edit().putString(key, legacy).apply()
            legacy
        }
        return runCatching {
            val payload = JSONObject(raw)
            val rows = payload.optJSONArray("records") ?: JSONArray()
            ExamCache(
                term = payload.optString("term"),
                records = buildList {
                    for (index in 0 until rows.length()) {
                        val row = rows.optJSONObject(index) ?: continue
                        add(RemoteExam(
                            courseName = row.optString("courseName"),
                            examWeek = row.optString("examWeek"),
                            examWeekday = row.optString("examWeekday"),
                            examSessions = row.optString("examSessions"),
                            classroom = row.optString("classroom", "-")
                        ))
                    }
                }
            )
        }.getOrNull()
    }

    private fun saveScoreCache(
        result: RemoteScoreResult,
        account: String = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_ACCOUNT, "").orEmpty()
    ) {
        val records = JSONArray()
        result.records.forEach { score ->
            records.put(JSONObject().apply {
                put("courseCode", score.courseCode)
                put("courseName", score.courseName)
                put("credit", score.credit)
                put("score", score.score)
                put("gpa", score.gpa)
                put("studentIdRaw", score.studentIdRaw)
                put("teachingTaskId", score.teachingTaskId)
                put("scoreRecordId", score.scoreRecordId)
            })
        }
        val payload = JSONObject().apply {
            put("account", account)
            put("term", result.term)
            put("statsScope", SCORE_STATS_SCOPE)
            put("averageScore", result.averageScore)
            put("averageCreditGpa", result.averageCreditGpa)
            put("totalCredits", result.totalCredits)
            put("records", records)
        }
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val raw = payload.toString()
        val edit = preferences.edit()
        if (account.isNotBlank()) edit.putString(scoreCacheKey(account, result.term), raw)
        if (
            account.isBlank() ||
            (isActiveAcademicSession(account) && selectedScoreTerm() == result.term)
        ) {
            edit.putString(KEY_SCORES, raw)
        }
        edit.apply()
    }

    private fun saveStudentName(account: String, name: String) {
        val normalized = name.trim()
        saveStudentNameCache(account, normalized)
        if (isActiveAcademicSession(account)) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_STUDENT_NAME, normalized)
                .apply()
        }
    }

    private fun loadScoreCache(): RemoteScoreResult? {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val account = preferences.getString(KEY_ACCOUNT, "").orEmpty()
        val term = selectedScoreTerm()
        if (account.isBlank()) return null
        val key = scoreCacheKey(account, term)
        val raw = preferences.getString(key, null) ?: run {
            val legacy = preferences.getString(KEY_SCORES, null) ?: return null
            val legacyPayload = runCatching { JSONObject(legacy) }.getOrNull() ?: return null
            val legacyAccount = legacyPayload.optString("account")
            if (
                legacyPayload.optString("term") != term ||
                (legacyAccount.isNotBlank() && legacyAccount != account)
            ) return null
            preferences.edit().putString(key, legacy).apply()
            legacy
        }
        return runCatching {
            val payload = JSONObject(raw)
            val records = payload.optJSONArray("records") ?: JSONArray()
            val result = RemoteScoreResult(
                term = payload.optString("term"),
                records = buildList {
                    for (index in 0 until records.length()) {
                        val row = records.optJSONObject(index) ?: continue
                        add(RemoteScore(
                            courseCode = row.optString("courseCode"),
                            courseName = row.optString("courseName"),
                            credit = row.optString("credit"),
                            score = row.optString("score", "-"),
                            gpa = row.optString("gpa", "-"),
                            studentIdRaw = row.optString("studentIdRaw"),
                            teachingTaskId = row.optString("teachingTaskId"),
                            scoreRecordId = row.optString("scoreRecordId")
                        ))
                    }
                },
                averageScore = payload.optString("averageScore", "-"),
                averageCreditGpa = payload.optString("averageCreditGpa", "-"),
                totalCredits = payload.optString("totalCredits", "-")
            )
            if (payload.optString("statsScope") == SCORE_STATS_SCOPE) {
                result.copy(records = applyCalculatedGradePoints(result.records))
            } else {
                result.copy(
                    records = applyCalculatedGradePoints(result.records),
                    averageScore = "-",
                    averageCreditGpa = "-"
                )
            }
        }.getOrNull()
    }

    private fun examCacheKey(account: String, term: String): String {
        val safeAccount = account.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeTerm = term.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${KEY_EXAMS_PREFIX}_${safeAccount}_$safeTerm"
    }

    private fun scoreCacheKey(account: String, term: String): String {
        val safeAccount = account.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeTerm = term.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${KEY_SCORES_PREFIX}_${safeAccount}_$safeTerm"
    }

    private fun inputBox(hint: String) = TextInputLayout(this).apply {
        this.hint = hint
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        setBoxCornerRadii(dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat())
        boxStrokeWidth = dp(1)
        boxStrokeWidthFocused = dp(2)
        setBoxStrokeColorStateList(inputStrokeColors())
        defaultHintTextColor = ColorStateList.valueOf(activeThemeColors.secondaryText)
        hintTextColor = ColorStateList.valueOf(activeThemeColors.primary)
        setErrorTextColor(ColorStateList.valueOf(activeThemeColors.error))
    }

    private fun selectorInputBox(hint: String) = inputBox(hint).apply {
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_NONE
        setBoxBackgroundColor(Color.TRANSPARENT)
        setBoxCornerRadii(0f, 0f, 0f, 0f)
        boxStrokeWidth = 0
        boxStrokeWidthFocused = 0
    }

    private fun selectorFieldBackground(enabled: Boolean): LayerDrawable {
        val layers = LayerDrawable(arrayOf(
            ColorDrawable(Color.TRANSPARENT),
            ColorDrawable(if (enabled) activeThemeColors.outline else activeThemeColors.disabledOutline)
        ))
        layers.setLayerGravity(1, Gravity.BOTTOM)
        layers.setLayerWidth(1, -1)
        layers.setLayerHeight(1, dp(1))
        return layers
    }

    private fun input(inputType: Int) = TextInputEditText(this).apply {
        setSingleLine(true); textSize = 16f; setTextColor(activeThemeColors.primaryText); setHintTextColor(activeThemeColors.secondaryText)
        this.inputType = inputType; minHeight = dp(58); setPadding(dp(16), 0, dp(16), 0)
    }

    private fun inputStrokeColors() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(activeThemeColors.primary, activeThemeColors.disabledOutline, activeThemeColors.outline)
    )

    private fun surfaceCard(radius: Float) = MaterialCardView(this).apply {
        this.radius = radius
        cardElevation = if (activeThemeColors.isDark) 0f else dp(2).toFloat()
        setCardBackgroundColor(activeThemeColors.surface)
        if (activeThemeColors.isDark) {
            strokeWidth = 0
        } else {
            setStrokeColor(activeThemeColors.outline)
            strokeWidth = dp(1)
        }
    }

    private fun text(value: String, size: Float, color: Int, style: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setTypeface(Typeface.DEFAULT, style); includeFontPadding = false
    }

    private fun fixedAdaptiveText(
        value: String,
        maximumDp: Float,
        minimumDp: Float,
        color: Int,
        style: Int
    ) = TextView(this).apply {
        text = value
        setTextColor(color)
        setTypeface(Typeface.DEFAULT, style)
        includeFontPadding = false
        maxLines = 1
        setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(maximumDp).toFloat())
        setAutoSizeTextTypeUniformWithConfiguration(
            minimumDp.toInt(), maximumDp.toInt(), 1, TypedValue.COMPLEX_UNIT_DIP
        )
    }

    private fun hideKeyboard() {
        val focused = currentFocus ?: return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }

    @Deprecated("Use OnBackInvokedDispatcher on newer Android versions")
    override fun onBackPressed() {
        if (forceUpdateActive) return
        if (backgroundEditorOverlay != null) {
            if (backgroundEditorOverlay?.isApplyingBackground() == true) return
            hideBackgroundEditor(
                deletePendingSource = backgroundEditorPendingSource != null,
                restoreScheduleBackground = true
            )
            return
        }
        if (emptyRoomFilterOverlay != null) {
            hideEmptyRoomFilterPicker()
            return
        }
        if (publicOptionOverlay != null) {
            hidePublicOptionPicker()
            return
        }
        if (appearanceOverlay != null) {
            hideAppearanceDialog()
            return
        }
        if (refreshScheduleConfirmOverlay != null) {
            hideRefreshScheduleConfirmation()
            return
        }
        if (scoreDetailOverlay != null) {
            hideScoreDetail()
            return
        }
        if (actionMenuOverlay != null) {
            hideActionMenu()
            return
        }
        if (updateOverlay != null) {
            hideUpdateDialog()
            return
        }
        if (onLoginPage && loginMode == LoginMode.PUBLIC) {
            academicSessionGeneration++
            loginMode = LoginMode.PERSONAL
            swapPage(buildLoginPage(), false, true)
            return
        }
        super.onBackPressed()
    }

    private fun verticalLayout() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun horizontalLayout() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun matchParentParams() = FrameLayout.LayoutParams(-1, -1)
    private fun matchWrapParams() = LinearLayout.LayoutParams(-1, -2)
    private fun spacedParams(bottom: Int) = matchWrapParams().apply { bottomMargin = bottom }
    private fun dp(value: Number) = (value.toFloat() * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    /**
     * 使用 Catmull-Rom 曲线穿过用户给出的五个色点，再采样为 65 个颜色。
     * 相比直接做五段线性渐变，色彩变化的一阶导数连续，配合抖动绘制可显著减少色带。
     */
    private inner class SilkyGradientDrawable : Drawable() {
        private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private val sampleCount = 65
        private val sampledColors = IntArray(sampleCount) { sampleIndex ->
            val position = sampleIndex / (sampleCount - 1f)
            sampleSmoothGradient(position)
        }
        private val sampledPositions = FloatArray(sampleCount) { it / (sampleCount - 1f) }

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            super.onBoundsChange(bounds)
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            gradientPaint.shader = LinearGradient(
                bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(),
                sampledColors, sampledPositions, Shader.TileMode.CLAMP
            )
        }

        override fun draw(canvas: Canvas) {
            canvas.drawRect(bounds, gradientPaint)
        }

        override fun setAlpha(alpha: Int) {
            gradientPaint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            gradientPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE

        private fun sampleSmoothGradient(position: Float): Int {
            val gradientColors = activeThemeColors.gradient
            val lastSegment = gradientColors.size - 2
            val scaled = position.coerceIn(0f, 1f) * (gradientColors.size - 1)
            val segment = scaled.toInt().coerceIn(0, lastSegment)
            val t = (scaled - segment).coerceIn(0f, 1f)
            val p0 = gradientColors[(segment - 1).coerceAtLeast(0)]
            val p1 = gradientColors[segment]
            val p2 = gradientColors[segment + 1]
            val p3 = gradientColors[(segment + 2).coerceAtMost(gradientColors.lastIndex)]
            fun channel(shift: Int): Int {
                val a = (p0 shr shift) and 0xff
                val b = (p1 shr shift) and 0xff
                val c = (p2 shr shift) and 0xff
                val d = (p3 shr shift) and 0xff
                val t2 = t * t
                val t3 = t2 * t
                return (.5f * (2f * b + (-a + c) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (-a + 3f * b - 3f * c + d) * t3))
                    .toInt().coerceIn(0, 255)
            }
            return Color.rgb(channel(16), channel(8), channel(0))
        }
    }

    private enum class EmptyAcademicState { EXAMS, GRADES, ROOMS }

    private inner class AcademicEmptyIllustration(
        context: Context,
        private val type: EmptyAcademicState
    ) : View(context) {
        private val illustrationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val illustrationRect = RectF()
        private val illustrationPath = Path()

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = when (type) {
                EmptyAcademicState.EXAMS -> "暂无考试安排"
                EmptyAcademicState.GRADES -> "暂无课程成绩"
                EmptyAcademicState.ROOMS -> "暂无空闲教室"
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.shader = null
            when (type) {
                EmptyAcademicState.EXAMS -> drawEmptyExam(canvas, cx, cy)
                EmptyAcademicState.GRADES -> drawEmptyGrades(canvas, cx, cy)
                EmptyAcademicState.ROOMS -> drawEmptyRooms(canvas, cx, cy, true)
            }
        }

        private fun drawEmptyExam(canvas: Canvas, cx: Float, cy: Float) {
            illustrationRect.set(
                cx - dp(39f), cy - dp(39f),
                cx + dp(31f), cy + dp(35f)
            )
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.argb(28, 55, 72, 125)
            canvas.drawRoundRect(
                illustrationRect.left + dp(2f), illustrationRect.top + dp(4f),
                illustrationRect.right + dp(2f), illustrationRect.bottom + dp(4f),
                dp(14f).toFloat(), dp(14f).toFloat(), illustrationPaint
            )
            illustrationPaint.color = Color.argb(205, 252, 253, 255)
            canvas.drawRoundRect(illustrationRect, dp(14f).toFloat(), dp(14f).toFloat(), illustrationPaint)
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(1.2f).toFloat()
            illustrationPaint.color = Color.argb(100, 126, 139, 179)
            canvas.drawRoundRect(illustrationRect, dp(14f).toFloat(), dp(14f).toFloat(), illustrationPaint)
            canvas.drawLine(
                illustrationRect.left + dp(8f), illustrationRect.top + dp(22f),
                illustrationRect.right - dp(8f), illustrationRect.top + dp(22f), illustrationPaint
            )
            illustrationPaint.strokeWidth = dp(3f).toFloat()
            illustrationPaint.color = Color.rgb(131, 140, 199)
            canvas.drawLine(cx - dp(20f), cy - dp(47f), cx - dp(20f), cy - dp(33f), illustrationPaint)
            canvas.drawLine(cx + dp(12f), cy - dp(47f), cx + dp(12f), cy - dp(33f), illustrationPaint)

            illustrationPaint.style = Paint.Style.FILL
            val dotColors = intArrayOf(
                Color.rgb(245, 108, 126), Color.rgb(131, 140, 199), Color.rgb(130, 173, 247),
                Color.rgb(131, 140, 199), Color.rgb(130, 173, 247), Color.rgb(245, 108, 126)
            )
            var colorIndex = 0
            for (row in 0..1) {
                for (column in 0..2) {
                    illustrationPaint.color = Color.argb(145, Color.red(dotColors[colorIndex]), Color.green(dotColors[colorIndex]), Color.blue(dotColors[colorIndex]))
                    canvas.drawCircle(
                        illustrationRect.left + dp(17f + column * 17f),
                        illustrationRect.top + dp(36f + row * 16f),
                        dp(3.2f).toFloat(), illustrationPaint
                    )
                    colorIndex++
                }
            }

            val clockX = cx + dp(32f)
            val clockY = cy + dp(29f)
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.rgb(248, 250, 255)
            canvas.drawCircle(clockX, clockY, dp(19f).toFloat(), illustrationPaint)
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(2f).toFloat()
            illustrationPaint.color = Color.rgb(131, 140, 199)
            canvas.drawCircle(clockX, clockY, dp(16f).toFloat(), illustrationPaint)
            canvas.drawLine(clockX, clockY, clockX, clockY - dp(8f), illustrationPaint)
            canvas.drawLine(clockX, clockY, clockX + dp(7f), clockY + dp(4f), illustrationPaint)
        }

        private fun drawEmptyGrades(canvas: Canvas, cx: Float, cy: Float) {
            val save = canvas.save()
            canvas.rotate(-4f, cx, cy)
            illustrationRect.set(cx - dp(37f), cy - dp(43f), cx + dp(35f), cy + dp(39f))
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.argb(27, 55, 72, 125)
            canvas.drawRoundRect(
                illustrationRect.left + dp(3f), illustrationRect.top + dp(4f),
                illustrationRect.right + dp(3f), illustrationRect.bottom + dp(4f),
                dp(13f).toFloat(), dp(13f).toFloat(), illustrationPaint
            )
            illustrationPaint.color = Color.argb(210, 252, 253, 255)
            canvas.drawRoundRect(illustrationRect, dp(13f).toFloat(), dp(13f).toFloat(), illustrationPaint)
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(1.2f).toFloat()
            illustrationPaint.color = Color.argb(95, 126, 139, 179)
            canvas.drawRoundRect(illustrationRect, dp(13f).toFloat(), dp(13f).toFloat(), illustrationPaint)

            illustrationPaint.strokeWidth = dp(3f).toFloat()
            illustrationPaint.color = Color.rgb(131, 140, 199)
            canvas.drawLine(cx - dp(23f), cy - dp(25f), cx + dp(18f), cy - dp(25f), illustrationPaint)
            illustrationPaint.strokeWidth = dp(2f).toFloat()
            illustrationPaint.color = Color.argb(100, 105, 113, 132)
            canvas.drawLine(cx - dp(23f), cy - dp(12f), cx + dp(8f), cy - dp(12f), illustrationPaint)
            canvas.drawLine(cx - dp(23f), cy, cx + dp(15f), cy, illustrationPaint)

            illustrationPaint.style = Paint.Style.FILL
            val baseY = cy + dp(25f)
            illustrationPaint.color = Color.rgb(245, 108, 126)
            canvas.drawRoundRect(cx - dp(21f), baseY - dp(12f), cx - dp(12f), baseY, dp(3f).toFloat(), dp(3f).toFloat(), illustrationPaint)
            illustrationPaint.color = Color.rgb(131, 140, 199)
            canvas.drawRoundRect(cx - dp(6f), baseY - dp(20f), cx + dp(3f), baseY, dp(3f).toFloat(), dp(3f).toFloat(), illustrationPaint)
            illustrationPaint.color = Color.rgb(130, 173, 247)
            canvas.drawRoundRect(cx + dp(9f), baseY - dp(28f), cx + dp(18f), baseY, dp(3f).toFloat(), dp(3f).toFloat(), illustrationPaint)
            canvas.restoreToCount(save)

            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.rgb(245, 108, 126)
            canvas.drawCircle(cx + dp(39f), cy - dp(27f), dp(8f).toFloat(), illustrationPaint)
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(1.8f).toFloat()
            illustrationPaint.color = Color.WHITE
            canvas.drawLine(cx + dp(35f), cy - dp(27f), cx + dp(38f), cy - dp(24f), illustrationPaint)
            canvas.drawLine(cx + dp(38f), cy - dp(24f), cx + dp(44f), cy - dp(31f), illustrationPaint)
        }

        private fun drawEmptyRooms(canvas: Canvas, cx: Float, cy: Float, showNoRoomBadge: Boolean) {
            illustrationRect.set(cx - dp(39f), cy - dp(40f), cx + dp(32f), cy + dp(39f))
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.argb(27, 55, 72, 125)
            canvas.drawRoundRect(
                illustrationRect.left + dp(3f), illustrationRect.top + dp(4f),
                illustrationRect.right + dp(3f), illustrationRect.bottom + dp(4f),
                dp(13f).toFloat(), dp(13f).toFloat(), illustrationPaint
            )
            illustrationPaint.color = Color.argb(210, 252, 253, 255)
            canvas.drawRoundRect(illustrationRect, dp(13f).toFloat(), dp(13f).toFloat(), illustrationPaint)
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(1.2f).toFloat()
            illustrationPaint.color = Color.argb(95, 126, 139, 179)
            canvas.drawRoundRect(illustrationRect, dp(13f).toFloat(), dp(13f).toFloat(), illustrationPaint)

            // 顶部的教室门牌延续考试、成绩空状态的彩色信息条语言。
            illustrationPaint.style = Paint.Style.FILL
            illustrationPaint.color = Color.rgb(131, 140, 199)
            canvas.drawRoundRect(
                illustrationRect.left + dp(12f), illustrationRect.top + dp(10f),
                illustrationRect.right - dp(12f), illustrationRect.top + dp(23f),
                dp(4f).toFloat(), dp(4f).toFloat(), illustrationPaint
            )
            illustrationPaint.color = Color.argb(205, 255, 255, 255)
            canvas.drawRoundRect(
                illustrationRect.left + dp(19f), illustrationRect.top + dp(15f),
                illustrationRect.right - dp(19f), illustrationRect.top + dp(18f),
                dp(1.5f).toFloat(), dp(1.5f).toFloat(), illustrationPaint
            )

            val doorLeft = cx - dp(17f)
            val doorTop = cy - dp(8f)
            val doorRight = cx + dp(13f)
            val doorBottom = illustrationRect.bottom
            illustrationPaint.style = Paint.Style.STROKE
            illustrationPaint.strokeWidth = dp(2.2f).toFloat()
            illustrationPaint.color = Color.rgb(131, 140, 199)
            illustrationPath.reset()
            illustrationPath.moveTo(doorLeft, doorBottom)
            illustrationPath.lineTo(doorLeft, doorTop)
            illustrationPath.lineTo(doorRight, doorTop)
            illustrationPath.lineTo(doorRight, doorBottom)
            canvas.drawPath(illustrationPath, illustrationPaint)

            if (showNoRoomBadge) {
                illustrationPaint.style = Paint.Style.FILL
                illustrationPaint.color = Color.rgb(221, 225, 245)
                canvas.drawRoundRect(
                    doorLeft + dp(5f), doorTop + dp(5f),
                    doorRight - dp(5f), doorBottom,
                    dp(3f).toFloat(), dp(3f).toFloat(), illustrationPaint
                )
                illustrationPaint.color = Color.rgb(105, 205, 185)
                canvas.drawRoundRect(
                    doorLeft + dp(9f), doorTop + dp(10f),
                    doorRight - dp(9f), doorTop + dp(21f),
                    dp(2.5f).toFloat(), dp(2.5f).toFloat(), illustrationPaint
                )
                illustrationPaint.color = Color.rgb(245, 108, 126)
                canvas.drawCircle(doorRight - dp(9f), cy + dp(16f), dp(2.2f).toFloat(), illustrationPaint)
            } else {
                illustrationPath.reset()
                illustrationPath.moveTo(doorLeft + dp(5f), doorTop + dp(5f))
                illustrationPath.lineTo(doorRight + dp(9f), doorTop + dp(1f))
                illustrationPath.lineTo(doorRight + dp(9f), doorBottom - dp(1f))
                illustrationPath.lineTo(doorLeft + dp(5f), doorBottom - dp(6f))
                illustrationPath.close()
                illustrationPaint.style = Paint.Style.FILL
                illustrationPaint.color = Color.rgb(176, 193, 238)
                canvas.drawPath(illustrationPath, illustrationPaint)
                illustrationPaint.style = Paint.Style.STROKE
                illustrationPaint.strokeWidth = dp(1.7f).toFloat()
                illustrationPaint.color = Color.rgb(131, 140, 199)
                canvas.drawPath(illustrationPath, illustrationPaint)
                illustrationPaint.style = Paint.Style.FILL
                illustrationPaint.color = Color.rgb(245, 108, 126)
                canvas.drawCircle(doorRight + dp(2f), cy + dp(15f), dp(2.2f).toFloat(), illustrationPaint)
            }

            val badgeX = cx + dp(36f)
            val badgeY = cy + dp(25f)
            if (showNoRoomBadge) {
                illustrationPaint.style = Paint.Style.FILL
                illustrationPaint.color = Color.rgb(245, 108, 126)
                canvas.drawCircle(badgeX, badgeY, dp(14f).toFloat(), illustrationPaint)
                illustrationPaint.style = Paint.Style.STROKE
                illustrationPaint.strokeWidth = dp(2f).toFloat()
                illustrationPaint.color = Color.WHITE
                canvas.drawCircle(badgeX, badgeY, dp(7f).toFloat(), illustrationPaint)
                canvas.drawLine(badgeX, badgeY, badgeX, badgeY - dp(4f), illustrationPaint)
                canvas.drawLine(badgeX, badgeY, badgeX + dp(4f), badgeY + dp(2f), illustrationPaint)
            } else {
                illustrationPaint.style = Paint.Style.FILL
                illustrationPaint.color = Color.rgb(248, 250, 255)
                canvas.drawCircle(badgeX, badgeY, dp(15f).toFloat(), illustrationPaint)
                illustrationPaint.style = Paint.Style.STROKE
                illustrationPaint.strokeWidth = dp(2.2f).toFloat()
                illustrationPaint.color = Color.rgb(91, 108, 190)
                canvas.drawCircle(badgeX - dp(2f), badgeY - dp(2f), dp(8f).toFloat(), illustrationPaint)
                canvas.drawLine(
                    badgeX + dp(4f), badgeY + dp(4f),
                    badgeX + dp(10f), badgeY + dp(10f), illustrationPaint
                )
            }
        }
    }

    private data class Course(
        val day: Int,
        val startSlot: Int,
        val slotCount: Int,
        val name: String,
        val room: String,
        val teacher: String,
        val background: Int,
        val foreground: Int,
        val weeks: String = "",
        val isCustom: Boolean = false
    )

    private data class CoursePlacement(
        val course: Course,
        val column: Int,
        val columnCount: Int
    )

    /**
     * 登录卡片的横向手势协调器。顶部标题和导航保持原位，仅让表单跟随手指移动；
     * 导航胶囊的位置通过 [onPositionChanged] 使用同一份进度同步更新。
     */
    private inner class LoginSwipeLayout(
        context: Context,
        initialMode: LoginMode,
        private val onPositionChanged: (Float) -> Unit,
        private val createModeForm: (LoginMode) -> View,
        private val onModeSettled: (LoginMode) -> Unit
    ) : LinearLayout(context) {
        private val formHost = FrameLayout(context).apply {
            clipChildren = true
            clipToPadding = true
        }
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
        private var mode = initialMode
        private var downX = 0f
        private var downY = 0f
        private var dragProgress = 0f
        private var dragging = false
        private var velocityTracker: VelocityTracker? = null
        private var transitionAnimator: ValueAnimator? = null

        init {
            orientation = VERTICAL
        }

        fun attachInitialForm(form: View) {
            formHost.removeAllViews()
            formHost.addView(form, FrameLayout.LayoutParams(-1, -2))
            addView(formHost, LinearLayout.LayoutParams(-1, -2))
        }

        fun animateToMode(targetMode: LoginMode) {
            if (targetMode == mode || transitionAnimator != null || dragging) return
            completeTransition(targetMode, 0f)
        }

        fun updateFromModeToggle(position: Float) {
            if (transitionAnimator != null) return
            val direction = if (mode == LoginMode.PERSONAL) 1f else -1f
            val basePosition = modeIndex(mode)
            dragProgress = ((position.coerceIn(0f, 1f) - basePosition) * direction)
                .coerceIn(0f, .98f)
            // A simple tap also reports an initial zero-distance drag from Compose.
            // Do not lock the form transition unless the indicator actually moved.
            dragging = dragProgress > .001f
            val width = formHost.width.coerceAtLeast(1).toFloat()
            val current = formHost.getChildAt(0) ?: return
            current.translationX = -direction * width * dragProgress
            current.alpha = 1f - .12f * dragProgress
            onPositionChanged(basePosition + direction * dragProgress)
        }

        fun finishModeToggleDrag(position: Float, velocityX: Float) {
            if (transitionAnimator != null) return
            val direction = if (mode == LoginMode.PERSONAL) 1f else -1f
            val basePosition = modeIndex(mode)
            dragProgress = ((position.coerceIn(0f, 1f) - basePosition) * direction)
                .coerceIn(0f, .98f)
            dragging = false
            val target = if (mode == LoginMode.PERSONAL) LoginMode.PUBLIC else LoginMode.PERSONAL
            if (dragProgress >= .5f) {
                completeTransition(target, dragProgress)
            } else {
                cancelDrag()
            }
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            if (transitionAnimator != null) return true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    dragProgress = 0f
                    dragging = false
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val movingTowardOtherPage =
                        (mode == LoginMode.PERSONAL && dx < 0f) ||
                            (mode == LoginMode.PUBLIC && dx > 0f)
                    if (movingTowardOtherPage &&
                        abs(dx) > touchSlop &&
                        abs(dx) > abs(dy) * 1.15f
                    ) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        updateDrag(dx)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> recycleVelocityTracker()
            }
            return super.onInterceptTouchEvent(event)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (transitionAnimator != null) return true
            velocityTracker?.addMovement(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) updateDrag(event.x - downX)
                    return dragging
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        recycleVelocityTracker()
                        return false
                    }
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityX = velocityTracker?.xVelocity ?: 0f
                    val direction = if (mode == LoginMode.PERSONAL) 1 else -1
                    val flingTowardTarget =
                        abs(velocityX) >= minimumFlingVelocity * 4f &&
                            velocityX * direction < 0f
                    val shouldCommit = dragProgress >= .26f || flingTowardTarget
                    val target = if (mode == LoginMode.PERSONAL) LoginMode.PUBLIC else LoginMode.PERSONAL
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    recycleVelocityTracker()
                    if (shouldCommit) completeTransition(target, dragProgress) else cancelDrag()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) cancelDrag()
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    recycleVelocityTracker()
                    return true
                }
            }
            return true
        }

        private fun updateDrag(dx: Float) {
            val width = formHost.width.coerceAtLeast(1).toFloat()
            val direction = if (mode == LoginMode.PERSONAL) 1 else -1
            dragProgress = (-dx * direction / width).coerceIn(0f, .98f)
            val current = formHost.getChildAt(0) ?: return
            current.translationX = -direction * width * dragProgress
            current.alpha = 1f - .12f * dragProgress
            onPositionChanged(modeIndex(mode) + direction * dragProgress)
        }

        private fun cancelDrag() {
            val current = formHost.getChildAt(0) ?: return
            val start = dragProgress
            val direction = if (mode == LoginMode.PERSONAL) 1 else -1
            val width = formHost.width.coerceAtLeast(1).toFloat()
            transitionAnimator = ValueAnimator.ofFloat(start, 0f).apply {
                duration = (120L + 140L * start).toLong()
                interpolator = PathInterpolator(.2f, .78f, .2f, 1f)
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    current.translationX = -direction * width * progress
                    current.alpha = 1f - .12f * progress
                    onPositionChanged(modeIndex(mode) + direction * progress)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        current.translationX = 0f
                        current.alpha = 1f
                        dragProgress = 0f
                        transitionAnimator = null
                        onModeSettled(mode)
                    }
                })
                start()
            }
        }

        private fun completeTransition(targetMode: LoginMode, startProgress: Float) {
            val previous = formHost.getChildAt(0) ?: return
            val oldMode = mode
            val direction = modeIndex(targetMode) - modeIndex(oldMode)
            if (direction == 0f) return
            val width = formHost.width.coerceAtLeast(1)
            val startHeight = formHost.height
            val next = createModeForm(targetMode)
            // ComposeView needs an attached window to obtain its windowRecomposer.
            // Attach the new form before pre-measuring it for the height animation.
            formHost.addView(next, FrameLayout.LayoutParams(-1, -2))
            next.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val targetHeight = next.measuredHeight
            formHost.layoutParams = formHost.layoutParams.apply { height = startHeight }

            fun applyProgress(progress: Float) {
                previous.translationX = -direction * width * progress
                previous.alpha = 1f - .12f * progress
                next.translationX = direction * width * (1f - progress)
                next.alpha = .72f + .28f * progress
                formHost.layoutParams = formHost.layoutParams.apply {
                    height = (startHeight + (targetHeight - startHeight) * progress).toInt()
                }
                onPositionChanged(modeIndex(oldMode) + direction * progress)
            }

            applyProgress(startProgress)
            transitionAnimator = ValueAnimator.ofFloat(startProgress, 1f).apply {
                duration = (160L + 180L * (1f - startProgress)).toLong()
                interpolator = PathInterpolator(.2f, .78f, .2f, 1f)
                addUpdateListener { animator -> applyProgress(animator.animatedValue as Float) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        formHost.removeView(previous)
                        next.translationX = 0f
                        next.alpha = 1f
                        formHost.layoutParams = formHost.layoutParams.apply {
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                        mode = targetMode
                        dragProgress = 0f
                        transitionAnimator = null
                        onModeSettled(targetMode)
                    }
                })
                start()
            }
        }

        private fun modeIndex(value: LoginMode): Float =
            if (value == LoginMode.PUBLIC) 1f else 0f

        private fun recycleVelocityTracker() {
            velocityTracker?.recycle()
            velocityTracker = null
        }
    }

    private inner class LoginModeToggle(
        context: Context,
        initialMode: LoginMode,
        private val onDragPosition: ((Float) -> Unit)? = null,
        private val onDragFinished: ((Float, Float) -> Unit)? = null,
        private val onModeSelected: (LoginMode, LoginModeToggle) -> Unit
    ) : FrameLayout(context) {
        private var selectedMode = initialMode
        private var modeChangePending = false
        private val liquidToggle: LoginLiquidModeToggleView

        init {
            setBackgroundColor(Color.TRANSPARENT)
            liquidToggle = createLoginLiquidModeToggleView(
                context = context,
                initialIndex = if (initialMode == LoginMode.PUBLIC) 1 else 0,
                onTabSelected = { index ->
                    requestMode(if (index == 1) LoginMode.PUBLIC else LoginMode.PERSONAL)
                },
                onPositionDragged = { position -> onDragPosition?.invoke(position) },
                onDragFinished = { position, velocityX ->
                    if (onDragFinished != null) onDragFinished.invoke(position, velocityX)
                    else finishStandaloneDrag(position, velocityX)
                }
            )
            addView(liquidToggle, FrameLayout.LayoutParams(-1, -1))
        }

        private fun requestMode(mode: LoginMode) {
            if (mode == selectedMode || modeChangePending) return
            modeChangePending = true
            try {
                onModeSelected(mode, this)
            } catch (error: Exception) {
                modeChangePending = false
                liquidToggle.setSettledIndex(if (selectedMode == LoginMode.PUBLIC) 1 else 0)
                Toast.makeText(
                    this@MainActivity,
                    "切换课表类型失败：${error.message ?: "未知错误"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        private fun finishStandaloneDrag(position: Float, velocityX: Float) {
            val target = if (position >= .5f) LoginMode.PUBLIC else LoginMode.PERSONAL
            if (target == selectedMode) {
                liquidToggle.setSettledIndex(if (selectedMode == LoginMode.PUBLIC) 1 else 0)
            } else {
                requestMode(target)
            }
        }

        fun setSelectionPosition(position: Float) {
            liquidToggle.setSelectionPosition(position)
        }

        fun setSettledMode(mode: LoginMode) {
            selectedMode = mode
            modeChangePending = false
            liquidToggle.setSettledIndex(if (mode == LoginMode.PUBLIC) 1 else 0)
        }
    }

    private inner class ScheduleGridView(context: Context, private var courses: List<Course>) : View(context) {
        private val dayNames = arrayOf("一", "二", "三", "四", "五", "六", "日")
        private val springTimes = arrayOf(
            arrayOf("08:00", "08:50"), arrayOf("09:00", "09:50"), arrayOf("10:10", "11:00"),
            arrayOf("10:55", "11:40"), arrayOf("14:00", "14:45"), arrayOf("14:55", "15:40"),
            arrayOf("16:00", "16:45"), arrayOf("16:55", "17:40"), arrayOf("19:00", "19:45"),
            arrayOf("19:55", "20:40")
        )
        private val summerTimes = arrayOf(
            arrayOf("08:00", "08:45"), arrayOf("08:55", "09:40"), arrayOf("10:00", "10:45"),
            arrayOf("10:55", "11:40"), arrayOf("14:30", "15:15"), arrayOf("15:25", "16:10"),
            arrayOf("16:30", "17:15"), arrayOf("17:25", "18:10"), arrayOf("19:30", "20:15"),
            arrayOf("20:25", "21:10")
        )
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private var weekIndex = 1
        private var scheduleMode = ScheduleMode.SPRING
        private var timeColumnWidth = 0
        private var dayColumnWidth = 0
        private var headerHeight = 0
        private var slotHeight = 0
        private var desiredWidth = 0
        private var desiredHeight = 0
        private var downX = 0f
        private var downY = 0f
        private var dragOffset = 0f
        private var gestureAxis = 0 // 0 未确定，1 横向翻页，2 纵向手势
        private var pageAnimator: ValueAnimator? = null
        private var pageVelocityTracker: VelocityTracker? = null
        private var cachedCurrentPage: Bitmap? = null
        private var cachedAdjacentPage: Bitmap? = null
        private var cachedCurrentNode: RenderNode? = null
        private var cachedAdjacentNode: RenderNode? = null
        private var cachedCurrentWeek = -1
        private var cachedAdjacentWeek = -1
        private val pageBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val pageSettleInterpolator = PathInterpolator(.18f, .82f, .22f, 1f)
        private val viewConfiguration = ViewConfiguration.get(context)
        private val touchSlop = viewConfiguration.scaledTouchSlop
        private val pageMinimumFlingVelocity = viewConfiguration.scaledMinimumFlingVelocity
        private val pageMaximumFlingVelocity = viewConfiguration.scaledMaximumFlingVelocity
        private var backgroundSamplePageWidth = 0f
        private var backgroundSamplePageHeight = 0f
        private var backgroundSampleGridOffsetX = 0f
        private var backgroundSampleGridOffsetY = 0f
        private var backgroundSampleScrimColor = Color.TRANSPARENT
        private var selectedAddWeek = -1
        private var selectedAddDay = -1
        private var selectedAddSlot = -1
        private var selectedAddAlpha = 0f
        private var selectedAddScale = 1f
        private var addPlaceholderAnimator: ValueAnimator? = null
        private var addPlaceholderFadeRunnable: Runnable? = null
        private var addPlaceholderAnimationGeneration = 0

        init { setBackgroundColor(Color.TRANSPARENT); isFocusable = true; contentDescription = "开发测试周课程表，包含 9 门示例课程" }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            var width = MeasureSpec.getSize(widthMeasureSpec)
            val parentWidth = (parent as? View)?.measuredWidth ?: 0
            val parentContentWidth = (parentWidth - dp(16f)).coerceAtLeast(dp(280f))
            if (width <= 0 || parentContentWidth > width + dp(8f)) width = parentContentWidth
            desiredWidth = width
            timeColumnWidth = Math.max(dp(30f), Math.min(dp(44f), (width * .095f).toInt()))
            dayColumnWidth = ((width - timeColumnWidth) / 7).coerceAtLeast(1)
            headerHeight = dp(44f)
            val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
            if (availableHeight > 0) {
                desiredHeight = availableHeight
                slotHeight = ((availableHeight - headerHeight) / 11f).toInt().coerceAtLeast(dp(40f))
            } else {
                slotHeight = dp(48f)
                desiredHeight = headerHeight + slotHeight * 10
            }
            setMeasuredDimension(desiredWidth, desiredHeight)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (pageAnimator != null) return false
                    pageVelocityTracker?.recycle()
                    pageVelocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    downX = event.x; downY = event.y
                    gestureAxis = 0
                    // 手指按下时先缓存当前周，把主要绘制成本移出连续拖动帧。
                    prepareSwipeBitmaps(-1)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    pageVelocityTracker?.addMovement(event)
                    val dx = event.x - downX; val dy = event.y - downY
                    if (gestureAxis == 0 && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                        gestureAxis = if (kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.1f) 1 else 2
                    }
                    if (gestureAxis == 2) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    } else if (gestureAxis == 1) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        val canMove = (dx < 0f && weekIndex < 20) || (dx > 0f && weekIndex > 0)
                        if (canMove) prepareSwipeBitmaps(if (dx < 0f) weekIndex + 1 else weekIndex - 1)
                        dragOffset = if (canMove) {
                            dx.coerceIn(-desiredWidth.toFloat(), desiredWidth.toFloat())
                        } else {
                            resistedEdgeOffset(dx)
                        }
                        postInvalidateOnAnimation()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    pageVelocityTracker?.addMovement(event)
                    pageVelocityTracker?.computeCurrentVelocity(1000, pageMaximumFlingVelocity.toFloat())
                    val velocityX = pageVelocityTracker?.xVelocity ?: 0f
                    val dx = event.x - downX; val dy = event.y - downY
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (gestureAxis == 1) {
                        val threshold = minOf(desiredWidth * .22f, dp(72f).toFloat())
                        val fastFling = kotlin.math.abs(velocityX) >= pageMinimumFlingVelocity * 1.35f
                        val projectedOffset = dragOffset + velocityX * .12f
                        val delta = if (fastFling) {
                            when {
                                velocityX < 0f && weekIndex < 20 -> 1
                                velocityX > 0f && weekIndex > 0 -> -1
                                else -> 0
                            }
                        } else {
                            when {
                                projectedOffset <= -threshold && weekIndex < 20 -> 1
                                projectedOffset >= threshold && weekIndex > 0 -> -1
                                else -> 0
                            }
                        }
                        settleDraggedWeek(delta, velocityX)
                    } else {
                        if (kotlin.math.abs(dx) <= touchSlop && kotlin.math.abs(dy) <= touchSlop) {
                            handleScheduleTap(event.x, event.y)
                        }
                        clearSwipeBitmaps()
                    }
                    pageVelocityTracker?.recycle()
                    pageVelocityTracker = null
                    gestureAxis = 0
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    settleDraggedWeek(0, 0f)
                    pageVelocityTracker?.recycle()
                    pageVelocityTracker = null
                    gestureAxis = 0
                    return true
                }
            }
            return true
        }

        fun setWeekIndex(index: Int) {
            clearAddCourseSelection(invalidateView = false)
            clearSwipeBitmaps()
            weekIndex = index
            invalidate()
        }
        fun setScheduleMode(mode: ScheduleMode) { clearSwipeBitmaps(); scheduleMode = mode; invalidate() }
        fun setCourses(updated: List<Course>) {
            clearAddCourseSelection(invalidateView = false)
            clearSwipeBitmaps()
            courses = updated
            invalidate()
        }
        fun refreshTextPalette() { clearSwipeBitmaps(); invalidate() }

        private fun handleScheduleTap(x: Float, y: Float) {
            val cell = findScheduleCellAt(x, y)
            val tappedCourse = findCourseAt(x, y) ?: cell?.let { (day, slot) ->
                courses.firstOrNull { course ->
                    course.day == day &&
                        slot >= course.startSlot &&
                        slot < course.startSlot + course.slotCount &&
                        courseVisibleOnScheduleDate(course, activeScheduleTerm(), weekIndex)
                }
            }
            if (tappedCourse != null) {
                clearAddCourseSelection()
                showCourseDetails(tappedCourse)
                return
            }
            if (cell == null || viewingPublicSchedule || weekIndex <= 0) {
                fadeOutAddCourseSelection()
                return
            }
            val (day, slot) = cell
            if (selectedAddWeek == weekIndex && selectedAddDay == day && selectedAddSlot == slot) {
                clearAddCourseSelection()
                showAddCourse(day, slot, weekIndex)
            } else {
                animateAddCourseSelection(day, slot)
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }

        private fun findScheduleCellAt(x: Float, y: Float): Pair<Int, Int>? {
            if (x < timeColumnWidth || y < headerHeight) return null
            val day = ((x - timeColumnWidth) / dayColumnWidth).toInt()
            val slot = ((y - headerHeight) / slotHeight).toInt()
            return if (day in 0..6 && slot in 0..9) day to slot else null
        }

        private fun clearAddCourseSelection(invalidateView: Boolean = true) {
            addPlaceholderAnimationGeneration++
            addPlaceholderFadeRunnable?.let(::removeCallbacks)
            addPlaceholderFadeRunnable = null
            addPlaceholderAnimator?.cancel()
            addPlaceholderAnimator = null
            selectedAddWeek = -1
            selectedAddDay = -1
            selectedAddSlot = -1
            selectedAddAlpha = 0f
            selectedAddScale = 1f
            if (invalidateView) {
                clearSwipeBitmaps()
                invalidate()
            }
        }

        private fun animateAddCourseSelection(day: Int, slot: Int) {
            val generation = ++addPlaceholderAnimationGeneration
            addPlaceholderFadeRunnable?.let(::removeCallbacks)
            addPlaceholderFadeRunnable = null
            addPlaceholderAnimator?.cancel()
            addPlaceholderAnimator = null
            clearSwipeBitmaps()
            val oldSelectionVisible = selectedAddWeek >= 0 && selectedAddAlpha > .01f
            if (oldSelectionVisible) {
                animateAddPlaceholderAlpha(0f, 90L, generation) {
                    startAddCourseSelectionFadeIn(day, slot, generation)
                }
            } else {
                startAddCourseSelectionFadeIn(day, slot, generation)
            }
        }

        private fun startAddCourseSelectionFadeIn(day: Int, slot: Int, generation: Int) {
            if (generation != addPlaceholderAnimationGeneration) return
            selectedAddWeek = weekIndex
            selectedAddDay = day
            selectedAddSlot = slot
            // 先给出轻微的即时反馈，再用缩放+透明度完成进入，避免点击后空等一帧。
            selectedAddAlpha = .12f
            selectedAddScale = .88f
            animateAddPlaceholderAlpha(1f, 170L, generation) {
                scheduleAddCourseSelectionFadeOut(day, slot, generation)
            }
        }

        private fun scheduleAddCourseSelectionFadeOut(day: Int, slot: Int, generation: Int) {
            if (generation != addPlaceholderAnimationGeneration) return
            val fadeRunnable = Runnable {
                if (
                    generation == addPlaceholderAnimationGeneration &&
                    selectedAddWeek == weekIndex && selectedAddDay == day && selectedAddSlot == slot
                ) {
                    animateAddPlaceholderAlpha(0f, 280L, generation) {
                        if (generation == addPlaceholderAnimationGeneration) {
                            selectedAddWeek = -1
                            selectedAddDay = -1
                            selectedAddSlot = -1
                            selectedAddAlpha = 0f
                            selectedAddScale = 1f
                            invalidate()
                        }
                    }
                }
            }
            addPlaceholderFadeRunnable = fadeRunnable
            postDelayed(fadeRunnable, 1250L)
        }

        private fun fadeOutAddCourseSelection() {
            if (selectedAddWeek < 0 || selectedAddAlpha <= .01f) {
                clearAddCourseSelection()
                return
            }
            val generation = ++addPlaceholderAnimationGeneration
            addPlaceholderFadeRunnable?.let(::removeCallbacks)
            addPlaceholderFadeRunnable = null
            addPlaceholderAnimator?.cancel()
            addPlaceholderAnimator = null
            clearSwipeBitmaps()
            animateAddPlaceholderAlpha(0f, 220L, generation) {
                if (generation == addPlaceholderAnimationGeneration) {
                    selectedAddWeek = -1
                    selectedAddDay = -1
                    selectedAddSlot = -1
                    selectedAddAlpha = 0f
                    selectedAddScale = 1f
                    invalidate()
                }
            }
        }

        private fun animateAddPlaceholderAlpha(
            targetAlpha: Float,
            durationMillis: Long,
            generation: Int,
            onFinished: () -> Unit
        ) {
            var cancelled = false
            val startAlpha = selectedAddAlpha
            val startScale = selectedAddScale
            val endScale = if (targetAlpha > startAlpha) 1f else .88f
            addPlaceholderAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMillis
                interpolator = pageSettleInterpolator
                addUpdateListener { animator ->
                    if (generation == addPlaceholderAnimationGeneration) {
                        val progress = animator.animatedValue as Float
                        selectedAddAlpha = startAlpha + (targetAlpha - startAlpha) * progress
                        selectedAddScale = startScale + (endScale - startScale) * progress
                        postInvalidateOnAnimation()
                    }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (!cancelled && generation == addPlaceholderAnimationGeneration) {
                            addPlaceholderAnimator = null
                            onFinished()
                        }
                    }
                })
                start()
            }
        }

        fun releaseTransientCaches() {
            clearAddCourseSelection(invalidateView = false)
            pageAnimator?.removeAllUpdateListeners()
            pageAnimator?.removeAllListeners()
            pageAnimator?.cancel()
            pageAnimator = null
            pageVelocityTracker?.recycle()
            pageVelocityTracker = null
            dragOffset = 0f
            clearSwipeBitmaps()
        }

        override fun onDraw(canvas: Canvas) {
            prepareBackgroundTextSampling()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val currentNode = cachedCurrentNode
                if (currentNode != null && cachedCurrentWeek == weekIndex) {
                    drawCachedNode(canvas, currentNode, dragOffset)
                    cachedAdjacentNode?.let { adjacent ->
                        when {
                            cachedAdjacentWeek > weekIndex -> drawCachedNode(canvas, adjacent, desiredWidth + dragOffset)
                            cachedAdjacentWeek < weekIndex -> drawCachedNode(canvas, adjacent, -desiredWidth + dragOffset)
                        }
                    }
                    return
                }
            }
            val cached = cachedCurrentPage
            if (cached != null && cachedCurrentWeek == weekIndex) {
                canvas.drawBitmap(cached, dragOffset, 0f, pageBitmapPaint)
                val adjacent = cachedAdjacentPage
                if (adjacent != null) {
                    if (cachedAdjacentWeek > weekIndex) {
                        canvas.drawBitmap(adjacent, desiredWidth + dragOffset, 0f, pageBitmapPaint)
                    } else if (cachedAdjacentWeek < weekIndex) {
                        canvas.drawBitmap(adjacent, -desiredWidth + dragOffset, 0f, pageBitmapPaint)
                    }
                }
                return
            }
            drawWeekPage(canvas, weekIndex, dragOffset)
            if (dragOffset < 0f && weekIndex < 20) {
                drawWeekPage(canvas, weekIndex + 1, desiredWidth + dragOffset)
            } else if (dragOffset > 0f && weekIndex > 0) {
                drawWeekPage(canvas, weekIndex - 1, -desiredWidth + dragOffset)
            }
        }

        private fun buildCoursePlacements(visibleCourses: List<Course>): List<CoursePlacement> {
            val result = mutableListOf<CoursePlacement>()

            fun overlaps(first: Course, second: Course): Boolean {
                if (first.day != second.day) return false
                val firstEnd = first.startSlot + first.slotCount
                val secondEnd = second.startSlot + second.slotCount
                return first.startSlot < secondEnd && second.startSlot < firstEnd
            }

            visibleCourses.filter { it.day in 0..6 && it.startSlot in 0..9 }
                .groupBy { it.day }
                .values
                .forEach { dayCourses ->
                    val sorted = dayCourses.sortedWith(
                        compareBy<Course> { it.startSlot }
                            .thenByDescending { it.slotCount }
                    )
                    val component = mutableListOf<Course>()

                    fun flushComponent() {
                        if (component.isEmpty()) return
                        val columnEnds = mutableListOf<Int>()
                        val assigned = mutableListOf<Pair<Course, Int>>()
                        component.forEach { course ->
                            val column = columnEnds.indexOfFirst { end -> end <= course.startSlot }
                                .let { if (it >= 0) it else columnEnds.size }
                            if (column == columnEnds.size) columnEnds += 0
                            columnEnds[column] = course.startSlot + course.slotCount
                            assigned += course to column
                        }
                        val columnCount = columnEnds.size
                        assigned.forEach { (course, column) ->
                            result += CoursePlacement(course, column, columnCount)
                        }
                        component.clear()
                    }

                    sorted.forEach { course ->
                        if (component.isNotEmpty() && component.none { overlaps(it, course) }) {
                            flushComponent()
                        }
                        component += course
                    }
                    flushComponent()
                }
            return result
        }

        private fun drawWeekPage(canvas: Canvas, week: Int, offset: Float) {
            val save = canvas.save()
            canvas.translate(offset, 0f)
            canvas.clipRect(0f, 0f, desiredWidth.toFloat(), desiredHeight.toFloat())
            drawHeaders(canvas, week); drawTimes(canvas)
            val visibleCourses = courses.filter {
                courseVisibleOnScheduleDate(it, activeScheduleTerm(), week)
            }
            val placements = buildCoursePlacements(visibleCourses)
            val showAddPlaceholder = !viewingPublicSchedule &&
                selectedAddAlpha > .001f && week == selectedAddWeek &&
                selectedAddDay in 0..6 && selectedAddSlot in 0..9 &&
                visibleCourses.none { course ->
                    course.day == selectedAddDay &&
                        selectedAddSlot >= course.startSlot &&
                        selectedAddSlot < course.startSlot + course.slotCount
                }
            val hasVisibleCourse = placements.isNotEmpty() || showAddPlaceholder
            placements.forEach { placement ->
                drawCourse(canvas, placement.course, placement.column, placement.columnCount)
            }
            if (showAddPlaceholder) {
                drawAddCoursePlaceholder(
                    canvas,
                    selectedAddDay,
                    selectedAddSlot,
                    selectedAddAlpha,
                    selectedAddScale
                )
            }
            if (!hasVisibleCourse) {
                val centerX = timeColumnWidth + (desiredWidth - timeColumnWidth) / 2f
                val groupCenterY = headerHeight + slotHeight * 4.5f
                drawScheduleEmptyState(canvas, centerX, groupCenterY, week == 0)
            }
            canvas.restoreToCount(save)
        }

        private fun drawAddCoursePlaceholder(
            canvas: Canvas,
            day: Int,
            slot: Int,
            alpha: Float,
            scale: Float
        ) {
            val safeAlpha = alpha.coerceIn(0f, 1f)
            val left = timeColumnWidth + day * dayColumnWidth + dp(2f)
            val top = headerHeight + slot * slotHeight + dp(2f)
            val right = left + dayColumnWidth - dp(4f)
            val bottom = top + slotHeight - dp(4f)
            val corner = minOf(dp(9f).toFloat(), dayColumnWidth * .12f)
            rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            val centerX = rect.centerX()
            val centerY = rect.centerY()
            val drawScale = scale.coerceIn(.84f, 1.02f)
            val save = canvas.save()
            canvas.scale(drawScale, drawScale, centerX, centerY)
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = if (activeThemeColors.isDark) {
                Color.argb((190 * safeAlpha).toInt(), 31, 31, 34)
            } else {
                Color.argb((126 * safeAlpha).toInt(), 226, 242, 255)
            }
            canvas.drawRoundRect(rect, corner, corner, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(if (activeThemeColors.isDark) 1f else 1.6f).toFloat()
            paint.color = if (activeThemeColors.isDark) {
                Color.argb((70 * safeAlpha).toInt(), 158, 160, 168)
            } else {
                Color.argb((205 * safeAlpha).toInt(), 104, 177, 232)
            }
            canvas.drawRoundRect(rect, corner, corner, paint)
            val radius = minOf(dp(12f).toFloat(), (rect.height() - dp(12f)) / 2f)
            paint.strokeWidth = dp(2.4f).toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            if (activeThemeColors.isDark) {
                paint.color = Color.argb((166 * safeAlpha).toInt(), 166, 168, 176)
            }
            canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, paint)
            canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, paint)
            paint.strokeCap = Paint.Cap.BUTT
            canvas.restoreToCount(save)
        }

        /**
         * 与考试、成绩空状态共用同一套视觉尺寸：158×132dp 插画、18dp 标题和 13dp 说明。
         * 课程表是单个 Canvas，因此这里用原生矢量绘制，避免额外位图占用和缩放失真。
         */
        private fun drawScheduleEmptyState(canvas: Canvas, centerX: Float, groupCenterY: Float, beforeTerm: Boolean) {
            val illustrationCenterY = groupCenterY - dp(28f)
            drawScheduleEmptyIllustration(canvas, centerX, illustrationCenterY, beforeTerm)
            val titleCenterY = illustrationCenterY + dp(89f)
            val descriptionCenterY = titleCenterY + dp(27f)
            drawCenteredText(
                canvas,
                if (beforeTerm) "还没有开学哦" else "本周暂无课程",
                centerX,
                titleCenterY,
                sp(18f),
                scheduleTextPalette.primary,
                Typeface.BOLD
            )
            drawCenteredText(
                canvas,
                if (beforeTerm) "距离开学还有 ${daysUntilTermStart()} 天" else "尽情放松吧～",
                centerX,
                descriptionCenterY,
                sp(13f),
                scheduleTextPalette.secondary,
                if (scheduleTextPalette.adaptive) Typeface.BOLD else secondaryTextTypeface()
            )
        }

        private fun drawScheduleEmptyIllustration(canvas: Canvas, cx: Float, cy: Float, beforeTerm: Boolean) {
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            if (beforeTerm) drawBeforeTermIllustration(canvas, cx, cy)
            else drawRelaxingWeekIllustration(canvas, cx, cy)
        }

        /** 未开学：一本等待翻开的课程册和即将升起的太阳。 */
        private fun drawBeforeTermIllustration(canvas: Canvas, cx: Float, cy: Float) {
            val save = canvas.save()
            canvas.rotate(-4f, cx, cy)
            rect.set(cx - dp(42f), cy - dp(35f), cx + dp(35f), cy + dp(38f))

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(27, 55, 72, 125)
            canvas.drawRoundRect(
                rect.left + dp(3f), rect.top + dp(4f), rect.right + dp(3f), rect.bottom + dp(4f),
                dp(14f).toFloat(), dp(14f).toFloat(), paint
            )
            paint.color = Color.argb(210, 252, 253, 255)
            canvas.drawRoundRect(rect, dp(14f).toFloat(), dp(14f).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.2f).toFloat()
            paint.color = Color.argb(95, 126, 139, 179)
            canvas.drawRoundRect(rect, dp(14f).toFloat(), dp(14f).toFloat(), paint)

            // 课程册的彩色书脊和即将展开的页面。
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(131, 140, 199)
            canvas.drawRoundRect(
                rect.left, rect.top, rect.left + dp(13f), rect.bottom,
                dp(12f).toFloat(), dp(12f).toFloat(), paint
            )
            paint.color = Color.rgb(245, 108, 126)
            canvas.drawRoundRect(
                rect.left + dp(20f), rect.top + dp(17f), rect.right - dp(10f), rect.top + dp(22f),
                dp(2.5f).toFloat(), dp(2.5f).toFloat(), paint
            )
            paint.color = Color.argb(105, 105, 113, 132)
            canvas.drawRoundRect(
                rect.left + dp(20f), rect.top + dp(31f), rect.right - dp(17f), rect.top + dp(34f),
                dp(1.5f).toFloat(), dp(1.5f).toFloat(), paint
            )
            canvas.drawRoundRect(
                rect.left + dp(20f), rect.top + dp(42f), rect.right - dp(12f), rect.top + dp(45f),
                dp(1.5f).toFloat(), dp(1.5f).toFloat(), paint
            )
            paint.color = Color.rgb(130, 173, 247)
            val bookmark = Path().apply {
                moveTo(rect.right - dp(22f), rect.bottom - dp(15f))
                lineTo(rect.right - dp(10f), rect.bottom - dp(15f))
                lineTo(rect.right - dp(16f), rect.bottom - dp(8f))
                close()
            }
            canvas.drawPath(bookmark, paint)
            canvas.restoreToCount(save)

            // 太阳从课程册右上角升起，表达“即将开学”。
            val sunX = cx + dp(38f)
            val sunY = cy - dp(31f)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(248, 180, 92)
            canvas.drawCircle(sunX, sunY, dp(9f).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2f).toFloat()
            paint.color = Color.argb(180, 248, 180, 92)
            for (angle in 0 until 360 step 45) {
                val radians = Math.toRadians(angle.toDouble())
                canvas.drawLine(
                    sunX + (kotlin.math.cos(radians) * dp(13f)).toFloat(),
                    sunY + (kotlin.math.sin(radians) * dp(13f)).toFloat(),
                    sunX + (kotlin.math.cos(radians) * dp(17f)).toFloat(),
                    sunY + (kotlin.math.sin(radians) * dp(17f)).toFloat(),
                    paint
                )
            }
        }

        /** 本周无课：一杯饮品、合上的书和柔和的小叶片。 */
        private fun drawRelaxingWeekIllustration(canvas: Canvas, cx: Float, cy: Float) {
            val save = canvas.save()
            canvas.rotate(4f, cx, cy)
            rect.set(cx - dp(43f), cy - dp(31f), cx + dp(30f), cy + dp(39f))
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(27, 55, 72, 125)
            canvas.drawRoundRect(
                rect.left + dp(3f), rect.top + dp(4f), rect.right + dp(3f), rect.bottom + dp(4f),
                dp(13f).toFloat(), dp(13f).toFloat(), paint
            )
            paint.color = Color.argb(210, 252, 253, 255)
            canvas.drawRoundRect(rect, dp(13f).toFloat(), dp(13f).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.2f).toFloat()
            paint.color = Color.argb(95, 126, 139, 179)
            canvas.drawRoundRect(rect, dp(13f).toFloat(), dp(13f).toFloat(), paint)

            // 两本简洁叠放的书，与参考图保持一致。
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(131, 140, 199)
            canvas.drawRoundRect(
                cx - dp(31f), cy + dp(8f), cx + dp(13f), cy + dp(25f),
                dp(6f).toFloat(), dp(6f).toFloat(), paint
            )
            paint.color = Color.rgb(130, 173, 247)
            canvas.drawRoundRect(
                cx - dp(27f), cy + dp(3f), cx + dp(17f), cy + dp(17f),
                dp(5f).toFloat(), dp(5f).toFloat(), paint
            )
            paint.color = Color.argb(205, 252, 253, 255)
            canvas.drawRoundRect(
                cx - dp(24f), cy + dp(7f), cx + dp(13f), cy + dp(11f),
                dp(2f).toFloat(), dp(2f).toFloat(), paint
            )
            canvas.restoreToCount(save)

            // 热饮悬在书边，蒸汽强化轻松、休息的氛围。
            val cupX = cx + dp(28f)
            val cupY = cy - dp(5f)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(245, 108, 126)
            canvas.drawRoundRect(
                cupX - dp(13f), cupY - dp(4f), cupX + dp(11f), cupY + dp(20f),
                dp(7f).toFloat(), dp(7f).toFloat(), paint
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(3f).toFloat()
            paint.color = Color.rgb(245, 108, 126)
            canvas.drawArc(
                cupX + dp(4f), cupY, cupX + dp(20f), cupY + dp(16f),
                -85f, 170f, false, paint
            )
            paint.strokeWidth = dp(1.8f).toFloat()
            paint.color = Color.argb(150, 131, 140, 199)
            canvas.drawArc(cupX - dp(8f), cupY - dp(20f), cupX, cupY - dp(3f), 155f, 135f, false, paint)
            canvas.drawArc(cupX + dp(2f), cupY - dp(23f), cupX + dp(10f), cupY - dp(5f), 155f, 135f, false, paint)

            // 两片小叶子与现有彩色插画的点缀语言保持一致。
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(105, 205, 185)
            canvas.save()
            canvas.rotate(-28f, cx - dp(33f), cy - dp(31f))
            canvas.drawOval(
                cx - dp(40f), cy - dp(36f), cx - dp(27f), cy - dp(27f), paint
            )
            canvas.restore()
            paint.color = Color.rgb(130, 173, 247)
            canvas.save()
            canvas.rotate(26f, cx - dp(22f), cy - dp(36f))
            canvas.drawOval(
                cx - dp(28f), cy - dp(40f), cx - dp(16f), cy - dp(32f), paint
            )
            canvas.restore()
        }

        private fun settleDraggedWeek(delta: Int, releaseVelocityX: Float) {
            pageAnimator?.cancel()
            val target = when {
                delta > 0 -> -desiredWidth.toFloat()
                delta < 0 -> desiredWidth.toFloat()
                else -> 0f
            }
            val remainingDistance = kotlin.math.abs(target - dragOffset)
            val distanceRatio = remainingDistance / desiredWidth.coerceAtLeast(1)
            val velocityTowardTarget = when {
                target < dragOffset && releaseVelocityX < 0f -> -releaseVelocityX
                target > dragOffset && releaseVelocityX > 0f -> releaseVelocityX
                else -> 0f
            }
            val distanceDuration = (180f + 110f * distanceRatio).toLong()
            val velocityDuration = if (velocityTowardTarget >= pageMinimumFlingVelocity) {
                (remainingDistance / velocityTowardTarget * 880f).toLong().coerceIn(140L, 275L)
            } else {
                distanceDuration
            }
            pageAnimator = ValueAnimator.ofFloat(dragOffset, target).apply {
                duration = minOf(distanceDuration, velocityDuration).coerceIn(140L, 295L)
                interpolator = pageSettleInterpolator
                addUpdateListener {
                    dragOffset = it.animatedValue as Float
                    postInvalidateOnAnimation()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (delta != 0) {
                            clearAddCourseSelection(invalidateView = false)
                            weekIndex = (weekIndex + delta).coerceIn(0, 20)
                            currentWeek = weekIndex
                            scheduleHeader?.updateWeek(formatWeekLabel(currentWeek))
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        dragOffset = 0f
                        pageAnimator = null
                        clearSwipeBitmaps()
                        invalidate()
                    }
                })
                start()
            }
        }

        private fun resistedEdgeOffset(distance: Float): Float {
            val magnitude = kotlin.math.abs(distance)
            val limit = desiredWidth * .16f
            val resisted = limit * magnitude / (magnitude + desiredWidth * .72f).coerceAtLeast(1f)
            return if (distance < 0f) -resisted else resisted
        }

        private fun prepareSwipeBitmaps(adjacentWeek: Int) {
            if (desiredWidth <= 0 || desiredHeight <= 0) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                prepareSwipeRenderNodes(adjacentWeek)
                return
            }
            if (cachedCurrentPage == null || cachedCurrentWeek != weekIndex) {
                clearSwipeBitmaps()
                cachedCurrentPage = renderWeekBitmap(weekIndex)
                cachedCurrentWeek = if (cachedCurrentPage != null) weekIndex else -1
            }
            if (adjacentWeek !in 0..20) return
            if (cachedAdjacentPage == null || cachedAdjacentWeek != adjacentWeek) {
                cachedAdjacentPage?.recycle()
                cachedAdjacentPage = renderWeekBitmap(adjacentWeek)
                cachedAdjacentWeek = if (cachedAdjacentPage != null) adjacentWeek else -1
            }
        }

        private fun renderWeekBitmap(week: Int): Bitmap? = runCatching {
            Bitmap.createBitmap(desiredWidth, desiredHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                drawWeekPage(Canvas(bitmap), week, 0f)
            }
        }.getOrNull()

        @android.annotation.TargetApi(Build.VERSION_CODES.Q)
        private fun prepareSwipeRenderNodes(adjacentWeek: Int) {
            if (cachedCurrentNode == null || cachedCurrentWeek != weekIndex) {
                clearSwipeBitmaps()
                cachedCurrentNode = renderWeekNode(weekIndex)
                cachedCurrentWeek = if (cachedCurrentNode != null) weekIndex else -1
            }
            if (adjacentWeek !in 0..20) return
            if (cachedAdjacentNode == null || cachedAdjacentWeek != adjacentWeek) {
                cachedAdjacentNode?.discardDisplayList()
                cachedAdjacentNode = renderWeekNode(adjacentWeek)
                cachedAdjacentWeek = if (cachedAdjacentNode != null) adjacentWeek else -1
            }
        }

        @android.annotation.TargetApi(Build.VERSION_CODES.Q)
        private fun renderWeekNode(week: Int): RenderNode? = runCatching {
            RenderNode("schedule-week-$week").apply {
                setPosition(0, 0, desiredWidth, desiredHeight)
                val recordingCanvas = beginRecording()
                try {
                    drawWeekPage(recordingCanvas, week, 0f)
                } finally {
                    endRecording()
                }
            }
        }.getOrNull()

        @android.annotation.TargetApi(Build.VERSION_CODES.Q)
        private fun drawCachedNode(canvas: Canvas, node: RenderNode, offset: Float) {
            val save = canvas.save()
            canvas.translate(offset, 0f)
            canvas.drawRenderNode(node)
            canvas.restoreToCount(save)
        }

        private fun clearSwipeBitmaps() {
            cachedCurrentPage?.recycle()
            cachedAdjacentPage?.recycle()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cachedCurrentNode?.discardDisplayList()
                cachedAdjacentNode?.discardDisplayList()
            }
            cachedCurrentPage = null
            cachedAdjacentPage = null
            cachedCurrentNode = null
            cachedAdjacentNode = null
            cachedCurrentWeek = -1
            cachedAdjacentWeek = -1
        }

        override fun onDetachedFromWindow() {
            releaseTransientCaches()
            super.onDetachedFromWindow()
        }

        private fun drawHeaders(canvas: Canvas, week: Int) {
            val monthDate = displayWeekBaseDate(week)
            val today = Calendar.getInstance()
            val month = monthDate.get(Calendar.MONTH) + 1
            val monthSize = fittedGridTextSize(month.toString(), sp(14f), timeColumnWidth - dp(6f), Typeface.BOLD)
            drawCenteredText(
                canvas,
                month.toString(),
                timeColumnWidth / 2f,
                dp(14f).toFloat(),
                monthSize,
                scheduleTextPalette.primary,
                Typeface.BOLD
            )
            drawCenteredText(
                canvas,
                "月",
                timeColumnWidth / 2f,
                dp(31f).toFloat(),
                sp(9f),
                scheduleTextPalette.secondary,
                if (currentPageBackgroundBitmap != null) Typeface.BOLD else Typeface.NORMAL
            )
            for (day in 0..6) {
                val center = timeColumnWidth + day * dayColumnWidth + dayColumnWidth / 2f
                val headerDate = monthDate.clone() as Calendar
                headerDate.add(Calendar.DAY_OF_MONTH, day)
                val isToday = headerDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    headerDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                val textColor = if (isToday) scheduleTextPalette.primary else scheduleTextPalette.secondary
                drawCenteredText(
                    canvas,
                    dayNames[day],
                    center,
                    dp(14f).toFloat(),
                    sp(if (isToday) 13.3f else 11.5f),
                    textColor,
                    if (isToday || currentPageBackgroundBitmap != null) Typeface.BOLD else Typeface.NORMAL,
                    extraBold = isToday
                )
                drawCenteredText(
                    canvas,
                    dateForDay(day, week),
                    center,
                    dp(31f).toFloat(),
                    sp(if (isToday) 9.4f else 7.7f),
                    textColor,
                    if (isToday || currentPageBackgroundBitmap != null) Typeface.BOLD else Typeface.NORMAL,
                    extraBold = isToday
                )
            }
        }

        private fun dateForDay(day: Int, week: Int): String {
            val date = displayWeekBaseDate(week).apply { add(Calendar.DAY_OF_MONTH, day) }
            return SimpleDateFormat("M/d", Locale.CHINA).format(date.time)
        }

        private fun displayWeekBaseDate(week: Int): Calendar {
            if (week == 0) {
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                while (today.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) today.add(Calendar.DAY_OF_MONTH, -1)
                return today
            }
            return termStartDate(selectedTerm()).apply { add(Calendar.DAY_OF_MONTH, (week - 1) * 7) }
        }

        private fun drawTimes(canvas: Canvas) {
            val times = if (scheduleMode == ScheduleMode.SPRING) springTimes else summerTimes
            for (slot in 0..9) {
                val top = headerHeight + slot * slotHeight
                val center = timeColumnWidth / 2f
                val slotLabel = (slot + 1).toString()
                val slotSize = fittedGridTextSize(slotLabel, sp(15f), timeColumnWidth - dp(6f), Typeface.BOLD)
                val timeSize = minOf(
                    fittedGridTextSize(times[slot][0], sp(10f), timeColumnWidth - dp(4f), Typeface.NORMAL),
                    fittedGridTextSize(times[slot][1], sp(10f), timeColumnWidth - dp(4f), Typeface.NORMAL)
                )
                val timeStyle = if (scheduleTextPalette.adaptive) Typeface.BOLD else Typeface.NORMAL
                drawCenteredText(
                    canvas,
                    slotLabel,
                    center,
                    (top + dp(15f)).toFloat(),
                    slotSize,
                    scheduleTextPalette.primary,
                    Typeface.BOLD
                )
                drawCenteredText(
                    canvas,
                    times[slot][0],
                    center,
                    (top + dp(30f)).toFloat(),
                    timeSize,
                    scheduleTextPalette.secondary,
                    timeStyle
                )
                drawCenteredText(
                    canvas,
                    times[slot][1],
                    center,
                    (top + dp(43f)).toFloat(),
                    timeSize,
                    scheduleTextPalette.secondary,
                    timeStyle
                )
            }
        }

        private fun fittedGridTextSize(value: String, desiredSize: Float, maxWidth: Int, style: Int): Float {
            paint.textSize = desiredSize
            paint.typeface = Typeface.create(Typeface.DEFAULT, style)
            val measured = paint.measureText(value)
            return if (measured > maxWidth && measured > 0f) desiredSize * maxWidth / measured else desiredSize
        }

        private fun drawCourse(canvas: Canvas, course: Course, column: Int = 0, columnCount: Int = 1) {
            val dayLeft = timeColumnWidth + course.day * dayColumnWidth
            val horizontalGap = if (columnCount > 1) dp(2f).toFloat() else 0f
            val availableWidth = dayColumnWidth - dp(4f).toFloat()
            val courseWidth = (availableWidth - horizontalGap * (columnCount - 1)) /
                columnCount.coerceAtLeast(1)
            val left = dayLeft + dp(2f) + column * (courseWidth + horizontalGap)
            val top = headerHeight + course.startSlot * slotHeight + dp(2f)
            val right = left + courseWidth
            val bottom = headerHeight + (course.startSlot + course.slotCount) * slotHeight - dp(2f)
            rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            paint.style = Paint.Style.FILL
            paint.color = displayedCourseColor(course.background)
            val corner = minOf(dp(9f).toFloat(), dayColumnWidth * .12f); canvas.drawRoundRect(rect, corner, corner, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(if (activeThemeColors.isDark) 1f else 2f).toFloat()
            paint.color = if (activeThemeColors.isDark) {
                ColorUtils.setAlphaComponent(
                    displayedCourseForeground(course.background, course.foreground),
                    62
                )
            } else {
                Color.argb(175, 255, 255, 255)
            }
            canvas.drawRoundRect(rect, corner, corner, paint)
            val padding = maxOf(dp(3f).toFloat(), minOf(dp(7f).toFloat(), dayColumnWidth * .075f))
            // 使用卡片的真实内部宽度。列宽还包含左右各 3dp 的卡片外边距，
            // 若直接使用 dayColumnWidth，W/M 等宽字形会被误判为能够放下并越界。
            val maxWidth = (right - left).toFloat() - padding * 2f - dp(1f)
            val maxHeight = bottom - top - padding * 2
            var size = minOf(sp(12f), dayColumnWidth * .22f)
            var lines = wrapCourseLines(course, size, maxWidth)
            while (size > sp(7f) && lines.size * size * 1.16f > maxHeight) {
                size -= sp(.5f)
                lines = wrapCourseLines(course, size, maxWidth)
            }
            paint.style = Paint.Style.FILL
            paint.textSize = size
            paint.color = displayedCourseForeground(course.background, course.foreground)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            var baseline = top + padding - paint.ascent()
            val lineHeight = size * 1.16f
            lines.forEach { line ->
                if (baseline <= bottom - padding) {
                    paint.textSize = size
                    val measuredWidth = paint.measureText(line)
                    if (measuredWidth > maxWidth && measuredWidth > 0f) {
                        paint.textSize = size * maxWidth / measuredWidth
                    }
                    canvas.drawText(line, left + padding, baseline, paint)
                    baseline += lineHeight
                }
            }
        }

        private fun findCourseAt(x: Float, y: Float): Course? {
            if (y < headerHeight) return null
            return buildCoursePlacements(courses.filter {
                courseVisibleOnScheduleDate(it, activeScheduleTerm(), weekIndex)
            })
                .firstOrNull { placement ->
                val course = placement.course
                val dayLeft = timeColumnWidth + course.day * dayColumnWidth
                val horizontalGap = if (placement.columnCount > 1) dp(2f).toFloat() else 0f
                val availableWidth = dayColumnWidth - dp(4f).toFloat()
                val courseWidth = (availableWidth - horizontalGap * (placement.columnCount - 1)) /
                    placement.columnCount.coerceAtLeast(1)
                val left = dayLeft + dp(2f) + placement.column * (courseWidth + horizontalGap)
                val top = headerHeight + course.startSlot * slotHeight + dp(2f)
                val right = left + courseWidth
                val bottom = headerHeight + (course.startSlot + course.slotCount) * slotHeight - dp(2f)
                x in left.toFloat()..right.toFloat() && y in top.toFloat()..bottom.toFloat()
            }?.course
        }

        private fun wrapCourseLines(course: Course, size: Float, maxWidth: Float): List<String> {
            paint.textSize = size
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val result = mutableListOf<String>()
            fun isHanCharacter(char: Char): Boolean =
                char in '\u3400'..'\u4DBF' ||
                    char in '\u4E00'..'\u9FFF' ||
                    char in '\uF900'..'\uFAFF'

            fun appendWrapped(raw: String, hanCharacterLimit: Int? = null) {
                raw.split('\n').forEach { paragraph ->
                    var remaining = paragraph
                    if (remaining.isEmpty()) result.add("")
                    while (remaining.isNotEmpty()) {
                        val measuredCount = paint.breakText(remaining, true, maxWidth, null)
                        val count = if (hanCharacterLimit == null) {
                            measuredCount
                        } else {
                            var acceptedCount = 0
                            var hanCount = 0
                            while (acceptedCount < measuredCount) {
                                if (isHanCharacter(remaining[acceptedCount])) {
                                    if (hanCount >= hanCharacterLimit) break
                                    hanCount++
                                }
                                acceptedCount++
                            }
                            acceptedCount
                        }
                        if (count <= 0) break
                        result.add(remaining.substring(0, count))
                        remaining = remaining.substring(count)
                    }
                }
            }
            // 汉字每行最多三个；英文、数字和符号按卡片实际宽度尽量多排。
            appendWrapped(course.name, hanCharacterLimit = 3)
            formatRoom(course.room).split('\n').forEach { roomLine ->
                val compactCode = roomLine.length <= 6 && roomLine.matches(Regex("^@[A-Za-z0-9-]+$"))
                val completeBuilding = roomLine.matches(Regex("^\\d+号楼$"))
                if (compactCode || completeBuilding) result.add(roomLine) else appendWrapped(roomLine)
            }
            appendWrapped(course.teacher)
            return result
        }

        private fun prepareBackgroundTextSampling() {
            val page = schedulePageRoot
            val bitmap = liveScheduleBackgroundBitmap ?: currentPageBackgroundBitmap
            if (
                !scheduleTextPalette.adaptive ||
                page == null ||
                bitmap == null ||
                bitmap.isRecycled ||
                page.width <= 0 ||
                page.height <= 0 ||
                !isAttachedToWindow
            ) {
                backgroundSamplePageWidth = 0f
                backgroundSamplePageHeight = 0f
                return
            }
            val pageLocation = IntArray(2)
            val gridLocation = IntArray(2)
            page.getLocationInWindow(pageLocation)
            getLocationInWindow(gridLocation)
            backgroundSamplePageWidth = page.width.toFloat()
            backgroundSamplePageHeight = page.height.toFloat()
            backgroundSampleGridOffsetX = (gridLocation[0] - pageLocation[0]).toFloat()
            backgroundSampleGridOffsetY = (gridLocation[1] - pageLocation[1]).toFloat()
            backgroundSampleScrimColor =
                liveScheduleBackgroundScrimColor ?: customBackgroundScrimColor()
        }

        private fun localScheduleHaloColor(
            textColor: Int,
            centerX: Float,
            centerY: Float,
            measuredTextWidth: Float,
            textSize: Float
        ): Int {
            val bitmap = liveScheduleBackgroundBitmap ?: currentPageBackgroundBitmap
            val pageWidth = backgroundSamplePageWidth
            val pageHeight = backgroundSamplePageHeight
            if (
                bitmap == null ||
                bitmap.isRecycled ||
                pageWidth <= 0f ||
                pageHeight <= 0f
            ) return scheduleTextPalette.halo

            val previewCrop = liveScheduleBackgroundCrop
            val cropWidth = previewCrop
                ?.let { (it.right - it.left) * bitmap.width }
                ?.coerceAtLeast(1f)
                ?: bitmap.width.toFloat()
            val cropHeight = previewCrop
                ?.let { (it.bottom - it.top) * bitmap.height }
                ?.coerceAtLeast(1f)
                ?: bitmap.height.toFloat()
            val cropCenterX = previewCrop
                ?.let { (it.left + it.right) * bitmap.width / 2f }
                ?: bitmap.width / 2f
            val cropCenterY = previewCrop
                ?.let { (it.top + it.bottom) * bitmap.height / 2f }
                ?: bitmap.height / 2f
            val imageScale = maxOf(pageWidth / cropWidth, pageHeight / cropHeight)
            val imageLeft = pageWidth / 2f - cropCenterX * imageScale
            val imageTop = pageHeight / 2f - cropCenterY * imageScale
            val pageCenterX = backgroundSampleGridOffsetX + centerX
            val pageCenterY = backgroundSampleGridOffsetY + centerY
            val halfWidth = (measuredTextWidth * 0.52f)
                .coerceIn(dp(8f).toFloat(), dp(90f).toFloat())
            val halfHeight = (textSize * 0.55f).coerceAtLeast(dp(4f).toFloat())
            val opaqueTextColor = ColorUtils.setAlphaComponent(textColor, 255)
            var contrastTotal = 0.0
            var sampleCount = 0

            for (row in 0 until 3) {
                val pageY = (pageCenterY - halfHeight + halfHeight * row)
                    .coerceIn(0f, pageHeight - 1f)
                val gradientColors = activeThemeColors.gradient
                val gradientIndex = ((pageY / pageHeight) * (gradientColors.size - 1))
                    .roundToInt().coerceIn(gradientColors.indices)
                for (column in 0 until 7) {
                    val pageX = (pageCenterX - halfWidth + halfWidth * column / 3f)
                        .coerceIn(0f, pageWidth - 1f)
                    val sourceX = ((pageX - imageLeft) / imageScale)
                        .roundToInt().coerceIn(0, bitmap.width - 1)
                    val sourceY = ((pageY - imageTop) / imageScale)
                        .roundToInt().coerceIn(0, bitmap.height - 1)
                    val wallpaperColor = ColorUtils.compositeColors(
                        bitmap.getPixel(sourceX, sourceY),
                        gradientColors[gradientIndex]
                    )
                    val finalColor = ColorUtils.compositeColors(
                        backgroundSampleScrimColor,
                        wallpaperColor
                    )
                    contrastTotal += ColorUtils.calculateContrast(opaqueTextColor, finalColor)
                    sampleCount++
                }
            }
            val contrast = contrastTotal / sampleCount.coerceAtLeast(1)
            val baseAlpha = Color.alpha(scheduleTextPalette.halo)
            val alphaScale = when {
                contrast >= 7.0 -> 0.56f
                contrast >= 4.5 -> 0.74f
                contrast >= 3.0 -> 1f
                else -> 1.28f
            }
            return ColorUtils.setAlphaComponent(
                scheduleTextPalette.halo,
                (baseAlpha * alphaScale).roundToInt().coerceIn(72, 196)
            )
        }

        private fun drawCenteredText(
            canvas: Canvas,
            value: String,
            centerX: Float,
            centerY: Float,
            size: Float,
            color: Int,
            style: Int,
            extraBold: Boolean = false
        ) {
            paint.textSize = size
            paint.typeface = Typeface.create(Typeface.DEFAULT, style)
            paint.isFakeBoldText = extraBold
            paint.textAlign = Paint.Align.CENTER
            val metrics = paint.fontMetrics
            val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
            if (scheduleTextPalette.adaptive) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = maxOf(dp(0.40f).toFloat(), size * 0.045f)
                paint.strokeJoin = Paint.Join.ROUND
                paint.color = localScheduleHaloColor(
                    textColor = color,
                    centerX = centerX,
                    centerY = centerY,
                    measuredTextWidth = paint.measureText(value),
                    textSize = size
                )
                canvas.drawText(value, centerX, baseline, paint)
            }
            paint.style = if (extraBold) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
            paint.strokeWidth = if (extraBold) maxOf(dp(0.18f).toFloat(), size * 0.008f) else 0f
            paint.color = color
            canvas.drawText(value, centerX, baseline, paint)
            paint.isFakeBoldText = false
            paint.strokeWidth = 0f
            paint.textAlign = Paint.Align.LEFT
        }

        private fun sp(value: Float) = value * resources.displayMetrics.density
    }

    private fun formatExportRoom(room: String): String {
        val normalized = room.replace(Regex("\\s+"), "")
        return if (normalized.startsWith("图信")) "@$normalized" else formatRoom(room)
    }

    private fun formatRoom(room: String): String {
        val normalized = room.replace(Regex("\\s+"), "")
        val numberedBuilding = Regex("^(北校|南校)(\\d+)号楼(\\d+)$").find(normalized)
        if (numberedBuilding != null) {
            return "@${numberedBuilding.groupValues[1]}\n${numberedBuilding.groupValues[2]}号楼\n${numberedBuilding.groupValues[3]}"
        }
        val mapBuilding = Regex("^图信(楼.*)$").find(normalized)
        if (mapBuilding != null) return "@图信\n${mapBuilding.groupValues[1]}"
        return "@$room"
    }

    private fun showCourseDetails(course: Course) {
        if (detailOverlay != null || courseDialogCapturePending) return
        courseDialogCapturePending = true
        captureUpdateBackdrop { pageSnapshot ->
            courseDialogCapturePending = false
            if (
                isFinishing ||
                isDestroyed ||
                onLoginPage ||
                currentMainSection != 0 ||
                detailOverlay != null
            ) {
                pageSnapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
                return@captureUpdateBackdrop
            }
            val dialog = LiquidCourseDialogView(
                context = this,
                pageSnapshot = pageSnapshot,
                courseName = course.name,
                room = course.room,
                teacher = course.teacher,
                slotText = "第 ${course.startSlot + 1}-${course.startSlot + course.slotCount} 节",
                scheduleTitle = courseDialogScheduleTitle(course.day, course.startSlot),
                weeks = course.weeks,
                canEdit = !viewingPublicSchedule,
                initialSlotCount = course.slotCount,
                maxSlotCount = 10 - course.startSlot,
                allowDurationEdit = course.isCustom,
                onSave = { name, room, teacher, weeks, slotCount ->
                    updateCourseCache(course, name, room, teacher, weeks, slotCount)
                    hideCourseDetails()
                },
                onDelete = if (!viewingPublicSchedule && course.isCustom) {
                    {
                        deleteCourseFromCache(course)
                        hideCourseDetails()
                    }
                } else null,
                onDismiss = ::hideCourseDetails
            )
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            pageHost.addView(dialog, matchParentParams())
            detailOverlay = dialog
            dialog.alpha = 0f
            dialog.animate().alpha(1f).setDuration(180).start()
        }
    }

    private fun showAddCourse(day: Int, startSlot: Int, week: Int) {
        if (viewingPublicSchedule || detailOverlay != null || courseDialogCapturePending) return
        val maxSlotCount = maximumInsertSlotCount(day, startSlot, week)
        if (maxSlotCount <= 0) return
        courseDialogCapturePending = true
        captureUpdateBackdrop { pageSnapshot ->
            courseDialogCapturePending = false
            if (
                isFinishing || isDestroyed || onLoginPage || currentMainSection != 0 ||
                viewingPublicSchedule || detailOverlay != null
            ) {
                pageSnapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
                return@captureUpdateBackdrop
            }
            val defaultSlotCount = if (startSlot % 2 == 0) 2 else 1
            val dialog = LiquidCourseDialogView(
                context = this,
                pageSnapshot = pageSnapshot,
                courseName = "",
                room = "",
                teacher = "",
                slotText = "第 ${startSlot + 1} 节",
                scheduleTitle = courseDialogScheduleTitle(day, startSlot),
                weeks = week.toString(),
                canEdit = true,
                creating = true,
                initialSlotCount = minOf(defaultSlotCount, maxSlotCount),
                maxSlotCount = maxSlotCount,
                allowDurationEdit = true,
                onSave = { name, room, teacher, weeks, slotCount ->
                    addCourseToCache(day, startSlot, slotCount, name, room, teacher, weeks, week)
                    hideCourseDetails()
                },
                onDismiss = ::hideCourseDetails
            )
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            pageHost.addView(dialog, matchParentParams())
            detailOverlay = dialog
            dialog.alpha = 0f
            dialog.animate().alpha(1f).setDuration(180).start()
        }
    }

    private fun maximumInsertSlotCount(day: Int, startSlot: Int, week: Int): Int {
        val nextOccupiedSlot = loadCourseCache()
            .filter { course ->
                course.day == day &&
                    courseVisibleOnScheduleDate(course, activeScheduleTerm(), week) &&
                    course.startSlot > startSlot
            }
            .minOfOrNull(Course::startSlot)
            ?: 10
        return (nextOccupiedSlot - startSlot).coerceIn(1, 10 - startSlot)
    }

    private fun courseDialogScheduleTitle(day: Int, startSlot: Int): String {
        val dayLabel = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            .getOrElse(day) { "" }
        val slotLabel = arrayOf(
            "第一节", "第二节", "第三节", "第四节", "第五节",
            "第六节", "第七节", "第八节", "第九节", "第十节"
        ).getOrElse(startSlot) { "第 ${startSlot + 1} 节" }
        return listOf(dayLabel, slotLabel).filter(String::isNotBlank).joinToString(" · ")
    }

    private fun addCourseToCache(
        day: Int,
        startSlot: Int,
        slotCount: Int,
        name: String,
        room: String,
        teacher: String,
        weeks: String,
        fallbackWeek: Int
    ) {
        val course = Course(
            day = day,
            startSlot = startSlot,
            slotCount = slotCount.coerceIn(1, 10 - startSlot),
            name = name.trim().ifBlank { "未命名课程" },
            room = room.trim(),
            teacher = teacher.trim(),
            background = COURSE_COLORS.first(),
            foreground = Color.WHITE,
            weeks = weeks.trim().ifBlank { fallbackWeek.toString() },
            isCustom = true
        )
        val customCourses = recolorCourses(loadCustomCourseCache() + course)
        saveCustomCourseCache(customCourses)
        scheduleGrid?.setCourses(loadCourseCache())
    }

    private fun updateCourseCache(
        original: Course,
        name: String,
        room: String,
        teacher: String,
        weeks: String,
        slotCount: Int
    ) {
        val source = if (original.isCustom) loadCustomCourseCache() else loadImportedCourseCache()
        val updated = source.map { current ->
            if (sameCourseRecord(current, original)) {
                current.copy(
                    name = name,
                    room = room,
                    teacher = teacher,
                    weeks = weeks,
                    slotCount = if (original.isCustom) {
                        slotCount.coerceIn(1, 10 - current.startSlot)
                    } else {
                        current.slotCount
                    }
                )
            } else current
        }
        val recolored = recolorCourses(updated)
        if (original.isCustom) saveCustomCourseCache(recolored) else saveCourseCache(recolored)
        scheduleGrid?.setCourses(loadCourseCache())
    }

    private fun deleteCourseFromCache(course: Course) {
        if (!course.isCustom) return
        val updated = loadCustomCourseCache().filterNot { current -> sameCourseRecord(current, course) }
        val recolored = recolorCourses(updated)
        saveCustomCourseCache(recolored)
        scheduleGrid?.setCourses(loadCourseCache())
    }

    private fun sameCourseRecord(first: Course, second: Course): Boolean =
        first.day == second.day &&
            first.startSlot == second.startSlot &&
            first.slotCount == second.slotCount &&
            first.name == second.name &&
            first.room == second.room &&
            first.teacher == second.teacher &&
            first.weeks == second.weeks &&
            first.isCustom == second.isCustom

    private fun hideCourseDetails() {
        val overlay = detailOverlay ?: return
        detailOverlay = null
        // Keep the timetable at a fixed size while the IME is leaving. Restoring
        // ADJUST_RESIZE before that animation finishes makes both the wallpaper and
        // course grid measure once with the keyboard and once without it, producing
        // a visible one-frame jump after saving a course.
        hideKeyboard()
        overlay.animate().alpha(0f).setDuration(140).withEndAction {
            pageHost.removeView(overlay)
            overlay.releaseSnapshot()
            restoreCourseDialogSoftInputModeWhenImeHidden()
        }.start()
    }

    private fun restoreCourseDialogSoftInputModeWhenImeHidden(attempt: Int = 0) {
        val decor = window.decorView
        val insets = ViewCompat.getRootWindowInsets(decor)
        val imeVisible = insets?.let {
            it.isVisible(WindowInsetsCompat.Type.ime()) ||
                it.getInsets(WindowInsetsCompat.Type.ime()).bottom >
                it.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        } == true
        val minimumSettleFrames = 8
        val maximumWaitFrames = 32
        if (attempt < maximumWaitFrames && (attempt < minimumSettleFrames || imeVisible)) {
            decor.postDelayed(
                { restoreCourseDialogSoftInputModeWhenImeHidden(attempt + 1) },
                16L
            )
            return
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onResume() {
        super.onResume()
        hideSystemNavigationBar()
        val automaticMode = ScheduleTimePolicy.currentMode()
        if (automaticMode == scheduleMode) return
        scheduleMode = automaticMode
        scheduleGrid?.setScheduleMode(automaticMode)
        CourseWidgetProvider.updateAll(this)
        if (
            pushEnabled &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                )
        ) {
            scheduleSystemCourseReminder()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemNavigationBar()
    }

    override fun onDestroy() {
        scheduleGrid?.releaseTransientCaches()
        publicScheduleIndexCache.clear()
        clearLiquidToastImmediately()
        updateDialogView?.releaseSnapshot()
        updateDialogView = null
        scoreDetailOverlay?.releaseSnapshot()
        scoreDetailOverlay = null
        detailOverlay?.releaseSnapshot()
        detailOverlay = null
        emptyRoomFilterOverlay?.releaseSnapshot()
        emptyRoomFilterOverlay = null
        publicOptionOverlay?.releaseSnapshot()
        publicOptionOverlay = null
        appearanceOverlay?.releaseSnapshot()
        appearanceOverlay = null
        refreshScheduleConfirmOverlay?.releaseSnapshot()
        refreshScheduleConfirmOverlay = null
        (shareOverlay as? LiquidPickerDialogView)?.releaseSnapshot()
        actionMenuOverlay?.releaseSnapshot()
        backgroundEditorOverlay?.releaseBitmap()
        backgroundEditorOverlay = null
        backgroundEditorPendingSource?.delete()
        backgroundEditorPendingSource = null
        clearUpdateDownloadReceiver()
        networkExecutor.shutdownNow()
        publicSyncExecutor.shutdownNow()
        updateExecutor.shutdownNow()
        backgroundExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) scheduleGrid?.releaseTransientCaches()
        if (level >= TRIM_MEMORY_UI_HIDDEN) publicScheduleIndexCache.clear()
    }

    override fun onLowMemory() {
        scheduleGrid?.releaseTransientCaches()
        super.onLowMemory()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 3001
        private const val PREFS_NAME = "offline_login"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PASSWORD_PREFIX = "password_cache"
        private const val KEY_STUDENT_NAME = "student_name"
        private const val KEY_STUDENT_NAME_PREFIX = "student_name_cache"
        private const val KEY_TERM = "term"
        private const val KEY_SCORE_TERM = "score_term"
        private const val KEY_COURSES = "courses_cache"
        private const val KEY_COURSES_PREFIX = "courses_cache_account"
        private const val KEY_CUSTOM_COURSES_PREFIX = "custom_courses_cache"
        private const val KEY_CUSTOM_COURSES_OWNER_PREFIX = "custom_courses_owner"
        private const val KEY_PUBLIC_SCHEDULE_SYNCED_TERM = "public_schedule_synced_term"
        private const val KEY_PUBLIC_SCHEDULE_HASH_PREFIX = "public_schedule_sha256_"
        private const val KEY_SCORES = "scores_cache"
        private const val KEY_SCORES_PREFIX = "scores_cache_account"
        private const val SCORE_STATS_SCOPE = "all_terms_v2"
        private const val KEY_EXAMS = "exams_cache"
        private const val KEY_EXAMS_PREFIX = "exams_cache_account"
        private const val KEY_COLOR_MAP = "course_color_map"
        private const val KEY_DARK_COLOR_MAP = "dark_course_color_map"
        private const val KEY_PUSH_ENABLED = "push_enabled"
        private const val KEY_BATTERY_PROMPTED = "battery_prompted"
        private const val KEY_UPDATE_STARTED_CODE = "update_started_code"
        private const val KEY_CUSTOM_BACKGROUND = "custom_background"
        private const val KEY_CUSTOM_BACKGROUND_CLARITY = "custom_background_clarity"
        private const val KEY_CUSTOM_BACKGROUND_CROP_LEFT = "custom_background_crop_left"
        private const val KEY_CUSTOM_BACKGROUND_CROP_TOP = "custom_background_crop_top"
        private const val KEY_CUSTOM_BACKGROUND_CROP_RIGHT = "custom_background_crop_right"
        private const val KEY_CUSTOM_BACKGROUND_CROP_BOTTOM = "custom_background_crop_bottom"
        private const val CUSTOM_BACKGROUND_FILE_NAME = "custom_schedule_background"
        private const val CUSTOM_BACKGROUND_SOURCE_FILE_NAME = "custom_schedule_background_source"
        private const val MAX_CUSTOM_BACKGROUND_BYTES = 30L * 1024L * 1024L
        private const val MAX_CUSTOM_BACKGROUND_DIMENSION = 2048
        private const val VERSION_URL = "https://raw.giteeusercontent.com/sleexy/onlinedata/raw/master/WeSDAU_Class_Schedule_version.json"
        private const val APK_URL = "https://gitee.com/sleexy/onlinedata/raw/master/ClassSchedule-modern.apk"
        private const val UPDATE_FILE_NAME = "WeSDAU课程表最新版本.apk"
        private const val OFFICIAL_TERM = "2026-2027-1"
        private const val OFFICIAL_TERM_START_YEAR = 2026
        private const val OFFICIAL_TERM_START_MONTH = Calendar.SEPTEMBER
        private const val OFFICIAL_TERM_START_DAY = 7
        private const val SAME_WEEK_COURSE_COLOR_WEIGHT = 1.0
        private const val NEARBY_COURSE_COLOR_WEIGHT = 3.0
        private const val CURRENT_WEEK_COURSE_COLOR_WEIGHT = 6.0
        private const val CURRENT_WEEK_NEARBY_COURSE_COLOR_WEIGHT = 8.0
        private const val MIN_SAME_PAGE_COLOR_DISTANCE = 18.0
        private const val DARK_COURSE_CANDIDATE_COUNT = 64
        private const val DARK_COURSE_BACKGROUND_LIGHTNESS = 0.31
        private const val DARK_COURSE_BACKGROUND_CHROMA = 0.05
        private const val DARK_COURSE_FOREGROUND_LIGHTNESS = 0.78
        private const val DARK_COURSE_FOREGROUND_CHROMA = 0.095
        private val COURSE_COLORS = intArrayOf(
            Color.rgb(130, 173, 247), Color.rgb(237, 184, 119), Color.rgb(120, 225, 208),
            Color.rgb(104, 154, 205), Color.rgb(232, 138, 117), Color.rgb(231, 121, 151),
            Color.rgb(118, 181, 238), Color.rgb(184, 167, 246),
            Color.rgb(205, 142, 190), Color.rgb(132, 176, 212), Color.rgb(222, 174, 104),
            Color.rgb(125, 190, 151), Color.rgb(196, 143, 137), Color.rgb(151, 170, 218),
            Color.rgb(222, 142, 125), Color.rgb(164, 142, 205), Color.rgb(111, 183, 198),
            Color.rgb(211, 157, 116), Color.rgb(145, 193, 151), Color.rgb(191, 151, 210),
            Color.rgb(120, 171, 207), Color.rgb(222, 158, 143), Color.rgb(156, 190, 126),
            Color.rgb(202, 141, 167), Color.rgb(137, 161, 207), Color.rgb(213, 181, 117),
            Color.rgb(132, 193, 184), Color.rgb(185, 153, 210)
        )
        private const val DEFAULT_BACKGROUND_CLARITY = 0.64f
        private const val MIN_BACKGROUND_CLARITY = 0.40f
    }
}

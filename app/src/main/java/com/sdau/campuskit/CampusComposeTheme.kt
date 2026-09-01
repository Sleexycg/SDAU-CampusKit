package com.sdau.campuskit

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal enum class CampusThemeMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStoredValue(value: String?): CampusThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

/**
 * Process-wide theme state shared by the activity root and embedded ComposeView trees.
 * Embedded liquid components therefore update together instead of each reading the
 * system setting independently.
 */
internal object CampusThemeController {
    private const val PREFERENCES_NAME = "offline_login"
    private const val KEY_THEME_MODE = "theme_mode"

    var mode by mutableStateOf(CampusThemeMode.SYSTEM)
        private set

    fun initialize(context: Context) {
        mode = CampusThemeMode.fromStoredValue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME_MODE, CampusThemeMode.SYSTEM.storedValue)
        )
    }

    fun setMode(context: Context, value: CampusThemeMode) {
        if (mode == value) return
        mode = value
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, value.storedValue)
            .apply()
    }

    fun isSystemDark(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    fun isDark(context: Context): Boolean = when (mode) {
        CampusThemeMode.SYSTEM -> isSystemDark(context)
        CampusThemeMode.LIGHT -> false
        CampusThemeMode.DARK -> true
    }
}

@Immutable
internal data class CampusComposeColors(
    val isDark: Boolean,
    val accent: Color,
    val pageBackground: Color,
    val pageGradient: List<Color>,
    val surface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val glassSurface: Color,
    val glassSubtleSurface: Color,
    val glassStrongSurface: Color,
    val glassOutline: Color,
    val divider: Color,
    val selectedSurface: Color,
    val dialogScrim: Color,
    val shadow: Color,
    val error: Color,
    val glassBrightness: Float,
    val glassBlurDp: Float
)

private val LightCampusColors = CampusComposeColors(
    isDark = false,
    accent = Color(0xFF0088FF),
    pageBackground = Color(0xFFF4F6FC),
    pageGradient = listOf(
        Color(0xFFF3F2F9),
        Color(0xFFF0F1F9),
        Color(0xFFEBEFF8),
        Color(0xFFE3EBF7),
        Color(0xFFD9E5F4)
    ),
    surface = Color(0xFFFAFAFA),
    primaryText = Color(0xFF1C2230),
    secondaryText = Color(0xFF666F85),
    tertiaryText = Color(0xFF7B8498),
    glassSurface = Color(0xFFFAFAFA).copy(alpha = 0.40f),
    glassSubtleSurface = Color.White.copy(alpha = 0.26f),
    glassStrongSurface = Color(0xFFF4F6FA).copy(alpha = 0.62f),
    glassOutline = Color.White.copy(alpha = 0.72f),
    divider = Color(0xFF6F9FC5).copy(alpha = 0.54f),
    selectedSurface = Color.Black.copy(alpha = 0.10f),
    dialogScrim = Color(0xFF29293A).copy(alpha = 0.23f),
    shadow = Color.Black.copy(alpha = 0.10f),
    error = Color(0xFFBB3038),
    glassBrightness = 0.20f,
    glassBlurDp = 16f
)

/**
 * The dark glass effects follow AndroidLiquidGlass-kmp (white content/selection,
 * lower brightness and an 8dp blur). The surface tint is deliberately neutral
 * graphite instead of pure black so glass remains visible on a plain dark page.
 */
private val DarkCampusColors = CampusComposeColors(
    isDark = true,
    accent = Color(0xFF0091FF),
    pageBackground = Color(0xFF1B1B1E),
    pageGradient = listOf(
        Color(0xFF242427),
        Color(0xFF212124),
        Color(0xFF1D1D20),
        Color(0xFF1F1F22),
        Color(0xFF232326)
    ),
    surface = Color(0xFF242426),
    primaryText = Color(0xFFF2F2F3),
    secondaryText = Color(0xFFB3B3B8),
    tertiaryText = Color(0xFF8C8C92),
    glassSurface = Color(0xFF2B2B2E).copy(alpha = 0.42f),
    glassSubtleSurface = Color(0xFF29292C).copy(alpha = 0.28f),
    glassStrongSurface = Color(0xFF2D2D30).copy(alpha = 0.68f),
    glassOutline = Color.White.copy(alpha = 0.08f),
    divider = Color.White.copy(alpha = 0.12f),
    selectedSurface = Color.White.copy(alpha = 0.08f),
    dialogScrim = Color(0xFF0D0D0E).copy(alpha = 0.64f),
    shadow = Color.Black.copy(alpha = 0.36f),
    error = Color(0xFFFF6B72),
    glassBrightness = 0f,
    glassBlurDp = 8f
)

internal fun campusComposeColors(dark: Boolean): CampusComposeColors =
    if (dark) DarkCampusColors else LightCampusColors

internal data class CampusAndroidColors(
    val isDark: Boolean,
    val pageBackground: Int,
    val publicPageBackground: Int,
    val surface: Int,
    val cardOutline: Int,
    val fieldDivider: Int,
    val toggleBackground: Int,
    val scheduleBackground: Int,
    val gradient: IntArray,
    val primaryText: Int,
    val secondaryText: Int,
    val accent: Int,
    val primary: Int,
    val primaryContainer: Int,
    val outline: Int,
    val disabledOutline: Int,
    val error: Int
)

private val LightAndroidColors = CampusAndroidColors(
    isDark = false,
    pageBackground = 0xFFF4F6FC.toInt(),
    publicPageBackground = 0xFFF1F3F9.toInt(),
    surface = 0xFFF7F8FC.toInt(),
    cardOutline = 0xFFDCDFe8.toInt(),
    fieldDivider = 0xFFC2C4CC.toInt(),
    toggleBackground = 0xFFEFF1F3.toInt(),
    scheduleBackground = 0xFFEEF2FA.toInt(),
    gradient = intArrayOf(
        0xFFF3F2F9.toInt(),
        0xFFF0F1F9.toInt(),
        0xFFEBEFF8.toInt(),
        0xFFE3EBF7.toInt(),
        0xFFD9E5F4.toInt()
    ),
    primaryText = 0xFF1C2230.toInt(),
    secondaryText = 0xFF666F85.toInt(),
    accent = 0xFF0088FF.toInt(),
    primary = 0xFF4C5CC4.toInt(),
    primaryContainer = 0xFFE8EBFF.toInt(),
    outline = 0xFFDCE1ED.toInt(),
    disabledOutline = 0xFFE8EAEF.toInt(),
    error = 0xFFBB3038.toInt()
)

private val DarkAndroidColors = CampusAndroidColors(
    isDark = true,
    pageBackground = 0xFF1B1B1E.toInt(),
    publicPageBackground = 0xFF1B1B1E.toInt(),
    surface = 0xFF242426.toInt(),
    cardOutline = 0xFF303034.toInt(),
    fieldDivider = 0xFF55555B.toInt(),
    toggleBackground = 0xFF202023.toInt(),
    scheduleBackground = 0xFF1B1B1E.toInt(),
    gradient = intArrayOf(
        0xFF242427.toInt(),
        0xFF212124.toInt(),
        0xFF1D1D20.toInt(),
        0xFF1F1F22.toInt(),
        0xFF232326.toInt()
    ),
    primaryText = 0xFFF2F2F3.toInt(),
    secondaryText = 0xFFB3B3B8.toInt(),
    accent = 0xFF0091FF.toInt(),
    primary = 0xFF8DA0E8.toInt(),
    primaryContainer = 0xFF2B2B30.toInt(),
    outline = 0xFF343438.toInt(),
    disabledOutline = 0xFF29292C.toInt(),
    error = 0xFFFF6B72.toInt()
)

internal fun campusAndroidColors(context: Context): CampusAndroidColors =
    if (CampusThemeController.isDark(context)) DarkAndroidColors else LightAndroidColors

internal val LocalCampusComposeColors = staticCompositionLocalOf { LightCampusColors }

internal object CampusComposeTheme {
    val colors: CampusComposeColors
        @Composable get() = LocalCampusComposeColors.current
}

@Composable
internal fun CampusComposeTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (CampusThemeController.mode) {
        CampusThemeMode.SYSTEM -> systemDark
        CampusThemeMode.LIGHT -> false
        CampusThemeMode.DARK -> true
    }
    CompositionLocalProvider(
        LocalCampusComposeColors provides campusComposeColors(dark),
        content = content
    )
}

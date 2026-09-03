package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

internal sealed interface GradeExamUiState {
    data object Loading : GradeExamUiState
    data class Content(val records: List<RemoteGradeExam>) : GradeExamUiState
    data class Error(val message: String) : GradeExamUiState
}

internal class LiquidGradeExamPageView(
    context: Context,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    textPalette: ScheduleTextPalette,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) : FrameLayout(context) {
    private var uiState by mutableStateOf<GradeExamUiState>(GradeExamUiState.Loading)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(
            composeHostView(context) {
                GradeExamPage(
                    state = uiState,
                    pageBackgroundBitmap = pageBackgroundBitmap,
                    pageBackgroundScrim = pageBackgroundScrim,
                    textPalette = textPalette,
                    onBack = onBack,
                    onRefresh = onRefresh
                )
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun showLoading() { uiState = GradeExamUiState.Loading }
    fun showResult(records: List<RemoteGradeExam>) { uiState = GradeExamUiState.Content(records) }
    fun showError(message: String) { uiState = GradeExamUiState.Error(message) }
}

@Composable
private fun GradeExamPage(
    state: GradeExamUiState,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    textPalette: ScheduleTextPalette,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val theme = CampusComposeTheme.colors
    val backdrop = rememberLayerBackdrop()
    val background: ImageBitmap? = remember(pageBackgroundBitmap) { pageBackgroundBitmap?.asImageBitmap() }
    val primary = Color(textPalette.primary)
    val secondary = Color(textPalette.secondary)
    val shadow = gradeExamTextShadow(textPalette)

    Box(Modifier.fillMaxSize()) {
        PageAlignedBackdropSource(
            backdrop = backdrop,
            pageBackgroundImage = background,
            pageBackgroundScrim = pageBackgroundScrim,
            pageGradient = Brush.linearGradient(theme.pageGradient)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, top = 7.dp, end = 20.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item("grade_exam_header") {
                GradeExamHeader(
                    primary = primary,
                    shadow = shadow,
                    loading = state is GradeExamUiState.Loading,
                    onBack = onBack,
                    onRefresh = onRefresh
                )
            }
            when (state) {
                GradeExamUiState.Loading -> Unit
                is GradeExamUiState.Error -> item("grade_exam_error") {
                    GradeExamMessageCard(backdrop) {
                        androidx.compose.foundation.text.BasicText(
                            "等级考试查询失败",
                            style = TextStyle(primary, 19.sp, FontWeight.Bold, shadow = shadow)
                        )
                        androidx.compose.foundation.text.BasicText(
                            state.message,
                            style = TextStyle(secondary, 13.sp, FontWeight.Medium, shadow = shadow)
                        )
                        CampusLiquidButton(
                            onClick = onRefresh,
                            backdrop = backdrop,
                            style = LiquidButtonStyle.TINTED,
                            enabled = true,
                            allowDragDeformation = false,
                            modifier = Modifier.fillMaxWidth(),
                            height = 48.dp
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                "重新查询",
                                style = TextStyle(Color.White, 16.sp, FontWeight.SemiBold)
                            )
                        }
                    }
                }
                is GradeExamUiState.Content -> {
                    if (state.records.isEmpty()) {
                        item("grade_exam_empty") {
                            GradeExamMessageCard(backdrop) {
                                androidx.compose.foundation.text.BasicText(
                                    "暂无等级考试成绩",
                                    style = TextStyle(primary, 19.sp, FontWeight.Bold, shadow = shadow)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    "查询到的等级考试成绩会显示在这里",
                                    style = TextStyle(secondary, 13.sp, FontWeight.Medium, shadow = shadow)
                                )
                            }
                        }
                    } else {
                        item("grade_exam_count") {
                            androidx.compose.foundation.text.BasicText(
                                "共 ${state.records.size} 条记录",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = TextStyle(secondary, 13.sp, FontWeight.SemiBold, shadow = shadow)
                            )
                        }
                        val orderedRecords = state.records.sortedWith(
                            compareByDescending<RemoteGradeExam> { gradeExamSortKey(it.examTime) }
                                .thenByDescending { it.sequence.toIntOrNull() ?: 0 }
                        )
                        items(orderedRecords, key = { it.id }) { record ->
                            GradeExamCard(
                                backdrop = backdrop,
                                record = record,
                                primary = primary,
                                secondary = secondary,
                                shadow = shadow
                            )
                        }
                    }
                }
            }
        }
        if (state is GradeExamUiState.Loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { GradeExamSpinner() }
        }
    }
}

@Composable
private fun GradeExamHeader(
    primary: Color,
    shadow: Shadow?,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GradeExamHeaderButton(R.drawable.ic_back, "返回", onBack)
        androidx.compose.foundation.text.BasicText(
            "等级考试",
            modifier = Modifier.weight(1f),
            style = TextStyle(primary, 25.sp, FontWeight.Bold, shadow = shadow)
        )
        GradeExamHeaderButton(
            icon = R.drawable.ic_menu_refresh,
            description = "刷新",
            onClick = onRefresh,
            enabled = !loading
        )
    }
}

@Composable
private fun GradeExamHeaderButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(23.dp),
            colorFilter = ColorFilter.tint(CampusComposeTheme.colors.accent.copy(alpha = if (enabled) 1f else 0.45f))
        )
    }
}

@Composable
private fun GradeExamCard(
    backdrop: Backdrop,
    record: RemoteGradeExam,
    primary: Color,
    secondary: Color,
    shadow: Shadow?
) {
    val theme = CampusComposeTheme.colors
    val scoreColor = gradeExamScoreColor(record, theme.isDark)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 108.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(23.dp) },
                effects = {
                    vibrancy()
                    if (theme.isDark) {
                        colorControls(brightness = 0f, saturation = 0.54f)
                    }
                    lens(16.dp.toPx(), 32.dp.toPx())
                },
                shadow = null,
                highlight = { Highlight.Default.copy(alpha = if (theme.isDark) 0.10f else 0.42f) },
                onDrawSurface = {
                    if (theme.isDark) drawRect(theme.glassSurface)
                }
            )
            .padding(horizontal = 19.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            androidx.compose.foundation.text.BasicText(
                gradeExamTitle(record),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(primary, 18.sp, FontWeight.ExtraBold, shadow = shadow)
            )
            val subtitle = gradeExamSubtitle(record)
            if (subtitle.isNotBlank()) {
                androidx.compose.foundation.text.BasicText(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(secondary, 13.sp, FontWeight.SemiBold, shadow = shadow)
                )
            }
            androidx.compose.foundation.text.BasicText(
                gradeExamMonth(record.examTime),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(secondary, 13.sp, FontWeight.Medium, shadow = shadow)
            )
        }
        androidx.compose.foundation.text.BasicText(
            record.score,
            style = TextStyle(scoreColor, 29.sp, FontWeight.ExtraBold, shadow = shadow)
        )
    }
}

@Composable
private fun GradeExamMessageCard(backdrop: Backdrop, content: @Composable ColumnScope.() -> Unit) {
    val theme = CampusComposeTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 210.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(24.dp) },
                effects = { vibrancy(); lens(14.dp.toPx(), 28.dp.toPx()) },
                shadow = null,
                highlight = { Highlight.Default.copy(alpha = if (theme.isDark) 0.16f else 0.50f) },
                onDrawSurface = { drawRect(theme.glassSurface) }
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp, Alignment.CenterVertically),
        content = content
    )
}

@Composable
private fun GradeExamSpinner() {
    val transition = rememberInfiniteTransition(label = "gradeExamLoading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "gradeExamLoadingRotation"
    )
    val accent = CampusComposeTheme.colors.accent
    Canvas(Modifier.size(38.dp)) {
        drawArc(accent.copy(alpha = 0.22f), 0f, 360f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        drawArc(accent, rotation, 102f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun gradeExamScoreColor(record: RemoteGradeExam, dark: Boolean): Color {
    val numeric = record.score.trim().toDoubleOrNull()
    return when {
        numeric == null -> if (dark) Color(0xFF8FC5FF) else Color(0xFF1769C2)
        numeric >= 425 -> if (dark) Color(0xFF6FDB9A) else Color(0xFF16894C)
        else -> if (dark) Color(0xFFE58B91) else Color(0xFFB85B62)
    }
}

private fun gradeExamTitle(record: RemoteGradeExam): String {
    val raw = "${record.examName} ${record.examCategory}"
    val parenthesized = Regex("[（(]([^（）()]*(?:大学英语)?[四六]级考试)[）)]")
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    return when {
        parenthesized.contains("四级考试") -> "大学英语四级考试"
        parenthesized.contains("六级考试") -> "大学英语六级考试"
        raw.contains(Regex("CET\\s*[-_]?\\s*4", RegexOption.IGNORE_CASE)) -> "大学英语四级考试"
        raw.contains(Regex("CET\\s*[-_]?\\s*6", RegexOption.IGNORE_CASE)) -> "大学英语六级考试"
        raw.contains("大学英语四级考试") -> "大学英语四级考试"
        raw.contains("大学英语六级考试") -> "大学英语六级考试"
        else -> record.examName.ifBlank { "等级考试" }
    }
}

private fun gradeExamSubtitle(record: RemoteGradeExam): String {
    val raw = "${record.examName} ${record.examCategory}"
    return if (raw.contains(Regex("(英语[四六]级|CET\\s*[-_]?\\s*[46])", RegexOption.IGNORE_CASE))) {
        "大学英语四六级"
    } else {
        record.examCategory
    }
}

private fun gradeExamMonth(raw: String): String {
    val match = Regex("(20\\d{2})\\D*([01]?\\d)").find(raw) ?: return raw
    val month = match.groupValues[2].toIntOrNull()?.coerceIn(1, 12) ?: return raw
    return "${match.groupValues[1]}-${month.toString().padStart(2, '0')}"
}

private fun gradeExamSortKey(raw: String): Int {
    val match = Regex("(20\\d{2})\\D*([01]?\\d)(?:\\D*([0-3]?\\d))?").find(raw) ?: return 0
    val year = match.groupValues[1].toIntOrNull() ?: return 0
    val month = match.groupValues[2].toIntOrNull()?.coerceIn(1, 12) ?: 0
    val day = match.groupValues.getOrNull(3)?.toIntOrNull()?.coerceIn(0, 31) ?: 0
    return year * 10_000 + month * 100 + day
}

private fun gradeExamTextShadow(palette: ScheduleTextPalette): Shadow? {
    if (!palette.adaptive || palette.halo == android.graphics.Color.TRANSPARENT) return null
    return Shadow(color = Color(palette.halo), blurRadius = 3f)
}

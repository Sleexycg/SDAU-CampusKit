package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import java.util.Calendar
import kotlin.math.roundToInt

internal sealed interface TrainingPlanUiState {
    data object Loading : TrainingPlanUiState
    data class Content(val result: RemoteTrainingPlanResult) : TrainingPlanUiState
    data class Error(val message: String) : TrainingPlanUiState
}

internal class LiquidTrainingPlanPageView(
    context: Context,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    textPalette: ScheduleTextPalette,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) : FrameLayout(context) {
    private var uiState by mutableStateOf<TrainingPlanUiState>(TrainingPlanUiState.Loading)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(
            composeHostView(context) {
                TrainingPlanPage(
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

    fun showLoading() {
        uiState = TrainingPlanUiState.Loading
    }

    fun showResult(result: RemoteTrainingPlanResult) {
        uiState = TrainingPlanUiState.Content(result)
    }

    fun showError(message: String) {
        uiState = TrainingPlanUiState.Error(message)
    }
}

@Composable
private fun TrainingPlanPage(
    state: TrainingPlanUiState,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    textPalette: ScheduleTextPalette,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val backdrop = rememberLayerBackdrop()
    val pageBackgroundImage: ImageBitmap? = remember(pageBackgroundBitmap) {
        pageBackgroundBitmap?.asImageBitmap()
    }
    val pageGradient = Brush.linearGradient(themeColors.pageGradient)
    val primary = Color(textPalette.primary)
    val secondary = Color(textPalette.secondary)
    val shadow = trainingPlanTextShadow(textPalette)
    val scrollState = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        PageAlignedBackdropSource(
            backdrop = backdrop,
            pageBackgroundImage = pageBackgroundImage,
            pageBackgroundScrim = pageBackgroundScrim,
            pageGradient = pageGradient
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, top = 7.dp, end = 20.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            TrainingPlanHeader(
                backdrop = backdrop,
                primary = primary,
                shadow = shadow,
                refreshing = state is TrainingPlanUiState.Loading,
                onBack = onBack,
                onRefresh = onRefresh
            )
            when (state) {
                TrainingPlanUiState.Loading -> Unit
                is TrainingPlanUiState.Error -> TrainingPlanErrorCard(
                    backdrop = backdrop,
                    message = state.message,
                    primary = primary,
                    secondary = secondary,
                    shadow = shadow,
                    onRetry = onRefresh
                )
                is TrainingPlanUiState.Content -> TrainingPlanContent(
                    backdrop = backdrop,
                    result = state.result,
                    hasCustomBackground = pageBackgroundBitmap != null,
                    primary = primary,
                    secondary = secondary,
                    shadow = shadow
                )
            }
        }
        if (state is TrainingPlanUiState.Loading) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                TrainingPlanSpinner()
            }
        }
    }
}

@Composable
private fun TrainingPlanHeader(
    backdrop: Backdrop,
    primary: Color,
    shadow: Shadow?,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "返回",
                modifier = Modifier
                    .size(44.dp)
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                colorFilter = ColorFilter.tint(CampusComposeTheme.colors.accent)
            )
        }
        BasicText(
            "培养方案",
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = primary,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                shadow = shadow
            )
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer { alpha = if (refreshing) 0.45f else 1f }
                .clickable(enabled = !refreshing, onClick = onRefresh),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_menu_refresh),
                contentDescription = "刷新",
                modifier = Modifier
                    .size(44.dp)
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                colorFilter = ColorFilter.tint(CampusComposeTheme.colors.accent)
            )
        }
    }
}

@Composable
private fun TrainingPlanContent(
    backdrop: Backdrop,
    result: RemoteTrainingPlanResult,
    hasCustomBackground: Boolean,
    primary: Color,
    secondary: Color,
    shadow: Shadow?
) {
    val orderedCategories = listOf(
        "学科基础课组",
        "通识必修课",
        "实践教学环节",
        "专业核心课",
        "专业方向课",
        "艺术审美类",
        "耕读教育类",
        "体育健康类",
        "四史教育类"
    )
    val order = orderedCategories.withIndex().associate { it.value to it.index }
    val items = result.items
        .filterNot { it.category == "其它" }
        .sortedBy { order[it.category] ?: Int.MAX_VALUE }

    TrainingPlanSummaryCard(
        backdrop = backdrop,
        summary = result.summary,
        hasCustomBackground = hasCustomBackground,
        primary = primary,
        secondary = secondary,
        shadow = shadow
    )
    items.forEach { item ->
        TrainingPlanCategoryCard(
            backdrop = backdrop,
            item = item,
            hasCustomBackground = hasCustomBackground,
            primary = primary,
            secondary = secondary,
            shadow = shadow
        )
    }
}

@Composable
private fun TrainingPlanSummaryCard(
    backdrop: Backdrop,
    summary: RemoteTrainingPlanSummary,
    hasCustomBackground: Boolean,
    primary: Color,
    secondary: Color,
    shadow: Shadow?
) {
    val themeColors = CampusComposeTheme.colors
    val required = summary.requiredCredits.toDoubleOrNull() ?: 0.0
    val completed = summary.completedCredits.toDoubleOrNull() ?: 0.0
    val progress = if (required > 0) (completed / required).coerceIn(0.0, 1.0) else 0.0
    val shape = RoundedRectangle(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    lens(16.dp.toPx(), 32.dp.toPx())
                },
                shadow = null,
                highlight = {
                    Highlight.Default.copy(alpha = if (themeColors.isDark) 0.18f else 0.56f)
                },
                onDrawSurface = {
                    drawRect(
                        when {
                            hasCustomBackground && themeColors.isDark -> Color.White.copy(alpha = 0.14f)
                            hasCustomBackground -> Color.White.copy(alpha = 0.22f)
                            themeColors.isDark -> Color.White.copy(alpha = 0.085f)
                            else -> Color.White.copy(alpha = 0.15f)
                        }
                    )
                }
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "总体完成度",
                modifier = Modifier.weight(1f),
                style = TextStyle(primary, 19.sp, FontWeight.Bold, shadow = shadow)
            )
            BasicText(
                "${(progress * 100).roundToInt()}%",
                style = TextStyle(themeColors.accent, 27.sp, FontWeight.ExtraBold, shadow = shadow)
            )
        }
        TrainingPlanProgress(progress.toFloat(), themeColors.accent)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PlanMetric("要求", summary.requiredCredits, primary, secondary, shadow, Modifier.weight(1f))
            PlanMetric("已修", summary.completedCredits, primary, secondary, shadow, Modifier.weight(1f))
            PlanMetric("正修", summary.currentCredits, primary, secondary, shadow, Modifier.weight(1f))
            PlanMetric("未修", summary.remainingCredits, primary, secondary, shadow, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TrainingPlanCategoryCard(
    backdrop: Backdrop,
    item: RemoteTrainingPlanItem,
    hasCustomBackground: Boolean,
    primary: Color,
    secondary: Color,
    shadow: Shadow?
) {
    val themeColors = CampusComposeTheme.colors
    val required = item.requiredCredits.toDoubleOrNull() ?: 0.0
    val completed = item.completedCredits.toDoubleOrNull() ?: 0.0
    val progress = if (required > 0) (completed / required).coerceIn(0.0, 1.0) else 0.0
    val accent = trainingPlanCategoryColor(item.category, themeColors.isDark)
    val categoryCodes = trainingPlanCategoryCodes(item.category)
    val subjectVisibleCategories = setOf(
        "学科基础课组", "通识必修课", "实践教学环节", "专业核心课",
        "专业方向课", "艺术审美类", "耕读教育类", "四史教育类", "体育健康类"
    )
    val hideHistoricalCompletedCategories = setOf(
        "学科基础课组", "通识必修课", "实践教学环节", "专业核心课"
    )
    val currentTerm = remember { currentTrainingPlanTerm() }
    val visibleSubjects = remember(item.subjects, item.category, currentTerm) {
        val creditFiltered = item.subjects.filter(::hasVisibleTrainingPlanCredit)
        val categoryFiltered = if (item.category in hideHistoricalCompletedCategories) {
            creditFiltered.filter { subject ->
                trainingPlanSubjectState(subject) != TrainingPlanSubjectState.COMPLETED ||
                    subject.term.trim() == currentTerm
            }
        } else {
            creditFiltered
        }
        sortTrainingPlanSubjects(
            categoryFiltered,
            currentTerm
        )
    }
    val expandable = visibleSubjects.isNotEmpty() && item.category in subjectVisibleCategories
    var expanded by remember(item.category) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "trainingPlanArrow"
    )
    val shape = RoundedRectangle(21.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    lens(16.dp.toPx(), 32.dp.toPx())
                },
                shadow = null,
                highlight = {
                    Highlight.Default.copy(alpha = if (themeColors.isDark) 0.18f else 0.54f)
                },
                onDrawSurface = {
                    drawRect(
                        when {
                            hasCustomBackground && themeColors.isDark -> Color.White.copy(alpha = 0.14f)
                            hasCustomBackground -> Color.White.copy(alpha = 0.22f)
                            themeColors.isDark -> Color.White.copy(alpha = 0.085f)
                            else -> Color.White.copy(alpha = 0.15f)
                        }
                    )
                }
            )
            .then(
                if (expandable) Modifier.clickable(
                    interactionSource = null,
                    indication = null
                ) { expanded = !expanded } else Modifier
            )
            .padding(horizontal = 17.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 5.dp, height = 34.dp)
                    .clip(Capsule())
                    .background(accent)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    item.category,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(primary, 18.sp, FontWeight.Bold, shadow = shadow)
                )
                categoryCodes.forEach { code ->
                    TrainingPlanCategoryTag(code = code, accent = accent)
                }
            }
            BasicText(
                "${(progress * 100).roundToInt()}%",
                style = TextStyle(accent, 18.sp, FontWeight.ExtraBold, shadow = shadow)
            )
            if (expandable) {
                Canvas(
                    Modifier
                        .padding(start = 8.dp)
                        .size(width = 26.dp, height = 22.dp)
                        .graphicsLayer { rotationZ = arrowRotation }
                ) {
                    val strokeWidth = 2.dp.toPx()
                    val center = Offset(size.width * 0.5f, size.height * 0.62f)
                    drawLine(
                        color = accent,
                        start = Offset(size.width * 0.28f, size.height * 0.40f),
                        end = center,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = accent,
                        start = center,
                        end = Offset(size.width * 0.72f, size.height * 0.40f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        TrainingPlanProgress(progress.toFloat(), accent)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PlanMetric("要求", item.requiredCredits, primary, secondary, shadow, Modifier.weight(1f))
            PlanMetric("已修", item.completedCredits, primary, secondary, shadow, Modifier.weight(1f))
            PlanMetric("正修", item.currentCredits, primary, secondary, shadow, Modifier.weight(1f))
            PlanMetric("未修", item.remainingCredits, primary, secondary, shadow, Modifier.weight(1f))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(durationMillis = 110)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 145, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(durationMillis = 85))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(themeColors.divider)
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    visibleSubjects.forEachIndexed { index, subject ->
                        TrainingPlanSubjectRow(subject, primary, secondary, shadow)
                        if (index < visibleSubjects.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(themeColors.divider.copy(alpha = 0.58f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingPlanSubjectRow(
    subject: RemoteTrainingPlanSubject,
    primary: Color,
    secondary: Color,
    shadow: Shadow?
) {
    val themeColors = CampusComposeTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                subject.courseName.ifBlank { "未命名课程" },
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(primary, 16.sp, FontWeight.SemiBold, shadow = shadow)
            )
            val score = subject.score.trim().takeUnless { it.isBlank() || it == "-" || it == "--" }
            val statusText = score ?: subject.status.ifBlank { "未修读" }
            BasicText(
                statusText,
                modifier = Modifier.padding(start = 10.dp),
                style = TextStyle(
                    trainingPlanSubjectStatusColor(subject, themeColors.isDark),
                    15.sp,
                    FontWeight.ExtraBold,
                    shadow = shadow
                )
            )
        }
        val details = listOf(
            subject.term,
            subject.courseCode,
            subject.credit.takeIf(String::isNotBlank)?.let { "$it 学分" }
        ).filterNotNull().filter(String::isNotBlank).joinToString(" · ")
        if (details.isNotBlank()) {
            BasicText(
                details,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(secondary, 12.sp, FontWeight.Medium, shadow = shadow)
            )
        }
    }
}

@Composable
private fun TrainingPlanCategoryTag(code: String, accent: Color) {
    BasicText(
        code,
        maxLines = 1,
        style = TextStyle(accent, 10.sp, FontWeight.ExtraBold)
    )
}

private fun trainingPlanCategoryCodes(category: String): List<String> = when (category) {
    "专业方向课" -> listOf("XF")
    "艺术审美类" -> listOf("XY")
    "耕读教育类" -> listOf("XR", "XG")
    "四史教育类" -> listOf("XD")
    "体育健康类" -> listOf("XT")
    "实践教学环节" -> listOf("BS")
    "通识必修课", "学科基础课组", "专业核心课" -> listOf("BK")
    else -> emptyList()
}

private enum class TrainingPlanSubjectState { COMPLETED, CURRENT, UNCOMPLETED }

private fun trainingPlanSubjectState(subject: RemoteTrainingPlanSubject): TrainingPlanSubjectState {
    val status = subject.status.trim()
    return when {
        status.contains("未修") || status.contains("未完成") || status.contains("待修") ||
            status.contains("未通过") || status.contains("未读") -> TrainingPlanSubjectState.UNCOMPLETED
        status.contains("正修") || status.contains("在修") || status.contains("修读中") ||
            status.contains("在读") -> TrainingPlanSubjectState.CURRENT
        status.contains("已修") || status.contains("已完成") || status.contains("通过") ||
            status.contains("及格") || subject.score.toDoubleOrNull() != null -> TrainingPlanSubjectState.COMPLETED
        else -> TrainingPlanSubjectState.UNCOMPLETED
    }
}

private fun trainingPlanSubjectStatusColor(subject: RemoteTrainingPlanSubject, isDark: Boolean): Color {
    return when (trainingPlanSubjectState(subject)) {
        TrainingPlanSubjectState.COMPLETED -> if (isDark) Color(0xFF5ADC91) else Color(0xFF178A55)
        TrainingPlanSubjectState.CURRENT -> {
            if (isDark) Color(0xFF62B5FF) else Color(0xFF147FD1)
        }
        TrainingPlanSubjectState.UNCOMPLETED -> if (isDark) Color(0xFFFF747D) else Color(0xFFD44850)
    }
}

private fun hasVisibleTrainingPlanCredit(subject: RemoteTrainingPlanSubject): Boolean {
    val credit = subject.credit.toDoubleOrNull() ?: return true
    return credit > 0.0
}

private fun sortTrainingPlanSubjects(
    subjects: List<RemoteTrainingPlanSubject>,
    currentTerm: String
): List<RemoteTrainingPlanSubject> {
    val currentOrder = trainingPlanTermOrder(currentTerm)
    return subjects.sortedWith(
        compareBy<RemoteTrainingPlanSubject>(
            { subject ->
                val state = trainingPlanSubjectState(subject)
                val subjectOrder = trainingPlanTermOrder(subject.term)
                when (state) {
                    TrainingPlanSubjectState.COMPLETED -> 0
                    TrainingPlanSubjectState.CURRENT -> 1
                    TrainingPlanSubjectState.UNCOMPLETED -> {
                        if (subjectOrder < 0 ||
                            (currentOrder >= 0 && subjectOrder < currentOrder)
                        ) 3 else 2
                    }
                }
            },
            { subject -> trainingPlanTermOrder(subject.term).takeIf { it >= 0 } ?: Int.MAX_VALUE },
            { subject -> subject.courseName }
        )
    )
}

private fun trainingPlanTermOrder(term: String): Int {
    val match = Regex("^(\\d{4})-\\d{4}-([12])$").matchEntire(term.trim()) ?: return -1
    val startYear = match.groupValues[1].toIntOrNull() ?: return -1
    val semester = match.groupValues[2].toIntOrNull() ?: return -1
    return startYear * 2 + semester - 1
}

private fun currentTrainingPlanTerm(): String {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return when {
        month > 7 || (month == 7 && day >= 20) -> "$year-${year + 1}-1"
        month > 2 || (month == 2 && day >= 16) -> "${year - 1}-$year-2"
        else -> "${year - 1}-$year-1"
    }
}

@Composable
private fun PlanMetric(
    label: String,
    value: String,
    primary: Color,
    secondary: Color,
    shadow: Shadow?,
    modifier: Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            value.ifBlank { "0" },
            maxLines = 1,
            style = TextStyle(primary, 17.sp, FontWeight.Bold, shadow = shadow)
        )
        BasicText(
            label,
            maxLines = 1,
            style = TextStyle(secondary, 11.sp, FontWeight.Medium, shadow = shadow)
        )
    }
}

@Composable
private fun TrainingPlanProgress(progress: Float, accent: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(Capsule())
            .background(CampusComposeTheme.colors.selectedSurface)
    ) {
        if (progress > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0.015f, 1f))
                    .height(7.dp)
                    .clip(Capsule())
                    .background(accent)
            )
        }
    }
}

@Composable
private fun TrainingPlanErrorCard(
    backdrop: Backdrop,
    message: String,
    primary: Color,
    secondary: Color,
    shadow: Shadow?,
    onRetry: () -> Unit
) {
    TrainingPlanMessageCard(backdrop) {
        BasicText(
            "培养方案读取失败",
            style = TextStyle(primary, 19.sp, FontWeight.Bold, shadow = shadow)
        )
        BasicText(
            message,
            style = TextStyle(secondary, 13.sp, FontWeight.Medium, shadow = shadow)
        )
        CampusLiquidButton(
            onClick = onRetry,
            backdrop = backdrop,
            style = LiquidButtonStyle.TINTED,
            enabled = true,
            allowDragDeformation = false,
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp
        ) {
            BasicText("重新查询", style = TextStyle(Color.White, 16.sp, FontWeight.SemiBold))
        }
    }
}

@Composable
private fun TrainingPlanMessageCard(
    backdrop: Backdrop,
    content: @Composable ColumnScope.() -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 210.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(24.dp) },
                effects = {
                    colorControls(
                        brightness = if (themeColors.isDark) 0f else 0.12f,
                        saturation = if (themeColors.isDark) 0.50f else 0.76f
                    )
                    blur((if (themeColors.isDark) 9.dp else 14.dp).toPx())
                    lens(14.dp.toPx(), 28.dp.toPx())
                },
                shadow = null,
                highlight = {
                    Highlight.Default.copy(alpha = if (themeColors.isDark) 0.10f else 0.44f)
                },
                onDrawSurface = { drawRect(themeColors.glassSurface) }
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp, Alignment.CenterVertically),
        content = content
    )
}

@Composable
private fun TrainingPlanSpinner() {
    val transition = rememberInfiniteTransition(label = "trainingPlanLoading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "trainingPlanLoadingRotation"
    )
    val accent = CampusComposeTheme.colors.accent
    Canvas(Modifier.size(38.dp)) {
        drawArc(
            color = accent.copy(alpha = 0.22f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = accent,
            startAngle = rotation,
            sweepAngle = 102f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun trainingPlanCategoryColor(category: String, dark: Boolean): Color {
    val light = when (category) {
        "专业方向课" -> Color(0xFF0D8E7F)
        "专业核心课" -> Color(0xFF2F7FD6)
        "学科基础课组" -> Color(0xFF7A5AF8)
        "实践教学环节" -> Color(0xFFE07A1F)
        "通识必修课" -> Color(0xFFD94874)
        "耕读教育类" -> Color(0xFF16A34A)
        "四史教育类" -> Color(0xFF8B5CF6)
        "体育健康类" -> Color(0xFF0EA5A4)
        "艺术审美类" -> Color(0xFFD946EF)
        else -> Color(0xFF5C82C8)
    }
    return if (dark) light.copy(red = (light.red + 0.12f).coerceAtMost(1f), green = (light.green + 0.12f).coerceAtMost(1f), blue = (light.blue + 0.12f).coerceAtMost(1f)) else light
}

private fun trainingPlanTextShadow(palette: ScheduleTextPalette): Shadow? {
    if (!palette.adaptive || palette.halo == android.graphics.Color.TRANSPARENT) return null
    return Shadow(color = Color(palette.halo), blurRadius = 3f)
}

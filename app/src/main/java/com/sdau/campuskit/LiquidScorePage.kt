package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.roundToInt
/** Score list, metrics, term action, and export control. */
/** View 页面和参考项目 Compose Liquid Dialog 之间的桥接层。 */
internal fun createScoreLiquidScrollPageView(
    context: Context,
    result: RemoteScoreResult,
    scoreColors: List<Int>,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    textPalette: ScheduleTextPalette,
    termSelectorExpanded: State<Boolean>,
    scoreUpdatesEnabled: State<Boolean>,
    onTermClick: (android.graphics.Rect) -> Unit,
    onScoreReminderClick: () -> Unit,
    onScoreClick: (RemoteScore) -> Unit,
    onExport: () -> Unit
): View = composeHostView(context) {
    ScoreLiquidScrollPage(
        result = result,
        scoreColors = scoreColors,
        pageBackgroundBitmap = pageBackgroundBitmap,
        pageBackgroundScrim = pageBackgroundScrim,
        textPalette = textPalette,
        termSelectorExpanded = termSelectorExpanded.value,
        scoreUpdatesEnabled = scoreUpdatesEnabled.value,
        onTermClick = onTermClick,
        onScoreReminderClick = onScoreReminderClick,
        onScoreClick = onScoreClick,
        onExport = onExport
    )
}

internal fun createScoreReminderButtonView(
    context: Context,
    textPalette: ScheduleTextPalette,
    scoreUpdatesEnabled: State<Boolean>,
    onClick: () -> Unit
): View = composeHostView(context) {
    ScoreReminderButton(
        enabled = scoreUpdatesEnabled.value,
        textPalette = textPalette,
        onClick = onClick
    )
}

internal fun createScoreTermSelectorView(
    context: Context,
    term: String,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    textPalette: ScheduleTextPalette,
    termSelectorExpanded: State<Boolean>,
    onTermClick: (android.graphics.Rect) -> Unit
): View = composeHostView(context) {
    val themeColors = CampusComposeTheme.colors
    val backdrop = rememberLayerBackdrop()
    val pageBackgroundImage = remember(pageBackgroundBitmap) {
        pageBackgroundBitmap?.asImageBitmap()
    }
    val pageGradient = Brush.linearGradient(themeColors.pageGradient)
    Box {
        PageAlignedBackdropSource(
            backdrop = backdrop,
            pageBackgroundImage = pageBackgroundImage,
            pageBackgroundScrim = pageBackgroundScrim,
            pageGradient = pageGradient,
            // The source must follow the selector after Box measurement. A
            // fillMaxSize child would otherwise make this wrap-content bridge
            // consume the whole native score page while switching terms.
            modifier = Modifier.matchParentSize()
        )
        ScoreTermSelector(
            term = term,
            backdrop = backdrop,
            expanded = termSelectorExpanded.value,
            textPalette = textPalette,
            onClick = onTermClick
        )
    }
}

@Composable
private fun ScoreTermSelector(
    term: String,
    backdrop: Backdrop,
    expanded: Boolean,
    textPalette: ScheduleTextPalette,
    onClick: (android.graphics.Rect) -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val textPrimary = Color(textPalette.primary)
    val textSecondary = Color(textPalette.secondary)
    val textShadow = scheduleTextShadow(textPalette)
    val termFontWeight = if (textPalette.adaptive) FontWeight.Bold else FontWeight.Normal
    var bounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "scoreTermArrowRotation"
    )

    Row(
        Modifier
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                bounds = android.graphics.Rect(
                    position.x.roundToInt(),
                    position.y.roundToInt(),
                    (position.x + coordinates.size.width).roundToInt(),
                    (position.y + coordinates.size.height).roundToInt()
                )
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(14.dp) },
                effects = {
                    vibrancy()
                    if (themeColors.isDark) {
                        colorControls(brightness = 0f, saturation = 0.54f)
                    }
                    blur(4.dp.toPx())
                    lens(8.dp.toPx(), 16.dp.toPx())
                },
                shadow = null,
                highlight = {
                    Highlight.Default.copy(alpha = if (themeColors.isDark) 0.10f else 0.46f)
                },
                onDrawSurface = {
                    drawRect(
                        if (themeColors.isDark) Color.White.copy(alpha = 0.08f)
                        else Color.White.copy(alpha = 0.16f)
                    )
                }
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = { bounds?.let(onClick) }
            )
            .heightIn(min = 36.dp)
            .padding(start = 11.dp, top = 8.dp, end = 9.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            term,
            style = TextStyle(
                color = textPrimary,
                fontSize = 12.sp,
                fontWeight = termFontWeight,
                shadow = textShadow
            )
        )
        Canvas(
            modifier = Modifier
                .padding(start = 5.dp)
                .size(width = 15.dp, height = 20.dp)
                .graphicsLayer { rotationZ = arrowRotation }
        ) {
            val strokeWidth = 1.8.dp.toPx()
            val center = Offset(size.width * 0.5f, size.height * 0.62f)
            drawLine(
                color = textSecondary,
                start = Offset(size.width * 0.20f, size.height * 0.36f),
                end = center,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = textSecondary,
                start = center,
                end = Offset(size.width * 0.80f, size.height * 0.36f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ScoreLiquidScrollPage(
    result: RemoteScoreResult,
    scoreColors: List<Int>,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    textPalette: ScheduleTextPalette,
    termSelectorExpanded: Boolean,
    scoreUpdatesEnabled: Boolean,
    onTermClick: (android.graphics.Rect) -> Unit,
    onScoreReminderClick: () -> Unit,
    onScoreClick: (RemoteScore) -> Unit,
    onExport: () -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val backdrop = rememberLayerBackdrop()
    val pageBackgroundImage = remember(pageBackgroundBitmap) {
        pageBackgroundBitmap?.asImageBitmap()
    }
    val secondaryFontWeight = if (textPalette.adaptive) FontWeight.Bold else FontWeight.Normal
    val scoreMetricFontWeight = if (textPalette.adaptive) FontWeight.ExtraBold else FontWeight.Bold
    val textPrimary = Color(textPalette.primary)
    val textSecondary = Color(textPalette.secondary)
    val textShadow = scheduleTextShadow(textPalette)
    val pageGradient = Brush.linearGradient(themeColors.pageGradient)
    Box(Modifier.fillMaxSize()) {
        // This is the actual source sampled by every card and by the export button.
        PageAlignedBackdropSource(
            backdrop = backdrop,
            pageBackgroundImage = pageBackgroundImage,
            pageBackgroundScrim = pageBackgroundScrim,
            pageGradient = pageGradient
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 96.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    "成绩",
                    style = TextStyle(
                        color = textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = textShadow
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScoreReminderButton(
                        enabled = scoreUpdatesEnabled,
                        textPalette = textPalette,
                        onClick = onScoreReminderClick
                    )
                    ScoreTermSelector(
                        term = result.term,
                        backdrop = backdrop,
                        expanded = termSelectorExpanded,
                        textPalette = textPalette,
                        onClick = onTermClick
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(24.dp) },
                        effects = {
                            vibrancy()
                            if (themeColors.isDark) {
                                colorControls(brightness = 0f, saturation = 0.54f)
                            }
                            blur(5.dp.toPx())
                            lens(12.dp.toPx(), 24.dp.toPx())
                        },
                        shadow = null,
                        highlight = {
                            Highlight.Default.copy(alpha = if (themeColors.isDark) 0.10f else 0.50f)
                        },
                        onDrawSurface = {
                            drawRect(
                                if (themeColors.isDark) themeColors.glassSurface
                                else Color.White.copy(alpha = 0.18f)
                            )
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreMetric(
                    label = "平均成绩",
                    value = result.averageScore,
                    valueColor = Color(0xFFF56C7E),
                    textSecondary = textSecondary,
                    secondaryFontWeight = secondaryFontWeight,
                    secondaryShadow = textShadow,
                    valueFontWeight = scoreMetricFontWeight
                )
                ScoreMetric(
                    label = "平均绩点",
                    value = result.averageCreditGpa,
                    valueColor = Color(0xFF838CC7),
                    textSecondary = textSecondary,
                    secondaryFontWeight = secondaryFontWeight,
                    secondaryShadow = textShadow,
                    valueFontWeight = scoreMetricFontWeight
                )
                ScoreMetric(
                    label = "总学分",
                    value = result.totalCredits,
                    valueColor = if (themeColors.isDark) Color(0xFF8EA4FF) else Color(0xFF324099),
                    textSecondary = textSecondary,
                    secondaryFontWeight = secondaryFontWeight,
                    secondaryShadow = textShadow,
                    valueFontWeight = scoreMetricFontWeight
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                result.records.forEachIndexed { index, record ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedRectangle(20.dp) },
                                effects = {
                                    vibrancy()
                                    if (themeColors.isDark) {
                                        colorControls(brightness = 0f, saturation = 0.54f)
                                    }
                                    lens(16.dp.toPx(), 32.dp.toPx())
                                },
                                highlight = {
                                    Highlight.Default.copy(alpha = if (themeColors.isDark) 0.10f else 0.42f)
                                },
                                onDrawSurface = {
                                    if (themeColors.isDark) drawRect(themeColors.glassSurface)
                                }
                            )
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                role = Role.Button,
                                onClick = { onScoreClick(record) }
                            )
                            .padding(horizontal = 17.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            BasicText(
                                record.courseName.ifBlank { "未命名课程" },
                                modifier = Modifier.padding(bottom = 7.dp),
                                style = TextStyle(
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    shadow = textShadow
                                )
                            )
                            val details = buildList {
                                if (record.courseCode.isNotBlank()) add(record.courseCode)
                                if (record.credit.isNotBlank()) add("${record.credit} 学分")
                            }.joinToString("  ·  ")
                            BasicText(
                                details.ifBlank { "课程成绩" },
                                style = TextStyle(
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = secondaryFontWeight,
                                    shadow = textShadow
                                )
                            )
                        }
                        Box(
                            Modifier.size(width = 58.dp, height = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                record.score.ifBlank { "-" },
                                style = TextStyle(
                                    Color(scoreColors.getOrElse(index) { 0xFF324099.toInt() }),
                                    22.sp,
                                    scoreMetricFontWeight
                                )
                            )
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 14.dp)
                .size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            SurfaceLiquidExportButton(backdrop = backdrop, onClick = onExport)
        }
    }
}

@Composable
private fun ScoreReminderButton(
    enabled: Boolean,
    textPalette: ScheduleTextPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = CampusComposeTheme.colors
    Row(
        modifier
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .size(38.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_push_on),
            contentDescription = if (enabled) "成绩更新提醒已开启" else "成绩更新提醒已关闭",
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(themeColors.accent)
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ScoreMetric(
    label: String,
    value: String,
    valueColor: Color,
    textSecondary: Color,
    secondaryFontWeight: FontWeight,
    secondaryShadow: Shadow?,
    valueFontWeight: FontWeight
) {
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            value.ifBlank { "-" },
            modifier = Modifier.padding(bottom = 5.dp),
            style = TextStyle(valueColor, 21.sp, valueFontWeight)
        )
        BasicText(
            label,
            style = TextStyle(
                color = textSecondary,
                fontSize = 11.sp,
                fontWeight = secondaryFontWeight,
                shadow = secondaryShadow
            )
        )
    }
}

private fun scheduleTextShadow(textPalette: ScheduleTextPalette): Shadow? =
    if (textPalette.adaptive) {
        Shadow(
            color = Color(textPalette.halo),
            offset = Offset.Zero,
            blurRadius = 2.6f
        )
    } else null

@Composable
private fun SurfaceLiquidExportButton(
    backdrop: com.kyant.backdrop.Backdrop,
    onClick: () -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "scoreExportLiquidScale"
    )
    Box(
        Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                highlight = {
                    Highlight.Default.copy(alpha = if (themeColors.isDark) 0.12f else 0.72f)
                },
                onDrawSurface = { drawRect(themeColors.glassSurface) }
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = "保存成绩图片" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) {
            val color = themeColors.accent
            val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            val cx = size.width / 2f
            drawLine(color, Offset(cx, size.height * 0.13f), Offset(cx, size.height * 0.62f), stroke.width, stroke.cap)
            drawLine(color, Offset(cx, size.height * 0.62f), Offset(size.width * 0.31f, size.height * 0.43f), stroke.width, stroke.cap)
            drawLine(color, Offset(cx, size.height * 0.62f), Offset(size.width * 0.69f, size.height * 0.43f), stroke.width, stroke.cap)
            drawLine(color, Offset(size.width * 0.20f, size.height * 0.84f), Offset(size.width * 0.80f, size.height * 0.84f), stroke.width, stroke.cap)
        }
    }
}

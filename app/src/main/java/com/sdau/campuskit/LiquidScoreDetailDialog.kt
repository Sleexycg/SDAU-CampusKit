package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
/** Per-course score details, loading state, and error state. */
internal class LiquidScoreDetailDialogView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    courseName: String,
    onDismiss: () -> Unit
) : FrameLayout(context) {
    private var detailState by mutableStateOf<RemoteScoreDetail?>(null)
    private var errorState by mutableStateOf<String?>(null)
    private var totalScoreColorState by mutableStateOf(0xFF2A855B.toInt())

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            composeHostView(context) {
                LiquidScoreDetailDialog(
                    pageSnapshot = pageSnapshot,
                    courseName = courseName,
                    detail = detailState,
                    errorMessage = errorState,
                    totalScoreColor = totalScoreColorState,
                    onDismiss = onDismiss
                )
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun showDetail(detail: RemoteScoreDetail, totalScoreColor: Int) {
        errorState = null
        totalScoreColorState = totalScoreColor
        detailState = detail
    }

    fun showError(message: String) {
        detailState = null
        errorState = message
    }

    fun releaseSnapshot() {
        releaseDialogSnapshot(pageSnapshot) { pageSnapshot = null }
    }
}

/** Course details and editing share one sampled-page liquid glass dialog. */
@Composable
private fun LiquidScoreDetailDialog(
    pageSnapshot: Bitmap?,
    courseName: String,
    detail: RemoteScoreDetail?,
    errorMessage: String?,
    totalScoreColor: Int,
    onDismiss: () -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val contentColor = themeColors.primaryText
    val secondaryColor = contentColor.copy(alpha = 0.66f)
    val accentColor = themeColors.accent
    val containerColor = themeColors.glassSurface
    val dimColor = themeColors.dialogScrim
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()
    val title = courseName.ifBlank { "课程成绩" }
    val titleTextSize = ((212f / title.length.coerceAtLeast(1)).coerceIn(9f, 20f) + 1f).sp

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            if (snapshotImage != null) {
                Image(
                    bitmap = snapshotImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                Box(Modifier.fillMaxSize().background(themeColors.pageBackground))
            }
            Box(Modifier.fillMaxSize().background(dimColor))
        }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onDismiss
                )
        )
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .clip(RoundedRectangle(24.dp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(24.dp) },
                    effects = {
                        colorControls(
                            brightness = if (themeColors.isDark) 0f else 0.15f,
                            saturation = if (themeColors.isDark) 0.54f else 0.72f
                        )
                        blur((if (themeColors.isDark) 8.dp else 9.dp).toPx())
                        lens(16.dp.toPx(), 32.dp.toPx())
                    },
                    shadow = null,
                    highlight = {
                        Highlight.Default.copy(alpha = if (themeColors.isDark) 0.10f else 0.50f)
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = {}
                )
                .animateContentSize()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp, top = 16.dp, end = 20.dp, bottom = 10.dp)
            ) {
                BasicText(
                    "成绩构成",
                    style = TextStyle(secondaryColor, 13.sp, FontWeight.Medium)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(width = 5.dp, height = 28.dp)
                            .clip(RoundedRectangle(3.dp))
                            .background(accentColor)
                    )
                    BasicText(
                        title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(contentColor, titleTextSize, FontWeight.SemiBold)
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    detail != null -> ScoreDetailDialogContent(
                        detail = detail,
                        totalScoreColor = Color(totalScoreColor),
                        contentColor = contentColor,
                        secondaryColor = secondaryColor
                    )
                    errorMessage != null -> BasicText(
                        errorMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 108.dp)
                            .padding(14.dp),
                        style = TextStyle(Color(0xFFB3261E), 14.sp, lineHeight = 21.sp)
                    )
                    else -> {
                        val loadingTransition = rememberInfiniteTransition(
                            label = "scoreDetailLoadingTransition"
                        )
                        val loadingRotation by loadingTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 900, easing = LinearEasing)
                            ),
                            label = "scoreDetailLoadingRotation"
                        )
                        Row(
                            Modifier.height(126.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Canvas(
                                Modifier
                                    .size(26.dp)
                                    .graphicsLayer { rotationZ = loadingRotation }
                            ) {
                                drawCircle(
                                    color = accentColor.copy(alpha = 0.18f),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                                drawArc(
                                    color = accentColor,
                                    startAngle = -90f,
                                    sweepAngle = 250f,
                                    useCenter = false,
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            BasicText(
                                "正在加载成绩构成…",
                                style = TextStyle(secondaryColor, 14.sp, FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreDetailDialogContent(
    detail: RemoteScoreDetail,
    totalScoreColor: Color,
    contentColor: Color,
    secondaryColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = CampusComposeTheme.colors.glassOutline,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(start = 10.dp, top = 9.dp, end = 18.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                "总成绩",
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    secondaryColor,
                    16.sp,
                    FontWeight.SemiBold
                )
            )
            BasicText(
                detail.totalScore.ifBlank { "-" },
                style = TextStyle(
                    totalScoreColor,
                    30.sp,
                    FontWeight.Bold
                )
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScoreDetailMetric(
                label = "平时成绩",
                value = detail.usualScore,
                contentColor = contentColor,
                secondaryColor = secondaryColor,
                modifier = Modifier.weight(1f)
            )
            ScoreDetailMetric(
                label = "平时占比",
                value = formatScoreRatioForDialog(detail.usualRatio),
                contentColor = contentColor,
                secondaryColor = secondaryColor,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScoreDetailMetric(
                label = "期末成绩",
                value = detail.finalScore,
                contentColor = contentColor,
                secondaryColor = secondaryColor,
                modifier = Modifier.weight(1f)
            )
            ScoreDetailMetric(
                label = "期末占比",
                value = formatScoreRatioForDialog(detail.finalRatio),
                contentColor = contentColor,
                secondaryColor = secondaryColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ScoreDetailMetric(
    label: String,
    value: String,
    contentColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .border(
                width = 1.dp,
                color = CampusComposeTheme.colors.glassOutline,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(start = 16.dp, top = 9.dp, end = 8.dp, bottom = 9.dp)
    ) {
        BasicText(label, style = TextStyle(secondaryColor, 13.sp))
        BasicText(
            value.ifBlank { "-" },
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(contentColor, 22.sp, FontWeight.SemiBold)
        )
    }
}

private fun formatScoreRatioForDialog(value: String): String {
    val clean = value.trim()
    return if (clean.isNotBlank() && clean != "-" && !clean.contains("%")) {
        "$clean%"
    } else {
        clean.ifBlank { "-" }
    }
}

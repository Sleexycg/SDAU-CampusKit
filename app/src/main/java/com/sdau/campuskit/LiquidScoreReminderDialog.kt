package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class LiquidScoreReminderDialogView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    enabled: State<Boolean>,
    statusProvider: () -> ScoreUpdateQueryStatus,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) : FrameLayout(context) {
    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setCampusContent {
                    LiquidScoreReminderDialog(
                        pageSnapshot = pageSnapshot,
                        enabled = enabled,
                        statusProvider = statusProvider,
                        onToggle = onToggle,
                        onDismiss = onDismiss
                    )
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun releaseSnapshot() {
        val bitmap = pageSnapshot
        pageSnapshot = null
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}

@Composable
private fun LiquidScoreReminderDialog(
    pageSnapshot: Bitmap?,
    enabled: State<Boolean>,
    statusProvider: () -> ScoreUpdateQueryStatus,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()
    val panelColor = themeColors.glassStrongSurface.compositeOver(themeColors.pageBackground)
    val panelBackdrop = rememberCanvasBackdrop { drawRect(panelColor) }
    var queryStatus by remember { mutableStateOf(statusProvider()) }

    LaunchedEffect(statusProvider) {
        while (true) {
            queryStatus = statusProvider()
            delay(5_000L)
        }
    }

    Box(Modifier.fillMaxSize()) {
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
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (themeColors.isDark) 0.40f else 0.20f))
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
        )

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(28.dp) },
                    effects = {
                        vibrancy()
                        if (themeColors.isDark) {
                            colorControls(brightness = 0f, saturation = 0.48f)
                        }
                        blur(14.dp.toPx())
                        lens(10.dp.toPx(), 24.dp.toPx())
                    },
                    shadow = null,
                    highlight = {
                        Highlight.Default.copy(alpha = if (themeColors.isDark) 0.12f else 0.56f)
                    },
                    onDrawSurface = {
                        drawRect(
                            if (themeColors.isDark) themeColors.glassSurface.copy(alpha = 0.92f)
                            else Color.White.copy(alpha = 0.30f)
                        )
                    }
                )
                .clickable(interactionSource = null, indication = null, onClick = {})
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BasicText(
                "成绩更新提醒（Beta功能）",
                style = TextStyle(themeColors.primaryText, 22.sp, FontWeight.Bold)
            )
            BasicText(
                "开启后每 30 分钟检查一次是否发布新成绩",
                style = TextStyle(themeColors.secondaryText, 13.sp, FontWeight.Normal)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(themeColors.glassSubtleSurface, RoundedCornerShape(22.dp))
                    .border(1.dp, themeColors.glassOutline, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 14.dp)) {
                    BasicText(
                        "成绩更新提醒",
                        style = TextStyle(themeColors.primaryText, 15.sp, FontWeight.Medium)
                    )
                    BasicText(
                        if (enabled.value) "已开启" else "已关闭",
                        modifier = Modifier.padding(top = 3.dp),
                        style = TextStyle(themeColors.secondaryText, 12.sp, FontWeight.Normal)
                    )
                }
                LiquidSettingsToggle(
                    selected = { enabled.value },
                    onSelect = onToggle,
                    backdrop = panelBackdrop,
                    enabled = true
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(themeColors.glassSubtleSurface, RoundedCornerShape(18.dp))
                    .border(1.dp, themeColors.glassOutline, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        "查询结果",
                        modifier = Modifier.weight(0.8f),
                        style = TextStyle(themeColors.primaryText, 15.sp, FontWeight.Medium)
                    )
                    BasicText(
                        "上次查询：${formatScoreCheckTime(queryStatus.lastCheckAt)}",
                        modifier = Modifier.weight(1.35f),
                        style = TextStyle(themeColors.secondaryText, 11.sp, FontWeight.Normal)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ScoreQueryMetric(
                        label = "已发布",
                        value = "${queryStatus.publishedCount} 门",
                        modifier = Modifier.weight(0.8f)
                    )
                    ScoreQueryMetric(
                        label = "上次发布时间",
                        value = formatScorePublishTime(queryStatus.lastPublishedAt),
                        modifier = Modifier.weight(1.35f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreQueryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val themeColors = CampusComposeTheme.colors
    Column(modifier) {
        BasicText(
            label,
            style = TextStyle(themeColors.secondaryText, 12.sp, FontWeight.Normal)
        )
        BasicText(
            value,
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(themeColors.primaryText, 15.sp, FontWeight.SemiBold)
        )
    }
}

private fun formatScoreCheckTime(timestamp: Long): String {
    if (timestamp <= 0L) return "尚未查询"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

private fun formatScorePublishTime(timestamp: Long): String {
    if (timestamp <= 0L) return "尚未记录"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

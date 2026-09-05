package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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

/** Startup announcement dialog backed by the same sampled glass used for updates. */
internal class LiquidAnnouncementDialogView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    announcement: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onOpenUrl: (String) -> Unit
) : FrameLayout(context) {
    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setCampusContent {
                    LiquidAnnouncementDialog(
                        pageSnapshot = pageSnapshot,
                        announcement = announcement,
                        onCancel = onCancel,
                        onConfirm = onConfirm,
                        onOpenUrl = onOpenUrl
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
private fun LiquidAnnouncementDialog(
    pageSnapshot: Bitmap?,
    announcement: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val contentColor = themeColors.primaryText
    val secondaryColor = contentColor.copy(alpha = 0.68f)
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val links = remember(announcement) { extractAnnouncementLinks(announcement) }
    val announcementContent = remember(announcement) { removeAnnouncementLinks(announcement) }
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
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
            Box(Modifier.fillMaxSize().background(themeColors.dialogScrim))
        }
        // Consume outside taps so the explicit Cancel/Confirm behavior remains predictable.
        Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) {})
        Column(
            Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(48.dp) },
                    effects = {
                        colorControls(
                            brightness = if (themeColors.isDark) 0f else 0.08f,
                            saturation = if (themeColors.isDark) 0.54f else 1.35f
                        )
                        blur((if (themeColors.isDark) 8.dp else 12.dp).toPx())
                        lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                    },
                    highlight = {
                        Highlight.Plain.copy(alpha = if (themeColors.isDark) 0.12f else 1f)
                    },
                    onDrawSurface = { drawRect(themeColors.glassSurface) }
                )
                .clickable(interactionSource = null, indication = null) {}
        ) {
            BasicText(
                text = "公告",
                modifier = Modifier.padding(28.dp, 24.dp, 28.dp, 10.dp),
                style = TextStyle(contentColor, 24.sp, FontWeight.Medium)
            )
            Column(
                Modifier
                    .padding(horizontal = 28.dp)
                    .heightIn(min = 72.dp, max = 260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (announcementContent.isNotBlank()) {
                    BasicText(
                        text = announcementContent,
                        style = TextStyle(contentColor.copy(alpha = 0.82f), 15.sp, lineHeight = 23.sp)
                    )
                }
                if (links.isNotEmpty()) {
                    BasicText(
                        text = "相关链接",
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                        style = TextStyle(secondaryColor, 13.sp, FontWeight.SemiBold)
                    )
                    links.forEach { url ->
                        BasicText(
                            text = url,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClick = { onOpenUrl(url) }
                                )
                                .padding(vertical = 5.dp),
                            style = TextStyle(
                                color = themeColors.accent,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        )
                    }
                }
            }
            Row(
                Modifier
                    .padding(24.dp, 18.dp, 24.dp, 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuietDialogAction(
                    label = "取消",
                    foreground = contentColor,
                    enabled = true,
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                )
                CampusLiquidButton(
                    onClick = onConfirm,
                    backdrop = backdrop,
                    style = LiquidButtonStyle.TINTED,
                    enabled = true,
                    allowDragDeformation = false,
                    deformationHorizontalPadding = 0.dp,
                    deformationVerticalPadding = 0.dp,
                    modifier = Modifier.weight(1f),
                    height = 48.dp
                ) {
                    BasicText("确认", style = TextStyle(Color.White, 16.sp))
                }
            }
        }
    }
}

private val announcementLinkPattern = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)

private fun extractAnnouncementLinks(text: String): List<String> =
    announcementLinkPattern
        .findAll(text)
        .map { it.value.trimEnd('.', ',', ';', ':', '，', '。', '；', '：', '！', '？', ')', ']', '}') }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

private fun removeAnnouncementLinks(text: String): String =
    text.lines()
        .map { line ->
            announcementLinkPattern.replace(line, "")
                .trimEnd()
                .replace(Regex("^[\\s•·*+-]+$"), "")
        }
        .joinToString("\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

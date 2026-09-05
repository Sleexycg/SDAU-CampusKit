package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.math.roundToInt
/** Version-update dialog and download state. */
internal class LiquidUpdateDialogView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    versionName: String,
    changelog: String,
    forced: Boolean,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) : FrameLayout(context) {
    private var downloadInProgress by mutableStateOf(false)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setCampusContent {
                    LiquidUpdateDialog(
                        pageSnapshot = pageSnapshot,
                        versionName = versionName,
                        changelog = changelog,
                        forced = forced,
                        downloading = downloadInProgress,
                        onDismiss = onDismiss,
                        onUpdate = onUpdate
                    )
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setDownloading(value: Boolean) {
        downloadInProgress = value
    }

    fun releaseSnapshot() {
        val bitmap = pageSnapshot
        pageSnapshot = null
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}


@Composable
private fun LiquidUpdateDialog(
    pageSnapshot: Bitmap?,
    versionName: String,
    changelog: String,
    forced: Boolean,
    downloading: Boolean,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val contentColor = themeColors.primaryText
    val secondaryColor = contentColor.copy(alpha = 0.68f)
    val accentColor = themeColors.accent
    val containerColor = themeColors.glassSurface
    val dimColor = themeColors.dialogScrim
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Recreate the reference BackdropDemoScaffold: the actual captured page is
        // exported as a LayerBackdrop, then every dialog surface samples that layer.
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
                enabled = !forced,
                interactionSource = null,
                indication = null,
                onClick = onDismiss
            )
        )
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
                    onDrawSurface = { drawRect(containerColor) }
                )
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = {}
                )
        ) {
            BasicText(
                text = "发现新版本",
                modifier = Modifier.padding(28.dp, 24.dp, 28.dp, 8.dp),
                style = TextStyle(contentColor, 24.sp, FontWeight.Medium)
            )
            BasicText(
                text = versionName,
                modifier = Modifier.padding(horizontal = 28.dp),
                style = TextStyle(accentColor, 14.sp, FontWeight.SemiBold)
            )
            BasicText(
                text = "更新内容",
                modifier = Modifier.padding(24.dp, 18.dp, 24.dp, 6.dp),
                style = TextStyle(secondaryColor, 13.sp, FontWeight.SemiBold)
            )
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .heightIn(min = 64.dp, max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                BasicText(
                    text = changelog,
                    style = TextStyle(contentColor.copy(alpha = 0.78f), 15.sp, lineHeight = 23.sp)
                )
            }
            Row(
                Modifier
                    .padding(24.dp, 18.dp, 24.dp, 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!forced) {
                    DialogAction(
                        label = "稍后",
                        style = LiquidButtonStyle.TRANSPARENT,
                        backdrop = backdrop,
                        foreground = contentColor,
                        enabled = !downloading,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
                DialogAction(
                    label = if (downloading) "正在下载…" else "立即更新",
                    style = LiquidButtonStyle.TINTED,
                    backdrop = backdrop,
                    foreground = Color.White,
                    enabled = !downloading,
                    onClick = onUpdate,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DialogAction(
    label: String,
    style: LiquidButtonStyle,
    backdrop: com.kyant.backdrop.Backdrop,
    foreground: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = CampusComposeTheme.colors
    if (style == LiquidButtonStyle.TRANSPARENT) {
        QuietDialogAction(
            label = label,
            foreground = foreground,
            enabled = enabled,
            onClick = onClick,
            modifier = modifier
        )
        return
    }

    CampusLiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        style = style,
        enabled = enabled,
        allowDragDeformation = false,
        deformationHorizontalPadding = 0.dp,
        deformationVerticalPadding = 0.dp,
        modifier = modifier,
        height = 48.dp
    ) {
        BasicText(
            label,
            style = TextStyle(foreground.copy(alpha = if (enabled) 1f else 0.72f), 16.sp)
        )
    }
}

/**
 * Full score result page using the same structure as the reference ScrollContainer:
 * one fixed exported LayerBackdrop and one Compose-owned scrolling column. Keeping
 * both in the same composition makes the lens sample new background coordinates on
 * every scroll frame instead of translating a pre-rendered Android child layer.
 */

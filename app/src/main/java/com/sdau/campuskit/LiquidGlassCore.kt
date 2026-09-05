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

/** Shared Compose host, liquid button primitives, and sampled backdrop source. */
internal enum class LiquidButtonStyle { TRANSPARENT, SURFACE, FROSTED, TINTED }

/**
 * Dialog secondary action used by the update dialog's "稍后" button.
 * It intentionally does not sample the backdrop, keeping the capsule quiet and
 * preventing saturated page content from appearing as a color block inside it.
 */
@Composable
internal fun QuietDialogAction(
    label: String,
    foreground: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 48.dp
) {
    val themeColors = CampusComposeTheme.colors
    Row(
        modifier
            .height(height)
            .clip(Capsule())
            .background(
                if (themeColors.isDark) {
                    Color.White.copy(alpha = 0.08f)
                } else {
                    Color(0xFFFAFAFA).copy(alpha = 0.20f)
                }
            )
            .border(
                width = 1.dp,
                color = if (themeColors.isDark) {
                    Color.White.copy(alpha = 0.18f)
                } else {
                    Color(0xFF64748B).copy(alpha = 0.34f)
                },
                shape = Capsule()
            )
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            label,
            style = TextStyle(
                foreground.copy(alpha = if (enabled) 1f else 0.72f),
                16.sp
            )
        )
    }
}

/** Surface/Tinted 绘制核心。 */
@Composable
internal fun CampusLiquidButton(
    onClick: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop,
    style: LiquidButtonStyle,
    enabled: Boolean,
    allowDragDeformation: Boolean = true,
    deformationHorizontalPadding: androidx.compose.ui.unit.Dp = 14.dp,
    deformationVerticalPadding: androidx.compose.ui.unit.Dp = 4.dp,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 48.dp,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val themeColors = CampusComposeTheme.colors
    val accentColor = themeColors.accent
    Box(
        modifier = modifier
            .height(height)
            // Runtime blur is rendered through a rectangular offscreen layer. Keep
            // deformation room inside the host, but never expose that layer's corners.
            .clip(Capsule()),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .fillMaxSize()
                // Keep the Android host size unchanged while reserving deformation room.
                .padding(
                    horizontal = deformationHorizontalPadding,
                    vertical = deformationVerticalPadding
                )
                .graphicsLayer { alpha = if (enabled) 1f else 0.62f }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        if (style == LiquidButtonStyle.FROSTED) {
                            colorControls(
                                brightness = if (themeColors.isDark) 0f else 0.14f,
                                saturation = 0.62f
                            )
                            blur((if (themeColors.isDark) 8.dp else 18.dp).toPx())
                            lens(4.dp.toPx(), 8.dp.toPx())
                        } else {
                            vibrancy()
                            blur(2.dp.toPx())
                            lens(12.dp.toPx(), 24.dp.toPx())
                        }
                    },
                    layerBlock = if (enabled) {
                        {
                            val width = size.width
                            val heightPx = size.height
                            val progress = interactiveHighlight.pressProgress
                            val scale = lerp(1f, 1f + 4.dp.toPx() / heightPx, progress)
                            scaleX = scale
                            scaleY = scale
                            if (allowDragDeformation) {
                                val maxOffset = size.minDimension
                                val offset = interactiveHighlight.offset
                                translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                                translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                                val maxDragScale = 4.dp.toPx() / heightPx
                                val offsetAngle = atan2(offset.y, offset.x)
                                scaleX += maxDragScale *
                                    abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                    (width / heightPx).fastCoerceAtMost(1f)
                                scaleY += maxDragScale *
                                    abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                    (heightPx / width).fastCoerceAtMost(1f)
                            } else {
                                translationX = 0f
                                translationY = 0f
                            }
                        }
                    } else {
                        null
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = if (themeColors.isDark) 0.12f else 0.62f)
                    },
                    shadow = null,
                    onDrawSurface = {
                        when (style) {
                            LiquidButtonStyle.TRANSPARENT ->
                                drawRect(
                                    if (themeColors.isDark) {
                                        Color.White.copy(alpha = 0.08f)
                                    } else {
                                        Color.White.copy(alpha = 0.12f)
                                    }
                                )
                            LiquidButtonStyle.SURFACE -> drawRect(themeColors.glassSurface)
                            LiquidButtonStyle.FROSTED ->
                                drawRect(themeColors.glassStrongSurface)
                            LiquidButtonStyle.TINTED -> {
                                drawRect(accentColor, blendMode = BlendMode.Hue)
                                drawRect(accentColor.copy(alpha = 0.75f))
                            }
                        }
                    }
                )
                .then(
                    if (themeColors.isDark) {
                        Modifier.border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.24f),
                            shape = Capsule()
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .then(
                    if (enabled) {
                        Modifier
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** Android View 表单使用的蓝色 Tinted Liquid Button 桥接层。 */
internal class LiquidTintedActionButtonView(
    context: Context,
    initialText: String,
    private val buttonHeightDp: Int,
    onClick: () -> Unit
) : FrameLayout(context) {
    private var labelState by mutableStateOf(initialText)
    private var buttonEnabledState by mutableStateOf(true)

    var text: CharSequence
        get() = labelState
        set(value) {
            labelState = value.toString()
        }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(
            composeHostView(context) {
                // A transparent CanvasBackdrop still allocates a filtered rectangle
                // on Android and can leave a faint box around the capsule. Tinted
                // buttons only need their own blue surface and interaction highlight,
                // so an empty source avoids that offscreen residue completely.
                val backdrop = emptyBackdrop()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CampusLiquidButton(
                        onClick = onClick,
                        backdrop = backdrop,
                        style = LiquidButtonStyle.TINTED,
                        enabled = buttonEnabledState,
                        allowDragDeformation = false,
                        modifier = Modifier.fillMaxWidth(),
                        height = buttonHeightDp.dp
                    ) {
                        BasicText(
                            labelState,
                            style = TextStyle(Color.White, 16.sp, FontWeight.SemiBold)
                        )
                    }
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setButtonEnabled(enabled: Boolean) {
        buttonEnabledState = enabled
    }
}

internal fun composeHostView(
    context: Context,
    content: @Composable () -> Unit
): ComposeView = ComposeView(context).apply {
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    setCampusContent(content)
}

/** Ensures every embedded Compose tree receives the same project-level tokens. */
internal fun ComposeView.setCampusContent(content: @Composable () -> Unit) {
    setContent {
        CampusComposeTheme(content = content)
    }
}

internal data class ScheduleTextPalette(
    val primary: Int,
    val secondary: Int,
    val halo: Int,
    val adaptive: Boolean,
    val usesDarkForeground: Boolean
)


/**
 * Exports one page-aligned source for glass children. The custom image is center-cropped
 * against the whole window and only then translated into this embedded ComposeView.
 * This keeps the wallpaper continuous across the status area, page and bottom dock.
 */
@Composable
internal fun PageAlignedBackdropSource(
    backdrop: LayerBackdrop,
    pageBackgroundImage: ImageBitmap?,
    pageBackgroundScrim: Int,
    pageGradient: Brush,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val hostView = LocalView.current
    var hostOffsetInWindow by remember { mutableStateOf(IntOffset.Zero) }
    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    var sourceReady by remember(pageBackgroundImage) {
        mutableStateOf(pageBackgroundImage == null)
    }

    Box(
        modifier
            .onGloballyPositioned { coordinates ->
                val root = hostView.rootView
                val rootLocation = IntArray(2)
                root.getLocationInWindow(rootLocation)
                val position = coordinates.positionInWindow()
                val nextOffset = IntOffset(
                    (position.x - rootLocation[0]).roundToInt(),
                    (position.y - rootLocation[1]).roundToInt()
                )
                val nextSize = IntSize(root.width, root.height)
                if (hostOffsetInWindow != nextOffset) hostOffsetInWindow = nextOffset
                if (windowSize != nextSize) windowSize = nextSize
                if (!sourceReady && nextSize.width > 0 && nextSize.height > 0) {
                    sourceReady = true
                }
            }
            .layerBackdrop(backdrop)
    ) {
        if (pageBackgroundImage == null) {
            Box(Modifier.fillMaxSize().background(pageGradient))
        } else if (sourceReady) {
            // Processed custom wallpapers can contain per-pixel alpha. Keep the same
            // default gradient underneath so every liquid surface samples exactly the
            // composition visible on the page.
            Box(Modifier.fillMaxSize().background(pageGradient))
            Canvas(Modifier.fillMaxSize()) {
                val viewportWidth = windowSize.width.takeIf { it > 0 } ?: size.width.roundToInt()
                val viewportHeight = windowSize.height.takeIf { it > 0 } ?: size.height.roundToInt()
                val scale = maxOf(
                    viewportWidth.toFloat() / pageBackgroundImage.width,
                    viewportHeight.toFloat() / pageBackgroundImage.height
                )
                val scaledWidth = (pageBackgroundImage.width * scale).roundToInt()
                val scaledHeight = (pageBackgroundImage.height * scale).roundToInt()
                val imageLeftInWindow = (viewportWidth - scaledWidth) / 2
                val imageTopInWindow = (viewportHeight - scaledHeight) / 2
                drawImage(
                    image = pageBackgroundImage,
                    dstOffset = IntOffset(
                        imageLeftInWindow - hostOffsetInWindow.x,
                        imageTopInWindow - hostOffsetInWindow.y
                    ),
                    dstSize = IntSize(scaledWidth, scaledHeight),
                    filterQuality = FilterQuality.Medium
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(pageBackgroundScrim))
            )
        }
    }
}

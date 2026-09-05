package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.core.graphics.ColorUtils
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class BackgroundCropSpec(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

internal enum class WallpaperApplyToastState {
    HIDDEN,
    APPLYING,
    SUCCESS
}

/** Page-aligned source used only by the bottom glass controls. */
private class BackgroundEditorBackdrop(
    private val image: ImageBitmap,
    private val crop: BackgroundCropSpec,
    private val clarity: Float,
    private val pageSize: IntSize,
    private val gradientColors: List<Color>,
    private val scrimBase: Color
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val targetOffset =
            if (coordinates?.isAttached == true) coordinates.positionInRoot() else Offset.Zero
        val pageWidth = pageSize.width.takeIf { it > 0 }?.toFloat() ?: size.width
        val pageHeight = pageSize.height.takeIf { it > 0 }?.toFloat() ?: size.height
        drawRect(
            brush = Brush.linearGradient(
                gradientColors,
                start = Offset(-targetOffset.x, -targetOffset.y),
                end = Offset(pageWidth - targetOffset.x, pageHeight - targetOffset.y)
            )
        )
        val cropWidth = ((crop.right - crop.left) * image.width).coerceAtLeast(1f)
        val cropHeight = ((crop.bottom - crop.top) * image.height).coerceAtLeast(1f)
        val cropCenterX = (crop.left + crop.right) * image.width / 2f
        val cropCenterY = (crop.top + crop.bottom) * image.height / 2f
        val scale = maxOf(pageWidth / cropWidth, pageHeight / cropHeight)
        val imageLeft = pageWidth / 2f - cropCenterX * scale
        val imageTop = pageHeight / 2f - cropCenterY * scale
        drawImage(
            image = image,
            dstOffset = IntOffset(
                (imageLeft - targetOffset.x).roundToInt(),
                (imageTop - targetOffset.y).roundToInt()
            ),
            dstSize = IntSize(
                (image.width * scale).roundToInt().coerceAtLeast(1),
                (image.height * scale).roundToInt().coerceAtLeast(1)
            ),
            filterQuality = FilterQuality.High
        )
        val scrimAlpha = (1f - clarity).coerceIn(0f, 1f)
        if (scrimAlpha > 0f) drawRect(scrimBase.copy(alpha = scrimAlpha))
    }
}

private data class BackgroundHintPalette(
    val foreground: Color,
    val halo: Color
)

private fun resolveBackgroundHintPalette(
    source: Bitmap,
    crop: BackgroundCropSpec,
    clarity: Float,
    pageSize: IntSize,
    density: Float,
    darkTheme: Boolean
): BackgroundHintPalette {
    if (source.isRecycled || pageSize.width <= 0 || pageSize.height <= 0) {
        return BackgroundHintPalette(Color.White, Color.Black.copy(alpha = 0.68f))
    }

    val pageWidth = pageSize.width.toFloat()
    val pageHeight = pageSize.height.toFloat()
    val cropWidth = ((crop.right - crop.left) * source.width).coerceAtLeast(1f)
    val cropHeight = ((crop.bottom - crop.top) * source.height).coerceAtLeast(1f)
    val cropCenterX = (crop.left + crop.right) * source.width / 2f
    val cropCenterY = (crop.top + crop.bottom) * source.height / 2f
    val scale = maxOf(pageWidth / cropWidth, pageHeight / cropHeight)
    val imageLeft = pageWidth / 2f - cropCenterX * scale
    val imageTop = pageHeight / 2f - cropCenterY * scale
    val hintCenterY = pageHeight - 231f * density
    val halfSampleWidth = minOf(pageWidth * 0.30f, 118f * density)
    val halfSampleHeight = 15f * density
    val scrimColor = android.graphics.Color.argb(
        ((1f - clarity.coerceIn(0.40f, 1f)) * 255f).roundToInt(),
        if (darkTheme) 21 else 238,
        if (darkTheme) 22 else 241,
        if (darkTheme) 25 else 248
    )
    val darkForeground = android.graphics.Color.rgb(28, 34, 48)
    val lightForeground = android.graphics.Color.rgb(249, 250, 252)
    var darkContrast = 0.0
    var lightContrast = 0.0
    var sampleCount = 0

    for (row in 0 until 5) {
        val pageY = hintCenterY - halfSampleHeight + halfSampleHeight * 2f * row / 4f
        for (column in 0 until 17) {
            val pageX = pageWidth / 2f - halfSampleWidth + halfSampleWidth * 2f * column / 16f
            val sourceX = ((pageX - imageLeft) / scale).roundToInt().coerceIn(0, source.width - 1)
            val sourceY = ((pageY - imageTop) / scale).roundToInt().coerceIn(0, source.height - 1)
            val gradientProgress = ((pageX + pageY) / (pageWidth + pageHeight)).coerceIn(0f, 1f)
            val gradientColor = ColorUtils.blendARGB(
                if (darkTheme) android.graphics.Color.rgb(18, 19, 22)
                else android.graphics.Color.rgb(243, 242, 249),
                if (darkTheme) android.graphics.Color.rgb(25, 28, 33)
                else android.graphics.Color.rgb(217, 229, 244),
                gradientProgress
            )
            val wallpaperColor = ColorUtils.compositeColors(source.getPixel(sourceX, sourceY), gradientColor)
            val finalColor = ColorUtils.compositeColors(scrimColor, wallpaperColor)
            darkContrast += ColorUtils.calculateContrast(darkForeground, finalColor)
            lightContrast += ColorUtils.calculateContrast(lightForeground, finalColor)
            sampleCount++
        }
    }

    return if (darkContrast / sampleCount >= lightContrast / sampleCount) {
        BackgroundHintPalette(
            foreground = Color(0xFF1C2230),
            halo = Color.White.copy(alpha = 0.72f)
        )
    } else {
        BackgroundHintPalette(
            foreground = Color(0xFFF9FAFC),
            halo = Color.Black.copy(alpha = 0.68f)
        )
    }
}

/** Bottom-sheet wallpaper controls. The source bitmap is owned by this view. */
internal class LiquidBackgroundEditorView(
    context: Context,
    private var sourceBitmap: Bitmap?,
    initialClarity: Float,
    initialCrop: BackgroundCropSpec?,
    onPreview: (BackgroundCropSpec, Float) -> Unit,
    onCancel: () -> Unit,
    onApply: (BackgroundCropSpec, Float) -> Unit
) : FrameLayout(context) {
    private val panelVisibleState = mutableStateOf(false)
    private val applyToastState = mutableStateOf(WallpaperApplyToastState.HIDDEN)
    private var applySequenceStarted = false
    private var dismissing = false

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            composeHostView(context) {
                val bitmap = sourceBitmap ?: return@composeHostView
                BackgroundEditorScreen(
                    sourceBitmap = bitmap,
                    source = bitmap.asImageBitmap(),
                    initialClarity = initialClarity,
                    initialCrop = initialCrop,
                    panelVisible = panelVisibleState.value,
                    applyToastState = applyToastState.value,
                    onPreview = onPreview,
                    onCancel = onCancel,
                    onApply = onApply
                )
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        post { panelVisibleState.value = true }
    }

    fun dismiss(onFinished: () -> Unit) {
        if (dismissing) return
        dismissing = true
        panelVisibleState.value = false
        applyToastState.value = WallpaperApplyToastState.HIDDEN
        postDelayed(onFinished, 210L)
    }

    fun beginApplySequence(onControlsHidden: () -> Unit) {
        if (applySequenceStarted || dismissing) return
        applySequenceStarted = true
        panelVisibleState.value = false
        postDelayed({
            if (!dismissing) {
                applyToastState.value = WallpaperApplyToastState.APPLYING
                onControlsHidden()
            }
        }, 190L)
    }

    fun setApplyToastState(state: WallpaperApplyToastState) {
        applyToastState.value = state
    }

    fun isApplyingBackground(): Boolean = applySequenceStarted

    fun releaseBitmap() {
        val bitmap = sourceBitmap
        sourceBitmap = null
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}

@Composable
private fun BackgroundEditorScreen(
    sourceBitmap: Bitmap,
    source: ImageBitmap,
    initialClarity: Float,
    initialCrop: BackgroundCropSpec?,
    panelVisible: Boolean,
    applyToastState: WallpaperApplyToastState,
    onPreview: (BackgroundCropSpec, Float) -> Unit,
    onCancel: () -> Unit,
    onApply: (BackgroundCropSpec, Float) -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    var clarity by remember { mutableFloatStateOf(initialClarity.coerceIn(0.40f, 1f)) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var imageOffset by remember { mutableStateOf(Offset.Zero) }
    var cropSize by remember { mutableStateOf(IntSize.Zero) }
    var transformInitialized by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    fun currentCrop(): BackgroundCropSpec {
        val width = cropSize.width.coerceAtLeast(1).toFloat()
        val height = cropSize.height.coerceAtLeast(1).toFloat()
        val baseScale = maxOf(width / source.width, height / source.height)
        val imageScale = (baseScale * zoom).coerceAtLeast(0.0001f)
        val visibleWidth = (width / imageScale).coerceAtMost(source.width.toFloat())
        val visibleHeight = (height / imageScale).coerceAtMost(source.height.toFloat())
        val centerX = source.width / 2f - imageOffset.x / imageScale
        val centerY = source.height / 2f - imageOffset.y / imageScale
        val left = (centerX - visibleWidth / 2f).coerceIn(0f, source.width - visibleWidth)
        val top = (centerY - visibleHeight / 2f).coerceIn(0f, source.height - visibleHeight)
        return BackgroundCropSpec(
            left = left / source.width,
            top = top / source.height,
            right = (left + visibleWidth) / source.width,
            bottom = (top + visibleHeight) / source.height
        )
    }

    fun initializeTransform(newSize: IntSize) {
        cropSize = newSize
        if (transformInitialized || newSize.width <= 0 || newSize.height <= 0) return
        val crop = initialCrop
        if (crop != null) {
            val sourceWidth = ((crop.right - crop.left) * source.width).coerceAtLeast(1f)
            val sourceHeight = ((crop.bottom - crop.top) * source.height).coerceAtLeast(1f)
            val baseScale = maxOf(
                newSize.width.toFloat() / source.width,
                newSize.height.toFloat() / source.height
            )
            val actualScale = maxOf(
                newSize.width / sourceWidth,
                newSize.height / sourceHeight
            )
            zoom = (actualScale / baseScale).coerceIn(1f, 5f)
            val sourceCenter = Offset(
                (crop.left + crop.right) * source.width / 2f,
                (crop.top + crop.bottom) * source.height / 2f
            )
            imageOffset = Offset(
                (source.width / 2f - sourceCenter.x) * actualScale,
                (source.height / 2f - sourceCenter.y) * actualScale
            )
        }
        transformInitialized = true
    }

    fun submit() {
        if (!submitted && cropSize.width > 0 && cropSize.height > 0) {
            submitted = true
            onApply(currentCrop(), clarity)
        }
    }

    val previewCrop = remember(zoom, imageOffset, cropSize) {
        if (cropSize.width > 0 && cropSize.height > 0) currentCrop()
        else BackgroundCropSpec(0f, 0f, 1f, 1f)
    }
    val backgroundBackdrop = remember(source, previewCrop, clarity, cropSize, themeColors.isDark) {
        BackgroundEditorBackdrop(
            image = source,
            crop = previewCrop,
            clarity = clarity,
            pageSize = cropSize,
            gradientColors = themeColors.pageGradient,
            scrimBase = if (themeColors.isDark) Color(0xFF151619) else Color(0xFFEEF1F8)
        )
    }
    val density = LocalDensity.current.density
    val hintPalette = remember(
        sourceBitmap,
        previewCrop,
        clarity,
        cropSize,
        density,
        themeColors.isDark
    ) {
        resolveBackgroundHintPalette(
            sourceBitmap,
            previewCrop,
            clarity,
            cropSize,
            density,
            themeColors.isDark
        )
    }
    val panelReveal = remember { Animatable(1f) }
    LaunchedEffect(panelVisible) {
        if (panelVisible) {
            panelReveal.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 360f,
                    visibilityThreshold = 0.001f
                )
            )
        } else {
            panelReveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180)
            )
        }
    }
    LaunchedEffect(previewCrop, clarity, cropSize) {
        if (cropSize.width > 0 && cropSize.height > 0) onPreview(previewCrop, clarity)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged(::initializeTransform)
    ) {
        // Only this transparent gesture layer covers the page. The selected image
        // remains in MainActivity's real wallpaper layer and updates in real time.
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 214.dp)
                .pointerInput(source, cropSize, submitted) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        if (submitted || cropSize.width <= 0 || cropSize.height <= 0) {
                            return@detectTransformGestures
                        }
                        val nextZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                        val baseScale = maxOf(
                            cropSize.width.toFloat() / source.width,
                            cropSize.height.toFloat() / source.height
                        )
                        val imageWidth = source.width * baseScale * nextZoom
                        val imageHeight = source.height * baseScale * nextZoom
                        val maxX = ((imageWidth - cropSize.width) / 2f).coerceAtLeast(0f)
                        val maxY = ((imageHeight - cropSize.height) / 2f).coerceAtLeast(0f)
                        zoom = nextZoom
                        imageOffset = Offset(
                            (imageOffset.x + pan.x).coerceIn(-maxX, maxX),
                            (imageOffset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    }
                }
        )

        BasicText(
            "双指缩放，拖动调整位置",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 222.dp)
                .graphicsLayer {
                    translationY = panelReveal.value * 72.dp.toPx()
                    alpha = 1f - panelReveal.value
                },
            style = TextStyle(
                color = hintPalette.foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = hintPalette.halo,
                    offset = Offset.Zero,
                    blurRadius = 1.25f * density
                )
            )
        )

        WallpaperApplyToast(
            state = applyToastState,
            backdrop = backgroundBackdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp)
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 6.dp, end = 6.dp, bottom = 12.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = panelReveal.value * (size.height + 28.dp.toPx())
                    alpha = 1f - panelReveal.value * 0.25f
                }
                .drawBackdrop(
                    backdrop = backgroundBackdrop,
                    shape = { RoundedRectangle(30.dp) },
                    effects = {
                        blur((if (themeColors.isDark) 8.dp else 18.dp).toPx())
                        lens(4.dp.toPx(), 8.dp.toPx())
                    },
                    shadow = {
                        Shadow(radius = 8.dp, color = themeColors.shadow)
                    },
                    onDrawSurface = { drawRect(themeColors.glassSurface) }
                )
                .padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    "背景清晰度",
                    style = TextStyle(themeColors.primaryText, 19.sp, FontWeight.SemiBold)
                )
                BasicText(
                    "${(clarity * 100).roundToInt()}%",
                    style = TextStyle(themeColors.accent, 18.sp, FontWeight.Bold)
                )
            }
            ReferenceLiquidSlider(
                value = { clarity },
                onValueChange = { clarity = it },
                valueRange = 0.40f..1f,
                visibilityThreshold = 0.005f,
                backdrop = backgroundBackdrop,
                enabled = !submitted,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CampusLiquidButton(
                    onClick = onCancel,
                    backdrop = backgroundBackdrop,
                    style = LiquidButtonStyle.SURFACE,
                    enabled = !submitted,
                    allowDragDeformation = false,
                    modifier = Modifier.weight(1f),
                    height = 60.dp
                ) {
                    BasicText(
                        "取消",
                        style = TextStyle(themeColors.primaryText, 18.sp, FontWeight.SemiBold)
                    )
                }
                CampusLiquidButton(
                    onClick = ::submit,
                    backdrop = backgroundBackdrop,
                    style = LiquidButtonStyle.TINTED,
                    enabled = !submitted,
                    allowDragDeformation = false,
                    modifier = Modifier.weight(1f),
                    height = 60.dp
                ) {
                    BasicText("应用", style = TextStyle(Color.White, 18.sp, FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
private fun WallpaperApplyToast(
    state: WallpaperApplyToastState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val success = state == WallpaperApplyToastState.SUCCESS
    LiquidStatusToast(
        visible = state != WallpaperApplyToastState.HIDDEN,
        visual = if (success) LiquidToastVisual.SUCCESS else LiquidToastVisual.LOADING,
        message = if (success) "背景图片已应用" else "正在应用背景…",
        backdrop = backdrop,
        modifier = modifier
    )
}

/** Ported from AndroidLiquidGlass-kmp/components/LiquidSlider.kt. */
@Composable
private fun ReferenceLiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val themeColors = CampusComposeTheme.colors
    val accentColor = themeColors.accent
    val trackColor = Color(0xFF787878).copy(alpha = 0.20f)
    val trackBackdrop = rememberLayerBackdrop()
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val trackWidth = constraints.maxWidth
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val dragAnimation = remember(animationScope) {
            BackgroundSliderDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = { if (didDrag) onValueChange(targetValue) },
                onDrag = { _, dragAmount ->
                    if (!didDrag) didDrag = dragAmount.x != 0f
                    val delta = (valueRange.endInclusive - valueRange.start) *
                        (dragAmount.x / trackWidth.coerceAtLeast(1))
                    val target =
                        if (isLtr) (targetValue + delta).coerceIn(valueRange)
                        else (targetValue - delta).coerceIn(valueRange)
                    onValueChange(target)
                }
            )
        }
        LaunchedEffect(dragAnimation) {
            snapshotFlow { value() }.collectLatest { next ->
                if (dragAnimation.targetValue != next) {
                    dragAnimation.updateValue(next)
                }
            }
        }
        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .pointerInput(animationScope, enabled) {
                        if (enabled) {
                            detectTapGestures { position ->
                                val delta = (valueRange.endInclusive - valueRange.start) *
                                    (position.x / trackWidth.coerceAtLeast(1))
                                val target = (
                                    if (isLtr) valueRange.start + delta
                                    else valueRange.endInclusive - delta
                                    ).coerceIn(valueRange)
                                dragAnimation.animateToValue(target)
                                onValueChange(target)
                            }
                        }
                    }
                    .height(6.dp)
                    .fillMaxWidth()
            )
            Box(
                Modifier
                    .clip(Capsule())
                    .background(accentColor)
                    .height(6.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    }
            )
        }
        Box(
            Modifier
                .graphicsLayer {
                    translationX = (-size.width / 2f + trackWidth * dragAnimation.progress)
                        .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                        if (isLtr) 1f else -1f
                }
                .then(if (enabled) dragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dragAnimation.pressProgress
                            scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dragAnimation.pressProgress
                        blur(8.dp.toPx() * (1f - progress))
                        lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        val progress = dragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f))
                    },
                    innerShadow = {
                        val progress = dragAnimation.pressProgress
                        InnerShadow(4.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dragAnimation.scaleX
                        scaleY = dragAnimation.scaleY
                        val velocity = dragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * .75f).fastCoerceIn(-.2f, .2f)
                        scaleY *= 1f - (velocity * .25f).fastCoerceIn(-.2f, .2f)
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 1f - dragAnimation.pressProgress))
                    }
                )
                .size(40.dp, 24.dp)
        )
    }
}

private class BackgroundSliderDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedRange<Float>,
    visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    private val onDragStarted: BackgroundSliderDragAnimation.(Offset) -> Unit,
    private val onDragStopped: BackgroundSliderDragAnimation.() -> Unit,
    private val onDrag: BackgroundSliderDragAnimation.(IntSize, Offset) -> Unit
) {
    private val valueSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocitySpec = spring(.5f, 300f, visibilityThreshold * 10f)
    private val pressSpec = spring(1f, 1000f, .001f)
    private val scaleXSpec = spring(.6f, 250f, .001f)
    private val scaleYSpec = spring(.7f, 250f, .001f)
    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressAnimation = Animatable(0f, .001f)
    private val scaleXAnimation = Animatable(initialScale, .001f)
    private val scaleYAnimation = Animatable(initialScale, .001f)
    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) /
        (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value
    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectBackgroundSliderGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { _, dragAmount -> onDrag(size, dragAmount) }
    }
    private fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressAnimation.animateTo(1f, pressSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYSpec) }
        }
    }
    private fun release() {
        animationScope.launch {
            androidx.compose.runtime.withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * .025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressAnimation.animateTo(0f, pressSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYSpec) }
        }
    }
    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            valueAnimation.animateTo(target, valueSpec) { updateVelocity() }
        }
    }
    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val target = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(target, valueSpec) }
                if (velocity != 0f) launch { velocityAnimation.animateTo(0f, velocitySpec) }
                release()
            }
        }
    }
    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val target = velocityTracker.calculateVelocity().x /
            (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(target, velocitySpec) }
    }
}

private suspend fun PointerInputScope.inspectBackgroundSliderGestures(
    onDragStart: (PointerInputChange) -> Unit,
    onDragEnd: (PointerInputChange) -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val up = backgroundSliderDrag(initialDown.id) { onDrag(it, it.positionChange()) }
        if (up == null) onDragCancel() else onDragEnd(up)
    }
}

private suspend inline fun AwaitPointerEventScope.backgroundSliderDrag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    if (currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitBackgroundSliderDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitBackgroundSliderDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (change.changedToUpIgnoreConsumed()) {
            val other = event.changes.fastFirstOrNull { it.pressed }
            if (other == null) return change
            pointer = other.id
        } else if (change.previousPosition != change.position) {
            return change
        }
    }
}

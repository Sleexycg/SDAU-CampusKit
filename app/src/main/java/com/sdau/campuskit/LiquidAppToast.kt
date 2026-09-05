package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

internal enum class LiquidToastVisual {
    LOADING,
    SUCCESS,
    BELL_ON,
    BELL_OFF,
    TEXT,
    ERROR
}

/**
 * Non-modal application toast that samples the page underneath it. The snapshot is used
 * only as the glass source; the rest of this full-screen host remains transparent.
 */
internal class LiquidAppToastView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    initialMessage: String,
    initialVisual: LiquidToastVisual
) : FrameLayout(context) {
    private val visibleState = mutableStateOf(false)
    private val messageState = mutableStateOf(initialMessage)
    private val visualState = mutableStateOf(initialVisual)
    private var transitionToken = 0

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        addView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isClickable = false
                isFocusable = false
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setCampusContent {
                    val themeColors = CampusComposeTheme.colors
                    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
                    val backdrop = remember(snapshotImage, themeColors.isDark) {
                        ToastSnapshotBackdrop(snapshotImage, themeColors.pageGradient)
                    }
                    Box(Modifier.fillMaxSize()) {
                        LiquidStatusToast(
                            visible = visibleState.value,
                            visual = visualState.value,
                            message = messageState.value,
                            backdrop = backdrop,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 104.dp)
                        )
                    }
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        post { visibleState.value = true }
    }

    fun update(message: String, visual: LiquidToastVisual) {
        transitionToken += 1
        messageState.value = message
        visualState.value = visual
        visibleState.value = true
    }

    fun dismiss(onFinished: () -> Unit) {
        val token = ++transitionToken
        visibleState.value = false
        postDelayed({
            if (token == transitionToken) onFinished()
        }, 150L)
    }

    fun releaseSnapshot() {
        releaseDialogSnapshot(pageSnapshot) { pageSnapshot = null }
    }
}

/** Shared visual used by both the wallpaper apply status and regular app feedback. */
@Composable
internal fun LiquidStatusToast(
    visible: Boolean,
    visual: LiquidToastVisual,
    message: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    var lastVisual by remember { mutableStateOf(visual) }
    var lastMessage by remember { mutableStateOf(message) }
    val displayVisual = if (visible) visual else lastVisual
    val displayMessage = if (visible) message else lastMessage
    LaunchedEffect(visible, visual, message) {
        if (visible) {
            lastVisual = visual
            lastMessage = message
        }
    }
    val reveal by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 180 else 130),
        label = "liquidStatusToastReveal"
    )
    val loadingTransition = rememberInfiniteTransition(label = "liquidStatusToastLoading")
    val loadingRotation by loadingTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing)
        ),
        label = "liquidStatusToastLoadingRotation"
    )
    val themeColors = CampusComposeTheme.colors
    val accent = themeColors.accent

    Row(
        modifier
            .graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * 10.dp.toPx()
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(24.dp) },
                effects = {
                    if (themeColors.isDark) {
                        colorControls(brightness = 0f, saturation = 0.48f)
                    }
                    blur((if (themeColors.isDark) 8.dp else 14.dp).toPx())
                    lens(3.dp.toPx(), 7.dp.toPx())
                },
                highlight = {
                    Highlight.Default.copy(alpha = if (themeColors.isDark) 0.12f else 0.52f)
                },
                shadow = {
                    Shadow(radius = 7.dp, color = themeColors.shadow)
                },
                innerShadow = {
                    InnerShadow(
                        radius = 2.dp,
                        color = Color.White.copy(alpha = if (themeColors.isDark) 0.08f else 0.28f)
                    )
                },
                onDrawSurface = {
                    drawRect(
                        if (themeColors.isDark) themeColors.glassStrongSurface.copy(alpha = 0.82f)
                        else Color(0xFFF8FAFF).copy(alpha = 0.48f)
                    )
                }
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (displayVisual != LiquidToastVisual.TEXT) {
            Box(
                Modifier.size(30.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    Modifier
                        .size(19.dp)
                        .graphicsLayer {
                            rotationZ = if (displayVisual == LiquidToastVisual.LOADING) loadingRotation else 0f
                        }
                ) {
                    when (displayVisual) {
                    LiquidToastVisual.LOADING -> {
                        drawCircle(
                            color = accent.copy(alpha = 0.18f),
                            style = Stroke(width = 2.3.dp.toPx())
                        )
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = 250f,
                            useCenter = false,
                            style = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    LiquidToastVisual.SUCCESS -> {
                        drawLine(
                            color = accent,
                            start = Offset(size.width * 0.18f, size.height * 0.52f),
                            end = Offset(size.width * 0.42f, size.height * 0.75f),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = accent,
                            start = Offset(size.width * 0.42f, size.height * 0.75f),
                            end = Offset(size.width * 0.84f, size.height * 0.28f),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    LiquidToastVisual.BELL_ON,
                    LiquidToastVisual.BELL_OFF -> {
                        val strokeWidth = 2.15.dp.toPx()
                        drawArc(
                            color = accent,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(size.width * 0.24f, size.height * 0.14f),
                            size = Size(size.width * 0.52f, size.height * 0.68f),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        drawLine(
                            color = accent,
                            start = Offset(size.width * 0.24f, size.height * 0.48f),
                            end = Offset(size.width * 0.17f, size.height * 0.72f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = accent,
                            start = Offset(size.width * 0.76f, size.height * 0.48f),
                            end = Offset(size.width * 0.83f, size.height * 0.72f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = accent,
                            start = Offset(size.width * 0.17f, size.height * 0.72f),
                            end = Offset(size.width * 0.83f, size.height * 0.72f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = accent,
                            radius = 1.75.dp.toPx(),
                            center = Offset(size.width * 0.5f, size.height * 0.84f)
                        )
                        if (displayVisual == LiquidToastVisual.BELL_ON) {
                            drawArc(
                                color = accent,
                                startAngle = 115f,
                                sweepAngle = 130f,
                                useCenter = false,
                                topLeft = Offset(size.width * 0.01f, size.height * 0.27f),
                                size = Size(size.width * 0.18f, size.height * 0.36f),
                                style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = accent,
                                startAngle = -65f,
                                sweepAngle = 130f,
                                useCenter = false,
                                topLeft = Offset(size.width * 0.81f, size.height * 0.27f),
                                size = Size(size.width * 0.18f, size.height * 0.36f),
                                style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
                            )
                        } else {
                            if (!themeColors.isDark) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.86f),
                                start = Offset(size.width * 0.15f, size.height * 0.16f),
                                end = Offset(size.width * 0.85f, size.height * 0.84f),
                                strokeWidth = 4.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            }
                            drawLine(
                                color = accent,
                                start = Offset(size.width * 0.15f, size.height * 0.16f),
                                end = Offset(size.width * 0.85f, size.height * 0.84f),
                                strokeWidth = 2.15.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    LiquidToastVisual.TEXT -> Unit

                    LiquidToastVisual.ERROR -> {
                        drawLine(
                            color = accent,
                            start = Offset(size.width * 0.24f, size.height * 0.24f),
                            end = Offset(size.width * 0.76f, size.height * 0.76f),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = accent,
                            start = Offset(size.width * 0.76f, size.height * 0.24f),
                            end = Offset(size.width * 0.24f, size.height * 0.76f),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    }
                }
            }
        }
        BasicText(
            displayMessage,
            style = TextStyle(
                color = themeColors.primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

private class ToastSnapshotBackdrop(
    private val snapshot: ImageBitmap?,
    private val fallbackGradient: List<Color>
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val targetOffset =
            if (coordinates?.isAttached == true) coordinates.positionInRoot() else Offset.Zero
        drawRect(
            brush = Brush.linearGradient(
                fallbackGradient,
                start = Offset(-targetOffset.x, -targetOffset.y),
                end = Offset(size.width - targetOffset.x, size.height - targetOffset.y)
            )
        )
        snapshot?.let { image ->
            drawImage(
                image = image,
                dstOffset = IntOffset(
                    -targetOffset.x.roundToInt(),
                    -targetOffset.y.roundToInt()
                ),
                dstSize = IntSize(image.width, image.height)
            )
        }
    }
}

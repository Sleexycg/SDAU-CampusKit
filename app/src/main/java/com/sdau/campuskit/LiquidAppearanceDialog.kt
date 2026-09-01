package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.flow.collectLatest

/** 独立的外观设置窗口：先在窗口内选择，点击应用后再统一刷新页面主题。 */
internal class LiquidAppearanceDialogView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    initialMode: CampusThemeMode,
    initialSystemDark: Boolean,
    onApply: (CampusThemeMode) -> Unit,
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
                    LiquidAppearanceDialog(
                        pageSnapshot = pageSnapshot,
                        initialMode = initialMode,
                        initialSystemDark = initialSystemDark,
                        onApply = onApply,
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
private fun LiquidAppearanceDialog(
    pageSnapshot: Bitmap?,
    initialMode: CampusThemeMode,
    initialSystemDark: Boolean,
    onApply: (CampusThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()
    val appearancePanelColor = themeColors.glassStrongSurface.compositeOver(themeColors.pageBackground)
    val appearanceSelectorBackdrop = rememberCanvasBackdrop {
        // The mode selector lives inside the appearance panel. Its glass source
        // must be that neutral panel surface, not the wallpaper behind the dialog.
        drawRect(appearancePanelColor)
    }
    var followSystem by remember { mutableStateOf(initialMode == CampusThemeMode.SYSTEM) }
    var manualMode by remember {
        mutableStateOf(
            when (initialMode) {
                CampusThemeMode.SYSTEM -> if (initialSystemDark) {
                    CampusThemeMode.DARK
                } else {
                    CampusThemeMode.LIGHT
                }
                CampusThemeMode.LIGHT -> CampusThemeMode.LIGHT
                CampusThemeMode.DARK -> CampusThemeMode.DARK
            }
        )
    }
    var submitted by remember { mutableStateOf(false) }

    fun submit() {
        if (submitted) return
        submitted = true
        onApply(
            when {
                followSystem -> CampusThemeMode.SYSTEM
                else -> manualMode
            }
        )
    }

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
            Box(Modifier.fillMaxSize().background(themeColors.dialogScrim))
        }
        Box(
            Modifier
                .fillMaxSize()
                .noIndicationClick(onDismiss)
        )
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(34.dp) },
                    effects = {
                        colorControls(
                            brightness = if (themeColors.isDark) 0f else 0.14f,
                            saturation = if (themeColors.isDark) 0.56f else 0.72f
                        )
                        blur((if (themeColors.isDark) 10.dp else 20.dp).toPx())
                        lens(10.dp.toPx(), 20.dp.toPx(), depthEffect = true)
                    },
                    highlight = {
                        Highlight.Plain.copy(alpha = if (themeColors.isDark) 0.12f else 1f)
                    },
                    shadow = {
                        Shadow(radius = 12.dp, color = themeColors.shadow)
                    },
                    onDrawSurface = { drawRect(themeColors.glassStrongSurface) }
                )
                .noIndicationClick {}
                .padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BasicText(
                        "外观（Beta功能）",
                        style = TextStyle(themeColors.primaryText, 22.sp, FontWeight.SemiBold)
                    )
                    BasicText(
                        "选择应用的显示模式",
                        style = TextStyle(themeColors.secondaryText, 13.sp, FontWeight.Normal)
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(themeColors.glassSubtleSurface)
                    .border(1.dp, themeColors.glassOutline, RoundedCornerShape(22.dp))
            ) {
                AppearanceToggleRow(
                    title = "跟随系统",
                    subtitle = "自动匹配设备的浅色或深色外观",
                    selected = { followSystem },
                    onSelect = { selected -> followSystem = selected },
                    enabled = true,
                    backdrop = appearanceSelectorBackdrop
                )
            }

            AnimatedVisibility(
                visible = !followSystem,
                enter = fadeIn(tween(180)) + expandVertically(tween(230)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(210))
            ) {
                Column(
                    Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicText(
                        "手动外观",
                        modifier = Modifier.padding(start = 3.dp),
                        style = TextStyle(themeColors.secondaryText, 12.sp, FontWeight.Normal)
                    )
                    AppearanceLiquidModeSelector(
                        selectedMode = manualMode,
                        onModeSelected = { manualMode = it },
                        backdrop = appearanceSelectorBackdrop
                    )
                }
            }

            Box(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(themeColors.divider)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuietDialogAction(
                    label = "取消",
                    foreground = themeColors.primaryText,
                    enabled = true,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    height = 54.dp
                )
                CampusLiquidButton(
                    onClick = ::submit,
                    backdrop = backdrop,
                    style = LiquidButtonStyle.TINTED,
                    enabled = true,
                    allowDragDeformation = false,
                    deformationHorizontalPadding = 0.dp,
                    deformationVerticalPadding = 0.dp,
                    modifier = Modifier.weight(1f),
                    height = 54.dp
                ) {
                    BasicText("应用", style = TextStyle(Color.White, 16.sp, FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
private fun AppearanceToggleRow(
    title: String,
    subtitle: String,
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    enabled: Boolean,
    backdrop: Backdrop
) {
    val themeColors = CampusComposeTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(82.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            BasicText(
                title,
                style = TextStyle(
                    themeColors.primaryText.copy(alpha = if (enabled) 1f else 0.48f),
                    17.sp,
                    FontWeight.Medium
                )
            )
            BasicText(
                subtitle,
                style = TextStyle(
                    themeColors.secondaryText.copy(alpha = if (enabled) 1f else 0.55f),
                    12.sp,
                    FontWeight.Normal
                )
            )
        }
        ReferenceLiquidToggle(
            selected = selected,
            onSelect = onSelect,
            backdrop = backdrop,
            enabled = enabled
        )
    }
}

@Composable
private fun AppearanceLiquidModeSelector(
    selectedMode: CampusThemeMode,
    onModeSelected: (CampusThemeMode) -> Unit,
    backdrop: Backdrop
) {
    LiquidBottomTabs(
        selectedTabIndex = { if (selectedMode == CampusThemeMode.DARK) 1 else 0 },
        onTabSelected = { index ->
            onModeSelected(if (index == 1) CampusThemeMode.DARK else CampusThemeMode.LIGHT)
        },
        backdrop = backdrop,
        tabsCount = 2,
        containerHeight = 60.dp,
        indicatorHeight = 52.dp,
        referenceStyle = false,
        refractContent = true,
        pressedScale = 1.14f,
        contentPressedScale = 1.08f,
        indicatorLensHorizontal = 10.dp,
        indicatorLensVertical = 14.dp,
        indicatorChromaticAberration = true,
        containerSurfaceAlpha = 0.34f,
        restingIndicatorAlpha = 0.10f,
        indicatorShadowEnabled = false,
        tapSelectionEnabled = true,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        LiquidBottomTab(onClick = { onModeSelected(CampusThemeMode.LIGHT) }) {
            AppearanceModeTabContent(label = "浅色模式", dark = false)
        }
        LiquidBottomTab(onClick = { onModeSelected(CampusThemeMode.DARK) }) {
            AppearanceModeTabContent(label = "深色模式", dark = true)
        }
    }
}

@Composable
private fun AppearanceModeTabContent(label: String, dark: Boolean) {
    val color = CampusComposeTheme.colors.primaryText
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            if (dark) {
                val moon = Path().apply {
                    moveTo(size.width * 0.70f, size.height * 0.13f)
                    cubicTo(
                        size.width * 0.43f,
                        size.height * 0.20f,
                        size.width * 0.30f,
                        size.height * 0.43f,
                        size.width * 0.34f,
                        size.height * 0.64f
                    )
                    cubicTo(
                        size.width * 0.38f,
                        size.height * 0.83f,
                        size.width * 0.58f,
                        size.height * 0.91f,
                        size.width * 0.79f,
                        size.height * 0.82f
                    )
                    cubicTo(
                        size.width * 0.60f,
                        size.height * 0.96f,
                        size.width * 0.32f,
                        size.height * 0.94f,
                        size.width * 0.17f,
                        size.height * 0.73f
                    )
                    cubicTo(
                        size.width * 0.00f,
                        size.height * 0.48f,
                        size.width * 0.13f,
                        size.height * 0.18f,
                        size.width * 0.39f,
                        size.height * 0.08f
                    )
                    cubicTo(
                        size.width * 0.50f,
                        size.height * 0.04f,
                        size.width * 0.61f,
                        size.height * 0.06f,
                        size.width * 0.70f,
                        size.height * 0.13f
                    )
                    close()
                }
                drawPath(moon, color)
            } else {
                val iconCenter = center
                val coreRadius = 3.4.dp.toPx()
                val rayStart = 6.7.dp.toPx()
                val rayEnd = 9.1.dp.toPx()
                drawCircle(color, radius = coreRadius, center = iconCenter, style = stroke)
                val diagonal = 0.7071f
                listOf(
                    0f to -1f,
                    diagonal to -diagonal,
                    1f to 0f,
                    diagonal to diagonal,
                    0f to 1f,
                    -diagonal to diagonal,
                    -1f to 0f,
                    -diagonal to -diagonal
                ).forEach { (dx, dy) ->
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(
                            iconCenter.x + dx * rayStart,
                            iconCenter.y + dy * rayStart
                        ),
                        end = androidx.compose.ui.geometry.Offset(
                            iconCenter.x + dx * rayEnd,
                            iconCenter.y + dy * rayEnd
                        ),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        BasicText(
            label,
            style = TextStyle(color, 15.sp, FontWeight.Medium)
        )
    }
}

/** Ported from AndroidLiquidGlass-kmp/components/LiquidToggle.kt. */
@Composable
private fun ReferenceLiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val themeColors = CampusComposeTheme.colors
    val accentColor = if (themeColors.isDark) Color(0xFF30D158) else Color(0xFF34C759)
    val trackColor = if (themeColors.isDark) {
        Color(0xFF787880).copy(alpha = 0.36f)
    } else {
        Color(0xFF787878).copy(alpha = 0.20f)
    }

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    onSelect(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (selected()) 0f else 1f
                    onSelect(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) didDrag = dragAmount.x != 0f
                val delta = dragAmount.x / dragWidth
                fraction = if (isLtr) {
                    (fraction + delta).fastCoerceIn(0f, 1f)
                } else {
                    (fraction - delta).fastCoerceIn(0f, 1f)
                }
            }
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest(dampedDragAnimation::updateValue)
    }
    LaunchedEffect(dampedDragAnimation, selected) {
        snapshotFlow { selected() }.collectLatest { isSelected ->
            val target = if (isSelected) 1f else 0f
            if (target != fraction) {
                fraction = target
                dampedDragAnimation.animateToValue(target)
            }
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    Box(
        modifier.graphicsLayer { alpha = if (enabled) 1f else 0.42f },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    drawRect(lerp(trackColor, accentColor, dampedDragAnimation.value))
                }
                .size(64.dp, 32.dp)
        )
        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2.dp.toPx()
                    translationX = if (isLtr) {
                        lerp(padding, padding + dragWidth, dampedDragAnimation.value)
                    } else {
                        lerp(-padding, -(padding + dragWidth), dampedDragAnimation.value)
                    }
                }
                .semantics {
                    role = Role.Switch
                    stateDescription = if (selected()) "开启" else "关闭"
                    if (!enabled) disabled()
                }
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            scale(
                                lerp(2f / 3f, 0.75f, progress),
                                lerp(0f, 0.75f, progress)
                            ) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8.dp.toPx() * (1f - progress))
                        lens(
                            5.dp.toPx() * progress,
                            10.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
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
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(4.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 1f - dampedDragAnimation.pressProgress))
                    }
                )
                .size(40.dp, 28.dp)
        )
    }
}

private fun Modifier.noIndicationClick(onClick: () -> Unit): Modifier =
    clickable(
        enabled = true,
        interactionSource = null,
        indication = null,
        onClick = onClick
    )

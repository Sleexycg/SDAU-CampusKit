package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign

internal fun createCampusLiquidBottomTabsView(
    context: Context,
    initialIndex: Int,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    onTabSelected: (Int, View) -> Unit
): CampusLiquidBottomTabsView = CampusLiquidBottomTabsView(
    context = context,
    initialIndex = initialIndex,
    pageBackgroundBitmap = pageBackgroundBitmap,
    pageBackgroundScrim = pageBackgroundScrim,
    onTabSelected = onTabSelected
)

internal class CampusLiquidBottomTabsView(
    context: Context,
    initialIndex: Int,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    private val onTabSelected: (Int, View) -> Unit
) : FrameLayout(context) {
    private var backgroundBitmapState by mutableStateOf(pageBackgroundBitmap)
    private var backgroundScrimState by mutableIntStateOf(pageBackgroundScrim)
    private var backgroundCropState by mutableStateOf<BackgroundCropSpec?>(null)
    private var selectedIndexState by mutableIntStateOf(initialIndex.coerceIn(0, 3))

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val composeView = ComposeView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setCampusContent {
                val themeColors = CampusComposeTheme.colors
                var hostOffsetInPage by remember { mutableStateOf(Offset.Zero) }
                var pageSize by remember { mutableStateOf(IntSize.Zero) }
                val pageBackgroundBitmap = backgroundBitmapState
                val pageBackgroundScrim = backgroundScrimState
                val pageBackgroundCrop = backgroundCropState
                val backgroundImage = remember(pageBackgroundBitmap) {
                    pageBackgroundBitmap?.asImageBitmap()
                }
                val backdrop = remember(
                    hostOffsetInPage,
                    pageSize,
                    backgroundImage,
                    pageBackgroundScrim,
                    pageBackgroundCrop,
                    themeColors.isDark
                ) {
                    SilkyPageGradientBackdrop(
                        hostOffsetInPage = hostOffsetInPage,
                        pageSize = pageSize,
                        pageBackgroundImage = backgroundImage,
                        pageBackgroundScrim = Color(pageBackgroundScrim),
                        pageBackgroundCrop = pageBackgroundCrop,
                        gradientColors = smoothGradientSamples(themeColors.pageGradient)
                    )
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            val page = this@CampusLiquidBottomTabsView.parent as? View
                                ?: return@onGloballyPositioned
                            val hostLocation = IntArray(2)
                            val pageLocation = IntArray(2)
                            this@CampusLiquidBottomTabsView.getLocationInWindow(hostLocation)
                            page.getLocationInWindow(pageLocation)
                            val nextOffset = Offset(
                                (hostLocation[0] - pageLocation[0]).toFloat(),
                                (hostLocation[1] - pageLocation[1]).toFloat()
                            )
                            val nextSize = IntSize(page.width, page.height)
                            if (hostOffsetInPage != nextOffset) hostOffsetInPage = nextOffset
                            if (pageSize != nextSize) pageSize = nextSize
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(Modifier.padding(bottom = 18.dp)) {
                        LiquidBottomTabs(
                            selectedTabIndex = { selectedIndexState },
                            onTabSelected = { index ->
                                selectedIndexState = index
                                onTabSelected(index, this@CampusLiquidBottomTabsView)
                            },
                            backdrop = backdrop,
                            tabsCount = 4,
                            containerHeight = 54.dp,
                            indicatorHeight = 46.dp,
                            indicatorInset = if (themeColors.isDark) 1.dp else 0.dp,
                            containerSurfaceAlpha = 0.34f,
                            modifier = Modifier
                                .width(216.dp)
                                .height(54.dp)
                        ) {
                            repeat(4) { index ->
                                LiquidBottomTab(
                                    onClick = {
                                        if (selectedIndexState == index) {
                                            onTabSelected(index, this@CampusLiquidBottomTabsView)
                                        } else selectedIndexState = index
                                    }
                                ) {
                                    LegacyNavigationIcon(index)
                                }
                            }
                        }
                    }
                }
            }
        }
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun updatePageBackground(
        bitmap: Bitmap?,
        scrim: Int,
        crop: BackgroundCropSpec? = null
    ) {
        backgroundBitmapState = bitmap
        backgroundScrimState = scrim
        backgroundCropState = crop
    }

    fun setSelectedIndex(index: Int) {
        selectedIndexState = index.coerceIn(0, 3)
    }
}

internal fun createCampusRadialSwitcherView(
    context: Context,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    onInteractionChanged: (Boolean) -> Unit = {},
    onActionSelected: (CampusRadialQuickAction) -> Unit
): CampusRadialSwitcherView = CampusRadialSwitcherView(
    context = context,
    pageBackgroundBitmap = pageBackgroundBitmap,
    pageBackgroundScrim = pageBackgroundScrim,
    onInteractionChanged = onInteractionChanged,
    onActionSelected = onActionSelected
)

internal enum class CampusRadialQuickAction {
    TRAINING_PLAN,
    GRADE_EXAM,
    DORM_ELECTRICITY
}

/**
 * An overlay-only radial page switcher. The Android host deliberately ignores
 * pointer downs outside the collapsed anchor, so the transparent part of this
 * view never steals touches from the schedule or the existing bottom tabs.
 */
internal class CampusRadialSwitcherView(
    context: Context,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    private val onInteractionChanged: (Boolean) -> Unit,
    private val onActionSelected: (CampusRadialQuickAction) -> Unit
) : FrameLayout(context) {
    private var backgroundBitmapState by mutableStateOf(pageBackgroundBitmap)
    private var backgroundScrimState by mutableIntStateOf(pageBackgroundScrim)
    private var backgroundCropState by mutableStateOf<BackgroundCropSpec?>(null)
    private var radialGestureActive = false
    private var radialMenuExpanded by mutableStateOf(false)
    private var interactionVisible = false

    private val anchorDiameterPx: Float
        get() = 56f * resources.displayMetrics.density
    private val anchorMarginPx: Float
        get() = 16f * resources.displayMetrics.density

    init {
        clipChildren = false
        clipToPadding = false
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val composeView = ComposeView(context).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setCampusContent {
                val themeColors = CampusComposeTheme.colors
                val density = LocalDensity.current
                var hostOffsetInPage by remember { mutableStateOf(Offset.Zero) }
                var pageSize by remember { mutableStateOf(IntSize.Zero) }
                val radialAnimationScope = rememberCoroutineScope()
                val releasedExpansion = remember { Animatable(0f) }
                var radialDragging by remember { mutableStateOf(false) }
                var dragExpansion by remember { mutableFloatStateOf(0f) }
                var gestureStartPosition by remember { mutableStateOf(Offset.Zero) }
                var gestureStartedExpanded by remember { mutableStateOf(false) }
                var gestureStartedWithVisibleAnchor by remember { mutableStateOf(false) }
                var anchorVisible by remember { mutableStateOf(false) }
                var anchorVisibilityToken by remember { mutableIntStateOf(0) }
                var radialAutoCloseToken by remember { mutableIntStateOf(0) }
                var hoveredAction by remember {
                    mutableStateOf<CampusRadialQuickAction?>(null)
                }
                val expansionProgress = if (radialDragging) {
                    dragExpansion
                } else {
                    releasedExpansion.value
                }
                val anchorTargetAlpha = if (
                    anchorVisible || radialDragging || radialMenuExpanded ||
                    expansionProgress > 0.01f
                ) {
                    1f
                } else {
                    0f
                }
                val anchorAlpha by animateFloatAsState(
                    targetValue = anchorTargetAlpha,
                    animationSpec = if (anchorTargetAlpha > 0f) {
                        tween(durationMillis = 140)
                    } else {
                        tween(durationMillis = 850, easing = EaseOut)
                    },
                    label = "radialSwitcherAnchorAlpha"
                )
                LaunchedEffect(
                    anchorVisible,
                    anchorVisibilityToken,
                    radialDragging,
                    radialMenuExpanded
                ) {
                    if (anchorVisible && !radialDragging && !radialMenuExpanded) {
                        // A first tap only reveals the gear briefly. That avoids it sitting
                        // over the bottom tabs for too long while preserving a second-tap
                        // affordance for opening the radial menu.
                        delay(900L)
                        anchorVisible = false
                    }
                }
                LaunchedEffect(Unit) {
                    // Start the idle countdown only once a drag has actually finished.  Using
                    // a state flow (rather than a single effect keyed only to "expanded")
                    // makes every new touch cancel and restart the countdown reliably.
                    snapshotFlow {
                        Triple(radialMenuExpanded, radialDragging, radialAutoCloseToken)
                    }.collectLatest { (expanded, dragging, token) ->
                        if (!expanded || dragging) return@collectLatest
                        delay(5_500L)
                        if (
                            radialMenuExpanded &&
                            !radialDragging &&
                            radialAutoCloseToken == token
                        ) {
                            hoveredAction = null
                            // Keep this animation outside collectLatest's cancellable body.
                            // Flipping radialMenuExpanded immediately emits a new snapshot and
                            // cancels the collector; doing that before animateTo previously left
                            // the bubbles visible while their hit targets were already disabled.
                            radialAnimationScope.launch {
                                releasedExpansion.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 320,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                radialMenuExpanded = false
                                anchorVisible = false
                                updateInteractionVisible(false)
                            }
                        }
                    }
                }
                val pageBackgroundBitmap = backgroundBitmapState
                val pageBackgroundScrim = backgroundScrimState
                val pageBackgroundCrop = backgroundCropState
                val backgroundImage = remember(pageBackgroundBitmap) {
                    pageBackgroundBitmap?.asImageBitmap()
                }
                val backdrop = remember(
                    hostOffsetInPage,
                    pageSize,
                    backgroundImage,
                    pageBackgroundScrim,
                    pageBackgroundCrop,
                    themeColors.isDark
                ) {
                    SilkyPageGradientBackdrop(
                        hostOffsetInPage = hostOffsetInPage,
                        pageSize = pageSize,
                        pageBackgroundImage = backgroundImage,
                        pageBackgroundScrim = Color(pageBackgroundScrim),
                        pageBackgroundCrop = pageBackgroundCrop,
                        gradientColors = smoothGradientSamples(themeColors.pageGradient)
                    )
                }

                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            val page = this@CampusRadialSwitcherView.parent as? View
                                ?: return@onGloballyPositioned
                            val hostLocation = IntArray(2)
                            val pageLocation = IntArray(2)
                            this@CampusRadialSwitcherView.getLocationInWindow(hostLocation)
                            page.getLocationInWindow(pageLocation)
                            val nextOffset = Offset(
                                (hostLocation[0] - pageLocation[0]).toFloat(),
                                (hostLocation[1] - pageLocation[1]).toFloat()
                            )
                            val nextSize = IntSize(page.width, page.height)
                            if (hostOffsetInPage != nextOffset) hostOffsetInPage = nextOffset
                            if (pageSize != nextSize) pageSize = nextSize
                        }
                ) {
                    val anchorDiameter = 56.dp
                    val targetDiameter = 50.dp
                    // The liquid lens grows while highlighted. Keep a generous, non-clipped
                    // drawing box around both the anchor and targets so a fast swipe cannot
                    // turn their circular edges into straight clipped lines.
                    val anchorContainerDiameter = 88.dp
                    val targetContainerDiameter = 80.dp
                    val anchorMargin = 16.dp
                    val widthPx = constraints.maxWidth.toFloat()
                    val heightPx = constraints.maxHeight.toFloat()
                    val anchorRadiusPx = with(density) { anchorDiameter.toPx() / 2f }
                    val targetRadiusPx = with(density) { targetDiameter.toPx() / 2f }
                    val anchorContainerRadiusPx = with(density) {
                        anchorContainerDiameter.toPx() / 2f
                    }
                    val targetContainerRadiusPx = with(density) {
                        targetContainerDiameter.toPx() / 2f
                    }
                    val marginPx = with(density) { anchorMargin.toPx() }
                    val revealStartPx = with(density) { 10.dp.toPx() }
                    val revealEndPx = with(density) { 82.dp.toPx() }
                    val tapSlopPx = with(density) { 12.dp.toPx() }
                    val anchorCenter = Offset(
                        widthPx - marginPx - anchorRadiusPx,
                        heightPx - marginPx - anchorRadiusPx
                    )
                    val expandTowardLeft = pageSize.width <= 0 ||
                        hostOffsetInPage.x + widthPx / 2f >= pageSize.width / 2f
                    val horizontalDirection = if (expandTowardLeft) -1f else 1f
                    val targetActions = remember {
                        listOf(
                            CampusRadialQuickAction.TRAINING_PLAN,
                            CampusRadialQuickAction.GRADE_EXAM,
                            CampusRadialQuickAction.DORM_ELECTRICITY
                        )
                    }
                    val radialOffsetsDp = remember {
                        listOf(
                            Offset(0f, -92f),
                            Offset(64f, -64f),
                            Offset(92f, 0f)
                        )
                    }
                    val fullTargetCenters = targetActions.mapIndexed { slot, _ ->
                        val offset = radialOffsetsDp[slot]
                        Offset(
                            anchorCenter.x + with(density) { offset.x.dp.toPx() } * horizontalDirection,
                            anchorCenter.y + with(density) { offset.y.dp.toPx() }
                        )
                    }
                    fun targetAt(position: Offset): CampusRadialQuickAction? {
                        var closestAction: CampusRadialQuickAction? = null
                        var closestDistance = Float.MAX_VALUE
                        fullTargetCenters.forEachIndexed { slot, center ->
                            val distance = hypot(position.x - center.x, position.y - center.y)
                            if (distance <= targetRadiusPx * 1.34f && distance < closestDistance) {
                                closestDistance = distance
                                closestAction = targetActions[slot]
                            }
                        }
                        return closestAction
                    }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(anchorCenter, horizontalDirection, radialMenuExpanded) {
                                inspectDragGestures(
                                    onDragStart = { down ->
                                        updateInteractionVisible(true)
                                        gestureStartPosition = down.position
                                        gestureStartedExpanded = radialMenuExpanded
                                        gestureStartedWithVisibleAnchor = anchorVisible
                                        if (gestureStartedExpanded) radialAutoCloseToken += 1
                                        radialDragging = true
                                        dragExpansion = if (radialMenuExpanded) 1f else 0f
                                        anchorVisible = true
                                        anchorVisibilityToken += 1
                                        hoveredAction = if (radialMenuExpanded) {
                                            targetAt(down.position)
                                        } else {
                                            null
                                        }
                                        radialAnimationScope.launch {
                                            releasedExpansion.stop()
                                            releasedExpansion.snapTo(
                                                if (gestureStartedExpanded) 1f else 0f
                                            )
                                        }
                                    },
                                    onDragEnd = { change ->
                                        val gestureDistance = hypot(
                                            change.position.x - gestureStartPosition.x,
                                            change.position.y - gestureStartPosition.y
                                        )
                                        val progressAtRelease = dragExpansion
                                        val isTap = gestureDistance <= tapSlopPx
                                        val releaseDistanceFromAnchor = hypot(
                                            change.position.x - anchorCenter.x,
                                            change.position.y - anchorCenter.y
                                        )
                                        val tappedAnchor = isTap && hypot(
                                            change.position.x - anchorCenter.x,
                                            change.position.y - anchorCenter.y
                                        ) <= anchorContainerRadiusPx * 1.1f
                                        val selected = when {
                                            gestureStartedExpanded -> targetAt(change.position)
                                            progressAtRelease >= 0.72f -> targetAt(change.position)
                                            else -> null
                                        }
                                        hoveredAction = null
                                        anchorVisibilityToken += 1
                                        if (tappedAnchor) {
                                            radialDragging = false
                                            when {
                                                // A second tap on an opened gear is always a
                                                // close action; do not restart the expansion.
                                                gestureStartedExpanded -> {
                                                    radialMenuExpanded = false
                                                    radialAnimationScope.launch {
                                                        releasedExpansion.snapTo(progressAtRelease)
                                                        releasedExpansion.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = tween(
                                                                durationMillis = 300,
                                                                easing = FastOutSlowInEasing
                                                            )
                                                        )
                                                        updateInteractionVisible(false)
                                                    }
                                                }

                                                // Tapping the invisible corner first only shows
                                                // the primary gear. The radial choices appear on
                                                // the next gear tap or on an outward drag.
                                                !gestureStartedWithVisibleAnchor -> {
                                                    radialMenuExpanded = false
                                                    anchorVisible = true
                                                    radialAnimationScope.launch {
                                                        releasedExpansion.snapTo(0f)
                                                        updateInteractionVisible(false)
                                                    }
                                                }

                                                else -> {
                                                    radialMenuExpanded = true
                                                    anchorVisible = true
                                                    radialAutoCloseToken += 1
                                                    radialAnimationScope.launch {
                                                        releasedExpansion.snapTo(progressAtRelease)
                                                        releasedExpansion.animateTo(
                                                            targetValue = 1f,
                                                            animationSpec = tween(
                                                                durationMillis = 420,
                                                                easing = FastOutSlowInEasing
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                            return@inspectDragGestures
                                        }
                                        // Once the secondary menu is visible, avoid treating a
                                        // fast pass between bubbles as an instruction to close.
                                        // Closing by drag is explicit: the finger must come back
                                        // inside the primary gear's safe hit area. This also
                                        // prevents a quick inward flick from being interpreted as
                                        // another request to settle at the fully expanded state.
                                        val returnedToAnchor = gestureStartedExpanded &&
                                            releaseDistanceFromAnchor <=
                                            anchorContainerRadiusPx * 1.15f
                                        val keepExpanded = selected == null && when {
                                            gestureStartedExpanded -> !returnedToAnchor
                                            else -> progressAtRelease >= 0.72f
                                        }
                                        if (keepExpanded) {
                                            radialDragging = false
                                            radialMenuExpanded = true
                                            anchorVisible = true
                                            radialAutoCloseToken += 1
                                            radialAnimationScope.launch {
                                                releasedExpansion.snapTo(progressAtRelease)
                                                releasedExpansion.animateTo(
                                                    targetValue = 1f,
                                                    // A deliberately slower settle makes a
                                                    // full outward swipe feel like the bubbles
                                                    // are floating into place instead of snapping.
                                                    animationSpec = tween(
                                                        durationMillis = 420,
                                                        easing = FastOutSlowInEasing
                                                    )
                                                )
                                            }
                                        } else {
                                            radialDragging = false
                                            radialMenuExpanded = false
                                            radialAnimationScope.launch {
                                                releasedExpansion.snapTo(progressAtRelease)
                                                releasedExpansion.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(
                                                        durationMillis = 300,
                                                        easing = FastOutSlowInEasing
                                                    )
                                                )
                                                updateInteractionVisible(false)
                                            }
                                            if (selected != null) {
                                                onActionSelected(selected)
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        val progressAtCancel = dragExpansion
                                        hoveredAction = null
                                        anchorVisibilityToken += 1
                                        radialDragging = false
                                        radialMenuExpanded = false
                                        radialAnimationScope.launch {
                                            releasedExpansion.snapTo(progressAtCancel)
                                            releasedExpansion.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(
                                                    durationMillis = 300,
                                                    easing = FastOutSlowInEasing
                                                )
                                            )
                                            updateInteractionVisible(false)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val gestureDistance = hypot(
                                            change.position.x - gestureStartPosition.x,
                                            change.position.y - gestureStartPosition.y
                                        )
                                        dragExpansion = if (gestureStartedExpanded) {
                                            val distanceFromAnchor = hypot(
                                                change.position.x - anchorCenter.x,
                                                change.position.y - anchorCenter.y
                                            )
                                            ((distanceFromAnchor - revealStartPx) /
                                                (revealEndPx - revealStartPx))
                                                .coerceIn(0f, 1f)
                                        } else {
                                            ((gestureDistance - revealStartPx) /
                                                (revealEndPx - revealStartPx))
                                                .coerceIn(0f, 1f)
                                        }
                                        hoveredAction = if (dragExpansion >= 0.68f) {
                                            targetAt(change.position)
                                        } else {
                                            null
                                        }
                                    }
                                )
                            }
                    ) {
                        targetActions.forEachIndexed { slot, action ->
                            val offset = radialOffsetsDp[slot]
                            val center = Offset(
                                anchorCenter.x + with(density) { offset.x.dp.toPx() } *
                                    horizontalDirection * expansionProgress,
                                anchorCenter.y + with(density) { offset.y.dp.toPx() } *
                                    expansionProgress
                            )
                            RadialLiquidBubble(
                                iconRes = when (action) {
                                    CampusRadialQuickAction.TRAINING_PLAN -> R.drawable.ic_training_plan
                                    CampusRadialQuickAction.GRADE_EXAM -> R.drawable.ic_radial_score_query
                                    CampusRadialQuickAction.DORM_ELECTRICITY -> R.drawable.ic_radial_dorm_electricity
                                },
                                backdrop = backdrop,
                                highlighted = hoveredAction == action,
                                accentIcon = hoveredAction == action,
                                bubbleDiameter = targetDiameter,
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (center.x - targetContainerRadiusPx).roundToInt(),
                                            (center.y - targetContainerRadiusPx).roundToInt()
                                        )
                                    }
                                    .size(targetContainerDiameter)
                                    .alpha(expansionProgress)
                            )
                        }
                        RadialLiquidBubble(
                            iconRes = R.drawable.ic_radial_settings,
                            backdrop = backdrop,
                            highlighted = expansionProgress > 0.04f,
                            accentIcon = expansionProgress > 0.04f,
                            bubbleDiameter = anchorDiameter,
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (anchorCenter.x - anchorContainerRadiusPx).roundToInt(),
                                        (anchorCenter.y - anchorContainerRadiusPx).roundToInt()
                                    )
                                }
                                .size(anchorContainerDiameter)
                                .alpha(anchorAlpha)
                        )
                    }
                }
            }
        }
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val radius = anchorDiameterPx / 2f
            val centerX = width - anchorMarginPx - radius
            val centerY = height - anchorMarginPx - radius
            radialGestureActive = radialMenuExpanded ||
                hypot(event.x - centerX, event.y - centerY) <= radius * 1.12f
            if (!radialGestureActive) return false
        }
        if (!radialGestureActive) return false
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            radialGestureActive = false
        }
        return handled
    }

    fun updatePageBackground(
        bitmap: Bitmap?,
        scrim: Int,
        crop: BackgroundCropSpec? = null
    ) {
        backgroundBitmapState = bitmap
        backgroundScrimState = scrim
        backgroundCropState = crop
    }

    override fun onDetachedFromWindow() {
        updateInteractionVisible(false)
        super.onDetachedFromWindow()
    }

    private fun updateInteractionVisible(visible: Boolean) {
        if (interactionVisible == visible) return
        interactionVisible = visible
        onInteractionChanged(visible)
    }
}

@Composable
private fun RadialLiquidBubble(
    iconRes: Int,
    backdrop: Backdrop,
    highlighted: Boolean,
    accentIcon: Boolean,
    bubbleDiameter: Dp,
    modifier: Modifier = Modifier
) {
    val themeColors = CampusComposeTheme.colors
    val visualDiameter by animateDpAsState(
        targetValue = bubbleDiameter * if (highlighted) 1.16f else 1f,
        // No overshoot: overshooting a circular host is what made the edge look clipped
        // during a very fast pass over a radial item.
        animationSpec = spring(dampingRatio = 1f, stiffness = 520f),
        label = "radialSwitcherBubbleDiameter"
    )
    Box(
        modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
            .size(visualDiameter)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(if (highlighted) 10.dp.toPx() else 8.dp.toPx())
                    lens(
                        if (highlighted) 18.dp.toPx() else 11.dp.toPx(),
                        if (highlighted) 30.dp.toPx() else 22.dp.toPx(),
                        chromaticAberration = highlighted
                    )
                },
                highlight = {
                    Highlight.Default.copy(
                        alpha = if (highlighted) {
                            if (themeColors.isDark) 0.34f else 0.92f
                        } else {
                            if (themeColors.isDark) 0.12f else 0.58f
                        }
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = if (highlighted) 7.dp else 4.dp,
                        alpha = if (highlighted) 0.56f else 0.24f
                    )
                },
                onDrawSurface = {
                    drawRect(
                        if (highlighted) themeColors.glassStrongSurface
                        else themeColors.glassSurface
                    )
                }
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(
                    alpha = if (themeColors.isDark) 0.24f else 0.76f
                ),
                shape = CircleShape
            )
            .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(if (highlighted) 23.dp else 21.dp),
                colorFilter = ColorFilter.tint(
                    if (accentIcon) themeColors.accent else themeColors.primaryText
                )
            )
        }
    }
}

internal fun createLoginLiquidModeToggleView(
    context: Context,
    initialIndex: Int,
    onTabSelected: (Int) -> Unit,
    onPositionDragged: (Float) -> Unit,
    onDragFinished: (Float, Float) -> Unit
): LoginLiquidModeToggleView = LoginLiquidModeToggleView(
    context = context,
    initialIndex = initialIndex,
    onTabSelected = onTabSelected,
    onPositionDragged = onPositionDragged,
    onDragFinished = onDragFinished
)

internal class LoginLiquidModeToggleView(
    context: Context,
    initialIndex: Int,
    private val onTabSelected: (Int) -> Unit,
    private val onPositionDragged: (Float) -> Unit,
    private val onDragFinished: (Float, Float) -> Unit
) : FrameLayout(context) {
    private var positionState by mutableFloatStateOf(initialIndex.toFloat())
    private var selectedIndexState by mutableIntStateOf(initialIndex)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        addView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                clipChildren = false
                clipToPadding = false
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setCampusContent {
                    val themeColors = CampusComposeTheme.colors
                    val backdrop = rememberCanvasBackdrop {
                        // 登录页没有独立的页面位图，使用所在卡片的底色作为真实折射源。
                        drawRect(themeColors.surface)
                    }
                    LiquidBottomTabs(
                        selectedTabIndex = { selectedIndexState },
                        onTabSelected = { index ->
                            selectedIndexState = index
                            onTabSelected(index)
                        },
                        backdrop = backdrop,
                        tabsCount = 2,
                        containerHeight = 60.dp,
                        indicatorHeight = 52.dp,
                        externalPosition = { positionState },
                        onPositionChanged = { position ->
                            updatePosition(position)
                            onPositionDragged(position)
                        },
                        onDragFinished = onDragFinished,
                        referenceStyle = true,
                        refractContent = true,
                        pressedScale = 1.14f,
                        contentPressedScale = 1.08f,
                        indicatorLensHorizontal = 10.dp,
                        indicatorLensVertical = 14.dp,
                        indicatorChromaticAberration = true,
                        containerSurfaceAlpha = 0.40f,
                        restingIndicatorAlpha = 0.10f,
                        indicatorShadowEnabled = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        LiquidBottomTab(onClick = { selectedIndexState = 0 }) {
                            LoginLiquidTabLabel("个人课表")
                        }
                        LiquidBottomTab(onClick = { selectedIndexState = 1 }) {
                            LoginLiquidTabLabel("全校课表")
                        }
                    }
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setSelectionPosition(position: Float) {
        updatePosition(position)
    }

    private fun updatePosition(position: Float) {
        positionState = position.coerceIn(0f, 1f)
    }

    fun setSettledIndex(index: Int) {
        selectedIndexState = index.coerceIn(0, 1)
        positionState = selectedIndexState.toFloat()
    }
}

@Composable
private fun LoginLiquidTabLabel(label: String) {
    val themeColors = CampusComposeTheme.colors
    BasicText(
        text = label,
        style = TextStyle(
            color = themeColors.primaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    )
}

internal class SilkyPageGradientBackdrop(
    private val hostOffsetInPage: Offset,
    private val pageSize: IntSize,
    private val pageBackgroundImage: ImageBitmap? = null,
    private val pageBackgroundScrim: Color = Color.Transparent,
    private val pageBackgroundCrop: BackgroundCropSpec? = null,
    private val gradientColors: List<Color>
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val targetOffsetInHost =
            if (coordinates?.isAttached == true) coordinates.positionInRoot() else Offset.Zero
        val targetOffsetInPage = hostOffsetInPage + targetOffsetInHost
        val pageWidth = pageSize.width.takeIf { it > 0 }?.toFloat() ?: size.width
        val pageHeight = pageSize.height.takeIf { it > 0 }?.toFloat() ?: size.height
        val image = pageBackgroundImage
        drawRect(
            brush = Brush.linearGradient(
                colors = gradientColors,
                start = Offset(-targetOffsetInPage.x, -targetOffsetInPage.y),
                end = Offset(
                    pageWidth - targetOffsetInPage.x,
                    pageHeight - targetOffsetInPage.y
                )
            )
        )
        if (image != null) {
            val crop = pageBackgroundCrop
            val cropLeft = (crop?.left ?: 0f) * image.width
            val cropTop = (crop?.top ?: 0f) * image.height
            val cropRight = (crop?.right ?: 1f) * image.width
            val cropBottom = (crop?.bottom ?: 1f) * image.height
            val cropWidth = (cropRight - cropLeft).coerceAtLeast(1f)
            val cropHeight = (cropBottom - cropTop).coerceAtLeast(1f)
            val cropCenterX = (cropLeft + cropRight) / 2f
            val cropCenterY = (cropTop + cropBottom) / 2f
            val scale = maxOf(
                pageWidth / cropWidth,
                pageHeight / cropHeight
            )
            val scaledWidth = image.width * scale
            val scaledHeight = image.height * scale
            val pageImageLeft = pageWidth / 2f - cropCenterX * scale
            val pageImageTop = pageHeight / 2f - cropCenterY * scale
            drawImage(
                image = image,
                dstOffset = IntOffset(
                    (pageImageLeft - targetOffsetInPage.x).fastRoundToInt(),
                    (pageImageTop - targetOffsetInPage.y).fastRoundToInt()
                ),
                dstSize = IntSize(
                    scaledWidth.fastRoundToInt().coerceAtLeast(1),
                    scaledHeight.fastRoundToInt().coerceAtLeast(1)
                )
            )
            if (pageBackgroundScrim.alpha > 0f) drawRect(pageBackgroundScrim)
        }
    }
}

private fun smoothGradientSamples(anchors: List<Color>): List<Color> = List(65) { sampleIndex ->
    val position = sampleIndex / 64f
    val lastSegment = anchors.size - 2
    val scaled = position * (anchors.size - 1)
    val segment = scaled.toInt().coerceIn(0, lastSegment)
    val t = (scaled - segment).coerceIn(0f, 1f)
    val p0 = anchors[(segment - 1).coerceAtLeast(0)]
    val p1 = anchors[segment]
    val p2 = anchors[segment + 1]
    val p3 = anchors[(segment + 2).coerceAtMost(anchors.lastIndex)]
    fun channel(a: Float, b: Float, c: Float, d: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return (0.5f * (
            2f * b + (-a + c) * t +
                (2f * a - 5f * b + 4f * c - d) * t2 +
                (-a + 3f * b - 3f * c + d) * t3
            )).coerceIn(0f, 1f)
    }
    Color(
        red = channel(p0.red, p1.red, p2.red, p3.red),
        green = channel(p0.green, p1.green, p2.green, p3.green),
        blue = channel(p0.blue, p1.blue, p2.blue, p3.blue),
        alpha = 1f
    )
}

private val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }

@Composable
internal fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val currentScale = scale()
                scaleX = currentScale
                scaleY = currentScale
            },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
internal fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    containerHeight: Dp,
    indicatorHeight: Dp,
    externalPosition: (() -> Float)? = null,
    onPositionChanged: ((Float) -> Unit)? = null,
    onDragFinished: ((Float, Float) -> Unit)? = null,
    referenceStyle: Boolean = false,
    refractContent: Boolean = true,
    pressedScale: Float = 78f / 56f,
    contentPressedScale: Float = 1.2f,
    indicatorLensHorizontal: Dp = 10.dp,
    indicatorLensVertical: Dp = 14.dp,
    indicatorInset: Dp = 0.dp,
    indicatorChromaticAberration: Boolean = true,
    containerSurfaceAlpha: Float? = null,
    restingIndicatorAlpha: Float = 0.10f,
    indicatorShadowEnabled: Boolean = referenceStyle,
    tapSelectionEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val accentColor = themeColors.accent
    val containerColor = if (themeColors.isDark) {
        Color(0xFF121212).copy(
            alpha = containerSurfaceAlpha ?: if (referenceStyle) 0.40f else 0.34f
        )
    } else {
        themeColors.glassSurface.copy(
            alpha = containerSurfaceAlpha ?: if (referenceStyle) 0.40f else 0.26f
        )
    }
    val tabsBackdrop = rememberLayerBackdrop()
    val isLtr = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Ltr
    var tappedIndex by remember { mutableIntStateOf(-1) }
    val tapSelectionModifier = if (tapSelectionEnabled) {
        Modifier.pointerInput(tabsCount, isLtr) {
            detectTapGestures { offset ->
                val tabWidth = size.width.toFloat() / tabsCount.coerceAtLeast(1)
                val visualIndex = (offset.x / tabWidth)
                    .toInt()
                    .fastCoerceIn(0, tabsCount - 1)
                tappedIndex = if (isLtr) visualIndex else tabsCount - 1 - visualIndex
            }
        }
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier.then(tapSelectionModifier),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val horizontalInset = 4.dp
        val horizontalInsetPx = with(density) { horizontalInset.toPx() }
        val indicatorInsetPx = with(density) { indicatorInset.toPx() }
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - horizontalInsetPx * 2f) / tabsCount
        }
        val tabWidthDp = with(density) { tabWidth.toDp() }
        val indicatorWidth = (tabWidthDp - indicatorInset * 2f).coerceAtLeast(0.dp)
        val indicatorVisualHeight = (indicatorHeight - indicatorInset * 2f).coerceAtLeast(0.dp)
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = pressedScale,
                onDragStarted = {},
                onDragStopped = {
                    onDragFinished?.invoke(targetValue, velocity)
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    val nextValue =
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    updateValue(nextValue)
                    onPositionChanged?.invoke(nextValue)
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        val latestSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
        LaunchedEffect(Unit) {
            snapshotFlow { latestSelectedTabIndex() }.collectLatest { index ->
                currentIndex = index.fastCoerceIn(0, tabsCount - 1)
            }
        }
        LaunchedEffect(tappedIndex) {
            if (tappedIndex >= 0) {
                currentIndex = tappedIndex.fastCoerceIn(0, tabsCount - 1)
            }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                dampedDragAnimation.animateToValue(index.toFloat())
                onTabSelected(index)
            }
        }
        val indicatorValueState = remember(externalPosition, dampedDragAnimation, tabsCount) {
            derivedStateOf {
                (
                    if (dampedDragAnimation.isDragging) dampedDragAnimation.value
                    else externalPosition?.invoke() ?: dampedDragAnimation.value
                    ).fastCoerceIn(0f, (tabsCount - 1).toFloat())
            }
        }
        val indicatorValue = indicatorValueState.value
        val currentIndicatorValue = rememberUpdatedState(indicatorValue)
        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) horizontalInsetPx +
                            (currentIndicatorValue.value + 0.5f) * tabWidth + panelOffset
                        else size.width - horizontalInsetPx -
                            (currentIndicatorValue.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                // Android's runtime blur can bleed into the rectangular offscreen layer
                // around the capsule. Clip only the resting track so those four corners
                // stay transparent; the independent liquid indicator can still grow
                // above and below the 60dp track while pressed or dragged.
                .clip(Capsule())
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = if (themeColors.isDark) 0.15f else 0.52f)
                    },
                    shadow = null,
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(containerHeight)
                .fillMaxWidth()
                .padding(horizontal = horizontalInset, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        if (refractContent) {
            CompositionLocalProvider(
                LocalLiquidBottomTabScale provides {
                    lerp(1f, contentPressedScale, dampedDragAnimation.pressProgress)
                }
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .clip(Capsule())
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(8f.dp.toPx())
                                lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                            },
                            highlight = {
                                Highlight.Default.copy(
                                    alpha = dampedDragAnimation.pressProgress *
                                        if (referenceStyle) 1f else 0.35f
                                )
                            },
                            shadow = null,
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .then(interactiveHighlight.modifier)
                        .height(indicatorHeight)
                        .fillMaxWidth()
                        .padding(horizontal = horizontalInset)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }

        val indicatorBackdrop = if (refractContent) {
            rememberCombinedBackdrop(backdrop, tabsBackdrop)
        } else {
            backdrop
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        if (isLtr) horizontalInsetPx +
                            indicatorValue * tabWidth + indicatorInsetPx + panelOffset
                        else constraints.maxWidth.toFloat() - horizontalInsetPx -
                            (indicatorValue + 1f) * tabWidth + indicatorInsetPx + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = indicatorBackdrop,
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            indicatorLensHorizontal.toPx() * progress,
                            indicatorLensVertical.toPx() * progress,
                            chromaticAberration = indicatorChromaticAberration
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(
                            alpha = dampedDragAnimation.pressProgress *
                                if (referenceStyle) 1f else 0.35f
                        )
                    },
                    shadow = if (indicatorShadowEnabled) {
                        {
                            Shadow(alpha = dampedDragAnimation.pressProgress)
                        }
                    } else {
                        null
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress * if (referenceStyle) 1f else 0.45f
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            (if (themeColors.isDark) Color.White else Color.Black)
                                .copy(restingIndicatorAlpha),
                            alpha = 1f - progress
                        )
                        if (referenceStyle) {
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        }
                    }
                )
                .height(indicatorVisualHeight)
                .width(indicatorWidth),
            contentAlignment = Alignment.Center
        ) {}
    }
}

@Composable
private fun LegacyNavigationIcon(
    index: Int,
    color: Color = CampusComposeTheme.colors.primaryText,
    iconSize: Dp = 20.dp
) {
    val iconColor = color
    androidx.compose.foundation.Canvas(Modifier.size(iconSize)) {
        val s = 8.dp.toPx()
        fun drawGlyph(color: Color, stroke: Stroke, verticalOffset: Float) {
            val cx = size.width / 2f
            val cy = size.height / 2f + verticalOffset
            when (index) {
                0 -> {
                    val left = cx - s
                    val top = cy - s * .72f
                    val right = cx + s
                    val bottom = cy + s * .78f
                    drawRoundRect(
                        color,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()),
                        style = stroke
                    )
                    drawLine(color, Offset(left, cy - s * .28f), Offset(right, cy - s * .28f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx - s * .48f, cy - s), Offset(cx - s * .48f, cy - s * .5f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx + s * .48f, cy - s), Offset(cx + s * .48f, cy - s * .5f), stroke.width, stroke.cap)
                }
                1 -> {
                    val left = cx - s * .72f
                    val top = cy - s
                    val right = cx + s * .72f
                    val bottom = cy + s
                    drawRoundRect(
                        color,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                        style = stroke
                    )
                    drawLine(color, Offset(cx - s * .4f, cy - s * .48f), Offset(cx + s * .38f, cy - s * .48f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx - s * .4f, cy - s * .08f), Offset(cx + s * .2f, cy - s * .08f), stroke.width, stroke.cap)
                    drawCircle(color, s * .28f, Offset(cx + s * .34f, cy + s * .48f), style = stroke)
                    drawLine(color, Offset(cx + s * .34f, cy + s * .48f), Offset(cx + s * .34f, cy + s * .31f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx + s * .34f, cy + s * .48f), Offset(cx + s * .47f, cy + s * .56f), stroke.width, stroke.cap)
                }
                2 -> {
                    drawLine(color, Offset(cx - s, cy + s * .9f), Offset(cx + s, cy + s * .9f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx - s * .65f, cy + s * .9f), Offset(cx - s * .65f, cy + s * .1f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx, cy + s * .9f), Offset(cx, cy - s * .45f), stroke.width, stroke.cap)
                    drawLine(color, Offset(cx + s * .65f, cy + s * .9f), Offset(cx + s * .65f, cy - s), stroke.width, stroke.cap)
                }
                3 -> {
                    drawCircle(color, s * .58f, Offset(cx - s * .18f, cy - s * .18f), style = stroke)
                    drawLine(
                        color,
                        Offset(cx + s * .23f, cy + s * .23f),
                        Offset(cx + s * .86f, cy + s * .86f),
                        stroke.width,
                        stroke.cap
                    )
                }
                4 -> {
                    // Settings: a compact gear that stays legible inside the 56dp anchor.
                    drawCircle(color, s * .42f, Offset(cx, cy), style = stroke)
                    drawCircle(color, s * .12f, Offset(cx, cy), style = stroke)
                    repeat(8) { step ->
                        val angle = Math.toRadians((step * 45.0) - 90.0)
                        val inner = s * .58f
                        val outer = s * .88f
                        drawLine(
                            color,
                            Offset(
                                cx + kotlin.math.cos(angle).toFloat() * inner,
                                cy + kotlin.math.sin(angle).toFloat() * inner
                            ),
                            Offset(
                                cx + kotlin.math.cos(angle).toFloat() * outer,
                                cy + kotlin.math.sin(angle).toFloat() * outer
                            ),
                            stroke.width,
                            stroke.cap
                        )
                    }
                }
                5 -> {
                    // Training plan: an open book.
                    val top = cy - s * .72f
                    val bottom = cy + s * .78f
                    drawLine(color, Offset(cx, top), Offset(cx, bottom), stroke.width, stroke.cap)
                    drawPath(
                        Path().apply {
                            moveTo(cx, top + s * .12f)
                            cubicTo(
                                cx - s * .28f, top - s * .08f,
                                cx - s * .92f, top,
                                cx - s * .92f, top + s * .3f
                            )
                            lineTo(cx - s * .92f, bottom)
                            cubicTo(
                                cx - s * .5f, bottom - s * .2f,
                                cx - s * .22f, bottom - s * .16f,
                                cx, bottom
                            )
                        },
                        color = color,
                        style = stroke
                    )
                    drawPath(
                        Path().apply {
                            moveTo(cx, top + s * .12f)
                            cubicTo(
                                cx + s * .28f, top - s * .08f,
                                cx + s * .92f, top,
                                cx + s * .92f, top + s * .3f
                            )
                            lineTo(cx + s * .92f, bottom)
                            cubicTo(
                                cx + s * .5f, bottom - s * .2f,
                                cx + s * .22f, bottom - s * .16f,
                                cx, bottom
                            )
                        },
                        color = color,
                        style = stroke
                    )
                }
                6 -> {
                    // Appearance: sun icon.
                    drawCircle(color, s * .36f, Offset(cx, cy), style = stroke)
                    repeat(8) { step ->
                        val angle = Math.toRadians((step * 45.0) - 90.0)
                        val inner = s * .58f
                        val outer = s * .92f
                        drawLine(
                            color,
                            Offset(
                                cx + kotlin.math.cos(angle).toFloat() * inner,
                                cy + kotlin.math.sin(angle).toFloat() * inner
                            ),
                            Offset(
                                cx + kotlin.math.cos(angle).toFloat() * outer,
                                cy + kotlin.math.sin(angle).toFloat() * outer
                            ),
                            stroke.width,
                            stroke.cap
                        )
                    }
                }
            }
        }

        drawGlyph(
            color = iconColor,
            stroke = Stroke(1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            verticalOffset = 0f
        )
    }
}

internal class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedRange<Float>,
    visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    private val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)
    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)
    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    var isDragging by mutableStateOf(false)
        private set

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                isDragging = true
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                isDragging = false
                release()
            },
            onDragCancel = {
                onDragStopped()
                isDragging = false
                release()
            }
        ) { _, dragAmount -> onDrag(size, dragAmount) }
    }

    private fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    private fun release() {
        animationScope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val target = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                if (velocity != 0f) launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val targetVelocity = velocityTracker.calculateVelocity().x /
            (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

internal class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition
    private val shader = if (isRuntimeShaderSupported()) {
        RuntimeShader(
            """
            uniform float2 size;
            layout(color) uniform half4 color;
            uniform float radius;
            uniform float2 position;

            half4 main(float2 coord) {
                float dist = distance(coord, position);
                float intensity = smoothstep(radius, radius * 0.5, dist);
                return color * intensity;
            }
            """.trimIndent()
        )
    } else null

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            if (shader != null) {
                drawRect(Color.White.copy(0.08f * progress), blendMode = BlendMode.Plus)
                shader.apply {
                    val currentPosition = position(size, positionAnimation.value)
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(0.15f * progress))
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        currentPosition.x.fastCoerceIn(0f, size.width),
                        currentPosition.y.fastCoerceIn(0f, size.height)
                    )
                }
                drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Plus)
            } else {
                drawRect(Color.White.copy(0.25f * progress), blendMode = BlendMode.Plus)
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            }
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}

private suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        val drag = initialDown
        onDragStart(down)
        onDrag(drag, Offset.Zero)
        val upEvent = drag(drag.id) { onDrag(it, it.positionChange()) }
        if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    if (currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) return dragEvent
            pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}

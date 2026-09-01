package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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

private const val LIQUID_MENU_COLLAPSE_DURATION_MS = 165L

/** More actions and score-term dropdown menus. */
internal data class LiquidMenuAction(
    val title: String,
    val iconRes: Int,
    val isUpdateAction: Boolean = false,
    val isPushAction: Boolean = false,
    val isBackAction: Boolean = false,
    val hasSubmenu: Boolean = false,
    val enabled: Boolean = true,
    val dividerAfter: Boolean = false,
    val onClick: () -> Unit
)

/** Anchored score-term dropdown with the same sampled glass and slide highlight as More. */
internal class LiquidScoreTermDropdownView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    menuX: Int,
    menuY: Int,
    menuWidth: Int,
    maxMenuHeight: Int,
    expandDownward: Boolean,
    terms: List<String>,
    selectedTerm: String,
    onTermSelected: (String) -> Unit,
    onDismiss: () -> Unit
) : FrameLayout(context) {
    private var expanded by mutableStateOf(false)
    private var collapsePending = false

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setCampusContent {
                    LiquidScoreTermDropdown(
                        pageSnapshot = pageSnapshot,
                        menuX = menuX,
                        menuY = menuY,
                        menuWidth = menuWidth,
                        maxMenuHeight = maxMenuHeight,
                        expandDownward = expandDownward,
                        terms = terms,
                        selectedTerm = selectedTerm,
                        expanded = expanded,
                        onTermSelected = onTermSelected,
                        onDismiss = onDismiss
                    )
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        post { expanded = true }
    }

    fun collapse(afterCollapse: () -> Unit) {
        if (collapsePending) return
        collapsePending = true
        expanded = false
        postDelayed({ afterCollapse() }, LIQUID_MENU_COLLAPSE_DURATION_MS)
    }

    fun releaseSnapshot() {
        val bitmap = pageSnapshot
        pageSnapshot = null
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}

/** Compact top-right action menu backed by the same live page sampling as the dialogs. */
internal class LiquidActionMenuView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    menuX: Int,
    menuY: Int,
    actions: List<LiquidMenuAction>,
    backgroundActions: List<LiquidMenuAction> = emptyList(),
    hasCustomBackground: Boolean,
    onDismiss: () -> Unit
) : FrameLayout(context) {
    private var rootActions = actions
    private val secondaryBackgroundActions = backgroundActions
    private var updateStatus by mutableStateOf("")
    private var checkingUpdate by mutableStateOf(false)
    private var menuActions by mutableStateOf(actions)
    private var expanded by mutableStateOf(false)
    private var collapsePending = false

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setCampusContent {
                    LiquidActionMenu(
                        pageSnapshot = pageSnapshot,
                        menuX = menuX,
                        menuY = menuY,
                        actions = menuActions,
                        hasCustomBackground = hasCustomBackground,
                        updateStatus = updateStatus,
                        checkingUpdate = checkingUpdate,
                        expanded = expanded,
                        onDismiss = onDismiss
                    )
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        post { expanded = true }
    }

    fun collapse(afterCollapse: () -> Unit) {
        if (collapsePending) return
        collapsePending = true
        expanded = false
        postDelayed({ afterCollapse() }, LIQUID_MENU_COLLAPSE_DURATION_MS)
    }

    fun setUpdateStatus(message: String, checking: Boolean) {
        updateStatus = message
        checkingUpdate = checking
    }

    fun setPushState(enabled: Boolean) {
        val updatedRootActions = rootActions.map { action ->
            if (action.isPushAction) {
                action.copy(
                    title = if (enabled) "关闭课程通知" else "开启课程通知",
                    iconRes = if (enabled) R.drawable.ic_push_on else R.drawable.ic_push_off
                )
            } else {
                action
            }
        }
        rootActions = updatedRootActions
        menuActions = if (menuActions.any(LiquidMenuAction::isBackAction)) {
            menuActions
        } else {
            updatedRootActions
        }
    }

    fun showBackgroundActions() {
        if (secondaryBackgroundActions.isNotEmpty()) {
            menuActions = secondaryBackgroundActions
        }
    }

    fun showRootActions() {
        menuActions = rootActions
    }

    fun releaseSnapshot() {
        val bitmap = pageSnapshot
        pageSnapshot = null
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}

@Composable
private fun LiquidActionMenu(
    pageSnapshot: Bitmap?,
    menuX: Int,
    menuY: Int,
    actions: List<LiquidMenuAction>,
    hasCustomBackground: Boolean,
    updateStatus: String,
    checkingUpdate: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit
) {
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()
    val panelShape = RoundedCornerShape(22.dp)
    val themeColors = CampusComposeTheme.colors
    val contentColor = themeColors.primaryText
    val accentColor = themeColors.accent
    var slidingIndex by remember { mutableStateOf<Int?>(null) }
    val actionBounds = remember(actions) { mutableMapOf<Int, Pair<Float, Float>>() }
    val density = LocalDensity.current
    val revealProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) 260 else LIQUID_MENU_COLLAPSE_DURATION_MS.toInt(),
            easing = if (expanded) {
                CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
            } else {
                CubicBezierEasing(0.4f, 0f, 1f, 1f)
            }
        ),
        label = "actionMenuReveal"
    )
    val panelOffsetPx = with(density) { 7.dp.toPx() }
    val itemOffsetPx = with(density) { 5.dp.toPx() }

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
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
        )
        Column(
            Modifier
                .offset { IntOffset(menuX, menuY) }
                .width(204.dp)
                .animateContentSize(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                )
                .graphicsLayer {
                    alpha = revealProgress
                    translationY = -panelOffsetPx * (1f - revealProgress)
                    scaleX = 0.96f + 0.04f * revealProgress
                    scaleY = 0.80f + 0.20f * revealProgress
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(22.dp) },
                    effects = {
                        vibrancy()
                        if (themeColors.isDark) {
                            colorControls(brightness = 0f, saturation = 0.52f)
                            blur(8.dp.toPx())
                        } else if (hasCustomBackground) {
                            colorControls(brightness = 0.06f, saturation = 1.25f)
                            blur(12.dp.toPx())
                        } else {
                            colorControls(brightness = 0.10f, saturation = 0.88f)
                            blur(18.dp.toPx())
                        }
                        lens(12.dp.toPx(), 24.dp.toPx(), depthEffect = true)
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = if (themeColors.isDark) 0.12f else 0.68f)
                    },
                    onDrawSurface = {
                        drawRect(
                            if (themeColors.isDark) {
                                themeColors.glassStrongSurface
                            } else {
                                Color.White.copy(alpha = if (hasCustomBackground) 0.30f else 0.42f)
                            }
                        )
                    }
                )
                .clip(panelShape)
                .pointerInput(actions, checkingUpdate) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun actionIndexAt(y: Float): Int? = actionBounds.entries
                            .firstOrNull { (_, bounds) -> y >= bounds.first && y <= bounds.second }
                            ?.key
                            ?.takeUnless {
                                !actions[it].enabled ||
                                    (actions[it].isUpdateAction && checkingUpdate)
                            }

                        slidingIndex = actionIndexAt(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            slidingIndex = actionIndexAt(change.position.y)
                            if (!change.pressed) {
                                val selected = slidingIndex
                                slidingIndex = null
                                if (selected != null) actions[selected].onClick()
                                break
                            }
                            change.consume()
                        }
                    }
                }
                .padding(6.dp)
        ) {
            actions.forEachIndexed { index, action ->
                val isUpdateAction = action.isUpdateAction
                val rowHeight = when {
                    action.isBackAction -> 40.dp
                    isUpdateAction && updateStatus.isNotBlank() -> 54.dp
                    else -> 44.dp
                }
                val itemProgress by animateFloatAsState(
                    targetValue = if (expanded) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (expanded) 170 else 90,
                        delayMillis = if (expanded) 38 + index * 28 else 0,
                        easing = if (expanded) {
                            CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
                        } else {
                            CubicBezierEasing(0.4f, 0f, 1f, 1f)
                        }
                    ),
                    label = "actionMenuItemEntry"
                )
                val highlightProgress by animateFloatAsState(
                    targetValue = if (slidingIndex == index) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
                    label = "menuItemHighlight"
                )
                val itemShape = RoundedCornerShape(14.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .onGloballyPositioned { coordinates ->
                            val top = coordinates.positionInParent().y
                            actionBounds[index] = top to (top + coordinates.size.height)
                        }
                        .graphicsLayer {
                            val selectedScale = 1f + highlightProgress * 0.018f
                            val entryScale = 0.985f + itemProgress * 0.015f
                            alpha = itemProgress * if (action.enabled) 1f else 0.42f
                            translationY = -itemOffsetPx * (1f - itemProgress)
                            scaleX = selectedScale * entryScale
                            scaleY = selectedScale * entryScale
                        }
                        .clip(itemShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.48f * highlightProgress),
                                    Color.White.copy(alpha = 0.17f * highlightProgress),
                                    Color(0xFFBDE5FF).copy(alpha = 0.23f * highlightProgress)
                                ),
                                start = Offset.Zero,
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            ),
                            shape = itemShape
                        )
                        .background(
                            color = if (isUpdateAction && checkingUpdate) {
                                Color.White.copy(alpha = 0.18f)
                            } else {
                                Color.Transparent
                            },
                            shape = itemShape
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.88f * highlightProgress),
                                    Color(0xFF98D7FF).copy(alpha = 0.42f * highlightProgress),
                                    Color.White.copy(alpha = 0.62f * highlightProgress)
                                )
                            ),
                            shape = itemShape
                        )
                        .semantics {
                            role = Role.Button
                            contentDescription = action.title
                        }
                        .padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(action.iconRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(if (action.isBackAction) 18.dp else 21.dp)
                            .graphicsLayer {
                                if (action.isBackAction) rotationZ = 90f
                            },
                        colorFilter = ColorFilter.tint(
                            accentColor.copy(alpha = if (action.enabled) 1f else 0.64f)
                        )
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    ) {
                        BasicText(
                            action.title,
                            style = TextStyle(
                                contentColor.copy(alpha = if (action.enabled) 1f else 0.68f),
                                14.sp,
                                if (action.isBackAction) FontWeight.SemiBold else FontWeight.Medium
                            )
                        )
                        if (isUpdateAction && updateStatus.isNotBlank()) {
                            BasicText(
                                updateStatus,
                                modifier = Modifier.padding(top = 2.dp),
                                style = TextStyle(contentColor.copy(alpha = 0.64f), 10.sp)
                            )
                        }
                    }
                    if (action.hasSubmenu) {
                        Image(
                            painter = painterResource(R.drawable.ic_expand_chevron),
                            contentDescription = null,
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer { rotationZ = -90f },
                            colorFilter = ColorFilter.tint(contentColor.copy(alpha = 0.58f))
                        )
                    }
                }
                if (action.dividerAfter) {
                    Box(
                        Modifier
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .graphicsLayer { alpha = itemProgress }
                            .then(
                                if (hasCustomBackground) {
                                    Modifier.background(Color.White.copy(alpha = 0.42f))
                                } else {
                                    Modifier.background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                themeColors.divider,
                                                themeColors.divider,
                                                Color.Transparent
                                            )
                                        )
                                    )
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidScoreTermDropdown(
    pageSnapshot: Bitmap?,
    menuX: Int,
    menuY: Int,
    menuWidth: Int,
    maxMenuHeight: Int,
    expandDownward: Boolean,
    terms: List<String>,
    selectedTerm: String,
    expanded: Boolean,
    onTermSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val panelShape = RoundedCornerShape(22.dp)
    val itemShape = RoundedCornerShape(14.dp)
    val themeColors = CampusComposeTheme.colors
    val contentColor = themeColors.primaryText
    val accentColor = themeColors.accent
    val scrollState = rememberScrollState()
    var slidingIndex by remember { mutableStateOf<Int?>(null) }
    val menuWidthDp = with(density) { menuWidth.toDp() }
    val maxMenuHeightDp = with(density) { maxMenuHeight.toDp() }
    val menuBackdropHeight = minOf(
        maxMenuHeight,
        with(density) { (terms.size * 44 + 12).dp.roundToPx() }
    )
    val revealProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) 260 else LIQUID_MENU_COLLAPSE_DURATION_MS.toInt(),
            easing = if (expanded) {
                CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
            } else {
                CubicBezierEasing(0.4f, 0f, 1f, 1f)
            }
        ),
        label = "scoreTermMenuReveal"
    )
    val panelOffsetPx = with(density) { 7.dp.toPx() }
    val itemOffsetPx = with(density) { 5.dp.toPx() }

    LaunchedEffect(scrollState.maxValue, selectedTerm) {
        val selectedIndex = terms.indexOf(selectedTerm)
        if (scrollState.maxValue > 0 && selectedIndex >= 0) {
            val denominator = (terms.size - 1).coerceAtLeast(1)
            scrollState.scrollTo(scrollState.maxValue * selectedIndex / denominator)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                // Keep a full-size recorded layer so blur and lens effects can
                // sample pixels beyond the panel edges. Only the on-screen copy
                // is clipped to the panel, leaving the live term arrow visible.
                .drawWithContent {
                    clipRect(
                        left = menuX.toFloat(),
                        top = menuY.toFloat(),
                        right = (menuX + menuWidth).toFloat(),
                        bottom = (menuY + menuBackdropHeight).toFloat()
                    ) {
                        this@drawWithContent.drawContent()
                    }
                }
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
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
        )
        Box(
            Modifier
                .offset { IntOffset(menuX, menuY) }
                .width(menuWidthDp)
                .heightIn(max = maxMenuHeightDp)
                .graphicsLayer {
                    alpha = revealProgress
                    translationY = (if (expandDownward) -panelOffsetPx else panelOffsetPx) *
                        (1f - revealProgress)
                    scaleX = 0.96f + 0.04f * revealProgress
                    scaleY = 0.80f + 0.20f * revealProgress
                    transformOrigin = TransformOrigin(0.5f, if (expandDownward) 0f else 1f)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(22.dp) },
                    effects = {
                        vibrancy()
                        colorControls(
                            brightness = if (themeColors.isDark) 0f else 0.06f,
                            saturation = if (themeColors.isDark) 0.52f else 1.25f
                        )
                        blur((if (themeColors.isDark) 8.dp else 12.dp).toPx())
                        lens(12.dp.toPx(), 24.dp.toPx(), depthEffect = true)
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = if (themeColors.isDark) 0.12f else 0.68f)
                    },
                    onDrawSurface = {
                        drawRect(
                            if (themeColors.isDark) themeColors.glassStrongSurface
                            else themeColors.glassSurface
                        )
                    }
                )
                .clip(panelShape)
                .pointerInput(terms, scrollState.maxValue) {
                    val panelPadding = 6.dp.toPx()
                    val rowHeight = 44.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var lastY = down.position.y
                        var scrolled = false

                        fun termIndexAt(y: Float): Int? {
                            if (y < panelPadding || y > size.height - panelPadding) return null
                            val contentY = y - panelPadding + scrollState.value
                            val index = (contentY / rowHeight).toInt()
                            return index.takeIf { it in terms.indices }
                        }

                        slidingIndex = termIndexAt(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val currentY = change.position.y
                            val deltaY = currentY - lastY
                            lastY = currentY

                            if (change.pressed) {
                                if (
                                    scrollState.maxValue > 0 &&
                                    abs(currentY - down.position.y) > viewConfiguration.touchSlop
                                ) {
                                    scrolled = true
                                    scrollState.dispatchRawDelta(-deltaY)
                                }
                                slidingIndex = termIndexAt(currentY)
                                change.consume()
                            } else {
                                val selectedIndex = slidingIndex
                                slidingIndex = null
                                if (!scrolled && selectedIndex != null) {
                                    onTermSelected(terms[selectedIndex])
                                }
                                break
                            }
                        }
                    }
                }
                .padding(6.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState, enabled = false)
            ) {
                terms.forEachIndexed { index, term ->
                    val active = term == selectedTerm
                    val itemProgress by animateFloatAsState(
                        targetValue = if (expanded) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = if (expanded) 170 else 90,
                            delayMillis = if (expanded) 38 + index.coerceAtMost(5) * 28 else 0,
                            easing = if (expanded) {
                                CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
                            } else {
                                CubicBezierEasing(0.4f, 0f, 1f, 1f)
                            }
                        ),
                        label = "scoreTermItemEntry"
                    )
                    val highlightProgress by animateFloatAsState(
                        targetValue = if (slidingIndex == index) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
                        label = "scoreTermHighlight"
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .graphicsLayer {
                                val selectedScale = 1f + highlightProgress * 0.018f
                                val entryScale = 0.985f + itemProgress * 0.015f
                                alpha = itemProgress
                                translationY = -itemOffsetPx * (1f - itemProgress)
                                scaleX = selectedScale * entryScale
                                scaleY = selectedScale * entryScale
                            }
                            .clip(itemShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (active) 0.18f else 0f),
                                        accentColor.copy(alpha = if (active) 0.14f else 0f)
                                    )
                                ),
                                shape = itemShape
                            )
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.48f * highlightProgress),
                                        Color.White.copy(alpha = 0.17f * highlightProgress),
                                        Color(0xFFBDE5FF).copy(alpha = 0.23f * highlightProgress)
                                    ),
                                    start = Offset.Zero,
                                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                ),
                                shape = itemShape
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.88f * highlightProgress),
                                        Color(0xFF98D7FF).copy(alpha = 0.42f * highlightProgress),
                                        Color.White.copy(alpha = 0.62f * highlightProgress)
                                    )
                                ),
                                shape = itemShape
                            )
                            .semantics {
                                role = Role.Button
                                contentDescription = term
                            }
                            .padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            term,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                color = if (active) accentColor else contentColor,
                                fontSize = 14.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                        if (active) {
                            Image(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                colorFilter = ColorFilter.tint(accentColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Generic picker that uses the exact same captured-page glass shell as the update dialog. */

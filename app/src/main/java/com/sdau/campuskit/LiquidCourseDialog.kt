package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Color as AndroidColor
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.EditText
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import kotlinx.coroutines.delay
/** Course details and editing form. */
internal class LiquidCourseDialogView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    courseName: String,
    room: String,
    teacher: String,
    slotText: String,
    scheduleTitle: String = slotText,
    weeks: String,
    canEdit: Boolean,
    creating: Boolean = false,
    initialSlotCount: Int = 1,
    maxSlotCount: Int = 1,
    allowDurationEdit: Boolean = false,
    onSave: (
        name: String,
        room: String,
        teacher: String,
        weeks: String,
        slotCount: Int
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) : FrameLayout(context) {
    private val hostImeVisible = mutableStateOf(false)
    private val visibleWindowFrame = Rect()
    private val keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        updateImeVisibilityFromWindow()
    }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val visible = insets.isVisible(WindowInsetsCompat.Type.ime()) ||
                imeInsets.bottom > navigationInsets.bottom
            if (hostImeVisible.value != visible) hostImeVisible.value = visible
            insets
        }
        addView(
            composeHostView(context) {
                LiquidCourseDialog(
                    pageSnapshot = pageSnapshot,
                    initialCourseName = courseName,
                    initialRoom = room,
                    initialTeacher = teacher,
                    slotText = slotText,
                    scheduleTitle = scheduleTitle,
                    initialWeeks = weeks,
                    canEdit = canEdit,
                    creating = creating,
                    initialSlotCount = initialSlotCount,
                    maxSlotCount = maxSlotCount,
                    allowDurationEdit = allowDurationEdit,
                    hostImeVisible = hostImeVisible.value,
                    onSave = onSave,
                    onDelete = onDelete,
                    onDismiss = onDismiss
                )
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
        ViewCompat.requestApplyInsets(this)
        post(::updateImeVisibilityFromWindow)
    }

    override fun onDetachedFromWindow() {
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnGlobalLayoutListener(keyboardLayoutListener)
        }
        super.onDetachedFromWindow()
    }

    private fun updateImeVisibilityFromWindow() {
        if (!isAttachedToWindow) return
        getWindowVisibleDisplayFrame(visibleWindowFrame)
        val obscuredHeight = (rootView.height - visibleWindowFrame.bottom).coerceAtLeast(0)
        val threshold = (96f * resources.displayMetrics.density).roundToInt()
        val insets = ViewCompat.getRootWindowInsets(this)
        val visibleFromInsets = insets?.let {
            it.isVisible(WindowInsetsCompat.Type.ime()) ||
                it.getInsets(WindowInsetsCompat.Type.ime()).bottom >
                it.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        } == true
        val visible = visibleFromInsets || obscuredHeight > threshold
        if (hostImeVisible.value != visible) hostImeVisible.value = visible
    }

    fun releaseSnapshot() {
        val bitmap = pageSnapshot
        pageSnapshot = null
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}

private enum class CourseDialogIcon { EDIT, SAVE, DELETE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiquidCourseDialog(
    pageSnapshot: Bitmap?,
    initialCourseName: String,
    initialRoom: String,
    initialTeacher: String,
    slotText: String,
    scheduleTitle: String,
    initialWeeks: String,
    canEdit: Boolean,
    creating: Boolean,
    initialSlotCount: Int,
    maxSlotCount: Int,
    allowDurationEdit: Boolean,
    hostImeVisible: Boolean,
    onSave: (
        name: String,
        room: String,
        teacher: String,
        weeks: String,
        slotCount: Int
    ) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val contentColor = themeColors.primaryText
    val secondaryColor = contentColor.copy(alpha = 0.66f)
    val accentColor = themeColors.accent
    val containerColor = if (themeColors.isDark) {
        themeColors.glassStrongSurface
    } else {
        themeColors.glassSurface
    }
    val dimColor = themeColors.dialogScrim
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    var editing by remember(creating) { mutableStateOf(creating) }
    var courseName by remember(initialCourseName) { mutableStateOf(initialCourseName) }
    var room by remember(initialRoom) { mutableStateOf(initialRoom) }
    var teacher by remember(initialTeacher) { mutableStateOf(initialTeacher) }
    var weeks by remember(initialWeeks) { mutableStateOf(initialWeeks) }
    var slotCount by remember(initialSlotCount) { mutableStateOf(initialSlotCount.toString()) }
    val availableSlotCount = maxSlotCount.coerceAtLeast(1)
    val imeVisible = hostImeVisible || WindowInsets.isImeVisible
    var keyboardRaised by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            keyboardRaised = true
        } else {
            delay(220)
            keyboardRaised = false
        }
    }
    val keyboardTranslationPx by animateFloatAsState(
        targetValue = if (keyboardRaised) with(density) { (-118).dp.toPx() } else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "courseDialogKeyboardTranslation"
    )

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
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
        )
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .widthIn(max = 372.dp)
                    .graphicsLayer { translationY = keyboardTranslationPx }
                    .clip(RoundedRectangle(28.dp))
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(28.dp) },
                        effects = {
                            vibrancy()
                            colorControls(
                                brightness = if (themeColors.isDark) 0f else 0.14f,
                                saturation = if (themeColors.isDark) 0.54f else 0.80f
                            )
                            blur((if (themeColors.isDark) 8.dp else 18.dp).toPx())
                            lens(12.dp.toPx(), 24.dp.toPx(), depthEffect = true)
                        },
                        shadow = null,
                        highlight = {
                            Highlight.Default.copy(alpha = if (themeColors.isDark) 0.12f else 0.58f)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .clickable(interactionSource = null, indication = null, onClick = {})
                    .animateContentSize()
            ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 20.dp, end = 18.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    BasicText(
                        when {
                            creating -> "添加课程 · $scheduleTitle"
                            editing -> "修改课程 · $scheduleTitle"
                            else -> "课程详情"
                        },
                        style = TextStyle(secondaryColor, 12.sp, FontWeight.Medium)
                    )
                    BasicText(
                        courseName.ifBlank { if (creating) "新课程" else "未命名课程" },
                        modifier = Modifier.padding(top = 4.dp),
                        style = TextStyle(contentColor, 20.sp, FontWeight.SemiBold)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!editing && canEdit) {
                        CourseLiquidIconButton(
                            backdrop = backdrop,
                            icon = CourseDialogIcon.EDIT,
                            contentDescription = "修改课程",
                            onClick = { editing = true }
                        )
                        if (onDelete != null) {
                            CourseLiquidIconButton(
                                backdrop = backdrop,
                                icon = CourseDialogIcon.DELETE,
                                contentDescription = "删除课程",
                                onClick = onDelete
                            )
                        }
                    }
                    if (editing) {
                        CourseLiquidIconButton(
                            backdrop = backdrop,
                            icon = CourseDialogIcon.SAVE,
                            contentDescription = if (creating) "添加课程" else "保存修改",
                            onClick = {
                                onSave(
                                    courseName,
                                    room,
                                    teacher,
                                    weeks,
                                    slotCount.toIntOrNull()
                                        ?.coerceIn(1, availableSlotCount)
                                        ?: 1
                                )
                            }
                        )
                    }
                }
            }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 22.dp)
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                if (editing) {
                    CourseLiquidTextField(
                        label = "课程名",
                        value = courseName,
                        keyboardAlreadyVisible = keyboardRaised,
                        onValueChange = { courseName = it }
                    )
                    CourseLiquidTextField(
                        label = "地点",
                        value = room,
                        keyboardAlreadyVisible = keyboardRaised,
                        onValueChange = { room = it }
                    )
                    CourseLiquidTextField(
                        label = "教师",
                        value = teacher,
                        keyboardAlreadyVisible = keyboardRaised,
                        onValueChange = { teacher = it }
                    )
                    CourseLiquidTextField(
                        label = "周数（如1-16；1，2，3；7，8，9，11-16）",
                        value = weeks,
                        keyboardAlreadyVisible = keyboardRaised,
                        onValueChange = { weeks = it }
                    )
                    if (creating || allowDurationEdit) {
                        CourseLiquidTextField(
                            label = "持续节数（1-$availableSlotCount）",
                            value = slotCount,
                            keyboardAlreadyVisible = keyboardRaised,
                            onValueChange = { input ->
                                slotCount = input.filter(Char::isDigit).take(2)
                            }
                        )
                    }
                } else {
                    CourseDetailLine(
                        label = "地点",
                        value = "@${room.ifBlank { "-" }}",
                        iconRes = R.drawable.ic_detail_location,
                        contentColor = contentColor,
                        secondaryColor = secondaryColor
                    )
                    CourseDetailLine(
                        label = "教师",
                        value = teacher.ifBlank { "-" },
                        iconRes = R.drawable.ic_detail_teacher,
                        contentColor = contentColor,
                        secondaryColor = secondaryColor
                    )
                    CourseDetailLine(
                        label = "节次",
                        value = slotText,
                        iconRes = R.drawable.ic_detail_time,
                        contentColor = contentColor,
                        secondaryColor = secondaryColor
                    )
                    CourseDetailLine(
                        label = "周数",
                        value = weeks.ifBlank { "-" },
                        iconRes = R.drawable.ic_detail_week,
                        contentColor = contentColor,
                        secondaryColor = secondaryColor
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun CourseDetailLine(
    label: String,
    value: String,
    iconRes: Int,
    contentColor: Color,
    secondaryColor: Color
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(CampusComposeTheme.colors.accent)
        )
        BasicText(
            label,
            modifier = Modifier.padding(start = 10.dp).width(42.dp),
            style = TextStyle(secondaryColor, 12.sp, FontWeight.Bold)
        )
        BasicText(
            value,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            style = TextStyle(contentColor, 15.sp, FontWeight.Medium)
        )
    }
}

@Composable
private fun CourseLiquidTextField(
    label: String,
    value: String,
    keyboardAlreadyVisible: Boolean,
    onValueChange: (String) -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val contentColor = themeColors.primaryText
    val fieldShape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            // The dialog shell has already sampled and blurred the page. Sampling the
            // root backdrop again here would reveal a clearer copy of the original
            // timetable inside every field, so fields only tint the blurred shell.
            .clip(fieldShape)
            .background(themeColors.glassSubtleSurface, fieldShape)
            .border(1.dp, themeColors.glassOutline, fieldShape)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        BasicText(label, style = TextStyle(contentColor.copy(alpha = 0.62f), 10.sp, FontWeight.Medium))
        AndroidView(
            factory = { context ->
                CourseEditText(context).apply {
                    setTextColor(AndroidColor.rgb(23, 25, 35))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setSingleLine(true)
                    includeFontPadding = false
                    gravity = Gravity.CENTER_VERTICAL
                    background = null
                    setPadding(0, 0, 0, 0)
                    inputType = InputType.TYPE_CLASS_TEXT
                    imeOptions = EditorInfo.IME_ACTION_NEXT
                }
            },
            update = { field ->
                field.setTextColor(
                    if (themeColors.isDark) AndroidColor.rgb(243, 245, 248)
                    else AndroidColor.rgb(23, 25, 35)
                )
                field.onCourseTextChanged = onValueChange
                field.updateCourseText(value)
                // When the IME is already on screen, changing fields must only move
                // the input connection. Requesting showSoftInput again makes several
                // OEM keyboards replay their complete entrance animation.
                field.showSoftInputOnFocus = !keyboardAlreadyVisible
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(top = 1.dp)
        )
    }
}

private class CourseEditText(context: Context) : EditText(context) {
    var onCourseTextChanged: (String) -> Unit = {}
    private var applyingExternalText = false

    init {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!applyingExternalText) onCourseTextChanged(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    fun updateCourseText(value: String) {
        if (text?.toString() == value) return
        applyingExternalText = true
        setText(value)
        setSelection(value.length)
        applyingExternalText = false
    }
}

@Composable
private fun CourseLiquidIconButton(
    backdrop: com.kyant.backdrop.Backdrop,
    icon: CourseDialogIcon,
    contentDescription: String,
    onClick: () -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "courseDialogIconScale"
    )
    val themeColors = CampusComposeTheme.colors
    val accentColor = if (icon == CourseDialogIcon.DELETE) Color(0xFFF05252) else themeColors.accent
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    colorControls(
                        brightness = if (themeColors.isDark) 0f else 0.14f,
                        saturation = if (themeColors.isDark) 0.54f else 0.84f
                    )
                    blur(8.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                highlight = {
                    Highlight.Default.copy(
                        alpha = interactiveHighlight.pressProgress *
                            if (themeColors.isDark) 0.18f else 0.68f
                    )
                },
                onDrawSurface = {
                    drawRect(
                        if (themeColors.isDark) themeColors.glassStrongSurface
                        else themeColors.glassSurface
                    )
                }
            )
            .then(
                if (themeColors.isDark) {
                    Modifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.28f),
                        shape = CircleShape
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.82f),
                        shape = CircleShape
                    )
                }
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        when (icon) {
            CourseDialogIcon.EDIT -> Image(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(accentColor)
            )
            CourseDialogIcon.SAVE -> Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(accentColor)
            )
            CourseDialogIcon.DELETE -> Canvas(Modifier.size(20.dp)) {
                val stroke = 2.dp.toPx()
                drawLine(accentColor, Offset(size.width * .25f, size.height * .28f), Offset(size.width * .75f, size.height * .28f), stroke, StrokeCap.Round)
                drawLine(accentColor, Offset(size.width * .40f, size.height * .18f), Offset(size.width * .60f, size.height * .18f), stroke, StrokeCap.Round)
                drawLine(accentColor, Offset(size.width * .31f, size.height * .38f), Offset(size.width * .36f, size.height * .82f), stroke, StrokeCap.Round)
                drawLine(accentColor, Offset(size.width * .69f, size.height * .38f), Offset(size.width * .64f, size.height * .82f), stroke, StrokeCap.Round)
                drawLine(accentColor, Offset(size.width * .36f, size.height * .82f), Offset(size.width * .64f, size.height * .82f), stroke, StrokeCap.Round)
                drawLine(accentColor, Offset(size.width * .44f, size.height * .43f), Offset(size.width * .44f, size.height * .70f), stroke * .8f, StrokeCap.Round)
                drawLine(accentColor, Offset(size.width * .56f, size.height * .43f), Offset(size.width * .56f, size.height * .70f), stroke * .8f, StrokeCap.Round)
            }
        }
    }
}

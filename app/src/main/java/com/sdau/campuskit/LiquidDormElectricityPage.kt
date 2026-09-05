package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

private fun dormOutlineColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.18f)
    else Color(0xFF64748B).copy(alpha = 0.34f)

internal enum class DormElectricityLoading {
    CAMPUSES,
    BUILDINGS,
    ROOMS,
    QUERY,
    RECHARGE
}

internal data class DormElectricityUiState(
    val campuses: List<DormElectricityOption> = emptyList(),
    val buildings: List<DormElectricityOption> = emptyList(),
    val rooms: List<DormElectricityOption> = emptyList(),
    val equipmentTypes: List<DormElectricityOption> = DormElectricityPolicy.defaultTypes,
    val campus: DormElectricityOption? = null,
    val building: DormElectricityOption? = null,
    val room: DormElectricityOption? = null,
    val equipment: DormElectricityOption? = DormElectricityPolicy.defaultTypes.firstOrNull(),
    val reading: DormElectricityReading? = null,
    val rechargeQr: DormRechargeQr? = null,
    val rechargeError: String? = null,
    val rechargeHistory: List<DormRechargeHistoryEntry> = emptyList(),
    val loading: DormElectricityLoading? = null,
    val error: String? = null
)

internal class LiquidDormElectricityPageView(
    context: Context,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    textPalette: ScheduleTextPalette,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCampusSelected: (DormElectricityOption) -> Unit,
    onBuildingSelected: (DormElectricityOption) -> Unit,
    onRoomSelected: (DormElectricityOption) -> Unit,
    onEquipmentSelected: (DormElectricityOption) -> Unit,
    onQuery: () -> Unit,
    onRecharge: (Double) -> Unit,
    onSaveRechargeQr: () -> Unit,
    onDeleteRechargeHistory: (String) -> Unit,
    onCompleteRechargeQr: () -> Unit,
    onCancelRechargeQr: () -> Unit
) : FrameLayout(context) {
    private var state by mutableStateOf(DormElectricityUiState())

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(
            composeHostView(context) {
                DormElectricityPage(
                    state = state,
                    pageBackgroundBitmap = pageBackgroundBitmap,
                    pageBackgroundScrim = pageBackgroundScrim,
                    textPalette = textPalette,
                    onBack = onBack,
                    onRefresh = onRefresh,
                    onCampusSelected = onCampusSelected,
                    onBuildingSelected = onBuildingSelected,
                    onRoomSelected = onRoomSelected,
                    onEquipmentSelected = onEquipmentSelected,
                    onQuery = onQuery,
                    onRecharge = onRecharge,
                    onSaveRechargeQr = onSaveRechargeQr,
                    onDeleteRechargeHistory = onDeleteRechargeHistory,
                    onCompleteRechargeQr = onCompleteRechargeQr,
                    onCancelRechargeQr = onCancelRechargeQr
                )
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun render(next: DormElectricityUiState) {
        state = next
    }
}

private enum class DormPickerTarget(val title: String) {
    CAMPUS("选择校区"),
    BUILDING("选择楼栋"),
    ROOM("选择房间"),
    EQUIPMENT("选择用电类型")
}

@Composable
private fun DormElectricityPage(
    state: DormElectricityUiState,
    pageBackgroundBitmap: Bitmap?,
    pageBackgroundScrim: Int,
    textPalette: ScheduleTextPalette,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCampusSelected: (DormElectricityOption) -> Unit,
    onBuildingSelected: (DormElectricityOption) -> Unit,
    onRoomSelected: (DormElectricityOption) -> Unit,
    onEquipmentSelected: (DormElectricityOption) -> Unit,
    onQuery: () -> Unit,
    onRecharge: (Double) -> Unit,
    onSaveRechargeQr: () -> Unit,
    onDeleteRechargeHistory: (String) -> Unit,
    onCompleteRechargeQr: () -> Unit,
    onCancelRechargeQr: () -> Unit
) {
    val theme = CampusComposeTheme.colors
    val backdrop = rememberLayerBackdrop()
    val background: ImageBitmap? = remember(pageBackgroundBitmap) { pageBackgroundBitmap?.asImageBitmap() }
    val primary = Color(textPalette.primary)
    val secondary = Color(textPalette.secondary)
    val shadow = dormTextShadow(textPalette)
    var picker by remember { mutableStateOf<DormPickerTarget?>(null) }
    var showRecharge by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var selectedHistoryEntry by remember { mutableStateOf<DormRechargeHistoryEntry?>(null) }

    Box(Modifier.fillMaxSize()) {
        PageAlignedBackdropSource(
            backdrop = backdrop,
            pageBackgroundImage = background,
            pageBackgroundScrim = pageBackgroundScrim,
            pageGradient = Brush.linearGradient(theme.pageGradient)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, top = 7.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            item("dorm_header") {
                DormHeader(primary, shadow, state.loading != null, onBack, onRefresh)
            }
            item("dorm_result") {
                DormResultCard(backdrop, state, primary, secondary, shadow) { showRecharge = true }
            }
            item("dorm_condition_title") {
                androidx.compose.foundation.text.BasicText(
                    "宿舍信息",
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    style = TextStyle(primary, 20.sp, FontWeight.ExtraBold, shadow = shadow)
                )
            }
            item("dorm_conditions") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DormSelector(
                            backdrop,
                            "校区",
                            state.campus?.label ?: "请选择",
                            state.campuses.isNotEmpty() && state.loading != DormElectricityLoading.CAMPUSES,
                            Modifier.weight(1f),
                            primary,
                            secondary,
                            shadow
                        ) { picker = DormPickerTarget.CAMPUS }
                        DormSelector(
                            backdrop,
                            "楼栋",
                            state.building?.label ?: "请选择",
                            state.campus != null && state.buildings.isNotEmpty() && state.loading != DormElectricityLoading.BUILDINGS,
                            Modifier.weight(1f),
                            primary,
                            secondary,
                            shadow
                        ) { picker = DormPickerTarget.BUILDING }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DormSelector(
                            backdrop,
                            "房间",
                            state.room?.label ?: "请选择",
                            state.building != null && state.rooms.isNotEmpty() && state.loading != DormElectricityLoading.ROOMS,
                            Modifier.weight(1f),
                            primary,
                            secondary,
                            shadow
                        ) { picker = DormPickerTarget.ROOM }
                        DormSelector(
                            backdrop,
                            "用电类型",
                            state.equipment?.label ?: "请选择",
                            state.equipmentTypes.isNotEmpty(),
                            Modifier.weight(1f),
                            primary,
                            secondary,
                            shadow
                        ) { picker = DormPickerTarget.EQUIPMENT }
                    }
                }
            }
            item("dorm_query") {
                CampusLiquidButton(
                    onClick = onQuery,
                    backdrop = backdrop,
                    style = LiquidButtonStyle.TINTED,
                    enabled = state.campus != null && state.building != null && state.room != null &&
                        state.equipment != null && state.loading == null,
                    allowDragDeformation = false,
                    modifier = Modifier.fillMaxWidth(),
                    height = 54.dp
                ) {
                    androidx.compose.foundation.text.BasicText(
                        if (state.loading == DormElectricityLoading.QUERY) "正在查询" else "查询剩余电量",
                        style = TextStyle(Color.White, 17.sp, FontWeight.Bold)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = picker != null,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f)
        ) {
            val target = picker
            if (target != null) {
                val options = when (target) {
                    DormPickerTarget.CAMPUS -> state.campuses
                    DormPickerTarget.BUILDING -> state.buildings
                    DormPickerTarget.ROOM -> state.rooms
                    DormPickerTarget.EQUIPMENT -> state.equipmentTypes
                }
                val selected = when (target) {
                    DormPickerTarget.CAMPUS -> state.campus
                    DormPickerTarget.BUILDING -> state.building
                    DormPickerTarget.ROOM -> state.room
                    DormPickerTarget.EQUIPMENT -> state.equipment
                }
                DormPicker(
                    backdrop = backdrop,
                    title = target.title,
                    options = options,
                    selected = selected,
                    primary = primary,
                    secondary = secondary,
                    shadow = shadow,
                    searchableRoom = target == DormPickerTarget.ROOM,
                    onDismiss = { picker = null },
                    onSelected = { option ->
                        when (target) {
                            DormPickerTarget.CAMPUS -> onCampusSelected(option)
                            DormPickerTarget.BUILDING -> onBuildingSelected(option)
                            DormPickerTarget.ROOM -> onRoomSelected(option)
                            DormPickerTarget.EQUIPMENT -> onEquipmentSelected(option)
                        }
                        picker = null
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showRecharge || state.rechargeQr != null,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f)
        ) {
            DormRechargeDialog(
                backdrop = backdrop,
                state = state,
                primary = primary,
                secondary = secondary,
                shadow = shadow,
                onCancel = {
                    showRecharge = false
                    onCancelRechargeQr()
                },
                onComplete = {
                    showRecharge = false
                    onCompleteRechargeQr()
                },
                onRecharge = onRecharge,
                onSaveRechargeQr = onSaveRechargeQr
            )
        }

        DormHistoryButton(
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 24.dp),
            onClick = { showHistory = true }
        )

        AnimatedVisibility(
            visible = showHistory,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f)
        ) {
            DormRechargeHistoryDialog(
                backdrop = backdrop,
                entries = state.rechargeHistory,
                primary = primary,
                secondary = secondary,
                shadow = shadow,
                onDismiss = { showHistory = false },
                onEntryClick = { selectedHistoryEntry = it }
            )
        }

        AnimatedVisibility(
            visible = selectedHistoryEntry != null,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f)
        ) {
            selectedHistoryEntry?.let { entry ->
                DormRechargeHistoryDetailDialog(
                    backdrop = backdrop,
                    entry = entry,
                    primary = primary,
                    secondary = secondary,
                    shadow = shadow,
                    onDismiss = { selectedHistoryEntry = null },
                    onDelete = {
                        selectedHistoryEntry = null
                        onDeleteRechargeHistory(entry.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun DormHeader(
    primary: Color,
    shadow: Shadow?,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DormHeaderButton(R.drawable.ic_back, "返回", true, onBack)
        androidx.compose.foundation.text.BasicText(
            "宿舍用电",
            modifier = Modifier.weight(1f),
            style = TextStyle(primary, 25.sp, FontWeight.Bold, shadow = shadow)
        )
        DormHeaderButton(R.drawable.ic_menu_refresh, "刷新", !loading, onRefresh)
    }
}

@Composable
private fun DormHeaderButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(CampusComposeTheme.colors.accent.copy(alpha = if (enabled) 1f else 0.4f))
        )
    }
}

@Composable
private fun DormResultCard(
    backdrop: Backdrop,
    state: DormElectricityUiState,
    primary: Color,
    secondary: Color,
    shadow: Shadow?,
    onRecharge: () -> Unit
) {
    val theme = CampusComposeTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 204.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(26.dp) },
                effects = {
                    vibrancy()
                    if (theme.isDark) colorControls(brightness = 0f, saturation = 0.58f)
                    lens(16.dp.toPx(), 32.dp.toPx())
                },
                shadow = null,
                highlight = { Highlight.Default.copy(alpha = if (theme.isDark) 0.14f else 0.48f) },
                onDrawSurface = { drawRect(theme.glassSurface) }
            )
            .border(1.dp, dormOutlineColor(theme.isDark), RoundedCornerShape(26.dp))
            .padding(horizontal = 23.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.text.BasicText(
                "剩余电量",
                style = TextStyle(secondary, 14.sp, FontWeight.SemiBold, shadow = shadow)
            )
            if (state.reading != null) {
                Box(
                    Modifier
                        .clickable(enabled = state.loading == null, onClick = onRecharge)
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicText(
                        "充值",
                        style = TextStyle(
                            color = theme.accent.copy(alpha = if (state.loading == null) 1f else 0.45f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                }
            }
        }
        when {
            state.loading == DormElectricityLoading.QUERY -> {
                Box(Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
                    DormSpinner()
                }
            }
            state.reading != null -> {
                val normalSupply = state.reading.supplyStatus.trim() == "正常供电"
                val supplyColor = if (normalSupply) Color(0xFF469A69) else Color(0xFFC4646C)
                Row(verticalAlignment = Alignment.Bottom) {
                    androidx.compose.foundation.text.BasicText(
                        String.format(Locale.US, "%.2f", state.reading.remainingKwh),
                        style = TextStyle(theme.accent, 47.sp, FontWeight.ExtraBold, shadow = shadow)
                    )
                    Spacer(Modifier.width(7.dp))
                    androidx.compose.foundation.text.BasicText(
                        "kWh",
                        modifier = Modifier.padding(bottom = 7.dp),
                        style = TextStyle(secondary, 15.sp, FontWeight.SemiBold, shadow = shadow)
                    )
                }
                androidx.compose.foundation.text.BasicText(
                    state.reading.supplyStatus,
                    style = TextStyle(supplyColor, 17.sp, FontWeight.Bold, shadow = shadow)
                )
                androidx.compose.foundation.text.BasicText(
                    state.reading.location,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(secondary, 13.sp, FontWeight.SemiBold, shadow = shadow)
                )
            }
            state.error != null -> {
                androidx.compose.foundation.text.BasicText(
                    "查询失败",
                    style = TextStyle(theme.error, 26.sp, FontWeight.ExtraBold, shadow = shadow)
                )
                androidx.compose.foundation.text.BasicText(
                    state.error,
                    style = TextStyle(secondary, 14.sp, FontWeight.Medium, shadow = shadow)
                )
            }
            else -> {
                androidx.compose.foundation.text.BasicText(
                    "--",
                    style = TextStyle(theme.accent, 47.sp, FontWeight.ExtraBold, shadow = shadow)
                )
                androidx.compose.foundation.text.BasicText(
                    "选择校区、楼栋和房间后查询",
                    style = TextStyle(secondary, 14.sp, FontWeight.Medium, shadow = shadow)
                )
            }
        }
    }
}

@Composable
private fun DormSelector(
    backdrop: Backdrop,
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier,
    primary: Color,
    secondary: Color,
    shadow: Shadow?,
    onClick: () -> Unit
) {
    val theme = CampusComposeTheme.colors
    Row(
        modifier
            .height(72.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(22.dp) },
                effects = {
                    vibrancy()
                    if (theme.isDark) colorControls(brightness = 0f, saturation = 0.55f)
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                shadow = null,
                highlight = { Highlight.Default.copy(alpha = if (theme.isDark) 0.12f else 0.44f) },
                onDrawSurface = { drawRect(theme.glassSurface) }
            )
            .border(1.dp, dormOutlineColor(theme.isDark), RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 17.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            androidx.compose.foundation.text.BasicText(
                label,
                style = TextStyle(secondary.copy(alpha = if (enabled) 1f else 0.58f), 13.sp, FontWeight.Medium, shadow = shadow)
            )
            androidx.compose.foundation.text.BasicText(
                value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(primary.copy(alpha = if (enabled) 1f else 0.58f), 15.sp, FontWeight.Bold, shadow = shadow)
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_expand_chevron),
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            colorFilter = ColorFilter.tint(secondary.copy(alpha = if (enabled) 0.9f else 0.35f))
        )
    }
}

@Composable
private fun DormRechargeDialog(
    backdrop: Backdrop,
    state: DormElectricityUiState,
    primary: Color,
    secondary: Color,
    shadow: Shadow?,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    onRecharge: (Double) -> Unit,
    onSaveRechargeQr: () -> Unit
) {
    val theme = CampusComposeTheme.colors
    var amountText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val qrBitmap = remember(state.rechargeQr?.imageBytes) {
        state.rechargeQr?.imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    BackHandler(enabled = qrBitmap != null) { }
    Box(
        Modifier.fillMaxSize().clickable(
            enabled = qrBitmap == null && state.loading != DormElectricityLoading.RECHARGE,
            interactionSource = null,
            indication = null,
            onClick = onCancel
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(30.dp) },
                    effects = {
                        vibrancy()
                        colorControls(
                            brightness = if (theme.isDark) 0f else 0.12f,
                            saturation = if (theme.isDark) 0.46f else 0.62f
                        )
                        lens(18.dp.toPx(), 36.dp.toPx())
                    },
                    shadow = null,
                    highlight = { Highlight.Default.copy(alpha = if (theme.isDark) 0.18f else 0.58f) },
                    onDrawSurface = { drawRect(theme.glassStrongSurface) }
                )
                .clip(RoundedCornerShape(30.dp))
                .clickable(interactionSource = null, indication = null, onClick = {})
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (qrBitmap == null) {
                androidx.compose.foundation.text.BasicText(
                    "电费充值",
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(primary, 24.sp, FontWeight.ExtraBold, shadow = shadow)
                )
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicText(
                        "充值二维码",
                        modifier = Modifier.weight(1f),
                        style = TextStyle(primary, 24.sp, FontWeight.ExtraBold, shadow = shadow)
                    )
                    androidx.compose.foundation.text.BasicText(
                        "保存",
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onSaveRechargeQr)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        style = TextStyle(theme.accent, 15.sp, FontWeight.Bold, shadow = shadow)
                    )
                }
            }
            if (qrBitmap != null) {
                Box(
                    Modifier
                        .size(218.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(qrBitmap, contentDescription = "充值二维码", modifier = Modifier.fillMaxSize())
                }
                androidx.compose.foundation.text.BasicText(
                    "充值金额：${String.format(Locale.US, "%.2f", state.rechargeQr?.amount ?: 0.0)} 元",
                    style = TextStyle(primary, 17.sp, FontWeight.Bold, shadow = shadow)
                )
                androidx.compose.foundation.text.BasicText(
                    "请使用中国建设银行APP扫码完成充值",
                    style = TextStyle(secondary, 13.sp, FontWeight.Medium, shadow = shadow)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuietDialogAction(
                        label = "取消",
                        foreground = primary,
                        enabled = true,
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        height = 50.dp
                    )
                    CampusLiquidButton(
                        onClick = onComplete,
                        backdrop = backdrop,
                        style = LiquidButtonStyle.TINTED,
                        enabled = true,
                        allowDragDeformation = false,
                        deformationHorizontalPadding = 0.dp,
                        deformationVerticalPadding = 0.dp,
                        modifier = Modifier.weight(1f),
                        height = 50.dp
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            "完成",
                            style = TextStyle(Color.White, 16.sp, FontWeight.Bold)
                        )
                    }
                }
            } else {
                BasicTextField(
                    value = amountText,
                    onValueChange = { value ->
                        if (value.length <= 7 && value.matches(Regex("\\d{0,3}(\\.\\d{0,2})?"))) {
                            amountText = value
                            inputError = null
                        }
                    },
                    enabled = state.loading != DormElectricityLoading.RECHARGE,
                    textStyle = TextStyle(primary, 20.sp, FontWeight.Bold, shadow = shadow),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(theme.accent),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(theme.glassSurface)
                        .border(1.dp, dormOutlineColor(theme.isDark), RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp),
                    decorationBox = { inner ->
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                if (amountText.isBlank()) {
                                    androidx.compose.foundation.text.BasicText(
                                        "输入充值金额",
                                        style = TextStyle(secondary.copy(alpha = 0.75f), 17.sp, FontWeight.Medium)
                                    )
                                }
                                inner()
                            }
                            androidx.compose.foundation.text.BasicText(
                                "元",
                                style = TextStyle(secondary, 16.sp, FontWeight.Bold, shadow = shadow)
                            )
                        }
                    }
                )
                val errorText = inputError ?: state.rechargeError
                if (!errorText.isNullOrBlank()) {
                    androidx.compose.foundation.text.BasicText(
                        errorText,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(theme.error, 13.sp, FontWeight.SemiBold, shadow = shadow)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuietDialogAction(
                        label = "取消",
                        foreground = primary,
                        enabled = state.loading != DormElectricityLoading.RECHARGE,
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        height = 50.dp
                    )
                    CampusLiquidButton(
                        onClick = {
                            val amount = amountText.toDoubleOrNull()
                            if (amount == null || amount <= 0.0 || amount > 100.0) {
                                inputError = "请输入 0.01～100 元的充值金额"
                            } else {
                                onRecharge(amount)
                            }
                        },
                        backdrop = backdrop,
                        style = LiquidButtonStyle.TINTED,
                        enabled = state.loading != DormElectricityLoading.RECHARGE,
                        allowDragDeformation = false,
                        deformationHorizontalPadding = 0.dp,
                        deformationVerticalPadding = 0.dp,
                        modifier = Modifier.weight(1f),
                        height = 50.dp
                    ) {
                        if (state.loading == DormElectricityLoading.RECHARGE) {
                            DormSpinner(size = 22.dp, strokeWidth = 2.4.dp)
                        } else {
                            androidx.compose.foundation.text.BasicText(
                                "确认",
                                style = TextStyle(Color.White, 15.sp, FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DormPicker(
    backdrop: Backdrop,
    title: String,
    options: List<DormElectricityOption>,
    selected: DormElectricityOption?,
    primary: Color,
    secondary: Color,
    shadow: Shadow?,
    searchableRoom: Boolean,
    onDismiss: () -> Unit,
    onSelected: (DormElectricityOption) -> Unit
) {
    val theme = CampusComposeTheme.colors
    var searchText by remember(searchableRoom) { mutableStateOf("") }
    val visibleOptions = remember(options, searchText, searchableRoom) {
        if (!searchableRoom || searchText.isBlank()) options
        else options.filter { option ->
            option.label.filter(Char::isDigit).contains(searchText) ||
                option.code.filter(Char::isDigit).contains(searchText)
        }
    }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(horizontal = 30.dp)
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(28.dp) },
                    effects = {
                        vibrancy()
                        colorControls(
                            brightness = if (theme.isDark) 0f else 0.12f,
                            saturation = if (theme.isDark) 0.48f else 0.64f
                        )
                        lens(16.dp.toPx(), 32.dp.toPx())
                    },
                    shadow = null,
                    highlight = { Highlight.Default.copy(alpha = if (theme.isDark) 0.16f else 0.58f) },
                    onDrawSurface = { drawRect(theme.glassStrongSurface) }
                )
                .clip(RoundedCornerShape(28.dp))
                .clickable(interactionSource = null, indication = null, onClick = {})
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText(
                    title,
                    modifier = Modifier.padding(horizontal = 6.dp).weight(1f),
                    style = TextStyle(primary, 22.sp, FontWeight.ExtraBold, shadow = shadow)
                )
                if (searchableRoom) {
                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it.filter(Char::isDigit).take(8) },
                        textStyle = TextStyle(primary, 15.sp, FontWeight.Bold, shadow = shadow),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        cursorBrush = SolidColor(theme.accent),
                        modifier = Modifier
                            .width(132.dp)
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(theme.glassSurface.copy(alpha = 0.68f))
                            .border(1.dp, dormOutlineColor(theme.isDark), RoundedCornerShape(21.dp))
                            .padding(horizontal = 12.dp),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                if (searchText.isBlank()) {
                                    androidx.compose.foundation.text.BasicText(
                                        "搜索房间",
                                        style = TextStyle(secondary.copy(alpha = 0.72f), 14.sp, FontWeight.Medium)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(visibleOptions, key = { it.code }) { option ->
                    val isSelected = option.code == selected?.code
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelected(option) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            option.label,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                if (isSelected) theme.accent else primary,
                                16.sp,
                                if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                shadow = shadow
                            )
                        )
                        if (isSelected) {
                            androidx.compose.foundation.text.BasicText(
                                "✓",
                                style = TextStyle(theme.accent, 18.sp, FontWeight.Bold)
                            )
                        }
                    }
                }
                if (visibleOptions.isEmpty()) {
                    item("empty") {
                        androidx.compose.foundation.text.BasicText(
                            if (searchableRoom && searchText.isNotBlank()) "房间号不存在，请重新输入" else "暂无可选数据",
                            modifier = Modifier.padding(14.dp),
                            style = TextStyle(secondary, 15.sp, FontWeight.Medium, shadow = shadow)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DormHistoryButton(backdrop: Backdrop, modifier: Modifier, onClick: () -> Unit) {
    val theme = CampusComposeTheme.colors
    Box(
        modifier
            .size(56.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { com.kyant.shapes.Capsule() },
                effects = { vibrancy(); lens(12.dp.toPx(), 24.dp.toPx()) },
                shadow = null,
                highlight = { Highlight.Default.copy(alpha = if (theme.isDark) 0.14f else 0.64f) },
                onDrawSurface = { drawRect(theme.glassSurface) }
            )
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "充值记录" },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_dorm_history),
            contentDescription = null,
            modifier = Modifier.size(27.dp),
            colorFilter = ColorFilter.tint(theme.accent)
        )
    }
}

@Composable
private fun DormRechargeHistoryDialog(
    backdrop: Backdrop,
    entries: List<DormRechargeHistoryEntry>,
    primary: Color,
    secondary: Color,
    shadow: Shadow?,
    onDismiss: () -> Unit,
    onEntryClick: (DormRechargeHistoryEntry) -> Unit
) {
    val theme = CampusComposeTheme.colors
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(610.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(30.dp) },
                    effects = {
                        vibrancy()
                        colorControls(brightness = if (theme.isDark) 0f else 0.12f, saturation = if (theme.isDark) 0.46f else 0.62f)
                        lens(18.dp.toPx(), 36.dp.toPx())
                    },
                    shadow = null,
                    highlight = { Highlight.Default.copy(alpha = if (theme.isDark) 0.18f else 0.58f) },
                    onDrawSurface = { drawRect(theme.glassStrongSurface) }
                )
                .clip(RoundedCornerShape(30.dp))
                .clickable(interactionSource = null, indication = null, onClick = {})
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            androidx.compose.foundation.text.BasicText(
                "充值记录",
                style = TextStyle(primary, 24.sp, FontWeight.ExtraBold, shadow = shadow)
            )
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.text.BasicText(
                        "暂无充值记录",
                        style = TextStyle(secondary, 15.sp, FontWeight.Medium, shadow = shadow)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 490.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(theme.glassSurface.copy(alpha = 0.76f))
                                .border(1.dp, dormOutlineColor(theme.isDark), RoundedCornerShape(20.dp))
                                .clickable { onEntryClick(entry) }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.text.BasicText(
                                    entry.location,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(primary, 16.sp, FontWeight.Bold, shadow = shadow)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    String.format(Locale.US, "¥%.2f", entry.amount),
                                    style = TextStyle(theme.accent, 17.sp, FontWeight.ExtraBold)
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                androidx.compose.foundation.text.BasicText(
                                    dateFormatter.format(Date(entry.createdAt)),
                                    style = TextStyle(secondary, 12.sp, FontWeight.Medium, shadow = shadow)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    entry.addedKwh?.let { String.format(Locale.US, "+%.2f kWh", it) } ?: "等待充值后查询",
                                    style = TextStyle(secondary, 12.sp, FontWeight.SemiBold, shadow = shadow)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DormRechargeHistoryDetailDialog(
    backdrop: Backdrop,
    entry: DormRechargeHistoryEntry,
    primary: Color,
    secondary: Color,
    shadow: Shadow?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = CampusComposeTheme.colors
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(30.dp) },
                    effects = {
                        vibrancy()
                        colorControls(
                            brightness = if (theme.isDark) 0f else 0.12f,
                            saturation = if (theme.isDark) 0.46f else 0.62f
                        )
                        lens(18.dp.toPx(), 36.dp.toPx())
                    },
                    shadow = null,
                    highlight = { Highlight.Default.copy(alpha = if (theme.isDark) 0.18f else 0.58f) },
                    onDrawSurface = { drawRect(theme.glassStrongSurface) }
                )
                .clip(RoundedCornerShape(30.dp))
                .clickable(interactionSource = null, indication = null, onClick = {})
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.foundation.text.BasicText(
                "充值详情",
                style = TextStyle(primary, 24.sp, FontWeight.ExtraBold, shadow = shadow)
            )
            DormRechargeDetailRow("充值宿舍", entry.location, primary, secondary, shadow)
            DormRechargeDetailRow(
                "充值时间",
                dateFormatter.format(Date(entry.createdAt)),
                primary,
                secondary,
                shadow
            )
            DormRechargeDetailRow(
                "充值金额",
                String.format(Locale.US, "¥%.2f", entry.amount),
                primary,
                secondary,
                shadow
            )
            DormRechargeDetailRow(
                "充值前电量",
                entry.beforeKwh?.let { String.format(Locale.US, "%.2f kWh", it) } ?: "未记录",
                primary,
                secondary,
                shadow
            )
            DormRechargeDetailRow(
                "充值后电量",
                entry.afterKwh?.let { String.format(Locale.US, "%.2f kWh", it) } ?: "等待充值后查询",
                primary,
                secondary,
                shadow
            )
            DormRechargeDetailRow(
                "充值度数",
                entry.addedKwh?.let { String.format(Locale.US, "+%.2f kWh", it) } ?: "等待充值后查询",
                primary,
                secondary,
                shadow
            )
            QuietDialogAction(
                label = "删除记录",
                foreground = theme.error,
                enabled = true,
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                height = 50.dp
            )
        }
    }
}

@Composable
private fun DormRechargeDetailRow(
    label: String,
    value: String,
    primary: Color,
    secondary: Color,
    shadow: Shadow?
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        androidx.compose.foundation.text.BasicText(
            label,
            modifier = Modifier.width(84.dp),
            style = TextStyle(secondary, 13.sp, FontWeight.Medium, shadow = shadow)
        )
        androidx.compose.foundation.text.BasicText(
            value,
            modifier = Modifier.weight(1f),
            style = TextStyle(primary, 15.sp, FontWeight.SemiBold, shadow = shadow)
        )
    }
}

@Composable
private fun DormSpinner(size: Dp = 38.dp, strokeWidth: Dp = 4.dp) {
    val accent = CampusComposeTheme.colors.accent
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dormElectricityLoading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "dormElectricityRotation"
    )
    Canvas(Modifier.size(size)) {
        drawArc(accent.copy(alpha = 0.22f), 0f, 360f, false, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
        drawArc(accent, rotation, 102f, false, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
    }
}

private fun dormTextShadow(palette: ScheduleTextPalette): Shadow? =
    if (palette.adaptive) Shadow(Color(palette.halo), blurRadius = 3f) else null

package com.sdau.campuskit

import android.content.Context
import android.graphics.Rect
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Compose implementation of the schedule header with the legacy dimensions preserved. */
internal class ScheduleHeaderComposeView(
    context: Context,
    initialDate: String,
    initialWeek: String,
    initialPalette: ScheduleTextPalette,
    private val showRefresh: Boolean,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onMore: (Rect) -> Unit
) : FrameLayout(context) {
    private var date by mutableStateOf(initialDate)
    private var week by mutableStateOf(initialWeek)
    private var palette by mutableStateOf(initialPalette)
    private var refreshInProgress by mutableStateOf(false)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(
            composeHostView(context) {
                val primary = Color(palette.primary)
                val haloRadius = with(LocalDensity.current) { 1.05.dp.toPx() }
                val shadow = if (palette.adaptive) {
                    Shadow(Color(palette.halo), Offset.Zero, haloRadius)
                } else {
                    null
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 10.dp, end = 4.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 1.dp)
                    ) {
                        AdaptiveHeaderText(
                            text = date,
                            modifier = Modifier.padding(bottom = 7.dp),
                            maxSizeSp = 26f,
                            minSizeSp = 20f,
                            color = primary,
                            weight = FontWeight.Bold,
                            shadow = shadow
                        )
                        AdaptiveHeaderText(
                            text = week,
                            maxSizeSp = 13f,
                            minSizeSp = 11f,
                            color = primary,
                            weight = if (palette.adaptive) FontWeight.Bold else FontWeight.Normal,
                            shadow = shadow
                        )
                    }
                    HeaderIconButton(
                        iconRes = R.drawable.ic_menu_login,
                        description = "退出登录",
                        widthDp = 48,
                        horizontalPaddingDp = 11,
                        enabled = true,
                        onClick = onLogout
                    )
                    if (showRefresh) {
                        HeaderIconButton(
                            iconRes = R.drawable.ic_menu_refresh,
                            description = "刷新课表",
                            widthDp = 48,
                            horizontalPaddingDp = 11,
                            enabled = !refreshInProgress,
                            alpha = if (refreshInProgress) .5f else 1f,
                            onClick = onRefresh
                        )
                    }
                    var moreBounds by remember { mutableStateOf(Rect()) }
                    HeaderIconButton(
                        iconRes = R.drawable.ic_more,
                        description = "更多操作",
                        widthDp = 56,
                        horizontalPaddingDp = 12,
                        enabled = true,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            moreBounds = Rect(
                                bounds.left.roundToInt(),
                                bounds.top.roundToInt(),
                                bounds.right.roundToInt(),
                                bounds.bottom.roundToInt()
                            )
                        },
                        onClick = { onMore(moreBounds) }
                    )
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
    }

    fun updateWeek(value: String) {
        week = value
    }

    fun updatePalette(value: ScheduleTextPalette) {
        palette = value
    }

    fun setRefreshRunning(value: Boolean) {
        refreshInProgress = value
    }
}

internal class ScheduleVersionComposeView(
    context: Context,
    version: String,
    initialPalette: ScheduleTextPalette
) : FrameLayout(context) {
    private var palette by mutableStateOf(initialPalette)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(
            composeHostView(context) {
                val haloRadius = with(LocalDensity.current) { 1.05.dp.toPx() }
                BasicText(
                    text = version,
                    style = TextStyle(
                        color = Color(palette.secondary),
                        fontSize = 10.sp,
                        fontWeight = if (palette.adaptive) FontWeight.Bold else FontWeight.Normal,
                        shadow = if (palette.adaptive) {
                            Shadow(Color(palette.halo), Offset.Zero, haloRadius)
                        } else {
                            null
                        }
                    ),
                    maxLines = 1
                )
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
    }

    fun updatePalette(value: ScheduleTextPalette) {
        palette = value
    }
}

@androidx.compose.runtime.Composable
private fun AdaptiveHeaderText(
    text: String,
    maxSizeSp: Float,
    minSizeSp: Float,
    color: Color,
    weight: FontWeight,
    shadow: Shadow?,
    modifier: Modifier = Modifier
) {
    var resolvedSize by remember(text, maxSizeSp, minSizeSp) { mutableStateOf(maxSizeSp) }
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = resolvedSize.sp,
            fontWeight = weight,
            shadow = shadow
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && resolvedSize > minSizeSp) {
                resolvedSize = (resolvedSize - 1f).coerceAtLeast(minSizeSp)
            }
        }
    )
}

@androidx.compose.runtime.Composable
private fun HeaderIconButton(
    iconRes: Int,
    description: String,
    widthDp: Int,
    horizontalPaddingDp: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    onClick: () -> Unit
) {
    val accentColor = CampusComposeTheme.colors.accent
    Box(
        modifier = modifier
            .size(widthDp.dp, 44.dp)
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier
                .size(widthDp.dp, 44.dp)
                .padding(horizontal = horizontalPaddingDp.dp, vertical = 10.dp),
            colorFilter = ColorFilter.tint(accentColor),
            alpha = alpha
        )
    }
}

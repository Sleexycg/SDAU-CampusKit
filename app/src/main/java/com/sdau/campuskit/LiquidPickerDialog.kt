package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
/** Shared liquid picker dialogs for sharing and empty-room filters. */
internal data class LiquidPickerOption(
    val title: String,
    val subtitle: String = "",
    val iconRes: Int = 0,
    val selected: Boolean = false,
    val onClick: () -> Unit
)

internal class LiquidPickerDialogView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    title: String,
    options: List<LiquidPickerOption>,
    highFrost: Boolean = false,
    onDismiss: () -> Unit
) : FrameLayout(context) {
    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            composeHostView(context) {
                LiquidPickerDialog(
                    pageSnapshot = pageSnapshot,
                    title = title,
                    options = options,
                    highFrost = highFrost,
                    onDismiss = onDismiss
                )
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun releaseSnapshot() {
        releaseDialogSnapshot(pageSnapshot) { pageSnapshot = null }
    }
}

/** 成绩详情 View 页面与液态玻璃 Dialog 之间的桥接层。 */

@Composable
private fun LiquidPickerDialog(
    pageSnapshot: Bitmap?,
    title: String,
    options: List<LiquidPickerOption>,
    highFrost: Boolean,
    onDismiss: () -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val contentColor = themeColors.primaryText
    val accentColor = themeColors.accent
    val containerColor = if (highFrost) {
        themeColors.glassStrongSurface
    } else {
        themeColors.glassSurface
    }
    val dimColor = themeColors.dialogScrim
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()
    val panelCorner = if (highFrost) 28.dp else 48.dp

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
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onDismiss
                )
        )
        Column(
            Modifier
                .padding(horizontal = if (highFrost) 46.dp else 40.dp)
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(panelCorner) },
                    effects = {
                        if (themeColors.isDark) {
                            colorControls(brightness = 0f, saturation = 1.08f)
                            blur(8.dp.toPx())
                            lens(10.dp.toPx(), 20.dp.toPx(), depthEffect = true)
                        } else if (highFrost) {
                            colorControls(brightness = 0.16f, saturation = 0.62f)
                            blur(20.dp.toPx())
                            lens(10.dp.toPx(), 20.dp.toPx(), depthEffect = true)
                        } else {
                            colorControls(brightness = 0.08f, saturation = 1.35f)
                            blur(12.dp.toPx())
                            lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                        }
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
                title,
                modifier = Modifier.padding(
                    start = if (highFrost) 22.dp else 28.dp,
                    top = if (highFrost) 20.dp else 24.dp,
                    end = if (highFrost) 22.dp else 28.dp,
                    bottom = if (highFrost) 10.dp else 16.dp
                ),
                style = TextStyle(
                    contentColor,
                    if (highFrost) 21.sp else 24.sp,
                    if (highFrost) FontWeight.SemiBold else FontWeight.Medium
                )
            )
            Column(
                Modifier
                    .padding(
                        start = if (highFrost) 12.dp else 20.dp,
                        end = if (highFrost) 12.dp else 20.dp,
                        bottom = if (highFrost) 14.dp else 24.dp
                    )
                    .heightIn(max = if (highFrost) 356.dp else 330.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (highFrost) 2.dp else 8.dp)
            ) {
                options.forEach { option ->
                    val optionForeground = contentColor
                    if (highFrost) {
                        val itemShape = RoundedCornerShape(14.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(itemShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(
                                                alpha = if (option.selected) {
                                                    if (themeColors.isDark) 0.12f else 0.52f
                                                } else 0f
                                            ),
                                            accentColor.copy(
                                                alpha = if (option.selected) {
                                                    if (themeColors.isDark) 0.10f else 0.30f
                                                } else 0f
                                            )
                                        )
                                    ),
                                    shape = itemShape
                                )
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = option.onClick
                                )
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(
                                option.title,
                                modifier = Modifier.weight(1f),
                                style = TextStyle(
                                    color = if (option.selected) accentColor else contentColor,
                                    fontSize = 14.sp,
                                    fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            )
                            if (option.selected) {
                                Image(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp),
                                    colorFilter = ColorFilter.tint(accentColor)
                                )
                            }
                        }
                    } else {
                        CampusLiquidButton(
                            onClick = option.onClick,
                            backdrop = backdrop,
                            style = LiquidButtonStyle.FROSTED,
                            enabled = true,
                            allowDragDeformation = false,
                            deformationHorizontalPadding = 4.dp,
                            deformationVerticalPadding = 4.dp,
                            modifier = Modifier.fillMaxWidth(),
                            height = 68.dp
                        ) {
                            if (option.iconRes != 0) {
                                Image(
                                    painter = painterResource(option.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    colorFilter = ColorFilter.tint(accentColor)
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(width = 5.dp, height = 28.dp)
                                        .clip(Capsule())
                                        .background(accentColor)
                                )
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 13.dp)
                            ) {
                                BasicText(
                                    option.title,
                                    style = TextStyle(optionForeground, 16.sp, FontWeight.SemiBold)
                                )
                                if (option.subtitle.isNotBlank()) {
                                    BasicText(
                                        option.subtitle,
                                        modifier = Modifier.padding(top = 3.dp),
                                        style = TextStyle(optionForeground.copy(alpha = 0.68f), 12.sp)
                                    )
                                }
                            }
                            if (option.selected) {
                                BasicText("✓", style = TextStyle(accentColor, 18.sp, FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}

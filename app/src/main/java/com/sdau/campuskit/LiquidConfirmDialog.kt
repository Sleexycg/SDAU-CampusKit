package com.sdau.campuskit

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
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
import com.kyant.shapes.RoundedRectangle

/** Reusable two-action confirmation dialog with a sampled liquid-glass backdrop. */
internal class LiquidConfirmDialogView(
    context: Context,
    private var pageSnapshot: Bitmap?,
    title: String,
    message: String,
    cancelLabel: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) : FrameLayout(context) {
    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        addView(
            composeHostView(context) {
                LiquidConfirmDialog(
                    pageSnapshot = pageSnapshot,
                    title = title,
                    message = message,
                    cancelLabel = cancelLabel,
                    confirmLabel = confirmLabel,
                    onDismiss = onDismiss,
                    onConfirm = onConfirm
                )
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
private fun LiquidConfirmDialog(
    pageSnapshot: Bitmap?,
    title: String,
    message: String,
    cancelLabel: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val themeColors = CampusComposeTheme.colors
    val contentColor = themeColors.primaryText
    val secondaryColor = contentColor.copy(alpha = 0.70f)
    val snapshotImage = remember(pageSnapshot) { pageSnapshot?.asImageBitmap() }
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
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
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onDismiss
                )
        )

        Column(
            Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(48.dp) },
                    effects = {
                        colorControls(
                            brightness = if (themeColors.isDark) 0f else 0.08f,
                            saturation = if (themeColors.isDark) 0.54f else 1.35f
                        )
                        blur((if (themeColors.isDark) 8.dp else 12.dp).toPx())
                        lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                    },
                    highlight = {
                        Highlight.Plain.copy(alpha = if (themeColors.isDark) 0.12f else 1f)
                    },
                    onDrawSurface = {
                        drawRect(
                            if (themeColors.isDark) {
                                Color(0xFF2B2B2E).copy(alpha = 0.58f)
                            } else {
                                Color(0xFFFAFAFA).copy(alpha = 0.55f)
                            }
                        )
                    }
                )
                .clickable(interactionSource = null, indication = null, onClick = {})
        ) {
            BasicText(
                title,
                modifier = Modifier.padding(start = 28.dp, top = 24.dp, end = 28.dp),
                style = TextStyle(contentColor, 24.sp, FontWeight.Medium)
            )
            BasicText(
                message,
                modifier = Modifier.padding(start = 28.dp, top = 14.dp, end = 28.dp),
                style = TextStyle(secondaryColor, 15.sp, lineHeight = 23.sp)
            )
            Row(
                Modifier
                    .padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuietDialogAction(
                    label = cancelLabel,
                    foreground = contentColor,
                    enabled = true,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    height = 52.dp
                )
                CampusLiquidButton(
                    onClick = onConfirm,
                    backdrop = backdrop,
                    style = LiquidButtonStyle.TINTED,
                    enabled = true,
                    allowDragDeformation = false,
                    deformationHorizontalPadding = 4.dp,
                    deformationVerticalPadding = 4.dp,
                    modifier = Modifier.weight(1f),
                    height = 52.dp
                ) {
                    BasicText(confirmLabel, style = TextStyle(Color.White, 16.sp, FontWeight.Medium))
                }
            }
        }
    }
}

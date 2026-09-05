package com.sdau.campuskit

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared loading indicator; callers retain their existing size and animation labels. */
@Composable
internal fun CampusLoadingSpinner(
    label: String,
    rotationLabel: String,
    size: Dp = 38.dp,
    strokeWidth: Dp = 4.dp
) {
    val accent = CampusComposeTheme.colors.accent
    val transition = rememberInfiniteTransition(label = label)
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = rotationLabel
    )
    Canvas(Modifier.size(size)) {
        drawArc(accent.copy(alpha = 0.22f), 0f, 360f, false, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
        drawArc(accent, rotation, 102f, false, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
    }
}

/** Only for owned dialog snapshots: clear the owner's reference before recycling. */
internal inline fun releaseDialogSnapshot(snapshot: Bitmap?, clearReference: () -> Unit) {
    clearReference()
    if (snapshot != null && !snapshot.isRecycled) snapshot.recycle()
}

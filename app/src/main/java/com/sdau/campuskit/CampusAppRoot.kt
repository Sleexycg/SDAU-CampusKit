package com.sdau.campuskit

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose root for the activity while the remaining legacy screens are migrated.
 * Keeping one stable AndroidView boundary preserves the existing coordinate system
 * used by wallpaper sampling, PixelCopy and liquid-glass overlays.
 */
@Composable
internal fun CampusAppRoot(pageHost: View) {
    AndroidView(
        factory = { pageHost },
        modifier = Modifier.fillMaxSize()
    )
}

/** Temporary screen-level bridge used while a legacy screen is replaced section by section. */
@Composable
internal fun LegacyScreenHost(factory: () -> View) {
    AndroidView(
        factory = { factory() },
        modifier = Modifier.fillMaxSize()
    )
}

package com.lumen.tv.ui.theme

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp

/**
 * BlurCompat: Applies hardware RenderEffect blur on Android 12+ (API 31+).
 * Gracefully degrades to crisp scrim on API 29 and 30 without throwing exceptions.
 */
fun Modifier.blurCompat(
    radius: Dp,
    edgeTreatment: BlurredEdgeTreatment = BlurredEdgeTreatment.Rectangle
): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > Dp.Hairline) {
        this.blur(radius, edgeTreatment)
    } else {
        this
    }
}

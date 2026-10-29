package com.lumen.tv.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.tv.ui.theme.LocalReduceMotion
import com.lumen.tv.ui.theme.Tokens
import kotlin.math.abs

enum class NavDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Spatial navigation scoring engine matching spec formula:
 * score = primaryAxisDistance + 2.2 * |crossAxisOffset|
 */
object SpatialNavigationScorer {
    fun calculateScore(from: Rect, to: Rect, direction: NavDirection): Float? {
        val primaryDist: Float
        val crossDist: Float

        when (direction) {
            NavDirection.RIGHT -> {
                primaryDist = to.left - from.right
                crossDist = abs(to.center.y - from.center.y)
                if (primaryDist < 10f) return null // Must be >= 10dp along axis
            }
            NavDirection.LEFT -> {
                primaryDist = from.left - to.right
                crossDist = abs(to.center.y - from.center.y)
                if (primaryDist < 10f) return null
            }
            NavDirection.DOWN -> {
                primaryDist = to.top - from.bottom
                crossDist = abs(to.center.x - from.center.x)
                if (primaryDist < 10f) return null
            }
            NavDirection.UP -> {
                primaryDist = from.top - to.bottom
                crossDist = abs(to.center.x - from.center.x)
                if (primaryDist < 10f) return null
            }
        }

        return primaryDist + (2.2f * crossDist)
    }
}

/**
 * Sibling shelf focus state holder to enable tvOS-style shelf dimming
 * (active card scale 1.05, sibling cards dim to 80% brightness).
 */
val LocalShelfFocusActive = compositionLocalOf { false }

/**
 * Focusable element modifier with visual elevation, scale, border, and ambient shadow.
 */
fun Modifier.tvFocusable(
    focusRequester: FocusRequester? = null,
    targetScale: Float = 1.05f,
    shape: Shape = Tokens.ShapeCard,
    borderWidth: Dp = 1.dp,
    borderColor: Color = Tokens.CardBorder,
    focusedBorderColor: Color = Tokens.CardBorderFocused,
    hasShadow: Boolean = false,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current

    val scale by animateFloatAsState(
        targetValue = if (isFocused && !reduceMotion) targetScale else 1.0f,
        animationSpec = Tokens.MotionSpec,
        label = "tvScale"
    )

    val currentBorder = if (isFocused) focusedBorderColor else borderColor
    val currentBorderWidth = if (isFocused) 1.5.dp else borderWidth

    var mod = this
    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    if (hasShadow && isFocused && !reduceMotion) {
        mod = mod.drawBehind {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = Color.Black.copy(alpha = 0.55f)
                    asFrameworkPaint().apply {
                        maskFilter = android.graphics.BlurMaskFilter(
                            with(density) { 24.dp.toPx() },
                            android.graphics.BlurMaskFilter.Blur.NORMAL
                        )
                    }
                }
                canvas.drawRoundRect(
                    left = -4f,
                    top = with(density) { 6.dp.toPx() },
                    right = size.width + 4f,
                    bottom = size.height + with(density) { 14.dp.toPx() },
                    radiusX = with(density) { Tokens.RadiusCard.toPx() },
                    radiusY = with(density) { Tokens.RadiusCard.toPx() },
                    paint = paint
                )
            }
        }
    }

    mod
        .scale(scale)
        .onFocusChanged { isFocused = it.isFocused }
        .border(currentBorderWidth, currentBorder, shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
            } else Modifier
        )
        .focusable()
}

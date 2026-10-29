package com.lumen.tv.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.tv.data.ContinueItem
import com.lumen.tv.focus.LocalShelfFocusActive
import com.lumen.tv.ui.theme.LocalReduceMotion
import com.lumen.tv.ui.theme.Tokens
import com.lumen.tv.ui.theme.blurCompat

/**
 * Continue Watching Card:
 * Features tvOS-style shelf focus (focused card elevates and scales to 1.05;
 * siblings dim to 80% brightness).
 * Bottom progress bar: 3dp track white @14%, fill white @85% rounded 2dp tip.
 */
@Composable
fun ContinueCard(
    item: ContinueItem,
    width: Dp,
    height: Dp,
    onFocusStateChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shelfHasFocus = LocalShelfFocusActive.current
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current

    val scale by animateFloatAsState(
        targetValue = if (isFocused && !reduceMotion) 1.05f else 1.0f,
        animationSpec = Tokens.MotionSpec,
        label = "continueCardScale"
    )

    val translateY by animateFloatAsState(
        targetValue = if (isFocused && !reduceMotion) -3f else 0f,
        animationSpec = Tokens.MotionSpec,
        label = "continueCardTranslateY"
    )

    // Sibling cards dim to 80% brightness when any card in the shelf is focused
    val shelfAlpha by animateFloatAsState(
        targetValue = if (shelfHasFocus && !isFocused) 0.80f else 1.0f,
        animationSpec = Tokens.MotionSpec,
        label = "continueShelfAlpha"
    )

    val fillColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.GlassFillFocused else Tokens.GlassFill,
        label = "continueFill"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.CardBorderFocused else Tokens.CardBorder,
        label = "continueBorder"
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .offset(y = translateY.dp)
            .scale(scale)
            .alpha(shelfAlpha)
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusStateChange(it.isFocused)
            }
            .then(
                if (isFocused && !reduceMotion) {
                    Modifier.drawBehind {
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
                                left = -2f,
                                top = with(density) { 4.dp.toPx() },
                                right = size.width + 2f,
                                bottom = size.height + with(density) { 14.dp.toPx() },
                                radiusX = with(density) { Tokens.RadiusCard.toPx() },
                                radiusY = with(density) { Tokens.RadiusCard.toPx() },
                                paint = paint
                            )
                        }
                    }
                } else Modifier
            )
            .clip(Tokens.ShapeCard)
            .blurCompat(Tokens.GlassBlurDp)
            .background(fillColor)
            .border(1.dp, borderColor, Tokens.ShapeCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .focusable()
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        // Card Title (Bottom-Left 16/15dp inset)
        Text(
            text = item.title,
            color = Tokens.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 12.dp)
        )

        // Bottom Progress Indicator (3dp track white @14%, fill white @85%)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.14f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(item.progressPercent / 100f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }
    }
}


package com.lumen.tv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.lumen.tv.ui.theme.Tokens

/**
 * Settings Switch Row:
 * Custom tvOS Switch: 46×27dp track, off rgba(120,120,128,.32), ON #30D158, knob 22dp white, travel 19dp.
 * Accessible with Role.Switch.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = Tokens.ChipGray,
    valueText: String? = null,
    showDivider: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.CardFillFocused else Color.Transparent,
        label = "switchRowBg"
    )

    val labelColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.TextPrimary else Tokens.TextSecondary,
        label = "switchRowLabel"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable(interactionSource = remember { MutableInteractionSource() })
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon chip
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint)
                        .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Label
            Text(
                text = label,
                style = Tokens.RowLabelText,
                color = labelColor,
                modifier = Modifier.weight(1f)
            )

            // Optional status / value text before the switch
            if (!valueText.isNullOrBlank()) {
                Text(
                    text = valueText,
                    style = Tokens.ValueText,
                    color = Tokens.TextTertiary,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }

            // Custom Switch: 46×27dp track, knob 22dp, travel 19dp
            CustomTvSwitch(checked = checked)
        }

        // Inset Separator
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                thickness = 0.5.dp,
                color = Tokens.CardBorderHairline
            )
        }
    }
}

@Composable
private fun CustomTvSwitch(
    checked: Boolean
) {
    val trackBgColor by animateColorAsState(
        targetValue = if (checked) Tokens.SwitchOn else Tokens.SwitchOff,
        animationSpec = Tokens.MotionSpec,
        label = "switchTrackColor"
    )

    val knobOffset by animateDpAsState(
        targetValue = if (checked) 21.dp else 2.5.dp,
        animationSpec = Tokens.MotionSpec,
        label = "switchKnobOffset"
    )

    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 27.dp)
            .clip(CircleShape)
            .background(trackBgColor),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(22.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Tokens.SwitchKnob)
        )
    }
}

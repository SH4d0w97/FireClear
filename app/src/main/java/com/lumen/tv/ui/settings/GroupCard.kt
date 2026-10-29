package com.lumen.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lumen.tv.ui.theme.Tokens
import com.lumen.tv.ui.theme.blurCompat

/**
 * Settings Group Card:
 * Radius 18dp, fill white @5%, hairline border 1dp white @10%, blur 20dp, clipped.
 * Optional section header label (12sp/500 uppercase 12% tracking) and footnote (12sp tertiary).
 */
@Composable
fun GroupCard(
    title: String? = null,
    footnote: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = Tokens.SectionLabelText,
                color = Tokens.TextTertiary,
                modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Tokens.RadiusGroup))
                .blurCompat(Tokens.BlurGlass)
                .background(Tokens.CardFillGlass)
                .border(1.dp, Tokens.CardBorderHairline, RoundedCornerShape(Tokens.RadiusGroup)),
            content = content
        )

        if (!footnote.isNullOrBlank()) {
            Text(
                text = footnote,
                style = Tokens.FootnoteText,
                color = Tokens.TextTertiary,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp, end = 16.dp)
            )
        }
    }
}

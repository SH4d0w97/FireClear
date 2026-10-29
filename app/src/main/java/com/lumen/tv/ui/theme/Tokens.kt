package com.lumen.tv.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalReduceMotion = compositionLocalOf { false }
val LocalIsTvMode = compositionLocalOf { true }

object Tokens {
    // ───────────────────────── Base & Glass Colors ─────────────────────────
    val BaseBg = Color(0xFF050506)
    val BaseDark = BaseBg

    // Text hierarchy
    val TextPrimary = Color(0xEBFFFFFF)    // #FFF @ 92%
    val TextSecondary = Color(0x8CFFFFFF)  // #FFF @ 55%
    val TextTertiary = Color(0x57FFFFFF)   // #FFF @ 34%
    val Hairline = Color(0x1AFFFFFF)       // White @ 10%

    // Glass surfaces
    val GlassFill = Color(0x10FFFFFF)      // White @ 6%
    val GlassFillFocused = Color(0x17FFFFFF) // White @ 9%
    val GlassFillSettingsRow = Color(0x14FFFFFF) // White @ 8%
    val CardFillGlass = GlassFill
    val CardFillFocused = GlassFillFocused

    val CardBorder = Color(0x1AFFFFFF)     // 1dp white @ 10%
    val CardBorderHairline = CardBorder
    val CardBorderFocused = Color(0x8CFFFFFF) // White @ 55%
    val TileBorderFocused = Color(0x57FFFFFF) // White @ 34%
    val ChromeScrimColor = Color(0xFF050506)

    // Blur metrics
    val GlassBlurDp = 20.dp
    val BlurGlass = GlassBlurDp
    val ChromeScrimBlurDp = 14.dp
    val BlurChrome = ChromeScrimBlurDp
    val ScrimHeight = 84.dp

    // ───────────────────────── Geometry & Radii ─────────────────────────
    val RadiusCard = 16.dp
    val RadiusGroup = 18.dp
    val RadiusIconSquircle = 24.dp
    val RadiusSquircle = RadiusIconSquircle
    val IconSizeSquircle = 92.dp
    val RadiusSearch = 12.dp
    val ShapeCard = RoundedCornerShape(RadiusCard)
    val ShapeGroup = RoundedCornerShape(RadiusGroup)
    val ShapeIcon = RoundedCornerShape(RadiusIconSquircle)
    val ShapeSearch = RoundedCornerShape(RadiusSearch)

    // Overscan safe insets
    val OverscanHorizontal = 48.dp
    val OverscanVertical = 28.dp

    // ───────────────────────── Switch Specs ─────────────────────────
    val SwitchTrackWidth = 46.dp
    val SwitchTrackHeight = 27.dp
    val SwitchKnobSize = 22.dp
    val SwitchTravel = 19.dp
    val SwitchOffTrack = Color(0x52787880) // rgba(120,120,128,.32)
    val SwitchOff = SwitchOffTrack
    val SwitchOnTrack = Color(0xFF30D158)
    val SwitchOn = SwitchOnTrack
    val SwitchKnob = Color.White

    // ───────────────────────── Settings Chip Palette ─────────────────────────
    val ChipSize = 28.dp
    val ChipRadius = 8.dp
    val ChipGlyphSize = 15.dp
    val ChipRingColor = Color(0x40FFFFFF) // 0.5dp inset ring @ 25%

    val ChipOrange = Color(0xFFFF9F0A)
    val ChipBlue = Color(0xFF0A84FF)
    val ChipIndigo = Color(0xFF5E5CE6)
    val ChipCyan = Color(0xFF32ADE6)
    val ChipSlate = Color(0xFF636366)
    val ChipGray = Color(0xFF8E8E93)
    val ChipTeal = Color(0xFF64D2FF)
    val ChipPink = Color(0xFFFF375F)
    val ChipPurple = Color(0xFFBF5AF2)
    val ChipGreen = Color(0xFF30D158)

    object Chips {
        val Orange = ChipOrange
        val Blue = ChipBlue
        val Indigo = ChipIndigo
        val Cyan = ChipCyan
        val Slate = ChipSlate
        val Gray = ChipGray
        val Teal = ChipTeal
        val Pink = ChipPink
        val Purple = ChipPurple
        val Green = ChipGreen
    }

    // ───────────────────────── Motion Specifications ─────────────────────────
    val StandardMotionDuration = 220
    val MotionEasing = CubicBezierEasing(0.25f, 1f, 0.3f, 1f)
    val MotionSpec: AnimationSpec<Float> = tween(durationMillis = StandardMotionDuration, easing = MotionEasing)
    val MotionColorSpec: AnimationSpec<Color> = tween(durationMillis = StandardMotionDuration, easing = MotionEasing)
    val MotionDpSpec: AnimationSpec<Dp> = tween(durationMillis = StandardMotionDuration, easing = MotionEasing)
    val FocusSpringSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    const val PageCrossfadeDuration = 300
    const val DurationPageCrossfade = PageCrossfadeDuration
    const val WallpaperCrossfadeDuration = 500

    // ───────────────────────── Typography Styles ─────────────────────────
    val FontSans = FontFamily.SansSerif

    val HeroTitle = TextStyle(
        fontFamily = FontSans,
        fontSize = 48.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.03).sp,
        color = TextPrimary
    )

    val PageTitleStyle = TextStyle(
        fontFamily = FontSans,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    val PageTitle = PageTitleStyle

    val KickerStyle = TextStyle(
        fontFamily = FontSans,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = TextTertiary
    )
    val KickerText = KickerStyle

    val SectionLabelStyle = TextStyle(
        fontFamily = FontSans,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = TextTertiary,
        letterSpacing = 0.12.sp
    )
    val SectionLabelText = SectionLabelStyle

    val RowLabelStyle = TextStyle(
        fontFamily = FontSans,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = TextPrimary
    )
    val RowLabelText = RowLabelStyle

    val ValueStyle = TextStyle(
        fontFamily = FontSans,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = TextTertiary
    )
    val ValueText = ValueStyle

    val FootnoteStyle = TextStyle(
        fontFamily = FontSans,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = TextTertiary
    )
    val FootnoteText = FootnoteStyle

    val TileNameStyle = TextStyle(
        fontFamily = FontSans,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        color = TextTertiary
    )
    val TileNameText = TileNameStyle
}

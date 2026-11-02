package com.srik.shortstracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.srik.shortstracker.R
import com.srik.shortstracker.tracker.ShortsApp

/*
 * Same design system as the promo page (docs/promo.html): a cool lavender-grey ground, ink-black type,
 * one violet accent, scoreboard numerals in Big Shoulders, Figtree for reading, JetBrains Mono for labels.
 */

val DisplayFont = FontFamily(
    Font(R.font.big_shoulders_extrabold, FontWeight.ExtraBold),
    Font(R.font.big_shoulders_black, FontWeight.Black),
)
val BodyFont = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)
val MonoFont = FontFamily(Font(R.font.jetbrains_mono_medium, FontWeight.Medium))

val YouTubeRed = Color(0xFFFF4E45)
val InstagramPink = Color(0xFFD62976)
val SnapchatYellow = Color(0xFFF5C400)

val ShortsApp.color: Color
    get() = when (this) {
        ShortsApp.YOUTUBE -> YouTubeRed
        ShortsApp.INSTAGRAM -> InstagramPink
        ShortsApp.SNAPCHAT -> SnapchatYellow
    }

val ShortsApp.shortName: String
    get() = when (this) {
        ShortsApp.YOUTUBE -> "YouTube"
        ShortsApp.INSTAGRAM -> "Instagram"
        ShortsApp.SNAPCHAT -> "Snapchat"
    }

/** Tokens Material's scheme has no slot for. */
@Immutable
data class ExtraColors(
    val line: Color,
    val good: Color,
    val danger: Color,
    /** Over your limit: a heads-up, not an alarm, so amber rather than red. */
    val caution: Color,
    val tally: Color,
)

private val LightExtras = ExtraColors(line = Color(0xFFE2E0EA), good = Color(0xFF1F9D55), danger = Color(0xFFD93A32), caution = Color(0xFFC27A00), tally = Color(0xFF17151F))
private val DarkExtras = ExtraColors(line = Color(0xFF2A2934), good = Color(0xFF3CC47C), danger = Color(0xFFFF6B63), caution = Color(0xFFFFB547), tally = Color(0xFFF3F1FA))

private val LocalExtraColors = staticCompositionLocalOf { LightExtras }

val extraColors: ExtraColors
    @Composable @ReadOnlyComposable get() = LocalExtraColors.current

private val Light = lightColorScheme(
    primary = Color(0xFF5B4BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = Color(0xFF5B4BD6),
    secondaryContainer = Color(0xFFE6E0FF),
    onSecondaryContainer = Color(0xFF17151F),
    background = Color(0xFFF3F2F7),
    onBackground = Color(0xFF17151F),
    surface = Color(0xFFF3F2F7),
    onSurface = Color(0xFF17151F),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFECEAF3),
    surfaceContainerHighest = Color(0xFFE2E0EA),
    onSurfaceVariant = Color(0xFF5F5D6B),
    outline = Color(0xFFB9B6C6),
    outlineVariant = Color(0xFFE2E0EA),
    inverseSurface = Color(0xFF17151F),
    inverseOnSurface = Color(0xFFF3F2F7),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB4A7FF),
    onPrimary = Color(0xFF1C1340),
    primaryContainer = Color(0xFF2E2560),
    onPrimaryContainer = Color(0xFFB4A7FF),
    secondaryContainer = Color(0xFF2E2560),
    onSecondaryContainer = Color(0xFFECEAF2),
    background = Color(0xFF0B0A10),
    onBackground = Color(0xFFECEAF2),
    surface = Color(0xFF0B0A10),
    onSurface = Color(0xFFECEAF2),
    surfaceContainer = Color(0xFF16151D),
    surfaceContainerHigh = Color(0xFF201F29),
    surfaceContainerHighest = Color(0xFF2A2934),
    onSurfaceVariant = Color(0xFFA19FAE),
    outline = Color(0xFF4A4857),
    outlineVariant = Color(0xFF2A2934),
    inverseSurface = Color(0xFFECEAF2),
    inverseOnSurface = Color(0xFF0B0A10),
)

private val Tabular = "tnum"

private val AppTypography = Typography(
    // The big tally on Today.
    displayLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Black, fontSize = 112.sp, lineHeight = 96.sp, letterSpacing = (-0.01).em, fontFeatureSettings = Tabular),
    displayMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 52.sp, fontFeatureSettings = Tabular),
    displaySmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 38.sp, fontFeatureSettings = Tabular),
    // Screen and section titles, always set in capitals.
    headlineLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 30.sp, fontFeatureSettings = Tabular),
    headlineSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 24.sp, fontFeatureSettings = Tabular),
    titleLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    // Eyebrows and small data, like the promo page's mono labels.
    labelMedium = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.08.em, fontFeatureSettings = Tabular),
    labelSmall = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.02.em, fontFeatureSettings = Tabular),
)

@Composable
fun ShortsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
    }
}

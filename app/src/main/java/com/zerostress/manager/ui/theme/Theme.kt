package com.zerostress.manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zerostress.manager.R

// ---- Brand palette: NEXUS UI (futuristic esports skin over Neon Glass) ----
// Deep space background + holographic glass surfaces + cyber cyan / violet
// lighting. Slot names are unchanged; every screen inherits the new values.
val ZsBgStart = Color(0xFF050814)
val ZsBgMid = Color(0xFF04060F)
val ZsBgEnd = Color(0xFF03050B)
val ZsCard = Color(0x10FFFFFF)         // glass fill (white @ ~6%)
val ZsCardAlt = Color(0x16FFFFFF)      // brighter glass (white @ ~9%)

val ZsPrimary = Color(0xFF20E7FF)      // cyber cyan
val ZsPrimaryDark = Color(0xFF0E7490)  // deep cyan (pressed / gradients)
val ZsPurple = Color(0xFF8B5CFF)       // electric violet (secondary glow)

val ZsAccent = Color(0xFF20E7FF)       // hero accent (cyan)
val ZsAccentDark = Color(0xFF0E7490)

val ZsTextPrimary = Color(0xFFF3F7FF)
val ZsTextSecondary = Color(0xFF9BA7C7)
val ZsTextMuted = Color(0xFF59647F)
val ZsBorder = Color(0xFF333A46)
val ZsBorderLight = Color(0xFF4A5160)

val ZsDanger = Color(0xFFFF416C)
val ZsSuccess = Color(0xFF31F7A5)
val ZsGreen = Color(0xFF31F7A5)
val ZsCyan = Color(0xFF20E7FF)         // kept as the shared "cyan" slot
val ZsGold = Color(0xFFFFC857)         // rank gold
val ZsSilver = Color(0xFFC0C7D1)
val ZsBronze = Color(0xFFCD7F32)
val ZsWarning = Color(0xFFFFB020)
val ZsInfo = Color(0xFF6EC1FF)
val ZsGrey = Color(0xFF8B949E)

val ZsChatSentStart = Color(0xFF20E7FF)
val ZsChatSentEnd = Color(0xFF8B5CFF)
val ZsChatReceived = Color(0x14FFFFFF)

private val ZsColorScheme = darkColorScheme(
    primary = ZsPrimary,
    onPrimary = Color(0xFF04101A),      // dark ink on neon fills
    primaryContainer = ZsCard,
    onPrimaryContainer = ZsTextPrimary,
    secondary = ZsPurple,
    onSecondary = Color(0xFF04101A),
    secondaryContainer = ZsCardAlt,
    onSecondaryContainer = ZsTextPrimary,
    tertiary = ZsAccent,
    onTertiary = Color(0xFF04101A),
    background = ZsBgStart,
    onBackground = ZsTextPrimary,
    surface = ZsBgMid,
    onSurface = ZsTextPrimary,
    surfaceVariant = ZsCardAlt,
    onSurfaceVariant = ZsTextSecondary,
    error = ZsDanger,
    onError = Color.White,
    outline = ZsBorder
)

/**
 * Esports display family (Rajdhani): squared, techy numerals and headings.
 * Weights map onto the shipped files; the Bold file also serves ExtraBold /
 * Black so headings stay crisp without extra font assets.
 */
private val ZsRajdhani = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Normal),
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
    Font(R.font.rajdhani_bold, FontWeight.ExtraBold),
    Font(R.font.rajdhani_bold, FontWeight.Black)
)

private val ZsTypography = Typography(
    // Esports HUD type: ExtraBold upright headings with wide tracking,
    // per the Neon Glass v4 spec (crisp, high contrast, no italic).
    headlineLarge = TextStyle(
        fontFamily = ZsRajdhani,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ZsRajdhani,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ZsRajdhani,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        letterSpacing = 0.4.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ZsRajdhani,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ZsRajdhani,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ZsRajdhani,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ZsRajdhani,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 0.8.sp
    )
)

@Composable
fun ZeroStressTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZsColorScheme,
        typography = ZsTypography,
        content = content
    )
}
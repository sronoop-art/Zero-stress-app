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

// ---- Brand palette: NEON GLASS v4 (ui-redesign-preview.html) ----
// Deep space navy + glassmorphism surfaces + neon cyan / violet lighting.
val ZsBgStart = Color(0xFF0E1122)
val ZsBgMid = Color(0xFF0B0D1A)
val ZsBgEnd = Color(0xFF070810)
val ZsCard = Color(0x0EFFFFFF)         // glass fill (white @ ~5.5%)
val ZsCardAlt = Color(0x14FFFFFF)      // brighter glass (white @ ~8%)

val ZsPrimary = Color(0xFF22D3EE)      // neon cyan
val ZsPrimaryDark = Color(0xFF0E7490)  // deep cyan (pressed / gradients)
val ZsPurple = Color(0xFFA855F7)       // neon violet (secondary glow)

val ZsAccent = Color(0xFF22D3EE)       // hero accent (cyan)
val ZsAccentDark = Color(0xFF0E7490)

val ZsTextPrimary = Color(0xFFE8EAF0)
val ZsTextSecondary = Color(0xFF9AA3B2)
val ZsTextMuted = Color(0xFF6B7280)
val ZsBorder = Color(0xFF333A46)
val ZsBorderLight = Color(0xFF4A5160)

val ZsDanger = Color(0xFFFB7185)
val ZsSuccess = Color(0xFF34D399)
val ZsGreen = Color(0xFF30D158)
val ZsCyan = Color(0xFF22D3EE)         // kept as the shared "cyan" slot (neon cyan)
val ZsGold = Color(0xFFFFD60A)         // rank gold
val ZsSilver = Color(0xFFC0C7D1)
val ZsBronze = Color(0xFFCD7F32)
val ZsWarning = Color(0xFFFFB020)
val ZsInfo = Color(0xFF6EC1FF)
val ZsGrey = Color(0xFF8B949E)

val ZsChatSentStart = Color(0xFF22D3EE)
val ZsChatSentEnd = Color(0xFFA855F7)
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
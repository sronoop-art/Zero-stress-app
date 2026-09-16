package com.zerostress.manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- Brand palette: CARBON GT RACING (theme #11) ----
// Carbon-fiber charcoal + racing red + silver-white, italic motorsport type.
val ZsBgStart = Color(0xFF1A1D24)
val ZsBgMid = Color(0xFF12141A)
val ZsBgEnd = Color(0xFF0C0E12)
val ZsCard = Color(0xFF14161C)
val ZsCardAlt = Color(0xFF1A1D24)

val ZsPrimary = Color(0xFFFF1E3C)      // racing red
val ZsPrimaryDark = Color(0xFFD91633)
val ZsPurple = Color(0xFF2A2E38)       // carbon slate (legacy slot)

val ZsAccent = Color(0xFFFF1E3C)       // hero red accent
val ZsAccentDark = Color(0xFFD91633)

val ZsTextPrimary = Color(0xFFE8EAF0)
val ZsTextSecondary = Color(0xFF9AA3B2)
val ZsTextMuted = Color(0xFF6B7280)
val ZsBorder = Color(0xFF333A46)
val ZsBorderLight = Color(0xFF4A5160)

val ZsDanger = Color(0xFFFF1E3C)
val ZsSuccess = Color(0xFF30D158)
val ZsGreen = Color(0xFF30D158)
val ZsCyan = Color(0xFFD7DCE6)         // silver-white secondary accent (legacy slot)
val ZsGold = Color(0xFFFFD60A)         // rank gold
val ZsSilver = Color(0xFFC0C7D1)
val ZsBronze = Color(0xFFCD7F32)
val ZsWarning = Color(0xFFFFB020)
val ZsInfo = Color(0xFF6EC1FF)
val ZsGrey = Color(0xFF8B949E)

val ZsChatSentStart = Color(0xFFFF1E3C)
val ZsChatSentEnd = Color(0xFFD91633)
val ZsChatReceived = Color(0xFF14161C)

private val ZsColorScheme = darkColorScheme(
    primary = ZsPrimary,
    onPrimary = Color.White,
    primaryContainer = ZsCard,
    onPrimaryContainer = ZsTextPrimary,
    secondary = ZsCyan,
    onSecondary = Color(0xFF0B1220),
    secondaryContainer = ZsCardAlt,
    onSecondaryContainer = ZsTextPrimary,
    tertiary = ZsAccent,
    onTertiary = Color(0xFF06251D),
    background = ZsBgStart,
    onBackground = ZsTextPrimary,
    surface = ZsCard,
    onSurface = ZsTextPrimary,
    surfaceVariant = ZsCardAlt,
    onSurfaceVariant = ZsTextSecondary,
    error = ZsDanger,
    onError = Color.White,
    outline = ZsBorder
)

private val ZsTypography = Typography(
    // Racing type: bold italic headings, like a motorsport HUD.
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontStyle = FontStyle.Italic,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontStyle = FontStyle.Italic,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
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
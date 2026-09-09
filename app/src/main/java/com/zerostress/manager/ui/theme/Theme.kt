package com.zerostress.manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- Brand palette (ported from res/values/colors.xml) ----
val ZsBgStart = Color(0xFF0F0C29)
val ZsBgMid = Color(0xFF302B63)
val ZsBgEnd = Color(0xFF24243E)
val ZsCard = Color(0xFF1A1A2E)
val ZsCardAlt = Color(0xFF16213E)

val ZsPrimary = Color(0xFF667EEA)
val ZsPrimaryDark = Color(0xFF5A67D8)
val ZsPurple = Color(0xFF764BA2)

val ZsAccent = Color(0xFF38EF7D)
val ZsAccentDark = Color(0xFF11998E)

val ZsTextPrimary = Color(0xFFFFFFFF)
val ZsTextSecondary = Color(0xFFA0AEC0)
val ZsTextMuted = Color(0xFF718096)
val ZsBorder = Color(0xFF4A5568)
val ZsBorderLight = Color(0xFF718096)

val ZsDanger = Color(0xFFFC4A1A)
val ZsSuccess = Color(0xFF11998E)
val ZsGreen = Color(0xFF10B981)
val ZsCyan = Color(0xFF38BDF8)
val ZsGold = Color(0xFFFFD200)
val ZsSilver = Color(0xFFC0C0C0)
val ZsBronze = Color(0xFFCD7F32)
val ZsWarning = Color(0xFFF7971E)
val ZsInfo = Color(0xFF00D2FF)
val ZsGrey = Color(0xFF8B949E)

val ZsChatSentStart = Color(0xFF667EEA)
val ZsChatSentEnd = Color(0xFF764BA2)
val ZsChatReceived = Color(0xFF1A1A2E)

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
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
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
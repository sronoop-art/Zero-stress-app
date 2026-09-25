package com.zerostress.manager.ui.futuristic

import androidx.compose.ui.graphics.Color

/**
 * NEXUS UI color system - the futuristic esports skin layered on top of the
 * existing Neon Glass palette in ui/theme/Theme.kt. The theme file keeps the
 * same slot names every screen already imports; this file adds the extra
 * tokens the new design system needs so screens never hardcode colors.
 */
object ZsNexus {
    // Brand
    val Cyan = Color(0xFF20E7FF)        // cyber cyan (primary)
    val Violet = Color(0xFF8B5CFF)      // electric violet (secondary)
    val Plasma = Color(0xFFC43CFF)      // plasma purple (accent)
    val Gold = Color(0xFFFFC857)        // premium gold
    val Success = Color(0xFF31F7A5)
    val Danger = Color(0xFFFF416C)

    // Deep background ladder ( darkest at the bottom of every screen )
    val Bg0 = Color(0xFF03050B)
    val Bg1 = Color(0xFF050814)
    val Bg2 = Color(0xFF070B17)

    // Text
    val TextPrimary = Color(0xFFF3F7FF)
    val TextSecondary = Color(0xFF9BA7C7)
    val TextMuted = Color(0xFF59647F)

    // Glass levels (translucent surfaces over the dark background)
    val Glass1 = Color(0x0AFFFFFF)      // level 1 - quiet panels
    val Glass2 = Color(0x10FFFFFF)      // level 2 - standard panels
    val Glass3 = Color(0x16FFFFFF)      // level 3 - emphasized panels
    val GlassBorder = Color(0x1AFFFFFF)  // hairline border (white ~10%)
    val GlassBorderLit = Color(0x2EFFFFFF)

    // HUD geometry
    val HudLine = Color(0x22FFFFFF)     // separators, grid
    val GridLine = Color(0x0720E7FF)    // cyan grid on the background

    // System status labels (real states only - see ZsHud.kt)
    val Online = Success
    val Offline = TextMuted
}

/** A rank's visual identity: core color + glow color, from one shared system. */
data class ZsRankSkin(val core: Color, val glow: Color)

/**
 * Rank visual identity for the ladder in ScoreMath.rankFor (Iron..Mythic) plus
 * the extended title ladder in models/ZsRankTitles. Thresholds and rank names
 * live in the data layer - this only decides how a rank LOOKS.
 */
fun zsRankSkin(rankName: String?): ZsRankSkin = when (rankName) {
    "Iron" -> ZsRankSkin(Color(0xFF8B949E), Color(0xFF5A6470))          // minimal metallic
    "Bronze" -> ZsRankSkin(Color(0xFFCD7F32), Color(0xFFFF9E58))        // warm metallic
    "Silver" -> ZsRankSkin(Color(0xFFC0C7D1), Color(0xFFE6ECF5))        // cool metallic
    "Gold" -> ZsRankSkin(Color(0xFFFFC857), Color(0xFFFFE29A))          // premium gold
    "Platinum" -> ZsRankSkin(Color(0xFF5CE1E6), Color(0xFFA9F6FA))      // cyan energy
    "Diamond" -> ZsRankSkin(Color(0xFF4FC3F7), Color(0xFF8ADCFF))       // crystalline blue
    "Heroic" -> ZsRankSkin(Color(0xFFB388FF), Color(0xFFD2BCFF))        // violet energy
    "Master" -> ZsRankSkin(Color(0xFFFF5252), Color(0xFFFF8A80))        // crimson
    "Grandmaster" -> ZsRankSkin(Color(0xFFFFD54F), Color(0xFFFFEC9F))   // radiant gold
    "Mythic" -> ZsRankSkin(Color(0xFFC43CFF), Color(0xFF20E7FF))        // cosmic plasma/cyan
    else -> ZsRankSkin(ZsNexus.Cyan, ZsNexus.Violet)                    // unknown: brand
}

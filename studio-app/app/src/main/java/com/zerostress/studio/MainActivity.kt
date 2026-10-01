package com.zerostress.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ============================================================================
// Palette state
// ============================================================================

class StudioState(
    var bgStart: String = "#050814",
    var bgMid: String = "#04060F",
    var bgEnd: String = "#03050B",
    var accent: String = "#20E7FF",
    var violet: String = "#8B5CFF",
    var text: String = "#F3F7FF",
    var text2: String = "#9BA7C7",
    var text3: String = "#59647F",
    var danger: String = "#FF416C",
    var success: String = "#31F7A5",
    var gold: String = "#FFC857",
    var cardA: Float = 0.06f,
    var borderA: Float = 0.09f,
    var radius: Int = 14
) {
    constructor(o: StudioState) : this(
        o.bgStart, o.bgMid, o.bgEnd, o.accent, o.violet,
        o.text, o.text2, o.text3, o.danger, o.success, o.gold,
        o.cardA, o.borderA, o.radius
    )

    override fun equals(other: Any?): Boolean =
        other is StudioState && other.toJson() == toJson()

    override fun hashCode(): Int = toJson().hashCode()

    fun toJson(): String = "{" +
        "\"bgStart\":\"$bgStart\",\"bgMid\":\"$bgMid\",\"bgEnd\":\"$bgEnd\"," +
        "\"accent\":\"$accent\",\"violet\":\"$violet\"," +
        "\"text\":\"$text\",\"text2\":\"$text2\",\"text3\":\"$text3\"," +
        "\"danger\":\"$danger\",\"success\":\"$success\",\"gold\":\"$gold\"," +
        "\"cardA\":$cardA,\"borderA\":$borderA,\"radius\":$radius}"

    companion object {
        fun fromJson(s: String): StudioState? = try {
            fun grab(key: String): String {
                val i = s.indexOf("\"$key\":\"")
                if (i < 0) throw IllegalArgumentException(key)
                val start = i + key.length + 3
                val end = s.indexOf('"', start)
                return s.substring(start, end)
            }
            fun num(key: String): Float {
                val i = s.indexOf("\"$key\":")
                if (i < 0) throw IllegalArgumentException(key)
                val start = i + key.length + 2
                var end = start
                while (end < s.length && (s[end].isDigit() || s[end] == '.' || s[end] == '-')) end++
                return s.substring(start, end).toFloat()
            }
            StudioState(
                bgStart = grab("bgStart"), bgMid = grab("bgMid"), bgEnd = grab("bgEnd"),
                accent = grab("accent"), violet = grab("violet"),
                text = grab("text"), text2 = grab("text2"), text3 = grab("text3"),
                danger = grab("danger"), success = grab("success"), gold = grab("gold"),
                cardA = num("cardA"), borderA = num("borderA"),
                radius = num("radius").roundToInt()
            )
        } catch (_: Exception) {
            null
        }
    }
}

private val PRESETS: Map<String, StudioState> = mapOf(
    "NEXUS" to StudioState(),
    "CYBER GRID" to StudioState(
        "#02030A", "#030610", "#010208", "#00F0FF", "#FF00E5", "#D9F7FF", "#7FA8B8", "#3E5A66",
        "#FF3355", "#00FF9D", "#FFE14D", 0.05f, 0.08f, 4
    ),
    "CARBON GT" to StudioState(
        "#0C0E12", "#0A0C10", "#07080B", "#FF1E3C", "#FF5C1E", "#E8EAF0", "#9AA3B2", "#4C5563",
        "#FF1E3C", "#3DFF88", "#FFC93C", 0.07f, 0.12f, 6
    ),
    "ARCTIC PRO" to StudioState(
        "#E9F0F6", "#F4F8FB", "#DFE7EE", "#0092B8", "#5B5BD6", "#0F172A", "#4A5568", "#94A3B8",
        "#D6304B", "#0E9F6E", "#B7791F", 0.65f, 0.18f, 16
    ),
    "BLOOD ARENA" to StudioState(
        "#0A0304", "#120507", "#070203", "#FF2E43", "#8B1E2E", "#FFEDEE", "#C08A90", "#6E4048",
        "#FF2E43", "#43FF8B", "#FFC857", 0.08f, 0.14f, 10
    ),
    "VAPORWAVE" to StudioState(
        "#12081F", "#1A0B2E", "#0C0517", "#FF71CE", "#01CDCF", "#F7ECFF", "#B39AC7", "#5F4A73",
        "#FF416C", "#01FF9D", "#FFD3A5", 0.09f, 0.15f, 20
    )
)

// ============================================================================
// Color helpers (mirror the web Theme Studio math exactly)
// ============================================================================

private fun hx(s: String): Long = try {
    s.removePrefix("#").toLong(16)
} catch (_: Exception) {
    0x000000
}

private fun c(hex: String): Color = Color(0xFF000000L or hx(hex))

private fun clampF(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))

private fun rgbToHsl(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
    val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
    val mx = max(rf, max(gf, bf)); val mn = min(rf, min(gf, bf))
    val d = mx - mn
    var h = 0f
    if (d != 0f) {
        h = (when (mx) {
            rf -> ((gf - bf) / d + (if (gf < bf) 6f else 0f))
            gf -> ((bf - rf) / d + 2f)
            else -> ((rf - gf) / d + 4f)
        }) * 60f
    }
    val l = (mx + mn) / 2f
    val sat = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
    return Triple(h, sat * 100f, l * 100f)
}

private fun hslToHex(h: Float, sIn: Float, lIn: Float): String {
    val s = clampF(sIn, 0f, 100f) / 100f
    val l = clampF(lIn, 0f, 100f) / 100f
    fun f(n: Double): Double {
        val k = (n + h / 30.0) % 12.0
        val a = s * min(l, 1 - l)
        return l - a * max(-1.0, min(min(k - 3.0, 9.0 - k), 1.0))
    }
    fun to255(v: Double) = (v * 255).roundToInt().coerceIn(0, 255)
    return String.format(
        Locale.US, "#%02X%02X%02X",
        to255(f(0.0)), to255(f(8.0)), to255(f(4.0))
    )
}

private fun darken(hex: String, amt: Float): String {
    val v = hx(hex)
    val (h, s, l) = rgbToHsl(((v shr 16) and 0xFF).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt())
    return hslToHex(h, s, clampF(l - amt, 0f, 100f))
}

private fun lighten(hex: String, amt: Float): String {
    val v = hx(hex)
    val (h, s, l) = rgbToHsl(((v shr 16) and 0xFF).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt())
    return hslToHex(h, s, clampF(l + amt, 0f, 100f))
}

private fun alphaByte(a: Float): String =
    (clampF(a, 0f, 1f) * 255).roundToInt().toString(16).padStart(2, '0').uppercase()

private fun randomTheme(): StudioState {
    val hue = (0..359).random().toFloat()
    val bgHue = (hue + (-20..20).random() + 360) % 360
    val accHue = if ((0..1).random() == 0) hue
    else (hue + listOf(90, 120, 180, 270).random()) % 360
    val vioHue = (accHue + 40 + (0..50).random()) % 360
    fun hs(h: Float, s: Float, l: Float) = hslToHex(h, s, l)
    return StudioState(
        bgStart = hs(bgHue, 30f + (0..30).random().toFloat(), 5f + (0..4).random().toFloat()),
        bgMid = hs(bgHue, 30f + (0..30).random().toFloat(), 3.5f + (0..3).random().toFloat()),
        bgEnd = hs(bgHue, 30f + (0..30).random().toFloat(), 2f + (0..2).random().toFloat()),
        accent = hs(accHue, 78f + (0..20).random().toFloat(), 55f + (0..12).random().toFloat()),
        violet = hs(vioHue, 75f + (0..20).random().toFloat(), 58f + (0..10).random().toFloat()),
        text = hs(bgHue, 40f + (0..30).random().toFloat(), 93f + (0..5).random().toFloat()),
        text2 = hs(bgHue, 18f + (0..20).random().toFloat(), 62f + (0..8).random().toFloat()),
        text3 = hs(bgHue, 12f + (0..15).random().toFloat(), 38f + (0..10).random().toFloat()),
        danger = hs((350 + (-10..10).random() + 360) % 360, 85f, 60f),
        success = hs((140 + (-15..15).random() + 360) % 360, 85f, 58f),
        gold = hs(40f + (0..8).random().toFloat(), 90f, 62f),
        cardA = 0.04f + (0..8).random() / 100f,
        borderA = 0.07f + (0..8).random() / 100f,
        radius = (4..20).random()
    )
}

// ============================================================================
// Export: drop-in Theme.kt / ZsTheme.kt (same output as the web studio)
// ============================================================================

private fun C(hex: String) = "Color(0xFF" + hex.removePrefix("#").uppercase() + ")"
private fun CA(a: Float, hex: String) =
    "Color(0x" + alphaByte(a) + hex.removePrefix("#").uppercase() + ")"

internal fun themeKt(s: StudioState): String {
    val dark = darken(s.accent, 25f)
    return "package com.zerostress.manager.ui.theme\n" +
        "\n" +
        "import androidx.compose.material3.MaterialTheme\n" +
        "import androidx.compose.material3.Typography\n" +
        "import androidx.compose.material3.darkColorScheme\n" +
        "import androidx.compose.runtime.Composable\n" +
        "import androidx.compose.ui.graphics.Color\n" +
        "import androidx.compose.ui.text.TextStyle\n" +
        "import androidx.compose.ui.text.font.Font\n" +
        "import androidx.compose.ui.text.font.FontFamily\n" +
        "import androidx.compose.ui.text.font.FontWeight\n" +
        "import androidx.compose.ui.unit.sp\n" +
        "import com.zerostress.manager.R\n" +
        "\n" +
        "// ---- Brand palette: generated with Layout Studio (native app) ----\n" +
        "// Replaces the previous NEXUS values; slot names are unchanged so every\n" +
        "// screen keeps compiling without edits.\n" +
        "val ZsBgStart = ${C(s.bgStart)}\n" +
        "val ZsBgMid = ${C(s.bgMid)}\n" +
        "val ZsBgEnd = ${C(s.bgEnd)}\n" +
        "val ZsCard = ${CA(s.cardA, \"#FFFFFF\")}         // glass fill\n" +
        "val ZsCardAlt = ${CA(min(1f, s.cardA * 1.5f), \"#FFFFFF\")}      // brighter glass\n" +
        "\n" +
        "val ZsPrimary = ${C(s.accent)}\n" +
        "val ZsPrimaryDark = ${C(dark)}  // deep accent (pressed / gradients)\n" +
        "val ZsPurple = ${C(s.violet)}       // secondary glow\n" +
        "\n" +
        "val ZsAccent = ${C(s.accent)}       // hero accent\n" +
        "val ZsAccentDark = ${C(dark)}\n" +
        "\n" +
        "val ZsTextPrimary = ${C(s.text)}\n" +
        "val ZsTextSecondary = ${C(s.text2)}\n" +
        "val ZsTextMuted = ${C(s.text3)}\n" +
        "val ZsBorder = ${CA(s.borderA, \"#FFFFFF\")}\n" +
        "val ZsBorderLight = ${CA(min(1f, s.borderA + 0.06f), \"#FFFFFF\")}\n" +
        "\n" +
        "val ZsDanger = ${C(s.danger)}\n" +
        "val ZsSuccess = ${C(s.success)}\n" +
        "val ZsGreen = ${C(s.success)}\n" +
        "val ZsCyan = ${C(s.accent)}         // kept as the shared \"cyan\" slot\n" +
        "val ZsGold = ${C(s.gold)}         // rank gold\n" +
        "val ZsSilver = Color(0xFFC0C7D1)\n" +
        "val ZsBronze = Color(0xFFCD7F32)\n" +
        "val ZsWarning = ${C(lighten(s.gold, 8f))}\n" +
        "val ZsInfo = ${C(lighten(s.accent, 18f))}\n" +
        "val ZsGrey = ${C(s.text3)}\n" +
        "\n" +
        "val ZsChatSentStart = ${C(s.accent)}\n" +
        "val ZsChatSentEnd = ${C(s.violet)}\n" +
        "val ZsChatReceived = ${CA(min(1f, s.cardA * 2f), \"#FFFFFF\")}\n" +
        "\n" +
        "private val ZsColorScheme = darkColorScheme(\n" +
        "    primary = ZsPrimary,\n" +
        "    onPrimary = Color(0xFF04101A),      // dark ink on neon fills\n" +
        "    primaryContainer = ZsCard,\n" +
        "    onPrimaryContainer = ZsTextPrimary,\n" +
        "    secondary = ZsPurple,\n" +
        "    onSecondary = Color(0xFF04101A),\n" +
        "    secondaryContainer = ZsCardAlt,\n" +
        "    onSecondaryContainer = ZsTextPrimary,\n" +
        "    tertiary = ZsAccent,\n" +
        "    onTertiary = Color(0xFF04101A),\n" +
        "    background = ZsBgStart,\n" +
        "    onBackground = ZsTextPrimary,\n" +
        "    surface = ZsBgMid,\n" +
        "    onSurface = ZsTextPrimary,\n" +
        "    surfaceVariant = ZsCardAlt,\n" +
        "    onSurfaceVariant = ZsTextSecondary,\n" +
        "    error = ZsDanger,\n" +
        "    onError = Color.White,\n" +
        "    outline = ZsBorder\n" +
        ")\n" +
        "\n" +
        "/**\n" +
        " * Esports display family (Rajdhani): squared, techy numerals and headings.\n" +
        " * Weights map onto the shipped files; the Bold file also serves ExtraBold /\n" +
        " * Black so headings stay crisp without extra font assets.\n" +
        " */\n" +
        "private val ZsRajdhani = FontFamily(\n" +
        "    Font(R.font.rajdhani_medium, FontWeight.Normal),\n" +
        "    Font(R.font.rajdhani_medium, FontWeight.Medium),\n" +
        "    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),\n" +
        "    Font(R.font.rajdhani_bold, FontWeight.Bold),\n" +
        "    Font(R.font.rajdhani_bold, FontWeight.ExtraBold),\n" +
        "    Font(R.font.rajdhani_bold, FontWeight.Black)\n" +
        ")\n" +
        "\n" +
        "private val ZsTypography = Typography(\n" +
        "    headlineLarge = TextStyle(fontFamily = ZsRajdhani, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.5).sp),\n" +
        "    headlineMedium = TextStyle(fontFamily = ZsRajdhani, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),\n" +
        "    titleLarge = TextStyle(fontFamily = ZsRajdhani, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = 0.4.sp),\n" +
        "    titleMedium = TextStyle(fontFamily = ZsRajdhani, fontWeight = FontWeight.Bold, fontSize = 16.sp),\n" +
        "    bodyLarge = TextStyle(fontFamily = ZsRajdhani, fontWeight = FontWeight.Normal, fontSize = 16.sp),\n" +
        "    bodyMedium = TextStyle(fontFamily = ZsRajdhani, fontWeight = FontWeight.Normal, fontSize = 14.sp),\n" +
        "    labelLarge = TextStyle(fontFamily = ZsRajdhani, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.8.sp)\n" +
        ")\n" +
        "\n" +
        "@Composable\n" +
        "fun ZeroStressTheme(content: @Composable () -> Unit) {\n" +
        "    MaterialTheme(\n" +
        "        colorScheme = ZsColorScheme,\n" +
        "        typography = ZsTypography,\n" +
        "        content = content\n" +
        "    )\n" +
        "}\n"
}

internal fun zsThemeKt(s: StudioState): String {
    val dark = darken(s.accent, 25f)
    return "package com.zerostress.manager.ui.theme\n" +
        "\n" +
        "import androidx.compose.ui.graphics.Color\n" +
        "\n" +
        "// NEXUS UI palette - generated with Layout Studio (native app).\n" +
        "// Mirrors ui/theme/Theme.kt. The object keeps the same slot names most\n" +
        "// screens already reference.\n" +
        "object ZeroStressTheme {\n" +
        "    val ZsAccent = ${C(s.accent)}      // primary accent\n" +
        "    val ZsAccent2 = ${C(s.violet)}     // secondary glow\n" +
        "    val ZsPurple = ${C(s.violet)}\n" +
        "    val ZsCyan = ${C(s.accent)}        // shared \"cyan\" slot\n" +
        "    val ZsGold = ${C(s.gold)}        // rank gold\n" +
        "    val ZsGreen = ${C(s.success)}\n" +
        "    val ZsDanger = ${C(s.danger)}\n" +
        "    val ZsWarning = ${C(lighten(s.gold, 8f))}\n" +
        "    val ZsTextPrimary = ${C(s.text)}\n" +
        "    val ZsTextSecondary = ${C(s.text2)}\n" +
        "    val ZsTextMuted = ${C(s.text3)}\n" +
        "    val ZsCard = ${CA(s.cardA, \"#FFFFFF\")}        // glass fill\n" +
        "    val ZsChatSentEnd = ${C(dark)}\n" +
        "}\n"
}

// ============================================================================
// Live phone preview - mirrors the web studio's three mock screens
// ============================================================================

@Composable
private fun PhonePreview(s: StudioState) {
    val bg = Brush.verticalGradient(listOf(c(s.bgStart), c(s.bgMid), c(s.bgEnd)))
    val accent = c(s.accent)
    val violet = c(s.violet)
    val card = Color.White.copy(alpha = s.cardA.coerceIn(0f, 1f))
    val border = Color.White.copy(alpha = s.borderA.coerceIn(0f, 1f))
    val txt = c(s.text)
    val txt2 = c(s.text2)
    val txt3 = c(s.text3)
    val gold = c(s.gold)
    val shape = RoundedCornerShape(s.radius.coerceIn(0, 32))
    val smallShape = RoundedCornerShape((s.radius.coerceIn(0, 32) * 0.6f).roundToInt())

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .padding(12.dp)
    ) {
        // ---- LOGIN mock ----
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(card)
                .border(1.dp, border, shape)
                .padding(vertical = 22.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent, violet))),
                contentAlignment = Alignment.Center
            ) {
                Text("X", color = Color(0xFF04101A), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("ONLY TEAM ", color = txt, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("X", color = accent, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(10.dp))
            listOf("Phone number", "Password").forEach { hint ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(smallShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, border, smallShape)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) { Text(hint, color = txt3, fontSize = 10.sp) }
                Spacer(Modifier.height(6.dp))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(smallShape)
                    .background(Brush.horizontalGradient(listOf(accent, violet)))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) { Text("SIGN IN", color = Color(0xFF04101A), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(10.dp))

        // ---- DASHBOARD mock ----
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(card)
                .border(1.dp, border, shape)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("ONLY TEAM-X", color = txt, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text("PLAYER CORE", color = accent, fontSize = 7.sp, letterSpacing = 2.sp)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(smallShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, gold.copy(alpha = 0.6f), smallShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) { Text("1,250 coins", color = gold, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("MATCH", "CHAT", "RANK").forEach { t ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(smallShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, border, smallShape)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(t, color = txt2, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(2.dp, gold, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("G", color = gold, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("GOLD", color = gold, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Text("2,000 pts - #4", color = txt2, fontSize = 8.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- CHAT mock ----
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(card)
                .border(1.dp, border, shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                Modifier
                    .clip(smallShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) { Text("gg everyone, que up?", color = txt, fontSize = 9.sp) }
            Box(
                Modifier
                    .align(Alignment.End)
                    .clip(smallShape)
                    .background(Brush.horizontalGradient(listOf(accent, violet)))
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) { Text("match at 8? @Ravi", color = Color(0xFF04101A), fontSize = 9.sp, fontWeight = FontWeight.SemiBold) }
            Box(
                Modifier
                    .clip(smallShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) { Text("ready @you", color = txt, fontSize = 9.sp) }
            Box(
                Modifier
                    .clip(smallShape)
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, border, smallShape)
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) { Text("Type a message...", color = txt3, fontSize = 9.sp) }
        }
    }
}

// ============================================================================
// Controls
// ============================================================================

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Color(0xFF8B949E),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun ColorRow(label: String, value: String, onChange: (String) -> Unit) {
    var showPicker by rememberSaveable(label) { mutableStateOf(false) }
    var field by rememberSaveable(label) { mutableStateOf(value) }
    // Keep the text field synced when the value changes externally (preset/random).
    if (field.uppercase(Locale.US) != value.uppercase(Locale.US)) field = value

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(c(value))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .clickable { showPicker = true }
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color(0xFFC9D2E8), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .clickable { showPicker = true }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(value.uppercase(Locale.US), color = Color(0xFF9BE8FF), fontSize = 11.sp)
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label, fontSize = 15.sp) },
            text = {
                Column {
                    Text(
                        "Type a hex color (like #20E7FF) or tap a swatch.",
                        color = Color(0xFF9BA7C7),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        textStyle = TextStyle(
                            color = Color(0xFF9BE8FF), fontSize = 15.sp, fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .padding(10.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    val swatches = listOf(
                        "#050814", "#0C0E12", "#12081F", "#0A0304", "#E9F0F6",
                        "#20E7FF", "#00F0FF", "#8B5CFF", "#FF71CE", "#FF1E3C",
                        "#FF416C", "#31F7A5", "#FFC857", "#FFB020", "#F3F7FF"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        swatches.chunked(5).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { sw ->
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(c(sw))
                                            .border(
                                                1.dp,
                                                if (sw.equals(value, true)) Color.White else Color.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                onChange(sw)
                                                field = sw
                                                showPicker = false
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = field.trim().uppercase(Locale.US)
                    if (v.matches(Regex("#[0-9A-F]{6}"))) {
                        onChange(v)
                        showPicker = false
                    }
                }) { Text("SET", color = Color(0xFF20E7FF), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false; field = value }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StudioSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    val widthPx = remember { mutableStateOf(1f) }
    fun emit(x: Float) {
        if (widthPx.value <= 0f) return
        val frac = clampF(x / widthPx.value, 0f, 1f)
        onChange(range.start + frac * (range.endInclusive - range.start))
    }

    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, color = Color(0xFFC9D2E8), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = Color(0xFF9BE8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .onSizeChanged { widthPx.value = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(range) {
                    detectDragGestures(
                        onDragStart = { off -> emit(off.x) },
                        onDrag = { change, _ -> emit(change.position.x) }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val frac = clampF(
                    (value - range.start) / (range.endInclusive - range.start), 0f, 1f
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f),
                    cornerRadius = CornerRadius(18f, 18f)
                )
                drawRoundRect(
                    color = Color(0xFF20E7FF),
                    size = Size(size.width * frac, size.height),
                    cornerRadius = CornerRadius(18f, 18f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 9.dp.toPx(),
                    center = Offset(size.width * frac, size.height / 2f)
                )
            }
        }
    }
}

// ============================================================================
// Main screen
// ============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("zs_studio", Context.MODE_PRIVATE)
        val initial = prefs.getString("theme", null)?.let { StudioState.fromJson(it) } ?: StudioState()

        setContent {
            var state by remember { mutableStateOf(initial) }

            fun update(transform: (StudioState) -> Unit) {
                val ns = StudioState(state)
                transform(ns)
                state = ns
                prefs.edit().putString("theme", ns.toJson()).apply()
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0E1A))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "LAYOUT STUDIO",
                    color = Color(0xFFF3F7FF),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Design the ONLY TEAM-X look live, then export the theme files.",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(14.dp))

                PhonePreview(state)

                // ---- Presets ----
                SectionTitle("PRESETS")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NEXUS", "CYBER GRID", "CARBON GT").forEach { name ->
                        PresetChip(name, state == PRESETS[name]) {
                            update { ns -> PRESETS[name]!!.let { p ->
                                ns.bgStart = p.bgStart; ns.bgMid = p.bgMid; ns.bgEnd = p.bgEnd
                                ns.accent = p.accent; ns.violet = p.violet
                                ns.text = p.text; ns.text2 = p.text2; ns.text3 = p.text3
                                ns.danger = p.danger; ns.success = p.success; ns.gold = p.gold
                                ns.cardA = p.cardA; ns.borderA = p.borderA; ns.radius = p.radius
                            } }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ARCTIC PRO", "BLOOD ARENA", "VAPORWAVE").forEach { name ->
                        PresetChip(name, state == PRESETS[name]) {
                            update { ns -> PRESETS[name]!!.let { p ->
                                ns.bgStart = p.bgStart; ns.bgMid = p.bgMid; ns.bgEnd = p.bgEnd
                                ns.accent = p.accent; ns.violet = p.violet
                                ns.text = p.text; ns.text2 = p.text2; ns.text3 = p.text3
                                ns.danger = p.danger; ns.success = p.success; ns.gold = p.gold
                                ns.cardA = p.cardA; ns.borderA = p.borderA; ns.radius = p.radius
                            } }
                        }
                    }
                    PresetChip("RANDOM", false) { update { ns -> randomTheme().let { r ->
                        ns.bgStart = r.bgStart; ns.bgMid = r.bgMid; ns.bgEnd = r.bgEnd
                        ns.accent = r.accent; ns.violet = r.violet
                        ns.text = r.text; ns.text2 = r.text2; ns.text3 = r.text3
                        ns.danger = r.danger; ns.success = r.success; ns.gold = r.gold
                        ns.cardA = r.cardA; ns.borderA = r.borderA; ns.radius = r.radius
                    } } }
                }

                // ---- Colors ----
                SectionTitle("BACKGROUND")
                ColorRow("Background start (ZsBgStart)", state.bgStart) { v -> update { it.bgStart = v } }
                ColorRow("Background mid (ZsBgMid)", state.bgMid) { v -> update { it.bgMid = v } }
                ColorRow("Background end (ZsBgEnd)", state.bgEnd) { v -> update { it.bgEnd = v } }

                SectionTitle("ACCENTS")
                ColorRow("Primary accent (ZsPrimary/ZsAccent)", state.accent) { v -> update { it.accent = v } }
                ColorRow("Secondary glow (ZsPurple)", state.violet) { v -> update { it.violet = v } }

                SectionTitle("TEXT")
                ColorRow("Text primary", state.text) { v -> update { it.text = v } }
                ColorRow("Text secondary", state.text2) { v -> update { it.text2 = v } }
                ColorRow("Text muted", state.text3) { v -> update { it.text3 = v } }

                SectionTitle("STATUS")
                ColorRow("Danger / defeat (ZsDanger)", state.danger) { v -> update { it.danger = v } }
                ColorRow("Success / victory (ZsSuccess)", state.success) { v -> update { it.success = v } }
                ColorRow("Gold / coins (ZsGold)", state.gold) { v -> update { it.gold = v } }

                SectionTitle("SURFACES")
                StudioSlider(
                    "Card glass", "${(state.cardA * 100).roundToInt()}%",
                    state.cardA, 0f..0.30f
                ) { v -> update { it.cardA = v } }
                StudioSlider(
                    "Border glass", "${(state.borderA * 100).roundToInt()}%",
                    state.borderA, 0f..0.30f
                ) { v -> update { it.borderA = v } }
                StudioSlider(
                    "Corner radius", "${state.radius}px",
                    state.radius.toFloat(), 0f..28f
                ) { v -> update { it.radius = v.roundToInt() } }

                // ---- Export ----
                SectionTitle("EXPORT")
                StudioButton("COPY THEME.KT", Color(0xFF20E7FF)) {
                    copyToClipboard(themeKt(state))
                }
                Spacer(Modifier.height(8.dp))
                StudioButton("COPY ZSTHEME.KT", Color(0xFF8B5CFF)) {
                    copyToClipboard(zsThemeKt(state))
                }
                Spacer(Modifier.height(8.dp))
                StudioButton("SHARE BOTH FILES", Color(0xFFFFC857)) {
                    shareBoth(state)
                }
                Spacer(Modifier.height(8.dp))
                StudioButton("SAVE TO DOWNLOADS", Color(0xFF31F7A5)) {
                    val ok = saveToDownloads(state)
                    Toast.makeText(
                        this,
                        if (ok) "Saved Theme.kt + ZsTheme.kt to Downloads/ZsStudio"
                        else "Could not save - use SHARE instead",
                        Toast.LENGTH_LONG
                    ).show()
                }
                Spacer(Modifier.height(8.dp))
                StudioButton("RESET TO NEXUS", Color(0xFFFF416C)) {
                    update { ns -> StudioState().let { p ->
                        ns.bgStart = p.bgStart; ns.bgMid = p.bgMid; ns.bgEnd = p.bgEnd
                        ns.accent = p.accent; ns.violet = p.violet
                        ns.text = p.text; ns.text2 = p.text2; ns.text3 = p.text3
                        ns.danger = p.danger; ns.success = p.success; ns.gold = p.gold
                        ns.cardA = p.cardA; ns.borderA = p.borderA; ns.radius = p.radius
                    } }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "After exporting: paste each file into the chat and the theme gets " +
                        "applied to the real app, then rebuild ZS3.1.zip as usual.",
                    color = Color(0xFF59647F),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("zs_theme", text))
        Toast.makeText(this, "Copied! Paste it into the chat.", Toast.LENGTH_LONG).show()
    }

    private fun shareBoth(s: StudioState) {
        val text = "// ===== Theme.kt =====\n" + themeKt(s) +
            "\n// ===== ZsTheme.kt =====\n" + zsThemeKt(s)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "ONLY TEAM-X theme export")
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        startActivity(android.content.Intent.createChooser(send, "Share theme"))
    }

    private fun saveToDownloads(s: StudioState): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            fun write(name: String, content: String) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/ZsStudio")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("insert failed")
                contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            }
            write("Theme.kt", themeKt(s))
            write("ZsTheme.kt", zsThemeKt(s))
            true
        } catch (_: Exception) {
            false
        }
    }
}

// ============================================================================
// Small widgets
// ============================================================================

@Composable
private fun PresetChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFF20E7FF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (active) Color(0xFF20E7FF) else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (active) Color(0xFF9BE8FF) else Color(0xFFC9D2E8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StudioButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = accent, fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp
        )
    }
}

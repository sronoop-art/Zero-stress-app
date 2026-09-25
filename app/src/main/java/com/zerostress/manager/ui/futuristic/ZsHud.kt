package com.zerostress.manager.ui.futuristic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NEXUS HUD chrome: glass panels with sci-fi corner accents, technical
 * status labels and the reusable scan effect. Every panel routes through
 * ZsGlassPanel so the glass levels stay consistent app-wide.
 */

/** Glass depth levels. */
enum class ZsGlassLevel(val fill: Color, val border: Color) {
    One(ZsNexus.Glass1, ZsNexus.GlassBorder),
    Two(ZsNexus.Glass2, ZsNexus.GlassBorderLit),
    Three(ZsNexus.Glass3, ZsNexus.GlassBorderLit)
}

/** Corner accent style for HUD panels. */
enum class ZsHudCorners { None, Subtle, Full }

/**
 * Holographic glass panel - the NEXUS replacement for plain cards.
 *
 * Layering: translucent fill, hairline border, inner top highlight, soft
 * outer glow when [glow] is set, optional HUD corner accents, press-depth
 * micro animation when clickable. Glass levels give hierarchy without
 * stronger colors.
 */
@Composable
fun ZsGlassPanel(
    modifier: Modifier = Modifier,
    level: ZsGlassLevel = ZsGlassLevel.Two,
    corner: Dp = 20.dp,
    hudCorners: ZsHudCorners = ZsHudCorners.Subtle,
    glow: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale = zsPressScale(pressed)
    var m = modifier
        .fillMaxWidth()
        .scale(scale)
        .clip(RoundedCornerShape(corner))
        .background(level.fill)
        .drawBehind {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
                ),
                cornerRadius = CornerRadius(corner.toPx())
            )
            if (glow != null) {
                drawRoundRect(
                    color = glow.copy(alpha = 0.22f),
                    topLeft = Offset(-5.dp.toPx(), -5.dp.toPx()),
                    size = Size(size.width + 10.dp.toPx(), size.height + 10.dp.toPx()),
                    cornerRadius = CornerRadius(corner.toPx() + 5.dp.toPx())
                )
            }
            drawHudCorners(hudCorners, corner, glow)
        }
        .border(
            width = if (level == ZsGlassLevel.One) 1.dp else 1.2.dp,
            color = glow?.copy(alpha = 0.45f) ?: level.border,
            shape = RoundedCornerShape(corner)
        )
    if (onClick != null) {
        m = m
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try { awaitRelease() } finally { pressed = false }
                    },
                    onTap = { onClick() }
                )
            }
    }
    Column(modifier = m.padding(16.dp), content = content)
}

/**
 * Sci-fi corner geometry: short technical lines inside each corner with a
 * tiny glowing node. Fully lit when the panel is emphasized (glow set) or
 * corners are Full; otherwise a quiet 35% presence.
 */
private fun DrawScope.drawHudCorners(style: ZsHudCorners, corner: Dp, glow: Color?) {
    if (style == ZsHudCorners.None) return
    val accent = glow ?: ZsNexus.Cyan
    val alpha = if (style == ZsHudCorners.Full || glow != null) 0.85f else 0.35f
    val inset = 7.dp.toPx()
    val len = 13.dp.toPx()
    val c = accent.copy(alpha = alpha)
    val nodeR = 1.6.dp.toPx()
    val w = size.width
    val h = size.height

    fun cornerLines(cx: Float, cy: Float, dx: Float, dy: Float) {
        drawLine(
            c,
            Offset(cx + dx * inset, cy + dy * inset),
            Offset(cx + dx * (inset + len), cy + dy * inset),
            1.6f,
            StrokeCap.Round
        )
        drawLine(
            c,
            Offset(cx + dx * inset, cy + dy * inset),
            Offset(cx + dx * inset, cy + dy * (inset + len)),
            1.6f,
            StrokeCap.Round
        )
        drawCircle(accent.copy(alpha = alpha), nodeR, Offset(cx + dx * inset, cy + dy * inset))
    }
    cornerLines(0f, 0f, 1f, 1f)
    cornerLines(w, 0f, -1f, 1f)
    cornerLines(0f, h, 1f, -1f)
    cornerLines(w, h, -1f, -1f)
}

/**
 * Tiny technical status label, e.g. "SYSTEM ONLINE". Callers only ever pass
 * real states - this component never fabricates data.
 */
@Composable
fun ZsStatusLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ZsNexus.TextMuted,
    dotColor: Color? = null
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (dotColor != null) {
            Box(
                Modifier
                    .padding(end = 5.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        androidx.compose.material3.Text(
            text.uppercase(),
            color = color,
            fontSize = 9.sp,
            letterSpacing = ZsType.TechTracking,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * DrawScope-level HUD corner accents for custom canvases and bespoke cards
 * (podium tiles, rank frames). [lit] renders full-intensity corners; the
 * quiet pass runs at 35% alpha so rows stay calm.
 */
fun DrawScope.ZsDrawHudCorners(accent: Color, lit: Boolean) {
    val alpha = if (lit) 0.85f else 0.35f
    val inset = 7.dp.toPx()
    val len = 13.dp.toPx()
    val c = accent.copy(alpha = alpha)
    val nodeR = 1.6.dp.toPx()
    val w = size.width
    val h = size.height

    fun cornerLines(cx: Float, cy: Float, dx: Float, dy: Float) {
        drawLine(
            c,
            Offset(cx + dx * inset, cy + dy * inset),
            Offset(cx + dx * (inset + len), cy + dy * inset),
            1.6f,
            StrokeCap.Round
        )
        drawLine(
            c,
            Offset(cx + dx * inset, cy + dy * inset),
            Offset(cx + dx * inset, cy + dy * (inset + len)),
            1.6f,
            StrokeCap.Round
        )
        drawCircle(accent.copy(alpha = alpha), nodeR, Offset(cx + dx * inset, cy + dy * inset))
    }
    cornerLines(0f, 0f, 1f, 1f)
    cornerLines(w, 0f, -1f, 1f)
    cornerLines(0f, h, 1f, -1f)
    cornerLines(w, h, -1f, -1f)
}

/**
 * The HUD scan effect: a thin light line sweeping vertically across the
 * wrapped content. Use sparingly (identity card, rank-up moment, battle-pass
 * reward) - never on every card at once.
 */
@Composable
fun ZsScanEffect(
    modifier: Modifier = Modifier,
    color: Color = ZsNexus.Cyan,
    content: @Composable () -> Unit
) {
    val phase = rememberZsAmbientPhase(ZsMotion.Ambient * 3)
    Box(modifier.clipToBounds()) {
        content()
        Canvas(Modifier.matchParentSize()) {
            val y = size.height * phase
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, color.copy(alpha = 0.30f), Color.Transparent)
                ),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f
            )
        }
    }
}

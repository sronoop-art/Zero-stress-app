package com.zerostress.manager.ui.futuristic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/**
 * NEXUS data visualization: energy progression bar, score core ring, mini
 * stat modules and the loading HUD. All values come from real data supplied
 * by the caller; nothing here computes or fabricates stats.
 */

/**
 * Energy progression bar: glowing leading edge on a gradient fill over a dark
 * track. Purely presentational - the fraction comes from existing XP math.
 */
@Composable
fun ZsEnergyBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = ZsNexus.Cyan,
    height: Dp = 8.dp,
    label: String? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                label,
                color = ZsNexus.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(5.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val f = fraction.coerceIn(0f, 1f)
                val r = size.height / 2f
                // Track
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.06f),
                    cornerRadius = CornerRadius(r)
                )
                val w = size.width * f
                if (w > r * 1.2f) {
                    // Gradient fill
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.55f), color)
                        ),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(r)
                    )
                    // Glowing leading edge
                    drawCircle(
                        color.copy(alpha = 0.30f),
                        radius = r * 1.5f,
                        center = Offset(w - r * 0.3f, size.height / 2)
                    )
                    drawCircle(
                        Color.White.copy(alpha = 0.85f),
                        radius = r * 0.55f,
                        center = Offset(w - r * 0.3f, size.height / 2)
                    )
                }
            }
        }
    }
}

/**
 * Circular score core: sweeping energy arc with a faint track and a content
 * slot (score number) in the middle. The arc fraction is display only.
 */
@Composable
fun ZsScoreCore(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = ZsNexus.Cyan,
    stroke: Dp = 7.dp,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2 + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            // Track
            drawArc(
                color = Color.White.copy(alpha = 0.07f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round)
            )
            // Energy arc with a soft tip dot
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            drawArc(
                brush = Brush.sweepGradient(listOf(color, ZsNexus.Violet, color)),
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round)
            )
            if (sweep > 4f) {
                val angleRad = Math.toRadians((sweep - 90).toDouble())
                val cx = size.width / 2 + (size.width / 2 - inset) * kotlin.math.cos(angleRad).toFloat()
                val cy = size.height / 2 + (size.height / 2 - inset) * kotlin.math.sin(angleRad).toFloat()
                drawCircle(color.copy(alpha = 0.35f), radius = strokePx * 1.6f, center = Offset(cx, cy))
                drawCircle(Color.White.copy(alpha = 0.9f), radius = strokePx * 0.55f, center = Offset(cx, cy))
            }
        }
        content()
    }
}

/**
 * Compact HUD stat module: tiny uppercase label over a bold value, inside a
 * level-1 glass tile. Used for KILLS / WINS / K-D grids.
 */
@Composable
fun ZsHudStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = ZsNexus.Cyan
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(ZsNexus.Glass1)
            .padding(vertical = 11.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label.uppercase(),
            color = ZsNexus.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = ZsType.LabelTracking
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            color = color,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * Loading HUD: pulsing segmented ring with a status line. Replaces the
 * generic spinner on full-screen loads. Loading duration is unchanged.
 */
@Composable
fun ZsLoadingHud(
    message: String,
    modifier: Modifier = Modifier
) {
    val phase = rememberZsAmbientPhase(ZsMotion.Ambient)
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                val inset = stroke
                val s = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(
                    color = ZsNexus.Cyan.copy(alpha = 0.12f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = s,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ZsNexus.Cyan,
                    startAngle = phase * 360f, sweepAngle = 100f, useCenter = false,
                    topLeft = Offset(inset, inset), size = s,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            message.uppercase(),
            color = ZsNexus.TextMuted,
            fontSize = 10.sp,
            letterSpacing = ZsType.TechTracking,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "ZERO STRESS NETWORK",
            color = ZsNexus.TextMuted.copy(alpha = 0.6f),
            fontSize = 8.sp,
            letterSpacing = ZsType.TechTracking,
            fontWeight = FontWeight.Bold
        )
    }
}

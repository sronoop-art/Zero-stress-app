package com.zerostress.manager.ui.futuristic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin
import kotlin.random.Random

/**
 * NEXUS background: the layered depth system from the spec.
 *
 *   deep gradient -> atmospheric glows -> geometric grid -> particle field
 *
 * Everything renders in ONE Canvas pass with static, remembered geometry; the
 * only motion is an extremely slow phase drift (particles + glow breathing).
 * Layers are low-alpha so text on top always stays readable. Cheap enough to
 * run under every screen at 60fps.
 */
@Composable
fun ZsNexusBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Static particle seeds - remembered, so no per-frame allocation and no
    // flicker on recomposition.
    val particles = remember {
        List(14) {
            val r = Random(it * 31 + 7)
            Particle(
                seedX = r.nextFloat(),
                seedY = r.nextFloat(),
                radius = 0.8f + r.nextFloat() * 1.2f,
                alpha = 0.05f + r.nextFloat() * 0.10f,
                drift = 0.4f + r.nextFloat() * 0.6f
            )
        }
    }
    val phase = rememberZsAmbientPhase(ZsMotion.Ambient * 2)

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(ZsNexus.Bg0, ZsNexus.Bg1, ZsNexus.Bg2, ZsNexus.Bg1)
                )
            )
            .drawBehind { drawAtmosphere(phase) }
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawGrid()
            drawParticles(particles, phase)
        }
        Box(Modifier.fillMaxSize()) {
            content()
        }
    }
}

private data class Particle(
    val seedX: Float,
    val seedY: Float,
    val radius: Float,
    val alpha: Float,
    val drift: Float
)

/** Ambient washes: violet top-right, cyan bottom-left, slowly breathing. */
private fun DrawScope.drawAtmosphere(phase: Float) {
    val breathe = 0.85f + 0.15f * sin(phase * 2f * Math.PI.toFloat())
    val violet = ZsNexus.Violet.copy(alpha = 0.09f * breathe)
    val cyan = ZsNexus.Cyan.copy(alpha = 0.07f * breathe)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(violet, Color.Transparent),
            center = Offset(size.width * 0.95f, size.height * 0.02f),
            radius = size.width * 0.9f
        )
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(cyan, Color.Transparent),
            center = Offset(size.width * 0.05f, size.height * 0.95f),
            radius = size.width * 0.8f
        )
    )
}

/** Faint geometric grid: technical lines every ~48px, cyan at 3% alpha. */
private fun DrawScope.drawGrid() {
    val step = 48f
    val paint = ZsNexus.GridLine
    var x = 0f
    while (x < size.width) {
        drawLine(paint, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(paint, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

/** Sparse slow-drifting micro particles, phase-driven, no per-frame allocs. */
private fun DrawScope.drawParticles(particles: List<Particle>, phase: Float) {
    particles.forEach { p ->
        val px = ((p.seedX + phase * 0.02f * p.drift) % 1f) * size.width
        val py = ((p.seedY - phase * 0.03f * p.drift + 1f) % 1f) * size.height
        drawCircle(
            color = Color.White.copy(alpha = p.alpha),
            radius = p.radius,
            center = Offset(px, py)
        )
    }
}

package com.zerostress.manager.ui.futuristic

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * NEXUS motion primitives. Durations follow the spec:
 *  150ms micro-interactions, 250ms transitions, 400-600ms data,
 *  800-2000ms ambient. Ambient loops live in ZsBackground/ZsScanEffect only.
 */

/** Duration tokens. */
object ZsMotion {
    const val Micro = 150
    const val Transition = 250
    const val Data = 500
    const val Ambient = 1600
}

/** Returns a scale that dips to [pressedScale] while [pressed] is true. */
@Composable
fun zsPressScale(pressed: Boolean, pressedScale: Float = 0.985f): Float =
    animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(ZsMotion.Micro),
        label = "zsPress"
    ).value

/**
 * Animates a displayed number from its previous value to [target] over
 * ZsMotion.Data ms, so score/XP changes read as motion, not a jump cut.
 * The animation always ends on the exact target - the displayed value is
 * never left inaccurate.
 */
@Composable
fun rememberZsCountUp(target: Long): Long {
    val anim = remember { androidx.compose.animation.core.Animatable(target.toFloat()) }
    LaunchedEffect(target) {
        if (anim.value != target.toFloat()) {
            anim.animateTo(target.toFloat(), tween(ZsMotion.Data))
        }
    }
    return anim.value.toLong()
}

/**
 * Ambient phase in 0..1, looping every [durationMs]. Drives the slow
 * background drift and the scan line. One shared implementation keeps the
 * number of infinite transitions per screen at one per consumer.
 */
@Composable
fun rememberZsAmbientPhase(durationMs: Int = ZsMotion.Ambient): Float {
    val transition = rememberInfiniteTransition(label = "zsAmbient")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs),
            repeatMode = RepeatMode.Restart
        ),
        label = "zsAmbientPhase"
    )
    return phase
}

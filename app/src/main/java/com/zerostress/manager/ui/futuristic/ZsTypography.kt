package com.zerostress.manager.ui.futuristic

import androidx.compose.ui.unit.sp

/**
 * NEXUS type scale (Rajdhani family, applied via the theme). Screens use these
 * helpers for the HUD hierarchy: DISPLAY / HEADLINE / TITLE / BODY / LABEL.
 * Uppercase + wide tracking is reserved for system labels and HUD metadata.
 */
object ZsType {
    val DisplaySize = 34.sp
    val HeadlineSize = 26.sp
    val TitleSize = 18.sp
    val BodySize = 15.sp
    val LabelSize = 11.sp

    val DisplayTracking = 0.5.sp
    val HeadlineTracking = 0.8.sp
    val LabelTracking = 1.6.sp      // uppercase HUD labels
    val TechTracking = 2.2.sp       // tiny technical labels ("PLAYER CORE")
}

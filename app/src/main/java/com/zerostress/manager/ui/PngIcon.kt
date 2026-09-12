package com.zerostress.manager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zerostress.manager.R

/**
 * Shows a PNG icon from res/drawable (or drawable-xxxhdpi etc.) in place of the
 * old emoji text.
 *
 * Usage:  ZsPngIcon(R.drawable.ic_menu_calendar, size = 26.dp)
 *
 * If [tint] is supplied the PNG is recolored (works best with white / alpha
 * PNGs — the standard Material icon export). Without a tint the PNG's own
 * colors are used as-is.
 */
@Composable
fun ZsPngIcon(
    resId: Int,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color? = null,
    contentDescription: String? = null
) {
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = if (tint != null) ColorFilter.tint(tint) else null
    )
}

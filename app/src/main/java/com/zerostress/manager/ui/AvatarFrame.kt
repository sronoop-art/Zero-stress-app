package com.zerostress.manager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Avatar with a rank-title frame.
 *
 * - `frameRes` is the drawable id of `frame_<id>.png` (0 = no PNG available).
 *   When present it is drawn OVER the avatar, scaled to `avatarSize + 24.dp`
 *   so a ring-shaped frame PNG lines up around the circular avatar.
 *   Make the frame PNG square with a transparent center hole.
 * - When the PNG is missing, a plain colored ring in the title's color is
 *   drawn instead, so the feature works before any frames are added.
 *
 * Usage:
 *   ZsAvatarFrame(frameRes, titleColor, avatarSize = 88.dp) {
 *       // avatar content here (image or placeholder icon)
 *   }
 */
@Composable
fun ZsAvatarFrame(
    frameRes: Int,
    frameColor: Color,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        content()
        if (frameRes != 0) {
            Image(
                painter = painterResource(frameRes),
                contentDescription = null,
                modifier = Modifier
                    .size(avatarSize + 24.dp)
                    .offset(y = (-2).dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .size(avatarSize + 6.dp)
                    .border(3.dp, frameColor, CircleShape)
            )
        }
    }
}

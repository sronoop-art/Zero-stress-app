package com.zerostress.manager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zerostress.manager.models.ZsRankTitles
import java.io.File

/**
 * Avatar with a rank-title frame.
 *
 * `frameSource` comes from ZsRankTitles.frameSource(...) - either a bundled
 * drawable (frame_<id>.png in res/drawable) or an OTA-downloaded file
 * (filesDir/ota/frame_<id>.png), which takes priority. When null, a plain
 * colored ring in the title's color is drawn instead.
 *
 * Frame PNGs are square with a transparent center hole, drawn over a circular
 * avatar, scaled to avatarSize + 24.dp.
 */
@Composable
fun ZsAvatarFrame(
    frameSource: ZsRankTitles.FrameSource?,
    frameColor: Color,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        content()
        when (frameSource) {
            is ZsRankTitles.FrameSource.File -> {
                // OTA-downloaded frame file - decode once per composition input.
                val bitmap = remember(frameSource.file.absolutePath, frameSource.file.lastModified()) {
                    try {
                        android.graphics.BitmapFactory.decodeFile(frameSource.file.absolutePath)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(avatarSize + 24.dp)
                            .offset(y = (-2).dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    FallbackRing(frameColor, avatarSize)
                }
            }
            is ZsRankTitles.FrameSource.Resource -> {
                Image(
                    painter = painterResource(frameSource.resId),
                    contentDescription = null,
                    modifier = Modifier
                        .size(avatarSize + 24.dp)
                        .offset(y = (-2).dp),
                    contentScale = ContentScale.Fit
                )
            }
            null -> FallbackRing(frameColor, avatarSize)
        }
    }
}

@Composable
private fun FallbackRing(frameColor: Color, avatarSize: Dp) {
    Box(
        modifier = Modifier
            .size(avatarSize + 6.dp)
            .border(3.dp, frameColor, CircleShape)
    )
}

/** Compatibility helper for existing call sites that pass a drawable res id. */
@Composable
fun ZsAvatarFrame(
    frameRes: Int,
    frameColor: Color,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    ZsAvatarFrame(
        frameSource = if (frameRes != 0) ZsRankTitles.FrameSource.Resource(frameRes) else null,
        frameColor = frameColor,
        avatarSize = avatarSize,
        modifier = modifier,
        content = content
    )
}

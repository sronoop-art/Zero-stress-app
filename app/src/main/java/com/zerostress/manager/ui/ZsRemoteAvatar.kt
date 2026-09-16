package com.zerostress.manager.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zerostress.manager.R
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-memory LRU-ish cache for remote avatar bitmaps (bounded to keep RAM sane).
 */
object ZsAvatarCache {
    private const val MAX = 128
    private val map = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
            size > MAX
    }

    fun get(key: String): Bitmap? = synchronized(map) { map[key] }
    fun put(key: String, bmp: Bitmap) = synchronized(map) { map[key] = bmp }
}

private fun cacheKeyFor(url: String): String = try {
    val md = MessageDigest.getInstance("MD5").digest(url.toByteArray())
    md.joinToString("") { "%02x".format(it) }
} catch (_: Exception) {
    url.hashCode().toString()
}

/**
 * Renders a profile image from a URL (e.g. Cloudinary CDN) inside a circular
 * container, with a fallback icon while loading or when the URL is empty.
 * Downloads happen once per URL and are cached in memory.
 */
@Composable
fun ZsRemoteAvatar(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    fallbackRes: Int = R.drawable.ic_menu_person
) {
    val key = url?.takeIf { it.isNotBlank() }?.let { cacheKeyFor(it) }
    var bitmap by remember(key) { mutableStateOf(key?.let { ZsAvatarCache.get(it) }) }

    produceState(initialValue = bitmap, key) {
        if (key == null || ZsAvatarCache.get(key) != null) return@produceState
        val bmp = download(url)
        if (bmp != null) {
            ZsAvatarCache.put(key, bmp)
            value = bmp
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Profile picture",
                modifier = Modifier
                    .size(size)
                    .background(Color(0xFF111827), CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            ZsPngIcon(fallbackRes, size = size * 0.5f, tint = Color(0xFF6B7280))
        }
    }
}

private fun download(url: String): Bitmap? = try {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10000
    conn.readTimeout = 15000
    conn.instanceFollowRedirects = true
    if (conn.responseCode in 200..299) {
        conn.inputStream.use { BitmapFactory.decodeStream(it) }
    } else null
} catch (_: Exception) {
    null
}

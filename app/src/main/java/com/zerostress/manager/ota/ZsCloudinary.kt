package com.zerostress.manager.ota

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Profile-picture hosting via Cloudinary (unsigned uploads).
 *
 * Why Cloudinary: the free tier is permanent (not a trial), supports
 * UNSIGNED uploads so the Android app never holds a secret, returns a
 * global CDN URL per image, and needs zero SDK - a plain HTTP POST from
 * the app. Configure once in Firebase Console -> Remote Config:
 *
 *   cloudinary_cloud_name   = your Cloudinary cloud name
 *   cloudinary_upload_preset= an UNSIGNED preset (Settings > Upload > Presets,
 *                             set Signing Mode = Unsigned, folder "zs_avatars",
 *                             max file size ~2 MB)
 *
 * If either key is blank the feature is silently disabled and the app keeps
 * local avatars, so nothing breaks before you configure it.
 */
object ZsCloudinary {

    private const val TAG = "ZsCloudinary"
    private const val BOUNDARY = "----ZeroStressBoundary7d1a6c"

    /** Human-readable reason for the most recent failure (null after success). */
    @Volatile
    var lastError: String? = null
        private set

    fun cloudName(): String = FirebaseRemoteConfig.getInstance().getString("cloudinary_cloud_name")
    fun uploadPreset(): String = FirebaseRemoteConfig.getInstance().getString("cloudinary_upload_preset")

    /** True when both Remote Config values are set (feature enabled). */
    fun isEnabled(): Boolean = cloudName().isNotBlank() && uploadPreset().isNotBlank()

    /**
     * Uploads a bitmap as JPEG and returns the CDN URL, or null on failure.
     * Runs on the calling thread - call from a background dispatcher.
     */
    fun uploadAvatar(bitmap: Bitmap): String? {
        lastError = null
        if (!isEnabled()) {
            lastError = "cloud not configured"
            Log.w(TAG, "Cloudinary not configured (set cloudinary_cloud_name / cloudinary_upload_preset)")
            return null
        }
        return try {
            // Cap at 512px, JPEG 85 - a few hundred KB per avatar.
            val scaled = scaleDown(bitmap, 512)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val bytes = baos.toByteArray()

            // Build the exact multipart body first, then declare its TRUE size.
            // (The old code over-declared the length by ~250 bytes, so the server
            // waited forever for a tail that never arrived and the upload timed out.)
            val body = buildMultipartBody(bytes)

            val url = "https://api.cloudinary.com/v1_1/${cloudName()}/image/upload"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
            conn.setFixedLengthStreamingMode(body.size)

            conn.outputStream.use { out ->
                out.write(body)
                out.flush()
            }

            val code = conn.responseCode
            val bodyText = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                lastError = "HTTP $code"
                Log.w(TAG, "Cloudinary upload failed: HTTP $code $bodyText")
                return null
            }

            // Extract "secure_url":"..." without a JSON library.
            val marker = "\"secure_url\":\""
            val start = bodyText.indexOf(marker)
            if (start < 0) {
                lastError = "unexpected response"
                Log.w(TAG, "Cloudinary response missing secure_url")
                return null
            }
            val urlStart = start + marker.length
            val end = bodyText.indexOf('"', urlStart)
            if (end < 0) {
                lastError = "unexpected response"
                return null
            }
            val secureUrl = bodyText.substring(urlStart, end)
            // JSON-escaped forward slashes are common; unescape them.
            secureUrl.replace("\\/", "/")
        } catch (e: Exception) {
            lastError = e.message?.take(80) ?: "network error"
            Log.w(TAG, "Cloudinary upload error: ${e.message}")
            null
        }
    }

    /** Builds the complete multipart/form-data payload for an unsigned upload. */
    private fun buildMultipartBody(jpeg: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(jpeg.size + 1024)
        fun s(t: String) = out.write(t.toByteArray(Charsets.UTF_8))
        s("--$BOUNDARY\r\n")
        s("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n")
        s(uploadPreset() + "\r\n")
        s("--$BOUNDARY\r\n")
        s("Content-Disposition: form-data; name=\"file\"; filename=\"avatar.jpg\"\r\n")
        s("Content-Type: image/jpeg\r\n\r\n")
        out.write(jpeg)
        s("\r\n--$BOUNDARY--\r\n")
        return out.toByteArray()
    }

    private fun scaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxSize && h <= maxSize) return bitmap
        val scale = maxSize.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(
            bitmap, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true
        )
    }
}

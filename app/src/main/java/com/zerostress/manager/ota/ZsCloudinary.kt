package com.zerostress.manager.ota

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
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

    fun cloudName(): String = FirebaseRemoteConfig.getInstance().getString("cloudinary_cloud_name")
    fun uploadPreset(): String = FirebaseRemoteConfig.getInstance().getString("cloudinary_upload_preset")

    /** True when both Remote Config values are set (feature enabled). */
    fun isEnabled(): Boolean = cloudName().isNotBlank() && uploadPreset().isNotBlank()

    /**
     * Uploads a bitmap as JPEG and returns the CDN URL, or null on failure.
     * Runs on the calling thread - call from a background dispatcher.
     */
    fun uploadAvatar(bitmap: Bitmap): String? {
        if (!isEnabled()) {
            Log.w(TAG, "Cloudinary not configured (set cloudinary_cloud_name / cloudinary_upload_preset)")
            return null
        }
        return try {
            // Cap at 512px, JPEG 85 - a few hundred KB per avatar.
            val scaled = scaleDown(bitmap, 512)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val bytes = baos.toByteArray()

            val url = "https://api.cloudinary.com/v1_1/${cloudName()}/image/upload"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
            conn.setFixedLengthStreamingMode(
                bytes.size + 512 + uploadPreset().length
            )

            DataOutputStream(conn.outputStream).use { out ->
                out.writeBytes("--$BOUNDARY\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n")
                out.writeBytes(uploadPreset() + "\r\n")
                out.writeBytes("--$BOUNDARY\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"avatar.jpg\"\r\n")
                out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                out.write(bytes)
                out.writeBytes("\r\n--$BOUNDARY--\r\n")
                out.flush()
            }

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                Log.w(TAG, "Cloudinary upload failed: HTTP $code $body")
                return null
            }

            // Extract "secure_url":"..." without a JSON library.
            val marker = "\"secure_url\":\""
            val start = body.indexOf(marker)
            if (start < 0) {
                Log.w(TAG, "Cloudinary response missing secure_url")
                return null
            }
            val urlStart = start + marker.length
            val end = body.indexOf('"', urlStart)
            if (end < 0) return null
            val secureUrl = body.substring(urlStart, end)
            // JSON-escaped forward slashes are common; unescape them.
            secureUrl.replace("\\/", "/")
        } catch (e: Exception) {
            Log.w(TAG, "Cloudinary upload error: ${e.message}")
            null
        }
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

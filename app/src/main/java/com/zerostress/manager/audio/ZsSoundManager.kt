package com.zerostress.manager.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.zerostress.manager.fcm.NotificationPreferences
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Central sound player for the app's event audio.
 *
 * Sounds come from two places:
 *  1. Bundled: app/src/main/res/raw/ (app_start.mp3, login_success.mp3,
 *     register_success.mp3) - always available, used as the default.
 *  2. Remote (OTA): Remote Config keys audio_app_start_url,
 *     audio_login_success_url, audio_register_success_url. When a URL is
 *     set, the MP3 is downloaded once into cacheDir/sounds and reused.
 *     Blank URL = bundled sound. Changing the URL swaps the sound on
 *     every device without shipping a new APK.
 *
 * All playback respects the in-app notification toggle (Settings screen):
 * when the user disables notifications there, in-app sounds are muted too.
 */
object ZsSoundManager {

    private const val TAG = "ZsSoundManager"

    private var loopPlayer: MediaPlayer? = null

    /** True when notifications (and in-app sounds) are switched on in Settings. */
    fun notificationsEnabled(context: Context): Boolean =
        NotificationPreferences.masterEnabled(context)

    // ---------------------------------------------------------------------
    // Remote audio cache
    // ---------------------------------------------------------------------

    /** Cache file for a sound name ("app_start" -> cacheDir/sounds/app_start.mp3). */
    private fun cachedFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "sounds")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$name.mp3")
    }

    /**
     * Resolves a sound to a playable source. Returns the cached/just-downloaded
     * file when a remote URL is configured, or null to fall back to bundled.
     */
    private fun remoteSource(context: Context, name: String, url: String): File? {
        if (url.isBlank()) return null
        val target = cachedFile(context, name)
        // Already downloaded THIS url? Reuse it.
        if (target.exists()) {
            val marker = File(target.parentFile, "$name.url")
            if (try { marker.readText() } catch (_: Exception) { "" } == url) {
                return target
            }
        }
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            conn.connect()
            if (conn.responseCode != 200) {
                conn.disconnect()
                Log.w(TAG, "$name: remote audio HTTP ${conn.responseCode}")
                return null
            }
            val tmp = File(target.parentFile, "$name.tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
            conn.disconnect()
            if (tmp.length() == 0L) return null
            tmp.renameTo(target) || (tmp.copyTo(target, overwrite = true).isFile)
            File(target.parentFile, "$name.url").writeText(url)
            target
        } catch (e: Exception) {
            Log.w(TAG, "$name: remote audio download failed: ${e.message}")
            null
        }
    }

    // ---------------------------------------------------------------------
    // Loading loop
    // ---------------------------------------------------------------------

    /** Starts the looping loading sound (no-op if missing or notifications off). */
    fun startLoadingLoop(context: Context) {
        if (loopPlayer != null) return // already running - keep it seamless
        if (!notificationsEnabled(context)) return
        try {
            val url = try {
                com.zerostress.manager.ota.ZsRemoteConfig.audioAppStartUrl()
            } catch (_: Exception) { "" }

            val player = if (url.isNotBlank()) {
                val f = remoteSource(context, "app_start", url) ?: return
                MediaPlayer.create(context, Uri.parse(f.absolutePath)) ?: return
            } else {
                val resId = context.resources.getIdentifier(
                    "app_start", "raw", context.packageName
                )
                if (resId == 0) return // asset not added yet - stay silent
                MediaPlayer.create(context, resId) ?: return
            }
            player.isLooping = true
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.start()
            loopPlayer = player
        } catch (e: Exception) {
            Log.w(TAG, "startLoadingLoop failed: ${e.message}")
            loopPlayer = null
        }
    }

    /** Keeps the loop alive across screen transitions (re-arms it if it died). */
    fun ensureLoadingLoop(context: Context) {
        val p = loopPlayer
        if (p != null && p.isPlaying) return
        loopPlayer = null
        startLoadingLoop(context)
    }

    /** Stops the loop for good (login complete, dashboard opening, app leaving). */
    fun stopLoadingLoop() {
        try {
            loopPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopLoadingLoop: ${e.message}")
        }
        loopPlayer = null
    }

    /** Pauses without releasing - used by app onPause so it survives the back stack. */
    fun pauseLoadingLoop() {
        try {
            loopPlayer?.let { if (it.isPlaying) it.pause() }
        } catch (e: Exception) {
            Log.w(TAG, "pauseLoadingLoop: ${e.message}")
        }
    }

    // ---------------------------------------------------------------------
    // One-shot jingles
    // ---------------------------------------------------------------------

    /** Plays a one-shot sound: remote file when configured, else bundled raw. */
    private fun playOnce(context: Context, name: String, remoteUrl: String) {
        if (!notificationsEnabled(context)) return
        try {
            val player = if (remoteUrl.isNotBlank()) {
                val f = remoteSource(context, name, remoteUrl) ?: return
                MediaPlayer.create(context, Uri.parse(f.absolutePath)) ?: return
            } else {
                val resId = context.resources.getIdentifier(name, "raw", context.packageName)
                if (resId == 0) return
                MediaPlayer.create(context, resId) ?: return
            }
            player.setOnCompletionListener { it.release() }
            player.start()
        } catch (e: Exception) {
            Log.w(TAG, "playOnce($name) failed: ${e.message}")
        }
    }

    /** Login success - also stops the loading loop (login is now complete). */
    fun playLoginSuccess(context: Context) {
        stopLoadingLoop()
        val url = try {
            com.zerostress.manager.ota.ZsRemoteConfig.audioLoginSuccessUrl()
        } catch (_: Exception) { "" }
        playOnce(context, "login_success", url)
    }

    /** Register success - loop continues because login is not complete yet. */
    fun playRegisterSuccess(context: Context) {
        val url = try {
            com.zerostress.manager.ota.ZsRemoteConfig.audioRegisterSuccessUrl()
        } catch (_: Exception) { "" }
        playOnce(context, "register_success", url)
    }
}

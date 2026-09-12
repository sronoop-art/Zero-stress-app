package com.zerostress.manager.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/**
 * Small helper for one-shot UI sounds (splash intro, login/register success).
 *
 * Audio files live in `app/src/main/res/raw/`:
 *   - app_start.mp3   → played when the splash/loading screen appears
 *   - login_success.mp3 → played after a successful sign-in
 *   - register_success.mp3 → played after a successful account creation
 *
 * Missing files are skipped silently (the helper logs and no-ops), so the app
 * still works if you have not added the audio yet.
 *
 * Keep the files SHORT (2–4 s) and use OGG or MP3 @ 96–128 kbps to stay kind
 * to the APK size.
 */
object ZsSoundManager {

    private const val TAG = "ZsSoundManager"

    /** Resource id of the last created player, used for a tiny single-player cache. */
    private var currentPlayer: MediaPlayer? = null

    fun playAppStart(context: Context) = play(context, "app_start")

    fun playLoginSuccess(context: Context) = play(context, "login_success")

    fun playRegisterSuccess(context: Context) = play(context, "register_success")

    /**
     * Plays `res/raw/<name>.(mp3|ogg|wav)` at medium volume.
     * Safe to call from any thread; failures are logged, never thrown.
     */
    fun play(context: Context, name: String) {
        try {
            val resId = findRawResource(context, name) ?: run {
                Log.w(TAG, "res/raw/$name not found — add the audio file to enable this sound")
                return
            }

            // Stop whatever one-shot sound is still playing so they never overlap.
            stop()

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val player = MediaPlayer.create(context, resId)
            if (player == null) {
                Log.w(TAG, "MediaPlayer.create returned null for $name")
                return
            }
            currentPlayer = player
            player.setAudioAttributes(attrs)
            player.setOnCompletionListener {
                it.release()
                if (currentPlayer === it) currentPlayer = null
            }
            player.start()
        } catch (e: Exception) {
            Log.w(TAG, "play($name) failed: ${e.message}")
        }
    }

    /** Stops the current one-shot sound (e.g. when leaving the screen early). */
    fun stop() {
        try {
            currentPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        }
        currentPlayer = null
    }

    /**
     * res/raw cannot be queried by name with R.raw.* when the file may not
     * exist yet, so resolve the id dynamically (also tolerates .ogg/.wav).
     */
    private fun findRawResource(context: Context, name: String): Int? {
        val res = context.resources
        val id = res.getIdentifier(name, "raw", context.packageName)
        return if (id != 0) id else null
    }
}

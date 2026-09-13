package com.zerostress.manager.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/**
 * Small helper for UI sounds.
 *
 * Two independent channels:
 *  1. One-shot sounds  - splash intro, login/register success (play once, auto-release)
 *  2. Loading loop     - res/raw/app_start.(mp3|ogg|wav) looping forever until auth
 *                        completes (login or register success) or a dashboard opens
 *
 * Audio files live in `app/src/main/res/raw/`:
 *   - app_start.mp3      -> loops on the loading screen + login/register screens
 *   - login_success.mp3  -> played once after a successful sign-in (stops the loop)
 *   - register_success.mp3 -> played once after account creation (stops the loop)
 *
 * Missing files are skipped silently (logged, no crash), so the app builds and
 * runs even before you add the audio files.
 *
 * Keep the files SHORT (2-8 s) and use OGG or MP3 @ 96-128 kbps to stay kind to
 * the APK size. The loop is seamless: MediaPlayer restarts it automatically.
 */
object ZsSoundManager {

    private const val TAG = "ZsSoundManager"

    /** One-shot player (intro jingles, success sounds). */
    private var currentPlayer: MediaPlayer? = null

    /** Looping loading-screen player. Plays until auth completes. */
    private var loopPlayer: MediaPlayer? = null

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    // ---------------------------------------------------------------------
    // Loading loop
    // ---------------------------------------------------------------------

    /**
     * Starts (or resumes) the looping loading-screen audio.
     * Safe to call from multiple screens: if the loop exists but is paused it
     * resumes; it is only created once.
     */
    @JvmStatic
    fun startLoadingLoop(context: Context) {
        try {
            loopPlayer?.let { p ->
                if (!p.isPlaying) {
                    try {
                        p.prepare()
                    } catch (_: Exception) {
                    }
                    p.start()
                }
                return
            }
            val resId = findRawResource(context, "app_start") ?: run {
                Log.w(TAG, "res/raw/app_start not found - add the audio file to enable the loading loop")
                return
            }
            val player = MediaPlayer.create(context, resId)
            if (player == null) {
                Log.w(TAG, "MediaPlayer.create returned null for app_start")
                return
            }
            player.setAudioAttributes(attrs)
            player.isLooping = true
            loopPlayer = player
            player.start()
        } catch (e: Exception) {
            Log.w(TAG, "startLoadingLoop failed: ${e.message}")
        }
    }

    /** Pauses the loading loop (screen paused / app in background). */
    @JvmStatic
    fun pauseLoadingLoop() {
        try {
            loopPlayer?.takeIf { it.isPlaying }?.pause()
        } catch (_: Exception) {
        }
    }

    /** Stops and releases the loading loop (login complete / entering dashboard). */
    @JvmStatic
    fun stopLoadingLoop() {
        try {
            loopPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        }
        loopPlayer = null
    }

    /** True while the loading loop is playing. */
    val isLoadingLoopRunning: Boolean
        get() = loopPlayer != null

    // ---------------------------------------------------------------------
    // One-shot sounds
    // ---------------------------------------------------------------------

    /** Login complete - the loading loop must stop now, then the jingle plays. */
    fun playLoginSuccess(context: Context) {
        stopLoadingLoop()
        play(context, "login_success")
    }

    /**
     * Account created - the jingle plays over the loop. The loop keeps going
     * because the player still has to sign in (login not complete yet).
     */
    fun playRegisterSuccess(context: Context) {
        play(context, "register_success")
    }

    /** Plays `res/raw/<name>.(mp3|ogg|wav)` once at medium volume. Never throws. */
    fun play(context: Context, name: String) {
        try {
            val resId = findRawResource(context, name) ?: run {
                Log.w(TAG, "res/raw/$name not found - add the audio file to enable this sound")
                return
            }

            // Stop whatever one-shot sound is still playing so they never overlap.
            stop()

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
    @JvmStatic
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

    /** Stops everything - one-shots and the loading loop. */
    @JvmStatic
    fun stopAll() {
        stop()
        stopLoadingLoop()
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

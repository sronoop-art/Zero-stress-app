package com.zerostress.manager.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.zerostress.manager.fcm.NotificationPreferences

/**
 * Central sound player for the app's event audio.
 *
 * Files live in app/src/main/res/raw/ (see README-AUDIO.md):
 *   app_start.mp3        - loading loop (splash + login + register)
 *   login_success.mp3    - sign-in success
 *   register_success.mp3 - account created
 *
 * All playback respects the in-app notification toggle stored by
 * NotificationPreferences (Settings screen): when the user disables
 * notifications there, in-app sounds are muted too. Push notifications are
 * gated separately inside ZSFCMService.
 */
object ZsSoundManager {

    private const val TAG = "ZsSoundManager"

    private var loopPlayer: MediaPlayer? = null

    /** True when notifications (and in-app sounds) are switched on in Settings. */
    fun notificationsEnabled(context: Context): Boolean =
        NotificationPreferences.masterEnabled(context)

    // ---------------------------------------------------------------------
    // Loading loop
    // ---------------------------------------------------------------------

    /** Starts the looping app_start.mp3 (no-op if missing or notifications off). */
    fun startLoadingLoop(context: Context) {
        if (loopPlayer != null) return // already running - keep it seamless
        if (!notificationsEnabled(context)) return
        try {
            val resId = context.resources.getIdentifier(
                "app_start", "raw", context.packageName
            )
            if (resId == 0) return // asset not added yet - stay silent

            val player = MediaPlayer.create(context, resId) ?: return
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

    /** Plays a one-shot raw resource; silently does nothing when unavailable. */
    private fun playOnce(context: Context, name: String) {
        if (!notificationsEnabled(context)) return
        try {
            val resId = context.resources.getIdentifier(name, "raw", context.packageName)
            if (resId == 0) return
            val player = MediaPlayer.create(context, resId) ?: return
            player.setOnCompletionListener { it.release() }
            player.start()
        } catch (e: Exception) {
            Log.w(TAG, "playOnce($name) failed: ${e.message}")
        }
    }

    /** Login success - also stops the loading loop (login is now complete). */
    fun playLoginSuccess(context: Context) {
        stopLoadingLoop()
        playOnce(context, "login_success")
    }

    /** Register success - loop continues because login is not complete yet. */
    fun playRegisterSuccess(context: Context) {
        playOnce(context, "register_success")
    }
}

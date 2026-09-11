package com.zerostress.manager.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zerostress.manager.R

/**
 * Foreground service that keeps the mic + Agora connection alive while the
 * VoiceActivity is in the background (or the screen is off).
 *
 * Started by [com.zerostress.manager.VoiceActivity] when a channel is joined,
 * stopped when the user leaves. Without it Android suspends the app a few
 * seconds after backgrounding and remote users hear dead air until the user
 * reopens the app.
 *
 * Uses MIC-type foreground service (required on Android 11+/14+ to access the
 * mic while backgrounded — RECORD_AUDIO + FOREGROUND_SERVICE_MICROPHONE).
 */
class VoiceForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "voice_call"
        private const val NOTIFICATION_ID = 4242
        private const val ACTION_START = "com.zerostress.manager.voice.START"
        private const val ACTION_STOP = "com.zerostress.manager.voice.STOP"

        /** Call this right after AgoraVoiceManager.join(...) succeeds. */
        fun start(context: Context, channelName: String) {
            val intent = Intent(context, VoiceForegroundService::class.java).apply {
                action = ACTION_START
                putExtra("channel", channelName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Call this in onDestroy / when leaving the channel. */
        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Voice call",
                NotificationManager.IMPORTANCE_LOW
            )
            ch.description = "Shown while you are connected to a voice channel"
            nm.createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val channel = intent?.getStringExtra("channel") ?: "voice"
        startForeground(NOTIFICATION_ID, buildNotification(channel))
        // START_STICKY makes Android restart the service if the system kills it,
        // so the notification (and mic access) comes back automatically.
        return START_STICKY
    }

    private fun buildNotification(channelName: String): Notification {
        val text = "Connected to $channelName — tap to return to the call"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ZERO STRESS voice")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // If the service dies (task removed, system kill) make sure the Agora
        // engine leaves the channel so remote users see the participant leave
        // instead of a frozen "in call" ghost.
        AgoraVoiceManager.leave()
        super.onDestroy()
    }
}

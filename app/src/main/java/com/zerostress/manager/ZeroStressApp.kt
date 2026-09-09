package com.zerostress.manager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class ZeroStressApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        setupAppCheck()
        createNotificationChannels()
    }

    private fun setupAppCheck() {
        try {
            val appCheck = FirebaseAppCheck.getInstance()
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            appCheck.setTokenAutoRefreshEnabled(true)

            appCheck.getToken(false)
                .addOnSuccessListener { result ->
                    Log.w(TAG, "APP CHECK DEBUG TOKEN: ${result.token}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "App Check token failed: ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "App Check setup failed: ${e.message}", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mainChannel = NotificationChannel(
                CHANNEL_ID, "ZERO STRESS Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Match updates, announcements, and alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
            }

            val chatChannel = NotificationChannel(
                CHAT_CHANNEL_ID, "Team Chat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat messages from your squad"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100)
                setShowBadge(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(mainChannel)
            manager?.createNotificationChannel(chatChannel)
        }
    }

    companion object {
        private const val TAG = "ZeroStressApp"
        const val CHANNEL_ID = "zs_notifications"
        const val CHAT_CHANNEL_ID = "zs_chat"
    }
}
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
        setupFirestoreOfflineCache()
    }

    /**
     * Persistent Firestore cache with a size cap: already-loaded screens
     * (chat, dashboards, rosters) render instantly from cache on launch and
     * while offline instead of showing empty state - the visual half of the
     * "lag/network" fix. The default cache is already persistent; this only
     * bounds it so it never grows unbounded on small devices.
     */
    private fun setupFirestoreOfflineCache() {
        try {
            val cache = com.google.firebase.firestore.PersistentCacheSettings
                .newBuilder()
                .setSizeBytes(100L * 1024 * 1024) // 100 MB cap
                .build()
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings
                .Builder()
                .setLocalCacheSettings(cache)
                .build()
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .firestoreSettings = settings
        } catch (e: Exception) {
            Log.w(TAG, "Firestore cache setup skipped: ${e.message}")
        }
    }

    private fun setupAppCheck() {
        try {
            val appCheck = FirebaseAppCheck.getInstance()
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            appCheck.setTokenAutoRefreshEnabled(true)

            appCheck.getToken(false)
                .addOnSuccessListener { result ->
                    Log.w(TAG, "APP CHECK DEBUG TOKEN: ${result.token}")
                    Log.w(TAG, "Copy this token into Firebase Console > App Check > Debug provider tokens, or Auth/Firestore calls may be rejected")
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

            val scheduleChannel = NotificationChannel(
                SCHEDULE_CHANNEL_ID, "Match Schedule",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Upcoming match reminders and schedule changes"
                enableVibration(true)
                setShowBadge(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(mainChannel)
            manager?.createNotificationChannel(chatChannel)
            manager?.createNotificationChannel(scheduleChannel)
        }
    }

    companion object {
        private const val TAG = "ZeroStressApp"
        const val CHANNEL_ID = "zs_notifications"
        const val CHAT_CHANNEL_ID = "zs_chat"
        const val SCHEDULE_CHANNEL_ID = "zs_schedule"
    }
}
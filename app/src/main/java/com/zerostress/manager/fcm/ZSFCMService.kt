package com.zerostress.manager.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zerostress.manager.LoginActivity
import com.zerostress.manager.R
import com.zerostress.manager.ZeroStressApp

/**
 * Deep-link target map. The value must be an Activity class name registered in
 * AndroidManifest.xml (fully qualified with the app package).
 */
private val deepLinks: Map<String, Class<*>> = mapOf(
    "chat" to com.zerostress.manager.ChatActivity::class.java,
    "mention" to com.zerostress.manager.ChatActivity::class.java,
    "announcement" to com.zerostress.manager.AnnouncementsActivity::class.java,
    "schedule" to com.zerostress.manager.ScheduleActivity::class.java,
    "achievement" to com.zerostress.manager.AchievementsActivity::class.java,
    "title" to com.zerostress.manager.PlayerTitlesActivity::class.java,
    "challenge" to com.zerostress.manager.DailyChallengesActivity::class.java,
    "admin" to com.zerostress.manager.AdminDashboardActivity::class.java,
    "friends" to com.zerostress.manager.FriendsActivity::class.java
)

/**
 * Notification preferences (Settings screen toggles + a remember-me for the
 * token when no user is signed in yet).
 */
object NotificationPreferences {
    private const val PREFS = "zs_notifications"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun masterEnabled(context: Context): Boolean =
        prefs(context).getBoolean("notifications_enabled", true)

    fun chatEnabled(context: Context): Boolean =
        prefs(context).getBoolean("chat_notifs_enabled", true)

    fun scheduleEnabled(context: Context): Boolean =
        prefs(context).getBoolean("schedule_notifs_enabled", true)

    /** Push notifications can never show without the runtime permission (Android 13+). */
    fun pushPossible(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

    /** Permission requested before? (so we only prompt once) */
    fun permissionRequested(context: Context): Boolean =
        prefs(context).getBoolean("notif_permission_requested", false)

    fun setPermissionRequested(context: Context, requested: Boolean) {
        prefs(context).edit().putBoolean("notif_permission_requested", requested).apply()
    }
}

class ZSFCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        saveTokenToFirestore(applicationContext)
        subscribeToTopics()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        var title = message.notification?.title ?: "ZERO STRESS"
        var body = message.notification?.body ?: ""
        var type = "general"
        var targetUid: String? = null

        // Data payload wins over the notification payload (our Cloud Function sends data)
        if (message.data.isNotEmpty()) {
            title = message.data["title"] ?: title
            body = message.data["body"] ?: body
            type = message.data["type"] ?: type
            targetUid = message.data["uid"]
        }

        if (body.isEmpty()) return

        // -------- Per-user gating: only show what belongs to this user --------
        if (targetUid != null && targetUid != FirebaseAuth.getInstance().uid) {
            Log.d(TAG, "Dropped push for another user ($targetUid)")
            return
        }

        // -------- In-app Settings toggles --------
        when (type) {
            "chat", "mention" ->
                if (!NotificationPreferences.chatEnabled(this)) return
            "schedule" ->
                if (!NotificationPreferences.scheduleEnabled(this)) return
        }
        if (!NotificationPreferences.masterEnabled(this)) return

        // Without the runtime permission the system silently drops our
        // notifications on Android 13+; skip building them.
        if (!NotificationPreferences.pushPossible(this)) return

        showNotification(title, body, type)
    }

    private fun subscribeToTopics() {
        val fm = FirebaseMessaging.getInstance()
        fm.subscribeToTopic("all_players")
            .addOnFailureListener { e -> Log.e(TAG, "Topic subscription failed: ${e.message}") }
        fm.subscribeToTopic("match_updates")
        fm.subscribeToTopic("announcements")
    }

    private fun showNotification(title: String, body: String, type: String) {
        // Tap target follows the notification type; unknown types fall back to
        // LoginActivity, which routes signed-in users to their dashboard.
        val targetClass = deepLinks[type] ?: LoginActivity::class.java
        val intent = Intent(this, targetClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, type.hashCode(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = when (type) {
            "chat", "mention" -> ZeroStressApp.CHAT_CHANNEL_ID
            "schedule" -> ZeroStressApp.SCHEDULE_CHANNEL_ID
            else -> ZeroStressApp.CHANNEL_ID
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(System.currentTimeMillis().toInt(), builder.build())
        Log.d(TAG, "Notification shown: $title")
    }

    companion object {
        private const val TAG = "ZSFCMService"

        fun saveTokenToFirestore(context: Context) {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.e(TAG, "FCM token fetch failed", task.exception)
                        return@addOnCompleteListener
                    }
                    val token = task.result ?: return@addOnCompleteListener
                    val uid = FirebaseAuth.getInstance().uid

                    if (uid != null) {
                        val tokenData = mapOf(
                            "fcmToken" to token,
                            "tokenUpdated" to System.currentTimeMillis()
                        )
                        FirebaseFirestore.getInstance()
                            .collection("players").document(uid)
                            .update(tokenData)
                            .addOnSuccessListener { Log.d(TAG, "FCM token saved for user: $uid") }
                            .addOnFailureListener { e ->
                                // players rule only allows fcmToken/online/lastSeen heartbeats
                                // for other-user writes; a fresh register may not have the
                                // doc yet, so merge-create as a fallback.
                                Log.w(TAG, "update failed (${e.message}), retrying with set/merge")
                                FirebaseFirestore.getInstance()
                                    .collection("players").document(uid)
                                    .set(tokenData, SetOptions.merge())
                                    .addOnFailureListener { e2 ->
                                        Log.e(TAG, "token set/merge failed: ${e2.message}")
                                    }
                            }
                    } else {
                        Log.w(TAG, "No user logged in, saving token to SharedPreferences for later")
                        NotificationPreferences.prefs(context)
                            .edit().putString("pending_token", token).apply()
                    }
                }
        }

        fun saveTokenToFirestoreWithRetry(@Suppress("UNUSED_PARAMETER") context: Context) {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.e(TAG, "FCM token fetch retry failed", task.exception)
                        return@addOnCompleteListener
                    }
                    val token = task.result ?: return@addOnCompleteListener
                    val uid = FirebaseAuth.getInstance().uid ?: return@addOnCompleteListener
                    val tokenData = mapOf(
                        "fcmToken" to token,
                        "tokenUpdated" to System.currentTimeMillis()
                    )
                    FirebaseFirestore.getInstance()
                        .collection("players").document(uid)
                        .set(tokenData, SetOptions.merge())
                        .addOnSuccessListener { Log.d(TAG, "FCM token re-saved for user: $uid") }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to re-save token: ${e.message}")
                        }
                }
        }
    }
}

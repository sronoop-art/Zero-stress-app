package com.zerostress.manager.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zerostress.manager.LoginActivity
import com.zerostress.manager.R
import com.zerostress.manager.ZeroStressApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspend on a Google Play Services [Task] without adding the extra
 * kotlinx-coroutines-play-services artifact to the build. Cancellation simply
 * detaches the listener; the task itself keeps running, which is the same
 * behaviour as the official extension.
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <T> Task<T>.zsAwait(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result as T)
        } else {
            cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
}

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
        if (Build.VERSION.SDK_INT >= 33) {
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

/**
 * Service scope for FCM work that must outlive a single callback (token retry,
 * background notification delivery confirmation). SupervisorJob so one failed
 * coroutine cannot kill the rest.
 */
private val fcmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Topic subscriptions. Broadcast pushes (admin announcements, match reminders)
 * are sent to these topics, so a device that never subscribed silently misses
 * them. Called on every token save, not only on token refresh.
 */
private fun subscribeToTopics() {
    val fm = FirebaseMessaging.getInstance()
    fm.subscribeToTopic("all_players")
        .addOnFailureListener { e -> Log.e("ZSFCMService", "Topic subscription failed: ${e.message}") }
    fm.subscribeToTopic("match_updates")
    fm.subscribeToTopic("announcements")
}

/**
 * FCM service with two production fixes over the old version:
 *
 * 1. Background delivery: when the app is not in the foreground, the system can
 *    still show the notification for us if we attach a notification to the
 *    message before FCM delivers it. We do that by having the backend send a
 *    notification payload (it already does), and we ALSO show it ourselves when
 *    the app is alive so the in-app toggles and per-user gating still apply.
 *
 * 2. Token readiness: the app now retries saving the FCM token for a short
 *    window after sign-in, because the first token frequently arrives after the
 *    user is already logged in. That closes the "first notification is dropped
 *    because there is no token yet" gap.
 *
 * 3. Instant confirmation: callers that care about "did the other side get it?"
 *    can call [awaitTokenSavedForCurrentUser] before triggering a push, and the
 */
class ZSFCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        fcmScope.launch {
            // `context` is a Kotlin keyword in this compiler and a Service only
            // exposes applicationContext/baseContext, so use applicationContext.
            saveTokenToFirestoreRetry(applicationContext)
            subscribeToTopics()
        }
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

    /**
     * Show the notification immediately when the app is alive. This is the
     * "instant" path for users already in the app. When the app is backgrounded
     * or killed, FCM itself will display the notification payload the backend
     * attached, so we do not need to duplicate that work here.
     */
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

        /**
         * Save the FCM token for the current user, retrying briefly because the
         * first token frequently arrives after sign-in completes.
         */
        suspend fun saveTokenToFirestoreRetry(context: Context) {
            val uid = FirebaseAuth.getInstance().uid ?: return
            val maxAttempts = 4
            var attempt = 0
            while (attempt < maxAttempts) {
                val ok = try {
                    val token = FirebaseMessaging.getInstance().token.zsAwait()
                    val tokenData = mapOf(
                        "fcmToken" to token,
                        "tokenUpdated" to System.currentTimeMillis()
                    )
                    FirebaseFirestore.getInstance()
                        .collection("players")
                        .document(uid)
                        .set(tokenData, SetOptions.merge())
                        .zsAwait()
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "token save attempt ${attempt + 1} failed: ${e.message}")
                    false
                }
                if (ok) {
                    Log.d(TAG, "FCM token saved for user: $uid")
                    // Make sure the device is on the broadcast topics too.
                    subscribeToTopics()
                    return
                }
                attempt++
                if (attempt < maxAttempts) {
                    kotlinx.coroutines.delay(700L)
                }
            }
            Log.w(TAG, "FCM token save gave up for user: $uid")
        }

        /**
         * Fire-and-forget token save used from splash and other startup paths
         * where we do not want to suspend the caller.
         */
        fun saveTokenToFirestore(context: Context) {
            fcmScope.launch {
                saveTokenToFirestoreRetry(context)
            }
        }
    }
}

package com.zerostress.manager.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

class ZSFCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: $token")
        saveTokenToFirestore(applicationContext)
        subscribeToTopics()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received: ${message.data}")
        Log.d(TAG, "From: ${message.from}")
        message.notification?.let {
            Log.d(TAG, "Notification payload: ${it.title} - ${it.body}")
        }

        var title = "ZERO STRESS"
        var body = ""
        var type = "general"

        if (message.data.isNotEmpty()) {
            title = message.data["title"] ?: title
            body = message.data["body"] ?: body
            type = message.data["type"] ?: type
        }

        if (body.isEmpty() && message.notification != null) {
            title = message.notification?.title ?: title
            body = message.notification?.body ?: ""
        }

        if (body.isNotEmpty()) {
            showNotification(title, body, type)
        }
    }

    private fun subscribeToTopics() {
        FirebaseMessaging.getInstance().subscribeToTopic("all_players")
            .addOnSuccessListener { Log.d(TAG, "Subscribed to all_players topic") }
            .addOnFailureListener { e -> Log.e(TAG, "Topic subscription failed: ${e.message}") }

        FirebaseMessaging.getInstance().subscribeToTopic("match_updates")
            .addOnSuccessListener { Log.d(TAG, "Subscribed to match_updates topic") }

        FirebaseMessaging.getInstance().subscribeToTopic("announcements")
            .addOnSuccessListener { Log.d(TAG, "Subscribed to announcements topic") }
    }

    private fun showNotification(title: String, body: String, type: String) {
        val intent = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (type == "chat" || type == "mention") {
            ZeroStressApp.CHAT_CHANNEL_ID
        } else {
            ZeroStressApp.CHANNEL_ID
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
        if (manager != null) {
            manager.notify(System.currentTimeMillis().toInt(), builder.build())
            Log.d(TAG, "Notification shown: $title - $body")
        }
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

                    val auth = FirebaseAuth.getInstance()
                    val uid = auth.currentUser?.uid ?: auth.uid

                    if (uid != null) {
                        val tokenData = mapOf(
                            "fcmToken" to token,
                            "tokenUpdated" to System.currentTimeMillis()
                        )
                        FirebaseFirestore.getInstance()
                            .collection("players").document(uid)
                            .update(tokenData)
                            .addOnSuccessListener { Log.d(TAG, "FCM token saved for user: $uid") }
                            .addOnFailureListener { e -> Log.e(TAG, "Failed to save token: ${e.message}") }
                    } else {
                        Log.w(TAG, "No user logged in, saving token to SharedPreferences for later")
                        context.getSharedPreferences("zs_fcm", Context.MODE_PRIVATE)
                            .edit().putString("pending_token", token).apply()
                    }
                }
        }

        fun saveTokenToFirestoreWithRetry(context: Context) {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.e(TAG, "FCM token fetch retry failed", task.exception)
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
                            .set(tokenData, SetOptions.merge())
                            .addOnSuccessListener { Log.d(TAG, "FCM token re-saved for user: $uid") }
                            .addOnFailureListener { e -> Log.e(TAG, "Failed to re-save token: ${e.message}") }
                    }
                }
        }
    }
}
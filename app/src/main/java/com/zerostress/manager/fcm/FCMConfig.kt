package com.zerostress.manager.fcm

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

object FCMConfig {
    private const val TAG = "FCMConfig"

    fun checkFCMConfiguration(activity: Activity) {
        Log.d(TAG, "=== FCM Configuration Check ===")

        try {
            val app = FirebaseApp.getInstance()
            Log.d(TAG, "FirebaseApp initialized: ${app != null}")
            Log.d(TAG, "FirebaseApp name: ${app?.name ?: "null"}")
            Log.d(TAG, "FirebaseApp options: ${app?.options ?: "null"}")
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseApp not initialized: ${e.message}")
        }

        try {
            val uid = FirebaseAuth.getInstance().uid
            Log.d(TAG, "FirebaseAuth current user UID: ${uid ?: "null (not logged in)"}")
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseAuth error: ${e.message}")
        }

        try {
            val token = FirebaseMessaging.getInstance().token.result
            val tokenPreview = token?.take(20)
            Log.d(TAG, "FCM token: ${if (token != null) "present ($tokenPreview...)" else "null"}")
            Log.d(TAG, "FCM token length: ${token?.length ?: 0}")
        } catch (e: Exception) {
            Log.e(TAG, "FCM token fetch failed: ${e.message}")
        }

        val notificationPermission = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        Log.d(
            TAG,
            "POST_NOTIFICATIONS permission: " +
                if (notificationPermission == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
        )

        Log.d(TAG, "=== End FCM Configuration Check ===")
    }
}
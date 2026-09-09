package com.zerostress.manager

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object OnOnlineStatusHelper {
    private const val TAG = "OnOnlineStatusHelper"

    fun updateOnlineStatus(isOnline: Boolean) {
        val uid = FirebaseAuth.getInstance().uid ?: return

        val updates = mutableMapOf<String, Any>("isOnline" to isOnline)
        if (isOnline) {
            updates["lastSeen"] = System.currentTimeMillis()
        }

        FirebaseFirestore.getInstance().collection("players").document(uid)
            .update(updates)
            .addOnSuccessListener { Log.d(TAG, "Online status updated: $isOnline") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to update online status", e) }
    }
}
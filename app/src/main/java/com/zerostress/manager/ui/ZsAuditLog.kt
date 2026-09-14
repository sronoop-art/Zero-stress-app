package com.zerostress.manager.ui

import com.google.firebase.firestore.FirebaseFirestore

/**
 * Append-only audit trail of admin actions, stored in `moderation_log`.
 *
 * Rules allow only admins to create entries (with their own uid) and nobody
 * can edit or delete them - so the history stays trustworthy. Visible in
 * Firebase Console -> Firestore -> moderation_log.
 *
 * Fire-and-forget: logging never blocks the admin action it records.
 */
object ZsAuditLog {

    data class Entry(
        val action: String,      // e.g. "approve_player", "set_role", "ban"
        val targetUid: String? = null,
        val targetName: String? = null,
        val details: String? = null
    )

    fun log(entry: Entry) {
        val adminId = com.google.firebase.auth.FirebaseAuth.getInstance().uid ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("moderation_log").add(
            mapOf(
                "adminId" to adminId,
                "action" to entry.action,
                "targetUid" to entry.targetUid,
                "targetName" to entry.targetName,
                "details" to entry.details,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    /** Convenience for player-management actions. */
    fun playerAction(action: String, uid: String, name: String?, details: String? = null) =
        log(Entry(action, uid, name, details))
}

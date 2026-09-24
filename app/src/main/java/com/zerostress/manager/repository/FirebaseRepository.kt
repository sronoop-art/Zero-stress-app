package com.zerostress.manager.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.messaging.FirebaseMessaging
import com.zerostress.manager.models.Achievement
import com.zerostress.manager.models.Announcement
import com.zerostress.manager.models.ChatMessage
import com.zerostress.manager.models.FriendRequest
import com.zerostress.manager.models.MatchLog
import com.zerostress.manager.models.MatchSchedule
import com.zerostress.manager.models.Player
import com.zerostress.manager.models.VoiceChannel

/** Simple two-method callback used across [FirebaseRepository]. */
interface OnResultCallback<T> {
    fun onSuccess(result: T)
    fun onFailure(e: Exception?)
}

object FirebaseRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val playersRef = db.collection("players")
    private val matchLogsRef = db.collection("match_logs")
    private val announcementsRef = db.collection("announcements")
    private val chatRef = db.collection("chat_messages")
    private val schedulesRef = db.collection("match_schedules")
    private val friendsRef = db.collection("friendships")
    private val friendRequestsRef = db.collection("friend_requests")
    private val achievementsRef = db.collection("player_achievements")
    private val seasonsRef = db.collection("seasons")
    private val voiceChannelsRef = db.collection("voice_channels")

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun updateFcmToken() {
        val userId = getCurrentUserId() ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                playersRef.document(userId).update("fcmToken", token)
            }
        }
    }

    // --- Players ---
    fun createPlayer(player: Player, callback: OnResultCallback<Void?>) {
        playersRef.document(player.id ?: "").set(player.toMap())
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    fun getPlayer(userId: String, callback: OnResultCallback<Player?>) {
        playersRef.document(userId).get()
            .addOnSuccessListener { doc -> callback.onSuccess(doc.toObject(Player::class.java)) }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    fun updatePlayerStatus(playerId: String, status: String, callback: OnResultCallback<Void?>) {
        playersRef.document(playerId).update("status", status)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    fun updatePlayerRole(playerId: String, role: String, callback: OnResultCallback<Void?>) {
        playersRef.document(playerId).update("role", role)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    fun resetPlayerPassword(playerId: String, callback: OnResultCallback<Void?>) {
        playersRef.document(playerId).update("passwordResetPending", true)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    fun getAllPlayers(callback: OnResultCallback<List<Player>>) {
        // status filter keeps pending/banned players out of rosters; sorting is
        // done client-side by callers that need a specific order.
        playersRef.whereEqualTo("status", "approved")
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) { callback.onFailure(e); return@addSnapshotListener }
                val list = snap.documents.mapNotNull { it.toObject(Player::class.java) }
                callback.onSuccess(list)
            }
    }

    fun getApprovedPlayers(callback: OnResultCallback<List<Player>>) {
        playersRef.whereEqualTo("status", "approved")
            .orderBy("score", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) { callback.onFailure(e); return@addSnapshotListener }
                val list = snap.documents.mapNotNull { it.toObject(Player::class.java) }
                callback.onSuccess(list)
            }
    }

    fun getPendingPlayers(callback: OnResultCallback<List<Player>>) {
        playersRef.whereEqualTo("status", "pending")
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) { callback.onFailure(e); return@addSnapshotListener }
                val list = snap.documents.mapNotNull { it.toObject(Player::class.java) }
                callback.onSuccess(list)
            }
    }

    // --- Match Logs ---
    /**
     * Writes a match log and rolls its stats into the player doc in ONE
     * transaction using the shared ZsScore rules - matching the admin
     * DailyInput path exactly (the old version recomputed lifetime score with
     * a stale formula from lifetime totals and used a racy read-modify-write).
     */
    fun addMatchLog(log: MatchLog, callback: OnResultCallback<Void?>) {
        val id = matchLogsRef.document().id
        log.id = id
        val entryScore = com.zerostress.manager.ZsScore.entryScore(log.kills, log.damage, log.win)
        // Written as an explicit map rather than set(log): MatchLog.getScore()
        // is a function, not a property, so Firestore never serialized it and
        // every performance chart read a missing "score" as 0.
        matchLogsRef.document(id).set(
            mapOf(
                "id" to id,
                "playerId" to (log.playerId ?: ""),
                "playerName" to (log.playerName ?: ""),
                "kills" to log.kills,
                "deaths" to log.deaths,
                "assists" to log.assists,
                "damage" to log.damage,
                "win" to log.win,
                "matchType" to (log.matchType ?: "Casual"),
                "date" to log.date,
                "score" to entryScore
            )
        )
            .addOnSuccessListener {
                val playerRef = playersRef.document(log.playerId ?: "")
                db.runTransaction { tx ->
                    val snap = tx.get(playerRef)
                    if (!snap.exists()) return@runTransaction null

                    val xpGained = com.zerostress.manager.ZsScore.entryXp(log.kills, log.damage, log.win)
                    var newXp = (snap.getLong("xp") ?: 0) + xpGained
                    var newLevel = (snap.getLong("level") ?: 1).toInt()
                    while (newXp >= Player.xpForLevel(newLevel)) {
                        newXp -= Player.xpForLevel(newLevel)
                        newLevel++
                    }
                    val newScore = (snap.getLong("score") ?: 0) + entryScore

                    tx.update(
                        playerRef,
                        mapOf<String, Any>(
                            "kills" to com.google.firebase.firestore.FieldValue.increment(log.kills.toLong()),
                            "deaths" to com.google.firebase.firestore.FieldValue.increment(log.deaths.toLong()),
                            "assists" to com.google.firebase.firestore.FieldValue.increment(log.assists.toLong()),
                            "damage" to com.google.firebase.firestore.FieldValue.increment(log.damage),
                            "wins" to com.google.firebase.firestore.FieldValue.increment(if (log.win) 1L else 0L),
                            "matches" to com.google.firebase.firestore.FieldValue.increment(1L),
                            "score" to com.google.firebase.firestore.FieldValue.increment(entryScore),
                            "rank" to com.zerostress.manager.ZsScore.rankFor(newScore),
                            "coins" to com.google.firebase.firestore.FieldValue.increment(
                                com.zerostress.manager.ZsScore.entryCoins(log.kills, log.win)
                            ),
                            "xp" to newXp,
                            "level" to newLevel.toLong(),
                            "updatedat" to System.currentTimeMillis()
                        )
                    )
                    null
                }
                    .addOnSuccessListener { callback.onSuccess(null) }
                    .addOnFailureListener { e -> callback.onFailure(e) }
            }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    fun getPlayerMatchLogs(playerId: String, callback: OnResultCallback<List<MatchLog>>) {
        matchLogsRef.whereEqualTo("playerId", playerId)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) { callback.onFailure(e); return@addSnapshotListener }
                val list = snap.documents.mapNotNull { it.toObject(MatchLog::class.java) }
                callback.onSuccess(list)
            }
    }

    fun resetAllPlayerData(callback: OnResultCallback<Void?>) {
        playersRef.get().addOnSuccessListener { snap ->
            val batch: WriteBatch = db.batch()
            for (doc in snap.documents) {
                val reset = mapOf(
                    "kills" to 0,
                    "damage" to 0,
                    "wins" to 0,
                    "matches" to 0,
                    "score" to 0L,
                    "rank" to "Iron",
                    "xp" to 0,
                    "level" to 1
                )
                batch.update(doc.reference, reset)
            }
            batch.commit()
                .addOnSuccessListener { callback.onSuccess(null) }
                .addOnFailureListener { e -> callback.onFailure(e) }
        }.addOnFailureListener { e -> callback.onFailure(e) }
    }

    // --- Announcements ---
    fun createAnnouncement(a: Announcement, callback: OnResultCallback<Void?>) {
        val id = announcementsRef.document().id
        a.id = id
        announcementsRef.document(id).set(a)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    fun getAnnouncements(callback: OnResultCallback<List<Announcement>>) {
        announcementsRef.orderBy("timestamp", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) { callback.onFailure(e); return@addSnapshotListener }
                val list = snap.documents.mapNotNull { it.toObject(Announcement::class.java) }
                callback.onSuccess(list)
            }
    }

    fun deleteAnnouncement(id: String, callback: OnResultCallback<Void?>) {
        announcementsRef.document(id).delete()
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    // --- Chat ---
    fun getChatMessages(callback: OnResultCallback<List<ChatMessage>>) {
        chatRef.orderBy("timestamp", Query.Direction.ASCENDING).limit(200)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) { callback.onFailure(e); return@addSnapshotListener }
                val list = snap.documents.mapNotNull { it.toObject(ChatMessage::class.java) }
                    .filter { !it.deleted }
                callback.onSuccess(list)
            }
    }

    fun sendChatMessage(msg: ChatMessage, callback: OnResultCallback<Void?>) {
        val id = chatRef.document().id
        msg.id = id
        chatRef.document(id).set(msg)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    fun deleteChatMessage(messageId: String, callback: OnResultCallback<Void?>) {
        chatRef.document(messageId).update("deleted", true)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    fun clearAllChats(callback: OnResultCallback<Void?>) {
        chatRef.get().addOnSuccessListener { snap ->
            val batch: WriteBatch = db.batch()
            for (doc in snap.documents) batch.delete(doc.reference)
            batch.commit()
                .addOnSuccessListener { callback.onSuccess(null) }
                .addOnFailureListener { e -> callback.onFailure(e) }
        }.addOnFailureListener { e -> callback.onFailure(e) }
    }

    // --- Schedules ---
    fun createSchedule(s: MatchSchedule, callback: OnResultCallback<Void?>) {
        val id = schedulesRef.document().id
        s.id = id
        schedulesRef.document(id).set(s)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    fun getSchedules(callback: OnResultCallback<List<MatchSchedule>>) {
        schedulesRef.orderBy("matchTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) { callback.onFailure(e); return@addSnapshotListener }
                val list = snap.documents.mapNotNull { it.toObject(MatchSchedule::class.java) }
                callback.onSuccess(list)
            }
    }

    fun deleteSchedule(id: String, callback: OnResultCallback<Void?>) {
        schedulesRef.document(id).delete()
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    // --- Friends ---
    fun sendFriendRequest(req: FriendRequest, callback: OnResultCallback<Void?>) {
        val id = friendRequestsRef.document().id
        req.id = id
        friendRequestsRef.document(id).set(req)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    fun getFriendRequests(userId: String, callback: OnResultCallback<List<FriendRequest>>) {
        friendRequestsRef.whereEqualTo("toUserId", userId).whereEqualTo("status", "pending")
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) { callback.onFailure(e); return@addSnapshotListener }
                val list = snap.documents.mapNotNull { it.toObject(FriendRequest::class.java) }
                callback.onSuccess(list)
            }
    }

    fun respondFriendRequest(request: FriendRequest, accept: Boolean, callback: OnResultCallback<Void?>) {
        friendRequestsRef.document(request.id ?: "").update("status", if (accept) "accepted" else "rejected")
            .addOnSuccessListener {
                if (accept) {
                    val friendship = mapOf(
                        "userId1" to request.fromUserId,
                        "userId2" to request.toUserId
                    )
                    friendsRef.document().set(friendship)
                        .addOnSuccessListener { callback.onSuccess(null) }
                        .addOnFailureListener { e -> callback.onFailure(e) }
                } else {
                    callback.onSuccess(null)
                }
            }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    // --- Achievements ---
    fun getPlayerAchievements(playerId: String, callback: OnResultCallback<List<String>>) {
        achievementsRef.whereEqualTo("playerId", playerId)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) { callback.onFailure(e); return@addSnapshotListener }
                val ids = snap.documents.mapNotNull { it.getString("achievementId") }
                callback.onSuccess(ids)
            }
    }

    fun unlockAchievement(playerId: String, achievementId: String, callback: OnResultCallback<Void?>) {
        val docId = "$playerId" + "_" + achievementId
        achievementsRef.document(docId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    val data = mapOf(
                        "achievementId" to achievementId,
                        "playerId" to playerId
                    )
                    achievementsRef.document(docId).set(data)
                        .addOnSuccessListener { callback.onSuccess(null) }
                        .addOnFailureListener { e -> callback.onFailure(e) }
                } else {
                    callback.onSuccess(null)
                }
            }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    // --- Voice Channels (participation tokens only) ---
    /**
     * Legacy helper kept for compatibility with any remaining callers.
     * The call experience now stores presence under
     * voice_channels/{channelId}/participants/{uid} and call chat under
     * voice_channels/{channelId}/call_chat, so this repository no longer
     * tracks a combined participant list on the channel document.
     */
    fun getVoiceChannelIds(callback: OnResultCallback<List<String>>) {
        voiceChannelsRef.whereEqualTo("active", true)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) { callback.onFailure(e); return@addSnapshotListener }
                val ids = snap.documents.mapNotNull { it.id }
                callback.onSuccess(ids)
            }
    }
}
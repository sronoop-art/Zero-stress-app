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
        playersRef.orderBy("score", Query.Direction.DESCENDING)
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
    fun addMatchLog(log: MatchLog, callback: OnResultCallback<Void?>) {
        val id = matchLogsRef.document().id
        log.id = id
        matchLogsRef.document(id).set(log)
            .addOnSuccessListener {
                getPlayer(log.playerId ?: "", object : OnResultCallback<Player?> {
                    override fun onSuccess(player: Player?) {
                        if (player == null) { callback.onSuccess(null); return }
                        val newKills = player.kills + log.kills
                        val newDamage = (player.damage + log.damage).toInt()
                        val newWins = player.wins + if (log.win) 1 else 0
                        val newMatches = player.matches + 1
                        val newScore = Player.calculateScore(newKills, newDamage.toLong(), newWins)
                        val newRank = Player.getRankTier(newScore)
                        val xpGained = log.kills * 5 + (log.damage / 50).toInt() + if (log.win) 100 else 20
                        var newXp = player.xp + xpGained
                        var newLevel = player.level
                        while (newXp >= Player.xpForLevel(newLevel)) {
                            newXp -= Player.xpForLevel(newLevel)
                            newLevel++
                        }
                        val coinsGained = log.kills * 2 + if (log.win) 25 else 5

                        val updates = mapOf(
                            "kills" to newKills,
                            "damage" to newDamage,
                            "wins" to newWins,
                            "matches" to newMatches,
                            "score" to newScore,
                            "rank" to newRank,
                            "xp" to newXp,
                            "level" to newLevel,
                            "coins" to player.coins + coinsGained
                        )

                        playersRef.document(log.playerId ?: "").update(updates)
                            .addOnSuccessListener { callback.onSuccess(null) }
                            .addOnFailureListener { e -> callback.onFailure(e) }
                    }

                    override fun onFailure(e: Exception?) { callback.onFailure(e) }
                })
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

    // --- Voice Channels ---
    fun getVoiceChannels(callback: OnResultCallback<List<VoiceChannel>>) {
        voiceChannelsRef.addSnapshotListener { snap, e ->
            if (e != null || snap == null) { callback.onFailure(e); return@addSnapshotListener }
            val list = snap.documents.mapNotNull { it.toObject(VoiceChannel::class.java) }
            callback.onSuccess(list)
        }
    }

    fun joinVoiceChannel(channelId: String, userId: String, callback: OnResultCallback<Void?>) {
        voiceChannelsRef.document(channelId).get()
            .addOnSuccessListener { doc ->
                val ch = doc.toObject(VoiceChannel::class.java)
                if (ch == null) { callback.onFailure(Exception("Channel not found")); return@addOnSuccessListener }
                if (ch.participants.size >= ch.maxParticipants) {
                    callback.onFailure(Exception("Channel full"))
                    return@addOnSuccessListener
                }
                val participants = ch.participants.toMutableList()
                if (!participants.contains(userId)) participants.add(userId)
                val updates = mapOf(
                    "participants" to participants,
                    "active" to true
                )
                voiceChannelsRef.document(channelId).update(updates)
                    .addOnSuccessListener { callback.onSuccess(null) }
                    .addOnFailureListener { e -> callback.onFailure(e) }
            }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }

    fun leaveVoiceChannel(channelId: String, userId: String, callback: OnResultCallback<Void?>) {
        voiceChannelsRef.document(channelId).get()
            .addOnSuccessListener { doc ->
                val ch = doc.toObject(VoiceChannel::class.java)
                if (ch == null) { callback.onFailure(Exception("Channel not found")); return@addOnSuccessListener }
                val participants = ch.participants.toMutableList()
                participants.remove(userId)
                val updates = mapOf(
                    "participants" to participants,
                    "active" to participants.isNotEmpty()
                )
                voiceChannelsRef.document(channelId).update(updates)
                    .addOnSuccessListener { callback.onSuccess(null) }
                    .addOnFailureListener { e -> callback.onFailure(e) }
            }
            .addOnFailureListener { e -> callback.onFailure(e) }
    }
}
package com.zerostress.manager

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.UUID

class VoiceCallSignaling(
    private val channelId: String,
    private val localUid: String,
    private val remoteUid: String,
    private val db: FirebaseFirestore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var callRef: DocumentReference? = null
    private var listener: ListenerRegistration? = null

    private val offerChannel = Channel<SessionDescription>(Channel.BUFFERED)
    private val answerChannel = Channel<SessionDescription>(Channel.BUFFERED)
    private val remoteIceChannel = Channel<IceCandidate>(Channel.BUFFERED)
    private val myIceChannel = Channel<IceCandidate>(Channel.BUFFERED)

    fun createCallRef(): DocumentReference {
        val ref = db.collection("voice_channels")
            .document(channelId)
            .collection("calls")
            .document(UUID.randomUUID().toString())
        callRef = ref
        return ref
    }

    val callId: String
        get() = callRef?.id ?: UUID.randomUUID().toString()

    fun startListening() {
        val ref = callRef ?: return
        listener = ref.addSnapshotListener { snap, e ->
            if (e != null) {
                Log.w(VoiceCallSignaling.Tag, "signaling listener failed", e)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) return@addSnapshotListener

            when (val action = snap.getString("action")) {
                "offer" -> {
                    val sdp = snap.getString("sdp") ?: return@addSnapshotListener
                    val type = snap.getString("type") ?: return@addSnapshotListener
                    val descType = SessionDescription.Type.fromCanonical(type)
                        ?: return@addSnapshotListener
                    scope.launch {
                        offerChannel.send(SessionDescription(descType, sdp))
                    }
                }
                "answer" -> {
                    val sdp = snap.getString("sdp") ?: return@addSnapshotListener
                    val type = snap.getString("type") ?: return@addSnapshotListener
                    val descType = SessionDescription.Type.fromCanonical(type)
                        ?: return@addSnapshotListener
                    scope.launch {
                        answerChannel.send(SessionDescription(descType, sdp))
                    }
                }
                "ice" -> {
                    val sdpMid = snap.getString("sdpMid") ?: ""
                    val sdpMLineIndex = snap.getLong("sdpMLineIndex")?.toInt() ?: 0
                    val candidate = snap.getString("candidate") ?: return@addSnapshotListener
                    scope.launch {
                        remoteIceChannel.send(IceCandidate(sdpMid, sdpMLineIndex, candidate))
                    }
                }
            }
        }
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    suspend fun waitForOffer(): SessionDescription {
        return offerChannel.receive()
    }

    suspend fun waitForAnswer(): SessionDescription {
        return answerChannel.receive()
    }

    suspend fun waitForRemoteIce(): IceCandidate {
        return remoteIceChannel.receive()
    }

    suspend fun waitForMyIce(): IceCandidate {
        return myIceChannel.receive()
    }

    fun sendLocalOffer(sdp: SessionDescription) {
        callRef?.set(
            mapOf(
                "action" to "offer",
                "type" to sdp.type.canonical(),
                "sdp" to sdp.sdp,
                "createdBy" to localUid,
                "targetUid" to remoteUid,
                "channelId" to channelId,
                "createdAt" to System.currentTimeMillis()
            )
        )
    }

    fun sendLocalAnswer(sdp: SessionDescription) {
        callRef?.set(
            mapOf(
                "action" to "answer",
                "type" to sdp.type.canonical(),
                "sdp" to sdp.sdp,
                "createdBy" to localUid,
                "targetUid" to remoteUid,
                "channelId" to channelId,
                "createdAt" to System.currentTimeMillis()
            )
        )
    }

    fun addLocalIceCandidate(candidate: IceCandidate) {
        callRef?.set(
            mapOf(
                "action" to "ice",
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "candidate" to candidate.sdp,
                "createdBy" to localUid,
                "targetUid" to remoteUid,
                "channelId" to channelId,
                "createdAt" to System.currentTimeMillis()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    companion object {
        private const val Tag = "VoiceCallSignaling"

        fun fromSnapshot(snap: DocumentSnapshot): CallMessage? {
            if (!snap.exists()) return null
            return CallMessage(
                action = snap.getString("action") ?: return null,
                createdBy = snap.getString("createdBy") ?: "",
                targetUid = snap.getString("targetUid") ?: "",
                channelId = snap.getString("channelId") ?: "",
                type = snap.getString("type"),
                sdp = snap.getString("sdp"),
                sdpMid = snap.getString("sdpMid"),
                sdpMLineIndex = snap.getLong("sdpMLineIndex")?.toInt(),
                createdAt = snap.getLong("createdAt") ?: 0L
            )
        }
    }
}

data class CallMessage(
    val action: String,
    val createdBy: String,
    val targetUid: String,
    val channelId: String,
    val type: String?,
    val sdp: String?,
    val sdpMid: String?,
    val sdpMLineIndex: Int?,
    val createdAt: Long
)

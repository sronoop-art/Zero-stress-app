package com.zerostress.manager

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import org.webrtc.*
import java.util.concurrent.atomic.AtomicBoolean

object VoiceCallPeer {
    private const val Tag = "VoiceCallPeer"

    private var factory: PeerConnectionFactory? = null
    private var initialized = AtomicBoolean(false)

    fun ensureFactory() {
        if (initialized.get()) return
        try {
            if (factory == null) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(null)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
                factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
                initialized.set(true)
            }
        } catch (t: Throwable) {
            Log.w(Tag, "factory init failed", t)
        }
    }

    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    fun startLocalMedia(context: Context) {
        ensureFactory()
        val f = factory ?: return

        try {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }
            localAudioSource = f.createAudioSource(constraints)
            localAudioTrack = f.createAudioTrack("local_audio", localAudioSource)
        } catch (t: Throwable) {
            Log.w(Tag, "audio start failed", t)
        }
    }

    fun stopLocalMedia() {
        try {
            localAudioTrack?.dispose()
            localAudioTrack = null
            localAudioSource?.dispose()
            localAudioSource = null
        } catch (t: Throwable) {
            Log.w(Tag, "local media stop failed", t)
        }
    }

    fun isActive(): Boolean {
        return localAudioTrack != null || localVideoTrack != null
    }

    data class CallView(
        val channelId: String,
        val callId: String,
        val localUid: String,
        val remoteUid: String,
        val pc: PeerConnection?,
        val signaling: VoiceCallSignaling?,
        val callRef: DocumentReference?
    )

    fun createCall(
        context: Context,
        firestore: FirebaseFirestore,
        channelId: String,
        localUid: String,
        remoteUid: String
    ): CallView? {
        ensureFactory()
        val f = factory ?: return null

        val signaling = VoiceCallSignaling(channelId, localUid, remoteUid, firestore)
        val callRef = signaling.createCallRef()
        val signalingRef = callRef

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpConstraints = PeerConnection.SdpConstraints.Builder()
                .setOfferRequiresIceLiveness(true)
                .createSdpConstraints()
        }

        val pc = f.createPeerConnection(config)

        pc.createDataChannel("voice", DataChannel.Init().apply { ordered = true })

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription?) {}
                        override fun onSetSuccess() {
                            signaling.sendLocalOffer(sdp)
                        }
                        override fun onCreateFailure(error: String?) {}
                        override fun onSetFailure(error: String?) {}
                    }, sdp)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        })

        pc.observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(Tag, "signaling=${state}")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(Tag, "ice=${state}")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(Tag, "iceReceiving=$receiving")
            }
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(Tag, "iceGathering=$state")
            }
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) signaling.addLocalIceCandidate(candidate)
            }
            override fun onAddTrack(
                receiver: PeerConnection.TrackReceiver?,
                streams: Array<out MediaStream>?
            ) {
                streams?.forEach { stream ->
                    Log.d(Tag, "remote track streamId=${stream.id}")
                }
            }
            override fun onRemoveTrack(receiver: PeerConnection.TrackReceiver?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddIntermediateStream(streams: Array<out MediaStream>?) {}
        }

        if (localAudioTrack != null) {
            try { pc.addTrack(localAudioTrack!!, listOf("audio")) } catch (_: Throwable) {}
        }
        if (localVideoTrack != null) {
            try { pc.addTrack(localVideoTrack!!, listOf("video")) } catch (_: Throwable) {}
        }

        return CallView(channelId, signaling.callId, localUid, remoteUid, pc, signaling, signalingRef)
    }

    fun createOffer(view: CallView) {
        val pc = view.pc ?: return
        val signaling = view.signaling ?: return
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription?) {}
                        override fun onSetSuccess() {
                            signaling.sendLocalOffer(sdp)
                        }
                        override fun onCreateFailure(error: String?) {}
                        override fun onSetFailure(error: String?) {}
                    }, sdp)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        })
    }

    fun setRemoteAnswer(view: CallView, answer: SessionDescription) {
        val pc = view.pc ?: return
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }, answer)
    }

    fun addRemoteIceCandidate(view: CallView, candidate: IceCandidate) {
        val pc = view.pc ?: return
        try {
            pc.addIceCandidate(candidate)
        } catch (t: Throwable) {
            Log.w(Tag, "add remote ice failed", t)
        }
    }

    fun close(view: CallView) {
        try {
            view.pc?.dispose()
        } catch (_: Throwable) {}
        view.signaling?.stopListening()
    }

    fun connectionState(view: CallView): String {
        val pc = view.pc ?: return "closed"
        return try {
            pc.connectionState.toString()
        } catch (_: Throwable) {
            "unknown"
        }
    }

    fun iceState(view: CallView): String {
        val pc = view.pc ?: return "closed"
        return try {
            pc.iceConnectionState.toString()
        } catch (_: Throwable) {
            "unknown"
        }
    }

    fun localDescription(view: CallView): SessionDescription? {
        val pc = view.pc ?: return null
        return try {
            pc.localDescription
        } catch (_: Throwable) {
            null
        }
    }

    fun remoteDescription(view: CallView): SessionDescription? {
        val pc = view.pc ?: return null
        return try {
            pc.remoteDescription
        } catch (_: Throwable) {
            null
        }
    }

    fun isConnected(view: CallView): Boolean {
        return connectionState(view) == "connected"
    }
}

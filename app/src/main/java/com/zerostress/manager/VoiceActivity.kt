package com.zerostress.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGreen
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.voice.AgoraVoiceManager
import com.zerostress.manager.voice.AgoraVoiceManager.Listener

class VoiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel = remember { VoiceViewModel() }
            val db = remember { FirebaseAuth.getInstance() }
            DisposableEffect(Unit) {
                val uid = db.uid
                val appId = BuildConfig.AGORA_APP_ID
                if (appId.isNotBlank()) {
                    AgoraVoiceManager.init(appId)
                }
                onDispose {
                    viewModel.leave()
                }
            }
            VoiceScreen(
                viewModel = viewModel,
                onBack = { viewModel.onBackPressed() }
            )
        }
    }
}

@Composable
private fun VoiceScreen(
    viewModel: VoiceViewModel,
    onBack: () -> Unit
) {
    ZSBackground {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ZSTopBar(
                title = "Voice Chat",
                onBack = onBack
            )

            Spacer(Modifier.height(8.dp))

            if (viewModel.state.isNotEmpty()) {
                Text(
                    text = viewModel.state,
                    color = ZsAccent,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
            }

            if (!viewModel.inCall) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Not in a voice channel",
                        color = ZsTextMuted,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.leave() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Leave voice", color = ZsDanger, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "In call",
                            color = ZsGreen,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Press back only if you want to exit via Leave.",
                            color = ZsTextMuted,
                            fontSize = 12.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFF0B1220))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { viewModel.mute = !viewModel.mute }) {
                                Icon(
                                    if (viewModel.mute) Icons.Default.MicOff else Icons.Default.CallEnd,
                                    contentDescription = null,
                                    tint = if (viewModel.mute) ZsDanger else ZsTextPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.leave() }) {
                                Icon(
                                    Icons.Default.CallEnd,
                                    contentDescription = "Leave call",
                                    tint = ZsDanger,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class VoiceViewModel : ViewModel() {
    var inCall by mutableStateOf(false)
    var state by mutableStateOf("")
    var mute by mutableStateOf(false)

    fun onBackPressed() {
        if (inCall) {
            state = "Use the Leave button to exit the call."
        } else {
            (androidContext() as? android.app.Activity)?.finish()
        }
    }

    fun join(channelId: String, uid: Int, token: String? = null) {
        inCall = true
        state = "Joining $channelId..."
        val listener = object : Listener {
            override fun onJoined(channelId: String, uid: Int) {
                state = "Connected to $channelId"
            }

            override fun onUserJoined(uid: Int) {
                state = "User joined uid=$uid"
            }

            override fun onUserOffline(uid: Int) {
                state = "User offline uid=$uid"
            }

            override fun onVoiceVolumeChanged(volumes: List<com.zerostress.manager.voice.AgoraVoiceManager.Listener>) {
            }

            override fun onError(code: Int, message: String) {
                state = "Error: $message"
            }

            override fun onMuteCallRemoteAudioResult(result: Int) {
            }
        }
        AgoraVoiceManager.join(
            channelId = channelId,
            uid = uid,
            token = token,
            listener = listener
        )
    }

    fun leave() {
        AgoraVoiceManager.leave()
        inCall = false
        state = ""
        mute = false
        (androidContext() as? android.app.Activity)?.finish()
    }

    private fun androidContext(): android.content.Context {
        return androidx.compose.ui.platform.LocalContext.current
    }
}

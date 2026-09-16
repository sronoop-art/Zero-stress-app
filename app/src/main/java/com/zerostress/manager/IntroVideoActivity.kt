package com.zerostress.manager

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zerostress.manager.ui.theme.ZeroStressTheme

/**
 * Plays the intro video (Remote Config: intro_video_url) full-screen and
 * skippable. Each video version (intro_video_version) plays only once per
 * device; afterwards the app goes straight to the destination activity.
 *
 * Launched with extras:
 *   "targetClass"  - String class name of the activity to open after the video
 *   "force"        - true to play even if already seen (not used by default)
 */
class IntroVideoActivity : ComponentActivity() {

    companion object {
        fun launch(
            context: Context,
            target: Class<*>,
            force: Boolean = false
        ) {
            val url = try {
                com.zerostress.manager.ota.ZsRemoteConfig.introVideoUrl()
            } catch (_: Exception) {
                ""
            }
            val skipIntent = Intent(context, target).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            if (url.isBlank()) {
                // Feature disabled - go straight to the destination.
                context.startActivity(skipIntent)
                return
            }
            val version = try {
                com.zerostress.manager.ota.ZsRemoteConfig.introVideoVersion()
            } catch (_: Exception) {
                1L
            }
            val prefs = context.getSharedPreferences("intro_video", Context.MODE_PRIVATE)
            if (!force && prefs.getLong("seen_version", 0L) >= version) {
                context.startActivity(skipIntent)
                return
            }
            val intent = Intent(context, IntroVideoActivity::class.java).apply {
                putExtra("targetClass", target.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onBackPressed() {
        // Back = skip (same as the Skip button).
        finishToTarget()
    }

    private fun finishToTarget() {
        val targetName = intent.getStringExtra("targetClass")
            ?: PlayerDashboardActivity::class.java.name
        try {
            val target = Class.forName(targetName)
            startActivity(Intent(this, target).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
        } catch (_: Exception) {
            startActivity(Intent(this, PlayerDashboardActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                IntroVideoScreen(
                    onFinished = { finishToTarget() }
                )
            }
        }
    }
}

@Composable
private fun IntroVideoScreen(onFinished: () -> Unit) {
    val context = LocalContext.current

    val url = com.zerostress.manager.ota.ZsRemoteConfig.introVideoUrl()
    val version = com.zerostress.manager.ota.ZsRemoteConfig.introVideoVersion()

    // Hold the VideoView so Skip/dispose can stop playback cleanly.
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    fun finishNow() {
        videoView?.stopPlayback()
        markSeen(context, version)
        onFinished()
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { videoView?.stopPlayback() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (url.isNotBlank()) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.parse(url))
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            mp.setVolume(1f, 1f)
                            start()
                        }
                        setOnCompletionListener {
                            markSeen(ctx, version)
                            onFinished()
                        }
                        setOnErrorListener { _, what, extra ->
                            android.util.Log.e("IntroVideo", "playback failed what=$what extra=$extra")
                            finishNow()
                            true
                        }
                    }.also { videoView = it }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Config not loaded / feature off - just continue.
            androidx.compose.runtime.LaunchedEffect(Unit) { onFinished() }
        }

        TextButton(
            onClick = { finishNow() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 16.dp)
        ) {
            Text("Skip  >>", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

private fun markSeen(context: Context, version: Long) {
    context.getSharedPreferences("intro_video", Context.MODE_PRIVATE)
        .edit()
        .putLong("seen_version", version)
        .apply()
}

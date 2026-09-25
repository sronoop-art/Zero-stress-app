package com.zerostress.manager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.fcm.ZSFCMService
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsBgMid
import com.zerostress.manager.ui.theme.ZsBgStart
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import kotlinx.coroutines.delay

class SplashScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Loading-screen audio (res/raw/app_start.mp3) - LOOPS from app start until
        // the player signs in / registers successfully, or a dashboard opens.
        com.zerostress.manager.audio.ZsSoundManager.startLoadingLoop(this)
        // Remote Config - OTA switches (update gate, title scores, asset pack).
        com.zerostress.manager.ota.ZsRemoteConfig.init(this)
        com.zerostress.manager.ota.ZsRemoteConfig.fetchAndActivate { }
        setContent {
            ZeroStressTheme {
                SplashScreen()
            }
        }
    }

    override fun onDestroy() {
        // The loop keeps playing on the Login/Register screens; only pause if the
        // whole app is leaving (isFinishing). Login stops it for good on success.
        if (isFinishing) {
            com.zerostress.manager.audio.ZsSoundManager.stopLoadingLoop()
        }
        super.onDestroy()
    }

    @Deprecated("Back is disabled on the splash screen")
    override fun onBackPressed() {
        // Disable back button on splash screen
    }
}

private val loadingMessages = listOf(
    "Initializing system...",
    "Loading player data...",
    "Syncing leaderboards...",
    "Connecting to voice servers...",
    "Preparing battle arena...",
    "Almost ready..."
)

@Composable
private fun SplashScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    val logoScale = remember { Animatable(0.5f) }
    val logoAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    var progress by remember { mutableIntStateOf(0) }
    var messageIndex by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }

    // OTA: blocking update dialog state
    var updateRequired by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var updateUrl by remember { mutableStateOf("") }
    var checkedForUpdate by remember { mutableStateOf(false) }

    // Kick off OTA asset-pack download in the background once config has fetched.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Wait briefly for the fetch callback to populate the config cache.
            repeat(10) {
                if (com.zerostress.manager.ota.ZsRemoteConfig.contentPackVersion() > 0) return@withContext
                kotlinx.coroutines.delay(300)
            }
            if (com.zerostress.manager.ota.ZsAssetUpdater.updateAvailable(context)) {
                val ok = com.zerostress.manager.ota.ZsAssetUpdater.downloadIfNewer(context)
                android.util.Log.d("SplashScreen", "asset pack update: $ok")
            }
        }
    }

    // Version gate: consult Remote Config once the loading bar is done.
    LaunchedEffect(ready) {
        if (!ready || checkedForUpdate) return@LaunchedEffect
        checkedForUpdate = true
        com.zerostress.manager.ota.ZsRemoteConfig.fetchAndActivate { ok ->
            if (ok && com.zerostress.manager.ota.ZsRemoteConfig.updateRequired()) {
                updateRequired = true
                updateMessage = com.zerostress.manager.ota.ZsRemoteConfig.updateMessage()
                updateUrl = com.zerostress.manager.ota.ZsRemoteConfig.updateUrl()
            }
        }
    }

    fun navigateToMain() {
        // A dashboard is opening - the loading loop's job is done.
        com.zerostress.manager.audio.ZsSoundManager.stopLoadingLoop()
        if (auth.currentUser != null) {
            val userId = auth.currentUser!!.uid
            // Save FCM token so push notifications work after app restart
            ZSFCMService.saveTokenToFirestore(context)

            db.collection("players").document(userId).get()
                .addOnSuccessListener { doc ->
                    // Intro video plays at APP START (once per version) for everyone -
                    // logged-in users continue to their dashboard, guests to login.
                    if (doc.exists() && doc.getString("role") == "admin") {
                        IntroVideoActivity.launch(context, AdminDashboardActivity::class.java)
                    } else if (doc.exists()) {
                        IntroVideoActivity.launch(context, PlayerDashboardActivity::class.java)
                    } else {
                        IntroVideoActivity.launch(context, LoginActivity::class.java)
                    }
                    (context as? android.app.Activity)?.finish()
                }
                .addOnFailureListener {
                    val intent = Intent(context, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                    (context as? android.app.Activity)?.finish()
                }
        } else {
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
            (context as? android.app.Activity)?.finish()
        }
    }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(500))
        logoScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        delay(300)
        titleAlpha.animateTo(1f, tween(400))
        delay(200)
        while (progress < 100) {
            delay(100)
            progress += 5
            if (progress % 20 == 0 && messageIndex < loadingMessages.size) {
                messageIndex++
            }
        }
        ready = true
        delay(500)
        navigateToMain()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ZsBgStart, ZsBgMid, ZsBgStart))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo mark - replace res/drawable/app_logo.png with your own PNG
        // (fits the 96dp slot; the scale/alpha animation still applies).
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = "Zero Stress logo",
            modifier = Modifier
                .width(96.dp)
                .height(96.dp)
                .scale(logoScale.value)
                .alpha(logoAlpha.value),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.alpha(titleAlpha.value), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ZERO ",
                color = ZsTextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                letterSpacing = 2.sp
            )
            Text(
                "STRESS",
                color = ZsAccent,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                letterSpacing = 2.sp
            )
        }
        Text(
            "NEXUS SYSTEM BOOT",
            modifier = Modifier.padding(top = 6.dp).alpha(titleAlpha.value),
            color = ZsPrimary.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Text(
            "PERFORMANCE \u00b7 LEADERBOARD MANAGER",
            modifier = Modifier.padding(top = 4.dp).alpha(titleAlpha.value),
            color = ZsTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.height(48.dp))

        // Loading
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .height(90.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (ready) "SYSTEM READY" else loadingMessages[messageIndex].uppercase(),
                color = if (ready) ZsAccent else ZsTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = ZsPrimary,
                trackColor = ZsCard
            )
        }
    }

    // --- OTA: blocking update dialog ---
    if (updateRequired) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { /* blocked until they update */ },
            title = { Text("Update Required", color = ZsTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(updateMessage, color = ZsTextSecondary, fontSize = 14.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        try {
                            if (updateUrl.isNotBlank()) {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(updateUrl)
                                    )
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    "No download link configured",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Update Now", color = ZsAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {}
        )
    }
}
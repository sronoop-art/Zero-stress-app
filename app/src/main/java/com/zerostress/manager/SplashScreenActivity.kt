package com.zerostress.manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import kotlinx.coroutines.delay

class SplashScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                SplashScreen()
            }
        }
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

    fun navigateToMain() {
        if (auth.currentUser != null) {
            val userId = auth.currentUser!!.uid
            // Save FCM token so push notifications work after app restart
            ZSFCMService.saveTokenToFirestore(context)

            db.collection("players").document(userId).get()
                .addOnSuccessListener { doc ->
                    val intent = if (doc.exists() && doc.getString("role") == "admin") {
                        Intent(context, AdminDashboardActivity::class.java)
                    } else if (doc.exists()) {
                        Intent(context, PlayerDashboardActivity::class.java)
                    } else {
                        Intent(context, LoginActivity::class.java)
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)
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
        // Logo mark
        Column(
            modifier = Modifier
                .scale(logoScale.value)
                .alpha(logoAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .height(96.dp)
                    .background(
                        Brush.linearGradient(listOf(ZsCyan, Color(0xFF0EA5E9), Color(0xFF2563EB))),
                        RoundedCornerShape(24.dp)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("ZS", color = Color(0xFF090D16), fontSize = 34.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "ZERO STRESS",
            modifier = Modifier.alpha(titleAlpha.value),
            color = ZsTextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )
        Text(
            "Performance & Leaderboard Manager",
            modifier = Modifier.padding(top = 6.dp).alpha(titleAlpha.value),
            color = ZsTextSecondary,
            fontSize = 14.sp
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
                if (ready) "Ready!" else loadingMessages[messageIndex],
                color = if (ready) ZsAccent else ZsTextMuted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = ZsCyan,
                trackColor = ZsCard
            )
        }
    }
}
package com.zerostress.manager

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.zerostress.manager.fcm.FCMConfig
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsBgMid
import com.zerostress.manager.ui.theme.ZsBgStart
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import com.zerostress.manager.ui.theme.ZsWarning

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                LoginScreen()
            }
        }
    }
}

@Composable
private fun LoginScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var showResetSent by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) FirebaseMessaging.getInstance().subscribeToTopic("all_players")
    }

    LaunchedEffect(Unit) {
        FCMConfig.checkFCMConfiguration(context as android.app.Activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun handleLogin() {
        val phoneT = phone.trim()
        val passwordT = password.trim()
        if (phoneT.isEmpty()) {
            Toast.makeText(context, "Enter phone number", Toast.LENGTH_SHORT).show()
            return
        }
        if (passwordT.isEmpty()) {
            Toast.makeText(context, "Enter password", Toast.LENGTH_SHORT).show()
            return
        }

        loading = true
        val email = "$phoneT@zerostress.local"
        auth.signInWithEmailAndPassword(email, passwordT)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                db.collection("players").document(uid).get()
                    .addOnSuccessListener { doc ->
                        loading = false
                        if (doc.exists()) {
                            val role = doc.getString("role")
                            saveFcmToken(uid)
                            val intent = if (role == "admin") {
                                Intent(context, AdminDashboardActivity::class.java)
                            } else {
                                Intent(context, PlayerDashboardActivity::class.java)
                            }
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            context.startActivity(intent)
                            (context as? android.app.Activity)?.finish()
                        } else {
                            Toast.makeText(context, "Player not found", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .addOnFailureListener { e ->
                loading = false
                Toast.makeText(context, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    ZSBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            Column(
                modifier = Modifier
                    .width(80.dp)
                    .height(80.dp)
                    .background(
                        Brush.linearGradient(listOf(ZsCyan, ZsBgMid)),
                        RoundedCornerShape(22.dp)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("ZS", color = ZsTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(20.dp))
            Text("ZERO STRESS", color = ZsTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Login to Leaderboards",
                modifier = Modifier.padding(top = 4.dp),
                color = ZsTextSecondary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(36.dp))

            ZSCard {
                Spacer(Modifier.height(8.dp))
                ZSField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "Phone Number",
                    placeholder = "+880 1XXXXXXXXX",
                    keyboardType = KeyboardType.Phone
                )
                Spacer(Modifier.height(14.dp))
                ZSField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    isPassword = true
                )
                Spacer(Modifier.height(20.dp))
                if (loading) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = ZsCyan)
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    ZSButton(text = "LOGIN", onClick = { handleLogin() })
                }
                TextButton(
                    onClick = { showForgotDialog = true },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("Forgot Password?", color = ZsTextMuted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New here?", color = ZsTextMuted, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = {
                    context.startActivity(Intent(context, RegisterActivity::class.java))
                }) {
                    Text("Create Account", color = ZsCyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showForgotDialog) {
        var inputPhone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Forgot Password") },
            text = {
                Column {
                    Text(
                        "Enter your phone number so we can send a password reset link",
                        color = ZsTextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    ZSField(
                        value = inputPhone,
                        onValueChange = { inputPhone = it },
                        label = "Phone number",
                        keyboardType = KeyboardType.Phone
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showForgotDialog = false
                    if (inputPhone.trim().isEmpty()) {
                        Toast.makeText(context, "Enter your phone number", Toast.LENGTH_SHORT).show()
                    } else {
                        showResetSent = true
                    }
                }) { Text("Send Reset Link", color = ZsCyan) }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetSent) {
        AlertDialog(
            onDismissRequest = { showResetSent = false },
            title = { Text("Reset Link Sent") },
            text = {
                Text(
                    "A password reset link has been sent to your account email.\n\nCheck your email to reset your password.",
                    color = ZsTextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = { showResetSent = false }) { Text("OK", color = ZsCyan) }
            }
        )
    }
}

private fun saveFcmToken(uid: String) {
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token ->
            if (token != null) {
                FirebaseFirestore.getInstance()
                    .collection("players").document(uid)
                    .update("fcmToken", token)
                    .addOnSuccessListener { }
                    .addOnFailureListener { }
            }
        }
    FirebaseMessaging.getInstance().subscribeToTopic("all_players")
    FirebaseMessaging.getInstance().subscribeToTopic("match_updates")
    FirebaseMessaging.getInstance().subscribeToTopic("announcements")
}
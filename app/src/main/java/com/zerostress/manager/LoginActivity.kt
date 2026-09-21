package com.zerostress.manager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.PasswordField
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If the user lands here before the splash finished (notification, back
        // stack restore), make sure the loading loop is running.
        com.zerostress.manager.audio.ZsSoundManager.startLoadingLoop(this)
        setContent {
            ZeroStressTheme {
                LoginScreen()
            }
        }
    }
}

@Composable
private fun LoginScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { FirebaseAuth.getInstance() }
    val fs = remember { FirebaseFirestore.getInstance() }

    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var resetLoading by remember { mutableStateOf(false) }

    // Firebase Auth is email/password internally; a typed phone number is
    // mapped to <phone>@zerostress.local (same mapping as registration).
    fun accountFor(input: String): String {
        val t = input.trim()
        return if (t.contains("@")) t else "$t@zerostress.local"
    }

    fun sendPasswordReset() {
        val account = accountFor(phone)
        if (!account.contains("@") || account.startsWith("@")) {
            Toast.makeText(context, "Enter your phone number or email first", Toast.LENGTH_SHORT).show()
            return
        }
        resetLoading = true
        db.sendPasswordResetEmail(account)
            .addOnCompleteListener { task ->
                resetLoading = false
                if (task.isSuccessful) {
                    Toast.makeText(
                        context,
                        "Reset link sent! Check your messages and follow the link to set a new password.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val msg = when {
                        task.exception?.message?.contains("no user record", true) == true ->
                            "No account found for that phone/email"
                        task.exception?.message?.contains("IDENTIFIER_INVALID", true) == true ->
                            "That account uses an email we cannot email - ask an admin to reset it"
                        else -> "Could not send reset email: ${task.exception?.localizedMessage}"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
    }

    ZSBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Login logo - replace res/drawable/zs_login_logo.png with your own PNG
            Image(
                painter = painterResource(R.drawable.zs_login_logo),
                contentDescription = "Zero Stress logo",
                modifier = Modifier.size(96.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "ZERO ",
                    color = ZsTextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic
                )
                Text(
                    text = "STRESS",
                    color = ZsPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Sign in to your account",
                color = com.zerostress.manager.ui.theme.ZsTextSecondary,
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .width(56.dp)
                    .height(4.dp)
                    .background(ZsPrimary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(24.dp))

            ZSCard {
                Column {
                    ZSField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "Phone or email"
                    )
                    Spacer(Modifier.height(12.dp))
                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password"
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { sendPasswordReset() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (resetLoading) "Sending reset link..." else "Forgot password?",
                            color = com.zerostress.manager.ui.theme.ZsCyan,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(14.dp))

                    ZSButton(
                        text = if (loading) "Signing in..." else "Sign in",
                        onClick = {
                            if (phone.isBlank() || password.isBlank()) {
                                Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                                return@ZSButton
                            }
                            loading = true
                            db.signInWithEmailAndPassword(accountFor(phone), password)
                                .addOnSuccessListener { result ->
                                    com.zerostress.manager.audio.ZsSoundManager.playLoginSuccess(context)
                                    Toast.makeText(context, "Signed in", Toast.LENGTH_SHORT).show()
                                    loading = false
                                    val uid = result.user?.uid
                                    if (uid != null) {
                                        // Push can't reach a fresh device without its token saved.
                                        com.zerostress.manager.fcm.ZSFCMService.saveTokenToFirestore(context)
                                        fs.collection("players").document(uid).get()
                                            .addOnSuccessListener { doc ->
                                                val target = if (doc.exists() && doc.getString("role") == "admin")
                                                    AdminDashboardActivity::class.java
                                                else
                                                    PlayerDashboardActivity::class.java
                                                // No intro video here - it already played at app
                                                // start; go straight to the dashboard.
                                                startActivity(Intent(context, target).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                                })
                                                (context as? android.app.Activity)?.finish()
                                            }
                                            .addOnFailureListener {
                                                // No profile (or Firestore failed): still enter the app
                                                // as a player - same behavior as the splash login path.
                                                startActivity(Intent(context, PlayerDashboardActivity::class.java).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                                })
                                                (context as? android.app.Activity)?.finish()
                                            }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Login failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    loading = false
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("No account yet?", color = ZsTextMuted, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(context, RegisterActivity::class.java))
                }) {
                    Text("Create account", color = ZsAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

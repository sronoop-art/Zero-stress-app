package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.PasswordField
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
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

    // Firebase Auth is email/password internally; a typed phone number is
    // mapped to <phone>@zerostress.local (same mapping as registration).
    fun accountFor(input: String): String {
        val t = input.trim()
        return if (t.contains("@")) t else "$t@zerostress.local"
    }

    ZSBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ZERO STRESS",
                color = ZsAccent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Sign in to your account",
                color = com.zerostress.manager.ui.theme.ZsTextSecondary,
                fontSize = 14.sp
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
                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (phone.isBlank() || password.isBlank()) {
                                Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            loading = true
                            db.signInWithEmailAndPassword(accountFor(phone), password)
                                .addOnSuccessListener { result ->
                                    com.zerostress.manager.audio.ZsSoundManager.playLoginSuccess(context)
                                    Toast.makeText(context, "Signed in", Toast.LENGTH_SHORT).show()
                                    loading = false
                                    val uid = result.user?.uid
                                    if (uid != null) {
                                        fs.collection("players").document(uid).get()
                                            .addOnSuccessListener { doc ->
                                                val target = if (doc.exists() && doc.getString("role") == "admin")
                                                    AdminDashboardActivity::class.java
                                                else
                                                    PlayerDashboardActivity::class.java
                                                val intent = android.content.Intent(context, target).apply {
                                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                }
                                                context.startActivity(intent)
                                                (context as? android.app.Activity)?.finish()
                                            }
                                            .addOnFailureListener {
                                                // No profile (or Firestore failed): still enter the app
                                                // as a player — splash login path behaves the same way.
                                                val intent = android.content.Intent(context, PlayerDashboardActivity::class.java).apply {
                                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                }
                                                context.startActivity(intent)
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
                    ) {
                        Text(
                            if (loading) "Signing in..." else "Sign in",
                            color = ZsTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
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

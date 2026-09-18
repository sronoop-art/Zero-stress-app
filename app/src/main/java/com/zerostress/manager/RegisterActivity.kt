package com.zerostress.manager

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
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

class RegisterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                RegisterScreen()
            }
        }
    }
}

@Composable
private fun RegisterScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    ZSBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
                text = "Create your account",
                color = ZsTextSecondary,
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
                        value = name,
                        onValueChange = { name = it },
                        label = "Name"
                    )
                    Spacer(Modifier.height(12.dp))
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
                    Spacer(Modifier.height(12.dp))
                    PasswordField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = "Confirm password"
                    )
                    Spacer(Modifier.height(20.dp))

                    ZSButton(
                        text = if (loading) "Creating account..." else "Register",
                        onClick = {
                            val nameT = name.trim()
                            val phoneT = phone.trim()
                            if (nameT.isEmpty() || phoneT.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
                                Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                                return@ZSButton
                            }
                            if (password.length < 6) {
                                Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                                return@ZSButton
                            }
                            if (password != confirm) {
                                Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                                return@ZSButton
                            }
                            // Firebase Auth is email/password internally; phones are
                            // mapped to <phone>@zerostress.local unless an email was typed.
                            val account = if (phoneT.contains("@")) phoneT else "$phoneT@zerostress.local"
                            loading = true
                            auth.createUserWithEmailAndPassword(account, password)
                                .addOnSuccessListener { result ->
                                    val uid = result.user?.uid
                                    loading = false
                                    if (uid == null) {
                                        Toast.makeText(context, "Registration failed: no user id returned", Toast.LENGTH_LONG).show()
                                        return@addOnSuccessListener
                                    }
                                    com.zerostress.manager.audio.ZsSoundManager.playRegisterSuccess(context)
                                    // Profile doc for dashboards/leaderboards (written in the
                                    // background so the UI returns to login immediately).
                                    val playerData = mapOf(
                                        "uid" to uid,
                                        "name" to nameT,
                                        "phone" to phoneT,
                                        "role" to "player",
                                        "status" to "pending",
                                        "score" to 0L,
                                        "kills" to 0,
                                        "deaths" to 0,
                                        "assists" to 0,
                                        "damage" to 0L,
                                        "wins" to 0,
                                        "matches" to 0,
                                        "xp" to 0,
                                        "level" to 1,
                                        "coins" to 0,
                                        "rank" to "Iron"
                                    )
                                    db.collection("players").document(uid).set(playerData)
                                    Toast.makeText(context, "Account created - you can sign in now", Toast.LENGTH_LONG).show()
                                    context.startActivity(Intent(context, LoginActivity::class.java))
                                    (context as? Activity)?.finish()
                                }
                                .addOnFailureListener { e ->
                                    loading = false
                                    val msg = when {
                                        e.message?.contains("email-already-in-use", ignoreCase = true) == true ->
                                            "This phone or email is already registered. Try logging in."
                                        e.message?.contains("invalid-email", ignoreCase = true) == true ->
                                            "Enter a valid phone number or email."
                                        e.message?.contains("weak-password", ignoreCase = true) == true ->
                                            "Password must be at least 6 characters."
                                        e.message?.contains("network", ignoreCase = true) == true ->
                                            "No internet connection."
                                        else -> e.localizedMessage
                                    }
                                    Toast.makeText(context, "Registration failed: $msg", Toast.LENGTH_LONG).show()
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "By registering you agree to our ",
                    color = ZsTextMuted,
                    fontSize = 11.sp
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(context, LegalActivity::class.java).putExtra(LegalActivity.EXTRA_TAB, "terms")
                    )
                }) {
                    Text("Terms", color = ZsAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(" & ", color = ZsTextMuted, fontSize = 11.sp)
                TextButton(onClick = {
                    context.startActivity(
                        Intent(context, LegalActivity::class.java).putExtra(LegalActivity.EXTRA_TAB, "privacy")
                    )
                }) {
                    Text("Privacy Policy", color = ZsAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?", color = ZsTextMuted, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = {
                    context.startActivity(Intent(context, LoginActivity::class.java))
                    (context as? Activity)?.finish()
                }) {
                    Text("Login", color = ZsAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

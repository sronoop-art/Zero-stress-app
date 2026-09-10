package com.zerostress.manager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private const val RegisterTag = "ZSM.Register"

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

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun doRegister() {
        val nameT = name.trim()
        val phoneT = phone.trim()
        val passwordT = password.trim()

        if (nameT.isEmpty()) { Toast.makeText(context, "Enter name", Toast.LENGTH_SHORT).show(); return }
        if (phoneT.isEmpty()) { Toast.makeText(context, "Enter phone", Toast.LENGTH_SHORT).show(); return }
        if (passwordT.isEmpty()) { Toast.makeText(context, "Enter password", Toast.LENGTH_SHORT).show(); return }
        if (passwordT.length < 6) { Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show(); return }

        val email = "$phoneT@zerostress.local"
        loading = true

        Log.d(RegisterTag, "Register attempt: email=$email name=$nameT")

        try {
            auth.createUserWithEmailAndPassword(email, passwordT)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: run {
                        Log.w(RegisterTag, "createUser succeeded but result.user was null")
                        loading = false
                        Toast.makeText(context, "Registration succeeded but user ID missing. Try again.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    Log.d(RegisterTag, "Auth createUser succeeded, uid=$uid")

                    // Return to login immediately so the app feels fast.
                    // The player profile is written to Firestore in the background.
                    loading = false
                    Toast.makeText(
                        context,
                        "Account created. Saving profile in background, then go to login.",
                        Toast.LENGTH_LONG
                    ).show()
                    context.startActivity(Intent(context, LoginActivity::class.java))
                    (context as? android.app.Activity)?.finish()

                    val playerData = mapOf(
                        "uid" to uid,
                        "name" to nameT,
                        "phone" to phoneT,
                        "role" to "player",
                        "status" to "pending",
                        "score" to 0,
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
                    FirebaseFirestore.getInstance().collection("players")
                        .document(uid)
                        .set(playerData)
                        .addOnSuccessListener {
                            Log.d(RegisterTag, "Firestore players/$uid set succeeded in background")
                        }
                        .addOnFailureListener { e ->
                            Log.w(RegisterTag, "Firestore players/$uid set failed in background: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    Log.w(RegisterTag, "Auth createUser failed: ${e.message}")
                    loading = false
                    val msg = when {
                        e.message?.contains("invalid-email", ignoreCase = true) == true ->
                            "That phone number is not valid for registration"
                        e.message?.contains("user-not-found", ignoreCase = true) == true ->
                            "Account not found"
                        e.message?.contains("wrong-password", ignoreCase = true) == true ->
                            "Wrong password"
                        e.message?.contains("email-already-in-use", ignoreCase = true) == true ||
                            e.message?.contains("duplicate", ignoreCase = true) == true ->
                            "This phone is already registered"
                        e.message?.contains("disabled", ignoreCase = true) == true ->
                            "Email/Password sign-in may be disabled in Firebase console"
                        e.message?.contains("network", ignoreCase = true) == true ->
                            "No internet connection"
                        else -> e.message
                    }
                    Toast.makeText(context, "Registration failed: $msg", Toast.LENGTH_LONG).show()

                    // Extra hint for the two most common new-console failures.
                    if (e.message?.contains("disabled", ignoreCase = true) == true) {
                        Log.w(RegisterTag, "AUTH_DISABLE_HINT: Email/Password provider may be disabled in Firebase Console > Authentication > Sign-in method")
                    }
                    if (e.message?.contains("app-check", ignoreCase = true) == true ||
                        e.message?.contains("APP_CHECK", ignoreCase = true) == true) {
                        Log.w(RegisterTag, "APP_CHECK_HINT: Firestore/Auth rejected by App Check. Register the debug token from Logcat (ZeroStressApp) in Firebase Console > App Check")
                    }
                }
        } catch (e: Exception) {
            Log.e(RegisterTag, "Register threw synchronously: ${e.message}")
            loading = false
            Toast.makeText(context, "Registration error: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // Safety timeout: if auth + Firestore never finish, unblock the UI.
        kotlinx.coroutines.GlobalScope.launch {
            delay(30_000)
            if (loading) {
                Log.w(RegisterTag, "Registration timed out after 30s; auth callbacks never fired")
                loading = false
                Toast.makeText(
                    context,
                    "Took too long. Check internet + Firebase console (Auth enabled?).",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    ZSBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ZERO STRESS", color = ZsTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Create Account",
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp),
                color = ZsTextSecondary,
                fontSize = 14.sp
            )

            ZSCard {
                Spacer(Modifier.height(8.dp))
                ZSField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name",
                    placeholder = "Your in-game name"
                )
                Spacer(Modifier.height(14.dp))
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
                    ZSButton(text = "REGISTER", onClick = { doRegister() })
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?", color = ZsTextMuted, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = {
                    context.startActivity(Intent(context, LoginActivity::class.java))
                    (context as? android.app.Activity)?.finish()
                }) {
                    Text("Login", color = ZsCyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
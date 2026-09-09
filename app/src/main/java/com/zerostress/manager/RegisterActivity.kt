package com.zerostress.manager

import android.content.Intent
import android.os.Bundle
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

        auth.createUserWithEmailAndPassword(email, passwordT)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
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
                        loading = false
                        Toast.makeText(
                            context,
                            "Registered! Wait for admin approval.",
                            Toast.LENGTH_LONG
                        ).show()
                        context.startActivity(Intent(context, LoginActivity::class.java))
                        (context as? android.app.Activity)?.finish()
                    }
                    .addOnFailureListener { e ->
                        loading = false
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                loading = false
                Toast.makeText(context, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
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
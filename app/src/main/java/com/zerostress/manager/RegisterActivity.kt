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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import com.zerostress.manager.ui.PasswordField
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsTextPrimary

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { FirebaseAuth.getInstance() }

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
            Text(
                text = "ZERO STRESS",
                color = ZsAccent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Create your account",
                color = com.zerostress.manager.ui.theme.ZsTextSecondary,
                fontSize = 14.sp
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

                    Button(
                        onClick = {
                            if (name.isBlank() || phone.isBlank() || password.isBlank() || confirm.isBlank()) {
                                Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password != confirm) {
                                Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            loading = true
                            db.createUserWithEmailAndPassword(phone, password)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Account created", Toast.LENGTH_SHORT).show()
                                    loading = false
                                    (context as? android.app.Activity)?.finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        context,
                                        "Register failed: ${e.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    loading = false
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    ) {
                        Text(
                            if (loading) "Creating account..." else "Register",
                            color = ZsTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

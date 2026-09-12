package com.zerostress.manager

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.R
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZsPngIcon
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSKeyValue
import com.zerostress.manager.ui.ZSStat
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import java.util.Locale

class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                ProfileScreen()
            }
        }
    }
}

@Composable
private fun ProfileScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = FirebaseAuth.getInstance().uid

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0L) }
    var level by remember { mutableStateOf(1) }
    var rank by remember { mutableStateOf("Iron") }
    var kills by remember { mutableStateOf(0L) }
    var deaths by remember { mutableStateOf(0L) }
    var assists by remember { mutableStateOf(0L) }
    var damage by remember { mutableStateOf(0L) }
    var wins by remember { mutableStateOf(0L) }
    var matches by remember { mutableStateOf(0L) }
    var xp by remember { mutableStateOf(0) }
    var coins by remember { mutableStateOf(0) }

    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap != null) {
                    avatarBitmap = bitmap
                    Toast.makeText(context, "Avatar updated!", Toast.LENGTH_SHORT).show()
                    // NOTE: upload to Firebase Storage is not wired up yet —
                    // the original app also only stored it locally.
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadProfile() {
        if (uid == null) return
        db.collection("players").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    name = doc.getString("name") ?: ""
                    phone = doc.getString("phone") ?: ""
                    score = doc.getLong("score") ?: 0
                    level = (doc.getLong("level") ?: 1).toInt()
                    rank = doc.getString("rank") ?: "Iron"
                    kills = doc.getLong("kills") ?: 0
                    deaths = doc.getLong("deaths") ?: 0
                    assists = doc.getLong("assists") ?: 0
                    damage = doc.getLong("damage") ?: 0
                    wins = doc.getLong("wins") ?: 0
                    matches = doc.getLong("matches") ?: 0
                    xp = (doc.getLong("xp") ?: 0).toInt()
                    coins = (doc.getLong("coins") ?: 0).toInt()
                }
            }
    }

    fun saveProfile() {
        val newName = editName.trim()
        val newPhone = editPhone.trim()
        if (newName.isEmpty()) {
            Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPhone.isEmpty()) {
            Toast.makeText(context, "Phone number cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (uid == null) return

        db.collection("players").document(uid)
            .update(mapOf("name" to newName, "phone" to newPhone))
            .addOnSuccessListener {
                Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
                isEditing = false
                loadProfile()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    LaunchedEffect(Unit) {
        loadProfile()
    }

    val winRate = if (matches > 0) wins * 100.0 / matches else 0.0

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "My Profile",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Avatar + name
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(88.dp)
                                .background(ZsPrimary, CircleShape)
                                .clickable { avatarPicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap!!.asImageBitmap(),
                                    contentDescription = "Avatar",
                                    modifier = Modifier.size(88.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                ZsPngIcon(R.drawable.ic_menu_person, size = 72.dp, tint = ZsTextSecondary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(name, color = ZsTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("+880 $phone", color = ZsTextMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                    }
                }

                if (isEditing) {
                    ZSCard(highlight = ZsAccent) {
                        ZSField(value = editName, onValueChange = { editName = it }, label = "Name")
                        Spacer(Modifier.height(12.dp))
                        ZSField(value = editPhone, onValueChange = { editPhone = it }, label = "Phone", keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                        Spacer(Modifier.height(14.dp))
                        ZSButton(text = "Save Profile", onClick = { saveProfile() }, container = ZsAccent)
                        TextButton(onClick = { isEditing = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancel", color = ZsTextMuted)
                        }
                    }
                }

                // Stats row
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZSStat("Score", "$score pts", ZsGold, Modifier.weight(1f))
                    ZSStat("Level", "$level", ZsCyan, Modifier.weight(1f))
                    ZSStat("Rank", rank, ZsAccent, Modifier.weight(1f))
                }

                Spacer(Modifier.height(16.dp))
                ZSCard {
                    ZSKeyValue("Kills", "$kills", ZsTextPrimary)
                    Spacer(Modifier.height(8.dp))
                    ZSKeyValue("Deaths", "$deaths")
                    Spacer(Modifier.height(8.dp))
                    ZSKeyValue("Assists", "$assists")
                    Spacer(Modifier.height(8.dp))
                    ZSKeyValue("Damage", "$damage")
                    Spacer(Modifier.height(8.dp))
                    ZSKeyValue("Wins", "$wins")
                    Spacer(Modifier.height(8.dp))
                    ZSKeyValue("Matches", "$matches")
                    Spacer(Modifier.height(8.dp))
                    ZSKeyValue("Win Rate", String.format(Locale.getDefault(), "%.1f%%", winRate))
                    Spacer(Modifier.height(8.dp))
                    ZSKeyValue("Coins", "$coins", ZsGold)
                    Spacer(Modifier.height(8.dp))
                    ZSKeyValue("XP", "$xp")
                }

                Spacer(Modifier.height(16.dp))
                ZSButton(
                    text = if (isEditing) "" else "Edit Profile",
                    onClick = {
                        if (!isEditing) {
                            editName = name
                            editPhone = phone
                            isEditing = true
                        }
                    },
                    container = ZsPrimary,
                    enabled = !isEditing
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
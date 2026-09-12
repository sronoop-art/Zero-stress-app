package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary
import com.zerostress.manager.ui.theme.ZsGreen

class SubmitMatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                SubmitMatchScreen()
            }
        }
    }
}

@Composable
private fun SubmitMatchScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().uid

    var kills by remember { mutableStateOf("") }
    var deaths by remember { mutableStateOf("") }
    var assists by remember { mutableStateOf("") }
    var damage by remember { mutableStateOf("") }
    var isWin by remember { mutableStateOf(true) }
    var matchType by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }

    val matchTypes = listOf("Classic", "Ranked", "Tournament", "Custom")

    fun submitMatch() {
        if (kills.trim().isEmpty()) {
            Toast.makeText(context, "Enter kills", Toast.LENGTH_SHORT).show()
            return
        }
        if (damage.trim().isEmpty()) {
            Toast.makeText(context, "Enter damage", Toast.LENGTH_SHORT).show()
            return
        }
        val k = kills.trim().toIntOrNull() ?: 0
        val d = deaths.trim().toIntOrNull() ?: 0
        val a = assists.trim().toIntOrNull() ?: 0
        val dmg = damage.trim().toLongOrNull() ?: 0L

        loading = true
        db.collection("match_logs").add(
            mapOf(
                "playerId" to userId,
                "kills" to k,
                "deaths" to d,
                "assists" to a,
                "damage" to dmg,
                "win" to isWin,
                "matchType" to matchTypes[matchType],
                "date" to System.currentTimeMillis(),
                "score" to (k * 10 + dmg / 100 + if (isWin) 200 else 0)
            )
        ).addOnSuccessListener {
            // Update player stats
            db.collection("players").document(userId ?: "").get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        loading = false
                        return@addOnSuccessListener
                    }
                    val currentKills = doc.getLong("kills") ?: 0
                    val currentDamage = doc.getLong("damage") ?: 0
                    val currentWins = doc.getLong("wins") ?: 0
                    val currentMatches = doc.getLong("matches") ?: 0
                    val currentScore = doc.getLong("score") ?: 0
                    var currentXp = (doc.getLong("xp") ?: 0).toInt()
                    var currentLevel = (doc.getLong("level") ?: 1).toInt()
                    val startLevel = currentLevel
                    val currentCoins = (doc.getLong("coins") ?: 0).toInt()

                    val newKills = currentKills + k
                    val newDamage = currentDamage + dmg
                    val newWins = currentWins + if (isWin) 1 else 0
                    val newMatches = currentMatches + 1
                    val newScore = newKills * 10 + newDamage / 100 + newWins * 50

                    val xpGained = k * 5 + (dmg / 50).toInt() + if (isWin) 100 else 20
                    var newXp = currentXp + xpGained
                    var newLevel = startLevel
                    while (newXp >= newLevel * 500) {
                        newXp -= newLevel * 500
                        newLevel++
                    }

                    val coinsGained = k * 2 + if (isWin) 25 else 5
                    val newCoins = currentCoins + coinsGained

                    val newRank = when {
                        newScore >= 5000 -> "Mythic"
                        newScore >= 4000 -> "Diamond"
                        newScore >= 3000 -> "Platinum"
                        newScore >= 2000 -> "Gold"
                        newScore >= 1200 -> "Silver"
                        newScore >= 600 -> "Bronze"
                        else -> "Iron"
                    }

                    db.collection("players").document(userId ?: "").update(
                        mapOf(
                            "kills" to newKills,
                            "damage" to newDamage,
                            "wins" to newWins,
                            "matches" to newMatches,
                            "score" to newScore,
                            "xp" to newXp,
                            "level" to newLevel,
                            "coins" to newCoins,
                            "rank" to newRank
                        )
                    ).addOnSuccessListener {
                        loading = false
                        Toast.makeText(
                            context,
                            "+$xpGained XP, +$coinsGained coins",
                            Toast.LENGTH_LONG
                        ).show()
                        (context as? android.app.Activity)?.finish()
                    }.addOnFailureListener { e ->
                        loading = false
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }.addOnFailureListener { e ->
            loading = false
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Submit Match",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {

                ZSCard {
                    ZSField(
                        value = kills,
                        onValueChange = { kills = it },
                        label = "Kills",
                        keyboardType = KeyboardType.Number
                    )
                    Spacer(Modifier.height(12.dp))
                    ZSField(
                        value = deaths,
                        onValueChange = { deaths = it },
                        label = "Deaths",
                        keyboardType = KeyboardType.Number
                    )
                    Spacer(Modifier.height(12.dp))
                    ZSField(
                        value = assists,
                        onValueChange = { assists = it },
                        label = "Assists",
                        keyboardType = KeyboardType.Number
                    )
                    Spacer(Modifier.height(12.dp))
                    ZSField(
                        value = damage,
                        onValueChange = { damage = it },
                        label = "Damage",
                        keyboardType = KeyboardType.Number
                    )
                    Spacer(Modifier.height(16.dp))

                    Text("Result", color = ZsTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        ZSChoiceChip("Win", isWin, { isWin = true }, Modifier.weight(1f), ZsAccent)
                        Spacer(Modifier.width(10.dp))
                        ZSChoiceChip("Loss", !isWin, { isWin = false }, Modifier.weight(1f), ZsPrimary)
                    }
                    Spacer(Modifier.height(16.dp))

                    Text("Match Type", color = ZsTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        matchTypes.forEachIndexed { index, type ->
                            ZSChoiceChip(
                                type,
                                matchType == index,
                                { matchType = index },
                                Modifier.weight(1f),
                                ZsCyan,
                                compact = true
                            )
                            if (index < matchTypes.size - 1) Spacer(Modifier.width(6.dp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                if (loading) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = ZsCyan)
                    }
                } else {
                    ZSButton(text = "SUBMIT MATCH", onClick = { submitMatch() }, container = ZsAccent)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ZSChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    accent: Color,
    compact: Boolean = false
) {
    Text(
        text = text,
        modifier = modifier
            .background(
                if (selected) accent else com.zerostress.manager.ui.theme.ZsCard,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = if (compact) 8.dp else 12.dp),
        color = if (selected) Color(0xFF0B1220) else ZsTextPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = if (compact) 11.sp else 14.sp,
        textAlign = TextAlign.Center
    )
}
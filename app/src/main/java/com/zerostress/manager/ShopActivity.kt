package com.zerostress.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.LoadingBox
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsSuccess
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

/**
 * Coin Shop: spend earned coins on cosmetic titles. Purchases run in a
 * Firestore Transaction (coins check + deduct + title grant are atomic, so
 * double-taps or concurrent spends cannot go negative), titles are written to
 * the existing player_titles collection (same shape the leaderboard rewards
 * and My Titles screen already read), and the shop never touches score, rank
 * or XP math.
 */
class ShopActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                ShopScreen()
            }
        }
    }
}

/** One purchasable item. IDs and prices are the shop's own data. */
private data class ShopItem(
    val id: String,
    val name: String,
    val description: String,
    val price: Long,
    val color: Color
)

private val SHOP_CATALOG = listOf(
    ShopItem("title_bronze", "Bronze Title", "Show your grind - Bronze rank title badge", 500, Color(0xFFCD7F32)),
    ShopItem("title_silver", "Silver Title", "Silver rank title badge", 900, Color(0xFFC0C7D1)),
    ShopItem("title_gold", "Gold Title", "Gold rank title badge", 1500, Color(0xFFFFC857)),
    ShopItem("title_sniper", "Sniper", "For the patient ones", 750, Color(0xFF20E7FF)),
    ShopItem("title_rusher", "Rusher", "First through the door", 750, Color(0xFFFF416C)),
    ShopItem("title_igl", "Shot Caller", "Lead the squad", 1200, Color(0xFF8B5CFF)),
    ShopItem("title_clutch", "Clutch Master", "Won when it mattered", 2000, Color(0xFF31F7A5))
)

@Composable
private fun ShopScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = auth.uid

    var coins by remember { mutableStateOf(0L) }
    var ownedTitles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var equippedTitle by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var buying by remember { mutableStateOf<ShopItem?>(null) }
    var purchasing by remember { mutableStateOf(false) }

    // Live wallet + title inventory.
    androidx.compose.runtime.DisposableEffect(uid) {
        if (uid == null) {
            loading = false
            onDispose { }
        } else {
            val reg = db.collection("players").document(uid).addSnapshotListener { doc, _ ->
                coins = doc?.getLong("coins") ?: 0L
                equippedTitle = doc?.getString("title") ?: ""
                loading = false
            }
            val titlesReg = db.collection("player_titles")
                .whereEqualTo("playerId", uid)
                .addSnapshotListener { q, _ ->
                    ownedTitles = q?.documents
                        ?.mapNotNull { it.getString("title") }
                        ?.toSet() ?: emptySet()
                }
            onDispose { reg.remove(); titlesReg.remove() }
        }
    }

    fun purchase(item: ShopItem) {
        if (uid == null) return
        purchasing = true
        val playerRef = db.collection("players").document(uid)
        val titleRef = db.collection("player_titles").document("${uid}_${item.id}")
        db.runTransaction { tx ->
            val snap = tx.get(playerRef)
            val balance = snap.getLong("coins") ?: 0L
            if (balance < item.price) {
                // Returning failure retries the transaction; the UI catch
                // below translates it into a friendly message.
                return@runTransaction kotlin.Result.failure<Long>(IllegalStateException("insufficient-coins"))
            }
            tx.update(playerRef, "coins", balance - item.price)
            tx.set(
                titleRef,
                mapOf(
                    "playerId" to uid,
                    "title" to item.name,
                    "source" to "shop",
                    "itemId" to item.id,
                    "price" to item.price,
                    "awardedAt" to System.currentTimeMillis()
                )
            )
            kotlin.Result.success(balance - item.price)
        }.addOnSuccessListener { _ ->
            purchasing = false
            buying = null
            Toast.makeText(context, "Purchased ${item.name}!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { _ ->
            purchasing = false
            // Covers both the guard above (someone spent elsewhere first) and
            // connectivity failures.
            Toast.makeText(
                context,
                "Purchase failed - check your coin balance and connection",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun equip(item: ShopItem) {
        if (uid == null) return
        db.collection("players").document(uid)
            .update("title", item.name)
            .addOnSuccessListener {
                Toast.makeText(context, "${item.name} equipped", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Could not equip: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = "Coin Shop",
                onBack = { (androidx.compose.ui.platform.LocalContext.current as? android.app.Activity)?.finish() },
                right = {
                    // Live wallet balance, styled like the dashboard badge.
                    Box(
                        Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ZsGold.copy(alpha = 0.18f))
                            .border(1.dp, ZsGold.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "$coins coins",
                            color = ZsGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
            if (loading) {
                LoadingBox(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(SHOP_CATALOG, key = { it.id }) { item ->
                        val owned = item.name in ownedTitles
                        val equipped = equippedTitle == item.name
                        ZSCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Rank-title style medallion: colored ring + initial.
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF11131F))
                                        .border(2.dp, item.color, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        item.name.first().toString(),
                                        color = item.color,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        color = ZsTextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        item.description,
                                        color = ZsTextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 2
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                when {
                                    equipped -> ZSBadge("EQUIPPED", ZsSuccess)
                                    owned -> TextButton(onClick = { equip(item) }) {
                                        Text("Equip", color = ZsCyan, fontWeight = FontWeight.Bold)
                                    }
                                    else -> TextButton(onClick = { buying = item }, enabled = !purchasing) {
                                        Text("${item.price} 🪙", color = ZsGold, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Purchase confirmation: no accidental spends.
    buying?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!purchasing) buying = null },
            title = { Text("Buy ${item.name}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${item.description}\n\nPrice: ${item.price} coins\nYour balance: $coins coins\n\nBalance after: ${coins - item.price} coins",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { purchase(item) }, enabled = !purchasing && coins >= item.price) {
                    Text("BUY", color = ZsGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { buying = null }, enabled = !purchasing) { Text("Cancel") }
            }
        )
    }
}

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zerostress.manager.ui.LoadingBox
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSButton
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSField
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsSuccess
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

/**
 * Coin Shop: spend earned coins on cosmetic titles.
 *
 * Catalog: admins manage it in-app (add / edit price & text / deactivate /
 * delete). Items live in the `shop_items` collection:
 *   { name, description, price, active, createdAt }
 * Firestore rules allow read for signed-in players and writes for admins
 * only - the UI is convenience, the rules are the enforcement.
 *
 * Purchases run in a Firestore Transaction that re-reads the LIVE item doc
 * and the player balance, so a price change mid-tap or a concurrent spend
 * can never go negative. The granted title is written to the existing
 * player_titles collection (same shape My Titles already reads). Score,
 * rank and XP math are untouched.
 *
 * Fallback: while `shop_items` is empty (first ever run) the shop displays
 * the built-in default catalog as read-only previews; admins get a one-tap
 * "Add default items" seed button. Items players already own are unaffected
 * by deleting a catalog entry - owned titles live in player_titles.
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

/** One catalog row. [docId] is null only for built-in fallback previews. */
private data class ShopItem(
    val docId: String?,
    val name: String,
    val description: String,
    val price: Long,
    val active: Boolean,
    val color: Color
)

/** Shown while the admin has not populated shop_items yet; not purchasable. */
private val DEFAULT_CATALOG = listOf(
    ShopItem(null, "Bronze Title", "Show your grind - Bronze rank title badge", 500, true, Color(0xFFCD7F32)),
    ShopItem(null, "Silver Title", "Silver rank title badge", 900, true, Color(0xFFC0C7D1)),
    ShopItem(null, "Gold Title", "Gold rank title badge", 1500, true, Color(0xFFFFC857)),
    ShopItem(null, "Sniper", "For the patient ones", 750, true, Color(0xFF20E7FF)),
    ShopItem(null, "Rusher", "First through the door", 750, true, Color(0xFFFF416C)),
    ShopItem(null, "Shot Caller", "Lead the squad", 1200, true, Color(0xFF8B5CFF)),
    ShopItem(null, "Clutch Master", "Won when it mattered", 2000, true, Color(0xFF31F7A5))
)

@Composable
private fun ShopScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = auth.uid

    var coins by remember { mutableStateOf(0L) }
    var isAdmin by remember { mutableStateOf(false) }
    var ownedTitles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var equippedTitle by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    // Catalog from Firestore; empty -> fallback defaults.
    var items by remember { mutableStateOf<List<ShopItem>>(emptyList()) }
    var catalogLoaded by remember { mutableStateOf(false) }

    // Admin editor state
    var manageMode by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShopItem?>(null) }   // existing item
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ShopItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf<ShopItem?>(null) }
    var purchasing by remember { mutableStateOf(false) }

    // Wallet, role, equipped title - one live listener on the player doc.
    DisposableEffect(uid) {
        if (uid == null) {
            loading = false
            onDispose { }
        } else {
            val reg = db.collection("players").document(uid).addSnapshotListener { doc, _ ->
                coins = doc?.getLong("coins") ?: 0L
                equippedTitle = doc?.getString("title") ?: ""
                isAdmin = doc?.getString("role") == "admin"
                loading = false
            }
            val titlesReg = db.collection("player_titles")
                .whereEqualTo("playerId", uid)
                .addSnapshotListener { q, _ ->
                    ownedTitles = q?.documents
                        ?.mapNotNull { it.getString("title") }
                        ?.toSet() ?: emptySet()
                }
            // Live catalog; sorted client-side (no composite index needed).
            val catalogReg = db.collection("shop_items")
                .addSnapshotListener { q, _ ->
                    items = q?.documents?.map { d ->
                        ShopItem(
                            docId = d.id,
                            name = d.getString("name") ?: "Item",
                            description = d.getString("description") ?: "",
                            price = d.getLong("price") ?: 0L,
                            active = d.getBoolean("active") ?: true,
                            color = Color(0xFF20E7FF)
                        )
                    }?.sortedBy { it.price } ?: emptyList()
                    catalogLoaded = true
                }
            onDispose { reg.remove(); titlesReg.remove(); catalogReg.remove() }
        }
    }

    // ------------------------------------------------------------------
    // Admin catalog operations (rules enforce admin-only server-side)
    // ------------------------------------------------------------------
    fun saveItem(target: ShopItem?, name: String, description: String, price: Long) {
        val data = mapOf(
            "name" to name,
            "description" to description,
            "price" to price,
            "active" to (target?.active ?: true)
        )
        busy = true
        val op = if (target?.docId != null) {
            db.collection("shop_items").document(target.docId).update(data)
        } else {
            db.collection("shop_items").add(
                data + mapOf("createdAt" to System.currentTimeMillis())
            )
        }
        op.addOnSuccessListener {
            busy = false
            editing = null
            creating = false
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            busy = false
            Toast.makeText(context, "Save failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun toggleActive(item: ShopItem) {
        val id = item.docId ?: return
        busy = true
        db.collection("shop_items").document(id)
            .update("active", !item.active)
            .addOnSuccessListener { busy = false }
            .addOnFailureListener { e ->
                busy = false
                Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    fun deleteItem(item: ShopItem) {
        val id = item.docId ?: return
        busy = true
        db.collection("shop_items").document(id)
            .delete()
            .addOnSuccessListener {
                busy = false
                deleting = null
                Toast.makeText(context, "${item.name} removed", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                busy = false
                Toast.makeText(context, "Delete failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    fun seedDefaults() {
        busy = true
        val batch = db.batch()
        DEFAULT_CATALOG.forEach { item ->
            val doc = db.collection("shop_items").document()
            batch.set(
                doc,
                mapOf(
                    "name" to item.name,
                    "description" to item.description,
                    "price" to item.price,
                    "active" to true,
                    "createdAt" to System.currentTimeMillis()
                )
            )
        }
        batch.commit()
            .addOnSuccessListener {
                busy = false
                Toast.makeText(context, "Default items added to the shop", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                busy = false
                Toast.makeText(context, "Seed failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    // ------------------------------------------------------------------
    // Purchase: transactional against the LIVE item doc + balance
    // ------------------------------------------------------------------
    fun purchase(item: ShopItem) {
        val me = uid
        val id = item.docId
        if (me == null || id == null) return
        purchasing = true
        val playerRef = db.collection("players").document(me)
        val itemRef = db.collection("shop_items").document(id)
        val titleRef = db.collection("player_titles").document("${me}_shop_$id")
        db.runTransaction { tx ->
            val itemSnap = tx.get(itemRef)
            if (!itemSnap.exists() || itemSnap.getBoolean("active") != true) {
                return@runTransaction kotlin.Result.failure<Long>(IllegalStateException("item-unavailable"))
            }
            val livePrice = itemSnap.getLong("price") ?: item.price
            val liveName = itemSnap.getString("name") ?: item.name
            val balance = tx.get(playerRef).getLong("coins") ?: 0L
            if (balance < livePrice) {
                return@runTransaction kotlin.Result.failure<Long>(IllegalStateException("insufficient-coins"))
            }
            tx.update(playerRef, "coins", balance - livePrice)
            tx.set(
                titleRef,
                mapOf(
                    "playerId" to me,
                    "title" to liveName,
                    "source" to "shop",
                    "itemId" to id,
                    "price" to livePrice,
                    "awardedAt" to System.currentTimeMillis()
                )
            )
            kotlin.Result.success(balance - livePrice)
        }.addOnSuccessListener { _ ->
            purchasing = false
            buying = null
            Toast.makeText(context, "Purchased ${item.name}!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { _ ->
            purchasing = false
            buying = null
            Toast.makeText(
                context,
                "Purchase failed - check your coin balance and connection",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun equip(item: ShopItem) {
        val me = uid ?: return
        db.collection("players").document(me)
            .update("title", item.name)
            .addOnSuccessListener {
                Toast.makeText(context, "${item.name} equipped", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Could not equip: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    val visibleItems = if (catalogLoaded && items.isNotEmpty()) {
        if (manageMode && isAdmin) items else items.filter { it.active }
    } else {
        DEFAULT_CATALOG
    }
    val fallbackActive = catalogLoaded && items.isEmpty()

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = if (manageMode) "Manage Shop" else "Coin Shop",
                onBack = {
                    if (manageMode) manageMode = false
                    else (context as? android.app.Activity)?.finish()
                },
                right = {
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

            // Admin: switch between shopping and catalog management.
            if (isAdmin) {
                TextButton(
                    onClick = { manageMode = !manageMode },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        if (manageMode) "← Back to shop" else "Manage items (admin)",
                        color = ZsCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

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
                    if (fallbackActive && isAdmin) {
                        item {
                            ZSCard(highlight = ZsGold) {
                                Text(
                                    "SHOP NOT SET UP YET",
                                    color = ZsGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "These are the built-in defaults (preview only). Add them to the shop to make them purchasable, or add your own items.",
                                    color = ZsTextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                ZSButton("Add default items", { seedDefaults() }, enabled = !busy)
                            }
                        }
                    }

                    items(visibleItems, key = { it.docId ?: it.name }) { item ->
                        val owned = item.name in ownedTitles
                        val equipped = equippedTitle == item.name
                        ZSCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            item.name,
                                            color = if (item.active) ZsTextPrimary else ZsTextMuted,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (!item.active) {
                                            Spacer(Modifier.width(6.dp))
                                            ZSBadgeCompat("HIDDEN")
                                        }
                                    }
                                    Text(
                                        item.description,
                                        color = ZsTextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 2
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                when {
                                    manageMode && isAdmin -> {
                                        Column(horizontalAlignment = Alignment.End) {
                                            TextButton(onClick = { editing = item }, enabled = !busy) {
                                                Text("Edit", color = ZsCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            Row {
                                                TextButton(onClick = { if (item.docId != null) toggleActive(item) }, enabled = !busy) {
                                                    Text(
                                                        if (item.active) "Hide" else "Show",
                                                        color = ZsGold, fontSize = 12.sp, fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                TextButton(onClick = { deleting = item }, enabled = !busy && item.docId != null) {
                                                    Text("Delete", color = ZsDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                    equipped -> ZSBadgeCompat("EQUIPPED")
                                    owned -> TextButton(onClick = { equip(item) }) {
                                        Text("Equip", color = ZsCyan, fontWeight = FontWeight.Bold)
                                    }
                                    item.docId == null -> ZSBadgeCompat("SOON")
                                    else -> TextButton(onClick = { buying = item }, enabled = !purchasing) {
                                        Text("${item.price} 🪙", color = ZsGold, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (manageMode && isAdmin) {
                        item {
                            ZSButton(
                                "Add new item",
                                { creating = true },
                                Modifier.fillMaxWidth(),
                                enabled = !busy
                            )
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

    // Delete confirmation.
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!busy) deleting = null },
            title = { Text("Delete ${item.name}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "The item disappears from the shop. Players who already own it keep the title.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteItem(item) }, enabled = !busy) {
                    Text("DELETE", color = ZsDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }, enabled = !busy) { Text("Cancel") }
            }
        )
    }

    // Add / Edit dialog.
    val dialogItem = if (creating) ShopItem(null, "", "", 100, true, ZsCyan) else editing
    dialogItem?.let { base ->
        var name by remember(base.docId ?: "new") { mutableStateOf(base.name) }
        var description by remember(base.docId ?: "new") { mutableStateOf(base.description) }
        var price by remember(base.docId ?: "new") { mutableStateOf(base.price.toString()) }
        val parsedPrice = price.toLongOrNull()
        val valid = name.trim().length >= 2 && parsedPrice != null && parsedPrice > 0
        AlertDialog(
            onDismissRequest = { if (!busy) { creating = false; editing = null } },
            title = {
                Text(
                    if (creating) "Add shop item" else "Edit ${base.name}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    ZSField(value = name, onValueChange = { name = it }, label = "Title name")
                    Spacer(Modifier.height(10.dp))
                    ZSField(value = description, onValueChange = { description = it }, label = "Description")
                    Spacer(Modifier.height(10.dp))
                    ZSField(
                        value = price,
                        onValueChange = { price = it.filter { c -> c.isDigit() }.take(7) },
                        label = "Price (coins)",
                        keyboardType = KeyboardType.Number
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { saveItem(if (creating) null else base, name.trim(), description.trim(), parsedPrice ?: 0) },
                    enabled = valid && !busy
                ) {
                    Text("SAVE", color = ZsCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { creating = false; editing = null }, enabled = !busy) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Local badge helper (same styling as the shared ZSBadge). */
@Composable
private fun ZSBadgeCompat(text: String) {
    Text(
        text = text,
        modifier = androidx.compose.ui.Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ZsTextMuted.copy(alpha = 0.18f))
            .border(1.dp, ZsTextMuted.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = ZsTextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
    )
}

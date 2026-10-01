package com.zerostress.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGreen
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

/**
 * In-app dashboard layout editor.
 *
 * Reorder / hide / show the six dashboard sections (identity, score,
 * performance, recentForm, leaderboard, nextMatch). The layout is stored
 * in SharedPreferences as JSON through [ZsDashboardLayout], and the
 * player dashboard renders its sections in that order each composition,
 * so edits apply instantly without rebuilding the APK.
 */
class DashboardLayoutEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroStressTheme {
                LayoutEditorScreen()
            }
        }
    }
}

private val SECTION_LABELS = mapOf(
    "identity" to "Identity Card",
    "score" to "Score Core",
    "performance" to "Performance",
    "recentForm" to "Recent Form",
    "leaderboard" to "Leaderboard Button",
    "nextMatch" to "Next Match Countdown"
)

@Composable
private fun LayoutEditorScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var order by remember { mutableStateOf(ZsDashboardLayout.loadOrder(context)) }
    var hidden by remember { mutableStateOf(ZsDashboardLayout.loadHidden(context)) }
    var confirmReset by remember { mutableStateOf(false) }

    fun save() {
        ZsDashboardLayout.save(context, order, hidden)
    }

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(title = "Edit Dashboard Layout", onBack = {
                (context as? android.app.Activity)?.finish()
            })

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                ZSCard(highlight = ZsAccent) {
                    Text(
                        "Changes save on this device and apply to your dashboard " +
                            "instantly. Tap a row to hide/show it, arrows to reorder.",
                        color = ZsTextSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(10.dp))

                order.forEachIndexed { idx, id ->
                    val isHidden = id in hidden
                    SectionRow(
                        position = idx + 1,
                        label = SECTION_LABELS[id] ?: id,
                        isHidden = isHidden,
                        isFirst = idx == 0,
                        isLast = idx == order.lastIndex,
                        onUp = {
                            if (idx > 0) {
                                order = order.toMutableList().apply {
                                    val tmp = this[idx - 1]
                                    this[idx - 1] = this[idx]
                                    this[idx] = tmp
                                }
                                save()
                            }
                        },
                        onDown = {
                            if (idx < order.lastIndex) {
                                order = order.toMutableList().apply {
                                    val tmp = this[idx + 1]
                                    this[idx + 1] = this[idx]
                                    this[idx] = tmp
                                }
                                save()
                            }
                        },
                        onToggle = {
                            hidden = if (isHidden) hidden - id else hidden + id
                            save()
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(10.dp))
                StudioButton("RESET TO DEFAULT", ZsDanger) {
                    confirmReset = true
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Live preview (hidden sections are dimmed):",
                    color = ZsTextMuted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                MiniPreview(order, hidden)

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset layout?") },
            text = { Text("The dashboard goes back to its default arrangement.") },
            confirmButton = {
                TextButton(onClick = {
                    ZsDashboardLayout.reset(context)
                    order = ZsDashboardLayout.loadOrder(context)
                    hidden = ZsDashboardLayout.loadHidden(context)
                    confirmReset = false
                }) { Text("RESET", color = ZsDanger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionRow(
    position: Int,
    label: String,
    isHidden: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onToggle: () -> Unit
) {
    val accent = if (isHidden) ZsTextMuted else ZsCyan
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("$position", color = accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = ZsTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (isHidden) "Hidden" else "Visible",
                    color = if (isHidden) ZsTextMuted else ZsGreen,
                    fontSize = 11.sp
                )
            }
            MiniButton("\u25B2", enabled = !isFirst, onClick = onUp)
            Spacer(Modifier.width(6.dp))
            MiniButton("\u25BC", enabled = !isLast, onClick = onDown)
            Spacer(Modifier.width(6.dp))
            MiniButton(if (isHidden) "SHOW" else "HIDE", enabled = true, onClick = onToggle)
        }
    }
}

@Composable
private fun MiniButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) ZsAccent.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.dp,
                if (enabled) ZsAccent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (enabled) ZsAccent else ZsTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MiniPreview(order: List<String>, hidden: Set<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF050814), Color(0xFF04060F))))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        order.forEach { id ->
            val isHidden = id in hidden
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isHidden) Color.White.copy(alpha = 0.02f) else Color.White.copy(alpha = 0.06f)
                    )
                    .border(
                        1.dp,
                        if (isHidden) Color.White.copy(alpha = 0.04f) else ZsAccent.copy(alpha = 0.35f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 10.dp, horizontal = 10.dp)
            ) {
                Text(
                    (SECTION_LABELS[id] ?: id) + if (isHidden) "  (hidden)" else "",
                    color = if (isHidden) ZsTextMuted else ZsTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StudioButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

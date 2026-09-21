package com.zerostress.manager.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// ZsPngIcon lives in this same package (ui/PngIcon.kt) — no import needed
import com.zerostress.manager.R
import com.zerostress.manager.ui.theme.ZsBgEnd
import com.zerostress.manager.ui.theme.ZsBgMid
import com.zerostress.manager.ui.theme.ZsBgStart
import com.zerostress.manager.ui.theme.ZsBorder
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsDanger
import com.zerostress.manager.ui.theme.ZsGold
import com.zerostress.manager.ui.theme.ZsSuccess
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsPrimaryDark
import com.zerostress.manager.ui.theme.ZsPurple
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

/**
 * Full-screen deep-space gradient with the Neon Glass v4 ambient light wash:
 * a violet glow top-right and a cyan glow top-left, like light spilling from
 * a stage. Used as the base of every screen.
 */
@Composable
fun ZSBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ZsBgStart, ZsBgMid, ZsBgEnd)))
            .drawBehind {
                // Ambient neon washes (screen-space, cheap radial layers).
                val violet = ZsPurple.copy(alpha = 0.10f)
                val cyan = ZsPrimary.copy(alpha = 0.08f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(violet, Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.02f),
                        radius = size.width * 0.85f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(cyan, Color.Transparent),
                        center = Offset(size.width * 0.02f, size.height * 0.10f),
                        radius = size.width * 0.75f
                    )
                )
            },
        content = content
    )
}

/**
 * v4 section header: upright ExtraBold uppercase title with wide tracking and
 * a muted subtitle, on the page background (the mockups use no gradient
 * bands - neon lighting comes from ZSBackground and highlighted cards).
 */
@Composable
fun ZSHeroHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            title.uppercase(),
            color = ZsTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = ZsTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            )
        }
    }
}

/** Scrollable column with the standard screen padding. */
@Composable
fun ZSScreenColumn(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding)
            .padding(bottom = 24.dp),
        content = content
    )
}

/**
 * Shared glassmorphism card (Neon Glass v4): translucent white fill on the
 * dark gradient, thin light stroke, 18dp rounded corners. Pass [highlight]
 * to tint the border and add a soft outer neon glow. Pass [onClick] to make
 * it tappable.
 */
@Composable
fun ZSCard(
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    highlight: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var m = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(corner))
        .background(ZsCard)
        .border(
            width = if (highlight != null) 1.5.dp else 1.dp,
            color = highlight ?: Color.White.copy(alpha = 0.09f),
            shape = RoundedCornerShape(corner)
        )
        .drawBehind {
            if (highlight != null) {
                // Soft outer neon glow behind highlighted cards.
                drawRoundRect(
                    color = highlight.copy(alpha = 0.28f),
                    topLeft = Offset(-6.dp.toPx(), -6.dp.toPx()),
                    size = Size(size.width + 12.dp.toPx(), size.height + 12.dp.toPx()),
                    cornerRadius = CornerRadius(corner.toPx() + 6.dp.toPx())
                )
            }
        }
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(modifier = m.padding(14.dp), content = content)
}

/** Full-width accent button (Neon Glass gradient style). */
@Composable
fun ZSButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = ZsPrimary,
    textColor: Color = Color(0xFF04101A),
    enabled: Boolean = true,
    height: Dp = 50.dp
) {
    // Neon Glass signature: the default action button is a cyan-to-violet
    // gradient. Callers passing a custom container color keep a solid fill
    // (danger/secondary actions).
    val useGradient = container == ZsPrimary
    val shape = RoundedCornerShape(14.dp)
    val bg: Modifier = when {
        enabled && useGradient -> Modifier.background(
            brush = Brush.horizontalGradient(listOf(ZsPrimary, ZsPurple)),
            shape = shape
        )
        !enabled -> Modifier.background(color = ZsBorder.copy(alpha = 0.35f), shape = shape)
        else -> Modifier.background(color = container, shape = shape)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(bg)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(),
            color = if (enabled) textColor else ZsTextMuted,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            letterSpacing = 1.2.sp
        )
    }
}

/** Outlined text field styled for the dark theme. */
@Composable
fun ZSField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder, color = ZsTextMuted) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = minLines,
        trailingIcon = trailing,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ZsPrimary.copy(alpha = 0.7f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedLabelColor = ZsPrimary,
            unfocusedLabelColor = ZsTextMuted,
            focusedTextColor = ZsTextPrimary,
            unfocusedTextColor = ZsTextPrimary,
            cursorColor = ZsPrimary,
            focusedContainerColor = ZsCard,
            unfocusedContainerColor = ZsCard
        )
    )
}

/** Top bar with optional back navigation and right-side slot. */
@Composable
fun ZSTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    right: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ZsCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text("Back", color = ZsCyan, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(start = if (onBack != null) 0.dp else 12.dp),
            color = ZsTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp
        )
        right?.invoke(this)
    }
}

/** Small colored badge (status chips, rank labels...). */
@Composable
fun ZSBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

/** Section heading used between groups of content. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
        color = ZsTextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

/** Centered loading spinner inside a full-width box. */
@Composable
fun LoadingBox(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = ZsCyan,
            strokeWidth = 3.dp
        )
    }
}

/** Horizontal progress bar with a label above it. */
@Composable
fun ZSProgress(
    fraction: Float,
    label: String? = null,
    modifier: Modifier = Modifier,
    color: Color = ZsPrimary
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, color = ZsTextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(6.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.07f)
        )
    }
}

/** Empty-state message shown when a list has no data. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = ZsTextMuted, fontSize = 15.sp)
    }
}

/** Simple glass stat tile (label over value). */
@Composable
fun ZSStat(
    label: String,
    value: String,
    color: Color = ZsCyan,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ZsCard)
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = ZsTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = color,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * Password text field with a show/hide toggle icon (vector drawables, no
 * material-icons-extended dependency needed).
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var visible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    ZSField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        isPassword = !visible,
        modifier = modifier,
        trailing = {
            IconButton(
                onClick = { visible = !visible },
                modifier = Modifier.size(24.dp)
            ) {
                ZsPngIcon(
                    if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                    size = 22.dp,
                    tint = ZsTextMuted,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        }
    )
}

/**
 * Grid tile used for dashboard navigation buttons.
 * Pass [iconRes] (a PNG drawable id, e.g. R.drawable.ic_menu_calendar) instead of
 * [emoji] — the emoji parameter is kept only for legacy callers and is ignored
 * when an iconRes is given.
 */
@Composable
fun ZSMenuTile(
    emoji: String = "",
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = ZsPrimary,
    iconRes: Int? = null
) {
    // Press-scale animation: tile shrinks slightly while touched.
    var pressed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val tileScale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "tileScale")
    Column(
        modifier = modifier
            .scale(tileScale)
            .clip(RoundedCornerShape(16.dp))
            .background(ZsCard)
            .drawBehind {
                // Accent glow dot in the top-left corner (Neon Glass tile).
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width * 0.16f, size.height * 0.12f),
                        radius = size.width * 0.55f
                    )
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try { awaitRelease() } finally { pressed = false }
                    },
                    onTap = { onClick() }
                )
            }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (iconRes != null) {
            ZsPngIcon(iconRes, size = 30.dp)
        } else {
            Text(emoji, fontSize = 26.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = ZsTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )
    }
}

/** Row helper: label on the left, value on the right. */
@Composable
fun ZSKeyValue(label: String, value: String, valueColor: Color = ZsTextPrimary, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = ZsTextMuted, fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** One destination in the bottom navigation bar. */
data class ZSNavItem(
    val iconRes: Int,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * Opens one of the five main tabs WITHOUT stacking duplicate activities.
 * Repeatedly calling plain startActivity() piles a new Activity instance
 * (each with its own Firestore listeners and glow animations) on the back
 * stack on every tap - freezing the app and ballooning RAM/CPU/GPU.
 * FLAG_ACTIVITY_REORDER_TO_FRONT instead brings the existing instance to
 * the front, so each tab exists at most once.
 */
fun android.content.Context.launchTab(cls: Class<*>) {
    startActivity(
        android.content.Intent(this, cls).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
    )
}

/**
 * The six bottom-nav destinations shared by every main screen.
 * [current] is 0=Home, 1=Performance, 2=Leaderboard, 3=Tournaments, 4=Profile,
 * 5=More. The More tab hosts every destination that used to be a home-screen
 * tile, keeping HOME a clean stats surface.
 */
@Composable
fun zsNavItems(current: Int, context: android.content.Context): List<ZSNavItem> = listOf(
    ZSNavItem(R.drawable.ic_nav_home, "Home", current == 0) {
        if (current != 0) context.launchTab(com.zerostress.manager.PlayerDashboardActivity::class.java)
    },
    ZSNavItem(R.drawable.ic_nav_perf, "Perf", current == 1) {
        if (current != 1) context.launchTab(com.zerostress.manager.PerformanceGraphsActivity::class.java)
    },
    ZSNavItem(R.drawable.ic_nav_board, "Board", current == 2) {
        if (current != 2) context.launchTab(com.zerostress.manager.LeaderboardActivity::class.java)
    },
    ZSNavItem(R.drawable.ic_nav_tournament, "Cups", current == 3) {
        if (current != 3) context.launchTab(com.zerostress.manager.TournamentActivity::class.java)
    },
    ZSNavItem(R.drawable.ic_nav_profile, "Profile", current == 4) {
        if (current != 4) context.launchTab(com.zerostress.manager.ProfileActivity::class.java)
    },
    ZSNavItem(R.drawable.ic_menu_settings, "More", current == 5) {
        if (current != 5) context.launchTab(com.zerostress.manager.MoreActivity::class.java)
    }
)

/**
 * Neon Glass bottom navigation shell: translucent glass bar pinned to the
 * bottom, thin light top stroke, active destination tinted cyan with a glow
 * dot above the icon. Place it as the LAST child of a Column whose content
 * is a Weight(1f) screen body (see PlayerDashboardActivity for the pattern).
 */
@Composable
fun ZSBottomNav(items: List<ZSNavItem>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ZsBgMid.copy(alpha = 0.92f))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.09f))
            .navigationBarsPadding()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val tint = if (item.selected) ZsPrimary else ZsTextMuted
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClick = item.onClick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (item.selected) {
                    Box(
                        Modifier
                            .padding(bottom = 3.dp)
                            .size(width = 16.dp, height = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ZsPrimary)
                    )
                }
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = item.label,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.label,
                    color = tint,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}

/**
 * Circular progress ring with a content slot in the middle (Neon Glass stat
 * ring). [fraction] is clamped to 0..1; the arc sweeps a cyan-to-violet
 * gradient over a faint track.
 */
@Composable
fun ZSRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = ZsPrimary,
    trackColor: Color = Color.White.copy(alpha = 0.08f),
    stroke: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2 + 1.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = trackColor,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(color, ZsPurple, color)),
                startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

/** Lightweight sparkline: neon line with a soft gradient fill underneath. */
@Composable
fun ZSSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = ZsPrimary,
    strokeWidth: Dp = 2.5.dp
) {
    Canvas(modifier = modifier.fillMaxWidth().height(64.dp)) {
        if (values.size < 2) return@Canvas
        val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val minV = values.minOrNull() ?: 0f
        val range = (maxV - minV).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1)
        fun y(v: Float) = size.height - 6.dp.toPx() -
            ((v - minV) / range) * (size.height - 12.dp.toPx())
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            if (i == 0) line.moveTo(x, y(v)) else line.lineTo(x, y(v))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent))
        )
        drawPath(line, color = color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
    }
}

/** Lightweight neon bar chart (kills per match, etc.). */
@Composable
fun ZSBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = ZsPrimary
) {
    Canvas(modifier = modifier.fillMaxWidth().height(64.dp)) {
        if (values.isEmpty()) return@Canvas
        val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val gap = 4.dp.toPx()
        val barW = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = (v / maxV) * (size.height - 4.dp.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f))),
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h.coerceAtLeast(2.dp.toPx())),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }
    }
}

/**
 * Glass filter chip row. The selected chip gets the cyan-to-violet gradient
 * with dark ink text; the rest stay translucent glass.
 */
@Composable
fun ZSFilterChips(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) Brush.horizontalGradient(listOf(ZsPrimary, ZsPurple))
                        else Brush.horizontalGradient(listOf(ZsCard, ZsCard))
                    )
                    .border(
                        1.dp,
                        if (selected) Color.Transparent else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                color = if (selected) Color(0xFF04101A) else ZsTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/** Trend arrow: 1 = up (green), -1 = down (red), 0 = flat (muted dash). */
@Composable
fun ZSTrend(direction: Int, modifier: Modifier = Modifier) {
    if (direction > 0) {
        ZsPngIcon(
            R.drawable.ic_trend_up, size = 14.dp, tint = ZsSuccess,
            modifier = modifier, contentDescription = "Trending up"
        )
    } else if (direction < 0) {
        ZsPngIcon(
            R.drawable.ic_trend_down, size = 14.dp, tint = ZsDanger,
            modifier = modifier, contentDescription = "Trending down"
        )
    } else {
        Box(
            modifier
                .padding(horizontal = 3.dp)
                .size(width = 8.dp, height = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(ZsTextMuted)
        )
    }
}

/**
 * Glass initials avatar with a neon ring. The name is reduced to up to two
 * initials so roster rows work everywhere without any image loading.
 *
 * When `avatarUrl` is supplied (players/{uid}.avatarUrl - Cloudinary CDN),
 * the cloud photo is rendered instead of initials, keeping the same ring.
 */
@Composable
fun ZSAvatar(
    name: String,
    size: Dp = 40.dp,
    ringColor: Color = ZsPrimary,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
) {
    if (!avatarUrl.isNullOrBlank()) {
        ZsRemoteAvatar(
            url = avatarUrl,
            size = size,
            modifier = modifier,
            ringColor = ringColor
        )
        return
    }
    val initials = name.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 50))
            .background(Brush.linearGradient(listOf(Color(0xFF1C2033), Color(0xFF11131F))))
            .border(1.5.dp, ringColor, RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            color = ZsTextSecondary,
            fontSize = (size.value * 0.34f).sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
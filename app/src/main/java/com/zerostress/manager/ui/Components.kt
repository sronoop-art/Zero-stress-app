package com.zerostress.manager.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// ZsPngIcon lives in this same package (ui/PngIcon.kt) — no import needed
import com.zerostress.manager.R
import com.zerostress.manager.ui.theme.ZsBgStart
import com.zerostress.manager.ui.theme.ZsBgMid
import com.zerostress.manager.ui.theme.ZsBorder
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsPrimaryDark
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

/**
 * Full-screen dark gradient with a subtle carbon-fiber weave texture,
 * used as the base of every screen (Carbon GT Racing theme).
 */
@Composable
fun ZSBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ZsBgStart, ZsBgMid, ZsBgStart)))
            .drawBehind {
                // Fine diagonal weave - reads as carbon fiber at low alpha.
                val step = 14.dp.toPx()
                val stroke = 3.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.025f),
                        start = Offset(x, 0f),
                        end = Offset(x + size.height, size.height),
                        strokeWidth = stroke
                    )
                    x += step
                }
            },
        content = content
    )
}

/**
 * Racing hero header: slanted red gradient band with italic title.
 * Used at the top of dashboard screens.
 */
@Composable
fun ZSHeroHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 0.dp))
            .background(Brush.horizontalGradient(listOf(ZsPrimaryDark, ZsPrimary)))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column {
            Text(
                title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
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
 * Shared card with the Carbon GT look: sharp-ish corners, thin steel border
 * and a short racing-red speed stripe along the top edge.
 * Pass [onClick] to make it tappable.
 */
@Composable
fun ZSCard(
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
    highlight: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var m = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(corner))
        .background(ZsCard)
        .drawBehind {
            // Speed stripe along the top edge (racing accent).
            drawRect(
                color = highlight ?: ZsPrimary,
                topLeft = Offset(0f, 0f),
                size = Size(size.width * 0.34f, 3.dp.toPx())
            )
        }
        .border(
            width = if (highlight != null) 1.5.dp else 1.dp,
            color = highlight ?: ZsBorder.copy(alpha = 0.6f),
            shape = RoundedCornerShape(corner)
        )
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(modifier = m.padding(14.dp), content = content)
}

/** Full-width accent button. */
@Composable
fun ZSButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = ZsPrimary,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    height: Dp = 50.dp
) {
    // Carbon GT signature: the default action button is a red-to-white speed
    // gradient. Callers passing a custom container color keep a solid fill
    // (danger/secondary actions).
    val useGradient = container == ZsPrimary
    val shape = RoundedCornerShape(6.dp)
    val bg: Modifier = when {
        enabled && useGradient -> Modifier.background(
            brush = Brush.horizontalGradient(listOf(ZsPrimary, Color(0xFFFFC9D1))),
            shape = shape
        )
        !enabled -> Modifier.background(color = ZsBorder.copy(alpha = 0.5f), shape = shape)
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
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            fontSize = 15.sp,
            letterSpacing = 1.sp
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
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // Red speed stripe on the left edge (racing input style).
                val stripeW = 3.5.dp.toPx()
                val stripeH = size.height * 0.42f
                drawRoundRect(
                    color = ZsPrimary.copy(alpha = 0.9f),
                    topLeft = Offset(0f, (size.height - stripeH) / 2f),
                    size = Size(stripeW, stripeH),
                    cornerRadius = CornerRadius(stripeW, stripeW)
                )
            },
        label = { Text(label) },
        placeholder = { Text(placeholder, color = ZsTextMuted) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = minLines,
        trailingIcon = trailing,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ZsPrimary.copy(alpha = 0.8f),
            unfocusedBorderColor = ZsBorder.copy(alpha = 0.7f),
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
            fontStyle = FontStyle.Italic
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
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = ZsCard
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

/** Simple stat tile (label over value). */
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
            .clip(RoundedCornerShape(8.dp))
            .background(ZsCard)
            .border(1.dp, ZsBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = ZsTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = color,
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic
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
            .clip(RoundedCornerShape(10.dp))
            .background(ZsCard)
            .drawBehind {
                // Accent speed stripe across the top edge (mockup tile style).
                drawRect(
                    color = accent,
                    topLeft = Offset.Zero,
                    size = Size(size.width, 3.dp.toPx())
                )
            }
            .border(1.dp, ZsBorder.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
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
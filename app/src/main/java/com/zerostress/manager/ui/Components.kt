package com.zerostress.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostress.manager.ui.theme.ZsBgStart
import com.zerostress.manager.ui.theme.ZsBgMid
import com.zerostress.manager.ui.theme.ZsBorder
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsCyan
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

/**
 * Full-screen dark gradient used as the base of every screen.
 */
@Composable
fun ZSBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ZsBgStart, ZsBgMid, ZsBgStart))),
        content = content
    )
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
 * Shared card with the Zero Stress look (dark surface, rounded corners, subtle border).
 * Pass [onClick] to make it tappable.
 */
@Composable
fun ZSCard(
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
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
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = textColor,
            disabledContainerColor = ZsBorder.copy(alpha = 0.5f),
            disabledContentColor = ZsTextMuted
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ZsPrimary,
            unfocusedBorderColor = ZsBorder,
            focusedLabelColor = ZsCyan,
            unfocusedLabelColor = ZsTextMuted,
            focusedTextColor = ZsTextPrimary,
            unfocusedTextColor = ZsTextPrimary,
            cursorColor = ZsCyan,
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
            fontWeight = FontWeight.ExtraBold
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
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
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
            .clip(RoundedCornerShape(14.dp))
            .background(ZsCard)
            .border(1.dp, ZsBorder.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = ZsTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = color, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * Grid tile used for dashboard navigation buttons.
 * Pass [iconRes] (a PNG drawable id, e.g. R.drawable.ic_menu_calendar) instead of
 * [emoji] — the emoji parameter is kept only for legacy callers and is ignored
 * when an iconRes is given.
 */
@Composable
fun ZSMenuTile(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = ZsPrimary,
    iconRes: Int? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ZsCard)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
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
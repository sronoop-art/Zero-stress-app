package com.zerostress.manager.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsAccent
import com.zerostress.manager.ui.theme.ZsCard
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

@Composable
fun ZSBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize().background(ZsCard)
    ) {
        content()
    }
}

@Composable
fun ZSTopBar(
    title: String,
    onBack: () -> Unit = {},
    right: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZsCard)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != {}) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ZsAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = title,
            color = ZsTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f)
        )
        if (right != null) {
            right()
        }
    }
}

@Composable
fun ZSCard(
    highlight: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(if (highlight != null) highlight.copy(alpha = 0.18f) else ZsCard)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun ZSField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    leading: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = ZsTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZsCard)
                .alpha(0.6f)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = value.ifEmpty { placeholder },
                color = if (value.isNotEmpty()) ZsTextPrimary else ZsTextMuted,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var visible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = label,
            color = ZsTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZsCard)
                .alpha(0.6f)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                color = if (value.isNotEmpty()) ZsTextPrimary else ZsTextMuted,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            if (!visible) {
                IconButton(
                    onClick = { visible = true },
                    modifier = Modifier.alpha(0.8f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Show password",
                        tint = ZsTextSecondary
                    )
                }
            } else {
                IconButton(
                    onClick = { visible = false },
                    modifier = Modifier.alpha(0.8f)
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Hide password",
                        tint = ZsTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = ZsTextMuted,
            fontSize = 15.sp
        )
    }
}

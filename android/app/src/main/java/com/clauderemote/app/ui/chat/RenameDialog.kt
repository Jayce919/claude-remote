package com.clauderemote.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.cdsColors
import kotlinx.coroutines.delay

/**
 * 重命名对话框。顶栏标题和侧边栏菜单共用一套，保证两处观感一致。
 */
@Composable
fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val c = cdsColors
    var value by remember(initial) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }

    fun submit() {
        val clean = value.trim()
        if (clean.isNotEmpty()) onConfirm(clean)
    }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface2,
        shape = RoundedCornerShape(CdsDimens.radiusComposer),
        title = {
            Text(
                tr("重命名对话", "Rename chat"),
                color = c.textPrimary,
                fontSize = CdsDimens.titleSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CdsFontFamilies.sans,
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface1, RoundedCornerShape(CdsDimens.radiusPanel))
                    // 重命名输入框同样是输入容器，边界要和别处一致
                    .border(1.dp, c.borderControl, RoundedCornerShape(CdsDimens.radiusPanel))
                    .padding(horizontal = CdsDimens.lg, vertical = CdsDimens.md2),
            ) {
                if (value.isEmpty()) {
                    Text(
                        tr("输入新标题", "Enter new title"),
                        color = c.textMuted,
                        fontSize = CdsDimens.bodySmallSize,
                        fontFamily = CdsFontFamilies.sans,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = { if (it.length <= 60) value = it },
                    textStyle = TextStyle(
                        color = c.textPrimary,
                        fontSize = CdsDimens.bodySmallSize,
                        fontFamily = CdsFontFamilies.sans,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) {
                Text(
                    tr("保存", "Save"),
                    color = if (value.isBlank()) c.textMuted else c.accentText,
                    fontSize = CdsDimens.bodySmallSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CdsFontFamilies.sans,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    tr("取消", "Cancel"),
                    color = c.textSecondary,
                    fontSize = CdsDimens.bodySmallSize,
                    fontFamily = CdsFontFamilies.sans,
                )
            }
        },
    )
}

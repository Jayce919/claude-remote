package com.clauderemote.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.clauderemote.app.data.Connection
import com.clauderemote.app.ui.i18n.LanguageSwitcher
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.cdsColors

@Composable
fun SetupScreen(
    initial: Connection?,
    connecting: Boolean,
    error: String?,
    language: String,
    onLanguage: (String) -> Unit,
    onConnect: (Connection) -> Unit,
) {
    val c = cdsColors
    var url by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var token by remember { mutableStateOf(initial?.token ?: "") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.pageBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CdsDimens.xxl, vertical = CdsDimens.huge),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Claude Remote", color = c.textPrimary, fontSize = CdsDimens.titleSize, fontFamily = CdsFontFamilies.sans)
            Text(
                tr("连接你电脑上的 Claude Code", "Connect to Claude Code on your computer"),
                color = c.textSecondary,
                fontSize = CdsDimens.bodySize,
                modifier = Modifier.padding(top = CdsDimens.md, bottom = CdsDimens.xxxl),
            )

            FieldLabel(tr("服务地址", "Server address"))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CdsDimens.radiusPanel),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = CdsFontFamilies.sans,
                    fontSize = CdsDimens.bodySize,
                    color = c.textPrimary,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors(),
            )

            FieldLabel(tr("访问令牌", "Access token"), top = CdsDimens.xl)
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = {
                    Text(
                        tr("粘贴访问令牌", "Paste access token"),
                        color = c.textMuted,
                        fontFamily = CdsFontFamilies.sans,
                        fontSize = CdsDimens.bodySize,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CdsDimens.radiusPanel),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = CdsFontFamilies.sans,
                    fontSize = CdsDimens.bodySize,
                    color = c.textPrimary,
                ),
                colors = fieldColors(),
            )

            Button(
                onClick = { onConnect(Connection(url.trim(), token.trim())) },
                enabled = !connecting && url.isNotBlank() && token.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CdsDimens.xxl),
                shape = RoundedCornerShape(CdsDimens.radiusPanel),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.fillPrimary,
                    contentColor = c.onPrimary,
                ),
            ) {
                Text(
                    if (connecting) tr("连接中…", "Connecting…") else tr("连接", "Connect"),
                    fontFamily = CdsFontFamilies.sans,
                    fontSize = CdsDimens.bodySize,
                )
            }

            if (error != null) {
                Text(
                    error,
                    color = c.danger,
                    fontSize = CdsDimens.footnoteSize,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = CdsDimens.lg),
                )
            }

            Text(
                tr(
                    "地址在电脑上运行 node server.js 时打印，形如 https://<你的设备名>.<你的tailnet>.ts.net",
                    "Shown when you run node server.js on your computer, e.g. https://<your-device>.<your-tailnet>.ts.net",
                ),
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = CdsDimens.xxxl),
            )
        }

        LanguageSwitcher(
            language = language,
            onChange = onLanguage,
            compact = true,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = CdsDimens.md, end = CdsDimens.lg),
        )
    }
}

@Composable
private fun FieldLabel(text: String, top: Dp = CdsDimens.md) {
    val c = cdsColors
    Text(
        text,
        color = c.textMuted,
        fontSize = CdsDimens.captionSize,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = top, bottom = CdsDimens.xs),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = cdsColors.accent,
    // 未聚焦也要看得出这是输入框：border 是分隔线级别的灰，围在 12dp 圆角上约等于隐形，
    // 首次配置页又是用户唯一必须填字的地方，所以控件边界单独用 borderControl。
    unfocusedBorderColor = cdsColors.borderControl,
    focusedContainerColor = cdsColors.surface2,
    unfocusedContainerColor = cdsColors.surface2,
    cursorColor = cdsColors.accent,
)

package com.clauderemote.app.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.clauderemote.app.R

/**
 * 应用标志：Claude 官方的十二道星芒。
 *
 * 图形不是描的，是从本机 Claude Desktop 的图标里抠出来的
 * （`Square310x310Logo.scale-400.png` → 去除底色 → `drawable-nodpi/claude_spark.png`），
 * 桌面图标用的 `mipmap-*dpi/ic_launcher_foreground.png` 是同一份形状。
 * 白色原图 + 运行时着色，浅色主题下显示为 clay，深色下也一致。
 */
@Composable
fun ClaudeMark(
    modifier: Modifier = Modifier,
    color: Color = cdsColors.clay,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.claude_spark),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(color),
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

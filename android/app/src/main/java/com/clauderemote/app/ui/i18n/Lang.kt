package com.clauderemote.app.ui.i18n

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.cdsColors

/** 界面语言：简体中文 / English */
enum class Lang(val code: String) {
    ZH("zh"),
    EN("en"),
}

fun langFor(code: String?): Lang = if (code == "en") Lang.EN else Lang.ZH

/** 当前界面语言，由 ClaudeApp 在最外层注入 */
val LocalLang = staticCompositionLocalOf { Lang.ZH }

/** 就地双语：zh 与 en 并排写，切英文时取 en */
@Composable
fun tr(zh: String, en: String): String = if (LocalLang.current == Lang.EN) en else zh

/**
 * 语言切换：两个胶囊。登录页角落用 compact（中 / EN），
 * 设置页用完整词（简体中文 / English）。
 */
@Composable
fun LanguageSwitcher(
    language: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val c = cdsColors
    val current = langFor(language)
    val options = if (compact) {
        listOf(Lang.ZH to "中", Lang.EN to "EN")
    } else {
        listOf(Lang.ZH to "简体中文", Lang.EN to "English")
    }
    val pillHeight = if (compact) 32.dp else 36.dp
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CdsDimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (lang, label) ->
            val selected = current == lang
            // 固定高度 + 文字居中，中英文胶囊不会因为字体回退行高不同而错位
            Box(
                modifier = Modifier
                    .height(pillHeight)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) c.fillPrimary else c.surface2, RoundedCornerShape(50))
                    .border(
                        CdsDimens.hairline,
                        if (selected) c.fillPrimary else c.border,
                        RoundedCornerShape(50),
                    )
                    .clickable { onChange(lang.code) }
                    // 语言名在子节点 Text 上，直接给可点胶囊一个名字
                    .clearAndSetSemantics { contentDescription = label }
                    .padding(horizontal = CdsDimens.lg2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) c.onPrimary else c.textSecondary,
                    fontSize = CdsDimens.footnoteSize,
                    fontFamily = CdsFontFamilies.sans,
                    maxLines = 1,
                )
            }
        }
    }
}

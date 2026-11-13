package com.clauderemote.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Claude 设计系统色板。
 *
 * 数值来自官方 claude.apk 的 resources.arsc（aapt2 dump resources 导出的
 * apk-colors.json），不是按截图估的。手机版比网页端把中性色往暖里推了一档：
 * 主文字 #29261B 而不是网页端的 #0B0B0B；强调色随主题换档，
 * 浅色用深一点的 #C6613F，深色用 #D97757。
 *
 * 强调色分两档：accent 用于填充、描边、图标与指示点，保持品牌原值；
 * accentText 只给强调色的小字文字（链接、可点操作、选中标签），
 * 浅色下比 accent 深一档，保证在浅底上达到 4.5:1，又不动品牌色本身的视觉重心。
 */
@Immutable
data class CdsColors(
    val pageBg: Color,
    val surface0: Color,
    val surface1: Color,
    val surface2: Color,
    /**
     * 第三级表面，专门表示「选中 / 当前 / 表头」这类状态底色。
     * 浅色原来是纯白，和 surface2 完全相同，被六处当成选中态用却看不出变化；
     * 现在压一档暖灰（对白卡 1.18、对页面底色 1.12），能看见但不抢内容。
     * 深色对应设计说明里的暖炭灰（对 surface2 1.15、对页面底色 1.30）。
     */
    val surface3: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentText: Color,
    val clay: Color,
    val onAccent: Color,
    val border: Color,
    val borderStrong: Color,
    /**
     * 只给「必须看得清的控件边界」用：输入框描边、勾选框未选中态的描边。
     * border 是分隔线级别的浅灰（对白底约 1.19），拿来围输入框几乎看不见；
     * borderStrong 被 Toast 和分隔强调用着，一起加深会把轻提示压重。
     * 所以单独开一档，对白卡 ≥ 3:1，满足非文本图形的辨识下限。
     */
    val borderControl: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val userBubble: Color,
    val codeBg: Color,
    val fillPrimary: Color,
    val onPrimary: Color,
    val isDark: Boolean,
)

val LightCdsColors = CdsColors(
    pageBg = Color(0xFFF9F9F7),
    surface0 = Color(0xFFF9F9F7),
    surface1 = Color(0xFFFCFCFB),
    surface2 = Color(0xFFFFFFFF),
    surface3 = Color(0xFFEDECE5),
    textPrimary = Color(0xFF29261B),
    textSecondary = Color(0xFF52514E),
    textMuted = Color(0xFF726F69),
    accent = Color(0xFFC6613F),
    accentText = Color(0xFFB45433),
    clay = Color(0xFFD97757),
    onAccent = Color(0xFFFFFFFF),
    border = Color(0xFFE7E6E1),
    borderStrong = Color(0x330B0B0B),
    borderControl = Color(0xFF8E8C85),
    danger = Color(0xFF8E2626),
    success = Color(0xFF006300),
    warning = Color(0xFF734500),
    userBubble = Color(0x1A97958D),
    codeBg = Color(0xFFF0EFEC),
    fillPrimary = Color(0xFF29261B),
    onPrimary = Color(0xFFFFFFFF),
    isDark = false,
)

val DarkCdsColors = CdsColors(
    pageBg = Color(0xFF0B0B0B),
    surface0 = Color(0xFF0B0B0B),
    surface1 = Color(0xFF151515),
    surface2 = Color(0xFF1A1A19),
    surface3 = Color(0xFF262624),
    textPrimary = Color(0xFFF5F4EF),
    textSecondary = Color(0xFFC3C2B7),
    textMuted = Color(0xFF898781),
    accent = Color(0xFFD97757),
    accentText = Color(0xFFD97757),
    clay = Color(0xFFD97757),
    onAccent = Color(0xFF0B0B0B),
    border = Color(0xFF313130),
    borderStrong = Color(0x33FFFFFF),
    borderControl = Color(0xFF6B6B68),
    danger = Color(0xFFEC7E7E),
    success = Color(0xFF0CA30C),
    warning = Color(0xFFDB9300),
    userBubble = Color(0x0DFFFFFF),
    codeBg = Color(0xFF0D0D0D),
    fillPrimary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF0B0B0B),
    isDark = true,
)

val LocalCdsColors = staticCompositionLocalOf { LightCdsColors }

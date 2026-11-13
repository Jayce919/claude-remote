package com.clauderemote.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.clauderemote.app.data.ChatUiState
import com.clauderemote.app.data.Money
import com.clauderemote.app.data.compactTokens
import com.clauderemote.app.data.providerName
import com.clauderemote.app.data.realModelName
import com.clauderemote.app.ui.i18n.LanguageSwitcher
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.CdsMotion
import com.clauderemote.app.ui.theme.cdsColors
import com.clauderemote.app.ui.theme.reducedMotion
import kotlin.math.roundToInt

/** 返回箭头，官方图标集里没有 */
private val BackArrow: ImageVector by lazy {
    ImageVector.Builder("back", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M15 5l-7 7 7 7"),
            fill = null,
            stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

/**
 * 完整设置页：连接与用量、字号、外观、阅读习惯、关于集中在一页。
 * 左侧抽屉底部的齿轮直接打开这里，不再经过「设置」底部弹层再跳二级页。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: ChatUiState,
    onFontScale: (Float) -> Unit,
    onThemeMode: (String) -> Unit,
    onShowStats: (Boolean) -> Unit,
    onCodeWrap: (Boolean) -> Unit,
    onCurrency: (String) -> Unit,
    onDetailLevel: (String) -> Unit,
    onCollapseCommands: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
    onOpenUsage: () -> Unit,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    val c = cdsColors
    val context = LocalContext.current
    // 语义回调不是 @Composable，先在组合期把文案取出来
    val backLabel = tr("返回", "Back")
    val ui = state.ui
    val provider = state.providerName
    val model = state.realModelName()
    val upstream = state.nodes?.upstream.orEmpty()
    val today = state.usage?.windows?.get("today")
    var confirmDisconnect by remember { mutableStateOf(false) }
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
    }
    // 拖动时只改本地值，松手才提交全局字号：滑块连续、预览即时，
    // 也不会每帧重排整个聊天页。
    var previewScale by remember(ui.fontScale) { mutableFloatStateOf(ui.fontScale) }
    val density = LocalDensity.current
    val previewDensity = Density(
        density.density,
        density.fontScale * (previewScale / ui.fontScale.coerceAtLeast(0.01f)),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.pageBg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 返回键触摸层从 40dp 加到 48dp；外边距各收 4dp，
                // 图标中心与标题栏高度（56dp）都保持原样。
                .padding(horizontal = CdsDimens.xs, vertical = CdsDimens.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onClose() }
                    // 名字在图标上，可点容器自己读不出来；直接给容器一个名字
                    .clearAndSetSemantics { contentDescription = backLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(BackArrow, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(CdsDimens.iconGlyph))
            }
            Text(
                tr("设置", "Settings"),
                color = c.textPrimary,
                fontSize = CdsDimens.titleSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CdsFontFamilies.sans,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = CdsDimens.lg2, vertical = CdsDimens.md),
            verticalArrangement = Arrangement.spacedBy(CdsDimens.xxl),
        ) {
            // ---------------------------------------------------------- 连接
            Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.xs)) {
                SectionLabel(tr("连接", "Connection"))
                InfoRow(tr("供应商", "Provider"), provider.ifBlank { tr("未知", "Unknown") })
                InfoRow(tr("上游", "Upstream"), upstream.ifBlank { tr("未知", "Unknown") })
                InfoRow(tr("模型", "Model"), model.ifBlank { tr("未知", "Unknown") })
                InfoRow(tr("工作目录", "Working directory"), state.cwd.ifBlank { tr("未知", "Unknown") }, mono = false)
                Text(
                    tr(
                        "模型名是 cc-switch 路由到的第三方真实模型；换工作目录会新开会话，上下文不延续。",
                        "The model name is the third-party model cc-switch routes to; changing the working directory starts a new session and context does not carry over.",
                    ),
                    color = c.textMuted,
                    fontSize = CdsDimens.captionSize,
                    lineHeight = CdsDimens.lineCaption,
                    fontFamily = CdsFontFamilies.sans,
                )
                ActionRow(
                    title = tr("用量与花费", "Usage & cost"),
                    subtitle = if (today != null) {
                        tr("今日", "Today") + " ${Money.format(today.costUsd, ui.currency, 2)} · ${compactTokens(today.totalTokens)} tokens"
                    } else {
                        tr("查看当日、7 日、30 日的 token 与花费", "View token usage and cost for today, 7 days and 30 days")
                    },
                    onClick = onOpenUsage,
                )
                ActionRow(
                    title = tr("断开连接", "Disconnect"),
                    subtitle = tr("清除本机保存的地址与令牌，回到连接页", "Clear the saved address and token, and return to the sign-in screen"),
                    danger = true,
                    onClick = { confirmDisconnect = true },
                )
            }

            // ---------------------------------------------------------- 字号
            Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(tr("字体大小", "Font size"), Modifier.weight(1f))
                    Text(
                        "${(previewScale * 100).toInt()}%",
                        color = c.accentText,
                        fontSize = CdsDimens.captionSize,
                        fontFamily = CdsFontFamilies.mono,
                    )
                }
                // 示例句放在滑块上方：拖动时视线不用在滑块和预览之间来回跳
                CompositionLocalProvider(LocalDensity provides previewDensity) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.surface2, RoundedCornerShape(CdsDimens.radiusPanel))
                            .border(CdsDimens.hairline, c.border, RoundedCornerShape(CdsDimens.radiusPanel))
                            .padding(horizontal = CdsDimens.lg2, vertical = CdsDimens.md),
                        verticalArrangement = Arrangement.spacedBy(CdsDimens.xxs),
                    ) {
                        Text(
                            tr("预览", "Preview"),
                            color = c.textMuted,
                            fontSize = CdsDimens.captionSize,
                            fontFamily = CdsFontFamilies.sans,
                        )
                        Text(
                            buildAnnotatedString {
                                append(tr("Claude 读完 1,024 行 ", "Claude read 1,024 lines of "))
                                withStyle(SpanStyle(fontFamily = CdsFontFamilies.mono)) {
                                    append("app.ts")
                                }
                                append(tr("，只动了其中 3 行。", " and changed only 3 of them."))
                            },
                            color = c.textPrimary,
                            fontSize = CdsDimens.bodySize,
                            lineHeight = CdsDimens.lineBody,
                            fontFamily = CdsFontFamilies.sans,
                        )
                    }
                }
                Slider(
                    value = previewScale,
                    onValueChange = { previewScale = it },
                    onValueChangeFinished = { onFontScale(previewScale) },
                    valueRange = 0.85f..1.45f,
                    colors = SliderDefaults.colors(
                        thumbColor = c.fillPrimary,
                        activeTrackColor = c.accent,
                        inactiveTrackColor = c.border,
                        activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                        inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    // 默认轨道末端有个强调色小圆点，和整体不搭，自己画一条
                    // SliderState.value 是区间里的真实值，不是 0–1 的比例，得自己换算
                    track = { sliderState ->
                        val span = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                        val fraction = ((sliderState.value - sliderState.valueRange.start) / span)
                            .coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(c.border, RoundedCornerShape(50)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(6.dp)
                                    .background(c.accent, RoundedCornerShape(50)),
                            )
                        }
                    },
                    // 默认的竖条滑块和官方观感差得远，换成实心圆点
                    thumb = {
                        Box(
                            Modifier
                                .size(20.dp)
                                .background(c.fillPrimary, androidx.compose.foundation.shape.CircleShape),
                        )
                    },
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        tr("小", "Small"),
                        color = c.textMuted,
                        fontSize = CdsDimens.captionSize,
                        fontFamily = CdsFontFamilies.sans,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        tr("拖动实时预览，松手后应用到全局", "Drag for a live preview; release to apply globally"),
                        color = c.textMuted,
                        fontSize = CdsDimens.captionSize,
                        fontFamily = CdsFontFamilies.sans,
                    )
                    Text(
                        tr("大", "Large"),
                        color = c.textMuted,
                        fontSize = CdsDimens.captionSize,
                        fontFamily = CdsFontFamilies.sans,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }

            // ---------------------------------------------------------- 外观
            Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.sm)) {
                SectionLabel(tr("外观", "Appearance"))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CdsDimens.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("system" to tr("跟随系统", "System"), "light" to tr("浅色", "Light"), "dark" to tr("深色", "Dark")).forEach { (value, label) ->
                        SegmentChip(label, ui.themeMode == value) { onThemeMode(value) }
                    }
                }
            }

            // ---------------------------------------------------------- 语言
            Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.sm)) {
                SectionLabel(tr("语言", "Language"))
                LanguageSwitcher(language = ui.language, onChange = onLanguage, compact = false)
            }

            // ---------------------------------------------------------- 阅读
            Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.xs)) {
                SectionLabel(tr("阅读", "Reading"))
                DropdownRow(
                    title = tr("展开详细度", "Detail level"),
                    subtitle = detailHint(ui.detailLevel),
                    selected = ui.detailLevel,
                    options = listOf("detailed" to tr("详细", "Detailed"), "normal" to tr("默认", "Default"), "compact" to tr("精简", "Compact")),
                    onPick = onDetailLevel,
                )
                DropdownRow(
                    title = tr("费用货币", "Currency"),
                    subtitle = moneyHint(ui.currency),
                    selected = ui.currency,
                    options = listOf("cny" to tr("人民币 ¥", "CNY ¥"), "usd" to tr("美元 $", "USD $")),
                    onPick = onCurrency,
                )
                ToggleRow(tr("显示耗时与费用", "Show duration & cost"), tr("每轮回复后附上用时和 token 花费", "Append time and token cost after each reply"), ui.showStats, onShowStats)
                ToggleRow(tr("代码块自动换行", "Wrap code blocks"), tr("关掉后长行横向滚动，按原样排版", "When off, long lines scroll horizontally and stay as-is"), ui.codeWrap, onCodeWrap)
                ToggleRow(tr("收纳运行命令卡片", "Collapse command cards"), tr("Bash/PowerShell 这类命令卡默认收成一行，点开看命令与输出", "Bash/PowerShell cards collapse to one line by default; tap to see the command and output"), ui.collapseCommands, onCollapseCommands)
            }

            // ---------------------------------------------------------- 关于
            Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.xs)) {
                SectionLabel(tr("关于", "About"))
                InfoRow(tr("应用", "App"), "Claude Remote")
                InfoRow(tr("版本", "Version"), versionName.ifBlank { tr("未知", "Unknown") })
                InfoRow(tr("包名", "Package"), context.packageName)
            }
        }

    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            containerColor = c.surface2,
            shape = RoundedCornerShape(CdsDimens.radiusComposer),
            title = {
                Text(
                    tr("断开连接？", "Disconnect?"),
                    color = c.textPrimary,
                    fontSize = CdsDimens.titleSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CdsFontFamilies.sans,
                )
            },
            text = {
                Text(
                    tr("地址和令牌会从本机清除，下次打开需要重新填写。", "The address and token will be cleared from this device; you'll need to enter them again next time."),
                    color = c.textSecondary,
                    fontSize = CdsDimens.bodySmallSize,
                    lineHeight = CdsDimens.lineBodySmall,
                    fontFamily = CdsFontFamilies.sans,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    onDisconnect()
                }) {
                    Text(
                        tr("断开", "Disconnect"),
                        color = c.danger,
                        fontSize = CdsDimens.bodySmallSize,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = CdsFontFamilies.sans,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) {
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
}

/**
 * 简洁下拉选框：点一行，选项在行内就地展开，不弹浮层。
 * 详细度与货币共用，保持同一种交互。
 *
 * 不用 DropdownMenu 的原因：M3 的弹出菜单有约 112dp 的最小宽度，
 * 又被窗口右边距往回推，锚在窄窄的当前值上时整块菜单会跑到很左边，
 * 和正在点的那一行脱节。改成行内手风琴后，选项直接挂在这行下方，
 * 标签右缘与右侧当前值对齐，点哪行就从哪行长出来。
 */
@Composable
private fun DropdownRow(
    title: String,
    subtitle: String,
    selected: String,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
) {
    val c = cdsColors
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: selected
    // 可点行的名字：标题 + 说明 + 当前值，三行都在子节点上
    val rowLabel = listOf(title, subtitle, selectedLabel).filter { it.isNotBlank() }.joinToString(", ")
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(CdsMotion.duration(CdsMotion.DurBase), easing = CdsMotion.EaseOut),
        label = "dropdownChevron",
    )
    val expand by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(CdsMotion.duration(CdsMotion.DurBase), easing = CdsMotion.EaseOut),
        label = "dropdownExpand",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CdsDimens.radius))
                .clickable { expanded = !expanded }
                // 标题、说明、当前值都是子节点：直接给这一行一个名字
                .clearAndSetSemantics { contentDescription = rowLabel }
                .padding(vertical = CdsDimens.md, horizontal = CdsDimens.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CdsDimens.xxs)) {
                Text(
                    title,
                    color = c.textPrimary,
                    fontSize = CdsDimens.bodySize,
                    fontFamily = CdsFontFamilies.sans,
                )
                Text(
                    subtitle,
                    color = c.textMuted,
                    fontSize = CdsDimens.captionSize,
                    lineHeight = CdsDimens.lineCaption,
                    fontFamily = CdsFontFamilies.sans,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedLabel,
                    color = c.textSecondary,
                    fontSize = CdsDimens.footnoteSize,
                    fontFamily = CdsFontFamilies.sans,
                )
                Text(
                    "▾",
                    color = c.textMuted,
                    fontSize = CdsDimens.footnoteSize,
                    // 箭头只是「能展开」的视觉提示，别让读屏念成符号
                    modifier = Modifier
                        .padding(start = CdsDimens.sm)
                        .graphicsLayer { rotationZ = chevron }
                        .clearAndSetSemantics {},
                )
            }
        }
        // 选项始终参与组合，展开时只按进度裁剪高度：
        // 第一次点开不会在动画起帧时才临时组合三行文本，避免那一帧掉帧；
        // 也不用整块 alpha 图层做淡入，少一层离屏合成。
        Layout(
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (expanded) Modifier else Modifier.clearAndSetSemantics {})
                        .padding(end = CdsDimens.xs, bottom = CdsDimens.sm),
                    horizontalAlignment = Alignment.End,
                ) {
                    options.forEach { (value, label) ->
                        val isSelected = value == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(CdsDimens.radius))
                                .clickable {
                                    expanded = false
                                    onPick(value)
                                }
                                // 选项文字在子节点上，直接给这一行一个名字
                                .clearAndSetSemantics { contentDescription = label }
                                .padding(vertical = CdsDimens.sm),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (isSelected) "✓" else " ",
                                color = c.accent,
                                fontSize = CdsDimens.footnoteSize,
                                fontFamily = CdsFontFamilies.sans,
                                modifier = Modifier
                                    .width(16.dp)
                                    // 对勾只是视觉提示，别变成额外的朗读内容
                                    .clearAndSetSemantics {},
                            )
                            Spacer(Modifier.width(CdsDimens.sm))
                            Text(
                                label,
                                color = if (isSelected) c.accentText else c.textPrimary,
                                fontSize = CdsDimens.footnoteSize,
                                fontFamily = CdsFontFamilies.sans,
                            )
                            // 结尾留出和上方「当前值 ▾」同样的宽度（透明箭头占位），
                            // 让每个选项的文字右缘与当前值对齐
                            Text(
                                "▾",
                                color = c.textPrimary.copy(alpha = 0f),
                                fontSize = CdsDimens.footnoteSize,
                                fontFamily = CdsFontFamilies.sans,
                                modifier = Modifier
                                    .padding(start = CdsDimens.sm)
                                    .clearAndSetSemantics {},
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
        ) { measurables, constraints ->
            // 先按不受限高度量出选项的完整高度，再按动画进度裁掉可见部分
            val placeable = measurables.first().measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
            )
            val visibleHeight = (placeable.height * expand).roundToInt()
            layout(constraints.maxWidth, visibleHeight) {
                placeable.place(0, 0)
            }
        }
    }
}

/** 设置页里的可点击行：标题 + 说明 + 右侧箭头，用量入口和断开连接共用 */
@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val c = cdsColors
    // 标题和说明都在子节点上，拼成一句给可点行当名字
    val rowLabel = listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CdsDimens.radius))
            .clickable { onClick() }
            .clearAndSetSemantics { contentDescription = rowLabel }
            .padding(vertical = CdsDimens.md, horizontal = CdsDimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CdsDimens.xxs)) {
            Text(
                title,
                color = if (danger) c.danger else c.textPrimary,
                fontSize = CdsDimens.bodySize,
                fontFamily = CdsFontFamilies.sans,
            )
            Text(
                subtitle,
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                lineHeight = CdsDimens.lineCaption,
                fontFamily = CdsFontFamilies.sans,
            )
        }
        // 箭头是装饰，读屏念出来只是噪音
        Text(
            "›",
            color = c.textMuted,
            fontSize = CdsDimens.titleSize,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun detailHint(level: String): String = when (level) {
    "detailed" -> tr(
        "思考与工具调用默认展开，能看到完整参数和结果",
        "Thinking and tool calls are expanded by default, showing full parameters and results",
    )
    "compact" -> tr("隐藏思考，工具只保留一行摘要", "Hides thinking; tools collapse to a one-line summary")
    else -> tr("思考收起，工具卡片点开看详情", "Thinking collapsed; tap a tool card for details")
}

@Composable
private fun moneyHint(currency: String): String =
    if (currency == "cny") {
        tr(
            "按 1 美元 ≈ ¥${Money.USD_TO_CNY} 换算，仅用于显示",
            "Converted at 1 USD ≈ ¥${Money.USD_TO_CNY}, for display only",
        )
    } else {
        tr("按美元原价显示", "Shown at the original USD amount")
    }

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = cdsColors.textMuted,
        fontSize = CdsDimens.captionSize,
        fontFamily = CdsFontFamilies.sans,
        modifier = modifier,
    )
}

@Composable
private fun SegmentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = cdsColors
    val bg by animateColorAsState(
        if (selected) c.fillPrimary else c.surface2,
        animationSpec = CdsMotion.color(),
        label = "segBg",
    )
    val fg by animateColorAsState(
        if (selected) c.onPrimary else c.textSecondary,
        animationSpec = CdsMotion.color(),
        label = "segFg",
    )
    val line by animateColorAsState(
        if (selected) c.fillPrimary else c.border,
        animationSpec = CdsMotion.color(),
        label = "segLine",
    )
    // 固定高度 + 内容居中：真实机型上中英文字体回退会让同一行内不同胶囊
    // 的文字行高不一样，按内容撑高度时几个胶囊会一高一低。
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(bg, RoundedCornerShape(50))
            .border(CdsDimens.hairline, line, RoundedCornerShape(50))
            .clickable { onClick() }
            // 文字在子节点上，直接给胶囊一个名字（选中态靠视觉高亮）
            .clearAndSetSemantics { contentDescription = label }
            .padding(horizontal = CdsDimens.lg2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = fg,
            fontSize = CdsDimens.footnoteSize,
            fontFamily = CdsFontFamilies.sans,
            maxLines = 1,
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = cdsColors
    // 标题、说明都在子节点上；开关状态也一并读出来，别让读屏只说「可双击」
    val rowLabel = listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(", ")
    val onLabel = tr("已开启", "On")
    val offLabel = tr("已关闭", "Off")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .clearAndSetSemantics {
                contentDescription = rowLabel
                stateDescription = if (checked) onLabel else offLabel
            }
            .padding(vertical = CdsDimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CdsDimens.xxs)) {
            Text(title, color = c.textPrimary, fontSize = CdsDimens.bodySize, fontFamily = CdsFontFamilies.sans)
            Text(
                subtitle,
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                lineHeight = CdsDimens.lineCaption,
                fontFamily = CdsFontFamilies.sans,
            )
        }
        StretchSwitch(checked = checked, onChange = onChange)
    }
}

/**
 * 开关：按下时拇指朝即将去的方向拉伸，松手用弹簧跨过去。
 * 参数参考 Arc 的 switch——触屏没有 hover，用按压拉伸代替悬停反馈。
 */
@Composable
private fun StretchSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = cdsColors
    val noMotion = reducedMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val trackW = 46.dp
    val trackH = 28.dp
    val pad = 3.dp
    val thumbBase = 22.dp
    val stretch = if (pressed) 7.dp else 0.dp

    val thumbW by animateDpAsState(
        targetValue = thumbBase + stretch,
        animationSpec = tween(if (noMotion) 0 else CdsMotion.DurSnap, easing = CdsMotion.EaseSnap),
        label = "thumbWidth",
    )
    // 未选中时左对齐（向右拉长），选中时右对齐（向左拉长），形成"朝目标方向拉伸"
    val thumbX by animateDpAsState(
        targetValue = if (checked) trackW - pad - thumbW else pad,
        animationSpec = if (noMotion) snap() else spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "thumbX",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) c.fillPrimary else c.border,
        animationSpec = tween(if (noMotion) 0 else CdsMotion.DurBase),
        label = "trackColor",
    )

    Box(
        modifier = Modifier
            .size(trackW, trackH)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable(interactionSource = interaction, indication = null) { onChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                // offset 是相对 contentAlignment 的位置偏移，起点已经在垂直正中，
                // 再加 y 会把拇指压到轨道底部
                .offset(x = thumbX)
                .size(thumbW, thumbBase)
                .clip(CircleShape)
                .background(if (checked) c.onPrimary else c.textMuted),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = true) {
    val c = cdsColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CdsDimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = c.textSecondary, fontSize = CdsDimens.footnoteSize, fontFamily = CdsFontFamilies.sans)
        Text(
            value,
            color = c.textPrimary,
            fontSize = CdsDimens.captionSize,
            // 文件夹路径和界面里的普通文字保持同一种字体，等宽只留给模型名、包名这类标识
            fontFamily = if (mono) CdsFontFamilies.mono else CdsFontFamilies.sans,
            maxLines = 2,
            modifier = Modifier
                .weight(1f)
                .padding(start = CdsDimens.lg),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

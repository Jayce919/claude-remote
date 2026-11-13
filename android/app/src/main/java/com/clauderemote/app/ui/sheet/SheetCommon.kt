package com.clauderemote.app.ui.sheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.CdsMotion
import com.clauderemote.app.ui.theme.cdsColors
import com.clauderemote.app.ui.theme.reducedMotion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 三个次级面板共用的标题、分组和选项行，保持视觉一致 */
@Composable
internal fun SheetTitle(text: String) {
    Text(
        text,
        color = cdsColors.textPrimary,
        fontSize = CdsDimens.bodySize,
        fontWeight = FontWeight.SemiBold,
        fontFamily = CdsFontFamilies.sans,
    )
}

@Composable
internal fun SheetSectionLabel(text: String) {
    Text(
        text,
        color = cdsColors.textMuted,
        fontSize = CdsDimens.captionSize,
        fontFamily = CdsFontFamilies.sans,
    )
}

@Composable
internal fun SheetSection(title: String, current: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetSectionLabel(title)
            Text(
                "  ·  $current",
                color = cdsColors.accentText,
                fontSize = CdsDimens.captionSize,
                fontFamily = CdsFontFamilies.sans,
            )
        }
        content()
    }
}

@Composable
internal fun SheetOptionRow(
    label: String,
    selected: Boolean,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showSelection: Boolean = true,
    /** true 表示这是一组单选里的选项（读屏要播报「已选中」）；普通操作行保持 false */
    radioOption: Boolean = false,
    onClick: () -> Unit,
) {
    val c = cdsColors
    // 可点行的名字：标题 + 副标题（有副标题时拼一句，避免读屏把两行当两个节点）
    val rowLabel = if (subtitle.isNullOrBlank()) label else "$label, $subtitle"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CdsDimens.radius))
            .then(
                if (showSelection) {
                    Modifier
                        .background(if (selected) c.surface3 else c.surface2, RoundedCornerShape(CdsDimens.radius))
                        .border(CdsDimens.hairline, if (selected) c.accent else c.border, RoundedCornerShape(CdsDimens.radius))
                } else {
                    Modifier
                }
            )
            // 单选行用 selectable：TalkBack 会读出角色和「已选中 / 未选中」，
            // 纯 clickable 只说明「可双击」，听不出当前是哪一项。
            .then(
                if (radioOption) {
                    Modifier.selectable(
                        selected = selected,
                        onClick = onClick,
                        role = Role.RadioButton,
                    )
                } else {
                    Modifier.clickable { onClick() }
                }
            )
            // 标题（和副标题）都在子节点上：直接给这一行一个名字，读屏聚焦时才念得出来；
            // 选中态仍由 selectable 播报，二者不冲突。
            .clearAndSetSemantics { contentDescription = rowLabel }
            .padding(horizontal = CdsDimens.lg, vertical = CdsDimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = c.textPrimary,
                fontSize = CdsDimens.bodySize,
                fontWeight = if (selected && !showSelection) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = CdsFontFamilies.sans,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = c.textMuted,
                    fontSize = CdsDimens.captionSize,
                    lineHeight = CdsDimens.lineCaption,
                    fontFamily = CdsFontFamilies.sans,
                )
            }
        }
        if (selected && showSelection) {
            // 选中与否已由 selectable 播报，对勾只是视觉提示，别让它变成额外的朗读文字
            Text(
                "✓",
                color = c.accent,
                fontSize = CdsDimens.bodySize,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** 选项行的一条数据，供 SheetOptionList 生成滑动高亮 */
internal data class SheetOption(
    val key: String,
    val label: String,
    val subtitle: String? = null,
)

/**
 * 单选选项列表：只有一个高亮块，选中项变化时它滑到新位置（Arc 的 morph-select）。
 * 行高不同也能对齐——每行渲染后上报自己的 top/height，高亮块按测量值定位。
 */
@Composable
internal fun SheetOptionList(
    options: List<SheetOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    val c = cdsColors
    val noMotion = reducedMotion
    val density = LocalDensity.current
    val spots = remember(options) { mutableStateMapOf<Int, Pair<Int, Int>>() }
    val selectedIndex = options.indexOfFirst { it.key == selectedKey }
    val spot = spots[selectedIndex]

    // 高亮块的位置/高度手动驱动：第一次量到行才直接落位，
    // 之后切换选项才播放滑动——否则一打开面板就会从顶部落下来一次。
    val top = remember { androidx.compose.animation.core.Animatable(0f) }
    val height = remember { androidx.compose.animation.core.Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(spot, noMotion) {
        if (spot == null) return@LaunchedEffect
        if (!placed || noMotion) {
            top.snapTo(spot.first.toFloat())
            height.snapTo(spot.second.toFloat())
            placed = true
        } else {
            val spec = tween<Float>(CdsMotion.DurBase, easing = CdsMotion.EaseOut)
            launch { top.animateTo(spot.first.toFloat(), spec) }
            launch { height.animateTo(spot.second.toFloat(), spec) }
        }
    }

    Box(Modifier.fillMaxWidth()) {
        if (spot != null && height.value > 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, top.value.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(density) { height.value.toDp() })
                    .clip(RoundedCornerShape(CdsDimens.radius))
                    .background(c.surface3, RoundedCornerShape(CdsDimens.radius))
                    .border(CdsDimens.hairline, c.accent, RoundedCornerShape(CdsDimens.radius)),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.xs)) {
            options.forEachIndexed { index, option ->
                SheetOptionRow(
                    label = option.label,
                    selected = index == selectedIndex,
                    subtitle = option.subtitle,
                    showSelection = false,
                    radioOption = true,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInParent().y.roundToInt() to coords.size.height
                        if (spots[index] != pos) spots[index] = pos
                    },
                    onClick = { onSelect(option.key) },
                )
            }
        }
    }
}

@Composable
internal fun permissionLabel(mode: String): String = when (mode) {
    "auto" -> tr("自动", "Auto")
    "default" -> tr("逐步询问", "Ask each time")
    "acceptEdits" -> tr("自动接受编辑", "Auto-accept edits")
    "plan" -> tr("计划模式", "Plan mode")
    "dontAsk" -> tr("不询问", "Don't ask")
    else -> mode
}

@Composable
internal fun permissionHint(mode: String): String? = when (mode) {
    "auto" -> tr("由 Claude 判断，需要时再向你确认", "Claude decides, asking you when needed")
    "default" -> tr("每次调用工具前逐一确认", "Confirm before every tool call")
    "acceptEdits" -> tr("自动接受文件编辑，其他操作仍会询问", "Auto-accept file edits; other actions still ask")
    "plan" -> tr("只制定计划，不直接修改文件", "Only plan, without modifying files")
    "dontAsk" -> tr("不再询问，直接执行工具", "Never ask; run tools directly")
    else -> null
}

@Composable
internal fun effortLabel(effort: String): String = when (effort) {
    "low" -> tr("低", "Low")
    "medium" -> tr("中", "Medium")
    "high" -> tr("高", "High")
    "xhigh" -> tr("极高", "Very high")
    "max" -> tr("最大", "Max")
    else -> effort
}

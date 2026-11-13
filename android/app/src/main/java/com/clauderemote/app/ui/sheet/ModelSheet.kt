package com.clauderemote.app.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.clauderemote.app.data.ChannelInfo
import com.clauderemote.app.data.ChatUiState
import com.clauderemote.app.data.currentChannelInfo
import com.clauderemote.app.data.realModelName
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.cdsColors
import kotlinx.coroutines.launch

/**
 * 模型和思考强度放在一起：这两项决定「用哪个脑子、想多深」，
 * 和权限是两类决策，所以不再混在同一个面板里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    state: ChatUiState,
    onDismiss: () -> Unit,
    onModel: (String) -> Unit,
    onEffort: (String) -> Unit,
    onChannel: (String) -> Unit,
    onRefreshModels: () -> Unit,
) {
    val c = cdsColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // 模型和思考强度都是「选完即走」：选中后播放收起动画，再通知外层移除
    fun choose(apply: () -> Unit) {
        apply()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface2,
        shape = RoundedCornerShape(topStart = CdsDimens.radiusComposer, topEnd = CdsDimens.radiusComposer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CdsDimens.xxl, vertical = CdsDimens.md),
            verticalArrangement = Arrangement.spacedBy(CdsDimens.xl),
        ) {
            SheetTitle(tr("模型选择", "Model"))

            // 只有服务端真的给出两条以上通道时才显示切换器；
            // 旧服务端 channels 为空，整个开关和后面的模型列表表现与以前完全一致
            if (state.channels.size > 1) {
                ChannelSwitcher(
                    channels = state.channels,
                    current = state.currentChannel,
                    onSelect = onChannel,
                )
            }

            state.nodes?.let { info ->
                Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.xs)) {
                    // 有通道时供应商以通道为准：刚切过去、模型列表还没回来的瞬间也不会显示旧供应商
                    val provider = state.currentChannelInfo?.provider
                        ?.takeIf { it.isNotBlank() }
                        ?: info.provider.ifBlank { tr("未知", "Unknown") }
                    Text(
                        "$provider · ${tr("上游", "Upstream")} ${info.upstream}",
                        color = c.textMuted,
                        fontSize = CdsDimens.captionSize,
                        lineHeight = CdsDimens.lineCaption,
                        fontFamily = CdsFontFamilies.sans,
                    )
                    Text(
                        tr("刷新模型列表", "Refresh model list"),
                        color = c.accentText,
                        fontSize = CdsDimens.footnoteSize,
                        fontFamily = CdsFontFamilies.sans,
                        modifier = Modifier
                            .padding(top = CdsDimens.xxs)
                            .clickable { onRefreshModels() },
                    )
                }
            }

            SheetSection(tr("模型", "Model"), state.realModelName()) {
                // 切通道后立刻用该通道下发的模型列表，不等 /api/models 回来；
                // 只有通道功能不可用时才退回原来的全局列表，保持旧服务端行为
                val models = if (state.channels.isNotEmpty()) {
                    state.currentChannelModels
                } else {
                    (state.nodes?.models ?: emptyList()).ifEmpty { state.boot?.models ?: emptyList() }
                }
                // 主标题是第三方真实模型，副标题才是 claude-* 槽位名
                SheetOptionList(
                    options = models.map { m ->
                        val real = state.realModelName(m)
                        SheetOption(m, real, if (real != m) m else null)
                    },
                    selectedKey = state.model,
                    onSelect = { choose { onModel(it) } },
                )
            }

            SheetSection(tr("思考强度", "Thinking effort"), effortLabel(state.effort).ifBlank { tr("默认", "Default") }) {
                val efforts = state.boot?.efforts ?: listOf("low", "medium", "high", "xhigh", "max")
                // 空串 = 不指定，交给 CLI 自己的默认档；把它显式列出来，
                // 否则当前值落在列表之外，整组没有任何高亮，看起来像没选中
                SheetOptionList(
                    options = listOf(SheetOption("", tr("默认", "Default"))) +
                        efforts.map { effort -> SheetOption(effort, effortLabel(effort)) },
                    selectedKey = state.effort,
                    onSelect = { choose { onEffort(it) } },
                )
            }
        }
    }
}

/**
 * 通道切换器：一排胶囊，选中 accent、未选中 textMuted。
 * 只切通道、不关闭面板——下面的模型列表会立刻换成该通道的，用户还要接着选模型。
 * 服务端标记 available=false 的通道置灰且不响应点击，底下一行 footnote 说明。
 */
@Composable
private fun ChannelSwitcher(
    channels: List<ChannelInfo>,
    current: String,
    onSelect: (String) -> Unit,
) {
    val c = cdsColors
    val unavailable = channels.filter { !it.available }
    Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.xs)) {
        SheetSectionLabel(tr("通道", "Channel"))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CdsDimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            channels.forEach { ch ->
                val selected = ch.id == current
                val enabled = ch.available
                Box(
                    modifier = Modifier
                        .height(CdsDimens.iconButton)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) c.surface3 else c.surface2, RoundedCornerShape(50))
                        .border(
                            CdsDimens.hairline,
                            when {
                                selected -> c.accent
                                enabled -> c.border
                                // 不可用的通道连边框一起压暗，一眼能看出「有但不能选」
                                else -> c.border.copy(alpha = 0.5f)
                            },
                            RoundedCornerShape(50),
                        )
                        // 通道胶囊也是一组单选：用 selectable 让读屏播报角色和选中态；
                        // 不可用通道一并置为 disabled，读屏会说明它是禁用的。
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(ch.id) },
                        )
                        // 通道名在子节点 Text 上，直接给可点胶囊一个名字；
                        // 选中 / 禁用状态仍由 selectable 播报。
                        .clearAndSetSemantics { contentDescription = ch.label }
                        .padding(horizontal = CdsDimens.lg2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ch.label,
                        color = when {
                            selected -> c.accentText
                            enabled -> c.textMuted
                            else -> c.textMuted.copy(alpha = 0.45f)
                        },
                        fontSize = CdsDimens.footnoteSize,
                        fontFamily = CdsFontFamilies.sans,
                        maxLines = 1,
                    )
                }
            }
        }
        // provider 名 + 不可用原因说明，放在同一行 footnote，不额外占一块版面
        val notes = ArrayList<String>(2)
        channels.firstOrNull { it.id == current }
            ?.provider
            ?.takeIf { it.isNotBlank() }
            ?.let { notes += it }
        if (unavailable.isNotEmpty()) {
            notes += tr(
                unavailable.joinToString("、") { it.label } + " 暂不可用",
                unavailable.joinToString(", ") { it.label } + " unavailable",
            )
        }
        if (notes.isNotEmpty()) {
            Text(
                notes.joinToString(" · "),
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                lineHeight = CdsDimens.lineCaption,
                fontFamily = CdsFontFamilies.sans,
            )
        }
    }
}

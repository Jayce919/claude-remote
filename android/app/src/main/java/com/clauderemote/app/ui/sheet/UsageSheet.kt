package com.clauderemote.app.ui.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clauderemote.app.data.ChatUiState
import com.clauderemote.app.data.Money
import com.clauderemote.app.data.UsageDay
import com.clauderemote.app.data.UsageModel
import com.clauderemote.app.data.UsageWindow
import com.clauderemote.app.data.compactTokens
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.CdsMotion
import com.clauderemote.app.ui.theme.cdsColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 电脑端用量面板。数据来自 cc-switch 的 proxy_request_logs，
 * 三个窗口（今日 / 近 7 天 / 近 30 天）+ 一条 14 天柱状图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageSheet(
    state: ChatUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    val c = cdsColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val usage = state.usage

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetTitle(tr("用量与花费", "Usage & cost"))
                Spacer(Modifier.weight(1f))
                Text(
                    tr("刷新", "Refresh"),
                    color = c.accentText,
                    fontSize = CdsDimens.footnoteSize,
                    fontFamily = CdsFontFamilies.sans,
                    modifier = Modifier
                        .clickable { onRefresh() }
                        .padding(horizontal = CdsDimens.sm, vertical = CdsDimens.xs),
                )
            }

            Text(
                tr("电脑端 cc-switch 请求日志 · Claude 与 Codex 全部请求", "cc-switch request logs from the computer · all Claude and Codex requests"),
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                lineHeight = CdsDimens.lineCaption,
                fontFamily = CdsFontFamilies.sans,
            )

            if (state.usageLoading && usage == null) {
                Text(
                    tr("正在读取…", "Loading…"),
                    color = c.textMuted,
                    fontSize = CdsDimens.footnoteSize,
                    fontFamily = CdsFontFamilies.sans,
                )
            }

            state.usageError?.let { error ->
                Text(
                    error,
                    color = c.danger,
                    fontSize = CdsDimens.captionSize,
                    lineHeight = CdsDimens.lineCaption,
                    fontFamily = CdsFontFamilies.sans,
                )
            }

            if (usage != null && usage.available) {
                val currency = state.ui.currency
                listOf("today", "week", "month").forEach { key ->
                    usage.windows[key]?.let { UsageCard(it, currency) }
                }

                if (usage.daily.isNotEmpty()) {
                    DailyBars(usage.daily.takeLast(14))
                }

                if (usage.topModels.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.sm)) {
                        SheetSectionLabel(tr("近 7 天模型", "Top models (7 days)"))
                        usage.topModels.take(4).forEach { TopModelRow(it, currency) }
                    }
                }

                Text(
                    "${moneyHint(currency)} · ${tr("更新于", "Updated")} ${timeOf(usage.generatedAt)}",
                    color = c.textMuted,
                    fontSize = CdsDimens.captionSize,
                    lineHeight = CdsDimens.lineCaption,
                    fontFamily = CdsFontFamilies.sans,
                )
            } else if (usage != null && !usage.available) {
                Text(
                    usage.error ?: tr("用量数据不可用", "Usage data unavailable"),
                    color = c.textMuted,
                    fontSize = CdsDimens.captionSize,
                    fontFamily = CdsFontFamilies.sans,
                )
            }

            Spacer(Modifier.height(CdsDimens.xl))
        }
    }
}

@Composable
private fun UsageCard(window: UsageWindow, currency: String) {
    val c = cdsColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.pageBg, RoundedCornerShape(CdsDimens.radiusPanel))
            .border(CdsDimens.hairline, c.border, RoundedCornerShape(CdsDimens.radiusPanel))
            .padding(horizontal = CdsDimens.lg2, vertical = CdsDimens.md),
        verticalArrangement = Arrangement.spacedBy(CdsDimens.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                window.label.ifBlank { tr("用量", "Usage") },
                color = c.textSecondary,
                fontSize = CdsDimens.footnoteSize,
                fontFamily = CdsFontFamilies.sans,
            )
            Spacer(Modifier.weight(1f))
            Text(
                Money.format(window.costUsd, currency, 2),
                color = c.textPrimary,
                fontSize = CdsDimens.titleSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CdsFontFamilies.sans,
            )
        }
        Text(
            tr("${window.requests} 次请求", "${window.requests} requests") + " · ${compactTokens(window.totalTokens)} tokens",
            color = c.textMuted,
            fontSize = CdsDimens.captionSize,
            fontFamily = CdsFontFamilies.sans,
        )
        MetricLine(window)
    }
}

/**
 * 输入 / 输出 / 缓存三项指标。
 * 右侧 ⓘ 展开一句口径说明——用量数字最容易看不明白的就是这三项的分界。
 */
@Composable
private fun MetricLine(window: UsageWindow) {
    val c = cdsColors
    var open by remember { mutableStateOf(false) }
    // 语义回调不是 @Composable，先在组合期把文案取出来
    val infoLabel = tr("查看说明", "About these numbers")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("输入", "Input") + " ${compactTokens(window.input)} · " +
                    tr("输出", "Output") + " ${compactTokens(window.output)} · " +
                    tr("缓存", "Cache") + " ${compactTokens(window.cacheRead)}",
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                fontFamily = CdsFontFamilies.mono,
                modifier = Modifier.weight(1f),
            )
            Text(
                "ⓘ",
                color = c.accent,
                fontSize = CdsDimens.captionSize,
                fontFamily = CdsFontFamilies.sans,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { open = !open }
                    // 纯符号按钮：读屏念「ⓘ」没有意义，换成动作名
                    .clearAndSetSemantics { contentDescription = infoLabel }
                    .padding(horizontal = CdsDimens.sm, vertical = CdsDimens.xxs),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(animationSpec = CdsMotion.expandSize()) + fadeIn(),
            exit = shrinkVertically(animationSpec = CdsMotion.expandSize()) + fadeOut(),
        ) {
            Text(
                tr(
                    "输入：这次请求里发给模型的全部提示词（系统提示、历史对话、工具结果）。\n" +
                        "输出：模型生成的 token，含思考内容。\n" +
                        "缓存：命中前缀缓存的输入 token，价格约为普通输入的十分之一，命中越多越省。",
                    "Input: every prompt token sent to the model (system prompt, history, tool results).\n" +
                        "Output: tokens the model generated, thinking included.\n" +
                        "Cache: input tokens served from the prompt cache, roughly one tenth the price.",
                ),
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                lineHeight = CdsDimens.lineCaption,
                fontFamily = CdsFontFamilies.sans,
                modifier = Modifier.padding(top = CdsDimens.xs),
            )
        }
    }
}

/** 14 天柱状图：高度按 token 数，颜色沿用 Claude 的 clay 强调色 */
@Composable
private fun DailyBars(days: List<UsageDay>) {
    val c = cdsColors
    val max = days.maxOf { it.tokens }.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.sm)) {
        SheetSectionLabel(tr("近 14 天", "Last 14 days"))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { day ->
                val height = (4 + 44.0 * (day.tokens.toDouble() / max)).dp
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(height)
                        .background(
                            if (day.tokens > 0) c.clay.copy(alpha = 0.9f) else c.border,
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                days.firstOrNull()?.date?.takeLast(5) ?: "",
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                fontFamily = CdsFontFamilies.mono,
            )
            Spacer(Modifier.weight(1f))
            Text(
                days.lastOrNull()?.date?.takeLast(5) ?: "",
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                fontFamily = CdsFontFamilies.mono,
            )
        }
    }
}

@Composable
private fun TopModelRow(model: UsageModel, currency: String) {
    val c = cdsColors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            model.model,
            color = c.textSecondary,
            fontSize = CdsDimens.footnoteSize,
            fontFamily = CdsFontFamilies.sans,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${compactTokens(model.tokens)} · ${Money.format(model.costUsd, currency, 2)}",
            color = c.textMuted,
            fontSize = CdsDimens.captionSize,
            fontFamily = CdsFontFamilies.mono,
        )
    }
}

@Composable
private fun moneyHint(currency: String): String =
    if (currency == "cny") tr("按 1 美元 ≈ ¥${Money.USD_TO_CNY} 换算，仅用于显示", "Converted at 1 USD ≈ ¥${Money.USD_TO_CNY} for display only")
    else tr("按美元原价显示", "Shown at original USD prices")

@Composable
private fun timeOf(ms: Long): String =
    if (ms <= 0) tr("未知", "Unknown") else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

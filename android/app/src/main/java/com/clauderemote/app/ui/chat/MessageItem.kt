package com.clauderemote.app.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.clauderemote.app.data.Block
import com.clauderemote.app.data.ChatMessage
import com.clauderemote.app.data.PendingControl
import com.clauderemote.app.data.UiPrefs
import com.clauderemote.app.ui.cards.PermissionCard
import com.clauderemote.app.ui.cards.QuestionCard
import com.clauderemote.app.ui.cards.ThinkingBlock
import com.clauderemote.app.ui.cards.ToolCard
import com.clauderemote.app.ui.cards.ToolText
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.rich.Markdown
import com.clauderemote.app.ui.rich.RichBlocks
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.CdsMotion
import com.clauderemote.app.ui.theme.cdsColors

@Composable
fun MessageItem(
    message: ChatMessage,
    pending: Map<String, PendingControl>,
    ui: UiPrefs,
    running: Boolean,
    onAnswer: (String, Map<String, String>) -> Unit,
    onDecide: (String, Boolean) -> Unit,
) {
    if (message.role == "user") {
        UserMessage(message)
    } else {
        AssistantMessage(
            message = message,
            pending = pending,
            ui = ui,
            running = running,
            onAnswer = onAnswer,
            onDecide = onDecide,
        )
    }
}

@Composable
private fun UserMessage(message: ChatMessage) {
    val c = cdsColors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(CdsDimens.xxs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    // DeepSeek 的用户气泡是统一 6dp 圆角，这里对齐同一数值
                    .background(
                        c.userBubble,
                        RoundedCornerShape(CdsDimens.radius),
                    )
                    .padding(horizontal = CdsDimens.lg2, vertical = CdsDimens.md),
            ) {
                message.images.forEach { img ->
                    if (img.preview.isNotBlank() || img.base64.isNotBlank()) {
                        AttachmentImage(
                            base64 = img.base64,
                            preview = img.preview,
                            contentDescription = img.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = CdsDimens.sm),
                        )
                    } else {
                        // 普通文件在气泡里只留文件名，点开由电脑端读取
                        Row(
                            modifier = Modifier.padding(bottom = CdsDimens.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CdsDimens.sm),
                        ) {
                            Text(
                                "▤",
                                color = c.textSecondary,
                                fontSize = CdsDimens.footnoteSize,
                                fontFamily = CdsFontFamilies.sans,
                            )
                            Text(
                                img.name,
                                color = c.textPrimary,
                                fontSize = CdsDimens.footnoteSize,
                                fontFamily = CdsFontFamilies.sans,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (message.text.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = message.text,
                            color = c.textPrimary,
                            fontSize = CdsDimens.bodySize,
                            lineHeight = CdsDimens.lineBody,
                            fontFamily = CdsFontFamilies.sans,
                        )
                    }
                }
                if (message.queued) {
                    Text(
                        tr("排队中 · 等当前任务结束", "Queued · waiting for the current task to finish"),
                        color = c.textMuted,
                        fontSize = CdsDimens.captionSize,
                        fontFamily = CdsFontFamilies.sans,
                        modifier = Modifier.padding(top = CdsDimens.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    pending: Map<String, PendingControl>,
    ui: UiPrefs,
    running: Boolean,
    onAnswer: (String, Map<String, String>) -> Unit,
    onDecide: (String, Boolean) -> Unit,
) {
    val c = cdsColors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CdsDimens.md),
    ) {
        message.blocks.forEach { block ->
            when (block) {
                is Block.Text -> {
                    // 只有正在流式生成时才做入场淡入；历史消息上下滑动时
                    // LazyColumn 会重新组合条目，若仍在淡入，整页会边滑边闪。
                    val appear = remember { Animatable(if (running) 0f else 1f) }
                    val rise = with(LocalDensity.current) { 2.dp.toPx() }
                    val duration = CdsMotion.duration(150)
                    LaunchedEffect(Unit) {
                        if (running) appear.animateTo(1f, tween(duration, easing = CdsMotion.EaseOut))
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = appear.value
                                translationY = (1f - appear.value) * rise
                            },
                    ) {
                        // 只在正文变化时重新解析，避免无关重组重复跑 parser
                        val rich = remember(block.text) { Markdown.toBlocks(block.text) }
                        if (rich.isNotEmpty()) {
                            RichBlocks(rich)
                        } else if (block.text.isNotBlank()) {
                            Text(
                                block.text,
                                color = c.textPrimary,
                                fontSize = CdsDimens.bodySize,
                                lineHeight = CdsDimens.lineBody,
                                fontFamily = CdsFontFamilies.sans,
                            )
                        }
                    }
                }
                is Block.Thinking -> when (ui.detailLevel) {
                    // 精简模式直接不显示思考，只留结果
                    "compact" -> Unit
                    "detailed" -> ThinkingBlock(block.text, defaultOpen = true)
                    else -> ThinkingBlock(block.text, defaultOpen = false)
                }
                is Block.Tool -> Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.xs)) {
                    // 电脑端每一步都有文字交代当前在做什么；
                    // 手机上如果只剩一张卡片，长任务里会看不出进展
                    if (block.status == "pending") {
                        Text(
                            ToolText.running(block),
                            color = c.textSecondary,
                            fontSize = CdsDimens.footnoteSize,
                            lineHeight = CdsDimens.lineFootnote,
                            fontFamily = CdsFontFamilies.sans,
                        )
                    }
                    ToolCard(
                        block,
                        wrap = ui.codeWrap,
                        defaultOpen = ui.detailLevel == "detailed" && !(ToolText.isCommand(block.name) && ui.collapseCommands),
                        expandable = ui.detailLevel != "compact",
                    )
                    val p = pending[block.toolId]
                    if (p != null) {
                        when (p.kind) {
                            "question" -> QuestionCard(p.questions) { answers -> onAnswer(p.toolUseId, answers) }
                            else -> PermissionCard(p.toolName, p.decisionReason, p.input) { allow -> onDecide(p.toolUseId, allow) }
                        }
                    }
                }
            }
        }

        // 断线重连后工具块可能已经不在消息里，单独把待处理卡片补上
        pending.values
            .filter { p -> message.blocks.none { it is Block.Tool && it.toolId == p.toolUseId } }
            .forEach { p ->
                when (p.kind) {
                    "question" -> QuestionCard(p.questions) { answers -> onAnswer(p.toolUseId, answers) }
                    else -> PermissionCard(p.toolName, p.decisionReason, p.input) { allow -> onDecide(p.toolUseId, allow) }
                }
            }
    }
}

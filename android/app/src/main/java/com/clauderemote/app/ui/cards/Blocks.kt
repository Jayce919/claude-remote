package com.clauderemote.app.ui.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clauderemote.app.data.Block
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.CdsIcons
import com.clauderemote.app.ui.theme.CdsMotion
import com.clauderemote.app.ui.theme.cdsColors
import com.clauderemote.app.ui.theme.reducedMotion
import kotlinx.coroutines.delay

/** 思考过程：默认收成一行，点开展示全文 */
@Composable
fun ThinkingBlock(text: String, defaultOpen: Boolean = false) {
    val c = cdsColors
    var open by remember(defaultOpen) { mutableStateOf(defaultOpen) }

    // 还在长 = 思考中；一段时间没有新文字就算完成，标签交叉淡变成「思考 N 秒」。
    // 历史消息一次性铺开时也走这条路：短暂显示「思考中」后落到时长。
    var complete by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    val startedAt = remember { System.currentTimeMillis() }
    LaunchedEffect(text.length) {
        if (!complete) {
            delay(THINKING_SETTLE_MS)
            complete = true
            seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        }
    }

    // 可点的是整块（头部 + 展开后的正文），所以不能直接合并子语义——那会把整段
    // 思考文字变成一个按钮的名字。给容器一个「思考 N 秒」的名字，正文仍可逐段朗读。
    val headerLabel = if (complete) {
        tr("思考 $seconds 秒", "Thought for ${seconds}s")
    } else {
        tr("思考中", "Thinking")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .semantics { contentDescription = headerLabel }
            .padding(vertical = CdsDimens.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(c.borderStrong, RoundedCornerShape(1.dp)),
            )
            val enterMs = CdsMotion.duration(160)
            val exitMs = CdsMotion.duration(100)
            AnimatedContent(
                targetState = complete,
                transitionSpec = {
                    fadeIn(tween<Float>(enterMs, easing = CdsMotion.EaseOut))
                        .togetherWith(fadeOut(tween<Float>(exitMs, easing = CdsMotion.EaseSnap)))
                },
                label = "thinkingLabel",
                modifier = Modifier.padding(start = CdsDimens.md),
            ) { done ->
                Text(
                    text = if (done) tr("思考 $seconds 秒", "Thought for ${seconds}s") else tr("思考中", "Thinking"),
                    color = c.textMuted.copy(alpha = if (done) 1f else shimmerAlpha()),
                    fontSize = CdsDimens.captionSize,
                    fontFamily = CdsFontFamilies.sans,
                    // 名字已经挂在上面那个可点容器上，别让读屏再念一遍
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(animationSpec = CdsMotion.expandSize()) + fadeIn(),
            exit = shrinkVertically(animationSpec = CdsMotion.expandSize()) + fadeOut(),
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    color = c.textMuted,
                    fontSize = CdsDimens.footnoteSize,
                    lineHeight = CdsDimens.lineFootnote,
                    fontFamily = CdsFontFamilies.sans,
                    modifier = Modifier.padding(start = CdsDimens.lg, top = CdsDimens.sm),
                )
            }
        }
    }
}

/** 工具调用卡片 */
@Composable
fun ToolCard(
    block: Block.Tool,
    wrap: Boolean = false,
    defaultOpen: Boolean = false,
    expandable: Boolean = true,
) {
    val c = cdsColors
    var open by remember(defaultOpen) { mutableStateOf(defaultOpen && expandable) }
    // 运行中的工具标题带微光，完成后停
    val running = block.status == "pending"
    // 可点标题行的名字：工具名 + 摘要 + 状态，和视觉上那一行一致
    val cardLabel = listOf(ToolText.label(block.name), ToolText.summary(block), ToolText.badge(block))
        .filter { it.isNotBlank() }
        .joinToString(", ")
    val rotation by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = androidx.compose.animation.core.tween(CdsMotion.DurBase, easing = CdsMotion.EaseSnap),
        label = "chev",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CdsDimens.radiusPanel))
            .background(c.surface2, RoundedCornerShape(CdsDimens.radiusPanel))
            .border(CdsDimens.hairline, c.border, RoundedCornerShape(CdsDimens.radiusPanel)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = expandable) { open = !open }
                // 工具名、摘要、状态都在子节点上，直接给可点行一个名字
                .clearAndSetSemantics { contentDescription = cardLabel }
                .padding(horizontal = CdsDimens.lg, vertical = CdsDimens.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (expandable) {
                Icon(
                    CdsIcons.ICaretRight,
                    contentDescription = null,
                    tint = c.textSecondary,
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(rotation),
                )
            }
            Text(
                text = ToolText.label(block.name),
                color = if (running) c.textPrimary.copy(alpha = shimmerAlpha()) else c.textPrimary,
                fontSize = CdsDimens.footnoteSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CdsFontFamilies.sans,
                modifier = Modifier.padding(start = if (expandable) CdsDimens.md else 0.dp),
            )
            Text(
                ToolText.summary(block),
                color = c.textSecondary,
                fontSize = CdsDimens.footnoteSize,
                fontFamily = CdsFontFamilies.sans,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = CdsDimens.md),
            )
            Text(
                ToolText.badge(block),
                color = if (block.status == "error") c.danger else c.textMuted,
                fontSize = CdsDimens.captionSize,
                fontFamily = CdsFontFamilies.sans,
            )
        }

        AnimatedVisibility(
            visible = open && expandable,
            enter = expandVertically(animationSpec = CdsMotion.expandSize()) + fadeIn(),
            exit = shrinkVertically(animationSpec = CdsMotion.expandSize()) + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(start = CdsDimens.lg, end = CdsDimens.lg, bottom = CdsDimens.lg),
                verticalArrangement = Arrangement.spacedBy(CdsDimens.sm),
            ) {
                block.input?.let { input ->
                    MonoBlock(input.toString(2), wrap = wrap)
                }
                block.result?.let { result ->
                    MonoBlock(result, isError = block.isError, wrap = wrap)
                }
            }
        }
    }
}

@Composable
private fun MonoBlock(text: String, isError: Boolean = false, wrap: Boolean = false) {    val c = cdsColors
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.codeBg, RoundedCornerShape(CdsDimens.radiusCode))
            .padding(CdsDimens.md)
            .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())),
    ) {
        Text(
            text = text,
            color = if (isError) c.danger else c.textPrimary,
            fontSize = CdsDimens.codeSize,
            lineHeight = CdsDimens.lineCode,
            fontFamily = CdsFontFamilies.mono,
        )
    }
}

/** 思考多久没有新文字就算结束（毫秒） */
private const val THINKING_SETTLE_MS = 900L

/**
 * 运行中文字的微光：亮度缓慢呼吸。reduced-motion 打开时固定为不透明。
 */
@Composable
private fun shimmerAlpha(): Float {
    if (reducedMotion) return 1f
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    return alpha
}

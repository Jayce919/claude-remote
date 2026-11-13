package com.clauderemote.app.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clauderemote.app.data.Block
import com.clauderemote.app.data.ChatMessage
import com.clauderemote.app.data.ChatUiState
import com.clauderemote.app.data.ImageAttachment
import com.clauderemote.app.data.Money
import com.clauderemote.app.data.PendingControl
import com.clauderemote.app.data.compactTokens
import com.clauderemote.app.data.contextLimit
import com.clauderemote.app.data.currentChannelLabel
import com.clauderemote.app.data.percentOf
import com.clauderemote.app.data.providerName
import com.clauderemote.app.data.realModelName
import com.clauderemote.app.ui.cards.ToolText
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.sheet.permissionLabel
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.CdsIcons
import com.clauderemote.app.ui.theme.CdsMotion
import com.clauderemote.app.ui.theme.ClaudeMark
import com.clauderemote.app.ui.theme.cdsColors
import com.clauderemote.app.ui.theme.reducedMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Claude Code 原版转圈动词。CLI 在等待时不是只显示 Thinking/Working，
 * 而是从这一组里随机轮换，这里沿用同一套文案。
 */
private val STATUS_VERBS = listOf(
    "Thinking", "Working", "Sending", "Accomplishing", "Actioning", "Baking",
    "Booping", "Brewing", "Calculating", "Cerebrating", "Channelling", "Churning",
    "Clauding", "Coalescing", "Cogitating", "Combobulating", "Computing", "Concocting",
    "Conjuring", "Considering", "Contemplating", "Cooking", "Crafting", "Creating",
    "Crunching", "Deciphering", "Deliberating", "Determining", "Doing", "Effecting",
    "Elucidating", "Enchanting", "Envisioning", "Finagling", "Forging", "Forming",
    "Generating", "Germinating", "Hatching", "Herding", "Honking", "Hustling",
    "Ideating", "Imagining", "Incubating", "Inferring", "Manifesting", "Marinating",
    "Mulling", "Musing", "Noodling", "Percolating", "Pondering", "Processing",
    "Puttering", "Puzzling", "Reticulating", "Ruminating", "Schlepping", "Shimmying",
    "Shucking", "Simmering", "Smooshing", "Spinning", "Stewing", "Synthesizing",
    "Tinkering", "Transmuting", "Vibing", "Wandering", "Whirring", "Zesting",
)

@Composable
fun ChatScreen(
    state: ChatUiState,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenPermission: () -> Unit,
    onOpenModel: () -> Unit,
    onRename: (String) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onAnswer: (String, Map<String, String>) -> Unit,
    onDecide: (String, Boolean) -> Unit,
    attachments: List<ImageAttachment>,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onDraftConsumed: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val c = cdsColors
    val activeTitle = state.conversations
        .firstOrNull { it.id == state.activeId }
        ?.title
        ?.ifBlank { tr("新对话", "New chat") }
        ?: tr("新对话", "New chat")
    var renaming by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.pageBg),
    ) {
        TopBar(
            title = activeTitle,
            subtitle = subtitleOf(state),
            canRename = state.activeId != null,
            onRenameTitle = { if (state.activeId != null) renaming = true },
            onOpenDrawer = onOpenDrawer,
            onNewChat = onNewChat,
        )

        // 上下文占比、本对话 token 与实时速度；没有数据时整行不占位
        ContextBar(state)

        // 消息区顶部的一条紧凑状态：错误（可重试 / 可关闭）、断线重连、重连成功
        StateStrip(state, onDismiss = onDismissError, onRetry = onRetry)

        Box(Modifier.weight(1f)) {
            // 切会话：旧内容 100ms 淡出，新内容 160ms 从 4dp 处淡入（Arc 的线程切换）
            val density = LocalDensity.current
            val noMotion = reducedMotion
            AnimatedContent(
                targetState = state.activeId,
                transitionSpec = {
                    val enter = tween<Float>(if (noMotion) 0 else 160, easing = CdsMotion.EaseOut)
                    val exit = tween<Float>(if (noMotion) 0 else 100, easing = CdsMotion.EaseSnap)
                    val rise = with(density) { 4.dp.roundToPx() }
                    val slide = tween<androidx.compose.ui.unit.IntOffset>(if (noMotion) 0 else 160, easing = CdsMotion.EaseOut)
                    (fadeIn(enter) + slideInVertically(slide) { rise }).togetherWith(fadeOut(exit))
                },
                label = "conversation",
            ) {
                MessageList(state, onAnswer, onDecide)
            }
        }

        Composer(
            running = state.running,
            onSend = onSend,
            onStop = onStop,
            onAttach = onAttach,
            onOpenPermission = onOpenPermission,
            onOpenModel = onOpenModel,
            modelLabel = state.realModelName().ifBlank { tr("默认模型", "Default model") },
            permissionLabel = permissionLabel(state.permissionMode),
            // 有两条以上通道时，模型 chip 前多一个通道圆点；只有一条时不画，保持原样
            channelLabel = state.currentChannelLabel,
            attachments = attachments,
            onRemoveAttachment = onRemoveAttachment,
            draft = state.draft,
            onDraftConsumed = onDraftConsumed,
        )
    }

    // 点顶栏标题直接改名；没有活动会话时不弹（标题还只是「新对话」占位）
    if (renaming && state.activeId != null) {
        RenameDialog(
            initial = activeTitle,
            onDismiss = { renaming = false },
            onConfirm = { title ->
                renaming = false
                onRename(title)
            },
        )
    }
}

private enum class StripTone { Error, Warn, Success }

/**
 * 消息区顶部的状态条。同一时间只显示一条：
 * 重连成功（短暂） > 正在重连 > 错误。不抢消息空间，只占一行。
 */
@Composable
private fun StateStrip(state: ChatUiState, onDismiss: () -> Unit, onRetry: () -> Unit) {
    val errorText = state.error ?: state.connectionError
    when {
        state.reconnectedHint -> StatusStrip(
            text = tr("已重新连接", "Reconnected"),
            tone = StripTone.Success,
        )
        state.reconnecting -> StatusStrip(
            text = tr("连接已断开，正在重连…", "Connection lost, reconnecting…"),
            tone = StripTone.Warn,
        )
        errorText != null -> StatusStrip(
            text = errorText,
            tone = StripTone.Error,
            actionLabel = if (state.errorRetry != null) tr("重试", "Retry") else null,
            onAction = onRetry,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun StatusStrip(
    text: String,
    tone: StripTone,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
) {
    val c = cdsColors
    val tint = when (tone) {
        StripTone.Error -> c.danger
        StripTone.Warn -> c.warning
        StripTone.Success -> c.success
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CdsDimens.lg, vertical = CdsDimens.xs)
            .clip(RoundedCornerShape(CdsDimens.radius))
            .background(c.surface2, RoundedCornerShape(CdsDimens.radius))
            .border(CdsDimens.hairline, c.border, RoundedCornerShape(CdsDimens.radius))
            .padding(start = CdsDimens.md, end = CdsDimens.sm, top = CdsDimens.sm, bottom = CdsDimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Spacer(Modifier.width(CdsDimens.sm))
        Text(
            text,
            color = c.textPrimary,
            fontSize = CdsDimens.footnoteSize,
            lineHeight = CdsDimens.lineFootnote,
            fontFamily = CdsFontFamilies.sans,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            Text(
                actionLabel,
                color = c.accentText,
                fontSize = CdsDimens.footnoteSize,
                fontFamily = CdsFontFamilies.sans,
                modifier = Modifier
                    .clip(RoundedCornerShape(CdsDimens.radiusXs))
                    .clickable { onAction() }
                    .padding(horizontal = CdsDimens.md, vertical = CdsDimens.xs),
            )
        }
        if (onDismiss != null) {
            // 语义回调不是 @Composable，先在组合期把文案取出来
            val dismissLabel = tr("关闭提示", "Dismiss")
            Text(
                "×",
                color = c.textMuted,
                fontSize = CdsDimens.bodySize,
                fontFamily = CdsFontFamilies.sans,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onDismiss() }
                    // 纯符号按钮，读屏只说「×」没有意义；清掉符号本身，换成动作名
                    .clearAndSetSemantics { contentDescription = dismissLabel }
                    .padding(horizontal = CdsDimens.sm, vertical = CdsDimens.xs),
            )
        }
    }
}

@Composable
private fun TopBar(    title: String,
    subtitle: String,
    canRename: Boolean,
    onRenameTitle: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
) {
    val c = cdsColors
    // 可点标题的名字：标题 + 副标题，副标题为空时只用标题
    val renameLabel = if (subtitle.isBlank()) title else "$title, $subtitle"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.pageBg)
            .statusBarsPadding()
            // 两侧动作的触摸层从 40×44 加到 48×48，页面高度会多 4dp；
            // 这里把外边距各收 2dp（水平 8→4、垂直 8→6），图标中心与整栏高度都不变。
            .padding(horizontal = CdsDimens.xs, vertical = CdsDimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(CdsIcons.IChats, tr("会话列表", "Chats"), onOpenDrawer)
        Column(
            modifier = Modifier
                .weight(1f)
                // 标题和副标题都是子节点：可点重命名时把两行拼成一句给容器，
                // 容器自己没有 text，clickable 也不会把子节点文字带上来。
                .then(
                    if (canRename) {
                        Modifier
                            .clickable { onRenameTitle() }
                            .clearAndSetSemantics { contentDescription = renameLabel }
                    } else {
                        Modifier
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 切会话 / 切模型时标题与副标题一起交叉淡变，和聊天区的切换动画同步
            val titleIn = tween<Float>(CdsMotion.duration(160), easing = CdsMotion.EaseOut)
            val titleOut = tween<Float>(CdsMotion.duration(100), easing = CdsMotion.EaseSnap)
            AnimatedContent(
                targetState = title,
                transitionSpec = { fadeIn(titleIn).togetherWith(fadeOut(titleOut)) },
                label = "topTitle",
            ) { text ->
                Text(
                    text,
                    color = c.textPrimary,
                    fontSize = CdsDimens.bodySize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CdsFontFamilies.sans,
                    maxLines = 1,
                )
            }
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = { fadeIn(titleIn).togetherWith(fadeOut(titleOut)) },
                label = "topSubtitle",
            ) { text ->
                Text(
                    text,
                    color = c.textMuted,
                    fontSize = CdsDimens.captionSize,
                    fontFamily = CdsFontFamilies.sans,
                    maxLines = 1,
                )
            }
        }
        IconButton(CdsIcons.IChatAdd, tr("新建对话", "New chat"), onNewChat)
    }
}

@Composable
private fun IconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = cdsColors
    // 视觉仍是 21dp 图标，命中区域补到 48×48（无障碍最小触控目标）。
    // 外层多出的留白由 TopBar 的 padding 抵消，图标位置不移动。
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable { onClick() }
            // 名字在图标上，可点容器自己读不出来；直接给容器一个名字
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun MessageList(
    state: ChatUiState,
    onAnswer: (String, Map<String, String>) -> Unit,
    onDecide: (String, Boolean) -> Unit,
) {
    val c = cdsColors
    val listState = rememberLazyListState()

    // 反向列表：索引 0 就是最底部。停在底部时，内容变高不会把视口顶走，
    // 流式文本也就不需要每个 token 都滚一次——之前的快速上下闪屏正来自这里。
    var autoFollow by remember { mutableStateOf(true) }

    // 刚切换会话的一小段时间里不跑条目入场/位移动画。
    // 冷加载时一直关到内容真正回来，再额外等 400ms，避免长历史一次性铺开时卡顿。
    var animateItems by remember { mutableStateOf(true) }
    LaunchedEffect(state.activeId, state.loadingConversation) {
        animateItems = false
        if (state.loadingConversation) return@LaunchedEffect
        delay(400)
        animateItems = true
    }

    // 反向列表里索引 0 就是最底部。用 firstVisibleItemIndex（纯索引，不涉及
    // offset 的方向语义）判断是否在底部：一离开 0 就脱离跟随，滑回 0 才恢复。
    // 原来的做法是滚动停止后读 offset 估算「离底部多远」，在反向布局里 offset
    // 的方向不可靠，用户上翻看历史时会被误判成还在底部，于是每个结构变化都
    // scrollToItem(0) 把屏幕拽回最新消息——表现就是「一直往下跳」。
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { first -> autoFollow = (first == 0) }
    }

    // 切会话：恢复跟随，并落到最新一条（反向列表的索引 0）
    LaunchedEffect(state.activeId, state.loadingConversation) {
        if (state.loadingConversation) return@LaunchedEffect
        autoFollow = true
        if (state.messages.isNotEmpty()) listState.scrollToItem(0)
    }

    // 只在结构变化时贴底：新消息、状态行出现或消失。
    // 流式文本不再触发滚动，交给反向布局的底部锚点。
    LaunchedEffect(state.messages.size, state.running, state.ui.showStats) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val lastIsUser = state.messages.lastOrNull()?.role == "user"
        if (lastIsUser) autoFollow = true
        if (autoFollow) listState.scrollToItem(0)
    }

    if (state.messages.isEmpty() && state.loadingConversation) {
        // 没有本地缓存时才需要等网络。给一个明确的轻量等待态，
        // 比白屏或闪一下空状态更不容易让人以为卡死。
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ClaudeShrinkMark(24.dp)
            Spacer(Modifier.height(CdsDimens.md))
            Text(
                tr("正在载入对话…", "Loading conversation…"),
                color = c.textMuted,
                fontSize = CdsDimens.footnoteSize,
                fontFamily = CdsFontFamilies.sans,
            )
        }
        return
    }

    if (state.messages.isEmpty() && !state.loadingConversation) {
        // 打开失败 ≠ 空会话：错误和重试按钮已经在顶部状态条里，这里不再显示问候空态
        if (state.conversationLoadFailed) return
        // 官方空状态是一枚标志加一句衬线问候，不是干巴巴一行提示
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ClaudeMark(modifier = Modifier.size(44.dp), color = c.clay)
            Spacer(Modifier.height(CdsDimens.xxl))
            Text(
                tr("今天想做点什么？", "What shall we work on today?"),
                color = c.textPrimary,
                fontSize = CdsDimens.headlineSize,
                fontFamily = CdsFontFamilies.serif,
            )
            Spacer(Modifier.height(CdsDimens.md))
            Text(
                tr("Claude Remote · 消息由电脑上的 Claude Code 处理，回复实时回传", "Claude Remote · Messages are handled by Claude Code on your computer, replies stream back live"),
                color = c.textMuted,
                fontSize = CdsDimens.footnoteSize,
                lineHeight = CdsDimens.lineFootnote,
                fontFamily = CdsFontFamilies.sans,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = CdsDimens.xxxl),
            )
        }
        return
    }

    // 状态行右侧显示当前真实操作，而不是只转圈
    val lastAssistant = state.messages.lastOrNull { it.role == "assistant" }
    val pendingTool = lastAssistant?.blocks?.lastOrNull { it is Block.Tool && it.status == "pending" } as? Block.Tool
    val action = when {
        state.pending.isNotEmpty() -> tr("等待你的确认", "Waiting for your confirmation")
        pendingTool != null -> ToolText.running(pendingTool).removePrefix(tr("正在", ""))
        else -> null
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = CdsDimens.lg,
            end = CdsDimens.lg,
            top = CdsDimens.md,
            bottom = CdsDimens.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(CdsDimens.messageGap),
    ) {
        // 反向布局里先写的在最底部：状态行 → 统计行 → 从新到旧的消息
        if (state.running) {
            item("status") { StatusLine(action) }
        }
        if (state.ui.showStats && !state.running && state.lastDurationMs > 0) {
            item("stats") { StatsLine(state.lastDurationMs, state.lastCost, state.ui.currency) }
        }
        items(state.messages.asReversed(), key = { it.id }) { msg ->
            // 运行中或刚切会话时不做 placement 动画，避免长回复/长历史铺开时上下抖。
            // 只保留位移：animateItem 默认的淡入会在上下滑动、列表懒加载新条目时
            // 又放一遍入场动画，看起来像内容边滑边闪。
            val itemModifier = if (state.running || !animateItems) {
                Modifier
            } else {
                Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
            }
            Box(itemModifier) {
                MessageItem(
                    message = msg,
                    pending = pendingFor(state, msg),
                    ui = state.ui,
                    running = state.running,
                    onAnswer = onAnswer,
                    onDecide = onDecide,
                )
            }
        }
    }
}

/**
 * 顶栏下方的一条细用量带：上下文占比、本对话累计 token、运行中的估算速度。
 * 数值来自服务端 usage 事件；旧服务端没有该事件时整条自动隐藏。
 */
@Composable
private fun ContextBar(state: ChatUiState) {
    if (state.contextTokens <= 0 && state.convTokens <= 0) return
    val c = cdsColors
    val limit = state.contextLimit
    val pct = percentOf(state.contextTokens, limit)

    // 工具执行期间没有新文本，也需要让速度数字回落到零，所以按时间重算
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.running) {
        while (state.running) {
            delay(700)
            tick++
        }
    }
    val realTps = state.lastTps
    val speed = remember(tick, state.streamTokens, state.streamStartedAt, state.running, realTps) {
        when {
            // 服务端用真实 output_tokens 算出的值优先，本轮结束后仍然保留
            realTps > 0 -> realTps
            !state.running || state.streamStartedAt <= 0 -> 0.0
            else -> {
                val seconds = ((System.currentTimeMillis() - state.streamStartedAt).coerceAtLeast(600)) / 1000.0
                state.streamTokens / seconds
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.pageBg)
            .padding(start = CdsDimens.lg, end = CdsDimens.lg, bottom = CdsDimens.xs),
        verticalArrangement = Arrangement.spacedBy(CdsDimens.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.contextTokens > 0) tr("上下文 $pct%", "Context $pct%") else tr("上下文 —", "Context —"),
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                fontFamily = CdsFontFamilies.sans,
            )
            Spacer(Modifier.width(CdsDimens.md))
            if (state.contextTokens > 0) {
                Text(
                    "${compactTokens(state.contextTokens)} / ${compactTokens(limit)}",
                    color = c.textMuted,
                    fontSize = CdsDimens.captionSize,
                    fontFamily = CdsFontFamilies.mono,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                tr("本对话 ${compactTokens(state.convTokens)}", "This chat ${compactTokens(state.convTokens)}"),
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                fontFamily = CdsFontFamilies.mono,
                maxLines = 1,
            )
            if (speed > 0) {
                Text(
                    if (realTps > 0) "  ${speed.roundToInt()} tok/s" else "  ≈ ${speed.roundToInt()} tok/s",
                    color = c.accentText,
                    fontSize = CdsDimens.captionSize,
                    fontFamily = CdsFontFamilies.mono,
                    maxLines = 1,
                )
            }
        }
        if (state.contextTokens > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(c.border, RoundedCornerShape(50)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((pct / 100f).coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(if (pct >= 90) c.danger else c.accent, RoundedCornerShape(50)),
                )
            }
        }
    }
}

/** 一轮结束后的小字统计，默认关掉，在界面设置里开 */
@Composable
private fun StatsLine(durationMs: Long, cost: Double, currency: String) {
    val c = cdsColors
    val seconds = durationMs / 1000.0
    val text = buildString {
        append(tr("用时 ", "Duration "))
        append(if (seconds >= 60) tr("%.1f 分".format(seconds / 60), "%.1f min".format(seconds / 60)) else tr("%.1f 秒".format(seconds), "%.1f s".format(seconds)))
        if (cost > 0) append(" · ${Money.format(cost, currency, 4)}")
    }
    Text(
        text,
        color = c.textMuted,
        fontSize = CdsDimens.captionSize,
        fontFamily = CdsFontFamilies.mono,
    )
}

/**
 * 待处理卡片只挂到它真正属于的那条消息上。
 * 之前把整张表传给每条助手消息，结果是同一条选框卡在历史里重复出现好几次。
 */
private fun pendingFor(state: ChatUiState, msg: ChatMessage): Map<String, PendingControl> {
    if (state.pending.isEmpty() || msg.role != "assistant") return emptyMap()
    val own = msg.blocks.filterIsInstance<Block.Tool>().map { it.toolId }.toSet()
    // 断线重连后工具块可能不在任何消息里，兜底挂到最后一条助手消息上
    val lastId = state.messages.lastOrNull { it.role == "assistant" }?.id
    if (own.isNotEmpty() || msg.id == lastId) {
        val everywhere = state.messages
            .flatMap { m -> m.blocks.filterIsInstance<Block.Tool>().map { it.toolId } }
            .toSet()
        return state.pending.filter { (id, _) -> id in own || (msg.id == lastId && id !in everywhere) }
    }
    return emptyMap()
}

/**
 * 等待状态：Claude 星芒收缩呼吸动画 + Claude Code 原版动词轮换 + 当前操作。
 * 17dp 的星芒在手机上不抢内容，又能让等待过程有「Claude 在工作」的辨识度。
 */
@Composable
private fun StatusLine(action: String?) {
    val c = cdsColors
    val verbs = remember { STATUS_VERBS.shuffled() }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(if (index == 0) 900 else 2100)
            index = (index + 1) % verbs.size
        }
    }
    val verb = verbs[index]
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClaudeShrinkMark(17.dp)
        Spacer(Modifier.width(CdsDimens.md))
        Crossfade(
            targetState = verb to action,
            animationSpec = tween(260),
            modifier = Modifier.weight(1f),
            label = "verb",
        ) { (currentVerb, currentAction) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$currentVerb…",
                    color = c.textMuted,
                    fontSize = CdsDimens.footnoteSize,
                    fontFamily = CdsFontFamilies.sans,
                    maxLines = 1,
                )
                if (!currentAction.isNullOrBlank()) {
                    Text(
                        " · $currentAction",
                        color = c.textMuted,
                        fontSize = CdsDimens.footnoteSize,
                        fontFamily = CdsFontFamilies.sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Claude 星芒的收缩呼吸动画：缩放、轻微旋转、透明度一起变化 */
@Composable
private fun ClaudeShrinkMark(size: Dp) {
    val clay = cdsColors.clay
    // 减动效时不启动无限动画（把时长调短没有用，循环本身就会一直分心），
    // 直接按静止姿态画完整标志：缩放 1、不旋转、完全不透明。
    if (reducedMotion) {
        ClaudeMark(modifier = Modifier.size(size), color = clay)
        return
    }
    val transition = rememberInfiniteTransition(label = "claude-mark")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.76f,
        animationSpec = infiniteRepeatable(tween(760, easing = CdsMotion.EaseOut), RepeatMode.Reverse),
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(760, easing = CdsMotion.EaseOut), RepeatMode.Reverse),
        label = "alpha",
    )
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(1520, easing = LinearEasing), RepeatMode.Reverse),
        label = "angle",
    )
    ClaudeMark(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = angle
                this.alpha = alpha
            },
        color = clay,
    )
}

/** 顶栏副标题：通道（或供应商） · 真实模型；没有订阅，这里永远显示第三方模型名 */
private fun subtitleOf(state: ChatUiState): String {
    val provider = state.providerName.trim()
    // 有通道时用通道名顶替供应商名，避免出现「DeepSeek · DeepSeek 官方」这种重复
    val channel = state.currentChannelLabel.trim()
    val head = channel.ifBlank { provider }
    val model = state.realModelName().trim()
    val parts = listOfNotNull(head.ifBlank { null }, model.ifBlank { null })
    return if (parts.isEmpty()) state.cwd else parts.joinToString(" · ")
}

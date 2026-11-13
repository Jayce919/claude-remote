package com.clauderemote.app.ui.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clauderemote.app.data.ConversationSummary
import com.clauderemote.app.ui.chat.RenameDialog
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.CdsIcons
import com.clauderemote.app.ui.theme.CdsMotion
import com.clauderemote.app.ui.theme.ClaudeMark
import com.clauderemote.app.ui.theme.Clawd
import com.clauderemote.app.ui.theme.cdsColors
import com.clauderemote.app.ui.theme.reducedMotion
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 会话抽屉，布局参考 DeepSeek 的侧边栏：
 * 顶部搜索、按时间分组、每行右侧 ⋯ 菜单、底部品牌与设置入口。
 * 配色仍用 Claude 自己的暖色调，圆角与间距按截图量出来的比例放宽。
 */
@Composable
fun ConversationDrawer(
    drawerOpen: Boolean,
    conversations: List<ConversationSummary>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDelete: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: (String) -> Unit,
    searchQuery: String,
    searchResults: List<ConversationSummary>?,
    searching: Boolean,
    onClose: () -> Unit,
) {
    val c = cdsColors
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var renaming by remember { mutableStateOf<ConversationSummary?>(null) }
    var pendingDelete by remember { mutableStateOf<List<String>?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 语义回调不是 @Composable，先在组合期把文案取出来（关闭键和右侧点按区共用）
    val closeLabel = tr("关闭会话列表", "Close chat list")

    // 每次打开抽屉都清掉上次的搜索词，避免一进来就停在旧结果上；
    // 关闭时重置多选，和以前抽屉销毁重建的行为保持一致。
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) onSearch("")
        else {
            selecting = false
            selected = emptySet()
        }
    }

    // 会话被删掉后，选中集合里只保留仍然存在的 id；整组删完自动退出多选
    LaunchedEffect(conversations) {
        val alive = conversations.map { it.id }.toSet()
        val next = selected intersect alive
        if (selecting && selected.isNotEmpty() && next.isEmpty()) selecting = false
        selected = next
    }

    // 返回键：多选时先退出多选，否则关闭抽屉。抽屉自己的回调优先级最高，
    // 不会和 App 层的返回处理抢同一个按键。
    // 只在抽屉打开时启用：ModalNavigationDrawer 会一直组合抽屉内容，不能无条件拦截返回。
    BackHandler(enabled = drawerOpen) {
        if (selecting) {
            selecting = false
            selected = emptySet()
        } else {
            onClose()
        }
    }

    pendingDelete?.let { ids ->
        DeleteConfirmDialog(
            count = ids.size,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                selecting = false
                selected = emptySet()
                onDelete(ids)
            },
        )
    }

    renaming?.let { conv ->
        RenameDialog(
            initial = conv.title.ifBlank { tr("未命名对话", "Untitled chat") },
            onDismiss = { renaming = null },
            onConfirm = { title ->
                onRename(conv.id, title)
                renaming = null
            },
        )
    }

    val row: @Composable (ConversationSummary) -> Unit = { conv ->
        ConversationRow(
            conv = conv,
            active = conv.id == activeId,
            selecting = selecting,
            selected = conv.id in selected,
            onOpen = { onSelect(conv.id) },
            onToggleSelect = {
                selected = if (conv.id in selected) selected - conv.id else selected + conv.id
            },
            onRename = { renaming = conv },
            onTogglePin = {
                onTogglePin(conv.id)
                // 置顶后会话会移到最上方。LazyColumn 会把新插入的项留在视口外，
                // 这里回到顶部，让「置顶」分组立刻可见，而不是像凭空消失。
                scope.launch { listState.scrollToItem(0) }
            },
            onMulti = {
                onSearch("")
                selecting = true
                selected = setOf(conv.id)
            },
            onDelete = { pendingDelete = listOf(conv.id) },
        )
    }

    // 遮罩层改由 ModalNavigationDrawer 提供，透明度随拖拽进度联动；
    // 这里只保留面板 + 右侧点击关闭区，维持原来的 84% 宽度视觉。
    Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .fillMaxHeight()
                    // 面板整体吞掉点击，避免点在空白处穿透到背后的输入框把它聚焦
                    .pointerInput(Unit) { detectTapGestures { } }
                    .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                    .background(c.surface1)
                    .statusBarsPadding(),
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 关闭按钮的触摸层撑到 48dp 后整行会高 16dp；
                    // 去掉上下留白把它抵回来，标题的垂直中心仍在原来的位置。
                    .padding(start = CdsDimens.xl, end = CdsDimens.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr("会话", "Chats"),
                    color = c.textPrimary,
                    fontSize = CdsDimens.headlineSize,
                    fontFamily = CdsFontFamilies.serif,
                    modifier = Modifier.weight(1f),
                )
                // 语义回调不是 @Composable，先在组合期把文案取出来
                Box(
                    Modifier
                        .size(48.dp)
                        .clickable { onClose() }
                        // 名字给在可点容器上，子节点里的「✕」不再单独朗读
                        .clearAndSetSemantics { contentDescription = closeLabel },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .size(CdsDimens.iconButtonSmall)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "✕",
                            color = c.textMuted,
                            fontSize = CdsDimens.bodySize,
                        )
                    }
                }
            }

            if (selecting) {
                SelectionHeader(
                    count = selected.size,
                    onCancel = {
                        selecting = false
                        selected = emptySet()
                    },
                    onDelete = { if (selected.isNotEmpty()) pendingDelete = selected.toList() },
                )
            } else {
                SearchField(value = searchQuery, onSearch = onSearch)
            }

            Spacer(Modifier.height(CdsDimens.sm))

            Box(Modifier.weight(1f)) {
                val query = searchQuery.trim()
                when {
                    query.isNotEmpty() -> SearchResults(
                        results = searchResults.orEmpty(),
                        searching = searching,
                        row = row,
                    )

                    conversations.isEmpty() -> EmptyState()

                    else -> ConversationGroups(
                        conversations = conversations,
                        state = listState,
                        row = row,
                    )
                }
            }

            Footer(onOpenSettings = onOpenSettings)
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable { onClose() }
                // 面板右侧的点按区没有子节点，只能显式给名字，否则是个空按钮
                .semantics { contentDescription = closeLabel },
        )
    }
}

@Composable
private fun SearchField(value: String, onSearch: (String) -> Unit) {
    val c = cdsColors
    val focus = remember { FocusRequester() }
    // 语义回调不是 @Composable，先在组合期把文案取出来
    val searchLabel = tr("搜索对话内容…", "Search conversations…")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CdsDimens.lg)
            .height(40.dp)
            .clip(RoundedCornerShape(CdsDimens.radiusComposer))
            .background(c.surface2)
            // 搜索框是输入容器，边界要和输入区一样看得清
            .border(1.dp, c.borderControl, RoundedCornerShape(CdsDimens.radiusComposer))
            // 点框内空白也把焦点交给输入框。这里用 pointerInput 而不是 clickable：
            // clickable 会在无障碍树里多出一个没有名字、和输入框重叠的可点节点，
            // 而名字本来就该由输入框自己提供。点按行为完全一样（原来也没有涟漪）。
            .pointerInput(Unit) { detectTapGestures { focus.requestFocus() } }
            .padding(horizontal = CdsDimens.lg2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            CdsIcons.ISearch,
            contentDescription = null,
            tint = c.textMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(CdsDimens.md))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    searchLabel,
                    color = c.textMuted,
                    fontSize = CdsDimens.bodySmallSize,
                    fontFamily = CdsFontFamilies.sans,
                    maxLines = 1,
                    // 占位符只是视觉提示，名字由下面输入框自己提供，别念两遍
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { onSearch(it) },
                textStyle = TextStyle(
                    color = c.textPrimary,
                    fontSize = CdsDimens.bodySmallSize,
                    fontFamily = CdsFontFamilies.sans,
                ),
                cursorBrush = SolidColor(c.accent),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    // 空输入框（无 text、无 content-desc）读屏只会说「编辑框」
                    .then(
                        if (value.isEmpty()) {
                            Modifier.semantics { contentDescription = searchLabel }
                        } else {
                            Modifier
                        }
                    ),
            )
        }
        if (value.isNotEmpty()) {
            // 语义回调不是 @Composable，先在组合期把文案取出来
            val clearLabel = tr("清除搜索", "Clear search")
            // 清除键的触摸层 48×40：搜索框整体只有 40dp 高，纵向靠 Compose
            // 自带的最小触控目标外扩；视觉仍是 22dp 的圆点，贴右缘原位不动。
            Box(
                Modifier
                    .size(width = 48.dp, height = 40.dp)
                    .clickable { onSearch("") }
                    // 名字给在可点容器上，子节点里的「×」不再单独朗读
                    .clearAndSetSemantics { contentDescription = clearLabel },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "×",
                        color = c.textMuted,
                        fontSize = CdsDimens.bodySmallSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionHeader(count: Int, onCancel: () -> Unit, onDelete: () -> Unit) {
    val c = cdsColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = CdsDimens.lg2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tr("已选 $count 个", "$count selected"),
            color = c.textPrimary,
            fontSize = CdsDimens.bodySmallSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CdsFontFamilies.sans,
            modifier = Modifier.weight(1f),
        )
        Text(
            tr("取消", "Cancel"),
            color = c.textSecondary,
            fontSize = CdsDimens.bodySmallSize,
            fontFamily = CdsFontFamilies.sans,
            modifier = Modifier
                .clip(RoundedCornerShape(CdsDimens.radiusLg))
                .clickable { onCancel() }
                .padding(horizontal = CdsDimens.md, vertical = CdsDimens.xs),
        )
        Spacer(Modifier.width(CdsDimens.sm))
        Text(
            tr("删除", "Delete"),
            color = if (count > 0) c.danger else c.textMuted,
            fontSize = CdsDimens.bodySmallSize,
            fontFamily = CdsFontFamilies.sans,
            modifier = Modifier
                .clip(RoundedCornerShape(CdsDimens.radiusLg))
                .clickable(enabled = count > 0) { onDelete() }
                .padding(horizontal = CdsDimens.md, vertical = CdsDimens.xs),
        )
    }
}

@Composable
private fun ConversationGroups(
    conversations: List<ConversationSummary>,
    state: LazyListState,
    row: @Composable (ConversationSummary) -> Unit,
) {
    val pinned = conversations.filter { it.pinned }
    val rest = conversations.filterNot { it.pinned }
    val buckets = listOf(
        ConvBucket.Today,
        ConvBucket.Yesterday,
        ConvBucket.Week,
        ConvBucket.Earlier,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(
            start = CdsDimens.sm,
            end = CdsDimens.sm,
            top = CdsDimens.xs,
            bottom = CdsDimens.md,
        ),
    ) {
        if (pinned.isNotEmpty()) {
            item(key = "header-pinned") { GroupLabel(tr("置顶", "Pinned")) }
            items(pinned, key = { it.id }) { conv -> row(conv) }
        }
        buckets.forEach { bucket ->
            val group = rest.filter { bucketOf(it.updatedAt) == bucket }
            if (group.isNotEmpty()) {
                item(key = "header-${bucket.name}") { GroupLabel(bucketLabel(bucket)) }
                items(group, key = { it.id }) { conv -> row(conv) }
            }
        }
        item(key = "total") {
            Text(
                tr("共 ${conversations.size} 个会话", "${conversations.size} chats total"),
                color = cdsColors.textMuted,
                fontSize = CdsDimens.captionSize,
                fontFamily = CdsFontFamilies.sans,
                modifier = Modifier.padding(
                    start = CdsDimens.lg,
                    top = CdsDimens.xl,
                    bottom = CdsDimens.md,
                ),
            )
        }
    }
}

@Composable
private fun SearchResults(
    results: List<ConversationSummary>,
    searching: Boolean,
    row: @Composable (ConversationSummary) -> Unit,
) {
    val c = cdsColors
    if (results.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (searching) tr("搜索中…", "Searching…") else tr("没有找到相关对话", "No matching chats"),
                color = c.textMuted,
                fontSize = CdsDimens.bodySmallSize,
                fontFamily = CdsFontFamilies.sans,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CdsDimens.sm,
            end = CdsDimens.sm,
            top = CdsDimens.xs,
            bottom = CdsDimens.md,
        ),
    ) {
        item(key = "header-search") { GroupLabel(tr("搜索结果", "Search results") + " · ${results.size}") }
        items(results, key = { it.id }) { conv -> row(conv) }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        color = cdsColors.textMuted,
        fontSize = CdsDimens.captionSize,
        fontFamily = CdsFontFamilies.sans,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = CdsDimens.lg,
                end = CdsDimens.lg,
                top = CdsDimens.lg,
                bottom = CdsDimens.xs,
            ),
    )
}

@Composable
private fun EmptyState() {
    val c = cdsColors
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ClaudeMark(Modifier.size(30.dp), color = c.clay.copy(alpha = 0.7f))
        Spacer(Modifier.height(CdsDimens.lg))
        Text(
            tr("还没有对话", "No chats yet"),
            color = c.textMuted,
            fontSize = CdsDimens.bodySmallSize,
            fontFamily = CdsFontFamilies.sans,
        )
    }
}

@Composable
private fun ConversationRow(
    conv: ConversationSummary,
    active: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onMulti: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = cdsColors
    var menu by remember { mutableStateOf(false) }
    // 语义回调不是 @Composable，先在组合期把文案取出来
    val moreLabel = tr("更多操作", "More actions")
    val rowLabel = conv.title.ifBlank { tr("未命名对话", "Untitled chat") }
    val background = when {
        selecting && selected -> c.accent.copy(alpha = 0.12f)
        active -> c.surface3
        else -> Color.Transparent
    }

    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CdsDimens.xxs, vertical = CdsDimens.xxs)
                .clip(RoundedCornerShape(CdsDimens.radiusPanel))
                .background(background)
                .clickable { if (selecting) onToggleSelect() else onOpen() }
                // 整行只报一个名字：标题在子节点上，读屏聚焦可点行时读不出来。
                // 「更多操作」按钮挪到行外（见下面），不会被这里一并清掉。
                .clearAndSetSemantics { contentDescription = rowLabel }
                .heightIn(min = 46.dp)
                .padding(start = CdsDimens.sm, end = CdsDimens.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                SelectionCheck(checked = selected)
                Spacer(Modifier.width(CdsDimens.md))
            }

            Text(
                rowLabel,
                color = if (active || (selecting && selected)) c.textPrimary else c.textSecondary,
                fontSize = CdsDimens.bodySmallSize,
                fontFamily = CdsFontFamilies.sans,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = CdsDimens.md),
            )

            if (conv.live) LiveDot()

            if (!selecting) {
                // 原来「⋯」按钮就占这一格：留同样的 48dp，行宽和文字换行位置都不变
                Spacer(Modifier.size(48.dp))
            }
        }

        if (!selecting) {
            // 「更多操作」放在可点行之外，行整体的语义清理不会波及它；
            // 位置与原来一致：贴右缘、缩进两层 xxs，垂直居中。
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = CdsDimens.xxs + CdsDimens.xxs),
            ) {
                // 触摸层 48×48，行高从 46dp 涨到 48dp（2dp，不影响行内文字基线）；
                // 视觉上的 ⋯ 仍是 30dp，贴右缘位置不变。
                Box(
                    Modifier
                        .size(48.dp)
                        .clickable { menu = true }
                        // 名字给在可点容器上，子节点里的「⋯」不再单独朗读
                        .clearAndSetSemantics { contentDescription = moreLabel },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "⋯",
                            color = c.textMuted,
                            fontSize = 18.sp,
                            fontFamily = CdsFontFamilies.sans,
                        )
                    }
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = c.surface2,
                    shape = RoundedCornerShape(CdsDimens.radiusPanel),
                    modifier = Modifier.width(156.dp),
                ) {
                    MenuItem(tr("重命名", "Rename")) { menu = false; onRename() }
                    MenuItem(if (conv.pinned) tr("取消置顶", "Unpin") else tr("置顶", "Pin")) {
                        menu = false
                        onTogglePin()
                    }
                    MenuItem(tr("多选", "Multi-select")) { menu = false; onMulti() }
                    HorizontalDivider(color = c.border)
                    MenuItem(tr("删除", "Delete"), danger = true) { menu = false; onDelete() }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = cdsColors
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (danger) c.danger else c.textPrimary,
                fontSize = CdsDimens.bodySmallSize,
                fontFamily = CdsFontFamilies.sans,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun SelectionCheck(checked: Boolean) {
    val c = cdsColors
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(CdsDimens.radius))
            .background(if (checked) c.accent else Color.Transparent)
            .border(
                width = 1.5.dp,
                // 未选中时的那圈线是「这里可以勾」的唯一提示，必须看得清；
                // borderStrong 是 20% 透明黑，在浅底上只剩一层灰雾，所以用 borderControl。
                color = if (checked) c.accent else c.borderControl,
                shape = RoundedCornerShape(CdsDimens.radius),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(
                "✓",
                color = c.onAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 运行中的小绿点：中间一颗实心点，外围一圈呼吸光晕。
 * 比原来的 6dp 平点更容易在列表里被注意到，又不会抢标题的注意力。
 */
@Composable
private fun LiveDot() {
    val c = cdsColors
    // 减动效时不建无限循环过渡（调短时长没有意义，循环本身就会一直闪），
    // 光晕固定在中档亮度，绿点仍然清楚可辨。
    val halo = if (reducedMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "live-dot")
        val animated by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = CdsMotion.EaseOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "halo",
        )
        animated
    }
    Box(
        modifier = Modifier
            .padding(end = CdsDimens.xxs)
            .size(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .background(c.success.copy(alpha = 0.10f + halo * 0.14f), CircleShape),
        )
        Box(Modifier.size(6.dp).background(c.success, CircleShape))
    }
}

@Composable
private fun Footer(onOpenSettings: () -> Unit) {
    val c = cdsColors
    // 语义回调不是 @Composable，先在组合期把文案取出来
    val settingsLabel = tr("设置", "Settings")
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = c.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                // 品牌栏比普通列表行高一点：小虫本身有高度，留白太少会显得局促。
                // 设置键触摸层加到 48dp 后整行会高 14dp，这里把上下留白 14→6 抵掉。
                .padding(horizontal = CdsDimens.lg2, vertical = CdsDimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clawd(width = 34.dp)
            Spacer(Modifier.width(CdsDimens.md2))
            Text(
                "Claude Remote",
                color = c.textPrimary,
                fontSize = CdsDimens.bodySmallSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CdsFontFamilies.sans,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clickable { onOpenSettings() }
                    // 名字在图标上，可点容器自己读不出来；直接给容器一个名字
                    .clearAndSetSemantics { contentDescription = settingsLabel },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .size(CdsDimens.iconButtonSmall)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        CdsIcons.ISettings,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = cdsColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface2,
        shape = RoundedCornerShape(CdsDimens.radiusComposer),
        title = {
            Text(
                if (count > 1) tr("删除 $count 个对话？", "Delete $count chats?") else tr("删除这个对话？", "Delete this chat?"),
                color = c.textPrimary,
                fontSize = CdsDimens.titleSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CdsFontFamilies.sans,
            )
        },
        text = {
            Text(
                tr("删除后无法恢复，电脑上对应的会话记录也会一并移除。", "This cannot be undone; the matching chat on the computer will be removed too."),
                color = c.textSecondary,
                fontSize = CdsDimens.bodySmallSize,
                lineHeight = CdsDimens.lineBodySmall,
                fontFamily = CdsFontFamilies.sans,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    tr("删除", "Delete"),
                    color = c.danger,
                    fontSize = CdsDimens.bodySmallSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CdsFontFamilies.sans,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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

private enum class ConvBucket {
    Today,
    Yesterday,
    Week,
    Earlier,
}

@Composable
private fun bucketLabel(bucket: ConvBucket): String = when (bucket) {
    ConvBucket.Today -> tr("今天", "Today")
    ConvBucket.Yesterday -> tr("昨天", "Yesterday")
    ConvBucket.Week -> tr("7天内", "Last 7 days")
    ConvBucket.Earlier -> tr("更早", "Earlier")
}

/** 按本地日期把会话分到今天 / 昨天 / 7 天内 / 更早 */
private fun bucketOf(timestamp: Long): ConvBucket {
    val today = LocalDate.now()
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0L -> ConvBucket.Today
        days == 1L -> ConvBucket.Yesterday
        days <= 7L -> ConvBucket.Week
        else -> ConvBucket.Earlier
    }
}

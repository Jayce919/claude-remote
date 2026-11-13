package com.clauderemote.app.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clauderemote.app.data.ImageAttachment
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.CdsMotion
import com.clauderemote.app.ui.theme.cdsColors
import com.clauderemote.app.ui.theme.reducedMotion

/** 回形针：Material 官方 attach_file 的实心路径，比原来随手画的加号更像「附件」 */
private val AttachIcon: ImageVector by lazy {
    ImageVector.Builder("attach", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(
                "M16.5 6v11.5c0 2.21-1.79 4-4 4s-4-1.79-4-4V5c0-1.38 1.12-2.5 2.5-2.5" +
                    "s2.5 1.12 2.5 2.5v10.5c0 .55-.45 1-1 1s-1-.45-1-1V6H10v9.5c0 1.38 1.12 2.5 2.5 2.5" +
                    "s2.5-1.12 2.5-2.5V5c0-2.21-1.79-4-4-4S7 2.79 7 5v12.5c0 3.04 2.46 5.5 5.5 5.5" +
                    "s5.5-2.46 5.5-5.5V6h-1.5z",
            ),
            fill = SolidColor(Color.Black),
        )
        .build()
}

/** 发送按钮的向上箭头（Material arrow_upward 实心版），比描边箭头更饱满 */
private val SendArrow: ImageVector by lazy {
    ImageVector.Builder("send", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z"),
            fill = SolidColor(Color.Black),
        )
        .build()
}

@Composable
fun Composer(
    running: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onOpenPermission: () -> Unit,
    onOpenModel: () -> Unit,
    modelLabel: String,
    permissionLabel: String,
    /** 非空表示有多条通道：模型 chip 前加一个圆点，让用户不进面板也知道走哪条 */
    channelLabel: String = "",
    attachments: List<ImageAttachment>,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    draft: String? = null,
    onDraftConsumed: () -> Unit = {},
) {
    val c = cdsColors
    var text by remember { mutableStateOf("") }
    val canSend = text.isNotBlank()
    val inputPlaceholder = tr("给 Claude 发消息…", "Message Claude…")
    // 语义回调不是 @Composable，先在组合期把文案取出来
    val attachLabel = tr("添加附件", "Add attachment")
    val sendLabel = tr("发送", "Send")

    // 发送失败：把 ViewModel 回填的草稿放回输入框，光标落到末尾
    LaunchedEffect(draft) {
        val incoming = draft
        if (!incoming.isNullOrEmpty()) {
            text = incoming
            onDraftConsumed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.pageBg)
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 768.dp)
                .fillMaxWidth()
                .padding(horizontal = CdsDimens.lg, vertical = CdsDimens.md),
            verticalArrangement = Arrangement.spacedBy(CdsDimens.md),
        ) {
        // 待发送附件预览：图片显示缩略图，普通文件显示文件名，均可点 × 移除
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CdsDimens.sm),
            ) {
                attachments.forEach { att ->
                    AttachmentPreview(att) { onRemoveAttachment(att) }
                }
            }
        }

        // 权限与模型各管一件事；「设置」已经收进左侧抽屉底部，不再在输入框下重复出现。
        // DeepSeek 把这一排小芯片固定在输入框上方，这里跟着上移。
        // 芯片的触摸层是 48dp 高（视觉仍是 24dp 左右），整行因此比原来高 24dp；
        // 下移 12dp 把视觉位置还原，输入框和整栏底部都还钉在原处。
        Row(
            modifier = Modifier.offset(y = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(CdsDimens.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(tr("权限", "Permission") + " · $permissionLabel", onClick = onOpenPermission)
            Chip(
                tr("模型", "Model") + " · $modelLabel",
                modifier = Modifier.weight(1f, fill = false),
                dot = channelLabel.isNotBlank(),
                onClick = onOpenModel,
            )
            if (running) {
                Chip(tr("停止", "Stop"), danger = true, onClick = onStop)
            }
        }

        // 附件、输入区、发送三块并排放在输入框内部；两侧按钮与文字整体垂直居中，
        // 单行时不会把输入区顶到上面，换行后按钮也跟着守在两侧。
        // 输入区独占中间剩余宽度，占位符和正文始终共用同一条换行边界。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface2, RoundedCornerShape(CdsDimens.radiusComposer))
                // 输入框用 borderControl 而不是 border：border 是分隔线级别的浅灰，
                // 围在 16dp 大圆角的输入区上几乎不可见，用户看不出这是可输入的框。
                .border(1.dp, c.borderControl, RoundedCornerShape(CdsDimens.radiusComposer))
                .padding(horizontal = CdsDimens.xs, vertical = CdsDimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(CdsDimens.iconButton)
                    .clip(CircleShape)
                    .clickable { onAttach() }
                    // clickable 只加点击动作，子节点的标签不会被带上来；
                    // 这个按钮只有图标，直接在可点容器上给出名字。
                    .clearAndSetSemantics { contentDescription = attachLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AttachIcon,
                    contentDescription = null,
                    tint = c.textSecondary,
                    modifier = Modifier.size(21.dp),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 24.dp, max = 160.dp)
                    .padding(horizontal = CdsDimens.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(
                        // 占位符只是视觉提示；空输入框的名字由下面 BasicTextField 的语义提供，
                        // 这里清掉，免得读屏把同一句话念两遍。
                        inputPlaceholder,
                        color = c.textMuted,
                        fontSize = CdsDimens.bodySize,
                        fontFamily = CdsFontFamilies.sans,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        color = c.textPrimary,
                        fontSize = CdsDimens.bodySize,
                        lineHeight = CdsDimens.lineBody,
                        fontFamily = CdsFontFamilies.sans,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        // 空输入框在无障碍树里既没有 text 也没有 content-desc，
                        // 读屏只会念「编辑框」；清空时用占位符文案当名字。
                        .then(
                            if (text.isEmpty()) {
                                Modifier.semantics { contentDescription = inputPlaceholder }
                            } else {
                                Modifier
                            }
                        ),
                )
            }

            val sendBg by animateColorAsState(
                targetValue = if (canSend) c.fillPrimary else c.border,
                animationSpec = CdsMotion.color(),
                label = "send",
            )
            Box(
                modifier = Modifier
                    .size(CdsDimens.iconButton)
                    .clip(CircleShape)
                    .background(sendBg, CircleShape)
                    // 运行中也能点：由外层弹出「排队 / 打断」选择
                    .clickable(enabled = canSend) {
                        val payload = text.trim()
                        text = ""
                        onSend(payload)
                    }
                    .clearAndSetSemantics { contentDescription = sendLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SendArrow,
                    contentDescription = null,
                    tint = if (canSend) c.onPrimary else c.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        }
    }
}


@Composable
private fun AttachmentPreview(att: ImageAttachment, onRemove: () -> Unit) {
    val c = cdsColors
    // 语义回调不是 @Composable，先在组合期把文案取出来
    val removeLabel = tr("移除附件", "Remove attachment")
    Box {
        if (att.mediaType.startsWith("image/") && (att.base64.isNotBlank() || att.preview.isNotBlank())) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(CdsDimens.radiusCode))
                    .background(c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                // 解码完成前先垫一个文件图标，避免缩略图区域看起来是一片空白
                Text(
                    "▤",
                    color = c.textMuted,
                    fontSize = CdsDimens.titleSize,
                    fontFamily = CdsFontFamilies.sans,
                )
                AttachmentImage(
                    base64 = att.base64,
                    preview = att.preview,
                    contentDescription = att.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .background(c.surface2, RoundedCornerShape(CdsDimens.radiusCode))
                    .border(1.dp, c.border, RoundedCornerShape(CdsDimens.radiusCode))
                    .padding(horizontal = CdsDimens.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CdsDimens.sm),
            ) {
                Text(
                    "▤",
                    color = c.textSecondary,
                    fontSize = CdsDimens.titleSize,
                    fontFamily = CdsFontFamilies.sans,
                )
                Text(
                    att.name,
                    color = c.textPrimary,
                    fontSize = CdsDimens.captionSize,
                    fontFamily = CdsFontFamilies.sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 132.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                // 触摸层 48×48，仍落在 64dp 的预览范围里，不改变缩略图尺寸；
                // 视觉上的小圆点仍贴在右上角原位。
                .size(48.dp)
                .clickable { onRemove() }
                // 名字给在可点容器上，子节点里的「×」不再单独朗读
                .clearAndSetSemantics { contentDescription = removeLabel },
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(c.pageBg.copy(alpha = 0.92f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "×",
                    color = c.textSecondary,
                    fontSize = CdsDimens.captionSize,
                    fontFamily = CdsFontFamilies.sans,
                )
            }
        }
    }
}

@Composable
private fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    /** 模型 chip 专用：8dp 的小圆点，用强调色标出「当前走哪条通道」 */
    dot: Boolean = false,
    onClick: () -> Unit,
) {
    val c = cdsColors
    val density = LocalDensity.current
    val noMotion = reducedMotion
    // 文案变化（切模型 / 切权限）时交叉淡变：旧字 100ms 淡出，新字 160ms 从 2dp 处淡入
    val enter = tween<Float>(if (noMotion) 0 else 160, easing = CdsMotion.EaseOut)
    val exit = tween<Float>(if (noMotion) 0 else 100, easing = CdsMotion.EaseSnap)
    Box(
        // 触摸层 48dp 高：原胶囊只有 24dp 左右，手指很难点中。
        // 点击响应放在外层热区，内层仍是原来的胶囊外观，视觉尺寸不变。
        modifier = modifier
            .height(48.dp)
            .clickable { onClick() }
            // 标签是子节点里的文字（权限 / 模型 / 停止），名字直接给在可点容器上；
            // clickable 不会把子节点的 text 带到自己身上。
            .clearAndSetSemantics { contentDescription = text },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(c.surface2, RoundedCornerShape(50))
                .border(1.dp, if (danger) c.danger.copy(alpha = 0.4f) else c.border, RoundedCornerShape(50))
                .padding(horizontal = CdsDimens.lg, vertical = CdsDimens.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CdsDimens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dot) {
                    // 只做位置提示，不占字号的视觉重量
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(c.accent),
                    )
                }
                AnimatedContent(
                    targetState = text,
                    transitionSpec = {
                        val rise = with(density) { 2.dp.roundToPx() }
                        val slide = tween<androidx.compose.ui.unit.IntOffset>(if (noMotion) 0 else 160, easing = CdsMotion.EaseOut)
                        (fadeIn(enter) + slideInVertically(slide) { rise }).togetherWith(fadeOut(exit))
                    },
                    label = "chipText",
                ) { label ->
                    Text(
                        text = label,
                        color = if (danger) c.danger else c.textSecondary,
                        fontSize = CdsDimens.captionSize,
                        fontFamily = CdsFontFamilies.sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

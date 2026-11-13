package com.clauderemote.app.ui.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.cdsColors

/**
 * 任务运行中按发送时弹出：排队等当前任务结束，或者先打断再发。
 * 服务端本来就支持在流式输入里继续喂用户消息，所以排队不需要额外接口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSendSheet(
    onDismiss: () -> Unit,
    onQueue: () -> Unit,
    onInterrupt: () -> Unit,
) {
    val c = cdsColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface2,
        shape = RoundedCornerShape(topStart = CdsDimens.radiusComposer, topEnd = CdsDimens.radiusComposer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CdsDimens.xxl, vertical = CdsDimens.md),
            verticalArrangement = Arrangement.spacedBy(CdsDimens.md),
        ) {
            SheetTitle(tr("Claude 正在工作", "Claude is working"))
            Text(
                tr("这条消息要怎么处理？", "How should this message be handled?"),
                color = c.textMuted,
                fontSize = CdsDimens.footnoteSize,
                fontFamily = CdsFontFamilies.sans,
            )
            SheetOptionRow(
                label = tr("排队发送", "Queue and send"),
                selected = false,
                subtitle = tr("等当前任务结束后再处理，上下文不丢", "Send after the current task finishes; context is preserved"),
                onClick = onQueue,
            )
            SheetOptionRow(
                label = tr("打断并发送", "Interrupt and send"),
                selected = false,
                subtitle = tr("停止当前任务，立刻开始处理这条", "Stop the current task and handle this now"),
                onClick = onInterrupt,
            )
            Text(
                tr("取消", "Cancel"),
                color = c.textMuted,
                fontSize = CdsDimens.bodySize,
                fontFamily = CdsFontFamilies.sans,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = CdsDimens.lg),
            )
            Spacer(Modifier.height(CdsDimens.xl))
        }
    }
}

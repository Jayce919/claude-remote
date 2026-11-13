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

/** 附件入口：图片走相册，其余文件走系统文件选择器 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    onDismiss: () -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
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
            SheetTitle(tr("添加附件", "Add attachment"))
            SheetOptionRow(
                label = tr("照片", "Photo"),
                selected = false,
                subtitle = tr("从相册选择图片，直接随消息发给 Claude", "Pick images from your gallery to send with the message"),
                onClick = onPickImages,
            )
            SheetOptionRow(
                label = tr("文件", "File"),
                selected = false,
                subtitle = tr("任意文件会先传到电脑，再把路径告诉 Claude", "Any file is uploaded to the computer first, then its path is shared with Claude"),
                onClick = onPickFiles,
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

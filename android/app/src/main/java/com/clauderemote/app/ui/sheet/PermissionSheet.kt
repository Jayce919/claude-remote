package com.clauderemote.app.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clauderemote.app.data.ChatUiState
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.cdsColors
import kotlinx.coroutines.launch

/**
 * 只管理权限模式。和模型、思考强度分开，打开这一层时不需要在无关选项里找。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSheet(
    state: ChatUiState,
    onDismiss: () -> Unit,
    onPermission: (String) -> Unit,
) {
    val c = cdsColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // 选完一项就把面板收起来：先跑收起动画，动画结束再通知外层移除
    fun choose(mode: String) {
        onPermission(mode)
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
            SheetTitle(tr("权限", "Permission"))

            Text(
                tr("控制 Claude 调用工具前是否需要先征求你的同意。", "Choose whether Claude asks for your approval before calling tools."),
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                lineHeight = CdsDimens.lineCaption,
                fontFamily = CdsFontFamilies.sans,
            )

            SheetSection(tr("权限模式", "Permission mode"), permissionLabel(state.permissionMode)) {
                val modes = state.boot?.permissionModes
                    ?: listOf("auto", "default", "acceptEdits", "plan", "dontAsk")
                SheetOptionList(
                    options = modes.map { mode ->
                        SheetOption(mode, permissionLabel(mode), permissionHint(mode))
                    },
                    selectedKey = state.permissionMode,
                    onSelect = { choose(it) },
                )
            }
        }
    }
}

package com.clauderemote.app.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clauderemote.app.data.AskQuestion
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.cdsColors

/**
 * AskUserQuestion 的选项卡片。
 * 服务端按 `question.question` 作为答案的 key，多问题要一次性提交。
 */
@Composable
fun QuestionCard(
    questions: List<AskQuestion>,
    onAnswer: (Map<String, String>) -> Unit,
) {
    val c = cdsColors
    val picked = remember(questions) { mutableStateMapOf<String, String>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface2, RoundedCornerShape(CdsDimens.radiusPanel))
            .border(CdsDimens.emphasis, c.accent, RoundedCornerShape(CdsDimens.radiusPanel))
            .padding(CdsDimens.lg),
        verticalArrangement = Arrangement.spacedBy(CdsDimens.md),
    ) {
        questions.forEach { q ->
            if (q.header.isNotBlank() && q.header != q.question) {
                Text(
                    q.header,
                    color = c.accentText,
                    fontSize = CdsDimens.captionSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CdsFontFamilies.sans,
                )
            }
            Text(
                q.question,
                color = c.textPrimary,
                fontSize = CdsDimens.bodySize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CdsFontFamilies.sans,
            )
            if (q.description.isNotBlank()) {
                Text(
                    q.description,
                    color = c.textSecondary,
                    fontSize = CdsDimens.footnoteSize,
                    fontFamily = CdsFontFamilies.sans,
                )
            }

            q.options.forEach { opt ->
                val chosen = picked[q.question] == opt.label
                // 可点选项的名字：选项名 + 说明
                val optionLabel = if (opt.description.isBlank()) {
                    opt.label
                } else {
                    opt.label + ", " + opt.description
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CdsDimens.radius))
                        // 选中态用 surface3（暖灰实底）+ accent 描边，和模型面板里
                        // 「选中行」的表达保持一致。原先选中用 userBubble、未选中用
                        // surface3，而 P2 把 surface3 从纯白改成了 #EDECE5，两者关系
                        // 正好翻了过来——未选中的卡片反而比选中的更深。
                        .background(
                            if (chosen) c.surface3 else c.surface2,
                            RoundedCornerShape(CdsDimens.radius),
                        )
                        .border(
                            CdsDimens.hairline,
                            if (chosen) c.accent else c.border,
                            RoundedCornerShape(CdsDimens.radius),
                        )
                        .clickable { picked[q.question] = opt.label }
                        // 选项名和说明都在子节点上，直接给可点区域一个名字
                        .clearAndSetSemantics { contentDescription = optionLabel }
                        .padding(horizontal = CdsDimens.lg, vertical = CdsDimens.md),
                ) {
                    Text(
                        opt.label,
                        color = c.textPrimary,
                        fontSize = CdsDimens.bodySize,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = CdsFontFamilies.sans,
                    )
                    if (opt.description.isNotBlank()) {
                        Text(
                            opt.description,
                            color = c.textSecondary,
                            fontSize = CdsDimens.captionSize,
                            fontFamily = CdsFontFamilies.sans,
                        )
                    }
                }
            }
        }

        val ready = questions.isNotEmpty() && questions.all { picked.containsKey(it.question) }
        Button(
            onClick = { onAnswer(picked.toMap()) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CdsDimens.radius),
            colors = ButtonDefaults.buttonColors(containerColor = c.fillPrimary, contentColor = c.onPrimary),
        ) {
            Text(tr("提交", "Submit"), fontFamily = CdsFontFamilies.sans, fontSize = CdsDimens.bodySize)
        }
    }
}

/** 工具执行前的权限确认，带上真实命令／文件路径，别让人盲点「允许」 */
@Composable
fun PermissionCard(
    toolName: String,
    reason: String?,
    input: org.json.JSONObject? = null,
    onDecide: (Boolean) -> Unit,
) {
    val c = cdsColors
    val summary = ToolText.summaryOf(toolName, input)
    val detail = ToolText.permissionDetail(toolName, input)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface2, RoundedCornerShape(CdsDimens.radiusPanel))
            .border(CdsDimens.emphasis, c.accent, RoundedCornerShape(CdsDimens.radiusPanel))
            .padding(CdsDimens.lg),
        verticalArrangement = Arrangement.spacedBy(CdsDimens.md),
    ) {
        Text(
            tr("需要确认：", "Permission needed: ") + ToolText.label(toolName),
            color = c.textPrimary,
            fontSize = CdsDimens.bodySize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CdsFontFamilies.sans,
        )
        if (summary.isNotBlank() && summary != detail) {
            Text(
                summary,
                color = c.textSecondary,
                fontSize = CdsDimens.footnoteSize,
                fontFamily = CdsFontFamilies.sans,
                maxLines = 2,
            )
        }
        if (!detail.isNullOrBlank()) {
            Text(
                detail,
                color = c.textPrimary,
                fontSize = CdsDimens.codeSize,
                lineHeight = CdsDimens.lineCode,
                fontFamily = CdsFontFamilies.mono,
                maxLines = 10,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.codeBg, RoundedCornerShape(CdsDimens.radiusCode))
                    .padding(CdsDimens.md),
            )
        }
        if (!reason.isNullOrBlank()) {
            Text(
                reason,
                color = c.textSecondary,
                fontSize = CdsDimens.footnoteSize,
                fontFamily = CdsFontFamilies.sans,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CdsDimens.md)) {
            Button(
                onClick = { onDecide(true) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(CdsDimens.radius),
                colors = ButtonDefaults.buttonColors(containerColor = c.fillPrimary, contentColor = c.onPrimary),
            ) { Text(tr("允许", "Allow"), fontFamily = CdsFontFamilies.sans, fontSize = CdsDimens.bodySize) }

            Button(
                onClick = { onDecide(false) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(CdsDimens.radius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.surface3,
                    contentColor = c.textPrimary,
                ),
            ) { Text(tr("拒绝", "Deny"), fontFamily = CdsFontFamilies.sans, fontSize = CdsDimens.bodySize) }
        }
    }
}

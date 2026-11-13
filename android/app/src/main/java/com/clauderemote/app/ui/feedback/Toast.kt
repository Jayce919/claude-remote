package com.clauderemote.app.ui.feedback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.CdsMotion
import com.clauderemote.app.ui.theme.cdsColors
import com.clauderemote.app.ui.theme.reducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 轻提示类型，决定左侧圆点颜色和展示时长 */
enum class ToastKind { Info, Success, Warn, Error }

internal data class ToastData(
    val id: Long,
    val message: String,
    val kind: ToastKind,
    val durationMs: Long,
)

/**
 * 全局轻提示状态。用法：
 * ```
 * val toastState = rememberToastState()
 * toastState.show("已连接", ToastKind.Success)
 * ```
 *
 * 参数对齐 Arc 的 toast-stack：最多 12 条、默认 5000ms、warn/error 延长 1.6 倍。
 */
@Stable
internal class ToastState {
    private var nextId = 0L
    private val _toasts = mutableStateListOf<ToastData>()
    val toasts: List<ToastData> get() = _toasts

    fun show(message: String, kind: ToastKind = ToastKind.Info) {
        val data = ToastData(
            id = nextId++,
            message = message,
            kind = kind,
            durationMs = if (kind == ToastKind.Warn || kind == ToastKind.Error) {
                (BASE * 1.6f).toLong()
            } else {
                BASE
            },
        )
        _toasts.add(data)
        while (_toasts.size > MAX) _toasts.removeAt(0)
    }

    fun dismiss(id: Long) {
        _toasts.removeAll { it.id == id }
    }

    companion object {
        private const val BASE = 5000L
        const val MAX = 12
    }
}

@Composable
internal fun rememberToastState(): ToastState = remember { ToastState() }

/**
 * 底部居中的 toast 堆叠。最新一条完整显示，更早的只露出 14dp 一条边；
 * 点一下展开成完整列表，再点收起；单条时点一下直接关闭；横向拖出即关闭。
 */
@Composable
internal fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val peekPx = with(density) { PEEK.toPx() }
    val gutterPx = with(density) { GUTTER.toPx() }
    var fanOut by remember { mutableStateOf(false) }
    val heights = remember { mutableStateMapOf<Long, Float>() }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        state.toasts.forEachIndexed { index, toast ->
            key(toast.id) {
                val depth = state.toasts.size - 1 - index // 0 = 最新
                val newerTotal = if (fanOut) {
                    state.toasts.subList(index + 1, state.toasts.size)
                        .sumOf { (heights[it.id] ?: 0f).toDouble() + gutterPx }
                        .toFloat()
                } else {
                    depth * peekPx
                }
                ToastItem(
                    toast = toast,
                    depth = depth,
                    fanOut = fanOut,
                    offsetY = -newerTotal,
                    onHeight = { h -> heights[toast.id] = h },
                    onDismiss = { state.dismiss(toast.id) },
                    onTap = {
                        if (state.toasts.size <= 1) state.dismiss(toast.id) else fanOut = !fanOut
                    },
                )
            }
        }
    }
}

@Composable
private fun ToastItem(
    toast: ToastData,
    depth: Int,
    fanOut: Boolean,
    offsetY: Float,
    onHeight: (Float) -> Unit,
    onDismiss: () -> Unit,
    onTap: () -> Unit,
) {
    val cds = cdsColors
    val noMotion = reducedMotion
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var cardWidth by remember { mutableFloatStateOf(1f) }

    val enterY = remember { Animatable(with(density) { 48.dp.toPx() }) }
    val fade = remember { Animatable(0f) }
    val swipeX = remember { Animatable(0f) }

    // 堆叠时更早的卡片轻微缩小，展开后恢复原尺寸；从底部中心缩放保证露出的边不位移
    val scaleTarget = if (fanOut) 1f else (1f - 0.05f * depth).coerceAtLeast(0.85f)
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = tween(if (noMotion) 0 else CdsMotion.DurBase, easing = CdsMotion.EaseOut),
        label = "toastScale",
    )
    val stackOffset by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = tween(if (noMotion) 0 else CdsMotion.DurBase, easing = CdsMotion.EaseOut),
        label = "toastOffset",
    )

    fun exit() {
        scope.launch {
            val d = if (noMotion) 0 else CdsMotion.DurBase
            coroutineScope {
                launch { swipeX.animateTo(cardWidth, tween(d, easing = CdsMotion.EaseSnap)) }
                launch { fade.animateTo(0f, tween(d)) }
            }
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        launch { enterY.animateTo(0f, tween(if (noMotion) 0 else CdsMotion.DurBase, easing = CdsMotion.EaseOut)) }
        launch { fade.animateTo(1f, tween(if (noMotion) 0 else CdsMotion.DurSnap)) }
    }

    // 自动消失；展开状态下暂停计时，收起后重新计时
    LaunchedEffect(toast.id, fanOut) {
        if (!fanOut) {
            delay(toast.durationMs)
            exit()
        }
    }

    val tint = when (toast.kind) {
        ToastKind.Success -> cds.success
        ToastKind.Warn -> cds.warning
        ToastKind.Error -> cds.danger
        ToastKind.Info -> cds.accent
    }

    val dragState = rememberDraggableState { delta ->
        scope.launch { swipeX.snapTo((swipeX.value + delta).coerceAtLeast(0f)) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CdsDimens.lg)
            .graphicsLayer {
                translationY = stackOffset + enterY.value
                translationX = swipeX.value
                alpha = fade.value * (1f - (swipeX.value / cardWidth).coerceIn(0f, 1f) * 0.4f)
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onHeight(it.height.toFloat()); cardWidth = it.width.toFloat() }
                .clip(RoundedCornerShape(CdsDimens.radiusComposer))
                .background(cds.surface2)
                .border(CdsDimens.hairline, cds.borderStrong, RoundedCornerShape(CdsDimens.radiusComposer))
                .clickable(onClick = onTap)
                // 轻提示是自动出现的播报，读屏应当主动念出来：
                // 错误用 Assertive（打断当前朗读），其余用 Polite（等当前读完后播报）。
                // 整条提示是可点关闭的，文字在子节点里，这里直接把文字作为容器的名字。
                .clearAndSetSemantics {
                    contentDescription = toast.message
                    liveRegion = if (toast.kind == ToastKind.Error) {
                        LiveRegionMode.Assertive
                    } else {
                        LiveRegionMode.Polite
                    }
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        if (swipeX.value > cardWidth * 0.35f || velocity > 1200f) {
                            exit()
                        } else {
                            scope.launch {
                                swipeX.animateTo(0f, tween(if (noMotion) 0 else CdsMotion.DurSnap, easing = CdsMotion.EaseSnap))
                            }
                        }
                    },
                )
                .padding(horizontal = CdsDimens.xl, vertical = CdsDimens.lg2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(CdsDimens.md))
            androidx.compose.material3.Text(
                text = toast.message,
                color = cds.textPrimary,
                fontSize = CdsDimens.bodySmallSize,
                lineHeight = CdsDimens.lineBodySmall,
                fontFamily = CdsFontFamilies.sans,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private val PEEK = 14.dp
private val GUTTER = 8.dp

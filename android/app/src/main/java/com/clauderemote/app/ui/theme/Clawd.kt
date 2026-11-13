package com.clauderemote.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 官方像素小虫 Clawd。
 *
 * 造型直接取自 Claude Desktop 内置的 `/images/clawd.svg`：
 * 一个 66×52 的 viewBox，由 11 列 × 4 行、每格 6×13 的方块拼成，
 * 身体 #D97757，眼睛 #141413。这里不重新描线，就按原始方块铺点阵，
 * 所以放大后依然是清晰的马赛克边，而不是被矢量平滑。
 *
 * 动画是六个 8bit 待机动作的轮播：原地碎步、慢呼吸、东张西望、
 * 开心跳跃、小碎步跑、经典四帧。全部按 10fps 逐帧切换，每段都从
 * 静止姿势开始并回到静止姿势，接缝处不会出现位置或眨眼状态的跳变。
 */
private const val CELL_W = 6
private const val CELL_H = 13
private const val BASE_W = 66
private const val BASE_H = 52

/** 10fps：8bit 待机的节奏，不跟屏幕刷新率走 */
private const val TICK_NANOS = 100_000_000L

private val BodyRows = listOf(1 to 9, 0 to 10, 1 to 9)
private val LegCols = intArrayOf(0, 2, 6, 10)
private val EyeCols = intArrayOf(2, 8)
private val ZERO_LEGS = intArrayOf(0, 0, 0, 0)

private val BodyColor = Color(0xFFD97757)
private val EyeColor = Color(0xFF141413)

/** 一帧的姿势，长度单位都是原始精灵的像素格 */
private class ClawdPose(
    val bodyY: Int = 0,
    val headDx: Int = 0,
    val eyeDx: Int = 0,
    val legDx: IntArray = ZERO_LEGS,
    val legLift: IntArray = ZERO_LEGS,
    val legH: Int = CELL_H,
    val eyeOpen: Float = 1f,
    val dust: Boolean = false,
)

private fun neutral(count: Int): List<ClawdPose> = List(count) { ClawdPose() }

/** 原地碎步：身体轻浮，四腿交替迈半步，末尾眨一次眼 */
private fun shuffleFrames(): List<ClawdPose> {
    val a = ClawdPose(legDx = intArrayOf(1, -1, 1, -1), legLift = intArrayOf(0, 2, 0, 2))
    val b = ClawdPose(bodyY = -1, legDx = intArrayOf(-1, 1, -1, 1), legLift = intArrayOf(2, 0, 2, 0))
    return buildList {
        addAll(neutral(2))
        repeat(2) { add(a) }
        repeat(2) { add(b) }
        repeat(2) { add(a) }
        repeat(2) { add(b) }
        add(ClawdPose(eyeOpen = 0.45f))
        add(ClawdPose(eyeOpen = 0f))
        add(ClawdPose(eyeOpen = 0.45f))
        addAll(neutral(3))
    }
}

/** 慢呼吸：腿不动，身体下沉 1px，中间慢慢闭眼再睁开 */
private fun breathFrames(): List<ClawdPose> = buildList {
    addAll(neutral(3))
    repeat(6) { add(ClawdPose()) }
    repeat(4) { add(ClawdPose(bodyY = 1)) }
    add(ClawdPose(bodyY = 1, eyeOpen = 0.5f))
    add(ClawdPose(bodyY = 1, eyeOpen = 0f))
    add(ClawdPose(bodyY = 1, eyeOpen = 0f))
    add(ClawdPose(bodyY = 1, eyeOpen = 0.5f))
    repeat(5) { add(ClawdPose()) }
    addAll(neutral(3))
}

/** 东张西望：头两行和眼睛一起左右偏 1px，最后眨一次眼 */
private fun lookFrames(): List<ClawdPose> = buildList {
    addAll(neutral(2))
    repeat(6) { add(ClawdPose(headDx = -1, eyeDx = -1)) }
    addAll(neutral(3))
    repeat(6) { add(ClawdPose(headDx = 1, eyeDx = 1)) }
    addAll(neutral(3))
    add(ClawdPose(eyeOpen = 0.5f))
    add(ClawdPose(eyeOpen = 0f))
    add(ClawdPose(eyeOpen = 0f))
    add(ClawdPose(eyeOpen = 0.5f))
    addAll(neutral(3))
}

/** 开心跳跃：下蹲、腾空、落地扬尘，再回到站立 */
private fun hopFrames(): List<ClawdPose> = buildList {
    addAll(neutral(3))
    add(ClawdPose(bodyY = 2, legH = 11, eyeOpen = 0.6f))
    add(ClawdPose(bodyY = -5, legH = 6))
    repeat(2) { add(ClawdPose(bodyY = -8, legH = 5)) }
    add(ClawdPose(bodyY = -4, legH = 6))
    add(ClawdPose(bodyY = 1, legH = 12, eyeOpen = 0.7f, dust = true))
    add(ClawdPose(dust = true))
    addAll(neutral(8))
}

/** 小碎步跑：快速交替迈腿，末尾眨眼，起步和收尾各有一帧半步过渡 */
private fun scurryFrames(): List<ClawdPose> = buildList {
    val halfA = ClawdPose(legDx = intArrayOf(1, -1, 1, -1), legLift = intArrayOf(0, 2, 0, 2))
    val a = ClawdPose(legDx = intArrayOf(2, -2, 2, -2), legLift = intArrayOf(0, 4, 0, 4))
    val b = ClawdPose(bodyY = -1, legDx = intArrayOf(-2, 2, -2, 2), legLift = intArrayOf(4, 0, 4, 0))
    val halfB = ClawdPose(legDx = intArrayOf(-1, 1, -1, 1), legLift = intArrayOf(2, 0, 2, 0))
    addAll(neutral(2))
    add(halfA)
    repeat(4) {
        add(a)
        add(b)
    }
    add(halfB)
    add(ClawdPose(eyeOpen = 0.45f))
    add(ClawdPose(eyeOpen = 0f))
    add(ClawdPose(eyeOpen = 0.45f))
    addAll(neutral(3))
}

/** 经典四帧：四张定格姿势硬切，最接近 NES 精灵表 */
private fun fourFrameFrames(): List<ClawdPose> = buildList {
    val a = ClawdPose(legDx = intArrayOf(-1, 1, -1, 1))
    val b = ClawdPose(bodyY = -1, legDx = intArrayOf(1, -1, 1, -1))
    val c = ClawdPose(legDx = intArrayOf(1, -1, 1, -1), eyeOpen = 0.5f)
    val d = ClawdPose(bodyY = -1, legDx = intArrayOf(-1, 1, -1, 1))
    addAll(neutral(2))
    for (pose in listOf(a, b, c, d, a, b, c, d)) {
        repeat(2) { add(pose) }
    }
    addAll(neutral(2))
}

/** 六个动作串成一个循环；首尾都是静止姿势，轮播接缝无跳变 */
private val ClawdCycle: List<ClawdPose> = buildList {
    addAll(shuffleFrames())
    addAll(breathFrames())
    addAll(lookFrames())
    addAll(hopFrames())
    addAll(scurryFrames())
    addAll(fourFrameFrames())
}

/**
 * @param width 小虫的宽度；高度按官方比例 52/66 自动得出。
 * @param animated 是否播放待机动画。静态场景（截图、列表缩略）可以关掉。
 */
@Composable
fun Clawd(
    width: Dp = 32.dp,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    bodyColor: Color = BodyColor,
    eyeColor: Color = EyeColor,
) {
    val tick = remember { mutableIntStateOf(0) }
    // 每次重新进入组合（抽屉打开）都随机落到循环里的某一帧，而不是总从第一个动作
    // 「原地碎步」开始。偏移只影响起点，之后的轮播顺序不变。
    val phase = remember { Random.nextInt(ClawdCycle.size) }
    LaunchedEffect(animated) {
        if (!animated) {
            tick.intValue = 0
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        var last = -1
        while (true) {
            withFrameNanos { now ->
                val next = ((now - start) / TICK_NANOS).toInt()
                if (next != last) {
                    last = next
                    tick.intValue = next
                }
            }
        }
    }

    val pose = if (animated) ClawdCycle[(tick.intValue + phase) % ClawdCycle.size] else ClawdPose()

    Canvas(
        modifier = modifier.size(
            width = width,
            height = width * (BASE_H.toFloat() / BASE_W),
        ),
    ) {
        val unit = size.width / BASE_W

        // 32dp 的宽度除以 66 格，每格是 1.39 个设备像素——直接按小数坐标画，
        // 相邻方块的公共边会被抗锯齿各削一次，身体上就会出现一圈圈浅色网格缝。
        // 把左右上下边分别对齐到整像素，公共边取整后完全重合，马赛克才是整块实色。
        fun cell(x: Int, y: Int, w: Int, h: Int, color: Color) {
            val left = (x * unit).roundToInt()
            val top = (y * unit).roundToInt()
            val right = ((x + w) * unit).roundToInt()
            val bottom = ((y + h) * unit).roundToInt()
            drawRect(
                color = color,
                topLeft = Offset(left.toFloat(), top.toFloat()),
                size = Size((right - left).toFloat(), (bottom - top).toFloat()),
            )
        }

        // 身体三行；前两行当作脑袋，可以整体左右偏 1px
        BodyRows.forEachIndexed { row, (from, to) ->
            val shift = if (row < 2) pose.headDx else 0
            for (col in from..to) {
                cell(col * CELL_W + shift, row * CELL_H + pose.bodyY, CELL_W, CELL_H, bodyColor)
            }
        }

        // 四条腿：legDx 左右迈步，legLift 从地面方向抬脚
        for (i in LegCols.indices) {
            val height = max(2, pose.legH - pose.legLift[i])
            cell(
                LegCols[i] * CELL_W + pose.legDx[i],
                3 * CELL_H + pose.bodyY,
                CELL_W,
                height,
                bodyColor,
            )
        }

        // 眼睛坐在第二行，眨眼时从中间向上下收缩
        val eyeH = (6f * pose.eyeOpen).roundToInt()
        if (eyeH > 0) {
            val top = CELL_H + pose.bodyY + 3 + (6 - eyeH) / 2
            for (col in EyeCols) {
                cell(
                    col * CELL_W + pose.headDx + pose.eyeDx,
                    top,
                    CELL_W,
                    eyeH,
                    eyeColor,
                )
            }
        }

        // 落地扬尘，只出现在跳跃落地的两帧
        if (pose.dust) {
            val dust = bodyColor.copy(alpha = 0.65f)
            cell(3, 49, 3, 1, dust)
            cell(60, 49, 3, 1, dust)
        }
    }
}

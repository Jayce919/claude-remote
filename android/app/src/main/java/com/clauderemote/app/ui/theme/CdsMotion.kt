package com.clauderemote.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

/**
 * 动效曲线。两条曲线都是从官方包的 res/interpolator 里读出来的真实控制点，
 * 不是按 Material 默认值凑的。
 *
 * cds_ease_out   -> cubic-bezier(0.165, 0.84, 0.44, 1)
 * fast_out_slow_in -> cubic-bezier(0.4, 0, 0.2, 1)
 */
object CdsMotion {
    val EaseOut: Easing = CubicBezierEasing(0.165f, 0.84f, 0.44f, 1f)
    val EaseSnap: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    val FastOutSlowIn: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    // 低于 150ms 的过渡在 460dpi 屏幕上会被感知成「一闪」：帧数太少，
    // 眼睛还没跟上就已经结束，比从容一点的过渡更突兀。120ms 是网页端的取值，
    // 手机上按这块屏的刷新率抬到 150ms。
    const val DurSnap = 150
    const val DurBase = 200
    const val DurSheet = 300

    /**
     * 系统「移除动画」开启时把时长压到 0：所有走这个入口的动效原地完成，
     * 只保留状态变化本身。开关、展开收起、颜色过渡等都应从这里取时长。
     */
    @Composable
    fun duration(base: Int): Int = if (LocalReducedMotion.current) 0 else base

    /** 内容块展开收起 */
    @Composable
    fun expandSize() = tween<androidx.compose.ui.unit.IntSize>(durationMillis = duration(DurBase), easing = EaseOut)

    /** 颜色过渡 */
    @Composable
    fun color() = tween<androidx.compose.ui.graphics.Color>(durationMillis = duration(DurSnap), easing = EaseSnap)
}

package com.clauderemote.app.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 尺寸阶梯。取自官方包的 dimen 资源（126 项）里实际出现的值：
 * 4 / 6 / 8 / 12 / 16 / 20 / 24 / 32 / 48 dp，触控高度 56dp。
 *
 * 字号按 6.31 英寸、2640×1216（19.54:9，约 422dp 宽、460dpi）这块屏校过：
 * 官方手机端正文字号比网页端大一档，14sp 在 460dpi 上偏小，正文取 16sp。
 * 用户还能在设置里整体缩放字号（0.85–1.45 倍），所以这里给的是基准值。
 */
object CdsDimens {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 6.dp
    val md = 8.dp
    val md2 = 10.dp
    val lg = 12.dp
    val lg2 = 14.dp
    val xl = 16.dp
    val xl2 = 18.dp
    val xxl = 20.dp
    val xxl2 = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp

    /** 输入区与主要按钮的触控高度 */
    val touchTarget = 52.dp

    /** 图标按钮 */
    val iconButton = 40.dp
    val iconButtonSmall = 32.dp
    val iconGlyph = 22.dp
    val iconGlyphSmall = 18.dp

    /** 顶栏两侧动作按钮：DeepSeek 用 40×44dp 的高触控区 */
    val topbarActionWidth = 40.dp
    val topbarActionHeight = 44.dp

    /** 边框：卡片和输入框用 1dp，选中态强调线用 1.5dp */
    val hairline = 1.dp
    val emphasis = 1.5.dp

    /*
     * 圆角只留四档：6（基础控件）/ 8（小容器）/ 12（面板）/ 16（输入区、面板顶部）。
     * radiusXs、radiusSm 原来各写 5dp，和 radius 的 6dp 差 1dp——这种差值肉眼分辨
     * 不出来，调用点却因此分成了三套写法。保留名字、值统一到 6dp，调用点不用动。
     */
    val radiusXs = 6.dp
    val radiusSm = 6.dp
    val radius = 6.dp
    val radiusLg = 8.dp
    val radiusPanel = 12.dp
    val radiusComposer = 16.dp

    /** 代码块圆角，来自 bg_ccr_code_block.xml 的 corners radius */
    val radiusCode = 6.dp

    /** 消息之间的间距 */
    val messageGap = 18.dp

    /*
     * 字号阶梯收敛成 5 档：12 / 14 / 16 / 18 / 22。
     * 原来是 12/13/15/16/17/22 六档，相邻只差 1sp，抽屉展开一屏能同时出现六七种字号，
     * 层级读不出来、看着像随手填的值。13 和 15 并进 14，17 升到 18，档与档之间
     * 至少差 2sp，缩进和字重之外多一个可依赖的层级信号。
     */
    val bodySize = 16.sp
    val bodySmallSize = 14.sp
    val captionSize = 12.sp
    val footnoteSize = 14.sp
    val codeSize = 14.sp
    val titleSize = 18.sp
    val headlineSize = 22.sp

    // 行高跟着字号走：14sp 的两档（正文小字、脚注、代码）统一 1.5 倍 = 21sp，
    // 正文 16sp 保持 25sp，标题 18sp 配 26sp，保证换行后的密实度一致。
    val lineBody = 25.sp
    val lineBodySmall = 21.sp
    val lineCaption = 17.sp
    val lineFootnote = 21.sp
    val lineCode = 21.sp
    val lineTitle = 26.sp
}

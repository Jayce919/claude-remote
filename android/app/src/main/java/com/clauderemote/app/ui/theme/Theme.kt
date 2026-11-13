package com.clauderemote.app.ui.theme

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.clauderemote.app.R

/** 官方包里的 anthropic_sans 是可变字体，580 这个字重是真实存在的轴取值 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val AnthropicSans = FontFamily(
    Font(R.font.anthropic_sans, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.anthropic_sans, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.anthropic_sans, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(580))),
    Font(R.font.anthropic_sans, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.anthropic_sans_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
)


@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val AnthropicSerif = FontFamily(
    Font(R.font.anthropic_serif, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.anthropic_serif, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.anthropic_serif, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(580))),
    Font(R.font.anthropic_serif_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
)

private val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, weight = FontWeight.Normal),
    Font(R.font.jetbrains_mono, weight = FontWeight.Bold),
)

object CdsFontFamilies {
    val sans: FontFamily = AnthropicSans
    val serif: FontFamily = AnthropicSerif
    val mono: FontFamily = JetBrainsMono
}

/** 系统「移除动画」开关；true 表示用户希望尽量少动效（无障碍） */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** 取当前 reduced-motion 状态的快捷方式 */
val reducedMotion: Boolean
    @Composable get() = LocalReducedMotion.current

/**
 * 读取「是否减弱动效」：系统的动画器开关与全局时长缩放任一为关就算减弱。
 * 两个来源都包在 runCatching 里——有的 ROM 会移除对应的系统设置项，
 * 读不到时按「正常动效」处理，不能因此让首屏崩掉。
 */
private fun readReducedMotion(context: Context): Boolean {
    val animatorsOff = runCatching { !ValueAnimator.areAnimatorsEnabled() }.getOrDefault(false)
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return animatorsOff || scale == 0f
}

/**
 * 监听系统的「移除动画」开关。
 *
 * 原来用 remember 只读一次，用户在系统设置里打开移除动画后必须杀进程重开才生效。
 * 这里注册 ContentObserver，设置一变就更新状态，LocalReducedMotion 随之切换，
 * 已经组合好的界面（以及无限动画）会在下一帧按新值重算。
 */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember(context) { mutableStateOf(readReducedMotion(context)) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = readReducedMotion(context)
            }
        }
        // 注册失败（极少数 ROM 限制访问 Settings.Global）时退化为静态值，不影响使用
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }
    return reduce
}

private fun cdsTypography(): Typography = Typography(
    bodyLarge = TextStyle(fontFamily = AnthropicSans, fontSize = CdsDimens.bodySize, lineHeight = CdsDimens.lineBody),
    bodyMedium = TextStyle(fontFamily = AnthropicSans, fontSize = CdsDimens.bodySmallSize, lineHeight = CdsDimens.lineBodySmall),
    bodySmall = TextStyle(fontFamily = AnthropicSans, fontSize = CdsDimens.footnoteSize, lineHeight = CdsDimens.lineFootnote),
    labelSmall = TextStyle(fontFamily = AnthropicSans, fontSize = CdsDimens.captionSize, lineHeight = CdsDimens.lineCaption),
    titleLarge = TextStyle(fontFamily = AnthropicSans, fontSize = CdsDimens.headlineSize, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = AnthropicSans, fontSize = CdsDimens.titleSize, fontWeight = FontWeight.SemiBold, lineHeight = CdsDimens.lineTitle),
)

@Composable
fun ClaudeRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val cds = if (darkTheme) DarkCdsColors else LightCdsColors

    // 读系统「移除动画」全局缩放，0 表示用户关掉了动画；开关变化会实时反映
    val reduceMotion = rememberReducedMotion()

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = cds.fillPrimary,
            onPrimary = cds.onPrimary,
            background = cds.pageBg,
            onBackground = cds.textPrimary,
            surface = cds.surface2,
            onSurface = cds.textPrimary,
            surfaceVariant = cds.surface3,
            onSurfaceVariant = cds.textSecondary,
            outline = cds.border,
            error = cds.danger,
        )
    } else {
        lightColorScheme(
            primary = cds.fillPrimary,
            onPrimary = cds.onPrimary,
            background = cds.pageBg,
            onBackground = cds.textPrimary,
            surface = cds.surface2,
            onSurface = cds.textPrimary,
            surfaceVariant = cds.surface3,
            onSurfaceVariant = cds.textSecondary,
            outline = cds.border,
            error = cds.danger,
        )
    }

    // 字号缩放走 Density.fontScale：所有 sp 一起缩放，行高和图标按钮跟着走，
    // 不用把每个 Text 的字号都乘一遍。
    val base = LocalDensity.current
    val scaled = Density(base.density, base.fontScale * fontScale.coerceIn(0.85f, 1.45f))

    CompositionLocalProvider(LocalCdsColors provides cds, LocalReducedMotion provides reduceMotion, LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = scheme,
            typography = cdsTypography(),
            content = content,
        )
    }
}

/** 取当前配色的快捷方式 */
val cdsColors: CdsColors
    @Composable get() = LocalCdsColors.current

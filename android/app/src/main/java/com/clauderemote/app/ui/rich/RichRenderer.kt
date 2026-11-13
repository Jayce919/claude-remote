package com.clauderemote.app.ui.rich

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.theme.CdsDimens
import com.clauderemote.app.ui.theme.CdsFontFamilies
import com.clauderemote.app.ui.theme.CdsIcons
import com.clauderemote.app.ui.theme.cdsColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RichBlocks(blocks: List<RichBlock>, modifier: Modifier = Modifier) {
    val c = cdsColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CdsDimens.lg)) {
        blocks.forEach { block ->
            when (block) {
                is RichBlock.Paragraph -> if (block.spans.hasMath()) {
                    // 段落里有行内公式时整段交给 WebView，KaTeX 才能把公式
                    // 按基线嵌进文字里；纯文本段落仍然走原生渲染
                    MathParagraph(block.spans)
                } else SelectionContainer {
                    Text(
                        text = Markdown.toAnnotated(block.spans, c.textPrimary, c.accentText, c.accentText),
                        fontFamily = CdsFontFamilies.sans,
                        fontSize = CdsDimens.bodySize,
                        lineHeight = CdsDimens.lineBody,
                    )
                }
                is RichBlock.Heading -> Text(
                    text = Markdown.toAnnotated(block.spans, c.textPrimary, c.accentText, c.accentText),
                    fontFamily = CdsFontFamilies.sans,
                    fontSize = when (block.level) {
                        1 -> CdsDimens.titleSize
                        2 -> 18.sp()
                        3 -> 16.sp()
                        else -> CdsDimens.bodySize
                    },
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = CdsDimens.lineBody,
                )
                is RichBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(CdsDimens.xs)) {
                    block.items.forEachIndexed { index, item ->
                        Row {
                            Text(
                                if (block.ordered) "${block.start + index}." else "•",
                                color = c.textSecondary,
                                fontFamily = CdsFontFamilies.sans,
                                fontSize = CdsDimens.bodySize,
                                modifier = Modifier.width(if (block.ordered) 24.dp else 16.dp),
                            )
                            if (item.hasMath()) {
                                MathParagraph(item, Modifier.weight(1f))
                            } else SelectionContainer {
                                Text(
                                    text = Markdown.toAnnotated(item, c.textPrimary, c.accentText, c.accentText),
                                    fontFamily = CdsFontFamilies.sans,
                                    fontSize = CdsDimens.bodySize,
                                    lineHeight = CdsDimens.lineBody,
                                )
                            }
                        }
                    }
                }
                is RichBlock.Code -> CodeBlock(block.lang, block.text)
                is RichBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(c.border),
                    ) {}
                    if (block.spans.hasMath()) {
                        MathParagraph(
                            block.spans,
                            Modifier
                                .weight(1f)
                                .padding(start = CdsDimens.lg),
                        )
                    } else SelectionContainer {
                        Text(
                            text = Markdown.toAnnotated(block.spans, c.textSecondary, c.accentText, c.accentText),
                            fontFamily = CdsFontFamilies.sans,
                            fontSize = CdsDimens.bodySize,
                            lineHeight = CdsDimens.lineBody,
                            modifier = Modifier.padding(start = CdsDimens.lg),
                        )
                    }
                }
                is RichBlock.Table -> TableView(block)
                is RichBlock.Math -> MathBlock(block.latex)
                RichBlock.Rule -> Column(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.border),
                ) {}
            }
        }
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

@Composable
private fun CodeBlock(lang: String, code: String) {
    val c = cdsColors
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember(code) { mutableStateOf(false) }
    // 语义回调不是 @Composable，先在组合期把文案取出来
    val copyLabel = if (copied) tr("已复制", "Copied") else tr("复制", "Copy")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.codeBg, RoundedCornerShape(CdsDimens.radiusCode))
            .border(1.dp, c.border, RoundedCornerShape(CdsDimens.radiusCode))
            .padding(CdsDimens.lg),
    ) {
        // 头条：语言名 + 复制，和网页端、官方客户端的结构一致
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                lang.ifBlank { "text" },
                color = c.textMuted,
                fontSize = CdsDimens.captionSize,
                fontFamily = CdsFontFamilies.sans,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(CdsDimens.radiusXs))
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                        scope.launch {
                            delay(1200)
                            copied = false
                        }
                    }
                    // 名字在子节点（图标 + 「已复制」）上，直接给可点区域一个名字
                    .clearAndSetSemantics { contentDescription = copyLabel }
                    .padding(horizontal = CdsDimens.xs, vertical = CdsDimens.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (copied) {
                    Text(
                        tr("已复制", "Copied"),
                        color = c.success,
                        fontSize = CdsDimens.captionSize,
                        fontFamily = CdsFontFamilies.sans,
                    )
                }
                Icon(
                    CdsIcons.ICopy,
                    contentDescription = tr("复制", "Copy"),
                    tint = if (copied) c.success else c.textSecondary,
                    modifier = Modifier
                        .padding(start = if (copied) CdsDimens.sm else 0.dp)
                        .size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(CdsDimens.sm))
        SelectionContainer {
            Text(
                text = code,
                color = c.textPrimary,
                fontFamily = CdsFontFamilies.mono,
                fontSize = CdsDimens.codeSize,
                lineHeight = CdsDimens.lineCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun TableView(block: RichBlock.Table) {
    val c = cdsColors
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                tr("放大", "Expand"),
                color = c.textSecondary,
                fontSize = CdsDimens.captionSize,
                fontFamily = CdsFontFamilies.sans,
                modifier = Modifier
                    .clip(RoundedCornerShape(CdsDimens.radiusXs))
                    .border(0.5.dp, c.border, RoundedCornerShape(CdsDimens.radiusXs))
                    .clickable { expanded = true }
                    .padding(horizontal = CdsDimens.md, vertical = CdsDimens.xs),
            )
        }
        Spacer(Modifier.height(CdsDimens.xs))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Row {
                block.header.forEach { h ->
                    Cell(h, header = true)
                }
            }
            block.rows.forEach { row ->
                Row(verticalAlignment = Alignment.Top) {
                    row.forEach { cell -> Cell(cell, header = false) }
                }
            }
        }
    }
    if (expanded) {
        TablePreviewDialog(block, onClose = { expanded = false })
    }
}

/** 表格全屏预览：竖屏下宽表只能横滚，给一个能同时上下左右看的浮层 */
@Composable
private fun TablePreviewDialog(block: RichBlock.Table, onClose: () -> Unit) {
    val c = cdsColors
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(CdsDimens.lg),
            color = c.surface2,
            shape = RoundedCornerShape(CdsDimens.radiusPanel),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(CdsDimens.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        tr("表格", "Table"),
                        color = c.textSecondary,
                        fontSize = CdsDimens.bodySize,
                        fontFamily = CdsFontFamilies.sans,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        tr("关闭", "Close"),
                        color = c.textSecondary,
                        fontSize = CdsDimens.captionSize,
                        fontFamily = CdsFontFamilies.sans,
                        modifier = Modifier
                            .clip(RoundedCornerShape(CdsDimens.radiusXs))
                            .clickable(onClick = onClose)
                            .padding(horizontal = CdsDimens.md, vertical = CdsDimens.xs),
                    )
                }
                Spacer(Modifier.height(CdsDimens.md))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Column {
                        Row {
                            block.header.forEach { h -> Cell(h, header = true, cellWidth = 160.dp) }
                        }
                        block.rows.forEach { row ->
                            Row(verticalAlignment = Alignment.Top) {
                                row.forEach { cell -> Cell(cell, header = false, cellWidth = 160.dp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(text: String, header: Boolean, cellWidth: Dp = 120.dp) {
    val c = cdsColors
    Text(
        text = text,
        color = if (header) c.textPrimary else c.textSecondary,
        fontFamily = CdsFontFamilies.sans,
        fontSize = CdsDimens.footnoteSize,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .width(cellWidth)
            .background(if (header) c.surface3 else c.surface2)
            .border(0.5.dp, c.border)
            .padding(horizontal = CdsDimens.md, vertical = CdsDimens.sm),
    )
}

/** KaTeX 的样式表。必须内联：file:// 页面加载 <link> 会被当成跨源样式表挡掉。 */
private fun loadKatexCss(context: android.content.Context): String = runCatching {
    context.assets.open("katex/katex.min.css").bufferedReader().use { it.readText() }
        .replace("url(fonts/", "url(file:///android_asset/katex/fonts/")
}.getOrDefault("")

/** 页面渲染完把实际高度报回 Compose；字体是异步加载的，几个时机都要报一次 */
private const val HEIGHT_REPORTER_JS = """
  var lastHeight = -1;
  function report() {
    var h = Math.ceil(document.documentElement.scrollHeight);
    if (h > 0 && h !== lastHeight) {
      lastHeight = h;
      if (window.AndroidHeight) window.AndroidHeight.report(String(h));
    }
  }
  new ResizeObserver(report).observe(document.body);
  window.addEventListener('load', report);
  setTimeout(report, 60);
  setTimeout(report, 250);
"""

/**
 * 自适应高度的 WebView。块级公式和带行内公式的段落都用它：
 * 高度由页面里的脚本量出来回写，Compose 这边跟着变。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AutoHeightWebView(html: String, minHeight: Dp = 56.dp) {
    // html 变了（换主题、改公式）要整块重建：AndroidView 的 factory 只跑一次，
    // 不 key 住的话，切到深色模式后旧 WebView 仍然是黑字
    androidx.compose.runtime.key(html) {
        HeightMeasuredWebView(html, minHeight)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HeightMeasuredWebView(html: String, minHeight: Dp) {
    var heightPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            // WebView 的 CSS 像素就是 dp，这里不能再按设备密度换算，
            // 否则 75px 高的公式会被压成 26dp，只剩一条被裁掉的横条。
            .height(if (heightPx > 0) heightPx.dp else minHeight),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                // KaTeX 的 css/js/字体都是 file:// 子资源。从 Android 4.1 起
                // file:// 页面访问其他 file:// 资源默认被拦，要显式放开
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                // 关掉 WebView 的「强制深色」：页面背景是透明的，
                // 系统会把我们按深色主题调好的浅色文字再反转一次，变成深色不可读
                @Suppress("DEPRECATION")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    settings.forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
                }
                // 跟着 App 的字号缩放走，公式和正文才不会一大一小
                settings.textZoom = (density.fontScale * 100).toInt()
                addJavascriptInterface(
                    object {
                        @android.webkit.JavascriptInterface
                        fun report(px: String) {
                            val h = px.toIntOrNull() ?: return
                            // JS 接口跑在后台线程，状态更新要回主线程
                            mainHandler.post { if (h > 0) heightPx = h }
                        }
                    },
                    "AndroidHeight",
                )
                loadDataWithBaseURL("file:///android_asset/katex/", html, "text/html", "utf-8", null)
            }
        },
        update = { web ->
            // 设置里改字号后，已经建好的 WebView 也要跟着缩放
            val zoom = (density.fontScale * 100).toInt()
            if (web.settings.textZoom != zoom) web.settings.textZoom = zoom
        },
    )
}

/** 块级公式 */
@Composable
private fun MathBlock(latex: String) {
    val c = cdsColors
    val colorHex = "#%06X".format(c.textPrimary.toArgb() and 0xFFFFFF)
    val context = androidx.compose.ui.platform.LocalContext.current
    val katexCss = remember { loadKatexCss(context) }

    val html = remember(latex, colorHex, katexCss, c.isDark) {
        """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="color-scheme" content="${if (c.isDark) "dark" else "light"}">
        <style>$katexCss</style>
        <script src="katex.min.js"></script>
        <style>
          html,body{margin:0;padding:0;background:transparent;color:$colorHex;overflow:hidden;color-scheme:${if (c.isDark) "dark" else "light"};}
          #m{font-size:16px;padding:2px 0;display:flex;justify-content:center;}
        </style></head><body><div id="m"></div>
        <script>
          try {
            katex.render(${jsString(latex)}, document.getElementById('m'),
              {displayMode:true, throwOnError:false});
          } catch (e) {}
          $HEIGHT_REPORTER_JS
        </script></body></html>
        """.trimIndent()
    }

    AutoHeightWebView(html)
}

/** 含行内公式的段落：整段交给 KaTeX 的 auto-render，公式按基线嵌在文字里 */
@Composable
private fun MathParagraph(spans: List<Inline>, modifier: Modifier = Modifier.fillMaxWidth()) {
    val c = cdsColors
    val colorHex = "#%06X".format(c.textPrimary.toArgb() and 0xFFFFFF)
    // 链接是正文级小字，用 accentText；WebView 里 <a> 的颜色跟着一起走
    val accentHex = "#%06X".format(c.accentText.toArgb() and 0xFFFFFF)
    val context = androidx.compose.ui.platform.LocalContext.current
    val katexCss = remember { loadKatexCss(context) }
    val body = remember(spans) { spansToHtml(spans) }

    val html = remember(body, colorHex, accentHex, katexCss, c.isDark) {
        """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="color-scheme" content="${if (c.isDark) "dark" else "light"}">
        <style>$katexCss</style>
        <script src="katex.min.js"></script>
        <script src="auto-render.min.js"></script>
        <style>
          @font-face{font-family:'AnthropicSans';src:url('file:///android_asset/fonts/anthropic_sans.ttf');}
          html,body{margin:0;padding:0;background:transparent;color:$colorHex;overflow:hidden;color-scheme:${if (c.isDark) "dark" else "light"};}
          body{font-family:'AnthropicSans',sans-serif;font-size:16px;line-height:25px;word-wrap:break-word;}
          code{font-family:monospace;font-size:14px;background:rgba(127,127,127,.12);padding:1px 4px;border-radius:4px;}
          a{color:$accentHex;text-decoration:none;}
          .katex{font-size:1.02em;}
        </style></head><body>$body
        <script>
          try {
            renderMathInElement(document.body, {
              delimiters: [{left:'\\(', right:'\\)', display:false}],
              throwOnError: false,
              ignoredTags: ['script','noscript','style','textarea','pre','code']
            });
          } catch (e) {}
          $HEIGHT_REPORTER_JS
        </script></body></html>
        """.trimIndent()
    }

    Box(modifier) { AutoHeightWebView(html, minHeight = 40.dp) }
}

private fun List<Inline>.hasMath(): Boolean = any {
    when (it) {
        is Inline.MathInline -> true
        is Inline.Bold -> it.children.hasMath()
        is Inline.Italic -> it.children.hasMath()
        is Inline.Link -> it.children.hasMath()
        is Inline.Grouped -> it.items.hasMath()
        else -> false
    }
}

/** 把行内节点转成 HTML；公式保留成 \( ... \) 交给 auto-render */
private fun spansToHtml(spans: List<Inline>): String {
    val out = StringBuilder()
    fun emit(list: List<Inline>) {
        list.forEach { node ->
            when (node) {
                is Inline.Plain -> out.append(escapeHtml(node.text))
                is Inline.Bold -> { out.append("<strong>"); emit(node.children); out.append("</strong>") }
                is Inline.Italic -> { out.append("<em>"); emit(node.children); out.append("</em>") }
                is Inline.CodeSpan -> { out.append("<code>"); out.append(escapeHtml(node.text)); out.append("</code>") }
                is Inline.Link -> {
                    out.append("<a href=\"").append(escapeHtml(node.url)).append("\">")
                    emit(node.children)
                    out.append("</a>")
                }
                is Inline.MathInline -> {
                    out.append("\\(").append(escapeHtml(node.latex)).append("\\)")
                }
                is Inline.Grouped -> emit(node.items)
            }
        }
    }
    emit(spans)
    return out.toString()
}

private fun escapeHtml(s: String): String = buildString(s.length) {
    s.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(ch)
        }
    }
}

private fun jsString(s: String): String =
    buildString {
        append('"')
        s.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

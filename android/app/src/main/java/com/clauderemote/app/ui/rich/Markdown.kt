package com.clauderemote.app.ui.rich

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import com.clauderemote.app.ui.theme.CdsFontFamilies

/**
 * 消息正文的分块模型。
 *
 * 段落、标题、列表、代码块、表格都走原生 Compose 渲染；
 * 只有块级公式交给 WebView + KaTeX —— Compose 里没有成熟的数学排版方案。
 */
sealed interface RichBlock {
    data class Paragraph(val spans: List<Inline>) : RichBlock
    data class Heading(val level: Int, val spans: List<Inline>) : RichBlock
    data class Bullets(val items: List<List<Inline>>, val ordered: Boolean, val start: Int) : RichBlock
    data class Code(val lang: String, val text: String) : RichBlock
    data class Quote(val spans: List<Inline>) : RichBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : RichBlock
    data class Math(val latex: String) : RichBlock
    data object Rule : RichBlock
}

sealed interface Inline {
    data class Plain(val text: String) : Inline
    data class Bold(val children: List<Inline>) : Inline
    data class Italic(val children: List<Inline>) : Inline
    data class CodeSpan(val text: String) : Inline
    data class Link(val children: List<Inline>, val url: String) : Inline
    data class MathInline(val latex: String) : Inline
    data class Grouped(val items: List<Inline>) : Inline
}

object Markdown {

    private val extensions = listOf(TablesExtension.create())
    private val parser: Parser = Parser.builder().extensions(extensions).build()

    fun toBlocks(markdown: String): List<RichBlock> {
        if (markdown.isBlank()) return emptyList()
        val doc = parser.parse(markdown)
        val out = ArrayList<RichBlock>()
        var child = doc.firstChild
        while (child != null) {
            convertBlock(child, out)
            child = child.next
        }
        return out
    }

    private fun convertBlock(node: Node, out: MutableList<RichBlock>) {
        when (node) {
            is Paragraph -> {
                val raw = literal(node)
                val display = displayMath(raw)
                if (display != null) out += RichBlock.Math(display)
                else out += RichBlock.Paragraph(inlines(node))
            }
            is Heading -> out += RichBlock.Heading(node.level, inlines(node))
            is FencedCodeBlock -> out += RichBlock.Code(
                lang = node.info?.trim()?.split(' ')?.firstOrNull().orEmpty(),
                text = node.literal.orEmpty().trimEnd('\n'),
            )
            is BlockQuote -> {
                // 引用里的内容展平成一个段落（仍然走行内解析，保住加粗和公式）
                val parts = ArrayList<Inline>()
                var c = node.firstChild
                while (c != null) {
                    if (c is Paragraph || c is Heading) {
                        if (parts.isNotEmpty()) parts += Inline.Plain("\n")
                        parts += inlines(c)
                    }
                    c = c.next
                }
                if (parts.isEmpty()) parts += Inline.Plain(literal(node).trim())
                out += RichBlock.Quote(parts)
            }
            is BulletList, is OrderedList -> {
                val ordered = node is OrderedList
                val items = ArrayList<List<Inline>>()
                var c = node.firstChild
                while (c != null) {
                    if (c is ListItem) {
                        // 列表项也要走行内解析，否则加粗、行内公式都会被原样显示
                        val parts = ArrayList<Inline>()
                        var cc = c.firstChild
                        while (cc != null) {
                            if (cc is Paragraph || cc is Heading) parts += inlines(cc)
                            else {
                                val t = literal(cc)
                                if (t.isNotBlank()) parts += Inline.Plain(t)
                            }
                            cc = cc.next
                        }
                        items += parts
                    }
                    c = c.next
                }
                val start = if (node is OrderedList) node.markerStartNumber else 1
                out += RichBlock.Bullets(items, ordered, start)
            }
            is TableBlock -> {
                val header = ArrayList<String>()
                val rows = ArrayList<List<String>>()
                var first = true
                // commonmark 的结构是 TableBlock → TableHead/TableBody → TableRow，
                // 只遍历直接子节点永远碰不到 TableRow，表格会解析成空表
                fun collectRows(parent: Node) {
                    var c = parent.firstChild
                    while (c != null) {
                        if (c is TableRow) {
                            val cells = ArrayList<String>()
                            var cell = c.firstChild
                            while (cell != null) {
                                if (cell is TableCell) cells += literal(cell).trim()
                                cell = cell.next
                            }
                            if (first) {
                                header.addAll(cells)
                                first = false
                            } else {
                                rows += cells
                            }
                        } else {
                            collectRows(c)
                        }
                        c = c.next
                    }
                }
                collectRows(node)
                out += RichBlock.Table(header, rows)
            }
            is ThematicBreak -> out += RichBlock.Rule
            is HtmlBlock -> out += RichBlock.Paragraph(listOf(Inline.Plain(node.literal.orEmpty())))
            else -> {
                // 未知类型直接按纯文本处理，避免内容丢失
                val text = literal(node)
                if (text.isNotBlank()) out += RichBlock.Paragraph(listOf(Inline.Plain(text)))
            }
        }
    }

    private fun displayMath(raw: String): String? {
        val t = raw.trim()
        if (t.startsWith("$$") && t.endsWith("$$") && t.length > 4) {
            return t.substring(2, t.length - 2).trim()
        }
        if (t.startsWith("\\[") && t.endsWith("\\]") && t.length > 4) {
            return t.substring(2, t.length - 2).trim()
        }
        return null
    }

    private fun literal(node: Node): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            var c = n.firstChild
            while (c != null) {
                when (c) {
                    is Text -> sb.append(c.literal)
                    is Code -> sb.append(c.literal)
                    else -> walk(c)
                }
                c = c.next
            }
        }
        walk(node)
        return sb.toString()
    }

    // ------------------------------------------------------------ 行内

    fun inlines(node: Node): List<Inline> {
        val out = ArrayList<Inline>()
        var c = node.firstChild
        while (c != null) {
            out += inlineOf(c)
            c = c.next
        }
        return out
    }

    private fun inlineOf(node: Node): Inline = when (node) {
        is Text -> splitMath(node.literal)
        is Code -> Inline.CodeSpan(node.literal)
        is StrongEmphasis -> Inline.Bold(inlines(node))
        is Emphasis -> Inline.Italic(inlines(node))
        is Link -> Inline.Link(inlines(node), node.destination)
        else -> Inline.Plain(literal(node))
    }

    /** 把 $...$ 从普通文本里拆出来 */
    private fun splitMath(text: String): Inline {
        if (!text.contains('$')) return Inline.Plain(text)
        // 只有真正的行内公式才拆，避免把价格类的 $ 误判
        val m = Regex("\\$([^$\\n]{1,80}?)\\$").findAll(text).toList()
        if (m.isEmpty()) return Inline.Plain(text)
        val parts = ArrayList<Inline>()
        var last = 0
        m.forEach { r ->
            val inner = r.groupValues[1].trim()
            // 纯数字当价格处理（"$ 5"），其余带字母、运算符、反斜杠的都认为是公式。
            // 早先要求必须含 ^_=+- 或数字，"$x$ 轴" 这种单字母公式会被漏掉。
            val looksMath = inner.isNotBlank() && !Regex("^[\\d.,]+$").matches(inner)
            if (!looksMath) return@forEach
            if (r.range.first > last) parts += Inline.Plain(text.substring(last, r.range.first))
            parts += Inline.MathInline(inner)
            last = r.range.last + 1
        }
        if (last < text.length) parts += Inline.Plain(text.substring(last))
        return when {
            parts.isEmpty() -> Inline.Plain(text)
            parts.size == 1 -> parts[0]
            else -> Inline.Grouped(parts)
        }
    }

    // ------------------------------------------------------------ 转 AnnotatedString

    fun toAnnotated(spans: List<Inline>, baseColor: androidx.compose.ui.graphics.Color, linkColor: androidx.compose.ui.graphics.Color, mathColor: androidx.compose.ui.graphics.Color): AnnotatedString =
        buildAnnotatedString {
            appendAll(spans, baseColor, linkColor, mathColor, bold = false, italic = false)
        }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendAll(
        spans: List<Inline>,
        base: androidx.compose.ui.graphics.Color,
        linkColor: androidx.compose.ui.graphics.Color,
        mathColor: androidx.compose.ui.graphics.Color,
        bold: Boolean,
        italic: Boolean,
    ) {
        spans.forEach { s ->
            when (s) {
                is Inline.Plain -> withStyle(
                    SpanStyle(color = base, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
                ) { append(s.text) }
                is Inline.Bold -> appendAll(s.children, base, linkColor, mathColor, true, italic)
                is Inline.Italic -> appendAll(s.children, base, linkColor, mathColor, bold, true)
                is Inline.Grouped -> appendAll(s.items, base, linkColor, mathColor, bold, italic)
                is Inline.CodeSpan -> withStyle(
                    SpanStyle(fontFamily = CdsFontFamilies.mono, color = base),
                ) { append(s.text) }
                is Inline.Link -> withStyle(SpanStyle(color = linkColor)) { appendAll(s.children, linkColor, linkColor, mathColor, bold, italic) }
                is Inline.MathInline -> withStyle(
                    SpanStyle(fontFamily = CdsFontFamilies.mono, color = mathColor),
                ) { append(s.latex) }
            }
        }
    }
}

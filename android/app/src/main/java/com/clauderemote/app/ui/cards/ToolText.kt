package com.clauderemote.app.ui.cards

import androidx.compose.runtime.Composable
import com.clauderemote.app.data.Block
import com.clauderemote.app.ui.i18n.tr
import org.json.JSONObject

/**
 * 工具调用的人话摘要。
 *
 * 折叠状态的工具卡只占一行，所以这里按工具类型挑最有信息量的字段：
 * 命令、文件名、行号范围、匹配模式、任务数量……展开后再看完整 JSON。
 * 权限确认卡也复用同一套摘要，避免只显示「运行命令」却看不到要跑什么。
 */
internal object ToolText {

    /** 运行命令这类卡片：可被设置里的「收纳运行命令卡片」默认收成一行 */
    fun isCommand(name: String): Boolean = name == "Bash" || name == "PowerShell"

    @Composable
    fun label(name: String): String = when (name) {
        "Bash", "PowerShell" -> tr("运行命令", "Run command")
        "BashOutput" -> tr("读取输出", "Read output")
        "KillShell" -> tr("终止命令", "Stop command")
        "Read" -> tr("读取文件", "Read file")
        "Write" -> tr("写入文件", "Write file")
        "Edit", "MultiEdit" -> tr("编辑文件", "Edit file")
        "NotebookEdit" -> tr("编辑笔记本", "Edit notebook")
        "Glob" -> tr("查找文件", "Find files")
        "Grep" -> tr("搜索内容", "Search")
        "Task", "Agent" -> tr("子任务", "Subtask")
        "WebFetch" -> tr("抓取网页", "Fetch page")
        "WebSearch" -> tr("联网搜索", "Web search")
        "TodoWrite" -> tr("更新任务清单", "Update todos")
        "AskUserQuestion" -> tr("向你提问", "Ask a question")
        "ExitPlanMode" -> tr("提交计划", "Submit plan")
        "Skill" -> tr("调用技能", "Use skill")
        "EnterPlanMode" -> tr("进入计划模式", "Enter plan mode")
        else -> name
    }

    @Composable
    fun summary(block: Block.Tool): String = summaryOf(block.name, block.input)

    @Composable
    fun summaryOf(name: String, input: JSONObject?): String {
        if (input == null) return ""
        fun s(key: String) = input.optString(key).trim()
        return when (name) {
            "Bash", "PowerShell" -> s("command").ifBlank { s("description") }
            "BashOutput" -> s("shell_id").let {
                if (it.isBlank()) tr("读取终端输出", "Read terminal output") else tr("终端输出 #$it", "Terminal output #$it")
            }
            "KillShell" -> s("shell_id").let {
                if (it.isBlank()) tr("结束命令", "Stop command") else tr("结束终端 #$it", "Stop terminal #$it")
            }
            "Read" -> buildString {
                append(tr("读取 ", "Read "))
                append(baseName(s("file_path")))
                append(rangeSuffix(input))
            }
            "Write" -> buildString {
                append(tr("写入 ", "Write "))
                append(baseName(s("file_path")))
                val lines = s("content").count { it == '\n' } + if (s("content").isBlank()) 0 else 1
                if (lines > 0) append(tr("（$lines 行）", " ($lines lines)"))
            }
            "Edit" -> buildString {
                append(tr("编辑 ", "Edit "))
                append(baseName(s("file_path")))
                val edits = if (input.optBoolean("replace_all", false)) tr(" · 全部替换", " · replace all") else ""
                append(edits)
            }
            "MultiEdit" -> buildString {
                append(tr("编辑 ", "Edit "))
                append(baseName(s("file_path")))
                val n = input.optJSONArray("edits")?.length() ?: 0
                if (n > 0) append(tr("（$n 处修改）", " ($n edits)"))
            }
            "NotebookEdit" -> tr("编辑 ${baseName(s("notebook_path"))}", "Edit ${baseName(s("notebook_path"))}")
            "Glob" -> buildString {
                append(tr("匹配 ", "Match "))
                append(s("pattern"))
                val dir = s("path")
                if (dir.isNotBlank()) append(" · $dir")
            }
            "Grep" -> buildString {
                append(tr("搜索「", "Search \""))
                append(s("pattern"))
                append(tr("」", "\""))
                val dir = s("path")
                if (dir.isNotBlank()) append(" · $dir")
            }
            "Task", "Agent" -> s("description").ifBlank { firstLine(s("prompt")) }
            "WebFetch" -> s("url").ifBlank { s("prompt") }
            "WebSearch" -> s("query")
            "TodoWrite" -> todoSummary(input)
            "AskUserQuestion" -> firstQuestion(input) ?: s("question")
            "ExitPlanMode" -> tr("提交执行计划", "Submit execution plan")
            "Skill" -> s("skill").ifBlank { s("name") }
            else -> firstNonBlank(input, listOf("command", "file_path", "path", "pattern", "prompt", "query", "url", "description"))
        }
    }

    /** 卡片右侧的小状态：执行中 / 失败 / 结果规模 */
    @Composable
    fun badge(block: Block.Tool): String = when (block.status) {
        "pending" -> tr("执行中", "Running")
        "error" -> tr("失败", "Failed")
        else -> resultBadge(block)
    }

    /**
     * 执行中的整句说明，显示在工具卡片上方的正文里。
     * 电脑端每一步都有文字交代在做什么，手机上也不能只剩一张卡片。
     */
    @Composable
    fun running(block: Block.Tool): String {
        val s = summary(block)
        val detail = if (s.isBlank()) "" else tr("：$s", ": $s")
        return when (block.name) {
            "Bash", "PowerShell" -> tr("正在运行命令$detail", "Running command$detail")
            "BashOutput" -> tr("正在读取终端输出$detail", "Reading terminal output$detail")
            "KillShell" -> tr("正在结束命令$detail", "Stopping command$detail")
            "Read" -> tr("正在" + s.ifBlank { "读取文件" }, s.ifBlank { "Reading file" })
            "Write" -> tr("正在" + s.ifBlank { "写入文件" }, s.ifBlank { "Writing file" })
            "Edit", "MultiEdit" -> tr("正在" + s.ifBlank { "编辑文件" }, s.ifBlank { "Editing file" })
            "NotebookEdit" -> tr("正在" + s.ifBlank { "编辑笔记本" }, s.ifBlank { "Editing notebook" })
            "Glob" -> tr("正在" + s.ifBlank { "查找文件" }, s.ifBlank { "Finding files" })
            "Grep" -> tr("正在" + s.ifBlank { "搜索内容" }, s.ifBlank { "Searching" })
            "Task", "Agent" -> tr("正在执行子任务$detail", "Running subtask$detail")
            "WebFetch" -> tr("正在抓取网页$detail", "Fetching page$detail")
            "WebSearch" -> tr("正在联网搜索$detail", "Searching the web$detail")
            "TodoWrite" -> tr("正在更新任务清单$detail", "Updating todos$detail")
            "AskUserQuestion" -> tr("正在等待你回答", "Waiting for your answer")
            "ExitPlanMode" -> tr("正在提交执行计划", "Submitting execution plan")
            "Skill" -> tr("正在调用技能$detail", "Using skill$detail")
            else -> tr("正在${label(block.name)}$detail", "${label(block.name)}$detail")
        }
    }

    @Composable
    private fun resultBadge(block: Block.Tool): String {
        val result = block.result ?: return tr("完成", "Done")
        return when (block.name) {
            "Read" -> tr("${result.lineCount()} 行", "${result.lineCount()} lines")
            "Glob" -> if (result.contains("No files found", ignoreCase = true)) tr("无匹配", "No match") else tr("${result.nonBlankLines()} 项", "${result.nonBlankLines()} items")
            "Grep" -> if (result.contains("No matches", ignoreCase = true)) tr("无匹配", "No match") else tr("${result.nonBlankLines()} 行", "${result.nonBlankLines()} lines")
            "Write" -> tr("已写入", "Written")
            "Edit", "MultiEdit" -> tr("已修改", "Edited")
            "TodoWrite" -> tr("已更新", "Updated")
            else -> tr("完成", "Done")
        }
    }

    private fun String.lineCount() = count { it == '\n' } + 1

    private fun String.nonBlankLines() = lineSequence().count { it.isNotBlank() }

    private fun baseName(path: String): String {
        val name = path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
        return name.ifBlank { path }
    }

    private fun firstLine(text: String): String =
        text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    @Composable
    private fun rangeSuffix(input: JSONObject): String {
        val offset = input.optInt("offset", 0)
        val limit = input.optInt("limit", 0)
        if (offset <= 0 && limit <= 0) return ""
        val start = offset.coerceAtLeast(0) + 1
        return if (limit > 0) {
            tr("（第 $start–${start + limit - 1} 行）", " (lines $start–${start + limit - 1})")
        } else {
            tr("（从第 $start 行起）", " (from line $start)")
        }
    }

    @Composable
    private fun todoSummary(input: JSONObject): String {
        val todos = input.optJSONArray("todos") ?: return tr("更新任务清单", "Update todos")
        if (todos.length() == 0) return tr("任务清单已清空", "Todo list cleared")
        var doing = 0
        for (i in 0 until todos.length()) {
            val status = todos.optJSONObject(i)?.optString("status").orEmpty()
            if (status == "in_progress") doing++
        }
        return if (doing > 0) {
            tr("${todos.length()} 项 · $doing 项进行中", "${todos.length()} items · $doing in progress")
        } else {
            tr("${todos.length()} 项任务", "${todos.length()} tasks")
        }
    }

    private fun firstQuestion(input: JSONObject): String? {
        val arr = input.optJSONArray("questions") ?: return null
        val q = arr.optJSONObject(0) ?: return null
        return q.optString("question").ifBlank { q.optString("header") }.takeIf { it.isNotBlank() }
    }

    private fun firstNonBlank(input: JSONObject, keys: List<String>): String {
        for (key in keys) {
            val value = input.optString(key).trim()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    /** 给权限确认卡用：把真实命令或文件参数单独拎出来，方便一眼看清 */
    @Composable
    fun permissionDetail(name: String, input: JSONObject?): String? {
        if (input == null) return null
        return when (name) {
            "Bash", "PowerShell" -> input.optString("command").takeIf { it.isNotBlank() }
            "Read", "Write", "Edit", "MultiEdit" -> input.optString("file_path").takeIf { it.isNotBlank() }
            "NotebookEdit" -> input.optString("notebook_path").takeIf { it.isNotBlank() }
            else -> {
                val summary = summaryOf(name, input)
                summary.takeIf { it.isNotBlank() }
            }
        }
    }
}

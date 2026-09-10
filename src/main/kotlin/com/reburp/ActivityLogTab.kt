package com.reburp

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.persistence.PersistedList
import burp.api.montoya.ui.editor.EditorOptions
import burp.api.montoya.ui.editor.HttpRequestEditor
import burp.api.montoya.ui.editor.HttpResponseEditor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.*
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.ListSelectionEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

data class LogEntry(
    val id: Int,
    val timestamp: String,
    val method: String,
    val path: String,
    val status: Int,
    val durationMs: Long,
    val requestHeaders: String,
    val requestBody: String,
    val responseBody: String,
    var notes: String = "",
    val sessionId: String? = null,
)

private class LogTableModel : AbstractTableModel() {
    private val cols    = arrayOf("#", "Time", "Method", "Status", "ms", "Path", "AI Notes")
    private val entries = mutableListOf<LogEntry>()

    fun add(entry: LogEntry) {
        entries.add(entry)
        fireTableRowsInserted(entries.size - 1, entries.size - 1)
    }

    fun clear() {
        val size = entries.size
        entries.clear()
        if (size > 0) fireTableRowsDeleted(0, size - 1)
    }

    fun getEntry(row: Int): LogEntry = entries[row]

    override fun getRowCount()                         = entries.size
    override fun getColumnCount()                      = cols.size
    override fun getColumnName(col: Int)               = cols[col]
    override fun isCellEditable(row: Int, col: Int)    = false
    override fun getValueAt(row: Int, col: Int): Any {
        val e = entries[row]
        return when (col) {
            0 -> e.id
            1 -> e.timestamp
            2 -> e.method
            3 -> if (e.status == 0) "" else e.status.toString()
            4 -> e.durationMs
            5 -> e.path
            6 -> e.notes
            else -> ""
        }
    }

    override fun getColumnClass(col: Int): Class<*> = when (col) {
        0, 4 -> Integer::class.java
        else -> String::class.java
    }
}

// ─── Main tab ─────────────────────────────────────────────────────────────────

class ActivityLogTab(private val api: MontoyaApi) {

    private val tableModel     = LogTableModel()
    private val table          = JTable(tableModel)
    private val sorter         = TableRowSorter(tableModel)
    private val counter        = AtomicInteger(0)
    private val fmt            = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private val requestEditor:        HttpRequestEditor  = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY)
    private val nestedRequestEditor:  HttpRequestEditor  = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY)
    private val responseEditor:       HttpResponseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY)
    private val nestedResponseEditor: HttpResponseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY)
    private val requestTabs  = JTabbedPane()
    private val responseTabs = JTabbedPane()

    val panel = JPanel(BorderLayout())

    private val persistedLog: PersistedList<String> by lazy {
        val ext = api.persistence().extensionData()
        ext.getStringList("activity_log") ?: PersistedList.persistedStringList().also {
            ext.setStringList("activity_log", it)
        }
    }

    init {
        setupTable()

        // Restore entries persisted from previous sessions
        runCatching {
            persistedLog.forEach { json ->
                runCatching { tableModel.add(entryFromJson(json)) }.getOrNull()
            }
            if (tableModel.rowCount > 0) counter.set(tableModel.rowCount)
        }

        requestTabs.addTab("API Request",    requestEditor.uiComponent())
        requestTabs.addTab("Target Request", nestedRequestEditor.uiComponent())

        responseTabs.addTab("API Response",    responseEditor.uiComponent())
        responseTabs.addTab("Target Response", nestedResponseEditor.uiComponent())

        val detailSplit = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            requestTabs,
            responseTabs,
        )
        detailSplit.resizeWeight      = 0.5
        detailSplit.isContinuousLayout = true

        val mainSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, JScrollPane(table), detailSplit)
        mainSplit.resizeWeight      = 0.4
        mainSplit.isContinuousLayout = true

        panel.add(buildToolbar(), BorderLayout.NORTH)
        panel.add(mainSplit, BorderLayout.CENTER)
        api.userInterface().applyThemeToComponent(panel)
    }

    // ── table setup ───────────────────────────────────────────────────────────

    private fun setupTable() {
        table.font           = Font("Monospaced", Font.PLAIN, 12)
        table.rowHeight      = 22
        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        table.rowSorter      = sorter
        table.tableHeader.reorderingAllowed = true
        table.showHorizontalLines = false
        table.intercellSpacing    = Dimension(0, 1)

        // Default sort: # descending (newest first)
        sorter.sortKeys = listOf(RowSorter.SortKey(0, SortOrder.DESCENDING))

        val cm = table.columnModel
        cm.getColumn(0).preferredWidth = 42   // #
        cm.getColumn(1).preferredWidth = 90   // Time
        cm.getColumn(2).preferredWidth = 68   // Method
        cm.getColumn(3).preferredWidth = 110  // Status
        cm.getColumn(4).preferredWidth = 58   // ms
        cm.getColumn(5).preferredWidth = 380  // Path
        cm.getColumn(6).preferredWidth = 160  // Notes

        // Method renderer - colored badges
        val methodRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                tbl: JTable, value: Any?, sel: Boolean, foc: Boolean, row: Int, col: Int
            ): Component {
                super.getTableCellRendererComponent(tbl, value, sel, foc, row, col)
                horizontalAlignment = SwingConstants.CENTER
                font = Font("Monospaced", Font.BOLD, 11)
                if (!sel) foreground = when (value?.toString()) {
                    "GET"     -> Color(100, 180, 255)
                    "POST"    -> Color(255, 165, 70)
                    "PUT"     -> Color(160, 220, 100)
                    "PATCH"   -> Color(200, 150, 255)
                    "DELETE"  -> Color(255, 100, 100)
                    "HEAD", "OPTIONS" -> Color(160, 160, 160)
                    else      -> tbl.foreground
                } else foreground = tbl.selectionForeground
                return this
            }
        }
        cm.getColumn(2).cellRenderer = methodRenderer

        // Status renderer - full text, colored
        val statusRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                tbl: JTable, value: Any?, sel: Boolean, foc: Boolean, row: Int, col: Int
            ): Component {
                val modelRow = tbl.convertRowIndexToModel(row)
                val code = tableModel.getEntry(modelRow).status
                val label = when (code) {
                    0    -> ""
                    200  -> "200 OK"; 201 -> "201 Created"; 204 -> "204 No Content"
                    301  -> "301 Moved"; 302 -> "302 Found"; 304 -> "304 Not Modified"
                    400  -> "400 Bad Request"; 401 -> "401 Unauthorized"; 403 -> "403 Forbidden"
                    404  -> "404 Not Found"; 405 -> "405 Method Not Allowed"
                    422  -> "422 Unprocessable"; 429 -> "429 Too Many Requests"
                    500  -> "500 Internal Error"; 502 -> "502 Bad Gateway"; 503 -> "503 Unavailable"
                    else -> code.toString()
                }
                super.getTableCellRendererComponent(tbl, label, sel, foc, row, col)
                if (!sel) foreground = when (code) {
                    in 200..299 -> Color(70, 190, 90)
                    in 300..399 -> Color(100, 180, 220)
                    in 400..499 -> Color(230, 140, 40)
                    in 500..599 -> Color(220, 65, 65)
                    else        -> tbl.foreground
                } else foreground = tbl.selectionForeground
                return this
            }
        }
        cm.getColumn(3).cellRenderer = statusRenderer

        // ms renderer - right-aligned with unit suffix, slow=orange/red
        val msRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                tbl: JTable, value: Any?, sel: Boolean, foc: Boolean, row: Int, col: Int
            ): Component {
                val ms = when (value) { is Long -> value; is Int -> value.toLong(); is Number -> value.toLong(); else -> 0L }
                super.getTableCellRendererComponent(tbl, "${ms}ms", sel, foc, row, col)
                horizontalAlignment = SwingConstants.RIGHT
                if (!sel) foreground = when {
                    ms > 3000L -> Color(220, 65, 65)
                    ms > 1000L -> Color(230, 140, 40)
                    else       -> tbl.foreground
                } else foreground = tbl.selectionForeground
                return this
            }
        }
        cm.getColumn(4).cellRenderer = msRenderer

        // AI Notes column - auto-generated, read-only, italic when empty
        val notesRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                tbl: JTable, value: Any?, sel: Boolean, foc: Boolean, row: Int, col: Int
            ): Component {
                val text = value?.toString() ?: ""
                super.getTableCellRendererComponent(tbl, text, sel, foc, row, col)
                font = if (text.isBlank()) Font(tbl.font.name, Font.ITALIC, tbl.font.size)
                       else tbl.font
                if (!sel && text.isBlank()) foreground = Color(100, 100, 100)
                return this
            }
        }
        cm.getColumn(6).cellRenderer = notesRenderer

        table.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
            if (!e.valueIsAdjusting && table.selectedRow >= 0) {
                val modelRow = table.convertRowIndexToModel(table.selectedRow)
                SwingUtilities.invokeLater { showEntry(tableModel.getEntry(modelRow)) }
            }
        }

        val popup = JPopupMenu()
        val sendToRepeater  = JMenuItem("Send Target Request to Repeater")
        val sendToIntruder  = JMenuItem("Send Target Request to Intruder")
        popup.add(sendToRepeater)
        popup.add(sendToIntruder)

        sendToRepeater.addActionListener {
            selectedNestedRequest()?.let { api.repeater().sendToRepeater(it.first, it.second) }
        }
        sendToIntruder.addActionListener {
            selectedNestedRequest()?.let { api.intruder().sendToIntruder(it.first, it.second) }
        }

        table.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent)  = maybeShowPopup(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)
            private fun maybeShowPopup(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val row = table.rowAtPoint(e.point)
                if (row >= 0) {
                    table.setRowSelectionInterval(row, row)
                    val modelRow = table.convertRowIndexToModel(row)
                    val entry = tableModel.getEntry(modelRow)
                    val hasRequest = extractNestedRequest(entry.responseBody) != null
                        || extractNestedRequest(entry.requestBody) != null
                    sendToRepeater.isEnabled = hasRequest
                    sendToIntruder.isEnabled = hasRequest
                    popup.show(e.component, e.x, e.y)
                }
            }
        })
    }

    private fun selectedNestedRequest(): Pair<HttpRequest, String>? {
        val row = table.selectedRow.takeIf { it >= 0 } ?: return null
        val entry = tableModel.getEntry(table.convertRowIndexToModel(row))
        val rawRequest = extractNestedRequest(entry.requestBody) ?: return null
        return try {
            val bodyJson = Json.parseToJsonElement(entry.requestBody).jsonObject
            val host     = bodyJson["host"]?.jsonPrimitive?.content ?: return null
            val port     = bodyJson["port"]?.jsonPrimitive?.intOrNull ?: 443
            val useHttps = bodyJson["use_https"]?.jsonPrimitive?.booleanOrNull ?: true
            val service  = HttpService.httpService(host, port, useHttps)
            Pair(HttpRequest.httpRequest(service, rawRequest), entry.path)
        } catch (_: Exception) { null }
    }

    // ── layout helpers ────────────────────────────────────────────────────────

    private fun labeledPane(title: String, comp: Component): JPanel {
        val hdr = JLabel("  $title")
        hdr.font   = Font(hdr.font.name, Font.BOLD, 12)
        hdr.border = EmptyBorder(4, 4, 3, 4)
        return JPanel(BorderLayout()).also {
            it.add(hdr, BorderLayout.NORTH)
            it.add(comp, BorderLayout.CENTER)
        }
    }

    // ── toolbar ───────────────────────────────────────────────────────────────

    private fun buildToolbar(): JPanel {
        val countLbl = JLabel("0 calls")
        tableModel.addTableModelListener { countLbl.text = "${tableModel.rowCount} calls" }

        val filterField = JTextField(22)
        filterField.toolTipText = "Filter by method, path, or status  (regex supported)"
        filterField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent)  = applyFilter(filterField.text)
            override fun removeUpdate(e: DocumentEvent)  = applyFilter(filterField.text)
            override fun changedUpdate(e: DocumentEvent) = applyFilter(filterField.text)
        })

        val clearFilterBtn = JButton("x").also { b ->
            b.toolTipText = "Clear filter"
            b.margin      = Insets(0, 4, 0, 4)
            b.addActionListener { filterField.text = "" }
        }

        val clearLogBtn = JButton("Clear log")
        clearLogBtn.addActionListener {
            SwingUtilities.invokeLater {
                tableModel.clear()
                requestEditor.setRequest(HttpRequest.httpRequest())
                nestedRequestEditor.setRequest(HttpRequest.httpRequest())
                responseEditor.setResponse(HttpResponse.httpResponse())
                nestedResponseEditor.setResponse(HttpResponse.httpResponse())
            }
        }

        return JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).also { bar ->
            bar.add(JLabel("reburp API Log"))
            bar.add(JSeparator(SwingConstants.VERTICAL).also { it.preferredSize = Dimension(1, 16) })
            bar.add(countLbl)
            bar.add(JSeparator(SwingConstants.VERTICAL).also { it.preferredSize = Dimension(1, 16) })
            bar.add(JLabel("Filter:"))
            bar.add(filterField)
            bar.add(clearFilterBtn)
            bar.add(Box.createHorizontalStrut(8))
            bar.add(clearLogBtn)
        }
    }

    private fun applyFilter(text: String) {
        sorter.rowFilter = if (text.isBlank()) null
        else try { RowFilter.regexFilter("(?i)${Regex.escape(text)}") }  // all columns
        catch (_: Exception) { null }
    }

    // ── entry renderer ────────────────────────────────────────────────────────

    private fun showEntry(entry: LogEntry) {
        val rawReq = buildString {
            append("${entry.method} ${entry.path} HTTP/1.1\r\n")
            if (entry.requestHeaders.isNotBlank()) {
                append(entry.requestHeaders)
                if (!entry.requestHeaders.endsWith("\n")) append("\r\n")
            }
            append("\r\n")
            if (entry.requestBody.isNotBlank()) append(entry.requestBody.trim())
        }

        val rawResp = buildString {
            append("HTTP/1.1 ${statusLine(entry.status)}\r\n")
            if (entry.responseBody.isNotBlank()) {
                val body = entry.responseBody.trim()
                append("Content-Type: application/json\r\n")
                append("Content-Length: ${body.toByteArray().size}\r\n")
                append("\r\n")
                append(body)
            } else {
                append("\r\n")
            }
        }

        requestEditor.setRequest(HttpRequest.httpRequest(rawReq))
        responseEditor.setResponse(HttpResponse.httpResponse(rawResp))

        // Prefer the actual raw request Burp sent (in responseBody["request"]),
        // fall back to reconstructing from structured fields in requestBody.
        val nested = extractNestedRequest(entry.responseBody)
            ?: extractNestedRequest(entry.requestBody)
        nestedRequestEditor.setRequest(
            if (nested != null) HttpRequest.httpRequest(nested)
            else HttpRequest.httpRequest()
        )

        // Decode nested HTTP response from the response body (e.g. /api/http/send "response" field)
        val nestedResp = extractNestedResponse(entry.responseBody)
        nestedResponseEditor.setResponse(
            if (nestedResp != null) HttpResponse.httpResponse(nestedResp)
            else HttpResponse.httpResponse()
        )

        // Auto-switch both tab panes: prefer target view when nested content exists
        requestTabs.selectedIndex  = if (nested != null) 1 else 0
        responseTabs.selectedIndex = if (nestedResp != null) 1 else 0
    }

    private fun extractNestedRequest(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val obj = Json.parseToJsonElement(body).jsonObject
            // Raw format: request string already present
            obj["request"]?.jsonPrimitive?.content
                ?: obj["requests"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                // Structured format: reconstruct from method + path + headers + body
                ?: run {
                    val method  = obj["method"]?.jsonPrimitive?.contentOrNull ?: return@run null
                    val path    = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@run null
                    val host    = obj["host"]?.jsonPrimitive?.contentOrNull ?: return@run null
                    val headers = obj["headers"]?.jsonObject?.entries
                        ?.joinToString("") { (k, v) -> "$k: ${v.jsonPrimitive.content}\r\n" } ?: ""
                    val bodyStr = when (val b = obj["body"]) {
                        null           -> null
                        is JsonPrimitive -> b.contentOrNull
                        else            -> b.toString()
                    }
                    val contentHeaders = if (bodyStr != null)
                        "Content-Type: application/json\r\nContent-Length: ${bodyStr.toByteArray().size}\r\n"
                    else ""
                    "$method $path HTTP/1.1\r\nHost: $host\r\n$headers$contentHeaders\r\n${bodyStr ?: ""}"
                }
        } catch (_: Exception) { null }
    }

    private fun extractNestedResponse(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val obj = Json.parseToJsonElement(body).jsonObject
            obj["response"]?.jsonPrimitive?.content
        } catch (_: Exception) { null }
    }

    private fun statusLine(code: Int) = when (code) {
        0   -> "000 (no response)"
        200 -> "200 OK";       201 -> "201 Created";  204 -> "204 No Content"
        400 -> "400 Bad Request"; 401 -> "401 Unauthorized"; 403 -> "403 Forbidden"
        404 -> "404 Not Found"; 405 -> "405 Method Not Allowed"; 422 -> "422 Unprocessable Entity"
        500 -> "500 Internal Server Error"; 503 -> "503 Service Unavailable"
        else -> "$code"
    }

    // ── public API ────────────────────────────────────────────────────────────

    // ── AI notes - see AiNotes.kt ─────────────────────────────────────────────

    fun log(
        method: String, path: String, status: Int, durationMs: Long,
        requestHeaders: String, requestBody: String, responseBody: String,
        sessionId: String? = null,
        forcedNotes: String? = null,
    ) {
        // For /api/http/send* endpoints, surface the target request details
        // instead of the reburp API call itself (which is always POST 200).
        val (displayMethod, displayPath, displayStatus, displayNotes) = run {
            // forcedNotes bypasses analyzeEntry entirely - used for PoC-labeled multi-step attacks
            if (forcedNotes != null) return@run arrayOf(method, path, status, forcedNotes)
            val resp = try { Json.parseToJsonElement(responseBody).jsonObject } catch (_: Exception) { null }
            val targetStatus  = resp?.get("status")?.jsonPrimitive?.intOrNull
            val targetNotes   = resp?.get("ai_notes")?.jsonPrimitive?.contentOrNull
            if (targetStatus != null) {
                val req = try { Json.parseToJsonElement(requestBody).jsonObject } catch (_: Exception) { null }
                val tMethod = req?.get("method")?.jsonPrimitive?.contentOrNull ?: method
                val tPath   = req?.get("path")?.jsonPrimitive?.contentOrNull   ?: path
                arrayOf(tMethod, tPath, targetStatus, targetNotes ?: "")
            } else {
                arrayOf(method, path, status, analyzeEntry(method, path, status, requestBody, responseBody))
            }
        }
        val entry = LogEntry(
            id             = counter.incrementAndGet(),
            timestamp      = LocalTime.now().format(fmt),
            method         = displayMethod as String,
            path           = displayPath   as String,
            status         = displayStatus as Int,
            durationMs     = durationMs,
            requestHeaders = requestHeaders,
            requestBody    = requestBody,
            responseBody   = responseBody,
            notes          = displayNotes  as String,
            sessionId      = sessionId,
        )
        SwingUtilities.invokeLater {
            tableModel.add(entry)
            if (table.selectedRow < 0) {
                val last    = tableModel.rowCount - 1
                val viewRow = table.convertRowIndexToView(last)
                if (viewRow >= 0) table.scrollRectToVisible(table.getCellRect(viewRow, 0, true))
            }
        }
        // Persist to extensionData - trim oldest entries if over cap
        runCatching {
            persistedLog.add(entryToJson(entry))
            while (persistedLog.size > MAX_PERSISTED_ENTRIES) persistedLog.removeAt(0)
        }
    }

    // ── Persistence helpers ───────────────────────────────────────────────────

    private fun entryToJson(e: LogEntry): String = buildJsonObject {
        put("id",          e.id)
        put("ts",          e.timestamp)
        put("method",      e.method)
        put("path",        e.path)
        put("status",      e.status)
        put("duration_ms", e.durationMs)
        put("req_headers", e.requestHeaders)
        put("req_body",    e.requestBody)
        put("resp_body",   e.responseBody)
        put("notes",       e.notes)
        if (e.sessionId != null) put("session_id", e.sessionId)
    }.toString()

    private fun entryFromJson(json: String): LogEntry {
        val o = Json.parseToJsonElement(json).jsonObject
        return LogEntry(
            id             = o["id"]!!.jsonPrimitive.int,
            timestamp      = o["ts"]!!.jsonPrimitive.content,
            method         = o["method"]!!.jsonPrimitive.content,
            path           = o["path"]!!.jsonPrimitive.content,
            status         = o["status"]!!.jsonPrimitive.int,
            durationMs     = o["duration_ms"]!!.jsonPrimitive.long,
            requestHeaders = o["req_headers"]?.jsonPrimitive?.content ?: "",
            requestBody    = o["req_body"]?.jsonPrimitive?.content ?: "",
            responseBody   = o["resp_body"]?.jsonPrimitive?.content ?: "",
            notes          = o["notes"]?.jsonPrimitive?.content ?: "",
            sessionId      = o["session_id"]?.jsonPrimitive?.contentOrNull,
        )
    }

    fun allEntries(): List<LogEntry> = (0 until tableModel.rowCount).map { tableModel.getEntry(it) }

    companion object {
        private const val MAX_PERSISTED_ENTRIES = 2000
    }
}



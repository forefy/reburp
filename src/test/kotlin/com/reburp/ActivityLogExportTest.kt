package com.reburp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityLogExportTest {

    private fun entry(
        id: Int = 1,
        method: String = "GET",
        path: String = "/api/users/1",
        status: Int = 200,
        notes: String = "",
        sessionId: String? = null,
    ) = LogEntry(
        id = id, timestamp = "10:00:00.000", method = method, path = path, status = status,
        durationMs = 12, requestHeaders = "Host: 127.0.0.1:9090", requestBody = "{}",
        responseBody = "{}", notes = notes, sessionId = sessionId,
    )

    private fun row(e: LogEntry) = ExportRow(
        entry = e,
        apiRequest = "POST /api/http/send HTTP/1.1\r\nHost: 127.0.0.1:9090\r\n\r\n{}",
        apiResponse = "HTTP/1.1 200 OK\r\n\r\n{}",
        targetRequest = "GET /users/1 HTTP/1.1\r\nHost: target.example\r\n\r\n",
        targetResponse = "HTTP/1.1 200 OK\r\n\r\n{\"id\":1}",
    )

    private val columns = LogColumn.entries.toList()

    @Test
    fun `csv writes a header and one line per row`() {
        val csv = exportCsv(listOf(row(entry()), row(entry(id = 2, method = "POST"))), columns)
        val lines = csv.trim().split("\r\n")
        assertEquals(3, lines.size)
        assertEquals("#,Time,Method,Status,ms,Path,AI Notes,Session", lines[0])
        assertTrue(lines[1].startsWith("1,10:00:00.000,GET,200,12,/api/users/1"))
    }

    @Test
    fun `csv quotes separators quotes and newlines`() {
        val csv = exportCsv(listOf(row(entry(notes = "IDOR, maybe \"high\"\nconfirmed"))), columns)
        assertTrue(csv.contains("\"IDOR, maybe \"\"high\"\"\nconfirmed\""), csv)
    }

    @Test
    fun `csv neutralises spreadsheet formulas in untrusted fields`() {
        val csv = exportCsv(listOf(row(entry(path = "=cmd|'/c calc'!A1"))), columns)
        assertTrue(csv.contains("'=cmd"), csv)
        assertFalse(csv.contains(",=cmd"), csv)
    }

    @Test
    fun `csv keeps an empty status for entries that never got a response`() {
        val csv = exportCsv(listOf(row(entry(status = 0))), columns)
        assertTrue(csv.trim().split("\r\n")[1].contains("GET,,12,"), csv)
    }

    @Test
    fun `html escapes values and embeds the raw messages`() {
        val html = exportHtml(
            listOf(row(entry(path = "/search?q=<script>alert(1)</script>"))),
            columns,
            "2026-10-02 13:30:00",
        )
        assertFalse(html.contains("<script>alert"), "payload must not land unescaped")
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(html.contains("GET /users/1 HTTP/1.1"), "target request should be embedded")
        assertTrue(html.contains("2026-10-02 13:30:00"))
        assertTrue(html.trimEnd().endsWith("</html>"))
    }

    @Test
    fun `html omits sections for messages that are absent`() {
        val bare = row(entry()).copy(targetRequest = null, targetResponse = "")
        val html = exportHtml(listOf(bare), columns, "now")
        assertFalse(html.contains("Target Request"))
        assertFalse(html.contains("Target Response"))
        assertTrue(html.contains("API Request"))
    }

    @Test
    fun `target messages come first and the api pair can be dropped`() {
        val targetOnly = row(entry()).copy(apiRequest = null, apiResponse = null)
        assertEquals(
            listOf("Target Request", "Target Response"),
            targetOnly.messages().map { it.first },
        )
        val html = exportHtml(listOf(targetOnly), columns, "now")
        assertFalse(html.contains("API Request"), "the extension's own call should be absent")
        assertTrue(html.contains("GET /users/1 HTTP/1.1"))
        // Target first: a report is about the target, not about reburp talking to itself.
        assertTrue(html.indexOf("Target Request") < html.indexOf("Target Response"))
    }

    @Test
    fun `a row with no messages at all gets no toggle and no detail row`() {
        val empty = row(entry()).copy(
            apiRequest = null, apiResponse = null, targetRequest = null, targetResponse = "",
        )
        val html = exportHtml(listOf(empty), columns, "now")
        assertFalse(html.contains("class=\"detail\""), "nothing to expand means no detail row")
        assertFalse(html.contains("class=\"chev\""), "nothing to expand means no chevron")
        assertTrue(html.contains("<tr class=\"plain\">"))
    }

    @Test
    fun `html gives each detail its own row spanning every column`() {
        val columns = listOf(LogColumn.ID, LogColumn.METHOD, LogColumn.PATH)
        val html = exportHtml(listOf(row(entry())), columns, "now")
        // Nested in the last cell the messages render at that cell's width, which was unreadable.
        assertTrue(html.contains("<tr class=\"detail\" id=\"d0\" hidden><td colspan=\"4\">"), html)
        assertTrue(html.indexOf("</tr>") < html.indexOf("class=\"msgs\""), "detail must follow the data row")
    }

    @Test
    fun `html offers an explicit theme choice as well as following the system`() {
        val html = exportHtml(listOf(row(entry())), columns, "now")
        assertTrue(html.contains("data-theme-set=\"light\""))
        assertTrue(html.contains("data-theme-set=\"dark\""))
        assertTrue(html.contains("data-theme-set=\"auto\""))
        assertTrue(html.contains("prefers-color-scheme: dark"), "Auto must still follow the system")
        assertTrue(html.contains(":root[data-theme=\"dark\"]"), "explicit dark must override the system")
    }

    @Test
    fun `exports follow the column order they are given`() {
        val reordered = listOf(LogColumn.PATH, LogColumn.METHOD, LogColumn.ID)
        val csv = exportCsv(listOf(row(entry())), reordered)
        val lines = csv.trim().split("\r\n")
        assertEquals("Path,Method,#", lines[0])
        assertEquals("/api/users/1,GET,1", lines[1])
    }

    @Test
    fun `session column falls back to an empty field`() {
        val withSession = exportCsv(listOf(row(entry(sessionId = "s-42"))), listOf(LogColumn.SESSION))
        assertEquals("Session\r\ns-42", withSession.trim())
        val without = exportCsv(listOf(row(entry())), listOf(LogColumn.SESSION))
        assertEquals("Session", without.trim())
    }
}

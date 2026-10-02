package com.reburp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityLogFilterTest {

    private fun entry(
        id: Int = 282,
        method: String = "POST",
        path: String = "/api/admin/roles/grant",
        status: Int = 403,
        durationMs: Long = 921,
        host: String = "admin.target.example",
        notes: String = "Forbidden - check role/scope",
        requestBody: String = """{"host":"admin.target.example","port":443,"use_https":true}""",
        responseBody: String = """{"status":403,"request":"POST /api/admin/roles/grant"}""",
    ) = LogEntry(
        id = id, timestamp = "14:08:51.900", method = method, path = path, status = status,
        durationMs = durationMs, requestHeaders = "Host: 127.0.0.1:9090",
        requestBody = requestBody, responseBody = responseBody, notes = notes,
        host = host, targetUrl = "https://$host$path",
    )

    @Test
    fun `a target host now matches, which the column sweep could never do`() {
        val e = entry()
        assertTrue(matchesFilter(e, "admin.target.example", FilterTarget.HOST_AND_PATH, false))
        assertTrue(matchesFilter(e, "target", FilterTarget.HOST_AND_PATH, false))
        assertTrue(matchesFilter(e, "TARGET", FilterTarget.HOST_AND_PATH, false), "must be case insensitive")
        assertTrue(matchesFilter(e, "target", FilterTarget.HOST, false))
    }

    @Test
    fun `a row with no target host is excluded by a host filter`() {
        val apiOnly = entry(host = "", path = "/api/scope/add")
        assertFalse(matchesFilter(apiOnly, "target", FilterTarget.HOST, false))
        assertFalse(matchesFilter(apiOnly, "target", FilterTarget.HOST_AND_PATH, false))
    }

    @Test
    fun `narrowing the fields stops a number matching a row id or a duration`() {
        val e = entry(id = 40, durationMs = 403, path = "/api/users/40", host = "a.example")
        // The old behaviour: every column, so the id and the duration both matched.
        assertTrue(matchesFilter(e, "403", FilterTarget.ALL_COLUMNS, false))
        // Narrowed to the fields that describe the target, a status-shaped number is ignored.
        assertFalse(matchesFilter(e, "403", FilterTarget.HOST_AND_PATH, false))
        // The path genuinely contains 40, so that still matches, which is intended.
        assertTrue(matchesFilter(e, "40", FilterTarget.PATH, false))
        assertFalse(matchesFilter(e, "40", FilterTarget.HOST, false))
    }

    @Test
    fun `the text is literal so a dot in a host is a dot`() {
        val e = entry(host = "admin.target.example")
        assertFalse(
            matchesFilter(e.copy(host = "adminXtargetYexample"), "admin.target.example", FilterTarget.HOST, false),
            "a dot must not behave as a regex wildcard",
        )
        assertTrue(matchesFilter(e, "admin.target.example", FilterTarget.HOST, false))
    }

    @Test
    fun `regex metacharacters are matched literally rather than throwing`() {
        val e = entry(path = "/search?q=a+b(c)")
        assertTrue(matchesFilter(e, "a+b(c)", FilterTarget.PATH, false))
        assertFalse(matchesFilter(e, "[unclosed", FilterTarget.PATH, false))
    }

    @Test
    fun `bodies are searched only when asked for`() {
        val e = entry(responseBody = """{"status":403,"request":"POST /x\r\nAuthorization: Bearer leaked"}""")
        assertFalse(matchesFilter(e, "Bearer leaked", FilterTarget.HOST_AND_PATH, false))
        assertTrue(matchesFilter(e, "Bearer leaked", FilterTarget.HOST_AND_PATH, true))
    }

    @Test
    fun `notes are searchable on their own`() {
        val e = entry()
        assertTrue(matchesFilter(e, "role/scope", FilterTarget.NOTES, false))
        assertFalse(matchesFilter(e, "role/scope", FilterTarget.HOST, false))
    }

    @Test
    fun `blank text matches everything so the scope toggle can stand alone`() {
        assertTrue(matchesFilter(entry(), "", FilterTarget.HOST, false))
        assertTrue(matchesFilter(entry(), "   ", FilterTarget.HOST, false))
    }

    @Test
    fun `all columns still reaches every displayed value, now including the host`() {
        val e = entry()
        val all = filterHaystack(e, FilterTarget.ALL_COLUMNS, false)
        assertTrue(all.contains("admin.target.example"), "the host column is part of the sweep")
        assertTrue(all.contains("POST"))
        assertTrue(all.contains("282"))
        assertEquals(8, all.size)
    }

    @Test
    fun `an entry with no response contributes a blank status rather than a zero`() {
        val pending = filterHaystack(entry(status = 0), FilterTarget.ALL_COLUMNS, false)
        assertEquals("", pending[3], "a pending call must not read as status 0")
        val answered = filterHaystack(entry(status = 403), FilterTarget.ALL_COLUMNS, false)
        assertEquals("403", answered[3])
    }
}

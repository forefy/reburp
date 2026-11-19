package com.reburp.routes

import com.reburp.SetConfigRequest
import com.reburp.normalizeRequest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigHelpersTest {

    private val user = """{"user_options":{"proxy":{"a":1},"bchecks":{"imported_scripts":[]}}}"""

    @Test
    fun `bare user sections are read under user_options`() {
        assertEquals("user_options.proxy", qualifyUserPath("proxy"))
        assertEquals("user_options.proxy", qualifyUserPath("user_options.proxy"))
        assertEquals("user_options", qualifyUserPath("user_options"))
    }

    @Test
    fun `a path missing from the export is reported, because Burp answers it with everything`() {
        assertEquals(emptyList(), missingConfigPaths(user, listOf("user_options.proxy", "user_options.bchecks")))
        assertEquals(listOf("user_options.scanner"), missingConfigPaths(user, listOf("user_options.scanner", "user_options.proxy.a")))
        assertEquals(emptyList(), missingConfigPaths(user, emptyList()))
        assertEquals(listOf("x"), missingConfigPaths("not json", listOf("x")))
    }

    @Test
    fun `section names come from the top level or under a key`() {
        assertEquals(listOf("user_options"), configChildren(user))
        assertEquals(listOf("bchecks", "proxy"), configChildren(user, "user_options"))
    }

    @Test
    fun `config body takes an object or the same JSON as a string`() {
        val asObject = Json.decodeFromString<SetConfigRequest>("""{"json":{"proxy":{"a":1}}}""")
        val asString = Json.decodeFromString<SetConfigRequest>("""{"json":"{\"proxy\":{\"a\":1}}"}""")
        assertEquals(Json.parseToJsonElement(asObject.text()), Json.parseToJsonElement(asString.text()))
    }

    @Test
    fun `a raw request with a body gains the Content-Length it lacked`() {
        val out = normalizeRequest("POST /x HTTP/1.1\nHost: a\nContent-Type: application/json\n\n{\"v\":1}")
        assertTrue("\r\nContent-Length: 7\r\n\r\n" in out, out)
        val chunked = normalizeRequest("POST /x HTTP/1.1\r\nHost: a\r\nTransfer-Encoding: chunked\r\n\r\n1\r\na\r\n0\r\n\r\n")
        assertTrue("Content-Length" !in chunked, "chunked bodies keep their own framing")
        val fixed = normalizeRequest("POST /x HTTP/1.1\r\nHost: a\r\nContent-Length: 99\r\n\r\nab")
        assertTrue("Content-Length: 2\r\n" in fixed && "99" !in fixed)
    }
}

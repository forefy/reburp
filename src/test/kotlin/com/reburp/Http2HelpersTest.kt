package com.reburp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Http2HelpersTest {

    @Test
    fun `pseudo-headers fill scheme and authority from the target`() {
        val headers = http2PseudoHeaders(mapOf(":method" to "GET", ":path" to "/a"), "example.com", 443, true)
        assertEquals(
            listOf(":method" to "GET", ":scheme" to "https", ":authority" to "example.com", ":path" to "/a"),
            headers
        )
        val plain = http2PseudoHeaders(mapOf("method" to "GET", "PATH" to "/"), "example.com", 8080, false).toMap()
        assertEquals("http", plain[":scheme"])
        assertEquals("example.com:8080", plain[":authority"], "a non-default port belongs in :authority")
    }

    @Test
    fun `given pseudo-headers win over the defaults`() {
        val headers = http2PseudoHeaders(
            mapOf(":method" to "POST", ":path" to "/x", ":authority" to "api.example.com", ":scheme" to "https"),
            "example.com", 443, true
        ).toMap()
        assertEquals("api.example.com", headers[":authority"])
    }

    @Test
    fun `missing method or path is refused with a pointer to the raw form`() {
        val err = assertFailsWith<IllegalArgumentException> { http2PseudoHeaders(emptyMap(), "example.com", 443, true) }
        assertTrue(":method and :path" in err.message!!)
        assertTrue("'request'" in err.message!!)
        assertFailsWith<IllegalArgumentException> { http2PseudoHeaders(mapOf(":method" to "GET"), "example.com", 443, true) }
    }

    @Test
    fun `a raw request becomes HTTP2 headers and a body`() {
        val raw = "POST /api/v1/export?fmt=csv HTTP/1.1\r\nHost: app.example.test\r\nConnection: keep-alive\r\n" +
            "Content-Type: application/json\r\nX-Trace: 1\r\nContent-Length: 2\r\n\r\n{}"
        val parts = http2FromRaw(raw, "app.example.test", 443, true)
        assertEquals(
            listOf(
                ":method" to "POST", ":scheme" to "https", ":authority" to "app.example.test", ":path" to "/api/v1/export?fmt=csv",
                "content-type" to "application/json", "x-trace" to "1", "content-length" to "2"
            ),
            parts.headers
        )
        assertEquals("{}", parts.body)
    }

    @Test
    fun `the request-line version is ignored and bare LF line endings work`() {
        val parts = http2FromRaw("GET / HTTP/2\nHost: example.com\nAccept: */*\n\n", "example.com", 443, true)
        assertEquals(listOf(":method" to "GET", ":scheme" to "https", ":authority" to "example.com", ":path" to "/", "accept" to "*/*"), parts.headers)
        assertEquals("", parts.body)
    }

    @Test
    fun `a raw request without Host takes the target as authority`() {
        val parts = http2FromRaw("GET /p HTTP/1.1\r\n\r\n", "example.com", 8443, true)
        assertEquals("example.com:8443", parts.headers.toMap()[":authority"])
    }

    @Test
    fun `a raw request with no request line is refused`() {
        assertFailsWith<IllegalArgumentException> { http2FromRaw("Host: example.com\r\n\r\n", "example.com", 443, true) }
    }
}

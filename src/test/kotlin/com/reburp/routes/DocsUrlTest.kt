package com.reburp.routes

import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals

class DocsUrlTest {

    private fun url(vararg pairs: Pair<String, String>) = docsUrl(headersOf(*pairs.map { it.first to listOf(it.second) }.toTypedArray()), 9090)

    @Test fun `uses the Host the caller reached`() =
        assertEquals("http://localhost:9999/docs", url("Host" to "localhost:9999"))

    @Test fun `falls back to loopback without a Host header`() =
        assertEquals("http://127.0.0.1:9090/docs", url())

    @Test fun `honours a proxy's scheme and path prefix`() =
        assertEquals(
            "https://burp.tailnet.ts.net/reburp/docs",
            url("Host" to "burp.tailnet.ts.net", "X-Forwarded-Proto" to "https", "X-Forwarded-Prefix" to "/reburp/"),
        )

    @Test fun `takes the first scheme from a proxy chain and normalises a bare prefix`() =
        assertEquals("https://h/p/docs", url("Host" to "h", "X-Forwarded-Proto" to "https, http", "X-Forwarded-Prefix" to "p"))
}

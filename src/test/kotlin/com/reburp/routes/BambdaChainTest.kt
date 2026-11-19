package com.reburp.routes

import com.reburp.ChainStep
import com.reburp.GenerateChainRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BambdaChainTest {

    private fun chain(vararg steps: ChainStep) =
        generateChainBambda(GenerateChainRequest(name = "t", host = "example.com", port = 8443, https = true, steps = steps.toList()))

    @Test
    fun `uses the custom action's api and logging variables`() {
        val src = chain(ChainStep("GET", "/a"))
        assertFalse("api()" in src || "logging()" in src || "createRequest" in src, src)
        assertTrue("httpRequestFromUrl(\"https://example.com:8443/a\").withMethod(\"GET\")" in src, src)
        assertTrue("api.http().sendRequest(req1)" in src && "logging.logToOutput(" in src, src)
    }

    @Test
    fun `request text cannot break out of the Java string`() {
        val src = chain(ChainStep("POST", "/a", headers = mapOf("X-Q" to "a\"b"), body = "{\"k\":\"v\\\\\"}\nline2"))
        assertTrue("withAddedHeader(\"X-Q\", \"a\\\"b\")" in src, src)
        assertTrue("withBody(\"{\\\"k\\\":\\\"v\\\\\\\\\\\"}\\nline2\")" in src, src)
    }

    @Test
    fun `an extracted value is injected into a later step's header`() {
        val src = chain(
            ChainStep("POST", "/login", extract = mapOf("token" to "$.access")),
            ChainStep("GET", "/me", inject = mapOf("token" to "Authorization: Bearer {{token}}"))
        )
        assertTrue("vars.put(\"token\", m1_0.group(1))" in src, src)
        assertTrue("withAddedHeader(\"Authorization\", \"Bearer \" + vars.getOrDefault(\"token\", \"\"))" in src, src)
    }

    @Test
    fun `template expressions keep literals around each variable`() {
        assertEquals("\"a \" + vars.getOrDefault(\"x\", \"\") + \" b\"", templateExpression("a {{x}} b"))
        assertEquals("\"plain\"", templateExpression("plain"))
        assertEquals("\"\\\\\"", javaString("\\"))
    }
}

package com.reburp.routes

import burp.api.montoya.utilities.DigestAlgorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UtilityNamesTest {

    @Test
    fun `digest names are accepted as people write them`() {
        assertEquals(DigestAlgorithm.SHA_256, digestAlgorithm("SHA_256"))
        assertEquals(DigestAlgorithm.SHA_256, digestAlgorithm("sha256"))
        assertEquals(DigestAlgorithm.SHA_256, digestAlgorithm("SHA-256"))
        assertEquals(DigestAlgorithm.SHA3_256, digestAlgorithm("sha3-256"))
        assertEquals(DigestAlgorithm.SHA_1, digestAlgorithm("SHA1"))
        assertEquals(DigestAlgorithm.MD5, digestAlgorithm("md5"))
        assertNull(digestAlgorithm("rot13"))
    }

    @Test
    fun `json writes follow type, or JSON-ness when there is none`() {
        assertEquals("\"x\"", jsonLiteral("x", null))
        assertEquals("\"x\"", jsonLiteral("\"x\"", null))
        assertEquals("2", jsonLiteral("2", null))
        assertEquals("\"2\"", jsonLiteral("2", "STRING"))
        assertEquals("true", jsonLiteral("TRUE", "boolean"))
        assertEquals("42", jsonLiteral(" 42 ", "LONG"))
        assertEquals("1.5", jsonLiteral("1.5", "DOUBLE"))
        assertEquals("null", jsonLiteral("anything", "NULL"))
        assertEquals("{\"a\":1}", jsonLiteral("{\"a\":1}", "RAW"))
        assertFailsWith<IllegalArgumentException> { jsonLiteral("x", "LONG") }
        assertFailsWith<IllegalArgumentException> { jsonLiteral("{nope", "RAW") }
        assertFailsWith<IllegalArgumentException> { jsonLiteral("x", "DATE") }
    }
}

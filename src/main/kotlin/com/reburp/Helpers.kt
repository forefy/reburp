package com.reburp

import burp.api.montoya.http.HttpService
import burp.api.montoya.proxy.ProxyHttpRequestResponse
import burp.api.montoya.proxy.ProxyWebSocketMessage
import java.security.MessageDigest
import java.util.Base64

// ── Burp type → DTO conversions ──────────────────────────────────────────────

fun ProxyHttpRequestResponse.toDto(includeBody: Boolean = true): HttpEntryDto {
    val req = request()
    val res = response()
    return HttpEntryDto(
        url = req?.url() ?: "",
        method = req?.method(),
        status = res?.statusCode()?.toInt(),
        request_length = req?.toString()?.length ?: 0,
        response_length = res?.toString()?.length ?: 0,
        notes = runCatching { annotations().notes() }.getOrElse { "" },
        highlight = runCatching { annotations().highlightColor().name }.getOrNull(),
        request = if (includeBody) req?.toString() else null,
        response = if (includeBody) res?.toString() else null
    )
}

fun ProxyHttpRequestResponse.toProxyEntryDto(includeBody: Boolean = true): ProxyEntryDto {
    val req = request()
    val res = response()
    val finalReq = runCatching { finalRequest() }.getOrNull()
    val origRes = runCatching { originalResponse() }.getOrNull()
    val timing = runCatching { timingData() }.getOrNull()
    return ProxyEntryDto(
        id = runCatching { id() }.getOrElse { -1 },
        url = runCatching { url() }.getOrElse { req?.url() ?: "" },
        method = runCatching { method() }.getOrElse { req?.method() },
        host = runCatching { host() }.getOrElse { "" },
        port = runCatching { port() }.getOrElse { 0 },
        secure = runCatching { secure() }.getOrElse { false },
        status = res?.statusCode()?.toInt(),
        mime_type = runCatching { mimeType().name }.getOrNull(),
        has_response = runCatching { hasResponse() }.getOrElse { res != null },
        edited = runCatching { edited() }.getOrElse { false },
        listener_port = runCatching { listenerPort() }.getOrElse { 0 },
        time = runCatching { time()?.toString() }.getOrNull(),
        timing_ms = timing?.timeBetweenRequestSentAndEndOfResponse()?.toMillis(),
        request_length = req?.toString()?.length ?: 0,
        response_length = res?.toString()?.length ?: 0,
        notes = runCatching { annotations().notes() }.getOrElse { "" },
        highlight = runCatching { annotations().highlightColor().name }.getOrNull(),
        request = if (includeBody) req?.toString() else null,
        response = if (includeBody) res?.toString() else null,
        final_request = if (includeBody) finalReq?.toString() else null,
        original_response = if (includeBody) origRes?.toString() else null,
        http_service_string = runCatching { httpServiceString() }.getOrNull(),
        request_body = if (includeBody) runCatching { requestBody() }.getOrNull() else null,
        request_http_version = runCatching { requestHttpVersion() }.getOrNull()
    )
}

fun burp.api.montoya.http.message.HttpRequestResponse.toDto(includeBody: Boolean = true): HttpEntryDto {
    val req = request()
    val res = response()
    return HttpEntryDto(
        url = req?.url() ?: "",
        method = req?.method(),
        status = res?.statusCode()?.toInt(),
        request_length = req?.toString()?.length ?: 0,
        response_length = res?.toString()?.length ?: 0,
        notes = runCatching { annotations().notes() }.getOrElse { "" },
        highlight = null,
        request = if (includeBody) req?.toString() else null,
        response = if (includeBody) res?.toString() else null
    )
}

fun ProxyWebSocketMessage.toDto(): WsEntryDto {
    val original = runCatching { payload()?.toString() }.getOrNull()
    val edited = runCatching { editedPayload()?.toString() }.getOrNull()
    return WsEntryDto(
        direction = runCatching { direction().name }.getOrElse { "UNKNOWN" },
        payload = original,
        notes = runCatching { annotations().notes() }.getOrElse { "" },
        websocket_id = runCatching { webSocketId() }.getOrNull(),
        // Only surfaced when Burp's rules actually changed the frame, so callers can tell
        // an edited message from an untouched one without comparing strings themselves.
        edited_payload = if (edited != null && edited != original) edited else null,
        upgrade_request_url = runCatching { upgradeRequest()?.url() }.getOrNull()
    )
}

// ── HTTP request helpers ──────────────────────────────────────────────────────

fun httpService(host: String, port: Int, useHttps: Boolean): HttpService =
    HttpService.httpService(host, port, useHttps)

/**
 * Normalizes HTTP request line endings to CRLF and fixes Content-Length when the
 * body byte count changes due to line-ending normalization.
 */
fun normalizeRequest(raw: String): String {
    val normalized = raw.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n")
    val sep = normalized.indexOf("\r\n\r\n")
    if (sep < 0) return normalized
    val headerSection = normalized.substring(0, sep)
    val body = normalized.substring(sep + 4)
    if (body.isEmpty()) return normalized
    val bodyBytes = body.toByteArray(Charsets.UTF_8)
    val lines = headerSection.split("\r\n").toMutableList()
    val clIdx = lines.indexOfFirst { it.startsWith("Content-Length:", ignoreCase = true) }
    if (clIdx >= 0) lines[clIdx] = "Content-Length: ${bodyBytes.size}"
    return lines.joinToString("\r\n") + "\r\n\r\n" + body
}

// ── Utility helpers ───────────────────────────────────────────────────────────

fun diffLines(a: String, b: String): String {
    val left = a.replace("\r", "").split("\n")
    val right = b.replace("\r", "").split("\n")
    return buildString {
        appendLine("--- request_a")
        appendLine("+++ request_b")
        for (i in 0 until maxOf(left.size, right.size)) {
            val l = left.getOrNull(i)
            val r = right.getOrNull(i)
            when {
                l == r -> if (l != null) appendLine(" $l")
                else -> {
                    if (l != null) appendLine("-$l")
                    if (r != null) appendLine("+$r")
                }
            }
        }
    }.trim()
}

fun decodeJwt(token: String): String {
    val parts = token.trim().split(".")
    if (parts.size < 2) return "Invalid JWT: expected header.payload.signature"
    val decoder = Base64.getUrlDecoder()
    val header = runCatching { String(decoder.decode(parts[0]), Charsets.UTF_8) }.getOrElse { "<invalid>" }
    val payload = runCatching { String(decoder.decode(parts[1]), Charsets.UTF_8) }.getOrElse { "<invalid>" }
    val sig = parts.getOrElse(2) { "" }
    return "header=$header\npayload=$payload\nsignature=$sig"
}

fun normalizeHashAlgorithm(raw: String): String = when (raw.trim().uppercase()) {
    "SHA1" -> "SHA-1"
    "SHA256" -> "SHA-256"
    "SHA512" -> "SHA-512"
    "MD5" -> "MD5"
    else -> raw.trim()
}

fun computeHash(algorithm: String, value: String): String {
    val digest = MessageDigest.getInstance(normalizeHashAlgorithm(algorithm))
    return digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

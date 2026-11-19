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
        request_length = runCatching { req?.toByteArray()?.length() }.getOrNull() ?: 0,
        response_length = runCatching { res?.toByteArray()?.length() }.getOrNull() ?: 0,
        notes = runCatching { annotations().notes() }.getOrElse { "" },
        highlight = runCatching { annotations().highlightColor().name }.getOrNull(),
        request = if (includeBody) req?.toString() else null,
        response = if (includeBody) res?.toString() else null
    )
}

/** Default per-message cap for `include_body=true` history listings; `max_body=0` lifts it. */
const val DEFAULT_MAX_BODY = 512 * 1024

/**
 * Truncate a full HTTP message string for a listing. One history page of a thousand entries
 * whose responses run to hundreds of kilobytes each builds a JSON string in the hundreds of
 * megabytes; serialising it pinned a Netty worker for an hour and starved Burp's heap. Single-
 * entry reads keep the whole message; listings cap each one unless the caller asks otherwise.
 */
fun capMessage(message: String?, maxBody: Int): String? {
    if (message == null || maxBody <= 0 || message.length <= maxBody) return message
    return message.substring(0, maxBody) + "\r\n... [reburp: truncated ${message.length - maxBody} more chars; use max_body=0 or GET /api/proxy/history/{index} for the full message]"
}

fun ProxyHttpRequestResponse.toProxyEntryDto(includeBody: Boolean = true, maxBody: Int = 0): ProxyEntryDto {
    val req = request()
    val res = response()
    val finalReq = runCatching { finalRequest() }.getOrNull()
    val origRes = runCatching { originalResponse() }.getOrNull()
    val timing = runCatching { timingData() }.getOrNull()
    return ProxyEntryDto(
        id = runCatching { id() }.getOrElse { -1 },
        url = runCatching { url() }.getOrElse { req?.url() ?: "" },
        method = runCatching { method() }.getOrElse { req?.method() },
        host = runCatching { host() }.recoverCatching { httpService().host() }.getOrElse { "" },
        port = runCatching { port() }.recoverCatching { httpService().port() }.getOrElse { 0 },
        secure = runCatching { secure() }.recoverCatching { httpService().secure() }.getOrElse { false },
        status = res?.statusCode()?.toInt(),
        mime_type = runCatching { mimeType().name }.getOrNull(),
        has_response = runCatching { hasResponse() }.getOrElse { res != null },
        edited = runCatching { edited() }.getOrElse { false },
        listener_port = runCatching { listenerPort() }.getOrElse { 0 },
        time = runCatching { time()?.toString() }.getOrNull(),
        timing_ms = timing?.timeBetweenRequestSentAndEndOfResponse()?.toMillis(),
        request_length = runCatching { req?.toByteArray()?.length() }.getOrNull() ?: 0,
        response_length = runCatching { res?.toByteArray()?.length() }.getOrNull() ?: 0,
        notes = runCatching { annotations().notes() }.getOrElse { "" },
        highlight = runCatching { annotations().highlightColor().name }.getOrNull(),
        request = if (includeBody) capMessage(req?.toString(), maxBody) else null,
        response = if (includeBody) capMessage(res?.toString(), maxBody) else null,
        final_request = if (includeBody) capMessage(finalReq?.toString(), maxBody) else null,
        original_response = if (includeBody) capMessage(origRes?.toString(), maxBody) else null,
        http_service_string = runCatching { httpServiceString() }.getOrNull(),
        request_body = if (includeBody) capMessage(runCatching { requestBody() }.getOrNull(), maxBody) else null,
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
        request_length = runCatching { req?.toByteArray()?.length() }.getOrNull() ?: 0,
        response_length = runCatching { res?.toByteArray()?.length() }.getOrNull() ?: 0,
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
// Intruder (unlike Repeater) rejects a request with no HttpService: "HttpRequest must have an
// HttpService". A raw request only names its target in the Host header, so derive the service
// from it. TLS cannot be read from a raw request, so assume https unless the Host names port 80.
fun serviceFromRawRequest(raw: String): HttpService {
    val hostValue = raw.replace("\r\n", "\n").lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("Host:", ignoreCase = true) }
        ?.substringAfter(':')?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("raw_request has no Host header, so its target is unknown")
    val hasPort = hostValue.contains(':')
    val host = if (hasPort) hostValue.substringBeforeLast(':') else hostValue
    val port = if (hasPort) hostValue.substringAfterLast(':').toIntOrNull() else null
    val https = port != 80
    return HttpService.httpService(host, port ?: if (https) 443 else 80, https)
}

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

// ── HTTP/2 helpers ────────────────────────────────────────────────────────────

/** Headers that only mean something on an HTTP/1 connection; HTTP/2 forbids them. */
private val HTTP1_ONLY_HEADERS = setOf("host", "connection", "keep-alive", "proxy-connection", "transfer-encoding", "upgrade")

/**
 * Complete an HTTP/2 request's pseudo-headers. `:method` and `:path` must be given; `:scheme` and
 * `:authority` default from the target. Keys are lower-cased and gain a leading colon if missing.
 * Without this, a body with no pseudo-headers went to Burp as an empty request and the endpoint
 * answered 200 with a null response.
 */
fun http2PseudoHeaders(given: Map<String, String>, host: String, port: Int, useHttps: Boolean): List<Pair<String, String>> {
    val headers = LinkedHashMap<String, String>()
    for ((k, v) in given) headers[(if (k.startsWith(":")) k else ":$k").lowercase()] = v
    val missing = listOf(":method", ":path").filter { headers[it].isNullOrBlank() }
    require(missing.isEmpty()) {
        "HTTP/2 needs ${missing.joinToString(" and ")} in 'pseudo_headers', or pass the whole request as a raw 'request' string"
    }
    headers.putIfAbsent(":scheme", if (useHttps) "https" else "http")
    val defaultPort = if (useHttps) 443 else 80
    headers.putIfAbsent(":authority", if (port == defaultPort) host else "$host:$port")
    val order = listOf(":method", ":scheme", ":authority", ":path")
    return order.mapNotNull { k -> headers[k]?.let { k to it } } + headers.filterKeys { it !in order }.toList()
}

/** An HTTP/2 request split into header pairs (pseudo-headers first) and a body. */
data class Http2Parts(val headers: List<Pair<String, String>>, val body: String)

/**
 * Turn a raw HTTP/1-style request ("GET /path HTTP/1.1\r\nHost: ...") into HTTP/2 parts: the
 * request line becomes `:method` and `:path`, Host becomes `:authority`, connection-only headers
 * are dropped and names are lower-cased. The version on the request line is ignored.
 */
fun http2FromRaw(raw: String, host: String, port: Int, useHttps: Boolean): Http2Parts {
    val normalized = normalizeRequest(raw)
    val sep = normalized.indexOf("\r\n\r\n")
    val head = if (sep >= 0) normalized.substring(0, sep) else normalized.trimEnd()
    val body = if (sep >= 0) normalized.substring(sep + 4) else ""
    val lines = head.split("\r\n")
    val requestLine = lines.first().trim().split(Regex("\\s+"))
    val looksLikeRequestLine = requestLine.size >= 2 &&
        requestLine[0].matches(Regex("[A-Za-z]+")) &&
        (requestLine[1].startsWith("/") || requestLine[1] == "*" || requestLine[1].startsWith("http://") || requestLine[1].startsWith("https://"))
    require(looksLikeRequestLine) { "'request' must start with a request line such as 'GET /path HTTP/1.1'" }
    var authority: String? = null
    val regular = mutableListOf<Pair<String, String>>()
    for (line in lines.drop(1)) {
        val colon = line.indexOf(':')
        if (colon <= 0) continue
        val name = line.substring(0, colon).trim().lowercase()
        val value = line.substring(colon + 1).trim()
        when {
            name == "host" -> authority = value
            name in HTTP1_ONLY_HEADERS -> {}
            name == "content-length" && body.isEmpty() -> {}
            else -> regular += name to value
        }
    }
    val pseudo = mutableMapOf(":method" to requestLine[0], ":path" to requestLine[1])
    if (authority != null) pseudo[":authority"] = authority
    return Http2Parts(http2PseudoHeaders(pseudo, host, port, useHttps) + regular, body)
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

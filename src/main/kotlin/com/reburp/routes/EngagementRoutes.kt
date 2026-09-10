package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.URLEncoder

fun Routing.engagementRoutes(api: MontoyaApi) {
    route("/api/engagement") {

        // ── CSRF PoC Generator ────────────────────────────────────────────────
        // Parses a raw HTTP request and generates a cross-origin attack PoC.
        // format values: HTML_FORM, FETCH_JS, AUTO_SUBMIT (default)
        //   HTML_FORM   - plain <form> with hidden inputs, submit button
        //   FETCH_JS    - JavaScript fetch() call (works for any method/body)
        //   AUTO_SUBMIT - HTML_FORM with auto-submitting <script> tag

        post("/csrf-poc") {
            val req = runCatching { call.receive<CsrfPocRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.request.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'request' is required"))
            val validFormats = listOf("HTML_FORM", "FETCH_JS", "AUTO_SUBMIT")
            if (req.format.uppercase() !in validFormats) return@post call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Invalid 'format'. Allowed: ${validFormats.joinToString(", ")}")
            )
            runCatching {
                val service = httpService(req.host, req.port, req.use_https)
                val httpReq = HttpRequest.httpRequest(service, normalizeRequest(req.request))
                val method = httpReq.method().uppercase()
                val actionUrl = httpReq.url()
                val params = httpReq.parameters()
                val fmt = req.format.uppercase()
                val html = when (fmt) {
                    "FETCH_JS" -> buildFetchPoC(method, actionUrl, httpReq)
                    else -> {
                        val formParams = params.filter { it.type().name == "BODY" || it.type().name == "URL" }
                        val formHtml = buildFormPoC(method, actionUrl, formParams, autoSubmit = fmt == "AUTO_SUBMIT")
                        formHtml
                    }
                }
                call.respond(CsrfPocResponse(format = fmt, method = method, action_url = actionUrl, html = html))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error generating PoC")) }
        }

        // ── Find References ───────────────────────────────────────────────────
        // Searches proxy history and/or site map response bodies for a URL, host,
        // path, or any string fragment. Returns URLs that reference the query.
        // Body: { query, search_proxy (true), search_sitemap (true), scope_only, limit }

        post("/find-references") {
            val req = runCatching { call.receive<FindReferencesRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.query.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'query' is required"))
            val limit = req.limit.coerceIn(1, 1000)
            runCatching {
                val refs = mutableListOf<ReferenceDto>()
                val needle = req.query

                if (req.search_proxy) {
                    for (item in api.proxy().history()) {
                        if (refs.size >= limit) break
                        val url = runCatching { item.url() }.getOrElse { item.request()?.url() } ?: continue
                        if (req.scope_only && !runCatching { api.scope().isInScope(url) }.getOrElse { false }) continue
                        val body = item.response()?.bodyToString() ?: continue
                        if (body.contains(needle, ignoreCase = true)) {
                            val idx = body.indexOf(needle, ignoreCase = true)
                            val start = maxOf(0, idx - 40)
                            val end = minOf(body.length, idx + needle.length + 40)
                            refs.add(ReferenceDto(source_url = url, match = "...${body.substring(start, end)}...", source = "PROXY"))
                        }
                    }
                }

                if (req.search_sitemap) {
                    for (item in api.siteMap().requestResponses()) {
                        if (refs.size >= limit) break
                        val url = runCatching { item.request()?.url() ?: "" }.getOrElse { "" }
                        if (url.isBlank()) continue
                        if (req.scope_only && !runCatching { api.scope().isInScope(url) }.getOrElse { false }) continue
                        val body = item.response()?.bodyToString() ?: continue
                        if (body.contains(needle, ignoreCase = true)) {
                            val idx = body.indexOf(needle, ignoreCase = true)
                            val start = maxOf(0, idx - 40)
                            val end = minOf(body.length, idx + needle.length + 40)
                            refs.add(ReferenceDto(source_url = url, match = "...${body.substring(start, end)}...", source = "SITEMAP"))
                        }
                    }
                }
                call.respond(refs)
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error searching references")) }
        }

        // ── Analyze Target ────────────────────────────────────────────────────
        // Summarizes the attack surface for a host (or all hosts) from proxy history:
        // unique URLs, endpoints, HTTP methods, status codes, MIME types, parameters,
        // and interesting response headers (Server, X-Powered-By, Set-Cookie, etc.)
        // Body: { host (optional), scope_only }

        post("/analyze-target") {
            val req = runCatching { call.receive<AnalyzeTargetRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            runCatching {
                var items = api.proxy().history()
                if (req.host != null) items = items.filter { it.host().equals(req.host, ignoreCase = true) }
                if (req.scope_only) items = items.filter { runCatching { api.scope().isInScope(it.url()) }.getOrElse { false } }

                val urls = mutableSetOf<String>()
                val endpoints = mutableSetOf<String>()
                val methods = mutableMapOf<String, Int>()
                val statusCodes = mutableMapOf<String, Int>()
                val mimeTypes = mutableMapOf<String, Int>()
                val parameters = mutableSetOf<String>()
                val interestingHeaders = mutableMapOf<String, MutableSet<String>>()
                val interestingHeaderNames = setOf("server", "x-powered-by", "x-aspnet-version", "x-aspnetmvc-version",
                    "x-generator", "x-runtime", "via", "x-forwarded-for", "x-frame-options",
                    "content-security-policy", "strict-transport-security", "access-control-allow-origin")

                for (item in items) {
                    val url = runCatching { item.url() }.getOrElse { null } ?: continue
                    urls.add(url)
                    val path = runCatching { url.substringAfter("//").substringAfter("/").let { "/${it.substringBefore("?")}" } }.getOrElse { "/" }
                    endpoints.add("${runCatching { item.method() }.getOrElse { "?" }} $path")
                    methods.merge(runCatching { item.method() }.getOrElse { "UNKNOWN" }, 1, Int::plus)
                    item.response()?.let { res ->
                        statusCodes.merge(res.statusCode().toString(), 1, Int::plus)
                        res.headers().forEach { h ->
                            if (h.name().lowercase() in interestingHeaderNames) {
                                interestingHeaders.getOrPut(h.name().lowercase()) { mutableSetOf() }.add(h.value())
                            }
                        }
                    }
                    runCatching { item.mimeType().name }.getOrNull()?.let { mimeTypes.merge(it, 1, Int::plus) }
                    runCatching { item.request()?.parameters() }.getOrNull()?.forEach { p ->
                        parameters.add("${p.type().name.lowercase()}:${p.name()}")
                    }
                }

                call.respond(TargetAnalysisDto(
                    host = req.host,
                    total_requests = items.size,
                    unique_urls = urls.size,
                    unique_endpoints = endpoints.size,
                    methods = methods,
                    status_codes = statusCodes,
                    mime_types = mimeTypes,
                    parameters = parameters.sorted(),
                    interesting_headers = interestingHeaders.mapValues { it.value.toList() }
                ))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error analyzing target")) }
        }

        // ── Discover Content ──────────────────────────────────────────────────
        // Probes a wordlist of paths against a target, returning those that respond
        // with a "found" status code. Results are also added to the Burp site map.
        // concurrency: 1–20 parallel requests (default 5)
        // found_status_codes: list of status codes that indicate discovery (default includes 200-2xx, 301, 302, 401, 403)

        post("/discover-content") {
            val req = runCatching { call.receive<DiscoverContentRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.host.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'host' is required"))
            if (req.wordlist.isEmpty()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'wordlist' must not be empty"))
            if (req.wordlist.size > 5000) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'wordlist' exceeds max size of 5000 entries"))
            val concurrency = req.concurrency.coerceIn(1, 20)
            val validMethods = listOf("GET", "HEAD", "POST", "OPTIONS")
            if (req.method.uppercase() !in validMethods) return@post call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Invalid 'method'. Allowed: ${validMethods.joinToString(", ")}")
            )

            runCatching {
                val service = httpService(req.host, req.port, req.use_https)
                val scheme = if (req.use_https) "https" else "http"
                val base = req.base_path.trimEnd('/')
                val foundStatusSet = req.found_status_codes.toSet()
                val method = req.method.uppercase()

                val discovered = withContext(Dispatchers.IO) {
                    req.wordlist.chunked(concurrency).flatMap { chunk ->
                        chunk.map { word ->
                            async {
                                val path = "$base/${word.trimStart('/')}"
                                val rawReq = buildString {
                                    append("$method $path HTTP/1.1\r\n")
                                    append("Host: ${req.host}${if ((req.use_https && req.port != 443) || (!req.use_https && req.port != 80)) ":${req.port}" else ""}\r\n")
                                    req.headers.forEach { (k, v) -> append("$k: $v\r\n") }
                                    append("Connection: close\r\n\r\n")
                                }
                                runCatching {
                                    val httpReq = HttpRequest.httpRequest(service, rawReq)
                                    val rr = api.http().sendRequest(httpReq)
                                    val res = rr?.response()
                                    if (res != null) {
                                        val status = res.statusCode().toInt()
                                        if (status in foundStatusSet) {
                                            // Add to site map
                                            runCatching {
                                                api.siteMap().add(burp.api.montoya.http.message.HttpRequestResponse.httpRequestResponse(httpReq, res))
                                            }
                                            val ct = res.headers().firstOrNull { it.name().equals("Content-Type", ignoreCase = true) }?.value()
                                            DiscoveredPathDto(
                                                url = "$scheme://${req.host}${if ((req.use_https && req.port != 443) || (!req.use_https && req.port != 80)) ":${req.port}" else ""}$path",
                                                status = status,
                                                response_length = res.bodyToString().length,
                                                content_type = ct
                                            )
                                        } else null
                                    } else null
                                }.getOrNull()
                            }
                        }.awaitAll().filterNotNull()
                    }
                }
                call.respond(discovered)
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Discovery failed")) }
        }

        // ── Send to Decoder ───────────────────────────────────────────────────
        // Opens Burp Decoder tab pre-populated with the provided bytes.
        // Body: { base64: "<base64-encoded data>" }

        post("/send-to-decoder") {
            val req = runCatching { call.receive<SendToDecoderRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.base64.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'base64' is required"))
            runCatching {
                val bytes = java.util.Base64.getDecoder().decode(req.base64)
                api.decoder().sendToDecoder(burp.api.montoya.core.ByteArray.byteArray(*bytes))
                call.respond(MessageResponse("Sent ${bytes.size} bytes to Decoder tab"))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error sending to Decoder")) }
        }
    }
}

// ── CSRF PoC helpers ──────────────────────────────────────────────────────────

private fun buildFormPoC(
    method: String,
    actionUrl: String,
    params: List<burp.api.montoya.http.message.params.HttpParameter>,
    autoSubmit: Boolean
): String = buildString {
    appendLine("<!DOCTYPE html>")
    appendLine("<html>")
    appendLine("<body>")
    appendLine("""<form action="${escapeHtml(actionUrl)}" method="${escapeHtml(method)}">""")
    for (p in params) {
        appendLine("""  <input type="hidden" name="${escapeHtml(p.name())}" value="${escapeHtml(p.value())}" />""")
    }
    if (!autoSubmit) {
        appendLine("""  <input type="submit" value="Submit request" />""")
    }
    appendLine("</form>")
    if (autoSubmit) {
        appendLine("<script>document.forms[0].submit();</script>")
    }
    appendLine("</body>")
    appendLine("</html>")
}

private fun buildFetchPoC(
    method: String,
    actionUrl: String,
    httpReq: burp.api.montoya.http.message.requests.HttpRequest
): String {
    val body = httpReq.bodyToString().trim()
    val ct = httpReq.headers().firstOrNull { it.name().equals("Content-Type", ignoreCase = true) }?.value() ?: "application/x-www-form-urlencoded"
    val bodyJs = if (body.isNotEmpty()) "\"${body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")}\"" else "null"
    return """fetch("${actionUrl}", {
  method: "${method}",
  mode: "no-cors",
  credentials: "include",
  headers: { "Content-Type": "${ct}" },
  body: ${bodyJs}
});"""
}

private fun escapeHtml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#x27;")

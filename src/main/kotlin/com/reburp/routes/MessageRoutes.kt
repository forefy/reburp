package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.Annotations
import burp.api.montoya.core.HighlightColor
import burp.api.montoya.core.Marker
import burp.api.montoya.core.Range
import burp.api.montoya.http.HttpMode
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.RequestOptions
import burp.api.montoya.http.message.HttpHeader
import burp.api.montoya.http.message.HttpMessage
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.StatusCodeClass
import burp.api.montoya.http.message.params.HttpParameter
import burp.api.montoya.http.message.params.HttpParameterType
import burp.api.montoya.http.message.params.ParsedHttpParameter
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

// ── Shared value objects ──────────────────────────────────────────────────────

@Serializable data class MsgHeader(val name: String, val value: String)

@Serializable data class MsgRange(val start: Int, val end: Int)

@Serializable
data class MsgMarker(
    val start: Int,
    val end: Int,
    val excerpt: String? = null
)

@Serializable
data class MsgParameter(
    val name: String,
    val value: String,
    val type: String,
    val name_start: Int,
    val name_end: Int,
    val value_start: Int,
    val value_end: Int
)

@Serializable
data class MsgParamSpec(
    val name: String,
    val value: String = "",
    val type: String = "URL"
)

@Serializable
data class MsgCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val expiration: String? = null
)

@Serializable data class MsgKeywordCount(val keyword: String, val count: Int)

@Serializable
data class MsgStatusClass(
    val name: String,
    val start_inclusive: Int,
    val end_exclusive: Int,
    val member: Boolean
)

/** Fields every HTTP message carries, read through the shared HttpMessage interface. */
@Serializable
data class MsgCommon(
    val http_version: String? = null,
    val body_offset: Int,
    val header_count: Int,
    val headers: List<MsgHeader> = emptyList(),
    val markers: List<MsgMarker> = emptyList(),
    val header_present: Boolean? = null,
    val header_value: String? = null
)

// ── Request inspection ────────────────────────────────────────────────────────

@Serializable
data class MsgRequestInspectInput(
    val raw: String? = null,
    val url: String? = null,
    val host: String? = null,
    val port: Int = 443,
    val secure: Boolean = true,
    val header_name: String? = null,
    val parameter_name: String? = null,
    val parameter_type: String? = null
)

@Serializable
data class MsgRequestInspectResult(
    val method: String? = null,
    val url: String? = null,
    val path: String? = null,
    val path_without_query: String? = null,
    val query: String? = null,
    val file_extension: String? = null,
    val content_type: String? = null,
    val in_scope: Boolean? = null,
    val common: MsgCommon,
    val parameters: List<MsgParameter> = emptyList(),
    val has_parameters: Boolean = false,
    val has_parameters_of_type: Boolean? = null,
    val has_parameter: Boolean? = null,
    val parameter_value: String? = null,
    val parameter_type: String? = null,
    val raw_request: String
)

// ── Response inspection ───────────────────────────────────────────────────────

@Serializable
data class MsgResponseInspectInput(
    val raw: String,
    val keywords: List<String> = emptyList(),
    val header_name: String? = null,
    val cookie_name: String? = null
)

@Serializable
data class MsgResponseInspectResult(
    val status_code: Int,
    val reason_phrase: String? = null,
    val stated_mime_type: String? = null,
    val inferred_mime_type: String? = null,
    val mime_type_disagrees: Boolean = false,
    val status_classes: List<MsgStatusClass> = emptyList(),
    val common: MsgCommon,
    val cookies: List<MsgCookie> = emptyList(),
    val has_cookie: Boolean? = null,
    val cookie_value: String? = null,
    val keyword_counts: List<MsgKeywordCount> = emptyList()
)

// ── Request mutation ──────────────────────────────────────────────────────────

@Serializable
data class MsgRequestHeadersInput(
    val raw: String,
    val host: String? = null,
    val port: Int = 443,
    val secure: Boolean = true,
    val set: MsgHeader? = null,
    val add: List<MsgHeader> = emptyList(),
    val update: List<MsgHeader> = emptyList(),
    val remove: List<String> = emptyList(),
    val apply_default_headers: Boolean = false
)

@Serializable
data class MsgRequestEditResult(
    val request: String,
    val http_version: String? = null,
    val body_offset: Int,
    val headers: List<MsgHeader> = emptyList(),
    val applied: List<String> = emptyList(),
    val not_found: List<String> = emptyList()
)

@Serializable
data class MsgRequestParametersInput(
    val raw: String,
    val host: String? = null,
    val port: Int = 443,
    val secure: Boolean = true,
    val set: MsgParamSpec? = null,
    val add: List<MsgParamSpec> = emptyList(),
    val update: List<MsgParamSpec> = emptyList(),
    val remove: List<MsgParamSpec> = emptyList()
)

@Serializable
data class MsgRequestParametersResult(
    val request: String,
    val parameters: List<MsgParameter> = emptyList(),
    val has_parameters: Boolean = false,
    val applied: List<String> = emptyList()
)

@Serializable
data class MsgServiceInput(
    val raw: String,
    val host: String,
    val port: Int = 443,
    val secure: Boolean = true
)

@Serializable
data class MsgServiceResult(
    val request: String,
    val url: String? = null,
    val host: String,
    val port: Int,
    val secure: Boolean,
    val ip_address: String? = null,
    val ip_resolution_error: String? = null
)

// ── Response mutation ─────────────────────────────────────────────────────────

@Serializable
data class MsgResponseEditInput(
    val raw: String,
    val http_version: String? = null,
    val reason_phrase: String? = null,
    val add: List<MsgHeader> = emptyList(),
    val update: List<MsgHeader> = emptyList(),
    val remove: List<String> = emptyList()
)

@Serializable
data class MsgResponseEditResult(
    val response: String,
    val status_code: Int,
    val reason_phrase: String? = null,
    val http_version: String? = null,
    val body_offset: Int,
    val headers: List<MsgHeader> = emptyList(),
    val applied: List<String> = emptyList(),
    val not_found: List<String> = emptyList()
)

// ── Markers and annotations ───────────────────────────────────────────────────

@Serializable
data class MsgMarkersInput(
    val request: String? = null,
    val response: String? = null,
    val request_markers: List<MsgRange> = emptyList(),
    val response_markers: List<MsgRange> = emptyList(),
    val notes: String? = null,
    val highlight_color: String? = null
)

@Serializable
data class MsgMarkersResult(
    val request: String? = null,
    val response: String? = null,
    val request_markers: List<MsgMarker> = emptyList(),
    val response_markers: List<MsgMarker> = emptyList(),
    val pair_request_markers: List<MsgMarker> = emptyList(),
    val pair_response_markers: List<MsgMarker> = emptyList(),
    val pair_built: Boolean = false,
    val content_type: String? = null,
    val has_notes: Boolean? = null,
    val notes: String? = null,
    val has_highlight_color: Boolean? = null,
    val highlight_color: String? = null
)

// ── Timing ────────────────────────────────────────────────────────────────────

@Serializable
data class MsgTimingInput(
    val raw: String,
    val host: String,
    val port: Int = 443,
    val secure: Boolean = true,
    val http_mode: String? = null,
    val connection_id: String? = null,
    val server_name_indicator: String? = null,
    val verify_upstream_tls: Boolean = false
)

@Serializable
data class MsgTimingResult(
    val status_code: Int? = null,
    val reason_phrase: String? = null,
    val ip_address: String? = null,
    val time_request_sent: String? = null,
    val ms_to_first_response_byte: Long? = null,
    val ms_to_complete_response: Long? = null,
    val timing_available: Boolean = false,
    val options_applied: List<String> = emptyList(),
    val response_body_offset: Int? = null
)

// ── Helpers ───────────────────────────────────────────────────────────────────

private val FACTORY_PARAM_TYPES = listOf("URL", "BODY", "COOKIE")

/** Renders Burp markers as offsets plus the text they cover, for callers that cannot see the message. */
private fun markerInfos(markers: List<Marker>?, text: String): List<MsgMarker> =
    markers.orEmpty().map { m ->
        val r = m.range()
        val start = r.startIndexInclusive()
        val end = r.endIndexExclusive()
        val excerpt = if (start in 0..text.length && end in start..text.length) text.substring(start, end) else null
        MsgMarker(start, end, excerpt)
    }

/**
 * Reads the fields that HttpRequest and HttpResponse share through their common
 * HttpMessage supertype, so both inspect endpoints report them identically.
 */
private fun commonOf(message: HttpMessage, headerName: String?): MsgCommon {
    val text = runCatching { message.toString() }.getOrDefault("")
    val headers: List<MsgHeader> = runCatching { message.headers().map { MsgHeader(it.name(), it.value()) } }.getOrDefault(emptyList())
    return MsgCommon(
        http_version = runCatching { message.httpVersion() }.getOrNull(),
        body_offset = runCatching { message.bodyOffset() }.getOrDefault(-1),
        header_count = headers.size,
        headers = headers,
        markers = markerInfos(runCatching { message.markers() }.getOrNull(), text),
        header_present = headerName?.let { runCatching { message.hasHeader(it) }.getOrNull() },
        header_value = headerName?.let { runCatching { message.headerValue(it) }.getOrNull() }
    )
}

private fun parameterInfo(p: ParsedHttpParameter): MsgParameter {
    val nameRange = p.nameOffsets()
    val valueRange = p.valueOffsets()
    return MsgParameter(
        name = p.name(),
        value = p.value(),
        type = p.type().name,
        name_start = nameRange.startIndexInclusive(),
        name_end = nameRange.endIndexExclusive(),
        value_start = valueRange.startIndexInclusive(),
        value_end = valueRange.endIndexExclusive()
    )
}

/** Builds one of Burp's typed parameters. Only URL, BODY and COOKIE have factory methods. */
private fun buildParameter(spec: MsgParamSpec): HttpParameter = when (spec.type.trim().uppercase()) {
    "URL" -> HttpParameter.urlParameter(spec.name, spec.value)
    "BODY" -> HttpParameter.bodyParameter(spec.name, spec.value)
    else -> HttpParameter.cookieParameter(spec.name, spec.value)
}

/** Turns a caller supplied range into a Burp marker, or null when the range does not fit the message. */
private fun buildMarker(range: MsgRange, length: Int): Marker? {
    if (range.start < 0 || range.end <= range.start || range.end > length) return null
    return Marker.marker(Range.range(range.start, range.end))
}

private fun badRange(field: String, index: Int, range: MsgRange, length: Int): String =
    "Invalid '$field[$index]': start=${range.start}, end=${range.end}. " +
        "Offsets must satisfy 0 <= start < end <= $length, where $length is the length of the supplied message."

/**
 * HTTP message inspection and mutation, backed by Burp's own parser rather than a
 * hand rolled one.
 *
 * Every endpoint takes raw HTTP text, hands it to Montoya's `HttpRequest` or
 * `HttpResponse` factories, and reports or rewrites what Burp itself sees. That
 * matters because Burp's view of a message can differ from a naive parse: it infers
 * a MIME type from the body when the `Content-Type` header lies, it classifies
 * parameters by where they live rather than by syntax, and it records byte offsets
 * that other parts of the API (markers, insertion points, scanner issues) are
 * expressed in.
 *
 * A parsed message has no target until you give it one. Endpoints that only read or
 * rewrite text work without a service, but `url()`, scope checks and default headers
 * need `host`, `port` and `secure`, so supply them whenever a result field comes back
 * null unexpectedly.
 */
fun Routing.messageRoutes(api: MontoyaApi) {
    route("/api/http/message") {

        // Parse a raw request and report everything Burp can tell you about it.
        post("/inspect/request") {
            val req = runCatching { call.receive<MsgRequestInspectInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.raw.isNullOrEmpty() && req.url.isNullOrBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Provide either 'raw' (the request as HTTP text) or 'url' (an absolute URL to build a GET request from). Both were empty.")
                )
            }
            val lookupType: HttpParameterType? = req.parameter_type?.let { raw ->
                runCatching { HttpParameterType.valueOf(raw.trim().uppercase()) }.getOrElse {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid 'parameter_type': '$raw'. Allowed values: ${HttpParameterType.values().joinToString(", ") { t -> t.name }}")
                    )
                }
            }

            val parsed = runCatching {
                val rawText = req.raw
                when {
                    rawText.isNullOrEmpty() -> HttpRequest.httpRequestFromUrl(req.url)
                    req.host != null -> HttpRequest.httpRequest(HttpService.httpService(req.host, req.port, req.secure), rawText)
                    else -> HttpRequest.httpRequest(rawText)
                }
            }.getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Burp could not parse the request: ${it.message}")
                )
            }

            runCatching {
                val parameters: List<MsgParameter> = runCatching { parsed.parameters().map { p -> parameterInfo(p) } }.getOrDefault(emptyList())
                val hasParameter = req.parameter_name?.let { name ->
                    runCatching {
                        // hasParameter needs a type; without one, fall back to the untyped lookup.
                        if (lookupType != null) parsed.hasParameter(name, lookupType) else parsed.parameter(name) != null
                    }.getOrNull()
                }
                val parameterValue = req.parameter_name?.let { name ->
                    runCatching {
                        if (lookupType != null) parsed.parameterValue(name, lookupType) else parsed.parameterValue(name)
                    }.getOrNull()
                }
                call.respond(
                    MsgRequestInspectResult(
                        method = runCatching { parsed.method() }.getOrNull(),
                        url = runCatching { parsed.url() }.getOrNull(),
                        path = runCatching { parsed.path() }.getOrNull(),
                        path_without_query = runCatching { parsed.pathWithoutQuery() }.getOrNull(),
                        query = runCatching { parsed.query() }.getOrNull(),
                        file_extension = runCatching { parsed.fileExtension() }.getOrNull(),
                        content_type = runCatching { parsed.contentType().name }.getOrNull(),
                        in_scope = runCatching { parsed.isInScope }.getOrNull(),
                        common = commonOf(parsed, req.header_name),
                        parameters = parameters,
                        has_parameters = runCatching { parsed.hasParameters() }.getOrDefault(false),
                        has_parameters_of_type = lookupType?.let { t -> runCatching { parsed.hasParameters(t) }.getOrNull() },
                        has_parameter = hasParameter,
                        parameter_value = parameterValue,
                        parameter_type = lookupType?.name,
                        raw_request = parsed.toString()
                    )
                )
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Inspection failed: ${it.message}"))
            }
        }

        // Parse a raw response and report status, MIME typing, cookies and keyword hits.
        post("/inspect/response") {
            val req = runCatching { call.receive<MsgResponseInspectInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.raw.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'raw' is required and must contain the response as HTTP text. Received an empty string."))
            }
            if (req.keywords.size > 100) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'keywords': ${req.keywords.size} entries. At most 100 keywords may be counted in one call.")
                )
            }
            val parsed = runCatching { HttpResponse.httpResponse(req.raw) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Burp could not parse the response: ${it.message}"))
            }

            runCatching {
                val stated = runCatching { parsed.statedMimeType().name }.getOrNull()
                val inferred = runCatching { parsed.inferredMimeType().name }.getOrNull()
                val classes = StatusCodeClass.values().map { c ->
                    MsgStatusClass(
                        name = c.name,
                        start_inclusive = c.startStatusCodeInclusive(),
                        end_exclusive = c.endStatusCodeExclusive(),
                        member = runCatching { parsed.isStatusCodeClass(c) }.getOrDefault(false)
                    )
                }
                val counts: List<MsgKeywordCount> = if (req.keywords.isEmpty()) emptyList() else
                    runCatching {
                        parsed.keywordCounts(*req.keywords.toTypedArray()).map { k -> MsgKeywordCount(k.keyword(), k.count()) }
                    }.getOrDefault(emptyList())
                val cookies: List<MsgCookie> = runCatching {
                    parsed.cookies().map { c ->
                        MsgCookie(
                            name = c.name(),
                            value = c.value(),
                            domain = runCatching { c.domain() }.getOrNull(),
                            path = runCatching { c.path() }.getOrNull(),
                            expiration = runCatching { c.expiration().map { e -> e.toString() }.orElse(null) }.getOrNull()
                        )
                    }
                }.getOrDefault(emptyList())

                call.respond(
                    MsgResponseInspectResult(
                        status_code = runCatching { parsed.statusCode().toInt() }.getOrDefault(0),
                        reason_phrase = runCatching { parsed.reasonPhrase() }.getOrNull(),
                        stated_mime_type = stated,
                        inferred_mime_type = inferred,
                        mime_type_disagrees = stated != null && inferred != null && stated != inferred,
                        status_classes = classes,
                        common = commonOf(parsed, req.header_name),
                        cookies = cookies,
                        has_cookie = req.cookie_name?.let { n -> runCatching { parsed.hasCookie(n) }.getOrNull() },
                        cookie_value = req.cookie_name?.let { n -> runCatching { parsed.cookieValue(n) }.getOrNull() },
                        keyword_counts = counts
                    )
                )
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Inspection failed: ${it.message}"))
            }
        }

        // Add, replace or delete request headers, and optionally re-apply Burp's defaults.
        post("/request/headers") {
            val req = runCatching { call.receive<MsgRequestHeadersInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.raw.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'raw' is required and must contain the request as HTTP text. Received an empty string."))
            }
            val blank = (req.add + req.update + listOfNotNull(req.set)).firstOrNull { it.name.isBlank() }
            if (blank != null) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Header names must not be blank. One entry in 'set', 'add' or 'update' had name '${blank.name}'."))
            }
            if (req.apply_default_headers && req.host.isNullOrBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("'apply_default_headers' is true but 'host' is '${req.host}'. Burp derives Host and connection headers from the request's service, so a host is required here.")
                )
            }

            val parsed = runCatching {
                if (req.host != null) HttpRequest.httpRequest(HttpService.httpService(req.host, req.port, req.secure), req.raw)
                else HttpRequest.httpRequest(req.raw)
            }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Burp could not parse the request: ${it.message}"))
            }

            runCatching {
                val applied = mutableListOf<String>()
                val notFound = mutableListOf<String>()
                var out = parsed

                if (req.apply_default_headers) {
                    out = out.withDefaultHeaders()
                    applied.add("default_headers")
                }
                req.set?.let { h ->
                    out = out.withHeader(h.name, h.value)
                    applied.add("set:${h.name}")
                }
                if (req.add.isNotEmpty()) {
                    out = out.withAddedHeaders(req.add.map { h -> HttpHeader.httpHeader(h.name, h.value) })
                    applied.addAll(req.add.map { h -> "add:${h.name}" })
                }
                if (req.update.isNotEmpty()) {
                    out = out.withUpdatedHeaders(req.update.map { h -> HttpHeader.httpHeader(h.name, h.value) })
                    applied.addAll(req.update.map { h -> "update:${h.name}" })
                }
                if (req.remove.isNotEmpty()) {
                    // withRemovedHeaders matches on whole headers, so each name is resolved
                    // against the current message before removal.
                    val doomed = mutableListOf<HttpHeader>()
                    req.remove.forEach { name ->
                        val existing = if (out.hasHeader(name)) out.header(name) else null
                        if (existing == null) notFound.add(name) else {
                            doomed.add(existing)
                            applied.add("remove:$name")
                        }
                    }
                    if (doomed.isNotEmpty()) out = out.withRemovedHeaders(doomed)
                }

                call.respond(
                    MsgRequestEditResult(
                        request = out.toString(),
                        http_version = runCatching { out.httpVersion() }.getOrNull(),
                        body_offset = out.bodyOffset(),
                        headers = out.headers().map { h -> MsgHeader(h.name(), h.value()) },
                        applied = applied,
                        not_found = notFound
                    )
                )
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Header edit failed: ${it.message}"))
            }
        }

        // Add, replace or delete URL, body and cookie parameters.
        post("/request/parameters") {
            val req = runCatching { call.receive<MsgRequestParametersInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.raw.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'raw' is required and must contain the request as HTTP text. Received an empty string."))
            }
            val specs = listOfNotNull(req.set) + req.add + req.update + req.remove
            if (specs.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Nothing to do: 'set', 'add', 'update' and 'remove' were all empty."))
            }
            val badType = specs.firstOrNull { it.type.trim().uppercase() !in FACTORY_PARAM_TYPES }
            if (badType != null) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid parameter 'type': '${badType.type}' on parameter '${badType.name}'. Allowed values: ${FACTORY_PARAM_TYPES.joinToString(", ")}. Burp only exposes factories for these three; XML, JSON and multipart parameters can be read but not built.")
                )
            }
            val blank = specs.firstOrNull { it.name.isBlank() }
            if (blank != null) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Parameter names must not be blank. One entry of type '${blank.type}' had name '${blank.name}'."))
            }

            val parsed = runCatching {
                if (req.host != null) HttpRequest.httpRequest(HttpService.httpService(req.host, req.port, req.secure), req.raw)
                else HttpRequest.httpRequest(req.raw)
            }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Burp could not parse the request: ${it.message}"))
            }

            runCatching {
                val applied = mutableListOf<String>()
                var out = parsed

                req.set?.let { spec ->
                    out = out.withParameter(buildParameter(spec))
                    applied.add("set:${spec.type.uppercase()}:${spec.name}")
                }
                if (req.add.isNotEmpty()) {
                    out = out.withAddedParameters(req.add.map { s -> buildParameter(s) })
                    applied.addAll(req.add.map { s -> "add:${s.type.uppercase()}:${s.name}" })
                }
                if (req.update.isNotEmpty()) {
                    out = out.withUpdatedParameters(req.update.map { s -> buildParameter(s) })
                    applied.addAll(req.update.map { s -> "update:${s.type.uppercase()}:${s.name}" })
                }
                if (req.remove.isNotEmpty()) {
                    out = out.withRemovedParameters(req.remove.map { s -> buildParameter(s) })
                    applied.addAll(req.remove.map { s -> "remove:${s.type.uppercase()}:${s.name}" })
                }

                call.respond(
                    MsgRequestParametersResult(
                        request = out.toString(),
                        parameters = runCatching { out.parameters().map { p -> parameterInfo(p) } }.getOrDefault(emptyList()),
                        has_parameters = runCatching { out.hasParameters() }.getOrDefault(false),
                        applied = applied
                    )
                )
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Parameter edit failed: ${it.message}"))
            }
        }

        // Point an existing request at a different host, port or scheme.
        post("/request/service") {
            val req = runCatching { call.receive<MsgServiceInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.raw.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'raw' is required and must contain the request as HTTP text. Received an empty string."))
            }
            if (req.host.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'host' is required and must not be blank. Received '${req.host}'."))
            }
            if (req.port !in 1..65535) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'port': ${req.port}. Allowed values: 1 to 65535."))
            }

            val parsed = runCatching { HttpRequest.httpRequest(req.raw) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Burp could not parse the request: ${it.message}"))
            }

            runCatching {
                val service = HttpService.httpService(req.host, req.port, req.secure)
                val out = parsed.withService(service)
                val resolved = out.httpService()
                var ipError: String? = null
                val ip = runCatching { resolved.ipAddress() }.getOrElse { ipError = it.message ?: it.toString(); null }
                call.respond(
                    MsgServiceResult(
                        request = out.toString(),
                        url = runCatching { out.url() }.getOrNull(),
                        host = resolved.host(),
                        port = resolved.port(),
                        secure = resolved.secure(),
                        ip_address = ip,
                        ip_resolution_error = ipError
                    )
                )
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Retargeting failed: ${it.message}"))
            }
        }

        // Rewrite a response's status line and headers.
        post("/response/edit") {
            val req = runCatching { call.receive<MsgResponseEditInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.raw.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'raw' is required and must contain the response as HTTP text. Received an empty string."))
            }
            if (req.http_version != null && !req.http_version.startsWith("HTTP/")) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'http_version': '${req.http_version}'. It is written verbatim into the status line, so it must look like HTTP/1.0, HTTP/1.1 or HTTP/2.")
                )
            }
            val blank = (req.add + req.update).firstOrNull { it.name.isBlank() }
            if (blank != null) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Header names must not be blank. One entry in 'add' or 'update' had name '${blank.name}'."))
            }

            val parsed = runCatching { HttpResponse.httpResponse(req.raw) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Burp could not parse the response: ${it.message}"))
            }

            runCatching {
                val applied = mutableListOf<String>()
                val notFound = mutableListOf<String>()
                var out = parsed

                req.http_version?.let { v ->
                    out = out.withHttpVersion(v)
                    applied.add("http_version")
                }
                req.reason_phrase?.let { p ->
                    out = out.withReasonPhrase(p)
                    applied.add("reason_phrase")
                }
                if (req.add.isNotEmpty()) {
                    out = out.withAddedHeaders(req.add.map { h -> HttpHeader.httpHeader(h.name, h.value) })
                    applied.addAll(req.add.map { h -> "add:${h.name}" })
                }
                if (req.update.isNotEmpty()) {
                    out = out.withUpdatedHeaders(req.update.map { h -> HttpHeader.httpHeader(h.name, h.value) })
                    applied.addAll(req.update.map { h -> "update:${h.name}" })
                }
                if (req.remove.isNotEmpty()) {
                    val doomed = mutableListOf<HttpHeader>()
                    req.remove.forEach { name ->
                        val existing = if (out.hasHeader(name)) out.header(name) else null
                        if (existing == null) notFound.add(name) else {
                            doomed.add(existing)
                            applied.add("remove:$name")
                        }
                    }
                    if (doomed.isNotEmpty()) out = out.withRemovedHeaders(doomed)
                }

                call.respond(
                    MsgResponseEditResult(
                        response = out.toString(),
                        status_code = out.statusCode().toInt(),
                        reason_phrase = runCatching { out.reasonPhrase() }.getOrNull(),
                        http_version = runCatching { out.httpVersion() }.getOrNull(),
                        body_offset = out.bodyOffset(),
                        headers = out.headers().map { h -> MsgHeader(h.name(), h.value()) },
                        applied = applied,
                        not_found = notFound
                    )
                )
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Response edit failed: ${it.message}"))
            }
        }

        // Attach highlight markers to a request, a response or the pair, plus annotations.
        post("/markers") {
            val req = runCatching { call.receive<MsgMarkersInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.request.isNullOrEmpty() && req.response.isNullOrEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Provide 'request', 'response' or both as HTTP text. Both were empty."))
            }
            if (req.request.isNullOrEmpty() && req.request_markers.isNotEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'request_markers' were supplied but 'request' is empty, so there is nothing to mark."))
            }
            if (req.response.isNullOrEmpty() && req.response_markers.isNotEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'response_markers' were supplied but 'response' is empty, so there is nothing to mark."))
            }
            val color: HighlightColor? = req.highlight_color?.let { raw ->
                runCatching { HighlightColor.valueOf(raw.trim().uppercase()) }.getOrElse {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid 'highlight_color': '$raw'. Allowed values: ${HighlightColor.values().joinToString(", ") { c -> c.name }}")
                    )
                }
            }

            val parsedRequest = if (req.request.isNullOrEmpty()) null else runCatching { HttpRequest.httpRequest(req.request) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Burp could not parse the request: ${it.message}"))
            }
            val parsedResponse = if (req.response.isNullOrEmpty()) null else runCatching { HttpResponse.httpResponse(req.response) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Burp could not parse the response: ${it.message}"))
            }

            val requestText = parsedRequest?.toString() ?: ""
            val responseText = parsedResponse?.toString() ?: ""
            val requestMarkers = mutableListOf<Marker>()
            req.request_markers.forEachIndexed { i, r ->
                val m = buildMarker(r, requestText.length)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(badRange("request_markers", i, r, requestText.length)))
                requestMarkers.add(m)
            }
            val responseMarkers = mutableListOf<Marker>()
            req.response_markers.forEachIndexed { i, r ->
                val m = buildMarker(r, responseText.length)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(badRange("response_markers", i, r, responseText.length)))
                responseMarkers.add(m)
            }

            runCatching {
                val markedRequest = parsedRequest?.let { r -> if (requestMarkers.isEmpty()) r else r.withMarkers(requestMarkers) }
                val markedResponse = parsedResponse?.let { r -> if (responseMarkers.isEmpty()) r else r.withMarkers(responseMarkers) }

                var pair: HttpRequestResponse? = null
                if (markedRequest != null && markedResponse != null) {
                    var built = HttpRequestResponse.httpRequestResponse(markedRequest, markedResponse)
                    if (requestMarkers.isNotEmpty()) built = built.withRequestMarkers(requestMarkers)
                    if (responseMarkers.isNotEmpty()) built = built.withResponseMarkers(responseMarkers)
                    if (req.notes != null || color != null) {
                        var annotations = Annotations.annotations()
                        if (req.notes != null) annotations = annotations.withNotes(req.notes)
                        if (color != null) annotations = annotations.withHighlightColor(color)
                        built = built.withAnnotations(annotations)
                    }
                    pair = built
                }
                val pairAnnotations = pair?.annotations()

                call.respond(
                    MsgMarkersResult(
                        request = markedRequest?.toString(),
                        response = markedResponse?.toString(),
                        request_markers = markerInfos(markedRequest?.markers(), requestText),
                        response_markers = markerInfos(markedResponse?.markers(), responseText),
                        pair_request_markers = markerInfos(pair?.requestMarkers(), requestText),
                        pair_response_markers = markerInfos(pair?.responseMarkers(), responseText),
                        pair_built = pair != null,
                        content_type = pair?.let { p -> runCatching { p.contentType().name }.getOrNull() },
                        has_notes = pairAnnotations?.let { a -> runCatching { a.hasNotes() }.getOrNull() },
                        notes = pairAnnotations?.let { a -> runCatching { a.notes() }.getOrNull() },
                        has_highlight_color = pairAnnotations?.let { a -> runCatching { a.hasHighlightColor() }.getOrNull() },
                        highlight_color = pairAnnotations?.let { a -> runCatching { a.highlightColor().name }.getOrNull() }
                    )
                )
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Marking failed: ${it.message}"))
            }
        }

        // Send a request through Burp's own stack and report how long each stage took.
        post("/timing") {
            val req = runCatching { call.receive<MsgTimingInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.raw.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'raw' is required and must contain the request as HTTP text. Received an empty string."))
            }
            if (req.host.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'host' is required and must not be blank. Received '${req.host}'."))
            }
            if (req.port !in 1..65535) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'port': ${req.port}. Allowed values: 1 to 65535."))
            }
            val mode: HttpMode? = req.http_mode?.let { raw ->
                runCatching { HttpMode.valueOf(raw.trim().uppercase()) }.getOrElse {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid 'http_mode': '$raw'. Allowed values: ${HttpMode.values().joinToString(", ") { m -> m.name }}")
                    )
                }
            }
            if (req.connection_id != null && req.connection_id.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'connection_id': '${req.connection_id}'. Omit the field entirely to use a fresh connection, or give it a non-blank label to pin several requests to one connection."))
            }
            if (req.server_name_indicator != null && req.server_name_indicator.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'server_name_indicator': '${req.server_name_indicator}'. Omit the field to send the host as SNI, or give it a non-blank hostname."))
            }
            if (req.server_name_indicator != null && !req.secure) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'server_name_indicator' was set to '${req.server_name_indicator}' but 'secure' is false. SNI only exists inside a TLS handshake, so set 'secure' to true or drop the field."))
            }

            runCatching {
                val applied = mutableListOf<String>()
                var options = RequestOptions.requestOptions()
                if (mode != null) {
                    options = options.withHttpMode(mode)
                    applied.add("http_mode=${mode.name}")
                }
                if (req.connection_id != null) {
                    options = options.withConnectionId(req.connection_id)
                    applied.add("connection_id=${req.connection_id}")
                }
                if (req.server_name_indicator != null) {
                    options = options.withServerNameIndicator(req.server_name_indicator)
                    applied.add("server_name_indicator=${req.server_name_indicator}")
                }
                if (req.verify_upstream_tls) {
                    options = options.withUpstreamTLSVerification()
                    applied.add("upstream_tls_verification")
                }

                val service = HttpService.httpService(req.host, req.port, req.secure)
                val outgoing = HttpRequest.httpRequest(service, req.raw)
                val rr = api.http().sendRequest(outgoing, options)
                if (rr == null) {
                    return@runCatching call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse("No response from ${req.host}:${req.port}")
                    )
                }
                val timing = runCatching { rr.timingData().orElse(null) }.getOrNull()
                val response = runCatching { rr.response() }.getOrNull()
                call.respond(
                    MsgTimingResult(
                        status_code = runCatching { response?.statusCode()?.toInt() }.getOrNull(),
                        reason_phrase = runCatching { response?.reasonPhrase() }.getOrNull(),
                        ip_address = runCatching { rr.httpService().ipAddress() }.getOrNull(),
                        time_request_sent = runCatching { timing?.timeRequestSent()?.toString() }.getOrNull(),
                        ms_to_first_response_byte = runCatching { timing?.timeBetweenRequestSentAndStartOfResponse()?.toMillis() }.getOrNull(),
                        ms_to_complete_response = runCatching { timing?.timeBetweenRequestSentAndEndOfResponse()?.toMillis() }.getOrNull(),
                        timing_available = timing != null,
                        options_applied = applied,
                        response_body_offset = runCatching { response?.bodyOffset() }.getOrNull()
                    )
                )
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Send failed: ${it.message}"))
            }
        }
    }
}

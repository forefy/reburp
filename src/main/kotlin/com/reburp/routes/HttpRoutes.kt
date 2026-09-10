package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.HttpMode
import burp.api.montoya.http.RedirectionMode
import com.reburp.analyzeEntry
import burp.api.montoya.http.RequestOptions
import burp.api.montoya.http.message.HttpHeader
import burp.api.montoya.http.message.params.HttpParameter
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.requests.HttpTransformation
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.intruder.HttpRequestTemplate
import burp.api.montoya.intruder.HttpRequestTemplateGenerationOptions
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import java.time.ZonedDateTime

/**
 * Builds a raw HTTP/1.1 request string from structured fields.
 * Automatically injects Host, Content-Type, and Content-Length headers.
 */
private fun buildStructuredRequest(
    host: String, method: String, path: String,
    extraHeaders: Map<String, String>?, body: JsonElement?
): String {
    val bodyStr: String = when {
        body == null                          -> ""
        body is JsonPrimitive && body.isString -> body.content
        else                                  -> body.toString()
    }
    return buildString {
        append("${method.uppercase()} $path HTTP/1.1\r\n")
        append("Host: $host\r\n")
        // Auto-set Content-Type / Content-Length if there is a body
        if (bodyStr.isNotBlank()) {
            if (extraHeaders?.keys?.none { it.equals("content-type", ignoreCase = true) } != false)
                append("Content-Type: application/json\r\n")
            if (extraHeaders?.keys?.none { it.equals("content-length", ignoreCase = true) } != false)
                append("Content-Length: ${bodyStr.toByteArray().size}\r\n")
        }
        extraHeaders?.forEach { (k, v) -> append("$k: $v\r\n") }
        append("\r\n")
        if (bodyStr.isNotBlank()) append(bodyStr)
    }
}

fun Routing.httpRoutes(api: MontoyaApi, activityLog: ActivityLogTab? = null) {

    route("/api/http") {

        // ── Send HTTP/1.1 ─────────────────────────────────────────────────────

        post("/send") {
            val req = runCatching { call.receive<SendHttpRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.host.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'host' is required"))

            // Resolve raw vs structured format
            val rawRequest = when {
                req.request != null ->
                    normalizeRequest(req.request)
                req.method != null && req.path != null ->
                    buildStructuredRequest(req.host, req.method, req.path, req.headers, req.body)
                else -> return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Provide either 'request' (raw HTTP string) or 'method' + 'path' (structured format)")
                )
            }
            runCatching {
                val service = httpService(req.host, req.port, req.use_https)
                val httpReq = HttpRequest.httpRequest(service, rawRequest)
                val rr = if (req.redirect_mode != null || req.timeout_ms != null) {
                    var opts = RequestOptions.requestOptions()
                    if (req.redirect_mode != null) {
                        val rm = runCatching { RedirectionMode.valueOf(req.redirect_mode.trim().uppercase()) }.getOrElse {
                            return@runCatching call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse("Invalid 'redirect_mode': '${req.redirect_mode}'. Allowed values: ${RedirectionMode.values().joinToString(", ") { it.name }}")
                            )
                        }
                        opts = opts.withRedirectionMode(rm)
                    }
                    if (req.timeout_ms != null) {
                        opts = opts.withResponseTimeout(req.timeout_ms)
                    }
                    api.http().sendRequest(httpReq, opts)
                } else {
                    api.http().sendRequest(httpReq)
                }
                if (rr == null) {
                    return@runCatching call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse("Failed to connect to ${req.host}:${req.port} - no response received")
                    )
                }
                val rawResp   = rr.response()?.toString()
                val httpStatus = rr.response()?.statusCode()
                val reqStr    = rr.request()?.toString() ?: rawRequest
                call.respond(
                    HttpSendResponse(
                        request   = reqStr,
                        response  = rawResp,
                        status    = httpStatus?.toInt(),
                        ai_notes  = analyzeEntry(
                            method       = req.method ?: "GET",
                            path         = req.path ?: "/",
                            status       = httpStatus?.toInt() ?: 0,
                            requestBody  = reqStr,
                            responseBody = rawResp ?: ""
                        ).ifBlank { null }
                    )
                )
            }.onFailure { e ->
                if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Error"))
            }
        }

        // ── Send HTTP/1.1 batch ───────────────────────────────────────────────

        post("/send-batch") {
            val req = runCatching { call.receive<SendBatchRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.requests.isEmpty()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'requests' must not be empty"))
            if (req.requests.size > 20) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'requests' exceeds maximum of 20 items"))
            runCatching {
                val httpReqs = req.requests.map { r ->
                    val raw = when {
                        r.request != null -> normalizeRequest(r.request)
                        r.method != null && r.path != null -> buildStructuredRequest(r.host, r.method, r.path, r.headers, r.body)
                        else -> throw IllegalArgumentException("Each request needs 'request' or 'method'+'path'")
                    }
                    HttpRequest.httpRequest(httpService(r.host, r.port, r.use_https), raw)
                }
                val results = api.http().sendRequests(httpReqs)
                val responses = results.mapIndexed { i, rr ->
                    val fallback = req.requests[i].request ?: ""
                    HttpSendResponse(
                        request = rr?.request()?.toString() ?: fallback,
                        response = rr?.response()?.toString()
                    )
                }
                call.respond(responses)
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Send HTTP/2 ───────────────────────────────────────────────────────

        post("/send/http2") {
            val req = runCatching { call.receive<SendHttp2Request>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            runCatching {
                val service = httpService(req.host, req.port, req.use_https)
                val pseudoHeaders = req.pseudo_headers.map { (k, v) ->
                    val key = if (k.startsWith(":")) k else ":$k"
                    HttpHeader.httpHeader(key.lowercase(), v)
                }
                val regularHeaders = req.headers.map { (k, v) -> HttpHeader.httpHeader(k.lowercase(), v) }
                val httpReq = HttpRequest.http2Request(service, pseudoHeaders + regularHeaders, req.body)
                val rr = api.http().sendRequest(httpReq, HttpMode.HTTP_2)
                if (rr == null) {
                    return@runCatching call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse("Failed to connect to ${req.host}:${req.port} - no response received")
                    )
                }
                call.respond(
                    HttpSendResponse(
                        request = rr.request()?.toString() ?: "",
                        response = rr.response()?.toString()
                    )
                )
            }.onFailure { e ->
                if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Error"))
            }
        }

        // ── Parse request ─────────────────────────────────────────────────────

        post("/parse/request") {
            val req = runCatching { call.receive<ParseRequestInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                val httpReq = HttpRequest.httpRequest(req.request)
                call.respond(
                    ParsedRequestDto(
                        method = httpReq.method(),
                        path = httpReq.path(),
                        url = httpReq.url(),
                        headers = httpReq.headers().map { HeaderDto(it.name(), it.value()) },
                        parameters = httpReq.parameters().map { ParamDto(it.type().name, it.name(), it.value()) },
                        body = if (req.include_body) httpReq.bodyToString() else null,
                        body_length = httpReq.body().length()
                    )
                )
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Parse response ────────────────────────────────────────────────────

        post("/parse/response") {
            val req = runCatching { call.receive<ParseResponseInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                val httpRes = HttpResponse.httpResponse(req.response)
                call.respond(
                    ParsedResponseDto(
                        status_code = httpRes.statusCode().toInt(),
                        headers = httpRes.headers().map { HeaderDto(it.name(), it.value()) },
                        body = if (req.include_body) httpRes.bodyToString() else null,
                        body_length = httpRes.body().length()
                    )
                )
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Diff two requests ─────────────────────────────────────────────────

        post("/diff") {
            val req = runCatching { call.receive<DiffInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            call.respond(StringResult(diffLines(req.request_a, req.request_b)))
        }

        // ── Extract parameters ────────────────────────────────────────────────

        post("/params") {
            val req = runCatching { call.receive<ExtractParamsInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                val httpReq = HttpRequest.httpRequest(req.request)
                call.respond(httpReq.parameters().map { ParamDto(it.type().name, it.name(), it.value()) })
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Find reflected parameters ─────────────────────────────────────────

        post("/reflect") {
            val req = runCatching { call.receive<FindReflectedInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                val httpReq = HttpRequest.httpRequest(req.request)
                val hits = httpReq.parameters().mapNotNull { param ->
                    val value = param.value()
                    if (value.isBlank()) return@mapNotNull null
                    val count = req.response.split(value).size - 1
                    if (count > 0) mapOf("name" to param.name(), "type" to param.type().name, "count" to count.toString())
                    else null
                }
                call.respond(hits)
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Insertion points ──────────────────────────────────────────────────

        post("/insertion-points") {
            val req = runCatching { call.receive<InsertionPointsInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                val httpReq = HttpRequest.httpRequest(req.request)
                val option = HttpRequestTemplateGenerationOptions.valueOf(req.mode.trim().uppercase())
                val template = HttpRequestTemplate.httpRequestTemplate(httpReq, option)
                call.respond(
                    template.insertionPointOffsets().map { InsertionPointDto(it.startIndexInclusive(), it.endIndexExclusive()) }
                )
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }
    }

    // ── Cookie jar (GET) ──────────────────────────────────────────────────────

    get("/api/cookies") {
        val domain = call.request.queryParameters["domain"]
        val scopeOnly = call.request.queryParameters["scope_only"]?.toBooleanStrictOrNull() ?: false
        val includeValues = call.request.queryParameters["include_values"]?.toBooleanStrictOrNull() ?: false
        val includeSubdomains = call.request.queryParameters["include_subdomains"]?.toBooleanStrictOrNull() ?: true
        val domainFilter = domain?.trim()?.removePrefix(".")?.lowercase()?.ifBlank { null }

        val cookies = api.http().cookieJar().cookies()
            .filter { cookie ->
                if (domainFilter == null) true
                else {
                    val d = cookie.domain().removePrefix(".").lowercase()
                    if (includeSubdomains) d == domainFilter || d.endsWith(".$domainFilter")
                    else d == domainFilter
                }
            }
            .filter { cookie ->
                if (!scopeOnly) true
                else {
                    val d = cookie.domain().removePrefix(".")
                    api.scope().isInScope("http://$d/") || api.scope().isInScope("https://$d/")
                }
            }
            .map { cookie ->
                CookieDto(
                    name = cookie.name(),
                    value = if (includeValues) cookie.value() else "[REDACTED]",
                    domain = cookie.domain(),
                    path = cookie.path(),
                    expires_at = cookie.expiration().map { it.toString() }.orElse(null)
                )
            }
        call.respond(cookies)
    }

    // ── Cookie jar (POST / set) ───────────────────────────────────────────────

    post("/api/cookies") {
        val req = runCatching { call.receive<SetCookieRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
        }
        if (req.name.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'name' is required and must not be blank"))
        if (req.domain.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'domain' is required and must not be blank"))
        runCatching {
            val expiresAt = if (req.expires_at != null) {
                runCatching { ZonedDateTime.parse(req.expires_at) }.getOrElse {
                    return@runCatching call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid 'expires_at' format. Use ISO-8601 ZonedDateTime (e.g. 2025-01-01T00:00:00Z)")
                    )
                }
            } else null
            api.http().cookieJar().setCookie(req.name, req.value, req.domain, req.path, expiresAt)
            call.respond(MessageResponse("Cookie '${req.name}' set for domain '${req.domain}'"))
        }.onFailure { e ->
            if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Error"))
        }
    }

    // ── Cookie jar (DELETE) ───────────────────────────────────────────────────

    delete("/api/cookies/{name}") {
        val name   = call.parameters["name"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("'name' path param required"))
        val domain = call.request.queryParameters["domain"]
        runCatching {
            val jar = api.http().cookieJar()
            val matches = jar.cookies().filter {
                it.name() == name && (domain == null || it.domain().removePrefix(".").equals(domain.removePrefix("."), ignoreCase = true))
            }
            if (matches.isEmpty()) return@runCatching call.respond(HttpStatusCode.NotFound, ErrorResponse("Cookie '$name' not found${if (domain != null) " for domain '$domain'" else ""}"))
            // Montoya API has no deleteCookie; expire each match by setting expiry to epoch
            val epoch = java.time.ZonedDateTime.ofInstant(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
            matches.forEach { c -> jar.setCookie(c.name(), "", c.domain(), c.path(), epoch) }
            call.respond(MessageResponse("Deleted ${matches.size} cookie(s) named '$name'"))
        }.onFailure { if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
    }

    // ── Send HTTP/1.1 with automatic JWT re-auth ──────────────────────────────

    post("/api/http/send-with-auth") {
        val req = runCatching { call.receive<SendWithAuthRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
        }

        fun extractToken(responseBody: String, tokenPath: String): String? {
            val key = tokenPath.removePrefix("$.").trim()
            return runCatching {
                val json = Json.parseToJsonElement(responseBody).jsonObject
                key.split(".").fold(json as JsonElement?) { acc, part ->
                    acc?.jsonObject?.get(part)
                }?.jsonPrimitive?.content
            }.getOrNull()
        }

        fun sendTarget(extraHeader: String?): burp.api.montoya.http.message.responses.HttpResponse? {
            val rawReq = when {
                req.request.request != null -> normalizeRequest(req.request.request)
                req.request.method != null && req.request.path != null ->
                    buildStructuredRequest(req.request.host, req.request.method, req.request.path, req.request.headers, req.request.body)
                else -> return null
            }
            val augmented = if (extraHeader != null) {
                val headerName  = extraHeader.substringBefore(":").trim()
                val headerValue = extraHeader.substringAfter(":").trim()
                HttpRequest.httpRequest(httpService(req.request.host, req.request.port, req.request.use_https), rawReq)
                    .withAddedHeader(headerName, headerValue)
            } else {
                HttpRequest.httpRequest(httpService(req.request.host, req.request.port, req.request.use_https), rawReq)
            }
            return api.http().sendRequest(augmented).response()
        }

        runCatching {
            val firstResp = sendTarget(null)
            val firstStatus = firstResp?.statusCode()?.toInt() ?: 0

            if (firstStatus !in req.retry_on) {
                call.respond(HttpSendResponse(
                    request  = req.request.request ?: "",
                    response = firstResp?.toString()
                ))
                return@post
            }

            val authRaw = buildStructuredRequest(req.auth.host, req.auth.method, req.auth.path, req.auth.headers, req.auth.body)
            val authReq = HttpRequest.httpRequest(httpService(req.auth.host, req.auth.port, req.auth.use_https), authRaw)
            val authResp = api.http().sendRequest(authReq).response()
            val authBody = authResp?.bodyToString() ?: ""
            val token = extractToken(authBody, req.auth.token_path)
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Could not extract token from auth response using path '${req.auth.token_path}'. Auth response: ${authBody.take(200)}"))

            val headerLine = req.inject_as.replace("{token}", token)
            val retryResp = sendTarget(headerLine)
            call.respond(HttpSendResponse(
                request  = req.request.request ?: headerLine,
                response = retryResp?.toString()
            ))
        }.onFailure { if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
    }

    // ── Repeater ──────────────────────────────────────────────────────────────

    post("/api/repeater") {
        val req = runCatching { call.receive<SendToToolRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
        }
        runCatching {
            val service = httpService(req.host, req.port, req.use_https)
            val httpReq = HttpRequest.httpRequest(service, normalizeRequest(req.request))
            api.repeater().sendToRepeater(httpReq, req.tab_name ?: "REST API")
            call.respond(MessageResponse("Sent to Repeater tab: ${req.tab_name ?: "REST API"}"))
        }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
    }

    // ── Intruder ──────────────────────────────────────────────────────────────

    post("/api/intruder") {
        val req = runCatching { call.receive<SendToToolRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
        }
        runCatching {
            val service = httpService(req.host, req.port, req.use_https)
            val httpReq = HttpRequest.httpRequest(service, normalizeRequest(req.request))
            api.intruder().sendToIntruder(httpReq, req.tab_name ?: "REST API")
            call.respond(MessageResponse("Sent to Intruder tab: ${req.tab_name ?: "REST API"}"))
        }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
    }

    // ── Comparer ──────────────────────────────────────────────────────────────

    post("/api/comparer") {
        val req = runCatching { call.receive<ComparerRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
        }
        runCatching {
            val byteArrays = req.items.map { burp.api.montoya.core.ByteArray.byteArray(it) }
            api.comparer().sendToComparer(*byteArrays.toTypedArray())
            call.respond(MessageResponse("Sent ${req.items.size} item(s) to Comparer"))
        }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
    }

    // ── Request Mutation ──────────────────────────────────────────────────────
    // Apply one or more transformations to a raw HTTP/1.1 request.
    // All mutation fields are optional - only provided fields are applied, in order:
    //   toggle_method → method → path → body → add_headers → update_headers → remove_headers
    //   → add_params → update_params → remove_params
    // Allowed param types: URL, BODY, COOKIE, XML, XML_ATTRIBUTE, MULTIPART_ATTRIBUTE, JSON
    // toggle_method and method are mutually exclusive; if both set, toggle_method runs first.

    post("/api/http/request/mutate") {
        val req = runCatching { call.receive<MutateRequestInput>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
        }
        if (req.request.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'request' is required"))
        val validParamTypes = burp.api.montoya.http.message.params.HttpParameterType.values().map { it.name }
        val badType = (req.add_params + req.remove_params + req.update_params)
            .firstOrNull { it.type.uppercase() !in validParamTypes }
        if (badType != null) return@post call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("Invalid param type '${badType.type}'. Allowed: ${validParamTypes.joinToString(", ")}")
        )
        runCatching {
            val service = req.service_host?.let { httpService(it, req.service_port, req.service_use_https) }
            var mutated = if (service != null) {
                HttpRequest.httpRequest(service, normalizeRequest(req.request))
            } else {
                HttpRequest.httpRequest(normalizeRequest(req.request))
            }
            if (req.toggle_method) mutated = mutated.withTransformationApplied(HttpTransformation.TOGGLE_METHOD)
            if (req.method != null) mutated = mutated.withMethod(req.method)
            if (req.path != null) mutated = mutated.withPath(req.path)
            if (req.body != null) mutated = mutated.withBody(req.body)
            for ((name, value) in req.add_headers) mutated = mutated.withAddedHeader(name, value)
            for ((name, value) in req.update_headers) mutated = mutated.withUpdatedHeader(name, value)
            for (name in req.remove_headers) mutated = mutated.withRemovedHeader(name)
            if (req.add_params.isNotEmpty()) mutated = mutated.withAddedParameters(req.add_params.map {
                HttpParameter.parameter(it.name, it.value, burp.api.montoya.http.message.params.HttpParameterType.valueOf(it.type.uppercase()))
            })
            if (req.update_params.isNotEmpty()) mutated = mutated.withUpdatedParameters(req.update_params.map {
                HttpParameter.parameter(it.name, it.value, burp.api.montoya.http.message.params.HttpParameterType.valueOf(it.type.uppercase()))
            })
            if (req.remove_params.isNotEmpty()) mutated = mutated.withRemovedParameters(req.remove_params.map {
                HttpParameter.parameter(it.name, it.value, burp.api.montoya.http.message.params.HttpParameterType.valueOf(it.type.uppercase()))
            })
            call.respond(StringResult(mutated.toString()))
        }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Mutation failed")) }
    }

    // ── Fuzz ─────────────────────────────────────────────────────────────────

    post("/api/http/fuzz") {
        val req = runCatching { call.receive<FuzzRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
        }
        if (req.host.isBlank())          return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'host' is required"))
        if (req.path_template.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'path_template' is required"))
        if (req.wordlist.isEmpty())      return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'wordlist' must not be empty"))
        if (req.wordlist.size > 500)     return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'wordlist' exceeds maximum of 500"))
        val concurrency = req.concurrency.coerceIn(1, 20)

        val results = mutableListOf<Pair<String, FuzzResult>>()
        val chunks  = req.wordlist.chunked(concurrency)
        for (chunk in chunks) {
            val chunkResults = chunk.map { word ->
                val path    = req.path_template.replace("{word}", word)
                val rawReq  = buildStructuredRequest(req.host, req.method, path, req.headers, req.body)
                val service = httpService(req.host, req.port, req.https)
                runCatching {
                    val opts = if (req.timeout_ms != null) RequestOptions.requestOptions().withResponseTimeout(req.timeout_ms) else null
                    val rr   = if (opts != null) api.http().sendRequest(HttpRequest.httpRequest(service, rawReq), opts)
                               else api.http().sendRequest(HttpRequest.httpRequest(service, rawReq))
                    val status  = rr?.response()?.statusCode()?.toInt() ?: 0
                    val body    = rr?.response()?.body()?.toString() ?: ""
                    val notes   = analyzeEntry(req.method, path, status, rawReq, body).ifBlank { null }
                    val snippet = if (req.include_body_snippet) body.take(300).ifBlank { null } else null
                    // Pair body with result so we can filter on full content even without include_body_snippet
                    Pair(body, FuzzResult(word = word, path = path, status = status, length = body.length,
                        ai_notes = notes, body_snippet = snippet))
                }.getOrElse { Pair("", FuzzResult(word = word, path = path, status = 0, length = 0, ai_notes = "error: ${it.message}")) }
            }
            results.addAll(chunkResults)
        }

        val filtered = results
            .let { if (req.filter_status.isEmpty()) it else it.filter { (_, r) -> r.status in req.filter_status } }
            .let { if (req.exclude_body_size == null) it else it.filter { (_, r) -> r.length != req.exclude_body_size } }
            .let { if (req.exclude_body_contains == null) it else it.filter { (b, _) -> !b.contains(req.exclude_body_contains) } }
            .map { (_, r) -> r }
        call.respond(filtered.sortedWith(compareBy({ it.status }, { it.path })))
    }

    // ── JWT decode ────────────────────────────────────────────────────────────

    post("/api/http/jwt/decode") {
        val req = runCatching { call.receive<JwtDecodeRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
        }
        val parts = req.token.trim().split(".")
        if (parts.size < 2) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Not a valid JWT: expected at least 2 dot-separated parts"))

        fun b64Decode(s: String): String {
            val padded = s.replace('-', '+').replace('_', '/')
                .let { it + "=".repeat((4 - it.length % 4) % 4) }
            return String(java.util.Base64.getDecoder().decode(padded), Charsets.UTF_8)
        }

        runCatching {
            val header  = Json.parseToJsonElement(b64Decode(parts[0])).jsonObject
            val payload = Json.parseToJsonElement(b64Decode(parts[1])).jsonObject
            val issues  = mutableListOf<String>()

            val alg = header["alg"]?.jsonPrimitive?.contentOrNull
            if (alg == null || alg.equals("none", ignoreCase = true)) issues += "CRITICAL: alg=none - signature not verified"
            if (alg != null && !alg.startsWith("HS") && !alg.startsWith("RS") && !alg.startsWith("ES"))
                issues += "Unusual algorithm: $alg"

            val exp = payload["exp"]?.jsonPrimitive?.longOrNull
            if (exp == null) issues += "No expiry (exp) claim - token never expires"
            else if (exp < System.currentTimeMillis() / 1000) issues += "Token is EXPIRED (exp=$exp)"

            val iat = payload["iat"]?.jsonPrimitive?.longOrNull
            if (iat != null && iat > System.currentTimeMillis() / 1000 + 60) issues += "Issued in the future (iat=$iat)"

            if (payload["is_staff"]?.jsonPrimitive?.booleanOrNull == true) issues += "is_staff=true in payload"
            if (payload["is_superuser"]?.jsonPrimitive?.booleanOrNull == true) issues += "is_superuser=true in payload"
            if (payload["role"]?.jsonPrimitive?.contentOrNull?.lowercase() in listOf("admin","superuser","root"))
                issues += "Privileged role in payload: ${payload["role"]}"

            if (parts.size < 3 || parts[2].isEmpty()) issues += "No signature - token is unsigned"

            call.respond(JwtDecodeResponse(header = header, payload = payload, issues = issues))
        }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to decode JWT: ${it.message}")) }
    }

    // ── Auth diff ─────────────────────────────────────────────────────────────

    post("/api/http/auth-diff") {
        val req = runCatching { call.receive<AuthDiffRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
        }
        if (req.host.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'host' is required"))
        if (req.auth_header.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'auth_header' is required"))

        val service = httpService(req.host, req.port, req.https)

        fun sendSide(authHeader: String?): AuthDiffSide {
            val extraHeaders = (req.headers ?: emptyMap()).toMutableMap()
            if (authHeader != null) {
                val colonIdx = authHeader.indexOf(':')
                if (colonIdx > 0) extraHeaders[authHeader.substring(0, colonIdx).trim()] =
                    authHeader.substring(colonIdx + 1).trim()
            }
            val rawReq = buildStructuredRequest(req.host, req.method, req.path, extraHeaders, req.body)
            val rr = runCatching { api.http().sendRequest(HttpRequest.httpRequest(service, rawReq)) }.getOrNull()
            val status = rr?.response()?.statusCode()?.toInt() ?: 0
            val body   = rr?.response()?.body()?.toString() ?: ""
            val notes  = analyzeEntry(req.method, req.path, status, rawReq, body).ifBlank { null }
            return AuthDiffSide(status = status, body_length = body.length, ai_notes = notes)
        }

        val authed      = sendSide(req.auth_header)
        val unauthed    = sendSide(null)
        val secondUser  = req.second_auth_header?.let { sendSide(it) }

        val sameStatus = authed.status == unauthed.status
        val sameLength = authed.body_length == unauthed.body_length

        val verdict = when {
            // Three-way: second user gets same response as high-priv = privilege escalation
            secondUser != null && secondUser.status == authed.status && secondUser.body_length == authed.body_length ->
                "PRIVILEGE ESCALATION: second user gets identical response to authenticated user"
            secondUser != null && secondUser.status == authed.status ->
                "POSSIBLE PRIVILEGE ESCALATION: second user gets same status (${authed.status}) - check body content"
            secondUser != null && secondUser.status !in listOf(401, 403) ->
                "SUSPICIOUS: second user gets ${secondUser.status} (not 401/403) - investigate"
            sameStatus && sameLength ->
                "POSSIBLE AUTH BYPASS: identical status and body length with and without auth"
            sameStatus ->
                "Same status code (${authed.status}) - check body content manually"
            unauthed.status in listOf(401, 403) ->
                "Auth enforced: unauthenticated gets ${unauthed.status}"
            else ->
                "Differs: auth=${authed.status}, unauth=${unauthed.status}"
        }

        val isPoc = verdict.startsWith("POSSIBLE AUTH BYPASS") ||
                    verdict.startsWith("PRIVILEGE ESCALATION") ||
                    verdict.startsWith("POSSIBLE PRIVILEGE ESCALATION") ||
                    verdict.startsWith("SUSPICIOUS")

        // When a confirmed/suspected finding, suppress the generic API-level log entry
        // and emit one labeled PoC entry per side so the log reads like an Autorize report.
        if (isPoc && activityLog != null) {
            call.attributes.put(skipLogAttr, true)
            val sessionId = runCatching {
                call.receiveText().let { Json.parseToJsonElement(it).jsonObject["session_id"]?.jsonPrimitive?.content }
            }.getOrNull()
            val total = if (secondUser != null) 3 else 2
            val pocType = if (verdict.startsWith("PRIVILEGE ESCALATION") || verdict.startsWith("POSSIBLE PRIVILEGE")) "Priv Escalation PoC" else "Auth Bypass PoC"

            activityLog.log(req.method, req.path, authed.status, 0, "", "", "",
                sessionId = sessionId,
                forcedNotes = "$pocType [1/$total] Authenticated - ${authed.status} (${authed.body_length}B)${authed.ai_notes?.let { " - $it" } ?: ""}"
            )
            secondUser?.let {
                activityLog.log(req.method, req.path, it.status, 0, "", "", "",
                    sessionId = sessionId,
                    forcedNotes = "$pocType [2/$total] Low-Priv User - ${it.status} (${it.body_length}B)${it.ai_notes?.let { n -> " - $n" } ?: ""} | $verdict"
                )
            }
            val unauthIdx = if (secondUser != null) 3 else 2
            activityLog.log(req.method, req.path, unauthed.status, 0, "", "", "",
                sessionId = sessionId,
                forcedNotes = "$pocType [$unauthIdx/$total] Unauthenticated - ${unauthed.status} (${unauthed.body_length}B) | $verdict"
            )
        }

        call.respond(AuthDiffResponse(
            authenticated   = authed,
            unauthenticated = unauthed,
            second_user     = secondUser,
            same_status     = sameStatus,
            same_length     = sameLength,
            verdict         = verdict
        ))
    }

    // ── Rate test ─────────────────────────────────────────────────────────────

    post("/api/http/rate-test") {
        val req = runCatching { call.receive<RateTestRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
        }
        if (req.host.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'host' is required"))
        if (req.count !in 1..200) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'count' must be 1-200"))

        val service     = httpService(req.host, req.port, req.https)
        val rawReq      = buildStructuredRequest(req.host, req.method, req.path, req.headers, req.body)
        val concurrency = req.concurrency.coerceIn(1, 20)
        val statuses    = mutableListOf<Int>()
        val times       = mutableListOf<Long>()
        var firstBlockAt: Int? = null
        var reqNum = 0
        val chunks = (0 until req.count).toList().chunked(concurrency)

        for (chunk in chunks) {
            chunk.forEach { _ ->
                reqNum++
                val t0 = System.currentTimeMillis()
                val rr = runCatching { api.http().sendRequest(HttpRequest.httpRequest(service, rawReq)) }.getOrNull()
                times += System.currentTimeMillis() - t0
                val status = rr?.response()?.statusCode()?.toInt() ?: 0
                statuses += status
                if (firstBlockAt == null && status in listOf(429, 503, 403))
                    firstBlockAt = reqNum
            }
        }

        val dist    = statuses.groupingBy { it.toString() }.eachCount()
        val avgMs   = if (times.isEmpty()) 0L else times.average().toLong()
        val blocked = statuses.count { it in listOf(429, 503) }
        val verdict = when {
            blocked == 0             -> "No rate limiting detected after ${req.count} requests"
            firstBlockAt != null     -> "Rate limited at request #$firstBlockAt (${blocked}/${req.count} blocked)"
            else                     -> "$blocked/${req.count} requests blocked"
        }
        call.respond(RateTestResponse(total = statuses.size, status_distribution = dist,
            first_block_at = firstBlockAt, avg_ms = avgMs, verdict = verdict))
    }

    // Apply one or more transformations to a raw HTTP response.
    // Mutations applied in order: status_code → body → add_headers → update_headers → remove_headers
    // status_code must be 100–599.

    post("/api/http/response/mutate") {
        val req = runCatching { call.receive<MutateResponseInput>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
        }
        if (req.response.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'response' is required"))
        if (req.status_code != null && req.status_code !in 100..599) return@post call.respond(
            HttpStatusCode.BadRequest, ErrorResponse("'status_code' must be 100–599, got ${req.status_code}")
        )
        runCatching {
            var mutated = HttpResponse.httpResponse(req.response)
            if (req.status_code != null) mutated = mutated.withStatusCode(req.status_code.toShort())
            if (req.body != null) mutated = mutated.withBody(req.body)
            for ((name, value) in req.add_headers) mutated = mutated.withAddedHeader(name, value)
            for ((name, value) in req.update_headers) mutated = mutated.withUpdatedHeader(name, value)
            for (name in req.remove_headers) mutated = mutated.withRemovedHeader(name)
            call.respond(StringResult(mutated.toString()))
        }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Mutation failed")) }
    }
}

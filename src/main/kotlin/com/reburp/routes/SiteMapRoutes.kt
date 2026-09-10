package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.sitemap.SiteMapFilter
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.regex.Pattern

fun Routing.siteMapRoutes(api: MontoyaApi) {

    route("/api/sitemap") {

        // List site map entries.
        // Query params: offset, limit, include_body, prefix (URL prefix for fast filtering)
        get("") {
            val p = call.request.queryParameters
            val offset      = p["offset"]?.toIntOrNull() ?: 0
            val limit       = (p["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 1000)
            val includeBody = p["include_body"]?.toBooleanStrictOrNull() ?: false
            val prefix      = p["prefix"]
            val items = if (prefix != null) {
                api.siteMap().requestResponses(SiteMapFilter.prefixFilter(prefix))
            } else {
                api.siteMap().requestResponses()
            }
            call.respond(items.drop(offset).take(limit).map { it.toDto(includeBody) })
        }

        // Regex search across site map URLs.
        // Tip: for prefix-based filtering, use GET /api/sitemap?prefix=https://example.com (faster)
        get("/search") {
            val p = call.request.queryParameters
            val regex       = p["regex"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("'regex' parameter required. For prefix matching use GET /api/sitemap?prefix=https://example.com instead.")
            )
            val offset      = p["offset"]?.toIntOrNull() ?: 0
            val limit       = (p["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 1000)
            val includeBody = p["include_body"]?.toBooleanStrictOrNull() ?: false
            runCatching {
                val pattern = Pattern.compile(regex)
                val filter = SiteMapFilter { node -> pattern.matcher(node.url()).find() }
                val items = api.siteMap().requestResponses(filter)
                    .drop(offset).take(limit).map { it.toDto(includeBody) }
                call.respond(items)
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid regex: ${it.message}")) }
        }

        // Add a request (and optional response) to the site map.
        post("/add") {
            val req = runCatching { call.receive<SiteMapAddRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.host.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'host' is required"))
            if (req.request.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'request' is required (raw HTTP request text)"))
            runCatching {
                val service = httpService(req.host, req.port, req.use_https)
                val httpReq = HttpRequest.httpRequest(service, normalizeRequest(req.request))
                val httpRes = req.response?.let { HttpResponse.httpResponse(it) }
                    ?: HttpResponse.httpResponse("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
                api.siteMap().add(HttpRequestResponse.httpRequestResponse(httpReq, httpRes))
                call.respond(MessageResponse("Added to site map: ${httpReq.url()}"))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error adding to site map")) }
        }

        // List scanner issues from the site map.
        // Query params: prefix (URL prefix filter), offset, limit
        get("/issues") {
            val prefix = call.request.queryParameters["prefix"]
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit  = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)
            runCatching {
                val issues = if (prefix != null) {
                    api.siteMap().issues(SiteMapFilter.prefixFilter(prefix))
                } else {
                    api.siteMap().issues()
                }
                val result = issues.drop(offset).take(limit).map { issue ->
                    SiteMapIssueDto(
                        name        = issue.name(),
                        detail      = issue.detail(),
                        remediation = issue.remediation(),
                        base_url    = issue.baseUrl(),
                        severity    = runCatching { issue.severity().name }.getOrElse { "UNKNOWN" },
                        confidence  = runCatching { issue.confidence().name }.getOrElse { "UNKNOWN" },
                        host        = runCatching { issue.httpService().host() }.getOrNull(),
                        port        = runCatching { issue.httpService().port() }.getOrNull()
                    )
                }
                call.respond(result)
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error listing issues")) }
        }
    }

    route("/api/scope") {

        get("") {
            val url = call.request.queryParameters["url"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("'url' parameter required"))
            call.respond(ScopeResult(url = url, in_scope = api.scope().isInScope(url)))
        }

        post("/include") {
            val req = runCatching { call.receive<ScopeRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            api.scope().includeInScope(req.url)
            call.respond(MessageResponse("Added to scope: ${req.url}"))
        }

        post("/exclude") {
            val req = runCatching { call.receive<ScopeRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            api.scope().excludeFromScope(req.url)
            call.respond(MessageResponse("Excluded from scope: ${req.url}"))
        }
    }
}

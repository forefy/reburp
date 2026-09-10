package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.HighlightColor
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.regex.Pattern
import kotlinx.serialization.json.*

fun Routing.proxyRoutes(api: MontoyaApi) {
    route("/api/proxy") {

        // ── HTTP History ──────────────────────────────────────────────────────
        // Query params:
        //   offset, limit, include_body - pagination / body inclusion
        //   host         - filter by exact hostname
        //   method       - filter by HTTP method (GET, POST, …)
        //   mime_type    - filter by MIME type enum name (HTML, JSON, SCRIPT, IMAGE_JPEG, …)
        //   status_min   - minimum HTTP status code (inclusive)
        //   status_max   - maximum HTTP status code (inclusive)
        //   edited_only  - true: only items modified by proxy match-replace rules
        //   has_response - true: only items that received a response; false: only those that did not
        //   scope_only   - true: only in-scope items
        //   listener_port - filter by the proxy listener port the request came through

        get("/history") {
            val p = call.request.queryParameters
            val offset      = p["offset"]?.toIntOrNull() ?: 0
            val limit       = (p["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 1000)
            val includeBody = p["include_body"]?.toBooleanStrictOrNull() ?: false
            val host        = p["host"]
            val method      = p["method"]?.uppercase()
            val mimeType    = p["mime_type"]?.uppercase()
            val statusMin   = p["status_min"]?.toIntOrNull()
            val statusMax   = p["status_max"]?.toIntOrNull()
            val editedOnly  = p["edited_only"]?.toBooleanStrictOrNull() ?: false
            val hasResponse = p["has_response"]?.toBooleanStrictOrNull()
            val scopeOnly   = p["scope_only"]?.toBooleanStrictOrNull() ?: false
            val listenerPort = p["listener_port"]?.toIntOrNull()

            var items = api.proxy().history()
            if (host != null)        items = items.filter { runCatching { it.host() }.getOrElse { "" }.equals(host, ignoreCase = true) }
            if (method != null)      items = items.filter { runCatching { it.method() }.getOrElse { "" }.uppercase() == method }
            if (mimeType != null)    items = items.filter { runCatching { it.mimeType().name }.getOrNull()?.uppercase() == mimeType }
            if (statusMin != null)   items = items.filter { (it.response()?.statusCode()?.toInt() ?: 0) >= statusMin }
            if (statusMax != null)   items = items.filter { (it.response()?.statusCode()?.toInt() ?: 999) <= statusMax }
            if (editedOnly)          items = items.filter { runCatching { it.edited() }.getOrElse { false } }
            if (hasResponse != null) items = items.filter { (it.response() != null) == hasResponse }
            if (scopeOnly)           items = items.filter { runCatching { api.scope().isInScope(it.url()) }.getOrElse { false } }
            if (listenerPort != null) items = items.filter { runCatching { it.listenerPort() == listenerPort }.getOrElse { false } }

            call.respond(items.drop(offset).take(limit).map { it.toProxyEntryDto(includeBody) })
        }

        // Full-text regex search across request + response bytes
        get("/history/search") {            val regex = call.request.queryParameters["regex"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("'regex' parameter required"))
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit  = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)
            val includeBody = call.request.queryParameters["include_body"]?.toBooleanStrictOrNull() ?: false
            runCatching {
                val pattern = Pattern.compile(regex)
                val items = api.proxy().history { it.contains(pattern) }
                    .drop(offset).take(limit).map { it.toProxyEntryDto(includeBody) }
                call.respond(items)
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid regex: ${it.message}")) }
        }

        // ── Annotate ──────────────────────────────────────────────────────────

        // Get single history item by index
        get("/history/{index}") {
            val index = call.parameters["index"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("'index' must be an integer"))
            val includeBody = call.request.queryParameters["include_body"]?.toBooleanStrictOrNull() ?: true
            val history = api.proxy().history()
            if (index < 0 || index >= history.size)
                return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Index $index out of range (history size: ${history.size})"))
            call.respond(history[index].toProxyEntryDto(includeBody))
        }

        patch("/history/annotate") {
            val req = runCatching { call.receive<AnnotateRequest>() }.getOrElse {
                return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            val validColors = HighlightColor.values().map { it.name }
            if (req.highlight != null && req.highlight.uppercase() !in validColors) {
                return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'highlight'. Allowed values: ${validColors.joinToString(", ")}")
                )
            }
            runCatching {
                val pattern = Pattern.compile(req.regex)
                val highlightColor = req.highlight?.let { runCatching { HighlightColor.valueOf(it.uppercase()) }.getOrNull() }
                var count = 0
                for (item in api.proxy().history { it.contains(pattern) }) {
                    if (req.scope_only) {
                        val url = runCatching { item.url() }.getOrElse { item.request()?.url() } ?: continue
                        if (!api.scope().isInScope(url)) continue
                    }
                    if (req.note.isNotBlank()) item.annotations().setNotes(req.note)
                    if (highlightColor != null) item.annotations().setHighlightColor(highlightColor)
                    if (++count >= req.limit) break
                }
                call.respond(MessageResponse("Annotated $count item(s)"))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Response Body Search ──────────────────────────────────────────────
        // Returns matched URLs with extracted snippets of surrounding context.
        // Query params: regex (required), scope_only, offset, limit,
        //               max_snippets (1–10, default 3), context_chars (10–200, default 60)

        get("/history/response-search") {
            val regex = call.request.queryParameters["regex"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("'regex' parameter required"))
            val scopeOnly   = call.request.queryParameters["scope_only"]?.toBooleanStrictOrNull() ?: false
            val offset      = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit       = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)
            val maxSnippets = (call.request.queryParameters["max_snippets"]?.toIntOrNull() ?: 3).coerceIn(1, 10)
            val contextLen  = (call.request.queryParameters["context_chars"]?.toIntOrNull() ?: 60).coerceIn(10, 200)
            runCatching {
                val pattern = Pattern.compile(regex)
                val matches = mutableListOf<ResponseSearchMatchDto>()
                for (item in api.proxy().history()) {
                    val url: String? = try { item.url() } catch (_: Throwable) { item.request()?.url() }
                    if (url == null) continue
                    val inScope = try { api.scope().isInScope(url) } catch (_: Throwable) { false }
                    if (scopeOnly && !inScope) continue
                    val body = item.response()?.bodyToString() ?: continue
                    if (body.isBlank()) continue
                    val m = pattern.matcher(body)
                    val snippets = mutableListOf<String>()
                    var count = 0
                    while (m.find()) {
                        count++
                        if (snippets.size < maxSnippets) {
                            val s = maxOf(0, m.start() - contextLen)
                            val e = minOf(body.length, m.end() + contextLen)
                            snippets.add("...${body.substring(s, e)}...")
                        }
                    }
                    if (count > 0) matches.add(ResponseSearchMatchDto(url = url, match_count = count, snippets = snippets))
                }
                call.respond(matches.drop(offset).take(limit))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid regex: ${it.message}")) }
        }

        // ── WebSocket History ─────────────────────────────────────────────────

        get("/websocket/history") {
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit  = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)
            call.respond(api.proxy().webSocketHistory().drop(offset).take(limit).map { it.toDto() })
        }

        get("/websocket/history/search") {
            val regex = call.request.queryParameters["regex"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("'regex' parameter required"))
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit  = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)
            runCatching {
                val pattern = Pattern.compile(regex)
                val items = api.proxy().webSocketHistory { it.contains(pattern) }
                    .drop(offset).take(limit).map { it.toDto() }
                call.respond(items)
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid regex: ${it.message}")) }
        }

        // ── Intercept ─────────────────────────────────────────────────────────

        get("/intercept") {
            // Montoya API does not expose current intercept state; use PUT to toggle
            call.respond(mapOf("note" to "Use PUT /api/proxy/intercept to toggle. Current state is not queryable via Montoya API."))
        }

        put("/intercept") {
            val req = runCatching { call.receive<InterceptRequest>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                if (req.enabled) api.proxy().enableIntercept() else api.proxy().disableIntercept()
                call.respond(MessageResponse("Intercept ${if (req.enabled) "enabled" else "disabled"}"))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Intercept Rules ───────────────────────────────────────────────────

        get("/intercept/rules") {
            runCatching {
                val json = api.burpSuite().exportProjectOptionsAsJson("proxy")
                val root = Json.parseToJsonElement(json).jsonObject
                val proxy = root["proxy"]?.jsonObject ?: root
                val clientRules = proxy["intercept_client"]?.jsonObject?.get("rules")?.jsonArray ?: JsonArray(emptyList())
                val serverRules = proxy["intercept_server"]?.jsonObject?.get("rules")?.jsonArray ?: JsonArray(emptyList())
                call.respond(mapOf("client_rules" to clientRules, "server_rules" to serverRules))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        post("/intercept/rules/client") {
            val req = runCatching { call.receive<InterceptRuleRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad body"))
            }
            runCatching {
                val json = api.burpSuite().exportProjectOptionsAsJson("proxy")
                val root = Json.parseToJsonElement(json).jsonObject.toMutableMap()
                val proxy = root["proxy"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
                val clientSection = proxy["intercept_client"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
                val rules = clientSection["rules"]?.jsonArray?.toMutableList() ?: mutableListOf()
                rules.add(buildJsonObject {
                    put("enabled", req.enabled)
                    put("match_type", req.match_type)
                    put("match_relationship", req.match_relationship)
                    put("match_condition", req.match_condition)
                })
                clientSection["rules"] = JsonArray(rules)
                proxy["intercept_client"] = JsonObject(clientSection)
                root["proxy"] = JsonObject(proxy)
                api.burpSuite().importProjectOptionsFromJson(JsonObject(root).toString())
                call.respond(MessageResponse("Client intercept rule added"))
            }.onFailure { if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        delete("/intercept/rules/client/{index}") {
            val index = call.parameters["index"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("'index' must be integer"))
            runCatching {
                val json = api.burpSuite().exportProjectOptionsAsJson("proxy")
                val root = Json.parseToJsonElement(json).jsonObject.toMutableMap()
                val proxy = root["proxy"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
                val clientSection = proxy["intercept_client"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
                val rules = clientSection["rules"]?.jsonArray?.toMutableList() ?: mutableListOf()
                if (index < 0 || index >= rules.size)
                    return@runCatching call.respond(HttpStatusCode.NotFound, ErrorResponse("Index $index out of range"))
                rules.removeAt(index)
                clientSection["rules"] = JsonArray(rules)
                proxy["intercept_client"] = JsonObject(clientSection)
                root["proxy"] = JsonObject(proxy)
                api.burpSuite().importProjectOptionsFromJson(JsonObject(root).toString())
                call.respond(MessageResponse("Client intercept rule at index $index deleted"))
            }.onFailure { if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }
    }
}

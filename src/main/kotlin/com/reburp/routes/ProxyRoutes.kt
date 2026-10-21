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
            val maxBody     = p["max_body"]?.toIntOrNull() ?: DEFAULT_MAX_BODY
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
            if (host != null)        items = items.filter { e -> runCatching { e.host() }.recoverCatching { e.httpService().host() }.getOrElse { "" }.equals(host, ignoreCase = true) }
            if (method != null)      items = items.filter { e -> runCatching { e.method() }.recoverCatching { e.request().method() }.getOrElse { "" }.uppercase() == method }
            if (mimeType != null)    items = items.filter { runCatching { it.mimeType().name }.getOrNull()?.uppercase() == mimeType }
            if (statusMin != null)   items = items.filter { (it.response()?.statusCode()?.toInt() ?: 0) >= statusMin }
            if (statusMax != null)   items = items.filter { (it.response()?.statusCode()?.toInt() ?: 999) <= statusMax }
            if (editedOnly)          items = items.filter { runCatching { it.edited() }.getOrElse { false } }
            if (hasResponse != null) items = items.filter { (it.response() != null) == hasResponse }
            if (scopeOnly)           items = items.filter { e -> runCatching { api.scope().isInScope(e.url()) }.recoverCatching { api.scope().isInScope(e.request().url()) }.getOrElse { false } }
            if (listenerPort != null) items = items.filter { runCatching { it.listenerPort() == listenerPort }.getOrElse { false } }

            call.respond(items.drop(offset).take(limit).map { it.toProxyEntryDto(includeBody, maxBody) })
        }

        // Full-text regex search across request + response bytes
        get("/history/search") {            val regex = call.request.queryParameters["regex"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("'regex' parameter required"))
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit  = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)
            val includeBody = call.request.queryParameters["include_body"]?.toBooleanStrictOrNull() ?: false
            val maxBody = call.request.queryParameters["max_body"]?.toIntOrNull() ?: DEFAULT_MAX_BODY
            runCatching {
                val pattern = Pattern.compile(regex)
                val items = api.proxy().history { it.contains(pattern) }
                    .drop(offset).take(limit).map { it.toProxyEntryDto(includeBody, maxBody) }
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
                call.respond(
                    mapOf(
                        "client_rules" to interceptRules(api, CLIENT_INTERCEPT),
                        "server_rules" to interceptRules(api, SERVER_INTERCEPT)
                    )
                )
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        post("/intercept/rules/client") {
            val req = runCatching { call.receive<InterceptRuleRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad body"))
            }
            runCatching {
                val rules = interceptRules(api, CLIENT_INTERCEPT).toMutableList()
                val before = rules.size
                rules.add(buildJsonObject {
                    // boolean_operator is not optional: Burp drops any rule that omits it.
                    put("boolean_operator", req.boolean_operator)
                    put("enabled", req.enabled)
                    put("match_type", req.match_type)
                    put("match_relationship", req.match_relationship)
                    put("match_condition", req.match_condition)
                })
                applyInterceptRules(api, CLIENT_INTERCEPT, JsonArray(rules))

                val after = interceptRules(api, CLIENT_INTERCEPT).size
                if (after == before) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            "Burp discarded the rule (still $before). Check match_type and " +
                                "match_relationship: Burp accepts only its own vocabulary, such as " +
                                "url, http_method, file_extension, content_type_header, status_code, " +
                                "request and matches, does_not_match, is_in_target_scope."
                        )
                    )
                } else {
                    call.respond(MessageResponse("Client intercept rule added ($after total)"))
                }
            }.onFailure { if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        delete("/intercept/rules/client/{index}") {
            val index = call.parameters["index"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("'index' must be integer"))
            runCatching {
                val rules = interceptRules(api, CLIENT_INTERCEPT).toMutableList()
                val before = rules.size
                if (index < 0 || index >= before)
                    return@runCatching call.respond(HttpStatusCode.NotFound, ErrorResponse("Index $index out of range ($before rules)"))
                rules.removeAt(index)
                applyInterceptRules(api, CLIENT_INTERCEPT, JsonArray(rules))

                val after = interceptRules(api, CLIENT_INTERCEPT).size
                if (after == before) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Burp did not persist the deletion (still $before rules)."))
                } else {
                    call.respond(MessageResponse("Client intercept rule at index $index deleted ($after remaining)"))
                }
            }.onFailure { if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }
    }
}

// ── Intercept rule config access ──────────────────────────────────────────────
//
// [Montoya Config] - intercept rules have no Montoya API, so they are read and written
// through the proxy section of the project config.
//
// Burp names these sections "intercept_client_requests" and "intercept_server_responses".
// Earlier code looked for "intercept_client"/"intercept_server", which never matched: reads
// always returned empty arrays, and writes created stray keys Burp ignored while the endpoint
// answered 200. Imports carry only the touched section because Burp merges on import.

private const val CLIENT_INTERCEPT = "intercept_client_requests"
private const val SERVER_INTERCEPT = "intercept_server_responses"

private fun interceptSection(api: MontoyaApi, section: String): JsonObject? = runCatching {
    val root = Json.parseToJsonElement(api.burpSuite().exportProjectOptionsAsJson("proxy")).jsonObject
    (root["proxy"]?.jsonObject ?: root)[section]?.jsonObject
}.getOrNull()

private fun interceptRules(api: MontoyaApi, section: String): JsonArray =
    runCatching { interceptSection(api, section)?.get("rules")?.jsonArray }.getOrNull() ?: JsonArray(emptyList())

private fun applyInterceptRules(api: MontoyaApi, section: String, rules: JsonArray) {
    // Preserve the section's sibling settings (do_intercept and the auto-fix toggles).
    val updated = (interceptSection(api, section)?.toMutableMap() ?: mutableMapOf())
    updated["rules"] = rules
    val doc = buildJsonObject { put("proxy", buildJsonObject { put(section, JsonObject(updated)) }) }
    api.burpSuite().importProjectOptionsFromJson(doc.toString())
}

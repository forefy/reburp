package com.reburp.routes

import burp.api.montoya.MontoyaApi
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

// [Montoya API] - api.scope()
fun Routing.scopeRoutes(api: MontoyaApi) {

    // GET /api/scope (with ?url) is the in-scope check in siteMapRoutes, which registers
    // first and so always answered it; a second handler here for the rules was dead code.
    get("/api/scope/rules") { respondScopeRules(api, call) }

    route("/api/scope") {

        // Check whether a URL is in scope
        get("/check") {
            val url = call.request.queryParameters["url"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Query parameter 'url' is required (e.g. ?url=https://example.com)")
                )
            val inScope = runCatching { api.scope().isInScope(url) }.getOrElse {
                return@get call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Scope check failed: ${it.message}")
                )
            }
            call.respond(ScopeCheckResult(url = url, in_scope = inScope))
        }

        // Add a URL to scope
        post("/include") {
            val req = runCatching { call.receive<ScopeUrlRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid request body"))
            }
            runCatching { api.scope().includeInScope(req.url) }.onFailure {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Failed to include in scope: ${it.message}")
                )
            }
            call.respond(MessageResponse("URL included in scope: ${req.url}"))
        }

        // Remove a URL from scope
        post("/exclude") {
            val req = runCatching { call.receive<ScopeUrlRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid request body"))
            }
            runCatching { api.scope().excludeFromScope(req.url) }.onFailure {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Failed to exclude from scope: ${it.message}")
                )
            }
            call.respond(MessageResponse("URL excluded from scope: ${req.url}"))
        }
    }
}

// [Montoya Config] - scope rules are read from the target section of the project config.
private suspend fun respondScopeRules(api: MontoyaApi, call: ApplicationCall) {
    runCatching {
        val json = api.burpSuite().exportProjectOptionsAsJson("target")
        val root = Json.parseToJsonElement(json).jsonObject
        val target = root["target"]?.jsonObject ?: root
        val scope = target["scope"]?.jsonObject

        fun parseRules(arr: JsonArray?): List<Map<String, String?>> =
            arr?.mapNotNull { it.jsonObject }?.map { rule ->
                mapOf(
                    "enabled"  to rule["enabled"]?.jsonPrimitive?.content,
                    "protocol" to (rule["protocol"]?.jsonPrimitive?.contentOrNull ?: rule["scheme"]?.jsonPrimitive?.contentOrNull),
                    "host"     to rule["host"]?.jsonPrimitive?.contentOrNull,
                    "file"     to rule["file"]?.jsonPrimitive?.contentOrNull,
                    "port"     to rule["port"]?.jsonPrimitive?.contentOrNull
                )
            } ?: emptyList()

        call.respond(
            mapOf(
                "include" to parseRules(scope?.get("include")?.jsonArray),
                "exclude" to parseRules(scope?.get("exclude")?.jsonArray)
            )
        )
    }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error reading scope")) }
}

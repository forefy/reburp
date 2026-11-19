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

    // Every /api/scope route lives here. siteMapRoutes used to register GET, POST and DELETE
    // /api/scope plus /include and /exclude as well; it registers first, so its copies, which
    // had no error handling, answered and the ones here never ran.
    get("/api/scope/rules") { respondScopeRules(api, call) }

    route("/api/scope") {

        // Check whether a URL is in scope: /check is the documented path, ?url on the base path
        // the older one.
        get("/check") { respondInScope(api, call) }
        get("") { respondInScope(api, call) }

        post("/include") { changeScope(api, call, include = true) }
        post("") { changeScope(api, call, include = true) }

        // Montoya cannot delete scope rules: excluding adds an exclude rule (and drops a
        // matching include).
        post("/exclude") { changeScope(api, call, include = false) }
        delete("") { changeScope(api, call, include = false) }
    }
}

private suspend fun respondInScope(api: MontoyaApi, call: ApplicationCall) {
    val url = call.request.queryParameters["url"]
        ?: return call.respond(HttpStatusCode.BadRequest,
            ErrorResponse("Query parameter 'url' is required (e.g. ?url=https://example.com)"))
    val inScope = runCatching { api.scope().isInScope(url) }.getOrElse {
        return call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Scope check failed: ${it.message}"))
    }
    call.respond(ScopeCheckResult(url = url, in_scope = inScope))
}

private suspend fun changeScope(api: MontoyaApi, call: ApplicationCall, include: Boolean) {
    val req = runCatching { call.receive<ScopeUrlRequest>() }.getOrElse {
        return call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid request body"))
    }
    val verb = if (include) "include in" else "exclude from"
    runCatching { if (include) api.scope().includeInScope(req.url) else api.scope().excludeFromScope(req.url) }
        .onFailure { return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to $verb scope: ${it.message}")) }
    call.respond(MessageResponse("URL ${if (include) "included in" else "excluded from"} scope: ${req.url}"))
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

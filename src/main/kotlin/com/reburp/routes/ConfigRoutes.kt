package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.burpsuite.TaskExecutionEngine.TaskExecutionEngineState
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

fun Routing.configRoutes(api: MontoyaApi) {
    route("/api/config") {

        // ── Project options ───────────────────────────────────────────────────

        get("/project") {
            val paths = configPaths(call.request.queryParameters)
            val section = paths.joinToString(", ")
            runCatching {
                val json = if (paths.isNotEmpty()) api.burpSuite().exportProjectOptionsAsJson(*paths.toTypedArray())
                           else api.burpSuite().exportProjectOptionsAsJson()
                val missing = missingConfigPaths(json, paths)
                if (missing.isNotEmpty()) return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                    "Unknown section ${missing.joinToString(", ") { "'$it'" }}. Project sections: " +
                    configChildren(api.burpSuite().exportProjectOptionsAsJson()).joinToString(", ")))
                call.respond(StringResult(json))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unknown or invalid section '${section}': ${it.message}")) }
        }

        put("/project") {
            val req = runCatching { call.receive<SetConfigRequest>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                api.burpSuite().importProjectOptionsFromJson(req.text())
                call.respond(MessageResponse("Project options applied"))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        // ── User options ──────────────────────────────────────────────────────

        get("/user") {
            // User options all sit under user_options, so ?section=proxy means user_options.proxy.
            val paths = configPaths(call.request.queryParameters).map(::qualifyUserPath)
            val section = paths.joinToString(", ")
            runCatching {
                val json = if (paths.isNotEmpty()) api.burpSuite().exportUserOptionsAsJson(*paths.toTypedArray())
                           else api.burpSuite().exportUserOptionsAsJson()
                val missing = missingConfigPaths(json, paths)
                if (missing.isNotEmpty()) return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                    "Unknown section ${missing.joinToString(", ") { "'$it'" }}. User sections: " +
                    configChildren(api.burpSuite().exportUserOptionsAsJson(), "user_options").joinToString(", ")))
                call.respond(StringResult(json))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unknown or invalid section '${section}': ${it.message}")) }
        }

        put("/user") {
            val req = runCatching { call.receive<SetConfigRequest>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                api.burpSuite().importUserOptionsFromJson(req.text())
                call.respond(MessageResponse("User options applied"))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Task execution engine ─────────────────────────────────────────────

        get("/tasks") {
            call.respond(TaskEngineStateDto(api.burpSuite().taskExecutionEngine().state.name))
        }

        put("/tasks") {
            val req = runCatching { call.receive<SetTaskEngineStateRequest>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            api.burpSuite().taskExecutionEngine().state =
                if (req.running) TaskExecutionEngineState.RUNNING else TaskExecutionEngineState.PAUSED
            call.respond(MessageResponse("Task engine ${if (req.running) "running" else "paused"}"))
        }
    }

    // ── Burp shutdown ─────────────────────────────────────────────────────────

    post("/api/burp/shutdown") {
        call.respond(MessageResponse("Burp Suite is shutting down..."))
        // Respond before shutting down so the caller receives a response
        kotlinx.coroutines.GlobalScope.launch {
            kotlinx.coroutines.delay(500)
            api.burpSuite().shutdown()
        }
    }

    // ── Extensions ────────────────────────────────────────────────────────────

    get("/api/extensions") {
        runCatching {
            val json = api.burpSuite().exportUserOptionsAsJson()
            val root = Json.parseToJsonElement(json).jsonObject
            val exts = root["user_options"]?.jsonObject?.get("extender")?.jsonObject?.get("extensions")?.jsonArray
                ?: root["extender"]?.jsonObject?.get("extensions")?.jsonArray
                ?: root["extensions"]?.jsonObject?.get("extensions")?.jsonArray
                ?: JsonArray(emptyList())
            val result = exts.mapNotNull { it.jsonObject }.map { ext ->
                buildJsonObject {
                    put("name",    ext["name"]          ?: JsonPrimitive("unknown"))
                    put("enabled", ext["loaded"]        ?: ext["enabled"] ?: JsonPrimitive(false))
                    put("type",    ext["extension_type"]?: ext["type"]    ?: JsonPrimitive("java"))
                    put("file",    ext["extension_file"]?: ext["filename"]?: ext["file"] ?: JsonPrimitive(""))
                }
            }
            call.respond(result)
        }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
    }
}

// The spec documented both ?path (a dotted sub-tree) and ?section, but only section was read,
// so ?path=project_options.connections silently returned the whole config. They are the same
// thing to Montoya, whose export takes any number of dotted paths, so accept either name and
// repeat either to export several sub-trees.
private fun configPaths(query: io.ktor.http.Parameters): List<String> =
    (query.getAll("path").orEmpty() + query.getAll("section").orEmpty())
        .flatMap { it.split(',') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }

internal fun qualifyUserPath(path: String): String =
    if (path == "user_options" || path.startsWith("user_options.")) path else "user_options.$path"

/**
 * Paths that are not in an export. Burp answers an unknown path with the whole config rather
 * than an error, so ?section=scanner looked like it worked and returned every section.
 */
internal fun missingConfigPaths(json: String, paths: List<String>): List<String> {
    if (paths.isEmpty()) return emptyList()
    val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return paths
    return paths.filter { p ->
        p.split('.').fold(root as JsonElement?) { node, key -> (node as? JsonObject)?.get(key) } == null
    }
}

/** Keys at the top of a config export, or under [under] when given. */
internal fun configChildren(json: String, under: String? = null): List<String> {
    val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return emptyList()
    val node = if (under == null) root else root[under] as? JsonObject ?: return emptyList()
    return node.keys.sorted()
}

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
            val section = call.request.queryParameters["section"]
            runCatching {
                val json = if (section != null) api.burpSuite().exportProjectOptionsAsJson(section)
                           else api.burpSuite().exportProjectOptionsAsJson()
                call.respond(StringResult(json))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unknown or invalid section '${section}': ${it.message}")) }
        }

        put("/project") {
            val req = runCatching { call.receive<SetConfigRequest>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                api.burpSuite().importProjectOptionsFromJson(req.json)
                call.respond(MessageResponse("Project options applied"))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        // ── User options ──────────────────────────────────────────────────────

        get("/user") {
            val section = call.request.queryParameters["section"]
            runCatching {
                val json = if (section != null) api.burpSuite().exportUserOptionsAsJson(section)
                           else api.burpSuite().exportUserOptionsAsJson()
                call.respond(StringResult(json))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unknown or invalid section '${section}': ${it.message}")) }
        }

        put("/user") {
            val req = runCatching { call.receive<SetConfigRequest>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                api.burpSuite().importUserOptionsFromJson(req.json)
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
            val exts = root["extender"]?.jsonObject?.get("extensions")?.jsonArray
                ?: root["extensions"]?.jsonObject?.get("extensions")?.jsonArray
                ?: JsonArray(emptyList())
            val result = exts.mapNotNull { it.jsonObject }.map { ext ->
                buildJsonObject {
                    put("name",    ext["name"]    ?: JsonPrimitive("unknown"))
                    put("enabled", ext["loaded"]  ?: ext["enabled"] ?: JsonPrimitive(false))
                    put("type",    ext["type"]    ?: JsonPrimitive("java"))
                    put("file",    ext["filename"]?: ext["file"]    ?: JsonPrimitive(""))
                    put("errors",  ext["errors"]  ?: JsonPrimitive(""))
                }
            }
            call.respond(result)
        }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
    }
}

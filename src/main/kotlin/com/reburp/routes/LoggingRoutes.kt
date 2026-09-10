package com.reburp.routes

import burp.api.montoya.MontoyaApi
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class LogMessageInput(
    val message: String,
    val stack_trace: String? = null
)

@Serializable
data class LogEventInput(
    val message: String,
    val level: String = "INFO"
)

@Serializable
data class LogStreamInput(
    val message: String,
    val stream: String = "OUTPUT",
    val newline: Boolean = true
)

private val EVENT_LEVELS = listOf("DEBUG", "INFO", "ERROR", "CRITICAL")
private val STREAMS = listOf("OUTPUT", "ERROR")

/**
 * Writes into Burp's own logging channels.
 *
 * Two destinations exist and they are not the same thing. The extension output and error
 * tabs are per-extension text streams, read by whoever opens this extension in Burp.
 * Events are structured entries in Burp's event log, which is shared across the whole
 * suite and is where operational problems are expected to surface.
 *
 * This complements /api/log, which reads back this extension's own REST activity buffer.
 */
fun Routing.loggingRoutes(api: MontoyaApi) {
    route("/api/logging") {

        // Append a line to this extension's output tab.
        post("/output") {
            val req = runCatching { call.receive<LogMessageInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            runCatching { api.logging().logToOutput(req.message) }
                .onSuccess { call.respond(MessageResponse("Written to extension output")) }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Write failed: ${it.message}")) }
        }

        // Append a line to this extension's error tab, optionally with a stack trace.
        post("/error") {
            val req = runCatching { call.receive<LogMessageInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            runCatching {
                if (req.stack_trace.isNullOrBlank()) {
                    api.logging().logToError(req.message)
                } else {
                    // Carries the caller's trace text through as the throwable's message so it
                    // renders in Burp's error tab alongside the summary line.
                    api.logging().logToError(req.message, Throwable(req.stack_trace))
                }
            }
                .onSuccess { call.respond(MessageResponse("Written to extension error log")) }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Write failed: ${it.message}")) }
        }

        // Raise a structured entry in Burp's suite-wide event log.
        post("/event") {
            val req = runCatching { call.receive<LogEventInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val level = req.level.trim().uppercase()
            if (level !in EVENT_LEVELS) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'level': '${req.level}'. Allowed values: ${EVENT_LEVELS.joinToString(", ")}"))
            }
            runCatching {
                when (level) {
                    "DEBUG"    -> api.logging().raiseDebugEvent(req.message)
                    "INFO"     -> api.logging().raiseInfoEvent(req.message)
                    "ERROR"    -> api.logging().raiseErrorEvent(req.message)
                    else       -> api.logging().raiseCriticalEvent(req.message)
                }
            }
                .onSuccess { call.respond(MessageResponse("Raised $level event in Burp's event log")) }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Raise failed: ${it.message}")) }
        }

        // Write directly to the underlying stream, for callers that need control over newlines.
        post("/stream") {
            val req = runCatching { call.receive<LogStreamInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val stream = req.stream.trim().uppercase()
            if (stream !in STREAMS) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'stream': '${req.stream}'. Allowed values: ${STREAMS.joinToString(", ")}"))
            }
            runCatching {
                val target = if (stream == "OUTPUT") api.logging().output() else api.logging().error()
                if (req.newline) target.println(req.message) else target.print(req.message)
                target.flush()
            }
                .onSuccess { call.respond(MessageResponse("Written to $stream stream")) }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Write failed: ${it.message}")) }
        }
    }
}

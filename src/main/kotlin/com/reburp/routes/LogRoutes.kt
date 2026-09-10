package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.persistence.PersistedList
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

fun Routing.logRoutes(api: MontoyaApi, activityLog: ActivityLogTab) {

    // ── GET /api/log ──────────────────────────────────────────────────────────
    // Query the persisted activity log. All params optional.
    // ?host=            match path prefix or host embedded in path
    // ?method=          exact match (case-insensitive)
    // ?status=          exact status code
    // ?path_contains=   substring match on path
    // ?ai_notes_contains= substring match on ai_notes
    // ?session_id=      exact match on session tag
    // ?limit=           max results (default 100, max 500)
    // ?offset=          skip first N results (for pagination)

    get("/api/log") {
        val p = call.request.queryParameters
        val method         = p["method"]?.uppercase()
        val status         = p["status"]?.toIntOrNull()
        val pathContains   = p["path_contains"]
        val notesContains  = p["ai_notes_contains"]
        val sessionId      = p["session_id"]
        val limit          = (p["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)
        val offset         = (p["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)

        val entries = activityLog.allEntries()
            .let { if (method       != null) it.filter { e -> e.method.uppercase() == method } else it }
            .let { if (status       != null) it.filter { e -> e.status == status } else it }
            .let { if (pathContains != null) it.filter { e -> e.path.contains(pathContains, ignoreCase = true) } else it }
            .let { if (notesContains!= null) it.filter { e -> e.notes.contains(notesContains, ignoreCase = true) } else it }
            .let { if (sessionId    != null) it.filter { e -> e.sessionId == sessionId } else it }
            .drop(offset)
            .take(limit)
            .map { e ->
                LogEntryDto(
                    id         = e.id,
                    ts         = e.timestamp,
                    method     = e.method,
                    path       = e.path,
                    status     = e.status,
                    duration_ms= e.durationMs,
                    ai_notes   = e.notes,
                    session_id = e.sessionId
                )
            }

        call.respond(entries)
    }

    // ── DELETE /api/log ───────────────────────────────────────────────────────

    delete("/api/log") {
        runCatching {
            val ext = api.persistence().extensionData()
            ext.deleteStringList("activity_log")
            call.respond(MessageResponse("Activity log cleared"))
        }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
    }

    // ── POST /api/session ─────────────────────────────────────────────────────
    // Create a named session. Returns a session_id to tag subsequent requests.

    post("/api/session") {
        val req = runCatching { call.receive<SessionRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
        }
        if (req.label.isBlank()) return@post call.respond(
            HttpStatusCode.BadRequest, ErrorResponse("'label' is required")
        )
        runCatching {
            val sessionId  = "${req.label.trim().replace(" ", "-")}-${System.currentTimeMillis()}"
            val createdAt  = ISO.format(Instant.now())
            val dto        = SessionDto(id = sessionId, label = req.label.trim(), host = req.host, created_at = createdAt)
            val json       = buildJsonObject {
                put("id",         sessionId)
                put("label",      req.label.trim())
                put("created_at", createdAt)
                if (req.host != null) put("host", req.host)
            }.toString()

            val ext      = api.persistence().extensionData()
            val sessions = ext.getStringList("sessions")
                ?: PersistedList.persistedStringList().also { ext.setStringList("sessions", it) }
            sessions.add(json)

            call.respond(dto)
        }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
    }

    // ── GET /api/sessions ─────────────────────────────────────────────────────

    get("/api/sessions") {
        runCatching {
            val sessions = api.persistence().extensionData().getStringList("sessions") ?: emptyList<String>()
            val dtos = sessions.mapNotNull { json ->
                runCatching {
                    val o = Json.parseToJsonElement(json).jsonObject
                    SessionDto(
                        id         = o["id"]!!.jsonPrimitive.content,
                        label      = o["label"]!!.jsonPrimitive.content,
                        host       = o["host"]?.jsonPrimitive?.contentOrNull,
                        created_at = o["created_at"]!!.jsonPrimitive.content
                    )
                }.getOrNull()
            }
            call.respond(dtos)
        }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
    }

    // ── DELETE /api/sessions/{id} ─────────────────────────────────────────────

    delete("/api/sessions/{id}") {
        val id = call.parameters["id"]
            ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("'id' path param required"))
        runCatching {
            val ext      = api.persistence().extensionData()
            val sessions = ext.getStringList("sessions") ?: return@runCatching call.respond(
                HttpStatusCode.NotFound, ErrorResponse("No sessions found")
            )
            val idx = sessions.indexOfFirst { json ->
                runCatching { Json.parseToJsonElement(json).jsonObject["id"]?.jsonPrimitive?.content == id }.getOrElse { false }
            }
            if (idx < 0) return@runCatching call.respond(HttpStatusCode.NotFound, ErrorResponse("Session '$id' not found"))
            sessions.removeAt(idx)
            call.respond(MessageResponse("Session '$id' deleted"))
        }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
    }
}

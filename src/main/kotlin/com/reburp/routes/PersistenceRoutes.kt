package com.reburp.routes

import burp.api.montoya.MontoyaApi
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val VALID_TYPES = listOf("string", "boolean", "byte", "short", "int", "long")

fun Routing.persistenceRoutes(api: MontoyaApi) {
    fun prefs() = api.persistence().preferences()

    route("/api/preferences") {

        // ── GET all preferences by type ───────────────────────────────────────

        get("") {
            val type = call.request.queryParameters["type"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("'type' query parameter is required. Valid types: ${VALID_TYPES.joinToString(", ")}")
                )
            when (type.trim().lowercase()) {
                "string" -> call.respond(prefs().stringKeys().associateWith { prefs().getString(it) ?: "" })
                "boolean" -> call.respond(prefs().booleanKeys().associateWith { prefs().getBoolean(it) ?: false })
                "byte" -> call.respond(prefs().byteKeys().associateWith { (prefs().getByte(it) ?: 0).toInt() })
                "short" -> call.respond(prefs().shortKeys().associateWith { (prefs().getShort(it) ?: 0).toInt() })
                "int" -> call.respond(prefs().integerKeys().associateWith { prefs().getInteger(it) ?: 0 })
                "long" -> call.respond(prefs().longKeys().associateWith { prefs().getLong(it) ?: 0L })
                else -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'type': '$type'. Valid types: ${VALID_TYPES.joinToString(", ")}")
                )
            }
        }

        // ── PUT / set a preference ────────────────────────────────────────────

        put("/{key}") {
            val key = call.parameters["key"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("'key' path parameter is required"))
            val req = runCatching { call.receive<PersistenceValueRequest>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            runCatching {
                when (req.type.trim().lowercase()) {
                    "string" -> {
                        prefs().setString(key, req.value)
                        call.respond(MessageResponse("Set string preference '$key'"))
                    }
                    "boolean" -> {
                        val v = req.value.toBooleanStrictOrNull()
                            ?: return@runCatching call.respond(HttpStatusCode.BadRequest, ErrorResponse("Value must be 'true' or 'false' for type boolean"))
                        prefs().setBoolean(key, v)
                        call.respond(MessageResponse("Set boolean preference '$key'"))
                    }
                    "byte" -> {
                        val v = req.value.toByteOrNull()
                            ?: return@runCatching call.respond(HttpStatusCode.BadRequest, ErrorResponse("Value must be a byte (-128 to 127) for type byte"))
                        prefs().setByte(key, v)
                        call.respond(MessageResponse("Set byte preference '$key'"))
                    }
                    "short" -> {
                        val v = req.value.toShortOrNull()
                            ?: return@runCatching call.respond(HttpStatusCode.BadRequest, ErrorResponse("Value must be a short integer for type short"))
                        prefs().setShort(key, v)
                        call.respond(MessageResponse("Set short preference '$key'"))
                    }
                    "int" -> {
                        val v = req.value.toIntOrNull()
                            ?: return@runCatching call.respond(HttpStatusCode.BadRequest, ErrorResponse("Value must be an integer for type int"))
                        prefs().setInteger(key, v)
                        call.respond(MessageResponse("Set int preference '$key'"))
                    }
                    "long" -> {
                        val v = req.value.toLongOrNull()
                            ?: return@runCatching call.respond(HttpStatusCode.BadRequest, ErrorResponse("Value must be a long integer for type long"))
                        prefs().setLong(key, v)
                        call.respond(MessageResponse("Set long preference '$key'"))
                    }
                    else -> call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid 'type': '${req.type}'. Valid types: ${VALID_TYPES.joinToString(", ")}")
                    )
                }
            }.onFailure { e ->
                if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Error"))
            }
        }

        // ── DELETE a preference ───────────────────────────────────────────────

        delete("/{key}") {
            val key = call.parameters["key"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("'key' path parameter is required"))
            val type = call.request.queryParameters["type"]
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("'type' query parameter is required. Valid types: ${VALID_TYPES.joinToString(", ")}")
                )
            runCatching {
                when (type.trim().lowercase()) {
                    "string" -> { prefs().deleteString(key); call.respond(MessageResponse("Deleted string preference '$key'")) }
                    "boolean" -> { prefs().deleteBoolean(key); call.respond(MessageResponse("Deleted boolean preference '$key'")) }
                    "byte" -> { prefs().deleteByte(key); call.respond(MessageResponse("Deleted byte preference '$key'")) }
                    "short" -> { prefs().deleteShort(key); call.respond(MessageResponse("Deleted short preference '$key'")) }
                    "int" -> { prefs().deleteInteger(key); call.respond(MessageResponse("Deleted int preference '$key'")) }
                    "long" -> { prefs().deleteLong(key); call.respond(MessageResponse("Deleted long preference '$key'")) }
                    else -> call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid 'type': '$type'. Valid types: ${VALID_TYPES.joinToString(", ")}")
                    )
                }
            }.onFailure { e ->
                if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Error"))
            }
        }
    }
}

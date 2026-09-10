package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.collaborator.PayloadOption
import burp.api.montoya.collaborator.SecretKey
import burp.api.montoya.core.BurpSuiteEdition
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.collaboratorRoutes(api: MontoyaApi) {
    val isPro = api.burpSuite().version().edition() == BurpSuiteEdition.PROFESSIONAL

    route("/api/collaborator") {

        // ── Generate payload ──────────────────────────────────────────────────

        post("/generate") {
            if (!isPro) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val req = runCatching { call.receive<CollaboratorGenerateRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                val client = api.collaborator().createClient()
                val options = req.options.mapNotNull {
                    runCatching { PayloadOption.valueOf(it.trim().uppercase()) }.getOrNull()
                }.toTypedArray()
                val payload = if (req.custom_data.isNullOrBlank()) {
                    client.generatePayload(*options)
                } else {
                    client.generatePayload(req.custom_data, *options)
                }
                val secretKey = client.getSecretKey().toString()
                call.respond(
                    CollaboratorGeneratedDto(
                        payload = payload.toString(),
                        interaction_id = payload.id().toString(),
                        secret_key = secretKey
                    )
                )
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Poll for interactions ─────────────────────────────────────────────

        get("/poll/{secretKey}") {
            if (!isPro) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val secretKey = call.parameters["secretKey"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("'secretKey' path parameter is required"))
            runCatching {
                val client = api.collaborator().restoreClient(SecretKey.secretKey(secretKey))
                val interactions = client.getAllInteractions().map { interaction ->
                    CollaboratorInteractionDto(
                        id = interaction.id().toString(),
                        type = interaction.type().toString(),
                        time = interaction.timeStamp().toString(),
                        client_ip = interaction.clientIp().hostAddress,
                        client_port = interaction.clientPort(),
                        custom_data = interaction.customData().orElse(null)
                    )
                }
                call.respond(interactions)
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }
    }
}

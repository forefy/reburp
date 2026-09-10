package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.collaborator.InteractionFilter
import burp.api.montoya.collaborator.PayloadOption
import burp.api.montoya.collaborator.SecretKey
import burp.api.montoya.core.BurpSuiteEdition
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CollaboratorServerDto(
    val address: String,
    val is_literal_address: Boolean
)

@Serializable
data class DefaultPayloadDto(
    val payload: String,
    val interaction_id: String,
    val custom_data: String? = null,
    val server: CollaboratorServerDto? = null
)

@Serializable
data class DnsDetailsDto(val query_type: String, val query: String)

@Serializable
data class HttpDetailsDto(val protocol: String, val request: String?, val response: String?)

@Serializable
data class SmtpDetailsDto(val protocol: String, val conversation: String)

@Serializable
data class DetailedInteractionDto(
    val id: String,
    val type: String,
    val time: String,
    val client_ip: String,
    val client_port: Int,
    val custom_data: String? = null,
    val dns: DnsDetailsDto? = null,
    val http: HttpDetailsDto? = null,
    val smtp: SmtpDetailsDto? = null
)

@Serializable
data class DefaultPayloadInput(val options: List<String> = emptyList())

/**
 * Collaborator endpoints beyond generate and poll.
 *
 * The base routes in CollaboratorRoutes create a private client and return flat
 * interaction summaries. These add the project's shared default generator, the server a
 * payload resolves against, and the protocol-specific detail attached to each hit, which
 * is the part that tells you what the target actually did.
 *
 * All of this is Professional only, like the rest of Collaborator.
 */
fun Routing.collaboratorExtraRoutes(api: MontoyaApi) {
    val isPro = api.burpSuite().version().edition() == BurpSuiteEdition.PROFESSIONAL

    route("/api/collaborator") {

        // The Collaborator server this project resolves payloads against.
        get("/server") {
            if (!isPro) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            runCatching {
                val server = api.collaborator().createClient().server()
                call.respond(CollaboratorServerDto(server.address(), server.isLiteralAddress()))
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Could not read server details: ${it.message}"))
            }
        }

        // Generate from Burp's project-wide default generator rather than a new private client.
        post("/generate/default") {
            if (!isPro) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val req = runCatching { call.receive<DefaultPayloadInput>() }.getOrElse {
                DefaultPayloadInput()
            }
            val options = req.options.map { raw ->
                runCatching { PayloadOption.valueOf(raw.trim().uppercase()) }.getOrElse {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid payload option: '$raw'. Allowed values: ${PayloadOption.values().joinToString(", ") { o -> o.name }}")
                    )
                }
            }.toTypedArray()
            runCatching {
                val payload = api.collaborator().defaultPayloadGenerator().generatePayload(*options)
                val server = payload.server().orElse(null)
                call.respond(
                    DefaultPayloadDto(
                        payload = payload.toString(),
                        interaction_id = payload.id().toString(),
                        custom_data = payload.customData().orElse(null),
                        server = server?.let { CollaboratorServerDto(it.address(), it.isLiteralAddress()) }
                    )
                )
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Payload generation failed: ${it.message}"))
            }
        }

        // Poll with protocol detail, optionally narrowed to one payload or interaction id.
        get("/interactions/{secretKey}") {
            if (!isPro) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val secretKey = call.parameters["secretKey"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("'secretKey' path parameter is required"))
            val payloadFilter = call.request.queryParameters["payload"]
            val idFilter = call.request.queryParameters["interaction_id"]
            if (payloadFilter != null && idFilter != null) {
                return@get call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Provide only one of 'payload' or 'interaction_id', not both"))
            }
            runCatching {
                val client = api.collaborator().restoreClient(SecretKey.secretKey(secretKey))
                val interactions = when {
                    payloadFilter != null -> client.getInteractions(InteractionFilter.interactionPayloadFilter(payloadFilter))
                    idFilter != null      -> client.getInteractions(InteractionFilter.interactionIdFilter(idFilter))
                    else                  -> client.getAllInteractions()
                }
                call.respond(
                    interactions.map { interaction ->
                        val dns = interaction.dnsDetails().orElse(null)
                        val http = interaction.httpDetails().orElse(null)
                        val smtp = interaction.smtpDetails().orElse(null)
                        DetailedInteractionDto(
                            id = interaction.id().toString(),
                            type = interaction.type().toString(),
                            time = interaction.timeStamp().toString(),
                            client_ip = interaction.clientIp().hostAddress,
                            client_port = interaction.clientPort(),
                            custom_data = interaction.customData().orElse(null),
                            dns = dns?.let {
                                DnsDetailsDto(
                                    query_type = runCatching { it.queryType().name }.getOrElse { _ -> "UNKNOWN" },
                                    query = runCatching { it.query().toString() }.getOrElse { _ -> "" }
                                )
                            },
                            http = http?.let {
                                val rr = runCatching { it.requestResponse() }.getOrNull()
                                HttpDetailsDto(
                                    protocol = runCatching { it.protocol().name }.getOrElse { _ -> "UNKNOWN" },
                                    request = runCatching { rr?.request()?.toString() }.getOrNull(),
                                    response = runCatching { rr?.response()?.toString() }.getOrNull()
                                )
                            },
                            smtp = smtp?.let {
                                SmtpDetailsDto(
                                    protocol = runCatching { it.protocol().name }.getOrElse { _ -> "UNKNOWN" },
                                    conversation = runCatching { it.conversation() }.getOrElse { _ -> "" }
                                )
                            }
                        )
                    }
                )
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Poll failed: ${it.message}"))
            }
        }
    }
}

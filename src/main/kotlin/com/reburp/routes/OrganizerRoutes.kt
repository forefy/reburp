package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.organizer.OrganizerItemStatus
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.organizerRoutes(api: MontoyaApi) {
    route("/api/organizer") {

        // ── List items ────────────────────────────────────────────────────────

        get("/items") {
            val statusParam = call.request.queryParameters["status"]
            val items = if (statusParam != null) {
                val status = runCatching { OrganizerItemStatus.valueOf(statusParam.trim().uppercase()) }.getOrElse {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid 'status': '$statusParam'. Allowed values: ${OrganizerItemStatus.values().joinToString(", ") { it.name }}")
                    )
                }
                api.organizer().items { item -> item.status() == status }
            } else {
                api.organizer().items()
            }
            val dtos = items.map { item ->
                OrganizerItemDto(
                    id = item.id(),
                    status = item.status().name,
                    url = item.request()?.url(),
                    method = item.request()?.method(),
                    status_code = item.response()?.statusCode()?.toInt(),
                    request_length = item.request()?.body()?.length() ?: 0,
                    response_length = item.response()?.body()?.length() ?: 0
                )
            }
            call.respond(dtos)
        }

        // ── Send to organizer ─────────────────────────────────────────────────

        post("/send") {
            val req = runCatching { call.receive<SendToToolRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.host.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'host' is required and must not be blank"))
            if (req.request.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'request' is required and must not be blank"))
            runCatching {
                val service = httpService(req.host, req.port, req.use_https)
                val httpReq = HttpRequest.httpRequest(service, normalizeRequest(req.request))
                api.organizer().sendToOrganizer(httpReq)
                call.respond(MessageResponse("Request sent to Organizer"))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }
    }
}

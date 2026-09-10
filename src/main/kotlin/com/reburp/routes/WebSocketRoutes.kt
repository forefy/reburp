package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.websocket.BinaryMessage
import burp.api.montoya.websocket.TextMessage
import burp.api.montoya.websocket.extension.ExtensionWebSocket
import burp.api.montoya.websocket.extension.ExtensionWebSocketCreationStatus
import burp.api.montoya.websocket.extension.ExtensionWebSocketMessageHandler
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// ── Active WebSocket connection registry ──────────────────────────────────────

private val wsConnections   = ConcurrentHashMap<String, ExtensionWebSocket>()
private val wsMessageQueues = ConcurrentHashMap<String, CopyOnWriteArrayList<WsClientMessageDto>>()
private val wsConnectionMeta = ConcurrentHashMap<String, WsConnectionInfoDto>()

// ─────────────────────────────────────────────────────────────────────────────

fun Routing.webSocketClientRoutes(api: MontoyaApi) {
    route("/api/websocket") {

        // List all active outbound WebSocket connections managed by this extension.
        get("") {
            call.respond(wsConnectionMeta.values.toList())
        }

        // Open a new outbound WebSocket connection.
        // Body: { host, port (default 443), use_https (default true), path (default "/") }
        // Returns: { id, host, port, path, secure, upgrade_status }
        // On failure: 502 with error detail and status enum value.
        // ExtensionWebSocketCreationStatus values: SUCCESS, INVALID_HOST, UNKNOWN_HOST,
        //   INVALID_PORT, CONNECTION_FAILED, INVALID_REQUEST, NON_UPGRADE_RESPONSE, STREAMING_RESPONSE
        post("/connect") {
            val req = runCatching { call.receive<WsConnectRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.host.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'host' is required"))
            if (!req.path.startsWith("/")) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'path' must start with '/' (e.g. \"/chat\")"))
            if (req.port !in 1..65535) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'port' must be 1–65535"))

            runCatching {
                val service = burp.api.montoya.http.HttpService.httpService(req.host, req.port, req.use_https)
                val creation = api.websockets().createWebSocket(service, req.path)

                if (creation.status() != ExtensionWebSocketCreationStatus.SUCCESS) {
                    val upgradeInfo = creation.upgradeResponse()
                        .map { "HTTP ${it.statusCode()}: ${it.toString().lines().firstOrNull() ?: ""}" }
                        .orElse("no upgrade response")
                    return@runCatching call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse(
                            "WebSocket connection failed - status: ${creation.status()} ($upgradeInfo). " +
                            "Allowed status values: ${ExtensionWebSocketCreationStatus.values().joinToString(", ")}"
                        )
                    )
                }

                val ws = creation.webSocket().orElseThrow { IllegalStateException("WebSocket absent despite SUCCESS") }
                val id = UUID.randomUUID().toString().take(8)
                val queue = CopyOnWriteArrayList<WsClientMessageDto>()
                wsConnections[id] = ws
                wsMessageQueues[id] = queue

                val upgradeStatus = creation.upgradeResponse().map { it.statusCode().toInt() }.orElse(101)
                val meta = WsConnectionInfoDto(id = id, host = req.host, port = req.port, path = req.path, secure = req.use_https, upgrade_status = upgradeStatus)
                wsConnectionMeta[id] = meta

                ws.registerMessageHandler(object : ExtensionWebSocketMessageHandler {
                    override fun textMessageReceived(msg: TextMessage) {
                        queue.add(WsClientMessageDto(direction = "INCOMING", type = "TEXT", payload = msg.payload(), payload_base64 = null))
                    }
                    override fun binaryMessageReceived(msg: BinaryMessage) {
                        val b64 = java.util.Base64.getEncoder().encodeToString(msg.payload().getBytes())
                        queue.add(WsClientMessageDto(direction = "INCOMING", type = "BINARY", payload = null, payload_base64 = b64))
                    }
                    override fun onClose() {
                        wsConnections.remove(id)
                        wsMessageQueues.remove(id)
                        wsConnectionMeta.remove(id)
                    }
                })

                call.respond(meta)
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Failed to create WebSocket"))
            }
        }

        // Send a UTF-8 text message on an existing connection.
        // Body: { message: "..." }
        post("/{id}/send-text") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing connection id"))
            val ws = wsConnections[id] ?: return@post call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("WebSocket '$id' not found. Active connections: ${wsConnections.keys.joinToString(", ").ifBlank { "none" }}")
            )
            val req = runCatching { call.receive<WsSendRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            runCatching {
                ws.sendTextMessage(req.message)
                wsMessageQueues[id]?.add(WsClientMessageDto(direction = "OUTGOING", type = "TEXT", payload = req.message, payload_base64 = null))
                call.respond(MessageResponse("Sent ${req.message.length} chars on connection $id"))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Send failed")) }
        }

        // Send a binary message (base64-encoded input) on an existing connection.
        // Body: { base64: "<base64-encoded bytes>" }
        post("/{id}/send-binary") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing connection id"))
            val ws = wsConnections[id] ?: return@post call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("WebSocket '$id' not found. Active connections: ${wsConnections.keys.joinToString(", ").ifBlank { "none" }}")
            )
            val req = runCatching { call.receive<WsSendBinaryRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            runCatching {
                val bytes = java.util.Base64.getDecoder().decode(req.base64)
                ws.sendBinaryMessage(burp.api.montoya.core.ByteArray.byteArray(*bytes.map { it.toInt() }.toIntArray()))
                wsMessageQueues[id]?.add(WsClientMessageDto(direction = "OUTGOING", type = "BINARY", payload = null, payload_base64 = req.base64))
                call.respond(MessageResponse("Sent ${bytes.size} bytes (binary) on connection $id"))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Send failed")) }
        }

        // Retrieve received messages (and optionally clear the queue).
        // Query params: clear=true - drain the queue after returning (default false)
        get("/{id}/messages") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing connection id"))
            if (!wsConnections.containsKey(id)) return@get call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("WebSocket '$id' not found. Active connections: ${wsConnections.keys.joinToString(", ").ifBlank { "none" }}")
            )
            val clear = call.request.queryParameters["clear"]?.toBooleanStrictOrNull() ?: false
            val queue = wsMessageQueues[id] ?: emptyList<WsClientMessageDto>()
            val messages = queue.toList()
            if (clear) wsMessageQueues[id]?.clear()
            call.respond(messages)
        }

        // Close and remove a WebSocket connection.
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing connection id"))
            val ws = wsConnections.remove(id) ?: return@delete call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("WebSocket '$id' not found. Active connections: ${wsConnections.keys.joinToString(", ").ifBlank { "none" }}")
            )
            wsMessageQueues.remove(id)
            wsConnectionMeta.remove(id)
            runCatching { ws.close() }
            call.respond(MessageResponse("WebSocket $id closed"))
        }
    }
}

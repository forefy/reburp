package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.ai.chat.Message
import burp.api.montoya.ai.chat.PromptException
import burp.api.montoya.ai.chat.PromptOptions
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.aiRoutes(api: MontoyaApi) {
    route("/api/ai") {

        // ── AI status ─────────────────────────────────────────────────────────

        get("/status") {
            call.respond(AiStatusResponse(enabled = api.ai().isEnabled()))
        }

        // ── AI chat ───────────────────────────────────────────────────────────

        post("/chat") {
            if (!api.ai().isEnabled()) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("AI features are not enabled. Enable AI in Burp Suite settings.")
                )
            }
            val req = runCatching { call.receive<AiChatRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.messages.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'messages' must not be empty"))
            }

            val messages = buildList {
                if (req.system_prompt != null) {
                    add(Message.systemMessage(req.system_prompt))
                }
                for (msg in req.messages) {
                    when (msg.role.trim().lowercase()) {
                        "user" -> add(Message.userMessage(msg.content))
                        "assistant" -> add(Message.assistantMessage(msg.content))
                        else -> return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid message role: '${msg.role}'. Allowed values: user, assistant")
                        )
                    }
                }
            }

            if (req.temperature != null && (req.temperature < 0.0 || req.temperature > 1.0)) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'temperature': ${req.temperature}. Must be between 0.0 and 1.0 inclusive.")
                )
            }

            runCatching {
                // Burp applies its own default sampling unless options are supplied, so only
                // build a PromptOptions when the caller actually asked for a temperature.
                val response = if (req.temperature != null) {
                    val options = PromptOptions.promptOptions().withTemperature(req.temperature)
                    api.ai().prompt().execute(options, *messages.toTypedArray())
                } else {
                    api.ai().prompt().execute(*messages.toTypedArray())
                }
                call.respond(AiChatResponse(response = response.content()))
            }.onFailure { e ->
                when (e) {
                    is PromptException -> call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("AI prompt failed: ${e.message ?: "Unknown error"}")
                    )
                    else -> call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(e.message ?: "Internal error")
                    )
                }
            }
        }
    }
}

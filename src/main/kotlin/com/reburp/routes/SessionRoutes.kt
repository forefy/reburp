package com.reburp.routes

import burp.api.montoya.MontoyaApi
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

fun Routing.sessionRoutes(api: MontoyaApi) {
    route("/api/sessions") {

        // List session handling rules
        get("/rules") {
            runCatching {
                val json = api.burpSuite().exportUserOptionsAsJson()
                val root = Json.parseToJsonElement(json).jsonObject
                val sessions = root["sessions"]?.jsonObject
                val rules = sessions?.get("session_handling_rules")?.jsonObject
                    ?.get("rules")?.jsonArray ?: JsonArray(emptyList())
                call.respond(rules)
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        // Add an "add header" session rule
        post("/rules/add-header") {
            val req = runCatching { call.receive<AddHeaderRuleRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad body"))
            }
            runCatching {
                val json = api.burpSuite().exportUserOptionsAsJson()
                val root = Json.parseToJsonElement(json).jsonObject.toMutableMap()
                val sessions = root["sessions"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
                val rulesObj = sessions["session_handling_rules"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
                val rules = rulesObj["rules"]?.jsonArray?.toMutableList() ?: mutableListOf()

                val newRule = buildJsonObject {
                    put("enabled", true)
                    put("name", req.name ?: "Add ${req.header_name}")
                    put("description", "Auto-added by reburp")
                    putJsonArray("actions") {
                        addJsonObject {
                            put("action_type", "ADD_HEADER")
                            put("header_name", req.header_name)
                            put("header_value", req.header_value)
                        }
                    }
                    if (req.scope_url != null) {
                        putJsonArray("scope_urls") { add(req.scope_url) }
                    }
                }
                rules.add(newRule)
                rulesObj["rules"] = JsonArray(rules)
                sessions["session_handling_rules"] = JsonObject(rulesObj)
                root["sessions"] = JsonObject(sessions)
                api.burpSuite().importUserOptionsFromJson(JsonObject(root).toString())
                call.respond(MessageResponse("Session rule '${req.name ?: "Add ${req.header_name}"}' added"))
            }.onFailure { if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        // Delete session rule by index
        delete("/rules/{index}") {
            val index = call.parameters["index"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("'index' must be an integer"))
            runCatching {
                val json = api.burpSuite().exportUserOptionsAsJson()
                val root = Json.parseToJsonElement(json).jsonObject.toMutableMap()
                val sessions = root["sessions"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
                val rulesObj = sessions["session_handling_rules"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
                val rules = rulesObj["rules"]?.jsonArray?.toMutableList() ?: mutableListOf()
                if (index < 0 || index >= rules.size)
                    return@runCatching call.respond(HttpStatusCode.NotFound, ErrorResponse("Index $index out of range (${rules.size} rules)"))
                rules.removeAt(index)
                rulesObj["rules"] = JsonArray(rules)
                sessions["session_handling_rules"] = JsonObject(rulesObj)
                root["sessions"] = JsonObject(sessions)
                api.burpSuite().importUserOptionsFromJson(JsonObject(root).toString())
                call.respond(MessageResponse("Deleted rule at index $index"))
            }.onFailure { if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }
    }
}

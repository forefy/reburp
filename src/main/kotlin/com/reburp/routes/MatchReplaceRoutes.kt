package com.reburp.routes

import burp.api.montoya.MontoyaApi
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

// [Montoya Config] - reads/writes Burp config via api.burpSuite().exportProjectOptionsAsJson("proxy")
// Rules are stored as a JSON array; we identify them by their zero-based list index.
fun Routing.matchReplaceRoutes(api: MontoyaApi) {
    route("/api/proxy/match-replace") {

        // List all match-and-replace rules
        get {
            val config = runCatching {
                Json.parseToJsonElement(api.burpSuite().exportProjectOptionsAsJson("proxy"))
            }.getOrElse {
                return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to export proxy config: ${it.message}"))
            }
            val rules = extractMatchReplaceRules(config)
            call.respond(MatchReplaceListResponse(count = rules.size, rules = rules))
        }

        // Create a new rule (appended to the end of the list)
        post {
            val req = runCatching { call.receive<MatchReplaceRule>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid request body"))
            }
            if (req.rule_type !in VALID_RULE_TYPES) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'rule_type': '${req.rule_type}'. Allowed: ${VALID_RULE_TYPES.joinToString(", ")}")
                )
            }
            val updatedJson = runCatching {
                val config = Json.parseToJsonElement(api.burpSuite().exportProjectOptionsAsJson("proxy"))
                appendMatchReplaceRule(config, req)
            }.getOrElse {
                return@post call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to read config: ${it.message}"))
            }
            runCatching { api.burpSuite().importProjectOptionsFromJson(updatedJson) }.onFailure {
                return@post call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to apply rule: ${it.message}"))
            }
            call.respond(HttpStatusCode.Created, MessageResponse("Match/replace rule created"))
        }

        // Update a rule at the given zero-based index
        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Path param 'id' must be an integer (zero-based rule index)"))
            val req = runCatching { call.receive<MatchReplaceRule>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid request body"))
            }
            if (req.rule_type !in VALID_RULE_TYPES) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'rule_type'. Allowed: ${VALID_RULE_TYPES.joinToString(", ")}")
                )
            }
            val updatedJson = runCatching {
                val config = Json.parseToJsonElement(api.burpSuite().exportProjectOptionsAsJson("proxy"))
                replaceMatchReplaceRule(config, id, req)
            }.getOrElse {
                return@put call.respond(
                    if (it is IndexOutOfBoundsException) HttpStatusCode.NotFound else HttpStatusCode.InternalServerError,
                    ErrorResponse(it.message ?: "Failed to update rule")
                )
            }
            runCatching { api.burpSuite().importProjectOptionsFromJson(updatedJson) }.onFailure {
                return@put call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to apply update: ${it.message}"))
            }
            call.respond(MessageResponse("Rule $id updated"))
        }

        // Delete one rule by index
        delete("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Path param 'id' must be an integer (zero-based rule index)"))
            val updatedJson = runCatching {
                val config = Json.parseToJsonElement(api.burpSuite().exportProjectOptionsAsJson("proxy"))
                removeMatchReplaceRule(config, id)
            }.getOrElse {
                return@delete call.respond(
                    if (it is IndexOutOfBoundsException) HttpStatusCode.NotFound else HttpStatusCode.InternalServerError,
                    ErrorResponse(it.message ?: "Failed to delete rule")
                )
            }
            runCatching { api.burpSuite().importProjectOptionsFromJson(updatedJson) }.onFailure {
                return@delete call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to apply deletion: ${it.message}"))
            }
            call.respond(MessageResponse("Rule $id deleted"))
        }

        // Delete ALL rules
        delete {
            val updatedJson = runCatching {
                val config = Json.parseToJsonElement(api.burpSuite().exportProjectOptionsAsJson("proxy"))
                clearMatchReplaceRules(config)
            }.getOrElse {
                return@delete call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to read config: ${it.message}"))
            }
            runCatching { api.burpSuite().importProjectOptionsFromJson(updatedJson) }.onFailure {
                return@delete call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to clear rules: ${it.message}"))
            }
            call.respond(MessageResponse("All match/replace rules cleared"))
        }
    }
}

// ── Rule type validation ───────────────────────────────────────────────────────

val VALID_RULE_TYPES = setOf(
    "request_first_line",
    "request_header",
    "request_body",
    "request_param_name",
    "request_param_value",
    "response_header",
    "response_body"
)

// ── Config JSON helpers ────────────────────────────────────────────────────────

private fun extractMatchReplaceRules(config: JsonElement): List<MatchReplaceRule> {
    val proxy = (config as? JsonObject)?.get("proxy") as? JsonObject ?: return emptyList()
    val arr = proxy["match_replace_rules"] as? JsonArray ?: return emptyList()
    return arr.mapIndexed { idx, el ->
        val obj = el as? JsonObject ?: return@mapIndexed null
        MatchReplaceRule(
            id = idx,
            rule_type = obj["rule_type"]?.jsonPrimitive?.contentOrNull ?: "",
            string_match = obj["string_match"]?.jsonPrimitive?.contentOrNull ?: "",
            string_replace = obj["string_replace"]?.jsonPrimitive?.contentOrNull ?: "",
            is_simple_match = obj["is_simple_match"]?.jsonPrimitive?.booleanOrNull ?: false,
            enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
            comment = obj["comment"]?.jsonPrimitive?.contentOrNull
        )
    }.filterNotNull()
}

private fun ruleToJsonObject(rule: MatchReplaceRule): JsonObject = buildJsonObject {
    put("rule_type", rule.rule_type)
    put("string_match", rule.string_match)
    put("string_replace", rule.string_replace)
    put("is_simple_match", rule.is_simple_match)
    put("enabled", rule.enabled)
    put("comment", rule.comment ?: "")
}

private fun modifyRules(config: JsonElement, transform: (MutableList<JsonElement>) -> Unit): String {
    val root = (config as JsonObject).toMutableMap()
    val proxy = ((root["proxy"] as? JsonObject)?.toMutableMap() ?: mutableMapOf())
    val rules = (proxy["match_replace_rules"] as? JsonArray)?.toMutableList() ?: mutableListOf()
    transform(rules)
    proxy["match_replace_rules"] = JsonArray(rules)
    root["proxy"] = JsonObject(proxy)
    return JsonObject(root).toString()
}

private fun appendMatchReplaceRule(config: JsonElement, rule: MatchReplaceRule): String =
    modifyRules(config) { it.add(ruleToJsonObject(rule)) }

private fun replaceMatchReplaceRule(config: JsonElement, id: Int, rule: MatchReplaceRule): String =
    modifyRules(config) { rules ->
        if (id < 0 || id >= rules.size) throw IndexOutOfBoundsException("No rule at index $id (total: ${rules.size})")
        rules[id] = ruleToJsonObject(rule)
    }

private fun removeMatchReplaceRule(config: JsonElement, id: Int): String =
    modifyRules(config) { rules ->
        if (id < 0 || id >= rules.size) throw IndexOutOfBoundsException("No rule at index $id (total: ${rules.size})")
        rules.removeAt(id)
    }

private fun clearMatchReplaceRules(config: JsonElement): String =
    modifyRules(config) { it.clear() }

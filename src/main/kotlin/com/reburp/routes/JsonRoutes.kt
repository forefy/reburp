package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.utilities.json.JsonArrayNode
import burp.api.montoya.utilities.json.JsonBooleanNode
import burp.api.montoya.utilities.json.JsonNode
import burp.api.montoya.utilities.json.JsonNullNode
import burp.api.montoya.utilities.json.JsonNumberNode
import burp.api.montoya.utilities.json.JsonObjectNode
import burp.api.montoya.utilities.json.JsonStringNode
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class JsonInput(val json: String)
@Serializable data class JsonValidResult(val valid: Boolean, val error: String? = null)

@Serializable
data class JsonPointerInput(
    val json: String,
    val pointer: String,
    val type: String = "STRING",
    val value: String = ""
)

@Serializable
data class JsonReadResult(
    val pointer: String,
    val type: String,
    val found: Boolean,
    val value: String? = null
)

@Serializable
data class JsonKeyInfo(
    val key: String,
    val type: String,
    val has_string: Boolean,
    val has_boolean: Boolean,
    val has_number: Boolean,
    val has_array: Boolean,
    val has_object: Boolean,
    val value: String? = null
)

@Serializable
data class JsonInspectResult(
    val type: String,
    val is_object: Boolean,
    val is_array: Boolean,
    val is_string: Boolean,
    val is_number: Boolean,
    val is_boolean: Boolean,
    val is_null: Boolean,
    val json_string: String,
    val value: String? = null,
    val array_length: Int? = null,
    val keys: List<JsonKeyInfo>? = null
)

private val READ_TYPES = listOf("STRING", "BOOLEAN", "LONG", "DOUBLE", "RAW")

/** Describes a JsonNode's concrete kind for API responses. */
private fun kindOf(node: JsonNode): String = when {
    node.isObject  -> "OBJECT"
    node.isArray   -> "ARRAY"
    node.isString  -> "STRING"
    node.isNumber  -> "NUMBER"
    node.isBoolean -> "BOOLEAN"
    node.isNull    -> "NULL"
    else           -> "UNKNOWN"
}

/** Rebuilds a kotlinx JSON element as a Montoya JsonNode tree. */
private fun toMontoyaNode(element: JsonElement): JsonNode = when (element) {
    is JsonNull   -> JsonNullNode.jsonNullNode()
    is JsonObject -> {
        val obj = JsonObjectNode.jsonObjectNode()
        element.forEach { (k, v) ->
            when {
                v is JsonNull                   -> obj.put(k, JsonNullNode.jsonNullNode())
                v is JsonPrimitive && v.isString -> obj.putString(k, v.content)
                v is JsonPrimitive && v.booleanOrNull != null -> obj.putBoolean(k, v.boolean)
                v is JsonPrimitive && v.longOrNull != null    -> obj.putNumber(k, v.long)
                v is JsonPrimitive && v.doubleOrNull != null  -> obj.putNumber(k, v.double)
                else                            -> obj.put(k, toMontoyaNode(v))
            }
        }
        obj
    }
    is JsonArray -> {
        val arr = JsonArrayNode.jsonArrayNode()
        element.forEach { v ->
            when {
                v is JsonNull                   -> arr.add(JsonNullNode.jsonNullNode())
                v is JsonPrimitive && v.isString -> arr.addString(v.content)
                v is JsonPrimitive && v.booleanOrNull != null -> arr.addBoolean(v.boolean)
                v is JsonPrimitive && v.longOrNull != null    -> arr.addNumber(v.long)
                v is JsonPrimitive && v.doubleOrNull != null  -> arr.addNumber(v.double)
                else                            -> arr.add(toMontoyaNode(v))
            }
        }
        arr
    }
    is JsonPrimitive -> when {
        element.isString            -> JsonStringNode.jsonStringNode(element.content)
        element.booleanOrNull != null -> JsonBooleanNode.jsonBooleanNode(element.boolean)
        element.longOrNull != null    -> JsonNumberNode.jsonNumberNode(element.long)
        element.doubleOrNull != null  -> JsonNumberNode.jsonNumberNode(element.double)
        else                        -> JsonStringNode.jsonStringNode(element.content)
    }
}

/**
 * JSON inspection and editing, backed by Montoya's JsonUtils and JsonNode tree model.
 *
 * Pointers use Burp's dotted-path syntax (for example `user.roles.0.name`), not RFC 6901.
 */
fun Routing.jsonRoutes(api: MontoyaApi) {
    route("/api/utils/json") {

        // Whether the supplied text parses as JSON.
        post("/validate") {
            val req = runCatching { call.receive<JsonInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val valid = runCatching { api.utilities().jsonUtils().isValidJson(req.json) }.getOrElse { false }
            call.respond(JsonValidResult(valid, if (valid) null else "Document did not parse as JSON"))
        }

        // Read a value at a pointer, coerced to the requested type.
        post("/read") {
            val req = runCatching { call.receive<JsonPointerInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val type = req.type.trim().uppercase()
            if (type !in READ_TYPES) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'type': '${req.type}'. Allowed values: ${READ_TYPES.joinToString(", ")}"))
            }
            val j = api.utilities().jsonUtils()
            val value: String? = runCatching {
                when (type) {
                    "STRING"  -> j.readString(req.json, req.pointer)
                    "BOOLEAN" -> j.readBoolean(req.json, req.pointer)?.toString()
                    "LONG"    -> j.readLong(req.json, req.pointer)?.toString()
                    "DOUBLE"  -> j.readDouble(req.json, req.pointer)?.toString()
                    else      -> j.read(req.json, req.pointer)
                }
            }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Read failed at pointer '${req.pointer}': ${it.message}"))
            }
            call.respond(JsonReadResult(req.pointer, type, value != null, value))
        }

        // Insert a new value at a pointer.
        post("/add") {
            val req = runCatching { call.receive<JsonPointerInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            runCatching { api.utilities().jsonUtils().add(req.json, req.pointer, req.value) }
                .onSuccess { call.respond(StringResult(it)) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Add failed at pointer '${req.pointer}': ${it.message}")) }
        }

        // Replace an existing value at a pointer.
        post("/update") {
            val req = runCatching { call.receive<JsonPointerInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            runCatching { api.utilities().jsonUtils().update(req.json, req.pointer, req.value) }
                .onSuccess { call.respond(StringResult(it)) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Update failed at pointer '${req.pointer}': ${it.message}")) }
        }

        // Delete the value at a pointer.
        post("/remove") {
            val req = runCatching { call.receive<JsonPointerInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            runCatching { api.utilities().jsonUtils().remove(req.json, req.pointer) }
                .onSuccess { call.respond(StringResult(it)) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Remove failed at pointer '${req.pointer}': ${it.message}")) }
        }

        // Parse into Burp's node model and describe the root, including per-key typing.
        post("/inspect") {
            val req = runCatching { call.receive<JsonInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val node = runCatching { JsonNode.jsonNode(req.json) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Document did not parse as JSON: ${it.message}"))
            }
            val keys: List<JsonKeyInfo>? = if (node.isObject) {
                val obj = node.asObject()
                obj.asMap().keys.map { k ->
                    val child = obj.get(k)
                    JsonKeyInfo(
                        key = k,
                        type = if (obj.has(k)) kindOf(child) else "MISSING",
                        has_string = obj.hasString(k),
                        has_boolean = obj.hasBoolean(k),
                        has_number = obj.hasNumber(k),
                        has_array = obj.hasArray(k),
                        has_object = obj.hasObject(k),
                        value = when {
                            obj.hasString(k)  -> obj.getString(k)
                            obj.hasBoolean(k) -> obj.getBoolean(k)?.toString()
                            obj.hasNumber(k)  -> (obj.getLong(k) ?: obj.getDouble(k) ?: obj.getNumber(k))?.toString()
                            else              -> null
                        }
                    )
                }
            } else null

            val arrayLength: Int? = if (node.isArray) node.asArray().asList().size else null
            val scalar: String? = runCatching {
                when {
                    node.isString  -> node.asString()
                    node.isBoolean -> node.asBoolean()?.toString()
                    node.isNumber  -> (node.asLong() ?: node.asDouble() ?: node.asNumber())?.toString()
                    node.isNull    -> null
                    else           -> node.getValue()?.toString()
                }
            }.getOrNull()

            call.respond(
                JsonInspectResult(
                    type = kindOf(node),
                    is_object = node.isObject,
                    is_array = node.isArray,
                    is_string = node.isString,
                    is_number = node.isNumber,
                    is_boolean = node.isBoolean,
                    is_null = node.isNull,
                    json_string = node.toJsonString(),
                    value = scalar,
                    array_length = arrayLength,
                    keys = keys
                )
            )
        }

        // Round-trip a document through Burp's node builders, normalising its serialisation.
        post("/normalize") {
            val req = runCatching { call.receive<JsonInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val element = runCatching { Json.parseToJsonElement(req.json) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Document did not parse as JSON: ${it.message}"))
            }
            runCatching { toMontoyaNode(element).toJsonString() }
                .onSuccess { call.respond(StringResult(it)) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Rebuild failed: ${it.message}")) }
        }
    }
}

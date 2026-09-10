package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.ByteArray
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.persistence.PersistedList
import burp.api.montoya.persistence.PersistedObject
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

// ── Request and response models ───────────────────────────────────────────────

@Serializable
data class ExtensionDataKeysResult(
    val path: String,
    val child_objects: List<String>,
    val strings: List<String>,
    val booleans: List<String>,
    val bytes: List<String>,
    val shorts: List<String>,
    val integers: List<String>,
    val longs: List<String>,
    val byte_arrays: List<String>,
    val http_requests: List<String>,
    val http_responses: List<String>,
    val http_request_responses: List<String>,
    val string_lists: List<String>,
    val boolean_lists: List<String>,
    val short_lists: List<String>,
    val integer_lists: List<String>,
    val long_lists: List<String>,
    val byte_array_lists: List<String>,
    val http_request_lists: List<String>,
    val http_response_lists: List<String>,
    val http_request_response_lists: List<String>
)

@Serializable
data class HttpPairDto(val request: String, val response: String? = null)

@Serializable
data class ExtensionDataValueRequest(
    val path: String = "",
    val type: String,
    val key: String,
    val value: String? = null,
    val values: List<String>? = null,
    val request: String? = null,
    val response: String? = null,
    val pairs: List<HttpPairDto>? = null
)

@Serializable
data class ExtensionDataValueResult(
    val path: String,
    val type: String,
    val key: String,
    val found: Boolean,
    val value: String? = null,
    val values: List<String>? = null,
    val pair: HttpPairDto? = null,
    val pairs: List<HttpPairDto>? = null
)

@Serializable
data class ExtensionDataChildRequest(val path: String)

@Serializable
data class ExtensionDataChildResult(
    val path: String,
    val created: Boolean,
    val child_objects: List<String>
)

/** Every value shape the extension-data tree can hold, as accepted by `type`. */
private enum class ExtDataType {
    STRING, BOOLEAN, BYTE, SHORT, INTEGER, LONG, BYTE_ARRAY,
    HTTP_REQUEST, HTTP_RESPONSE, HTTP_REQUEST_RESPONSE,
    STRING_LIST, BOOLEAN_LIST, SHORT_LIST, INTEGER_LIST, LONG_LIST, BYTE_ARRAY_LIST,
    HTTP_REQUEST_LIST, HTTP_RESPONSE_LIST, HTTP_REQUEST_RESPONSE_LIST
}

private val ALLOWED_TYPES = ExtDataType.values().joinToString(", ") { it.name }

/** Parses the `type` field, returning null when it names no known value shape. */
private fun parseType(raw: String?): ExtDataType? =
    raw?.trim()?.uppercase()?.let { name -> ExtDataType.values().firstOrNull { it.name == name } }

private fun badType(raw: String?): ErrorResponse =
    ErrorResponse("Invalid 'type': '${raw ?: ""}'. Allowed values: $ALLOWED_TYPES")

/** Splits "a/b/c" into its non-empty segments. An empty path addresses the root object. */
private fun pathSegments(path: String?): List<String> =
    (path ?: "").split('/').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Walks the child-object chain named by [path]. Returns null as soon as a segment is
 * missing, so callers can answer 404 instead of silently creating nodes.
 */
private fun resolvePath(root: PersistedObject, path: String?): PersistedObject? {
    var node = root
    for (segment in pathSegments(path)) {
        node = runCatching { node.getChildObject(segment) }.getOrNull() ?: return null
    }
    return node
}

/**
 * Walks the child-object chain named by [path], creating any missing node with
 * PersistedObject.persistedObject() and attaching it with setChildObject.
 */
private fun resolveOrCreatePath(root: PersistedObject, path: String?): PersistedObject {
    var node = root
    for (segment in pathSegments(path)) {
        val existing = runCatching { node.getChildObject(segment) }.getOrNull()
        node = existing ?: run {
            node.setChildObject(segment, PersistedObject.persistedObject())
            node.getChildObject(segment)
                ?: throw IllegalStateException("Burp did not return the child object '$segment' after creating it")
        }
    }
    return node
}

/** Collects every key held by [node], one bucket per Montoya value type. */
private fun keysOf(node: PersistedObject, path: String) = ExtensionDataKeysResult(
    path = path,
    child_objects = node.childObjectKeys().sorted(),
    strings = node.stringKeys().sorted(),
    booleans = node.booleanKeys().sorted(),
    bytes = node.byteKeys().sorted(),
    shorts = node.shortKeys().sorted(),
    integers = node.integerKeys().sorted(),
    longs = node.longKeys().sorted(),
    byte_arrays = node.byteArrayKeys().sorted(),
    http_requests = node.httpRequestKeys().sorted(),
    http_responses = node.httpResponseKeys().sorted(),
    http_request_responses = node.httpRequestResponseKeys().sorted(),
    string_lists = node.stringListKeys().sorted(),
    boolean_lists = node.booleanListKeys().sorted(),
    short_lists = node.shortListKeys().sorted(),
    integer_lists = node.integerListKeys().sorted(),
    long_lists = node.longListKeys().sorted(),
    byte_array_lists = node.byteArrayListKeys().sorted(),
    http_request_lists = node.httpRequestListKeys().sorted(),
    http_response_lists = node.httpResponseListKeys().sorted(),
    http_request_response_lists = node.httpRequestResponseListKeys().sorted()
)

private fun pairOf(rr: HttpRequestResponse) =
    HttpPairDto(rr.request().toString(), if (rr.hasResponse()) rr.response().toString() else null)

/** The scalar text the caller supplied, or a 400-worthy failure naming the missing field. */
private fun requireValue(req: ExtensionDataValueRequest): String = req.value
    ?: throw IllegalArgumentException("Missing 'value': type '${req.type}' needs a scalar 'value' field")

private fun requireValues(req: ExtensionDataValueRequest): List<String> = req.values
    ?: throw IllegalArgumentException("Missing 'values': type '${req.type}' needs a 'values' array")

private fun requirePairs(req: ExtensionDataValueRequest): List<HttpPairDto> = req.pairs
    ?: throw IllegalArgumentException("Missing 'pairs': type '${req.type}' needs a 'pairs' array of request/response objects")

private fun badNumber(field: String, value: String, type: ExtDataType, range: String): Nothing =
    throw IllegalArgumentException("Invalid '$field': '$value'. Type ${type.name} accepts $range")

/** Reads one typed value out of [node], or null when the key is absent. */
private fun readValue(node: PersistedObject, type: ExtDataType, key: String): ExtensionDataValueResult? {
    val base = ExtensionDataValueResult(path = "", type = type.name, key = key, found = true)
    return when (type) {
        ExtDataType.STRING -> node.getString(key)?.let { base.copy(value = it) }
        ExtDataType.BOOLEAN -> node.getBoolean(key)?.let { base.copy(value = it.toString()) }
        ExtDataType.BYTE -> node.getByte(key)?.let { base.copy(value = it.toString()) }
        ExtDataType.SHORT -> node.getShort(key)?.let { base.copy(value = it.toString()) }
        ExtDataType.INTEGER -> node.getInteger(key)?.let { base.copy(value = it.toString()) }
        ExtDataType.LONG -> node.getLong(key)?.let { base.copy(value = it.toString()) }
        ExtDataType.BYTE_ARRAY -> node.getByteArray(key)?.let { base.copy(value = it.toString()) }
        ExtDataType.HTTP_REQUEST -> node.getHttpRequest(key)?.let { base.copy(value = it.toString()) }
        ExtDataType.HTTP_RESPONSE -> node.getHttpResponse(key)?.let { base.copy(value = it.toString()) }
        ExtDataType.HTTP_REQUEST_RESPONSE -> node.getHttpRequestResponse(key)?.let { base.copy(pair = pairOf(it)) }
        ExtDataType.STRING_LIST -> node.getStringList(key)?.let { list -> base.copy(values = list.toList()) }
        ExtDataType.BOOLEAN_LIST -> node.getBooleanList(key)?.let { list -> base.copy(values = list.map { it.toString() }) }
        ExtDataType.SHORT_LIST -> node.getShortList(key)?.let { list -> base.copy(values = list.map { it.toString() }) }
        ExtDataType.INTEGER_LIST -> node.getIntegerList(key)?.let { list -> base.copy(values = list.map { it.toString() }) }
        ExtDataType.LONG_LIST -> node.getLongList(key)?.let { list -> base.copy(values = list.map { it.toString() }) }
        ExtDataType.BYTE_ARRAY_LIST -> node.getByteArrayList(key)?.let { list -> base.copy(values = list.map { it.toString() }) }
        ExtDataType.HTTP_REQUEST_LIST -> node.getHttpRequestList(key)?.let { list -> base.copy(values = list.map { it.toString() }) }
        ExtDataType.HTTP_RESPONSE_LIST -> node.getHttpResponseList(key)?.let { list -> base.copy(values = list.map { it.toString() }) }
        ExtDataType.HTTP_REQUEST_RESPONSE_LIST -> node.getHttpRequestResponseList(key)?.let { list -> base.copy(pairs = list.map { pairOf(it) }) }
    }
}

/** Writes one typed value into [node], throwing IllegalArgumentException on bad input. */
private fun writeValue(node: PersistedObject, type: ExtDataType, req: ExtensionDataValueRequest) {
    val key = req.key
    when (type) {
        ExtDataType.STRING -> node.setString(key, requireValue(req))

        ExtDataType.BOOLEAN -> {
            val raw = requireValue(req).trim()
            node.setBoolean(key, raw.lowercase().toBooleanStrictOrNull()
                ?: throw IllegalArgumentException("Invalid 'value': '$raw'. Type BOOLEAN accepts true, false"))
        }

        ExtDataType.BYTE -> {
            val raw = requireValue(req).trim()
            node.setByte(key, raw.toByteOrNull() ?: badNumber("value", raw, type, "a whole number from -128 to 127"))
        }

        ExtDataType.SHORT -> {
            val raw = requireValue(req).trim()
            node.setShort(key, raw.toShortOrNull() ?: badNumber("value", raw, type, "a whole number from -32768 to 32767"))
        }

        ExtDataType.INTEGER -> {
            val raw = requireValue(req).trim()
            node.setInteger(key, raw.toIntOrNull() ?: badNumber("value", raw, type, "a 32 bit whole number"))
        }

        ExtDataType.LONG -> {
            val raw = requireValue(req).trim()
            node.setLong(key, raw.toLongOrNull() ?: badNumber("value", raw, type, "a 64 bit whole number"))
        }

        ExtDataType.BYTE_ARRAY -> node.setByteArray(key, ByteArray.byteArray(requireValue(req)))

        ExtDataType.HTTP_REQUEST -> node.setHttpRequest(key, HttpRequest.httpRequest(requireValue(req)))

        ExtDataType.HTTP_RESPONSE -> node.setHttpResponse(key, HttpResponse.httpResponse(requireValue(req)))

        ExtDataType.HTTP_REQUEST_RESPONSE -> {
            val reqText = req.request
                ?: throw IllegalArgumentException("Missing 'request': type HTTP_REQUEST_RESPONSE needs raw HTTP text in 'request'")
            val respText = req.response
                ?: throw IllegalArgumentException("Missing 'response': type HTTP_REQUEST_RESPONSE needs raw HTTP text in 'response'")
            node.setHttpRequestResponse(
                key,
                HttpRequestResponse.httpRequestResponse(HttpRequest.httpRequest(reqText), HttpResponse.httpResponse(respText))
            )
        }

        ExtDataType.STRING_LIST -> {
            val list = PersistedList.persistedStringList()
            requireValues(req).forEach { list.add(it) }
            node.setStringList(key, list)
        }

        ExtDataType.BOOLEAN_LIST -> {
            val list = PersistedList.persistedBooleanList()
            requireValues(req).forEach { raw ->
                list.add(raw.trim().lowercase().toBooleanStrictOrNull()
                    ?: throw IllegalArgumentException("Invalid 'values' entry: '$raw'. Type BOOLEAN_LIST accepts true, false"))
            }
            node.setBooleanList(key, list)
        }

        ExtDataType.SHORT_LIST -> {
            val list = PersistedList.persistedShortList()
            requireValues(req).forEach { raw ->
                list.add(raw.trim().toShortOrNull() ?: badNumber("values", raw, type, "whole numbers from -32768 to 32767"))
            }
            node.setShortList(key, list)
        }

        ExtDataType.INTEGER_LIST -> {
            val list = PersistedList.persistedIntegerList()
            requireValues(req).forEach { raw ->
                list.add(raw.trim().toIntOrNull() ?: badNumber("values", raw, type, "32 bit whole numbers"))
            }
            node.setIntegerList(key, list)
        }

        ExtDataType.LONG_LIST -> {
            val list = PersistedList.persistedLongList()
            requireValues(req).forEach { raw ->
                list.add(raw.trim().toLongOrNull() ?: badNumber("values", raw, type, "64 bit whole numbers"))
            }
            node.setLongList(key, list)
        }

        ExtDataType.BYTE_ARRAY_LIST -> {
            val list = PersistedList.persistedByteArrayList()
            requireValues(req).forEach { list.add(ByteArray.byteArray(it)) }
            node.setByteArrayList(key, list)
        }

        ExtDataType.HTTP_REQUEST_LIST -> {
            val list = PersistedList.persistedHttpRequestList()
            requireValues(req).forEach { list.add(HttpRequest.httpRequest(it)) }
            node.setHttpRequestList(key, list)
        }

        ExtDataType.HTTP_RESPONSE_LIST -> {
            val list = PersistedList.persistedHttpResponseList()
            requireValues(req).forEach { list.add(HttpResponse.httpResponse(it)) }
            node.setHttpResponseList(key, list)
        }

        ExtDataType.HTTP_REQUEST_RESPONSE_LIST -> {
            val list = PersistedList.persistedHttpRequestResponseList()
            requirePairs(req).forEachIndexed { index, pair ->
                val respText = pair.response
                    ?: throw IllegalArgumentException("Missing 'pairs[$index].response': every HTTP_REQUEST_RESPONSE_LIST entry needs raw HTTP text for both request and response")
                list.add(
                    HttpRequestResponse.httpRequestResponse(
                        HttpRequest.httpRequest(pair.request),
                        HttpResponse.httpResponse(respText)
                    )
                )
            }
            node.setHttpRequestResponseList(key, list)
        }
    }
}

/** Deletes one typed value from [node]. */
private fun deleteValue(node: PersistedObject, type: ExtDataType, key: String) {
    when (type) {
        ExtDataType.STRING -> node.deleteString(key)
        ExtDataType.BOOLEAN -> node.deleteBoolean(key)
        ExtDataType.BYTE -> node.deleteByte(key)
        ExtDataType.SHORT -> node.deleteShort(key)
        ExtDataType.INTEGER -> node.deleteInteger(key)
        ExtDataType.LONG -> node.deleteLong(key)
        ExtDataType.BYTE_ARRAY -> node.deleteByteArray(key)
        ExtDataType.HTTP_REQUEST -> node.deleteHttpRequest(key)
        ExtDataType.HTTP_RESPONSE -> node.deleteHttpResponse(key)
        ExtDataType.HTTP_REQUEST_RESPONSE -> node.deleteHttpRequestResponse(key)
        ExtDataType.STRING_LIST -> node.deleteStringList(key)
        ExtDataType.BOOLEAN_LIST -> node.deleteBooleanList(key)
        ExtDataType.SHORT_LIST -> node.deleteShortList(key)
        ExtDataType.INTEGER_LIST -> node.deleteIntegerList(key)
        ExtDataType.LONG_LIST -> node.deleteLongList(key)
        ExtDataType.BYTE_ARRAY_LIST -> node.deleteByteArrayList(key)
        ExtDataType.HTTP_REQUEST_LIST -> node.deleteHttpRequestList(key)
        ExtDataType.HTTP_RESPONSE_LIST -> node.deleteHttpResponseList(key)
        ExtDataType.HTTP_REQUEST_RESPONSE_LIST -> node.deleteHttpRequestResponseList(key)
    }
}

/**
 * Typed extension data persistence, backed by Montoya's `PersistedObject` tree returned
 * from `api.persistence().extensionData()`.
 *
 * This tree lives inside the Burp project file, so anything written here survives an
 * extension reload and comes back when the same project is reopened. It is a different
 * store from `/api/preferences`, which is user-scoped and only holds flat scalars.
 *
 * Nodes are addressed by a slash separated path such as `campaign/targets`. Reads return
 * 404 when a segment of the path does not exist. Writes create the missing segments with
 * `PersistedObject.persistedObject()` and attach them with `setChildObject`.
 */
fun Routing.extensionDataRoutes(api: MontoyaApi) {
    fun root(): PersistedObject = api.persistence().extensionData()

    route("/api/extension-data") {

        // ── List every key at the root of the extension data tree ─────────────

        get("") {
            runCatching { keysOf(root(), "") }
                .onSuccess { call.respond(it) }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Failed to read extension data keys")) }
        }

        // ── List every key at a nested child object path ──────────────────────

        get("/keys/{path...}") {
            val path = call.parameters.getAll("path").orEmpty().joinToString("/")
            val node = runCatching { resolvePath(root(), path) }.getOrElse {
                return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Failed to resolve path"))
            } ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("No child object at path '$path'"))

            runCatching { keysOf(node, path) }
                .onSuccess { call.respond(it) }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Failed to read extension data keys")) }
        }

        // ── Read one typed value ──────────────────────────────────────────────

        get("/value") {
            val path = call.request.queryParameters["path"] ?: ""
            val rawType = call.request.queryParameters["type"]
            val key = call.request.queryParameters["key"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("'key' query parameter is required"))
            val type = parseType(rawType)
                ?: return@get call.respond(HttpStatusCode.BadRequest, badType(rawType))

            val node = runCatching { resolvePath(root(), path) }.getOrElse {
                return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Failed to resolve path"))
            } ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("No child object at path '$path'"))

            runCatching { readValue(node, type, key) }
                .onSuccess { result ->
                    if (result == null) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("No ${type.name} value stored under key '$key' at path '$path'"))
                    } else {
                        call.respond(result.copy(path = path))
                    }
                }
                .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Failed to read value")) }
        }

        // ── Write one typed value, creating any missing path segments ─────────

        put("/value") {
            val req = runCatching { call.receive<ExtensionDataValueRequest>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.key.isBlank()) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'key': '${req.key}'. The key must not be blank."))
            }
            val type = parseType(req.type)
                ?: return@put call.respond(HttpStatusCode.BadRequest, badType(req.type))

            runCatching {
                val node = resolveOrCreatePath(root(), req.path)
                writeValue(node, type, req)
            }
                .onSuccess { call.respond(MessageResponse("Set ${type.name} value '${req.key}' at path '${req.path}'")) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Failed to write value")) }
        }

        // ── Delete one typed value ────────────────────────────────────────────

        delete("/value") {
            val path = call.request.queryParameters["path"] ?: ""
            val rawType = call.request.queryParameters["type"]
            val key = call.request.queryParameters["key"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("'key' query parameter is required"))
            val type = parseType(rawType)
                ?: return@delete call.respond(HttpStatusCode.BadRequest, badType(rawType))

            val node = runCatching { resolvePath(root(), path) }.getOrElse {
                return@delete call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Failed to resolve path"))
            } ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("No child object at path '$path'"))

            runCatching { deleteValue(node, type, key) }
                .onSuccess { call.respond(MessageResponse("Deleted ${type.name} value '$key' at path '$path'")) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Failed to delete value")) }
        }

        // ── Create a child object, and every missing node above it ────────────

        post("/child") {
            val req = runCatching { call.receive<ExtensionDataChildRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val segments = pathSegments(req.path)
            if (segments.isEmpty()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'path': '${req.path}'. Give at least one segment, such as 'campaign' or 'campaign/targets'.")
                )
            }

            runCatching {
                val existed = resolvePath(root(), req.path) != null
                val node = resolveOrCreatePath(root(), req.path)
                ExtensionDataChildResult(
                    path = segments.joinToString("/"),
                    created = !existed,
                    child_objects = node.childObjectKeys().sorted()
                )
            }
                .onSuccess { call.respond(it) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Failed to create child object")) }
        }

        // ── Delete a child object and everything below it ─────────────────────

        delete("/child") {
            val path = call.request.queryParameters["path"] ?: ""
            val segments = pathSegments(path)
            if (segments.isEmpty()) {
                return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'path': '$path'. Give at least one segment, such as 'campaign' or 'campaign/targets'. The root object itself cannot be deleted.")
                )
            }
            val parentPath = segments.dropLast(1).joinToString("/")
            val name = segments.last()

            val parent = runCatching { resolvePath(root(), parentPath) }.getOrElse {
                return@delete call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Failed to resolve path"))
            } ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("No child object at path '$parentPath'"))

            if (!parent.childObjectKeys().contains(name)) {
                return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("No child object named '$name' at path '$parentPath'"))
            }

            runCatching { parent.deleteChildObject(name) }
                .onSuccess { call.respond(MessageResponse("Deleted child object '${segments.joinToString("/")}'")) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Failed to delete child object")) }
        }
    }
}

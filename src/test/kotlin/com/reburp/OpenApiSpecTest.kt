package com.reburp

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.fail

/**
 * Checks the OpenAPI spec against itself and against the Kotlin types it documents.
 *
 * The spec is written by hand, so it drifts: this project shipped documented fields that did
 * not exist, required fields the code defaulted, duplicate operationIds, and defaults outside
 * their own enum. None of that needs Burp to detect, so it runs here rather than only in
 * tools/smoke_test.py against a live extension.
 */
class OpenApiSpecTest {

    private val spec: JsonObject = Json.parseToJsonElement(openApiJson(9090)).jsonObject
    private val schemas: JsonObject = spec["components"]!!.jsonObject["schemas"]!!.jsonObject
    private val paths: JsonObject = spec["paths"]!!.jsonObject

    private fun operations(): List<Triple<String, String, JsonObject>> =
        paths.flatMap { (path, item) ->
            item.jsonObject.filterKeys { it in HTTP_METHODS }.map { (method, op) -> Triple(path, method, op.jsonObject) }
        }

    @Test
    fun `operationIds are unique`() {
        val dupes = operations()
            .mapNotNull { (path, method, op) -> op["operationId"]?.jsonPrimitive?.content?.let { it to "${method.uppercase()} $path" } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
        if (dupes.isNotEmpty()) fail("Duplicate operationIds, generated clients drop all but one:\n" +
            dupes.entries.joinToString("\n") { (id, where) -> "  $id: ${where.joinToString(", ")}" })
    }

    @Test
    fun `every schema reference resolves`() {
        val missing = sortedSetOf<String>()
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> e.forEach { (k, v) ->
                    if (k == "\$ref") {
                        // "#/components/<section>/<name>": schemas, responses, parameters and so on
                        val ref = v.jsonPrimitive.content
                        val parts = ref.removePrefix("#/").split('/')
                        var node: JsonElement? = spec
                        for (p in parts) node = (node as? JsonObject)?.get(p)
                        if (node == null) missing += ref
                    } else walk(v)
                }
                is JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        walk(spec)
        if (missing.isNotEmpty()) fail("Unresolved \$ref targets: $missing")
    }

    @Test
    fun `defaults and examples stay inside their enums`() {
        val problems = mutableListOf<String>()
        fun walk(e: JsonElement, at: String) {
            if (e is JsonObject) {
                val enum = (e["enum"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                val default = (e["default"] as? JsonPrimitive)?.contentOrNull
                if (enum != null && default != null && default !in enum) problems += "$at: default '$default' not in $enum"
                e.forEach { (k, v) -> walk(v, "$at/$k") }
            } else if (e is JsonArray) e.forEachIndexed { i, v -> walk(v, "$at[$i]") }
        }
        walk(spec, "")
        for ((path, method, op) in operations()) {
            val body = op["requestBody"]?.jsonObject?.get("content")?.jsonObject?.get("application/json")?.jsonObject ?: continue
            val example = body["example"] as? JsonObject ?: continue
            val props = resolve(body["schema"])?.get("properties") as? JsonObject ?: continue
            for ((field, value) in example) {
                val prop = resolve(props[field]) ?: continue
                val enum = (prop["enum"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: continue
                val v = (value as? JsonPrimitive)?.contentOrNull ?: continue
                if (v !in enum) problems += "${method.uppercase()} $path example: $field='$v' not in $enum"
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test
    fun `schemas match the Kotlin types they document`() {
        val problems = mutableListOf<String>()
        val requestSchemas = operations().mapNotNull { (_, _, op) ->
            op["requestBody"]?.jsonObject?.get("content")?.jsonObject?.get("application/json")?.jsonObject
                ?.get("schema")?.jsonObject?.get("\$ref")?.jsonPrimitive?.content?.substringAfterLast('/')
        }.toSet()
        var compared = 0
        for ((name, raw) in schemas) {
            val schema = raw.jsonObject
            val props = (schema["properties"] as? JsonObject)?.keys ?: continue
            val descriptor = descriptorFor(name) ?: continue
            compared++
            val fields = (0 until descriptor.elementsCount).associate { descriptor.getElementName(it) to it }
            val undocumented = fields.keys - props
            val phantom = props - fields.keys
            if (undocumented.isNotEmpty()) problems += "$name: accepted/returned but undocumented $undocumented"
            if (phantom.isNotEmpty()) problems += "$name: documented but not in ${descriptor.serialName} $phantom"
            if (name in requestSchemas) {
                val required = (schema["required"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty().toSet()
                val mustSend = fields.filter { (_, i) ->
                    !descriptor.isElementOptional(i) && !descriptor.getElementDescriptor(i).isNullable
                }.keys
                val notMarked = mustSend - required
                if (notMarked.isNotEmpty()) problems += "$name: the code rejects a body without $notMarked but the spec does not mark them required"
            }
        }
        if (compared < 50) fail("Only $compared schemas matched a Kotlin type; the class lookup is probably broken")
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    private fun resolve(e: JsonElement?): JsonObject? {
        var o = e as? JsonObject ?: return null
        while (true) {
            val ref = (o["\$ref"] as? JsonPrimitive)?.content ?: return o
            o = schemas[ref.substringAfterLast('/')]?.jsonObject ?: return null
        }
    }

    private fun descriptorFor(schemaName: String): SerialDescriptor? {
        for (pkg in listOf("com.reburp", "com.reburp.routes")) {
            for (candidate in listOf(schemaName, "${schemaName}Dto")) {
                val cls = runCatching { Class.forName("$pkg.$candidate") }.getOrNull() ?: continue
                return runCatching { serializer(cls).descriptor }.getOrNull() ?: continue
            }
        }
        return null
    }

    private companion object {
        val HTTP_METHODS = setOf("get", "put", "post", "delete", "patch")
    }
}

package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.bambda.BambdaImportResult
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.bambdaRoutes(api: MontoyaApi) {

    /**
     * Import a raw Bambda script into the Burp Bambda library.
     * The script must be valid Java compatible with the Burp custom action API.
     * Once imported, load it from the Bambda library in the Repeater Custom actions side panel.
     */
    post("/api/bambda/import") {
        val req = runCatching { call.receive<BambdaImportRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid request body"))
        }
        val source = wrapBambdaSource(req.name, req.source)
        val result = runCatching { api.bambda().importBambda(source) }.getOrElse {
            return@post call.respond(HttpStatusCode.InternalServerError, ErrorResponse("importBambda failed: ${it.message}"))
        }
        val errors = result.importErrors() ?: emptyList()
        val status = when (result.status()) {
            BambdaImportResult.Status.LOADED_WITHOUT_ERRORS -> "imported"
            BambdaImportResult.Status.LOADED_WITH_ERRORS -> "imported_with_errors"
            else -> "failed"
        }
        call.respond(BambdaImportResponse(status = status, errors = errors))
    }

    /**
     * Generate a multi-step chain Bambda from a list of steps, import it into the Bambda library,
     * and return the generated source. Each step can extract values from the response and inject
     * them into subsequent requests. Enable auto-run in Repeater to execute the full chain.
     *
     * Steps support:
     * - extract: jsonpath keys like "$.access" or regex patterns like "sessionid=([^;]+)"
     * - inject: variable -> header string template, e.g. {"token": "Authorization: Bearer {{token}}"}
     */
    post("/api/bambda/generate-chain") {
        val req = runCatching { call.receive<GenerateChainRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid request body"))
        }
        if (req.steps.isEmpty()) {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("At least one step is required"))
        }
        val wrapped = wrapBambdaSource(req.name, generateChainBambda(req))
        if (!req.import_to_library) {
            return@post call.respond(GenerateChainResponse(name = req.name, source = wrapped, imported = false))
        }
        val result = runCatching { api.bambda().importBambda(wrapped) }.getOrElse {
            return@post call.respond(
                GenerateChainResponse(name = req.name, source = wrapped, imported = false,
                    import_errors = listOf("importBambda failed: ${it.message}"))
            )
        }
        // status() is never null, so this used to report imported=true even when Burp refused it.
        val imported = result.status() == BambdaImportResult.Status.LOADED_WITHOUT_ERRORS ||
            result.status() == BambdaImportResult.Status.LOADED_WITH_ERRORS
        call.respond(GenerateChainResponse(name = req.name, source = wrapped, imported = imported,
            import_errors = result.importErrors() ?: emptyList()))
    }
}

// Wraps bare source in the .bambda envelope format Burp expects
private fun wrapBambdaSource(name: String, source: String): String {
    // Deterministic UUID from name so re-importing the same named Bambda overwrites in place
    val safeId = java.util.UUID.nameUUIDFromBytes(name.toByteArray()).toString()
    val safeName = name.replace("\"", "'")
    return """
id: $safeId
name: "$safeName"
function: CUSTOM_ACTION
location: REPEATER
source: |
${source.trimEnd().lines().joinToString("\n") { "  $it" }}
""".trimStart()
}

/** A Java string literal for [s], so request text can never break out of the generated code. */
internal fun javaString(s: String): String = buildString {
    append('"')
    for (c in s) when (c) {
        '\\' -> append("\\\\")
        '"'  -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
    }
    append('"')
}

/** A Java expression for a header value template, with each {{var}} read from the chain's vars. */
internal fun templateExpression(template: String): String {
    val parts = Regex("""\{\{(\w+)}}""").split(template)
    val names = Regex("""\{\{(\w+)}}""").findAll(template).map { it.groupValues[1] }.toList()
    val out = mutableListOf<String>()
    parts.forEachIndexed { i, literal ->
        if (literal.isNotEmpty()) out += javaString(literal)
        if (i < names.size) out += "vars.getOrDefault(${javaString(names[i])}, \"\")"
    }
    return if (out.isEmpty()) "\"\"" else out.joinToString(" + ")
}

/**
 * Generates the Java body of a Repeater custom action that runs the chain. Burp compiles it as
 * analyze(HttpRequestResponse requestResponse, RequestResponseSelection selection,
 * HttpEditor httpEditor, MontoyaApi api, Logging logging), so `api` and `logging` are
 * variables. This used to call api(), logging() and a createRequest(host, port, https) that
 * Montoya does not have, so every generated chain failed to compile.
 */
internal fun generateChainBambda(req: GenerateChainRequest): String {
    val base = "${if (req.https) "https" else "http"}://${req.host}:${req.port}"
    val total = req.steps.size
    val sb = StringBuilder()
    sb.appendLine("/**")
    sb.appendLine(" * Chain: ${req.name.replace("*/", "* /")}")
    sb.appendLine(" * Generated by reburp, $total step(s). Run it from Repeater's Custom actions panel.")
    sb.appendLine(" */")
    sb.appendLine("var vars = new java.util.HashMap<String, String>();")
    sb.appendLine()
    req.steps.forEachIndexed { i, step ->
        val n = i + 1
        val label = javaString("[Step $n/$total] ${step.method} ${step.path} -> ")
        sb.appendLine("// Step $n/$total")
        sb.appendLine("var req$n = burp.api.montoya.http.message.requests.HttpRequest.httpRequestFromUrl(" +
            "${javaString(base + step.path)}).withMethod(${javaString(step.method)});")
        step.headers.forEach { (k, v) -> sb.appendLine("req$n = req$n.withAddedHeader(${javaString(k)}, ${javaString(v)});") }
        // inject maps a variable to a header line such as "Authorization: Bearer {{token}}".
        step.inject.values.forEach { line ->
            val name = line.substringBefore(":").trim()
            if (line.contains(":") && name.isNotEmpty())
                sb.appendLine("req$n = req$n.withAddedHeader(${javaString(name)}, ${templateExpression(line.substringAfter(":").trim())});")
        }
        if (!step.body.isNullOrEmpty()) sb.appendLine("req$n = req$n.withBody(${javaString(step.body)});")
        sb.appendLine("var rr$n = api.http().sendRequest(req$n);")
        sb.appendLine("if (!rr$n.hasResponse()) { logging.logToOutput($label + \"no response\"); return; }")
        sb.appendLine("logging.logToOutput($label + rr$n.response().statusCode());")
        if (step.extract.isNotEmpty()) sb.appendLine("var body$n = rr$n.response().bodyToString();")
        step.extract.entries.forEachIndexed { j, (varName, pattern) ->
            // "$.key" reads a string field anywhere in the JSON body; anything else is a regex
            // whose first group is the value.
            val regex = if (pattern.startsWith("$.")) {
                val key = pattern.substringAfterLast(".").replace(Regex("""[\\^$.|?*+()\[\]{}]"""), """\\$0""")
                "\"$key\"\\s*:\\s*\"([^\"]*)\""
            } else pattern
            sb.appendLine("var m${n}_$j = java.util.regex.Pattern.compile(${javaString(regex)}).matcher(body$n);")
            sb.appendLine("if (m${n}_$j.find()) { vars.put(${javaString(varName)}, m${n}_$j.group(1)); " +
                "logging.logToOutput(${javaString("  extracted $varName=")} + m${n}_$j.group(1)); }")
        }
        sb.appendLine()
    }
    sb.appendLine("logging.logToOutput(${javaString("Chain complete: ${req.name}")});")
    return sb.toString()
}

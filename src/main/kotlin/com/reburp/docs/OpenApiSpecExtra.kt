package com.reburp

/**
 * Extension points for the OpenAPI document.
 *
 * The original spec in OpenApiSpec.kt interpolates these three functions at the end of
 * its `tags` array, its `paths` object and its `components.schemas` object. Each returns
 * text beginning with a comma so it can be appended to a non-empty list, or an empty
 * string when there is nothing to add.
 *
 * Adding a route group means writing a `*Paths()` fragment and a `*Schemas()` fragment,
 * then listing them here. Nothing else in the spec needs to change.
 */

private fun joinFragments(fragments: List<String>): String {
    val kept = fragments.map { it.trim() }.filter { it.isNotEmpty() }
    if (kept.isEmpty()) return ""
    return ",\n" + kept.joinToString(",\n")
}

internal fun extraTags(): String = joinFragments(
    listOf(
        """{ "name": "Numbers",      "description": "Base conversion between binary, octal, decimal and hex" }""",
        """{ "name": "JSON",         "description": "JSON validation, pointer reads and edits, and structural inspection" }""",
        """{ "name": "Bytes",        "description": "Raw byte search, slicing and inspection" }""",
        """{ "name": "Ranking",      "description": "Interest ranking over proxy history" }""",
        """{ "name": "Shell",        "description": "OS command execution. Disabled by default." }""",
        """{ "name": "Extension Data", "description": "Typed key/value storage held in the Burp project file" }""",
        """{ "name": "Logging",      "description": "Write into Burp's extension output, error log and suite event log" }""",
        """{ "name": "Meta",         "description": "Burp version components, extension load info and enum vocabularies" }""",
        """{ "name": "Messages",     "description": "Parse, inspect and rewrite HTTP requests and responses" }""",
        """{ "name": "Events",       "description": "Passive traffic observers and the events they capture" }""",
        // Declared here rather than in OpenApiSpec.kt's own tag list, which omitted it even
        // though its WebSocket operations were already tagged with it.
        """{ "name": "WebSocket Client", "description": "Outbound WebSocket connections opened by this extension" }""",
        """{ "name": "Request Engine", "description": "High-throughput async request execution engine (Burp 2026.x http.execution)" }"""
    )
)

internal fun extraPaths(): String = joinFragments(
    listOf(
        numberPaths(),
        jsonPaths(),
        bytesPaths(),
        rankingPaths(),
        shellPaths(),
        extensionDataPaths(),
        corePaths(),
        messagePaths(),
        eventsPaths(),
        requestEnginePaths()
    )
)

internal fun extraSchemas(): String = joinFragments(
    listOf(
        utilsSchemas(),
        extensionDataSchemas(),
        coreSchemas(),
        messageSchemas(),
        eventsSchemas(),
        requestEngineSchemas()
    )
)

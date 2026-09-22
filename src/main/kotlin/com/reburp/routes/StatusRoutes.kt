package com.reburp.routes

import burp.api.montoya.MontoyaApi
import com.reburp.REBURP_VERSION
import com.reburp.StatusResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.statusRoutes(api: MontoyaApi, port: Int) {
    get("/api/status") {
        val version = api.burpSuite().version()
        call.respond(
            StatusResponse(
                extension = "reburp",
                extension_version = REBURP_VERSION,
                version = version.name(),
                edition = version.edition().name,
                port = port,
                docs_url = docsUrl(call.request.headers, port),
                project_name = api.project().name(),
                project_id = api.project().id(),
                command_line_args = api.burpSuite().commandLineArguments()
            )
        )
    }
}

/**
 * The docs URL as the caller reached this server, so it still works through an SSH tunnel on
 * another port or a reverse proxy. Proxies that change the scheme or add a path prefix say so in
 * X-Forwarded-Proto / X-Forwarded-Prefix; without them the prefix cannot be known.
 */
internal fun docsUrl(headers: Headers, port: Int): String {
    val scheme = headers["X-Forwarded-Proto"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() } ?: "http"
    val host   = headers[HttpHeaders.Host]?.takeIf { it.isNotBlank() } ?: "127.0.0.1:$port"
    val prefix = headers["X-Forwarded-Prefix"]?.trim('/', ' ')?.takeIf { it.isNotEmpty() }?.let { "/$it" } ?: ""
    return "$scheme://$host$prefix/docs"
}

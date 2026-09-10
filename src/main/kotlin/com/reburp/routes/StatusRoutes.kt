package com.reburp.routes

import burp.api.montoya.MontoyaApi
import com.reburp.StatusResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.statusRoutes(api: MontoyaApi, port: Int) {
    get("/api/status") {
        val version = api.burpSuite().version()
        call.respond(
            StatusResponse(
                extension = "reburp",
                version = version.name(),
                edition = version.edition().name,
                port = port,
                docs_url = "http://localhost:$port/docs",
                project_name = api.project().name(),
                project_id = api.project().id(),
                command_line_args = api.burpSuite().commandLineArguments()
            )
        )
    }
}

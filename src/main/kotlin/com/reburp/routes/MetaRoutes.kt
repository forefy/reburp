package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.HighlightColor
import burp.api.montoya.organizer.OrganizerItemStatus
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class BurpVersionDto(
    val name: String,
    val major: String,
    val minor: String,
    val build: String,
    val build_number: Long,
    val edition: String,
    val edition_display_name: String,
    val full_version: String
)

@Serializable
data class ExtensionInfoDto(
    val filename: String,
    val is_bapp: Boolean,
    val rest_port: Int
)

@Serializable
data class EnumValueDto(val value: String, val display_name: String)

@Serializable
data class EnumCatalogDto(
    val highlight_colors: List<EnumValueDto>,
    val organizer_statuses: List<EnumValueDto>
)

@Serializable
data class UnloadInput(val confirm: Boolean = false)

/**
 * Burp and extension metadata that sits outside any single tool.
 *
 * /api/status already reports a short summary for health checks. These endpoints expose
 * the version broken into its parts, which is what you want when gating behaviour on a
 * specific Burp build, plus the enum vocabularies other endpoints accept.
 */
fun Routing.metaRoutes(api: MontoyaApi, port: Int) {
    route("/api/meta") {

        // Burp's version, split into its components.
        get("/version") {
            val version = api.burpSuite().version()
            call.respond(
                BurpVersionDto(
                    name = runCatching { version.name() }.getOrElse { "Burp Suite" },
                    major = runCatching { version.major() }.getOrElse { "" },
                    minor = runCatching { version.minor() }.getOrElse { "" },
                    build = runCatching { version.build() }.getOrElse { "" },
                    build_number = runCatching { version.buildNumber() }.getOrElse { 0L },
                    edition = runCatching { version.edition().name }.getOrElse { "UNKNOWN" },
                    edition_display_name = runCatching { version.edition().displayName() }.getOrElse { "Unknown" },
                    full_version = runCatching { version.toString() }.getOrElse { "" }
                )
            )
        }

        // How Burp loaded this extension.
        get("/extension") {
            call.respond(
                ExtensionInfoDto(
                    filename = runCatching { api.extension().filename() }.getOrElse { "" },
                    is_bapp = runCatching { api.extension().isBapp() }.getOrElse { false },
                    rest_port = port
                )
            )
        }

        // Enum vocabularies accepted elsewhere in this API, with Burp's own display labels.
        get("/enums") {
            call.respond(
                EnumCatalogDto(
                    highlight_colors = HighlightColor.values().map {
                        EnumValueDto(it.name, runCatching { it.displayName() }.getOrElse { _ -> it.name })
                    },
                    organizer_statuses = OrganizerItemStatus.values().map {
                        EnumValueDto(it.name, runCatching { it.displayName() }.getOrElse { _ -> it.name })
                    }
                )
            )
        }

        // Unload this extension. This also stops the REST server serving this request.
        post("/extension/unload") {
            val req = runCatching { call.receive<UnloadInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (!req.confirm) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Set 'confirm' to true. Unloading stops this REST API, and it can only be " +
                                  "started again from Burp's Extensions tab."))
            }
            call.respond(MessageResponse("Unloading extension. This REST API stops responding once it completes."))
            // Unload after the response has been written, on a thread that is not serving it.
            Thread {
                runCatching {
                    Thread.sleep(250)
                    api.extension().unload()
                }
            }.apply { isDaemon = true }.start()
        }
    }
}

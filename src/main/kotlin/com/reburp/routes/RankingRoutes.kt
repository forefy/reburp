package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.utilities.rank.RankingAlgorithm
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class RankInput(
    val limit: Int = 100,
    val offset: Int = 0,
    val algorithm: String? = null,
    val scope_only: Boolean = false,
    val host: String? = null
)

@Serializable
data class RankedEntry(
    val rank: Int,
    val url: String,
    val method: String?,
    val status: Int?,
    val response_length: Int
)

@Serializable
data class RankResult(
    val algorithm: String,
    val considered: Int,
    val ranked: List<RankedEntry>
)

/**
 * Interest ranking over proxy history, backed by Montoya's RankingUtils.
 *
 * Burp scores each exchange for how anomalous it looks relative to the rest of the
 * supplied set, so the input set materially changes the scores. Filter deliberately.
 */
fun Routing.rankingRoutes(api: MontoyaApi) {
    route("/api/utils/rank") {

        post {
            val req = runCatching { call.receive<RankInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.limit < 1 || req.limit > 1000) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'limit': ${req.limit}. Must be between 1 and 1000."))
            }
            if (req.offset < 0) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'offset': ${req.offset}. Must be zero or greater."))
            }
            val algorithm: RankingAlgorithm? = req.algorithm?.let { name ->
                runCatching { RankingAlgorithm.valueOf(name.trim().uppercase()) }.getOrElse {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid 'algorithm': '$name'. Allowed values: ${RankingAlgorithm.values().joinToString(", ") { a -> a.name }}")
                    )
                }
            }

            var history = api.proxy().history()
            if (req.scope_only) {
                history = history.filter { runCatching { api.scope().isInScope(it.request().url()) }.getOrElse { false } }
            }
            if (req.host != null) {
                history = history.filter { runCatching { it.host() }.getOrElse { "" }.equals(req.host, ignoreCase = true) }
            }
            val window = history.drop(req.offset).take(req.limit)
            if (window.isEmpty()) {
                return@post call.respond(RankResult(algorithm?.name ?: "DEFAULT", 0, emptyList()))
            }

            val messages: Collection<HttpRequestResponse> = window.mapNotNull { entry ->
                runCatching {
                    HttpRequestResponse.httpRequestResponse(entry.request(), entry.response())
                }.getOrNull()
            }

            val ranked = runCatching {
                if (algorithm != null) api.utilities().rankingUtils().rank(messages, algorithm)
                else api.utilities().rankingUtils().rank(messages)
            }.getOrElse {
                return@post call.respond(HttpStatusCode.InternalServerError,
                    ErrorResponse("Ranking failed: ${it.message}"))
            }

            call.respond(
                RankResult(
                    algorithm = algorithm?.name ?: "DEFAULT",
                    considered = messages.size,
                    ranked = ranked.map { entry ->
                        val rr = entry.requestResponse()
                        RankedEntry(
                            rank = entry.rank(),
                            url = runCatching { rr.request().url() }.getOrElse { "" },
                            method = runCatching { rr.request().method() }.getOrNull(),
                            status = runCatching { rr.response()?.statusCode()?.toInt() }.getOrNull(),
                            response_length = runCatching { rr.response()?.toString()?.length ?: 0 }.getOrElse { 0 }
                        )
                    }
                )
            )
        }
    }
}

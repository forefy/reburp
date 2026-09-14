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
import java.util.IdentityHashMap

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
    /** Burp's proxy-history id, so results join against /api/proxy/history. -1 if unresolvable. */
    val id: Int,
    val rank: Int,
    val url: String,
    val method: String?,
    val status: Int?,
    val response_length: Int
)

@Serializable
data class RankResult(
    val algorithm: String,
    /** How many exchanges were actually scored (the whole filtered set, subject to [MAX_SCORED]). */
    val considered: Int,
    /** True when the filtered set exceeded [MAX_SCORED] and only the most recent were scored. */
    val truncated: Boolean,
    val ranked: List<RankedEntry>
)

/**
 * Upper bound on how many exchanges we hand to Burp's ranker in one call.
 * Scores are relative to the supplied set, so this also bounds how much the
 * set can drift between calls. The most recent entries are kept.
 */
private const val MAX_SCORED = 2000

/** Identity-independent key for matching a ranked result back to its history entry. */
private fun rankKey(rr: HttpRequestResponse): String =
    runCatching { "${rr.request().method()} ${rr.request().url()}" }.getOrElse { "" }

/**
 * Interest ranking over proxy history, backed by Montoya's RankingUtils.
 *
 * Burp scores each exchange for how anomalous it looks relative to the rest of the
 * supplied set, so the input set materially changes the scores. Filter deliberately
 * with [RankInput.scope_only] and [RankInput.host].
 *
 * The whole filtered set is scored (capped at [MAX_SCORED], most recent kept); results
 * are sorted by descending rank and only then paginated, so `offset`/`limit` select a
 * window into the *ranked* output rather than deciding which entries get scored.
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
                history = history.filter { e -> runCatching { e.host() }.recoverCatching { e.httpService().host() }.getOrElse { "" }.equals(req.host, ignoreCase = true) }
            }
            // Score the whole filtered set, not the requested page: Burp's scores are
            // relative to the supplied set, so slicing first would score each page
            // against a different population and could never yield a global top-N.
            // offset/limit are applied to the *sorted results* further down.
            val truncated = history.size > MAX_SCORED
            val window = if (truncated) history.takeLast(MAX_SCORED) else history
            if (window.isEmpty()) {
                return@post call.respond(RankResult(algorithm?.name ?: "DEFAULT", 0, false, emptyList()))
            }

            // Keep each constructed message tied to its originating history id so the
            // ranked output can be joined back to /api/proxy/history. Montoya is not
            // documented to hand back the same instances, so carry a method+URL fallback
            // for when identity lookup misses.
            val idByMessage = IdentityHashMap<HttpRequestResponse, Int>()
            val idsByKey = HashMap<String, ArrayDeque<Int>>()
            val messages: Collection<HttpRequestResponse> = window.mapNotNull { entry ->
                runCatching {
                    HttpRequestResponse.httpRequestResponse(entry.request(), entry.response())
                }.getOrNull()?.also { msg ->
                    val id = runCatching { entry.id() }.getOrElse { -1 }
                    idByMessage[msg] = id
                    idsByKey.getOrPut(rankKey(msg)) { ArrayDeque() }.addLast(id)
                }
            }

            val ranked = runCatching {
                if (algorithm != null) api.utilities().rankingUtils().rank(messages, algorithm)
                else api.utilities().rankingUtils().rank(messages)
            }.getOrElse {
                return@post call.respond(HttpStatusCode.InternalServerError,
                    ErrorResponse("Ranking failed: ${it.message}"))
            }

            // Montoya returns the ranked collection in the order it was supplied, not by
            // score, so sort here before paginating. Highest rank = most anomalous.
            val page = ranked
                .sortedByDescending { it.rank() }
                .drop(req.offset)
                .take(req.limit)

            call.respond(
                RankResult(
                    algorithm = algorithm?.name ?: "DEFAULT",
                    considered = messages.size,
                    truncated = truncated,
                    ranked = page.map { entry ->
                        val rr = entry.requestResponse()
                        RankedEntry(
                            id = idByMessage[rr] ?: idsByKey[rankKey(rr)]?.removeFirstOrNull() ?: -1,
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

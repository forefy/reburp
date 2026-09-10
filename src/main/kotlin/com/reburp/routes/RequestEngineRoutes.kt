package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.execution.CompletionHandler
import burp.api.montoya.http.execution.ExecutionStats
import burp.api.montoya.http.execution.RequestEngineOptions
import burp.api.montoya.http.execution.RequestExecution
import burp.api.montoya.http.execution.RequestExecutionEngine
import burp.api.montoya.http.execution.RequestExecutionResult
import burp.api.montoya.http.execution.ResourcePool
import burp.api.montoya.http.message.requests.HttpRequest
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * REST surface for Montoya's high-throughput request execution engine
 * (`burp.api.montoya.http.execution`, added in Burp 2026.x).
 *
 * The engine is stateful and asynchronous, so it is exposed as server-side handles:
 * create an engine, queue requests, start it (which yields an execution handle), then poll
 * stats / drive the lifecycle / collect results. Handles live until deleted or the extension
 * unloads.
 *
 * `awaitCompletion()` blocks, so it is run on an IO dispatcher with a bounded timeout and
 * never on the Netty event loop - a hung upstream must not wedge the whole REST server.
 */

private val engines = ConcurrentHashMap<String, RequestExecutionEngine>()
private val executions = ConcurrentHashMap<String, RequestExecution>()
private val engineSeq = AtomicLong(0)
private val execSeq = AtomicLong(0)

private fun ExecutionStats.toDto() = ExecutionStatsDto(
    requested = requested(),
    completed = completed(),
    failed = failed(),
    in_flight = inFlight(),
    pending = pending(),
    elapsed_ms = elapsed().toMillis()
)

private fun buildEngineRaw(host: String, method: String, path: String,
                           headers: Map<String, String>?, body: JsonElement?): String {
    val bodyStr = when {
        body == null                           -> ""
        body is JsonPrimitive && body.isString -> body.content
        else                                   -> body.toString()
    }
    return buildString {
        append("${method.uppercase()} $path HTTP/1.1\r\n")
        append("Host: $host\r\n")
        if (bodyStr.isNotBlank()) {
            if (headers?.keys?.none { it.equals("content-type", true) } != false)
                append("Content-Type: application/json\r\n")
            if (headers?.keys?.none { it.equals("content-length", true) } != false)
                append("Content-Length: ${bodyStr.toByteArray().size}\r\n")
        }
        headers?.forEach { (k, v) -> append("$k: $v\r\n") }
        append("\r\n")
        if (bodyStr.isNotBlank()) append(bodyStr)
    }
}

private fun EngineRequestItem.toHttpRequest(): HttpRequest {
    val service = httpService(host, port, use_https)
    val raw = when {
        request != null                 -> normalizeRequest(request)
        method != null && path != null  -> buildEngineRaw(host, method, path, headers, body)
        else -> throw IllegalArgumentException("each request needs 'request' or 'method'+'path'")
    }
    return HttpRequest.httpRequest(service, raw)
}

fun Routing.requestEngineRoutes(api: MontoyaApi, activityLog: ActivityLogTab? = null) {

    route("/api/http/engine") {

        // ── Create an engine ──────────────────────────────────────────────────
        post {
            val req = runCatching { call.receive<CreateEngineRequest>() }.getOrElse { CreateEngineRequest() }

            val bespoke = req.name != null || req.concurrent_request_limit != null ||
                req.throttle_ms != null || req.max_retries != null ||
                req.resource_pool_name != null || req.default_pool

            val engine = if (!bespoke) {
                // Simplest path: engine on Burp's default settings.
                api.http().createRequestEngine()
            } else {
                val pool = when {
                    req.resource_pool_name != null -> ResourcePool.existingResourcePool(req.resource_pool_name)
                    req.default_pool               -> ResourcePool.defaultResourcePool()
                    else -> {
                        var p = ResourcePool.resourcePool()
                        req.concurrent_request_limit?.let { p = p.withConcurrentRequestLimit(it) }
                        req.throttle_ms?.let { p = p.withThrottle(Duration.ofMillis(it)) }
                        req.max_retries?.let { p = p.withMaxRetries(it) }
                        p
                    }
                }
                val options = RequestEngineOptions.requestEngineOptions()
                    .withName(req.name ?: "reburp-engine")
                    .withResourcePool(pool)
                api.http().createRequestEngine(options)
            }

            val id = "eng-${engineSeq.incrementAndGet()}"
            engines[id] = engine
            call.respond(EngineCreated(id, req.name))
        }

        // ── List handles ──────────────────────────────────────────────────────
        get {
            call.respond(EngineListDto(engines.keys.sorted(), executions.keys.sorted()))
        }

        // ── Queue requests onto an engine (before it is started) ───────────────
        post("/{id}/queue") {
            val id = call.parameters["id"]!!
            val engine = engines[id] ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("no such engine '$id'"))
            val body = runCatching { call.receive<QueueRequestsRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid body: ${it.message}"))
            }
            var n = 0
            for (item in body.requests) {
                val httpReq = runCatching { item.toHttpRequest() }.getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "bad request item"))
                }
                if (item.label != null) engine.queue(httpReq, item.label) else engine.queue(httpReq)
                n++
            }
            call.respond(QueuedResponse(id, n))
        }

        // ── Start the engine: send everything queued ───────────────────────────
        post("/{id}/send") {
            val id = call.parameters["id"]!!
            val engine = engines[id] ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("no such engine '$id'"))
            val opts = runCatching { call.receive<SendAllRequest>() }.getOrElse { SendAllRequest() }

            val execution = if (opts.timeout_ms != null) engine.sendAll(Duration.ofMillis(opts.timeout_ms))
                            else engine.sendAll()

            // Log completion asynchronously; Burp invokes this on its own thread.
            runCatching {
                execution.lifetime().onComplete(CompletionHandler { result ->
                    val s = result.stats()
                    api.logging().logToOutput(
                        "reburp engine '$id' finished: ${s.completed()}/${s.requested()} completed, " +
                        "${s.failed()} failed, cancelled=${result.cancelled()}"
                    )
                })
            }

            val execId = "exec-${execSeq.incrementAndGet()}"
            executions[execId] = execution
            call.respond(ExecutionCreated(execId))
        }

        // ── Queue more onto a running execution ─────────────────────────────────
        post("/execution/{id}/queue") {
            val id = call.parameters["id"]!!
            val execution = executions[id] ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("no such execution '$id'"))
            val body = runCatching { call.receive<QueueRequestsRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid body: ${it.message}"))
            }
            var n = 0
            for (item in body.requests) {
                val httpReq = runCatching { item.toHttpRequest() }.getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "bad request item"))
                }
                if (item.label != null) execution.queue(httpReq, item.label) else execution.queue(httpReq)
                n++
            }
            call.respond(QueuedResponse(id, n))
        }

        // ── Live stats ──────────────────────────────────────────────────────────
        get("/execution/{id}/stats") {
            val id = call.parameters["id"]!!
            val execution = executions[id] ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("no such execution '$id'"))
            call.respond(execution.stats().toDto())
        }

        // ── Finished? ─────────────────────────────────────────────────────────
        get("/execution/{id}/finished") {
            val id = call.parameters["id"]!!
            val execution = executions[id] ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("no such execution '$id'"))
            call.respond(FinishedDto(execution.lifetime().finished()))
        }

        // ── Lifecycle controls ──────────────────────────────────────────────────
        post("/execution/{id}/pause") {
            val id = call.parameters["id"]!!
            val execution = executions[id] ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("no such execution '$id'"))
            execution.lifetime().pause()
            call.respond(MessageResponse("paused"))
        }
        post("/execution/{id}/resume") {
            val id = call.parameters["id"]!!
            val execution = executions[id] ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("no such execution '$id'"))
            execution.lifetime().resume()
            call.respond(MessageResponse("resumed"))
        }
        post("/execution/{id}/cancel") {
            val id = call.parameters["id"]!!
            val execution = executions[id] ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("no such execution '$id'"))
            execution.lifetime().cancel()
            call.respond(MessageResponse("cancelled"))
        }

        // ── Block for completion, bounded, off the event loop ────────────────────
        post("/execution/{id}/await") {
            val id = call.parameters["id"]!!
            val execution = executions[id] ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("no such execution '$id'"))
            val req = runCatching { call.receive<AwaitRequest>() }.getOrElse { AwaitRequest() }
            val budget = (req.timeout_ms ?: 30000).coerceIn(1, 600_000)

            // awaitCompletion() blocks; run it on IO and cap how long we wait so a slow or
            // stuck upstream cannot hold a request open indefinitely.
            val result: RequestExecutionResult? = withContext(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                while (!execution.lifetime().finished() && System.currentTimeMillis() - start < budget) {
                    Thread.sleep(100)
                }
                if (execution.lifetime().finished()) execution.lifetime().awaitCompletion() else null
            }

            if (result == null) {
                // Not done within the budget: hand back current stats instead of blocking.
                call.respond(HttpStatusCode.Accepted, ExecutionResultDto(
                    cancelled = false, timed_out = true,
                    stats = execution.stats().toDto(), results = emptyList()
                ))
                return@post
            }

            val results = result.results().map { r ->
                RequestResultDto(
                    label = r.label(),
                    status = r.status().name,
                    request_response = runCatching { r.requestResponse().toDto(req.include_body) }.getOrNull()
                )
            }
            call.respond(ExecutionResultDto(
                cancelled = result.cancelled(),
                timed_out = false,
                stats = result.stats().toDto(),
                results = results
            ))
        }

        // ── Cleanup ─────────────────────────────────────────────────────────────
        delete("/execution/{id}") {
            val id = call.parameters["id"]!!
            if (executions.remove(id) == null) return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("no such execution '$id'"))
            call.respond(MessageResponse("removed execution '$id'"))
        }
        delete("/{id}") {
            val id = call.parameters["id"]!!
            if (engines.remove(id) == null) return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("no such engine '$id'"))
            call.respond(MessageResponse("removed engine '$id'"))
        }
    }
}

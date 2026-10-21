package com.reburp

import burp.api.montoya.MontoyaApi
import com.reburp.routes.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val responseBodyAttr = AttributeKey<String>("reburpRespBody")
internal val skipLogAttr     = AttributeKey<Boolean>("reburpSkipLog")

// Paths that are noise - skip logging them
private val SKIP_PATHS = setOf("/docs", "/openapi.json", "/favicon.ico")

class RestApiServer(private val api: MontoyaApi, val port: Int = 9090, private val activityLog: ActivityLogTab? = null) {

    private var engine: EmbeddedServer<*, *>? = null

    fun start() {
        engine = embeddedServer(Netty, host = "127.0.0.1", port = port) {

            // DoubleReceive allows reading the request body in the monitoring intercept
            // without consuming it before the route handler gets it.
            install(DoubleReceive)

            install(ContentNegotiation) {
                json(Json { prettyPrint = true; ignoreUnknownKeys = true })
            }

            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Patch)
                allowMethod(HttpMethod.Delete)
            }

            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(cause.message ?: "Internal error")
                    )
                }
            }

            // Installed AFTER ContentNegotiation so transformBody sees the serialised TextContent,
            // not the raw Kotlin object.
            if (activityLog != null) {
                install(createApplicationPlugin("reburpRespCapture") {
                    onCallRespond { call ->
                        transformBody { body: Any ->
                            val text = when (body) {
                                is TextContent          -> body.text
                                is ByteArrayContent     -> body.bytes().let { b ->
                                    if (b.size <= 1_048_576) b.decodeToString() else b.decodeToString(0, 1_048_576)
                                }
                                else                    -> null
                            }
                            if (text != null) call.attributes.put(responseBodyAttr, text)
                            body
                        }
                    }
                })
            }

            // Registered on the application pipeline rather than inside routing(): the
            // Route.intercept overload is deprecated, and this needs to see every call
            // anyway, not just routed ones.
            if (activityLog != null) {
                intercept(ApplicationCallPipeline.Monitoring) {
                    val method = call.request.httpMethod.value
                    val path   = call.request.uri

                    if (path in SKIP_PATHS || path.startsWith("/openapi")) {
                        proceed()
                        return@intercept
                    }

                    val start      = System.currentTimeMillis()
                    val reqHeaders = call.request.headers.entries()
                        .filter { (k, _) -> k.lowercase() !in setOf("accept-encoding", "user-agent", "connection") }
                        .joinToString("\r\n") { (k, v) -> "$k: ${v.joinToString(", ")}" }
                    val reqBody    = try { call.receiveText() } catch (_: Exception) { "" }

                    // Extract session_id if the body is JSON and contains it
                    val sessionId = runCatching {
                        Json.parseToJsonElement(reqBody).jsonObject["session_id"]?.jsonPrimitive?.content
                    }.getOrNull()

                    proceed()

                    // Route may have self-logged PoC entries and marked this to skip
                    if (call.attributes.getOrNull(skipLogAttr) == true) return@intercept

                    val duration = System.currentTimeMillis() - start
                    val status   = call.response.status()?.value ?: 0
                    val respBody = call.attributes.getOrNull(responseBodyAttr) ?: ""

                    activityLog.log(method, path, status, duration, reqHeaders, reqBody, respBody, sessionId)
                }
            }

            routing {
                docsRoutes()
                statusRoutes(api, port)
                proxyRoutes(api)
                siteMapRoutes(api)
                httpRoutes(api, activityLog)
                requestEngineRoutes(api, activityLog)
                scannerRoutes(api)
                configRoutes(api)
                collaboratorRoutes(api)
                utilityRoutes(api)
                issueRoutes(api)
                persistenceRoutes(api)
                organizerRoutes(api)
                aiRoutes(api)
                webSocketClientRoutes(api)
                engagementRoutes(api)
                scopeRoutes(api)
                repeaterIntruderRoutes(api)
                matchReplaceRoutes(api)
                bambdaRoutes(api)
                sessionRoutes(api)
                numberRoutes(api)
                jsonRoutes(api)
                bytesRoutes(api)
                rankingRoutes(api)
                shellRoutes(api)
                loggingRoutes(api)
                metaRoutes(api, port)
                collaboratorExtraRoutes(api)
                scannerExtraRoutes(api)
                extensionDataRoutes(api)
                messageRoutes(api)
                eventsRoutes(api)
                uiRoutes(api)
                uiElementRoutes(api)
                if (activityLog != null) logRoutes(api, activityLog)
            }
        }.start(wait = false)
    }

    fun stop() {
        // Bounded shutdown so unloading the extension never hangs Burp, and null the engine so a
        // repeated stop is a no-op. Ktor's stop() awaits the Netty event loops with no timeout of
        // its own, and a loop whose thread is still inside a long handler (a huge response being
        // serialised) never finishes - that wedged Burp's reload for good once. So run it on a
        // helper thread and wait a bounded time; the listening socket is closed first either way,
        // and a leaked loop dies with its handler.
        val e = engine ?: return
        engine = null
        val worker = Thread({ runCatching { e.stop(500, 2_000) } }, "reburp-stop").apply { isDaemon = true }
        worker.start()
        worker.join(5_000)
    }
}

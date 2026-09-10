package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.Registration
import burp.api.montoya.core.ToolSource
import burp.api.montoya.core.ToolType
import burp.api.montoya.http.handler.HttpHandler
import burp.api.montoya.http.handler.HttpRequestToBeSent
import burp.api.montoya.http.handler.HttpResponseReceived
import burp.api.montoya.http.handler.RequestToBeSentAction
import burp.api.montoya.http.handler.ResponseReceivedAction
import burp.api.montoya.http.handler.TimingData
import burp.api.montoya.http.message.StatusCodeClass
import burp.api.montoya.http.message.params.HttpParameterType
import burp.api.montoya.http.sessions.ActionResult
import burp.api.montoya.http.sessions.SessionHandlingAction
import burp.api.montoya.http.sessions.SessionHandlingActionData
import burp.api.montoya.proxy.http.InterceptedRequest
import burp.api.montoya.proxy.http.InterceptedResponse
import burp.api.montoya.proxy.http.ProxyRequestHandler
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction
import burp.api.montoya.proxy.http.ProxyResponseHandler
import burp.api.montoya.proxy.http.ProxyResponseReceivedAction
import burp.api.montoya.proxy.http.ProxyResponseToBeSentAction
import burp.api.montoya.proxy.websocket.BinaryMessageReceivedAction
import burp.api.montoya.proxy.websocket.BinaryMessageToBeSentAction
import burp.api.montoya.proxy.websocket.InterceptedBinaryMessage
import burp.api.montoya.proxy.websocket.InterceptedTextMessage
import burp.api.montoya.proxy.websocket.ProxyMessageHandler
import burp.api.montoya.proxy.websocket.ProxyWebSocketCreation
import burp.api.montoya.proxy.websocket.ProxyWebSocketCreationHandler
import burp.api.montoya.proxy.websocket.TextMessageReceivedAction
import burp.api.montoya.proxy.websocket.TextMessageToBeSentAction
import burp.api.montoya.scope.ScopeChange
import burp.api.montoya.scope.ScopeChangeHandler
import burp.api.montoya.websocket.WebSocketCreated
import burp.api.montoya.websocket.WebSocketCreatedHandler
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Hard cap on the number of events held in memory. Oldest events are dropped first. */
private const val MAX_EVENTS = 1000

/** How many payload characters of a websocket message are kept in an event. */
private const val MAX_PAYLOAD_PREVIEW = 200

/** Upper bound on markers copied into a single event. */
private const val MAX_MARKERS = 20

/**
 * How many times a single proxy websocket will consult `Proxy.webSocketHistory()` while
 * trying to resolve its Burp websocket id. Bounded so a chatty socket cannot turn every
 * message into a full history scan.
 */
private const val MAX_WEBSOCKET_HISTORY_LOOKUPS = 5

/** Name shown in Burp's session handling rule editor for the observing action. */
private const val SESSION_ACTION_NAME = "REST API event observer (passive)"

private const val OBSERVER_HTTP_REQUEST = "HTTP_REQUEST"
private const val OBSERVER_HTTP_RESPONSE = "HTTP_RESPONSE"
private const val OBSERVER_PROXY_REQUEST = "PROXY_REQUEST"
private const val OBSERVER_PROXY_RESPONSE = "PROXY_RESPONSE"
private const val OBSERVER_PROXY_WEBSOCKET_CREATION = "PROXY_WEBSOCKET_CREATION"
private const val OBSERVER_WEBSOCKET_CREATION = "WEBSOCKET_CREATION"
private const val OBSERVER_SCOPE_CHANGE = "SCOPE_CHANGE"
private const val OBSERVER_SESSION_HANDLING = "SESSION_HANDLING"

/** Request headers whose presence and value are recorded on every captured request. */
private val PROBED_REQUEST_HEADERS = listOf("Host", "User-Agent", "Cookie", "Referer", "Content-Type", "Authorization")

/** Response headers whose presence and value are recorded on every captured response. */
private val PROBED_RESPONSE_HEADERS = listOf("Content-Type", "Content-Length", "Server", "Location", "Set-Cookie")

/** Parameters probed on every captured request, as `name` to `type` pairs. */
private val PROBED_PARAMETERS = listOf(
    "id" to HttpParameterType.URL,
    "token" to HttpParameterType.URL,
    "redirect" to HttpParameterType.URL,
    "csrf" to HttpParameterType.BODY
)

/** Cookie names probed on every captured proxy response. */
private val PROBED_COOKIES = listOf("session", "sessionid", "JSESSIONID", "PHPSESSID")

/** Keywords counted in every captured proxy response body. */
private val PROBED_KEYWORDS = arrayOf("error", "exception", "password", "token", "admin")

// ---------------------------------------------------------------------------
// Wire models
// ---------------------------------------------------------------------------

@Serializable
data class ObservedToolSource(
    val tool_type: String,
    val tool_name: String,
    val is_from_proxy: Boolean,
    val is_from_scanner_or_intruder: Boolean
)

@Serializable
data class ObservedHeader(val name: String, val present: Boolean, val value: String? = null)

@Serializable
data class ObservedParameter(val name: String, val type: String, val present: Boolean, val value: String? = null)

@Serializable
data class ObservedCookie(val name: String, val present: Boolean, val value: String? = null)

@Serializable
data class ObservedKeyword(val keyword: String, val count: Int)

@Serializable
data class ObservedMarker(val start_index_inclusive: Int, val end_index_exclusive: Int)

@Serializable
data class ObservedTiming(
    val time_request_sent: String? = null,
    val time_to_start_of_response_millis: Long? = null
)

@Serializable
data class ObservedWebSocketHistory(
    val web_socket_id: Int,
    val upgrade_request_url: String? = null,
    val edited_payload_length: Int? = null
)

/**
 * One captured Burp event. Every field except [id], [timestamp], [observer] and [phase] is
 * optional, because each observer only fills in what its own Montoya callback exposes.
 * Unset fields are omitted from the JSON response.
 */
@Serializable
data class ObservedEvent(
    val id: Long,
    val timestamp: String,
    val observer: String,
    val phase: String,
    val message_id: Int? = null,
    val tool_source: ObservedToolSource? = null,
    val url: String? = null,
    val method: String? = null,
    val path_without_query: String? = null,
    val http_version: String? = null,
    val content_type: String? = null,
    val status_code: Int? = null,
    val reason_phrase: String? = null,
    val status_code_class: String? = null,
    val stated_mime_type: String? = null,
    val inferred_mime_type: String? = null,
    val initiating_request_url: String? = null,
    val body_offset: Int? = null,
    val has_parameters: Boolean? = null,
    val has_url_parameters: Boolean? = null,
    val headers: List<ObservedHeader>? = null,
    val parameters: List<ObservedParameter>? = null,
    val cookies: List<ObservedCookie>? = null,
    val keyword_counts: List<ObservedKeyword>? = null,
    val markers: List<ObservedMarker>? = null,
    val marker_count: Int? = null,
    val source_ip_address: String? = null,
    val destination_ip_address: String? = null,
    val listener_interface: String? = null,
    val upgrade_request_url: String? = null,
    val direction: String? = null,
    val payload_length: Int? = null,
    val payload_preview: String? = null,
    val web_socket_history: ObservedWebSocketHistory? = null,
    val timing: ObservedTiming? = null,
    val macro_request_response_count: Int? = null,
    val note: String? = null
)

@Serializable
data class ObserverState(
    val name: String,
    val enabled: Boolean,
    val description: String,
    val montoya_call: String,
    val buffered_events: Int
)

@Serializable
data class ObserversResponse(
    val observers: List<ObserverState>,
    val buffered_events: Int,
    val buffer_capacity: Int,
    val next_event_id: Long,
    val proxy_intercept_enabled: Boolean
)

@Serializable
data class EventsResponse(
    val total: Int,
    val returned: Int,
    val offset: Int,
    val limit: Int,
    val buffer_capacity: Int,
    val observer: String? = null,
    val events: List<ObservedEvent>
)

// ---------------------------------------------------------------------------
// Event buffer
// ---------------------------------------------------------------------------

private val eventIds = AtomicLong(0)
private val eventBuffer = ConcurrentLinkedDeque<ObservedEvent>()

/** `ConcurrentLinkedDeque.size()` walks the list, so the count is tracked separately. */
private val eventBufferSize = AtomicInteger(0)

/** Registrations for the per-socket proxy websocket message handlers. */
private val proxyWebSocketMessageRegistrations = ConcurrentLinkedQueue<Registration>()

private fun pushEvent(event: ObservedEvent) {
    eventBuffer.addLast(event)
    eventBufferSize.incrementAndGet()
    while (eventBufferSize.get() > MAX_EVENTS) {
        if (eventBuffer.pollFirst() == null) break
        eventBufferSize.decrementAndGet()
    }
}

/**
 * CRITICAL SAFETY: every observer funnels its capture through here, and every failure is
 * swallowed. Burp invokes these handlers on its own traffic threads, so a bug in the
 * capture logic must never escape the callback. If it did, Burp could drop or stall the
 * user's request or response. Capturing is strictly best effort; the handler that called
 * this function always goes on to return the "continue unchanged" action.
 */
private fun capture(build: () -> ObservedEvent) {
    runCatching { pushEvent(build()) }
}

private fun newEventId(): Long = eventIds.incrementAndGet()

private fun nowIso(): String = Instant.now().toString()

// ---------------------------------------------------------------------------
// Detail extraction helpers. All of these are called from inside `capture`.
// ---------------------------------------------------------------------------

private fun describeToolSource(source: ToolSource): ObservedToolSource {
    val type: ToolType = source.toolType()
    return ObservedToolSource(
        tool_type = type.name,
        tool_name = type.toolName(),
        is_from_proxy = source.isFromTool(ToolType.PROXY),
        is_from_scanner_or_intruder = source.isFromTool(ToolType.SCANNER, ToolType.INTRUDER)
    )
}

private fun probeHeaders(names: List<String>, has: (String) -> Boolean, value: (String) -> String?): List<ObservedHeader> =
    names.map { name ->
        val present = has(name)
        ObservedHeader(name, present, if (present) value(name) else null)
    }

private fun markersOf(markers: List<burp.api.montoya.core.Marker>): List<ObservedMarker> =
    markers.take(MAX_MARKERS).map { ObservedMarker(it.range().startIndexInclusive(), it.range().endIndexExclusive()) }

private fun statusClassOf(matches: (StatusCodeClass) -> Boolean): String? =
    StatusCodeClass.values().firstOrNull { matches(it) }?.name

private fun describeTiming(timing: TimingData): ObservedTiming = ObservedTiming(
    time_request_sent = timing.timeRequestSent().toString(),
    time_to_start_of_response_millis = timing.timeBetweenRequestSentAndStartOfResponse()?.toMillis()
)

/** Builds an event from an outgoing request seen by the HTTP or proxy request observers. */
private fun requestEvent(observer: String, phase: String, request: HttpRequestToBeSent): ObservedEvent = ObservedEvent(
    id = newEventId(),
    timestamp = nowIso(),
    observer = observer,
    phase = phase,
    message_id = request.messageId(),
    tool_source = describeToolSource(request.toolSource()),
    url = request.url(),
    method = request.method(),
    path_without_query = request.pathWithoutQuery(),
    http_version = request.httpVersion(),
    content_type = request.contentType().name,
    body_offset = request.bodyOffset(),
    has_parameters = request.hasParameters(),
    has_url_parameters = request.hasParameters(HttpParameterType.URL),
    headers = probeHeaders(PROBED_REQUEST_HEADERS, { request.hasHeader(it) }, { request.headerValue(it) }),
    parameters = PROBED_PARAMETERS.map { (name, type) ->
        val present = request.hasParameter(name, type)
        ObservedParameter(name, type.name, present, if (present) request.parameterValue(name, type) else null)
    },
    markers = markersOf(request.markers()),
    marker_count = request.markers().size
)

/** Builds an event from a response seen by the HTTP response observer. */
private fun responseEvent(observer: String, phase: String, response: HttpResponseReceived): ObservedEvent = ObservedEvent(
    id = newEventId(),
    timestamp = nowIso(),
    observer = observer,
    phase = phase,
    message_id = response.messageId(),
    tool_source = describeToolSource(response.toolSource()),
    url = response.initiatingRequest()?.url(),
    initiating_request_url = response.initiatingRequest()?.url(),
    http_version = response.httpVersion(),
    status_code = response.statusCode().toInt(),
    body_offset = response.bodyOffset(),
    headers = probeHeaders(PROBED_RESPONSE_HEADERS, { response.hasHeader(it) }, { response.headerValue(it) }),
    markers = markersOf(response.markers()),
    marker_count = response.markers().size
)

/** Builds an event from a proxy intercepted request. */
private fun interceptedRequestEvent(phase: String, request: InterceptedRequest): ObservedEvent = ObservedEvent(
    id = newEventId(),
    timestamp = nowIso(),
    observer = OBSERVER_PROXY_REQUEST,
    phase = phase,
    message_id = request.messageId(),
    url = request.url(),
    method = request.method(),
    path_without_query = request.pathWithoutQuery(),
    http_version = request.httpVersion(),
    content_type = request.contentType().name,
    body_offset = request.bodyOffset(),
    has_parameters = request.hasParameters(),
    has_url_parameters = request.hasParameters(HttpParameterType.URL),
    headers = probeHeaders(PROBED_REQUEST_HEADERS, { request.hasHeader(it) }, { request.headerValue(it) }),
    parameters = PROBED_PARAMETERS.map { (name, type) ->
        val present = request.hasParameter(name, type)
        ObservedParameter(name, type.name, present, if (present) request.parameterValue(name, type) else null)
    },
    markers = markersOf(request.markers()),
    marker_count = request.markers().size,
    source_ip_address = request.sourceIpAddress()?.hostAddress,
    destination_ip_address = request.destinationIpAddress()?.hostAddress,
    listener_interface = request.listenerInterface()
)

/** Builds an event from a proxy intercepted response. */
private fun interceptedResponseEvent(phase: String, response: InterceptedResponse): ObservedEvent = ObservedEvent(
    id = newEventId(),
    timestamp = nowIso(),
    observer = OBSERVER_PROXY_RESPONSE,
    phase = phase,
    message_id = response.messageId(),
    url = response.initiatingRequest()?.url(),
    initiating_request_url = response.initiatingRequest()?.url(),
    http_version = response.httpVersion(),
    status_code = response.statusCode().toInt(),
    reason_phrase = response.reasonPhrase(),
    status_code_class = statusClassOf { response.isStatusCodeClass(it) },
    stated_mime_type = response.statedMimeType()?.name,
    inferred_mime_type = response.inferredMimeType()?.name,
    body_offset = response.bodyOffset(),
    headers = probeHeaders(PROBED_RESPONSE_HEADERS, { response.hasHeader(it) }, { response.headerValue(it) }),
    cookies = PROBED_COOKIES.map { name ->
        val present = response.hasCookie(name)
        ObservedCookie(name, present, if (present) response.cookieValue(name) else null)
    },
    keyword_counts = response.keywordCounts(*PROBED_KEYWORDS).filter { it.count() > 0 }
        .map { ObservedKeyword(it.keyword(), it.count()) },
    markers = markersOf(response.markers()),
    marker_count = response.markers().size,
    source_ip_address = response.sourceIpAddress()?.hostAddress,
    destination_ip_address = response.destinationIpAddress()?.hostAddress,
    listener_interface = response.listenerInterface()
)

// ---------------------------------------------------------------------------
// Handlers. Every one of these is passive: it captures and then returns the
// action that leaves the message exactly as Burp handed it over.
// ---------------------------------------------------------------------------

/** Observes outgoing requests. Responses pass straight through untouched. */
private fun httpRequestObserver(): HttpHandler = object : HttpHandler {
    override fun handleHttpRequestToBeSent(request: HttpRequestToBeSent): RequestToBeSentAction {
        capture { requestEvent(OBSERVER_HTTP_REQUEST, "request_to_be_sent", request) }
        return RequestToBeSentAction.continueWith(request, request.annotations())
    }

    override fun handleHttpResponseReceived(response: HttpResponseReceived): ResponseReceivedAction =
        ResponseReceivedAction.continueWith(response, response.annotations())
}

/** Observes received responses. Requests pass straight through untouched. */
private fun httpResponseObserver(): HttpHandler = object : HttpHandler {
    override fun handleHttpRequestToBeSent(request: HttpRequestToBeSent): RequestToBeSentAction =
        RequestToBeSentAction.continueWith(request, request.annotations())

    override fun handleHttpResponseReceived(response: HttpResponseReceived): ResponseReceivedAction {
        capture { responseEvent(OBSERVER_HTTP_RESPONSE, "response_received", response) }
        return ResponseReceivedAction.continueWith(response, response.annotations())
    }
}

/**
 * Observes both proxy request phases. `continueWith` maps to `MessageReceivedAction.CONTINUE`
 * and `MessageToBeSentAction.CONTINUE`, which means Burp keeps applying the user's own
 * interception rules. The observer never intercepts, drops or edits.
 */
private fun proxyRequestObserver(): ProxyRequestHandler = object : ProxyRequestHandler {
    override fun handleRequestReceived(request: InterceptedRequest): ProxyRequestReceivedAction {
        capture { interceptedRequestEvent("proxy_request_received", request) }
        return ProxyRequestReceivedAction.continueWith(request, request.annotations())
    }

    override fun handleRequestToBeSent(request: InterceptedRequest): ProxyRequestToBeSentAction {
        capture { interceptedRequestEvent("proxy_request_to_be_sent", request) }
        return ProxyRequestToBeSentAction.continueWith(request, request.annotations())
    }
}

/** Observes both proxy response phases and always continues with the response unchanged. */
private fun proxyResponseObserver(): ProxyResponseHandler = object : ProxyResponseHandler {
    override fun handleResponseReceived(response: InterceptedResponse): ProxyResponseReceivedAction {
        capture { interceptedResponseEvent("proxy_response_received", response) }
        return ProxyResponseReceivedAction.continueWith(response, response.annotations())
    }

    override fun handleResponseToBeSent(response: InterceptedResponse): ProxyResponseToBeSentAction {
        capture { interceptedResponseEvent("proxy_response_to_be_sent", response) }
        return ProxyResponseToBeSentAction.continueWith(response, response.annotations())
    }
}

/**
 * Passive message handler attached to one proxy websocket. Burp does not hand the websocket
 * id to the creation callback, so it is recovered once from `Proxy.webSocketHistory()` and
 * cached. Lookups are capped by [MAX_WEBSOCKET_HISTORY_LOOKUPS] so a chatty socket cannot
 * turn every frame into a history scan.
 */
private class ProxyWebSocketObserver(
    private val api: MontoyaApi,
    private val upgradeRequestUrl: String?
) : ProxyMessageHandler {

    private val lookupsLeft = AtomicInteger(MAX_WEBSOCKET_HISTORY_LOOKUPS)

    @Volatile
    private var resolved: ObservedWebSocketHistory? = null

    private fun historyDetail(): ObservedWebSocketHistory? {
        resolved?.let { return it }
        if (lookupsLeft.getAndDecrement() <= 0) return null
        val detail = runCatching {
            val latest = api.proxy().webSocketHistory().lastOrNull()
            val url = latest?.upgradeRequest()?.url()
            // Only trust the newest history entry when it belongs to this socket.
            val mismatched = upgradeRequestUrl != null && url != null && url != upgradeRequestUrl
            if (latest == null || mismatched) null
            else ObservedWebSocketHistory(
                web_socket_id = latest.webSocketId(),
                upgrade_request_url = url,
                edited_payload_length = latest.editedPayload()?.length()
            )
        }.getOrNull()
        if (detail != null) resolved = detail
        return detail
    }

    private fun messageEvent(phase: String, direction: String, payload: String, length: Int): ObservedEvent =
        ObservedEvent(
            id = newEventId(),
            timestamp = nowIso(),
            observer = OBSERVER_PROXY_WEBSOCKET_CREATION,
            phase = phase,
            upgrade_request_url = upgradeRequestUrl,
            direction = direction,
            payload_length = length,
            payload_preview = payload.take(MAX_PAYLOAD_PREVIEW),
            web_socket_history = historyDetail()
        )

    override fun handleTextMessageReceived(message: InterceptedTextMessage): TextMessageReceivedAction {
        capture {
            messageEvent("websocket_text_received", message.direction().name, message.payload(), message.payload().length)
        }
        return TextMessageReceivedAction.continueWith(message)
    }

    override fun handleTextMessageToBeSent(message: InterceptedTextMessage): TextMessageToBeSentAction {
        capture {
            messageEvent("websocket_text_to_be_sent", message.direction().name, message.payload(), message.payload().length)
        }
        return TextMessageToBeSentAction.continueWith(message)
    }

    override fun handleBinaryMessageReceived(message: InterceptedBinaryMessage): BinaryMessageReceivedAction {
        capture {
            messageEvent("websocket_binary_received", message.direction().name, "", message.payload().length())
        }
        return BinaryMessageReceivedAction.continueWith(message)
    }

    override fun handleBinaryMessageToBeSent(message: InterceptedBinaryMessage): BinaryMessageToBeSentAction {
        capture {
            messageEvent("websocket_binary_to_be_sent", message.direction().name, "", message.payload().length())
        }
        return BinaryMessageToBeSentAction.continueWith(message)
    }

    override fun onClose() {
        capture {
            ObservedEvent(
                id = newEventId(),
                timestamp = nowIso(),
                observer = OBSERVER_PROXY_WEBSOCKET_CREATION,
                phase = "websocket_closed",
                upgrade_request_url = upgradeRequestUrl,
                web_socket_history = resolved,
                note = "The proxy websocket was closed."
            )
        }
    }
}

/**
 * Observes proxy websocket creation and attaches a passive message handler to each new
 * socket so its frames are observed too. The per-socket registrations are remembered so
 * disabling this observer also detaches them.
 */
private fun proxyWebSocketCreationObserver(api: MontoyaApi): ProxyWebSocketCreationHandler =
    ProxyWebSocketCreationHandler { creation: ProxyWebSocketCreation ->
        val upgradeUrl = runCatching { creation.upgradeRequest()?.url() }.getOrNull()
        capture {
            ObservedEvent(
                id = newEventId(),
                timestamp = nowIso(),
                observer = OBSERVER_PROXY_WEBSOCKET_CREATION,
                phase = "proxy_websocket_created",
                upgrade_request_url = upgradeUrl,
                note = "A proxy websocket was created. A passive message handler was attached to it."
            )
        }
        runCatching {
            val registration = creation.proxyWebSocket().registerProxyMessageHandler(
                ProxyWebSocketObserver(api, upgradeUrl)
            )
            proxyWebSocketMessageRegistrations.add(registration)
        }
    }

/** Observes websocket creation across all Burp tools. The socket itself is left alone. */
private fun webSocketCreatedObserver(): WebSocketCreatedHandler =
    WebSocketCreatedHandler { created: WebSocketCreated ->
        capture {
            ObservedEvent(
                id = newEventId(),
                timestamp = nowIso(),
                observer = OBSERVER_WEBSOCKET_CREATION,
                phase = "websocket_created",
                tool_source = describeToolSource(created.toolSource()),
                upgrade_request_url = created.upgradeRequest()?.url(),
                url = created.upgradeRequest()?.url(),
                note = "A websocket was created. No message handler was attached to it."
            )
        }
    }

/** Observes changes to Burp's target scope. `ScopeChange` carries no detail of its own. */
private fun scopeChangeObserver(): ScopeChangeHandler =
    ScopeChangeHandler { _: ScopeChange ->
        capture {
            ObservedEvent(
                id = newEventId(),
                timestamp = nowIso(),
                observer = OBSERVER_SCOPE_CHANGE,
                phase = "scope_changed",
                note = "Burp target scope changed. The Montoya ScopeChange object carries no further detail."
            )
        }
    }

/**
 * A session handling action that observes but never edits. It returns the request exactly
 * as it arrived. This is the only handler that can see [TimingData], because Montoya only
 * exposes timings on `HttpRequestResponse` and not on the streaming handler messages.
 */
private fun sessionHandlingObserver(): SessionHandlingAction = object : SessionHandlingAction {
    override fun name(): String = SESSION_ACTION_NAME

    override fun performAction(actionData: SessionHandlingActionData): ActionResult {
        capture {
            val macros = actionData.macroRequestResponses()
            val timing: TimingData? = macros.asSequence()
                .map { it.timingData() }
                .firstOrNull { it.isPresent }
                ?.get()
            ObservedEvent(
                id = newEventId(),
                timestamp = nowIso(),
                observer = OBSERVER_SESSION_HANDLING,
                phase = "session_handling_action",
                url = actionData.request()?.url(),
                method = actionData.request()?.method(),
                macro_request_response_count = macros.size,
                timing = timing?.let { describeTiming(it) },
                note = "The request was returned unchanged."
            )
        }
        return ActionResult.actionResult(actionData.request(), actionData.annotations())
    }
}

// ---------------------------------------------------------------------------
// Observer registry
// ---------------------------------------------------------------------------

/** One toggleable observer. Holds the Montoya [Registration] while it is enabled. */
private class ObserverSlot(
    val name: String,
    val description: String,
    val montoyaCall: String,
    val register: (MontoyaApi) -> Registration
) {
    @Volatile
    var registration: Registration? = null

    fun isEnabled(): Boolean = runCatching { registration?.isRegistered() == true }.getOrDefault(false)
}

private val OBSERVERS: Map<String, ObserverSlot> = listOf(
    ObserverSlot(
        OBSERVER_HTTP_REQUEST,
        "Captures every request Burp is about to send, from any tool.",
        "Http.registerHttpHandler"
    ) { api -> api.http().registerHttpHandler(httpRequestObserver()) },

    ObserverSlot(
        OBSERVER_HTTP_RESPONSE,
        "Captures every response Burp receives, from any tool.",
        "Http.registerHttpHandler"
    ) { api -> api.http().registerHttpHandler(httpResponseObserver()) },

    ObserverSlot(
        OBSERVER_PROXY_REQUEST,
        "Captures proxy requests in both the received and to-be-sent phases, with connection detail.",
        "Proxy.registerRequestHandler"
    ) { api -> api.proxy().registerRequestHandler(proxyRequestObserver()) },

    ObserverSlot(
        OBSERVER_PROXY_RESPONSE,
        "Captures proxy responses in both the received and to-be-sent phases, with connection detail.",
        "Proxy.registerResponseHandler"
    ) { api -> api.proxy().registerResponseHandler(proxyResponseObserver()) },

    ObserverSlot(
        OBSERVER_PROXY_WEBSOCKET_CREATION,
        "Captures proxy websocket creation and attaches a passive message handler to each new socket.",
        "Proxy.registerWebSocketCreationHandler and ProxyWebSocket.registerProxyMessageHandler"
    ) { api -> api.proxy().registerWebSocketCreationHandler(proxyWebSocketCreationObserver(api)) },

    ObserverSlot(
        OBSERVER_WEBSOCKET_CREATION,
        "Captures websocket creation across all Burp tools, including the upgrade request URL.",
        "WebSockets.registerWebSocketCreatedHandler"
    ) { api -> api.websockets().registerWebSocketCreatedHandler(webSocketCreatedObserver()) },

    ObserverSlot(
        OBSERVER_SCOPE_CHANGE,
        "Records that Burp target scope changed.",
        "Scope.registerScopeChangeHandler"
    ) { api -> api.scope().registerScopeChangeHandler(scopeChangeObserver()) },

    ObserverSlot(
        OBSERVER_SESSION_HANDLING,
        "Registers a passive session handling action that records macro timing and returns the request unchanged.",
        "Http.registerSessionHandlingAction"
    ) { api -> api.http().registerSessionHandlingAction(sessionHandlingObserver()) }
).associateBy { it.name }

private val OBSERVER_NAMES: String = OBSERVERS.keys.joinToString(", ")

/** Detaches every per-socket proxy websocket message handler that is still attached. */
private fun deregisterProxyWebSocketMessageHandlers() {
    while (true) {
        val registration = proxyWebSocketMessageRegistrations.poll() ?: break
        runCatching { if (registration.isRegistered()) registration.deregister() }
    }
}

/**
 * Detaches every observer and clears the buffer.
 *
 * Called from the extension's unloading handler. Without this, an observer left enabled
 * would keep a handler attached to Burp that captures into a buffer nobody can read any
 * more, since the REST server serving it has stopped.
 */
internal fun deregisterAllObservers() {
    OBSERVERS.values.forEach { slot ->
        runCatching {
            slot.registration?.let { if (it.isRegistered()) it.deregister() }
            slot.registration = null
        }
    }
    deregisterProxyWebSocketMessageHandlers()
    runCatching {
        eventBuffer.clear()
        eventBufferSize.set(0)
    }
}

// ---------------------------------------------------------------------------
// Routes
// ---------------------------------------------------------------------------

/**
 * REST control over Burp's live event hooks.
 *
 * Montoya's `register*Handler` calls take in-process callbacks, which cannot be invoked
 * over HTTP. What can be exposed is the registration itself, which is a stateful toggle,
 * plus the events the registered handler observes. So this route group registers passive
 * observer handlers on demand, buffers what they see in memory, and serves that buffer
 * back over HTTP.
 *
 * Every handler is read only. Each one returns the "continue unchanged" action for its
 * callback, preserves the incoming annotations, and never intercepts, drops or edits a
 * message. All capture logic runs inside [capture], which swallows failures so a capture
 * bug can never escape a Burp callback and disturb the user's traffic.
 *
 * The buffer is capped at [MAX_EVENTS] and lives only in memory, so events are lost when
 * the extension is reloaded. Deregistering the observers at extension unload is handled by
 * the extension lifecycle, not here.
 */
fun Routing.eventsRoutes(api: MontoyaApi) {
    route("/api/events") {

        // Current on/off state of every observer, plus buffer statistics.
        get("/observers") {
            val counts = eventBuffer.toList().groupingBy { it.observer }.eachCount()
            val states = OBSERVERS.values.map { slot ->
                ObserverState(
                    name = slot.name,
                    enabled = slot.isEnabled(),
                    description = slot.description,
                    montoya_call = slot.montoyaCall,
                    buffered_events = counts[slot.name] ?: 0
                )
            }
            val intercept = runCatching { api.proxy().isInterceptEnabled() }.getOrDefault(false)
            call.respond(
                ObserversResponse(
                    observers = states,
                    buffered_events = eventBufferSize.get(),
                    buffer_capacity = MAX_EVENTS,
                    next_event_id = eventIds.get() + 1,
                    proxy_intercept_enabled = intercept
                )
            )
        }

        // Register the named observer with Burp. Idempotent while it is already registered.
        post("/observers/{name}/enable") {
            val requested = call.parameters["name"].orEmpty()
            val slot = OBSERVERS[requested.trim().uppercase()]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'name': '$requested'. Allowed values: $OBSERVER_NAMES")
                )

            // The Montoya register call happens under the slot lock; the HTTP response is
            // written outside it so no coroutine ever suspends while holding the monitor.
            val outcome: Result<String> = synchronized(slot) {
                if (slot.isEnabled()) {
                    Result.success("Observer '${slot.name}' is already enabled")
                } else {
                    runCatching { slot.register(api) }.map { registration ->
                        slot.registration = registration
                        "Observer '${slot.name}' enabled via ${slot.montoyaCall}"
                    }
                }
            }
            outcome.fold(
                onSuccess = { call.respond(MessageResponse(it)) },
                onFailure = {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Could not enable observer '${slot.name}': ${it.message ?: it::class.java.simpleName}")
                    )
                }
            )
        }

        // Deregister the named observer. Buffered events it already captured are kept.
        post("/observers/{name}/disable") {
            val requested = call.parameters["name"].orEmpty()
            val slot = OBSERVERS[requested.trim().uppercase()]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'name': '$requested'. Allowed values: $OBSERVER_NAMES")
                )

            val message = synchronized(slot) {
                val registration = slot.registration
                slot.registration = null
                if (slot.name == OBSERVER_PROXY_WEBSOCKET_CREATION) deregisterProxyWebSocketMessageHandlers()
                if (registration == null) {
                    "Observer '${slot.name}' was already disabled"
                } else {
                    runCatching { if (registration.isRegistered()) registration.deregister() }
                    "Observer '${slot.name}' disabled"
                }
            }
            call.respond(MessageResponse(message))
        }

        // Read the captured events, newest last.
        get { call.respondEvents() }

        // Empty the buffer. Observers stay registered and keep capturing.
        delete { call.respondCleared() }
    }
}

/** Serves a filtered, paginated slice of the event buffer. */
private suspend fun io.ktor.server.application.ApplicationCall.respondEvents() {
    val params = request.queryParameters
    val offset = (params["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
    val limit = (params["limit"]?.toIntOrNull() ?: 100).coerceIn(1, MAX_EVENTS)
    val observerParam = params["observer"]?.trim()?.takeIf { it.isNotEmpty() }

    val observer = if (observerParam == null) null else {
        OBSERVERS[observerParam.uppercase()]?.name ?: return respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("Invalid 'observer': '$observerParam'. Allowed values: $OBSERVER_NAMES")
        )
    }

    val all = eventBuffer.toList().let { events ->
        if (observer == null) events else events.filter { it.observer == observer }
    }
    val page = all.drop(offset).take(limit)
    respond(
        EventsResponse(
            total = all.size,
            returned = page.size,
            offset = offset,
            limit = limit,
            buffer_capacity = MAX_EVENTS,
            observer = observer,
            events = page
        )
    )
}

/** Clears the buffer and reports how many events were discarded. */
private suspend fun io.ktor.server.application.ApplicationCall.respondCleared() {
    val discarded = eventBufferSize.get()
    eventBuffer.clear()
    eventBufferSize.set(0)
    respond(MessageResponse("Cleared $discarded buffered event(s). Observers were left registered."))
}

package com.reburp

/**
 * OpenAPI fragments for the passive event observer routes in
 * `com.reburp.routes.EventsRoutes`.
 *
 * Both functions return comma-separated entries with no leading or trailing comma, so
 * [extraPaths] and [extraSchemas] can splice them into the document.
 */

internal fun eventsPaths(): String = """
    "/api/events/observers": {
      "get": {
        "tags": ["Events"],
        "summary": "List the event observers and whether each one is registered",
        "description": "**[Montoya API]** Reports one entry per observer, using `Registration.isRegistered()` to decide whether that observer is currently attached to Burp. Also reports how many buffered events each observer produced, the buffer capacity, the id the next captured event will receive, and whether proxy interception is on according to `Proxy.isInterceptEnabled()`. This endpoint registers nothing and changes nothing.",
        "operationId": "listEventObservers",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ObserversResponse" },
              "example": {
                "observers": [
                  { "name": "HTTP_REQUEST", "enabled": true, "description": "Captures every request Burp is about to send, from any tool.", "montoya_call": "Http.registerHttpHandler", "buffered_events": 42 },
                  { "name": "SCOPE_CHANGE", "enabled": false, "description": "Records that Burp target scope changed.", "montoya_call": "Scope.registerScopeChangeHandler", "buffered_events": 0 }
                ],
                "buffered_events": 42,
                "buffer_capacity": 1000,
                "next_event_id": 43,
                "proxy_intercept_enabled": false
              }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/events/observers/{name}/enable": {
      "post": {
        "tags": ["Events"],
        "summary": "Register the named observer with Burp",
        "description": "**[Montoya API]** Registers the named observer through its Montoya call, for example `Http.registerHttpHandler`, `Proxy.registerRequestHandler`, `Proxy.registerResponseHandler`, `Proxy.registerWebSocketCreationHandler`, `WebSockets.registerWebSocketCreatedHandler`, `Scope.registerScopeChangeHandler` or `Http.registerSessionHandlingAction`. Every observer is passive. It never modifies, intercepts, drops or delays traffic. Each handler returns the continue unchanged action for its callback and preserves the annotations it was given, so Burp keeps applying the user's own interception rules exactly as before. The captured events are held in a bounded in memory ring buffer of 1000 entries. The oldest event is dropped once that cap is reached, and the whole buffer is lost when the extension is reloaded, so nothing here is persistent storage. Calling this endpoint again while the observer is already registered is a no-op and still returns 200. Enabling `PROXY_WEBSOCKET_CREATION` also attaches a passive `ProxyWebSocket.registerProxyMessageHandler` to every websocket created after that point, so websocket frames are observed too.",
        "operationId": "enableEventObserver",
        "x-api-source": "montoya",
        "parameters": [
          {
            "name": "name",
            "in": "path",
            "required": true,
            "schema": {
              "type": "string",
              "enum": ["HTTP_REQUEST", "HTTP_RESPONSE", "PROXY_REQUEST", "PROXY_RESPONSE", "PROXY_WEBSOCKET_CREATION", "WEBSOCKET_CREATION", "SCOPE_CHANGE", "SESSION_HANDLING"]
            },
            "example": "PROXY_REQUEST",
            "description": "Observer to register. The value is matched case insensitively. An unknown name returns 400 and lists every valid name."
          }
        ],
        "responses": {
          "200": {
            "description": "The observer is registered",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" },
              "example": { "message": "Observer 'PROXY_REQUEST' enabled via Proxy.registerRequestHandler" }
            } }
          },
          "400": {
            "description": "Unknown observer name, or Burp refused the registration",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" },
              "example": { "error": "Invalid 'name': 'PROXY_REQUESTS'. Allowed values: HTTP_REQUEST, HTTP_RESPONSE, PROXY_REQUEST, PROXY_RESPONSE, PROXY_WEBSOCKET_CREATION, WEBSOCKET_CREATION, SCOPE_CHANGE, SESSION_HANDLING" }
            } }
          }
        }
      }
    },

    "/api/events/observers/{name}/disable": {
      "post": {
        "tags": ["Events"],
        "summary": "Deregister the named observer",
        "description": "**[Montoya API]** Calls `Registration.deregister()` on the registration Burp returned when the observer was enabled, after checking `Registration.isRegistered()`. Disabling `PROXY_WEBSOCKET_CREATION` also deregisters every per socket proxy message handler that observer attached. Events the observer already captured stay in the buffer and are still readable. Disabling an observer that is not enabled is a no-op and still returns 200.",
        "operationId": "disableEventObserver",
        "x-api-source": "montoya",
        "parameters": [
          {
            "name": "name",
            "in": "path",
            "required": true,
            "schema": {
              "type": "string",
              "enum": ["HTTP_REQUEST", "HTTP_RESPONSE", "PROXY_REQUEST", "PROXY_RESPONSE", "PROXY_WEBSOCKET_CREATION", "WEBSOCKET_CREATION", "SCOPE_CHANGE", "SESSION_HANDLING"]
            },
            "example": "PROXY_REQUEST",
            "description": "Observer to deregister. The value is matched case insensitively. An unknown name returns 400 and lists every valid name."
          }
        ],
        "responses": {
          "200": {
            "description": "The observer is no longer registered",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" },
              "example": { "message": "Observer 'PROXY_REQUEST' disabled" }
            } }
          },
          "400": {
            "description": "Unknown observer name",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" },
              "example": { "error": "Invalid 'name': 'nope'. Allowed values: HTTP_REQUEST, HTTP_RESPONSE, PROXY_REQUEST, PROXY_RESPONSE, PROXY_WEBSOCKET_CREATION, WEBSOCKET_CREATION, SCOPE_CHANGE, SESSION_HANDLING" }
            } }
          }
        }
      }
    },

    "/api/events": {
      "get": {
        "tags": ["Events"],
        "summary": "Read the buffered events captured by the enabled observers",
        "description": "**[Montoya API]** Returns the events the enabled observers captured, oldest first. The buffer holds at most 1000 events in memory and drops the oldest once that cap is reached, so a caller that wants a complete record should poll often. The buffer does not survive an extension reload. Only fields the originating Montoya callback actually exposes are present on each event, so most fields are absent for most events.",
        "operationId": "listObservedEvents",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "offset", "in": "query", "required": false, "schema": { "type": "integer", "default": 0, "minimum": 0 }, "example": 0, "description": "Number of matching events to skip before the page begins. Values below zero are treated as zero." },
          { "name": "limit", "in": "query", "required": false, "schema": { "type": "integer", "default": 100, "minimum": 1, "maximum": 1000 }, "example": 50, "description": "Maximum number of events to return. Values outside the range are clamped into it." },
          {
            "name": "observer",
            "in": "query",
            "required": false,
            "schema": {
              "type": "string",
              "enum": ["HTTP_REQUEST", "HTTP_RESPONSE", "PROXY_REQUEST", "PROXY_RESPONSE", "PROXY_WEBSOCKET_CREATION", "WEBSOCKET_CREATION", "SCOPE_CHANGE", "SESSION_HANDLING"]
            },
            "example": "PROXY_RESPONSE",
            "description": "Return only events produced by this observer. The value is matched case insensitively. Omit it to return events from every observer. An unknown name returns 400 and lists every valid name."
          }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/EventsResponse" },
              "example": {
                "total": 2,
                "returned": 1,
                "offset": 0,
                "limit": 50,
                "buffer_capacity": 1000,
                "observer": "PROXY_RESPONSE",
                "events": [
                  {
                    "id": 17,
                    "timestamp": "2026-09-09T11:04:21.118Z",
                    "observer": "PROXY_RESPONSE",
                    "phase": "proxy_response_received",
                    "message_id": 904,
                    "url": "https://example.org/account",
                    "initiating_request_url": "https://example.org/account",
                    "http_version": "HTTP/2",
                    "status_code": 200,
                    "reason_phrase": "OK",
                    "status_code_class": "CLASS_2XX_SUCCESS",
                    "stated_mime_type": "HTML",
                    "inferred_mime_type": "HTML",
                    "body_offset": 312,
                    "headers": [{ "name": "Content-Type", "present": true, "value": "text/html; charset=utf-8" }],
                    "cookies": [{ "name": "session", "present": true, "value": "a1b2c3" }],
                    "keyword_counts": [{ "keyword": "token", "count": 3 }],
                    "markers": [],
                    "marker_count": 0,
                    "source_ip_address": "93.184.216.34",
                    "destination_ip_address": "127.0.0.1",
                    "listener_interface": "127.0.0.1:8080"
                  }
                ]
              }
            } }
          },
          "400": {
            "description": "Unknown observer filter",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" },
              "example": { "error": "Invalid 'observer': 'proxy'. Allowed values: HTTP_REQUEST, HTTP_RESPONSE, PROXY_REQUEST, PROXY_RESPONSE, PROXY_WEBSOCKET_CREATION, WEBSOCKET_CREATION, SCOPE_CHANGE, SESSION_HANDLING" }
            } }
          }
        }
      },
      "delete": {
        "tags": ["Events"],
        "summary": "Discard every buffered event",
        "description": "**[Montoya API]** Empties the in memory event buffer and reports how many events were discarded. The observers themselves are left registered and keep capturing, so this clears history without changing which Montoya handlers are attached.",
        "operationId": "clearObservedEvents",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "The buffer is empty",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" },
              "example": { "message": "Cleared 42 buffered event(s). Observers were left registered." }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    }
"""

internal fun eventsSchemas(): String = """
      "ObservedToolSource": {
        "type": "object",
        "description": "The Burp tool that produced the message, read from Montoya's ToolSource.",
        "properties": {
          "tool_type": { "type": "string", "description": "The ToolType enum constant name, for example PROXY or REPEATER." },
          "tool_name": { "type": "string", "description": "The display name Burp gives that tool, from ToolType.toolName()." },
          "is_from_proxy": { "type": "boolean", "description": "Whether ToolSource.isFromTool reports the message came from the Proxy tool." },
          "is_from_scanner_or_intruder": { "type": "boolean", "description": "Whether ToolSource.isFromTool reports the message came from either the Scanner or the Intruder tool." }
        }
      },

      "ObservedHeader": {
        "type": "object",
        "description": "The result of probing one header name on the observed message. The observer only looks at a fixed list of header names rather than copying every header.",
        "properties": {
          "name": { "type": "string", "description": "The header name that was probed." },
          "present": { "type": "boolean", "description": "Whether hasHeader reported the header on the message." },
          "value": { "type": "string", "nullable": true, "description": "The value returned by headerValue, or null when the header was absent." }
        }
      },

      "ObservedParameter": {
        "type": "object",
        "description": "The result of probing one request parameter. The observer only looks at a fixed list of parameter names rather than copying every parameter.",
        "properties": {
          "name": { "type": "string", "description": "The parameter name that was probed." },
          "type": { "type": "string", "enum": ["URL", "BODY", "COOKIE", "XML", "XML_ATTRIBUTE", "MULTIPART_ATTRIBUTE", "JSON"], "description": "The Montoya HttpParameterType the parameter was probed under." },
          "present": { "type": "boolean", "description": "Whether hasParameter reported the parameter on the request." },
          "value": { "type": "string", "nullable": true, "description": "The value returned by parameterValue, or null when the parameter was absent." }
        }
      },

      "ObservedCookie": {
        "type": "object",
        "description": "The result of probing one cookie name on a proxy response.",
        "properties": {
          "name": { "type": "string", "description": "The cookie name that was probed." },
          "present": { "type": "boolean", "description": "Whether hasCookie reported the cookie on the response." },
          "value": { "type": "string", "nullable": true, "description": "The value returned by cookieValue, or null when the cookie was absent." }
        }
      },

      "ObservedKeyword": {
        "type": "object",
        "description": "One keyword count returned by Montoya's response keyword analysis. Only keywords that occurred at least once are listed.",
        "properties": {
          "keyword": { "type": "string", "description": "The keyword that was counted." },
          "count": { "type": "integer", "description": "How many times the keyword appeared in the response." }
        }
      },

      "ObservedMarker": {
        "type": "object",
        "description": "One Burp marker range on the observed message, given as byte offsets into the full message.",
        "properties": {
          "start_index_inclusive": { "type": "integer", "description": "First byte offset covered by the marker." },
          "end_index_exclusive": { "type": "integer", "description": "Byte offset one past the last byte covered by the marker." }
        }
      },

      "ObservedTiming": {
        "type": "object",
        "description": "Timings read from Montoya's TimingData. Montoya exposes timings on HttpRequestResponse only, so this appears on SESSION_HANDLING events that carried at least one macro request and response.",
        "properties": {
          "time_request_sent": { "type": "string", "nullable": true, "description": "When Burp sent the request, from TimingData.timeRequestSent(), formatted as a zoned date and time." },
          "time_to_start_of_response_millis": { "type": "integer", "format": "int64", "nullable": true, "description": "Milliseconds from sending the request to the first byte of the response, from TimingData.timeBetweenRequestSentAndStartOfResponse()." }
        }
      },

      "ObservedWebSocketHistory": {
        "type": "object",
        "description": "Detail resolved from Burp's proxy websocket history for the socket that carried the message. Montoya does not hand the websocket id to the creation callback, so it is looked up once per socket and cached. It is absent when the lookup did not find a matching history entry.",
        "properties": {
          "web_socket_id": { "type": "integer", "description": "Burp's own identifier for the websocket, from ProxyWebSocketMessage.webSocketId()." },
          "upgrade_request_url": { "type": "string", "nullable": true, "description": "URL of the HTTP upgrade request that opened the socket, from ProxyWebSocketMessage.upgradeRequest()." },
          "edited_payload_length": { "type": "integer", "nullable": true, "description": "Length in bytes of the payload after any edit, from ProxyWebSocketMessage.editedPayload()." }
        }
      },

      "ObservedEvent": {
        "type": "object",
        "description": "One event captured by an observer. Only the fields the originating Montoya callback exposes are present, so most events carry a small subset of these properties.",
        "required": ["id", "timestamp", "observer", "phase"],
        "properties": {
          "id": { "type": "integer", "format": "int64", "description": "Monotonically increasing identifier assigned when the event was captured. Ids are never reused within one extension load." },
          "timestamp": { "type": "string", "description": "When the event was captured, as an ISO 8601 instant in UTC." },
          "observer": { "type": "string", "enum": ["HTTP_REQUEST", "HTTP_RESPONSE", "PROXY_REQUEST", "PROXY_RESPONSE", "PROXY_WEBSOCKET_CREATION", "WEBSOCKET_CREATION", "SCOPE_CHANGE", "SESSION_HANDLING"], "description": "Which observer captured the event." },
          "phase": { "type": "string", "description": "Which callback produced the event, for example request_to_be_sent, proxy_response_received, websocket_text_received or scope_changed." },
          "message_id": { "type": "integer", "nullable": true, "description": "Burp's identifier for the HTTP message, from messageId(). It ties a request event to its response event." },
          "tool_source": { "${'$'}ref": "#/components/schemas/ObservedToolSource" },
          "url": { "type": "string", "nullable": true, "description": "The URL the event relates to. For response events this is the URL of the initiating request." },
          "method": { "type": "string", "nullable": true, "description": "HTTP method of the request." },
          "path_without_query": { "type": "string", "nullable": true, "description": "Request path with the query string removed, from pathWithoutQuery()." },
          "http_version": { "type": "string", "nullable": true, "description": "HTTP version string reported by Burp, for example HTTP/1.1 or HTTP/2." },
          "content_type": { "type": "string", "nullable": true, "description": "The Montoya ContentType enum constant name for the request body, for example JSON or URL_ENCODED." },
          "status_code": { "type": "integer", "nullable": true, "description": "HTTP status code of the response." },
          "reason_phrase": { "type": "string", "nullable": true, "description": "HTTP reason phrase of the response, from reasonPhrase()." },
          "status_code_class": { "type": "string", "nullable": true, "description": "The StatusCodeClass constant the status code falls into, decided with isStatusCodeClass(), for example CLASS_2XX_SUCCESS." },
          "stated_mime_type": { "type": "string", "nullable": true, "description": "MIME type the response declared in its headers, from statedMimeType()." },
          "inferred_mime_type": { "type": "string", "nullable": true, "description": "MIME type Burp inferred from the response body, from inferredMimeType()." },
          "initiating_request_url": { "type": "string", "nullable": true, "description": "URL of the request that produced this response, from initiatingRequest()." },
          "body_offset": { "type": "integer", "nullable": true, "description": "Byte offset at which the message body starts, from bodyOffset(). It is also the length of the headers section." },
          "has_parameters": { "type": "boolean", "nullable": true, "description": "Whether the request carries any parameter of any type, from hasParameters()." },
          "has_url_parameters": { "type": "boolean", "nullable": true, "description": "Whether the request carries any URL query parameter, from hasParameters(HttpParameterType.URL)." },
          "headers": { "type": "array", "description": "Results of probing the fixed list of header names for this message kind.", "items": { "${'$'}ref": "#/components/schemas/ObservedHeader" } },
          "parameters": { "type": "array", "description": "Results of probing the fixed list of request parameter names.", "items": { "${'$'}ref": "#/components/schemas/ObservedParameter" } },
          "cookies": { "type": "array", "description": "Results of probing the fixed list of cookie names on a proxy response.", "items": { "${'$'}ref": "#/components/schemas/ObservedCookie" } },
          "keyword_counts": { "type": "array", "description": "Counts of the fixed list of keywords in a proxy response body. Keywords that did not occur are omitted.", "items": { "${'$'}ref": "#/components/schemas/ObservedKeyword" } },
          "markers": { "type": "array", "description": "Burp markers on the message, capped at the first 20.", "items": { "${'$'}ref": "#/components/schemas/ObservedMarker" } },
          "marker_count": { "type": "integer", "nullable": true, "description": "Total number of markers on the message before the cap was applied." },
          "source_ip_address": { "type": "string", "nullable": true, "description": "Address the proxied connection came from, from sourceIpAddress()." },
          "destination_ip_address": { "type": "string", "nullable": true, "description": "Address the proxied connection was headed to, from destinationIpAddress()." },
          "listener_interface": { "type": "string", "nullable": true, "description": "The proxy listener interface that handled the connection, from listenerInterface()." },
          "upgrade_request_url": { "type": "string", "nullable": true, "description": "URL of the HTTP upgrade request that created the websocket, from upgradeRequest()." },
          "direction": { "type": "string", "enum": ["CLIENT_TO_SERVER", "SERVER_TO_CLIENT"], "nullable": true, "description": "Travel direction of the websocket message." },
          "payload_length": { "type": "integer", "nullable": true, "description": "Length of the websocket payload, in characters for text messages and in bytes for binary messages." },
          "payload_preview": { "type": "string", "nullable": true, "description": "First 200 characters of a text websocket payload. It is empty for binary messages." },
          "web_socket_history": { "${'$'}ref": "#/components/schemas/ObservedWebSocketHistory" },
          "timing": { "${'$'}ref": "#/components/schemas/ObservedTiming" },
          "macro_request_response_count": { "type": "integer", "nullable": true, "description": "How many macro request and response pairs the session handling action was given." },
          "note": { "type": "string", "nullable": true, "description": "Plain text detail for events that carry no structured payload, such as a scope change." }
        }
      },

      "ObserverState": {
        "type": "object",
        "description": "The current state of one observer.",
        "properties": {
          "name": { "type": "string", "description": "The observer name used in the enable, disable and filter endpoints." },
          "enabled": { "type": "boolean", "description": "Whether Registration.isRegistered() reports the observer is attached to Burp right now." },
          "description": { "type": "string", "description": "What this observer captures, in plain language." },
          "montoya_call": { "type": "string", "description": "The Montoya registration method this observer uses, for example Proxy.registerRequestHandler." },
          "buffered_events": { "type": "integer", "description": "How many events currently in the buffer came from this observer." }
        }
      },

      "ObserversResponse": {
        "type": "object",
        "description": "State of every observer plus buffer statistics.",
        "properties": {
          "observers": { "type": "array", "description": "One entry per observer, in a stable order.", "items": { "${'$'}ref": "#/components/schemas/ObserverState" } },
          "buffered_events": { "type": "integer", "description": "Total number of events held in the buffer across all observers." },
          "buffer_capacity": { "type": "integer", "description": "Maximum number of events the buffer holds before the oldest is dropped." },
          "next_event_id": { "type": "integer", "format": "int64", "description": "The id the next captured event will be given." },
          "proxy_intercept_enabled": { "type": "boolean", "description": "Whether Proxy.isInterceptEnabled() reports that proxy interception is currently on. Observers never change this." }
        }
      },

      "EventsResponse": {
        "type": "object",
        "description": "A page of buffered events, oldest first.",
        "properties": {
          "total": { "type": "integer", "description": "Number of buffered events that matched the observer filter, before paging." },
          "returned": { "type": "integer", "description": "Number of events actually included in this page." },
          "offset": { "type": "integer", "description": "The offset that was applied, after clamping." },
          "limit": { "type": "integer", "description": "The limit that was applied, after clamping." },
          "buffer_capacity": { "type": "integer", "description": "Maximum number of events the buffer holds before the oldest is dropped." },
          "observer": { "type": "string", "nullable": true, "description": "The observer filter that was applied, or null when events from every observer were returned." },
          "events": { "type": "array", "description": "The events in this page.", "items": { "${'$'}ref": "#/components/schemas/ObservedEvent" } }
        }
      }
"""

package com.reburp

/**
 * OpenAPI fragments for the HTTP message inspection and mutation routes.
 *
 * [messagePaths] returns comma-separated `"path": { ... }` entries and [messageSchemas]
 * returns comma-separated `"Name": { ... }` entries, both with no leading or trailing
 * comma, so they can be dropped into [extraPaths] and [extraSchemas].
 */

internal fun messagePaths(): String = """
    "/api/http/message/inspect/request": {
      "post": {
        "tags": ["Messages"],
        "summary": "Parse a raw request and report what Burp sees in it",
        "description": "**[Montoya API]** Hands the request text to Burp's own parser and reports the parts other tools key off: method, path with and without the query string, the query itself, the file extension Burp derives from the path, the body content type it classifies, the HTTP version, the byte offset where the body starts, every header, and every parameter with the exact offsets of its name and value. Supply `url` instead of `raw` to build a plain GET request from an absolute URL. A parsed request has no target until you give it one, so `url` and `in_scope` come back null unless you also pass `host`, `port` and `secure`. `parameter_name` runs a targeted lookup; add `parameter_type` to disambiguate a name that appears in more than one location, since a URL parameter and a cookie of the same name are different parameters to Burp.",
        "operationId": "inspectHttpRequestMessage",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/MsgRequestInspectInput" },
            "example": {
              "raw": "POST /search.php?lang=en HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/x-www-form-urlencoded\r\nCookie: session=abc123\r\nContent-Length: 9\r\n\r\nq=admin&x",
              "host": "example.com",
              "port": 443,
              "secure": true,
              "header_name": "Cookie",
              "parameter_name": "q",
              "parameter_type": "BODY"
            }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MsgRequestInspectResult" },
              "example": {
                "method": "POST",
                "url": "https://example.com/search.php?lang=en",
                "path": "/search.php?lang=en",
                "path_without_query": "/search.php",
                "query": "lang=en",
                "file_extension": "php",
                "content_type": "URL_ENCODED",
                "has_parameters": true,
                "has_parameter": true,
                "parameter_value": "admin",
                "parameter_type": "BODY"
              }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/message/inspect/response": {
      "post": {
        "tags": ["Messages"],
        "summary": "Parse a raw response and report status, MIME typing and cookies",
        "description": "**[Montoya API]** Reports the status code, the reason phrase, the body offset, the headers, and the cookies Burp extracts from `Set-Cookie`. Two MIME types are returned: the one stated by the `Content-Type` header and the one Burp infers from the body bytes. They disagree on mislabelled uploads and on endpoints that serve JSON as text, which is exactly where content sniffing bugs live, so `mime_type_disagrees` is worth checking on its own. Every status code class is listed with its numeric bounds and whether this response falls in it, so a caller can classify without hardcoding ranges. Pass `keywords` to have Burp count occurrences in the response, which is the same counting used by its response analysis.",
        "operationId": "inspectHttpResponseMessage",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/MsgResponseInspectInput" },
            "example": {
              "raw": "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nSet-Cookie: session=abc123; Path=/; HttpOnly\r\nContent-Length: 40\r\n\r\n{\"error\":\"invalid credentials\",\"id\":41}",
              "keywords": ["error", "invalid credentials"],
              "header_name": "Content-Type",
              "cookie_name": "session"
            }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MsgResponseInspectResult" },
              "example": {
                "status_code": 200,
                "reason_phrase": "OK",
                "stated_mime_type": "HTML",
                "inferred_mime_type": "JSON",
                "mime_type_disagrees": true,
                "has_cookie": true,
                "cookie_value": "abc123",
                "keyword_counts": [{ "keyword": "error", "count": 1 }]
              }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/message/request/headers": {
      "post": {
        "tags": ["Messages"],
        "summary": "Add, replace or delete request headers",
        "description": "**[Montoya API]** Applies header edits through Burp's builders, which keep the message well formed instead of leaving you to splice text. The operations run in a fixed order: default headers first, then `set`, then `add`, then `update`, then `remove`. `set` writes a header whether or not it already exists, `add` appends without checking for a duplicate, and `update` only rewrites the value of a header that is already present. Removal matches on the whole header rather than on the name, so each name in `remove` is resolved against the current message first; names that are not present are reported in `not_found` rather than failing the call. `apply_default_headers` asks Burp to fill in the headers it would normally add itself, which needs a target, so `host` is required when that flag is set.",
        "operationId": "editHttpRequestHeaders",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/MsgRequestHeadersInput" },
            "example": {
              "raw": "GET /admin HTTP/1.1\r\nHost: example.com\r\nUser-Agent: curl/8.4.0\r\nX-Debug: 1\r\n\r\n",
              "host": "example.com",
              "port": 443,
              "secure": true,
              "set": { "name": "Authorization", "value": "Bearer eyJhbGciOi" },
              "add": [{ "name": "X-Forwarded-For", "value": "127.0.0.1" }],
              "update": [{ "name": "User-Agent", "value": "Mozilla/5.0" }],
              "remove": ["X-Debug"]
            }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MsgRequestEditResult" },
              "example": {
                "request": "GET /admin HTTP/1.1\r\nHost: example.com\r\nUser-Agent: Mozilla/5.0\r\nAuthorization: Bearer eyJhbGciOi\r\nX-Forwarded-For: 127.0.0.1\r\n\r\n",
                "http_version": "HTTP/1.1",
                "body_offset": 132,
                "applied": ["set:Authorization", "add:X-Forwarded-For", "update:User-Agent", "remove:X-Debug"],
                "not_found": []
              }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/message/request/parameters": {
      "post": {
        "tags": ["Messages"],
        "summary": "Add, replace or delete URL, body and cookie parameters",
        "description": "**[Montoya API]** Edits parameters by location rather than by string surgery, so a URL parameter lands in the query string, a body parameter lands in the body with the length header corrected, and a cookie parameter lands in the `Cookie` header. Operations run in the order `set`, `add`, `update`, `remove`. `set` writes the parameter whether or not it exists, while `update` and `remove` act on a parameter that is already there and are silently ineffective when it is not. Only URL, BODY and COOKIE can be built: Burp parses XML, JSON and multipart parameters and reports them in the inspect endpoint, but exposes no factory for constructing them, so those types are rejected here. The response re-parses the edited request so you can see the resulting offsets.",
        "operationId": "editHttpRequestParameters",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/MsgRequestParametersInput" },
            "example": {
              "raw": "POST /login?next=/home HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/x-www-form-urlencoded\r\nCookie: theme=dark\r\nContent-Length: 21\r\n\r\nuser=bob&pass=hunter2",
              "host": "example.com",
              "add": [{ "name": "debug", "value": "1", "type": "URL" }],
              "update": [{ "name": "pass", "value": "' OR 1=1--", "type": "BODY" }],
              "remove": [{ "name": "theme", "value": "dark", "type": "COOKIE" }]
            }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MsgRequestParametersResult" },
              "example": {
                "has_parameters": true,
                "applied": ["add:URL:debug", "update:BODY:pass", "remove:COOKIE:theme"]
              }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/message/request/service": {
      "post": {
        "tags": ["Messages"],
        "summary": "Retarget a request at a different host, port or scheme",
        "description": "**[Montoya API]** Attaches a new service to an existing request, which is how you replay one captured message against staging, against a second virtual host, or over cleartext instead of TLS. The message text is left alone, so the `Host` header keeps whatever value it had; that is deliberate and is what makes this useful for host header testing, but it means you should edit the header yourself when you want it to follow the new target. The resolved IP address is reported when Burp can determine it. Resolution depends on DNS and on Burp's own connection state, so `ip_address` may be null with the reason in `ip_resolution_error` for a host that has never been contacted.",
        "operationId": "retargetHttpRequestService",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/MsgServiceInput" },
            "example": {
              "raw": "GET /api/v1/users HTTP/1.1\r\nHost: prod.example.com\r\nAccept: application/json\r\n\r\n",
              "host": "staging.example.com",
              "port": 8443,
              "secure": true
            }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MsgServiceResult" },
              "example": {
                "url": "https://staging.example.com:8443/api/v1/users",
                "host": "staging.example.com",
                "port": 8443,
                "secure": true,
                "ip_address": "93.184.216.34"
              }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/message/response/edit": {
      "post": {
        "tags": ["Messages"],
        "summary": "Rewrite a response status line and headers",
        "description": "**[Montoya API]** Rewrites the parts of a response that a client keys off, which is how you build fixtures for testing how an application or a proxy reacts to an unusual server. `http_version` and `reason_phrase` are written verbatim into the status line, so this is a way to produce a deliberately odd but still parseable response. Header operations behave as they do for requests: `add` appends, `update` rewrites a header that already exists, and `remove` resolves each name against the current message and reports names it could not find in `not_found`. The body is never touched, so removing `Content-Length` leaves the body in place and produces a response whose framing is intentionally ambiguous.",
        "operationId": "editHttpResponseMessage",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/MsgResponseEditInput" },
            "example": {
              "raw": "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nServer: nginx/1.24.0\r\nContent-Length: 2\r\n\r\nhi",
              "http_version": "HTTP/1.0",
              "reason_phrase": "Totally Fine",
              "add": [{ "name": "X-Frame-Options", "value": "DENY" }],
              "update": [{ "name": "Content-Type", "value": "application/json" }],
              "remove": ["Server"]
            }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MsgResponseEditResult" },
              "example": {
                "status_code": 200,
                "reason_phrase": "Totally Fine",
                "http_version": "HTTP/1.0",
                "body_offset": 118,
                "applied": ["http_version", "reason_phrase", "add:X-Frame-Options", "update:Content-Type", "remove:Server"],
                "not_found": []
              }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/message/markers": {
      "post": {
        "tags": ["Messages"],
        "summary": "Apply markers and annotations to a request, a response or a pair",
        "description": "**[Montoya API]** Markers are byte ranges Burp highlights inside a message, and they are what a scanner issue or a manual note points at when it says where the interesting bytes are. Offsets are counted over the whole message text including the status or request line and the header block, not from the start of the body, so read `body_offset` from an inspect call before computing them. A range must satisfy 0 <= start < end <= message length, and a range that does not is rejected with the offsets it received and the length it was measured against. When both a request and a response are supplied they are also combined into a pair, which is the form the rest of Burp passes messages around in, and the pair carries the annotations: free text notes and a highlight colour drawn from Burp's fixed palette.",
        "operationId": "applyHttpMessageMarkers",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/MsgMarkersInput" },
            "example": {
              "request": "GET /profile?id=7 HTTP/1.1\r\nHost: example.com\r\n\r\n",
              "response": "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 22\r\n\r\n{\"id\":7,\"admin\":false}",
              "request_markers": [{ "start": 14, "end": 16 }],
              "response_markers": [{ "start": 74, "end": 82 }],
              "notes": "IDOR candidate: id is reflected and unauthenticated",
              "highlight_color": "ORANGE"
            }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MsgMarkersResult" },
              "example": {
                "request_markers": [{ "start": 14, "end": 16, "excerpt": "id" }],
                "pair_built": true,
                "content_type": "NONE",
                "has_notes": true,
                "has_highlight_color": true,
                "highlight_color": "ORANGE"
              }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/message/timing": {
      "post": {
        "tags": ["Messages"],
        "summary": "Send a request and report Burp's timing data",
        "description": "**[Montoya API]** Sends the request through Burp's own HTTP stack and returns the timing Burp recorded: when the request was sent, how long until the first response byte arrived, and how long until the response was complete. The gap between those two numbers is what separates a slow backend from a slow transfer, which is what makes this endpoint useful for time based injection and for rate limiting work. Connection behaviour is controlled by the options: `connection_id` pins several calls to the same connection so you can measure a warm request rather than a fresh handshake, `http_mode` forces HTTP/1 or HTTP/2 instead of letting ALPN decide, `server_name_indicator` sends an SNI value that differs from the target host, and `verify_upstream_tls` turns on certificate verification that Burp otherwise skips. Timing data is optional in the API, so `timing_available` says whether the numbers are real or simply absent.",
        "operationId": "sendHttpRequestWithTiming",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/MsgTimingInput" },
            "example": {
              "raw": "GET /slow-endpoint HTTP/1.1\r\nHost: example.com\r\nAccept: */*\r\n\r\n",
              "host": "example.com",
              "port": 443,
              "secure": true,
              "http_mode": "HTTP_1",
              "connection_id": "timing-probe-1",
              "server_name_indicator": "example.com",
              "verify_upstream_tls": false
            }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MsgTimingResult" },
              "example": {
                "status_code": 200,
                "reason_phrase": "OK",
                "ip_address": "93.184.216.34",
                "time_request_sent": "2025-01-09T11:04:22.118Z[UTC]",
                "ms_to_first_response_byte": 512,
                "ms_to_complete_response": 530,
                "timing_available": true,
                "options_applied": ["http_mode=HTTP_1", "connection_id=timing-probe-1", "server_name_indicator=example.com"]
              }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    }
"""

internal fun messageSchemas(): String = """
      "MsgHeader": {
        "type": "object",
        "description": "One HTTP header as a name and a value, with no colon or line break in either part",
        "required": ["name", "value"],
        "properties": {
          "name":  { "type": "string", "description": "Header name. Burp matches names case insensitively, so `content-type` and `Content-Type` refer to the same header." },
          "value": { "type": "string", "description": "Header value, written verbatim with no folding or encoding applied" }
        }
      },

      "MsgRange": {
        "type": "object",
        "description": "A half open byte range over a whole message, counted from the first byte of the request or status line",
        "required": ["start", "end"],
        "properties": {
          "start": { "type": "integer", "description": "First offset covered by the range, inclusive" },
          "end":   { "type": "integer", "description": "Offset just past the range, exclusive. Must be greater than `start` and no greater than the message length." }
        }
      },

      "MsgMarker": {
        "type": "object",
        "description": "A byte range Burp highlights inside a message, returned with the text it covers so a caller can confirm it landed where intended",
        "properties": {
          "start":   { "type": "integer", "description": "First offset covered by the marker, inclusive" },
          "end":     { "type": "integer", "description": "Offset just past the marker, exclusive" },
          "excerpt": { "type": "string", "nullable": true, "description": "The marked text, or null when the range falls outside the message" }
        }
      },

      "MsgParameter": {
        "type": "object",
        "description": "A parameter as Burp parsed it, including where its name and value sit in the message so they can be marked or replaced by offset",
        "properties": {
          "name":        { "type": "string",  "description": "Parameter name as it appears in the message, still encoded" },
          "value":       { "type": "string",  "description": "Parameter value as it appears in the message, still encoded" },
          "type":        { "type": "string",  "enum": ["URL", "BODY", "COOKIE", "XML", "XML_ATTRIBUTE", "MULTIPART_ATTRIBUTE", "JSON"], "description": "Where the parameter lives. Burp classifies by location, so the same name can appear more than once with different types." },
          "name_start":  { "type": "integer", "description": "Offset of the first byte of the name, counted over the whole message" },
          "name_end":    { "type": "integer", "description": "Offset just past the last byte of the name" },
          "value_start": { "type": "integer", "description": "Offset of the first byte of the value, which is where an insertion point would begin" },
          "value_end":   { "type": "integer", "description": "Offset just past the last byte of the value" }
        }
      },

      "MsgParamSpec": {
        "type": "object",
        "description": "A parameter to create, replace or delete. Burp only exposes factories for the three locations listed in `type`.",
        "required": ["name"],
        "properties": {
          "name":  { "type": "string", "description": "Parameter name. Must not be blank." },
          "value": { "type": "string", "default": "", "description": "Parameter value. Ignored for removal, where only the name and type are matched." },
          "type":  { "type": "string", "enum": ["URL", "BODY", "COOKIE"], "default": "URL", "description": "Where to put the parameter: the query string, the request body, or the Cookie header" }
        }
      },

      "MsgCookie": {
        "type": "object",
        "description": "A cookie Burp extracted from a Set-Cookie header",
        "properties": {
          "name":       { "type": "string", "description": "Cookie name" },
          "value":      { "type": "string", "description": "Cookie value, still encoded" },
          "domain":     { "type": "string", "nullable": true, "description": "Domain attribute, or null when the header did not set one" },
          "path":       { "type": "string", "nullable": true, "description": "Path attribute, or null when the header did not set one" },
          "expiration": { "type": "string", "nullable": true, "description": "Expiry as an ISO 8601 timestamp, or null for a session cookie" }
        }
      },

      "MsgKeywordCount": {
        "type": "object",
        "description": "How many times one keyword occurs in a response body",
        "properties": {
          "keyword": { "type": "string",  "description": "The keyword that was counted, echoed back" },
          "count":   { "type": "integer", "description": "Number of occurrences Burp found" }
        }
      },

      "MsgStatusClass": {
        "type": "object",
        "description": "One of Burp's status code classes with its numeric bounds and whether the response belongs to it",
        "properties": {
          "name":            { "type": "string",  "enum": ["CLASS_1XX_INFORMATIONAL_RESPONSE", "CLASS_2XX_SUCCESS", "CLASS_3XX_REDIRECTION", "CLASS_4XX_CLIENT_ERRORS", "CLASS_5XX_SERVER_ERRORS"], "description": "Class name as Burp defines it" },
          "start_inclusive": { "type": "integer", "description": "Lowest status code in the class" },
          "end_exclusive":   { "type": "integer", "description": "One past the highest status code in the class" },
          "member":          { "type": "boolean", "description": "Whether the inspected response falls in this class" }
        }
      },

      "MsgCommon": {
        "type": "object",
        "description": "The fields every HTTP message carries, read through the interface requests and responses share",
        "properties": {
          "http_version":   { "type": "string",  "nullable": true, "description": "Version token from the first line, such as HTTP/1.1" },
          "body_offset":    { "type": "integer", "description": "Offset of the first body byte, which is also the length of the first line plus the header block. Marker offsets are counted from 0, not from here." },
          "header_count":   { "type": "integer", "description": "Number of headers Burp parsed" },
          "headers":        { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgHeader" }, "description": "Headers in the order they appear" },
          "markers":        { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgMarker" }, "description": "Markers carried by the message. A message parsed from text carries none until markers are applied." },
          "header_present": { "type": "boolean", "nullable": true, "description": "Whether the header named in `header_name` exists, or null when no name was asked about" },
          "header_value":   { "type": "string",  "nullable": true, "description": "Value of the header named in `header_name`, or null when it is absent or none was asked about" }
        }
      },

      "MsgRequestInspectInput": {
        "type": "object",
        "description": "Supply exactly one source: `raw` for an existing request, or `url` to build a GET request from an absolute URL",
        "properties": {
          "raw":            { "type": "string",  "nullable": true, "description": "The request as HTTP text, with CRLF line endings and a blank line before the body" },
          "url":            { "type": "string",  "nullable": true, "description": "Absolute URL to build a GET request from, used only when `raw` is absent. The scheme and port set the service, so the result carries a target without further fields." },
          "host":           { "type": "string",  "nullable": true, "description": "Target host to attach to a `raw` request. Without it `url` and `in_scope` cannot be computed." },
          "port":           { "type": "integer", "default": 443, "description": "Target port, used only alongside `host`" },
          "secure":         { "type": "boolean", "default": true, "description": "Whether the target uses TLS, used only alongside `host`" },
          "header_name":    { "type": "string",  "nullable": true, "description": "Header to look up, reported in `common.header_present` and `common.header_value`" },
          "parameter_name": { "type": "string",  "nullable": true, "description": "Parameter to look up by name, reported in `has_parameter` and `parameter_value`" },
          "parameter_type": { "type": "string",  "nullable": true, "enum": ["URL", "BODY", "COOKIE", "XML", "XML_ATTRIBUTE", "MULTIPART_ATTRIBUTE", "JSON"], "description": "Restricts the lookup to one location. Omit it to take the first match of any type, which is ambiguous when a name appears in both the query and a cookie." }
        }
      },

      "MsgRequestInspectResult": {
        "type": "object",
        "description": "Everything Burp can report about a parsed request",
        "properties": {
          "method":             { "type": "string",  "nullable": true, "description": "Request method as written" },
          "url":                { "type": "string",  "nullable": true, "description": "Absolute URL, or null when the request has no service attached" },
          "path":               { "type": "string",  "nullable": true, "description": "Path exactly as it appears on the request line, query string included" },
          "path_without_query": { "type": "string",  "nullable": true, "description": "Path with the query string stripped, which is the form to compare against a route table" },
          "query":              { "type": "string",  "nullable": true, "description": "Query string without the leading question mark, empty when there is none" },
          "file_extension":     { "type": "string",  "nullable": true, "description": "Extension Burp derives from the path, empty when the path has none" },
          "content_type":       { "type": "string",  "nullable": true, "enum": ["NONE", "UNKNOWN", "AMF", "JSON", "MULTIPART", "URL_ENCODED", "XML"], "description": "How Burp classifies the body. NONE means there is no body and UNKNOWN means the body did not match a format Burp knows." },
          "in_scope":           { "type": "boolean", "nullable": true, "description": "Whether the URL is in the project's target scope. Null when no service is attached." },
          "common":             { "allOf": [{ "${'$'}ref": "#/components/schemas/MsgCommon" }], "description": "Version, body offset, headers and markers, read through the interface requests and responses share" },
          "parameters":         { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgParameter" }, "description": "Every parameter Burp found, in every location" },
          "has_parameters":     { "type": "boolean", "description": "Whether the request carries any parameter at all" },
          "has_parameters_of_type": { "type": "boolean", "nullable": true, "description": "Whether it carries any parameter of `parameter_type`, or null when no type was given" },
          "has_parameter":      { "type": "boolean", "nullable": true, "description": "Whether the named parameter exists, or null when no name was given" },
          "parameter_value":    { "type": "string",  "nullable": true, "description": "Value of the named parameter, or null when it is absent" },
          "parameter_type":     { "type": "string",  "nullable": true, "description": "The type the lookup was restricted to, echoed back" },
          "raw_request":        { "type": "string",  "description": "The request as Burp re-serialises it, which is what its own tools would send" }
        }
      },

      "MsgResponseInspectInput": {
        "type": "object",
        "required": ["raw"],
        "properties": {
          "raw":         { "type": "string", "description": "The response as HTTP text, status line first and a blank line before the body" },
          "keywords":    { "type": "array",  "items": { "type": "string" }, "description": "Keywords to count in the response. At most 100 per call. Leave empty to skip counting." },
          "header_name": { "type": "string", "nullable": true, "description": "Header to look up, reported in `common.header_present` and `common.header_value`" },
          "cookie_name": { "type": "string", "nullable": true, "description": "Cookie to look up, reported in `has_cookie` and `cookie_value`" }
        }
      },

      "MsgResponseInspectResult": {
        "type": "object",
        "description": "Everything Burp can report about a parsed response",
        "properties": {
          "status_code":          { "type": "integer", "description": "Status code from the status line" },
          "reason_phrase":        { "type": "string",  "nullable": true, "description": "Reason phrase as written. HTTP/2 responses have none, so this can be empty." },
          "stated_mime_type":     { "type": "string",  "nullable": true, "description": "MIME type taken from the Content-Type header" },
          "inferred_mime_type":   { "type": "string",  "nullable": true, "description": "MIME type Burp derives from the body bytes, ignoring what the header claims" },
          "mime_type_disagrees":  { "type": "boolean", "description": "True when the stated and inferred types differ, which is where content sniffing and upload filter bugs tend to be" },
          "status_classes":       { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgStatusClass" }, "description": "Every status class with its bounds and whether this response belongs to it" },
          "common":               { "allOf": [{ "${'$'}ref": "#/components/schemas/MsgCommon" }], "description": "Version, body offset, headers and markers, read through the interface requests and responses share" },
          "cookies":              { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgCookie" }, "description": "Cookies parsed from Set-Cookie headers" },
          "has_cookie":           { "type": "boolean", "nullable": true, "description": "Whether the named cookie is set, or null when no name was given" },
          "cookie_value":         { "type": "string",  "nullable": true, "description": "Value of the named cookie, or null when it is not set" },
          "keyword_counts":       { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgKeywordCount" }, "description": "Occurrence count per requested keyword" }
        }
      },

      "MsgRequestHeadersInput": {
        "type": "object",
        "required": ["raw"],
        "properties": {
          "raw":                   { "type": "string",  "description": "The request as HTTP text" },
          "host":                  { "type": "string",  "nullable": true, "description": "Target host to attach. Required when `apply_default_headers` is true, optional otherwise." },
          "port":                  { "type": "integer", "default": 443, "description": "Target port, used only alongside `host`" },
          "secure":                { "type": "boolean", "default": true, "description": "Whether the target uses TLS, used only alongside `host`" },
          "set":                   { "allOf": [{ "${'$'}ref": "#/components/schemas/MsgHeader" }], "description": "One header to write whether or not it already exists. Applied before add, update and remove." },
          "add":                   { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgHeader" }, "description": "Headers to append without checking whether the name is already used" },
          "update":                { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgHeader" }, "description": "Headers whose value should replace an existing header of the same name" },
          "remove":                { "type": "array",   "items": { "type": "string" }, "description": "Header names to delete. Names that are not present are reported in `not_found` instead of failing the call." },
          "apply_default_headers": { "type": "boolean", "default": false, "description": "Ask Burp to add the headers it would normally supply itself. Needs `host`, since some of them are derived from the target." }
        }
      },

      "MsgRequestEditResult": {
        "type": "object",
        "description": "The rewritten request and a record of what was applied",
        "properties": {
          "request":      { "type": "string",  "description": "The edited request as HTTP text, ready to send" },
          "http_version": { "type": "string",  "nullable": true, "description": "Version token of the edited request" },
          "body_offset":  { "type": "integer", "description": "Body offset after the edit, which moves whenever a header is added or removed" },
          "headers":      { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgHeader" }, "description": "Headers of the edited request, in order" },
          "applied":      { "type": "array",   "items": { "type": "string" }, "description": "Operations that ran, in the order they ran, each tagged with the header it touched" },
          "not_found":    { "type": "array",   "items": { "type": "string" }, "description": "Names from `remove` that were not present, so nothing was deleted for them" }
        }
      },

      "MsgRequestParametersInput": {
        "type": "object",
        "required": ["raw"],
        "properties": {
          "raw":    { "type": "string",  "description": "The request as HTTP text" },
          "host":   { "type": "string",  "nullable": true, "description": "Target host to attach, optional for parameter editing" },
          "port":   { "type": "integer", "default": 443, "description": "Target port, used only alongside `host`" },
          "secure": { "type": "boolean", "default": true, "description": "Whether the target uses TLS, used only alongside `host`" },
          "set":    { "allOf": [{ "${'$'}ref": "#/components/schemas/MsgParamSpec" }], "description": "One parameter to write whether or not it already exists. Applied before add, update and remove." },
          "add":    { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgParamSpec" }, "description": "Parameters to insert into their respective locations" },
          "update": { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgParamSpec" }, "description": "Parameters whose value should replace an existing parameter of the same name and type" },
          "remove": { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgParamSpec" }, "description": "Parameters to delete. Only the name and type are matched, so `value` may be left empty." }
        }
      },

      "MsgRequestParametersResult": {
        "type": "object",
        "description": "The rewritten request, re-parsed so the new offsets are visible",
        "properties": {
          "request":        { "type": "string",  "description": "The edited request as HTTP text, with body length corrected where a body parameter changed" },
          "parameters":     { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgParameter" }, "description": "Parameters of the edited request with their new offsets" },
          "has_parameters": { "type": "boolean", "description": "Whether the edited request still carries any parameter" },
          "applied":        { "type": "array",   "items": { "type": "string" }, "description": "Operations that ran, each tagged with the type and name it touched" }
        }
      },

      "MsgServiceInput": {
        "type": "object",
        "required": ["raw", "host"],
        "properties": {
          "raw":    { "type": "string",  "description": "The request as HTTP text" },
          "host":   { "type": "string",  "description": "New target host. The Host header is deliberately left untouched, so edit it separately when you want it to match." },
          "port":   { "type": "integer", "default": 443, "description": "New target port, 1 to 65535" },
          "secure": { "type": "boolean", "default": true, "description": "Whether to speak TLS to the new target" }
        }
      },

      "MsgServiceResult": {
        "type": "object",
        "description": "The retargeted request and the service it now points at",
        "properties": {
          "request":             { "type": "string",  "description": "The request text, unchanged apart from carrying a new target" },
          "url":                 { "type": "string",  "nullable": true, "description": "Absolute URL the request now resolves to" },
          "host":                { "type": "string",  "description": "Host of the attached service" },
          "port":                { "type": "integer", "description": "Port of the attached service" },
          "secure":              { "type": "boolean", "description": "Whether the attached service uses TLS" },
          "ip_address":          { "type": "string",  "nullable": true, "description": "Address Burp resolved for the host, or null when it could not resolve one" },
          "ip_resolution_error": { "type": "string",  "nullable": true, "description": "Why resolution failed, when it did. A host Burp has never contacted often has no address yet." }
        }
      },

      "MsgResponseEditInput": {
        "type": "object",
        "required": ["raw"],
        "properties": {
          "raw":           { "type": "string", "description": "The response as HTTP text" },
          "http_version":  { "type": "string", "nullable": true, "description": "Replacement version token. Written into the status line verbatim, so it must look like HTTP/1.0, HTTP/1.1 or HTTP/2." },
          "reason_phrase": { "type": "string", "nullable": true, "description": "Replacement reason phrase. Any text is accepted, since the phrase is advisory and clients ignore it." },
          "add":           { "type": "array",  "items": { "${'$'}ref": "#/components/schemas/MsgHeader" }, "description": "Headers to append" },
          "update":        { "type": "array",  "items": { "${'$'}ref": "#/components/schemas/MsgHeader" }, "description": "Headers whose value should replace an existing header of the same name" },
          "remove":        { "type": "array",  "items": { "type": "string" }, "description": "Header names to delete. Removing Content-Length leaves the body in place and makes the framing ambiguous, which may be exactly what you want." }
        }
      },

      "MsgResponseEditResult": {
        "type": "object",
        "description": "The rewritten response and a record of what was applied",
        "properties": {
          "response":      { "type": "string",  "description": "The edited response as HTTP text" },
          "status_code":   { "type": "integer", "description": "Status code, unchanged by this endpoint" },
          "reason_phrase": { "type": "string",  "nullable": true, "description": "Reason phrase after the edit" },
          "http_version":  { "type": "string",  "nullable": true, "description": "Version token after the edit" },
          "body_offset":   { "type": "integer", "description": "Body offset after the edit" },
          "headers":       { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgHeader" }, "description": "Headers of the edited response, in order" },
          "applied":       { "type": "array",   "items": { "type": "string" }, "description": "Operations that ran, in order" },
          "not_found":     { "type": "array",   "items": { "type": "string" }, "description": "Names from `remove` that were not present" }
        }
      },

      "MsgMarkersInput": {
        "type": "object",
        "description": "At least one of `request` and `response` must be present. Markers may only be supplied for a message that is present.",
        "properties": {
          "request":          { "type": "string", "nullable": true, "description": "The request as HTTP text" },
          "response":         { "type": "string", "nullable": true, "description": "The response as HTTP text" },
          "request_markers":  { "type": "array",  "items": { "${'$'}ref": "#/components/schemas/MsgRange" }, "description": "Ranges to highlight in the request, measured over the whole message from offset 0" },
          "response_markers": { "type": "array",  "items": { "${'$'}ref": "#/components/schemas/MsgRange" }, "description": "Ranges to highlight in the response, measured over the whole message from offset 0" },
          "notes":            { "type": "string", "nullable": true, "description": "Free text note to attach. Only kept when both messages are present, since notes live on the pair rather than on a lone message." },
          "highlight_color":  { "type": "string", "nullable": true, "enum": ["NONE", "RED", "ORANGE", "YELLOW", "GREEN", "CYAN", "BLUE", "PINK", "MAGENTA", "GRAY"], "description": "Highlight colour from Burp's fixed palette. Like notes, it is carried by the pair." }
        }
      },

      "MsgMarkersResult": {
        "type": "object",
        "description": "The marked messages, the markers as Burp stored them, and the annotations on the pair",
        "properties": {
          "request":               { "type": "string",  "nullable": true, "description": "The marked request as HTTP text. Marker positions are metadata, so the text itself is unchanged." },
          "response":              { "type": "string",  "nullable": true, "description": "The marked response as HTTP text" },
          "request_markers":       { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgMarker" }, "description": "Markers read back from the request, each with the text it covers" },
          "response_markers":      { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgMarker" }, "description": "Markers read back from the response" },
          "pair_request_markers":  { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgMarker" }, "description": "Request markers as recorded on the pair, which is the form Burp's own tools read" },
          "pair_response_markers": { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/MsgMarker" }, "description": "Response markers as recorded on the pair" },
          "pair_built":            { "type": "boolean", "description": "Whether both messages were supplied, so a pair could be formed. When false the pair fields and the annotations are empty." },
          "content_type":          { "type": "string",  "nullable": true, "enum": ["NONE", "UNKNOWN", "AMF", "JSON", "MULTIPART", "URL_ENCODED", "XML"], "description": "Body classification the pair reports, which follows the request body" },
          "has_notes":             { "type": "boolean", "nullable": true, "description": "Whether the pair carries notes" },
          "notes":                 { "type": "string",  "nullable": true, "description": "The notes on the pair" },
          "has_highlight_color":   { "type": "boolean", "nullable": true, "description": "Whether a highlight colour was set. Setting NONE counts as no colour." },
          "highlight_color":       { "type": "string",  "nullable": true, "description": "The colour on the pair" }
        }
      },

      "MsgTimingInput": {
        "type": "object",
        "required": ["raw", "host"],
        "properties": {
          "raw":                   { "type": "string",  "description": "The request as HTTP text" },
          "host":                  { "type": "string",  "description": "Target host to send to" },
          "port":                  { "type": "integer", "default": 443, "description": "Target port, 1 to 65535" },
          "secure":                { "type": "boolean", "default": true, "description": "Whether to speak TLS" },
          "http_mode":             { "type": "string",  "nullable": true, "enum": ["AUTO", "HTTP_1", "HTTP_2", "HTTP_2_IGNORE_ALPN"], "description": "Forces the protocol instead of negotiating it. HTTP_2_IGNORE_ALPN speaks HTTP/2 even when the server did not offer it, which is how request smuggling and downgrade tests are set up." },
          "connection_id":         { "type": "string",  "nullable": true, "description": "Label that pins this request to a named connection. Reuse it across calls to measure a warm request instead of a fresh handshake, or to keep a stateful sequence on one socket." },
          "server_name_indicator": { "type": "string",  "nullable": true, "description": "SNI value to send, when it should differ from the target host. Requires `secure` to be true, since SNI exists only inside a TLS handshake." },
          "verify_upstream_tls":   { "type": "boolean", "default": false, "description": "Turns on upstream certificate verification, which Burp otherwise skips. Sending to a host with an invalid certificate then fails instead of succeeding quietly." }
        }
      },

      "MsgTimingResult": {
        "type": "object",
        "description": "The response summary plus the timing Burp recorded for the exchange",
        "properties": {
          "status_code":               { "type": "integer", "nullable": true, "description": "Status code of the response, or null when none arrived" },
          "reason_phrase":             { "type": "string",  "nullable": true, "description": "Reason phrase of the response" },
          "ip_address":                { "type": "string",  "nullable": true, "description": "Address the request actually went to, which is worth checking when a host resolves to a pool" },
          "time_request_sent":         { "type": "string",  "nullable": true, "description": "When the request left Burp, as a zoned timestamp" },
          "ms_to_first_response_byte": { "type": "integer", "nullable": true, "description": "Milliseconds from sending the request to the first response byte. This is the number that moves under a time based injection." },
          "ms_to_complete_response":   { "type": "integer", "nullable": true, "description": "Milliseconds from sending the request to the last response byte. Subtract the previous field to separate backend latency from transfer time." },
          "timing_available":          { "type": "boolean", "description": "Whether Burp recorded timing for this exchange. Timing is optional in the API, so the two duration fields can be null even on a successful send." },
          "options_applied":           { "type": "array",   "items": { "type": "string" }, "description": "Request options that were actually applied, so a caller can confirm a mode or connection label took effect" },
          "response_body_offset":      { "type": "integer", "nullable": true, "description": "Body offset of the received response" }
        }
      }
"""

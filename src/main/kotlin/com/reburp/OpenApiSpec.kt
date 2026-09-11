package com.reburp

fun openApiJson(port: Int): String = """
{
  "openapi": "3.0.0",
  "info": {
    "title": "Burp Suite REST API Extension",
    "description": "A Burp Suite extension that exposes every Montoya API capability over a local HTTP REST interface. Load the JAR in Burp's Extender tab - the server starts automatically on the configured port.\n\n**Pro-only endpoints** (Scanner, Collaborator, AI) return `403` on Burp Community/free edition.",
    "version": "2.0.0",
    "contact": { "name": "reburp" }
  },
  "servers": [{ "url": "http://localhost:$port" }],
  "tags": [
    { "name": "Status",       "description": "Extension and Burp version info" },
    { "name": "Proxy",        "description": "Proxy HTTP and WebSocket history, intercept toggle" },
    { "name": "Site Map",     "description": "Site map browsing" },
    { "name": "HTTP",         "description": "Send requests, parse, diff, extract params, Repeater, Intruder, Comparer, cookies" },
    { "name": "Scanner",      "description": "Audit/crawl tasks and issue management (Pro only)" },
    { "name": "Config",       "description": "Project and user options, task engine state" },
    { "name": "Collaborator", "description": "Generate payloads and poll interactions (Pro only)" },
    { "name": "Utilities",    "description": "Encode/decode, hash, JWT, random strings, compress/decompress, response analysis" },
    { "name": "Issues",       "description": "Create custom audit issues" },
    { "name": "Persistence",  "description": "Persistent key/value preference storage" },
    { "name": "Organizer",    "description": "Burp Organizer tab integration" },
    { "name": "AI",           "description": "Burp AI chat integration (Pro only)" },
    { "name": "Scope",        "description": "Target scope management - include/exclude URLs" },
    { "name": "Sessions",     "description": "Session handling rules - auto-inject headers and tokens" },
    { "name": "Engagement",   "description": "Client-side attack PoCs, reconnaissance, content discovery, and tooling" }${extraTags()}
  ],
  "paths": {

    "/api/status": {
      "get": {
        "tags": ["Status"],
        "summary": "Extension and Burp version status",
        "description": "**[Montoya API]** Returns extension metadata, Burp version, edition, the port this server is listening on, and current project info.",
        "operationId": "getStatus",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/StatusResponse" },
                "example": {
                  "extension": "reburp",
                  "version": "2.0.0",
                  "edition": "PROFESSIONAL",
                  "port": 8090,
                  "docs_url": "http://localhost:8090/docs",
                  "project_name": "MyProject",
                  "project_id": "abc-123",
                  "command_line_args": []
                }
              }
            }
          }
        }
      }
    },

    "/api/proxy/history": {
      "get": {
        "tags": ["Proxy"],
        "summary": "List proxy HTTP history",
        "description": "**[Montoya API]** Returns paginated proxy HTTP history entries. Set `include_body=true` to include raw request/response bytes.",
        "operationId": "getProxyHistory",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "offset",       "in": "query", "schema": { "type": "integer", "default": 0 },   "description": "Pagination offset (0-based)" },
          { "name": "limit",        "in": "query", "schema": { "type": "integer", "default": 100 }, "description": "Maximum number of items to return" },
          { "name": "include_body", "in": "query", "schema": { "type": "boolean", "default": false }, "description": "Include raw request/response text" }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/HttpEntry" } }
              }
            }
          }
        }
      }
    },

    "/api/proxy/history/{index}": {
      "get": {
        "tags": ["Proxy"],
        "summary": "Get proxy history item by index",
        "description": "**[Montoya API]** Returns full request and response for a single proxy history item by its zero-based index.",
        "operationId": "getProxyHistoryItem",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "index", "in": "path", "required": true, "schema": { "type": "integer" }, "description": "Zero-based index into proxy history" },
          { "name": "include_body", "in": "query", "required": false, "schema": { "type": "boolean", "default": true }, "description": "Include request/response bodies" }
        ],
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/HttpEntry" } } } },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/proxy/history/search": {
      "get": {
        "tags": ["Proxy"],
        "summary": "Search proxy HTTP history",
        "description": "**[Montoya API]** Search proxy history entries whose request or response matches the provided Java regex.",
        "operationId": "searchProxyHistory",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "regex",        "in": "query", "required": true,  "schema": { "type": "string" }, "description": "Java-compatible regular expression" },
          { "name": "offset",       "in": "query", "schema": { "type": "integer", "default": 0 } },
          { "name": "limit",        "in": "query", "schema": { "type": "integer", "default": 100 } },
          { "name": "include_body", "in": "query", "schema": { "type": "boolean", "default": false } }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/HttpEntry" } }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/proxy/history/annotate": {
      "patch": {
        "tags": ["Proxy"],
        "summary": "Annotate proxy history items",
        "description": "**[Montoya API]** Annotate (add a note and/or highlight colour) all proxy history items whose request matches the given regex. Optionally restrict to in-scope items only.",
        "operationId": "annotateProxyHistory",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/AnnotateRequest" },
              "example": {
                "regex": "admin",
                "note": "Interesting admin endpoint",
                "highlight": "RED",
                "scope_only": false,
                "limit": 500
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK - returns count of annotated items",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/proxy/history/response-search": {
      "get": {
        "tags": ["Proxy"],
        "summary": "Search proxy history response bodies",
        "description": "**[Montoya API]** Search proxy history response bodies for the given regex. Optionally restrict to in-scope items only.",
        "operationId": "searchProxyHistoryResponses",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "regex",      "in": "query", "required": true, "schema": { "type": "string" }, "description": "Java-compatible regular expression" },
          { "name": "scope_only", "in": "query", "schema": { "type": "boolean", "default": false }, "description": "Restrict to in-scope requests only" },
          { "name": "offset",     "in": "query", "schema": { "type": "integer", "default": 0 } },
          { "name": "limit",      "in": "query", "schema": { "type": "integer", "default": 100 } }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/HttpEntry" } }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/proxy/websocket/history": {
      "get": {
        "tags": ["Proxy"],
        "summary": "List proxy WebSocket history",
        "description": "**[Montoya API]** Returns paginated WebSocket message history recorded by Burp's proxy.",
        "operationId": "getWebSocketHistory",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "offset", "in": "query", "schema": { "type": "integer", "default": 0 } },
          { "name": "limit",  "in": "query", "schema": { "type": "integer", "default": 100 } }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/WsEntry" } }
              }
            }
          }
        }
      }
    },

    "/api/proxy/websocket/history/search": {
      "get": {
        "tags": ["Proxy"],
        "summary": "Search proxy WebSocket history",
        "description": "**[Montoya API]** Regex-filters proxy WebSocket message history by message content.",
        "operationId": "searchWebSocketHistory",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "regex",  "in": "query", "required": true, "schema": { "type": "string" }, "description": "Regular expression to match against message content" },
          { "name": "offset", "in": "query", "schema": { "type": "integer", "default": 0 } },
          { "name": "limit",  "in": "query", "schema": { "type": "integer", "default": 100 } }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/WsEntry" } }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/proxy/intercept": {
      "get": {
        "tags": ["Proxy"],
        "summary": "Get proxy intercept state",
        "description": "**[Montoya API]** Returns whether Burp's intercept is currently enabled or disabled.",
        "operationId": "getInterceptState",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/InterceptRequest" }
              }
            }
          }
        }
      },
      "post": {
        "tags": ["Proxy"],
        "summary": "Toggle proxy intercept",
        "description": "**[Montoya API]** Enable or disable Burp's proxy intercept functionality.",
        "operationId": "setInterceptState",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/InterceptRequest" },
              "example": { "enabled": true }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/proxy/intercept/rules": {
      "get": {
        "tags": ["Proxy"],
        "summary": "List intercept rules",
        "description": "**[Montoya API]** Returns client and server intercept rules from Burp's proxy configuration.",
        "operationId": "listInterceptRules",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "client_rules": { "type": "array", "items": { "type": "object" } },
                    "server_rules": { "type": "array", "items": { "type": "object" } }
                  }
                }
              }
            }
          }
        }
      }
    },

    "/api/proxy/intercept/rules/client": {
      "post": {
        "tags": ["Proxy"],
        "summary": "Add client intercept rule",
        "description": "**[Montoya API]** Adds a new client-side intercept rule to Burp's proxy configuration.",
        "operationId": "addClientInterceptRule",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/InterceptRuleRequest" },
              "example": { "enabled": true, "match_type": "METHOD", "match_relationship": "MATCHES", "match_condition": "POST" }
            }
          }
        },
        "responses": {
          "200": { "description": "Added", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } }
        }
      }
    },

    "/api/proxy/intercept/rules/client/{index}": {
      "delete": {
        "tags": ["Proxy"],
        "summary": "Delete client intercept rule",
        "description": "**[Montoya API]** Deletes a client intercept rule by zero-based index.",
        "operationId": "deleteClientInterceptRule",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "index", "in": "path", "required": true, "schema": { "type": "integer" } }
        ],
        "responses": {
          "200": { "description": "Deleted", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/sitemap": {
      "get": {
        "tags": ["Site Map"],
        "summary": "Browse site map",
        "description": "**[Montoya API]** Returns site map entries optionally filtered by URL prefix. Supports pagination.",
        "operationId": "getSiteMap",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "prefix",       "in": "query", "schema": { "type": "string" }, "description": "URL prefix filter, e.g. https://example.com/api" },
          { "name": "include_body", "in": "query", "schema": { "type": "boolean", "default": false } },
          { "name": "offset",       "in": "query", "schema": { "type": "integer", "default": 0 } },
          { "name": "limit",        "in": "query", "schema": { "type": "integer", "default": 100 } }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/HttpEntry" } }
              }
            }
          }
        }
      }
    },

    "/api/sitemap/search": {
      "get": {
        "tags": ["Site Map"],
        "summary": "Search site map by regex",
        "description": "**[Montoya API]** Filters site map entries whose URL matches a regular expression. For prefix-based filtering prefer `GET /api/sitemap?prefix=...` (faster).",
        "operationId": "searchSiteMap",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "regex",        "in": "query", "required": true, "schema": { "type": "string" }, "description": "Regex applied to the full URL, e.g. `/api/user/\\d+`" },
          { "name": "include_body", "in": "query", "schema": { "type": "boolean", "default": false } },
          { "name": "offset",       "in": "query", "schema": { "type": "integer", "default": 0 } },
          { "name": "limit",        "in": "query", "schema": { "type": "integer", "default": 100 } }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/HttpEntry" } }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/sitemap/add": {
      "post": {
        "tags": ["Site Map"],
        "summary": "Add entry to site map",
        "description": "**[Montoya API]** Manually inserts a request (and optional response) into Burp's site map. Useful for seeding the site map from external sources.",
        "operationId": "addToSiteMap",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SiteMapAddRequest" },
              "example": {
                "host": "example.com", "port": 443, "use_https": true,
                "request": "GET /api/users HTTP/1.1\r\nHost: example.com\r\n\r\n",
                "response": "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n[]"
              }
            }
          }
        },
        "responses": {
          "200": { "description": "Added", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/sitemap/issues": {
      "get": {
        "tags": ["Site Map"],
        "summary": "List scanner issues from site map",
        "description": "**[Montoya API]** Returns scanner audit issues associated with site map entries, optionally filtered by URL prefix.",
        "operationId": "getSiteMapIssues",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "prefix", "in": "query", "schema": { "type": "string" }, "description": "URL prefix filter, e.g. https://example.com" },
          { "name": "offset", "in": "query", "schema": { "type": "integer", "default": 0 } },
          { "name": "limit",  "in": "query", "schema": { "type": "integer", "default": 100 } }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/SiteMapIssueDto" } }
              }
            }
          }
        }
      }
    },

    "/api/scope": {
      "get": {
        "tags": ["Site Map"],
        "summary": "Check if URL is in scope",
        "description": "**[Montoya API]** Returns whether the given URL is currently in Burp's target scope.",
        "operationId": "checkScope",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "url", "in": "query", "required": true, "schema": { "type": "string" }, "description": "The full URL to check, e.g. https://example.com/api/users" }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ScopeResult" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      },
      "post": {
        "tags": ["Site Map"],
        "summary": "Add URL to scope",
        "description": "**[Montoya API]** Adds the given URL to Burp's target scope.",
        "operationId": "addToScope",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ScopeRequest" },
              "example": { "url": "https://example.com/" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      },
      "delete": {
        "tags": ["Site Map"],
        "summary": "Remove URL from scope",
        "description": "**[Montoya API]** Removes the given URL from Burp's target scope.",
        "operationId": "removeFromScope",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ScopeRequest" },
              "example": { "url": "https://example.com/" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/send": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Send HTTP/1.1 request",
        "description": "**[Montoya API]** Issues an HTTP/1.1 request through Burp's HTTP engine. Returns 502 if the connection to the target fails.",
        "operationId": "sendHttp",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SendHttpRequest" },
              "examples": {
                "raw": {
                  "summary": "Raw format",
                  "value": {
                    "host": "example.com",
                    "port": 443,
                    "use_https": true,
                    "request": "GET / HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n"
                  }
                },
                "structured": {
                  "summary": "Structured format (no escaping needed)",
                  "value": {
                    "host": "example.com",
                    "port": 443,
                    "use_https": true,
                    "method": "POST",
                    "path": "/api/login/",
                    "headers": { "Accept": "application/json" },
                    "body": { "email": "demo@example.com", "password": "test1234" }
                  }
                }
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Request sent successfully",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/HttpSendResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "502": {
            "description": "Failed to connect to target host",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" }
              }
            }
          }
        }
      }
    },

    "/api/http/send-with-auth": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Send with automatic JWT re-auth",
        "description": "**[Native]** Sends an HTTP request. If the response status matches `retry_on` (default 401/403), automatically performs the configured auth request, extracts a token via `token_path` (e.g. `$.access`), injects it per `inject_as`, and retries. Ideal for JWT-authenticated APIs.",
        "operationId": "sendWithAuth",
        "x-api-source": "native",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SendWithAuthRequest" },
              "example": {
                "auth": {
                  "host": "app.example.com",
                  "port": 443,
                  "use_https": true,
                  "method": "POST",
                  "path": "/api/token/",
                  "body": { "email": "demo@example.com", "password": "correct-horse-battery" },
                  "token_path": "$.access"
                },
                "request": {
                  "host": "app.example.com",
                  "port": 443,
                  "use_https": true,
                  "method": "GET",
                  "path": "/api/users/"
                },
                "inject_as": "Authorization: Bearer {token}",
                "retry_on": [401, 403]
              }
            }
          }
        },
        "responses": {
          "200": { "description": "Response (possibly after re-auth)", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/HttpSendResponse" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/send-batch": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Send multiple HTTP/1.1 requests",
        "description": "**[Montoya API]** Sends up to 20 HTTP requests concurrently through Burp's HTTP engine. Returns an ordered list of responses.",
        "operationId": "sendHttpBatch",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SendBatchRequest" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "All requests processed",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/HttpSendResponse" } }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/send/http2": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Send HTTP/2 request",
        "description": "**[Montoya API]** Issues an HTTP/2 request with explicit pseudo-headers and headers through Burp's HTTP engine.",
        "operationId": "sendHttp2",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SendHttp2Request" },
              "example": {
                "host": "example.com",
                "port": 443,
                "use_https": true,
                "pseudo_headers": { ":method": "GET", ":path": "/", ":scheme": "https", ":authority": "example.com" },
                "headers": { "accept": "*/*" },
                "body": ""
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/HttpSendResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "502": { "description": "Failed to connect to target host", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" } } } }
        }
      }
    },

    "/api/http/parse/request": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Parse raw HTTP request",
        "description": "**[Montoya API]** Parses a raw HTTP request string into structured components: method, URL, headers, parameters, and body.",
        "operationId": "parseHttpRequest",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ParseRequestInput" },
              "example": { "request": "GET /api/users?id=1 HTTP/1.1\r\nHost: example.com\r\n\r\n", "include_body": true }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ParsedRequestDto" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/parse/response": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Parse raw HTTP response",
        "description": "**[Montoya API]** Parses a raw HTTP response string into structured components: status code, reason, headers, and body.",
        "operationId": "parseHttpResponse",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ParseResponseInput" },
              "example": { "response": "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"ok\":true}", "include_body": true }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ParsedResponseDto" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/diff": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Diff two HTTP requests",
        "description": "**[Montoya API]** Computes the textual diff between two raw HTTP request strings.",
        "operationId": "diffHttpRequests",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/DiffInput" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/StringResult" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/params": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Extract parameters from HTTP request",
        "description": "**[Montoya API]** Extracts all parameters (query string, body, cookies, headers, JSON, XML, multipart) from a raw HTTP request.",
        "operationId": "extractHttpParams",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ExtractParamsInput" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/ParamDto" } }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/reflect": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Find reflected parameters",
        "description": "**[Montoya API]** Finds which request parameters are reflected in the response body, helping identify potential XSS or injection points.",
        "operationId": "findReflectedParams",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/FindReflectedInput" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK - list of reflected parameter names",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "type": "string" } }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/insertion-points": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Get insertion point offsets",
        "description": "**[Montoya API]** Returns all Burp-identified insertion points (byte offsets and types) for a given HTTP request. Useful for building custom scanners.",
        "operationId": "getInsertionPoints",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/InsertionPointsInput" },
              "example": {
                "request": "POST /login HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/x-www-form-urlencoded\r\n\r\nuser=admin&pass=secret",
                "mode": "ALL_PARAMETERS"
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/InsertionPointDto" } }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/request/mutate": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Mutate HTTP request",
        "description": "**[Montoya API]** Applies one or more transformations to a raw HTTP/1.1 request string and returns the mutated result. Operations are applied in order: toggle_method, method, path, body, add_headers, update_headers, remove_headers, add_params, update_params, remove_params.",
        "operationId": "mutateRequest",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MutateRequestInput" },
              "example": {
                "request": "GET /api/users HTTP/1.1\r\nHost: example.com\r\n\r\n",
                "method": "POST",
                "path": "/api/users/create",
                "body": "{\"name\":\"test\"}",
                "add_headers": { "Content-Type": "application/json" },
                "remove_headers": ["X-Old-Header"]
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Mutated request string",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/http/response/mutate": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Mutate HTTP response",
        "description": "**[Montoya API]** Applies one or more transformations to a raw HTTP response string. Operations applied in order: status_code, body, add_headers, update_headers, remove_headers.",
        "operationId": "mutateResponse",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MutateResponseInput" },
              "example": {
                "response": "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nHello",
                "status_code": 404,
                "body": "Not Found",
                "add_headers": { "X-Custom": "value" }
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Mutated response string",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/cookies": {
      "get": {
        "tags": ["HTTP"],
        "summary": "List cookie jar",
        "description": "**[Montoya API]** Returns cookies from Burp's cookie jar, optionally filtered by domain.",
        "operationId": "getCookies",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "domain",             "in": "query", "schema": { "type": "string" },  "description": "Filter by domain (exact match)" },
          { "name": "scope_only",         "in": "query", "schema": { "type": "boolean", "default": false }, "description": "Only return cookies for in-scope domains" },
          { "name": "include_values",     "in": "query", "schema": { "type": "boolean", "default": true  }, "description": "Include cookie values in response" },
          { "name": "include_subdomains", "in": "query", "schema": { "type": "boolean", "default": false }, "description": "Include cookies matching subdomains" }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/CookieDto" } }
              }
            }
          }
        }
      },
      "post": {
        "tags": ["HTTP"],
        "summary": "Set a cookie in the jar",
        "description": "**[Montoya API]** Adds or updates a cookie in Burp's cookie jar.",
        "operationId": "setCookie",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SetCookieRequest" },
              "example": {
                "name": "session",
                "value": "abc123",
                "domain": "example.com",
                "path": "/",
                "expires_at": "2025-12-31T23:59:59Z"
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/cookies/{name}": {
      "delete": {
        "tags": ["HTTP"],
        "summary": "Delete cookie from jar",
        "description": "**[Montoya API]** Removes all cookies with the given name from Burp's cookie jar. Optionally filter by domain.",
        "operationId": "deleteCookie",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "name", "in": "path", "required": true, "schema": { "type": "string" }, "description": "Cookie name to delete" },
          { "name": "domain", "in": "query", "required": false, "schema": { "type": "string" }, "description": "Filter by domain (optional)" }
        ],
        "responses": {
          "200": { "description": "Deleted", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/repeater": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Send request to Repeater",
        "description": "**[Montoya API]** Sends a request to Burp's Repeater tab, optionally naming the tab.",
        "operationId": "sendToRepeater",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SendToToolRequest" },
              "example": {
                "host": "example.com",
                "port": 443,
                "use_https": true,
                "request": "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n",
                "tab_name": "MyRequest"
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/intruder": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Send request to Intruder",
        "description": "**[Montoya API]** Sends a request to Burp's Intruder tab, optionally naming the tab.",
        "operationId": "sendToIntruder",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SendToToolRequest" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/comparer": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Send items to Comparer",
        "description": "**[Montoya API]** Sends one or more raw HTTP items (requests or responses) to Burp's Comparer tab.",
        "operationId": "sendToComparer",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ComparerRequest" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/scanner/issues": {
      "get": {
        "tags": ["Scanner"],
        "summary": "List scan issues (Pro only)",
        "description": "**[Montoya API]** Returns paginated scan issues from Burp's scanner. Requires Burp Suite Professional.",
        "operationId": "getScannerIssues",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "offset", "in": "query", "schema": { "type": "integer", "default": 0 } },
          { "name": "limit",  "in": "query", "schema": { "type": "integer", "default": 100 } }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/ScanIssueDto" } }
              }
            }
          },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" }
        }
      }
    },

    "/api/scanner/audit": {
      "post": {
        "tags": ["Scanner"],
        "summary": "Start audit with built-in configuration (Pro only)",
        "description": "**[Montoya API]** Starts a Burp audit using one of the built-in scan configurations. Requires Burp Suite Professional.\n\nAvailable configuration values: CRAWL_AND_AUDIT_EVERYTHING_FAST, CRAWL_AND_AUDIT_EVERYTHING_THOROUGH, CRAWL_EVERYTHING_FAST, CRAWL_EVERYTHING_THOROUGH, AUDIT_COVERAGE_SPEED, AUDIT_COVERAGE_THOROUGH, AUDIT_ACCURACY_SPEED, AUDIT_ACCURACY_THOROUGH.",
        "operationId": "startAudit",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/StartAuditRequest" },
              "example": {
                "configuration": "CRAWL_AND_AUDIT_EVERYTHING_FAST",
                "host": "example.com",
                "port": 443,
                "use_https": true,
                "requests": ["GET / HTTP/1.1\r\nHost: example.com\r\n\r\n"]
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Audit task created",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ScanTaskDto" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" }
        }
      }
    },

    "/api/scanner/audit/from-history": {
      "post": {
        "tags": ["Scanner"],
        "summary": "Audit a proxy history item",
        "description": "**[Montoya API]** Starts an active or passive audit on a request from proxy history identified by its zero-based index. Returns a scan task ID.",
        "operationId": "auditFromHistory",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/AuditFromHistoryRequest" },
              "example": { "index": 5, "configuration": "ACTIVE" }
            }
          }
        },
        "responses": {
          "200": { "description": "Task started", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ScanTaskDto" } } } },
          "403": { "${'$'}ref": "#/components/responses/Forbidden" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/scanner/audit/mode": {
      "post": {
        "tags": ["Scanner"],
        "summary": "Start audit by mode (Pro only)",
        "description": "**[Montoya API]** Starts a Burp audit task using ACTIVE or PASSIVE mode. Requires Burp Suite Professional.",
        "operationId": "startAuditMode",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/StartAuditModeRequest" },
              "example": {
                "mode": "ACTIVE",
                "host": "example.com",
                "port": 443,
                "use_https": true,
                "requests": ["GET /api/users HTTP/1.1\r\nHost: example.com\r\n\r\n"]
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Audit task created",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ScanTaskDto" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" }
        }
      }
    },

    "/api/scanner/crawl": {
      "post": {
        "tags": ["Scanner"],
        "summary": "Start crawl (Pro only)",
        "description": "**[Montoya API]** Starts a Burp crawl task with the provided seed URLs. Requires Burp Suite Professional.",
        "operationId": "startCrawl",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/StartCrawlRequest" },
              "example": { "seed_urls": ["https://example.com/", "https://example.com/api"] }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Crawl task created",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ScanTaskDto" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" }
        }
      }
    },

    "/api/scanner/tasks/{id}": {
      "get": {
        "tags": ["Scanner"],
        "summary": "Get scan task status",
        "description": "**[Montoya API]** Returns the current status and progress of a scanner task by its ID.",
        "operationId": "getScanTask",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" }, "description": "Task ID returned when creating the task" }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ScanTaskDto" }
              }
            }
          },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      },
      "delete": {
        "tags": ["Scanner"],
        "summary": "Delete scan task",
        "description": "**[Montoya API]** Deletes a scanner task. If the task is still running it will be cancelled first.",
        "operationId": "deleteScanTask",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" }, "description": "Task ID to delete" }
        ],
        "responses": {
          "200": {
            "description": "Task deleted",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/scanner/report": {
      "post": {
        "tags": ["Scanner"],
        "summary": "Generate scan report",
        "description": "**[Montoya API]** Generates a scan report in HTML or XML format and writes it to the specified path on disk.",
        "operationId": "generateScanReport",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ScanReportRequest" },
              "example": {
                "task_id": "task-abc-123",
                "all_issues": false,
                "format": "HTML",
                "path": "/tmp/scan-report.html"
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Report generated",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" }
        }
      }
    },

    "/api/scanner/report/download": {
      "post": {
        "tags": ["Scanner"],
        "summary": "Download scan report as base64",
        "description": "**[Montoya API]** Generates a scanner report and returns the file content base64-encoded in the response body. Useful when the caller does not share a filesystem with Burp.",
        "operationId": "downloadScanReport",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": {
                "type": "object",
                "required": ["format"],
                "properties": {
                  "format": { "type": "string", "enum": ["HTML", "XML"], "description": "Report format" },
                  "prefix": { "type": "string", "nullable": true, "description": "URL prefix filter (e.g. https://app.example.com/api)" }
                }
              },
              "example": { "format": "HTML", "prefix": "https://app.example.com" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Report data",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "format":      { "type": "string" },
                    "size_bytes":  { "type": "integer" },
                    "data_base64": { "type": "string", "description": "Base64-encoded report file" }
                  }
                }
              }
            }
          },
          "403": { "${'$'}ref": "#/components/responses/Forbidden" }
        }
      }
    },

    "/api/scanner/bchecks/import": {
      "post": {
        "tags": ["Scanner"],
        "summary": "Import BCheck script (Pro only)",
        "description": "**[Montoya API]** Imports a BCheck script into Burp's scanner. Requires Burp Suite Professional. Returns 422 if the import succeeded but produced validation warnings.",
        "operationId": "importBCheck",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ImportScriptRequest" },
              "example": { "source": "metadata:\n  language: v1-beta\n  name: My BCheck\n...", "enabled": true }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Import succeeded",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" },
          "422": {
            "description": "Import succeeded with validation warnings",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" }
              }
            }
          }
        }
      }
    },

    "/api/scanner/bambda/import": {
      "post": {
        "tags": ["Scanner"],
        "summary": "Import Bambda script",
        "description": "**[Montoya API]** Imports a Bambda (Java lambda) script into Burp's HTTP handler. Returns 422 if the import succeeded but produced compilation warnings.",
        "operationId": "importBambda",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ImportScriptRequest" },
              "example": { "source": "return requestResponse.request().method().equals(\"POST\");", "enabled": true }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Import succeeded",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "422": {
            "description": "Import succeeded with warnings",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" }
              }
            }
          }
        }
      }
    },

    "/api/config/project": {
      "get": {
        "tags": ["Config"],
        "summary": "Get project configuration",
        "description": "**[Montoya Config]** Exports project options as JSON. Use ?section=<name> to export a specific section (e.g. ?section=proxy exports only proxy rules; ?section=scanner exports scanner config including BChecks). Omit section to export all.",
        "operationId": "getProjectConfig",
        "x-api-source": "montoya-config",
        "parameters": [
          { "name": "path", "in": "query", "schema": { "type": "string" }, "description": "Dot-notation path to a config sub-tree, e.g. project_options.connections" },
          { "name": "section", "in": "query", "schema": { "type": "string" }, "description": "Optional config section name to export (e.g. proxy, scanner). Omit to export all." }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "object", "description": "Arbitrary Burp config JSON object" }
              }
            }
          }
        }
      },
      "post": {
        "tags": ["Config"],
        "summary": "Set project configuration",
        "description": "**[Montoya Config]** Merges the provided JSON into the current Burp project configuration.",
        "operationId": "setProjectConfig",
        "x-api-source": "montoya-config",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SetConfigRequest" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/config/user": {
      "get": {
        "tags": ["Config"],
        "summary": "Get user configuration",
        "description": "**[Montoya Config]** Exports user options as JSON. Use ?section=<name> to export a specific section (e.g. ?section=proxy exports proxy user settings; Bambda filters are in the proxy section). Omit section to export all.",
        "operationId": "getUserConfig",
        "x-api-source": "montoya-config",
        "parameters": [
          { "name": "path", "in": "query", "schema": { "type": "string" }, "description": "Dot-notation path to a sub-tree" },
          { "name": "section", "in": "query", "schema": { "type": "string" }, "description": "Optional config section name to export (e.g. proxy, scanner). Omit to export all." }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "object" }
              }
            }
          }
        }
      },
      "post": {
        "tags": ["Config"],
        "summary": "Set user configuration",
        "description": "**[Montoya Config]** Merges the provided JSON into the current Burp user configuration.",
        "operationId": "setUserConfig",
        "x-api-source": "montoya-config",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SetConfigRequest" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/config/tasks": {
      "get": {
        "tags": ["Config"],
        "summary": "Get task engine state",
        "description": "**[Montoya Config]** Returns whether Burp's background task engine is currently running.",
        "operationId": "getTaskEngineState",
        "x-api-source": "montoya-config",
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/TaskEngineStateDto" }
              }
            }
          }
        }
      },
      "put": {
        "tags": ["Config"],
        "summary": "Set task engine state",
        "description": "**[Montoya Config]** Starts or stops Burp's background task engine.",
        "operationId": "setTaskEngineState",
        "x-api-source": "montoya-config",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SetTaskEngineStateRequest" },
              "example": { "running": true }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/sessions/rules": {
      "get": {
        "tags": ["Sessions"],
        "summary": "List session handling rules",
        "description": "**[Montoya API]** Returns all Burp session handling rules from user options.",
        "operationId": "listSessionRules",
        "x-api-source": "montoya",
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "type": "array", "items": { "type": "object" } } } } }
        }
      }
    },

    "/api/sessions/rules/add-header": {
      "post": {
        "tags": ["Sessions"],
        "summary": "Add header session rule",
        "description": "**[Montoya API]** Creates a session handling rule that automatically adds a header (e.g. `Authorization: Bearer ...`) to matching requests. Persists to Burp user options.",
        "operationId": "addHeaderSessionRule",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/AddHeaderRuleRequest" },
              "example": {
                "header_name": "Authorization",
                "header_value": "Bearer eyJhbGciOiJIUzI1NiJ9...",
                "name": "JWT Bearer token",
                "scope_url": "https://app.example.com"
              }
            }
          }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } }
        }
      }
    },

    "/api/sessions/rules/{index}": {
      "delete": {
        "tags": ["Sessions"],
        "summary": "Delete session rule",
        "description": "**[Montoya API]** Removes a session handling rule by its zero-based index.",
        "operationId": "deleteSessionRule",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "index", "in": "path", "required": true, "schema": { "type": "integer" } }
        ],
        "responses": {
          "200": { "description": "Deleted", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/extensions": {
      "get": {
        "tags": ["Config"],
        "summary": "List loaded extensions",
        "description": "**[Montoya API]** Returns the list of Burp extensions currently loaded, including name, enabled status, type, and file path.",
        "operationId": "listExtensions",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name":    { "type": "string" },
                      "enabled": { "type": "boolean" },
                      "type":    { "type": "string" },
                      "file":    { "type": "string" }
                    }
                  }
                }
              }
            }
          }
        }
      }
    },

    "/api/collaborator/generate": {
      "post": {
        "tags": ["Collaborator"],
        "summary": "Generate Collaborator payload (Pro only)",
        "description": "**[Montoya API]** Generates a new Burp Collaborator payload with an interaction ID and secret key for polling. Requires Burp Suite Professional.",
        "operationId": "generateCollaboratorPayload",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/CollaboratorGenerateRequest" },
              "example": { "options": ["DNS", "HTTP"], "custom_data": "myprobe" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/CollaboratorGeneratedDto" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" }
        }
      }
    },

    "/api/collaborator/poll/{secretKey}": {
      "get": {
        "tags": ["Collaborator"],
        "summary": "Poll Collaborator interactions (Pro only)",
        "description": "**[Montoya API]** Polls for Burp Collaborator interactions associated with the given secret key. Returns all recorded interactions since the last poll.",
        "operationId": "pollCollaboratorInteractions",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "secretKey", "in": "path", "required": true, "schema": { "type": "string" }, "description": "Secret key returned by /api/collaborator/generate" }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/CollaboratorInteractionDto" } }
              }
            }
          },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/utils/url/encode": {
      "post": {
        "tags": ["Utilities"],
        "summary": "URL encode string",
        "description": "**[Montoya API]** URL-encodes a string using Java's default encoding (space as + and special characters as %XX).",
        "operationId": "urlEncode",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/StringInput" },
              "example": { "value": "hello world & foo=bar" }
            }
          }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/url/decode": {
      "post": {
        "tags": ["Utilities"],
        "summary": "URL decode string",
        "description": "**[Montoya API]** URL-decodes a percent-encoded string.",
        "operationId": "urlDecode",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/StringInput" },
              "example": { "value": "hello+world+%26+foo%3Dbar" }
            }
          }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/url/encode-mode": {
      "post": {
        "tags": ["Utilities"],
        "summary": "URL encode with mode",
        "description": "**[Montoya API]** URL-encodes a string using one of the supported encoding modes: JAVA_DEFAULT, KEY_CHARACTERS, ALL_CHARACTERS, ALL_CHARACTERS_UNICODE.",
        "operationId": "urlEncodeMode",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UrlEncodeInput" },
              "example": { "value": "<script>alert(1)</script>", "encoding": "ALL_CHARACTERS" }
            }
          }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/base64/encode": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Base64 encode",
        "description": "**[Montoya API]** Base64-encodes a string using standard (padded, non-URL-safe) encoding.",
        "operationId": "base64Encode",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/StringInput" },
              "example": { "value": "Hello, World!" }
            }
          }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/base64/decode": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Base64 decode",
        "description": "**[Montoya API]** Decodes a standard Base64-encoded string.",
        "operationId": "base64Decode",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/StringInput" },
              "example": { "value": "SGVsbG8sIFdvcmxkIQ==" }
            }
          }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/base64/encode-options": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Base64 encode with options",
        "description": "**[Montoya API]** Base64-encodes a string with control over URL-safe alphabet and padding.",
        "operationId": "base64EncodeOptions",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/Base64EncodeInput" },
              "example": { "value": "Hello, World!", "url_safe": true, "no_padding": false }
            }
          }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/base64/decode-options": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Base64 decode with options",
        "description": "**[Montoya API]** Decodes a Base64-encoded string with optional URL-safe alphabet support.",
        "operationId": "base64DecodeOptions",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/Base64DecodeInput" },
              "example": { "value": "SGVsbG8sIFdvcmxkIQ==", "url_safe": false }
            }
          }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/html/encode": {
      "post": {
        "tags": ["Utilities"],
        "summary": "HTML encode",
        "description": "**[Montoya API]** HTML-encodes a string. Modes: STANDARD (encode <, >, &, \", '), ALL_CHARACTERS (named entities), ALL_CHARACTERS_DECIMAL (decimal numeric), ALL_CHARACTERS_HEX (hex numeric).",
        "operationId": "htmlEncode",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/HtmlEncodeInput" },
              "example": { "value": "<script>alert('xss')</script>", "encoding": "STANDARD" }
            }
          }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/html/decode": {
      "post": {
        "tags": ["Utilities"],
        "summary": "HTML decode",
        "description": "**[Montoya API]** HTML-decodes a string, converting HTML entities back to their characters.",
        "operationId": "htmlDecode",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/StringInput" },
              "example": { "value": "&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;" }
            }
          }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/string/to-hex": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Convert ASCII string to hex",
        "description": "**[Montoya API]** Converts each character in the input string to its two-digit hexadecimal representation.",
        "operationId": "stringToHex",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/StringInput" },
              "example": { "value": "ABC" }
            }
          }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/string/from-hex": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Convert hex to ASCII string",
        "description": "**[Montoya API]** Converts a hex-encoded string (e.g. 414243) back to its ASCII representation.",
        "operationId": "stringFromHex",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/StringInput" },
              "example": { "value": "414243" }
            }
          }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/hash": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Compute hash (legacy)",
        "description": "**[Montoya API]** Computes a cryptographic hash of the input string. Supported algorithms: MD5, SHA1, SHA256, SHA512. For a broader algorithm selection use /api/utils/digest.",
        "operationId": "computeHash",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/HashInput" },
              "example": { "algorithm": "SHA256", "value": "secret" }
            }
          }
        },
        "responses": {
          "200": { "description": "Hex-encoded hash string", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/digest": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Compute cryptographic digest",
        "description": "**[Montoya API]** Computes a cryptographic digest using Burp's Montoya CryptoUtils. Returns a lowercase hex string.\n\nSupported algorithms: SHA_256, SHA_512, MD5, SHA3_256, SHA3_512, BLAKE2B_256, BLAKE3_256, KECCAK_256, SHA_1, SHA_384, RIPEMD_160, WHIRLPOOL, TIGER, SM3.",
        "operationId": "computeDigest",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/DigestInput" },
              "example": { "algorithm": "SHA_256", "value": "secret" }
            }
          }
        },
        "responses": {
          "200": { "description": "Hex-encoded digest string", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/jwt/decode": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Decode JWT (no verification)",
        "description": "**[Montoya API]** Decodes a JWT token into its header, payload, and signature parts without verifying the signature.",
        "operationId": "decodeJwt",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/StringInput" },
              "example": { "value": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Decoded JWT parts",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "header":    { "type": "object" },
                    "payload":   { "type": "object" },
                    "signature": { "type": "string" }
                  }
                }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/random": {
      "get": {
        "tags": ["Utilities"],
        "summary": "Generate random string",
        "description": "**[Montoya API]** Generates a random string of the specified length using the specified character set.",
        "operationId": "generateRandomString",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "length",  "in": "query", "schema": { "type": "integer", "default": 16 }, "description": "Length of the random string to generate" },
          { "name": "charset", "in": "query", "schema": { "type": "string", "enum": ["ALPHANUMERIC", "ALPHA", "NUMERIC", "HEX", "PRINTABLE"], "default": "ALPHANUMERIC" }, "description": "Character set to use" }
        ],
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/decompress": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Decompress base64-encoded data",
        "description": "**[Montoya API]** Decompresses base64-encoded compressed data and returns the decompressed string. Supported encodings: GZIP, DEFLATE, BROTLI, IDENTITY, RAW.",
        "operationId": "decompress",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/DecompressInput" },
              "example": { "base64": "H4sIAAAAAAAAA0rNS8wBAIINrCAEAAAA", "encoding": "GZIP" }
            }
          }
        },
        "responses": {
          "200": { "description": "Decompressed string", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/compress": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Compress string and return base64",
        "description": "**[Montoya API]** Compresses a string and returns it base64-encoded. Supported encodings: GZIP, DEFLATE, BROTLI.",
        "operationId": "compress",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/CompressInput" },
              "example": { "value": "Hello, World!", "encoding": "GZIP" }
            }
          }
        },
        "responses": {
          "200": { "description": "Base64-encoded compressed data", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/response/keywords": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Analyze keyword variance across responses",
        "description": "**[Montoya API]** Analyzes a list of raw HTTP responses (2-50) and categorizes the provided keywords as variant (appears in some but not all responses) or invariant (appears in all or none). Useful for differential analysis in blind injection attacks.",
        "operationId": "analyzeResponseKeywords",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ResponseKeywordsInput" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ResponseKeywordsResult" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/response/variations": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Analyze attribute variance across responses",
        "description": "**[Montoya API]** Analyzes a list of raw HTTP responses (2-50) and returns which response attributes (status code, content type, body length, etc.) vary across them. Useful for identifying stable comparison points when building custom detections.",
        "operationId": "analyzeResponseVariations",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ResponseVariationsInput" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ResponseVariationsResult" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/issues": {
      "post": {
        "tags": ["Issues"],
        "summary": "Create custom audit issue",
        "description": "**[Montoya API]** Creates a custom scan issue and adds it to Burp's Target tab issue list.",
        "operationId": "createIssue",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/CreateIssueRequest" },
              "example": {
                "name": "Sensitive Data Exposure",
                "detail": "The response contains a plain-text API key.",
                "base_url": "https://example.com/api/config",
                "severity": "HIGH",
                "confidence": "CERTAIN",
                "http_request": "GET /api/config HTTP/1.1\r\nHost: example.com\r\n\r\n",
                "http_response": "HTTP/1.1 200 OK\r\n\r\n{\"api_key\":\"sk-1234\"}"
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Issue created",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/preferences": {
      "get": {
        "tags": ["Persistence"],
        "summary": "Get all preferences by type",
        "description": "**[Montoya API]** Returns all persisted extension preferences for the given type as a key/value map.",
        "operationId": "getPreferences",
        "x-api-source": "montoya",
        "parameters": [
          {
            "name": "type",
            "in": "query",
            "required": true,
            "schema": { "type": "string", "enum": ["string", "boolean", "byte", "short", "int", "long"] },
            "description": "Primitive type of the stored preference values to retrieve"
          }
        ],
        "responses": {
          "200": {
            "description": "OK - key/value map for the given type",
            "content": {
              "application/json": {
                "schema": { "type": "object", "additionalProperties": true }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/preferences/{key}": {
      "put": {
        "tags": ["Persistence"],
        "summary": "Set preference",
        "description": "**[Montoya API]** Persists a preference value under the given key. The value will be coerced to the specified type.",
        "operationId": "setPreference",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "key", "in": "path", "required": true, "schema": { "type": "string" }, "description": "Preference key" }
        ],
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/PersistenceValueRequest" },
              "example": { "type": "string", "value": "my-value" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      },
      "delete": {
        "tags": ["Persistence"],
        "summary": "Delete preference",
        "description": "**[Montoya API]** Deletes a persisted preference value by key and type.",
        "operationId": "deletePreference",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "key", "in": "path", "required": true, "schema": { "type": "string" }, "description": "Preference key to delete" },
          { "name": "type", "in": "query", "required": true, "schema": { "type": "string", "enum": ["string", "boolean", "byte", "short", "int", "long"] }, "description": "Type of the preference to delete" }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/organizer/items": {
      "get": {
        "tags": ["Organizer"],
        "summary": "List organizer items",
        "description": "**[Montoya API]** Returns items from Burp's Organizer tab, optionally filtered by status. Valid status values: NEW, IN_PROGRESS, POSTPONED, DONE, IGNORED, UNKNOWN.",
        "operationId": "getOrganizerItems",
        "x-api-source": "montoya",
        "parameters": [
          {
            "name": "status",
            "in": "query",
            "schema": { "type": "string", "enum": ["NEW", "IN_PROGRESS", "POSTPONED", "DONE", "IGNORED", "UNKNOWN"] },
            "description": "Filter items by status"
          }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/OrganizerItemDto" } }
              }
            }
          }
        }
      }
    },

    "/api/organizer/send": {
      "post": {
        "tags": ["Organizer"],
        "summary": "Send request to Organizer",
        "description": "**[Montoya API]** Sends an HTTP request to Burp's Organizer tab for later review.",
        "operationId": "sendToOrganizer",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SendHttpRequest" },
              "example": {
                "host": "example.com",
                "port": 443,
                "use_https": true,
                "request": "GET /api/admin HTTP/1.1\r\nHost: example.com\r\n\r\n"
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/websocket": {
      "get": {
        "tags": ["WebSocket Client"],
        "summary": "List active WebSocket connections",
        "description": "**[Montoya API]** Returns metadata for all outbound WebSocket connections currently managed by reburp.",
        "operationId": "listWebSocketConnections",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/WsConnectionInfoDto" } } } }
          }
        }
      }
    },

    "/api/websocket/connect": {
      "post": {
        "tags": ["WebSocket Client"],
        "summary": "Open WebSocket connection",
        "description": "**[Montoya API]** Opens a new outbound WebSocket connection through Burp's engine. Returns a connection `id` used for subsequent send/receive/close calls.",
        "operationId": "connectWebSocket",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/WsConnectRequest" },
              "example": { "host": "example.com", "port": 443, "use_https": true, "path": "/ws" }
            }
          }
        },
        "responses": {
          "200": { "description": "Connected", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/WsConnectionInfoDto" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "502": { "description": "WebSocket upgrade failed", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" } } } }
        }
      }
    },

    "/api/websocket/{id}/send-text": {
      "post": {
        "tags": ["WebSocket Client"],
        "summary": "Send text frame",
        "description": "**[Montoya API]** Sends a UTF-8 text message over an active WebSocket connection.",
        "operationId": "sendWebSocketText",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" }, "description": "Connection ID from /api/websocket/connect" }
        ],
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/WsSendRequest" },
              "example": { "message": "{\"type\":\"ping\"}" }
            }
          }
        },
        "responses": {
          "200": { "description": "Sent", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/websocket/{id}/send-binary": {
      "post": {
        "tags": ["WebSocket Client"],
        "summary": "Send binary frame",
        "description": "**[Montoya API]** Sends a binary message (base64-encoded) over an active WebSocket connection.",
        "operationId": "sendWebSocketBinary",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } }
        ],
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/WsSendBinaryRequest" },
              "example": { "base64": "aGVsbG8=" }
            }
          }
        },
        "responses": {
          "200": { "description": "Sent", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/websocket/{id}/messages": {
      "get": {
        "tags": ["WebSocket Client"],
        "summary": "Get received messages",
        "description": "**[Montoya API]** Returns queued messages (both directions) for a WebSocket connection since it was opened.",
        "operationId": "getWebSocketMessages",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } }
        ],
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/WsClientMessageDto" } } } } },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/websocket/{id}": {
      "delete": {
        "tags": ["WebSocket Client"],
        "summary": "Close WebSocket connection",
        "description": "**[Montoya API]** Closes and removes an active WebSocket connection.",
        "operationId": "closeWebSocket",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } }
        ],
        "responses": {
          "200": { "description": "Closed", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/ai/status": {
      "get": {
        "tags": ["AI"],
        "summary": "Check if AI is enabled",
        "description": "**[Montoya API]** Returns whether Burp AI features are currently enabled and available.",
        "operationId": "getAiStatus",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/AiStatusResponse" }
              }
            }
          }
        }
      }
    },

    "/api/ai/chat": {
      "post": {
        "tags": ["AI"],
        "summary": "Chat with Burp AI",
        "description": "**[Montoya API]** Sends a conversation to Burp's built-in AI and returns the assistant's response. Returns 403 if AI features are not enabled in Burp settings.",
        "operationId": "aiChat",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/AiChatRequest" },
              "example": {
                "messages": [
                  { "role": "user", "content": "Analyze this request for SQL injection: GET /users?id=1 HTTP/1.1" }
                ],
                "system_prompt": "You are a security expert specializing in web application vulnerabilities."
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "AI response",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/AiChatResponse" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": {
            "description": "AI features are not enabled in Burp settings",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" }
              }
            }
          }
        }
      }
    },

    "/api/scope/rules": {
      "get": {
        "tags": ["Scope"],
        "summary": "List scope rules",
        "description": "**[Montoya API]** Returns current include and exclude scope rules parsed from Burp's project configuration.",
        "operationId": "listScopeRules",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ScopeRulesDto" }
              }
            }
          }
        }
      }
    },

    "/api/scope/check": {
      "get": {
        "tags": ["Scope"],
        "summary": "Check if URL is in scope",
        "description": "**[Montoya API]** Returns whether the given URL matches the current Burp target scope.",
        "operationId": "checkScopeNew",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "url", "in": "query", "required": true, "schema": { "type": "string" }, "description": "The full URL to check" }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/ScopeCheckResult" }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/scope/include": {
      "post": {
        "tags": ["Scope"],
        "summary": "Add URL to scope",
        "description": "**[Montoya API]** Includes a URL or prefix pattern in the Burp target scope.",
        "operationId": "includeScopeUrl",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ScopeUrlRequest" },
              "example": { "url": "https://example.com/" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/scope/exclude": {
      "post": {
        "tags": ["Scope"],
        "summary": "Remove URL from scope",
        "description": "**[Montoya API]** Excludes a URL or prefix pattern from the Burp target scope.",
        "operationId": "excludeScopeUrl",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ScopeUrlRequest" },
              "example": { "url": "https://example.com/admin" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/repeater/send": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Send request to Repeater",
        "description": "**[Montoya API]** Queues a raw HTTP request in the Burp Repeater tab. The request appears immediately; you still need to click Send in Burp.",
        "operationId": "sendToRepeaterRaw",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SendToRepeaterRequest" },
              "example": { "raw_request": "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n", "tab_name": "MyTab" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/intruder/send": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Send request to Intruder",
        "description": "**[Montoya API]** Queues a raw HTTP request in the Burp Intruder tab. Mark insertion points with § symbols (e.g. \"username=§admin§\"). Configure attack type and payload sets in the Burp UI before starting.",
        "operationId": "sendToIntruderRaw",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SendToIntruderRequest" },
              "example": { "raw_request": "GET /search?q=§test§ HTTP/1.1\r\nHost: example.com\r\n\r\n", "tab_name": "FuzzTab" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/proxy/match-replace": {
      "get": {
        "tags": ["Proxy"],
        "summary": "List match-and-replace rules",
        "description": "**[Montoya Config]** Returns all active Burp proxy match-and-replace rules. Rules are identified by their zero-based index (id field).",
        "operationId": "listMatchReplaceRules",
        "x-api-source": "montoya-config",
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "${'$'}ref": "#/components/schemas/MatchReplaceListResponse" }
              }
            }
          }
        }
      },
      "post": {
        "tags": ["Proxy"],
        "summary": "Create match-and-replace rule",
        "description": "**[Montoya Config]** Appends a new rule to Burp's proxy match-and-replace list.",
        "operationId": "createMatchReplaceRule",
        "x-api-source": "montoya-config",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MatchReplaceRule" },
              "example": {
                "rule_type": "request_header",
                "string_match": "^User-Agent:.*$",
                "string_replace": "User-Agent: CustomAgent/1.0",
                "is_simple_match": false,
                "enabled": true,
                "comment": "Override User-Agent"
              }
            }
          }
        },
        "responses": {
          "201": {
            "description": "Rule created",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      },
      "delete": {
        "tags": ["Proxy"],
        "summary": "Clear all match-and-replace rules",
        "description": "**[Montoya Config]** Removes all proxy match-and-replace rules.",
        "operationId": "clearMatchReplaceRules",
        "x-api-source": "montoya-config",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } }
          }
        }
      }
    },

    "/api/proxy/match-replace/{id}": {
      "put": {
        "tags": ["Proxy"],
        "summary": "Update match-and-replace rule",
        "description": "**[Montoya Config]** Replaces the rule at zero-based index {id}.",
        "operationId": "updateMatchReplaceRule",
        "x-api-source": "montoya-config",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "integer" }, "description": "Zero-based rule index from GET response" }
        ],
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MatchReplaceRule" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      },
      "delete": {
        "tags": ["Proxy"],
        "summary": "Delete match-and-replace rule",
        "description": "**[Montoya Config]** Deletes the rule at zero-based index {id}. Remaining rules are re-indexed.",
        "operationId": "deleteMatchReplaceRule",
        "x-api-source": "montoya-config",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "integer" }, "description": "Zero-based rule index" }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } }
          },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/burp/shutdown": {
      "post": {
        "tags": ["Config"],
        "summary": "Shutdown Burp Suite",
        "description": "**[Montoya API]** Gracefully shuts down Burp Suite. The response is sent before shutdown begins (~500ms delay). Use with caution - this terminates the Burp process.",
        "operationId": "shutdownBurp",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK - shutdown initiated",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } }
          }
        }
      }
    },

    "/api/scanner/bchecks/config": {
      "get": {
        "tags": ["Scanner"],
        "summary": "Export scanner config (BChecks)",
        "description": "**[Montoya Config]** Exports the raw Burp scanner project config section as JSON. BCheck scripts are stored here. Use PUT /api/config/project with modified JSON to update them. Pro only.",
        "operationId": "getScannerBchecksConfig",
        "x-api-source": "montoya-config",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } }
          },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" }
        }
      }
    },

    "/api/scanner/bambda/config": {
      "get": {
        "tags": ["Scanner"],
        "summary": "Export user config (Bambdas)",
        "description": "**[Montoya Config]** Exports the raw Burp user options JSON. Bambda filters are stored here. Use PUT /api/config/user with modified JSON to update them.",
        "operationId": "getScannerBambdaConfig",
        "x-api-source": "montoya-config",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } }
          }
        }
      }
    },

    "/api/utils/jwt/encode": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Encode and sign a JWT",
        "description": "**[Native]** Creates a signed or unsigned JWT token. Supports HS256, HS384, HS512, and none (unsigned). Supply payload_json as a JSON object string. Complement to POST /api/utils/jwt/decode.",
        "operationId": "encodeJwt",
        "x-api-source": "native",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/JwtEncodeRequest" },
              "example": {
                "payload_json": "{\"sub\":\"1234567890\",\"name\":\"John Doe\",\"iat\":1516239022}",
                "secret": "my-secret-key",
                "algorithm": "HS256"
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/JwtEncodeResponse" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/headers/analyze": {
      "post": {
        "tags": ["Utilities"],
        "summary": "Analyze HTTP security headers",
        "description": "**[Native]** Checks a server response for the presence and correctness of 10 common HTTP security headers (HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy, COOP, CORP, X-XSS-Protection, Cache-Control). Returns a per-header status and an overall grade (A+/A/B/C/D/F).",
        "operationId": "analyzeSecurityHeaders",
        "x-api-source": "native",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/HeaderAnalysisRequest" },
              "example": {
                "headers": {
                  "Strict-Transport-Security": "max-age=31536000; includeSubDomains",
                  "X-Frame-Options": "DENY",
                  "Content-Security-Policy": "default-src 'self'"
                }
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/HeaderAnalysisResponse" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/payloads": {
      "get": {
        "tags": ["Utilities"],
        "summary": "Get security testing payloads",
        "description": "**[Native]** Returns a curated list of security testing payloads for a given vulnerability category. Useful for parameter fuzzing, manual testing, or seeding intruder attacks.",
        "operationId": "getPayloads",
        "x-api-source": "native",
        "parameters": [
          {
            "name": "category",
            "in": "query",
            "schema": {
              "type": "string",
              "enum": ["xss", "sqli", "path_traversal", "ssti", "xxe", "cmd", "open_redirect", "lfi", "nosqli", "crlf"],
              "default": "xss"
            },
            "description": "Vulnerability category for which to return payloads"
          }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/PayloadsResponse" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/engagement/csrf-poc": {
      "post": {
        "tags": ["Engagement"],
        "summary": "Generate CSRF proof-of-concept",
        "description": "**[Native]** Parses a raw HTTP request and generates a cross-site request forgery (CSRF) proof-of-concept in HTML, Fetch JavaScript, or auto-submitting form format. Useful for testing and demonstrating CSRF vulnerabilities.",
        "operationId": "generateCsrfPoc",
        "x-api-source": "native",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/CsrfPocRequest" },
              "example": {
                "request": "POST /transfer HTTP/1.1\r\nHost: bank.example.com\r\nContent-Type: application/x-www-form-urlencoded\r\n\r\nto_account=attacker&amount=1000",
                "host": "bank.example.com",
                "port": 443,
                "use_https": true,
                "format": "AUTO_SUBMIT"
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/CsrfPocResponse" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    },

    "/api/engagement/find-references": {
      "post": {
        "tags": ["Engagement"],
        "summary": "Find URL references in proxy/sitemap",
        "description": "**[Native]** Searches proxy history and/or site map response bodies for references to a URL, hostname, path, or string fragment. Useful for discovering hidden or referenced endpoints.",
        "operationId": "findReferences",
        "x-api-source": "native",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/FindReferencesRequest" },
              "example": {
                "query": "/api/admin",
                "search_proxy": true,
                "search_sitemap": true,
                "scope_only": false,
                "limit": 200
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK - array of reference matches",
            "content": {
              "application/json": {
                "schema": {
                  "type": "array",
                  "items": { "${'$'}ref": "#/components/schemas/ReferenceDto" }
                }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    },

    "/api/engagement/analyze-target": {
      "post": {
        "tags": ["Engagement"],
        "summary": "Analyze target attack surface",
        "description": "**[Native]** Summarizes the attack surface for a host (or all hosts) from proxy history. Returns unique URLs, endpoints, HTTP methods, status codes, MIME types, parameters, and interesting response headers (Server, X-Powered-By, Set-Cookie, etc.).",
        "operationId": "analyzeTarget",
        "x-api-source": "native",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/AnalyzeTargetRequest" },
              "example": {
                "host": "api.example.com",
                "scope_only": false
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/TargetAnalysisDto" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    },

    "/api/engagement/discover-content": {
      "post": {
        "tags": ["Engagement"],
        "summary": "Discover hidden content via wordlist probing",
        "description": "**[Native]** Probes a wordlist of paths against a target, returning those that respond with 'found' status codes (200–299, 301, 302, 401, 403 by default). Discovered paths are also added to the Burp site map. Supports parallel requests and custom headers.",
        "operationId": "discoverContent",
        "x-api-source": "native",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/DiscoverContentRequest" },
              "example": {
                "host": "api.example.com",
                "port": 443,
                "use_https": true,
                "base_path": "/api",
                "wordlist": ["users", "admin", "backup.zip", ".git/config"],
                "method": "GET",
                "found_status_codes": [200, 201, 301, 302, 401, 403],
                "concurrency": 5
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK - array of discovered paths",
            "content": {
              "application/json": {
                "schema": {
                  "type": "array",
                  "items": { "${'$'}ref": "#/components/schemas/DiscoveredPathDto" }
                }
              }
            }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    },

    "/api/engagement/send-to-decoder": {
      "post": {
        "tags": ["Engagement"],
        "summary": "Send bytes to Burp Decoder tab",
        "description": "**[Montoya API]** Opens the Burp Decoder tab and pre-populates it with the provided base64-encoded bytes for decoding/encoding.",
        "operationId": "sendToDecoder",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/SendToDecoderRequest" },
              "example": {
                "base64": "SGVsbG8gV29ybGQ="
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "OK - bytes sent to Decoder",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    }
${extraPaths()}
  }

,

  "components": {

    "responses": {
      "BadRequest": {
        "description": "Bad request - invalid parameters, missing required fields, or invalid enum value",
        "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" } } }
      },
      "NotFound": {
        "description": "Resource not found",
        "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" } } }
      },
      "ProOnly": {
        "description": "Feature requires Burp Suite Professional",
        "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" } } }
      },
      "Forbidden": {
        "description": "Forbidden - insufficient permissions or feature not available",
        "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" } } }
      },
      "InternalServerError": {
        "description": "Internal error - the operation reached Burp but failed while executing",
        "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" } } }
      }
    },

    "schemas": {

      "StatusResponse": {
        "type": "object",
        "description": "Extension and Burp version metadata",
        "properties": {
          "extension":         { "type": "string",  "description": "Extension name" },
          "version":           { "type": "string",  "description": "Extension version" },
          "edition":           { "type": "string",  "description": "Burp edition: COMMUNITY, PROFESSIONAL, or ENTERPRISE" },
          "port":              { "type": "integer", "description": "Port this REST server is listening on" },
          "docs_url":          { "type": "string",  "description": "URL to the OpenAPI documentation UI" },
          "project_name":      { "type": "string",  "description": "Current Burp project name (null for temporary project)" },
          "project_id":        { "type": "string",  "description": "Current Burp project unique ID" },
          "command_line_args": { "type": "array", "items": { "type": "string" }, "description": "Burp command-line arguments" }
        }
      },

      "ErrorResponse": {
        "type": "object",
        "description": "Standard error body",
        "required": ["message"],
        "properties": {
          "error":   { "type": "string", "description": "Short error code or category" },
          "message": { "type": "string", "description": "Human-readable error description" }
        }
      },

      "MessageResponse": {
        "type": "object",
        "description": "Generic success message",
        "required": ["message"],
        "properties": {
          "message": { "type": "string" }
        }
      },

      "StringResult": {
        "type": "object",
        "description": "Single string result",
        "required": ["result"],
        "properties": {
          "result": { "type": "string" }
        }
      },

      "HttpEntry": {
        "type": "object",
        "description": "A single HTTP request/response pair from proxy history or site map",
        "properties": {
          "id":              { "type": "integer", "description": "Unique entry ID" },
          "host":            { "type": "string",  "description": "Target hostname" },
          "port":            { "type": "integer", "description": "Target port" },
          "use_https":       { "type": "boolean", "description": "Whether HTTPS was used" },
          "url":             { "type": "string",  "description": "Full request URL" },
          "method":          { "type": "string",  "description": "HTTP method" },
          "path":            { "type": "string",  "description": "Request path including query string" },
          "status_code":     { "type": "integer", "description": "HTTP response status code" },
          "mime_type":       { "type": "string",  "description": "Response MIME type" },
          "request_length":  { "type": "integer", "description": "Request byte length" },
          "response_length": { "type": "integer", "description": "Response byte length" },
          "note":            { "type": "string",  "description": "Annotation note (if any)" },
          "highlight":       { "type": "string",  "description": "Highlight colour: RED, ORANGE, YELLOW, GREEN, CYAN, BLUE, PINK, MAGENTA, GRAY" },
          "request":         { "type": "string",  "description": "Raw HTTP request (only present when include_body=true)" },
          "response":        { "type": "string",  "description": "Raw HTTP response (only present when include_body=true)" },
          "timestamp":       { "type": "string",  "description": "ISO-8601 timestamp when this entry was recorded" },
          "in_scope":        { "type": "boolean", "description": "Whether the URL is in Burp's target scope" }
        }
      },

      "WsEntry": {
        "type": "object",
        "description": "A single WebSocket message from proxy history",
        "properties": {
          "id":        { "type": "integer" },
          "url":       { "type": "string",  "description": "WebSocket upgrade URL" },
          "direction": { "type": "string",  "enum": ["CLIENT_TO_SERVER", "SERVER_TO_CLIENT"] },
          "message":   { "type": "string",  "description": "Message content (text frame) or base64 (binary frame)" },
          "is_binary": { "type": "boolean" },
          "timestamp": { "type": "string" }
        }
      },

      "AnnotateRequest": {
        "type": "object",
        "description": "Request body for annotating proxy history items matching a regex",
        "required": ["regex"],
        "properties": {
          "regex":      { "type": "string",  "description": "Java-compatible regex to match against request text" },
          "note":       { "type": "string",  "description": "Annotation note to set on matching items" },
          "highlight":  { "type": "string",  "enum": ["RED", "ORANGE", "YELLOW", "GREEN", "CYAN", "BLUE", "PINK", "MAGENTA", "GRAY"], "description": "Highlight colour to apply" },
          "scope_only": { "type": "boolean", "default": false, "description": "Only annotate items whose URL is in scope" },
          "limit":      { "type": "integer", "default": 1000,  "description": "Maximum number of items to annotate" }
        }
      },

      "InterceptRequest": {
        "type": "object",
        "description": "Proxy intercept toggle state",
        "required": ["enabled"],
        "properties": {
          "enabled": { "type": "boolean", "description": "true to enable intercept, false to disable" }
        }
      },

      "ScopeResult": {
        "type": "object",
        "description": "Result of a scope check",
        "properties": {
          "url":      { "type": "string" },
          "in_scope": { "type": "boolean" }
        }
      },

      "ScopeRequest": {
        "type": "object",
        "description": "URL to add or remove from scope",
        "required": ["url"],
        "properties": {
          "url": { "type": "string", "description": "Full URL or prefix to add/remove from Burp's target scope" }
        }
      },

      "SendHttpRequest": {
        "type": "object",
        "description": "HTTP/1.1 request to send through Burp's HTTP engine. Use either the **raw** format (`request` field) or the **structured** format (`method` + `path` + optional `headers`/`body`).",
        "required": ["host"],
        "properties": {
          "host":         { "type": "string",  "description": "Target hostname or IP address" },
          "port":         { "type": "integer", "default": 443,  "description": "Target port" },
          "use_https":    { "type": "boolean", "default": true,  "description": "Whether to use TLS" },
          "request":      { "type": "string",  "nullable": true, "description": "**Raw format** - full HTTP/1.1 request string with \\r\\n separators. Mutually exclusive with method/path." },
          "method":       { "type": "string",  "nullable": true, "description": "**Structured format** - HTTP method (GET, POST, PUT, …). Requires `path`." },
          "path":         { "type": "string",  "nullable": true, "description": "**Structured format** - Request path, e.g. `/api/users`. Requires `method`." },
          "headers":      { "type": "object",  "nullable": true, "additionalProperties": { "type": "string" }, "description": "**Structured format** - Extra request headers. Host is injected automatically. Content-Type and Content-Length are auto-set when `body` is present." },
          "body":         { "nullable": true,  "description": "**Structured format** - Request body. Pass a JSON object/array directly (no escaping needed), or a plain string.", "oneOf": [{ "type": "object" }, { "type": "array" }, { "type": "string" }] },
          "redirect_mode": { "type": "string", "nullable": true, "enum": ["ALWAYS", "NEVER", "SAME_HOST", "IN_SCOPE"], "description": "Redirect-following policy. null = Burp default." },
          "timeout_ms":   { "type": "integer", "nullable": true, "description": "Request timeout in milliseconds. null = Burp default." }
        }
      },

      "SendHttp2Request": {
        "type": "object",
        "description": "HTTP/2 request to send through Burp's HTTP engine",
        "required": ["host", "pseudo_headers"],
        "properties": {
          "host":           { "type": "string" },
          "port":           { "type": "integer", "default": 443 },
          "use_https":      { "type": "boolean", "default": true },
          "pseudo_headers": { "type": "object", "additionalProperties": { "type": "string" }, "description": "HTTP/2 pseudo-headers: :method, :path, :scheme, :authority" },
          "headers":        { "type": "object", "additionalProperties": { "type": "string" }, "description": "Regular HTTP/2 headers" },
          "body":           { "type": "string", "description": "Request body (optional)" }
        }
      },

      "HttpSendResponse": {
        "type": "object",
        "description": "Result of sending an HTTP request",
        "properties": {
          "request":  { "type": "string",  "description": "Echoed raw request as actually sent by Burp" },
          "response": { "type": "string",  "nullable": true, "description": "Raw HTTP response, or null if connection failed" },
          "error":    { "type": "string",  "nullable": true, "description": "Error message if connection failed (502 case)" }
        }
      },

      "SendBatchRequest": {
        "type": "object",
        "description": "Batch of HTTP requests to send (max 20)",
        "required": ["requests"],
        "properties": {
          "requests": {
            "type": "array",
            "items": { "${'$'}ref": "#/components/schemas/SendHttpRequest" },
            "minItems": 1,
            "maxItems": 20,
            "description": "List of requests to send concurrently"
          }
        }
      },

      "ParseRequestInput": {
        "type": "object",
        "description": "Raw HTTP request to parse",
        "required": ["request"],
        "properties": {
          "request":      { "type": "string",  "description": "Raw HTTP request string" },
          "include_body": { "type": "boolean", "default": true, "description": "Include request body in response" }
        }
      },

      "ParsedRequestDto": {
        "type": "object",
        "description": "Structured representation of a parsed HTTP request",
        "properties": {
          "method":       { "type": "string" },
          "url":          { "type": "string" },
          "path":         { "type": "string" },
          "query":        { "type": "string" },
          "http_version": { "type": "string" },
          "headers":      { "type": "array", "items": { "${'$'}ref": "#/components/schemas/HeaderDto" } },
          "body":         { "type": "string", "nullable": true },
          "body_length":  { "type": "integer" },
          "content_type": { "type": "string", "nullable": true }
        }
      },

      "ParseResponseInput": {
        "type": "object",
        "description": "Raw HTTP response to parse",
        "required": ["response"],
        "properties": {
          "response":     { "type": "string",  "description": "Raw HTTP response string" },
          "include_body": { "type": "boolean", "default": true }
        }
      },

      "ParsedResponseDto": {
        "type": "object",
        "description": "Structured representation of a parsed HTTP response",
        "properties": {
          "status_code":  { "type": "integer" },
          "reason":       { "type": "string" },
          "http_version": { "type": "string" },
          "headers":      { "type": "array", "items": { "${'$'}ref": "#/components/schemas/HeaderDto" } },
          "body":         { "type": "string", "nullable": true },
          "body_length":  { "type": "integer" },
          "content_type": { "type": "string", "nullable": true },
          "mime_type":    { "type": "string", "nullable": true }
        }
      },

      "HeaderDto": {
        "type": "object",
        "description": "A single HTTP header",
        "properties": {
          "name":  { "type": "string" },
          "value": { "type": "string" }
        }
      },

      "ParamDto": {
        "type": "object",
        "description": "An extracted HTTP parameter",
        "properties": {
          "name":  { "type": "string", "description": "Parameter name" },
          "value": { "type": "string", "description": "Parameter value" },
          "type":  { "type": "string", "description": "Parameter type: URL, BODY, COOKIE, XML, XML_ATTR, MULTIPART_ATTR, JSON, UNKNOWN" }
        }
      },

      "DiffInput": {
        "type": "object",
        "description": "Two raw HTTP request strings to diff",
        "required": ["request_a", "request_b"],
        "properties": {
          "request_a": { "type": "string", "description": "First HTTP request" },
          "request_b": { "type": "string", "description": "Second HTTP request" }
        }
      },

      "ExtractParamsInput": {
        "type": "object",
        "description": "Raw HTTP request to extract parameters from",
        "required": ["request"],
        "properties": {
          "request": { "type": "string", "description": "Raw HTTP request string" }
        }
      },

      "FindReflectedInput": {
        "type": "object",
        "description": "Request and response pair for reflection analysis",
        "required": ["request", "response"],
        "properties": {
          "request":  { "type": "string", "description": "Raw HTTP request string" },
          "response": { "type": "string", "description": "Raw HTTP response string" }
        }
      },

      "InsertionPointsInput": {
        "type": "object",
        "description": "HTTP request for insertion point extraction",
        "required": ["request"],
        "properties": {
          "request": { "type": "string", "description": "Raw HTTP request string" },
          "mode":    { "type": "string", "default": "ALL_PARAMETERS", "description": "Insertion point mode: ALL_PARAMETERS returns all identified injection points" }
        }
      },

      "InsertionPointDto": {
        "type": "object",
        "description": "A single insertion point identified by Burp",
        "properties": {
          "name":  { "type": "string",  "description": "Parameter name or identifier" },
          "value": { "type": "string",  "description": "Current parameter value" },
          "type":  { "type": "string",  "description": "Insertion point type: URL_PARAMETER, BODY_PARAMETER, COOKIE, etc." },
          "start": { "type": "integer", "description": "Byte offset of value start in the request" },
          "end":   { "type": "integer", "description": "Byte offset of value end in the request" }
        }
      },

      "SendToToolRequest": {
        "type": "object",
        "description": "Request to send to a Burp tool tab (Repeater or Intruder)",
        "required": ["host", "request"],
        "properties": {
          "host":      { "type": "string" },
          "port":      { "type": "integer", "default": 443 },
          "use_https": { "type": "boolean", "default": true },
          "request":   { "type": "string",  "description": "Raw HTTP request string" },
          "tab_name":  { "type": "string",  "nullable": true, "description": "Optional name for the new tab" }
        }
      },

      "ComparerRequest": {
        "type": "object",
        "description": "Items to send to Burp's Comparer tab",
        "required": ["items"],
        "properties": {
          "items": {
            "type": "array",
            "items": { "type": "string" },
            "minItems": 1,
            "description": "List of raw HTTP requests or responses to compare"
          }
        }
      },

      "CookieDto": {
        "type": "object",
        "description": "A cookie from Burp's cookie jar",
        "properties": {
          "name":       { "type": "string" },
          "value":      { "type": "string",  "nullable": true, "description": "Cookie value (omitted when include_values=false)" },
          "domain":     { "type": "string" },
          "path":       { "type": "string",  "nullable": true },
          "expires_at": { "type": "string",  "nullable": true, "description": "Expiry as ISO-8601 ZonedDateTime string" }
        }
      },

      "SetCookieRequest": {
        "type": "object",
        "description": "Cookie to add or update in Burp's cookie jar",
        "required": ["name", "value", "domain"],
        "properties": {
          "name":       { "type": "string" },
          "value":      { "type": "string" },
          "domain":     { "type": "string" },
          "path":       { "type": "string",  "nullable": true, "description": "Cookie path (optional)" },
          "expires_at": { "type": "string",  "nullable": true, "description": "ISO-8601 ZonedDateTime expiry, e.g. 2025-12-31T23:59:59Z" }
        }
      },

      "StartAuditRequest": {
        "type": "object",
        "description": "Start a Burp audit with a built-in scan configuration",
        "required": ["requests"],
        "properties": {
          "configuration": {
            "type": "string",
            "default": "CRAWL_AND_AUDIT_EVERYTHING_FAST",
            "enum": [
              "CRAWL_AND_AUDIT_EVERYTHING_FAST",
              "CRAWL_AND_AUDIT_EVERYTHING_THOROUGH",
              "CRAWL_EVERYTHING_FAST",
              "CRAWL_EVERYTHING_THOROUGH",
              "AUDIT_COVERAGE_SPEED",
              "AUDIT_COVERAGE_THOROUGH",
              "AUDIT_ACCURACY_SPEED",
              "AUDIT_ACCURACY_THOROUGH"
            ],
            "description": "Built-in Burp scan configuration name"
          },
          "host":      { "type": "string",  "nullable": true },
          "port":      { "type": "integer", "default": 443 },
          "use_https": { "type": "boolean", "default": true },
          "requests":  { "type": "array",   "items": { "type": "string" }, "description": "Seed HTTP requests for the audit" }
        }
      },

      "StartAuditModeRequest": {
        "type": "object",
        "description": "Start a Burp audit using ACTIVE or PASSIVE mode",
        "required": ["requests"],
        "properties": {
          "mode":      { "type": "string",  "default": "ACTIVE", "enum": ["ACTIVE", "PASSIVE"], "description": "Audit mode" },
          "host":      { "type": "string",  "nullable": true },
          "port":      { "type": "integer", "default": 443 },
          "use_https": { "type": "boolean", "default": true },
          "requests":  { "type": "array",   "items": { "type": "string" } }
        }
      },

      "StartCrawlRequest": {
        "type": "object",
        "description": "Start a Burp crawl with seed URLs",
        "required": ["seed_urls"],
        "properties": {
          "seed_urls": {
            "type": "array",
            "items": { "type": "string" },
            "minItems": 1,
            "description": "List of URLs to use as crawl seeds"
          }
        }
      },

      "ScanTaskDto": {
        "type": "object",
        "description": "Burp scanner task status",
        "properties": {
          "id":          { "type": "string",  "description": "Task ID" },
          "type":        { "type": "string",  "description": "Task type: AUDIT or CRAWL" },
          "status":      { "type": "string",  "description": "Task status: RUNNING, FINISHED, CANCELLED, FAILED" },
          "issue_count": { "type": "integer", "description": "Number of issues found so far" },
          "created_at":  { "type": "string",  "description": "ISO-8601 creation timestamp" }
        }
      },

      "ScanReportRequest": {
        "type": "object",
        "description": "Generate a scan report to a local file",
        "required": ["path"],
        "properties": {
          "task_id":    { "type": "string",  "nullable": true, "description": "Specific task ID to report on. Mutually exclusive with all_issues" },
          "all_issues": { "type": "boolean", "default": false, "description": "Report on all scanner issues across all tasks" },
          "format":     { "type": "string",  "enum": ["HTML", "XML"], "default": "HTML", "description": "Report output format" },
          "path":       { "type": "string",  "description": "Absolute file path to write the report to, e.g. /tmp/report.html" }
        }
      },

      "ScanIssueDto": {
        "type": "object",
        "description": "A Burp scanner issue",
        "properties": {
          "id":          { "type": "integer" },
          "name":        { "type": "string" },
          "severity":    { "type": "string", "enum": ["HIGH", "MEDIUM", "LOW", "INFORMATION"] },
          "confidence":  { "type": "string", "enum": ["CERTAIN", "FIRM", "TENTATIVE"] },
          "url":         { "type": "string" },
          "host":        { "type": "string" },
          "port":        { "type": "integer" },
          "detail":      { "type": "string", "nullable": true },
          "remediation": { "type": "string", "nullable": true },
          "background":  { "type": "string", "nullable": true }
        }
      },

      "ImportScriptRequest": {
        "type": "object",
        "description": "BCheck or Bambda script to import",
        "required": ["source"],
        "properties": {
          "source":  { "type": "string",  "description": "Full script source code" },
          "enabled": { "type": "boolean", "default": true, "description": "Whether to enable the script after import" }
        }
      },

      "SetConfigRequest": {
        "type": "object",
        "description": "Burp config JSON to merge",
        "required": ["json"],
        "properties": {
          "json": { "type": "object", "description": "JSON object matching Burp's config schema to merge into the current config" }
        }
      },

      "TaskEngineStateDto": {
        "type": "object",
        "description": "Task engine running state",
        "properties": {
          "running": { "type": "boolean", "description": "true if the task engine is currently processing tasks" }
        }
      },

      "SetTaskEngineStateRequest": {
        "type": "object",
        "description": "Desired task engine state",
        "required": ["running"],
        "properties": {
          "running": { "type": "boolean", "description": "true to start the engine, false to pause it" }
        }
      },

      "CollaboratorGenerateRequest": {
        "type": "object",
        "description": "Options for Collaborator payload generation",
        "properties": {
          "options":     { "type": "array", "items": { "type": "string", "enum": ["DNS", "HTTP", "SMTP", "SMTPS"] }, "description": "Interaction types to enable on the payload" },
          "custom_data": { "type": "string", "nullable": true, "description": "Optional custom data embedded in the payload" }
        }
      },

      "CollaboratorGeneratedDto": {
        "type": "object",
        "description": "Generated Collaborator payload",
        "properties": {
          "payload":        { "type": "string", "description": "Full Collaborator payload hostname" },
          "interaction_id": { "type": "string", "description": "Interaction identifier to correlate hits" },
          "secret_key":     { "type": "string", "description": "Secret key used to poll for interactions" }
        }
      },

      "CollaboratorInteractionDto": {
        "type": "object",
        "description": "A recorded Collaborator interaction",
        "properties": {
          "id":             { "type": "string" },
          "type":           { "type": "string", "enum": ["DNS", "HTTP", "SMTP", "SMTPS"] },
          "timestamp":      { "type": "string", "description": "ISO-8601 timestamp" },
          "client_ip":      { "type": "string" },
          "interaction_id": { "type": "string" },
          "custom_data":    { "type": "string", "nullable": true },
          "raw_query":      { "type": "string", "nullable": true, "description": "Raw DNS query or HTTP request" },
          "raw_response":   { "type": "string", "nullable": true }
        }
      },

      "StringInput": {
        "type": "object",
        "description": "Single string input for encoding/hashing utilities",
        "required": ["value"],
        "properties": {
          "value": { "type": "string" }
        }
      },

      "HashInput": {
        "type": "object",
        "description": "Input for legacy hash computation",
        "required": ["algorithm", "value"],
        "properties": {
          "algorithm": { "type": "string", "enum": ["MD5", "SHA1", "SHA256", "SHA512"], "default": "SHA256", "description": "Hash algorithm" },
          "value":     { "type": "string", "description": "String to hash" }
        }
      },

      "DigestInput": {
        "type": "object",
        "description": "Input for Montoya CryptoUtils digest",
        "required": ["algorithm", "value"],
        "properties": {
          "algorithm": {
            "type": "string",
            "enum": [
              "SHA_256", "SHA_512", "MD5", "SHA3_256", "SHA3_512",
              "BLAKE2B_256", "BLAKE3_256", "KECCAK_256",
              "SHA_1", "SHA_384", "RIPEMD_160",
              "WHIRLPOOL", "TIGER", "SM3"
            ],
            "description": "Digest algorithm supported by Montoya CryptoUtils"
          },
          "value": { "type": "string", "description": "String to digest" }
        }
      },

      "UrlEncodeInput": {
        "type": "object",
        "description": "Input for URL encoding with mode",
        "required": ["value"],
        "properties": {
          "value":    { "type": "string" },
          "encoding": { "type": "string", "enum": ["JAVA_DEFAULT", "KEY_CHARACTERS", "ALL_CHARACTERS", "ALL_CHARACTERS_UNICODE"], "default": "ALL_CHARACTERS", "description": "URL encoding mode" }
        }
      },

      "Base64EncodeInput": {
        "type": "object",
        "description": "Input for Base64 encoding with options",
        "required": ["value"],
        "properties": {
          "value":      { "type": "string" },
          "url_safe":   { "type": "boolean", "default": false, "description": "Use URL-safe alphabet (- and _ instead of + and /)" },
          "no_padding": { "type": "boolean", "default": false, "description": "Omit = padding characters" }
        }
      },

      "Base64DecodeInput": {
        "type": "object",
        "description": "Input for Base64 decoding with options",
        "required": ["value"],
        "properties": {
          "value":    { "type": "string" },
          "url_safe": { "type": "boolean", "default": false, "description": "Use URL-safe alphabet (- and _ instead of + and /)" }
        }
      },

      "HtmlEncodeInput": {
        "type": "object",
        "description": "Input for HTML encoding with mode",
        "required": ["value"],
        "properties": {
          "value":    { "type": "string" },
          "encoding": { "type": "string", "enum": ["STANDARD", "ALL_CHARACTERS", "ALL_CHARACTERS_DECIMAL", "ALL_CHARACTERS_HEX"], "default": "STANDARD", "description": "HTML encoding mode" }
        }
      },

      "CompressInput": {
        "type": "object",
        "description": "Input for compression",
        "required": ["value"],
        "properties": {
          "value":    { "type": "string",  "description": "String to compress" },
          "encoding": { "type": "string",  "enum": ["GZIP", "DEFLATE", "BROTLI"], "default": "GZIP", "description": "Compression algorithm to use" }
        }
      },

      "DecompressInput": {
        "type": "object",
        "description": "Input for decompression",
        "required": ["base64"],
        "properties": {
          "base64":   { "type": "string",  "description": "Base64-encoded compressed data" },
          "encoding": { "type": "string",  "enum": ["GZIP", "DEFLATE", "BROTLI", "IDENTITY", "RAW"], "default": "GZIP", "description": "Compression algorithm to use for decompression" }
        }
      },

      "ResponseKeywordsInput": {
        "type": "object",
        "description": "Input for keyword variance analysis across multiple responses",
        "required": ["responses", "keywords"],
        "properties": {
          "responses": { "type": "array", "items": { "type": "string" }, "minItems": 2, "maxItems": 50, "description": "List of raw HTTP response strings to analyze (2-50)" },
          "keywords":  { "type": "array", "items": { "type": "string" }, "minItems": 1, "description": "Keywords to check for variance across the responses" }
        }
      },

      "ResponseKeywordsResult": {
        "type": "object",
        "description": "Keyword variance analysis result",
        "properties": {
          "variant":   { "type": "array", "items": { "type": "string" }, "description": "Keywords that appear in some but not all responses" },
          "invariant": { "type": "array", "items": { "type": "string" }, "description": "Keywords that appear in all or no responses" }
        }
      },

      "ResponseVariationsInput": {
        "type": "object",
        "description": "Input for attribute variance analysis across multiple responses",
        "required": ["responses"],
        "properties": {
          "responses": { "type": "array", "items": { "type": "string" }, "minItems": 2, "maxItems": 50, "description": "List of raw HTTP response strings to analyze (2-50)" }
        }
      },

      "ResponseVariationsResult": {
        "type": "object",
        "description": "Attribute variance analysis result",
        "properties": {
          "variant":   { "type": "array", "items": { "type": "string" }, "description": "Response attributes that vary across responses (e.g. status_code, body_length)" },
          "invariant": { "type": "array", "items": { "type": "string" }, "description": "Response attributes that are the same across all responses" }
        }
      },

      "CreateIssueRequest": {
        "type": "object",
        "description": "Custom scan issue to create in Burp's Target tab",
        "required": ["name", "detail", "base_url"],
        "properties": {
          "name":                   { "type": "string",  "description": "Issue name" },
          "detail":                 { "type": "string",  "description": "Detailed issue description (supports HTML)" },
          "remediation":            { "type": "string",  "nullable": true, "description": "Remediation advice (supports HTML)" },
          "base_url":               { "type": "string",  "description": "URL where the issue was found" },
          "severity":               { "type": "string",  "enum": ["HIGH", "MEDIUM", "LOW", "INFORMATION"], "default": "INFORMATION" },
          "confidence":             { "type": "string",  "enum": ["CERTAIN", "FIRM", "TENTATIVE"], "default": "CERTAIN" },
          "background":             { "type": "string",  "nullable": true, "description": "Issue background information" },
          "remediation_background": { "type": "string",  "nullable": true, "description": "Remediation background" },
          "typical_severity":       { "type": "string",  "nullable": true, "description": "Typical severity for this issue class" },
          "target_host":            { "type": "string",  "nullable": true, "description": "Target hostname (overrides base_url host)" },
          "target_port":            { "type": "integer", "default": 443 },
          "target_use_https":       { "type": "boolean", "default": true },
          "http_request":           { "type": "string",  "nullable": true, "description": "Raw HTTP request to attach to the issue" },
          "http_response":          { "type": "string",  "nullable": true, "description": "Raw HTTP response to attach to the issue" }
        }
      },

      "PersistenceValueRequest": {
        "type": "object",
        "description": "Preference value to persist",
        "required": ["type", "value"],
        "properties": {
          "type":  { "type": "string", "enum": ["string", "boolean", "byte", "short", "int", "long"], "description": "Primitive type of the value" },
          "value": { "type": "string", "description": "String representation of the value to persist (coerced to the specified type)" }
        }
      },

      "OrganizerItemDto": {
        "type": "object",
        "description": "An item in Burp's Organizer tab",
        "properties": {
          "id":              { "type": "integer" },
          "status":          { "type": "string",  "enum": ["NEW", "IN_PROGRESS", "POSTPONED", "DONE", "IGNORED", "UNKNOWN"] },
          "url":             { "type": "string",  "nullable": true },
          "method":          { "type": "string",  "nullable": true },
          "status_code":     { "type": "integer", "nullable": true },
          "request_length":  { "type": "integer" },
          "response_length": { "type": "integer" }
        }
      },

      "AiMessage": {
        "type": "object",
        "description": "A single message in an AI conversation",
        "required": ["role", "content"],
        "properties": {
          "role":    { "type": "string", "enum": ["user", "assistant"], "description": "Message author role" },
          "content": { "type": "string", "description": "Message content" }
        }
      },

      "AiChatRequest": {
        "type": "object",
        "description": "AI chat conversation request",
        "required": ["messages"],
        "properties": {
          "messages":      { "type": "array", "items": { "${'$'}ref": "#/components/schemas/AiMessage" }, "minItems": 1, "description": "Conversation history (alternating user/assistant messages)" },
          "system_prompt": { "type": "string", "nullable": true, "description": "Optional system prompt to configure AI behaviour" }
        }
      },

      "AiChatResponse": {
        "type": "object",
        "description": "AI chat response",
        "properties": {
          "response": { "type": "string", "description": "AI assistant response text" }
        }
      },

      "AiStatusResponse": {
        "type": "object",
        "description": "AI feature availability status",
        "properties": {
          "enabled": { "type": "boolean", "description": "true if AI features are enabled in Burp settings" }
        }
      },

      "CsrfPocRequest": {
        "type": "object",
        "description": "Request to generate a CSRF proof-of-concept",
        "required": ["request", "host"],
        "properties": {
          "request":    { "type": "string", "description": "Raw HTTP request to generate PoC for" },
          "host":       { "type": "string", "description": "Target host" },
          "port":       { "type": "integer", "default": 443, "description": "Target port" },
          "use_https":  { "type": "boolean", "default": true, "description": "Whether to use HTTPS" },
          "format":     { "type": "string", "enum": ["HTML_FORM", "FETCH_JS", "AUTO_SUBMIT"], "default": "AUTO_SUBMIT", "description": "Output format for the PoC" }
        }
      },

      "CsrfPocResponse": {
        "type": "object",
        "description": "Generated CSRF proof-of-concept",
        "properties": {
          "format":     { "type": "string", "description": "Output format used (HTML_FORM, FETCH_JS, or AUTO_SUBMIT)" },
          "method":     { "type": "string", "description": "HTTP method of the original request" },
          "action_url": { "type": "string", "description": "Target URL for the PoC request" },
          "html":       { "type": "string", "description": "Generated PoC code (HTML or JavaScript)" }
        }
      },

      "FindReferencesRequest": {
        "type": "object",
        "description": "Request to find URL references in proxy/sitemap",
        "required": ["query"],
        "properties": {
          "query":           { "type": "string", "description": "URL, host, path fragment, or string to search for" },
          "search_proxy":    { "type": "boolean", "default": true, "description": "Search proxy history" },
          "search_sitemap":  { "type": "boolean", "default": true, "description": "Search site map" },
          "scope_only":      { "type": "boolean", "default": false, "description": "Restrict search to URLs in scope" },
          "limit":           { "type": "integer", "default": 200, "description": "Maximum number of results to return" }
        }
      },

      "ReferenceDto": {
        "type": "object",
        "description": "A found reference to a URL or string",
        "properties": {
          "source_url": { "type": "string", "description": "URL where the reference was found" },
          "match":      { "type": "string", "description": "Context snippet showing the match (with surrounding text)" },
          "source":     { "type": "string", "enum": ["PROXY", "SITEMAP"], "description": "Where the reference was found" }
        }
      },

      "AnalyzeTargetRequest": {
        "type": "object",
        "description": "Request to analyze target attack surface",
        "properties": {
          "host":       { "type": "string", "nullable": true, "description": "Specific host to analyze; if omitted, analyzes entire proxy history" },
          "scope_only":  { "type": "boolean", "default": false, "description": "Only include URLs that are in scope" }
        }
      },

      "TargetAnalysisDto": {
        "type": "object",
        "description": "Summary of target attack surface",
        "properties": {
          "host":                { "type": "string", "nullable": true, "description": "Host analyzed" },
          "total_requests":      { "type": "integer", "description": "Total number of requests analyzed" },
          "unique_urls":         { "type": "integer", "description": "Count of unique URLs" },
          "unique_endpoints":    { "type": "integer", "description": "Count of unique endpoints (method + path)" },
          "methods":             { "type": "object", "additionalProperties": { "type": "integer" }, "description": "HTTP method frequency map" },
          "status_codes":        { "type": "object", "additionalProperties": { "type": "integer" }, "description": "HTTP status code frequency map" },
          "mime_types":          { "type": "object", "additionalProperties": { "type": "integer" }, "description": "MIME type frequency map" },
          "parameters":          { "type": "array", "items": { "type": "string" }, "description": "Sorted list of unique parameters found" },
          "interesting_headers": { "type": "object", "additionalProperties": { "type": "array", "items": { "type": "string" } }, "description": "Map of interesting headers to their values" }
        }
      },

      "DiscoverContentRequest": {
        "type": "object",
        "description": "Request to discover hidden content via wordlist",
        "required": ["host", "wordlist"],
        "properties": {
          "host":                { "type": "string", "description": "Target hostname" },
          "port":                { "type": "integer", "default": 443, "description": "Target port" },
          "use_https":           { "type": "boolean", "default": true, "description": "Whether to use HTTPS" },
          "base_path":           { "type": "string", "default": "/", "description": "Base path to probe under (e.g., '/api')" },
          "wordlist":            { "type": "array", "items": { "type": "string" }, "description": "List of paths/filenames to probe (max 5000 items)" },
          "method":              { "type": "string", "default": "GET", "enum": ["GET", "HEAD", "POST", "OPTIONS"], "description": "HTTP method to use for probing" },
          "headers":             { "type": "object", "additionalProperties": { "type": "string" }, "default": {}, "description": "Additional headers to send with requests" },
          "found_status_codes":  { "type": "array", "items": { "type": "integer" }, "default": [200, 201, 204, 301, 302, 307, 401, 403, 405], "description": "Status codes considered as 'found'" },
          "concurrency":         { "type": "integer", "default": 5, "description": "Number of parallel requests (1–20)" }
        }
      },

      "DiscoveredPathDto": {
        "type": "object",
        "description": "A discovered path/resource",
        "properties": {
          "url":             { "type": "string", "description": "Full URL of the discovered path" },
          "status":          { "type": "integer", "description": "HTTP status code received" },
          "response_length": { "type": "integer", "description": "Length of the response body in bytes" },
          "content_type":    { "type": "string", "nullable": true, "description": "Content-Type header value if present" }
        }
      },

      "SendToDecoderRequest": {
        "type": "object",
        "description": "Request to send data to Burp Decoder",
        "required": ["base64"],
        "properties": {
          "base64": { "type": "string", "description": "Base64-encoded bytes to send to the Decoder tab" }
        }
      },



      "ScopeCheckResult": {
        "type": "object",
        "description": "Result of a scope check",
        "properties": {
          "url":      { "type": "string", "description": "The URL that was checked" },
          "in_scope": { "type": "boolean", "description": "Whether the URL is in the current Burp target scope" }
        }
      },

      "ScopeUrlRequest": {
        "type": "object",
        "description": "URL to include or exclude from Burp target scope",
        "required": ["url"],
        "properties": {
          "url": { "type": "string", "description": "Full URL or prefix pattern to add to scope" }
        }
      },

      "SendToRepeaterRequest": {
        "type": "object",
        "description": "Raw HTTP request to queue in Burp Repeater",
        "required": ["raw_request"],
        "properties": {
          "raw_request": { "type": "string", "description": "Full HTTP request string including Host header and body" },
          "tab_name":    { "type": "string", "nullable": true, "description": "Optional name for the Repeater tab" }
        }
      },

      "SendToIntruderRequest": {
        "type": "object",
        "description": "Raw HTTP request to queue in Burp Intruder",
        "required": ["raw_request"],
        "properties": {
          "raw_request": { "type": "string", "description": "Full HTTP request string; mark insertion points with § symbols" },
          "tab_name":    { "type": "string", "nullable": true, "description": "Optional name for the Intruder tab" }
        }
      },

      "MatchReplaceRule": {
        "type": "object",
        "description": "A Burp proxy match-and-replace rule",
        "required": ["rule_type"],
        "properties": {
          "id":             { "type": "integer", "nullable": true, "description": "Zero-based rule index (present in GET responses, ignored in POST/PUT bodies)" },
          "rule_type":      { "type": "string", "enum": ["request_first_line", "request_header", "request_body", "request_param_name", "request_param_value", "response_header", "response_body"], "description": "Which part of the HTTP message this rule applies to" },
          "string_match":   { "type": "string", "nullable": true, "description": "Regex pattern to match (or literal string when is_simple_match=true)" },
          "string_replace": { "type": "string", "nullable": true, "description": "Replacement string; supports $1 back-references when is_simple_match=false" },
          "is_simple_match":{ "type": "boolean", "default": false, "description": "When true, string_match is treated as a literal string rather than a regex" },
          "enabled":        { "type": "boolean", "default": true, "description": "Whether the rule is active" },
          "comment":        { "type": "string", "nullable": true, "description": "Optional comment describing the rule" }
        }
      },

      "MatchReplaceListResponse": {
        "type": "object",
        "description": "List of match-and-replace rules",
        "properties": {
          "count": { "type": "integer", "description": "Total number of rules" },
          "rules": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/MatchReplaceRule" }, "description": "Ordered list of rules (zero-based indexed)" }
        }
      },

      "JwtEncodeRequest": {
        "type": "object",
        "description": "Input for JWT encoding and signing",
        "required": ["payload_json"],
        "properties": {
          "header_json":  { "type": "string", "nullable": true, "description": "Optional JSON object string for the header (defaults to {\"alg\":\"<algorithm>\",\"typ\":\"JWT\"})" },
          "payload_json": { "type": "string", "description": "JSON object string containing the JWT claims" },
          "secret":       { "type": "string", "nullable": true, "description": "HMAC secret key (UTF-8); leave empty or omit for unsigned token" },
          "algorithm":    { "type": "string", "enum": ["HS256", "HS384", "HS512", "none"], "default": "HS256", "description": "Signing algorithm" }
        }
      },

      "JwtEncodeResponse": {
        "type": "object",
        "description": "Encoded JWT token",
        "properties": {
          "token":     { "type": "string", "description": "The complete signed JWT string" },
          "header":    { "type": "string", "description": "Base64url-encoded header" },
          "payload":   { "type": "string", "description": "Base64url-encoded payload" },
          "algorithm": { "type": "string", "description": "Algorithm used for signing" }
        }
      },

      "HeaderAnalysisRequest": {
        "type": "object",
        "description": "HTTP response headers to analyze for security posture",
        "properties": {
          "headers":      { "type": "object", "additionalProperties": { "type": "string" }, "nullable": true, "description": "Map of header name to value (case-insensitive)" },
          "raw_response": { "type": "string", "nullable": true, "description": "Raw HTTP response string; headers parsed automatically. Supply either this or headers." }
        }
      },

      "SecurityHeaderCheck": {
        "type": "object",
        "description": "Result of checking a single security header",
        "properties": {
          "header":         { "type": "string", "description": "Header name" },
          "status":         { "type": "string", "enum": ["present", "missing", "weak"], "description": "Whether the header is present, missing, or present but misconfigured" },
          "value":          { "type": "string", "nullable": true, "description": "Actual header value if present" },
          "recommendation": { "type": "string", "description": "Guidance on the correct configuration for this header" }
        }
      },

      "HeaderAnalysisResponse": {
        "type": "object",
        "description": "Security header analysis result",
        "properties": {
          "checks":     { "type": "array", "items": { "${'$'}ref": "#/components/schemas/SecurityHeaderCheck" }, "description": "Per-header check results" },
          "score":      { "type": "integer", "description": "Achieved score out of max_score" },
          "max_score":  { "type": "integer", "description": "Maximum possible score" },
          "grade":      { "type": "string", "description": "Overall grade: A+, A, B, C, D, or F" },
          "summary":    { "type": "string", "description": "Human-readable summary of the analysis" }
        }
      },

      "PayloadsResponse": {
        "type": "object",
        "description": "Security testing payload list",
        "properties": {
          "category":    { "type": "string", "description": "Vulnerability category" },
          "description": { "type": "string", "description": "Short description of the vulnerability category" },
          "payloads":    { "type": "array", "items": { "type": "string" }, "description": "List of test payloads" },
          "count":       { "type": "integer", "description": "Number of payloads returned" }
        }
      },

      "SiteMapAddRequest": {
        "type": "object",
        "required": ["host", "request"],
        "properties": {
          "host":      { "type": "string" },
          "port":      { "type": "integer", "default": 443 },
          "use_https": { "type": "boolean", "default": true },
          "request":   { "type": "string", "description": "Raw HTTP/1.1 request string" },
          "response":  { "type": "string", "nullable": true, "description": "Raw HTTP response string (optional)" }
        }
      },

      "SiteMapIssueDto": {
        "type": "object",
        "properties": {
          "name":        { "type": "string" },
          "detail":      { "type": "string" },
          "remediation": { "type": "string" },
          "base_url":    { "type": "string" },
          "severity":    { "type": "string", "enum": ["INFORMATION", "LOW", "MEDIUM", "HIGH", "CRITICAL"] },
          "confidence":  { "type": "string", "enum": ["TENTATIVE", "FIRM", "CERTAIN"] },
          "host":        { "type": "string" },
          "port":        { "type": "integer" }
        }
      },

      "WsConnectRequest": {
        "type": "object",
        "required": ["host"],
        "properties": {
          "host":      { "type": "string" },
          "port":      { "type": "integer", "default": 443 },
          "use_https": { "type": "boolean", "default": true },
          "path":      { "type": "string", "default": "/", "description": "WebSocket upgrade path" }
        }
      },

      "WsConnectionInfoDto": {
        "type": "object",
        "properties": {
          "id":             { "type": "string" },
          "host":           { "type": "string" },
          "port":           { "type": "integer" },
          "path":           { "type": "string" },
          "secure":         { "type": "boolean" },
          "upgrade_status": { "type": "integer", "description": "HTTP upgrade response status code" }
        }
      },

      "WsClientMessageDto": {
        "type": "object",
        "properties": {
          "direction":      { "type": "string", "enum": ["INCOMING", "OUTGOING"] },
          "type":           { "type": "string", "enum": ["TEXT", "BINARY"] },
          "payload":        { "type": "string", "nullable": true, "description": "UTF-8 text payload (TEXT frames)" },
          "payload_base64": { "type": "string", "nullable": true, "description": "Base64 payload (BINARY frames)" }
        }
      },

      "WsSendRequest": {
        "type": "object",
        "required": ["message"],
        "properties": {
          "message": { "type": "string" }
        }
      },

      "WsSendBinaryRequest": {
        "type": "object",
        "required": ["base64"],
        "properties": {
          "base64": { "type": "string", "description": "Base64-encoded binary payload" }
        }
      },

      "ParamMutationInput": {
        "type": "object",
        "required": ["type", "name"],
        "properties": {
          "type":  { "type": "string", "enum": ["URL", "BODY", "COOKIE", "JSON", "XML", "MULTIPART_ATTRIBUTE", "PATH"] },
          "name":  { "type": "string" },
          "value": { "type": "string", "nullable": true }
        }
      },

      "MutateRequestInput": {
        "type": "object",
        "required": ["request"],
        "properties": {
          "request":         { "type": "string", "description": "Raw HTTP/1.1 request string to mutate" },
          "service_host":    { "type": "string",  "nullable": true },
          "service_port":    { "type": "integer", "nullable": true },
          "service_use_https": { "type": "boolean", "nullable": true },
          "method":          { "type": "string",  "nullable": true },
          "path":            { "type": "string",  "nullable": true },
          "body":            { "type": "string",  "nullable": true },
          "toggle_method":   { "type": "boolean", "nullable": true, "description": "Toggle between GET and POST" },
          "add_headers":     { "type": "object",  "nullable": true, "additionalProperties": { "type": "string" } },
          "remove_headers":  { "type": "array",   "nullable": true, "items": { "type": "string" } },
          "update_headers":  { "type": "object",  "nullable": true, "additionalProperties": { "type": "string" } },
          "add_params":      { "type": "array",   "nullable": true, "items": { "${'$'}ref": "#/components/schemas/ParamMutationInput" } },
          "remove_params":   { "type": "array",   "nullable": true, "items": { "${'$'}ref": "#/components/schemas/ParamMutationInput" } },
          "update_params":   { "type": "array",   "nullable": true, "items": { "${'$'}ref": "#/components/schemas/ParamMutationInput" } }
        }
      },

      "MutateResponseInput": {
        "type": "object",
        "required": ["response"],
        "properties": {
          "response":       { "type": "string", "description": "Raw HTTP response string to mutate" },
          "status_code":    { "type": "integer", "nullable": true },
          "body":           { "type": "string",  "nullable": true },
          "add_headers":    { "type": "object",  "nullable": true, "additionalProperties": { "type": "string" } },
          "remove_headers": { "type": "array",   "nullable": true, "items": { "type": "string" } },
          "update_headers": { "type": "object",  "nullable": true, "additionalProperties": { "type": "string" } }
        }
      },

      "AuditFromHistoryRequest": {
        "type": "object",
        "required": ["index"],
        "properties": {
          "index":         { "type": "integer", "description": "Zero-based index into proxy history" },
          "configuration": { "type": "string", "default": "ACTIVE", "enum": ["ACTIVE", "PASSIVE", "LEGACY_ACTIVE", "LEGACY_PASSIVE"], "description": "Audit configuration preset" }
        }
      },

      "ScopeRuleEntry": {
        "type": "object",
        "properties": {
          "enabled":  { "type": "string" },
          "protocol": { "type": "string", "nullable": true },
          "host":     { "type": "string", "nullable": true },
          "file":     { "type": "string", "nullable": true },
          "port":     { "type": "string", "nullable": true }
        }
      },

      "ScopeRulesDto": {
        "type": "object",
        "properties": {
          "include": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/ScopeRuleEntry" } },
          "exclude": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/ScopeRuleEntry" } }
        }
      },

      "AuthConfig": {
        "type": "object",
        "required": ["host", "path"],
        "properties": {
          "host":       { "type": "string" },
          "port":       { "type": "integer", "default": 443 },
          "use_https":  { "type": "boolean", "default": true },
          "method":     { "type": "string",  "default": "POST" },
          "path":       { "type": "string" },
          "headers":    { "type": "object",  "nullable": true, "additionalProperties": { "type": "string" } },
          "body":       { "nullable": true },
          "token_path": { "type": "string",  "default": "$.access", "description": "Dot-path into JSON auth response, e.g. $.access or $.data.token" }
        }
      },

      "SendWithAuthRequest": {
        "type": "object",
        "required": ["auth", "request"],
        "properties": {
          "auth":      { "${'$'}ref": "#/components/schemas/AuthConfig" },
          "request":   { "${'$'}ref": "#/components/schemas/SendHttpRequest" },
          "inject_as": { "type": "string", "default": "Authorization: Bearer {token}", "description": "Header line to inject. Use {token} as placeholder." },
          "retry_on":  { "type": "array", "items": { "type": "integer" }, "default": [401, 403] }
        }
      },

      "AddHeaderRuleRequest": {
        "type": "object",
        "required": ["header_name", "header_value"],
        "properties": {
          "header_name":  { "type": "string", "description": "Header name, e.g. Authorization" },
          "header_value": { "type": "string", "description": "Header value, e.g. Bearer eyJ..." },
          "name":         { "type": "string", "nullable": true, "description": "Rule name (auto-generated if omitted)" },
          "scope_url":    { "type": "string", "nullable": true, "description": "Limit rule to this URL prefix" }
        }
      },

      "InterceptRuleRequest": {
        "type": "object",
        "required": ["match_type", "match_relationship", "match_condition"],
        "properties": {
          "enabled":            { "type": "boolean", "default": true },
          "match_type":         { "type": "string", "enum": ["URL", "METHOD", "LISTENER_PORT", "HTTP_VERSION", "REQUEST_HAS_PARAMS", "MIME_TYPE", "STATUS_CODE", "TAG", "HEADER", "ATTRIBUTE"] },
          "match_relationship": { "type": "string", "enum": ["MATCHES", "NOT_MATCHES", "CONTAINS", "NOT_CONTAINS"] },
          "match_condition":    { "type": "string", "description": "Value to match against" }
        }
      }
${extraSchemas()}
    }
  }
}
"""

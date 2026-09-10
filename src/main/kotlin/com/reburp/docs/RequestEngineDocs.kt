package com.reburp

/**
 * OpenAPI fragments for the request execution engine (`/api/http/engine`), backed by
 * Montoya's `http.execution` package (Burp 2026.x). Wired in via OpenApiSpecExtra.kt.
 */

internal fun requestEnginePaths(): String = """
    "/api/http/engine": {
      "post": {
        "tags": ["Request Engine"],
        "summary": "Create a request execution engine",
        "description": "**[Montoya API]** Creates a high-throughput async request engine via `Http.createRequestEngine`. With no body it uses Burp's default settings; otherwise a `ResourcePool` is built from the fields (concurrency limit, throttle, retries), or an existing named pool / the shared default pool is attached. Returns an `engine_id` handle used by the other engine endpoints.",
        "operationId": "createRequestEngine",
        "x-api-source": "montoya",
        "requestBody": { "required": false, "content": { "application/json": {
          "schema": { "${'$'}ref": "#/components/schemas/CreateEngineRequest" },
          "example": { "name": "spray", "concurrent_request_limit": 10, "throttle_ms": 50, "max_retries": 2 }
        } } },
        "responses": { "200": { "description": "Engine created", "content": { "application/json": {
          "schema": { "${'$'}ref": "#/components/schemas/EngineCreated" } } } } }
      },
      "get": {
        "tags": ["Request Engine"],
        "summary": "List engine and execution handles",
        "operationId": "listRequestEngines",
        "x-api-source": "montoya",
        "responses": { "200": { "description": "OK", "content": { "application/json": {
          "schema": { "${'$'}ref": "#/components/schemas/EngineListDto" } } } } }
      }
    },
    "/api/http/engine/{id}/queue": {
      "post": {
        "tags": ["Request Engine"],
        "summary": "Queue requests onto an engine",
        "description": "**[Montoya API]** Adds requests to the engine before it is started, via `RequestExecutionEngine.queue`. Each item is structured (`method`+`path`+optional `headers`/`body`) or a raw `request` string, plus an optional `label` echoed back in results.",
        "operationId": "queueEngineRequests",
        "x-api-source": "montoya",
        "parameters": [ { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } } ],
        "requestBody": { "required": true, "content": { "application/json": {
          "schema": { "${'$'}ref": "#/components/schemas/QueueRequestsRequest" },
          "example": { "requests": [ { "host": "example.com", "method": "GET", "path": "/", "label": "root" } ] }
        } } },
        "responses": { "200": { "description": "Queued", "content": { "application/json": {
          "schema": { "${'$'}ref": "#/components/schemas/QueuedResponse" } } } },
          "404": { "description": "No such engine" } }
      }
    },
    "/api/http/engine/{id}/send": {
      "post": {
        "tags": ["Request Engine"],
        "summary": "Start the engine (send all queued requests)",
        "description": "**[Montoya API]** Calls `RequestExecutionEngine.sendAll`, optionally with a per-request timeout, and returns an `execution_id` for polling stats, driving the lifecycle and collecting results.",
        "operationId": "sendAllEngineRequests",
        "x-api-source": "montoya",
        "parameters": [ { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } } ],
        "requestBody": { "required": false, "content": { "application/json": {
          "schema": { "${'$'}ref": "#/components/schemas/SendAllRequest" }, "example": { "timeout_ms": 10000 } } } },
        "responses": { "200": { "description": "Execution started", "content": { "application/json": {
          "schema": { "${'$'}ref": "#/components/schemas/ExecutionCreated" } } } },
          "404": { "description": "No such engine" } }
      }
    },
    "/api/http/engine/execution/{id}/queue": {
      "post": {
        "tags": ["Request Engine"],
        "summary": "Queue more requests onto a running execution",
        "operationId": "queueExecutionRequests",
        "x-api-source": "montoya",
        "parameters": [ { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } } ],
        "requestBody": { "required": true, "content": { "application/json": {
          "schema": { "${'$'}ref": "#/components/schemas/QueueRequestsRequest" } } } },
        "responses": { "200": { "description": "Queued", "content": { "application/json": {
          "schema": { "${'$'}ref": "#/components/schemas/QueuedResponse" } } } }, "404": { "description": "No such execution" } }
      }
    },
    "/api/http/engine/execution/{id}/stats": {
      "get": {
        "tags": ["Request Engine"],
        "summary": "Live execution statistics",
        "description": "**[Montoya API]** Reports `ExecutionStats`: requested, completed, failed, in-flight and pending counts plus elapsed milliseconds.",
        "operationId": "getExecutionStats",
        "x-api-source": "montoya",
        "parameters": [ { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } } ],
        "responses": { "200": { "description": "OK", "content": { "application/json": {
          "schema": { "${'$'}ref": "#/components/schemas/ExecutionStatsDto" } } } }, "404": { "description": "No such execution" } }
      }
    },
    "/api/http/engine/execution/{id}/finished": {
      "get": {
        "tags": ["Request Engine"],
        "summary": "Whether the execution has finished",
        "operationId": "getExecutionFinished",
        "x-api-source": "montoya",
        "parameters": [ { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } } ],
        "responses": { "200": { "description": "OK", "content": { "application/json": {
          "schema": { "${'$'}ref": "#/components/schemas/FinishedDto" } } } }, "404": { "description": "No such execution" } }
      }
    },
    "/api/http/engine/execution/{id}/pause": {
      "post": { "tags": ["Request Engine"], "summary": "Pause the execution", "operationId": "pauseExecution", "x-api-source": "montoya",
        "parameters": [ { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } } ],
        "responses": { "200": { "description": "Paused" }, "404": { "description": "No such execution" } } }
    },
    "/api/http/engine/execution/{id}/resume": {
      "post": { "tags": ["Request Engine"], "summary": "Resume the execution", "operationId": "resumeExecution", "x-api-source": "montoya",
        "parameters": [ { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } } ],
        "responses": { "200": { "description": "Resumed" }, "404": { "description": "No such execution" } } }
    },
    "/api/http/engine/execution/{id}/cancel": {
      "post": { "tags": ["Request Engine"], "summary": "Cancel the execution", "operationId": "cancelExecution", "x-api-source": "montoya",
        "parameters": [ { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } } ],
        "responses": { "200": { "description": "Cancelled" }, "404": { "description": "No such execution" } } }
    },
    "/api/http/engine/execution/{id}/await": {
      "post": {
        "tags": ["Request Engine"],
        "summary": "Wait (bounded) for completion and collect results",
        "description": "**[Montoya API]** Blocks up to `timeout_ms` for the execution to finish, then returns `RequestExecutionResult`: per-request results (label, status, request/response) plus final stats. The wait runs off the server event loop and is capped, so a stuck upstream returns `202` with current stats and `timed_out: true` instead of hanging.",
        "operationId": "awaitExecution",
        "x-api-source": "montoya",
        "parameters": [ { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } } ],
        "requestBody": { "required": false, "content": { "application/json": {
          "schema": { "${'$'}ref": "#/components/schemas/AwaitRequest" }, "example": { "timeout_ms": 30000, "include_body": true } } } },
        "responses": {
          "200": { "description": "Completed", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ExecutionResultDto" } } } },
          "202": { "description": "Still running after the timeout budget", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ExecutionResultDto" } } } },
          "404": { "description": "No such execution" }
        }
      }
    },
    "/api/http/engine/execution/{id}": {
      "delete": { "tags": ["Request Engine"], "summary": "Discard an execution handle", "operationId": "deleteExecution", "x-api-source": "montoya",
        "parameters": [ { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } } ],
        "responses": { "200": { "description": "Removed" }, "404": { "description": "No such execution" } } }
    },
    "/api/http/engine/{id}": {
      "delete": { "tags": ["Request Engine"], "summary": "Discard an engine handle", "operationId": "deleteEngine", "x-api-source": "montoya",
        "parameters": [ { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } } ],
        "responses": { "200": { "description": "Removed" }, "404": { "description": "No such engine" } } }
    }
"""

internal fun requestEngineSchemas(): String = """
      "CreateEngineRequest": {
        "type": "object",
        "description": "Options for a new request engine. Omit everything for Burp defaults.",
        "properties": {
          "name": { "type": "string", "description": "Engine name shown in Burp." },
          "concurrent_request_limit": { "type": "integer", "description": "Max requests in flight (ResourcePool.withConcurrentRequestLimit)." },
          "throttle_ms": { "type": "integer", "description": "Delay between requests in ms (ResourcePool.withThrottle)." },
          "max_retries": { "type": "integer", "description": "Retry attempts per request (ResourcePool.withMaxRetries)." },
          "resource_pool_name": { "type": "string", "description": "Attach to an existing named pool instead of building one." },
          "default_pool": { "type": "boolean", "description": "Use Burp's shared default resource pool." }
        }
      },
      "EngineCreated": { "type": "object", "properties": {
        "engine_id": { "type": "string" }, "name": { "type": "string", "nullable": true } } },
      "EngineRequestItem": {
        "type": "object",
        "required": ["host"],
        "properties": {
          "host": { "type": "string" }, "port": { "type": "integer", "default": 443 }, "use_https": { "type": "boolean", "default": true },
          "request": { "type": "string", "description": "Raw HTTP/1.1 request string; alternative to method+path." },
          "method": { "type": "string" }, "path": { "type": "string" },
          "headers": { "type": "object", "additionalProperties": { "type": "string" } },
          "body": { "description": "Object or string body." },
          "label": { "type": "string", "description": "Echoed back in results." }
        }
      },
      "QueueRequestsRequest": { "type": "object", "required": ["requests"], "properties": {
        "requests": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/EngineRequestItem" } } } },
      "QueuedResponse": { "type": "object", "properties": { "id": { "type": "string" }, "queued": { "type": "integer" } } },
      "SendAllRequest": { "type": "object", "properties": { "timeout_ms": { "type": "integer", "nullable": true } } },
      "ExecutionCreated": { "type": "object", "properties": { "execution_id": { "type": "string" } } },
      "ExecutionStatsDto": { "type": "object", "properties": {
        "requested": { "type": "integer" }, "completed": { "type": "integer" }, "failed": { "type": "integer" },
        "in_flight": { "type": "integer" }, "pending": { "type": "integer" }, "elapsed_ms": { "type": "integer" } } },
      "FinishedDto": { "type": "object", "properties": { "finished": { "type": "boolean" } } },
      "AwaitRequest": { "type": "object", "properties": {
        "timeout_ms": { "type": "integer", "default": 30000 }, "include_body": { "type": "boolean", "default": true } } },
      "RequestResultDto": { "type": "object", "properties": {
        "label": { "type": "string", "nullable": true },
        "status": { "type": "string", "description": "RequestStatus: RESPONDED, TIMED_OUT, CONNECTION_FAILED or DROPPED." },
        "request_response": { "${'$'}ref": "#/components/schemas/HttpEntryDto" } } },
      "ExecutionResultDto": { "type": "object", "properties": {
        "cancelled": { "type": "boolean" }, "timed_out": { "type": "boolean" },
        "stats": { "${'$'}ref": "#/components/schemas/ExecutionStatsDto" },
        "results": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/RequestResultDto" } } } },
      "EngineListDto": { "type": "object", "properties": {
        "engines": { "type": "array", "items": { "type": "string" } },
        "executions": { "type": "array", "items": { "type": "string" } } } }
"""

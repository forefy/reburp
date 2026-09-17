package com.reburp

/**
 * Paths that were implemented and advertised in the README but missing from the OpenAPI
 * document, so an agent reading the spec could not discover them: the activity log, reburp's
 * own recording sessions, the Bambda group and four HTTP analysis endpoints.
 *
 * Shapes here are taken from the request models in Models.kt rather than described from
 * memory, because a spec that does not match the code is the bug this file exists to fix.
 */

internal fun undocumentedPaths(): String = """
    "/api/log": {
      "get": {
        "tags": ["Activity Log"],
        "summary": "Query the reburp call log",
        "description": "Returns a page of reburp's own record of REST calls, newest first by default. This is reburp bookkeeping, not Burp data. `total` reports how many entries matched, so a page is distinguishable from the whole log.",
        "operationId": "getActivityLog",
        "parameters": [
          { "name": "limit",  "in": "query", "schema": { "type": "integer", "default": 100, "minimum": 1, "maximum": 500 }, "description": "Entries per page. Values above 500 are clamped to 500; compare `returned` against `total` to see whether more remain." },
          { "name": "offset", "in": "query", "schema": { "type": "integer", "default": 0 }, "description": "Entries to skip, counted in the requested order" },
          { "name": "newest_first", "in": "query", "schema": { "type": "boolean", "default": true }, "description": "Newest call first. Set false for chronological order." },
          { "name": "method", "in": "query", "schema": { "type": "string" }, "description": "Filter by HTTP method" },
          { "name": "status", "in": "query", "schema": { "type": "integer" }, "description": "Filter by response status" },
          { "name": "path_contains", "in": "query", "schema": { "type": "string" }, "description": "Substring match on the request path" },
          { "name": "session_id", "in": "query", "schema": { "type": "string" }, "description": "Restrict to one reburp session" }
        ],
        "responses": { "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/LogPage" } } } } }
      },
      "delete": {
        "tags": ["Activity Log"],
        "summary": "Clear the reburp call log",
        "operationId": "clearActivityLog",
        "responses": { "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } } }
      }
    },

    "/api/session": {
      "post": {
        "tags": ["Activity Log"],
        "summary": "Start a labelled reburp session",
        "description": "Opens a named recording session that subsequent log entries are attributed to. Unrelated to Burp's session handling rules.",
        "operationId": "startReburpSession",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/SessionRequest" } } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/SessionDto" } } } },
          "400": { "description": "Missing label", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" } } } }
        }
      }
    },

    "/api/sessions": {
      "get": {
        "tags": ["Activity Log"],
        "summary": "List reburp sessions",
        "operationId": "listReburpSessions",
        "responses": { "200": { "description": "OK", "content": { "application/json": { "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/SessionDto" } } } } } }
      }
    },

    "/api/repeater/tabs": {
      "delete": {
        "tags": ["HTTP"],
        "summary": "Clear Repeater tabs",
        "description": "Closes the Repeater tabs reburp opened in this Burp session.",
        "operationId": "clearRepeaterTabs",
        "responses": { "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } } }
      }
    },

    "/api/bambda/import": {
      "post": {
        "tags": ["Bambda"],
        "summary": "Import a Bambda script",
        "operationId": "importBambdaScript",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/BambdaImportRequest" } } }
        },
        "responses": { "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/BambdaImportResponse" } } } } }
      }
    },

    "/api/bambda/generate-chain": {
      "post": {
        "tags": ["Bambda"],
        "summary": "Generate a Bambda request chain",
        "description": "Builds Bambda source for a multi-step request chain, extracting a value from each step's response to feed the next, and optionally imports it.",
        "operationId": "generateBambdaChain",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/GenerateChainRequest" } } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/GenerateChainResponse" } } } },
          "400": { "description": "No steps supplied", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" } } } }
        }
      }
    },

    "/api/http/jwt/decode": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Decode a JWT",
        "description": "Splits a JWT into header and payload without verifying its signature, and reports weaknesses such as alg none.",
        "operationId": "analyzeJwt",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "type": "object", "required": ["token"], "properties": { "token": { "type": "string" } } }, "example": {"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"} } }
        },
        "responses": { "200": { "description": "OK", "content": { "application/json": { "schema": { "type": "object", "properties": { "header": { "type": "object" }, "payload": { "type": "object" }, "issues": { "type": "array", "items": { "type": "string" } } } } } } } }
      }
    },

    "/api/http/auth-diff": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Diff a request with and without authentication",
        "description": "Sends the same request authenticated and unauthenticated and diffs the responses, which is the access-control (BOLA/BFLA) check. Supply second_auth_header to compare two privilege levels instead.",
        "operationId": "authDiff",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/AuthDiffRequest" } } }
        },
        "responses": { "200": { "description": "OK", "content": { "application/json": { "schema": { "type": "object" } } } } }
      }
    },

    "/api/http/fuzz": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Fuzz a path template against a wordlist",
        "description": "Substitutes each wordlist entry into the {word} placeholder in path_template and reports the responses, with filters to drop catch-all pages.",
        "operationId": "fuzzRequests",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/FuzzRequest" } } }
        },
        "responses": { "200": { "description": "OK", "content": { "application/json": { "schema": { "type": "object" } } } } }
      }
    },

    "/api/http/rate-test": {
      "post": {
        "tags": ["HTTP"],
        "summary": "Test for rate limiting",
        "description": "Repeats one request count times and reports how the status codes and timings change, to show whether a rate limit engages.",
        "operationId": "rateTest",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/RateTestRequest" } } }
        },
        "responses": { "200": { "description": "OK", "content": { "application/json": { "schema": { "type": "object" } } } } }
      }
    }
"""

internal fun undocumentedSchemas(): String = """
      "LogPage": {
        "type": "object",
        "description": "One page of the activity log",
        "properties": {
          "total":        { "type": "integer", "description": "How many entries matched the filters, before paging" },
          "returned":     { "type": "integer", "description": "How many are in this page" },
          "offset":       { "type": "integer" },
          "limit":        { "type": "integer", "description": "The limit actually applied, after clamping" },
          "newest_first": { "type": "boolean" },
          "entries":      { "type": "array", "items": { "type": "object" } }
        }
      },

      "SessionRequest": {
        "type": "object",
        "required": ["label"],
        "properties": {
          "label": { "type": "string", "description": "Human-readable name for the session" },
          "host":  { "type": "string", "nullable": true, "description": "Optional host this session relates to" }
        }
      },

      "SessionDto": {
        "type": "object",
        "properties": {
          "id":         { "type": "string" },
          "label":      { "type": "string" },
          "host":       { "type": "string", "nullable": true },
          "created_at": { "type": "string" }
        }
      },

      "BambdaImportRequest": {
        "type": "object",
        "required": ["name", "source"],
        "properties": {
          "name":   { "type": "string" },
          "source": { "type": "string", "description": "Bambda source code" }
        }
      },

      "BambdaImportResponse": {
        "type": "object",
        "properties": {
          "status": { "type": "string" },
          "errors": { "type": "array", "items": { "type": "string" } }
        }
      },

      "ChainStep": {
        "type": "object",
        "required": ["method", "path"],
        "properties": {
          "method":  { "type": "string" },
          "path":    { "type": "string" },
          "headers": { "type": "object", "additionalProperties": { "type": "string" } },
          "body":    { "type": "string", "nullable": true },
          "extract": { "type": "object", "additionalProperties": { "type": "string" }, "description": "Variable name to a jsonpath or regex pulled from this step's response, e.g. {\"token\": \"$.access\"}" },
          "inject":  { "type": "object", "additionalProperties": { "type": "string" }, "description": "Variable name to the header it fills in the next step, e.g. {\"token\": \"Authorization: Bearer {token}\"}" }
        }
      },

      "GenerateChainRequest": {
        "type": "object",
        "required": ["name", "host", "steps"],
        "properties": {
          "name":  { "type": "string" },
          "host":  { "type": "string" },
          "port":  { "type": "integer", "default": 443 },
          "https": { "type": "boolean", "default": true },
          "steps": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/ChainStep" } }
        }
      },

      "GenerateChainResponse": {
        "type": "object",
        "properties": {
          "name":          { "type": "string" },
          "source":        { "type": "string" },
          "imported":      { "type": "boolean" },
          "import_errors": { "type": "array", "items": { "type": "string" } }
        }
      },

      "AuthDiffRequest": {
        "type": "object",
        "required": ["host", "path", "auth_header"],
        "properties": {
          "host":               { "type": "string" },
          "port":               { "type": "integer", "default": 443 },
          "https":              { "type": "boolean", "default": true },
          "method":             { "type": "string", "default": "GET" },
          "path":               { "type": "string" },
          "headers":            { "type": "object", "nullable": true, "additionalProperties": { "type": "string" } },
          "body":               { "description": "Request body. A JSON value is sent as JSON; a string is sent verbatim." },
          "auth_header":        { "type": "string", "description": "Header for the high-privilege user, e.g. Authorization: Bearer ..." },
          "second_auth_header": { "type": "string", "nullable": true, "description": "Header for a second, lower-privilege user. Enables privilege-escalation comparison." }
        }
      },

      "FuzzRequest": {
        "type": "object",
        "required": ["host", "path_template", "wordlist"],
        "properties": {
          "host":                  { "type": "string" },
          "port":                  { "type": "integer", "default": 443 },
          "https":                 { "type": "boolean", "default": true },
          "method":                { "type": "string", "default": "GET" },
          "path_template":         { "type": "string", "description": "Path containing the {word} placeholder, e.g. /api/{word}/" },
          "wordlist":              { "type": "array", "items": { "type": "string" } },
          "body":                  { "description": "Request body sent with every attempt. A JSON value is sent as JSON; a string verbatim." },
          "headers":               { "type": "object", "nullable": true, "additionalProperties": { "type": "string" } },
          "filter_status":         { "type": "array", "items": { "type": "integer" }, "description": "Only report these status codes. Empty reports all." },
          "exclude_body_size":     { "type": "integer", "nullable": true },
          "exclude_body_contains": { "type": "string", "nullable": true },
          "include_body_snippet":  { "type": "boolean", "default": false },
          "concurrency":           { "type": "integer", "default": 5 },
          "timeout_ms":            { "type": "integer", "nullable": true }
        }
      },

      "RateTestRequest": {
        "type": "object",
        "required": ["host", "path"],
        "properties": {
          "host":    { "type": "string" },
          "port":    { "type": "integer", "default": 443 },
          "https":   { "type": "boolean", "default": true },
          "method":  { "type": "string", "default": "POST" },
          "path":    { "type": "string" },
          "headers": { "type": "object", "nullable": true, "additionalProperties": { "type": "string" } },
          "body":    { "description": "Request body. A JSON value is sent as JSON; a string is sent verbatim." },
          "count":   { "type": "integer", "default": 30, "description": "How many times to repeat the request" },
          "concurrency": { "type": "integer", "default": 10, "description": "How many of those requests are in flight at once" }
        }
      }
"""

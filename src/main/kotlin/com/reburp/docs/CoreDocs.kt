package com.reburp

/**
 * OpenAPI fragments for the logging, metadata, Collaborator and Scanner additions.
 * Comma-separated entries, no leading or trailing comma; [extraPaths] and [extraSchemas] join them.
 */

internal fun corePaths(): String = """
    "/api/logging/output": {
      "post": {
        "tags": ["Logging"],
        "summary": "Append a line to this extension's output tab",
        "description": "**[Montoya API]** Writes to the output stream Burp shows for this extension. Use it to leave a trace next to whatever else the extension is doing. This is not the same as Burp's suite-wide event log, which is written by `/api/logging/event`.",
        "operationId": "logToOutput",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/LogMessageInput" },
            "example": { "message": "Replayed 42 requests from history" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    },

    "/api/logging/error": {
      "post": {
        "tags": ["Logging"],
        "summary": "Append a line to this extension's error tab",
        "description": "**[Montoya API]** Writes to the error stream Burp shows for this extension. Supplying `stack_trace` attaches it as a throwable so Burp renders it beneath the summary line.",
        "operationId": "logToError",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/LogMessageInput" },
            "example": { "message": "Upstream refused the connection", "stack_trace": "java.net.ConnectException: Connection refused" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    },

    "/api/logging/event": {
      "post": {
        "tags": ["Logging"],
        "summary": "Raise an entry in Burp's suite-wide event log",
        "description": "**[Montoya API]** Raises a structured event in the log shared by the whole suite, which is where operators look for operational problems. Prefer this over the extension output tab when something needs attention rather than just recording.",
        "operationId": "raiseLogEvent",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/LogEventInput" },
            "example": { "level": "ERROR", "message": "Session token refresh failed three times in a row" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    },

    "/api/logging/stream": {
      "post": {
        "tags": ["Logging"],
        "summary": "Write directly to the output or error stream",
        "description": "**[Montoya API]** Writes to the underlying stream and flushes it. Set `newline` to false to build a line across several calls, which the line-oriented endpoints cannot do.",
        "operationId": "writeLogStream",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/LogStreamInput" },
            "example": { "stream": "OUTPUT", "message": "progress: ", "newline": false }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    },

    "/api/meta/version": {
      "get": {
        "tags": ["Meta"],
        "summary": "Burp's version, split into components",
        "description": "**[Montoya API]** Returns the version as separate major, minor and build parts plus the numeric build number. Use this rather than parsing the string from `/api/status` when you need to gate behaviour on a specific Burp build.",
        "operationId": "getBurpVersion",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/BurpVersionDto" },
              "example": { "name": "Burp Suite Professional", "major": "2025", "minor": "12", "build": "4", "build_number": 20251204, "edition": "PROFESSIONAL", "edition_display_name": "Burp Suite Professional", "full_version": "2025.12.4" }
            } }
          }
        }
      }
    },

    "/api/meta/extension": {
      "get": {
        "tags": ["Meta"],
        "summary": "How Burp loaded this extension",
        "description": "**[Montoya API]** Reports the JAR path Burp loaded, whether it came from the BApp Store, and the port this REST server is bound to.",
        "operationId": "getExtensionInfo",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ExtensionInfoDto" } } }
          }
        }
      }
    },

    "/api/meta/enums": {
      "get": {
        "tags": ["Meta"],
        "summary": "Enum vocabularies accepted elsewhere in this API",
        "description": "**[Montoya API]** Lists the highlight colours and Organizer statuses this API accepts, each with Burp's own display label. Read this instead of hardcoding the enum names, since they can change between Burp releases.",
        "operationId": "getEnumCatalog",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/EnumCatalogDto" } } }
          }
        }
      }
    },

    "/api/meta/extension/unload": {
      "post": {
        "tags": ["Meta"],
        "summary": "Unload this extension",
        "description": "**[Montoya API]** Asks Burp to unload the extension. This stops the REST API: the response is written first, then the unload runs, and nothing on this port answers afterwards. Only Burp's Extensions tab can start it again, so do not call this from an unattended script. Requires `confirm` to be true.",
        "operationId": "unloadExtension",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/UnloadInput" },
            "example": { "confirm": true }
          } }
        },
        "responses": {
          "200": { "description": "Unload scheduled", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/collaborator/server": {
      "get": {
        "tags": ["Collaborator"],
        "summary": "The Collaborator server this project uses",
        "description": "**[Montoya API]** Returns the Collaborator server address payloads resolve against, and whether it is a literal IP rather than a hostname. Useful for confirming a private Collaborator instance is actually in use before relying on out-of-band detection.",
        "operationId": "getCollaboratorServer",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/CollaboratorServerDto" },
              "example": { "address": "oastify.com", "is_literal_address": false }
            } }
          },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    },

    "/api/collaborator/generate/default": {
      "post": {
        "tags": ["Collaborator"],
        "summary": "Generate a payload from the project's default generator",
        "description": "**[Montoya API]** Generates from Burp's project-wide default generator rather than creating a private client. Interactions for these payloads appear in Burp's own Collaborator tab, so use this when a human will watch the results. Use `/api/collaborator/generate` instead when a script needs a secret key it can poll on its own.",
        "operationId": "generateDefaultCollaboratorPayload",
        "x-api-source": "montoya",
        "requestBody": {
          "required": false,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/DefaultPayloadInput" },
            "example": { "options": ["WITHOUT_SERVER_LOCATION"] }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/DefaultPayloadDto" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    },

    "/api/collaborator/interactions/{secretKey}": {
      "get": {
        "tags": ["Collaborator"],
        "summary": "Poll interactions with protocol detail",
        "description": "**[Montoya API]** Like `/api/collaborator/poll`, but includes the protocol-specific detail attached to each hit: the DNS query and its type, the HTTP exchange, or the SMTP conversation. That detail is what tells you what the target actually did, rather than only that it called home.\n\nPolling consumes interactions, so a hit is returned once. Narrow with `payload` or `interaction_id`, but not both.",
        "operationId": "getCollaboratorInteractions",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "secretKey",      "in": "path",  "required": true, "schema": { "type": "string" }, "description": "Secret key returned when the payload was generated" },
          { "name": "payload",        "in": "query", "schema": { "type": "string" }, "description": "Return only interactions for this payload" },
          { "name": "interaction_id", "in": "query", "schema": { "type": "string" }, "description": "Return only the interaction with this id" }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/DetailedInteractionDto" } } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    },

    "/api/scanner/tasks/{id}/detail": {
      "get": {
        "tags": ["Scanner"],
        "summary": "Progress detail for a running audit",
        "description": "**[Montoya API]** Reports how many insertion points Burp derived, how many requests it has sent, and how many errors it hit. Insertion point count is the useful signal for whether an audit is actually covering the target or has found almost nothing to test. Crawl tasks have no insertion points and return 400.",
        "operationId": "getAuditDetail",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" }, "description": "Task id returned when the audit was started" }
        ],
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/AuditDetailDto" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/scanner/tasks/{id}/add": {
      "post": {
        "tags": ["Scanner"],
        "summary": "Feed an extra exchange into a running audit",
        "description": "**[Montoya API]** Adds a request, and optionally its response, to an audit that is already running so Burp audits that exchange too. Use it to steer an audit onto traffic Burp would not have reached by itself, such as a request built by hand or captured elsewhere.",
        "operationId": "addToAudit",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" }, "description": "Task id of a running audit" }
        ],
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/AddToAuditInput" },
            "example": { "host": "example.com", "port": 443, "secure": true, "request": "GET /admin HTTP/1.1\r\nHost: example.com\r\n\r\n" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/scanner/crawl/preview": {
      "post": {
        "tags": ["Scanner"],
        "summary": "Validate crawl seed URLs without starting a crawl",
        "description": "**[Montoya API]** Builds a crawl configuration and echoes back the seed URLs Burp accepted. Use it to catch a malformed seed before committing scanner capacity to a crawl.",
        "operationId": "previewCrawl",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/CrawlPreviewInput" },
            "example": { "seed_urls": ["https://example.com/", "https://example.com/admin"] }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/CrawlPreviewDto" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" }
        }
      }
    },

    "/api/scanner/issue-definition": {
      "post": {
        "tags": ["Scanner"],
        "summary": "Build an issue definition and see how Burp reads it",
        "description": "**[Montoya API]** Constructs an audit issue definition and echoes back Burp's interpretation, including the type index it assigns. Use it to check a custom finding's wording and severity before creating issues with `/api/issues`.",
        "operationId": "buildIssueDefinition",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/IssueDefinitionInput" },
            "example": { "name": "Debug endpoint exposed", "background": "The application exposes a debug endpoint without authentication.", "remediation": "Remove the endpoint or require authentication.", "typical_severity": "HIGH" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/IssueDefinitionDto" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/scanner/issues/consolidate": {
      "post": {
        "tags": ["Scanner"],
        "summary": "Group issues into a single audit result",
        "description": "**[Montoya API]** Collects the site map's current issues into one audit result, optionally filtered by severity, and returns each with its Collaborator hits and issue definition attached. This is the fullest view of a finding available over REST, and the Collaborator hits are what distinguish a confirmed out-of-band finding from an inference.",
        "operationId": "consolidateIssues",
        "x-api-source": "montoya",
        "requestBody": {
          "required": false,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/ConsolidateInput" },
            "example": { "severities": ["HIGH", "MEDIUM"] }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ConsolidatedDto" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/ProOnly" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    }
"""

internal fun coreSchemas(): String = """
      "LogMessageInput": {
        "type": "object",
        "required": ["message"],
        "properties": {
          "message":     { "type": "string", "description": "Text to write" },
          "stack_trace": { "type": "string", "nullable": true, "description": "Optional trace text attached as a throwable. Used by the error endpoint only." }
        }
      },

      "LogEventInput": {
        "type": "object",
        "required": ["message"],
        "properties": {
          "message": { "type": "string", "description": "Text of the event" },
          "level":   { "type": "string", "enum": ["DEBUG", "INFO", "ERROR", "CRITICAL"], "default": "INFO", "description": "Severity recorded in Burp's event log" }
        }
      },

      "LogStreamInput": {
        "type": "object",
        "required": ["message"],
        "properties": {
          "message": { "type": "string",  "description": "Text to write" },
          "stream":  { "type": "string",  "enum": ["OUTPUT", "ERROR"], "default": "OUTPUT", "description": "Which of this extension's streams to write to" },
          "newline": { "type": "boolean", "default": true, "description": "Append a newline. Set false to build a line across several calls." }
        }
      },

      "BurpVersionDto": {
        "type": "object",
        "properties": {
          "name":                 { "type": "string",  "description": "Product name, for example Burp Suite Professional" },
          "major":                { "type": "string",  "description": "Major version component" },
          "minor":                { "type": "string",  "description": "Minor version component" },
          "build":                { "type": "string",  "description": "Build component" },
          "build_number":         { "type": "integer", "format": "int64", "description": "Numeric build number, suitable for ordering comparisons" },
          "edition":              { "type": "string",  "description": "Edition enum name, for example PROFESSIONAL" },
          "edition_display_name": { "type": "string",  "description": "Edition as Burp labels it in its own interface" },
          "full_version":         { "type": "string",  "description": "Version as a single string" }
        }
      },

      "ExtensionInfoDto": {
        "type": "object",
        "properties": {
          "filename":  { "type": "string",  "description": "Path of the JAR Burp loaded" },
          "is_bapp":   { "type": "boolean", "description": "Whether Burp installed this from the BApp Store" },
          "rest_port": { "type": "integer", "description": "Port this REST server is bound to" }
        }
      },

      "EnumValueDto": {
        "type": "object",
        "properties": {
          "value":        { "type": "string", "description": "Enum name to send in requests" },
          "display_name": { "type": "string", "description": "Label Burp shows for it" }
        }
      },

      "EnumCatalogDto": {
        "type": "object",
        "properties": {
          "highlight_colors":   { "type": "array", "items": { "${'$'}ref": "#/components/schemas/EnumValueDto" }, "description": "Highlight colours accepted by the annotate endpoints" },
          "organizer_statuses": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/EnumValueDto" }, "description": "Statuses used by Burp's Organizer" }
        }
      },

      "UnloadInput": {
        "type": "object",
        "required": ["confirm"],
        "properties": {
          "confirm": { "type": "boolean", "default": false, "description": "Must be true. Guards against unloading the API by accident." }
        }
      },

      "CollaboratorServerDto": {
        "type": "object",
        "properties": {
          "address":            { "type": "string",  "description": "Collaborator server address payloads resolve against" },
          "is_literal_address": { "type": "boolean", "description": "Whether the address is a literal IP rather than a hostname" }
        }
      },

      "DefaultPayloadInput": {
        "type": "object",
        "properties": {
          "options": { "type": "array", "items": { "type": "string" }, "description": "Payload options, for example WITHOUT_SERVER_LOCATION. Invalid values are rejected with the full allowed list." }
        }
      },

      "DefaultPayloadDto": {
        "type": "object",
        "properties": {
          "payload":        { "type": "string", "description": "The generated Collaborator payload" },
          "interaction_id": { "type": "string", "description": "Identifier correlating hits back to this payload" },
          "custom_data":    { "type": "string", "nullable": true, "description": "Custom data embedded in the payload, when present" },
          "server":         { "${'$'}ref": "#/components/schemas/CollaboratorServerDto" }
        }
      },

      "DnsDetailsDto": {
        "type": "object",
        "properties": {
          "query_type": { "type": "string", "description": "DNS record type requested, for example A or AAAA" },
          "query":      { "type": "string", "description": "Raw query as received" }
        }
      },

      "HttpDetailsDto": {
        "type": "object",
        "properties": {
          "protocol": { "type": "string", "nullable": false, "description": "HTTP or HTTPS" },
          "request":  { "type": "string", "nullable": true,  "description": "Request the target sent to the Collaborator server" },
          "response": { "type": "string", "nullable": true,  "description": "Response the Collaborator server returned" }
        }
      },

      "SmtpDetailsDto": {
        "type": "object",
        "properties": {
          "protocol":     { "type": "string", "description": "SMTP or SMTPS" },
          "conversation": { "type": "string", "description": "Full SMTP conversation transcript" }
        }
      },

      "DetailedInteractionDto": {
        "type": "object",
        "description": "A Collaborator hit with its protocol-specific detail. Exactly one of dns, http or smtp is populated.",
        "properties": {
          "id":          { "type": "string",  "description": "Interaction id" },
          "type":        { "type": "string",  "description": "Interaction type, for example DNS or HTTP" },
          "time":        { "type": "string",  "description": "When the hit arrived" },
          "client_ip":   { "type": "string",  "description": "Source address that contacted the server" },
          "client_port": { "type": "integer", "description": "Source port" },
          "custom_data": { "type": "string",  "nullable": true, "description": "Custom data carried by the payload" },
          "dns":         { "${'$'}ref": "#/components/schemas/DnsDetailsDto" },
          "http":        { "${'$'}ref": "#/components/schemas/HttpDetailsDto" },
          "smtp":        { "${'$'}ref": "#/components/schemas/SmtpDetailsDto" }
        }
      },

      "AuditDetailDto": {
        "type": "object",
        "properties": {
          "task_id":               { "type": "string",  "description": "Task id of the audit" },
          "status_message":        { "type": "string",  "description": "Burp's own status line for the task" },
          "insertion_point_count": { "type": "integer", "description": "How many insertion points Burp derived. A low count on a large target usually means the audit is not reaching the parameters you expected." },
          "request_count":         { "type": "integer", "description": "Requests the audit has sent so far" },
          "error_count":           { "type": "integer", "description": "Errors the audit has encountered" },
          "issue_count":           { "type": "integer", "description": "Issues raised so far" }
        }
      },

      "AddToAuditInput": {
        "type": "object",
        "required": ["request", "host"],
        "properties": {
          "request":  { "type": "string",  "description": "Raw HTTP request text. Line endings are normalised and Content-Length is corrected." },
          "response": { "type": "string",  "nullable": true, "description": "Raw HTTP response text. Omit to add a request with no response." },
          "host":     { "type": "string",  "description": "Target hostname" },
          "port":     { "type": "integer", "default": 443, "description": "Target port" },
          "secure":   { "type": "boolean", "default": true, "description": "Whether to use TLS" }
        }
      },

      "CrawlPreviewInput": {
        "type": "object",
        "required": ["seed_urls"],
        "properties": {
          "seed_urls": { "type": "array", "items": { "type": "string" }, "description": "URLs the crawl would start from. Must contain at least one." }
        }
      },

      "CrawlPreviewDto": {
        "type": "object",
        "properties": {
          "seed_urls": { "type": "array", "items": { "type": "string" }, "description": "Seed URLs Burp accepted" },
          "count":     { "type": "integer", "description": "How many seeds were accepted" }
        }
      },

      "IssueDefinitionInput": {
        "type": "object",
        "required": ["name", "background", "remediation"],
        "properties": {
          "name":             { "type": "string", "description": "Short title of the finding" },
          "background":       { "type": "string", "description": "Explanation of the issue class" },
          "remediation":      { "type": "string", "description": "How to fix it" },
          "typical_severity": { "type": "string", "enum": ["HIGH", "MEDIUM", "LOW", "INFORMATION", "FALSE_POSITIVE"], "default": "INFORMATION", "description": "Severity Burp should treat as typical for this class" }
        }
      },

      "IssueDefinitionDto": {
        "type": "object",
        "properties": {
          "name":             { "type": "string",  "description": "Title as Burp stored it" },
          "background":       { "type": "string",  "description": "Issue class explanation" },
          "remediation":      { "type": "string",  "description": "Remediation advice" },
          "typical_severity": { "type": "string",  "description": "Typical severity for this class" },
          "type_index":       { "type": "integer", "description": "Burp's internal type index, or -1 when it does not assign one" }
        }
      },

      "CollaboratorHitDto": {
        "type": "object",
        "properties": {
          "id":   { "type": "string", "description": "Interaction id" },
          "type": { "type": "string", "description": "Interaction type" },
          "time": { "type": "string", "description": "When the hit arrived" }
        }
      },

      "IssueDetailDto": {
        "type": "object",
        "properties": {
          "name":                      { "type": "string",  "description": "Finding title" },
          "base_url":                  { "type": "string",  "description": "URL the finding is anchored to" },
          "severity":                  { "type": "string",  "description": "Severity Burp assigned" },
          "confidence":                { "type": "string",  "description": "Confidence Burp assigned" },
          "detail":                    { "type": "string",  "nullable": true, "description": "Finding-specific detail" },
          "remediation":               { "type": "string",  "nullable": true, "description": "Remediation advice" },
          "request_response_count":    { "type": "integer", "description": "How many exchanges evidence this finding" },
          "collaborator_interactions": { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/CollaboratorHitDto" }, "description": "Out-of-band hits proving the finding, when Burp recorded any" },
          "definition":                { "${'$'}ref": "#/components/schemas/IssueDefinitionDto" }
        }
      },

      "ConsolidateInput": {
        "type": "object",
        "properties": {
          "severities": { "type": "array", "items": { "type": "string", "enum": ["HIGH", "MEDIUM", "LOW", "INFORMATION", "FALSE_POSITIVE"] }, "description": "Severities to include. Omit or leave empty for all." }
        }
      },

      "ConsolidatedDto": {
        "type": "object",
        "properties": {
          "total":  { "type": "integer", "description": "How many issues are included" },
          "issues": { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/IssueDetailDto" }, "description": "The issues, with detail attached" }
        }
      }
"""

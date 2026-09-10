package com.reburp

/**
 * OpenAPI fragments for the utility route groups added on top of the original spec.
 * Each function returns comma-separated `"path": { ... }` entries with no leading or
 * trailing comma; [extraPaths] joins them.
 */

internal fun numberPaths(): String = """
    "/api/utils/number/convert": {
      "post": {
        "tags": ["Numbers"],
        "summary": "Convert between binary, octal, decimal and hex",
        "description": "**[Montoya API]** Converts `value` from one named base to another using Burp's own `NumberUtils` converters. `from` and `to` must differ.",
        "operationId": "convertNumberBase",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/NumberConvertInput" },
            "example": { "value": "ff", "from": "HEX", "to": "DECIMAL" }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/NumberConvertResult" },
              "example": { "value": "ff", "from": "HEX", "to": "DECIMAL", "result": "255" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/number/convert-radix": {
      "post": {
        "tags": ["Numbers"],
        "summary": "Convert a value into an arbitrary radix",
        "description": "**[Montoya API]** Converts `value`, interpreted in the named base `from`, into the target `radix` (2-36).",
        "operationId": "convertNumberRadix",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/NumberRadixInput" },
            "example": { "value": "255", "from": "DECIMAL", "radix": 36 }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/NumberConvertResult" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    }
"""

internal fun jsonPaths(): String = """
    "/api/utils/json/validate": {
      "post": {
        "tags": ["JSON"],
        "summary": "Check whether a document is valid JSON",
        "description": "**[Montoya API]** Returns whether Burp's JSON parser accepts the document. Never returns an error for malformed input - the verdict is in the `valid` field.",
        "operationId": "validateJson",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/JsonInput" },
            "example": { "json": "{\"a\":1}" }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/JsonValidResult" },
              "example": { "valid": true, "error": null }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/json/read": {
      "post": {
        "tags": ["JSON"],
        "summary": "Read a value at a pointer",
        "description": "**[Montoya API]** Reads the value at `pointer`, coerced to `type`. Pointers use Burp's dotted-path syntax such as `user.roles.0.name`, not RFC 6901. `RAW` returns the value's JSON text.",
        "operationId": "readJsonPointer",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/JsonPointerInput" },
            "example": { "json": "{\"user\":{\"name\":\"ada\"}}", "pointer": "user.name", "type": "STRING" }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/JsonReadResult" },
              "example": { "pointer": "user.name", "type": "STRING", "found": true, "value": "ada" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/json/add": {
      "post": {
        "tags": ["JSON"],
        "summary": "Insert a value at a pointer",
        "description": "**[Montoya API]** Adds `value` at `pointer` and returns the resulting document. Use `/update` to replace a value that already exists.",
        "operationId": "addJsonPointer",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/JsonPointerInput" },
            "example": { "json": "{\"user\":{}}", "pointer": "user.name", "value": "\"ada\"" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/json/update": {
      "post": {
        "tags": ["JSON"],
        "summary": "Replace a value at a pointer",
        "description": "**[Montoya API]** Replaces the existing value at `pointer` and returns the resulting document.",
        "operationId": "updateJsonPointer",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/JsonPointerInput" },
            "example": { "json": "{\"user\":{\"name\":\"ada\"}}", "pointer": "user.name", "value": "\"grace\"" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/json/remove": {
      "post": {
        "tags": ["JSON"],
        "summary": "Delete the value at a pointer",
        "description": "**[Montoya API]** Removes the value at `pointer` and returns the resulting document.",
        "operationId": "removeJsonPointer",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/JsonPointerInput" },
            "example": { "json": "{\"user\":{\"name\":\"ada\"}}", "pointer": "user.name" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/json/inspect": {
      "post": {
        "tags": ["JSON"],
        "summary": "Describe a document's structure and types",
        "description": "**[Montoya API]** Parses the document into Burp's node model and reports the root node's kind. For objects, each key is typed individually so you can see how Burp classifies it before reading it.",
        "operationId": "inspectJson",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/JsonInput" },
            "example": { "json": "{\"id\":7,\"admin\":true}" }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/JsonInspectResult" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/json/normalize": {
      "post": {
        "tags": ["JSON"],
        "summary": "Round-trip a document through Burp's node builders",
        "description": "**[Montoya API]** Rebuilds the document node by node and re-serialises it, normalising whitespace and key serialisation. Useful for producing a canonical form before diffing two payloads.",
        "operationId": "normalizeJson",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/JsonInput" },
            "example": { "json": "{ \"b\" : 2,  \"a\":1 }" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/StringResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    }
"""

internal fun bytesPaths(): String = """
    "/api/utils/bytes/search": {
      "post": {
        "tags": ["Bytes"],
        "summary": "Find and count occurrences in a byte buffer",
        "description": "**[Montoya API]** Searches `data` for a literal `needle` or a `regex`, returning the first index and the total match count. Supply exactly one of the two. Setting both `from` and `to` restricts the search to that half-open range.",
        "operationId": "searchBytes",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/BytesSearchInput" },
            "example": { "data": "id=1&id=2&id=3", "needle": "id=", "case_sensitive": true }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/BytesSearchResult" },
              "example": { "index": 0, "count": 3, "data_length": 14, "searched_range": "[0, 14)" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/bytes/slice": {
      "post": {
        "tags": ["Bytes"],
        "summary": "Extract a byte range",
        "description": "**[Montoya API]** Returns the half-open range `[start, end)` of `data` as a new buffer.",
        "operationId": "sliceBytes",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/BytesSliceInput" },
            "example": { "data": "Authorization: Bearer xyz", "start": 15, "end": 25 }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/BytesResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/bytes/append": {
      "post": {
        "tags": ["Bytes"],
        "summary": "Concatenate a suffix onto a buffer",
        "description": "**[Montoya API]** Returns `data` with `suffix` appended.",
        "operationId": "appendBytes",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/BytesAppendInput" },
            "example": { "data": "payload", "suffix": "'--" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/BytesResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/bytes/alloc": {
      "post": {
        "tags": ["Bytes"],
        "summary": "Allocate a fixed-length buffer",
        "description": "**[Montoya API]** Allocates a zeroed buffer of `length` bytes, optionally writing the byte value `fill` at offset `at`. Capped at 1 MiB.",
        "operationId": "allocBytes",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/BytesAllocInput" },
            "example": { "length": 16, "fill": 65, "at": 0 }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/BytesResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/bytes/inspect": {
      "post": {
        "tags": ["Bytes"],
        "summary": "Summarise a byte buffer",
        "description": "**[Montoya API]** Reports length, first and last byte values, how many distinct byte values occur, and whether the buffer is printable text.",
        "operationId": "inspectBytes",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/BytesInput" },
            "example": { "data": "hello" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/BytesInspectResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/utils/bytes/convert": {
      "post": {
        "tags": ["Bytes"],
        "summary": "Round-trip text through Burp's byte codec",
        "description": "**[Montoya API]** Converts `data` to bytes and back using Burp's own string codec, returning the text, length and base64 form. Use this to see how Burp will interpret a payload's bytes.",
        "operationId": "convertBytes",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/BytesInput" },
            "example": { "data": "café" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/BytesResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    }
"""

internal fun rankingPaths(): String = """
    "/api/utils/rank": {
      "post": {
        "tags": ["Ranking"],
        "summary": "Rank proxy history by how anomalous each exchange looks",
        "description": "**[Montoya API]** Scores a window of proxy history with Burp's ranking engine and returns it ordered by interest. Scores are relative to the supplied set, so narrowing `host` or `scope_only` changes the ranking - filter deliberately rather than ranking everything.",
        "operationId": "rankHistory",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/RankInput" },
            "example": { "limit": 100, "offset": 0, "algorithm": "ANOMALY", "scope_only": true }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/RankResult" },
              "example": { "algorithm": "ANOMALY", "considered": 42, "ranked": [ { "rank": 1, "url": "https://example.com/admin", "method": "GET", "status": 200, "response_length": 5120 } ] }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    }
"""

internal fun shellPaths(): String = """
    "/api/utils/shell/status": {
      "get": {
        "tags": ["Shell"],
        "summary": "Report whether command execution is enabled",
        "description": "**[Montoya API]** Always available. Returns whether the execution routes are enabled and how to enable them. Reading this endpoint does not require the feature to be on.",
        "operationId": "getShellStatus",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ShellStatusResult" },
              "example": { "enabled": false, "enable_via": "REBURP_ENABLE_SHELL=1 in Burp's process environment, then restart Burp", "warning": "Shell execution is disabled..." }
            } }
          }
        }
      }
    },

    "/api/utils/shell/execute": {
      "post": {
        "tags": ["Shell"],
        "summary": "Execute an argument vector (disabled by default)",
        "description": "**[Montoya API]** Runs `command` as a direct argument vector with no shell involved, so shell metacharacters are inert.\n\n**Disabled by default and returns `403` unless `REBURP_ENABLE_SHELL=1` is set in Burp's environment.** This REST server is unauthenticated and accepts cross-origin requests, so enabling execution lets any web page you visit run commands on this host. Every invocation is written to Burp's extension output.",
        "operationId": "executeShellCommand",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/ShellExecInput" },
            "example": { "command": ["curl", "-s", "https://example.com"], "timeout_seconds": 30, "stderr_behavior": "MERGE" }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ShellExecResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/Forbidden" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    },

    "/api/utils/shell/execute-raw": {
      "post": {
        "tags": ["Shell"],
        "summary": "Execute a shell command line (disabled by default)",
        "description": "**[Montoya API]** Runs `command_line` through the system shell, which additionally exposes shell metacharacter injection. Prefer `/api/utils/shell/execute` unless you specifically need pipes or redirection.\n\n**Disabled by default and returns `403` unless `REBURP_ENABLE_SHELL=1` is set in Burp's environment.** Every invocation is written to Burp's extension output.",
        "operationId": "executeShellCommandLine",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/ShellExecInput" },
            "example": { "command_line": "ls -la | head -5", "timeout_seconds": 10 }
          } }
        },
        "responses": {
          "200": { "description": "OK", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ShellExecResult" } } } },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "403": { "${'$'}ref": "#/components/responses/Forbidden" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" }
        }
      }
    }
"""

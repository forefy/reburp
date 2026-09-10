package com.reburp

/**
 * OpenAPI fragments for the typed extension data routes mounted at `/api/extension-data`.
 * Both functions return comma-separated entries with no leading or trailing comma, so
 * [extraPaths] and [extraSchemas] can join them into the spec.
 */

internal fun extensionDataPaths(): String = """
    "/api/extension-data": {
      "get": {
        "tags": ["Extension Data"],
        "summary": "List every key stored at the root of the extension data tree",
        "description": "**[Montoya API]** Returns the keys held by the root `PersistedObject` from `api.persistence().extensionData()`, grouped into one bucket per Montoya value type. This tree is stored inside the Burp project file, so everything written through these routes survives an extension reload and comes back when the same project is reopened. It is a separate store from `/api/preferences`, which is user scoped and holds only flat scalar values.",
        "operationId": "listExtensionDataKeys",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ExtensionDataKeysResult" },
              "example": {
                "path": "",
                "child_objects": ["campaign"],
                "strings": ["last_run_id"],
                "booleans": [],
                "bytes": [],
                "shorts": [],
                "integers": ["run_count"],
                "longs": [],
                "byte_arrays": [],
                "http_requests": [],
                "http_responses": [],
                "http_request_responses": [],
                "string_lists": ["sessions"],
                "boolean_lists": [],
                "short_lists": [],
                "integer_lists": [],
                "long_lists": [],
                "byte_array_lists": [],
                "http_request_lists": [],
                "http_response_lists": [],
                "http_request_response_lists": []
              }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      }
    },

    "/api/extension-data/keys/{path}": {
      "get": {
        "tags": ["Extension Data"],
        "summary": "List every key stored at a nested child object path",
        "description": "**[Montoya API]** Walks the child object chain with `getChildObject` and returns the keys held by the node at the end of it, grouped by type. The path is a slash separated chain of child object names such as `campaign/targets`, and each segment becomes one more `getChildObject` hop. A missing segment yields 404 rather than an empty result, so a caller can tell an absent node from an empty one. The data lives in the Burp project file and survives extension reloads, unlike the flat user scoped values under `/api/preferences`.",
        "operationId": "listExtensionDataKeysAtPath",
        "x-api-source": "montoya",
        "parameters": [
          {
            "name": "path",
            "in": "path",
            "required": true,
            "description": "Slash separated chain of child object names, for example `campaign/targets`. Every segment must already exist.",
            "schema": { "type": "string" },
            "example": "campaign/targets"
          }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ExtensionDataKeysResult" },
              "example": {
                "path": "campaign/targets",
                "child_objects": [],
                "strings": ["owner"],
                "booleans": ["active"],
                "bytes": [],
                "shorts": [],
                "integers": [],
                "longs": [],
                "byte_arrays": [],
                "http_requests": ["login_probe"],
                "http_responses": [],
                "http_request_responses": [],
                "string_lists": ["hosts"],
                "boolean_lists": [],
                "short_lists": [],
                "integer_lists": ["ports"],
                "long_lists": [],
                "byte_array_lists": [],
                "http_request_lists": [],
                "http_response_lists": [],
                "http_request_response_lists": []
              }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/extension-data/value": {
      "get": {
        "tags": ["Extension Data"],
        "summary": "Read one typed value from the extension data tree",
        "description": "**[Montoya API]** Reads a single value with the `PersistedObject` getter that matches `type`, for example `getString`, `getIntegerList` or `getHttpRequestResponse`. Scalar and list results are returned as text in `value` and `values`. Request and response pairs are returned in `pair` or `pairs`. A missing key or a missing path segment yields 404. Values read here come from the Burp project file, which is why they survive an extension reload, and they are unrelated to `/api/preferences`.",
        "operationId": "getExtensionDataValue",
        "x-api-source": "montoya",
        "parameters": [
          {
            "name": "path",
            "in": "query",
            "required": false,
            "description": "Slash separated child object path. Leave it out or send an empty string to read from the root object.",
            "schema": { "type": "string", "default": "" },
            "example": "campaign/targets"
          },
          {
            "name": "type",
            "in": "query",
            "required": true,
            "description": "Which Montoya getter to use. The list variants read a `PersistedList` of that element type.",
            "schema": { "${'$'}ref": "#/components/schemas/ExtensionDataType" },
            "example": "STRING_LIST"
          },
          {
            "name": "key",
            "in": "query",
            "required": true,
            "description": "Name of the value inside the addressed object.",
            "schema": { "type": "string" },
            "example": "hosts"
          }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ExtensionDataValueResult" },
              "example": {
                "path": "campaign/targets",
                "type": "STRING_LIST",
                "key": "hosts",
                "found": true,
                "values": ["example.com", "api.example.com"]
              }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      },

      "put": {
        "tags": ["Extension Data"],
        "summary": "Write one typed value into the extension data tree",
        "description": "**[Montoya API]** Writes a single value with the `PersistedObject` setter that matches `type`, for example `setString`, `setLongList` or `setHttpResponse`. Any missing segment of `path` is created with `PersistedObject.persistedObject()` and attached with `setChildObject`, so a write never returns 404. List values are built with the matching `PersistedList` factory such as `persistedIntegerList`. HTTP values are parsed from raw HTTP text with `HttpRequest.httpRequest` and `HttpResponse.httpResponse`, and byte array values are built with `ByteArray.byteArray`. The write goes into the Burp project file and survives an extension reload, which is what separates this store from `/api/preferences`.",
        "operationId": "setExtensionDataValue",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/ExtensionDataValueRequest" },
            "example": {
              "path": "campaign/targets",
              "type": "STRING_LIST",
              "key": "hosts",
              "values": ["example.com", "api.example.com"]
            }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" },
              "example": { "message": "Set STRING_LIST value 'hosts' at path 'campaign/targets'" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      },

      "delete": {
        "tags": ["Extension Data"],
        "summary": "Delete one typed value from the extension data tree",
        "description": "**[Montoya API]** Removes a single value with the `PersistedObject` deleter that matches `type`, for example `deleteByteArray`, `deleteShortList` or `deleteHttpRequestResponseList`. Deleting a key that was never set is not an error. A missing path segment yields 404. The change is written into the Burp project file and therefore persists across extension reloads, separately from `/api/preferences`.",
        "operationId": "deleteExtensionDataValue",
        "x-api-source": "montoya",
        "parameters": [
          {
            "name": "path",
            "in": "query",
            "required": false,
            "description": "Slash separated child object path. Leave it out or send an empty string to delete from the root object.",
            "schema": { "type": "string", "default": "" },
            "example": "campaign/targets"
          },
          {
            "name": "type",
            "in": "query",
            "required": true,
            "description": "Which Montoya deleter to use. It must match the type the value was written with.",
            "schema": { "${'$'}ref": "#/components/schemas/ExtensionDataType" },
            "example": "STRING_LIST"
          },
          {
            "name": "key",
            "in": "query",
            "required": true,
            "description": "Name of the value inside the addressed object.",
            "schema": { "type": "string" },
            "example": "hosts"
          }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" },
              "example": { "message": "Deleted STRING_LIST value 'hosts' at path 'campaign/targets'" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    },

    "/api/extension-data/child": {
      "post": {
        "tags": ["Extension Data"],
        "summary": "Create a child object at a path",
        "description": "**[Montoya API]** Creates the child object named by `path`, together with any intermediate node that does not exist yet. Each new node is built with `PersistedObject.persistedObject()` and attached to its parent with `setChildObject`. The call is idempotent, and `created` reports whether the node already existed. The resulting tree is stored in the Burp project file and survives extension reloads, which the flat values under `/api/preferences` do not model at all.",
        "operationId": "createExtensionDataChild",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/ExtensionDataChildRequest" },
            "example": { "path": "campaign/targets" }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/ExtensionDataChildResult" },
              "example": { "path": "campaign/targets", "created": true, "child_objects": [] }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" }
        }
      },

      "delete": {
        "tags": ["Extension Data"],
        "summary": "Delete a child object and everything below it",
        "description": "**[Montoya API]** Resolves the parent of `path` with `getChildObject`, then removes the last segment with `deleteChildObject`. Every value and every nested child object under that node goes with it. The root object itself cannot be deleted, so an empty path is rejected with 400, and an unknown node yields 404. The removal is recorded in the Burp project file and outlives an extension reload, independently of `/api/preferences`.",
        "operationId": "deleteExtensionDataChild",
        "x-api-source": "montoya",
        "parameters": [
          {
            "name": "path",
            "in": "query",
            "required": true,
            "description": "Slash separated path of the child object to delete, for example `campaign/targets`. At least one segment is required.",
            "schema": { "type": "string" },
            "example": "campaign/targets"
          }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" },
              "example": { "message": "Deleted child object 'campaign/targets'" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" }
        }
      }
    }
"""

internal fun extensionDataSchemas(): String = """
      "ExtensionDataType": {
        "type": "string",
        "description": "Selects which typed family of Montoya accessors to use for a value. The plain names map to the scalar getters and setters, and the names ending in _LIST map to the PersistedList variants. There is no byte list, because Montoya stores byte sequences as a byte array instead.",
        "enum": [
          "STRING", "BOOLEAN", "BYTE", "SHORT", "INTEGER", "LONG", "BYTE_ARRAY",
          "HTTP_REQUEST", "HTTP_RESPONSE", "HTTP_REQUEST_RESPONSE",
          "STRING_LIST", "BOOLEAN_LIST", "SHORT_LIST", "INTEGER_LIST", "LONG_LIST", "BYTE_ARRAY_LIST",
          "HTTP_REQUEST_LIST", "HTTP_RESPONSE_LIST", "HTTP_REQUEST_RESPONSE_LIST"
        ]
      },

      "ExtensionDataKeysResult": {
        "type": "object",
        "description": "Every key held by one PersistedObject, grouped by the Montoya type it was stored with. A key can appear in more than one bucket, because each type keeps its own namespace.",
        "properties": {
          "path": { "type": "string", "description": "The child object path that was listed. An empty string means the root object." },
          "child_objects": { "type": "array", "items": { "type": "string" }, "description": "Names of nested child objects, from childObjectKeys." },
          "strings": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a string value, from stringKeys." },
          "booleans": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a boolean value, from booleanKeys." },
          "bytes": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a single byte value, from byteKeys." },
          "shorts": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a short value, from shortKeys." },
          "integers": { "type": "array", "items": { "type": "string" }, "description": "Keys holding an integer value, from integerKeys." },
          "longs": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a long value, from longKeys." },
          "byte_arrays": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a Montoya ByteArray, from byteArrayKeys." },
          "http_requests": { "type": "array", "items": { "type": "string" }, "description": "Keys holding an HttpRequest, from httpRequestKeys." },
          "http_responses": { "type": "array", "items": { "type": "string" }, "description": "Keys holding an HttpResponse, from httpResponseKeys." },
          "http_request_responses": { "type": "array", "items": { "type": "string" }, "description": "Keys holding an HttpRequestResponse pair, from httpRequestResponseKeys." },
          "string_lists": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a persisted list of strings, from stringListKeys." },
          "boolean_lists": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a persisted list of booleans, from booleanListKeys." },
          "short_lists": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a persisted list of shorts, from shortListKeys." },
          "integer_lists": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a persisted list of integers, from integerListKeys." },
          "long_lists": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a persisted list of longs, from longListKeys." },
          "byte_array_lists": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a persisted list of byte arrays, from byteArrayListKeys." },
          "http_request_lists": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a persisted list of requests, from httpRequestListKeys." },
          "http_response_lists": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a persisted list of responses, from httpResponseListKeys." },
          "http_request_response_lists": { "type": "array", "items": { "type": "string" }, "description": "Keys holding a persisted list of request and response pairs, from httpRequestResponseListKeys." }
        }
      },

      "HttpPairDto": {
        "type": "object",
        "description": "One request and response pair as raw HTTP text. It is used both when writing an HTTP_REQUEST_RESPONSE_LIST and when reading one back.",
        "required": ["request"],
        "properties": {
          "request": { "type": "string", "description": "Raw HTTP request text, parsed with HttpRequest.httpRequest on write." },
          "response": { "type": "string", "nullable": true, "description": "Raw HTTP response text, parsed with HttpResponse.httpResponse on write. It is null on read when the stored pair carries no response." }
        }
      },

      "ExtensionDataValueRequest": {
        "type": "object",
        "description": "A single typed write into the extension data tree. Which of the payload fields is required depends on the chosen type.",
        "required": ["type", "key"],
        "properties": {
          "path": { "type": "string", "default": "", "description": "Slash separated child object path. Missing segments are created before the write." },
          "type": { "allOf": [ { "${'$'}ref": "#/components/schemas/ExtensionDataType" } ], "description": "Which typed family of Montoya accessors performs the write." },
          "key": { "type": "string", "description": "Name of the value inside the addressed object. It must not be blank." },
          "value": { "type": "string", "nullable": true, "description": "The scalar payload, always sent as text. Numbers are parsed for their type, booleans accept true or false, BYTE_ARRAY is built with ByteArray.byteArray, and HTTP_REQUEST and HTTP_RESPONSE take raw HTTP text." },
          "values": { "type": "array", "items": { "type": "string" }, "nullable": true, "description": "The payload for every list type except HTTP_REQUEST_RESPONSE_LIST. Each entry is parsed the same way the matching scalar type parses value." },
          "request": { "type": "string", "nullable": true, "description": "Raw HTTP request text. It is required by type HTTP_REQUEST_RESPONSE." },
          "response": { "type": "string", "nullable": true, "description": "Raw HTTP response text. It is required by type HTTP_REQUEST_RESPONSE." },
          "pairs": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/HttpPairDto" }, "nullable": true, "description": "The payload for type HTTP_REQUEST_RESPONSE_LIST. Every entry needs both a request and a response." }
        }
      },

      "ExtensionDataValueResult": {
        "type": "object",
        "description": "A single value read back out of the extension data tree. Only the field that suits the requested type is populated.",
        "properties": {
          "path": { "type": "string", "description": "The child object path that was read. An empty string means the root object." },
          "type": { "allOf": [ { "${'$'}ref": "#/components/schemas/ExtensionDataType" } ], "description": "Which typed family of Montoya accessors performed the read." },
          "key": { "type": "string", "description": "Name of the value that was read." },
          "found": { "type": "boolean", "description": "Always true in a 200 response. A key that holds no value of the requested type yields 404 instead." },
          "value": { "type": "string", "nullable": true, "description": "The scalar result as text. It is populated for every scalar type, including the string form of a ByteArray, an HttpRequest or an HttpResponse." },
          "values": { "type": "array", "items": { "type": "string" }, "nullable": true, "description": "The list result, one text entry per element. It is populated for every list type except HTTP_REQUEST_RESPONSE_LIST." },
          "pair": { "allOf": [ { "${'$'}ref": "#/components/schemas/HttpPairDto" } ], "nullable": true, "description": "The result for type HTTP_REQUEST_RESPONSE, holding the stored request and its response." },
          "pairs": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/HttpPairDto" }, "nullable": true, "description": "The result for type HTTP_REQUEST_RESPONSE_LIST, one entry per stored pair." }
        }
      },

      "ExtensionDataChildRequest": {
        "type": "object",
        "description": "Names the child object to create inside the extension data tree.",
        "required": ["path"],
        "properties": {
          "path": { "type": "string", "description": "Slash separated path such as campaign/targets. At least one segment is required, because the root object always exists." }
        }
      },

      "ExtensionDataChildResult": {
        "type": "object",
        "description": "Outcome of creating a child object.",
        "properties": {
          "path": { "type": "string", "description": "The normalised path of the child object, with blank segments removed." },
          "created": { "type": "boolean", "description": "True when the node did not exist before this call, and false when it was already present." },
          "child_objects": { "type": "array", "items": { "type": "string" }, "description": "Names of the child objects that the addressed node holds, from childObjectKeys." }
        }
      }
"""

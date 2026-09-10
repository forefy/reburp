package com.reburp

/**
 * OpenAPI component schemas for the utility route groups. Returns comma-separated
 * `"Name": { ... }` entries with no leading or trailing comma; [extraSchemas] joins them.
 */
internal fun utilsSchemas(): String = """
      "NumberConvertInput": {
        "type": "object",
        "required": ["value", "from", "to"],
        "properties": {
          "value": { "type": "string", "description": "The number to convert, written in the `from` base" },
          "from":  { "type": "string", "enum": ["BINARY", "OCTAL", "DECIMAL", "HEX"], "description": "Base the value is written in" },
          "to":    { "type": "string", "enum": ["BINARY", "OCTAL", "DECIMAL", "HEX"], "description": "Base to convert into. Must differ from `from`." }
        }
      },

      "NumberRadixInput": {
        "type": "object",
        "required": ["value", "from", "radix"],
        "properties": {
          "value": { "type": "string", "description": "The number to convert, written in the `from` base" },
          "from":  { "type": "string", "enum": ["BINARY", "OCTAL", "DECIMAL", "HEX"], "description": "Base the value is written in" },
          "radix": { "type": "integer", "minimum": 2, "maximum": 36, "description": "Target radix" }
        }
      },

      "NumberConvertResult": {
        "type": "object",
        "description": "Result of a base conversion",
        "properties": {
          "value":  { "type": "string",  "description": "The input value, unchanged" },
          "from":   { "type": "string",  "description": "Source base, normalised to upper case" },
          "to":     { "type": "string",  "description": "Target base, or `RADIX_n` for arbitrary-radix conversions" },
          "result": { "type": "string",  "description": "Converted value" }
        }
      },

      "JsonInput": {
        "type": "object",
        "required": ["json"],
        "properties": {
          "json": { "type": "string", "description": "The JSON document as text" }
        }
      },

      "JsonValidResult": {
        "type": "object",
        "description": "Parse verdict. Malformed input yields `valid: false`, not an HTTP error.",
        "properties": {
          "valid": { "type": "boolean", "description": "Whether Burp's parser accepted the document" },
          "error": { "type": "string", "nullable": true, "description": "Why it was rejected, or null when valid" }
        }
      },

      "JsonPointerInput": {
        "type": "object",
        "required": ["json", "pointer"],
        "properties": {
          "json":    { "type": "string", "description": "The JSON document as text" },
          "pointer": { "type": "string", "description": "Dotted path such as `user.roles.0.name`. This is Burp's syntax, not RFC 6901." },
          "type":    { "type": "string", "enum": ["STRING", "BOOLEAN", "LONG", "DOUBLE", "RAW"], "default": "STRING", "description": "How to coerce the value on read. Ignored by add, update and remove." },
          "value":   { "type": "string", "description": "JSON text to write. Required by add and update; string values must include their quotes." }
        }
      },

      "JsonReadResult": {
        "type": "object",
        "description": "A single value read from a pointer",
        "properties": {
          "pointer": { "type": "string",  "description": "The pointer that was read" },
          "type":    { "type": "string",  "description": "Coercion that was applied" },
          "found":   { "type": "boolean", "description": "Whether a value existed at that pointer" },
          "value":   { "type": "string",  "nullable": true, "description": "The value as text, or null when absent" }
        }
      },

      "JsonKeyInfo": {
        "type": "object",
        "description": "How Burp classifies one key of an object",
        "properties": {
          "key":         { "type": "string",  "description": "Key name" },
          "type":        { "type": "string",  "enum": ["OBJECT", "ARRAY", "STRING", "NUMBER", "BOOLEAN", "NULL", "UNKNOWN", "MISSING"], "description": "Node kind" },
          "has_string":  { "type": "boolean", "description": "Whether Burp reads this key as a string" },
          "has_boolean": { "type": "boolean", "description": "Whether Burp reads this key as a boolean" },
          "has_number":  { "type": "boolean", "description": "Whether Burp reads this key as a number" },
          "has_array":   { "type": "boolean", "description": "Whether Burp reads this key as an array" },
          "has_object":  { "type": "boolean", "description": "Whether Burp reads this key as an object" },
          "value":       { "type": "string",  "nullable": true, "description": "Scalar value as text, or null for containers" }
        }
      },

      "JsonInspectResult": {
        "type": "object",
        "description": "Structural description of a document's root node",
        "properties": {
          "type":         { "type": "string",  "enum": ["OBJECT", "ARRAY", "STRING", "NUMBER", "BOOLEAN", "NULL", "UNKNOWN"], "description": "Root node kind" },
          "is_object":    { "type": "boolean" },
          "is_array":     { "type": "boolean" },
          "is_string":    { "type": "boolean" },
          "is_number":    { "type": "boolean" },
          "is_boolean":   { "type": "boolean" },
          "is_null":      { "type": "boolean" },
          "json_string":  { "type": "string",  "description": "The document as Burp re-serialises it" },
          "value":        { "type": "string",  "nullable": true, "description": "Scalar value when the root is not a container" },
          "array_length": { "type": "integer", "nullable": true, "description": "Element count when the root is an array" },
          "keys":         { "type": "array",   "nullable": true, "items": { "${'$'}ref": "#/components/schemas/JsonKeyInfo" }, "description": "Per-key typing when the root is an object" }
        }
      },

      "BytesSearchInput": {
        "type": "object",
        "required": ["data"],
        "properties": {
          "data":           { "type": "string",  "description": "Buffer to search, as UTF-8 text" },
          "needle":         { "type": "string",  "nullable": true, "description": "Literal to find. Supply this or `regex`, not both." },
          "regex":          { "type": "string",  "nullable": true, "description": "java.util.regex pattern to find. Supply this or `needle`, not both." },
          "case_sensitive": { "type": "boolean", "default": true, "description": "Applies to literal searches only" },
          "from":           { "type": "integer", "nullable": true, "description": "Start of the search range, inclusive. Set with `to`." },
          "to":             { "type": "integer", "nullable": true, "description": "End of the search range, exclusive. Set with `from`." }
        }
      },

      "BytesSearchResult": {
        "type": "object",
        "properties": {
          "index":          { "type": "integer", "description": "Offset of the first match, or -1 when absent" },
          "count":          { "type": "integer", "description": "Total number of matches" },
          "data_length":    { "type": "integer", "description": "Length of the buffer in bytes" },
          "searched_range": { "type": "string",  "description": "The half-open range actually searched" }
        }
      },

      "BytesSliceInput": {
        "type": "object",
        "required": ["data", "start", "end"],
        "properties": {
          "data":  { "type": "string",  "description": "Buffer to slice, as UTF-8 text" },
          "start": { "type": "integer", "description": "Start offset, inclusive" },
          "end":   { "type": "integer", "description": "End offset, exclusive" }
        }
      },

      "BytesAppendInput": {
        "type": "object",
        "required": ["data", "suffix"],
        "properties": {
          "data":   { "type": "string", "description": "Buffer to extend" },
          "suffix": { "type": "string", "description": "Text to append" }
        }
      },

      "BytesAllocInput": {
        "type": "object",
        "required": ["length"],
        "properties": {
          "length": { "type": "integer", "minimum": 0, "maximum": 1048576, "description": "Buffer length in bytes" },
          "fill":   { "type": "integer", "minimum": 0, "maximum": 255, "default": 0, "description": "Byte value to write at `at`" },
          "at":     { "type": "integer", "default": 0, "description": "Offset to write `fill` at" }
        }
      },

      "BytesInput": {
        "type": "object",
        "required": ["data"],
        "properties": {
          "data": { "type": "string", "description": "Buffer as UTF-8 text" }
        }
      },

      "BytesResult": {
        "type": "object",
        "properties": {
          "text":       { "type": "string",  "description": "Buffer decoded as text" },
          "length":     { "type": "integer", "description": "Length in bytes" },
          "base64":     { "type": "string",  "description": "Base64 of the raw bytes, for binary-safe transport" },
          "first_byte": { "type": "integer", "nullable": true, "description": "Value of byte 0, or null when empty" }
        }
      },

      "BytesInspectResult": {
        "type": "object",
        "properties": {
          "length":                  { "type": "integer", "description": "Length in bytes" },
          "base64":                  { "type": "string",  "description": "Base64 of the raw bytes" },
          "first_byte":              { "type": "integer", "nullable": true, "description": "Value of the first byte" },
          "last_byte":               { "type": "integer", "nullable": true, "description": "Value of the last byte" },
          "distinct_bytes":          { "type": "integer", "description": "Count of distinct byte values present" },
          "printable":               { "type": "boolean", "description": "Whether every byte is printable ASCII, tab, CR or LF" },
          "temp_file_backed_length": { "type": "integer", "description": "Length after a temp-file round trip, for very large buffers" }
        }
      },

      "RankInput": {
        "type": "object",
        "properties": {
          "limit":      { "type": "integer", "default": 100, "minimum": 1, "maximum": 1000, "description": "How many history entries to score" },
          "offset":     { "type": "integer", "default": 0, "description": "Pagination offset into history" },
          "algorithm":  { "type": "string",  "nullable": true, "enum": ["ANOMALY"], "description": "Ranking algorithm. Omit for Burp's default." },
          "scope_only": { "type": "boolean", "default": false, "description": "Score only in-scope entries" },
          "host":       { "type": "string",  "nullable": true, "description": "Restrict to a single hostname" }
        }
      },

      "RankedEntry": {
        "type": "object",
        "properties": {
          "rank":            { "type": "integer", "description": "Burp's interest score. Higher means more anomalous." },
          "url":             { "type": "string",  "description": "Request URL" },
          "method":          { "type": "string",  "nullable": true, "description": "HTTP method" },
          "status":          { "type": "integer", "nullable": true, "description": "Response status code" },
          "response_length": { "type": "integer", "description": "Response length in characters" }
        }
      },

      "RankResult": {
        "type": "object",
        "properties": {
          "algorithm":  { "type": "string",  "description": "Algorithm actually used" },
          "considered": { "type": "integer", "description": "How many exchanges were scored" },
          "ranked":     { "type": "array",   "items": { "${'$'}ref": "#/components/schemas/RankedEntry" }, "description": "Entries ordered by interest" }
        }
      },

      "ShellExecInput": {
        "type": "object",
        "description": "Execution request. Use `command` with /execute and `command_line` with /execute-raw.",
        "properties": {
          "command":            { "type": "array", "items": { "type": "string" }, "description": "Argument vector for /execute. No shell is involved." },
          "command_line":       { "type": "string", "nullable": true, "description": "Shell command line for /execute-raw" },
          "timeout_seconds":    { "type": "integer", "default": 30, "minimum": 1, "maximum": 600, "description": "Wall-clock limit" },
          "timeout_behavior":   { "type": "string", "enum": ["FAIL_ON_TIMEOUT", "ALLOW_TIMEOUT"], "default": "FAIL_ON_TIMEOUT", "description": "What to do when the limit is hit" },
          "stderr_behavior":    { "type": "string", "enum": ["MERGE", "DISCARD"], "default": "MERGE", "description": "Whether stderr joins the output" },
          "exit_code_behavior": { "type": "string", "enum": ["FAIL_ON_NON_ZERO", "ALLOW_NON_ZERO"], "default": "ALLOW_NON_ZERO", "description": "Whether a non-zero exit is an error" },
          "environment":        { "type": "object", "additionalProperties": { "type": "string" }, "description": "Extra environment variables for the child process" }
        }
      },

      "ShellStatusResult": {
        "type": "object",
        "properties": {
          "enabled":    { "type": "boolean", "description": "Whether the execution routes will run commands" },
          "enable_via": { "type": "string",  "description": "How to turn them on" },
          "warning":    { "type": "string",  "description": "Why they are off by default" }
        }
      },

      "ShellExecResult": {
        "type": "object",
        "properties": {
          "output":            { "type": "string",  "description": "Captured output, including stderr when merged" },
          "shell_interpreted": { "type": "boolean", "description": "True for /execute-raw, false for /execute" },
          "timeout_seconds":   { "type": "integer", "description": "Limit applied to this run" }
        }
      }
"""

# reburp endpoint reference

Generated from the OpenAPI spec (`GET /openapi.json`) of reburp 1.1.15. The live spec is the source of truth; refresh this file with `python3 .codex/skills/reburp-api/scripts/reburp.py endpoints > .codex/skills/reburp-api/references/endpoints.md` while Burp is running.

Notation: `*` marks a required field, `=x` is the default, `a|b` is an enum. `body:object` fields take a JSON object (sent as JSON) or a string.


## Status

- `GET /api/status` - Extension and Burp version status

## Proxy

- `GET /api/proxy/history` - List proxy HTTP history. Query: offset=0, limit=100, include_body=false, max_body=524288, host, method, mime_type, status_min, status_max, edited_only=false, has_response, scope_only=false, listener_port
- `GET /api/proxy/history/{index}` - Get proxy history item by index. Query: include_body=true
- `GET /api/proxy/history/search` - Search proxy HTTP history. Query: regex*, offset=0, limit=100, include_body=false, max_body=524288
- `PATCH /api/proxy/history/annotate` - Annotate proxy history items. Body: {regex*:string, note:string, highlight:NONE|RED|ORANGE|YELLOW|GREEN|CYAN|BLUE|PINK|MAGENTA|GRAY, scope_only:boolean=false, limit:integer=1000}
- `GET /api/proxy/history/response-search` - Search proxy history response bodies. Query: regex*, scope_only=false, offset=0, limit=100, max_snippets=3, context_chars=60
- `GET /api/proxy/websocket/history` - List proxy WebSocket history. Query: offset=0, limit=100
- `GET /api/proxy/websocket/history/search` - Search proxy WebSocket history. Query: regex*, offset=0, limit=100
- `GET /api/proxy/intercept` - Proxy intercept state is not readable
- `PUT /api/proxy/intercept` - Toggle proxy intercept. Body: {enabled*:boolean}
- `GET /api/proxy/intercept/rules` - List intercept rules
- `POST /api/proxy/intercept/rules/client` - Add client intercept rule. Body: {enabled:boolean=true, boolean_operator:and|or="or", match_type*:url|http_method|file_extension|content_type_header|status_code|request|mime_type|domain_name|protocol|cookie_name|cookie_value|any_header|body|param_name|param_value|listener_port, match_relationship*:matches|does_not_match|is_in_target_scope|contains_parameters|was_intercepted|was_modified|is_text, match_condition*:string}
- `DELETE /api/proxy/intercept/rules/client/{index}` - Delete client intercept rule
- `GET /api/proxy/match-replace` - List match-and-replace rules
- `POST /api/proxy/match-replace` - Create match-and-replace rule. Body: {id:integer, rule_type*:request_first_line|request_header|request_body|request_param_name|request_param_value|response_header|response_body, string_match:string, string_replace:string, is_simple_match:boolean=false, enabled:boolean=true, comment:string}
- `DELETE /api/proxy/match-replace` - Clear all match-and-replace rules
- `PUT /api/proxy/match-replace/{id}` - Update match-and-replace rule. Body: {id:integer, rule_type*:request_first_line|request_header|request_body|request_param_name|request_param_value|response_header|response_body, string_match:string, string_replace:string, is_simple_match:boolean=false, enabled:boolean=true, comment:string}
- `DELETE /api/proxy/match-replace/{id}` - Delete match-and-replace rule

## Site Map

- `GET /api/sitemap` - Browse site map. Query: prefix, include_body=false, offset=0, limit=100
- `GET /api/sitemap/search` - Search site map by regex. Query: regex*, include_body=false, offset=0, limit=100
- `POST /api/sitemap/add` - Add entry to site map. Body: {host*:string, port:integer=443, use_https:boolean=true, request*:string, response:string}
- `GET /api/sitemap/issues` - List scanner issues from site map. Query: prefix, offset=0, limit=100
- `GET /api/scope` - Check if URL is in scope. Query: url*
- `POST /api/scope` - Add URL to scope. Body: {url*:string}
- `DELETE /api/scope` - Remove URL from scope. Body: {url*:string}

## HTTP

- `POST /api/http/send` - Send HTTP/1.1 request. Body: {host*:string, port:integer=443, use_https:boolean=true, request:string, method:string, path:string, headers:object, body:object, session_id:string, redirect_mode:ALWAYS|NEVER|SAME_HOST|IN_SCOPE, timeout_ms:integer}
- `POST /api/http/send-with-auth` - Send with automatic JWT re-auth. Body: {auth*:object, request*:object, inject_as:string="Authorization: Bearer {token}", retry_on:[integer]=[401, 403]}
- `POST /api/http/send-batch` - Send multiple HTTP/1.1 requests. Body: {requests*:[object]}
- `POST /api/http/send/http2` - Send HTTP/2 request. Body: {host*:string, port:integer=443, use_https:boolean=true, pseudo_headers*:object, headers:object, body:string}
- `POST /api/http/parse/request` - Parse raw HTTP request. Body: {request*:string, include_body:boolean=true}
- `POST /api/http/parse/response` - Parse raw HTTP response. Body: {response*:string, include_body:boolean=true}
- `POST /api/http/diff` - Diff two HTTP requests. Body: {request_a*:string, request_b*:string}
- `POST /api/http/params` - Extract parameters from HTTP request. Body: {request*:string}
- `POST /api/http/reflect` - Find reflected parameters. Body: {request*:string, response*:string}
- `POST /api/http/insertion-points` - Get insertion point offsets. Body: {request*:string, mode:REPLACE_BASE_PARAMETER_VALUE_WITH_OFFSETS|APPEND_OFFSETS_TO_BASE_PARAMETER_VALUE="REPLACE_BASE_PARAMETER_VALUE_WITH_OFFSETS"}
- `POST /api/http/request/mutate` - Mutate HTTP request. Body: {request*:string, service_host:string, service_port:integer, service_use_https:boolean, method:string, path:string, body:string, toggle_method:boolean, add_headers:object, remove_headers:[string], update_headers:object, add_params:[object], remove_params:[object], update_params:[object]}
- `POST /api/http/response/mutate` - Mutate HTTP response. Body: {response*:string, status_code:integer, body:string, add_headers:object, remove_headers:[string], update_headers:object}
- `GET /api/cookies` - List cookie jar. Query: domain, scope_only=false, include_values=true, include_subdomains=false
- `POST /api/cookies` - Set a cookie in the jar. Body: {name*:string, value*:string, domain*:string, path:string, expires_at:string}
- `DELETE /api/cookies/{name}` - Delete cookie from jar. Query: domain
- `POST /api/repeater` - Send request to Repeater. Body: {host*:string, port:integer=443, use_https:boolean=true, request*:string, tab_name:string}
- `POST /api/intruder` - Send request to Intruder. Body: {host*:string, port:integer=443, use_https:boolean=true, request*:string, tab_name:string}
- `POST /api/comparer` - Send items to Comparer. Body: {items*:[string]}
- `POST /api/repeater/send` - Send request to Repeater. Body: {raw_request*:string, tab_name:string}
- `POST /api/intruder/send` - Send request to Intruder. Body: {raw_request*:string, tab_name:string}
- `DELETE /api/repeater/tabs` - Clear Repeater tabs
- `POST /api/http/jwt/decode` - Decode a JWT. Body: {token*:string}
- `POST /api/http/auth-diff` - Diff a request with and without authentication. Body: {host*:string, port:integer=443, https:boolean=true, method:string="GET", path*:string, headers:object, body:object, auth_header*:string, second_auth_header:string}
- `POST /api/http/fuzz` - Fuzz a path template against a wordlist. Body: {host*:string, port:integer=443, https:boolean=true, method:string="GET", path_template*:string, wordlist*:[string], body:object, headers:object, filter_status:[integer], exclude_body_size:integer, exclude_body_contains:string, include_body_snippet:boolean=false, concurrency:integer=5, timeout_ms:integer}
- `POST /api/http/rate-test` - Test for rate limiting. Body: {host*:string, port:integer=443, https:boolean=true, method:string="POST", path*:string, headers:object, body:object, count:integer=30, concurrency:integer=10}

## Scanner

- `GET /api/scanner/issues` - List scan issues (Pro only). Query: offset=0, limit=100, severity, confidence, host, name_contains, prefix, include_requests=false, include_request_body=false
- `POST /api/scanner/audit` - Start audit with built-in configuration (Pro only). Body: {configuration:ACTIVE|PASSIVE|LEGACY_ACTIVE_AUDIT_CHECKS|LEGACY_PASSIVE_AUDIT_CHECKS="ACTIVE", host:string, port:integer=443, use_https:boolean=true, requests:[string]}
- `POST /api/scanner/audit/from-history` - Audit a proxy history item. Body: {index*:integer, configuration:ACTIVE|PASSIVE|LEGACY_ACTIVE|LEGACY_PASSIVE|LEGACY_ACTIVE_AUDIT_CHECKS|LEGACY_PASSIVE_AUDIT_CHECKS="ACTIVE"}
- `POST /api/scanner/audit/mode` - Start audit by mode (Pro only). Body: {mode:ACTIVE|PASSIVE|LEGACY_ACTIVE_AUDIT_CHECKS|LEGACY_PASSIVE_AUDIT_CHECKS="ACTIVE", host:string, port:integer=443, use_https:boolean=true, requests:[string]}
- `POST /api/scanner/crawl` - Start crawl (Pro only). Body: {seed_urls*:[string]}
- `GET /api/scanner/tasks/{id}` - Get scan task status
- `DELETE /api/scanner/tasks/{id}` - Delete scan task
- `POST /api/scanner/report` - Generate scan report. Body: {task_id:string, all_issues:boolean=false, format:HTML|XML="HTML", path*:string}
- `POST /api/scanner/report/download` - Download scan report as base64. Body: {format*:HTML|XML, prefix:string}
- `POST /api/scanner/bchecks/import` - Import BCheck script (Pro only). Body: {source*:string, enabled:boolean=true}
- `POST /api/scanner/bambda/import` - Import Bambda script. Body: {source*:string, enabled:boolean=true}
- `GET /api/scanner/bchecks/config` - Export scanner config (BChecks)
- `GET /api/scanner/bambda/config` - Export user config (Bambdas)
- `GET /api/scanner/tasks/{id}/detail` - Progress detail for a running audit
- `POST /api/scanner/tasks/{id}/add` - Feed an extra exchange into a running audit. Body: {request*:string, response:string, host*:string, port:integer=443, secure:boolean=true}
- `POST /api/scanner/crawl/preview` - Validate crawl seed URLs without starting a crawl. Body: {seed_urls*:[string]}
- `POST /api/scanner/issue-definition` - Build an issue definition and see how Burp reads it. Body: {name*:string, background*:string, remediation*:string, typical_severity:HIGH|MEDIUM|LOW|INFORMATION|FALSE_POSITIVE="INFORMATION"}
- `POST /api/scanner/issues/consolidate` - Group issues into a single audit result. Body: {severities:[HIGH|MEDIUM|LOW|INFORMATION|FALSE_POSITIVE]}

## Config

- `GET /api/config/project` - Get project configuration. Query: path, section
- `PUT /api/config/project` - Set project configuration. Body: {json*:object}
- `GET /api/config/user` - Get user configuration. Query: path, section
- `PUT /api/config/user` - Set user configuration. Body: {json*:object}
- `GET /api/config/tasks` - Get task engine state
- `PUT /api/config/tasks` - Set task engine state. Body: {running*:boolean}
- `GET /api/extensions` - List loaded extensions
- `POST /api/burp/shutdown` - Shutdown Burp Suite

## Collaborator

- `POST /api/collaborator/generate` - Generate Collaborator payload (Pro only). Body: {options:[DNS|HTTP|SMTP], custom_data:string}
- `GET /api/collaborator/poll/{secretKey}` - Poll Collaborator interactions (Pro only)
- `GET /api/collaborator/server` - The Collaborator server this project uses
- `POST /api/collaborator/generate/default` - Generate a payload from the project's default generator. Body: {options:[string]}
- `GET /api/collaborator/interactions/{secretKey}` - Poll interactions with protocol detail. Query: payload, interaction_id

## Utilities

- `POST /api/utils/url/encode` - URL encode string. Body: {value*:string}
- `POST /api/utils/url/decode` - URL decode string. Body: {value*:string}
- `POST /api/utils/url/encode-mode` - URL encode with mode. Body: {value*:string, encoding:JAVA_DEFAULT|KEY_CHARACTERS|ALL_CHARACTERS|ALL_CHARACTERS_UNICODE="ALL_CHARACTERS"}
- `POST /api/utils/base64/encode` - Base64 encode. Body: {value*:string}
- `POST /api/utils/base64/decode` - Base64 decode. Body: {value*:string}
- `POST /api/utils/base64/encode-options` - Base64 encode with options. Body: {value*:string, url_safe:boolean=false, no_padding:boolean=false}
- `POST /api/utils/base64/decode-options` - Base64 decode with options. Body: {value*:string, url_safe:boolean=false}
- `POST /api/utils/html/encode` - HTML encode. Body: {value*:string, encoding:STANDARD|ALL_CHARACTERS|ALL_CHARACTERS_DECIMAL|ALL_CHARACTERS_HEX="STANDARD"}
- `POST /api/utils/html/decode` - HTML decode. Body: {value*:string}
- `POST /api/utils/string/to-hex` - Convert ASCII string to hex. Body: {value*:string}
- `POST /api/utils/string/from-hex` - Convert hex to ASCII string. Body: {value*:string}
- `POST /api/utils/hash` - Compute hash (legacy). Body: {algorithm:MD5|SHA1|SHA256|SHA512="SHA256", value*:string}
- `POST /api/utils/digest` - Compute cryptographic digest. Body: {algorithm*:SHA_256|SHA_512|MD5|SHA3_256|SHA3_512|BLAKE2B_256|BLAKE3_256|KECCAK_256|SHA_1|SHA_384|RIPEMD_160|WHIRLPOOL|TIGER|SM3, value*:string}
- `POST /api/utils/jwt/decode` - Decode JWT (no verification). Body: {value*:string}
- `GET /api/utils/random` - Generate random string. Query: length=16, charset="ALPHANUMERIC"
- `POST /api/utils/decompress` - Decompress base64-encoded data. Body: {base64*:string, encoding:GZIP|DEFLATE|BROTLI|IDENTITY|RAW="GZIP"}
- `POST /api/utils/compress` - Compress string and return base64. Body: {value*:string, encoding:GZIP|DEFLATE="GZIP"}
- `POST /api/utils/response/keywords` - Analyze keyword variance across responses. Body: {responses*:[string], keywords*:[string]}
- `POST /api/utils/response/variations` - Analyze attribute variance across responses. Body: {responses*:[string]}
- `POST /api/utils/jwt/encode` - Encode and sign a JWT. Body: {header_json:string, payload_json*:string, secret:string, algorithm:HS256|HS384|HS512|none="HS256"}
- `POST /api/utils/headers/analyze` - Analyze HTTP security headers. Body: {headers:object, raw_response:string}
- `GET /api/utils/payloads` - Get security testing payloads. Query: category="xss"

## Issues

- `POST /api/issues` - Create custom audit issue. Body: {name*:string, detail*:string, remediation:string, base_url*:string, severity:HIGH|MEDIUM|LOW|INFORMATION="INFORMATION", confidence:CERTAIN|FIRM|TENTATIVE="CERTAIN", background:string, remediation_background:string, typical_severity:string, target_host:string, target_port:integer=443, target_use_https:boolean=true, http_request:string, http_response:string}

## Persistence

- `GET /api/preferences` - Get all preferences by type. Query: type*
- `PUT /api/preferences/{key}` - Set preference. Body: {type*:string|boolean|byte|short|int|long, value*:string}
- `DELETE /api/preferences/{key}` - Delete preference. Query: type*

## Organizer

- `GET /api/organizer/items` - List organizer items. Query: status
- `POST /api/organizer/send` - Send request to Organizer. Body: {host*:string, port:integer=443, use_https:boolean=true, request:string, method:string, path:string, headers:object, body:object, session_id:string, redirect_mode:ALWAYS|NEVER|SAME_HOST|IN_SCOPE, timeout_ms:integer}

## AI

- `GET /api/ai/status` - Check if AI is enabled
- `POST /api/ai/chat` - Chat with Burp AI. Body: {messages*:[object], system_prompt:string, temperature:number}

## Scope

- `GET /api/scope/rules` - List scope rules
- `GET /api/scope/check` - Check if URL is in scope. Query: url*
- `POST /api/scope/include` - Add URL to scope. Body: {url*:string}
- `POST /api/scope/exclude` - Remove URL from scope. Body: {url*:string}

## Sessions

- `GET /api/sessions/rules` - List session handling rules
- `POST /api/sessions/rules/add-header` - Add header session rule (not supported by Burp)
- `DELETE /api/sessions/rules/{index}` - Delete session rule

## Engagement

- `POST /api/engagement/csrf-poc` - Generate CSRF proof-of-concept. Body: {request*:string, host*:string, port:integer=443, use_https:boolean=true, format:HTML_FORM|FETCH_JS|AUTO_SUBMIT="AUTO_SUBMIT"}
- `POST /api/engagement/find-references` - Find URL references in proxy/sitemap. Body: {query*:string, search_proxy:boolean=true, search_sitemap:boolean=true, scope_only:boolean=false, limit:integer=200}
- `POST /api/engagement/analyze-target` - Analyze target attack surface. Body: {host:string, scope_only:boolean=false}
- `POST /api/engagement/discover-content` - Discover hidden content via wordlist probing. Body: {host*:string, port:integer=443, use_https:boolean=true, base_path:string="/", wordlist*:[string], method:GET|HEAD|POST|OPTIONS="GET", headers:object={}, found_status_codes:[integer]=[200, 201, 204, 301, 302, 307, 401, 403, 405], concurrency:integer=5}
- `POST /api/engagement/send-to-decoder` - Send bytes to Burp Decoder tab. Body: {base64*:string}

## Activity Log

- `GET /api/log` - Query the reburp call log. Query: limit=100, offset=0, newest_first=true, method, status, path_contains, session_id
- `DELETE /api/log` - Clear the reburp call log
- `POST /api/session` - Start a labelled reburp session. Body: {label*:string, host:string}
- `GET /api/sessions` - List reburp sessions

## Bambda

- `POST /api/bambda/import` - Import a Bambda script. Body: {name*:string, source*:string}
- `POST /api/bambda/generate-chain` - Generate a Bambda request chain. Body: {name*:string, host*:string, port:integer=443, https:boolean=true, steps*:[object]}

## Numbers

- `POST /api/utils/number/convert` - Convert between binary, octal, decimal and hex. Body: {value*:string, from*:BINARY|OCTAL|DECIMAL|HEX, to*:BINARY|OCTAL|DECIMAL|HEX}
- `POST /api/utils/number/convert-radix` - Convert a value into an arbitrary radix. Body: {value*:string, from*:BINARY|OCTAL|DECIMAL|HEX, radix*:integer}

## JSON

- `POST /api/utils/json/validate` - Check whether a document is valid JSON. Body: {json*:string}
- `POST /api/utils/json/read` - Read a value at a pointer. Body: {json*:string, pointer*:string, type:STRING|BOOLEAN|LONG|DOUBLE|RAW="STRING", value:string}
- `POST /api/utils/json/add` - Insert a value at a pointer. Body: {json*:string, pointer*:string, type:STRING|BOOLEAN|LONG|DOUBLE|RAW="STRING", value:string}
- `POST /api/utils/json/update` - Replace a value at a pointer. Body: {json*:string, pointer*:string, type:STRING|BOOLEAN|LONG|DOUBLE|RAW="STRING", value:string}
- `POST /api/utils/json/remove` - Delete the value at a pointer. Body: {json*:string, pointer*:string, type:STRING|BOOLEAN|LONG|DOUBLE|RAW="STRING", value:string}
- `POST /api/utils/json/inspect` - Describe a document's structure and types. Body: {json*:string}
- `POST /api/utils/json/normalize` - Round-trip a document through Burp's node builders. Body: {json*:string}

## Bytes

- `POST /api/utils/bytes/search` - Find and count occurrences in a byte buffer. Body: {data*:string, needle:string, regex:string, case_sensitive:boolean=true, from:integer, to:integer}
- `POST /api/utils/bytes/slice` - Extract a byte range. Body: {data*:string, start*:integer, end*:integer}
- `POST /api/utils/bytes/append` - Concatenate a suffix onto a buffer. Body: {data*:string, suffix*:string}
- `POST /api/utils/bytes/alloc` - Allocate a fixed-length buffer. Body: {length*:integer, fill:integer=0, at:integer=0}
- `POST /api/utils/bytes/inspect` - Summarise a byte buffer. Body: {data*:string}
- `POST /api/utils/bytes/convert` - Round-trip text through Burp's byte codec. Body: {data*:string}

## Ranking

- `POST /api/utils/rank` - Rank proxy history by how anomalous each exchange looks. Body: {limit:integer=100, offset:integer=0, algorithm:ANOMALY, scope_only:boolean=false, host:string, max_scored:integer=2000}

## Shell

- `GET /api/utils/shell/status` - Report whether command execution is enabled
- `POST /api/utils/shell/execute` - Execute an argument vector (disabled by default). Body: {command:[string], command_line:string, timeout_seconds:integer=30, timeout_behavior:FAIL_ON_TIMEOUT|ALLOW_TIMEOUT="FAIL_ON_TIMEOUT", stderr_behavior:MERGE|DISCARD="MERGE", exit_code_behavior:FAIL_ON_NON_ZERO|ALLOW_NON_ZERO="ALLOW_NON_ZERO", environment:object}
- `POST /api/utils/shell/execute-raw` - Execute a shell command line (disabled by default). Body: {command:[string], command_line:string, timeout_seconds:integer=30, timeout_behavior:FAIL_ON_TIMEOUT|ALLOW_TIMEOUT="FAIL_ON_TIMEOUT", stderr_behavior:MERGE|DISCARD="MERGE", exit_code_behavior:FAIL_ON_NON_ZERO|ALLOW_NON_ZERO="ALLOW_NON_ZERO", environment:object}

## Extension Data

- `GET /api/extension-data` - List every key stored at the root of the extension data tree
- `GET /api/extension-data/keys/{path}` - List every key stored at a nested child object path
- `GET /api/extension-data/value` - Read one typed value from the extension data tree. Query: path="", type*, key*
- `PUT /api/extension-data/value` - Write one typed value into the extension data tree. Body: {path:string="", type*:object, key*:string, value:string, values:[string], request:string, response:string, pairs:[object]}
- `DELETE /api/extension-data/value` - Delete one typed value from the extension data tree. Query: path="", type*, key*
- `POST /api/extension-data/child` - Create a child object at a path. Body: {path*:string}
- `DELETE /api/extension-data/child` - Delete a child object and everything below it. Query: path*

## Logging

- `POST /api/logging/output` - Append a line to this extension's output tab. Body: {message*:string, stack_trace:string}
- `POST /api/logging/error` - Append a line to this extension's error tab. Body: {message*:string, stack_trace:string}
- `POST /api/logging/event` - Raise an entry in Burp's suite-wide event log. Body: {message*:string, level:DEBUG|INFO|ERROR|CRITICAL="INFO"}
- `POST /api/logging/stream` - Write directly to the output or error stream. Body: {message*:string, stream:OUTPUT|ERROR="OUTPUT", newline:boolean=true}

## Meta

- `GET /api/meta/version` - Burp's version, split into components
- `GET /api/meta/extension` - How Burp loaded this extension
- `GET /api/meta/enums` - Enum vocabularies accepted elsewhere in this API
- `POST /api/meta/extension/unload` - Unload this extension. Body: {confirm*:boolean=false}

## Messages

- `POST /api/http/message/inspect/request` - Parse a raw request and report what Burp sees in it. Body: {raw:string, url:string, host:string, port:integer=443, secure:boolean=true, header_name:string, parameter_name:string, parameter_type:URL|BODY|COOKIE|XML|XML_ATTRIBUTE|MULTIPART_ATTRIBUTE|JSON}
- `POST /api/http/message/inspect/response` - Parse a raw response and report status, MIME typing and cookies. Body: {raw*:string, keywords:[string], header_name:string, cookie_name:string}
- `POST /api/http/message/request/headers` - Add, replace or delete request headers. Body: {raw*:string, host:string, port:integer=443, secure:boolean=true, set:object, add:[object], update:[object], remove:[string], apply_default_headers:boolean=false}
- `POST /api/http/message/request/parameters` - Add, replace or delete URL, body and cookie parameters. Body: {raw*:string, host:string, port:integer=443, secure:boolean=true, set:object, add:[object], update:[object], remove:[object]}
- `POST /api/http/message/request/service` - Retarget a request at a different host, port or scheme. Body: {raw*:string, host*:string, port:integer=443, secure:boolean=true}
- `POST /api/http/message/response/edit` - Rewrite a response status line and headers. Body: {raw*:string, http_version:string, reason_phrase:string, add:[object], update:[object], remove:[string]}
- `POST /api/http/message/markers` - Apply markers and annotations to a request, a response or a pair. Body: {request:string, response:string, request_markers:[object], response_markers:[object], notes:string, highlight_color:NONE|RED|ORANGE|YELLOW|GREEN|CYAN|BLUE|PINK|MAGENTA|GRAY}
- `POST /api/http/message/timing` - Send a request and report Burp's timing data. Body: {raw*:string, host*:string, port:integer=443, secure:boolean=true, http_mode:AUTO|HTTP_1|HTTP_2|HTTP_2_IGNORE_ALPN, connection_id:string, server_name_indicator:string, verify_upstream_tls:boolean=false}

## Events

- `GET /api/events/observers` - List the event observers and whether each one is registered
- `POST /api/events/observers/{name}/enable` - Register the named observer with Burp
- `POST /api/events/observers/{name}/disable` - Deregister the named observer
- `GET /api/events` - Read the buffered events captured by the enabled observers. Query: offset=0, limit=100, observer
- `DELETE /api/events` - Discard every buffered event

## WebSocket Client

- `GET /api/websocket` - List active WebSocket connections
- `POST /api/websocket/connect` - Open WebSocket connection. Body: {host*:string, port:integer=443, use_https:boolean=true, path:string="/"}
- `POST /api/websocket/{id}/send-text` - Send text frame. Body: {message*:string}
- `POST /api/websocket/{id}/send-binary` - Send binary frame. Body: {base64*:string}
- `GET /api/websocket/{id}/messages` - Get received messages. Query: clear=false
- `DELETE /api/websocket/{id}` - Close WebSocket connection

## Request Engine

- `POST /api/http/engine` - Create a request execution engine. Body: {name:string, concurrent_request_limit:integer, throttle_ms:integer, max_retries:integer, resource_pool_name:string, default_pool:boolean}
- `GET /api/http/engine` - List engine and execution handles
- `POST /api/http/engine/{id}/queue` - Queue requests onto an engine. Body: {requests*:[object]}
- `POST /api/http/engine/{id}/send` - Start the engine (send all queued requests). Body: {timeout_ms:integer}
- `POST /api/http/engine/execution/{id}/queue` - Queue more requests onto a running execution. Body: {requests*:[object]}
- `GET /api/http/engine/execution/{id}/stats` - Live execution statistics
- `GET /api/http/engine/execution/{id}/finished` - Whether the execution has finished
- `POST /api/http/engine/execution/{id}/pause` - Pause the execution
- `POST /api/http/engine/execution/{id}/resume` - Resume the execution
- `POST /api/http/engine/execution/{id}/cancel` - Cancel the execution
- `POST /api/http/engine/execution/{id}/await` - Wait (bounded) for completion and collect results. Body: {timeout_ms:integer=30000, include_body:boolean=true}
- `DELETE /api/http/engine/execution/{id}` - Discard an execution handle
- `DELETE /api/http/engine/{id}` - Discard an engine handle

## UI

- `GET /api/ui/screenshot` - Screenshot a Burp window, or one element in it, as a PNG. Query: window, id
- `GET /api/ui/tabs` - List Burp's top-level tabs, the nested tab bars showing under them, and what is selected
- `POST /api/ui/select-tab` - Select a tab by name or index, in Burp's main bar or any nested tab bar. Body: {name:string, index:integer}
- `POST /api/ui/click-button` - Click a visible button by its label or tooltip. Body: {text*:string}
- `POST /api/ui/select-row` - Select a row in the current tab's main table. Body: {index:integer, last:boolean=false}
- `GET /api/ui/windows` - List the showing windows: the suite frame, dialogs and popups
- `GET /api/ui/elements` - Inventory the visible elements of a window with stable ids. Query: window="w0", text, type, all=false, hidden=false, limit=500
- `POST /api/ui/click` - Click any element by id, or a point in a window. Body: {id:string, x:integer, y:integer, window:string, button:left|middle|right="left", count:integer=1, modifiers:[shift|ctrl|alt|meta]=[]}
- `POST /api/ui/type` - Type into a text field, text area or editable combo box. Body: {id*:string, text*:string, replace:boolean=true, enter:boolean=false}
- `POST /api/ui/select` - Select an entry in a combo box, list, tab bar, table or tree. Body: {id*:string, text:string, index:integer}
- `POST /api/ui/key` - Send a key press to an element, or to whatever has focus. Body: {key*:string, id:string, modifiers:[shift|ctrl|alt|meta]=[]}

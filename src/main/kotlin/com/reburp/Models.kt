package com.reburp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ── Common ────────────────────────────────────────────────────────────────────

@Serializable data class ErrorResponse(val error: String)
@Serializable data class MessageResponse(val message: String)
@Serializable data class StringResult(val result: String)

// ── Status ────────────────────────────────────────────────────────────────────

@Serializable
data class StatusResponse(
    val extension: String,
    val version: String,
    val edition: String,
    val port: Int,
    val docs_url: String,
    val project_name: String,
    val project_id: String,
    val command_line_args: List<String>
)

// ── Proxy / HTTP History ──────────────────────────────────────────────────────

@Serializable
data class HttpEntryDto(
    val url: String,
    val method: String?,
    val status: Int?,
    val request_length: Int,
    val response_length: Int,
    val notes: String,
    val highlight: String?,
    val request: String?,
    val response: String?
)

@Serializable
data class WsEntryDto(
    val direction: String,
    val payload: String?,
    val notes: String,
    /** Burp's identifier for the socket this message belongs to */
    val websocket_id: Int? = null,
    /** Payload after Burp's match/replace rules, when it differs from `payload` */
    val edited_payload: String? = null,
    /** URL of the HTTP request that opened this socket */
    val upgrade_request_url: String? = null
)

@Serializable
data class AnnotateRequest(
    val regex: String,
    val note: String = "",
    val highlight: String? = null,
    val scope_only: Boolean = false,
    val limit: Int = 100
)

@Serializable data class InterceptRequest(val enabled: Boolean)

// ── Scope ─────────────────────────────────────────────────────────────────────

@Serializable data class ScopeResult(val url: String, val in_scope: Boolean)
@Serializable data class ScopeRequest(val url: String)

// ── HTTP Send ─────────────────────────────────────────────────────────────────

@Serializable
data class SendHttpRequest(
    val host: String,
    val port: Int = 443,
    val use_https: Boolean = true,
    // ── Raw format (existing) ──────────────────────────────────────────────────
    // Full HTTP request string with \r\n separators, e.g.:
    //   "GET /api/me/ HTTP/1.1\r\nHost: example.com\r\n\r\n"
    val request: String? = null,
    // ── Structured format (preferred for agents) ───────────────────────────────
    // Provide method + path + optional headers map + optional body object/string.
    // Host and Content-Type/Content-Length are injected automatically.
    val method: String? = null,
    val path: String? = null,
    val headers: Map<String, String>? = null,
    val body: JsonElement? = null,
    // ── Options ────────────────────────────────────────────────────────────────
    val redirect_mode: String? = null,
    val timeout_ms: Long? = null,
    /** Optional session label/id to tag this request in the activity log */
    val session_id: String? = null
)

@Serializable
data class SendHttp2Request(
    val host: String,
    val port: Int = 443,
    val use_https: Boolean = true,
    val pseudo_headers: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String = ""
)

@Serializable data class HttpSendResponse(val request: String, val response: String?, val status: Int? = null, val ai_notes: String? = null)

// ── Repeater / Intruder / Comparer ───────────────────────────────────────────

@Serializable
data class SendToToolRequest(
    val host: String,
    val port: Int = 443,
    val use_https: Boolean = true,
    val request: String,
    val tab_name: String? = null
)

@Serializable data class ComparerRequest(val items: List<String>)

// ── Parse ─────────────────────────────────────────────────────────────────────

@Serializable data class ParseRequestInput(val request: String, val include_body: Boolean = true)

@Serializable
data class ParsedRequestDto(
    val method: String?,
    val path: String?,
    val url: String?,
    val headers: List<HeaderDto>,
    val parameters: List<ParamDto>,
    val body: String?,
    val body_length: Int
)

@Serializable data class ParseResponseInput(val response: String, val include_body: Boolean = true)

@Serializable
data class ParsedResponseDto(
    val status_code: Int,
    val headers: List<HeaderDto>,
    val body: String?,
    val body_length: Int
)

@Serializable data class HeaderDto(val name: String, val value: String)
@Serializable data class ParamDto(val type: String, val name: String, val value: String)
@Serializable data class DiffInput(val request_a: String, val request_b: String)
@Serializable data class ExtractParamsInput(val request: String)
@Serializable data class FindReflectedInput(val request: String, val response: String)

@Serializable
data class InsertionPointsInput(
    val request: String,
    val mode: String = "ALL_PARAMETERS"
)

@Serializable data class InsertionPointDto(val start: Int, val end: Int)

// ── Cookies ───────────────────────────────────────────────────────────────────

@Serializable
data class CookieDto(
    val name: String,
    val value: String,
    val domain: String,
    val path: String?,
    val expires_at: String?
)

// ── Scanner (Pro) ─────────────────────────────────────────────────────────────

@Serializable
data class StartAuditRequest(
    val configuration: String = "CRAWL_AND_AUDIT_EVERYTHING_FAST",
    val host: String? = null,
    val port: Int = 443,
    val use_https: Boolean = true,
    val requests: List<String> = emptyList()
)

@Serializable
data class StartAuditModeRequest(
    val mode: String = "ACTIVE",
    val host: String? = null,
    val port: Int = 443,
    val use_https: Boolean = true,
    val requests: List<String> = emptyList()
)

@Serializable data class StartCrawlRequest(val seed_urls: List<String>)

@Serializable
data class ScanTaskDto(
    val id: String,
    val status: String,
    val request_count: Int,
    val error_count: Int,
    val issue_count: Int?
)

@Serializable
data class ScanReportRequest(
    val task_id: String? = null,
    val all_issues: Boolean = false,
    val format: String = "HTML",
    val path: String
)

@Serializable
data class ScanIssueDto(
    val name: String?,
    val detail: String?,
    val remediation: String?,
    val base_url: String?,
    val severity: String,
    val confidence: String
)

@Serializable
data class ScanIssueFull(
    val name: String?,
    val detail: String?,
    val remediation: String?,
    val base_url: String?,
    /** Severity: HIGH, MEDIUM, LOW, INFORMATION, FALSE_POSITIVE */
    val severity: String,
    /** Confidence: CERTAIN, FIRM, TENTATIVE */
    val confidence: String,
    val host: String?,
    val port: Int?,
    /** Background description from the issue definition */
    val background: String?,
    /** Typical severity for this issue type */
    val typical_severity: String?,
    /** Unique type index for this issue class */
    val type_index: Int?,
    /** HTTP request/response pairs that triggered this finding */
    val flagged_requests: List<HttpEntryDto>
)

// ── Config ────────────────────────────────────────────────────────────────────

@Serializable data class SetConfigRequest(val json: String)
@Serializable data class TaskEngineStateDto(val state: String)
@Serializable data class SetTaskEngineStateRequest(val running: Boolean)

// ── Collaborator (Pro) ────────────────────────────────────────────────────────

@Serializable
data class CollaboratorGenerateRequest(
    val options: List<String> = emptyList(),
    val custom_data: String? = null
)

@Serializable
data class CollaboratorGeneratedDto(
    val payload: String,
    val interaction_id: String,
    val secret_key: String
)

@Serializable
data class CollaboratorInteractionDto(
    val id: String,
    val type: String,
    val time: String,
    val client_ip: String,
    val client_port: Int,
    val custom_data: String?
)

// ── Utility ───────────────────────────────────────────────────────────────────

@Serializable data class StringInput(val value: String)
@Serializable data class HashInput(val algorithm: String = "SHA256", val value: String)
@Serializable data class DecompressInput(val base64: String, val encoding: String = "GZIP")
@Serializable data class UrlEncodeInput(val value: String, val encoding: String = "ALL_CHARACTERS")
@Serializable data class Base64EncodeInput(val value: String, val url_safe: Boolean = false, val no_padding: Boolean = false)
@Serializable data class Base64DecodeInput(val value: String, val url_safe: Boolean = false)
@Serializable data class HtmlEncodeInput(val value: String, val encoding: String = "STANDARD")
@Serializable data class DigestInput(val algorithm: String, val value: String)
@Serializable data class CompressInput(val value: String, val encoding: String = "GZIP")
@Serializable data class ResponseKeywordsInput(val responses: List<String>, val keywords: List<String>)
@Serializable data class ResponseKeywordsResult(val variant: List<String>, val invariant: List<String>)
@Serializable data class ResponseVariationsInput(val responses: List<String>)
@Serializable data class ResponseVariationsResult(val variant: List<String>, val invariant: List<String>)

// ── Issues ────────────────────────────────────────────────────────────────────

@Serializable
data class CreateIssueRequest(
    val name: String,
    val detail: String,
    val remediation: String? = null,
    val base_url: String,
    val severity: String = "INFORMATION",
    val confidence: String = "CERTAIN",
    val background: String? = null,
    val remediation_background: String? = null,
    val typical_severity: String? = null,
    val target_host: String? = null,
    val target_port: Int = 443,
    val target_use_https: Boolean = true,
    val http_request: String? = null,
    val http_response: String? = null
)

// ── HTTP Batch / Cookies ──────────────────────────────────────────────────────

@Serializable data class SetCookieRequest(
    val name: String,
    val value: String,
    val domain: String,
    val path: String? = null,
    val expires_at: String? = null
)
@Serializable data class SendBatchRequest(val requests: List<SendHttpRequest>)

// ── Scanner Scripts ───────────────────────────────────────────────────────────

@Serializable data class ImportScriptRequest(val source: String, val enabled: Boolean = true)

@Serializable
data class ImportResultDto(
    /** LOADED_WITHOUT_ERRORS or LOADED_WITH_ERRORS */
    val status: String,
    val success: Boolean,
    /** Compile/parse errors - empty on success */
    val errors: List<String>
)

// ── Organizer ─────────────────────────────────────────────────────────────────

@Serializable
data class OrganizerItemDto(
    val id: Int,
    val status: String,
    val url: String?,
    val method: String?,
    val status_code: Int?,
    val request_length: Int,
    val response_length: Int
)

// ── AI ────────────────────────────────────────────────────────────────────────

@Serializable data class AiMessage(val role: String, val content: String)
@Serializable data class AiChatRequest(
    val messages: List<AiMessage>,
    val system_prompt: String? = null,
    /** Sampling temperature. Omit to use Burp's default; 0.0 is deterministic, 1.0 is most varied. */
    val temperature: Double? = null
)
@Serializable data class AiChatResponse(val response: String)
@Serializable data class AiStatusResponse(val enabled: Boolean)

// ── Persistence ───────────────────────────────────────────────────────────────

@Serializable data class PersistenceValueRequest(val type: String, val value: String)

// ── WebSocket Client ──────────────────────────────────────────────────────────

@Serializable
data class WsConnectRequest(
    val host: String,
    val port: Int = 443,
    val use_https: Boolean = true,
    val path: String = "/"
)

@Serializable
data class WsConnectionInfoDto(
    val id: String,
    val host: String,
    val port: Int,
    val path: String,
    val secure: Boolean,
    val upgrade_status: Int?
)

@Serializable
data class WsClientMessageDto(
    /** Direction: INCOMING (from server) or OUTGOING (sent by us) */
    val direction: String,
    /** Message type: TEXT or BINARY */
    val type: String,
    /** Text payload (present when type=TEXT) */
    val payload: String?,
    /** Base64-encoded payload (present when type=BINARY) */
    val payload_base64: String?
)

@Serializable data class WsSendRequest(val message: String)
@Serializable data class WsSendBinaryRequest(
    /** Base64-encoded bytes to send */
    val base64: String
)

// ── HTTP Request / Response Mutation ─────────────────────────────────────────

@Serializable
data class MutateRequestInput(
    /** Raw HTTP/1.1 request text (CRLF line endings accepted) */
    val request: String,
    /** Optional: attach service context (used to resolve relative URLs) */
    val service_host: String? = null,
    val service_port: Int = 443,
    val service_use_https: Boolean = true,
    /** Replace HTTP method (e.g. "POST") */
    val method: String? = null,
    /** Replace request path (e.g. "/new/path?q=1") */
    val path: String? = null,
    /** Replace request body */
    val body: String? = null,
    /** Swap GET↔POST (toggles method and moves parameters; mutually exclusive with 'method') */
    val toggle_method: Boolean = false,
    /** Add headers: {name -> value}. Does not remove existing. */
    val add_headers: Map<String, String> = emptyMap(),
    /** Remove headers by name (case-insensitive) */
    val remove_headers: List<String> = emptyList(),
    /** Update existing headers by name; adds if not present */
    val update_headers: Map<String, String> = emptyMap(),
    /** Add parameters. type: URL | BODY | COOKIE | XML | XML_ATTRIBUTE | MULTIPART_ATTRIBUTE | JSON */
    val add_params: List<ParamMutationInput> = emptyList(),
    /** Remove parameters by name and type */
    val remove_params: List<ParamMutationInput> = emptyList(),
    /** Update parameters (match by name+type, replace value) */
    val update_params: List<ParamMutationInput> = emptyList()
)

@Serializable
data class MutateResponseInput(
    /** Raw HTTP response text */
    val response: String,
    /** Replace status code (100–599) */
    val status_code: Int? = null,
    /** Replace response body */
    val body: String? = null,
    /** Add headers: {name -> value} */
    val add_headers: Map<String, String> = emptyMap(),
    /** Remove headers by name (case-insensitive) */
    val remove_headers: List<String> = emptyList(),
    /** Update existing headers by name */
    val update_headers: Map<String, String> = emptyMap()
)

@Serializable
data class ParamMutationInput(
    /** Parameter type. Allowed: URL, BODY, COOKIE, XML, XML_ATTRIBUTE, MULTIPART_ATTRIBUTE, JSON */
    val type: String,
    val name: String,
    val value: String = ""
)

// ── Proxy History Extended ────────────────────────────────────────────────────

@Serializable
data class ProxyEntryDto(
    val id: Int,
    val url: String,
    val method: String?,
    val host: String,
    val port: Int,
    val secure: Boolean,
    val status: Int?,
    val mime_type: String?,
    val has_response: Boolean,
    val edited: Boolean,
    val listener_port: Int,
    val time: String?,
    val timing_ms: Long?,
    val request_length: Int,
    val response_length: Int,
    val notes: String,
    val highlight: String?,
    val request: String?,
    val response: String?,
    /** The final (possibly modified by Burp rules) request that was sent */
    val final_request: String?,
    /** The original (pre-match/replace) response received */
    val original_response: String?,
    /** Burp's own rendering of the target service, for example `https://example.com:443` */
    val http_service_string: String?,
    /** Request body only, with the headers stripped */
    val request_body: String?,
    /** HTTP version of the request, for example `HTTP/2` */
    val request_http_version: String?
)

// ── Response Search Match ─────────────────────────────────────────────────────

@Serializable
data class ResponseSearchMatchDto(
    val url: String,
    val match_count: Int,
    /** Up to max_snippets extracts of surrounding context */
    val snippets: List<String>
)

// ── Site Map ──────────────────────────────────────────────────────────────────

@Serializable
data class SiteMapAddRequest(
    val host: String,
    val port: Int = 443,
    val use_https: Boolean = true,
    /** Raw HTTP/1.1 request text */
    val request: String,
    /** Raw HTTP response text (optional; omit if no response available) */
    val response: String? = null
)

@Serializable
data class SiteMapIssueDto(
    val name: String?,
    val detail: String?,
    val remediation: String?,
    val base_url: String?,
    val severity: String,
    val confidence: String,
    val host: String?,
    val port: Int?
)

// ── Engagement Tools ──────────────────────────────────────────────────────────

@Serializable
data class CsrfPocRequest(
    /** Raw HTTP request to generate PoC for */
    val request: String,
    val host: String,
    val port: Int = 443,
    val use_https: Boolean = true,
    /** Output format: HTML_FORM (default), FETCH_JS, AUTO_SUBMIT */
    val format: String = "AUTO_SUBMIT"
)

@Serializable
data class CsrfPocResponse(
    val format: String,
    val method: String,
    val action_url: String,
    val html: String
)

@Serializable
data class FindReferencesRequest(
    /** URL, host, or path fragment to search for across proxy history and site map */
    val query: String,
    val search_proxy: Boolean = true,
    val search_sitemap: Boolean = true,
    val scope_only: Boolean = false,
    val limit: Int = 200
)

@Serializable
data class ReferenceDto(
    val source_url: String,
    val match: String,
    val source: String  // PROXY or SITEMAP
)

@Serializable
data class AnalyzeTargetRequest(
    /** Host to analyze (leave blank to analyze entire proxy history) */
    val host: String? = null,
    val scope_only: Boolean = false
)

@Serializable
data class TargetAnalysisDto(
    val host: String?,
    val total_requests: Int,
    val unique_urls: Int,
    val unique_endpoints: Int,
    val methods: Map<String, Int>,
    val status_codes: Map<String, Int>,
    val mime_types: Map<String, Int>,
    val parameters: List<String>,
    val interesting_headers: Map<String, List<String>>
)

@Serializable
data class DiscoverContentRequest(
    val host: String,
    val port: Int = 443,
    val use_https: Boolean = true,
    /** Base path to discover under (e.g. "/api") */
    val base_path: String = "/",
    /** Paths/filenames to probe (e.g. ["admin", "backup.zip", ".git/config"]) */
    val wordlist: List<String>,
    /** HTTP method to use (default GET) */
    val method: String = "GET",
    /** Additional headers to send */
    val headers: Map<String, String> = emptyMap(),
    /** Status codes to consider "found" (default 200–299 + 301 + 302 + 403) */
    val found_status_codes: List<Int> = listOf(200, 201, 204, 301, 302, 307, 401, 403, 405),
    /** Max concurrent requests (1–20, default 5) */
    val concurrency: Int = 5
)

@Serializable
data class DiscoveredPathDto(
    val url: String,
    val status: Int,
    val response_length: Int,
    val content_type: String?
)

@Serializable
data class SendToDecoderRequest(
    /** Base64-encoded bytes to send to Decoder */
    val base64: String
)

// ── Scope ──────────────────────────────────────────────────────────────────────

@Serializable
data class ScopeUrlRequest(
    /** URL or prefix pattern to add/remove from scope (e.g. "https://example.com", "https://example.com/api/") */
    val url: String
)

@Serializable
data class ScopeCheckResult(
    val url: String,
    val in_scope: Boolean
)

// ── Repeater / Intruder ────────────────────────────────────────────────────────

@Serializable
data class SendToRepeaterRequest(
    /** Raw HTTP request string. Must include a Host header so Burp can determine the target. */
    val raw_request: String,
    /** Optional tab label displayed in Repeater (e.g. "Login check") */
    val tab_name: String? = null
)

@Serializable
data class SendToIntruderRequest(
    /**
     * Raw HTTP request string. Mark payload insertion points by surrounding them with § symbols
     * (e.g. "username=§admin§&password=§pass§"). Burp will auto-detect positions if none are marked.
     */
    val raw_request: String,
    /** Optional tab label displayed in Intruder */
    val tab_name: String? = null
)

// ── Match & Replace ────────────────────────────────────────────────────────────

@Serializable
data class MatchReplaceRule(
    /**
     * Zero-based position of this rule in the list (assigned by Burp, present in GET responses,
     * ignored in POST, required in PUT/{id} and DELETE/{id} paths).
     */
    val id: Int? = null,
    /**
     * Which part of the HTTP message to match against.
     * Allowed: request_first_line | request_header | request_body |
     *          request_param_name | request_param_value |
     *          response_header | response_body
     */
    val rule_type: String,
    /** Match expression. Treated as a Java regex unless is_simple_match=true. */
    val string_match: String = "",
    /**
     * Replacement string. Supports Java regex back-references (e.g. $1) when
     * is_simple_match=false. Use empty string to delete matched content.
     */
    val string_replace: String = "",
    /** When true, string_match is treated as a literal string instead of a regex. */
    val is_simple_match: Boolean = false,
    val enabled: Boolean = true,
    val comment: String? = null
)

@Serializable
data class MatchReplaceListResponse(
    val count: Int,
    val rules: List<MatchReplaceRule>
)

// ── JWT encode ─────────────────────────────────────────────────────────────────

@Serializable
data class JwtEncodeRequest(
    /** JSON string for the JWT header. Defaults to {"alg":"<algorithm>","typ":"JWT"} */
    val header_json: String? = null,
    /** JSON string for the JWT payload/claims (e.g. '{"sub":"user1","exp":9999999999}') */
    val payload_json: String,
    /** HMAC secret (UTF-8 bytes). Leave blank to produce an unsigned token (alg=none). */
    val secret: String = "",
    /**
     * Signing algorithm. Allowed: HS256 | HS384 | HS512 | none
     * When algorithm=none or secret is empty, signature segment is omitted (unsigned token).
     */
    val algorithm: String = "HS256"
)

@Serializable
data class JwtEncodeResponse(
    val token: String,
    val header: String,
    val payload: String,
    val algorithm: String
)

// ── Security header analysis ───────────────────────────────────────────────────

@Serializable
data class HeaderAnalysisRequest(
    /**
     * Map of header name to value from an HTTP response.
     * Header names are case-insensitive (normalised to lowercase internally).
     */
    val headers: Map<String, String>? = null,
    /**
     * Raw HTTP response string. Headers are parsed automatically from the response.
     * Supply either this or 'headers', not both.
     */
    val raw_response: String? = null
)

@Serializable
data class SecurityHeaderCheck(
    val header: String,
    /** present | missing | weak */
    val status: String,
    /** Actual header value (null when missing) */
    val value: String? = null,
    val recommendation: String
)

@Serializable
data class HeaderAnalysisResponse(
    val checks: List<SecurityHeaderCheck>,
    val score: Int,
    val max_score: Int,
    val grade: String,
    val summary: String
)

// ── Payload lists ──────────────────────────────────────────────────────────────

@Serializable
data class PayloadsResponse(
    val category: String,
    val description: String,
    val payloads: List<String>,
    val count: Int
)

// ── Scanner: audit from proxy history ─────────────────────────────────────────

@Serializable
data class AuditFromHistoryRequest(
    val index: Int,
    val configuration: String = "ACTIVE"  // "ACTIVE", "PASSIVE", "LEGACY_ACTIVE", "LEGACY_PASSIVE"
)

// ── Scanner: report download (base64) ─────────────────────────────────────────

@Serializable
data class ScanReportDownloadRequest(
    val format: String = "HTML",
    val prefix: String? = null
)

// ── HTTP send-with-auth ────────────────────────────────────────────────────────

@Serializable
data class AuthConfig(
    val host: String,
    val port: Int = 443,
    val use_https: Boolean = true,
    val method: String = "POST",
    val path: String,
    val headers: Map<String, String>? = null,
    val body: JsonElement? = null,
    val token_path: String = "$.access"
)

@Serializable
data class SendWithAuthRequest(
    val auth: AuthConfig,
    val request: SendHttpRequest,
    val inject_as: String = "Authorization: Bearer {token}",
    val retry_on: List<Int> = listOf(401, 403)
)

// ── Session rules ──────────────────────────────────────────────────────────────

@Serializable
data class AddHeaderRuleRequest(
    val header_name: String,
    val header_value: String,
    val name: String? = null,
    val scope_url: String? = null
)

// ── Intercept rules ────────────────────────────────────────────────────────────

@Serializable
data class InterceptRuleRequest(
    val enabled: Boolean = true,
    val match_type: String,
    val match_relationship: String,
    val match_condition: String
)

// ── Fuzz ──────────────────────────────────────────────────────────────────────

@Serializable
data class FuzzRequest(
    val host: String,
    val port: Int = 443,
    val https: Boolean = true,
    val method: String = "GET",
    /** Path template with {word} placeholder, e.g. "/api/{word}/" */
    val path_template: String,
    val wordlist: List<String>,
    val headers: Map<String, String>? = null,
    val body: JsonElement? = null,
    /** Only include responses with these status codes (empty = include all) */
    val filter_status: List<Int> = emptyList(),
    /** Exclude responses whose body is exactly this many bytes (useful to filter SPA catch-all pages) */
    val exclude_body_size: Int? = null,
    /** Exclude responses whose body contains this string */
    val exclude_body_contains: String? = null,
    /** Include first 300 chars of each response body in results */
    val include_body_snippet: Boolean = false,
    val concurrency: Int = 5,
    val timeout_ms: Long? = null
)

@Serializable
data class FuzzResult(
    val word: String,
    val path: String,
    val status: Int,
    val length: Int,
    val ai_notes: String? = null,
    val body_snippet: String? = null
)

// ── JWT decode ────────────────────────────────────────────────────────────────

@Serializable
data class JwtDecodeRequest(val token: String)

@Serializable
data class JwtDecodeResponse(
    val header: JsonObject,
    val payload: JsonObject,
    val issues: List<String>
)

// ── Auth diff ─────────────────────────────────────────────────────────────────

@Serializable
data class AuthDiffRequest(
    val host: String,
    val port: Int = 443,
    val https: Boolean = true,
    val method: String = "GET",
    val path: String,
    val headers: Map<String, String>? = null,
    val body: JsonElement? = null,
    /** Header for the high-privilege user, e.g. "Authorization: Bearer eyJ..." */
    val auth_header: String,
    /** Optional header for a second (lower-privilege) user - enables privilege escalation testing */
    val second_auth_header: String? = null
)

@Serializable
data class AuthDiffResponse(
    val authenticated: AuthDiffSide,
    val unauthenticated: AuthDiffSide,
    val second_user: AuthDiffSide? = null,
    val same_status: Boolean,
    val same_length: Boolean,
    val verdict: String
)

@Serializable
data class AuthDiffSide(val status: Int, val body_length: Int, val ai_notes: String? = null)

// ── Rate test ─────────────────────────────────────────────────────────────────

@Serializable
data class RateTestRequest(
    val host: String,
    val port: Int = 443,
    val https: Boolean = true,
    val method: String = "POST",
    val path: String,
    val headers: Map<String, String>? = null,
    val body: JsonElement? = null,
    val count: Int = 30,
    val concurrency: Int = 10
)

@Serializable
data class RateTestResponse(
    val total: Int,
    val status_distribution: Map<String, Int>,
    val first_block_at: Int?,
    val avg_ms: Long,
    val verdict: String
)


// ── Activity Log ──────────────────────────────────────────────────────────────

@Serializable
data class LogEntryDto(
    val id: Int,
    val ts: String,
    val method: String,
    val path: String,
    val status: Int,
    val duration_ms: Long,
    val ai_notes: String,
    val session_id: String? = null
)

@Serializable
data class SessionRequest(
    val label: String,
    val host: String? = null
)

@Serializable
data class SessionDto(
    val id: String,
    val label: String,
    val host: String? = null,
    val created_at: String
)

// ── Bambda ────────────────────────────────────────────────────────────────────

@Serializable
data class BambdaImportRequest(
    val name: String,
    val source: String
)

@Serializable
data class BambdaImportResponse(
    val status: String,
    val errors: List<String> = emptyList()
)

/** Chain step for POST /api/bambda/generate-chain */
@Serializable
data class ChainStep(
    val method: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    /** jsonpath or regex to extract from this step's response, e.g. "$.access" or "sessionid=([^;]+)" */
    val extract: Map<String, String> = emptyMap(),
    /** variable name -> header name to inject into next step, e.g. {"token": "Authorization: Bearer {token}"} */
    val inject: Map<String, String> = emptyMap()
)

@Serializable
data class GenerateChainRequest(
    val name: String,
    val host: String,
    val port: Int = 443,
    val https: Boolean = true,
    val steps: List<ChainStep>
)

@Serializable
data class GenerateChainResponse(
    val name: String,
    val source: String,
    val imported: Boolean,
    val import_errors: List<String> = emptyList()
)

// ── Request execution engine (Montoya 2026.x http.execution) ─────────────────
@Serializable
data class EngineRequestItem(
    val host: String,
    val port: Int = 443,
    val use_https: Boolean = true,
    val request: String? = null,
    val method: String? = null,
    val path: String? = null,
    val headers: Map<String, String>? = null,
    val body: JsonElement? = null,
    val label: String? = null
)
@Serializable
data class CreateEngineRequest(
    val name: String? = null,
    val concurrent_request_limit: Int? = null,
    val throttle_ms: Long? = null,
    val max_retries: Int? = null,
    /** Attach to an already-created named resource pool instead of building one. */
    val resource_pool_name: String? = null,
    /** Use Burp's shared default resource pool. */
    val default_pool: Boolean = false
)
@Serializable data class EngineCreated(val engine_id: String, val name: String? = null)
@Serializable data class QueueRequestsRequest(val requests: List<EngineRequestItem>)
@Serializable data class QueuedResponse(val id: String, val queued: Int)
@Serializable data class SendAllRequest(val timeout_ms: Long? = null)
@Serializable data class ExecutionCreated(val execution_id: String)
@Serializable data class ExecutionStatsDto(
    val requested: Int, val completed: Int, val failed: Int,
    val in_flight: Int, val pending: Int, val elapsed_ms: Long
)
@Serializable data class FinishedDto(val finished: Boolean)
@Serializable data class RequestResultDto(val label: String?, val status: String, val request_response: HttpEntryDto?)
@Serializable data class ExecutionResultDto(
    val cancelled: Boolean,
    val timed_out: Boolean,
    val stats: ExecutionStatsDto,
    val results: List<RequestResultDto>
)
@Serializable data class AwaitRequest(val timeout_ms: Long? = 30000, val include_body: Boolean = true)
@Serializable data class EngineListDto(val engines: List<String>, val executions: List<String>)

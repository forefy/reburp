package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.BurpSuiteEdition
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.scanner.AuditResult
import burp.api.montoya.scanner.CrawlConfiguration
import burp.api.montoya.scanner.audit.Audit
import burp.api.montoya.scanner.audit.issues.AuditIssueDefinition
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class AuditDetailDto(
    val task_id: String,
    val status_message: String,
    val insertion_point_count: Int,
    val request_count: Int,
    val error_count: Int,
    val issue_count: Int
)

@Serializable
data class AddToAuditInput(
    val request: String,
    val response: String? = null,
    val host: String,
    val port: Int = 443,
    val secure: Boolean = true
)

@Serializable
data class IssueDefinitionInput(
    val name: String,
    val background: String,
    val remediation: String,
    val typical_severity: String = "INFORMATION"
)

@Serializable
data class IssueDefinitionDto(
    val name: String,
    val background: String,
    val remediation: String,
    val typical_severity: String,
    val type_index: Int
)

@Serializable
data class CollaboratorHitDto(
    val id: String,
    val type: String,
    val time: String
)

@Serializable
data class IssueDetailDto(
    val name: String,
    val base_url: String,
    val severity: String,
    val confidence: String,
    val detail: String?,
    val remediation: String?,
    val request_response_count: Int,
    val collaborator_interactions: List<CollaboratorHitDto>,
    val definition: IssueDefinitionDto?
)

@Serializable
data class CrawlPreviewInput(val seed_urls: List<String>)

@Serializable
data class CrawlPreviewDto(val seed_urls: List<String>, val count: Int)

@Serializable
data class ConsolidateInput(val severities: List<String> = emptyList())

@Serializable
data class ConsolidatedDto(
    val total: Int,
    val issues: List<IssueDetailDto>
)

private fun severityOrNull(raw: String): AuditIssueSeverity? =
    runCatching { AuditIssueSeverity.valueOf(raw.trim().uppercase()) }.getOrNull()

private val SEVERITY_NAMES = AuditIssueSeverity.values().joinToString(", ") { it.name }

/**
 * Scanner endpoints beyond starting tasks and listing issues.
 *
 * These cover the parts of an audit that only become visible once it is running: how many
 * insertion points Burp derived, feeding extra traffic into a live audit, and the
 * Collaborator hits and issue definition behind a finding.
 *
 * Professional only, like the rest of the scanner.
 */
fun Routing.scannerExtraRoutes(api: MontoyaApi) {
    val isPro = api.burpSuite().version().edition() == BurpSuiteEdition.PROFESSIONAL

    route("/api/scanner") {

        // Progress detail for a running audit, including derived insertion points.
        get("/tasks/{id}/detail") {
            if (!isPro) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("'id' path parameter is required"))
            val task = scannerTasks[id]
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("No scan task with id '$id'"))
            val audit = task as? Audit
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Task '$id' is a crawl, not an audit. Insertion point detail exists only for audits."))
            call.respond(
                AuditDetailDto(
                    task_id = id,
                    status_message = runCatching { audit.statusMessage() }.getOrElse { "" },
                    insertion_point_count = runCatching { audit.insertionPointCount() }.getOrElse { 0 },
                    request_count = runCatching { audit.requestCount() }.getOrElse { 0 },
                    error_count = runCatching { audit.errorCount() }.getOrElse { 0 },
                    issue_count = runCatching { audit.issues().size }.getOrElse { 0 }
                )
            )
        }

        // Feed an extra exchange into a running audit so Burp audits it too.
        post("/tasks/{id}/add") {
            if (!isPro) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val id = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'id' path parameter is required"))
            val req = runCatching { call.receive<AddToAuditInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val task = scannerTasks[id]
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("No scan task with id '$id'"))
            val audit = task as? Audit
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Task '$id' is a crawl, not an audit. Only audits accept added traffic."))
            runCatching {
                val service = httpService(req.host, req.port, req.secure)
                val request = HttpRequest.httpRequest(service, normalizeRequest(req.request))
                val pair = if (req.response.isNullOrBlank()) {
                    HttpRequestResponse.httpRequestResponse(request, null)
                } else {
                    HttpRequestResponse.httpRequestResponse(request, HttpResponse.httpResponse(req.response))
                }
                audit.addRequestResponse(pair)
            }
                .onSuccess { call.respond(MessageResponse("Exchange added to audit '$id'")) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Could not add exchange: ${it.message}")) }
        }

        // Validate a crawl's seed URLs without starting it.
        post("/crawl/preview") {
            if (!isPro) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val req = runCatching { call.receive<CrawlPreviewInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.seed_urls.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'seed_urls' must contain at least one URL"))
            }
            runCatching {
                val config: CrawlConfiguration = CrawlConfiguration.crawlConfiguration(*req.seed_urls.toTypedArray())
                val seeds = config.seedUrls()
                call.respond(CrawlPreviewDto(seeds, seeds.size))
            }.onFailure {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid crawl configuration: ${it.message}"))
            }
        }

        // Build an issue definition and echo how Burp interprets it, including its type index.
        post("/issue-definition") {
            val req = runCatching { call.receive<IssueDefinitionInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val severity = severityOrNull(req.typical_severity)
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'typical_severity': '${req.typical_severity}'. Allowed values: $SEVERITY_NAMES"))
            runCatching {
                val definition = AuditIssueDefinition.auditIssueDefinition(
                    req.name, req.background, req.remediation, severity
                )
                call.respond(
                    IssueDefinitionDto(
                        name = definition.name(),
                        background = definition.background(),
                        remediation = definition.remediation(),
                        typical_severity = definition.typicalSeverity().name,
                        type_index = runCatching { definition.typeIndex() }.getOrElse { -1 }
                    )
                )
            }.onFailure {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Could not build definition: ${it.message}"))
            }
        }

        // Collect current issues into one audit result, optionally filtered by severity.
        post("/issues/consolidate") {
            if (!isPro) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val req = runCatching { call.receive<ConsolidateInput>() }.getOrElse { ConsolidateInput() }
            val wanted = req.severities.map { raw ->
                severityOrNull(raw) ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid severity: '$raw'. Allowed values: $SEVERITY_NAMES")
                )
            }.toSet()
            runCatching {
                val all = api.siteMap().issues()
                val selected = if (wanted.isEmpty()) all else all.filter { it.severity() in wanted }
                // auditResult is Burp's own container for a set of findings, so the grouped
                // view below is exactly what a scan check would hand back to the scanner.
                val result: AuditResult = AuditResult.auditResult(selected)
                val issues = result.auditIssues().map { issue ->
                    IssueDetailDto(
                        name = runCatching { issue.name() }.getOrElse { "" },
                        base_url = runCatching { issue.baseUrl() }.getOrElse { "" },
                        severity = runCatching { issue.severity().name }.getOrElse { "UNKNOWN" },
                        confidence = runCatching { issue.confidence().name }.getOrElse { "UNKNOWN" },
                        detail = runCatching { issue.detail() }.getOrNull(),
                        remediation = runCatching { issue.remediation() }.getOrNull(),
                        request_response_count = runCatching { issue.requestResponses().size }.getOrElse { 0 },
                        collaborator_interactions = runCatching {
                            issue.collaboratorInteractions().map { hit ->
                                CollaboratorHitDto(
                                    id = hit.id().toString(),
                                    type = hit.type().toString(),
                                    time = hit.timeStamp().toString()
                                )
                            }
                        }.getOrElse { emptyList() },
                        definition = runCatching {
                            val def = issue.definition()
                            IssueDefinitionDto(
                                name = def.name(),
                                background = def.background(),
                                remediation = def.remediation(),
                                typical_severity = def.typicalSeverity().name,
                                type_index = def.typeIndex()
                            )
                        }.getOrNull()
                    )
                }
                call.respond(ConsolidatedDto(issues.size, issues))
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Consolidation failed: ${it.message}"))
            }
        }
    }
}

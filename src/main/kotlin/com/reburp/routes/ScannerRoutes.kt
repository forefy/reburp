package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.BurpSuiteEdition
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.scanner.AuditConfiguration
import burp.api.montoya.scanner.BuiltInAuditConfiguration
import burp.api.montoya.scanner.CrawlConfiguration
import burp.api.montoya.scanner.ReportFormat
import burp.api.montoya.scanner.audit.Audit
import burp.api.montoya.sitemap.SiteMapFilter
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// In-memory task registry - keys are short UUIDs assigned at task creation.
internal val scannerTasks = ConcurrentHashMap<String, burp.api.montoya.scanner.ScanTask>()

fun Routing.scannerRoutes(api: MontoyaApi) {
    val isPro = api.burpSuite().version().edition() == BurpSuiteEdition.PROFESSIONAL

    route("/api/scanner") {

        // ── Issues ────────────────────────────────────────────────────────────
        // Returns all scanner issues from the site map.
        // Query params:
        //   offset, limit         - pagination
        //   severity              - filter by severity (HIGH, MEDIUM, LOW, INFORMATION, FALSE_POSITIVE)
        //   confidence            - filter by confidence (CERTAIN, FIRM, TENTATIVE)
        //   host                  - filter by exact hostname
        //   name_contains         - filter by case-insensitive substring in issue name
        //   prefix                - URL prefix filter (e.g. https://example.com/api)
        //   include_requests      - true: include flagged HTTP request/response bodies (default false)
        //   include_request_body  - true: include request/response body text (default false; requires include_requests=true)

        get("/issues") {
            if (!isPro) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val p = call.request.queryParameters
            val offset     = p["offset"]?.toIntOrNull() ?: 0
            val limit      = (p["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)
            val severity   = p["severity"]?.uppercase()
            val confidence = p["confidence"]?.uppercase()
            val host       = p["host"]
            val nameContains = p["name_contains"]
            val prefix     = p["prefix"]
            val includeRequests   = p["include_requests"]?.toBooleanStrictOrNull() ?: false
            val includeBody       = p["include_request_body"]?.toBooleanStrictOrNull() ?: false

            val validSeverities  = burp.api.montoya.scanner.audit.issues.AuditIssueSeverity.values().map { it.name }
            val validConfidences = burp.api.montoya.scanner.audit.issues.AuditIssueConfidence.values().map { it.name }
            if (severity != null && severity !in validSeverities) return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Invalid 'severity'. Allowed: ${validSeverities.joinToString(", ")}")
            )
            if (confidence != null && confidence !in validConfidences) return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Invalid 'confidence'. Allowed: ${validConfidences.joinToString(", ")}")
            )

            runCatching {
                val filter = if (prefix != null) SiteMapFilter.prefixFilter(prefix) else null
                var issues = if (filter != null) api.siteMap().issues(filter) else api.siteMap().issues()
                if (severity != null)     issues = issues.filter { it.severity().name == severity }
                if (confidence != null)   issues = issues.filter { it.confidence().name == confidence }
                if (host != null)         issues = issues.filter { runCatching { it.httpService().host().equals(host, ignoreCase = true) }.getOrElse { false } }
                if (nameContains != null) issues = issues.filter { it.name()?.contains(nameContains, ignoreCase = true) == true }

                val result = issues.drop(offset).take(limit).map { issue ->
                    val def = runCatching { issue.definition() }.getOrNull()
                    val flaggedRequests = if (includeRequests) {
                        runCatching { issue.requestResponses().map { it.toDto(includeBody) } }.getOrElse { emptyList() }
                    } else emptyList()
                    ScanIssueFull(
                        name             = issue.name(),
                        detail           = issue.detail(),
                        remediation      = issue.remediation(),
                        base_url         = issue.baseUrl(),
                        severity         = issue.severity().name,
                        confidence       = issue.confidence().name,
                        host             = runCatching { issue.httpService().host() }.getOrNull(),
                        port             = runCatching { issue.httpService().port() }.getOrNull(),
                        background       = def?.background(),
                        typical_severity = def?.typicalSeverity()?.name,
                        type_index       = def?.typeIndex(),
                        flagged_requests = flaggedRequests
                    )
                }
                call.respond(result)
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error fetching issues")) }
        }

        // ── Start audit (built-in configuration) ──────────────────────────────

        post("/audit") {
            if (!isPro) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val req = runCatching { call.receive<StartAuditRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                val cfg = AuditConfiguration.auditConfiguration(
                    BuiltInAuditConfiguration.valueOf(req.configuration)
                )
                val audit = api.scanner().startAudit(cfg)
                if (req.requests.isNotEmpty() && req.host != null) {
                    val service = httpService(req.host, req.port, req.use_https)
                    for (raw in req.requests) {
                        audit.addRequest(HttpRequest.httpRequest(service, normalizeRequest(raw)))
                    }
                }
                val id = UUID.randomUUID().toString().take(8)
                scannerTasks[id] = audit
                call.respond(ScanTaskDto(id, audit.statusMessage(), audit.requestCount(), audit.errorCount(), audit.issues().size))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Start audit (active / passive mode) ───────────────────────────────
        post("/audit/mode") {
            if (!isPro) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val req = runCatching { call.receive<StartAuditModeRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                val builtIn = when (req.mode.trim().uppercase()) {
                    "ACTIVE" -> BuiltInAuditConfiguration.LEGACY_ACTIVE_AUDIT_CHECKS
                    "PASSIVE" -> BuiltInAuditConfiguration.LEGACY_PASSIVE_AUDIT_CHECKS
                    else -> BuiltInAuditConfiguration.valueOf(req.mode.trim().uppercase())
                }
                val audit = api.scanner().startAudit(AuditConfiguration.auditConfiguration(builtIn))
                if (req.requests.isNotEmpty() && req.host != null) {
                    val service = httpService(req.host, req.port, req.use_https)
                    for (raw in req.requests) {
                        audit.addRequest(HttpRequest.httpRequest(service, normalizeRequest(raw)))
                    }
                }
                val id = UUID.randomUUID().toString().take(8)
                scannerTasks[id] = audit
                call.respond(ScanTaskDto(id, audit.statusMessage(), audit.requestCount(), audit.errorCount(), audit.issues().size))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Start audit from proxy history item ───────────────────────────────

        post("/audit/from-history") {
            if (!isPro) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val req = runCatching { call.receive<AuditFromHistoryRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val history = api.proxy().history()
            if (req.index < 0 || req.index >= history.size)
                return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Index ${req.index} out of range (history size: ${history.size})"))
            runCatching {
                val item = history[req.index]
                val builtIn = when (req.configuration.uppercase()) {
                    "ACTIVE"         -> BuiltInAuditConfiguration.LEGACY_ACTIVE_AUDIT_CHECKS
                    "PASSIVE"        -> BuiltInAuditConfiguration.LEGACY_PASSIVE_AUDIT_CHECKS
                    "LEGACY_PASSIVE" -> BuiltInAuditConfiguration.LEGACY_PASSIVE_AUDIT_CHECKS
                    else             -> BuiltInAuditConfiguration.LEGACY_ACTIVE_AUDIT_CHECKS
                }
                val auditConfig = AuditConfiguration.auditConfiguration(builtIn)
                val audit = api.scanner().startAudit(auditConfig)
                audit.addRequest(item.request())
                val id = UUID.randomUUID().toString().take(8)
                scannerTasks[id] = audit
                call.respond(ScanTaskDto(id, audit.statusMessage(), audit.requestCount(), audit.errorCount(), audit.issues().size))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Start crawl ───────────────────────────────────────────────────────

        post("/crawl") {
            if (!isPro) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val req = runCatching { call.receive<StartCrawlRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                val crawl = api.scanner().startCrawl(
                    CrawlConfiguration.crawlConfiguration(*req.seed_urls.toTypedArray())
                )
                val id = UUID.randomUUID().toString().take(8)
                scannerTasks[id] = crawl
                call.respond(ScanTaskDto(id, crawl.statusMessage(), crawl.requestCount(), crawl.errorCount(), null))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Task status ───────────────────────────────────────────────────────

        get("/tasks/{id}") {
            if (!isPro) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val id = call.parameters["id"]!!
            val task = scannerTasks[id]
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Task not found: $id"))
            val issueCount = (task as? Audit)?.issues()?.size
            call.respond(ScanTaskDto(id, task.statusMessage(), task.requestCount(), task.errorCount(), issueCount))
        }

        // ── Delete task ───────────────────────────────────────────────────────

        delete("/tasks/{id}") {
            if (!isPro) return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val id = call.parameters["id"]!!
            val task = scannerTasks.remove(id)
                ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Task not found: $id"))
            task.delete()
            call.respond(MessageResponse("Deleted task: $id"))
        }

        // ── Generate report ───────────────────────────────────────────────────

        post("/report") {
            if (!isPro) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val req = runCatching { call.receive<ScanReportRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                val format = ReportFormat.valueOf(req.format.trim().uppercase())
                val path = Paths.get(req.path)
                val issues = when {
                    req.task_id != null -> {
                        val task = scannerTasks[req.task_id] as? Audit
                            ?: return@runCatching call.respond(
                                HttpStatusCode.NotFound,
                                ErrorResponse("Task not found or not an audit: ${req.task_id}")
                            )
                        task.issues()
                    }
                    req.all_issues -> api.siteMap().issues()
                    else -> return@runCatching call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Provide task_id or set all_issues=true")
                    )
                }
                api.scanner().generateReport(issues, format, path)
                call.respond(MessageResponse("Report generated: ${req.path}"))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Generate report (base64 download) ────────────────────────────────

        post("/report/download") {
            if (!isPro) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val req = runCatching { call.receive<ScanReportDownloadRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            runCatching {
                val validFormats = ReportFormat.values().map { it.name }
                if (req.format.uppercase() !in validFormats)
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'format'. Allowed: ${validFormats.joinToString(", ")}"))
                val filter = if (req.prefix != null) SiteMapFilter.prefixFilter(req.prefix) else null
                val issues = if (filter != null) api.siteMap().issues(filter) else api.siteMap().issues()
                val reportsDir = Paths.get(System.getProperty("user.home"), ".rburp", "reports")
                java.nio.file.Files.createDirectories(reportsDir)
                val reportFile = reportsDir.resolve("report_${System.currentTimeMillis()}.${req.format.lowercase()}")
                api.scanner().generateReport(issues, ReportFormat.valueOf(req.format.uppercase()), reportFile)
                val bytes = java.nio.file.Files.readAllBytes(reportFile)
                runCatching { java.nio.file.Files.delete(reportFile) }
                val b64 = java.util.Base64.getEncoder().encodeToString(bytes)
                call.respond(mapOf("format" to req.format.uppercase(), "size_bytes" to bytes.size.toString(), "data_base64" to b64))
            }.onFailure { if (!call.response.isCommitted) call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Import BCheck ─────────────────────────────────────────────────────
        // Loads a BCheck script into the active scanner.
        // Body: { source: "<bcheck source code>", enabled: true }
        // BCheck scripts use the BCheck DSL (see https://portswigger.net/burp/documentation/scanner/bchecks)
        // Returns: { status, success, errors[] }
        // status values: LOADED_WITHOUT_ERRORS, LOADED_WITH_ERRORS

        post("/bchecks/import") {
            if (!isPro) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            val req = runCatching { call.receive<ImportScriptRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.source.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'source' is required"))
            runCatching {
                val result = api.scanner().bChecks().importBCheck(req.source, req.enabled)
                val errors = result.importErrors()
                val success = errors.isEmpty()
                call.respond(
                    if (success) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity,
                    ImportResultDto(status = result.status().name, success = success, errors = errors)
                )
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        // ── Import Bambda ─────────────────────────────────────────────────────
        // Loads a Bambda into Burp (custom Java-like filter expressions used in
        // proxy history, site map, logger, etc.)
        // Body: { source: "<bambda source code>" }  (enabled field is ignored for bambdas)
        // Returns: { status, success, errors[] }
        // status values: LOADED_WITHOUT_ERRORS, LOADED_WITH_ERRORS

        post("/bambda/import") {
            val req = runCatching { call.receive<ImportScriptRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.source.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'source' is required"))
            runCatching {
                val result = api.bambda().importBambda(req.source)
                val errors = result.importErrors()
                val success = errors.isEmpty()
                call.respond(
                    if (success) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity,
                    ImportResultDto(status = result.status().name, success = success, errors = errors)
                )
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        // ── BChecks / Bambdas config export ───────────────────────────────────
        // Montoya does not expose a list or delete API for BChecks or Bambdas.
        // Use these endpoints to export the raw config sections where they are stored,
        // then use PUT /api/config/project or PUT /api/config/user to write back changes.

        get("/bchecks/config") {
            if (!isPro) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Requires Burp Suite Professional"))
            runCatching {
                call.respond(StringResult(api.burpSuite().exportProjectOptionsAsJson("scanner")))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to export scanner config: ${it.message}")) }
        }

        get("/bambda/config") {
            runCatching {
                call.respond(StringResult(api.burpSuite().exportUserOptionsAsJson()))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to export user config: ${it.message}")) }
        }
    }
}

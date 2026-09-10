package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.scanner.audit.issues.AuditIssue
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.issueRoutes(api: MontoyaApi) {
    post("/api/issues") {
        val req = runCatching { call.receive<CreateIssueRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
        }
        runCatching {
            val severity = AuditIssueSeverity.valueOf(req.severity.uppercase())
            val confidence = AuditIssueConfidence.valueOf(req.confidence.uppercase())
            val typicalSeverity = runCatching {
                AuditIssueSeverity.valueOf((req.typical_severity ?: req.severity).uppercase())
            }.getOrElse { severity }

            val requestResponses: List<HttpRequestResponse> = if (req.http_request != null && req.target_host != null) {
                val service = httpService(req.target_host, req.target_port, req.target_use_https)
                val httpReq = HttpRequest.httpRequest(service, normalizeRequest(req.http_request))
                val httpRes = req.http_response?.let { HttpResponse.httpResponse(normalizeRequest(it)) }
                listOf(
                    if (httpRes != null) HttpRequestResponse.httpRequestResponse(httpReq, httpRes)
                    else HttpRequestResponse.httpRequestResponse(httpReq, null)
                )
            } else emptyList()

            val issue = AuditIssue.auditIssue(
                req.name,
                req.detail,
                req.remediation ?: "",
                req.base_url,
                severity,
                confidence,
                req.background ?: "",
                req.remediation_background ?: "",
                typicalSeverity,
                requestResponses
            )
            api.siteMap().add(issue)
            call.respond(MessageResponse("Issue created: ${req.name}"))
        }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
    }
}

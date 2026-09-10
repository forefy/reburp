package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.utilities.shell.ExecuteOptions
import burp.api.montoya.utilities.shell.ExitCodeBehavior
import burp.api.montoya.utilities.shell.StderrBehavior
import burp.api.montoya.utilities.shell.TimeoutBehavior
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Duration
import kotlinx.serialization.Serializable

@Serializable
data class ShellExecInput(
    val command: List<String> = emptyList(),
    val command_line: String? = null,
    val timeout_seconds: Int = 30,
    val timeout_behavior: String = "FAIL_ON_TIMEOUT",
    val stderr_behavior: String = "MERGE",
    val exit_code_behavior: String = "ALLOW_NON_ZERO",
    val environment: Map<String, String> = emptyMap()
)

@Serializable
data class ShellStatusResult(
    val enabled: Boolean,
    val enable_via: String,
    val warning: String
)

@Serializable
data class ShellExecResult(
    val output: String,
    val shell_interpreted: Boolean,
    val timeout_seconds: Int
)

/** Environment variable that must be set in Burp's own process to enable these routes. */
const val SHELL_ENABLE_VAR = "REBURP_ENABLE_SHELL"

private const val SHELL_DISABLED_MESSAGE =
    "Shell execution is disabled. This REST API has no authentication and accepts cross-origin " +
    "requests, so any web page you visit could reach it; enabling command execution turns that " +
    "into remote code execution on this host. Set $SHELL_ENABLE_VAR=1 in Burp's environment and " +
    "restart Burp only if you accept that risk."

internal fun shellEnabled(): Boolean =
    System.getenv(SHELL_ENABLE_VAR)?.trim()?.lowercase() in setOf("1", "true", "yes")

/**
 * OS command execution, backed by Montoya's ShellUtils.
 *
 * DISABLED BY DEFAULT and deliberately so. The surrounding REST server binds an
 * unauthenticated port with permissive CORS, so exposing command execution on it is
 * equivalent to granting remote code execution to any origin that can reach localhost.
 * The routes return 403 unless [SHELL_ENABLE_VAR] is set in Burp's process environment.
 *
 * `/execute` passes an argument vector directly to the OS with no shell involved.
 * `/execute-raw` hands a string to the system shell, so it carries the extra risk of
 * shell metacharacter injection on top of execution itself. Prefer `/execute`.
 */
fun Routing.shellRoutes(api: MontoyaApi) {
    route("/api/utils/shell") {

        // Report whether execution is enabled, without requiring it to be.
        get("/status") {
            call.respond(
                ShellStatusResult(
                    enabled = shellEnabled(),
                    enable_via = "$SHELL_ENABLE_VAR=1 in Burp's process environment, then restart Burp",
                    warning = SHELL_DISABLED_MESSAGE
                )
            )
        }

        // Execute an argument vector with no shell interpretation.
        post("/execute") {
            if (!shellEnabled()) {
                return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse(SHELL_DISABLED_MESSAGE))
            }
            val req = runCatching { call.receive<ShellExecInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.command.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("'command' must be a non-empty argument vector, e.g. [\"curl\", \"-s\", \"https://example.com\"]"))
            }
            val options = buildOptions(req) ?: return@post call.respond(
                HttpStatusCode.BadRequest, ErrorResponse(behaviorError(req))
            )
            runCatching { api.utilities().shellUtils().execute(options, *req.command.toTypedArray()) }
                .onSuccess {
                    api.logging().logToOutput("[shell] execute: ${req.command.joinToString(" ")}")
                    call.respond(ShellExecResult(it, shell_interpreted = false, timeout_seconds = req.timeout_seconds))
                }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Execution failed: ${it.message}")) }
        }

        // Execute a command line through the system shell. Higher risk than /execute.
        post("/execute-raw") {
            if (!shellEnabled()) {
                return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse(SHELL_DISABLED_MESSAGE))
            }
            val req = runCatching { call.receive<ShellExecInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val line = req.command_line
            if (line.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("'command_line' is required and must be a non-empty shell command"))
            }
            val options = buildOptions(req) ?: return@post call.respond(
                HttpStatusCode.BadRequest, ErrorResponse(behaviorError(req))
            )
            runCatching { api.utilities().shellUtils().dangerouslyExecute(options, line) }
                .onSuccess {
                    api.logging().logToOutput("[shell] execute-raw: $line")
                    call.respond(ShellExecResult(it, shell_interpreted = true, timeout_seconds = req.timeout_seconds))
                }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Execution failed: ${it.message}")) }
        }
    }
}

/** Builds Montoya ExecuteOptions, or null when an enum value is unrecognised. */
private fun buildOptions(req: ShellExecInput): ExecuteOptions? {
    val timeoutBehavior = runCatching { TimeoutBehavior.valueOf(req.timeout_behavior.trim().uppercase()) }.getOrNull() ?: return null
    val stderrBehavior = runCatching { StderrBehavior.valueOf(req.stderr_behavior.trim().uppercase()) }.getOrNull() ?: return null
    val exitCodeBehavior = runCatching { ExitCodeBehavior.valueOf(req.exit_code_behavior.trim().uppercase()) }.getOrNull() ?: return null
    var options = ExecuteOptions.executeOptions()
        .withTimeout(Duration.ofSeconds(req.timeout_seconds.coerceIn(1, 600).toLong()))
        .withTimeoutBehavior(timeoutBehavior)
        .withStderrBehavior(stderrBehavior)
        .withExitCodeBehavior(exitCodeBehavior)
    req.environment.forEach { (k, v) -> options = options.withEnvironmentVariable(k, v) }
    return options
}

private fun behaviorError(req: ShellExecInput): String =
    "Invalid behavior value. 'timeout_behavior' must be one of ${TimeoutBehavior.values().joinToString(", ") { it.name }}; " +
    "'stderr_behavior' one of ${StderrBehavior.values().joinToString(", ") { it.name }}; " +
    "'exit_code_behavior' one of ${ExitCodeBehavior.values().joinToString(", ") { it.name }}. " +
    "Received '${req.timeout_behavior}', '${req.stderr_behavior}', '${req.exit_code_behavior}'."

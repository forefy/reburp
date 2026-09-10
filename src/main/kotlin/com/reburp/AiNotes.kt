package com.reburp

fun analyzeEntry(
    method: String, path: String, status: Int,
    requestBody: String, responseBody: String
): String {
    val notes     = mutableListOf<String>()
    val respLower = responseBody.lowercase()
    val pathLower = path.lowercase()

    // Auth / access control
    if (status == 401) notes += "Auth required"
    if (status == 403) notes += "Forbidden - check role/scope"
    if (status in 200..299 && method == "DELETE") notes += "DELETE succeeded - verify BOLA"
    if (status in 200..299 && method in listOf("PUT", "PATCH") && requestBody.isNotBlank()) {
        val reqLower = requestBody.lowercase()
        if (reqLower.contains("admin") || reqLower.contains("role") || reqLower.contains("is_staff"))
            notes += "Privileged field write - check mass assignment"
        else
            notes += "Write succeeded - verify authorization"
    }

    // ID enumeration
    if (Regex("""/\d+/?$""").containsMatchIn(path)) {
        if (status in 200..299) notes += "Object by ID - probe IDOR"
        if (status == 404)      notes += "Object not found - ID enum"
    }

    // Sensitive paths
    val sensitivePaths = listOf(
        "admin", "config", "debug", "internal", "backup", "export", "import",
        "secret", "token", "key", "cred", "password", "reset", "setup", "install"
    )
    if (sensitivePaths.any { pathLower.contains(it) }) notes += "Sensitive path"

    // Server errors
    if (status == 500) notes += "Server error - check for info disclosure"
    if (status in listOf(502, 503)) notes += "Service error"

    // Pre-auth crash: 500 on a request with no auth credentials
    val hasAuth = requestBody.contains("Authorization:", ignoreCase = true) ||
                  requestBody.contains("Bearer ", ignoreCase = true) ||
                  requestBody.contains("Token ", ignoreCase = true)
    if (status == 500 && !hasAuth) notes += "500 without auth - possible missing auth check (pre-auth crash)"

    // Validation runs before auth: 400 field errors with no auth headers suggest auth not checked first
    if (status == 400 && !hasAuth && (respLower.contains("field is required") || respLower.contains("is not a valid choice")))
        notes += "Validation before auth - auth may not be enforced"

    // Privilege fields exposed in response
    if (respLower.contains("\"is_staff\"") || respLower.contains("\"is_superuser\""))
        notes += "Privilege flags exposed in response (is_staff/is_superuser)"
    if (respLower.contains("\"user_type\"") && status in 200..299)
        notes += "User role field in response - check access control"

    // Internal config/flags exposed without auth
    if ((pathLower.contains("feature-flag") || pathLower.contains("config") || pathLower.contains("rate-limit")) &&
        status in 200..299 && !hasAuth)
        notes += "Internal config/flags accessible without auth"

    // Info disclosure in response
    if (respLower.contains("traceback") || respLower.contains("stack trace")) notes += "Stack trace exposed"
    if (respLower.contains("exception") && respLower.contains("django"))       notes += "Django exception exposed"
    if (respLower.contains("syntax error") || respLower.contains("sql"))       notes += "Possible SQL error"
    if (respLower.contains("\"password\"") || respLower.contains("\"secret\"")) notes += "Credentials in response"

    // Token in response
    if (respLower.contains("\"access\"") || respLower.contains("\"token\"")) notes += "Token returned"

    // Method signals
    if (status == 405) notes += "Method not allowed"
    if (method == "OPTIONS" && status in 200..299) notes += "CORS/OPTIONS - check allowed methods"

    // Injection probes in request
    if (requestBody.contains("' OR ") || requestBody.contains("1=1") || requestBody.contains("--"))
        notes += "SQLi probe sent"
    if (requestBody.contains("<script") || requestBody.contains("javascript:"))
        notes += "XSS probe sent"

    return notes.joinToString(" · ")
}

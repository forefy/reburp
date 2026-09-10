package com.reburp.routes

import burp.api.montoya.MontoyaApi
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class NumberConvertInput(
    val value: String,
    val from: String,
    val to: String
)

@Serializable
data class NumberRadixInput(
    val value: String,
    val from: String,
    val radix: Int
)

@Serializable
data class NumberConvertResult(
    val value: String,
    val from: String,
    val to: String,
    val result: String
)

private val BASES = listOf("BINARY", "OCTAL", "DECIMAL", "HEX")

/**
 * Number base conversion, backed by Montoya's NumberUtils.
 *
 * Two shapes are exposed:
 *   POST /convert       - named base to named base (the 12 pairwise converters)
 *   POST /convert-radix - named base to an arbitrary radix (the 4 radix converters)
 */
fun Routing.numberRoutes(api: MontoyaApi) {
    route("/api/utils/number") {

        // Pairwise conversion between BINARY, OCTAL, DECIMAL and HEX.
        post("/convert") {
            val req = runCatching { call.receive<NumberConvertInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val from = req.from.trim().uppercase()
            val to = req.to.trim().uppercase()
            if (from !in BASES) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'from': '${req.from}'. Allowed values: ${BASES.joinToString(", ")}"))
            }
            if (to !in BASES) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'to': '${req.to}'. Allowed values: ${BASES.joinToString(", ")}"))
            }
            if (from == to) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("'from' and 'to' must differ - both are '$from'"))
            }
            val n = api.utilities().numberUtils()
            val result = runCatching {
                when (from) {
                    "BINARY" -> when (to) {
                        "OCTAL"   -> n.convertBinaryToOctal(req.value)
                        "DECIMAL" -> n.convertBinaryToDecimal(req.value)
                        else      -> n.convertBinaryToHex(req.value)
                    }
                    "OCTAL" -> when (to) {
                        "BINARY"  -> n.convertOctalToBinary(req.value)
                        "DECIMAL" -> n.convertOctalToDecimal(req.value)
                        else      -> n.convertOctalToHex(req.value)
                    }
                    "DECIMAL" -> when (to) {
                        "BINARY" -> n.convertDecimalToBinary(req.value)
                        "OCTAL"  -> n.convertDecimalToOctal(req.value)
                        else     -> n.convertDecimalToHex(req.value)
                    }
                    else -> when (to) {
                        "BINARY"  -> n.convertHexToBinary(req.value)
                        "OCTAL"   -> n.convertHexToOctal(req.value)
                        else      -> n.convertHexToDecimal(req.value)
                    }
                }
            }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Conversion failed - '${req.value}' is not valid $from: ${it.message}"))
            }
            call.respond(NumberConvertResult(req.value, from, to, result))
        }

        // Conversion from a named base into an arbitrary target radix (2-36).
        post("/convert-radix") {
            val req = runCatching { call.receive<NumberRadixInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val from = req.from.trim().uppercase()
            if (from !in BASES) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'from': '${req.from}'. Allowed values: ${BASES.joinToString(", ")}"))
            }
            if (req.radix < 2 || req.radix > 36) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'radix': ${req.radix}. Must be between 2 and 36 inclusive."))
            }
            val n = api.utilities().numberUtils()
            val result = runCatching {
                when (from) {
                    "BINARY"  -> n.convertBinary(req.value, req.radix)
                    "OCTAL"   -> n.convertOctal(req.value, req.radix)
                    "DECIMAL" -> n.convertDecimal(req.value, req.radix)
                    else      -> n.convertHex(req.value, req.radix)
                }
            }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Conversion failed - '${req.value}' is not valid $from: ${it.message}"))
            }
            call.respond(NumberConvertResult(req.value, from, "RADIX_${req.radix}", result))
        }
    }
}

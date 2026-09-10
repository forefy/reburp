package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.ByteArray
import burp.api.montoya.core.Range
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.Base64
import java.util.regex.Pattern
import kotlinx.serialization.Serializable

@Serializable
data class BytesSearchInput(
    val data: String,
    val needle: String? = null,
    val regex: String? = null,
    val case_sensitive: Boolean = true,
    val from: Int? = null,
    val to: Int? = null
)

@Serializable
data class BytesSearchResult(
    val index: Int,
    val count: Int,
    val data_length: Int,
    val searched_range: String
)

@Serializable data class BytesSliceInput(val data: String, val start: Int, val end: Int)
@Serializable data class BytesAppendInput(val data: String, val suffix: String)
@Serializable data class BytesAllocInput(val length: Int, val fill: Int = 0, val at: Int = 0)
@Serializable data class BytesInput(val data: String)

@Serializable
data class BytesResult(
    val text: String,
    val length: Int,
    val base64: String,
    val first_byte: Int? = null
)

@Serializable
data class BytesInspectResult(
    val length: Int,
    val base64: String,
    val first_byte: Int?,
    val last_byte: Int?,
    val distinct_bytes: Int,
    val printable: Boolean,
    val temp_file_backed_length: Int
)

private fun ByteArray.toResult(): BytesResult = BytesResult(
    text = this.toString(),
    length = this.length(),
    base64 = Base64.getEncoder().encodeToString(this.getBytes()),
    first_byte = if (this.length() > 0) this.getByte(0).toInt() and 0xFF else null
)

/**
 * Raw byte inspection and manipulation, backed by Montoya's ByteUtils and ByteArray.
 *
 * All `data` fields are UTF-8 text; binary payloads round-trip through the `base64`
 * field on responses.
 */
fun Routing.bytesRoutes(api: MontoyaApi) {
    route("/api/utils/bytes") {

        // Locate and count occurrences of a literal or regex within a byte buffer.
        post("/search") {
            val req = runCatching { call.receive<BytesSearchInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.needle == null && req.regex == null) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Provide either 'needle' (literal) or 'regex' (java.util.regex pattern)"))
            }
            if (req.needle != null && req.regex != null) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Provide only one of 'needle' or 'regex', not both"))
            }
            val bu = api.utilities().byteUtils()
            val haystack = bu.convertFromString(req.data)
            val bounded = req.from != null && req.to != null
            if (bounded && (req.from!! < 0 || req.to!! > haystack.size || req.from >= req.to)) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid range: from=${req.from}, to=${req.to}, data length=${haystack.size}"))
            }
            val result = runCatching {
                if (req.regex != null) {
                    val pattern = Pattern.compile(req.regex)
                    if (bounded) {
                        Pair(bu.indexOf(haystack, pattern, req.from!!, req.to!!),
                             bu.countMatches(haystack, pattern, req.from, req.to))
                    } else {
                        Pair(bu.indexOf(haystack, pattern), bu.countMatches(haystack, pattern))
                    }
                } else {
                    val needle = bu.convertFromString(req.needle!!)
                    if (bounded) {
                        Pair(bu.indexOf(haystack, needle, req.case_sensitive, req.from!!, req.to!!),
                             bu.countMatches(haystack, needle, req.case_sensitive, req.from, req.to))
                    } else {
                        Pair(bu.indexOf(haystack, needle, req.case_sensitive),
                             bu.countMatches(haystack, needle, req.case_sensitive))
                    }
                }
            }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Search failed: ${it.message}"))
            }
            call.respond(
                BytesSearchResult(
                    index = result.first,
                    count = result.second,
                    data_length = haystack.size,
                    searched_range = if (bounded) "[${req.from}, ${req.to})" else "[0, ${haystack.size})"
                )
            )
        }

        // Extract a half-open byte range.
        post("/slice") {
            val req = runCatching { call.receive<BytesSliceInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val source = ByteArray.byteArray(req.data)
            if (req.start < 0 || req.end > source.length() || req.start >= req.end) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid slice: start=${req.start}, end=${req.end}, length=${source.length()}"))
            }
            val range: Range = Range.range(req.start, req.end)
            call.respond(source.subArray(range).toResult())
        }

        // Concatenate a suffix onto a buffer.
        post("/append") {
            val req = runCatching { call.receive<BytesAppendInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            call.respond(ByteArray.byteArray(req.data).withAppended(req.suffix).toResult())
        }

        // Allocate a zeroed buffer and optionally write a fill byte at an offset.
        post("/alloc") {
            val req = runCatching { call.receive<BytesAllocInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            if (req.length < 0 || req.length > 1_048_576) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'length': ${req.length}. Must be between 0 and 1048576."))
            }
            if (req.fill < 0 || req.fill > 255) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'fill': ${req.fill}. Must be a byte value between 0 and 255."))
            }
            val buffer = ByteArray.byteArrayOfLength(req.length)
            if (req.length > 0) {
                if (req.at < 0 || req.at >= req.length) {
                    return@post call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid 'at': ${req.at}. Must be between 0 and ${req.length - 1}."))
                }
                buffer.setBytes(req.at, req.fill)
            }
            call.respond(buffer.toResult())
        }

        // Summarise a buffer: size, byte extremes, printability.
        post("/inspect") {
            val req = runCatching { call.receive<BytesInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val source = ByteArray.byteArray(req.data)
            val working = source.copy()
            val raw = working.getBytes()
            val temp = runCatching { source.copyToTempFile().length() }.getOrElse { working.length() }
            call.respond(
                BytesInspectResult(
                    length = working.length(),
                    base64 = Base64.getEncoder().encodeToString(raw),
                    first_byte = if (working.length() > 0) working.getByte(0).toInt() and 0xFF else null,
                    last_byte = if (working.length() > 0) working.getByte(working.length() - 1).toInt() and 0xFF else null,
                    distinct_bytes = raw.toSet().size,
                    printable = raw.all { it >= 0x20 || it == 0x09.toByte() || it == 0x0A.toByte() || it == 0x0D.toByte() },
                    temp_file_backed_length = temp
                )
            )
        }

        // Byte-level string conversion round trip, exposing Burp's own codec.
        post("/convert") {
            val req = runCatching { call.receive<BytesInput>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${it.message}"))
            }
            val bu = api.utilities().byteUtils()
            val raw = bu.convertFromString(req.data)
            call.respond(
                BytesResult(
                    text = bu.convertToString(raw),
                    length = raw.size,
                    base64 = Base64.getEncoder().encodeToString(raw),
                    first_byte = if (raw.isNotEmpty()) raw[0].toInt() and 0xFF else null
                )
            )
        }
    }
}

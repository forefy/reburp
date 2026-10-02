package com.reburp

/**
 * Text matching for the activity log filter.
 *
 * The filter used to hand the whole table to RowFilter.regexFilter, which searches every
 * model column. That had two consequences: a target host could never match, because no
 * column carries one, and a short number matched a row id, a duration and a path fragment
 * at the same time. Matching is now against named fields, free of Swing so it can be tested.
 */

/** Which fields the filter text is matched against. Host and path is the common case. */
enum class FilterTarget(val label: String) {
    HOST_AND_PATH("Host + Path"),
    ALL_COLUMNS("All columns"),
    HOST("Host"),
    PATH("Path"),
    NOTES("AI Notes"),
}

/** The text a row offers up for matching, given the selected fields. */
fun filterHaystack(e: LogEntry, target: FilterTarget, bodies: Boolean): List<String> {
    val fields = when (target) {
        FilterTarget.HOST_AND_PATH -> listOf(e.host, e.path)
        FilterTarget.HOST          -> listOf(e.host)
        FilterTarget.PATH          -> listOf(e.path)
        FilterTarget.NOTES         -> listOf(e.notes)
        FilterTarget.ALL_COLUMNS   -> listOf(
            e.id.toString(), e.timestamp, e.method,
            if (e.status == 0) "" else e.status.toString(),
            e.durationMs.toString(), e.host, e.path, e.notes,
        )
    }
    // The bodies hold the raw target request and response, so a parameter name or a token
    // seen in the traffic becomes searchable too. Off by default: it is the slow path.
    return if (bodies) fields + e.requestBody + e.responseBody else fields
}

/**
 * Matching is literal and case insensitive. Hosts are full of dots, and a dot that quietly
 * means "any character" makes a host filter look like it works when it does not.
 */
fun matchesFilter(e: LogEntry, needle: String, target: FilterTarget, bodies: Boolean): Boolean =
    needle.isBlank() || filterHaystack(e, target, bodies).any { it.contains(needle, ignoreCase = true) }

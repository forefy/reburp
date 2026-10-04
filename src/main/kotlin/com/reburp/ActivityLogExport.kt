package com.reburp

/**
 * Export of the activity log table to CSV or a self-contained HTML report.
 *
 * Kept free of Swing so the formatting can be unit tested. The tab decides which rows and
 * which columns to hand over, so an export mirrors what the table currently shows: the
 * active filter, the active sort, and the column order the user dragged into place.
 */

/** A table column, in the model order the table declares them. SESSION is export only. */
enum class LogColumn(val title: String, val value: (LogEntry) -> String) {
    ID("#",        { it.id.toString() }),
    TIME("Time",   { it.timestamp }),
    METHOD("Method", { it.method }),
    STATUS("Status", { if (it.status == 0) "" else it.status.toString() }),
    MS("ms",       { it.durationMs.toString() }),
    HOST("Host",   { it.host }),
    PATH("Path",   { it.path }),
    NOTES("AI Notes", { it.notes }),
    SESSION("Session", { it.sessionId ?: "" }),
}

/**
 * One row plus the raw messages to embed. The reburp API call and its reply are the
 * extension talking to itself, so they are optional: a report about the target is
 * usually what is wanted, and the API pair doubles the size of every entry.
 */
data class ExportRow(
    val entry: LogEntry,
    val apiRequest: String?,
    val apiResponse: String?,
    val targetRequest: String?,
    val targetResponse: String?,
) {
    /** Titled messages that actually have content, in reading order. */
    fun messages(): List<Pair<String, String>> = listOf(
        "Target Request" to targetRequest,
        "Target Response" to targetResponse,
        "API Request" to apiRequest,
        "API Response" to apiResponse,
    ).mapNotNull { (title, body) -> body?.takeIf { it.isNotBlank() }?.let { title to it } }
}

// ── CSV ───────────────────────────────────────────────────────────────────────

fun exportCsv(rows: List<ExportRow>, columns: List<LogColumn>): String = buildString {
    append(columns.joinToString(",") { csvField(it.title) })
    append("\r\n")
    for (row in rows) {
        append(columns.joinToString(",") { csvField(it.value(row.entry)) })
        append("\r\n")
    }
}

/**
 * RFC 4180 quoting, plus a leading apostrophe on anything a spreadsheet would run as a
 * formula. Paths and notes come from the target application, so they are untrusted.
 */
private fun csvField(raw: String): String {
    val value = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r") "'$raw" else raw
    return if (value.any { it == '"' || it == ',' || it == '\n' || it == '\r' })
        "\"${value.replace("\"", "\"\"")}\""
    else value
}

// ── HTML ──────────────────────────────────────────────────────────────────────

fun exportHtml(rows: List<ExportRow>, columns: List<LogColumn>, generatedAt: String): String = buildString {
    val span = columns.size + 1   // data columns plus the expand toggle

    append(htmlHead())
    append("<header class=\"topbar\">\n")
    append("  <div class=\"titles\">\n")
    append("    <h1>reburp activity log</h1>\n")
    append("    <p class=\"meta\">${rows.size} call${if (rows.size == 1) "" else "s"}")
    append(" - exported ${esc(generatedAt)}</p>\n")
    append("  </div>\n")
    append("""
  <div class="controls">
    <button type="button" id="expand-all">Expand all</button>
    <button type="button" id="collapse-all">Collapse all</button>
    <div class="themes" role="group" aria-label="Colour theme">
      <button type="button" data-theme-set="auto">Auto</button>
      <button type="button" data-theme-set="light">Light</button>
      <button type="button" data-theme-set="dark">Dark</button>
    </div>
  </div>
</header>

""")
    append("<table>\n<thead><tr>")
    columns.forEach { col ->
        val cls = when (col) {
            LogColumn.MS -> " class=\"num\""
            LogColumn.PATH -> " class=\"grow\""
            else -> ""
        }
        append("<th$cls>${esc(col.title)}</th>")
    }
    append("<th class=\"toggle-col\"><span class=\"sr\">Messages</span></th></tr></thead>\n<tbody>\n")

    rows.forEachIndexed { i, row ->
        val e = row.entry
        val messages = row.messages()
        append(
            if (messages.isEmpty()) "<tr class=\"plain\">"
            else "<tr class=\"call\" data-detail=\"d$i\" tabindex=\"0\" aria-expanded=\"false\">"
        )
        for (col in columns) {
            val text = esc(col.value(e))
            when (col) {
                LogColumn.METHOD -> append("<td class=\"method m-${esc(e.method.lowercase())}\">$text</td>")
                LogColumn.STATUS -> append("<td class=\"status ${statusClass(e.status)}\">$text</td>")
                LogColumn.MS     -> append("<td class=\"num ${msClass(e.durationMs)}\">${text}ms</td>")
                LogColumn.PATH   -> append("<td class=\"path grow\">$text</td>")
                else             -> append("<td>$text</td>")
            }
        }
        append(
            if (messages.isEmpty()) "<td class=\"toggle-col\"></td>"
            else "<td class=\"toggle-col\"><span class=\"chev\" aria-hidden=\"true\"></span></td>"
        )
        append("</tr>\n")

        if (messages.isEmpty()) return@forEachIndexed

        // The messages get a row of their own spanning every column. Nested inside the
        // last cell they would be squeezed into its width, which is unreadable.
        append("<tr class=\"detail\" id=\"d$i\" hidden><td colspan=\"$span\">\n<div class=\"msgs\">")
        messages.forEach { (title, body) ->
            append("<section><h2>${esc(title)}</h2><pre>${esc(body)}</pre></section>")
        }
        append("</div>\n</td></tr>\n")
    }

    append("</tbody>\n</table>\n")
    append(HTML_SCRIPT)
    append("</body>\n</html>\n")
}

private fun statusClass(code: Int) = when (code) {
    in 200..299 -> "s2xx"
    in 300..399 -> "s3xx"
    in 400..499 -> "s4xx"
    in 500..599 -> "s5xx"
    else        -> "s0"
}

private fun msClass(ms: Long) = when {
    ms > 3000L -> "slow"
    ms > 1000L -> "warm"
    else       -> ""
}

private fun esc(s: String) = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

/** Tokens for the dark palette, reused by the system default and the explicit choice. */
private val DARK_TOKENS = """
    --bg: #1a1a1c; --fg: #e9e9ec; --muted: #9b9ba2; --line: #333337;
    --head: #232326; --hover: #26262a; --pre: #121214; --accent: #ff9d5c;
    --btn: #2a2a2e; --btn-fg: #d8d8dc; --btn-on: #3d3d44;
    --get: #5aa9e6; --post: #f0a05a; --put: #8fcf5f; --patch: #c08ef0; --del: #f06a6a;
    --s2: #5fc77a; --s3: #5aa9e6; --s4: #f0a05a; --s5: #f06a6a;
    --warm: #f0a05a; --slow: #f06a6a;
""".trim()

private fun htmlHead() = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>reburp activity log</title>
<style>
  :root {
    --bg: #ffffff; --fg: #1b1b1f; --muted: #67676e; --line: #e3e3e8;
    --head: #f4f4f7; --hover: #f0f0f4; --pre: #f7f7fa; --accent: #b4531a;
    --btn: #f1f1f5; --btn-fg: #3a3a42; --btn-on: #dcdce4;
    --get: #1f6fb5; --post: #b3661a; --put: #447d22; --patch: #6f45a8; --del: #b63434;
    --s2: #2f8440; --s3: #1f6fb5; --s4: #b3661a; --s5: #b63434;
    --warm: #b3661a; --slow: #b63434;
  }
  @media (prefers-color-scheme: dark) {
    :root:not([data-theme="light"]) { $DARK_TOKENS }
  }
  :root[data-theme="dark"] { $DARK_TOKENS }

  * { box-sizing: border-box; }
  body { margin: 0; padding: 20px 24px 60px; background: var(--bg); color: var(--fg);
         font: 14px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
  .sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }

  .topbar { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end;
            justify-content: space-between; margin-bottom: 16px; }
  h1 { font-size: 19px; margin: 0 0 2px; }
  .meta { color: var(--muted); margin: 0; font-size: 13px; }
  .controls { display: flex; gap: 8px; align-items: center; }
  .themes { display: flex; border: 1px solid var(--line); border-radius: 6px; overflow: hidden; }
  .themes button { border: 0; border-radius: 0; }
  .themes button + button { border-left: 1px solid var(--line); }
  button { font: inherit; font-size: 12px; padding: 5px 11px; cursor: pointer;
           background: var(--btn); color: var(--btn-fg); border: 1px solid var(--line);
           border-radius: 6px; }
  button:hover { background: var(--btn-on); }
  button[aria-pressed="true"] { background: var(--btn-on); color: var(--fg); font-weight: 600; }

  table { border-collapse: collapse; width: 100%; font-size: 13px; table-layout: auto; }
  th, td { text-align: left; padding: 6px 10px; border-bottom: 1px solid var(--line);
           vertical-align: top; white-space: nowrap; }
  thead th { position: sticky; top: 0; z-index: 2; background: var(--head);
             font-weight: 600; border-bottom: 2px solid var(--line); }
  .grow { width: 100%; white-space: normal; }
  tr.call { cursor: pointer; }
  tr.call:hover td { background: var(--hover); }
  tr.call:focus-visible { outline: 2px solid var(--accent); outline-offset: -2px; }
  .num { text-align: right; font-variant-numeric: tabular-nums; }
  .path, .method, .status, .num { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
  .path { word-break: break-word; }
  .method { font-weight: 700; }
  .m-get { color: var(--get); } .m-post { color: var(--post); } .m-put { color: var(--put); }
  .m-patch { color: var(--patch); } .m-delete { color: var(--del); }
  .s2xx { color: var(--s2); } .s3xx { color: var(--s3); }
  .s4xx { color: var(--s4); } .s5xx { color: var(--s5); }
  .warm { color: var(--warm); } .slow { color: var(--slow); }

  .toggle-col { width: 28px; text-align: center; }
  .chev { display: inline-block; border: 5px solid transparent; border-left-color: var(--muted);
          margin-left: 2px; transform-origin: 2px 50%; transition: transform .12s ease; }
  tr.call[aria-expanded="true"] .chev { transform: rotate(90deg); border-left-color: var(--accent); }

  tr.detail > td { padding: 0 10px 16px; background: var(--hover); white-space: normal; }
  /* Request and response sit side by side while there is room, and stack when there is not. */
  .msgs { display: grid; gap: 12px; grid-template-columns: repeat(auto-fit, minmax(420px, 1fr)); }
  .msgs h2 { font-size: 11px; text-transform: uppercase; letter-spacing: .06em;
             color: var(--muted); margin: 12px 0 5px; font-weight: 600; }
  pre { background: var(--pre); border: 1px solid var(--line); border-radius: 5px;
        padding: 10px 12px; margin: 0; overflow: auto; max-height: 460px;
        white-space: pre-wrap; overflow-wrap: anywhere; tab-size: 2;
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px;
        line-height: 1.45; }

  @media print {
    .controls { display: none; }
    tr.detail[hidden] { display: none; }
    pre { max-height: none; }
  }
</style>
</head>
<body>
""".trimStart()

private val HTML_SCRIPT = """
<script>
(function () {
  var root = document.documentElement;

  // Theme: Auto follows the operating system, Light and Dark override it. Remembered per browser.
  var themeButtons = document.querySelectorAll('[data-theme-set]');
  function applyTheme(name) {
    if (name === 'light' || name === 'dark') root.setAttribute('data-theme', name);
    else { root.removeAttribute('data-theme'); name = 'auto'; }
    themeButtons.forEach(function (b) {
      b.setAttribute('aria-pressed', String(b.dataset.themeSet === name));
    });
    try { localStorage.setItem('reburp-theme', name); } catch (e) {}
  }
  themeButtons.forEach(function (b) {
    b.addEventListener('click', function () { applyTheme(b.dataset.themeSet); });
  });
  var saved = null;
  try { saved = localStorage.getItem('reburp-theme'); } catch (e) {}
  applyTheme(saved || 'auto');

  // Expanding a row reveals its own full width detail row.
  function setOpen(row, open) {
    var detail = document.getElementById(row.dataset.detail);
    if (!detail) return;
    detail.hidden = !open;
    row.setAttribute('aria-expanded', String(open));
  }
  document.querySelectorAll('tr.call').forEach(function (row) {
    row.addEventListener('click', function () {
      setOpen(row, row.getAttribute('aria-expanded') !== 'true');
    });
    row.addEventListener('keydown', function (ev) {
      if (ev.key !== 'Enter' && ev.key !== ' ') return;
      ev.preventDefault();
      setOpen(row, row.getAttribute('aria-expanded') !== 'true');
    });
  });
  function setAll(open) {
    document.querySelectorAll('tr.call').forEach(function (row) { setOpen(row, open); });
  }
  document.getElementById('expand-all').addEventListener('click', function () { setAll(true); });
  document.getElementById('collapse-all').addEventListener('click', function () { setAll(false); });
})();
</script>
"""

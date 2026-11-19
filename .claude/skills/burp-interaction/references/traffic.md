# Traffic: reading, replaying and fuzzing through Burp

Part of the `burp-interaction` skill; its SKILL.md has setup, discovery and the shared
conventions. For the exact fields of any endpoint named here, run
`scripts/reburp.py describe METHOD PATH` (or `endpoints --grep WORD`) from the skill folder
rather than guessing.

Contents: getting traffic in front of you, scope first, replaying a history item, sending
requests, analysis helpers, testing patterns, changing what Burp does to traffic.

## Getting traffic in front of you

`GET /api/proxy/history` is Burp's proxy history, newest last, filterable server-side by `host`,
`method`, `mime_type`, `status_min`/`status_max`, `has_response`, `edited_only`, `scope_only` and
`listener_port`. Filter there rather than pulling everything and grepping.

- `GET /api/proxy/history/search?regex=` matches request+response bytes;
  `/api/proxy/history/response-search` returns snippets with context from response bodies only.
- `GET /api/sitemap` and `/api/sitemap/search` cover the whole target tree, including what was
  crawled but never proxied - use it when history looks thin.
- `POST /api/utils/rank` sorts history by how anomalous each exchange looks. Scores are relative to
  the set you pass, so narrow it with `host` or `scope_only=true`; raise `max_scored` (default 2000
  most recent) for a large history and check the `truncated` flag.
- `PATCH /api/proxy/history/annotate` writes notes and highlight colours onto matching entries, so
  findings stay visible to the user in Burp.
- `POST /api/engagement/analyze-target` summarises a host's attack surface;
  `POST /api/engagement/find-references` finds where a URL or string is referenced.

If the host is missing from history entirely, no traffic is being proxied yet - the user has to
browse through Burp (Proxy -> Open browser is the zero-config option) before there is anything to read.

## Scope first

`GET /api/scope/check?url=` before you touch a target, `POST /api/scope/include` /
`/api/scope/exclude` to change it, `GET /api/scope/rules` to see the rules. Keeping the engagement
target in scope is what makes `scope_only=true` filters and Burp's own tooling behave.

## Replaying a history item

`POST /api/utils/rank` and the history listings report Burp's own message `id`, but
`GET /api/proxy/history/{index}` takes a zero-based position in the current list, so
`/api/proxy/history/287` is the 288th entry, not id 287. To replay a ranked item:

1. Find it by id: `scripts/reburp.py history-item 287 --host app.example.test`, or page
   `GET /api/proxy/history?host=app.example.test&include_body=true` and pick the entry whose
   `id` matches.
2. Send its raw request: `POST /api/http/send` with `host`, `port`, `use_https` (the entry's
   `secure`) and `request` (the entry's `final_request`, or `request`). `history-item` prints
   this as `send_body`. reburp cannot replay by id alone, and a body without `host` is a 400.

## Sending requests

- `POST /api/http/send` - one HTTP/1.1 request through Burp's engine (so it lands in history and
  uses Burp's TLS, upstream proxies and cookie jar). Raw `request` string or structured
  `method`/`path`/`headers`/`body`. `redirect_mode` is `ALWAYS|NEVER|SAME_HOST|IN_SCOPE`. A failed
  connection is `502`, not an exception.
- `POST /api/http/send/http2` - explicit HTTP/2 with `pseudo_headers`.
- `POST /api/http/send-batch` - up to 20 concurrent, ordered results.
- `POST /api/http/send-with-auth` - for JWT APIs: on `401`/`403` it runs the `auth` request, pulls
  the token with `token_path` (e.g. `$.access`), injects it per `inject_as` and retries. Prefer it
  over hand-rolling token refresh.
- `POST /api/repeater/send` / `POST /api/intruder/send` put a raw request in front of the user in
  Burp's own tabs; Repeater does not auto-send. Their body is `raw_request` (plus an optional
  `tab_name`), not `host` + `request`: the target comes from the request's Host header.
- `POST /api/organizer/send` parks an interesting request in the Organizer for later.

## Analysis helpers

`POST /api/http/parse/request` and `/parse/response` turn raw messages into headers and parameters;
`/api/http/params` extracts parameters; `/api/http/reflect` finds reflected input;
`/api/http/insertion-points` gives Burp's own payload offsets; `/api/http/diff` diffs two requests.
The `/api/http/message/...` group inspects and rewrites a message without sending it (headers,
parameters, retargeting the service, markers, response edits). `/api/utils/...` covers URL, base64,
HTML, hex, hashes, digests, JWT decode/encode, compress/decompress, JSON pointer edits, byte
buffers and number radixes - use them instead of local re-implementations so the work is logged in Burp.

## Testing patterns

- **Access control (BOLA/BFLA)**: `POST /api/http/auth-diff` sends the same request with and without
  `auth_header` and diffs the responses; pass `second_auth_header` to compare two privilege levels.
- **Hidden content**: `POST /api/engagement/discover-content` (wordlist, `found_status_codes`,
  `concurrency`) or `POST /api/http/fuzz`, which substitutes each word into the `{word}` placeholder
  in `path_template` and filters out catch-all pages with `filter_status`, `exclude_body_size` and
  `exclude_body_contains`.
- **Rate limiting**: `POST /api/http/rate-test` fires `count` requests at `concurrency`.
- **CSRF**: `POST /api/engagement/csrf-poc` generates the PoC HTML.
- **High throughput**: the request engine. `POST /api/http/engine` (concurrency limit, throttle,
  retries) returns an `engine_id`; `POST /api/http/engine/{id}/queue` adds requests (each may carry
  a `label` that comes back with its result); `POST /api/http/engine/{id}/send` starts it and
  returns an execution id; then `.../execution/{id}/stats`, `/pause`, `/resume`, `/cancel`, and
  `/await` (bounded, `202` when it is still running - call it again) to collect results. Discard the
  handles when done. Use this over a loop of `send` for anything above a few dozen requests.
- **WebSockets**: read with `GET /api/proxy/websocket/history`; drive one yourself with
  `POST /api/websocket/connect`, then `/send-text`, `/send-binary`, `GET /{id}/messages`, `DELETE /{id}`.

## Changing what Burp does to traffic

`/api/proxy/match-replace` lists, adds, updates and deletes match-and-replace rules;
`/api/proxy/intercept` toggles interception (leaving it on will stall the user's browsing);
`/api/cookies` reads and writes Burp's cookie jar; `/api/sessions/rules` lists and deletes session
handling rules (Burp has no add-header action, so `POST /api/sessions/rules/add-header` always
returns `501` - inject a header with a `request_header` match-and-replace rule, or `send-with-auth`).
`/api/config/project` and `/api/config/user` read and write Burp's own options as JSON.
Undo any rule you add once the task is done; these settings outlive your session.

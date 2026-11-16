---
name: reburp-api
description: Call the reburp REST API, which exposes Burp Suite's full Montoya API on http://127.0.0.1:9090. Use for any Burp work over HTTP - checking the extension is up, discovering endpoints from the live OpenAPI spec, request/response conventions, error codes, and the safety rules for the unauthenticated port. Start here, then use reburp-traffic for proxy history, sending requests, and related traffic work.
---

# Calling the reburp API

reburp is a Burp Suite extension that serves the Montoya API as a local REST API on
`http://127.0.0.1:9090` (hardcoded in `BurpRestApiExtension.kt`). 206 operations across 30 tags.
Anything you would click in Burp - proxy history, sending a request, scope, a scan, decoding a
token, even clicking a button in the Burp window - is a JSON call.

Prefer these calls over telling the user to click in Burp.

## Every session starts here

```bash
.codex/skills/reburp-api/scripts/reburp.py status
```

- Connection refused means the extension is not loaded. Build it with `./gradlew shadowJar` (Java 17+)
  or download `reburp-*.jar` from https://github.com/forefy/reburp/releases, then in Burp:
  **Extensions -> Installed -> Add**, type **Java**, pick the jar, **Next**. Load the unversioned
  `build/libs/reburp.jar`, not the versioned copy: Burp reloads from the path it loaded from, and
  the versioned name changes every release.
- `edition` tells you whether Pro-only groups (Scanner, Collaborator, AI) will work; on Community
  they return `403` with a message.
- `build` is the short commit of the jar Burp is actually running. After a rebuild, compare it to
  `git rev-parse --short HEAD` before believing a code change took effect (`-dirty` = uncommitted).

## Discovering endpoints

The live spec at `GET /openapi.json` is the contract; Swagger UI is at
http://127.0.0.1:9090/docs. Do not guess shapes - endpoints are added and renamed often.

```bash
S=.codex/skills/reburp-api/scripts/reburp.py
$S endpoints --tag Proxy             # one group, compact
$S endpoints --grep 'websocket|jwt'  # search paths, summaries and field names
$S describe POST /api/http/send      # full contract, $refs resolved, with examples
```

`references/endpoints.md` is the same compact listing for every group, checked in so it is
readable without Burp running. It is generated, so it can lag the code: regenerate it with
`$S endpoints > .codex/skills/reburp-api/references/endpoints.md`, and trust `describe` over it.

## Making calls

```bash
$S call GET  /api/proxy/history -q limit=10 -q scope_only=true
$S call POST /api/http/send -d '{"host":"example.com","method":"GET","path":"/"}'
$S call POST /api/scanner/audit -d @audit.json
$S call GET  /api/ui/screenshot -o /tmp/burp.png     # non-JSON bodies need -o
```

Plain `curl` works just as well; the script only adds spec lookup, JSON pretty-printing and a
non-zero exit on HTTP errors. Set `REBURP_URL` if the port was changed.

## Conventions that hold across the API

- Request and response bodies are JSON. Errors are `{"error": "..."}` with a 4xx/5xx status, so
  always check the status, not just the body.
- Targets are given as `host` + `port` (default `443`) + `use_https` (default `true`) - *not* a URL.
  A few newer endpoints spell the flag `https` or `secure`; check the field list.
- Request bodies accept either a raw `request` string (HTTP/1.1, CRLF normalised for you) or
  structured `method` + `path` + `headers` + `body`. With structured fields, `body` may be a JSON
  object and `Host`, `Content-Type: application/json` and `Content-Length` are filled in for you.
- List endpoints page with `offset` and `limit` (usually default 100, capped at 500-1000) and omit
  message bodies unless you pass `include_body=true`; `max_body` caps each body at 512 KB by default.
  Fetch one full item with `GET /api/proxy/history/{index}` rather than pulling bodies in bulk.
- Indexes into proxy history are positional in the current history, so they shift as traffic
  arrives. Re-read the list right before acting on an index.
- Enum values are upper-case Montoya names (`HIGH`, `GREEN`, `LEGACY_ACTIVE_AUDIT_CHECKS`). The
  accepted set is in each endpoint's spec entry; `GET /api/meta/enums` additionally gives the
  highlight colours and Organizer statuses with Burp's own display labels.
- Handles (`engine_id`, scan task ids, websocket ids, session ids) live in extension memory only and
  die with the extension; nothing is retried for you after a reload.
- Every call shows up in the **reburp** tab in Burp and in `GET /api/log`, so the user can watch
  what you did. `POST /api/session` with a label tags the calls that follow (pass `session_id`),
  which makes a run easy to separate later.

## Safety

- The port is **unauthenticated** and answers **any origin**. Only ever target `127.0.0.1`, and
  never expose or proxy it off loopback.
- `POST /api/utils/shell/execute` and `/execute-raw` are host command execution and return `403`
  unless the user set `REBURP_ENABLE_SHELL=1` in Burp's own environment. Do not ask them to enable
  it unless the task truly needs it, and say what you will run first.
- `POST /api/burp/shutdown` and `POST /api/meta/extension/unload` kill Burp or the API. Never call
  them to "reset" something.
- Only scan, fuzz or send requests at hosts the user confirmed are in scope for their engagement.
  Active audits and `discover-content` send attack traffic.

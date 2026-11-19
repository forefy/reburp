---
name: burp-interaction
description: Interacting with BurpSuite over the reburp extension that exposes the full Montoya API as a local REST API. Use when driving Burp programmatically - reading proxy history, sending HTTP requests through Burp, managing scope, running scans, decoding/encoding, or ranking traffic - instead of clicking the Burp UI. Also covers installing, building, or loading reburp into Burp when it is not yet set up.
compatibility: Requires BurpSuite
---

# Driving Burp Suite over reburp

reburp is a Burp extension that serves the Montoya API as a REST API on `http://127.0.0.1:9090`.
Prefer these endpoints over describing manual clicks in the Burp UI.

## Before you start

1. Confirm the server is up: `GET /api/status`. If it refuses to connect, reburp is not
   loaded - see **Install reburp** below and guide the user through it, then retry.
2. Read the live contract instead of guessing endpoint shapes; see **Finding endpoints** below.
   Endpoint names in this file can drift; the spec is truth.
3. `edition` in the status says whether Pro-only groups (Scanner, Collaborator, AI) will work.
4. You only need a browser for traffic that must go *through the proxy* (real or authenticated
   sessions). Pure API work - sending requests, scope, scans - needs no browser. If you do need
   one, see **Capturing browser traffic** below.

## Finding endpoints

The live spec at `GET /openapi.json` is the contract, but it is about 400 KB; do not read it
whole. `scripts/reburp.py` in this skill folder (Python 3, standard library only) reads it for you:

```bash
R=<this skill folder>/scripts/reburp.py
$R status                             # is reburp up, which build
$R endpoints --tag Proxy              # one group, one line per endpoint with its fields
$R endpoints --grep 'websocket|jwt'   # search paths, summaries and field names
$R describe POST /api/http/send       # one endpoint's full contract, $refs resolved
$R call POST /api/http/send -d '{"host":"example.com","method":"GET","path":"/"}'
$R call GET /api/ui/screenshot -o /tmp/burp.png   # non-JSON bodies need -o
$R history-item 287 --host example.com            # a history entry by Burp's id, ready to replay
```

`call` exits non-zero on an HTTP error. Plain `curl` works just as well; set `REBURP_URL` for
the script if the port was changed. Swagger UI is at `http://127.0.0.1:9090/docs`.

## Install reburp

Only needed when `GET /api/status` does not connect. Get the jar, then load it:

- Download the latest `reburp-*.jar` from https://github.com/forefy/reburp/releases, or
- Build it (needs Java 17+): `./gradlew shadowJar`, then print the absolute path for the user:
  `ls "$PWD"/build/libs/reburp.jar`. Prefer this unversioned jar over the versioned copy
  beside it: its path survives a version bump, so Burp keeps reloading the extension.

Then in Burp: **Extensions -> Installed -> Add**, type **Java**, pick the jar, **Next**. It
starts on port 9090 and adds a **reburp** tab. Re-check `GET /api/status` to confirm.

## Reloading reburp after a rebuild

Burp keeps the extension's threads alive until reburp's own stop completes, and it reloads on
every jar write when the row's **Auto-reload** box is ticked. Both bit hard once: a reload
during a long request hung the unload, and the rebuilds that followed stacked loads until
Burp ran out of heap. So:

1. Leave **Auto-reload unticked** on the reburp row while building. Build with
   `./gradlew shadowJar`, then ask for one reload.
2. Stop calling the API before the reload, and never reload while a request is in flight.
3. Reload by **unticking and re-ticking Loaded** once (Ctrl-click did not reload in Burp 2026.9).
4. Prove it took: `GET /api/status` must report a `build` whose commit matches
   `git rev-parse --short HEAD` of the tree you built from (`-dirty` means uncommitted
   changes were in). The version string alone is the same for every build of a branch.
   Burp's **Extensions -> Output** pane shows the same line: `reburp <version> (<build>) listening`.
5. `python3 tools/smoke_test.py` checks that build match as its second step and fails loudly
   when Burp is holding an older jar.

If the port answers nothing after a reload, or Loaded is ticked with an empty Output pane,
the reload is wedged: `jcmd <burp pid> Thread.print` will show `RestApiServer.stop` awaiting
or `ActivityLogTab.<init>` parsing. The only recovery is a full quit of Burp; confirm the
process id changed before trusting the next load. Do not drive the Loaded checkbox through
`/api/ui` to reload reburp itself: the unload stops the server mid-call.

## Capturing browser traffic

Only needed when the task needs real proxied traffic (logged-in sessions, a real browser). Check
`GET /api/proxy/history` for the target host first; if it is not there, follow
`references/browser-traffic.md`, which ranks the options from "already flowing" to a throwaway
Chromium you can drive yourself.

## Seeing and driving Burp's window

When you do need to *see* Burp (confirm a Collaborator hit, show the user a Repeater result),
skip desktop screenshots and use `/api/ui`: `select-tab`, then `select-row` with `{"last": true}`,
then `screenshot`. It paints Burp's window from inside the JVM, so it works even when Burp is
minimized, and Java/Swing does not block it the way it blocks OS-level accessibility.

For anything the purpose-built calls do not name (an extension's form, a combo box, a right-click
menu, a confirmation dialog): `GET /api/ui/elements?text=<label>` to get the element's `id`, then
`POST /api/ui/click` with that id (`"button": "right"` opens its context menu; list again to see the
menu items, or `click-button` one by label), `POST /api/ui/type` for text fields, `POST /api/ui/select`
with `text` or `index` for combo boxes, lists, tables and trees, and `POST /api/ui/key` (e.g. `ESCAPE`,
`ENTER`) for dialogs. Ids are paths in the live layout, so re-list after switching tabs or opening a
dialog, and check `GET /api/ui/windows` when a dialog appears: it is `w1`, not part of `w0`.
Burp's own controls (Repeater's Send, for one) are obfuscated custom widgets, not JButtons: find them
by `text`, not `type`. `click-button` matches labels and tooltips, so Burp's icon-only editor
toolbar toggles work by name: `{"text": "line wrapping"}` flips wrapping in the first editor that
has it showing. Their tooltip names the next action, so `GET /api/ui/elements?text=line wrapping`
tells you the current state ("Enable line wrapping" means it is off) without clicking.

## Common tasks -> endpoints

| Goal | Call |
|------|------|
| Extension / Burp version | `GET /api/status` |
| List or search proxy history | `GET /api/proxy/history`, `GET /api/proxy/history/search` |
| Send an HTTP request through Burp | `POST /api/http/send` (HTTP/1.1 and HTTP/2) |
| Send with auth token auto-injected | `POST /api/http/send-with-auth` |
| Check / edit scope | `GET /api/scope/check`, `POST /api/scope/include`, `POST /api/scope/exclude` |
| Site map | `GET /api/sitemap`, `GET /api/sitemap/search` |
| Start a scan, poll it (Pro) | `POST /api/scanner/audit`, `GET /api/scanner/tasks/{id}` |
| Collaborator payload + poll (Pro) | `POST /api/collaborator/generate`, `GET /api/collaborator/poll/{secretKey}` |
| Encode / decode / hash / JWT | `POST /api/utils/...` |
| Rank history by how anomalous it looks | `POST /api/utils/rank` (body: `limit`, `offset`, `max_scored`, `scope_only`, `host`) |
| Replay a history or ranked item | `GET /api/proxy/history/by-id/{id}` (or `reburp.py history-item ID`), then `POST /api/http/send`; see below |
| Send request to Repeater / Intruder | `POST /api/repeater/send`, `POST /api/intruder/send` (body: `raw_request`, optional `tab_name`) |
| See the Burp window | `GET /api/ui/screenshot` (PNG; works while minimized or on another desktop) |
| Switch tab (Burp's or an extension's sub-tab), click a button, pick a row | `GET /api/ui/tabs` (`nested` lists sub-tabs), `POST /api/ui/select-tab`, `POST /api/ui/click-button`, `POST /api/ui/select-row` |
| Drive any other element: combo, text field, list, tree, dialog, context menu | `GET /api/ui/elements?text=...` for ids, then `POST /api/ui/click`, `/type`, `/select`, `/key`; `GET /api/ui/windows` for dialogs and popups |

Fuzzing, access-control diffs, JWT re-auth, the request engine, WebSockets and match-and-replace
are in `references/traffic.md`.

**History ids are not indexes.** Rank results and history entries carry Burp's own `id`;
`GET /api/proxy/history/{index}` takes a zero-based position in the current list. Fetch by id
with `GET /api/proxy/history/by-id/{id}`, never `/api/proxy/history/<id>`. To replay item 287:
`reburp.py history-item 287` (or the by-id call), then `POST /api/http/send` with its `host`,
`port`, `use_https` (the entry's `secure`) and raw `request`, which `history-item` prints as
`send_body`. reburp cannot replay by id alone.

## Conventions

- All bodies and responses are JSON. Errors are `{ "error": "..." }` with a 4xx/5xx status, so
  check the status, not just the body.
- Targets are `host` + `port` (default `443`) + `use_https` (default `true`), not a URL. A few
  newer endpoints spell the flag `https` or `secure`; check the field list.
- Requests go either as a raw `request` string (CRLF normalised for you) or as structured
  `method` + `path` + `headers` + `body`; with structured fields `body` may be a JSON object and
  `Host`, `Content-Type` and `Content-Length` are filled in.
- Lists page with `offset` and `limit` and omit bodies unless `include_body=true`; `max_body`
  caps each body at 512 KB by default. History indexes shift as traffic arrives, so re-read the
  list right before acting on one.
- Enum values are upper-case Montoya names (`HIGH`, `GREEN`). `GET /api/meta/enums` lists them
  with Burp's display labels.
- Handles (`engine_id`, scan task ids, websocket ids, session ids) live in extension memory and
  die with a reload; nothing is retried for you.
- Pro-only groups (scanner, collaborator, AI) return `403` on Community edition with a message.
- Every REST call is mirrored in Burp's **reburp** suite tab and in `GET /api/log`, so the user
  can watch what you do. `POST /api/session` with a label, then `session_id` on later calls,
  groups a run.

## Safety

- The port is **unauthenticated** and answers **any origin**. Only ever target `127.0.0.1`.
- `POST /api/utils/shell/execute*` is remote code execution and is **disabled by default**.
  It returns `403` unless the user set `REBURP_ENABLE_SHELL=1` in Burp's environment. Do not
  ask the user to enable it unless the task genuinely needs local command execution, and say
  what you will run first.
- `POST /api/burp/shutdown` and `POST /api/meta/extension/unload` stop Burp or the API. Never
  call them to "reset" something.
- Only drive scans and requests against targets the user has confirmed are in scope for their
  engagement. Active audits, `discover-content` and fuzzing send attack traffic.
- Undo any match-and-replace or session rule you add once the task is done; those settings
  outlive your session.

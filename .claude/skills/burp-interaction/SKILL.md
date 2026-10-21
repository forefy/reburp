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
2. Read the live contract instead of guessing endpoint shapes: `GET /openapi.json`, or open
   `http://127.0.0.1:9090/docs` for Swagger UI. Endpoint names below can drift; the spec is truth.
3. You only need a browser for traffic that must go *through the proxy* (real or authenticated
   sessions). Pure API work - sending requests, scope, scans - needs no browser. If you do need
   one, see **Capturing browser traffic** below.

## Install reburp

Only needed when `GET /api/status` does not connect. Get the jar, then load it:

- Download the latest `reburp-*.jar` from https://github.com/forefy/reburp/releases, or
- Build it (needs Java 17+): `./gradlew shadowJar`, then print the absolute path for the user:
  `ls "$PWD"/build/libs/reburp.jar`. Prefer this unversioned jar over the versioned copy
  beside it: its path survives a version bump, so Burp keeps reloading the extension.

Then in Burp: **Extensions -> Installed -> Add**, type **Java**, pick the jar, **Next**. It
starts on port 9090 and adds a **reburp** tab. Re-check `GET /api/status` to confirm.

## Capturing browser traffic

Only when the task needs real proxied traffic. Confirm whatever you pick is working by watching
`GET /api/proxy/history` grow. Prefer what is already set up, in this order:

1. **Already flowing?** If `/api/proxy/history` shows the target host, you are done.
2. **The user's own browser**, if they already run it through Burp (their normal setup - system
   proxy or a proxy-switcher like FoxyProxy, with Burp's CA trusted). Ask them to browse; you
   watch history.
3. **Burp's pre-wired browser** (Proxy -> Open browser; a local Chromium called **Burp Browser**):
   zero config - auto-proxied and already trusts Burp's CA. The user drives it; you cannot - it is
   a browser (read-only to desktop control) and its remote-debugging port is token-locked.
4. **Fallback that always works for *you* to drive.** Launch a Chromium-based browser (Chrome,
   Edge, or Brave - whatever the machine has) as a *separate* instance pointed at the target URL,
   with these launch flags. It needs none of the blocked steps below, so use it whenever the user
   is unsure or a setup is not routing:
   - `--user-data-dir=<a fresh temp dir>` - a throwaway profile, so it never touches the user's own
   - `--proxy-server=http://127.0.0.1:8080` - route everything through Burp
   - `--ignore-certificate-errors` - accept Burp's interception cert with no CA install
   - `--remote-debugging-port=<port>` - so you can drive it over CDP
   - `--no-first-run --no-default-browser-check`

   Find the browser's executable for the host OS (macOS: under `/Applications`; Windows: under
   `Program Files` or `%LOCALAPPDATA%`; Linux: `google-chrome` / `chromium` on `$PATH`), and quote
   the path if it has spaces. Then drive it over CDP: `GET http://127.0.0.1:<port>/json` for the
   page target's `webSocketDebuggerUrl`, then `Page.navigate` / `Runtime.evaluate`. Confirm
   `/api/proxy/history` grows; close the instance when done.

You **cannot**, in any browser: install extensions (the Chrome Web Store blocks all automation),
trust Burp's CA (needs the user's password), or click/type via desktop control (browsers are
read-only there). Don't burn turns on these - go to the fallback instead.

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
by `text`, not `type`, and prefer `/click` by id over `click-button`, which only knows real buttons.

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
| Send request to Repeater / Intruder | `POST /api/repeater/send`, `POST /api/intruder/send` |
| See the Burp window | `GET /api/ui/screenshot` (PNG; works while minimized or on another desktop) |
| Switch tab (Burp's or an extension's sub-tab), click a button, pick a row | `GET /api/ui/tabs` (`nested` lists sub-tabs), `POST /api/ui/select-tab`, `POST /api/ui/click-button`, `POST /api/ui/select-row` |
| Drive any other element: combo, text field, list, tree, dialog, context menu | `GET /api/ui/elements?text=...` for ids, then `POST /api/ui/click`, `/type`, `/select`, `/key`; `GET /api/ui/windows` for dialogs and popups |

## Conventions

- All bodies and responses are JSON. Errors are `{ "error": "..." }` with a 4xx/5xx status.
- Pro-only groups (scanner, collaborator) return `403` on Community edition with a message.
- Every REST call is mirrored in Burp's **reburp** suite tab, so the user can watch what you do.

## Safety

- The port is **unauthenticated** and answers **any origin**. Only ever target `127.0.0.1`.
- `POST /api/utils/shell/execute*` is remote code execution and is **disabled by default**.
  It returns `403` unless the user set `REBURP_ENABLE_SHELL=1` in Burp's environment. Do not
  ask the user to enable it unless the task genuinely needs local command execution, and say
  what you will run first.
- Only drive scans and requests against targets the user has confirmed are in scope for their
  engagement.

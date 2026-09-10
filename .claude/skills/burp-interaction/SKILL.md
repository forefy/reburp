---
name: burp-interaction
description: Interacting with BurpSuite over the reburp extension that exposes the full Montoya API as a local REST API. Use when driving Burp programmatically - reading proxy history, sending HTTP requests through Burp, managing scope, running scans, decoding/encoding, or ranking traffic - instead of clicking the Burp UI.
---

# Driving Burp Suite over reburp

reburp is a Burp extension that serves the Montoya API as a REST API on `http://127.0.0.1:9090`.
Prefer these endpoints over describing manual clicks in the Burp UI.

## Before you start

1. Confirm the server is up: `GET /api/status`. If it refuses to connect, the extension is
   not loaded - tell the user to load `reburp-1.0.0.jar` in Burp (Extensions -> Add -> Java).
2. Read the live contract instead of guessing endpoint shapes: `GET /openapi.json`, or open
   `http://127.0.0.1:9090/docs` for Swagger UI. Endpoint names below can drift; the spec is truth.

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
| Rank history by how anomalous it looks | `GET /api/utils/rank` |
| Send request to Repeater / Intruder | `POST /api/repeater/send`, `POST /api/intruder/send` |

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

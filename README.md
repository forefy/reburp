<h1 align="center">reburp</h1>

<p align="center">
  <img src="static/reburp.svg" alt="reburp" title="reburp" width="200">
</p>

<p align="center">
 <b>Burp's API/MCP is LIMITED - reburp is a Burp Suite extension that exposes the full Montoya API as a locally served openapi REST API, optimized for your AI agents.</b>
</p>

<p align="center">
  <a href="https://github.com/forefy/reburp/releases/"><img alt="reburp releases" title="reburp releases" src="https://img.shields.io/github/release/forefy/reburp"></a>
  <a href="https://github.com/forefy/reburp/actions/workflows/build.yml"><img alt="Build" title="Build" src="https://img.shields.io/github/actions/workflow/status/forefy/reburp/build.yml"></a>
  <img alt="reburp code size" title="reburp code size" src="https://img.shields.io/github/languages/code-size/forefy/reburp">
  <img alt="reburp commit activity" title="reburp commit activity" src="https://img.shields.io/github/commit-activity/m/forefy/reburp">
  <img alt="GitHub last commit" title="GitHub last commit" src="https://img.shields.io/github/last-commit/forefy/reburp">
  <a href="https://github.com/forefy/reburp/issues/new/choose"><img alt="Issues" title="Issues" src="https://img.shields.io/github/issues-raw/forefy/reburp"></a>
  <a href="https://twitter.com/forefy"><img alt="Forefy Twitter" title="Forefy Twitter" src="https://img.shields.io/twitter/follow/forefy.svg?logo=twitter"></a>
</p>

<p align="center">
 <a href="https://github.com/forefy/reburp/issues/new/choose" title="reburp Issues">Issues</a>
 | <a href="https://github.com/forefy/reburp/discussions" title="reburp Discussions">Discussions</a>
</p>

## What is reburp

reburp turns Burp Suite into something you can script. It loads as a Java extension and serves
the [Montoya API](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html)
over an HTTP REST API on `http://127.0.0.1:9090`, with an OpenAPI spec and Swagger UI. Anything
you would click in Burp - reading proxy history, sending a request, editing scope, running a
scan, decoding a token - becomes a JSON call an agent or a `curl` line can make.

It pairs with the [`burp-interaction`](.claude/skills/burp-interaction) agent skill so an AI
assistant can drive Burp directly.

- [Install](#install)
- [Load into Burp Suite](#load-into-burp-suite)
- [API docs](#api-docs)
- [Endpoints](#endpoints)
- [Quick start](#quick-start)
- [Security](#security)
- [API coverage](#api-coverage)
- [Notes](#notes)
- [Contributing](#contributing)

## Install

Grab the latest `reburp-*.jar` from [Releases](https://github.com/forefy/reburp/releases), or
build it from source (requires Java 17+):

```bash
git clone https://github.com/forefy/reburp.git
cd reburp
./gradlew shadowJar
# Output: build/libs/reburp-1.0.0.jar
```

If your `JAVA_HOME` isn't set, point it at your JDK:

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew shadowJar
```

## Load into Burp Suite

1. Open Burp Suite → **Extensions** → **Installed** → **Add**
2. Extension type: **Java**
3. Extension file: `build/libs/reburp-1.0.0.jar`
4. Click **Next** - the extension starts automatically on port **9090**

A **reburp** tab appears in Burp showing every REST call as it happens.

## API docs

Once loaded, open in your browser:

- **Swagger UI**: http://127.0.0.1:9090/docs
- **OpenAPI spec**: http://127.0.0.1:9090/openapi.json

The spec is the source of truth for request and response shapes.

## Endpoints

| Group | Prefix | Highlights |
|-------|--------|------------|
| Status | `/api/status` | Extension info, Burp version |
| Proxy | `/api/proxy/` | History, search, annotate, intercept toggle, WebSocket history |
| Site Map | `/api/sitemap/` | List, search |
| Scope | `/api/scope/` | Check URL in scope, add/remove scope rules |
| HTTP | `/api/http/` | Send HTTP/1.1 & HTTP/2, auth-token injection, parse, diff, params, reflection, insertion points, cookies |
| Messages | `/api/http/message/` | Inspect and rewrite requests and responses: headers, parameters, markers, MIME types, timing |
| Scanner | `/api/scanner/` | Issues, start audit/crawl, task status (Pro only) |
| Collaborator | `/api/collaborator/` | Generate payloads, poll interactions (Pro only) |
| Repeater / Intruder | `/api/repeater/`, `/api/intruder/` | Send requests to Repeater and Intruder |
| Config | `/api/config/` | Get/set project & user options, task engine state |
| Match & Replace | `/api/proxy/match-replace/` | List, add, remove proxy match-and-replace rules |
| Sessions | `/api/sessions/` | List and manage session-handling rules |
| Engagement | `/api/engagement/` | CSRF PoC generator, content discovery, find references, send to decoder |
| Bambda | `/api/bambda/` | Import Bambda scripts, generate filter chains |
| Organizer | `/api/organizer/` | List items, send requests to the Organizer |
| Issues | `/api/issues/` | Create custom audit issues |
| Events | `/api/events/` | Turn passive traffic observers on and off, then read the captured events |
| Extension Data | `/api/extension-data/` | Typed key/value storage held in the Burp project file |
| Preferences | `/api/preferences/` | Persisted extension preferences by type |
| Logging | `/api/logging/` | Write to the extension output and error tabs and Burp's event log |
| Activity Log | `/api/log/` | Query reburp's own persisted call log |
| AI | `/api/ai/` | Burp AI status and chat (where available) |
| Meta | `/api/meta/` | Burp version components, extension load info, enum vocabularies |
| Utilities | `/api/utils/` | URL encode/decode, base64, hash, JWT decode, random string, decompress |
| Numbers | `/api/utils/number/` | Convert between binary, octal, decimal, hex and arbitrary radixes |
| JSON | `/api/utils/json/` | Validate, read and edit by pointer, inspect structure, normalise |
| Bytes | `/api/utils/bytes/` | Search, slice, append, allocate and inspect byte buffers |
| Ranking | `/api/utils/rank` | Rank proxy history by how anomalous each exchange looks |
| Shell | `/api/utils/shell/` | OS command execution. Disabled by default, see [Security](#security) |

## Quick start

```bash
# Is the extension up?
curl -s http://127.0.0.1:9090/api/status

# Last 10 proxy entries
curl -s "http://127.0.0.1:9090/api/proxy/history?limit=10"

# Send a request through Burp
curl -s http://127.0.0.1:9090/api/http/send \
  -H 'Content-Type: application/json' \
  -d '{"host":"example.com","port":443,"use_https":true,"method":"GET","path":"/"}'

# Add a host to scope
curl -s http://127.0.0.1:9090/api/scope/include \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com"}'
```

## Security

The REST server binds **`127.0.0.1` only**, but the port is **unauthenticated** and responds to
**any origin**. Anything that can reach `localhost:9090` can drive Burp, including a web page
open in your browser. Treat the port as trusted-local-only and never expose it beyond loopback.

Because of that, OS command execution is **off by default**. `/api/utils/shell/execute` and
`/api/utils/shell/execute-raw` return `403` unless the extension sees this in Burp's own process
environment:

```bash
REBURP_ENABLE_SHELL=1
```

With it set, any origin that can reach the port gets remote code execution on the host. Enable it
only when you accept that. Every invocation is written to the extension output tab.
`GET /api/utils/shell/status` reports whether it is on and never requires it to be.

## API coverage

The claim that this exposes the whole Montoya API is checked mechanically rather than asserted:

```bash
python3 tools/api_coverage.py
```

It enumerates every method Montoya declares, subtracts the ones that cannot be represented over
REST (each with a stated reason, listed by `--unmapped`), and exits non-zero if anything mappable
is unreferenced. `--list` prints the gaps. Run it after upgrading the Montoya dependency: a new
Burp release that adds methods will fail the check until they are either exposed or explicitly
classified. The CI [build workflow](.github/workflows/build.yml) runs it on every push.

Name matching is generous, so a clean run shows the surface is wired up rather than that every
endpoint behaves. For that, load the extension and run the endpoints for real:

```bash
python3 tools/smoke_test.py
```

Roughly a third of the declared API is not REST-mappable. The main groups are the Swing user
interface, callback contracts that Burp invokes on its own threads, and registrations that take
extension-supplied code rather than data. Two types, `AttackConfiguration` and the `logger`
package, are unreachable in this API version: nothing in Montoya returns them.

## Notes

- **Port** is hardcoded to `9090`. Change `port` in `BurpRestApiExtension.kt` and rebuild if needed.
- **Pro-only endpoints** (scanner, collaborator) return HTTP 403 on Community edition with an explanatory message.
- **Montoya API** is `compileOnly` - it is not bundled; Burp provides it at runtime.
- All responses are JSON. Errors return `{ "error": "..." }`.
- **OpenAPI spec** is assembled from `OpenApiSpec.kt` plus fragments in `docs/`. To add a route
  group, write a `*Paths()` and `*Schemas()` fragment and list it in `docs/OpenApiSpecExtra.kt`.

## Contributing

Issues and pull requests are welcome. Please run `python3 tools/api_coverage.py` and
`./gradlew shadowJar` before opening a PR. New route groups should ship with their OpenAPI fragment
so the docs stay complete.

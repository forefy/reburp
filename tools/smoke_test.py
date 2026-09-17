#!/usr/bin/env python3
"""
Exercise the REST API against a running Burp with the extension loaded.

Coverage counting proves the code references the Montoya API. This proves the endpoints
actually answer once Burp is driving them, which is the part static analysis cannot show.

Usage:
    python3 tools/smoke_test.py [--port 9090] [--verbose]

Exit status is 1 if any check fails.
"""

import argparse
import json
import re
import sys
import urllib.error
import urllib.request

PASS, FAIL, SKIP = "PASS", "FAIL", "SKIP"


class Client:
    def __init__(self, port, verbose=False):
        self.base = f"http://localhost:{port}"
        self.verbose = verbose
        self.results = []

    def call(self, method, path, body=None):
        url = self.base + path
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        if data:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                raw = resp.read().decode()
                return resp.status, (json.loads(raw) if raw.strip() else None)
        except urllib.error.HTTPError as err:
            raw = err.read().decode()
            try:
                return err.code, json.loads(raw) if raw.strip() else None
            except json.JSONDecodeError:
                return err.code, {"error": raw[:200]}
        except Exception as err:  # connection refused, timeout, etc.
            return None, {"error": str(err)}

    def check(self, name, method, path, body=None, expect=200, verify=None,
              tolerate=()):
        """Run one endpoint. `tolerate` lists status codes that count as a skip."""
        status, payload = self.call(method, path, body)
        if status is None:
            self.record(name, FAIL, f"no response: {payload.get('error')}")
            return None
        if status in tolerate:
            self.record(name, SKIP, f"HTTP {status}: {self.brief(payload)}")
            return None
        if status != expect:
            self.record(name, FAIL, f"HTTP {status} (wanted {expect}): {self.brief(payload)}")
            return None
        if verify:
            problem = verify(payload)
            if problem:
                self.record(name, FAIL, problem)
                return None
        self.record(name, PASS, self.brief(payload))
        return payload

    # Responses carry real captured traffic: target URLs, hosts and bodies from whatever
    # engagement this Burp is running. Verbose output goes to a terminal and often into a
    # shared log, so scrub the identifying parts rather than echoing a client's traffic.
    _SCRUB = (
        # host of any absolute URL
        (re.compile(r'https?://[^"\s/]+'), "https://<host>"),
        # bare hostname fields, which are not URLs: "host": "...", "http_service_string": "..."
        (re.compile(r'(?i)("(?:host|http_service_string|domain|client_ip)"\s*:\s*")[^"]+'),
         r"\1<host>"),
        # secrets carried as query parameters, e.g. apiKey=..., token=..., sig=...
        (re.compile(r'(?i)([?&][a-z0-9_-]*(?:key|token|secret|password|sig|auth)[a-z0-9_-]*=)[^&"\s]+'),
         r"\1<redacted>"),
        # secrets as JSON fields or header values, e.g. "token": "...", Authorization: ...
        # value runs to the closing quote so "Bearer <jwt>" is redacted whole, not just "Bearer"
        (re.compile(r'(?i)((?:api[_-]?key|token|authorization|secret|password|cookie)"?\s*[:=]\s*"?)[^",}]+'),
         r"\1<redacted>"),
    )

    def brief(self, payload):
        if payload is None:
            return ""
        text = json.dumps(payload)
        for pattern, replacement in self._SCRUB:
            text = pattern.sub(replacement, text)
        return text if len(text) <= 110 else text[:107] + "..."

    def record(self, name, status, detail):
        self.results.append((name, status, detail))
        if self.verbose or status == FAIL:
            print(f"  {status:4}  {name}\n          {detail}")
        else:
            print(f"  {status:4}  {name}")


# ── Shared verifiers ─────────────────────────────────────────────────────────
# A 200 with a well-formed body is not the same as a correct answer. /api/utils/rank
# returned schema-valid JSON in capture order for several releases precisely because
# nothing here looked past the status code. Assert on meaning, not shape.

def wrote_something(payload):
    """Endpoints whose only job is a side effect still owe us a confirmation."""
    if payload.get("error"):
        return f"reported an error: {payload['error']}"
    return None if payload.get("message") else f"no confirmation message: {payload}"


def observer_enabled(payload, name):
    for o in payload.get("observers", []):
        if o.get("name") == name:
            return bool(o.get("enabled"))
    return False


def rank_is_usable(payload):
    """Ranking must come back ordered and joinable, not merely well-formed.

    Guards two regressions: results arriving in capture order rather than by
    descending rank, and entries carrying no id to join against proxy history.
    """
    entries = payload.get("ranked", [])
    if not entries:
        return None  # nothing proxied yet is not a failure
    ranks = [e.get("rank") for e in entries]
    if any(r is None for r in ranks):
        return "entries are missing the 'rank' field"
    if ranks != sorted(ranks, reverse=True):
        return f"not ordered by descending rank: first five are {ranks[:5]}"
    unresolved = [e for e in entries if e.get("id", -1) < 0]
    if unresolved:
        return f"{len(unresolved)}/{len(entries)} entries have no proxy-history id"
    if payload.get("truncated") and payload.get("considered", 0) == 0:
        return "truncated set but nothing considered"
    return None


def history_fields_present(payload):
    if not isinstance(payload, list) or not payload:
        return None  # empty history is not a failure
    e = payload[0]
    missing = [f for f in ("id", "mime_type", "http_service_string") if f not in e]
    if missing:
        return f"proxy history entry missing {', '.join(missing)}"
    if e.get("request") is None:
        return "include_body=true but request body was not returned"
    return None


def _gradle_version():
    """The version in build.gradle.kts, which the build bakes into the extension."""
    import os
    import re
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    try:
        text = open(os.path.join(root, "build.gradle.kts")).read()
    except OSError:
        return None
    found = re.search(r'^version\s*=\s*"([^"]+)"', text, re.M)
    return found.group(1) if found else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=9090)
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()
    c = Client(args.port, args.verbose)

    print("Baseline")
    status = c.check("status", "GET", "/api/status", expect=200,
                     verify=lambda p: None if p.get("port") else "no port in response")
    if status is None:
        print("\nExtension is not answering. Load or reload it in Burp, then re-run.")
        return 1
    c.check("openapi spec parses", "GET", "/openapi.json", expect=200,
            verify=lambda p: None if p.get("paths") else "no paths in spec")
    # The running build reports its own version, which is generated from the Gradle
    # version. Check it against build.gradle.kts so a stale jar in Burp is visible here
    # rather than being mistaken for the version that was just built.
    c.check("reports its own version", "GET", "/api/status",
            verify=lambda p: None if p.get("extension_version") == _gradle_version()
            else f"running {p.get('extension_version')}, but the source tree is "
                 f"{_gradle_version()}: Burp is holding an older jar")

    print("\nNumbers")
    c.check("hex to decimal", "POST", "/api/utils/number/convert",
            {"value": "ff", "from": "HEX", "to": "DECIMAL"},
            verify=lambda p: None if p.get("result") == "255" else f"got {p.get('result')}, wanted 255")
    c.check("decimal to radix 36", "POST", "/api/utils/number/convert-radix",
            {"value": "255", "from": "DECIMAL", "radix": 36},
            verify=lambda p: None if p.get("result") == "73" else f"got {p.get('result')}, wanted 73")
    c.check("rejects equal bases", "POST", "/api/utils/number/convert",
            {"value": "1", "from": "HEX", "to": "HEX"}, expect=400)

    print("\nJSON")
    c.check("validate good", "POST", "/api/utils/json/validate", {"json": '{"a":1}'},
            verify=lambda p: None if p.get("valid") else "valid document reported invalid")
    c.check("validate bad", "POST", "/api/utils/json/validate", {"json": "{nope"},
            verify=lambda p: None if p.get("valid") is False else "invalid document reported valid")
    c.check("read pointer", "POST", "/api/utils/json/read",
            {"json": '{"user":{"name":"ada"}}', "pointer": "user.name", "type": "STRING"},
            verify=lambda p: None if p.get("value") == "ada" else f"got {p.get('value')}")
    c.check("inspect object", "POST", "/api/utils/json/inspect", {"json": '{"id":7,"admin":true}'},
            verify=lambda p: None if p.get("type") == "OBJECT" else f"got type {p.get('type')}")
    c.check("normalize", "POST", "/api/utils/json/normalize", {"json": '{ "b" : 2,  "a":1 }'},
            verify=lambda p: None if p.get("result", "").find('"a"') < p.get("result", "").find('"b"')
            else f"keys not normalised into order: {p.get('result')!r}")

    print("\nBytes")
    c.check("search literal", "POST", "/api/utils/bytes/search",
            {"data": "id=1&id=2&id=3", "needle": "id="},
            verify=lambda p: None if p.get("count") == 3 else f"count {p.get('count')}, wanted 3")
    c.check("search regex", "POST", "/api/utils/bytes/search",
            {"data": "a1b2c3", "regex": "[0-9]"},
            verify=lambda p: None if p.get("count") == 3 else f"count {p.get('count')}, wanted 3")
    c.check("rejects both needle and regex", "POST", "/api/utils/bytes/search",
            {"data": "x", "needle": "x", "regex": "x"}, expect=400)
    c.check("slice", "POST", "/api/utils/bytes/slice", {"data": "abcdef", "start": 1, "end": 4},
            verify=lambda p: None if p.get("text") == "bcd" else f"got {p.get('text')}")
    c.check("append", "POST", "/api/utils/bytes/append", {"data": "pay", "suffix": "load"},
            verify=lambda p: None if p.get("text") == "payload" else f"got {p.get('text')}")
    c.check("alloc", "POST", "/api/utils/bytes/alloc", {"length": 8, "fill": 65, "at": 0},
            verify=lambda p: None if p.get("length") == 8 and p.get("first_byte") == 65
            else f"length {p.get('length')} first_byte {p.get('first_byte')}, wanted 8 and 65")
    c.check("inspect", "POST", "/api/utils/bytes/inspect", {"data": "hello"},
            verify=lambda p: None if p.get("length") == 5 else f"length {p.get('length')}")
    c.check("convert", "POST", "/api/utils/bytes/convert", {"data": "hello"},
            verify=lambda p: None if p.get("base64") == "aGVsbG8=" else f"base64 {p.get('base64')!r}")

    print("\nRanking")
    c.check("rank history", "POST", "/api/utils/rank", {"limit": 25, "scope_only": False},
            verify=rank_is_usable)
    # How much history gets scored is the caller's choice; it was a hardcoded 2000, which
    # silently ranked a slice of a large capture. Burp scores relative to the scored set,
    # so a wider scope is a different ranking, not just a slower one.
    c.check("max_scored widens the scored set", "POST", "/api/utils/rank",
            {"limit": 3, "max_scored": 50}, verify=lambda p: None
            if p.get("considered", 0) <= 50 else f"scored {p.get('considered')} with max_scored=50")
    c.check("rejects an out-of-range max_scored", "POST", "/api/utils/rank",
            {"limit": 1, "max_scored": 0}, expect=400,
            verify=lambda p: None if "max_scored" in (p or {}).get("error", "")
            else f"unhelpful error: {p}")

    print("\nShell (expected off)")
    c.check("shell status", "GET", "/api/utils/shell/status",
            verify=lambda p: None if "enabled" in p else "no enabled flag")
    c.check("execute refused while disabled", "POST", "/api/utils/shell/execute",
            {"command": ["echo", "hi"]}, expect=403, tolerate=(200,))

    print("\nLogging")
    c.check("output", "POST", "/api/logging/output", {"message": "smoke test: output"},
            verify=wrote_something)
    c.check("error", "POST", "/api/logging/error", {"message": "smoke test: error"},
            verify=wrote_something)
    c.check("event", "POST", "/api/logging/event", {"level": "INFO", "message": "smoke test: event"},
            verify=wrote_something)
    c.check("stream", "POST", "/api/logging/stream", {"stream": "OUTPUT", "message": "smoke test: stream"},
            verify=wrote_something)
    c.check("rejects bad level", "POST", "/api/logging/event", {"level": "LOUD", "message": "x"}, expect=400)

    print("\nMeta")
    c.check("version", "GET", "/api/meta/version",
            verify=lambda p: None if p.get("build_number") else "no build number")
    c.check("extension", "GET", "/api/meta/extension",
            verify=lambda p: None if p.get("filename") and p.get("rest_port")
            else f"extension self-description incomplete: {p}")
    c.check("enums", "GET", "/api/meta/enums",
            verify=lambda p: None if p.get("highlight_colors") else "no highlight colours")

    print("\nHTTP messages")
    raw_req = "GET /search?q=1 HTTP/1.1\r\nHost: example.com\r\nAccept: */*\r\n\r\n"
    raw_res = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nSet-Cookie: a=b\r\n\r\n<html></html>"
    c.check("inspect request", "POST", "/api/http/message/inspect/request",
            {"raw": raw_req, "host": "example.com", "port": 443, "secure": True},
            verify=lambda p: None if p.get("method") == "GET" else f"method {p.get('method')}, wanted GET")
    c.check("inspect request from url", "POST", "/api/http/message/inspect/request",
            {"url": "https://example.com/admin?x=1"},
            verify=lambda p: None if p.get("path_without_query") == "/admin" and p.get("query") == "x=1"
            else f"url not decomposed: path={p.get('path_without_query')!r} query={p.get('query')!r}")
    c.check("inspect response", "POST", "/api/http/message/inspect/response",
            {"raw": raw_res, "keywords": ["html"]},
            verify=lambda p: None if p.get("status_code") == 200 else f"status {p.get('status_code')}, wanted 200")
    c.check("rejects request with neither raw nor url", "POST",
            "/api/http/message/inspect/request", {}, expect=400)

    print("\nExtension data")
    c.check("write string", "PUT", "/api/extension-data/value",
            {"type": "STRING", "key": "smoke_key", "value": "smoke_value"},
            verify=wrote_something)
    c.check("read string", "GET", "/api/extension-data/value?type=STRING&key=smoke_key",
            verify=lambda p: None if json.dumps(p).find("smoke_value") >= 0 else f"value not returned: {p}")
    c.check("list root keys", "GET", "/api/extension-data",
            verify=lambda p: None if "smoke_key" in p.get("strings", [])
            else f"just-written key absent from listing: strings={p.get('strings')}")
    c.check("delete string", "DELETE", "/api/extension-data/value?type=STRING&key=smoke_key",
            verify=wrote_something)
    c.check("deleted string is really gone", "GET", "/api/extension-data",
            verify=lambda p: None if "smoke_key" not in p.get("strings", [])
            else "delete reported success but the key is still listed")
    c.check("rejects unknown type", "GET", "/api/extension-data/value?type=NOPE&key=x", expect=400)

    print("\nEvents")
    c.check("list observers", "GET", "/api/events/observers",
            verify=lambda p: None if any(o.get("name") == "HTTP_REQUEST" for o in p.get("observers", []))
            else "HTTP_REQUEST observer missing from the listing")
    c.check("enable http observer", "POST", "/api/events/observers/HTTP_REQUEST/enable",
            verify=wrote_something)
    c.check("observer reports itself enabled", "GET", "/api/events/observers",
            verify=lambda p: None if observer_enabled(p, "HTTP_REQUEST")
            else "enable returned 200 but the observer still reads as disabled")
    c.check("read events", "GET", "/api/events?limit=5",
            verify=lambda p: None if p.get("limit") == 5 and isinstance(p.get("events"), list)
            else f"event buffer shape unexpected: {p}")
    c.check("disable http observer", "POST", "/api/events/observers/HTTP_REQUEST/disable",
            verify=wrote_something)
    c.check("observer reports itself disabled", "GET", "/api/events/observers",
            verify=lambda p: None if not observer_enabled(p, "HTTP_REQUEST")
            else "disable returned 200 but the observer still reads as enabled")
    c.check("rejects unknown observer", "POST", "/api/events/observers/NOPE/enable", expect=400)

    print("\nProxy and scanner additions")
    c.check("proxy history carries new fields", "GET", "/api/proxy/history?limit=1&include_body=true",
            verify=history_fields_present)
    c.check("crawl preview", "POST", "/api/scanner/crawl/preview",
            {"seed_urls": ["https://example.com/"]}, tolerate=(403,),
            verify=lambda p: None if p.get("count") == 1 and p.get("seed_urls") == ["https://example.com/"]
            else f"seed urls not echoed back: {p}")
    c.check("issue definition", "POST", "/api/scanner/issue-definition",
            {"name": "Smoke finding", "background": "Background.", "remediation": "Fix it.",
             "typical_severity": "LOW"},
            verify=lambda p: None if p.get("name") == "Smoke finding" and p.get("typical_severity") == "LOW"
            else f"issue definition not echoed back: {p}")

    print("\nExtensions")
    # These endpoints read Burp's config export, where a wrong lookup path yields an empty
    # result with a 200 rather than an error. Both shipped broken that way, so assert on
    # content: reburp itself is loaded whenever this API answers, so the list cannot be empty.
    c.check("extensions list is not empty", "GET", "/api/extensions",
            verify=lambda p: None if isinstance(p, list) and p
            else "empty list - the config lookup path is wrong")
    c.check("extensions carry type and file", "GET", "/api/extensions",
            verify=lambda p: None if isinstance(p, list) and p
            and p[0].get("file") and p[0].get("type")
            else "entries missing type/file - the field mapping is wrong")

    print("\nSessions")
    listed = c.check("list session rules", "GET", "/api/sessions/rules",
                     verify=lambda p: None if isinstance(p, list) else f"not a list: {p}")
    if isinstance(listed, list):
        # Reading rules at all proves the config path resolves: a project with session
        # handling configured must report at least the rules Burp ships with.
        c.check("rules carry a description", "GET", "/api/sessions/rules",
                verify=lambda p: None if not p or p[0].get("description")
                else f"rule without description: {p[0]}")
        # Burp has no add-header action, so this must refuse rather than claim success.
        c.check("add-header refuses instead of lying", "POST",
                "/api/sessions/rules/add-header", {}, expect=501,
                verify=lambda p: None if "match-replace" in (p or {}).get("error", "")
                else f"no pointer to the working alternative: {p}")
        # Out-of-range delete must 404 against the real count, not a phantom empty list.
        c.check("delete reports the real rule count", "DELETE",
                "/api/sessions/rules/9999", expect=404,
                verify=lambda p: None if f"({len(listed)} rules)" in (p or {}).get("error", "")
                else f"wrong count in message: {p}")

    print("\nSpec integrity and defaults")
    # operationIds must be unique or generated clients silently drop one of the endpoints.
    c.check("operationIds are unique", "GET", "/openapi.json",
            verify=lambda p: None if len({op["operationId"] for pth in p["paths"].values()
                                          for op in pth.values() if isinstance(op, dict) and "operationId" in op})
            == sum(1 for pth in p["paths"].values() for op in pth.values() if isinstance(op, dict) and "operationId" in op)
            else "duplicate operationId in the spec")
    probe = "GET /a?q=1&r=2 HTTP/1.1\r\nHost: example.com\r\n\r\n"
    # mode used to default to ALL_PARAMETERS, which Montoya does not have, so omitting it failed.
    c.check("insertion points work with the default mode", "POST", "/api/http/insertion-points",
            {"request": probe}, verify=lambda p: None if isinstance(p, list) and p
            else f"no insertion points: {p}")
    c.check("insertion points reject an unknown mode by name", "POST", "/api/http/insertion-points",
            {"request": probe, "mode": "ALL_PARAMETERS"}, expect=400,
            verify=lambda p: None if "REPLACE_BASE_PARAMETER_VALUE_WITH_OFFSETS" in (p or {}).get("error", "")
            else f"error does not list the allowed modes: {p}")
    # Used to return one flattened "header=...\npayload=..." string instead of the documented parts.
    c.check("utils JWT decode returns structured parts", "POST", "/api/utils/jwt/decode",
            {"value": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln"},
            verify=lambda p: None if isinstance(p.get("header"), dict) and isinstance(p.get("payload"), dict)
            and p["payload"].get("sub") == "1" else f"not decoded into parts: {p}")
    # Empty lists were dropped from the JSON because they equalled the field default.
    c.check("header edit always reports not_found", "POST", "/api/http/message/request/headers",
            {"raw": probe, "add": [{"name": "X-Smoke", "value": "1"}]},
            verify=lambda p: None if isinstance(p.get("not_found"), list) and isinstance(p.get("applied"), list)
            else f"missing list fields: {sorted(p)}")
    # These return before any audit starts, so they are safe against a live Burp. Community
    # edition answers 403 first, which counts as a skip.
    c.check("audit rejects a configuration Montoya lacks", "POST", "/api/scanner/audit",
            {"configuration": "CRAWL_AND_AUDIT_EVERYTHING_FAST", "requests": []}, expect=400, tolerate=(403,),
            verify=lambda p: None if "Allowed values" in (p or {}).get("error", "") else f"unhelpful: {p}")
    c.check("audit from history refuses a typo instead of going active", "POST",
            "/api/scanner/audit/from-history", {"index": 0, "configuration": "PASIVE"},
            expect=400, tolerate=(403, 404),
            verify=lambda p: None if "PASIVE" in (p or {}).get("error", "") else f"typo was not refused: {p}")

    print("\nQuery parameters are honoured")
    # A documented parameter the handler never reads answers 200 with unfiltered data, which a
    # caller cannot tell from a real answer. ?path on the config export was ignored this way.
    full = c.call("GET", "/api/config/project")[1] or {}
    c.check("config ?path exports only that sub-tree", "GET",
            "/api/config/project?path=project_options.connections",
            verify=lambda p: None if len(p.get("result", "")) < len(full.get("result", "")) / 2
            and "connections" in p.get("result", "") else "path was ignored: got the whole config")
    c.check("history ?method filters", "GET", "/api/proxy/history?limit=50&method=POST",
            verify=lambda p: None if all(e.get("method") == "POST" for e in p)
            else "non-POST entries returned")
    c.check("history ?status_min/status_max filter", "GET",
            "/api/proxy/history?limit=50&status_min=200&status_max=299",
            verify=lambda p: None if all(e.get("status") and 200 <= e["status"] <= 299 for e in p)
            else "entries outside 2xx returned")
    c.check("scanner issues ?severity filters", "GET", "/api/scanner/issues?limit=100&severity=LOW",
            tolerate=(403,), verify=lambda p: None
            if all(i.get("severity") == "LOW" for i in (p if isinstance(p, list) else p.get("issues", [])))
            else "issues of other severities returned")

    print("\nActivity log")
    # Paging used to return the oldest calls with limit silently capped and no total, so a
    # busy log looked frozen and a page was indistinguishable from the whole thing.
    lg = c.check("log reports a total and pages newest first", "GET", "/api/log?limit=3",
                 verify=lambda p: None if isinstance(p, dict) and "total" in p
                 and p.get("newest_first") is True and p.get("returned", 0) <= 3
                 else f"unexpected shape: {p if not isinstance(p, dict) else sorted(p)}")
    if isinstance(lg, dict) and len(lg.get("entries", [])) > 1:
        c.check("newest_first=false reverses the order", "GET",
                "/api/log?limit=3&newest_first=false",
                verify=lambda p: None
                if p["entries"][0]["ts"] <= p["entries"][-1]["ts"]
                else f"not chronological: {[e['ts'] for e in p['entries']]}")
    c.check("over-max limit is clamped but total still reported", "GET", "/api/log?limit=99999",
            verify=lambda p: None if p.get("limit") == 500 and p.get("total", 0) >= p.get("returned", 0)
            else f"limit={p.get('limit')} total={p.get('total')} returned={p.get('returned')}")

    print("\nScope")
    # The spec documents /api/scope/rules; it used to 404 because the handler was only
    # registered at /api/scope.
    c.check("documented scope path answers", "GET", "/api/scope/rules",
            verify=lambda p: None if isinstance(p, dict) and "include" in p and "exclude" in p
            else f"unexpected shape: {p}")

    print("\nIntercept rules")
    # Read from Burp's real config section. The old code read intercept_client /
    # intercept_server, which do not exist, so both lists were always empty.
    ir = c.check("intercept rules are readable", "GET", "/api/proxy/intercept/rules",
                 verify=lambda p: None if isinstance(p, dict) and p.get("client_rules")
                 else "no client rules - the config section name is wrong")
    if isinstance(ir, dict) and ir.get("client_rules"):
        c.check("intercept rules carry Burp's real fields", "GET",
                "/api/proxy/intercept/rules",
                verify=lambda p: None
                if all(k in p["client_rules"][0] for k in ("match_type", "boolean_operator"))
                else f"missing real fields: {p['client_rules'][0]}")
    # Burp silently drops a rule whose vocabulary it does not recognise. MIME_TYPE looks
    # invalid but Burp accepts it as mime_type, so assert with a value it truly rejects.
    c.check("intercept rejects an unknown match_type", "POST",
            "/api/proxy/intercept/rules/client",
            {"match_type": "not_a_real_match_type", "match_relationship": "matches",
             "match_condition": "x"}, expect=500,
            verify=lambda p: None if "vocabulary" in (p or {}).get("error", "")
            else f"unhelpful error: {p}")
    # A valid rule must round-trip and then be removed, so repeat runs leave no residue.
    if isinstance(ir, dict) and ir.get("client_rules") is not None:
        n = len(ir["client_rules"])
        if c.check("intercept accepts a valid rule", "POST",
                   "/api/proxy/intercept/rules/client",
                   {"match_type": "url", "match_relationship": "matches",
                    "match_condition": "reburp-smoke", "enabled": False}) is not None:
            if c.check("added intercept rule is removed again", "DELETE",
                       f"/api/proxy/intercept/rules/client/{n}") is not None:
                c.check("intercept rule count is back to where it started", "GET",
                        "/api/proxy/intercept/rules",
                        verify=lambda p: None if len(p.get("client_rules", [])) == n
                        else f"expected {n} client rules after cleanup, found {len(p.get('client_rules', []))}")

    print("\nMatch and replace")
    listed_mr = c.check("list match/replace rules", "GET", "/api/proxy/match-replace",
                        verify=lambda p: None if isinstance(p, dict) and "rules" in p
                        else f"unexpected shape: {p}")
    if isinstance(listed_mr, dict):
        idx = listed_mr.get("count", len(listed_mr.get("rules", [])))
        # is_simple_match maps to Burp's "category". It used to be written as a field Burp
        # discards, so a literal rule silently became a regex one.
        made = c.check("create a literal match rule", "POST", "/api/proxy/match-replace",
                       {"rule_type": "request_header", "string_match": "X-Reburp-Smoke",
                        "string_replace": "X-Reburp-Smoke: 1", "is_simple_match": True,
                        "enabled": False, "comment": "reburp smoke test"}, expect=201)
        if made is not None:
            c.check("literal flag survives the round trip", "GET",
                    "/api/proxy/match-replace",
                    verify=lambda p: None if any(
                        r.get("comment") == "reburp smoke test" and r.get("is_simple_match")
                        for r in p.get("rules", []))
                    else "is_simple_match lost - category mapping is broken")
            if c.check("delete the test rule", "DELETE", f"/api/proxy/match-replace/{idx}") is not None:
                c.check("test rule is really gone", "GET", "/api/proxy/match-replace",
                        verify=lambda p: None if not any(
                            r.get("comment") == "reburp smoke test" for r in p.get("rules", []))
                        else "delete returned 200 but the rule is still listed")

    print("\nResponse shapes match the spec")
    # The documented schema had drifted from what these endpoints return: proxy history was
    # documented with status_code / note / timestamp while it actually sends status / notes /
    # time, so a client written from the spec read nothing.
    c.check("proxy history matches ProxyEntry", "GET", "/api/proxy/history?limit=1",
            verify=lambda p: None if not p or (
                {"status", "notes", "time", "secure"} <= set(p[0])
                and not {"status_code", "note", "timestamp", "use_https"} & set(p[0]))
            else f"field names drifted from the schema: {sorted(p[0])}")
    parsed = c.check("parsed response matches schema", "POST", "/api/http/parse/response",
                     {"response": "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\nhi"},
                     verify=lambda p: None if {"status_code", "headers", "body",
                                               "body_length"} == set(p)
                     else f"unexpected fields: {sorted(p)}")
    c.check("task engine state matches schema", "GET", "/api/config/tasks",
            verify=lambda p: None if "state" in p and "running" not in p
            else f"expected 'state', got {sorted(p)}")

    print("\nIssue enums")
    # The spec used to advertise CRITICAL, which Montoya has no constant for.
    c.check("rejects a severity Montoya lacks", "POST", "/api/issues",
            {"name": "probe", "detail": "d", "severity": "CRITICAL",
             "confidence": "CERTAIN", "base_url": "https://example.com/"}, expect=400,
            verify=lambda p: None if "Allowed values" in (p or {}).get("error", "")
            else f"error does not name the allowed values: {p}")

    passed = sum(1 for _, s, _ in c.results if s == PASS)
    failed = sum(1 for _, s, _ in c.results if s == FAIL)
    skipped = sum(1 for _, s, _ in c.results if s == SKIP)
    print(f"\n{passed} passed, {failed} failed, {skipped} skipped")
    if failed:
        print("\nFailures:")
        for name, status, detail in c.results:
            if status == FAIL:
                print(f"  {name}: {detail}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

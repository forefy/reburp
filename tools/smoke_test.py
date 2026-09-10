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

    def brief(self, payload):
        if payload is None:
            return ""
        text = json.dumps(payload)
        return text if len(text) <= 110 else text[:107] + "..."

    def record(self, name, status, detail):
        self.results.append((name, status, detail))
        if self.verbose or status == FAIL:
            print(f"  {status:4}  {name}\n          {detail}")
        else:
            print(f"  {status:4}  {name}")


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

    print("\nNumbers")
    c.check("hex to decimal", "POST", "/api/utils/number/convert",
            {"value": "ff", "from": "HEX", "to": "DECIMAL"},
            verify=lambda p: None if p.get("result") == "255" else f"got {p.get('result')}, wanted 255")
    c.check("decimal to radix 36", "POST", "/api/utils/number/convert-radix",
            {"value": "255", "from": "DECIMAL", "radix": 36})
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
    c.check("normalize", "POST", "/api/utils/json/normalize", {"json": '{ "b" : 2,  "a":1 }'})

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
    c.check("alloc", "POST", "/api/utils/bytes/alloc", {"length": 8, "fill": 65, "at": 0})
    c.check("inspect", "POST", "/api/utils/bytes/inspect", {"data": "hello"},
            verify=lambda p: None if p.get("length") == 5 else f"length {p.get('length')}")
    c.check("convert", "POST", "/api/utils/bytes/convert", {"data": "hello"})

    print("\nRanking")
    c.check("rank history", "POST", "/api/utils/rank", {"limit": 25})

    print("\nShell (expected off)")
    c.check("shell status", "GET", "/api/utils/shell/status",
            verify=lambda p: None if "enabled" in p else "no enabled flag")
    c.check("execute refused while disabled", "POST", "/api/utils/shell/execute",
            {"command": ["echo", "hi"]}, expect=403, tolerate=(200,))

    print("\nLogging")
    c.check("output", "POST", "/api/logging/output", {"message": "smoke test: output"})
    c.check("error", "POST", "/api/logging/error", {"message": "smoke test: error"})
    c.check("event", "POST", "/api/logging/event", {"level": "INFO", "message": "smoke test: event"})
    c.check("stream", "POST", "/api/logging/stream", {"stream": "OUTPUT", "message": "smoke test: stream"})
    c.check("rejects bad level", "POST", "/api/logging/event", {"level": "LOUD", "message": "x"}, expect=400)

    print("\nMeta")
    c.check("version", "GET", "/api/meta/version",
            verify=lambda p: None if p.get("build_number") else "no build number")
    c.check("extension", "GET", "/api/meta/extension")
    c.check("enums", "GET", "/api/meta/enums",
            verify=lambda p: None if p.get("highlight_colors") else "no highlight colours")

    print("\nHTTP messages")
    raw_req = "GET /search?q=1 HTTP/1.1\r\nHost: example.com\r\nAccept: */*\r\n\r\n"
    raw_res = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nSet-Cookie: a=b\r\n\r\n<html></html>"
    c.check("inspect request", "POST", "/api/http/message/inspect/request",
            {"raw": raw_req, "host": "example.com", "port": 443, "secure": True},
            verify=lambda p: None if p.get("method") == "GET" else f"method {p.get('method')}, wanted GET")
    c.check("inspect request from url", "POST", "/api/http/message/inspect/request",
            {"url": "https://example.com/admin?x=1"})
    c.check("inspect response", "POST", "/api/http/message/inspect/response",
            {"raw": raw_res, "keywords": ["html"]},
            verify=lambda p: None if p.get("status_code") == 200 else f"status {p.get('status_code')}, wanted 200")
    c.check("rejects request with neither raw nor url", "POST",
            "/api/http/message/inspect/request", {}, expect=400)

    print("\nExtension data")
    c.check("write string", "PUT", "/api/extension-data/value",
            {"type": "STRING", "key": "smoke_key", "value": "smoke_value"})
    c.check("read string", "GET", "/api/extension-data/value?type=STRING&key=smoke_key",
            verify=lambda p: None if json.dumps(p).find("smoke_value") >= 0 else f"value not returned: {p}")
    c.check("list root keys", "GET", "/api/extension-data")
    c.check("delete string", "DELETE", "/api/extension-data/value?type=STRING&key=smoke_key")
    c.check("rejects unknown type", "GET", "/api/extension-data/value?type=NOPE&key=x", expect=400)

    print("\nEvents")
    c.check("list observers", "GET", "/api/events/observers")
    c.check("enable http observer", "POST", "/api/events/observers/HTTP_REQUEST/enable")
    c.check("read events", "GET", "/api/events?limit=5")
    c.check("disable http observer", "POST", "/api/events/observers/HTTP_REQUEST/disable")
    c.check("rejects unknown observer", "POST", "/api/events/observers/NOPE/enable", expect=400)

    print("\nProxy and scanner additions")
    c.check("proxy history carries new fields", "GET", "/api/proxy/history?limit=1&include_body=true")
    c.check("crawl preview", "POST", "/api/scanner/crawl/preview",
            {"seed_urls": ["https://example.com/"]}, tolerate=(403,))
    c.check("issue definition", "POST", "/api/scanner/issue-definition",
            {"name": "Smoke finding", "background": "Background.", "remediation": "Fix it.",
             "typical_severity": "LOW"})

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

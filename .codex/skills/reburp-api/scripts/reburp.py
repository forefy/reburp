#!/usr/bin/env python3
"""
Small stdlib-only client for the reburp REST API (Burp Suite on http://127.0.0.1:9090).

    reburp.py status                             is the extension up, which build
    reburp.py endpoints [--tag T] [--grep RE]    compact endpoint list from the live spec
    reburp.py describe METHOD PATH               full contract of one operation, $refs resolved
    reburp.py call METHOD PATH [-q k=v ...] [-d JSON | -d @file | -d -] [-o out.png]

Set REBURP_URL to reach a non-default port. Exit status is 1 on HTTP errors or no connection.
"""

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("REBURP_URL", "http://127.0.0.1:9090").rstrip("/")
METHODS = ("get", "post", "put", "patch", "delete")
HEADER = (
    "# reburp endpoint reference\n\n"
    "Generated from the OpenAPI spec (`GET /openapi.json`) of reburp {version}. The live spec is the source "
    "of truth; refresh this file with `python3 .codex/skills/reburp-api/scripts/reburp.py endpoints > "
    ".codex/skills/reburp-api/references/endpoints.md` while Burp is running.\n\n"
    "Notation: `*` marks a required field, `=x` is the default, `a|b` is an enum. `body:object` fields take a "
    "JSON object (sent as JSON) or a string.\n"
)


def request(method, path, query=None, data=None, timeout=120):
    url = BASE + (path if path.startswith("/") else "/" + path)
    if query:
        url += ("&" if "?" in url else "?") + urllib.parse.urlencode(query)
    body = None
    headers = {}
    if data is not None:
        body = data.encode() if isinstance(data, str) else json.dumps(data).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, method=method.upper(), headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.headers.get("Content-Type", ""), resp.read()
    except urllib.error.HTTPError as err:
        return err.code, err.headers.get("Content-Type", ""), err.read()
    except (urllib.error.URLError, OSError) as err:
        sys.exit(f"reburp is not reachable at {BASE} ({err}). Is Burp running with the extension loaded?")


def load_spec():
    status, _, raw = request("GET", "/openapi.json")
    if status != 200:
        sys.exit(f"GET /openapi.json returned HTTP {status}")
    return json.loads(raw)


def resolve(spec, node):
    seen = 0
    while isinstance(node, dict) and "$ref" in node and seen < 20:
        target = spec
        for part in node["$ref"].lstrip("#/").split("/"):
            target = target[part]
        node, seen = target, seen + 1
    return node


def type_of(spec, schema):
    schema = resolve(spec, schema)
    if not isinstance(schema, dict):
        return "?"
    if "enum" in schema:
        return "|".join(map(str, schema["enum"]))
    kind = schema.get("type", "object")
    if kind == "array":
        return "[" + type_of(spec, schema.get("items", {})) + "]"
    return kind


def fields(spec, schema):
    schema = resolve(spec, schema)
    if not isinstance(schema, dict):
        return ""
    for key in ("oneOf", "anyOf"):
        if key in schema:
            return " OR ".join("{" + fields(spec, s) + "}" for s in schema[key])
    if "allOf" in schema:
        return ", ".join(fields(spec, s) for s in schema["allOf"])
    required = set(schema.get("required", []))
    out = []
    for name, prop in schema.get("properties", {}).items():
        res = resolve(spec, prop)
        default = res.get("default") if isinstance(res, dict) else None
        item = f"{name}{'*' if name in required else ''}:{type_of(spec, prop)}"
        if default is not None:
            item += "=" + json.dumps(default)
        out.append(item)
    return ", ".join(out)


def operations(spec):
    for path, item in spec.get("paths", {}).items():
        for method, op in item.items():
            if method in METHODS:
                yield path, method, op


def compact_line(spec, path, method, op):
    line = f"- `{method.upper()} {path}` - {op.get('summary', '').strip()}"
    query = [resolve(spec, p) for p in op.get("parameters", []) if resolve(spec, p).get("in") == "query"]
    if query:
        parts = []
        for p in query:
            part = p["name"] + ("*" if p.get("required") else "")
            if "default" in p.get("schema", {}):
                part += "=" + json.dumps(p["schema"]["default"])
            parts.append(part)
        line += ". Query: " + ", ".join(parts)
    body = op.get("requestBody")
    if body:
        content = resolve(spec, body).get("content", {})
        if "application/json" in content:
            line += ". Body: {" + fields(spec, content["application/json"].get("schema", {})) + "}"
        else:
            line += ". Body: " + ", ".join(content)
    return line


def cmd_endpoints(args):
    spec = load_spec()
    pattern = re.compile(args.grep, re.I) if args.grep else None
    grouped = {}
    for path, method, op in operations(spec):
        tag = (op.get("tags") or ["Other"])[0]
        if args.tag and tag.lower() != args.tag.lower():
            continue
        line = compact_line(spec, path, method, op)
        if pattern and not pattern.search(line):
            continue
        grouped.setdefault(tag, []).append(line)
    if not (args.tag or args.grep):
        version = spec.get("info", {}).get("version", "")
        status, _, raw = request("GET", "/api/status")
        if status == 200:
            version = json.loads(raw).get("extension_version", version)
        print(HEADER.format(version=version))
    order = [t["name"] for t in spec.get("tags", [])]
    order += [t for t in grouped if t not in order]
    for tag in order:
        if tag in grouped:
            print(f"\n## {tag}\n")
            print("\n".join(grouped[tag]))


def inline(spec, node, depth=0):
    """Copy of node with $refs expanded, bounded so recursive schemas terminate."""
    node = resolve(spec, node)
    if depth > 6:
        return node
    if isinstance(node, dict):
        return {k: inline(spec, v, depth + 1) for k, v in node.items()}
    if isinstance(node, list):
        return [inline(spec, v, depth + 1) for v in node]
    return node


def cmd_describe(args):
    spec = load_spec()
    item = spec.get("paths", {}).get(args.path)
    if item is None:
        candidates = [p for p in spec.get("paths", {}) if args.path.strip("/") in p]
        sys.exit(f"No path {args.path}. Close matches: {', '.join(candidates[:15]) or 'none'}")
    op = item.get(args.method.lower())
    if op is None:
        sys.exit(f"{args.path} has no {args.method.upper()}; it has {', '.join(m.upper() for m in item if m in METHODS)}")
    print(json.dumps(inline(spec, op), indent=2))


def read_data(value):
    if value is None:
        return None
    if value == "-":
        value = sys.stdin.read()
    elif value.startswith("@"):
        with open(value[1:], encoding="utf-8") as fh:
            value = fh.read()
    try:
        json.loads(value)
    except json.JSONDecodeError as err:
        sys.exit(f"Request body is not valid JSON: {err}")
    return value


def cmd_call(args):
    query = []
    for pair in args.query or []:
        key, sep, val = pair.partition("=")
        if not sep:
            sys.exit(f"-q expects key=value, got {pair!r}")
        query.append((key, val))
    status, ctype, raw = request(args.method, args.path, query, read_data(args.data), args.timeout)
    if args.out:
        with open(args.out, "wb") as fh:
            fh.write(raw)
        print(f"HTTP {status} {ctype} -> {args.out} ({len(raw)} bytes)")
    elif "json" in ctype:
        try:
            print(json.dumps(json.loads(raw), indent=2))
        except json.JSONDecodeError:
            print(raw.decode(errors="replace"))
    elif ctype.startswith("text/") or not ctype:
        print(raw.decode(errors="replace"))
    else:
        print(f"HTTP {status}: {len(raw)} bytes of {ctype}; pass -o FILE to save it")
    if status >= 400:
        print(f"HTTP {status}", file=sys.stderr)
        sys.exit(1)


def cmd_status(_args):
    status, _, raw = request("GET", "/api/status", timeout=10)
    print(json.dumps(json.loads(raw), indent=2) if raw.strip() else f"HTTP {status}")
    if status != 200:
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("status").set_defaults(fn=cmd_status)
    ep = sub.add_parser("endpoints")
    ep.add_argument("--tag", help="only this tag, e.g. Proxy, HTTP, Scanner, UI")
    ep.add_argument("--grep", help="regex over the compact line (path, summary, fields)")
    ep.set_defaults(fn=cmd_endpoints)
    de = sub.add_parser("describe")
    de.add_argument("method")
    de.add_argument("path", help="spec path with placeholders, e.g. /api/proxy/history/{index}")
    de.set_defaults(fn=cmd_describe)
    ca = sub.add_parser("call")
    ca.add_argument("method")
    ca.add_argument("path")
    ca.add_argument("-q", "--query", action="append", metavar="KEY=VALUE")
    ca.add_argument("-d", "--data", help="JSON body, @file, or - for stdin")
    ca.add_argument("-o", "--out", help="write the raw response body to this file (PNG screenshots, reports)")
    ca.add_argument("--timeout", type=float, default=120)
    ca.set_defaults(fn=cmd_call)
    args = parser.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()

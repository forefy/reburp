#!/usr/bin/env python3
"""
Measure how much of the Montoya API this extension actually exposes.

The README claims the extension exposes the full Montoya API. That claim is only
meaningful if something checks it, so this script does.

It enumerates every method Montoya declares, subtracts the ones that cannot be
represented over REST (each with a stated reason), and reports whether anything
mappable is still unreferenced by the Kotlin sources.

What this does and does not prove
---------------------------------
Matching is by name and is deliberately generous: a Java getFoo()/setFoo() pair is
reached in Kotlin as `.foo`, and trailing-lambda calls carry no parentheses, so the
checker accepts several shapes. A name that happens to collide with an unrelated local
function therefore counts as a hit. So a clean run proves the API surface is wired up,
not that each endpoint behaves correctly. For that, run tools/smoke_test.py against a
Burp with the extension loaded, which exercises the endpoints for real.

Usage:
    python3 tools/api_coverage.py            # summary
    python3 tools/api_coverage.py --list     # list every unreferenced mappable method
    python3 tools/api_coverage.py --unmapped # list excluded methods and why

Exit status is 1 when a mappable method is unreferenced, so this can gate a build.
"""

import argparse
import collections
import glob
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "kotlin")

# Methods inherited from Object or synthesised for enums. Never part of the API surface.
BUILTINS = {
    "toString", "equals", "hashCode", "wait", "notify", "notifyAll",
    "getClass", "clone", "finalize", "values", "valueOf", "ordinal",
    "name", "compareTo", "iterator", "forEach", "spliterator",
}

# Classes that cannot be exposed over HTTP, with the reason. Order matters: the first
# matching pattern wins. Anything not matched here is treated as mappable and must be
# referenced by the Kotlin sources.
UNMAPPABLE = [
    (r"^burp\.api\.montoya\.internal\.",
     "Internal object factory. Burp implements it; extensions use the static factory "
     "methods on the public interfaces, which this code does."),

    (r"^burp\.api\.montoya\.ui\.",
     "In-process Swing user interface. Editors, menus, hotkeys, context menus and "
     "settings panels are drawn inside Burp and have no request/response equivalent."),

    (r"(Handler|Provider|ScanCheck|Listener)$",
     "Callback contract. Burp invokes it on its own threads when an event occurs, so it "
     "requires a live in-process listener rather than a REST call."),

    (r"^burp\.api\.montoya\.scanner\.ScanCheck$",
     "Callback contract for custom scan logic, invoked by Burp during an audit."),

    (r"Action$",
     "Instruction returned to Burp from inside a handler callback. Only meaningful as the "
     "return value of an interception hook."),

    (r"Filter$",
     "Predicate evaluated in-process against every item. Expressed as query parameters on "
     "the listing endpoints instead."),

    (r"^burp\.api\.montoya\.BurpExtension$",
     "The extension entry point itself, called once by Burp at load time."),

    (r"^burp\.api\.montoya\.extension\.ExtensionUnloadingHandler$",
     "Unload callback, invoked by Burp when the extension is removed."),

    (r"^burp\.api\.montoya\.intruder\.(PayloadGenerator|PayloadProcessor|PayloadData|"
     r"PayloadProcessingResult|IntruderInsertionPoint)$",
     "Payload generation runs inside Intruder's attack loop and is driven by callbacks."),

    (r"^burp\.api\.montoya\.scanner\.audit\.insertionpoint\.AuditInsertionPoint$",
     "Insertion points are supplied to Burp's audit engine through a provider callback."),

    (r"^burp\.api\.montoya\.http\.sessions\.SessionHandlingAction$",
     "Session handling actions are invoked by Burp while it prepares a request."),

    (r"^burp\.api\.montoya\.http\.sessions\.(ActionResult|SessionHandlingActionData)$",
     "Argument and return value of a session handling callback. Only exists while Burp is "
     "inside that callback."),

    (r"^burp\.api\.montoya\.repeater\.HttpEditor$",
     "Swing panes inside a Repeater tab. User interface, despite living outside the ui package."),

    (r"^burp\.api\.montoya\.http\.RequestResponseSelection$",
     "The user's text selection inside a Burp message editor. User interface state."),

    (r"^burp\.api\.montoya\.scanner\.Scanner$",
     "Registration of custom scan checks and insertion point providers. These take "
     "extension-supplied code that runs inside Burp's audit engine; a REST call cannot "
     "supply executable logic. Scans themselves are driven through /api/scanner."),

    (r"^burp\.api\.montoya\.intruder\.Intruder$",
     "Registration of payload generators and processors. These take extension-supplied "
     "code that Intruder calls per payload. Attacks are driven through /api/intruder."),

    (r"^burp\.api\.montoya\.intruder\.AttackConfiguration$",
     "Only reachable as the argument to PayloadGeneratorProvider.providePayloadGenerator, "
     "which is itself a callback Intruder invokes with extension-supplied code. Nothing else "
     "in Montoya returns one and it has no static factory."),

    (r"^burp\.api\.montoya\.logger\.",
     "Unreachable in this API version. Nothing in Montoya returns a LoggerHttpRequestResponse "
     "or LoggerCaptureHttpRequestResponse, and MontoyaApi has no logger() accessor, so an "
     "extension cannot obtain an instance to call these on."),
]


def find_montoya_jar():
    """Locate the Montoya API jar in the Gradle cache."""
    pattern = os.path.expanduser(
        "~/.gradle/caches/modules-2/files-2.1/net.portswigger.burp.extensions/"
        "montoya-api/*/*/montoya-api-*.jar"
    )
    jars = sorted(glob.glob(pattern))
    if not jars:
        sys.exit("Montoya API jar not found in the Gradle cache. Run a build first.")
    return jars[-1]


def class_names(jar):
    out = subprocess.run(["unzip", "-l", jar], capture_output=True, text=True).stdout
    names = []
    for line in out.splitlines():
        parts = line.split()
        if parts and parts[-1].endswith(".class"):
            name = parts[-1][:-len(".class")].replace("/", ".")
            if "$" not in name:
                names.append(name)
    return sorted(names)


def declared_methods(jar, names):
    """Return {class: {method, ...}} for every declared method."""
    out = subprocess.run(["javap", "-cp", jar] + names,
                         capture_output=True, text=True).stdout
    methods = collections.defaultdict(set)
    current = None
    decl = re.compile(r"^(?:public |abstract |final |static |default )*"
                      r"(?:interface|class|enum) ([\w.$]+)")
    call = re.compile(r"\b(\w+)\(([^)]*)\);")
    for line in out.splitlines():
        stripped = line.strip()
        header = decl.match(stripped)
        if header:
            current = header.group(1)
            continue
        if not current:
            continue
        found = call.search(stripped)
        if found:
            method = found.group(1)
            if method == current.split(".")[-1]:
                continue  # constructor
            if method in BUILTINS:
                continue
            methods[current].add(method)
    return methods


def reason_unmappable(cls):
    for pattern, reason in UNMAPPABLE:
        if re.search(pattern, cls):
            return reason
    return None


def source_blob():
    text = []
    for path in glob.glob(os.path.join(SRC, "**", "*.kt"), recursive=True):
        with open(path, encoding="utf-8") as handle:
            text.append(handle.read())
    return "\n".join(text)


def property_accesses(blob):
    """
    Every `.name` in the sources.

    Kotlin exposes a Java getFoo()/setFoo() pair as the property `.foo`, so a method can
    be fully used without its own name ever appearing. This set lets the checker resolve
    those.
    """
    return set(re.findall(r"\.(\w+)\b", blob))


def is_used(method, called, properties):
    if method in called:
        return True
    # Kotlin property syntax for a Java accessor: getState() reached as `.state`.
    match = re.match(r"^(?:get|set)([A-Z]\w*)$", method)
    if match:
        name = match.group(1)
        return (name[0].lower() + name[1:]) in properties
    return False


def referenced_names(blob):
    """Every identifier the Kotlin sources call. Deliberately generous."""
    return (
        # Ordinary calls: foo.bar(...)
        set(re.findall(r"\.(\w+)\s*\(", blob))
        # Bare or statically imported calls: bar(...)
        | set(re.findall(r"\b(\w+)\s*\(", blob))
        # Trailing-lambda calls, which have no parentheses: foo.bar { ... }
        | set(re.findall(r"\.(\w+)\s*\{", blob))
        # Method references: ::bar
        | set(re.findall(r"::(\w+)", blob))
        # Kotlin property access for Java isFoo() getters, which drops the parentheses
        | set(re.findall(r"\.(is[A-Z]\w*)\b", blob))
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--list", action="store_true",
                        help="list unreferenced mappable methods")
    parser.add_argument("--unmapped", action="store_true",
                        help="list excluded classes and the reason each is excluded")
    args = parser.parse_args()

    jar = find_montoya_jar()
    methods = declared_methods(jar, class_names(jar))
    blob = source_blob()
    used = referenced_names(blob)
    properties = property_accesses(blob)

    mappable_total = mappable_hit = 0
    excluded_total = 0
    gaps = collections.defaultdict(list)
    excluded = {}

    for cls, names in sorted(methods.items()):
        reason = reason_unmappable(cls)
        if reason:
            excluded_total += len(names)
            excluded[cls] = (reason, len(names))
            continue
        for method in sorted(names):
            mappable_total += 1
            if is_used(method, used, properties):
                mappable_hit += 1
            else:
                gaps[cls].append(method)

    print(f"Montoya API jar     : {os.path.basename(jar)}")
    print(f"Classes declared    : {len(methods)}")
    print(f"Methods declared    : {mappable_total + excluded_total}")
    print(f"  not REST-mappable : {excluded_total} across {len(excluded)} classes")
    print(f"  mappable          : {mappable_total}")
    pct = (100.0 * mappable_hit / mappable_total) if mappable_total else 100.0
    print(f"  referenced        : {mappable_hit} ({pct:.1f}% of mappable)")
    remaining = mappable_total - mappable_hit
    print(f"  unreferenced      : {remaining}")

    if args.unmapped:
        print("\nExcluded classes:")
        for cls, (reason, count) in sorted(excluded.items()):
            print(f"  {cls} ({count})\n      {reason}")

    if args.list and gaps:
        print("\nUnreferenced mappable methods:")
        for cls, names in sorted(gaps.items(), key=lambda kv: -len(kv[1])):
            print(f"  {cls} ({len(names)}): {', '.join(names)}")

    if remaining:
        if not args.list:
            print("\nRe-run with --list to see them.")
        return 1
    print("\nFull coverage of the mappable API surface.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

---
name: api-coverage-check
description: Check reburp's Montoya API coverage and detect when a Burp/Montoya upgrade added or changed APIs. Use after bumping montoya-api in build.gradle.kts or before a release. Caches tested versions to skip redundant runs.
---

# Check API coverage / detect Montoya changes

1. Get the version: `grep montoya-api build.gradle.kts` (the `compileOnly` line).
2. If it is already listed in `tools/.api-cache`, coverage was verified - stop.
3. Static gate: `python3 tools/api_coverage.py`. Non-zero exit = new/changed methods are unmapped. `--list` shows them, `--unmapped` shows exclusions. Wire each into a route or classify it, then rerun until clean.
4. Behavior (optional): start Burp with the extension loaded, then `python3 tools/smoke_test.py`.
5. On a clean run, cache it: `echo <version> >> tools/.api-cache`.

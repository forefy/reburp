# Contributing

Issues and pull requests are welcome.

Before opening a PR:

1. `./gradlew shadowJar` builds clean.
2. `python3 tools/api_coverage.py` passes (100% of the mappable Montoya surface).
3. New route groups ship with their OpenAPI fragment in `docs/` so the docs stay complete.
4. No em dashes in source; keep the loopback-only, unauthenticated threat model in mind.

See the [README](README.md) for architecture and the endpoint map.

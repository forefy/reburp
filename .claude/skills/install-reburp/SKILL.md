---
name: install-reburp
description: Install the reburp Burp extension - build it or grab the release jar, then load it into Burp Suite. Use when the user asks how to install, build, load, or set up reburp.
---

# Install reburp into Burp

**Get the jar** - either:
- Download the latest `reburp-*.jar` from https://github.com/forefy/reburp/releases, or
- Build it (needs Java 17+): `gradle shadowJar`, then print the absolute path to give the user:
  `ls "$PWD"/build/libs/reburp-*.jar`

**Load into Burp**
1. Burp -> Extensions -> Installed -> Add
2. Extension type: Java
3. Extension file: the jar path above
4. Next - it starts on port 9090 and a "reburp" tab appears.

**Verify**: `curl -s http://127.0.0.1:9090/api/status`. Swagger UI at http://127.0.0.1:9090/docs.

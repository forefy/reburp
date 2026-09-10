# Security Policy

reburp opens an **unauthenticated** REST server on `127.0.0.1:9090` that can fully drive Burp.
Any process or web page that can reach that port controls Burp. Keep it bound to loopback and
never expose it. OS command execution is off by default and gated behind `REBURP_ENABLE_SHELL=1`;
enabling it grants remote code execution to anything that can reach the port.

## Reporting a vulnerability

Please do **not** open a public issue for security bugs. Report privately via
[GitHub Security Advisories](https://github.com/forefy/reburp/security/advisories/new) or by
DM to [@forefy](https://twitter.com/forefy). We aim to respond within a few days.

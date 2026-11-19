# Capturing browser traffic

Part of the `burp-interaction` skill.

Only when the task needs real proxied traffic. Confirm whatever you pick is working by watching
`GET /api/proxy/history` grow. Prefer what is already set up, in this order:

1. **Already flowing?** If `/api/proxy/history` shows the target host, you are done.
2. **The user's own browser**, if they already run it through Burp (their normal setup - system
   proxy or a proxy-switcher like FoxyProxy, with Burp's CA trusted). Ask them to browse; you
   watch history.
3. **Burp's pre-wired browser** (Proxy -> Open browser; a local Chromium called **Burp Browser**):
   zero config - auto-proxied and already trusts Burp's CA. The user drives it; you cannot - it is
   a browser (read-only to desktop control) and its remote-debugging port is token-locked.
4. **Fallback that always works for *you* to drive.** Launch a Chromium-based browser (Chrome,
   Edge, or Brave - whatever the machine has) as a *separate* instance pointed at the target URL,
   with these launch flags. It needs none of the blocked steps below, so use it whenever the user
   is unsure or a setup is not routing:
   - `--user-data-dir=<a fresh temp dir>` - a throwaway profile, so it never touches the user's own
   - `--proxy-server=http://127.0.0.1:8080` - route everything through Burp
   - `--ignore-certificate-errors` - accept Burp's interception cert with no CA install
   - `--remote-debugging-port=<port>` - so you can drive it over CDP
   - `--no-first-run --no-default-browser-check`

   Find the browser's executable for the host OS (macOS: under `/Applications`; Windows: under
   `Program Files` or `%LOCALAPPDATA%`; Linux: `google-chrome` / `chromium` on `$PATH`), and quote
   the path if it has spaces. Then drive it over CDP: `GET http://127.0.0.1:<port>/json` for the
   page target's `webSocketDebuggerUrl`, then `Page.navigate` / `Runtime.evaluate`. Confirm
   `/api/proxy/history` grows; close the instance when done.

You **cannot**, in any browser: install extensions (the Chrome Web Store blocks all automation),
trust Burp's CA (needs the user's password), or click/type via desktop control (browsers are
read-only there). Don't burn turns on these - go to the fallback instead.

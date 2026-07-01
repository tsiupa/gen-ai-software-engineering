# Security Report — Bug 002

## Scope

Files reviewed (per `context/bugs/002/fix-summary.md`):

- **`src/scheduler.js`** — the only file changed by this fix (line 29, the
  `assignSlot()` boundary guard: `<=` → `<`).
- **`src/server.js`** — closely related code cited by the fix summary as the
  caller that converts a `null` slot into a `409` response
  (`src/server.js:58-64`). Reviewed as related code; **not modified by this
  fix**.
- **`src/queue.js`** — imported/related; `ticketNumber` source feeding
  `assignSlot()`.
- **`package.json`** — dependency surface.

Scan categories covered: injection, hardcoded secrets, insecure comparisons,
missing input validation, unsafe dependencies, XSS/CSRF.

## Findings

### 1. Hardcoded fallback secret for the doctor auth token — MEDIUM

- **Location:** `src/server.js:9`
- **Description:** `const DOCTOR_TOKEN = process.env.DOCTOR_TOKEN ?? 'doctor-secret-2024';`
  ships a hardcoded credential as the default. Any deployment that forgets to
  set `DOCTOR_TOKEN` silently authenticates with a value that is committed to
  source control, so anyone with repo access can call `POST /queue/next` and
  pull patient records (name + reason — health-related PII). This finding is
  **pre-existing and not introduced by the Bug 002 change** (Bug 002 only
  touches `src/scheduler.js:29`); it is reported because `src/server.js` is in
  the reviewed scope as related code.
- **Remediation:** Do not embed a real-looking fallback secret. Require
  `DOCTOR_TOKEN` to be set and fail closed when it is absent — e.g. throw at
  startup if `process.env.DOCTOR_TOKEN` is undefined, or in non-production
  generate a random per-process token. Remove the literal
  `'doctor-secret-2024'` from the codebase and rotate it if it was ever used.

### 2. Missing type/length validation on registration fields — LOW

- **Location:** `src/server.js:53-58`
- **Description:** `const { name, reason } = body ?? {};` then only checks
  truthiness (`!name || !reason`). `name`/`reason` may be arbitrarily large
  strings, or non-string JSON types (numbers, nested objects, arrays), which
  are stored verbatim in the in-memory queue (`src/queue.js:18-22`) and
  echoed back to the doctor client. No bound on payload size beyond Node's
  defaults, enabling unbounded memory growth via repeated large registrations.
  Not introduced by Bug 002; reported as related-code hygiene.
- **Remediation:** Validate that `name` and `reason` are strings, trim them,
  and enforce a sane maximum length (e.g. 200 chars). Reject other types with
  `400`. Consider a cap on total queue size.

### 3. Stored values are returned without output encoding — INFO

- **Location:** `src/server.js:66-69`, `src/server.js:84`
- **Description:** Patient-supplied `name`/`reason` are returned inside a JSON
  response (`Content-Type: application/json`). Because the API responds with
  JSON (not HTML) and performs no `innerHTML`-style rendering, there is **no
  XSS sink in this codebase**. The risk only materializes if a downstream
  consumer renders these fields as HTML without escaping. Noted for
  completeness; no action required in this service. There are no cookies,
  sessions, or browser-driven state-changing GETs, so **CSRF is not
  applicable** (auth is a custom header on `POST /queue/next`, which is not
  attacker-forgeable cross-site without the token).
- **Remediation:** None required here. Ensure any HTML consumer escapes these
  fields at render time.

### Changed code — `src/scheduler.js`

No security findings. The modified function `assignSlot()`
(`src/scheduler.js:24-35`) performs only integer arithmetic on a numeric index
and returns formatted time strings. The change `<=` → `<` at
`src/scheduler.js:29` is a correctness/boundary fix with no security impact: no
user-controlled string reaches it, no injection sink, no comparison of secret
material, and `slotIndex` is derived from a server-issued, monotonic
`ticketNumber` (`src/queue.js:8,17`). The comparison is purely numeric, so
timing-safety does not apply.

### Insecure comparison check (auth path) — no finding

`isValidToken()` (`src/server.js:34-44`) correctly uses
`crypto.timingSafeEqual` with a pre-length-check, which is the right
constant-time pattern. No weakness found.

### Unsafe dependencies — no finding

`package.json` declares **zero runtime/dev dependencies** and uses only Node
core modules (`node:http`, `node:url`, `node:crypto`), `engines.node >= 20`.
No third-party supply-chain surface to assess.

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 0 |
| HIGH     | 0 |
| MEDIUM   | 1 |
| LOW      | 1 |
| INFO     | 1 |

**Overall risk: LOW for the Bug 002 change itself.** The single-character
boundary fix in `src/scheduler.js:29` introduces no security risk and is sound.
The MEDIUM and LOW findings are **pre-existing issues in related code
(`src/server.js`)** surfaced during this review, not regressions from this
fix. The hardcoded fallback token (Finding 1) is the most material item and
should be addressed independently of Bug 002, as it exposes patient PII behind
a guessable, source-committed credential when `DOCTOR_TOKEN` is unset.

## References

- `src/scheduler.js:29` — changed boundary guard (no finding).
- `src/scheduler.js:24-35` — `assignSlot()` body reviewed.
- `src/server.js:9` — Finding 1 (hardcoded fallback secret).
- `src/server.js:53-58` — Finding 2 (missing validation).
- `src/server.js:66-69`, `src/server.js:84` — Finding 3 (output encoding / XSS scope).
- `src/server.js:34-44` — constant-time token comparison (no finding).
- `src/queue.js:8,17,18-22` — ticket source and stored fields.
- `package.json:7-15` — no third-party dependencies.

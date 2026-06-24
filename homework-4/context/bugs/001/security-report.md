# Security Report — Bug 001

## Scope

Files reviewed (per `context/bugs/001/fix-summary.md`):

- `src/queue.js` — the file changed by the Bug Fixer (`dequeueNext()`, `pop()` → `shift()`).
- `src/server.js` — call sites of the changed code (registration, doctor pull, auth).
- `src/scheduler.js` — imported by `server.js` and exercised on the registration path.
- `package.json` — dependency surface.

The fix itself (`pop()` → `shift()`) is a logic/ordering change with **no security
impact**. The findings below come from the surrounding code that the changed
method is integrated with, which falls within the review scope ("changed files
plus their imports / closely related code").

## Findings

### 1. Hardcoded secret (doctor auth token) committed to source
- **Severity:** HIGH
- **Location:** `src/server.js:7`
- **Description:** The doctor authentication token is hardcoded as a string
  literal (`const DOCTOR_TOKEN = 'doctor-secret-2024';`) and committed to the
  repository. Anyone with read access to the source (or git history) obtains the
  credential that gates the privileged `/queue/next` endpoint. The value cannot
  be rotated without a code change and redeploy, and it is identical across all
  environments.
- **Remediation:** Load the token from an environment variable / secret manager
  at startup and fail closed if it is unset, e.g.
  `const DOCTOR_TOKEN = process.env.DOCTOR_TOKEN;` with a guard that refuses to
  start when it is missing or empty. Remove the literal from source and rotate
  the leaked value.

### 2. Non-constant-time token comparison (timing side channel)
- **Severity:** LOW
- **Location:** `src/server.js:62`
- **Description:** Authentication compares the presented token with `!==`
  (`if (token !== DOCTOR_TOKEN)`). JavaScript string equality short-circuits on
  the first differing byte, leaking timing information that can theoretically be
  used to recover the secret byte-by-byte. (Rated LOW because remote network
  jitter makes this hard to exploit in practice and it is secondary to finding
  #1, but it should be fixed when the token handling is hardened.)
- **Remediation:** Use a constant-time comparison, e.g.
  `crypto.timingSafeEqual(Buffer.from(token), Buffer.from(DOCTOR_TOKEN))` after
  first verifying `token` is a present string of matching length (guard against
  `undefined` and length mismatch before constructing the buffers).

### 3. Unbounded request body — memory-exhaustion DoS
- **Severity:** MEDIUM
- **Location:** `src/server.js:15–30` (`readJson`, accumulation at line 18:
  `raw += chunk;`)
- **Description:** The body reader concatenates every incoming chunk into `raw`
  with no size cap. A single client can stream an arbitrarily large body to
  `POST /appointments` (an unauthenticated endpoint) and force the process to
  buffer it entirely in memory before `JSON.parse`, enabling a low-effort
  denial-of-service against a single-process Node server.
- **Remediation:** Enforce a maximum body size: track the accumulated length and
  respond `413 Payload Too Large` (and `req.destroy()`) once a threshold (e.g.
  a few KB for this payload shape) is exceeded.

### 4. Weak input validation on registration fields
- **Severity:** MEDIUM
- **Location:** `src/server.js:39–42`
- **Description:** Registration only checks truthiness:
  `if (!name || !reason)`. It does not validate type or length. A JSON body such
  as `{"name": {"x":1}, "reason": [1,2,3]}` passes the check, so non-string
  objects/arrays flow into `queue.enqueue` and are stored and later echoed back
  verbatim in the `/queue/next` response. There is also no length bound, so
  arbitrarily large strings can be stored per entry (unbounded in-memory growth,
  compounding finding #3). The stored values are reflected unsanitized to the
  doctor client (`src/queue.js:18–24`, returned at `src/server.js:70`).
- **Remediation:** Validate that `name` and `reason` are strings of bounded,
  non-empty length (e.g. `typeof name === 'string' && name.trim().length > 0 &&
  name.length <= 200`) before enqueuing; reject with `400` otherwise.

### 5. Reflected, unsanitized user input returned to client
- **Severity:** INFO
- **Location:** `src/server.js:70`; data stored at `src/queue.js:18–24`
- **Description:** Patient-supplied `name` / `reason` are returned to the doctor
  client without sanitization. This is **not** an XSS vulnerability in the API
  itself: every response is sent with `Content-Type: application/json`
  (`src/server.js:11`) and the body is `JSON.stringify`-encoded, so no markup is
  interpreted server-side. It is flagged INFO as a defensive note: if a
  downstream web/UI client renders these fields as HTML, it — not this service —
  must encode them to avoid stored XSS.
- **Remediation:** No change required in this service. Document that consumers
  must output-encode these fields; pairing with the validation in finding #4
  limits what can be stored.

### 6. Injection vectors — none found
- **Severity:** INFO
- **Location:** `src/server.js`, `src/queue.js`, `src/scheduler.js`
- **Description:** No SQL, no shell/`child_process`, no `eval`/`Function`, no
  dynamic `require`/`import`, and no template rendering are present. Body parsing
  uses `JSON.parse` (no prototype-pollution sink is exercised — parsed values are
  only read by key, never merged into an object). No injection finding.

### 7. Dependency surface — no third-party packages
- **Severity:** INFO
- **Location:** `package.json`
- **Description:** The project declares no runtime or dev dependencies; only
  Node.js built-in modules (`node:http`, `node:url`) are used. There is no
  third-party supply-chain or known-CVE exposure to assess. (`engines.node
  >= 20` — keep the Node runtime patched.)

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 0 |
| HIGH     | 1 |
| MEDIUM   | 2 |
| LOW      | 1 |
| INFO     | 3 |

**Overall risk:** The Bug Fixer's change (FIFO `pop()` → `shift()`) is
security-neutral. However, the surrounding code carries a meaningful risk
concentration: a hardcoded, source-committed doctor token (HIGH) is the primary
concern, compounded by an unbounded request body and weak input validation (both
MEDIUM) that together expose the unauthenticated registration endpoint to
denial-of-service and malformed-data storage. None are CRITICAL and none are
introduced by this fix, but the HIGH credential finding should be remediated
before any non-local deployment. **Report only — no code was modified.**

## References

- `src/queue.js:32` — changed line (`items.shift() ?? null`) — security-neutral.
- `src/queue.js:18–24` — user input stored verbatim in queue entry.
- `src/server.js:7` — hardcoded `DOCTOR_TOKEN` (Finding #1).
- `src/server.js:62` — non-constant-time token comparison (Finding #2).
- `src/server.js:15–30` / `:18` — unbounded body accumulation (Finding #3).
- `src/server.js:39–42` — weak field validation (Finding #4).
- `src/server.js:11`, `:70` — JSON content-type and reflected fields (Finding #5).
- `package.json` — no third-party dependencies (Finding #7).

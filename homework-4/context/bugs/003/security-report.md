# Security Report — Bug 003: Insecure doctor authentication

## Scope

Reviewed the files referenced by `context/bugs/003/fix-summary.md` and their
immediate dependencies:

- `src/server.js` — the only file changed by the fix (imports, `DOCTOR_TOKEN`
  constant, `isValidToken` helper, `/queue/next` auth check).
- `src/queue.js` — imported by `server.js` (`createQueue`).
- `src/scheduler.js` — imported by `server.js` (`assignSlot`).
- `package.json` — dependency surface.

The fix itself (constant-time token comparison via `timingSafeEqual`, sourcing
`DOCTOR_TOKEN` from the environment, and the `typeof` guard) is sound and
correctly resolves the original timing-attack and hardcoded-secret bug. The
findings below cover residual and adjacent issues observed during the review.

## Findings

### 1. Hardcoded secret retained as a runtime fallback

- **Severity**: HIGH
- **Location**: `src/server.js:9`
- **Description**: `const DOCTOR_TOKEN = process.env.DOCTOR_TOKEN ?? 'doctor-secret-2024';`
  still embeds a real, working credential in source control. Sourcing from the
  environment is correct, but the `??` fallback means that any deployment that
  forgets to set `DOCTOR_TOKEN` silently runs with a publicly known token
  committed to git history. An attacker who reads the repository can
  authenticate against `POST /queue/next` and pull patient PII (name + reason)
  whenever the env var is absent. The constant-time comparison provides no
  protection when the secret value itself is public.
- **Remediation**: Remove the literal fallback. Read the token once at startup
  and fail fast if it is missing:
  ```js
  const DOCTOR_TOKEN = process.env.DOCTOR_TOKEN;
  if (!DOCTOR_TOKEN) {
    throw new Error('DOCTOR_TOKEN environment variable is required');
  }
  ```
  Have tests inject `process.env.DOCTOR_TOKEN` (or accept the token via
  `createServer(options)`) instead of relying on a committed default. Rotate the
  now-exposed `doctor-secret-2024` value in any environment where it was used.

### 2. Unbounded request body — denial-of-service risk

- **Severity**: MEDIUM
- **Location**: `src/server.js:17-32` (`readJson`)
- **Description**: `readJson` accumulates the entire request body into the `raw`
  string (`raw += chunk`) with no size cap. A client can stream an arbitrarily
  large payload to `POST /appointments` and exhaust server memory. Because the
  process is a single in-memory server, one oversized request can degrade or
  crash the service for all users.
- **Remediation**: Enforce a maximum body size and abort early:
  ```js
  const MAX_BODY = 64 * 1024; // 64 KB
  let raw = '';
  req.on('data', (chunk) => {
    raw += chunk;
    if (raw.length > MAX_BODY) {
      sendJson(res, 413, { error: 'Payload too large' });
      req.destroy();
    }
  });
  ```

### 3. Weak input validation on `/appointments`

- **Severity**: MEDIUM
- **Location**: `src/server.js:53-58`
- **Description**: The handler only checks `!name || !reason` (truthiness). It
  does not validate type or length, so a client can submit non-string values
  (e.g. `{"name": {"$gt": ""}, "reason": [1,2,3]}`) or megabyte-long strings.
  These values are stored verbatim in the queue and later returned to the doctor
  via `dequeueNext()`. Storing unvalidated structured/oversized data invites
  downstream injection (whatever consumes the doctor response — a UI, a log
  pipeline, a database) and memory growth.
- **Remediation**: Validate type and bound length before enqueuing:
  ```js
  if (typeof name !== 'string' || typeof reason !== 'string'
      || name.trim() === '' || reason.trim() === ''
      || name.length > 200 || reason.length > 1000) {
    return sendJson(res, 400, { error: 'name and reason must be non-empty strings' });
  }
  ```

### 4. No rate limiting / lockout on the authentication endpoint

- **Severity**: LOW
- **Location**: `src/server.js:74-78`
- **Description**: `POST /queue/next` accepts unlimited token attempts. The
  constant-time comparison defeats timing analysis, but nothing prevents an
  attacker from brute-forcing the token via high-volume requests, especially if
  the weak hardcoded fallback (Finding 1) is in effect.
- **Remediation**: Add basic rate limiting / temporary lockout per source IP on
  repeated `401` responses (e.g. a sliding-window counter or a reverse proxy
  rule), and ensure `DOCTOR_TOKEN` is high-entropy (≥ 32 random bytes).

### 5. Patient PII returned and held in plaintext in memory

- **Severity**: INFO
- **Location**: `src/queue.js:16-33`, `src/server.js:84`
- **Description**: Patient `name` and `reason` (potentially sensitive health
  information) are stored in plaintext in the in-memory queue and returned in
  the `/queue/next` response. This is expected for the sample app, but in a real
  clinical context this data is regulated PHI. No XSS sink exists here (responses
  are `application/json`, never rendered as HTML by this service), so the XSS/CSRF
  surface is not exploitable within `server.js` itself — the risk shifts to any
  consumer that renders the returned values.
- **Remediation**: For production use, serve over TLS, restrict access to the
  doctor endpoint, and ensure any client rendering `name`/`reason` performs
  contextual output encoding. No action required for the sample scope.

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 0 |
| HIGH     | 1 |
| MEDIUM   | 2 |
| LOW      | 1 |
| INFO     | 1 |

**Overall risk: MEDIUM.** The Bug 003 fix correctly addresses the original
timing-unsafe comparison and removes the hardcoded token as the *primary*
source — `timingSafeEqual` is used properly and the `typeof` guard safely
handles missing headers. However, the retained `'doctor-secret-2024'` fallback
(Finding 1) re-introduces a known-credential exposure whenever the environment
variable is unset, which materially weakens the fix in any misconfigured
deployment. Removing that fallback and adding request-size / input validation
(Findings 2–3) would bring the changed code to a low residual risk.

## References

- `src/server.js:9` — hardcoded `DOCTOR_TOKEN` fallback (Finding 1)
- `src/server.js:17-32` — unbounded `readJson` body (Finding 2)
- `src/server.js:53-58` — weak `/appointments` validation (Finding 3)
- `src/server.js:34-44` — `isValidToken` constant-time comparison (reviewed, correct)
- `src/server.js:74-78` — `/queue/next` auth check, no rate limiting (Finding 4)
- `src/queue.js:16-33`, `src/server.js:84` — plaintext PII handling (Finding 5)
- `package.json:1-16` — no third-party dependencies (no unsafe-dependency findings)

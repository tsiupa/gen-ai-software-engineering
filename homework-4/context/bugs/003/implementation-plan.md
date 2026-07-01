# Implementation Plan — Bug 003: Insecure doctor authentication

## Gate Status

Verified research (`context/bugs/003/research/verified-research.md`) is
**PASS / L4**. Proceeding to plan without reservations.

## Objective

Fix `/queue/next` doctor authentication in `src/server.js` so that:

1. The doctor token is no longer a hardcoded, version-controlled plaintext
   secret — it must be sourceable from `process.env.DOCTOR_TOKEN`, consistent
   with the existing `process.env.PORT` config pattern (`src/server.js:84`).
2. The token comparison is constant-time (`crypto.timingSafeEqual`) instead of
   the native `!==` operator, removing the timing-side-channel concern.
3. Existing behavior is preserved: missing/invalid token → `401`; matching
   token → request proceeds. No test file changes are required because the
   fallback default keeps the literal value `tests/api.test.js:5` already
   sends.

## Changes

### 1. `src/server.js` — import `timingSafeEqual`

Location: top-of-file imports (lines 1–4).

**Before:**
```js
import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { createQueue } from './queue.js';
import { assignSlot } from './scheduler.js';
```

**After:**
```js
import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { timingSafeEqual } from 'node:crypto';
import { createQueue } from './queue.js';
import { assignSlot } from './scheduler.js';
```

Rationale: needed for the constant-time comparison added in change 3.

### 2. `src/server.js` — source the secret from the environment

Location: lines 6–7.

**Before:**
```js
// Token the doctor client must present to pull patients from the queue.
const DOCTOR_TOKEN = 'doctor-secret-2024';
```

**After:**
```js
// Token the doctor client must present to pull patients from the queue.
// Falls back to the legacy literal only when DOCTOR_TOKEN is unset (local dev/tests).
const DOCTOR_TOKEN = process.env.DOCTOR_TOKEN ?? 'doctor-secret-2024';
```

Rationale: matches the existing `PORT` config pattern (`src/server.js:84`,
`process.env.PORT ?? 3000`) and lets deployments override the secret without
touching source. The fallback keeps `tests/api.test.js:5` and
`tests/api.test.js:67` working unmodified since no test sets
`process.env.DOCTOR_TOKEN`.

### 3. `src/server.js` — add a constant-time token check

Location: insert immediately after the `readJson` function (currently lines
15–30) and before `export function createServer()` (currently line 32).

**Before (anchor — end of `readJson`, start of `createServer`):**
```js
function readJson(req, res, callback) {
  let raw = '';
  req.on('data', (chunk) => {
    raw += chunk;
  });
  req.on('end', () => {
    if (raw.length === 0) {
      return callback({});
    }
    try {
      return callback(JSON.parse(raw));
    } catch {
      return sendJson(res, 400, { error: 'Invalid JSON body' });
    }
  });
}

export function createServer() {
```

**After:**
```js
function readJson(req, res, callback) {
  let raw = '';
  req.on('data', (chunk) => {
    raw += chunk;
  });
  req.on('end', () => {
    if (raw.length === 0) {
      return callback({});
    }
    try {
      return callback(JSON.parse(raw));
    } catch {
      return sendJson(res, 400, { error: 'Invalid JSON body' });
    }
  });
}

function isValidToken(provided) {
  if (typeof provided !== 'string') {
    return false;
  }
  const expected = Buffer.from(DOCTOR_TOKEN);
  const candidate = Buffer.from(provided);
  if (candidate.length !== expected.length) {
    return false;
  }
  return timingSafeEqual(candidate, expected);
}

export function createServer() {
```

Rationale: `timingSafeEqual` throws if buffer lengths differ, so the length
guard must run first; that guard is on the candidate length only and leaks no
more than the current `!==` behavior already does. The `typeof` guard handles
the missing-header case (`undefined`) without calling `Buffer.from` on a
non-string.

### 4. `src/server.js` — use the constant-time check in the auth branch

Location: lines 60–64 (the `/queue/next` branch).

**Before:**
```js
    if (req.method === 'POST' && req.url === '/queue/next') {
      const token = req.headers['x-doctor-token'];
      if (token !== DOCTOR_TOKEN) {
        return sendJson(res, 401, { error: 'Unauthorized' });
      }
```

**After:**
```js
    if (req.method === 'POST' && req.url === '/queue/next') {
      const token = req.headers['x-doctor-token'];
      if (!isValidToken(token)) {
        return sendJson(res, 401, { error: 'Unauthorized' });
      }
```

Rationale: replaces the non-constant-time `!==` with the constant-time helper
from change 3, fixing the root cause at `src/server.js:62`.

## Test Command

```
npm test
```

Expected outcome: all existing tests in `tests/api.test.js` pass unmodified —
specifically:
- `doctor cannot pull the queue without a valid token` → still `401`
  (missing header → `isValidToken(undefined)` → `false`).
- `doctor pulls the only waiting patient` → still `200` (test's
  `DOCTOR_TOKEN = 'doctor-secret-2024'` matches the server's fallback
  default since `process.env.DOCTOR_TOKEN` is unset in the test run).

## Risk / Notes

- No test file changes are needed or planned; do not edit
  `tests/api.test.js`.
- The fallback literal (`'doctor-secret-2024'`) is intentionally retained as a
  dev/test default, mirroring the `PORT ?? 3000` pattern. It is a known
  residual: production deployments must set `process.env.DOCTOR_TOKEN` to a
  real secret, since an unset env var still falls back to the old literal.
  This is the minimal fix consistent with not breaking the existing test
  suite; flag to the user if a stricter "no fallback, fail closed without
  env var" policy is desired instead.
- `isValidToken` must run the `typeof` check before `Buffer.from`, and the
  length check before `timingSafeEqual`, or it will throw on a missing
  header or on a token of different length.
- No other references to `DOCTOR_TOKEN` exist outside the four locations
  touched above and the two read-only test references (confirmed via
  `grep -rn "DOCTOR_TOKEN" src tests`).

## References

- `src/server.js:7` — hardcoded `DOCTOR_TOKEN` literal (fixed by change 2).
- `src/server.js:60-64` — `/queue/next` authorization branch (fixed by change 4).
- `src/server.js:62` — `token !== DOCTOR_TOKEN` non-constant-time comparison (fixed by changes 3–4).
- `src/server.js:84` — `process.env.PORT ?? 3000` pattern mirrored in change 2.
- `tests/api.test.js:5` — test-side duplicate secret; left unmodified.
- `tests/api.test.js:67` — test sends token via `x-doctor-token`; left unmodified.

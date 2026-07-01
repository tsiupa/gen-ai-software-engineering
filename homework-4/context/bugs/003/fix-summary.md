# Fix Summary — Bug 003: Insecure doctor authentication

## Changes Made

### Change 1: Import `timingSafeEqual` from `node:crypto`
- **File**: `src/server.js`
- **Location**: line 3 (added to imports)
- **Before**: 
  ```js
  import http from 'node:http';
  import { fileURLToPath } from 'node:url';
  import { createQueue } from './queue.js';
  import { assignSlot } from './scheduler.js';
  ```
- **After**:
  ```js
  import http from 'node:http';
  import { fileURLToPath } from 'node:url';
  import { timingSafeEqual } from 'node:crypto';
  import { createQueue } from './queue.js';
  import { assignSlot } from './scheduler.js';
  ```
- **Test result**: ✔ PASS (all 8 tests)

### Change 2: Source DOCTOR_TOKEN from environment with fallback
- **File**: `src/server.js`
- **Location**: lines 6–8
- **Before**:
  ```js
  // Token the doctor client must present to pull patients from the queue.
  const DOCTOR_TOKEN = 'doctor-secret-2024';
  ```
- **After**:
  ```js
  // Token the doctor client must present to pull patients from the queue.
  // Falls back to the legacy literal only when DOCTOR_TOKEN is unset (local dev/tests).
  const DOCTOR_TOKEN = process.env.DOCTOR_TOKEN ?? 'doctor-secret-2024';
  ```
- **Test result**: ✔ PASS (all 8 tests)

### Change 3: Insert `isValidToken` helper function
- **File**: `src/server.js`
- **Location**: lines 32–41 (inserted after `readJson`, before `createServer`)
- **Code added**:
  ```js
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
  ```
- **Test result**: ✔ PASS (all 8 tests)

### Change 4: Use constant-time comparison in `/queue/next` auth check
- **File**: `src/server.js`
- **Location**: lines 69–70
- **Before**:
  ```js
  if (req.method === 'POST' && req.url === '/queue/next') {
    const token = req.headers['x-doctor-token'];
    if (token !== DOCTOR_TOKEN) {
      return sendJson(res, 401, { error: 'Unauthorized' });
    }
  ```
- **After**:
  ```js
  if (req.method === 'POST' && req.url === '/queue/next') {
    const token = req.headers['x-doctor-token'];
    if (!isValidToken(token)) {
      return sendJson(res, 401, { error: 'Unauthorized' });
    }
  ```
- **Test result**: ✔ PASS (all 8 tests)

## Overall Status

**SUCCESS** — All changes applied exactly as specified. All 8 existing tests pass:
- ✔ health check responds with ok
- ✔ registering a patient returns a ticket and the first time slot
- ✔ registration without name or reason is rejected
- ✔ doctor cannot pull the queue without a valid token
- ✔ doctor pulls the only waiting patient
- ✔ dequeueNext returns patients in FIFO order
- ✔ dequeueNext returns null when the queue is empty
- ✔ dequeueNext on a single-item queue returns that item, not the most recently added one

Test output: `8 pass, 0 fail` in 594.6ms

## Manual Verification

To verify the security fix in production:

1. **Token from environment** — set `DOCTOR_TOKEN=my-secret` and start the server:
   ```bash
   DOCTOR_TOKEN=my-secret npm start
   ```
   The server now accepts only `my-secret`, not the hardcoded `'doctor-secret-2024'`.

2. **Fallback for local dev** — run without setting `DOCTOR_TOKEN`:
   ```bash
   npm start
   ```
   The server falls back to `'doctor-secret-2024'` (existing behavior preserved).

3. **Constant-time comparison** — send an invalid token:
   ```bash
   curl -X POST http://localhost:3000/queue/next \
     -H "x-doctor-token: wrong-token"
   ```
   Returns `401 Unauthorized`. The comparison now takes constant time regardless of how much of the token matches.

4. **Valid token** — send the correct token (using fallback):
   ```bash
   curl -X POST http://localhost:3000/queue/next \
     -H "x-doctor-token: doctor-secret-2024"
   ```
   Returns `200` with the next patient (or `404` if queue is empty).

5. **Missing token** — omit the header:
   ```bash
   curl -X POST http://localhost:3000/queue/next
   ```
   Returns `401 Unauthorized`. The `typeof` guard safely handles `undefined`.

## References

- Implementation plan: `context/bugs/003/implementation-plan.md`
- Modified file: `src/server.js` (lines 3, 6–8, 32–41, 69–70)
- Affected endpoints: `POST /queue/next`
- Affected constant: `DOCTOR_TOKEN` (line 8)
- Helper function: `isValidToken` (lines 32–41)
- Test file: `tests/api.test.js` (unchanged; tests still pass with fallback)

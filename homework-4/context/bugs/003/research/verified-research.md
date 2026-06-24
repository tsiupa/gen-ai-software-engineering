# Verified Research — Bug 003: Insecure doctor authentication

## Verification Summary

- **Result: PASS**
- **Research Quality: L4 (Excellent)**
- **Accuracy: 7/7 (100%)**

Every claim in `codebase-research.md` was checked against the application source.
All `file:line` references resolve to the lines claimed, every quoted snippet
matches the source **verbatim**, and the root-cause reasoning (hardcoded secret +
non-constant-time `!==` comparison) is consistent with the code. No discrepancies
were found.

## Verified Claims

1. **Hardcoded secret constant** — `src/server.js:7`
   `const DOCTOR_TOKEN = 'doctor-secret-2024';`
   Confirmed verbatim. A plaintext credential is baked into source and is
   version-controlled.

2. **Existing env-config pattern for PORT** — `src/server.js:84`
   `const PORT = process.env.PORT ?? 3000;`
   Confirmed verbatim. The codebase already reads config from `process.env`,
   substantiating the claim that `DOCTOR_TOKEN` deviates from that pattern.

3. **Authorization branch snippet** — `src/server.js:60-64`
   ```js
       if (req.method === 'POST' && req.url === '/queue/next') {
         const token = req.headers['x-doctor-token'];
         if (token !== DOCTOR_TOKEN) {
           return sendJson(res, 401, { error: 'Unauthorized' });
         }
   ```
   Confirmed verbatim against lines 60–64.

4. **Non-constant-time comparison** — `src/server.js:62`
   `if (token !== DOCTOR_TOKEN) {`
   Confirmed. The check uses the native `!==` operator rather than a timing-safe
   primitive — technically accurate basis for the timing-side-channel concern.

5. **Absent-header behavior (rejected today, but incidental)** — `src/server.js:61-63`
   Confirmed by code logic: when `x-doctor-token` is missing, `token` is
   `undefined`, and `undefined !== 'doctor-secret-2024'` evaluates to `true`, so
   the request is rejected with 401. There is no separate explicit guard before
   the comparison — the rejection is an incidental consequence of `!==`, exactly
   as the research states. No crash or bypass exists for the missing-header path.

6. **Test-side duplicate secret** — `tests/api.test.js:5`
   `const DOCTOR_TOKEN = 'doctor-secret-2024';`
   Confirmed verbatim. The test independently re-declares the same literal.

7. **Test sends the hardcoded token** — `tests/api.test.js:67`
   `headers: { 'x-doctor-token': DOCTOR_TOKEN },`
   Confirmed verbatim (within the `doctor pulls the only waiting patient` test).
   The note that any env-based fix must keep this test able to supply a matching
   token is valid.

## Discrepancies Found

None. All references and snippets are accurate, and no inaccurate or
unverifiable claims were identified.

## Research Quality Assessment

**Level: L4 (Excellent).** Per the research-quality-measurement skill, L4
requires 100% of claims verified, all snippets verbatim, a precise and
actionable root cause, and no discrepancies — all satisfied here:

- Accuracy ratio = 7/7 = 100%, meeting the L4 threshold (and exceeding the L3
  ≥90% bar).
- All six quoted `file:line` snippets (server.js:7, 60–64, 84; api.test.js:5,
  67) match the source byte-for-byte; no off-by-one or whitespace drift.
- The root cause is precise and dual-pronged — (a) secret hardcoded at
  `src/server.js:7` instead of sourced from `process.env`, and (b) authorization
  at `src/server.js:62` using native `!==` instead of `crypto.timingSafeEqual`
  — and both defects are correctly traced to the same branch (`src/server.js:60-64`).
- The research is appropriately careful: it explicitly verifies that the
  missing-header case is *not* a present-day bypass (rejected via `undefined !==`)
  while still justifying the acceptance criterion for an explicit early guard.
  This nuance was independently confirmed and is correct.

No claim was downgraded to Inaccurate or Unverifiable, so no caution flags carry
forward.

## Gate Decision

L4 → **research is trustworthy; the Bug Planner may proceed** without
reservations.

## References (every file:line checked)

- `src/server.js:7` — hardcoded `DOCTOR_TOKEN` literal. ✓ Verified
- `src/server.js:60-64` — `/queue/next` authorization branch. ✓ Verified
- `src/server.js:61` — `req.headers['x-doctor-token']` read. ✓ Verified
- `src/server.js:62` — `token !== DOCTOR_TOKEN` comparison. ✓ Verified
- `src/server.js:84` — `process.env.PORT ?? 3000` config pattern. ✓ Verified
- `tests/api.test.js:5` — test-side duplicate secret. ✓ Verified
- `tests/api.test.js:67` — test sends token via `x-doctor-token`. ✓ Verified

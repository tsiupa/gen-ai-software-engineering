# Codebase Research — Bug 003: Insecure doctor authentication

## Bug Summary

The doctor-only `POST /queue/next` endpoint is protected by a token, `DOCTOR_TOKEN`,
that is hardcoded as a plaintext string literal directly in `src/server.js` and
committed to the repository. The check that validates the incoming
`x-doctor-token` header against this secret uses the plain `===` operator, which
is not constant-time and is therefore vulnerable to timing-based side-channel
analysis. There is no environment/config-based override, and no explicit
rejection path documented for a missing header (it falls through to the same
mismatch comparison).

## Findings

### 1. Hardcoded secret constant

`src/server.js:7`
```js
const DOCTOR_TOKEN = 'doctor-secret-2024';
```
The doctor's authentication secret is a literal string baked into the source
file. It is version-controlled, so anyone with read access to the repository
(or its history) learns the credential. There is no `process.env` lookup or
external configuration source — `PORT` is read from the environment at
`src/server.js:84` (`process.env.PORT ?? 3000`), showing the codebase already
has a pattern for environment-based config that `DOCTOR_TOKEN` does not follow.

### 2. Non-constant-time, header-presence-agnostic comparison

`src/server.js:60-64`
```js
    if (req.method === 'POST' && req.url === '/queue/next') {
      const token = req.headers['x-doctor-token'];
      if (token !== DOCTOR_TOKEN) {
        return sendJson(res, 401, { error: 'Unauthorized' });
      }
```
- `token !== DOCTOR_TOKEN` is a strict string/value inequality check performed
  by the JS engine in a way that is not guaranteed constant-time: V8's string
  comparison can short-circuit on the first mismatched byte, making response
  timing correlate with how many leading characters of a guessed token are
  correct. This is the textbook setup for a timing attack against the secret.
- If the `x-doctor-token` header is entirely absent, `req.headers['x-doctor-token']`
  is `undefined`. `undefined !== DOCTOR_TOKEN` evaluates to `true`, so the
  request **is** rejected with 401 today — there is no crash or bypass for a
  missing header. However, this rejection happens only as an incidental
  consequence of the `!==` check; there is no explicit, separate guard that
  short-circuits before any secret-comparison logic, which the acceptance
  criteria call for ("request rejected when the header is absent").

### 3. Secret duplicated in the test suite

`tests/api.test.js:5`
```js
const DOCTOR_TOKEN = 'doctor-secret-2024';
```
`tests/api.test.js:67`
```js
      headers: { 'x-doctor-token': DOCTOR_TOKEN },
```
The test file independently re-declares the same literal secret to exercise the
authenticated path. Any fix that sources the secret from configuration/environment
must keep this test (and any new ones) able to supply a matching token via the
same mechanism (e.g. setting `process.env.DOCTOR_TOKEN` before `createServer()`
is invoked), or the existing authenticated-path test will break.

## Root Cause

`DOCTOR_TOKEN` (`src/server.js:7`) is defined as a hardcoded literal rather than
being read from `process.env` (or another external config source), and the
authorization check at `src/server.js:62` uses the native `!==`/`===` operator
for the comparison instead of a timing-safe primitive such as
`crypto.timingSafeEqual`. Both defects stem from the same authorization branch
(`src/server.js:60-64`) treating the secret as ordinary source code rather than
as sensitive configuration requiring secure handling.

## Suggested Direction

- Source the doctor secret from the environment (e.g. `process.env.DOCTOR_TOKEN`,
  mirroring the existing `process.env.PORT` pattern at `src/server.js:84`),
  with no fallback hardcoded value committed to the repo.
- Replace the `!==` comparison with a constant-time comparison
  (`crypto.timingSafeEqual`), being careful to compare buffers of equal length
  (e.g. by hashing both sides or padding/length-checking before calling
  `timingSafeEqual`, since it throws on mismatched buffer lengths) so that the
  function itself doesn't leak length information through an exception path.
- Add an explicit, early guard that rejects the request with 401 when the
  `x-doctor-token` header is missing/empty, before any comparison logic runs,
  so the "no header → rejected" behavior is intentional rather than incidental.
- Update `tests/api.test.js` (and any new token-path tests) to obtain the
  expected token via the same environment-variable mechanism the server now
  uses, so the authenticated-path test keeps passing.

## References

- `src/server.js:7` — hardcoded `DOCTOR_TOKEN` literal.
- `src/server.js:60-64` — `/queue/next` authorization branch using `!==`.
- `src/server.js:84` — existing `process.env.PORT ?? 3000` pattern for config.
- `tests/api.test.js:5` — test-side duplicate of the hardcoded secret.
- `tests/api.test.js:67` — test sending the hardcoded token via `x-doctor-token` header.

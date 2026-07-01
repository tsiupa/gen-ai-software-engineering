# Test Report — Bug 003: Insecure doctor authentication

## Tests Added

File: `tests/auth.test.js` (new)

| Test | Behaviour covered |
|------|--------------------|
| `doctor pulls the queue with the correct token` | `isValidToken` accepts the legacy/fallback token via `POST /queue/next` and returns 200. |
| `doctor cannot pull the queue with a same-length wrong token` | `isValidToken` rejects a wrong token of identical byte length to `DOCTOR_TOKEN` — guards the `timingSafeEqual` length-equality branch (lines 40-42 of `src/server.js`), the case the original insecure `!==` comparison also caught, but which is the specific path the constant-time fix had to preserve. |
| `doctor cannot pull the queue with a different-length wrong token` | `isValidToken` rejects a token whose length differs from `DOCTOR_TOKEN`, exercising the early-return length check (line 40-42) before `timingSafeEqual` is called (which would throw on mismatched buffer lengths). |
| `doctor cannot pull the queue when the token header is duplicated (non-string value)` | When a header is sent twice, Node parses `req.headers['x-doctor-token']` as an array, not a string. Exercises the `typeof provided !== 'string'` guard (line 35-37) added in the fix — without it, comparing a non-string would throw or behave inconsistently. |
| `doctor cannot pull the queue with an empty token` | Empty string is a `string` type but wrong length — confirms the length check rejects it rather than falling through. |

## Test Run

```
> doctor-appointment-queue@1.0.0 test
> node --test

✔ health check responds with ok (14.4ms)
✔ registering a patient returns a ticket and the first time slot (8.1ms)
✔ registration without name or reason is rejected (2.5ms)
✔ doctor cannot pull the queue without a valid token (1.6ms)
✔ doctor pulls the only waiting patient (2.9ms)
✔ doctor pulls the queue with the correct token (32.3ms)
✔ doctor cannot pull the queue with a same-length wrong token (13.1ms)
✔ doctor cannot pull the queue with a different-length wrong token (2.0ms)
✔ doctor cannot pull the queue when the token header is duplicated (non-string value) (3.1ms)
✔ doctor cannot pull the queue with an empty token (1.2ms)
✔ dequeueNext returns patients in FIFO order (first registered, first served) (0.5ms)
✔ dequeueNext returns null when the queue is empty (0.1ms)
✔ dequeueNext on a single-item queue returns that item, not the most recently added one (0.1ms)

ℹ tests 13
ℹ pass 13
ℹ fail 0
ℹ duration_ms 830.1
```

All 13 tests pass (8 pre-existing + 5 new).

## FIRST Compliance

- **Fast** — Server binds to port `0` (ephemeral) and is closed in a `finally` block inside `withServer`; no `sleep` or fixed timeouts. Full suite runs in ~830ms.
- **Independent** — Each test calls `withServer`, which creates a brand-new `createServer()`/queue instance per test; no shared mutable state or reliance on execution order between tests.
- **Repeatable** — No wall-clock time, randomness, or external services involved; the same fixed token strings (`DOCTOR_TOKEN`, `'short'`, `'x'.repeat(...)`, `''`) are used every run, so results are deterministic.
- **Self-validating** — Every test asserts exact HTTP status codes (and, where relevant, response body fields) via `assert.equal`/`assert.strict`; no manual log inspection needed.
- **Timely** — Tests were written immediately after reading `fix-summary.md` for Bug 003, in the same pipeline run as the fix, targeting only the `isValidToken` helper and the `/queue/next` auth check it gates.

## References

- Changed code: `src/server.js:34-44` (`isValidToken` helper), `src/server.js:75-78` (auth check using `isValidToken`)
- New test file: `tests/auth.test.js`
- Fix summary: `context/bugs/003/fix-summary.md`

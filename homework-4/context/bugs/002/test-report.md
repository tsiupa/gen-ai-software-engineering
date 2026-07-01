# Test Report — Bug 002

## Tests Added

### `tests/scheduler.test.js` (new file)

Unit tests for `assignSlot()` directly, covering the boundary fixed at
`src/scheduler.js:29` (`<=` → `<`):

- `the 14th registration (slotIndex 13) gets the last valid slot before closing` —
  asserts `assignSlot(13)` returns `{ start: '15:30', end: '16:00' }`, the
  last slot that fits within posted hours.
- `the 15th registration (slotIndex 14) is rejected because it starts at closing time` —
  asserts `assignSlot(14)` returns `null` (the exact regression case: before
  the fix this returned `{ start: '16:00', end: '16:30' }`).
- `slots beyond closing time are also rejected` — asserts `assignSlot(20)`
  returns `null`, guarding against any future change that only special-cases
  the exact boundary.

### `tests/api.test.js` (added one test)

- `the 14th registration gets the last slot and the 15th is rejected with 409` —
  registers 14 patients in sequence over HTTP, asserts each gets `201` and
  the 14th gets `timeSlot: { start: '15:30', end: '16:00' }`, then registers a
  15th patient and asserts `409` with body
  `{ error: 'No appointment slots available today' }`. This exercises the
  full integration path including `src/server.js:58-64`'s `null`-to-`409`
  conversion, as recommended in `fix-summary.md`.

## Test Run

```
> doctor-appointment-queue@1.0.0 test
> node --test

✔ health check responds with ok (19.898083ms)
✔ registering a patient returns a ticket and the first time slot (9.132166ms)
✔ registration without name or reason is rejected (3.037208ms)
✔ doctor cannot pull the queue without a valid token (2.857792ms)
✔ the 14th registration gets the last slot and the 15th is rejected with 409 (18.781833ms)
✔ doctor pulls the only waiting patient (4.354041ms)
✔ doctor pulls the queue with the correct token (19.163584ms)
✔ doctor cannot pull the queue with a same-length wrong token (3.315417ms)
✔ doctor cannot pull the queue with a different-length wrong token (1.398167ms)
✔ doctor cannot pull the queue when the token header is duplicated (non-string value) (2.327583ms)
✔ doctor cannot pull the queue with an empty token (1.049125ms)
✔ dequeueNext returns patients in FIFO order (first registered, first served) (0.444666ms)
✔ dequeueNext returns null when the queue is empty (0.066166ms)
✔ dequeueNext on a single-item queue returns that item, not the most recently added one (0.08025ms)
✔ the 14th registration (slotIndex 13) gets the last valid slot before closing (0.789708ms)
✔ the 15th registration (slotIndex 14) is rejected because it starts at closing time (0.080375ms)
✔ slots beyond closing time are also rejected (0.056292ms)

ℹ tests 17
ℹ suites 0
ℹ pass 17
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 958.123916
```

All 17 tests pass (14 pre-existing + 3 new in `scheduler.test.js` + 1 new in
`api.test.js`). No regressions.

## FIRST Compliance

- **Fast** — `scheduler.test.js` tests call `assignSlot()` directly with no
  I/O (each runs in under 1ms). The new `api.test.js` test reuses the
  existing `withServer` helper, which binds to an ephemeral port (`listen(0)`)
  and closes the server in a `finally`; no fixed timeouts or `sleep` are used.
- **Independent** — Each `scheduler.test.js` test calls `assignSlot()` fresh
  with its own input; no shared state. The `api.test.js` test creates a brand
  new `createServer()`/queue instance via `withServer`, isolated from every
  other test in the file.
- **Repeatable** — All assertions are on pure arithmetic (`assignSlot`) or
  deterministic sequential HTTP calls against an in-process server; no
  reliance on wall-clock time, randomness, or external services.
- **Self-validating** — Every test uses `assert.equal`/`assert.deepEqual`
  against exact expected values (slot strings, status codes, error body);
  pass/fail requires no manual inspection.
- **Timely** — Tests were written in the same pipeline run as the fix
  described in `context/bugs/002/fix-summary.md`, targeting exactly the
  boundary condition the fix addresses.

## References

- Changed code under test: `src/scheduler.js:29` (`assignSlot()` boundary
  check), exercised indirectly via `src/server.js:58-64`.
- New test files: `tests/scheduler.test.js` (new), `tests/api.test.js`
  (one test added).

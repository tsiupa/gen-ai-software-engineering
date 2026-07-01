# Test Report — Bug 001: Doctor served the wrong (last) patient — FIFO violation

## Tests Added

**File:** `tests/queue.test.js`

| Test | Behaviour covered |
|------|--------------------|
| `dequeueNext returns patients in FIFO order (first registered, first served)` | Enqueues Alice, Bob, Charlie in order and asserts they are dequeued in the same order — the exact regression for the bug (was previously LIFO via `pop()`). |
| `dequeueNext returns null when the queue is empty` | Edge case: empty-queue behaviour is unaffected by the `pop()` → `shift()` change. |
| `dequeueNext on a single-item queue returns that item, not the most recently added one` | Guards against a regression where a two-item queue still returns the last-added item instead of the first. |

Tests call `createQueue()` directly (`src/queue.js:6`) rather than going through the HTTP layer in `src/server.js`, since the defect and fix are entirely contained in the queue module's `dequeueNext()` method.

## Test Run

```
> doctor-appointment-queue@1.0.0 test
> node --test

✔ health check responds with ok (15.368333ms)
✔ registering a patient returns a ticket and the first time slot (7.389958ms)
✔ registration without name or reason is rejected (2.623833ms)
✔ doctor cannot pull the queue without a valid token (2.228125ms)
✔ doctor pulls the only waiting patient (3.735334ms)
✔ dequeueNext returns patients in FIFO order (first registered, first served) (0.530666ms)
✔ dequeueNext returns null when the queue is empty (0.076125ms)
✔ dequeueNext on a single-item queue returns that item, not the most recently added one (0.08ms)
ℹ tests 8
ℹ suites 0
ℹ pass 8
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 579.124916
```

Result: **8/8 pass** (5 pre-existing API tests unaffected, 3 new queue tests added).

## FIRST Compliance

- **Fast** — Tests run in well under 1ms each (no I/O, no server, no network); `createQueue()` is a pure in-memory factory.
- **Independent** — Each test calls `createQueue()` fresh, creating its own isolated `items` array; no shared mutable state between tests or with `tests/api.test.js`.
- **Repeatable** — No randomness, wall-clock time, or external services involved; ticket numbers and ordering are fully deterministic.
- **Self-validating** — Each test uses `assert.equal`/`assert.notEqual` to assert exact patient names and queue state; no manual log inspection needed.
- **Timely** — Tests were written in the same pipeline run as the fix described in `context/bugs/001/fix-summary.md`, targeting only the changed `dequeueNext()` behaviour.

## References

- **Changed code:** `src/queue.js:32` (`dequeueNext()`: `items.pop()` → `items.shift()`)
- **New test file:** `tests/queue.test.js`
- **Existing suite (unaffected):** `tests/api.test.js`

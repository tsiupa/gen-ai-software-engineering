# Implementation Plan — Bug 001: Doctor served the wrong (last) patient — FIFO violation

Gate: verified-research.md = **PASS / L4**. Proceeding with the fix.

## Objective

`dequeueNext()` must return patients in the order they registered (FIFO), per
the documented intent in `src/queue.js:1-4`. Currently `enqueue()` appends to
the back (`push`) while `dequeueNext()` also removes from the back (`pop`),
producing LIFO/stack behavior — the most recently registered patient is
served first instead of the one who has waited longest.

## Changes

1. **file:** `src/queue.js`
   **location:** `dequeueNext()` method, lines 31–33.

   **Before:**
   ```js
    dequeueNext() {
      return items.pop() ?? null;
    },
   ```

   **After:**
   ```js
    dequeueNext() {
      return items.shift() ?? null;
    },
   ```

   **Rationale:** `enqueue()` already appends to the back of `items`
   (`items.push(entry)`, line 23). Removing from the front with `shift()`
   makes removal order match insertion order, restoring FIFO semantics. No
   other line in `enqueue()`, `size()`, or the call sites in `src/server.js`
   needs to change — the research confirmed `server.js` does no reordering of
   its own (lines 60–71).

## Test Command

```
npm test
```

Runs `node --test`, which executes `tests/api.test.js`. Expected outcome:
all tests pass, including any assertion that the first-registered patient is
the first one returned by `/queue/next` (FIFO order). No other test file is
affected by this change.

## Risk / Notes

- `Array.prototype.shift()` is O(n) (it re-indexes the remaining elements),
  versus O(1) for `pop()`. For the in-memory queue size expected in this
  sample app this is not a performance concern; flag only if `items` is
  expected to grow very large in production use.
- This is the only line that needs to change — `ticketCounter`/`enqueue()`
  logic (lines 16–25) already assigns and orders patients correctly per the
  verified research and does not need modification.
- Do not touch `src/server.js:44` or `src/server.js:60-71` — verified
  research confirmed these are not part of the defect.

## References

- `src/queue.js:23` — `items.push(entry);` (enqueue appends to back).
- `src/queue.js:31-33` — `dequeueNext()` body containing the defect.
- `src/queue.js:32` — `return items.pop() ?? null;` (root cause, line to change).
- `src/queue.js:1-4` — header comment documenting required FIFO behavior.
- `src/server.js:44` — `queue.enqueue({ name, reason })` call site (unaffected).
- `src/server.js:60-71` — `/queue/next` handler (unaffected, no reordering).

# Verified Research — Bug 001: Doctor served the wrong (last) patient — FIFO violation

## Verification Summary

- **Result: PASS**
- **Research Quality: L4 (Excellent)**
- **Accuracy: 7/7 (100%)**

Every claim in `codebase-research.md` was checked against the live source under
`src/`. All `file:line` references resolve to the lines claimed, every quoted
snippet matches the source verbatim (modulo leading indentation, which is
preserved correctly in context), and the stated root cause is fully consistent
with the code. No discrepancies were found.

## Verified Claims

1. **`src/queue.js:23` — `items.push(entry);` adds to the back.**
   Line 23 reads exactly `items.push(entry);`. Confirmed: `enqueue()` appends
   new entries to the end of `items`. Verified.

2. **`src/queue.js:31-33` — `dequeueNext()` removes from the back via `pop()`.**
   Lines 31–33 read `dequeueNext() {` / `return items.pop() ?? null;` / `},`.
   The snippet is verbatim and `items.pop()` does remove/return the last
   element. Verified.

3. **`src/queue.js:32` — `return items.pop() ?? null;` is the root-cause line.**
   Line 32 matches the quote exactly. This is the precise removal-end defect.
   Verified.

4. **`src/queue.js:16-25` — `enqueue()` assigns monotonically increasing ticket
   numbers and pushes to the back.**
   Lines 16–25 match the quoted block verbatim. `ticketCounter += 1` (line 17)
   confirms ticket numbers reflect registration order, supporting the claim that
   ticket number is a reliable FIFO-order indicator. Verified.

5. **`src/queue.js:1-4` — header comment documents intended FIFO behavior.**
   Lines 1–4 contain "The doctor pulls patients one at a time in the order they
   should be seen," confirming the required FIFO semantics the bug violates.
   Verified.

6. **`src/server.js:60-71` — `/queue/next` handler returns exactly what
   `dequeueNext()` produces, with no reordering.**
   Lines 60–71 match the quoted block verbatim. The handler only does token
   auth and an empty-queue check; ordering is entirely delegated to the queue.
   Verified — the bug is isolated to `queue.js`.

7. **`src/server.js:44` — `queue.enqueue({ name, reason })` call site in the
   `/appointments` handler.**
   Line 44 reads `const entry = queue.enqueue({ name, reason });`. Confirmed as
   the sole registration path feeding the queue. Verified.

## Discrepancies Found

None. No inaccurate or unverifiable claims were identified.

## Research Quality Assessment

**Level assigned: L4 (Excellent).**

Per the research-quality-measurement skill, L4 requires 100% of claims verified,
all snippets verbatim, a precise and actionable root cause, and no
discrepancies. All four conditions are met:

- **100% verified (7/7):** every reference and snippet checked out exactly.
- **Verbatim snippets:** the `dequeueNext()` block (lines 31–33), the
  `enqueue()` block (lines 16–25), and the `/queue/next` handler (lines 60–71)
  all match the source character-for-character.
- **Precise, actionable root cause:** the research correctly identifies that
  `enqueue()` pushes to the back (line 23) while `dequeueNext()` pops from the
  same end (line 32), yielding LIFO/stack behavior instead of the documented
  FIFO (line 1–4 comment). The suggested direction (remove from the front,
  e.g. `shift()`, with an O(n) performance note) is correct and directly
  implementable.
- **No discrepancies:** nothing required correction.

The conclusion is fully supported by the cited evidence, so the research is
trustworthy for the planning stage.

## Gate Decision

**L4 → PASS.** Research is trustworthy. The Bug Planner may proceed without
caution flags.

## References (every file:line checked)

- `src/queue.js:1-4` — FIFO intent header comment. ✓
- `src/queue.js:16-25` — `enqueue()` body. ✓
- `src/queue.js:23` — `items.push(entry);`. ✓
- `src/queue.js:31-33` — `dequeueNext()` body. ✓
- `src/queue.js:32` — `return items.pop() ?? null;` (root cause). ✓
- `src/server.js:44` — `queue.enqueue({ name, reason })` call site. ✓
- `src/server.js:60-71` — `/queue/next` route handler. ✓

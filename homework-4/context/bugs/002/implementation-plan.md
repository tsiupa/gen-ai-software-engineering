# Implementation Plan — Bug 002

## Objective

`assignSlot()` must stop handing out a 15th appointment slot (16:00–16:30)
that falls outside the clinic's posted hours (09:00–16:00). The clinic day
should yield exactly 14 valid slots; the 15th registration must receive a
`409 No appointment slots available today` response instead of a slot.

## Changes

1. **`src/scheduler.js`** — line 29, boundary check in `assignSlot()`.

   Before:
   ```js
   if (startMinutes <= closeMinutes) {
   ```

   After:
   ```js
   if (startMinutes < closeMinutes) {
   ```

   Rationale: a slot starting exactly at `closeMinutes` (16:00) would end at
   16:30, after closing time. The check must require the slot to *start*
   strictly before closing time, not at or before it. With `<`, the 15th
   registration (`slotIndex = 14`, `startMinutes = 960`) now fails the guard
   and `assignSlot()` returns `null`, which `src/server.js:58-64` already
   converts into the expected `409` response. No other file needs changes.

## Test Command

```
npm test
```

Expected outcome: all existing tests continue to pass, and tests covering
the appointment slot boundary (e.g. in `tests/queue.test.js` or
`tests/api.test.js`) confirm that:
- the 14th registration receives the slot `{ start: "15:30", end: "16:00" }`,
- the 15th registration receives a `409` response with body
  `No appointment slots available today` (no slot starting at or after
  16:00 is ever issued).

If no test currently asserts this boundary, the Bug Fixer should not add new
tests as part of this plan — test authoring is out of scope for this fix;
flag it to the user if regression coverage for this exact boundary is
absent.

## Risk / Notes

- This is a single-character change (`<=` → `<`) isolated to
  `src/scheduler.js:29`. No other call sites of `assignSlot()` or
  `closeMinutes`/`startMinutes` exist that depend on the inclusive boundary.
- Do not touch `src/server.js:58-64` — the verified research confirms its
  `null`-to-`409` handling is already correct and requires no change.
- Watch for any test fixtures that hardcode "15 slots per day" as an
  expected count; those would need to be updated to 14 to reflect correct
  clinic hours, but per the verified research no such fixture was flagged.

## References

- `src/scheduler.js:24-35` — `assignSlot()` function body (verified
  verbatim).
- `src/scheduler.js:29` — faulty `<=` guard (verified verbatim).
- `src/scheduler.js:7-9` — `OPEN_HOUR`, `CLOSE_HOUR`, `SLOT_MINUTES`
  constants (verified verbatim).
- `src/server.js:58-64` — caller converting `assignSlot(entry.ticketNumber - 1)`
  result of `null` into `409` (verified verbatim, no change required).
- `src/queue.js:8,17,19` — confirms `ticketNumber` is 1-based.

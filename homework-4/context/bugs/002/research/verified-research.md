# Verified Research — Bug 002

## Verification Summary

- **Result: PASS**
- **Research Quality: L4 (Excellent)**
- **Accuracy: 3/3 (100%)**

All `file:line` references resolve to the claimed locations, every quoted
snippet matches the source verbatim, and the root cause (a non-strict `<=`
boundary check in `assignSlot()`) is precise, correct, and actionable. The
research is trustworthy; the Bug Planner may proceed.

## Verified Claims

1. **`src/scheduler.js:24-35` — `assignSlot()` body, including the faulty
   `<=` guard at line 29.** The quoted block matches the source verbatim,
   including `if (startMinutes <= closeMinutes)` on line 29, the slot
   construction on lines 30–31, and `return null;` on line 34.

2. **`src/scheduler.js:7-9` — constants.** `OPEN_HOUR = 9`,
   `CLOSE_HOUR = 16`, `SLOT_MINUTES = 30` all match verbatim.

3. **`src/server.js:58-64` — caller mapping a registration to a slot index.**
   `assignSlot(entry.ticketNumber - 1)` (line 59) and the `409
   No appointment slots available today` response (lines 60–64) match
   verbatim. The claim that `ticketNumber` is 1-based is confirmed by
   `queue.js:8,17,19` (`ticketCounter` starts at 0 and is incremented before
   assignment, so the first patient is ticket 1).

### Root-cause arithmetic (independently confirmed)

- `closeMinutes = 16 * 60 = 960` (16:00). ✓
- 15th registration → `ticketNumber = 15` → `slotIndex = 14` →
  `startMinutes = 540 + 14*30 = 960`. ✓
- `960 <= 960` is `true`, so the slot `{ start: "16:00", end: "16:30" }` is
  returned instead of `null`. ✓
- Under `<=` the day yields **15** valid slots (indices 0–14); under the
  proposed `<` it yields exactly **14** (indices 0–13, last start 15:30,
  ending 16:00), matching the bug report. Verified by direct computation.

## Discrepancies Found

None. No inaccurate or unverifiable claims were identified.

## Research Quality Assessment

Assigned **L4 (Excellent)** because:

- **100% of claims verified (3/3).** Every `file:line` reference is exact and
  every snippet is verbatim — no off-by-one line numbers, no altered code.
- **Root cause is correct and precise.** The non-strict `startMinutes <=
  closeMinutes` comparison at `scheduler.js:29` is the genuine defect; the
  computed example (`slotIndex = 14`, `960 <= 960`) was reproduced
  independently and the 15-vs-14 slot count was confirmed.
- **Suggested direction is correct and minimal.** Changing `<=` to `<`
  produces exactly 14 valid slots and makes `assignSlot(14)` return `null`,
  which the existing `server.js:60-64` already converts to the expected
  `409`. The research's claim that no `server.js` change is needed is
  accurate.

No discrepancies reduced the score.

## References (checked)

- `src/scheduler.js:7-9` — `OPEN_HOUR`, `CLOSE_HOUR`, `SLOT_MINUTES`
  constants — verified verbatim.
- `src/scheduler.js:24-35` — `assignSlot()` function; faulty `<=` at line 29
  — verified verbatim.
- `src/server.js:58-64` — `/appointments` handler calling
  `assignSlot(entry.ticketNumber - 1)` and returning `409` on `null`
  — verified verbatim.
- `src/queue.js:8,17,19` — confirms `ticketNumber` is 1-based (supports the
  zero-based-index claim).

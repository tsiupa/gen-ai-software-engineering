# Codebase Research — Bug 002

## Bug Summary

Once the clinic's working day (09:00–16:00, 30-minute slots = 14 valid
slots) is full, the 15th registration of the day is still assigned a slot
starting at **16:00–16:30**, which is past closing time. The API should
instead respond with `409 No appointment slots available today` for that
registration.

## Findings

### `src/scheduler.js:24-35` — `assignSlot()` boundary check

```js
export function assignSlot(slotIndex) {
  const openMinutes = OPEN_HOUR * 60;
  const closeMinutes = CLOSE_HOUR * 60;
  const startMinutes = openMinutes + slotIndex * SLOT_MINUTES;

  if (startMinutes <= closeMinutes) {
    const endMinutes = startMinutes + SLOT_MINUTES;
    return { start: toHHMM(startMinutes), end: toHHMM(endMinutes) };
  }

  return null;
}
```

`closeMinutes` is `CLOSE_HOUR * 60` = `16 * 60` = `960` (16:00). The guard
uses `startMinutes <= closeMinutes`, which treats a slot whose **start**
time equals closing time (16:00) as valid. For the 15th registration,
`slotIndex` is `14` (see next finding), so:

```
startMinutes = 9*60 + 14*30 = 540 + 420 = 960
960 <= 960  // true → slot is returned instead of null
```

This returns `{ start: "16:00", end: "16:30" }`, a slot that runs entirely
after closing time, instead of `null`. The `<=` should be `<` so that a
slot is only valid when its **start** time is strictly before closing
time (the last valid start is 15:30, giving the 09:00–16:00 day exactly 14
slots, matching the bug report's "14 slots" expectation).

### `src/scheduler.js:7-9` — constants confirming the expected slot count

```js
export const OPEN_HOUR = 9;
export const CLOSE_HOUR = 16;
export const SLOT_MINUTES = 30;
```

09:00 to 16:00 is 7 hours = 420 minutes = 14 slots of 30 minutes. The
correct last start time is 15:30 (slot 13, zero-based), ending at 16:00.
The current `<=` check allows one extra slot starting exactly at 16:00
(slot index 14, the 15th registration), which is the off-by-one being
reported.

### `src/server.js:58-64` — caller that maps a registration to a slot index

```js
        const entry = queue.enqueue({ name, reason });
        const slot = assignSlot(entry.ticketNumber - 1);
        if (!slot) {
          return sendJson(res, 409, {
            error: 'No appointment slots available today',
          });
        }
```

`entry.ticketNumber` is 1-based (first patient is ticket 1), so
`assignSlot` is called with a zero-based `slotIndex`. For the 15th
patient, `ticketNumber = 15`, so `slotIndex = 14`. The `409` response
already exists and is correctly wired to fire whenever `assignSlot`
returns `null` — the only defect is that `assignSlot` fails to return
`null` for `slotIndex = 14`, since `960 <= 960` evaluates to `true`.

## Root Cause

In `assignSlot()` (`src/scheduler.js:29`), the closing-time boundary check
`startMinutes <= closeMinutes` uses a non-strict inequality, which
incorrectly allows a slot to start exactly at closing time (16:00). Because
the clinic should stop accepting new slot **starts** once the close hour
is reached (a slot starting at 16:00 would end at 16:30, after hours), the
check must be a strict inequality (`startMinutes < closeMinutes`) so that
the function returns `null` once `slotIndex` reaches `14` (i.e., the 15th
registration of the day).

## Suggested Direction

Change the boundary condition in `assignSlot()` so a slot is only assigned
when its start time is strictly less than `closeMinutes` (`startMinutes <
closeMinutes` instead of `startMinutes <= closeMinutes`). This yields
exactly 14 valid slots per day (09:00 through 15:30 start times) and makes
`assignSlot(14)` return `null`, which `src/server.js:60-64` already turns
into the expected `409 No appointment slots available today` response. No
changes are needed in `src/server.js`; the caller's logic and HTTP
response are already correct — only the boundary arithmetic in
`scheduler.js` is wrong.

## References

- `src/scheduler.js:7-9` — `OPEN_HOUR`, `CLOSE_HOUR`, `SLOT_MINUTES` constants.
- `src/scheduler.js:24-35` — `assignSlot()` function with the faulty `<=` boundary check at line 29.
- `src/server.js:58-64` — `/appointments` handler that calls `assignSlot(entry.ticketNumber - 1)` and returns `409` when the result is `null`.

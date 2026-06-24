# Codebase Research — Bug 001: Doctor served the wrong (last) patient — FIFO violation

## Bug Summary

`POST /queue/next` is expected to return and remove the **first** patient
registered (FIFO order). Instead, when two or more patients are waiting, it
returns the **most recently registered** patient (LIFO order). Reproduction:
register Alice (ticket 1) then Bob (ticket 2); calling `POST /queue/next`
returns Bob instead of the expected Alice.

## Findings

### `src/queue.js:23` — `enqueue()` adds to the back of the array

```js
items.push(entry);
```

`enqueue()` (lines 16–25) appends each new patient entry to the end of the
`items` array using `Array.prototype.push`. This is correct for a FIFO queue:
new arrivals go to the back.

### `src/queue.js:31-33` — `dequeueNext()` removes from the back of the array

```js
dequeueNext() {
  return items.pop() ?? null;
},
```

`dequeueNext()` uses `Array.prototype.pop()`, which removes and returns the
**last** element of `items` — i.e., the most recently pushed (most recently
registered) patient. Combined with `push()` in `enqueue()`, this makes the
queue behave as a **stack (LIFO)**, not a **queue (FIFO)**.

### `src/server.js:60-71` — route wiring confirms the queue is the sole source of ordering

```js
if (req.method === 'POST' && req.url === '/queue/next') {
  const token = req.headers['x-doctor-token'];
  if (token !== DOCTOR_TOKEN) {
    return sendJson(res, 401, { error: 'Unauthorized' });
  }

  const next = queue.dequeueNext();
  if (!next) {
    return sendJson(res, 404, { error: 'Queue is empty' });
  }
  return sendJson(res, 200, next);
}
```

The `/queue/next` handler does no reordering or filtering of its own — it
returns exactly whatever `queue.dequeueNext()` produces. There is no other
code path that selects "the next patient." This confirms the bug is isolated
entirely to the `items.pop()` call in `queue.js` and is not influenced by
`scheduler.js` or `server.js`.

### `src/queue.js:16-25` — `enqueue()` for reference (confirms push-to-back semantics)

```js
enqueue(patient) {
  ticketCounter += 1;
  const entry = {
    ticketNumber: ticketCounter,
    name: patient.name,
    reason: patient.reason,
  };
  items.push(entry);
  return entry;
},
```

Ticket numbers are assigned in monotonically increasing order
(`ticketCounter += 1`) at registration time, so ticket number reliably
reflects registration order. This is useful corroborating evidence: the
returned patient's `ticketNumber` field can be used directly in a test to
assert FIFO order (lowest ticket number must come out first).

## Root Cause

In `src/queue.js`, `enqueue()` appends to the end of the `items` array
(`items.push(entry)`), but `dequeueNext()` also removes from the end of the
array (`items.pop()`). Using `push`/`pop` together on the same end of the
array implements **stack (LIFO)** semantics, whereas the queue is documented
and required to behave as **FIFO** ("the doctor pulls patients one at a time
in the order they should be seen," per the comment at `src/queue.js:1-4`).
The mismatch between the insertion end and removal end of the array is the
exact defect.

## Suggested Direction

`dequeueNext()` should remove and return the **first** element of `items`
(the oldest entry, the one with the lowest `ticketNumber`) rather than the
last one, restoring FIFO order while keeping `enqueue()` unchanged (new
patients still go to the back of the line). The fix should be evaluated for
performance only if `items` is expected to grow very large, since removing
from the front of a plain JS array is an O(n) operation; for this in-memory,
single-clinic queue, that is unlikely to matter but is worth flagging to the
next pipeline stage.

## References

- `src/queue.js:16-25` — `enqueue()`, pushes new entries onto the back of `items`.
- `src/queue.js:23` — `items.push(entry);`
- `src/queue.js:31-33` — `dequeueNext()`, the buggy method.
- `src/queue.js:32` — `return items.pop() ?? null;` — root cause line.
- `src/queue.js:1-4` — header comment documenting intended FIFO behavior.
- `src/server.js:60-71` — `/queue/next` route handler, confirms no other code affects ordering.
- `src/server.js:44` — `queue.enqueue({ name, reason })` call site in `/appointments` handler.

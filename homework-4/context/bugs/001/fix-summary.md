# Fix Summary — Bug 001: Doctor served the wrong (last) patient — FIFO violation

## Changes Made

**File:** `src/queue.js`  
**Location:** `dequeueNext()` method, lines 31–33.

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

**Rationale:** `enqueue()` appends to the back of the `items` array with `push()` (line 23). By changing `dequeueNext()` from `pop()` (removes from back) to `shift()` (removes from front), the removal order now matches insertion order, restoring FIFO semantics. Patients are now served in the order they registered, as per the documented intent in the file header (lines 1–4).

**Test Result:** ✅ PASS — All 5 tests passed after applying this change.

## Overall Status

**Status:** ✅ **SUCCESS**

- Change applied exactly as specified in the implementation plan.
- All tests pass (5/5).
- No other files required modification per the verified research.
- FIFO contract is now satisfied: first-registered patient is first dequeued.

## Manual Verification

To verify the fix manually:

1. **Start the server:**
   ```bash
   npm start
   ```

2. **Register three patients in order:**
   ```bash
   curl -X POST http://localhost:3000/register \
     -H "Content-Type: application/json" \
     -d '{"name": "Alice", "reason": "Checkup"}'
   
   curl -X POST http://localhost:3000/register \
     -H "Content-Type: application/json" \
     -d '{"name": "Bob", "reason": "Flu"}'
   
   curl -X POST http://localhost:3000/register \
     -H "Content-Type: application/json" \
     -d '{"name": "Charlie", "reason": "Broken arm"}'
   ```

3. **Pull patients from the queue (with the doctor token from server output):**
   ```bash
   curl http://localhost:3000/queue/next?token=<DOCTOR_TOKEN>
   ```
   
   **Expected:** First response returns **Alice** (ticket 1), second returns **Bob** (ticket 2), third returns **Charlie** (ticket 3).
   
   **Before fix:** Would have returned Charlie, Bob, Alice (reverse order/LIFO).

## References

- **Plan:** `context/bugs/001/implementation-plan.md`
- **File changed:** `src/queue.js:32`
  - `src/queue.js:23` — `items.push(entry)` (enqueue appends to back)
  - `src/queue.js:31–33` — `dequeueNext()` method (defect location)
  - `src/queue.js:1–4` — Header comment documenting FIFO requirement
- **Files verified unaffected:** `src/server.js` (call sites lines 44, 60–71)
- **Test output:** `npm test` → 5 pass, 0 fail, 0 cancelled

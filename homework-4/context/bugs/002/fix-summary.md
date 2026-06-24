# Fix Summary — Bug 002

## Changes Made

### File: `src/scheduler.js` — Line 29

**Location:** `assignSlot()` function boundary check

**Before:**
```js
if (startMinutes <= closeMinutes) {
```

**After:**
```js
if (startMinutes < closeMinutes) {
```

**Change:** Single-character replacement: `<=` → `<`

**Rationale:** The clinic closes at 16:00 (960 minutes). A slot starting at exactly 16:00 would end at 16:30, exceeding the posted hours (09:00–16:00). The strict `<` comparison ensures only slots that *start before* the closing time are assigned. With this change:
- The 14th registration (slotIndex = 13) gets slot 15:30–16:00 ✓
- The 15th registration (slotIndex = 14) now fails the guard and returns `null` ✓
- `src/server.js:58-64` converts `null` to a `409 No appointment slots available today` response ✓

**Test Result:** All 13 tests pass after applying this change.

## Overall Status

**SUCCESS** — The change has been applied exactly as specified in the implementation plan. All existing tests pass (13/13). The fix correctly implements the boundary check to prevent slots starting at or after closing time.

## Manual Verification

To verify the fix works as intended:

1. **Start the server:**
   ```bash
   npm start
   ```

2. **Register 14 patients** and confirm each receives a valid slot:
   ```bash
   for i in {1..14}; do
     curl -X POST http://localhost:3000/appointments \
       -H "Content-Type: application/json" \
       -d "{\"name\": \"Patient $i\", \"reason\": \"Checkup\"}"
   done
   ```
   - Patient 1 should receive `{ start: "09:00", end: "09:30" }`
   - Patient 14 should receive `{ start: "15:30", end: "16:00" }` (last valid slot)

3. **Register the 15th patient** and confirm rejection:
   ```bash
   curl -X POST http://localhost:3000/appointments \
     -H "Content-Type: application/json" \
     -d "{\"name\": \"Patient 15\", \"reason\": \"Checkup\"}"
   ```
   - Should return HTTP 409 with body `No appointment slots available today`

4. **Run the test suite** to confirm no regressions:
   ```bash
   npm test
   ```

## Notes on Test Coverage

The plan notes that no existing tests explicitly assert the 14th/15th slot boundary condition. The current test suite (13 tests) validates core functionality but does not include a specific regression test for this boundary. Per the implementation plan, test authoring is out of scope for this fix — the plan directs the Bug Fixer not to add new tests if the boundary is absent, and to flag it if needed.

**Recommendation:** Consider adding an integration test that:
- Registers exactly 15 patients in sequence
- Confirms patient 14 receives `{ start: "15:30", end: "16:00" }`
- Confirms patient 15 receives a 409 response

This would prevent regression if the `<` check is accidentally reverted to `<=` in the future.

## References

- **Implementation Plan:** `context/bugs/002/implementation-plan.md`
- **File Changed:** `src/scheduler.js:29`
- **Constants Involved:** 
  - `OPEN_HOUR = 9` (line 7)
  - `CLOSE_HOUR = 16` (line 8)
  - `SLOT_MINUTES = 30` (line 9)
- **Caller Converting null to 409:** `src/server.js:58-64`
- **Test Suite:** All tests in `tests/` directory pass

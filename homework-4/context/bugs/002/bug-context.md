# Bug Context — 002: Appointments handed out past closing time — boundary error

**Bug ID:** 002
**Component:** `src/scheduler.js`
**Reported by:** QA / seeded for the 4-agent pipeline
**Severity:** Medium (invalid slot outside working hours)
**Status:** OPEN (before pipeline run)

## Application summary

A REST API that registers patients in a queue for a doctor's appointment.

- `POST /appointments` — a patient sends `{ name, reason }` and receives a
  ticket number and a 30-minute time slot. The clinic is open 09:00–16:00.
- `POST /queue/next` — the doctor (authenticated) requests the first patient in
  the queue and removes them.

## Reported issue

- **Symptom:** Once the working day is full (09:00–16:00, 30-minute slots = 14
  slots), the next registration is still given a slot at **16:00–16:30**, which
  is past closing. The API should instead report that no slots are available.
- **Reproduction:** Register 15 patients; the 15th receives `16:00–16:30`
  instead of a `409 No appointment slots available today`.
- **Expected:** Only 14 slots exist (09:00 through 15:30 start times); the 15th
  registration is rejected.
- **Suspected area:** `src/scheduler.js`, `assignSlot()` closing-time check.

## Acceptance criteria (after pipeline run)

- The 15th registration of the day is rejected (no post-closing slots).
- `npm test` passes, including a new test covering the closing-time boundary.

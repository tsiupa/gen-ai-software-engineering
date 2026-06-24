# Bug Context — 001: Doctor served the wrong (last) patient — FIFO violation

**Bug ID:** 001
**Component:** `src/queue.js`
**Reported by:** QA / seeded for the 4-agent pipeline
**Severity:** High (patients are seen out of order)
**Status:** OPEN (before pipeline run)

## Application summary

A REST API that registers patients in a queue for a doctor's appointment.

- `POST /appointments` — a patient sends `{ name, reason }` and receives a
  ticket number and a 30-minute time slot. The clinic is open 09:00–16:00.
- `POST /queue/next` — the doctor (authenticated) requests the first patient in
  the queue and removes them.

## Reported issue

- **Symptom:** When two or more patients are waiting, `POST /queue/next` returns
  the **most recently registered** patient instead of the **first** one.
- **Reproduction:**
  1. `POST /appointments {"name":"Alice","reason":"Cough"}` → ticket 1
  2. `POST /appointments {"name":"Bob","reason":"Fever"}` → ticket 2
  3. `POST /queue/next` (with doctor token) → returns **Bob**, expected **Alice**
- **Expected:** Patients are served in registration order (FIFO).
- **Suspected area:** `src/queue.js`, `dequeueNext()`.

## Acceptance criteria (after pipeline run)

- `POST /queue/next` returns patients in FIFO order.
- `npm test` passes, including a new test covering multi-patient FIFO ordering.

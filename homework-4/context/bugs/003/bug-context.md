# Bug Context — 003: Insecure doctor authentication (security)

**Bug ID:** 003
**Component:** `src/server.js`
**Reported by:** QA / seeded for the 4-agent pipeline
**Severity:** High (broken authentication / secret management)
**Status:** OPEN (before pipeline run)

## Application summary

A REST API that registers patients in a queue for a doctor's appointment.

- `POST /appointments` — a patient sends `{ name, reason }` and receives a
  ticket number and a 30-minute time slot. The clinic is open 09:00–16:00.
- `POST /queue/next` — the doctor (authenticated) requests the first patient in
  the queue and removes them.

## Reported issue

- **Symptom:** The doctor token is **hardcoded in source** (`DOCTOR_TOKEN` in
  `src/server.js`) and committed to the repository, and the token check uses a
  plain `===` string comparison (not constant-time).
- **Risks:** Anyone with repository access learns the credential; the comparison
  is vulnerable to timing analysis; there is no way to rotate the secret without
  a code change.
- **Expected:** Secret sourced from configuration/environment, never committed;
  comparison performed with a constant-time function (e.g.
  `crypto.timingSafeEqual`); request rejected when the header is absent.
- **Suspected area:** `src/server.js`, `DOCTOR_TOKEN` and the `/queue/next`
  authorization branch.

## Acceptance criteria (after pipeline run)

- The doctor secret is no longer hardcoded and is compared in constant time.
- A request without the token header is rejected.
- `npm test` passes, including a new test covering missing/invalid token paths.

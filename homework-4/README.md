# Homework 4 — 4-Agent Pipeline · Sample Mini Application

**Author / Student:** Oleksandr Tsiupa (`otsiupa48507`, tsiupa.bs@gmail.com)

This homework builds a 4-agent pipeline (Bug Research Verifier → Bug Fixer →
Security Verifier → Unit Test Generator) that operates on a small, self-contained
application. This document covers the **Sample Mini Application** (Task 5) — the
"before" state the pipeline runs against.

## The application — Doctor Appointment Queue API

A minimal REST API that registers patients in a queue for a doctor's
appointment, derived from [`INITIATIVE.md`](./INITIATIVE.md).

| Endpoint | Description |
|----------|-------------|
| `POST /appointments` | Patient sends `{ "name", "reason" }`; receives a `ticketNumber` and a 30-minute `timeSlot`. Clinic hours 09:00–16:00. |
| `POST /queue/next` | Doctor (authenticated via `x-doctor-token`) gets the first waiting patient and removes them from the queue. |
| `GET /` | Health check (`{ status, queueSize }`). |

**Stack:** Node.js (≥20), zero runtime dependencies — built-in `node:http` for
the server and `node:test` for the suite.

```
homework-4/
├── src/
│   ├── server.js      # HTTP server, routing, doctor auth
│   ├── queue.js       # in-memory patient queue
│   └── scheduler.js   # 30-minute slot allocation (09:00–16:00)
├── tests/
│   └── api.test.js    # before-state happy-path tests
├── context/bugs/
│   ├── 001/bug-context.md  # FIFO violation
│   ├── 002/bug-context.md  # closing-time boundary
│   └── 003/bug-context.md  # insecure doctor auth (security)
└── docs/screenshots/
```

## Seeded defects (before pipeline run)

The app intentionally contains **2 bugs** and **1 security issue** for the
pipeline to find and fix — each documented as a separate report under
`context/bugs/`:

1. **FIFO violation** (`src/queue.js`) — the doctor is served the *last*
   registered patient instead of the first (`pop()` instead of `shift()`).
   See [`context/bugs/001/bug-context.md`](./context/bugs/001/bug-context.md).
2. **Boundary error** (`src/scheduler.js`) — a slot at `16:00–16:30` is handed
   out past closing time (`<=` instead of `<`).
   See [`context/bugs/002/bug-context.md`](./context/bugs/002/bug-context.md).
3. **Insecure authentication** (`src/server.js`) — hardcoded doctor token in
   source, compared with a timing-unsafe `===`.
   See [`context/bugs/003/bug-context.md`](./context/bugs/003/bug-context.md).

The before-state tests cover happy paths only, so `npm test` is **green** while
the bugs remain latent on edge cases. After the pipeline runs, the fixes are
applied and the Unit Test Generator adds tests that cover all three issues.

## Run & test

See [`HOWTORUN.md`](./HOWTORUN.md). In short:

```bash
cd homework-4
npm start     # starts the API on http://localhost:3000
npm test      # runs the test suite
```

## Status

- [x] **Task 5** — Sample mini application (this app, with seeded defects)
- [ ] Tasks 1–4 — the four agents and skills (`agents/`, `skills/`)
- [ ] Agent outputs (`verified-research.md`, `fix-summary.md`,
      `security-report.md`, `test-report.md`) and single-command pipeline

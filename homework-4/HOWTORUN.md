# How to Run — Doctor Appointment Queue API

## Prerequisites

- Node.js **≥ 20** (developed on Node 25). No other dependencies — the app uses
  only Node built-ins.

```bash
node --version
```

## Install

There are no third-party packages, so there is nothing to install. Optionally:

```bash
cd homework-4
npm install   # no-op; no dependencies declared
```

## Run the API

```bash
cd homework-4
npm start
# -> Doctor Appointment Queue API listening on http://localhost:3000
```

Use a different port with `PORT=8080 npm start`.

## Try it

Register a patient:

```bash
curl -s -X POST http://localhost:3000/appointments \
  -H 'Content-Type: application/json' \
  -d '{"name":"Alice","reason":"Persistent cough"}'
# -> {"ticketNumber":1,"timeSlot":{"start":"09:00","end":"09:30"}}
```

Doctor pulls the next patient (requires the token header):

```bash
curl -s -X POST http://localhost:3000/queue/next \
  -H 'x-doctor-token: doctor-secret-2024'
# -> {"ticketNumber":1,"name":"Alice","reason":"Persistent cough"}
```

Health check:

```bash
curl -s http://localhost:3000/
# -> {"status":"ok","queueSize":0}
```

## Run the tests

```bash
cd homework-4
npm test
```

The before-state suite (`tests/api.test.js`) covers the happy paths and should
report **5 passing** tests.

## Run the agent pipeline

The pipeline finds, verifies, fixes, security-reviews, and writes regression
tests for the seeded bugs — all from one command.

### Prerequisites

- The **Claude Code CLI** (`claude`) installed and authenticated:
  ```bash
  claude --version   # must succeed
  ```
- The pipeline runs agents headlessly with `--dangerously-skip-permissions` so
  no prompts interrupt it. It **edits `src/` and `tests/`** (the Bug Fixer
  applies the fixes and the Unit Test Generator adds tests), so run it on a
  clean working tree / branch you can review or revert.

### Run it

```bash
cd homework-4
npm run pipeline          # process all bugs: 001, 002, 003
# or
npm run pipeline 002      # process a single bug
# or
./run-pipeline.sh
```

### What happens (per bug, in order)

1. **Bug Researcher** → `context/bugs/<ID>/research/codebase-research.md`
2. **Bug Research Verifier** (uses `research-quality-measurement` skill) → `research/verified-research.md`
3. **Bug Planner** → `context/bugs/<ID>/implementation-plan.md`
4. **Bug Fixer** → applies the fix to `src/`, runs tests → `fix-summary.md`
5. **Security Verifier** (on changed code) → `security-report.md`
6. **Unit Test Generator** (uses `unit-tests-FIRST` skill, on changed code) →
   adds tests in `tests/` → `test-report.md`

After all bugs are processed the runner executes `npm test` once more; the full
suite (original + generated tests) should be green.

### Reverting after a demo run

```bash
git checkout -- src tests          # discard applied fixes & generated tests
git clean -fd context/bugs         # remove generated artifacts (optional)
```
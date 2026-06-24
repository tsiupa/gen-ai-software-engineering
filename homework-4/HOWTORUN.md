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
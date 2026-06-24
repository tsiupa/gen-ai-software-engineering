import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from '../src/server.js';

const DOCTOR_TOKEN = 'doctor-secret-2024';

// Start the server on an ephemeral port and return helpers bound to it.
async function withServer(run) {
  const server = createServer();
  await new Promise((resolve) => server.listen(0, resolve));
  const { port } = server.address();
  const base = `http://127.0.0.1:${port}`;

  try {
    await run(base);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

function register(base, body) {
  return fetch(`${base}/appointments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

test('health check responds with ok', async () => {
  await withServer(async (base) => {
    const res = await fetch(`${base}/`);
    assert.equal(res.status, 200);
    const json = await res.json();
    assert.equal(json.status, 'ok');
  });
});

test('registering a patient returns a ticket and the first time slot', async () => {
  await withServer(async (base) => {
    const res = await register(base, { name: 'Alice', reason: 'Cough' });
    assert.equal(res.status, 201);
    const json = await res.json();
    assert.equal(json.ticketNumber, 1);
    assert.deepEqual(json.timeSlot, { start: '09:00', end: '09:30' });
  });
});

test('registration without name or reason is rejected', async () => {
  await withServer(async (base) => {
    const res = await register(base, { name: 'Bob' });
    assert.equal(res.status, 400);
  });
});

test('doctor cannot pull the queue without a valid token', async () => {
  await withServer(async (base) => {
    const res = await fetch(`${base}/queue/next`, { method: 'POST' });
    assert.equal(res.status, 401);
  });
});

test('the 14th registration gets the last slot and the 15th is rejected with 409', async () => {
  await withServer(async (base) => {
    let lastJson;
    for (let i = 1; i <= 14; i += 1) {
      const res = await register(base, { name: `Patient ${i}`, reason: 'Checkup' });
      assert.equal(res.status, 201);
      lastJson = await res.json();
    }
    assert.deepEqual(lastJson.timeSlot, { start: '15:30', end: '16:00' });

    const res15 = await register(base, { name: 'Patient 15', reason: 'Checkup' });
    assert.equal(res15.status, 409);
    const json15 = await res15.json();
    assert.equal(json15.error, 'No appointment slots available today');
  });
});

test('doctor pulls the only waiting patient', async () => {
  await withServer(async (base) => {
    await register(base, { name: 'Alice', reason: 'Cough' });
    const res = await fetch(`${base}/queue/next`, {
      method: 'POST',
      headers: { 'x-doctor-token': DOCTOR_TOKEN },
    });
    assert.equal(res.status, 200);
    const json = await res.json();
    assert.equal(json.name, 'Alice');
  });
});

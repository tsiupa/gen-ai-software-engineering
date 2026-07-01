import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createServer } from '../src/server.js';

const DOCTOR_TOKEN = 'doctor-secret-2024';

// Start the server on an ephemeral port and return helpers bound to it.
async function withServer(run) {
  const server = createServer();
  await new Promise((resolve) => server.listen(0, resolve));
  const { port } = server.address();
  const base = `http://127.0.0.1:${port}`;

  try {
    await run(base, port);
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

// Sends a request with a raw header value (supports arrays for duplicate headers),
// bypassing fetch's Headers normalization so we can exercise the typeof guard.
function rawPostQueueNext(port, headerValue) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        host: '127.0.0.1',
        port,
        path: '/queue/next',
        method: 'POST',
        headers: headerValue === undefined ? {} : { 'x-doctor-token': headerValue },
      },
      (res) => {
        let raw = '';
        res.on('data', (chunk) => {
          raw += chunk;
        });
        res.on('end', () => resolve({ status: res.statusCode, body: raw ? JSON.parse(raw) : null }));
      }
    );
    req.on('error', reject);
    req.end();
  });
}

test('doctor pulls the queue with the correct token', async () => {
  await withServer(async (base) => {
    await register(base, { name: 'Alice', reason: 'Cough' });
    const res = await fetch(`${base}/queue/next`, {
      method: 'POST',
      headers: { 'x-doctor-token': DOCTOR_TOKEN },
    });
    assert.equal(res.status, 200);
  });
});

test('doctor cannot pull the queue with a same-length wrong token', async () => {
  await withServer(async (base) => {
    const wrongSameLength = 'x'.repeat(DOCTOR_TOKEN.length);
    const res = await fetch(`${base}/queue/next`, {
      method: 'POST',
      headers: { 'x-doctor-token': wrongSameLength },
    });
    assert.equal(res.status, 401);
  });
});

test('doctor cannot pull the queue with a different-length wrong token', async () => {
  await withServer(async (base) => {
    const res = await fetch(`${base}/queue/next`, {
      method: 'POST',
      headers: { 'x-doctor-token': 'short' },
    });
    assert.equal(res.status, 401);
  });
});

test('doctor cannot pull the queue when the token header is duplicated (non-string value)', async () => {
  await withServer(async (base, port) => {
    const res = await rawPostQueueNext(port, [DOCTOR_TOKEN, DOCTOR_TOKEN]);
    assert.equal(res.status, 401);
    assert.equal(res.body.error, 'Unauthorized');
  });
});

test('doctor cannot pull the queue with an empty token', async () => {
  await withServer(async (base, port) => {
    const res = await rawPostQueueNext(port, '');
    assert.equal(res.status, 401);
  });
});

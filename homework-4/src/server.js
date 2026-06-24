import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { timingSafeEqual } from 'node:crypto';
import { createQueue } from './queue.js';
import { assignSlot } from './scheduler.js';

// Token the doctor client must present to pull patients from the queue.
// Falls back to the legacy literal only when DOCTOR_TOKEN is unset (local dev/tests).
const DOCTOR_TOKEN = process.env.DOCTOR_TOKEN ?? 'doctor-secret-2024';

function sendJson(res, statusCode, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(statusCode, { 'Content-Type': 'application/json' });
  res.end(body);
}

function readJson(req, res, callback) {
  let raw = '';
  req.on('data', (chunk) => {
    raw += chunk;
  });
  req.on('end', () => {
    if (raw.length === 0) {
      return callback({});
    }
    try {
      return callback(JSON.parse(raw));
    } catch {
      return sendJson(res, 400, { error: 'Invalid JSON body' });
    }
  });
}

function isValidToken(provided) {
  if (typeof provided !== 'string') {
    return false;
  }
  const expected = Buffer.from(DOCTOR_TOKEN);
  const candidate = Buffer.from(provided);
  if (candidate.length !== expected.length) {
    return false;
  }
  return timingSafeEqual(candidate, expected);
}

export function createServer() {
  const queue = createQueue();

  return http.createServer((req, res) => {
    // Patient registers for an appointment.
    if (req.method === 'POST' && req.url === '/appointments') {
      return readJson(req, res, (body) => {
        const { name, reason } = body ?? {};
        if (!name || !reason) {
          return sendJson(res, 400, { error: 'name and reason are required' });
        }

        const entry = queue.enqueue({ name, reason });
        const slot = assignSlot(entry.ticketNumber - 1);
        if (!slot) {
          return sendJson(res, 409, {
            error: 'No appointment slots available today',
          });
        }

        return sendJson(res, 201, {
          ticketNumber: entry.ticketNumber,
          timeSlot: slot,
        });
      });
    }

    // Doctor pulls the next patient and removes them from the queue.
    if (req.method === 'POST' && req.url === '/queue/next') {
      const token = req.headers['x-doctor-token'];
      if (!isValidToken(token)) {
        return sendJson(res, 401, { error: 'Unauthorized' });
      }

      const next = queue.dequeueNext();
      if (!next) {
        return sendJson(res, 404, { error: 'Queue is empty' });
      }
      return sendJson(res, 200, next);
    }

    // Health check.
    if (req.method === 'GET' && req.url === '/') {
      return sendJson(res, 200, { status: 'ok', queueSize: queue.size() });
    }

    return sendJson(res, 404, { error: 'Not found' });
  });
}

// Start the server when run directly (`npm start`).
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const PORT = process.env.PORT ?? 3000;
  createServer().listen(PORT, () => {
    // eslint-disable-next-line no-console
    console.log(`Doctor Appointment Queue API listening on http://localhost:${PORT}`);
  });
}

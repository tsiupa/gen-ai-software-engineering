import test from 'node:test';
import assert from 'node:assert/strict';
import { createQueue } from '../src/queue.js';

test('dequeueNext returns patients in FIFO order (first registered, first served)', () => {
  const queue = createQueue();
  queue.enqueue({ name: 'Alice', reason: 'Checkup' });
  queue.enqueue({ name: 'Bob', reason: 'Flu' });
  queue.enqueue({ name: 'Charlie', reason: 'Broken arm' });

  assert.equal(queue.dequeueNext().name, 'Alice');
  assert.equal(queue.dequeueNext().name, 'Bob');
  assert.equal(queue.dequeueNext().name, 'Charlie');
});

test('dequeueNext returns null when the queue is empty', () => {
  const queue = createQueue();
  assert.equal(queue.dequeueNext(), null);
});

test('dequeueNext on a single-item queue returns that item, not the most recently added one', () => {
  const queue = createQueue();
  queue.enqueue({ name: 'Alice', reason: 'Checkup' });
  queue.enqueue({ name: 'Bob', reason: 'Flu' });

  const next = queue.dequeueNext();
  assert.equal(next.name, 'Alice');
  assert.notEqual(next.name, 'Bob');
});

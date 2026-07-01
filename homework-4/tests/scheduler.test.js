import test from 'node:test';
import assert from 'node:assert/strict';
import { assignSlot } from '../src/scheduler.js';

test('the 14th registration (slotIndex 13) gets the last valid slot before closing', () => {
  const slot = assignSlot(13);
  assert.deepEqual(slot, { start: '15:30', end: '16:00' });
});

test('the 15th registration (slotIndex 14) is rejected because it starts at closing time', () => {
  const slot = assignSlot(14);
  assert.equal(slot, null);
});

test('slots beyond closing time are also rejected', () => {
  const slot = assignSlot(20);
  assert.equal(slot, null);
});

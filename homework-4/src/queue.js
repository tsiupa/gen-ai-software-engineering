// In-memory patient queue.
//
// Each registration receives a monotonically increasing ticket number. The
// doctor pulls patients one at a time in the order they should be seen.

export function createQueue() {
  const items = [];
  let ticketCounter = 0;

  return {
    /**
     * Add a patient to the back of the queue.
     * @param {{ name: string, reason: string }} patient
     * @returns {{ ticketNumber: number, name: string, reason: string }}
     */
    enqueue(patient) {
      ticketCounter += 1;
      const entry = {
        ticketNumber: ticketCounter,
        name: patient.name,
        reason: patient.reason,
      };
      items.push(entry);
      return entry;
    },

    /**
     * Remove and return the patient the doctor should see next.
     * @returns {object | null}
     */
    dequeueNext() {
      return items.pop() ?? null;
    },

    size() {
      return items.length;
    },
  };
}

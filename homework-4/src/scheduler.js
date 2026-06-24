// Appointment slot scheduling.
//
// The clinic is open from 09:00 to 16:00 and every appointment is 30 minutes
// long. A patient's slot is derived from their position in the day's
// registrations (the first registration takes the 09:00 slot, and so on).

export const OPEN_HOUR = 9;
export const CLOSE_HOUR = 16;
export const SLOT_MINUTES = 30;

function toHHMM(totalMinutes) {
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
}

/**
 * Assign the time slot for the patient at the given zero-based index.
 * Returns null when there are no more slots left in the working day.
 *
 * @param {number} slotIndex zero-based position of the registration
 * @returns {{ start: string, end: string } | null}
 */
export function assignSlot(slotIndex) {
  const openMinutes = OPEN_HOUR * 60;
  const closeMinutes = CLOSE_HOUR * 60;
  const startMinutes = openMinutes + slotIndex * SLOT_MINUTES;

  if (startMinutes < closeMinutes) {
    const endMinutes = startMinutes + SLOT_MINUTES;
    return { start: toHHMM(startMinutes), end: toHHMM(endMinutes) };
  }

  return null;
}

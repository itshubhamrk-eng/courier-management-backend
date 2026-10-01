import { ShipmentStatus } from '@core/models/shipment.model';

/** Every status except `CANCELLED` — cancelled orders live only in the Cancelled Orders Report. */
export const NON_CANCELLED_STATUSES: ShipmentStatus[] = [
  'BOOKED', 'READY_FOR_MANIFEST', 'MANIFEST_CREATED', 'DISPATCHED', 'IN_SCAN',
  'OUT_FOR_DELIVERY', 'DELIVERED', 'RETURNED'
];

/** The status filter every report sends: the user's own pick minus `CANCELLED`, or all non-cancelled when none. */
export function excludingCancelled(selected?: ShipmentStatus[] | null): ShipmentStatus[] {
  const kept = (selected ?? []).filter((s) => s !== 'CANCELLED');
  return kept.length ? kept : [...NON_CANCELLED_STATUSES];
}

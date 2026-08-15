/**
 * Address Distance models — mirror backend `com.courier.modules.distance` one-to-one.
 * Branch-only on the frontend for now: `CUSTOMER` addresses aren't geocoded on save yet,
 * so a customer pair can't resolve through the UI (see backend/CHANGELOG 0.19.0).
 */

export type AddressType = 'BRANCH' | 'CUSTOMER';

/** Mirrors backend `AddressDistanceResponse`. */
export interface AddressDistanceResponse {
  id: string;
  companyId: string;
  addressType: AddressType;
  fromId: string;
  toId: string;
  distanceKm: number;
  distanceMeter: number;
  requiredTimeMinutes: number;
  createdAt: string;
}

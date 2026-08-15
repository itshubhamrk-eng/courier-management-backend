/**
 * Freight Factor models — mirror the backend `com.courier.modules.freight` module
 * one-to-one (see MEMORY/modules/freight-factor.md). A cell prices one distance range x
 * one weight range with a multiplier; freight = factor * weight. Deliberately independent
 * of Rate Master — no route/service-type/package-type/payment-mode fields at all.
 */

export type FreightFactorStatus = 'ACTIVE' | 'INACTIVE';

export const FREIGHT_FACTOR_STATUSES: FreightFactorStatus[] = ['ACTIVE', 'INACTIVE'];

/** Full representation — mirrors backend `FreightFactorResponse`. Used for both list and
 *  detail; the entity has too few fields to warrant a separate summary projection. */
export interface FreightFactor {
  id: string;
  companyId: string;
  fromKm: number;
  toKm: number;
  fromWeight: number;
  toWeight: number;
  factor: number;
  status: FreightFactorStatus;
  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
}

/** Fields shared by create and update. */
export interface FreightFactorFields {
  fromKm: number;
  toKm: number;
  fromWeight: number;
  toWeight: number;
  factor: number;
}

/** Body of POST /freight-factors — mirrors backend `CreateFreightFactorRequest`. */
export type CreateFreightFactorRequest = FreightFactorFields;

/** Body of PUT /freight-factors/{id} — mirrors backend `UpdateFreightFactorRequest`. */
export interface UpdateFreightFactorRequest extends FreightFactorFields {
  version: number;
}

/** Body of POST /freight-factors/calculate — mirrors backend `FreightCalculationRequest`.
 *  Distance is resolved server-side from the branch pair, not passed by the caller. */
export interface FreightCalculationRequest {
  fromBranchId: string;
  toBranchId: string;
  weight: number;
}

/** Response of POST /freight-factors/calculate — mirrors backend
 *  `FreightCalculationResponse`. */
export interface FreightCalculationResponse {
  matchedFactorId: string;
  distanceKm: number;
  weight: number;
  factor: number;
  freight: number;
}

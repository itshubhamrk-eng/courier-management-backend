/**
 * Rate Master models — mirror the backend `com.courier.modules.rate` module one-to-one
 * (see MEMORY/modules/rate-master.md). A rate is company-specific, belongs to one Route,
 * and prices one weight slab for one Route + Service Type + Package Type + Payment Mode
 * combination. No mock shapes: every field is returned by, or accepted by, an endpoint.
 */

export type RateStatus = 'ACTIVE' | 'INACTIVE';
export type RateWeightUnit = 'KG' | 'GRAM' | 'POUND';

export const RATE_STATUSES: RateStatus[] = ['ACTIVE', 'INACTIVE'];
export const RATE_WEIGHT_UNITS: RateWeightUnit[] = ['KG', 'GRAM', 'POUND'];

/** List projection — mirrors backend `RateSummaryResponse` (GET /rates). */
export interface Rate {
  id: string;
  rateCode: string;
  rateName: string;
  routeId: string;
  serviceTypeId: string;
  packageTypeId: string;
  paymentModeId: string;
  minimumWeight: number;
  maximumWeight: number;
  weightUnit: RateWeightUnit;
  baseRate: number;
  status: RateStatus;
  effectiveFrom: string;
  effectiveTo?: string | null;
  version: number;
}

/** Full representation — mirrors backend `RateResponse` (GET /rates/{id}). */
export interface RateResponse {
  id: string;
  companyId: string;
  rateCode: string;
  rateName: string;
  routeId: string;
  serviceTypeId: string;
  packageTypeId: string;
  paymentModeId: string;
  minimumWeight: number;
  maximumWeight: number;
  weightUnit: RateWeightUnit;
  baseRate: number;
  additionalWeight: number;
  additionalWeightRate: number;
  minimumCharge: number;
  fuelSurcharge: number;
  handlingCharge: number;
  odaCharge: number;
  insuranceCharge: number;
  gstPercentage: number;
  effectiveFrom: string;
  effectiveTo?: string | null;
  status: RateStatus;
  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
}

/** Fields shared by create and update — everything but the immutable rateCode/version. */
export interface RateFields {
  rateName: string;
  routeId: string;
  serviceTypeId: string;
  packageTypeId: string;
  paymentModeId: string;
  minimumWeight: number;
  maximumWeight: number;
  weightUnit: RateWeightUnit;
  baseRate: number;
  additionalWeight: number;
  additionalWeightRate: number;
  minimumCharge: number;
  fuelSurcharge: number;
  handlingCharge: number;
  odaCharge: number;
  insuranceCharge: number;
  gstPercentage: number;
  effectiveFrom: string;
  effectiveTo?: string | null;
}

/** Body of POST /rates — mirrors backend `CreateRateRequest`. A new rate always starts ACTIVE. */
export interface CreateRateRequest extends RateFields {
  rateCode: string;
}

/** Body of PUT /rates/{id} — mirrors backend `UpdateRateRequest`. `rateCode` is immutable. */
export interface UpdateRateRequest extends RateFields {
  version: number;
}

/** Advanced-filter criteria for GET /rates. All optional; merged into the page query. */
export interface RateSearchRequest {
  routeId?: string[];
  serviceTypeId?: string[];
  packageTypeId?: string[];
  paymentModeId?: string[];
  status?: RateStatus[];
  search?: string;
}

/** Body of POST /rates/calculate — mirrors backend `RateCalculationRequest`. */
export interface RateCalculationRequest {
  bookingBranchId: string;
  deliveryBranchId: string;
  serviceTypeId: string;
  packageTypeId: string;
  paymentModeId: string;
  actualWeight: number;
  bookingDate?: string | null;
}

/** Response of POST /rates/calculate — mirrors backend `RateCalculationResponse`. */
export interface RateCalculationResponse {
  matchedRateId: string;
  matchedRateCode: string;
  matchedRateName: string;
  chargeableWeight: number;
  weightUnit: RateWeightUnit;
  freight: number;
  fuelSurcharge: number;
  handlingCharge: number;
  odaCharge: number;
  insuranceCharge: number;
  gstPercentage: number;
  gstAmount: number;
  totalAmount: number;
}

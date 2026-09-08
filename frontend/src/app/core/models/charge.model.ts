/**
 * Charge & Charge Settings models — mirror the backend `com.courier.modules.charge`
 * module one-to-one. A Charge is a named configuration scoped to one Service Type; a
 * Charge Setting is one FACTOR (flat/percentage) or SLAB (KG/KM/BOTH banded) row under
 * it. Configuration only — nothing here is consumed by Shipment Booking, freight,
 * commission or wallet calculation yet; a future calculation engine will read these rows.
 */

export type ChargeStatus = 'ACTIVE' | 'INACTIVE';
export type ChargeType = 'FACTOR' | 'SLAB';
export type ChargeSlabType = 'BOTH' | 'KG' | 'KM';
export type ChargeValueType = 'AMOUNT' | 'PERCENTAGE';
export type CommissionType = 'AMOUNT' | 'PERCENTAGE';

export const CHARGE_STATUSES: ChargeStatus[] = ['ACTIVE', 'INACTIVE'];
export const CHARGE_TYPES: ChargeType[] = ['FACTOR', 'SLAB'];
export const CHARGE_SLAB_TYPES: ChargeSlabType[] = ['BOTH', 'KG', 'KM'];
export const CHARGE_VALUE_TYPES: ChargeValueType[] = ['AMOUNT', 'PERCENTAGE'];
export const COMMISSION_TYPES: CommissionType[] = ['AMOUNT', 'PERCENTAGE'];

/** List projection — mirrors backend `ChargeSummaryResponse` (GET /charges). */
export interface Charge {
  id: string;
  chargeName: string;
  serviceTypeId: string;
  status: ChargeStatus;
  version: number;
}

/** One FACTOR or SLAB row — mirrors backend `ChargeSettingResponse`. */
export interface ChargeSetting {
  id: string;
  companyId: string;
  chargeId: string;
  chargeType: ChargeType;
  chargeSlabType: ChargeSlabType | null;
  fromKm: number | null;
  toKm: number | null;
  fromKg: number | null;
  toKg: number | null;
  chargeValue: number;
  chargeValueType: ChargeValueType;
  commissionType: CommissionType;
  commissionValue: number;
  status: ChargeStatus;
  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
}

/** Full representation, with its settings — mirrors backend `ChargeResponse` (GET /charges/{id}). */
export interface ChargeResponse {
  id: string;
  companyId: string;
  chargeName: string;
  serviceTypeId: string;
  status: ChargeStatus;
  settings: ChargeSetting[];
  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
}

/** Body of POST /charges — mirrors backend `CreateChargeRequest`. */
export interface CreateChargeRequest {
  chargeName: string;
  serviceTypeId: string;
}

/** Body of PUT /charges/{id} — mirrors backend `UpdateChargeRequest`. */
export interface UpdateChargeRequest {
  chargeName: string;
  serviceTypeId: string;
  version: number;
}

/** Advanced-filter criteria for GET /charges. All optional; merged into the page query. */
export interface ChargeSearchRequest {
  serviceTypeId?: string[];
  status?: ChargeStatus[];
  search?: string;
}

/** Fields shared by create and update of a charge setting. */
export interface ChargeSettingFields {
  chargeType: ChargeType;
  chargeSlabType?: ChargeSlabType | null;
  fromKm?: number | null;
  toKm?: number | null;
  fromKg?: number | null;
  toKg?: number | null;
  chargeValue: number;
  chargeValueType: ChargeValueType;
  commissionType: CommissionType;
  commissionValue: number;
}

/** Body of POST /charges/{chargeId}/settings — mirrors backend `CreateChargeSettingRequest`. */
export type CreateChargeSettingRequest = ChargeSettingFields;

/** Body of PUT /charges/{chargeId}/settings/{id} — mirrors backend `UpdateChargeSettingRequest`. */
export interface UpdateChargeSettingRequest extends ChargeSettingFields {
  version: number;
}

/**
 * Branch Management models — mirror the backend `branches` module one-to-one (see
 * MEMORY/modules/branch.md). A branch is a company's booking / delivery office (the UI-10
 * spec frames it as a "vendor / franchise"; the backend field for that classification is
 * `branchType`). No mock shapes: every field is returned by, or accepted by, an endpoint.
 */

export type BranchStatus = 'ACTIVE' | 'INACTIVE';

/** The branch classification — the backend's stand-in for "vendor type". 5 values. */
export type BranchType =
  | 'HEAD_OFFICE' | 'REGIONAL_OFFICE' | 'BOOKING_BRANCH'
  | 'DELIVERY_BRANCH' | 'BOOKING_DELIVERY_BRANCH';

export const BRANCH_TYPES: BranchType[] = [
  'HEAD_OFFICE', 'REGIONAL_OFFICE', 'BOOKING_BRANCH', 'DELIVERY_BRANCH', 'BOOKING_DELIVERY_BRANCH'
];

/**
 * List projection — mirrors backend `BranchSummaryResponse` (GET /branches). Compact: it
 * carries `managerId` (a UUID, resolved to a name via the users lookup) and only the two
 * headline capability flags. The full contact/address/hours live on {@link BranchResponse}.
 */
export interface Branch {
  id: string;
  companyId: string;
  branchCode: string;
  branchName: string;
  branchType: BranchType;
  status: BranchStatus;
  city?: string | null;
  state?: string | null;
  postalCode?: string | null;
  managerId?: string | null;
  allowBooking: boolean;
  allowDelivery: boolean;
  createdDate?: string | null;
  version: number;
}

/** Full representation — mirrors backend `BranchResponse` (GET /branches/{id}). */
export interface BranchResponse {
  id: string;
  companyId: string;
  branchCode: string;
  branchName: string;
  branchType: BranchType;
  status: BranchStatus;
  email?: string | null;
  mobile?: string | null;
  alternateMobile?: string | null;
  managerId?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  country?: string | null;
  state?: string | null;
  city?: string | null;
  district?: string | null;
  taluka?: string | null;
  postalCode?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  openingTime?: string | null;
  closingTime?: string | null;
  workingDays?: string | null;
  allowBooking: boolean;
  allowDelivery: boolean;
  allowPickup: boolean;
  allowManifest: boolean;
  allowCashCollection: boolean;
  allowWallet: boolean;
  instantCommission: boolean;
  remarks?: string | null;
  gstPercentage: number;
  commissionOnOtherCharges: number;
  commissionOnBasicFreight: number;
  companyServiceChargePercentage: number;
  /** DRS charge per item quantity, debited on delivery (drsCharge = drsChargePerQty * qty). */
  drsChargePerQty: number;
  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
  /** Only on the create response — never on a read. See BranchUserResponse. */
  branchUser?: BranchUserResponse | null;
}

/**
 * Body of POST /branches — mirrors backend `CreateBranchRequest`. `branchCode` is
 * uppercased / spaces→underscores on save, then immutable. `status` is not accepted (a new
 * branch starts ACTIVE). `managerId` optional here; it also has its own assign endpoint.
 */
/**
 * The branch's login account, filled in on the same form as the branch. Every field is
 * optional: omit the block entirely and the backend derives `<branch-code>@<company>.local`
 * with a generated password. Mirrors backend `BranchUserRequest`.
 */
export interface BranchUserRequest {
  email?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  mobile?: string | null;
  /** Blank means "generate one and return it once" — see BranchUserResponse. */
  password?: string | null;
}

/**
 * The account created with a branch. Present only on the POST /branches response.
 * `temporaryPassword` is populated only when the server generated it, is returned exactly
 * once and can never be read back — show it, then it is gone.
 *
 * `roleId`/`roleCode` are the company role the account was granted (`BRANCH_MANAGER`),
 * created from the default catalogue if the company had none. Distinct from the JWT role
 * authority the token carries: this one is the row the Roles screen manages.
 */
export interface BranchUserResponse {
  userId: string;
  email: string;
  temporaryPassword?: string | null;
  assignedAsManager: boolean;
  roleId?: string | null;
  roleCode?: string | null;
}

export interface CreateBranchRequest {
  branchCode: string;
  branchName: string;
  branchType: BranchType;
  email?: string | null;
  mobile?: string | null;
  alternateMobile?: string | null;
  managerId?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  country?: string | null;
  state?: string | null;
  city?: string | null;
  district?: string | null;
  taluka?: string | null;
  postalCode?: string | null;
  openingTime?: string | null;
  closingTime?: string | null;
  workingDays?: string | null;
  allowBooking?: boolean;
  allowDelivery?: boolean;
  allowPickup?: boolean;
  allowManifest?: boolean;
  allowCashCollection?: boolean;
  allowWallet?: boolean;
  /** Omitted defaults to true on the backend. */
  instantCommission?: boolean;
  remarks?: string | null;
  /** Omitted defaults to 18 on the backend. */
  gstPercentage?: number | null;
  /** Omitted defaults to 20 on the backend. */
  commissionOnOtherCharges?: number | null;
  /** Omitted defaults to 10 on the backend. */
  commissionOnBasicFreight?: number | null;
  /** Omitted defaults to 10 on the backend. */
  companyServiceChargePercentage?: number | null;
  /** Omitted defaults to 2 on the backend. */
  drsChargePerQty?: number | null;
  /** Optional; a branch always gets a user, this only says who it is. */
  branchUser?: BranchUserRequest | null;
}

/**
 * Body of PUT /branches/{id} — mirrors backend `UpdateBranchRequest`. Full replacement of
 * the editable fields; carries `version`. `branchCode` is immutable and omitted; `status`
 * and `managerId` are changed through their own endpoints, not here.
 */
export interface UpdateBranchRequest {
  branchName: string;
  branchType: BranchType;
  email?: string | null;
  mobile?: string | null;
  alternateMobile?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  country?: string | null;
  state?: string | null;
  city?: string | null;
  district?: string | null;
  taluka?: string | null;
  postalCode?: string | null;
  openingTime?: string | null;
  closingTime?: string | null;
  workingDays?: string | null;
  allowBooking?: boolean;
  allowDelivery?: boolean;
  allowPickup?: boolean;
  allowManifest?: boolean;
  allowCashCollection?: boolean;
  allowWallet?: boolean;
  instantCommission?: boolean;
  remarks?: string | null;
  gstPercentage: number;
  commissionOnOtherCharges: number;
  commissionOnBasicFreight: number;
  companyServiceChargePercentage: number;
  drsChargePerQty: number;
  version: number;
}

/** Advanced-filter criteria for GET /branches. All optional; merged into the page query. */
export interface BranchSearchRequest {
  branchType?: BranchType[];
  status?: BranchStatus[];
  managerId?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  allowBooking?: boolean;
  allowDelivery?: boolean;
  allowPickup?: boolean;
  search?: string;
}

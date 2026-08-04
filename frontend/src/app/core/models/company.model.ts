export type CompanyStatus = 'TRIAL' | 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'EXPIRED';

/** List projection — mirrors the backend CompanySummaryResponse. Used by the companies table. */
export interface Company {
  id: string;
  companyId: string;
  companyCode: string;
  companyName: string;
  legalName?: string;
  displayName?: string;
  subscriptionPlanId: string;
  status: CompanyStatus;
  email: string;
  mobile: string;
  city?: string;
  state?: string;
  country?: string;
  isActive: boolean;
  version: number;
}

/**
 * Full company (company) — mirrors the backend CompanyResponse.
 * Nulls are meaningful ("not set"), so optional rather than dropped.
 */
export interface CompanyResponse {
  id: string;
  companyId: string;
  companyCode: string;
  companyName: string;
  legalName?: string | null;
  displayName?: string | null;

  subscriptionPlanId: string;
  status: CompanyStatus;

  trialStartDate?: string | null;
  trialEndDate?: string | null;
  subscriptionStartDate?: string | null;
  subscriptionEndDate?: string | null;

  email: string;
  mobile: string;
  alternateMobile?: string | null;
  website?: string | null;

  gstNumber?: string | null;
  panNumber?: string | null;
  cinNumber?: string | null;

  logo?: string | null;
  favicon?: string | null;

  addressLine1?: string | null;
  addressLine2?: string | null;
  country?: string | null;
  state?: string | null;
  city?: string | null;
  postalCode?: string | null;

  timezone?: string | null;
  currency?: string | null;
  language?: string | null;
  dateFormat?: string | null;
  timeFormat?: string | null;

  isActive: boolean;
  remarks?: string | null;

  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;

  version: number;
}

/** Full-company alias used across the profile module. */
export type CompanyProfile = CompanyResponse;

/**
 * Body of PUT /api/v1/companies/{id} — mirrors the backend UpdateCompanyRequest.
 * Full replacement: an omitted optional field is written as null. `version` is
 * mandatory and echoes what was last read, so stale edits are rejected with 409.
 */
export interface CompanyRequest {
  companyName: string;
  legalName?: string | null;
  displayName?: string | null;
  subscriptionPlanId: string;
  email: string;
  mobile: string;
  alternateMobile?: string | null;
  website?: string | null;
  gstNumber?: string | null;
  panNumber?: string | null;
  cinNumber?: string | null;
  logo?: string | null;
  favicon?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  country?: string | null;
  state?: string | null;
  city?: string | null;
  postalCode?: string | null;
  timezone?: string | null;
  currency?: string | null;
  language?: string | null;
  dateFormat?: string | null;
  timeFormat?: string | null;
  remarks?: string | null;
  trialEndDate?: string | null;
  subscriptionStartDate?: string | null;
  subscriptionEndDate?: string | null;
  version: number;
}

/** Compact subscription-plan option for the plan dropdown. */
export interface SubscriptionPlanOption {
  id: string;
  planCode: string;
  planName: string;
  currency?: string;
  isActive: boolean;
}

// ---------------------------------------------------------------- subscription

export type BillingCycle = 'MONTHLY' | 'QUARTERLY' | 'HALF_YEARLY' | 'YEARLY';

export const BILLING_CYCLES: { value: BillingCycle; label: string; months: number }[] = [
  { value: 'MONTHLY', label: 'Monthly', months: 1 },
  { value: 'QUARTERLY', label: 'Quarterly', months: 3 },
  { value: 'HALF_YEARLY', label: 'Half-yearly', months: 6 },
  { value: 'YEARLY', label: 'Yearly', months: 12 }
];

/**
 * Body of POST /companies/{id}/subscription — mirrors AssignSubscriptionRequest.
 *
 * Supply either a `billingCycle` (the server derives the end date) or an explicit
 * `endDate` for a negotiated term. Both is allowed, and the explicit date wins: the
 * contract is the source of truth, not the calendar arithmetic.
 */
export interface AssignSubscriptionRequest {
  subscriptionPlanId: string;
  billingCycle?: BillingCycle | null;
  periods?: number | null;
  startDate?: string | null;
  endDate?: string | null;
  remarks?: string | null;
}

/**
 * Body of POST /companies/{id}/subscription/renew.
 *
 * No start date, deliberately: the server extends from the later of the current end and
 * today, so paying early keeps the days already bought and paying late is not billed for
 * the gap. Letting the caller pick would let it get that wrong.
 */
export interface RenewSubscriptionRequest {
  subscriptionPlanId?: string | null;
  billingCycle?: BillingCycle | null;
  periods?: number | null;
  endDate?: string | null;
  remarks?: string | null;
}

// ----------------------------------------------------------------- dashboards

/**
 * GET /companies/{id}/statistics.
 *
 * **No `shipmentCount`.** The shipments module does not exist, and a field that is always
 * zero reads as "this company has booked nothing" rather than "nobody has built this" —
 * indistinguishable on screen. It arrives with the module that can populate it.
 */
export interface CompanyStatistics {
  id: string;
  companyId: string;
  companyCode: string;
  companyName: string;
  status: CompanyStatus;

  subscriptionPlanId: string;
  planCode: string;
  planName: string;

  trialEndDate?: string | null;
  subscriptionStartDate?: string | null;
  subscriptionEndDate?: string | null;
  /** Negative once lapsed; null when the company has neither date. */
  daysToExpiry: number | null;

  userCount: number;
  activeUserCount: number;
  pendingUserCount: number;
  branchCount: number;
  activeBranchCount: number;
  roleCount: number;

  /** null means unlimited on both. */
  maxUsers: number | null;
  maxBranches: number | null;
  userQuotaReached: boolean;
  branchQuotaReached: boolean;
}

/** GET /super-admin/dashboard. Also carries no shipment figure, for the same reason. */
export interface PlatformDashboard {
  companyCount: number;
  /** Every status is present, including the ones at zero. */
  companiesByStatus: Record<CompanyStatus, number>;
  expiredCount: number;
  expiringSoonCount: number;
  planCount: number;
  activePlanCount: number;
  upcomingRenewals: PlatformRenewal[];
}

export interface PlatformRenewal {
  id: string;
  companyId: string;
  companyCode: string;
  companyName: string;
  status: CompanyStatus;
  endDate: string | null;
  daysRemaining: number;
}

// --------------------------------------------------------- platform operators

/** POST /super-admin/users. Omit `password` and one is generated and returned once. */
export interface CreateSuperAdminRequest {
  email: string;
  firstName?: string | null;
  lastName?: string | null;
  phone?: string | null;
  password?: string | null;
  homeCompanyId?: string | null;
}

/**
 * A platform-tier account.
 *
 * `temporaryPassword` is present only on the create response, and only when the server
 * generated it. Show it once and say so — it is never retrievable again.
 */
export interface SuperAdminUser {
  id: string;
  homeCompanyId: string | null;
  email: string;
  firstName?: string | null;
  lastName?: string | null;
  phone?: string | null;
  status: string;
  emailVerified: boolean;
  roles: string[];
  lastLoginAt?: string | null;
  createdAt?: string | null;
  temporaryPassword?: string | null;
}

/**
 * Body of POST /api/v1/companies — mirrors the backend CreateCompanyRequest.
 *
 * The admin block is optional: omitted, the first administrator is created at the
 * company's own email and mobile. Whichever address is used, that account is created
 * `PENDING` with a temporary password returned once in `provisioning.temporaryPassword`.
 */
export interface CreateCompanyRequest {
  companyCode: string;
  companyName: string;
  legalName?: string | null;
  displayName?: string | null;
  subscriptionPlanId: string;
  email: string;
  mobile: string;
  alternateMobile?: string | null;
  website?: string | null;
  gstNumber?: string | null;
  panNumber?: string | null;
  cinNumber?: string | null;
  logo?: string | null;
  favicon?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  country?: string | null;
  state?: string | null;
  city?: string | null;
  postalCode?: string | null;
  remarks?: string | null;
  subscriptionStartDate?: string | null;
  adminEmail?: string | null;
  adminFirstName?: string | null;
  adminLastName?: string | null;
  adminMobile?: string | null;
}

/**
 * What was created alongside a company. Present only on the create response.
 *
 * `temporaryPassword` is readable here and nowhere else, ever — never logged, emailed or
 * retrievable from a later request. Show it once and say so.
 */
export interface CompanyProvisioningSummary {
  adminUserId: string;
  adminEmail: string;
  temporaryPassword: string | null;
  activationEmailSent: boolean;
  roleCount: number;
  settingCount: number;
}

/** The create response: a full company plus the one-time provisioning block. */
export interface CreatedCompanyResponse extends CompanyResponse {
  provisioning?: CompanyProvisioningSummary | null;
}

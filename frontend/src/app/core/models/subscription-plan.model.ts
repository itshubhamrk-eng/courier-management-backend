/** Commercial tier. TRIAL must be free; ENTERPRISE has every quota forced unlimited. */
export type PlanType = 'TRIAL' | 'BASIC' | 'STANDARD' | 'PREMIUM' | 'ENTERPRISE';

export const PLAN_TYPES: PlanType[] = ['TRIAL', 'BASIC', 'STANDARD', 'PREMIUM', 'ENTERPRISE'];

/** List projection — mirrors backend SubscriptionPlanSummary (GET /subscription-plans). */
export interface SubscriptionPlan {
  id: string;
  planCode: string;
  planName: string;
  planType: PlanType;
  monthlyPrice: number;
  yearlyPrice: number;
  currency: string;
  trialDays: number;
  isActive: boolean;
  displayOrder: number;
  version: number;
}

/**
 * Full representation — mirrors backend SubscriptionPlanResponse (GET
 * /subscription-plans/{id}). A `null` quota means unlimited; kept `null` rather than
 * dropped, matching the backend's explicit `@JsonInclude(ALWAYS)`.
 */
export interface SubscriptionPlanProfile {
  id: string;
  planCode: string;
  planName: string;
  description?: string | null;
  planType: PlanType;
  monthlyPrice: number;
  yearlyPrice: number;
  currency: string;
  trialDays: number;
  maxUsers?: number | null;
  maxBranches?: number | null;
  maxHubs?: number | null;
  maxCustomers?: number | null;
  maxDrivers?: number | null;
  maxVehicles?: number | null;
  maxDailyBookings?: number | null;
  maxMonthlyBookings?: number | null;
  storageLimitGb?: number | null;
  apiRateLimit?: number | null;
  featureFlags?: Record<string, unknown>;
  isActive: boolean;
  displayOrder: number;
  unlimited: boolean;
  createdBy?: string | null;
  createdAt?: string | null;
  updatedBy?: string | null;
  updatedAt?: string | null;
  version: number;
}

/**
 * Body of POST /subscription-plans. `planCode` is uppercased on save, then immutable.
 * Omit a quota field, or send it `null`/`undefined`, to mean unlimited.
 */
export interface CreateSubscriptionPlanRequest {
  planCode: string;
  planName: string;
  description?: string | null;
  planType: PlanType;
  monthlyPrice: number;
  yearlyPrice: number;
  currency?: string;
  trialDays?: number;
  maxUsers?: number;
  maxBranches?: number;
  maxHubs?: number;
  maxCustomers?: number;
  maxDrivers?: number;
  maxVehicles?: number;
  maxDailyBookings?: number;
  maxMonthlyBookings?: number;
  storageLimitGb?: number;
  apiRateLimit?: number;
  featureFlags?: Record<string, unknown>;
  isActive?: boolean;
  displayOrder?: number;
}

/**
 * Body of PUT /subscription-plans/{id}. Full replacement — an omitted quota is written
 * as unlimited. `planCode` cannot be changed; activation has its own endpoints.
 */
export interface UpdateSubscriptionPlanRequest {
  planName: string;
  description?: string | null;
  planType: PlanType;
  monthlyPrice: number;
  yearlyPrice: number;
  currency?: string;
  trialDays?: number;
  maxUsers?: number;
  maxBranches?: number;
  maxHubs?: number;
  maxCustomers?: number;
  maxDrivers?: number;
  maxVehicles?: number;
  maxDailyBookings?: number;
  maxMonthlyBookings?: number;
  storageLimitGb?: number;
  apiRateLimit?: number;
  featureFlags?: Record<string, unknown>;
  displayOrder?: number;
  version: number;
}

/** Advanced-filter criteria for GET /subscription-plans. All optional. */
export interface SubscriptionPlanSearchRequest {
  planType?: PlanType;
  isActive?: boolean;
  currency?: string;
  minPrice?: number;
  maxPrice?: number;
  search?: string;
}

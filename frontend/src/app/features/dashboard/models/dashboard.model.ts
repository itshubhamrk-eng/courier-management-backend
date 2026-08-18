/**
 * Dashboard domain models. Every field maps to a real backend projection the operational
 * modules will expose; nothing here is fabricated. Until an endpoint exists the service
 * degrades a field to zero / empty (see dashboard.service.ts), never to a fake figure.
 */

/** Flat KPI figures. A role only renders the subset its layout selects (dashboard.roles.ts). */
export interface DashboardStatistics {
  // shared operational
  todayShipments: number;
  delivered: number;
  inTransit: number;
  pending: number;
  totalRevenue: number;
  activeBranches: number;
  activeHubs: number;
  /** Branch/Hub scoped only; null for company/platform scope. */
  walletBalance: number | null;

  // branch/operator focused
  todayBookings: number;
  todayCollection: number;
  pendingDelivery: number;

  // hub focused
  toReceive: number;
  toDispatch: number;
  inSorting: number;

  // platform (SUPER_ADMIN)
  totalCompanies: number;
  activeCompanies: number;
  totalShipments: number;
}

export const emptyStatistics = (): DashboardStatistics => ({
  todayShipments: 0, delivered: 0, inTransit: 0, pending: 0, totalRevenue: 0,
  activeBranches: 0, activeHubs: 0, walletBalance: null,
  todayBookings: 0, todayCollection: 0, pendingDelivery: 0,
  toReceive: 0, toDispatch: 0, inSorting: 0,
  totalCompanies: 0, activeCompanies: 0, totalShipments: 0
});

/** One (x,y) sample. `label` is the category (date/hour); `value` the measure. */
export interface TrendPoint { label: string; value: number; }

/** A named line/bar in a chart. */
export interface ChartSeries { name: string; points: TrendPoint[]; }

export interface DashboardCharts {
  shipmentTrend: ChartSeries[];
  deliveryPerformance: ChartSeries[];
  revenueTrend: ChartSeries[];
}

export const emptyCharts = (): DashboardCharts => ({
  shipmentTrend: [], deliveryPerformance: [], revenueTrend: []
});

export type ActivityKind = 'BOOKING' | 'DELIVERY' | 'WALLET' | 'SYSTEM';

/** A single item on the activity timeline. */
export interface DashboardActivity {
  id: string;
  kind: ActivityKind;
  title: string;
  detail?: string;
  at: string;          // ISO timestamp
  amount?: number;     // present for wallet events
}

/** A row in the Recent Shipments card. */
export interface RecentShipment {
  id: string;
  awb: string;
  consignee: string;
  destination: string;
  status: string;
  bookedAt: string;
  amount?: number;
}

/** A row in the Branch Summary card. */
export interface BranchSummaryRow {
  id: string;
  branchCode: string;
  branchName: string;
  city?: string;
  status: string;
  shipments?: number;
}

/** A row in the Hub Summary card. */
export interface HubSummaryRow {
  id: string;
  hubCode: string;
  hubName: string;
  city?: string;
  status: string;
  pending?: number;
}

/** One stage of the shipment lifecycle, with its month-to-date count. */
export interface PipelineStage { stage: string; count: number; }

/** A row in the Top Routes card. */
export interface TopRoute {
  branchId: string | null;
  branchCode: string | null;
  branchName: string;
  shipmentCount: number;
  revenue: number;
}

/** A row in the Top Customers card. */
export interface TopCustomer {
  customerName: string | null;
  customerContact: string | null;
  shipmentCount: number;
  revenue: number;
}

/**
 * Company-wide operational overview (COMPANY_ADMIN profile only) — pipeline, the
 * action-required backlog, wallet total across every branch, and top routes/customers.
 * `null` for a branch-scoped caller, whose layout never renders this section.
 */
export interface CompanyOverview {
  pipeline: PipelineStage[];
  readyForManifest: number;
  manifestsAwaitingDispatch: number;
  pendingDelivery: number;
  delayedShipments: number;
  totalWalletBalance: number;
  lowBalanceBranches: number;
  topRoutes: TopRoute[];
  topCustomers: TopCustomer[];
}

/**
 * Branch-scoped sibling of {@link CompanyOverview} — same pipeline/action-required shape
 * for a caller with an own branch (BRANCH_MANAGER/BRANCH_OPERATOR/hub roles). No wallet
 * total or top routes/customers: those are company-wide concepts, and the caller's own
 * wallet balance is already a KPI tile. `null` for a company/platform-scoped caller,
 * whose layout renders `companyOverview` instead.
 */
export interface BranchOverview {
  pipeline: PipelineStage[];
  readyForManifest: number;
  manifestsAwaitingDispatch: number;
  pendingDelivery: number;
  delayedShipments: number;
}

/** The full aggregate the page consumes. Assembled from several endpoints by the service. */
export interface DashboardSummary {
  statistics: DashboardStatistics;
  charts: DashboardCharts;
  recentActivity: DashboardActivity[];
  recentShipments: RecentShipment[];
  branchSummary: BranchSummaryRow[];
  hubSummary: HubSummaryRow[];
  companyOverview: CompanyOverview | null;
  branchOverview: BranchOverview | null;
}

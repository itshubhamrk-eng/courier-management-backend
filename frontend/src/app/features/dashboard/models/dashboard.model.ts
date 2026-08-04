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

/** The full aggregate the page consumes. Assembled from several endpoints by the service. */
export interface DashboardSummary {
  statistics: DashboardStatistics;
  charts: DashboardCharts;
  recentActivity: DashboardActivity[];
  recentShipments: RecentShipment[];
  branchSummary: BranchSummaryRow[];
  hubSummary: HubSummaryRow[];
}

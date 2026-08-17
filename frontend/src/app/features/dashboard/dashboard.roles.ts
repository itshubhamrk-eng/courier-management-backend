import { AppRole } from '@core/models/role.model';
import { DashboardStatistics } from './models/dashboard.model';

/**
 * Role-based dashboard layout. The backend role enum (AppRole) is richer/leaner than the
 * spec's role names in places, so we resolve every AppRole to one of these layout
 * profiles rather than hard-coding role literals in the page. The assigned branch/hub on
 * the session refines an ambiguous operator into its branch- or hub-flavoured view.
 */
export type DashboardProfile =
  | 'PLATFORM'         // SUPER_ADMIN / PLATFORM_ADMIN
  | 'COMPANY'          // COMPANY_ADMIN
  | 'BRANCH_MANAGER'   // BRANCH_MANAGER  (spec: Branch Admin)
  | 'BRANCH_OPERATOR'  // booking/delivery/finance operators at a branch
  | 'HUB_MANAGER'      // HUB_MANAGER
  | 'HUB_OPERATOR';    // operators at a hub

type Tone = 'brand' | 'success' | 'warning' | 'danger' | 'info';

export interface StatTileDef {
  key: keyof DashboardStatistics;
  label: string;
  icon: string;
  tone: Tone;
  /** Prefixed to the value, e.g. the currency symbol. */
  prefix?: string;
}

export interface QuickActionDef {
  id: string;
  label: string;
  icon: string;
  tone: Tone;
  /** Existing app route; omitted for actions whose module is not built yet. */
  route?: string;
}

/** Which optional cards a profile shows. Absent = hidden. */
export interface SectionFlags {
  shipmentTrend: boolean;
  deliveryPerformance: boolean;
  revenueTrend: boolean;
  recentActivity: boolean;
  recentShipments: boolean;
  branchSummary: boolean;
  hubSummary: boolean;
}

export interface DashboardLayout {
  stats: StatTileDef[];
  quickActions: QuickActionDef[];
  sections: SectionFlags;
}

// --- reusable tiles -------------------------------------------------------
const T = {
  todayShipments: { key: 'todayShipments', label: "This Month's Shipments", icon: 'local_shipping', tone: 'brand' },
  delivered:      { key: 'delivered', label: 'Delivered This Month', icon: 'task_alt', tone: 'success' },
  inTransit:      { key: 'inTransit', label: 'In Transit This Month', icon: 'moving', tone: 'info' },
  pending:        { key: 'pending', label: 'Pending This Month', icon: 'pending_actions', tone: 'warning' },
  revenue:        { key: 'totalRevenue', label: 'Total Revenue', icon: 'payments', tone: 'success', prefix: '₹' },
  activeBranches: { key: 'activeBranches', label: 'Active Branches', icon: 'store', tone: 'brand' },
  // activeHubs: { key: 'activeHubs', label: 'Active Hubs', icon: 'hub', tone: 'info' }, // hub module not built yet
  wallet:         { key: 'walletBalance', label: 'Wallet Balance', icon: 'account_balance_wallet', tone: 'brand', prefix: '₹' },
  todayBookings:  { key: 'todayBookings', label: "This Month's Bookings", icon: 'add_box', tone: 'info' },
  todayCollection:{ key: 'todayCollection', label: "This Month's Collection", icon: 'savings', tone: 'success', prefix: '₹' },
  pendingDelivery:{ key: 'pendingDelivery', label: 'Pending Delivery', icon: 'local_shipping', tone: 'warning' },
  toReceive:      { key: 'toReceive', label: 'To Receive', icon: 'call_received', tone: 'brand' },
  toDispatch:     { key: 'toDispatch', label: 'Dispatch Queue', icon: 'send', tone: 'info' },
  inSorting:      { key: 'inSorting', label: 'In Sorting', icon: 'sort', tone: 'warning' },
  totalCompanies: { key: 'totalCompanies', label: 'Total Companies', icon: 'apartment', tone: 'brand' },
  activeCompanies:{ key: 'activeCompanies', label: 'Active Companies', icon: 'domain_verification', tone: 'success' },
  totalShipments: { key: 'totalShipments', label: 'Total Shipments', icon: 'inventory_2', tone: 'info' }
} satisfies Record<string, StatTileDef>;

// --- quick actions --------------------------------------------------------
const QA = {
  book:     { id: 'book', label: 'Book Shipment', icon: 'add_box', tone: 'brand', route: '/shipments/new' },
  search:   { id: 'search', label: 'Search Shipment', icon: 'search', tone: 'info', route: '/shipments' },
  track:    { id: 'track', label: 'Track Shipment', icon: 'my_location', tone: 'success', route: '/track' },
  print:    { id: 'print', label: 'Print Label', icon: 'print', tone: 'warning' },
  loadingSheet: { id: 'loadingSheet', label: 'Loading Sheet', icon: 'qr_code_scanner', tone: 'danger', route: '/movement/loading-sheet' },
  dispatch: { id: 'dispatch', label: 'THC', icon: 'send', tone: 'info' },
  receive:  { id: 'receive', label: 'Receive', icon: 'call_received', tone: 'success' },
  branches: { id: 'branches', label: 'Branches', icon: 'store', tone: 'brand', route: '/branches' },
  // hubs: { id: 'hubs', label: 'Hubs', icon: 'hub', route: '/hubs' }, // hub module not built yet
  users:    { id: 'users', label: 'Users', icon: 'group', tone: 'warning', route: '/users' },
  companies:{ id: 'companies', label: 'Companies', icon: 'apartment', tone: 'danger', route: '/companies' }
} satisfies Record<string, QuickActionDef>;

const allSectionsOff: SectionFlags = {
  shipmentTrend: false, deliveryPerformance: false, revenueTrend: false,
  recentActivity: false, recentShipments: false, branchSummary: false, hubSummary: false
};

export const DASHBOARD_LAYOUTS: Record<DashboardProfile, DashboardLayout> = {
  PLATFORM: {
    stats: [T.totalCompanies, T.activeCompanies, T.totalShipments, { ...T.revenue, tone: 'warning' }],
    quickActions: [QA.companies, QA.users, QA.branches],
    sections: { ...allSectionsOff, shipmentTrend: true, revenueTrend: true, recentActivity: true }
  },
  COMPANY: {
    stats: [T.todayShipments, T.delivered, T.inTransit, T.pending, T.revenue, { ...T.activeBranches, tone: 'danger' }],
    quickActions: [QA.book, QA.search, QA.track, QA.branches, QA.users],
    sections: {
      shipmentTrend: true, deliveryPerformance: true, revenueTrend: true,
      recentActivity: true, recentShipments: true, branchSummary: true, hubSummary: false
    }
  },
  BRANCH_MANAGER: {
    stats: [T.wallet, T.todayBookings, T.pendingDelivery, T.todayCollection],
    quickActions: [QA.book, QA.search, QA.track, QA.loadingSheet, QA.print],
    sections: {
      ...allSectionsOff, shipmentTrend: true, deliveryPerformance: true,
      recentActivity: true, recentShipments: true
    }
  },
  BRANCH_OPERATOR: {
    stats: [T.todayBookings, T.todayShipments, { ...T.wallet, tone: 'success' }, T.pending],
    quickActions: [QA.book, QA.search, QA.track, QA.loadingSheet, QA.print],
    sections: { ...allSectionsOff, recentActivity: true, recentShipments: true }
  },
  HUB_MANAGER: {
    stats: [T.toReceive, { ...T.inSorting, tone: 'danger' }, T.toDispatch, T.pending],
    quickActions: [QA.receive, QA.dispatch, QA.loadingSheet, QA.search, QA.track],
    sections: { ...allSectionsOff, shipmentTrend: true, recentActivity: true }
  },
  HUB_OPERATOR: {
    stats: [T.toReceive, T.toDispatch, { ...T.todayShipments, tone: 'success' }],
    quickActions: [QA.receive, QA.dispatch, QA.search, QA.track],
    sections: { ...allSectionsOff, recentActivity: true }
  }
};

/**
 * Map the highest-privilege AppRole the user holds to a layout profile, then let an
 * assigned hub/branch refine an operator. Order matters: check the strongest first.
 */
export function resolveProfile(
  roles: string[],
  scope: { branchId?: string | null; hubId?: string | null }
): DashboardProfile {
  const has = (r: AppRole) => roles.includes(r);

  if (has(AppRole.SUPER_ADMIN) || has(AppRole.PLATFORM_ADMIN)) return 'PLATFORM';
  if (has(AppRole.COMPANY_ADMIN)) return 'COMPANY';
  if (has(AppRole.BRANCH_MANAGER)) return 'BRANCH_MANAGER';
  if (has(AppRole.HUB_MANAGER)) return 'HUB_MANAGER';

  // operators & other staff: branch vs hub decided by their assignment
  if (scope.hubId) return 'HUB_OPERATOR';
  return 'BRANCH_OPERATOR';
}

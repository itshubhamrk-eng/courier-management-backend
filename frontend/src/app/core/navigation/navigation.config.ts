import { NavNode } from './navigation.model';
import { AppRole } from '../models/role.model';

/**
 * The application navigation, authored as a nested tree. Every leaf declares the `permission`
 * code that governs its visibility once the backend authorises on permissions; today the JWT
 * still carries only roles, so each leaf also declares the `roles` bridge (declarative data,
 * resolved by PermissionService — not a role check in code). When permission codes start
 * arriving after login, they take over automatically and the `roles` lists can be removed.
 *
 * Permission codes follow the backend `MODULE_ACTION` convention (view-style gates here);
 * align them with the 174-row catalogue when the authorise-on-permissions step ships.
 *
 * Aspirational sections (Masters, Pricing, Operations, Finance, Reports) point at routes whose
 * feature modules are not built yet — the pages render an empty state, never mock data.
 */
const ADMINS = [AppRole.SUPER_ADMIN, AppRole.COMPANY_ADMIN];
/** The platform tier. Nothing under Platform is a company's to see. */
const PLATFORM = [AppRole.SUPER_ADMIN];
const MANAGERS = [...ADMINS, AppRole.BRANCH_MANAGER, AppRole.HUB_MANAGER];
/** Finance is company-side work — SUPER_ADMIN excluded, branch/finance roles unchanged. */
const FINANCE = [AppRole.COMPANY_ADMIN, AppRole.FINANCE_USER];
/** Every non-platform role — Customers is every company user's tool (counter desk
 * registers, back office edits), SUPER_ADMIN excluded. */
const COMPANY_ROLES = [
  AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER, AppRole.HUB_MANAGER, AppRole.BOOKING_OPERATOR,
  AppRole.DELIVERY_OPERATOR, AppRole.ACCOUNTS, AppRole.FINANCE_USER, AppRole.CUSTOMER_SERVICE, AppRole.VIEWER
];
/** The branch's money desk, alongside whoever runs the branch. */
const ACCOUNTS_DESK = [...FINANCE, AppRole.BRANCH_MANAGER, AppRole.ACCOUNTS];
/** Ticket Support: every company role raises/reads tickets, plus SUPER_ADMIN's own
 * cross-tenant view — the one nav section every signed-in role sees something of. */
const SUPPORT_READERS = [...COMPANY_ROLES, AppRole.SUPER_ADMIN];
/** Follow-up Management: branch operational tasks — every company role, no SUPER_ADMIN
 * (unlike Ticket Support, a follow-up has no cross-tenant view at all). */
const FOLLOW_UP_READERS = COMPANY_ROLES;
/** Every branch role reads what it books, moves or is asked about. */
const SHIPMENT_READERS = [...MANAGERS, AppRole.BOOKING_OPERATOR, AppRole.DELIVERY_OPERATOR, AppRole.ACCOUNTS];
/** Every branch staff role reads its own reports. */
const BRANCH_REPORT_READERS = [...MANAGERS, AppRole.BOOKING_OPERATOR, AppRole.DELIVERY_OPERATOR, AppRole.ACCOUNTS];
/** Company Settings / Branches / the company's own six Masters catalogues: the back
 * office's own setup work — no platform operator, no branch role. */
const COMPANY_ONLY = [AppRole.COMPANY_ADMIN];
/** The shared geography (Country..Pincode): read by the platform and by the company that
 * looks it up while booking; written by the platform alone (see master.config's
 * `GLOBAL_MASTER_WRITERS`, which the create/edit screens gate on, not this nav bridge). */
const GLOBAL_MASTER_READERS = [AppRole.SUPER_ADMIN, AppRole.COMPANY_ADMIN];
/** Rate Master: company and branch price their own shipments; a platform operator prices
 * nothing. */
const COMPANY_AND_BRANCH = [AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER];
/** Operations tier, SUPER_ADMIN excluded — day-to-day shipment work is a company's own,
 * same reasoning as the Platform section's own comment above. */
const OPS_MANAGERS = [AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER, AppRole.HUB_MANAGER];
/** The branch's own counter desk: finds/registers the customer, then books. */
const OPS_BOOKING = [...OPS_MANAGERS, AppRole.BOOKING_OPERATOR];
/** The road: takes the inbound manifest in, runs deliveries out, closes them. */
const OPS_DELIVERY_DESK = [...OPS_MANAGERS, AppRole.DELIVERY_OPERATOR];
const OPS_SHIPMENT_READERS = [...OPS_MANAGERS, AppRole.BOOKING_OPERATOR, AppRole.DELIVERY_OPERATOR, AppRole.ACCOUNTS];

export const NAVIGATION: NavNode[] = [
  { id: 'dashboard', title: 'Dashboard', icon: 'dashboard', route: '/dashboard', order: 1 },

  // The platform console. A super admin manages companies, their subscriptions and other
  // platform operators — and nothing operational. There is deliberately no branch,
  // shipment, customer, manifest or wallet entry here: those are a company's own work,
  // and a record a platform operator created would be indistinguishable from one the
  // company created itself. The shared geography used to live here too; it moved to
  // Masters (below) once COMPANY_ADMIN needed to read it as well, not just SUPER_ADMIN.
  {
    id: 'platform', title: 'Platform', icon: 'travel_explore', order: 1.5, roles: PLATFORM,
    children: [
      { id: 'platform-dashboard', title: 'Platform Dashboard', icon: 'monitoring', route: '/platform/dashboard', permission: 'DASHBOARD_READ', roles: PLATFORM },
      { id: 'companies', title: 'Companies', icon: 'domain', route: '/companies', permission: 'COMPANY_READ', roles: PLATFORM },
      { id: 'subscription-plans', title: 'Subscription Plans', icon: 'workspace_premium', route: '/subscription-plans', permission: 'SUBSCRIPTION_READ', roles: PLATFORM },
      { id: 'super-admins', title: 'Platform Operators', icon: 'shield_person', route: '/platform/users', permission: 'SUPER_ADMIN_USER_READ', roles: PLATFORM }
    ]
  },

  {
    id: 'administration', title: 'Administration', icon: 'admin_panel_settings', order: 2,
    children: [
      { id: 'users', title: 'Users', icon: 'group', route: '/users', permission: 'USER_VIEW', roles: [...ADMINS, AppRole.BRANCH_MANAGER] },
      { id: 'roles', title: 'Roles', icon: 'badge', route: '/roles', permission: 'ROLE_VIEW', roles: ADMINS },
      { id: 'permissions', title: 'Permissions', icon: 'key', route: '/permissions', permission: 'PERMISSION_VIEW', roles: ADMINS },
      { id: 'permission-assign', title: 'Assign Permissions', icon: 'rule', route: '/permissions/assign', permission: 'PERMISSION_ASSIGN', roles: [AppRole.COMPANY_ADMIN] },
      { id: 'branches', title: 'Branches', icon: 'store', route: '/branches', permission: 'BRANCH_VIEW', roles: COMPANY_ONLY },
      // { id: 'hubs', title: 'Hubs', icon: 'hub', route: '/hubs', permission: 'HUB_VIEW', roles: MANAGERS }, // hub module not built yet
      { id: 'company-settings', title: 'Company Settings', icon: 'tune', route: '/settings', permission: 'SETTINGS_VIEW', roles: COMPANY_ONLY }
    ]
  },

  {
    id: 'customers', title: 'Customers', icon: 'contacts', route: '/customers', order: 2.5,
    permission: 'CUSTOMER_READ', roles: COMPANY_ROLES.filter((r) => r !== AppRole.BRANCH_MANAGER)
    // Every company-side role, SUPER_ADMIN excluded — the backend itself reads with
    // isAuthenticated() (any signed-in company user), see CustomerService. BRANCH_MANAGER
    // dropped by direct request: a branch manager's own nav leads to Users (staffing their
    // branch), not Customers.
  },

  {
    id: 'rate-master', title: 'Rate Master', icon: 'price_change', order: 2.7,
    children: [
      { id: 'rates', title: 'Rate Cards', icon: 'request_quote', route: '/rates', permission: 'RATE_MASTER_READ', roles: COMPANY_AND_BRANCH },
      // Calculator page hosts both the Rate and Freight Factor calculators as tabs —
      // one calculator entry point for both pricing mechanisms, by direct request.
      { id: 'rate-calculator', title: 'Calculator', icon: 'calculate', route: '/rates/calculator', permission: 'RATE_MASTER_CALCULATE', roles: COMPANY_AND_BRANCH },
      // Standalone distance x weight pricing grid, independent of Rate Master's own
      // Route-based rates above — grouped here as a submenu for nav convenience only.
      // No `permission` key: backend has no permission codes for this module, writes
      // are COMPANY_ADMIN-only in-page. Its own Calculate card moved to the Calculator
      // page above as a tab — this leaf is the grid itself now.
      { id: 'freight-factor', title: 'Freight Factor', icon: 'grid_on', route: '/freight-factors', roles: COMPANY_AND_BRANCH }
    ]
  },

  // Resolve/list the road distance between two branches — groundwork for distance/freight-
  // factor pricing, not itself a pricing feature. Same isAuthenticated() posture as Rate
  // Master, so the same role tier (company + branch) is reused.
  { id: 'address-distance', title: 'Address Distance', icon: 'social_distance', route: '/distances', order: 2.8, roles: COMPANY_AND_BRANCH },

  {
    id: 'masters', title: 'Masters', icon: 'inventory_2', order: 3,
    children: [
      // The six geography lists: one catalogue shared by every company. SUPER_ADMIN and
      // COMPANY_ADMIN both read (a company looks up its own pincodes); only SUPER_ADMIN
      // writes — MasterList/MasterView enforce that per-list split themselves
      // (`readAccessFor`/`writeAccessFor` keyed on MasterDefinition.global), since this
      // nav bridge and the shared `masters/:master` route can't tell geography from a
      // company's own catalogue on their own.
      { id: 'global-countries', title: 'Country', icon: 'public', route: '/masters/countries', permission: 'GLOBAL_MASTER_READ', roles: GLOBAL_MASTER_READERS },
      { id: 'global-states', title: 'State', icon: 'flag', route: '/masters/states', permission: 'GLOBAL_MASTER_READ', roles: GLOBAL_MASTER_READERS },
      { id: 'global-districts', title: 'District', icon: 'map', route: '/masters/districts', permission: 'GLOBAL_MASTER_READ', roles: GLOBAL_MASTER_READERS },
      { id: 'global-cities', title: 'City', icon: 'location_city', route: '/masters/cities', permission: 'GLOBAL_MASTER_READ', roles: GLOBAL_MASTER_READERS },
      { id: 'global-areas', title: 'Area', icon: 'pin_drop', route: '/masters/areas', permission: 'GLOBAL_MASTER_READ', roles: GLOBAL_MASTER_READERS },
      { id: 'global-pincodes', title: 'Pincode', icon: 'markunread_mailbox', route: '/masters/pincodes', permission: 'GLOBAL_MASTER_READ', roles: GLOBAL_MASTER_READERS },
      // The company's own six catalogues — COMPANY_ADMIN only, both read and write.
      { id: 'vehicle-types', title: 'Vehicle Type', icon: 'local_shipping', route: '/masters/vehicle-types', permission: 'MASTER_DATA_READ', roles: COMPANY_ONLY },
      // The fleet itself (registration, class, ownership dates, statutory document
      // expiries) — a separate, bespoke module (com.courier.modules.manifest.Vehicle),
      // not one of the twelve generic master-data catalogues above; COMPANY_ADMIN and
      // BRANCH_MANAGER both write, matching VehicleServiceImpl's own gate (no
      // permission code exists for it yet, same as Freight Factor/Address Distance).
      { id: 'vehicles', title: 'Vehicles', icon: 'local_shipping', route: '/masters/vehicles', roles: COMPANY_AND_BRANCH },
      { id: 'package-types', title: 'Package Type', icon: 'inventory_2', route: '/masters/package-types', permission: 'MASTER_DATA_READ', roles: COMPANY_ONLY },
      { id: 'service-types', title: 'Service Type', icon: 'bolt', route: '/masters/service-types', permission: 'MASTER_DATA_READ', roles: COMPANY_ONLY },
      { id: 'payment-modes', title: 'Payment Mode', icon: 'payments', route: '/masters/payment-modes', permission: 'MASTER_DATA_READ', roles: COMPANY_ONLY },
      { id: 'weight-slabs', title: 'Weight Slab', icon: 'scale', route: '/masters/weight-slabs', permission: 'MASTER_DATA_READ', roles: COMPANY_ONLY },
      { id: 'routes', title: 'Route', icon: 'route', route: '/masters/routes', permission: 'ROUTE_MASTER_READ', roles: COMPANY_ONLY }
    ]
  },

  {
    id: 'pricing', title: 'Pricing (Soon)', icon: 'sell', order: 4,
    children: [
      { id: 'rate-cards', title: 'Rate Cards (Soon)', icon: 'payments', route: '/pricing/rate-cards', permission: 'RATE_VIEW', roles: COMPANY_ONLY },
      { id: 'zone-pricing', title: 'Zone Pricing (Soon)', icon: 'grid_on', route: '/pricing/zones', permission: 'RATE_VIEW', roles: COMPANY_ONLY },
      { id: 'surcharges', title: 'Surcharges (Soon)', icon: 'local_gas_station', route: '/pricing/surcharges', permission: 'RATE_VIEW', roles: COMPANY_ONLY }
    ]
  },

  {
    id: 'operations', title: 'Operations', icon: 'local_shipping', order: 5,
    children: [
      // Booking is the counter desk's job (SHIPMENT_CREATE); manifest through delivery is
      // the road (MANIFEST_DISPATCH, MANIFEST_RECEIVE, DELIVERY_DISPATCH, DELIVERY_DELIVER)
      // — see DefaultRoleCatalog's BOOKING_OPERATOR / DELIVERY_OPERATOR definitions.
      // Shipment Movement (V19) shipped these five: Loading Sheet/Trip Hire Challan (THC) happen at the
      // booking branch (OPS_BOOKING), In Scan/Out For Delivery/Deliver at the delivery
      // branch (OPS_DELIVERY_DESK) — see MEMORY/modules/shipment-movement.md.
      // 'sorting' has no module behind it yet and stays aspirational.
      { id: 'booking', title: 'Shipment Booking', icon: 'add_box', route: '/shipments/new', permission: 'SHIPMENT_CREATE', roles: OPS_BOOKING },
      { id: 'shipment-search', title: 'Shipment Search', icon: 'search', route: '/shipments', permission: 'SHIPMENT_VIEW', roles: OPS_SHIPMENT_READERS },
      { id: 'track', title: 'Track Shipment', icon: 'my_location', route: '/track', permission: 'SHIPMENT_VIEW', roles: OPS_SHIPMENT_READERS },
      { id: 'manifest', title: 'Loading Sheet', icon: 'qr_code_scanner', route: '/movement/loading-sheet', permission: 'TRACKING_CREATE', roles: OPS_BOOKING },
      { id: 'dispatch', title: 'Trip Hire Challan (THC)', icon: 'outbound', route: '/movement/trip-hire-challan', permission: 'MANIFEST_DISPATCH', roles: OPS_BOOKING },
      { id: 'receive', title: 'In Scan', icon: 'move_to_inbox', route: '/movement/in-scan', permission: 'MANIFEST_RECEIVE', roles: OPS_DELIVERY_DESK },
      { id: 'pending-delivery', title: 'Pending Delivery', icon: 'pending_actions', route: '/movement/pending-delivery', permission: 'DELIVERY_ASSIGN', roles: OPS_DELIVERY_DESK },
      { id: 'out-for-delivery', title: 'Generate DRS', icon: 'directions_run', route: '/movement/out-for-delivery', permission: 'DELIVERY_ASSIGN', roles: OPS_DELIVERY_DESK },
      { id: 'delivery', title: 'Delivery', icon: 'task_alt', route: '/movement/delivery', permission: 'DELIVERY_DELIVER', roles: OPS_DELIVERY_DESK },
      // POD Auto Verification's Manual Review screen — reviewer tier only, narrower than
      // OPS_DELIVERY_DESK (which also admits the delivery operator who captures a POD but
      // must not decide their own submission). See MEMORY/modules/pod-verification.md.
      { id: 'pod-review', title: 'POD Review', icon: 'fact_check', route: '/movement/pod-review', permission: 'POD_REVIEW', roles: COMPANY_AND_BRANCH }
    ]
  },

  {
    id: 'finance', title: 'Finance', icon: 'account_balance', order: 6,
    children: [
      // Recharging its own wallet is a branch responsibility; ACCOUNTS is the branch's
      // money desk and holds WALLET_RECHARGE alongside BRANCH_MANAGER.
      { id: 'branch-wallet', title: 'Branch Wallet', icon: 'account_balance_wallet', route: '/finance/branch-wallet', permission: 'BRANCH_WALLET_VIEW', roles: ACCOUNTS_DESK },
      { id: 'hub-wallet', title: 'Hub Wallet (Soon)', icon: 'savings', route: '/finance/hub-wallet', permission: 'WALLET_VIEW', roles: FINANCE },
      { id: 'wallet-transactions', title: 'Wallet Transactions', icon: 'receipt', route: '/finance/branch-wallet/transactions', permission: 'WALLET_VIEW', roles: ACCOUNTS_DESK },
      { id: 'topup-requests', title: 'Top-up Requests', icon: 'fact_check', route: '/finance/branch-wallet/topup-requests', permission: 'WALLET_VIEW', roles: ACCOUNTS_DESK },
      { id: 'settlement', title: 'Settlement (Soon)', icon: 'paid', route: '/finance/settlement', permission: 'SETTLEMENT_VIEW', roles: FINANCE },
      { id: 'payment', title: 'Payment (Soon)', icon: 'credit_card', route: '/finance/payment', permission: 'PAYMENT_VIEW', roles: [...FINANCE, AppRole.ACCOUNTS] },
      { id: 'invoice', title: 'Invoice (Soon)', icon: 'description', route: '/finance/invoice', permission: 'INVOICE_VIEW', roles: [...FINANCE, AppRole.ACCOUNTS] }
    ]
  },

  {
    id: 'follow-up', title: 'Follow-ups', icon: 'event_repeat', order: 6.4,
    children: [
      { id: 'follow-up-list', title: 'Follow-ups', icon: 'checklist', route: '/follow-ups', roles: FOLLOW_UP_READERS },
      { id: 'follow-up-new', title: 'Create Follow-up', icon: 'add_box', route: '/follow-ups/new', roles: FOLLOW_UP_READERS }
    ]
  },

  {
    id: 'support', title: 'Ticket Support', icon: 'support_agent', order: 6.5,
    children: [
      { id: 'support-tickets', title: 'Tickets', icon: 'confirmation_number', route: '/support/tickets', roles: SUPPORT_READERS },
      { id: 'support-new-ticket', title: 'Raise Ticket', icon: 'add_box', route: '/support/tickets/new', roles: SUPPORT_READERS },
      // Company-wide view, not branch-scoped — COMPANY_ADMIN and SUPER_ADMIN only.
      { id: 'support-dashboard', title: 'Support Dashboard', icon: 'dashboard', route: '/support/dashboard', roles: ADMINS },
      // Global category catalogue — SUPER_ADMIN only, same tier as Subscription Plans.
      { id: 'support-categories', title: 'Categories', icon: 'category', route: '/support/categories', roles: PLATFORM },
      // Per-priority first-response/resolution targets — company-scoped, COMPANY_ADMIN's own call.
      { id: 'support-sla-rules', title: 'SLA Rules', icon: 'schedule', route: '/support/sla-rules', roles: ADMINS }
    ]
  },

  {
    id: 'reports', title: 'Reports', icon: 'insights', order: 7,
    children: [
      { id: 'booking-report', title: 'Booking Report', icon: 'summarize', route: '/reports/bookings', permission: 'REPORT_VIEW', roles: SHIPMENT_READERS },
      { id: 'delivery-report', title: 'Delivery Report', icon: 'local_shipping', route: '/reports/deliveries', permission: 'REPORT_VIEW', roles: SHIPMENT_READERS },
      { id: 'commission-report', title: 'Commission Report', icon: 'payments', route: '/reports/commissions', permission: 'REPORT_VIEW', roles: SHIPMENT_READERS },
      { id: 'drs-report', title: 'DRS Report', icon: 'directions_run', route: '/reports/drs', permission: 'REPORT_VIEW', roles: SHIPMENT_READERS },
      { id: 'thc-report', title: 'Trip Hire Challan Report', icon: 'outbound', route: '/reports/thc', permission: 'REPORT_VIEW', roles: SHIPMENT_READERS },
      { id: 'branch-reports', title: 'Branch Reports', icon: 'bar_chart', route: '/reports/branches', permission: 'REPORT_VIEW', roles: BRANCH_REPORT_READERS },
      // Branch responsibility #11 — every branch staff role reads reports on the work it did.
      { id: 'finance-reports', title: 'Finance Reports', icon: 'analytics', route: '/reports/finance', permission: 'REPORT_VIEW', roles: [...FINANCE, AppRole.ACCOUNTS] },
      { id: 'customer-report', title: 'Customer Report', icon: 'groups', route: '/reports/customers', permission: 'REPORT_VIEW', roles: COMPANY_ROLES.filter((r) => r !== AppRole.BRANCH_MANAGER) },
      { id: 'exception-report', title: 'Shipment Exceptions', icon: 'report_problem', route: '/reports/exceptions', permission: 'REPORT_VIEW', roles: SHIPMENT_READERS }
    ]
  },

  { id: 'settings', title: 'Settings', icon: 'settings', route: '/settings', permission: 'SETTINGS_VIEW', roles: COMPANY_ONLY, order: 8 }
];

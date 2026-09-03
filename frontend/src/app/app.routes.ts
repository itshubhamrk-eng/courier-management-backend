import { Routes } from '@angular/router';
import { authGuard } from '@core/guards/auth.guard';
import { guestGuard } from '@core/guards/guest.guard';
import { roleGuard } from '@core/guards/role.guard';
import { AppRole } from '@core/models/role.model';

const ADMINS = [AppRole.SUPER_ADMIN, AppRole.COMPANY_ADMIN];
const UPDATERS = [AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER];
// Finance is company-side work — SUPER_ADMIN excluded, branch/finance roles unchanged.
const WALLET_VIEWERS = [AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER, AppRole.FINANCE_USER];
const FINANCE_REPORT_READERS = [AppRole.COMPANY_ADMIN, AppRole.FINANCE_USER, AppRole.ACCOUNTS];
// Customer create/update: COMPANY_ADMIN, BRANCH_MANAGER or the counter desk — mirrors the
// backend CustomerService @PreAuthorize. Lifecycle (activate/deactivate) is narrower, same
// as branch lifecycle.
const CUSTOMER_WRITERS = [...UPDATERS, AppRole.BOOKING_OPERATOR, AppRole.CUSTOMER_SERVICE];
// Customer reads: every non-platform role — SUPER_ADMIN excluded, everyone else who signs
// into a company keeps exactly the access they had.
const CUSTOMER_READERS = [
  AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER, AppRole.HUB_MANAGER, AppRole.BOOKING_OPERATOR,
  AppRole.DELIVERY_OPERATOR, AppRole.ACCOUNTS, AppRole.FINANCE_USER, AppRole.CUSTOMER_SERVICE, AppRole.VIEWER
];
const WALLET_RECHARGERS = [AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER, AppRole.FINANCE_USER];
// Shipment Booking: the counter desk's own job. Mirrors ShipmentServiceImpl's WRITERS
// gate (COMPANY_ADMIN, BRANCH_MANAGER, OPERATOR); BOOKING_OPERATOR is the company-role
// side of that same JWT-role gap the "authorise on permissions" capstone still owns.
const SHIPMENT_WRITERS = [...UPDATERS, AppRole.BOOKING_OPERATOR];
// Shipment reads: same tier as SHIPMENT_WRITERS plus the road (delivery desk reads what
// it's asked to move) and accounts — SUPER_ADMIN excluded, day-to-day operations are a
// company's own work, not the platform's.
const SHIPMENT_READERS = [...SHIPMENT_WRITERS, AppRole.DELIVERY_OPERATOR, AppRole.ACCOUNTS];
// Shipment Movement: the same desks as Shipment Booking, plus the delivery desk —
// Loading Sheet/Trip Hire Challan (THC) are typically the booking branch, In Scan/Out
// For Delivery/Deliver the
// delivery branch. Mirrors ShipmentServiceImpl's movement WRITERS (COMPANY_ADMIN,
// BRANCH_MANAGER, OPERATOR) the same way SHIPMENT_WRITERS does for booking.
const MOVEMENT_WRITERS = [...UPDATERS, AppRole.BOOKING_OPERATOR, AppRole.DELIVERY_OPERATOR];
// Rate Master: company and branch price their own shipments; a platform operator prices
// nothing.
const RATE_READERS = [AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER];
// Company Settings / Branches: the back office's own setup work — no platform operator,
// no branch role.
const COMPANY_ONLY = [AppRole.COMPANY_ADMIN];
// Masters route guard: the geography lists (SUPER_ADMIN + COMPANY_ADMIN) and a company's
// own six catalogues (COMPANY_ADMIN only) share this one `masters/:master` route, so the
// guard admits the union — MasterList/MasterView narrow per-list from MasterDefinition's
// own `global` flag via `readAccessFor`, the same source `writeAccessFor` already reads.
const MASTERS_READERS = ADMINS;
// User Management: a branch manager staffs their own branch (create/update/activate/
// deactivate/assign-role/remove-role, scoped server-side to their branch and to
// branch-assignable roles) — mirrors UserServiceImpl's own admission, not just COMPANY_ADMIN.
// Delete stays COMPANY_ADMIN-only; UserServiceImpl never grants it to BRANCH_MANAGER.
const USER_READERS = [...ADMINS, AppRole.BRANCH_MANAGER];
const USER_WRITERS = [AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER];
// Ticket Support (Phase 1): every company role raises/reads tickets, plus SUPER_ADMIN's
// own cross-tenant view. Categories are the global catalogue — SUPER_ADMIN only, mirrors
// TicketCategoryServiceImpl's own gate.
const SUPPORT_READERS = [
  AppRole.SUPER_ADMIN, AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER, AppRole.HUB_MANAGER,
  AppRole.BOOKING_OPERATOR, AppRole.DELIVERY_OPERATOR, AppRole.ACCOUNTS, AppRole.FINANCE_USER,
  AppRole.CUSTOMER_SERVICE, AppRole.VIEWER
];
const SUPPORT_ADMIN = [AppRole.SUPER_ADMIN];
// Follow-up Management: branch operational tasks — every company role (no SUPER_ADMIN,
// unlike Ticket Support: a follow-up has no cross-tenant view at all).
const FOLLOW_UP_READERS = [
  AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER, AppRole.HUB_MANAGER, AppRole.BOOKING_OPERATOR,
  AppRole.DELIVERY_OPERATOR, AppRole.ACCOUNTS, AppRole.FINANCE_USER, AppRole.CUSTOMER_SERVICE, AppRole.VIEWER
];
// Communication Center: dashboard/logs readable by whoever runs a branch; Channel
// Settings/Templates are COMPANY_ADMIN's own call — mirrors the backend's own
// @PreAuthorize split (no SUPER_ADMIN either way, this is company-owned config).
const COMMUNICATION_READERS = [AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER];
const COMMUNICATION_ADMIN = [AppRole.COMPANY_ADMIN];

/**
 * Lazy, guarded routes. The admin shell wraps every authenticated feature; each feature
 * is code-split. `data.roles` drives permission-based routing via roleGuard.
 */
export const routes: Routes = [
  // Public (no-login) info/policy pages linked from the login screen — reachable without
  // authentication so business/policy info can be reviewed without an account (Razorpay
  // business verification and general visitors alike). One shared PublicPage component,
  // content keyed by pageKey — see features/public/public-page.content.ts.
  ...(['home', 'services', 'pricing', 'about', 'contact', 'terms', 'privacy',
       'refund-policy', 'shipping-policy', 'payment-policy'] as const).map((pageKey) => ({
    path: pageKey,
    title: `Amazing Logistics — ${pageKey}`,
    data: { pageKey },
    loadComponent: () => import('@features/public/public-page').then((m) => m.PublicPage)
  })),
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('@layouts/auth-layout/auth-layout').then((m) => m.AuthLayout),
    children: [
      { path: '', loadComponent: () => import('@features/auth/login').then((m) => m.Login) }
    ]
  },
  {
    path: 'forgot-password',
    canActivate: [guestGuard],
    loadComponent: () => import('@layouts/auth-layout/auth-layout').then((m) => m.AuthLayout),
    children: [
      { path: '', loadComponent: () => import('@features/auth/forgot-password').then((m) => m.ForgotPassword) }
    ]
  },
  {
    path: 'reset-password',
    canActivate: [guestGuard],
    loadComponent: () => import('@layouts/auth-layout/auth-layout').then((m) => m.AuthLayout),
    children: [
      { path: '', loadComponent: () => import('@features/auth/reset-password').then((m) => m.ResetPassword) }
    ]
  },
  {
    path: 'unauthorized',
    loadComponent: () => import('@layouts/auth-layout/auth-layout').then((m) => m.AuthLayout),
    children: [
      { path: '', loadComponent: () => import('@features/auth/unauthorized').then((m) => m.Unauthorized) }
    ]
  },
  {
    path: 'session-expired',
    loadComponent: () => import('@layouts/auth-layout/auth-layout').then((m) => m.AuthLayout),
    children: [
      { path: '', loadComponent: () => import('@features/auth/session-expired').then((m) => m.SessionExpired) }
    ]
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('@layouts/admin-layout/admin-layout').then((m) => m.AdminLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard', title: 'Dashboard',
        loadComponent: () => import('@features/dashboard/dashboard').then((m) => m.Dashboard)
      },
      // The platform console. Everything here is SUPER_ADMIN's; nothing here is a
      // company's own operational work.
      {
        path: 'platform/dashboard', title: 'Platform Dashboard', canActivate: [roleGuard],
        data: { roles: [AppRole.SUPER_ADMIN] },
        loadComponent: () => import('@features/platform/platform-dashboard').then((m) => m.PlatformDashboardPage)
      },
      {
        path: 'platform/users', title: 'Platform Operators', canActivate: [roleGuard],
        data: { roles: [AppRole.SUPER_ADMIN] },
        loadComponent: () => import('@features/platform/super-admin-list').then((m) => m.SuperAdminListPage)
      },
      {
        path: 'companies', title: 'Companies', canActivate: [roleGuard],
        data: { roles: [AppRole.SUPER_ADMIN] },
        loadComponent: () => import('@features/company/company-list').then((m) => m.CompanyList)
      },
      {
        // Before ':id', or the literal is swallowed by the parameter — the same ordering
        // the permissions and masters routes needed.
        path: 'companies/new', title: 'New Company', canActivate: [roleGuard],
        data: { roles: [AppRole.SUPER_ADMIN] },
        loadComponent: () => import('@features/company/company-create').then((m) => m.CompanyCreatePage)
      },
      {
        path: 'companies/:id', title: 'Company Profile', canActivate: [roleGuard],
        data: { roles: [AppRole.SUPER_ADMIN] },
        loadComponent: () => import('@features/company/company-profile').then((m) => m.CompanyProfilePage)
      },
      {
        path: 'companies/:id/edit', title: 'Edit Company', canActivate: [roleGuard],
        data: { roles: [AppRole.SUPER_ADMIN] },
        loadComponent: () => import('@features/company/company-profile-edit').then((m) => m.CompanyProfileEditPage)
      },
      {
        path: 'subscription-plans', title: 'Subscription Plans', canActivate: [roleGuard],
        data: { roles: [AppRole.SUPER_ADMIN] },
        loadComponent: () => import('@features/subscription-plans/plan-list').then((m) => m.PlanList)
      },
      {
        // Before ':id', or the literal is swallowed by the parameter.
        path: 'subscription-plans/new', title: 'New Plan', canActivate: [roleGuard],
        data: { roles: [AppRole.SUPER_ADMIN] },
        loadComponent: () => import('@features/subscription-plans/plan-create').then((m) => m.PlanCreate)
      },
      {
        path: 'subscription-plans/:id', title: 'Subscription Plan', canActivate: [roleGuard],
        data: { roles: [AppRole.SUPER_ADMIN] },
        loadComponent: () => import('@features/subscription-plans/plan-view').then((m) => m.PlanView)
      },
      {
        path: 'subscription-plans/:id/edit', title: 'Edit Plan', canActivate: [roleGuard],
        data: { roles: [AppRole.SUPER_ADMIN] },
        loadComponent: () => import('@features/subscription-plans/plan-edit').then((m) => m.PlanEdit)
      },
      {
        path: 'branches', title: 'Branches', canActivate: [roleGuard], data: { roles: COMPANY_ONLY },
        loadComponent: () => import('@features/branch/branch-list').then((m) => m.BranchList)
      },
      {
        path: 'branches/new', title: 'New Branch', canActivate: [roleGuard], data: { roles: [AppRole.COMPANY_ADMIN] },
        loadComponent: () => import('@features/branch/branch-create').then((m) => m.BranchCreate)
      },
      {
        path: 'branches/:id', title: 'Branch', canActivate: [roleGuard], data: { roles: COMPANY_ONLY },
        loadComponent: () => import('@features/branch/branch-view').then((m) => m.BranchView)
      },
      {
        path: 'branches/:id/edit', title: 'Edit Branch', canActivate: [roleGuard], data: { roles: COMPANY_ONLY },
        loadComponent: () => import('@features/branch/branch-edit').then((m) => m.BranchEdit)
      },
      // Declared before 'masters/:master' (below) so the literal segment is not swallowed
      // by that generic route's parameter.
      {
        path: 'masters/pincode-branch-mapping', title: 'Pincode Branch Mapping',
        canActivate: [roleGuard], data: { roles: COMPANY_ONLY },
        loadComponent: () =>
          import('@features/branch/branch-pincode-mapping').then((m) => m.BranchPincodeMapping)
      },
      // Customers — reusable master data, independent of Shipment Order. Any
      // authenticated company user reads (no roles restriction admits everyone signed
      // in, same as the backend's isAuthenticated() read policy); 'new'/'edit' are
      // declared before ':id' so the literal segment is not swallowed by the parameter.
      {
        path: 'customers', title: 'Customers', canActivate: [roleGuard], data: { roles: CUSTOMER_READERS },
        loadComponent: () => import('@features/customer/customer-list').then((m) => m.CustomerList)
      },
      {
        path: 'customers/new', title: 'New Customer', canActivate: [roleGuard], data: { roles: CUSTOMER_WRITERS },
        loadComponent: () => import('@features/customer/customer-create').then((m) => m.CustomerCreate)
      },
      {
        path: 'customers/:id', title: 'Customer', canActivate: [roleGuard], data: { roles: CUSTOMER_READERS },
        loadComponent: () => import('@features/customer/customer-view').then((m) => m.CustomerView)
      },
      {
        path: 'customers/:id/edit', title: 'Edit Customer', canActivate: [roleGuard], data: { roles: CUSTOMER_WRITERS },
        loadComponent: () => import('@features/customer/customer-edit').then((m) => m.CustomerEdit)
      },
      // Rate Master — company rate cards. Reads (list/view/calculator) are
      // isAuthenticated() on the backend, so no roles restriction admits every signed-in
      // company user, same as Customers; create/update is COMPANY_ADMIN only, mirroring
      // RateServiceImpl's WRITE gate. 'new' and 'calculator' are declared before ':id' so
      // neither literal segment is swallowed by the parameter. The Calculator page now
      // hosts both the Rate and Freight Factor calculators as tabs (the rate list's own
      // quick-lookup dialog, RateCalculatorDialog, is a separate, untouched entry point).
      {
        path: 'rates', title: 'Rate Master', canActivate: [roleGuard], data: { roles: RATE_READERS },
        loadComponent: () => import('@features/rate-master/rate-list').then((m) => m.RateList)
      },
      {
        path: 'rates/new', title: 'New Rate', canActivate: [roleGuard], data: { roles: [AppRole.COMPANY_ADMIN] },
        loadComponent: () => import('@features/rate-master/rate-create').then((m) => m.RateCreate)
      },
      {
        path: 'rates/calculator', title: 'Calculator', canActivate: [roleGuard], data: { roles: RATE_READERS },
        loadComponent: () => import('@features/rate-master/rate-calculator').then((m) => m.RateCalculator)
      },
      {
        path: 'rates/:id', title: 'Rate', canActivate: [roleGuard], data: { roles: RATE_READERS },
        loadComponent: () => import('@features/rate-master/rate-view').then((m) => m.RateView)
      },
      {
        path: 'rates/:id/edit', title: 'Edit Rate', canActivate: [roleGuard], data: { roles: [AppRole.COMPANY_ADMIN] },
        loadComponent: () => import('@features/rate-master/rate-edit').then((m) => m.RateEdit)
      },
      // Address Distance — resolve/list the road distance between two branches.
      // isAuthenticated() on the backend, same posture as Rate Master's reads/calculator.
      {
        path: 'distances', title: 'Address Distance', canActivate: [roleGuard], data: { roles: RATE_READERS },
        loadComponent: () => import('@features/address-distance/address-distance').then((m) => m.AddressDistance)
      },
      // Freight Factor — standalone distance x weight pricing grid, independent of Rate
      // Master. isAuthenticated() reads/calculate on the backend (RATE_READERS reused,
      // same audience); COMPANY_ADMIN-only writes are hidden in-page, not route-guarded,
      // since Add/Edit/Activate live on the same page as the read-only grid.
      {
        path: 'freight-factors', title: 'Freight Factor', canActivate: [roleGuard], data: { roles: RATE_READERS },
        loadComponent: () => import('@features/freight-factor/freight-factor').then((m) => m.FreightFactorPage)
      },
      // District Level Freight — rate setup by From Station + District + weight slab.
      // Rate setup only; reuses RATE_READERS (COMPANY_ADMIN/BRANCH_MANAGER, isAuthenticated()
      // on the backend) for reads, COMPANY_ADMIN-only for writes, mirroring Rate Master's
      // own split. 'new' declared before ':id' so it isn't swallowed by the parameter.
      {
        path: 'district-level-freight', title: 'District Level Freight', canActivate: [roleGuard], data: { roles: RATE_READERS },
        loadComponent: () => import('@features/district-level-freight/district-freight-list').then((m) => m.DistrictFreightList)
      },
      {
        path: 'district-level-freight/new', title: 'New District Level Freight Rate', canActivate: [roleGuard], data: { roles: [AppRole.COMPANY_ADMIN] },
        loadComponent: () => import('@features/district-level-freight/district-freight-create').then((m) => m.DistrictFreightCreate)
      },
      {
        path: 'district-level-freight/:id', title: 'District Level Freight Rate', canActivate: [roleGuard], data: { roles: RATE_READERS },
        loadComponent: () => import('@features/district-level-freight/district-freight-view').then((m) => m.DistrictFreightView)
      },
      {
        path: 'district-level-freight/:id/edit', title: 'Edit District Level Freight Rate', canActivate: [roleGuard], data: { roles: [AppRole.COMPANY_ADMIN] },
        loadComponent: () => import('@features/district-level-freight/district-freight-edit').then((m) => m.DistrictFreightEdit)
      },
      // Vehicles (fleet) — bespoke module, not one of the generic master-data lists;
      // COMPANY_ADMIN/BRANCH_MANAGER, matching VehicleServiceImpl's own gate. Routed
      // create/edit pages, not a dialog — seventeen fields read poorly in a modal
      // (direct user feedback, 0.25.2). 'new' declared before ':id/edit' so the
      // literal segment isn't swallowed by the parameter, same convention Shipment
      // Booking's own routes already use.
      {
        path: 'masters/vehicles', title: 'Vehicles', canActivate: [roleGuard], data: { roles: UPDATERS },
        loadComponent: () => import('@features/manifest/vehicle-list').then((m) => m.VehicleList)
      },
      {
        path: 'masters/vehicles/new', title: 'New Vehicle', canActivate: [roleGuard], data: { roles: UPDATERS },
        loadComponent: () => import('@features/manifest/vehicle-create').then((m) => m.VehicleCreate)
      },
      {
        path: 'masters/vehicles/:id/edit', title: 'Edit Vehicle', canActivate: [roleGuard], data: { roles: UPDATERS },
        loadComponent: () => import('@features/manifest/vehicle-edit').then((m) => m.VehicleEdit)
      },
      // Shipment Booking — the core transaction. Reads (list/view/charges/history/
      // documents) are isAuthenticated() on the backend, so no roles restriction admits
      // every signed-in company user, same as Customers/Rate Master; create/update/
      // cancel/document-upload mirror ShipmentServiceImpl's WRITERS gate. 'new' is
      // declared before ':id' so the literal segment is not swallowed by the parameter.
      {
        path: 'track', title: 'Track', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/shipment-movement/track').then((m) => m.Track)
      },
      {
        path: 'shipments', title: 'Shipments', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/shipment/shipment-list').then((m) => m.ShipmentList)
      },
      {
        path: 'reports/bookings', title: 'Booking Report', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/reports/booking-report').then((m) => m.BookingReport)
      },
      {
        path: 'reports/deliveries', title: 'Delivery Report', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/reports/delivery-report').then((m) => m.DeliveryReport)
      },
      {
        path: 'reports/commissions', title: 'Commission Report', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/reports/commission-report').then((m) => m.CommissionReport)
      },
      {
        path: 'reports/drs', title: 'DRS Report', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/reports/drs-report').then((m) => m.DrsReport)
      },
      {
        path: 'reports/drs/:deliveryUserId/:deliveryBranchId/:runDate', title: 'DRS Detail',
        canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/reports/drs-detail').then((m) => m.DrsDetailPage)
      },
      {
        path: 'reports/thc', title: 'Trip Hire Challan Report', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/reports/thc-report').then((m) => m.ThcReport)
      },
      {
        path: 'reports/branches', title: 'Branch Performance Report', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/reports/branch-report').then((m) => m.BranchReport)
      },
      {
        path: 'reports/finance', title: 'Finance Report', canActivate: [roleGuard], data: { roles: FINANCE_REPORT_READERS },
        loadComponent: () => import('@features/reports/finance-report').then((m) => m.FinanceReport)
      },
      {
        path: 'reports/customers', title: 'Customer Report', canActivate: [roleGuard], data: { roles: CUSTOMER_READERS },
        loadComponent: () => import('@features/reports/customer-report').then((m) => m.CustomerReport)
      },
      {
        path: 'reports/exceptions', title: 'Shipment Exception Report', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/reports/shipment-exception-report').then((m) => m.ShipmentExceptionReport)
      },
      {
        path: 'shipments/new', title: 'New Shipment', canActivate: [roleGuard], data: { roles: SHIPMENT_WRITERS },
        loadComponent: () => import('@features/shipment/shipment-create').then((m) => m.ShipmentCreate)
      },
      {
        path: 'shipments/:id', title: 'Shipment', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/shipment/shipment-view').then((m) => m.ShipmentView)
      },
      {
        path: 'shipments/:id/edit', title: 'Edit Shipment', canActivate: [roleGuard], data: { roles: SHIPMENT_WRITERS },
        loadComponent: () => import('@features/shipment/shipment-edit').then((m) => m.ShipmentEdit)
      },
      {
        path: 'shipments/:id/charges', title: 'Shipment Charges', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/shipment/shipment-charges').then((m) => m.ShipmentCharges)
      },
      {
        path: 'shipments/:id/history', title: 'Shipment History', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/shipment/shipment-history').then((m) => m.ShipmentHistory)
      },
      {
        path: 'shipments/:id/documents', title: 'Shipment Documents', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/shipment/shipment-documents').then((m) => m.ShipmentDocuments)
      },
      {
        path: 'shipments/:id/timeline', title: 'Shipment Timeline', canActivate: [roleGuard], data: { roles: SHIPMENT_READERS },
        loadComponent: () => import('@features/shipment-movement/timeline').then((m) => m.ShipmentTimeline)
      },
      // Shipment Movement — the minimal Manifest module (see MEMORY/modules/shipment-movement.md)
      // plus the five movement steps the brief's own REST API list names.
      {
        path: 'movement/loading-sheet', title: 'Loading Sheet', canActivate: [roleGuard], data: { roles: MOVEMENT_WRITERS },
        loadComponent: () => import('@features/shipment-movement/loading-sheet').then((m) => m.LoadingSheet)
      },
      {
        path: 'movement/trip-hire-challan', title: 'Trip Hire Challan (THC)', canActivate: [roleGuard], data: { roles: MOVEMENT_WRITERS },
        loadComponent: () => import('@features/shipment-movement/trip-hire-challan').then((m) => m.TripHireChallan)
      },
      {
        path: 'movement/in-scan', title: 'In Scan', canActivate: [roleGuard], data: { roles: MOVEMENT_WRITERS },
        loadComponent: () => import('@features/shipment-movement/in-scan').then((m) => m.InScan)
      },
      {
        path: 'movement/pending-delivery', title: 'Pending Delivery', canActivate: [roleGuard], data: { roles: MOVEMENT_WRITERS },
        loadComponent: () => import('@features/shipment-movement/pending-delivery').then((m) => m.PendingDelivery)
      },
      {
        path: 'movement/out-for-delivery', title: 'DRS', canActivate: [roleGuard], data: { roles: MOVEMENT_WRITERS },
        loadComponent: () => import('@features/shipment-movement/out-for-delivery').then((m) => m.OutForDelivery)
      },
      {
        path: 'movement/delivery', title: 'Delivery', canActivate: [roleGuard], data: { roles: MOVEMENT_WRITERS },
        loadComponent: () => import('@features/shipment-movement/delivery').then((m) => m.Delivery)
      },
      // POD Auto Verification's Manual Review screen — reviewer tier only (COMPANY_ADMIN/
      // BRANCH_MANAGER), mirrors PodVerificationServiceImpl's REVIEWERS @PreAuthorize
      // exactly (narrower than MOVEMENT_WRITERS, which also admits the booking/delivery
      // desks who may capture a POD but not decide one).
      {
        path: 'movement/pod-review', title: 'POD Review', canActivate: [roleGuard], data: { roles: UPDATERS },
        loadComponent: () => import('@features/shipment-movement/pod-review').then((m) => m.PodReview)
      },
      // Master data — one set of screens for all twelve lists, selected by :master.
      // 'new' is declared before ':id' so the literal segment is not swallowed by the
      // parameter, the same ordering the permissions module needed for 'assign'.
      {
        path: 'masters', pathMatch: 'full', redirectTo: 'masters/countries'
      },
      {
        path: 'masters/:master', title: 'Master Data', canActivate: [roleGuard], data: { roles: MASTERS_READERS },
        loadComponent: () => import('@features/masters/master-list').then((m) => m.MasterList)
      },
      {
        path: 'masters/:master/new', title: 'New Master Record', canActivate: [roleGuard],
        data: { roles: [AppRole.COMPANY_ADMIN] },
        loadComponent: () => import('@features/masters/master-form-page').then((m) => m.MasterFormPage)
      },
      {
        path: 'masters/:master/:id', title: 'Master Record', canActivate: [roleGuard], data: { roles: MASTERS_READERS },
        loadComponent: () => import('@features/masters/master-view').then((m) => m.MasterView)
      },
      {
        path: 'masters/:master/:id/edit', title: 'Edit Master Record', canActivate: [roleGuard],
        data: { roles: [AppRole.COMPANY_ADMIN] },
        loadComponent: () => import('@features/masters/master-form-page').then((m) => m.MasterFormPage)
      },
      // hub module not built yet
      // {
      //   path: 'hubs', title: 'Hubs', canActivate: [roleGuard], data: { roles: MANAGERS },
      //   loadComponent: () => import('@features/hub/hub-list').then((m) => m.HubList)
      // },
      {
        path: 'finance/branch-wallet', title: 'Branch Wallet', canActivate: [roleGuard], data: { roles: WALLET_VIEWERS },
        loadComponent: () => import('@features/branch-wallet/wallet-dashboard').then((m) => m.WalletDashboard)
      },
      {
        path: 'finance/branch-wallet/transactions', title: 'Wallet Transactions', canActivate: [roleGuard], data: { roles: WALLET_VIEWERS },
        loadComponent: () => import('@features/branch-wallet/wallet-transactions').then((m) => m.WalletTransactions)
      },
      {
        path: 'finance/branch-wallet/recharge', title: 'Recharge Wallet', canActivate: [roleGuard], data: { roles: WALLET_RECHARGERS },
        loadComponent: () => import('@features/branch-wallet/wallet-recharge').then((m) => m.WalletRecharge)
      },
      {
        path: 'finance/branch-wallet/topup-requests', title: 'Top-up Requests', canActivate: [roleGuard], data: { roles: WALLET_VIEWERS },
        loadComponent: () => import('@features/branch-wallet/topup-requests').then((m) => m.TopupRequests)
      },
      // Follow-up Management. 'new' before ':id', and ':id/edit' before the bare ':id'
      // detail route is unaffected since it's a distinct trailing segment — same
      // convention every other module here uses.
      {
        path: 'follow-ups', title: 'Follow-ups', canActivate: [roleGuard], data: { roles: FOLLOW_UP_READERS },
        loadComponent: () => import('@features/follow-up/follow-up-list').then((m) => m.FollowUpList)
      },
      {
        path: 'follow-ups/new', title: 'Create Follow-up', canActivate: [roleGuard], data: { roles: FOLLOW_UP_READERS },
        loadComponent: () => import('@features/follow-up/follow-up-create').then((m) => m.FollowUpCreate)
      },
      {
        path: 'follow-ups/:id/edit', title: 'Edit Follow-up', canActivate: [roleGuard], data: { roles: FOLLOW_UP_READERS },
        loadComponent: () => import('@features/follow-up/follow-up-edit').then((m) => m.FollowUpEdit)
      },
      {
        path: 'follow-ups/:id', title: 'Follow-up', canActivate: [roleGuard], data: { roles: FOLLOW_UP_READERS },
        loadComponent: () => import('@features/follow-up/follow-up-detail').then((m) => m.FollowUpDetailPage)
      },
      // Ticket Support (Phase 1). 'new' before ':id' so the literal segment is not
      // swallowed by the parameter, same convention every other module here uses.
      {
        path: 'support/tickets', title: 'Tickets', canActivate: [roleGuard], data: { roles: SUPPORT_READERS },
        loadComponent: () => import('@features/support/ticket-list').then((m) => m.TicketList)
      },
      {
        path: 'support/tickets/new', title: 'Raise Ticket', canActivate: [roleGuard], data: { roles: SUPPORT_READERS },
        loadComponent: () => import('@features/support/ticket-create').then((m) => m.TicketCreate)
      },
      {
        path: 'support/tickets/:id', title: 'Ticket', canActivate: [roleGuard], data: { roles: SUPPORT_READERS },
        loadComponent: () => import('@features/support/ticket-detail').then((m) => m.TicketDetailPage)
      },
      {
        path: 'support/dashboard', title: 'Support Dashboard', canActivate: [roleGuard], data: { roles: ADMINS },
        loadComponent: () => import('@features/support/support-dashboard').then((m) => m.SupportDashboard)
      },
      {
        path: 'support/categories', title: 'Ticket Categories', canActivate: [roleGuard], data: { roles: SUPPORT_ADMIN },
        loadComponent: () => import('@features/support/ticket-categories').then((m) => m.TicketCategories)
      },
      {
        path: 'support/sla-rules', title: 'SLA Rules', canActivate: [roleGuard], data: { roles: ADMINS },
        loadComponent: () => import('@features/support/ticket-sla-rules').then((m) => m.TicketSlaRules)
      },
      // Communication Center.
      {
        path: 'communication/dashboard', title: 'Communication Dashboard', canActivate: [roleGuard],
        data: { roles: COMMUNICATION_READERS },
        loadComponent: () => import('@features/communication/communication-dashboard').then((m) => m.CommunicationDashboardPage)
      },
      {
        path: 'communication/settings', title: 'Channel Settings', canActivate: [roleGuard],
        data: { roles: COMMUNICATION_ADMIN },
        loadComponent: () => import('@features/communication/channel-settings').then((m) => m.ChannelSettings)
      },
      {
        path: 'communication/templates', title: 'Notification Templates', canActivate: [roleGuard],
        data: { roles: COMMUNICATION_ADMIN },
        loadComponent: () => import('@features/communication/communication-templates').then((m) => m.CommunicationTemplates)
      },
      {
        path: 'communication/logs', title: 'Communication Logs', canActivate: [roleGuard],
        data: { roles: COMMUNICATION_READERS },
        loadComponent: () => import('@features/communication/communication-logs').then((m) => m.CommunicationLogs)
      },
      {
        path: 'users', title: 'Users', canActivate: [roleGuard], data: { roles: USER_READERS },
        loadComponent: () => import('@features/users/user-list').then((m) => m.UserList)
      },
      {
        path: 'users/new', title: 'New User', canActivate: [roleGuard], data: { roles: USER_WRITERS },
        loadComponent: () => import('@features/users/user-create').then((m) => m.UserCreate)
      },
      {
        path: 'users/:id', title: 'User', canActivate: [roleGuard], data: { roles: USER_READERS },
        loadComponent: () => import('@features/users/user-view').then((m) => m.UserView)
      },
      {
        path: 'users/:id/edit', title: 'Edit User', canActivate: [roleGuard], data: { roles: USER_WRITERS },
        loadComponent: () => import('@features/users/user-edit').then((m) => m.UserEdit)
      },
      {
        path: 'roles', title: 'Roles', canActivate: [roleGuard], data: { roles: ADMINS },
        loadComponent: () => import('@features/roles/role-list').then((m) => m.RoleList)
      },
      {
        path: 'roles/new', title: 'New Role', canActivate: [roleGuard], data: { roles: [AppRole.COMPANY_ADMIN] },
        loadComponent: () => import('@features/roles/role-create').then((m) => m.RoleCreate)
      },
      {
        path: 'roles/:id', title: 'Role', canActivate: [roleGuard], data: { roles: ADMINS },
        loadComponent: () => import('@features/roles/role-view').then((m) => m.RoleView)
      },
      {
        path: 'roles/:id/edit', title: 'Edit Role', canActivate: [roleGuard], data: { roles: [AppRole.COMPANY_ADMIN] },
        loadComponent: () => import('@features/roles/role-edit').then((m) => m.RoleEdit)
      },
      {
        path: 'permissions', title: 'Permissions', canActivate: [roleGuard], data: { roles: ADMINS },
        loadComponent: () => import('@features/permissions/permission-list').then((m) => m.PermissionList)
      },
      {
        path: 'permissions/assign', title: 'Assign Permissions', canActivate: [roleGuard], data: { roles: [AppRole.COMPANY_ADMIN] },
        loadComponent: () => import('@features/permissions/permission-assign').then((m) => m.PermissionAssign)
      },
      {
        path: 'permissions/:id', title: 'Permission', canActivate: [roleGuard], data: { roles: ADMINS },
        loadComponent: () => import('@features/permissions/permission-view').then((m) => m.PermissionView)
      },
      {
        path: 'settings', title: 'Settings', canActivate: [roleGuard], data: { roles: COMPANY_ONLY },
        loadComponent: () => import('@features/settings/settings-page').then((m) => m.SettingsPage)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];

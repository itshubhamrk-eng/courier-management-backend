/** Central registry of backend endpoints, so a path change is one edit. Base is prefixed
 *  by ApiService from environment.apiBaseUrl. */
export const API = {
  auth: {
    login: '/auth/login',
    refresh: '/auth/refresh',
    logout: '/auth/logout',
    me: '/auth/me',
    changePassword: '/auth/change-password',
    forgotPassword: '/auth/forgot-password',
    resetPassword: '/auth/reset-password',
    impersonate: (companyId: string) => `/auth/impersonate/${companyId}`,
    impersonateBranch: (branchId: string) => `/auth/impersonate/branch/${branchId}`
  },
  companies: '/companies',
  users: '/users',
  roles: '/roles',
  permissions: '/permissions',
  branches: '/branches',
  customers: '/customers',
  rates: '/rates',
  shipments: '/shipments',
  /** POD Auto Verification — verify/{id}, verification/{id}, review/{id}. See
   *  MEMORY/modules/pod-verification.md. */
  pod: (shipmentId: string) => `/shipments/${shipmentId}/pod`,
  /** The Pricing Engine's one endpoint — no frontend module of its own, called here for
   *  the booking wizard's live preview. See core/models/shipment.model.ts. */
  pricing: '/pricing',
  /** Shipment Movement's minimal prerequisite (see MEMORY/modules/shipment-movement.md) —
   *  not part of the movement brief's own REST list, which assumes a manifest exists. */
  manifests: '/manifests',
  vehicles: '/vehicles',
  /** Loading Sheet, Trip Hire Challan (THC), In Scan, Out For Delivery, Deliver. */
  shipmentMovement: '/shipment-movement',
  branchWallet: '/branch-wallet',
  hubs: '/hubs',
  companySettings: '/company-settings',
  companyRazorpayConfig: '/company-razorpay-config',
  subscriptionPlans: '/subscription-plans',
  /** The platform console: platform dashboard and platform-operator accounts. */
  superAdmin: '/super-admin',
  /** The geography every company shares. SUPER_ADMIN writes, anyone signed in reads. */
  globalMasters: '/global-masters',
  dashboard: '/dashboard/summary',
  distances: '/distances',
  freightFactors: '/freight-factors',
  /** Ticket Support — lifecycle, conversation, categories, SLA rules. See MEMORY. */
  supportTickets: '/support/tickets',
  supportCategories: '/support/categories',
  supportSlaRules: '/support/sla-rules',
  notifications: '/notifications',
  /** Follow-up Management — branch operational tasks. See MEMORY/modules/follow-up.md. */
  followUps: '/follow-ups',
  /** E-Way Bill Management — standalone CRUD/lifecycle. Shipment Booking itself carries
   *  E-Way Bill data inline (see shipments); this is for managing one afterward. See
   *  MEMORY/modules/eway-bill.md. */
  ewayBills: '/eway-bills'
} as const;

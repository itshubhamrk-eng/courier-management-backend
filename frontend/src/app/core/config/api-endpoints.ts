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
    resetPassword: '/auth/reset-password'
  },
  companies: '/companies',
  users: '/users',
  roles: '/roles',
  permissions: '/permissions',
  branches: '/branches',
  customers: '/customers',
  rates: '/rates',
  shipments: '/shipments',
  /** The Pricing Engine's one endpoint — no frontend module of its own, called here for
   *  the booking wizard's live preview. See core/models/shipment.model.ts. */
  pricing: '/pricing',
  /** Shipment Movement's minimal prerequisite (see MEMORY/modules/shipment-movement.md) —
   *  not part of the movement brief's own REST list, which assumes a manifest exists. */
  manifests: '/manifests',
  vehicles: '/vehicles',
  /** Out Scan, Dispatch, In Scan, Out For Delivery, Deliver. */
  shipmentMovement: '/shipment-movement',
  branchWallet: '/branch-wallet',
  hubs: '/hubs',
  companySettings: '/company-settings',
  subscriptionPlans: '/subscription-plans',
  /** The platform console: platform dashboard and platform-operator accounts. */
  superAdmin: '/super-admin',
  /** The geography every company shares. SUPER_ADMIN writes, anyone signed in reads. */
  globalMasters: '/global-masters',
  dashboard: '/dashboard/summary'
} as const;

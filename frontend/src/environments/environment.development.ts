// Dev config. `ng serve` proxies /api to the backend on :8081 (see proxy.conf.json),
// so a relative base keeps cookies/headers same-origin and avoids CORS.
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
  appName: 'Courier SaaS (dev)',
  version: '0.1.0',
  envLabel: 'development',
  accessTokenSkewSeconds: 30
};

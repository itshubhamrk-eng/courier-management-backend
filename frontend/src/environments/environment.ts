// Production config. The dev build swaps in environment.development.ts.
export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
  appName: 'Courier SaaS',
  version: '0.1.0',
  envLabel: 'production',
  accessTokenSkewSeconds: 30,
  idleTimeoutMinutes: 30
};

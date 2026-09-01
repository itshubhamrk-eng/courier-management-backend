/** Hostname → company code, for auto-filling login's company code field.
 *  Add an entry per company domain as they're provisioned. */
export const COMPANY_DOMAIN_MAP: Record<string, string> = {
  'vendor.amazinglpl.com': 'AMAZING_LOGISTICS'
};

/** Looks up the current hostname; undefined if no company owns this domain. */
export function companyCodeForHostname(hostname: string): string | undefined {
  return COMPANY_DOMAIN_MAP[hostname];
}

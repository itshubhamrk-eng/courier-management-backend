import { JwtClaims } from '../models/auth.model';

/** Decode a JWT payload without verifying — only for local UX (expiry, roles display). */
export function decodeJwt(token: string): JwtClaims | null {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(decodeURIComponent(escape(json))) as JwtClaims;
  } catch {
    return null;
  }
}

/** True when the token expires within `skewSeconds` (treated as already stale). */
export function isExpired(token: string, skewSeconds = 30): boolean {
  const claims = decodeJwt(token);
  if (!claims?.exp) return true;
  return claims.exp * 1000 <= Date.now() + skewSeconds * 1000;
}

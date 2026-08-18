import { Injectable } from '@angular/core';
import { storage } from '../utils/storage.util';
import { isExpired } from '../utils/jwt.util';
import { environment } from '@env/environment';

const ACCESS = 'cs.access';
const REFRESH = 'cs.refresh';
const COMPANY = 'cs.company';
/** Backup of the real (non-impersonating) session's tokens while a SUPER_ADMIN
 *  "login as company" session is active — see {@link stash}/{@link restoreStash}. */
const STASH_ACCESS = 'cs.stash.access';
const STASH_REFRESH = 'cs.stash.refresh';

/** Token store. The only thing that knows where tokens live. */
@Injectable({ providedIn: 'root' })
export class TokenService {
  get accessToken(): string | null { return storage.get(ACCESS); }
  get refreshToken(): string | null { return storage.get(REFRESH); }
  /** Impersonated company for a platform admin; sent as X-Company-ID when present. */
  get companyId(): string | null { return storage.get(COMPANY); }
  /** True while the real session is stashed under a "login as company" session. */
  get isImpersonating(): boolean { return storage.get(STASH_ACCESS) !== null; }

  setTokens(access: string, refresh: string): void {
    storage.set(ACCESS, access);
    storage.set(REFRESH, refresh);
  }
  setCompany(companyId: string | null): void {
    companyId ? storage.set(COMPANY, companyId) : storage.remove(COMPANY);
  }
  clear(): void {
    storage.remove(ACCESS);
    storage.remove(REFRESH);
    storage.remove(COMPANY);
    storage.remove(STASH_ACCESS);
    storage.remove(STASH_REFRESH);
  }

  hasValidAccess(): boolean {
    const t = this.accessToken;
    return !!t && !isExpired(t, environment.accessTokenSkewSeconds);
  }

  /**
   * Backs up the current (real) access+refresh tokens, then swaps in a "login as
   * company" access token with no refresh token — that session hard-expires rather
   * than being silently extendable via the stashed refresh token. See
   * {@code JwtTokenProvider#generateImpersonationAccessToken} on the backend.
   */
  beginImpersonation(impersonationAccessToken: string): void {
    const access = this.accessToken;
    const refresh = this.refreshToken;
    if (access) storage.set(STASH_ACCESS, access);
    if (refresh) storage.set(STASH_REFRESH, refresh);
    storage.set(ACCESS, impersonationAccessToken);
    storage.remove(REFRESH);
  }

  /** Restores the stashed real session. Returns false when nothing was stashed. */
  restoreStash(): boolean {
    const access = storage.get(STASH_ACCESS);
    const refresh = storage.get(STASH_REFRESH);
    storage.remove(STASH_ACCESS);
    storage.remove(STASH_REFRESH);
    if (!access) return false;
    storage.set(ACCESS, access);
    if (refresh) storage.set(REFRESH, refresh); else storage.remove(REFRESH);
    return true;
  }
}

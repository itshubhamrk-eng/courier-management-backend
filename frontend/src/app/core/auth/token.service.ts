import { Injectable } from '@angular/core';
import { storage } from '../utils/storage.util';
import { isExpired } from '../utils/jwt.util';
import { environment } from '@env/environment';

const ACCESS = 'cs.access';
const REFRESH = 'cs.refresh';
const COMPANY = 'cs.company';
/** The signed-in user's display name. The JWT itself carries no name claim (only
 *  `email`, same reasoning as `cnm`/`clogo`), so a hard reload — which rebuilds the
 *  session from the token alone — would otherwise fall back to showing the email
 *  where a name belongs. Stashed alongside the tokens for the same reason. */
const DISPLAY_NAME = 'cs.dname';
/** Backup of the real (non-impersonating) session's tokens while a SUPER_ADMIN
 *  "login as company" session is active — see {@link stash}/{@link restoreStash}. */
const STASH_ACCESS = 'cs.stash.access';
const STASH_REFRESH = 'cs.stash.refresh';
const STASH_DISPLAY_NAME = 'cs.stash.dname';

/** Token store. The only thing that knows where tokens live. */
@Injectable({ providedIn: 'root' })
export class TokenService {
  get accessToken(): string | null { return storage.get(ACCESS); }
  get refreshToken(): string | null { return storage.get(REFRESH); }
  /** Impersonated company for a platform admin; sent as X-Company-ID when present. */
  get companyId(): string | null { return storage.get(COMPANY); }
  get displayName(): string | null { return storage.get(DISPLAY_NAME); }
  /** True while the real session is stashed under a "login as company" session. */
  get isImpersonating(): boolean { return storage.get(STASH_ACCESS) !== null; }

  setTokens(access: string, refresh: string): void {
    storage.set(ACCESS, access);
    storage.set(REFRESH, refresh);
  }
  setCompany(companyId: string | null): void {
    companyId ? storage.set(COMPANY, companyId) : storage.remove(COMPANY);
  }
  setDisplayName(name: string | null): void {
    name ? storage.set(DISPLAY_NAME, name) : storage.remove(DISPLAY_NAME);
  }
  clear(): void {
    storage.remove(ACCESS);
    storage.remove(REFRESH);
    storage.remove(COMPANY);
    storage.remove(DISPLAY_NAME);
    storage.remove(STASH_ACCESS);
    storage.remove(STASH_REFRESH);
    storage.remove(STASH_DISPLAY_NAME);
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
  beginImpersonation(impersonationAccessToken: string, displayName?: string | null): void {
    const access = this.accessToken;
    const refresh = this.refreshToken;
    if (access) storage.set(STASH_ACCESS, access);
    if (refresh) storage.set(STASH_REFRESH, refresh);
    const currentName = this.displayName;
    if (currentName) storage.set(STASH_DISPLAY_NAME, currentName);
    storage.set(ACCESS, impersonationAccessToken);
    storage.remove(REFRESH);
    this.setDisplayName(displayName ?? null);
  }

  /** Restores the stashed real session. Returns false when nothing was stashed. */
  restoreStash(): boolean {
    const access = storage.get(STASH_ACCESS);
    const refresh = storage.get(STASH_REFRESH);
    const name = storage.get(STASH_DISPLAY_NAME);
    storage.remove(STASH_ACCESS);
    storage.remove(STASH_REFRESH);
    storage.remove(STASH_DISPLAY_NAME);
    if (!access) return false;
    storage.set(ACCESS, access);
    if (refresh) storage.set(REFRESH, refresh); else storage.remove(REFRESH);
    this.setDisplayName(name);
    return true;
  }
}

import { Injectable, computed, inject, signal } from '@angular/core';
import { storage } from '../utils/storage.util';
import { decodeJwt } from '../utils/jwt.util';
import { TokenService } from './token.service';
import { LoginResponse } from '../models/auth.model';

const SESSION_ID = 'cs.sid';

/**
 * Session metadata around the tokens: the server session id and the access-token
 * expiry, exposed as signals. {@link TokenService} owns *where* tokens live; this owns
 * the *lifecycle* facts the UI reacts to (which session, is it still live). Kept apart
 * so a "sign out everywhere" or an idle-timeout feature has one place to grow.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly tokens = inject(TokenService);

  private readonly _sessionId = signal<string | null>(storage.get(SESSION_ID));
  /** Epoch millis the access token expires; null when there is no session. */
  private readonly _expiresAt = signal<number | null>(this.readExpiry());

  readonly sessionId = this._sessionId.asReadonly();
  readonly expiresAt = this._expiresAt.asReadonly();
  readonly isLive = computed(() => {
    const exp = this._expiresAt();
    return exp !== null && exp > Date.now();
  });

  /** Opens the session after a successful login/refresh. */
  start(res: LoginResponse): void {
    if (res.sessionId) { storage.set(SESSION_ID, res.sessionId); this._sessionId.set(res.sessionId); }
    this.syncExpiry();
  }

  /** Re-reads expiry from the current access token (after a silent refresh). */
  syncExpiry(): void { this._expiresAt.set(this.readExpiry()); }

  end(): void {
    storage.remove(SESSION_ID);
    this._sessionId.set(null);
    this._expiresAt.set(null);
  }

  private readExpiry(): number | null {
    const token = this.tokens.accessToken;
    if (!token) return null;
    const claims = decodeJwt(token);
    return claims?.exp ? claims.exp * 1000 : null;
  }
}

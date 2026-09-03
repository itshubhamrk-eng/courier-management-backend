import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ApiService } from '../services/api.service';
import { TokenService } from './token.service';
import { SessionService } from './session.service';
import { API } from '../config/api-endpoints';
import {
  ForgotPasswordRequest, ImpersonationResponse, LoginRequest, LoginResponse, ResetPasswordRequest
} from '../models/auth.model';
import { CurrentUser } from '../models/user.model';
import { decodeJwt } from '../utils/jwt.util';

/**
 * Session state, exposed as signals so the whole UI reacts to sign-in/out without a
 * store library. The roles/company come from the verified access token, hydrated on
 * bootstrap from storage so a refresh keeps the session.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly tokens = inject(TokenService);
  private readonly session = inject(SessionService);

  private readonly _user = signal<CurrentUser | null>(this.hydrate());
  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null && this.tokens.hasValidAccess());
  readonly roles = computed(() => this._user()?.roles ?? []);
  readonly permissions = computed(() => this._user()?.permissions ?? []);
  readonly displayName = computed(() => this._user()?.displayName ?? '');
  readonly companyId = computed(() => this._user()?.companyId ?? null);
  readonly companyName = computed(() => this._user()?.companyName ?? null);
  readonly companyLogo = computed(() => this._user()?.companyLogo ?? null);
  /** The SUPER_ADMIN who opened this session, while impersonating; null otherwise. */
  readonly impersonatedBy = computed(() => this._user()?.impersonatedBy ?? null);
  readonly isImpersonating = computed(() => this.impersonatedBy() !== null);

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.api.post<LoginResponse>(API.auth.login, request).pipe(
      tap((res) => this.applySession(res))
    );
  }

  /** Starts a password reset. Always resolves 200 — the message never reveals if the account exists. */
  forgotPassword(request: ForgotPasswordRequest): Observable<void> {
    return this.api.post<void>(API.auth.forgotPassword, request);
  }

  /** Completes a reset with the emailed token. */
  resetPassword(request: ResetPasswordRequest): Observable<void> {
    return this.api.post<void>(API.auth.resetPassword, request);
  }

  /** Re-read /auth/me — reflects role changes without a fresh login. */
  refreshProfile(): Observable<CurrentUser> {
    return this.api.get<CurrentUser>(API.auth.me).pipe(tap((u) => {
      this.tokens.setDisplayName(u.displayName);
      this._user.set(u);
    }));
  }

  logout(): Observable<void> {
    const refreshToken = this.tokens.refreshToken;
    const done = this.api.post<void>(API.auth.logout, { refreshToken, allDevices: false });
    return done.pipe(tap({ next: () => this.clearSession(), error: () => this.clearSession() }));
  }

  clearSession(): void {
    this.tokens.clear();
    this.session.end();
    this._user.set(null);
  }

  /**
   * SUPER_ADMIN only: opens a short-lived (15 minute), company-scoped session acting
   * as that company's own COMPANY_ADMIN. The real session is stashed, not discarded —
   * see {@link exitImpersonation}.
   */
  impersonateCompany(companyId: string, password: string): Observable<ImpersonationResponse> {
    return this.api.post<ImpersonationResponse>(API.auth.impersonate(companyId), { password }).pipe(
      tap((res) => {
        this.tokens.beginImpersonation(res.accessToken, res.displayName ?? res.email);
        this.session.syncExpiry();
        this._user.set(this.hydrate());
      })
    );
  }

  /**
   * COMPANY_ADMIN only: opens a short-lived (15 minute), branch-scoped session acting
   * as that branch's own BRANCH_MANAGER — no password needed, unlike
   * {@link impersonateCompany}, since the caller is already the company's own admin
   * acting inside their own company. The real session is stashed, not discarded —
   * see {@link exitImpersonation}.
   */
  impersonateBranch(branchId: string): Observable<ImpersonationResponse> {
    return this.api.post<ImpersonationResponse>(API.auth.impersonateBranch(branchId), {}).pipe(
      tap((res) => {
        this.tokens.beginImpersonation(res.accessToken, res.displayName ?? res.email);
        this.session.syncExpiry();
        this._user.set(this.hydrate());
      })
    );
  }

  /** Restores the real (pre-impersonation) session. A no-op if not impersonating. */
  exitImpersonation(): void {
    if (!this.tokens.restoreStash()) return;
    this.session.syncExpiry();
    this._user.set(this.hydrate());
  }

  hasAnyRole(roles: string[]): boolean {
    if (roles.length === 0) return true;
    const held = new Set(this.roles());
    return roles.some((r) => held.has(r));
  }

  private applySession(res: LoginResponse): void {
    this.tokens.setTokens(res.accessToken, res.refreshToken);
    this.tokens.setDisplayName(res.displayName ?? res.email);
    this.session.start(res);
    const claims = decodeJwt(res.accessToken);
    this._user.set({
      userId: res.userId, companyId: res.companyId, email: res.email,
      displayName: res.displayName, roles: res.roles,
      permissions: res.permissions ?? claims?.permissions ?? [],
      branchId: res.branchId ?? claims?.bid ?? null, hubId: res.hubId ?? claims?.hid ?? null,
      companyName: res.companyName ?? claims?.cnm ?? null,
      companyLogo: res.companyLogo ?? claims?.clogo ?? null
    });
  }

  /**
   * Rebuilds the session from the stored access token alone (no network round trip) —
   * `branchId`/`hubId` come from the token's own `bid`/`hid` claims, not from a login
   * response that only exists at the moment of signing in. Without this, a page reload
   * (as opposed to client-side Angular routing, which keeps `applySession`'s in-memory
   * signal) silently lost both fields — the "Continue always says no branch assigned
   * after a refresh" bug `MEMORY/modules/shipment-booking.md` documents.
   */
  private hydrate(): CurrentUser | null {
    const token = this.tokens.accessToken;
    if (!token || !this.tokens.hasValidAccess()) return null;
    const c = decodeJwt(token);
    if (!c) return null;
    return {
      userId: c.sub, companyId: c.cid ?? c.tid ?? null, email: c.email,
      displayName: this.tokens.displayName ?? c.email, roles: c.roles ?? [], permissions: c.permissions ?? [],
      branchId: c.bid ?? null, hubId: c.hid ?? null,
      companyName: c.cnm ?? null, companyLogo: c.clogo ?? null,
      impersonatedBy: c.imp && c.impByEmail ? { userId: c.impBy, email: c.impByEmail } : null
    };
  }
}

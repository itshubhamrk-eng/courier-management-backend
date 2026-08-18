/** Mirrors the backend LoginRequest / LoginResponse (auth module). */
export interface LoginRequest {
  email: string;
  password: string;
  companyCode?: string;   // company code; resolved by CompanyDirectory
  companyId?: string;     // alternative to the code
  rememberMe?: boolean;
  deviceId?: string;
  deviceName?: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;       // "Bearer"
  expiresIn: number;       // seconds
  refreshExpiresIn: number;
  sessionId: string;
  userId: string;
  companyId: string | null;
  email: string;
  displayName: string;
  roles: string[];
  // Present once the backend authorises on permissions; consumed as-is, never mocked.
  permissions?: string[];
  branchId?: string | null;
  hubId?: string | null;
  companyName?: string | null;
  companyLogo?: string | null;
}

export interface RefreshRequest { refreshToken: string; }

/** POST /auth/impersonate/{companyId} — step-up confirmation (SUPER_ADMIN only). */
export interface ImpersonateRequest { password: string; }

/** No refresh token — this session hard-expires rather than being silently extendable. */
export interface ImpersonationResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  companyId: string;
  email: string;
  displayName: string;
  roles: string[];
  companyName?: string | null;
  companyLogo?: string | null;
  impersonatorId: string;
  impersonatorEmail: string;
}

/** POST /auth/forgot-password — always 200 to avoid account enumeration. */
export interface ForgotPasswordRequest { companyId: string; email: string; }

/** POST /auth/reset-password — consumes the emailed token. */
export interface ResetPasswordRequest { token: string; newPassword: string; }

/** Decoded access-token claims (sub/cid/roles/typ) — read locally, never trusted for authz. */
export interface JwtClaims {
  sub: string;
  /** The company binding. `tid` is the pre-rename spelling, still issued by nothing but
   *  still present in refresh-era tokens for up to seven days after the deploy. */
  cid?: string;
  tid?: string;
  email: string;
  roles: string[];
  permissions?: string[];
  /** The caller's own branch/hub, if staffed at one — see JwtTokenProvider's claim layout. */
  bid?: string;
  hid?: string;
  /** Company brand — display only, carried so a hard reload doesn't lose it (same reason as bid/hid). */
  cnm?: string;
  clogo?: string;
  /** Set only on a SUPER_ADMIN "login as company" token — display/audit only, never
   *  trusted for authorisation (roles/cid still carry the real grant). */
  imp?: boolean;
  impBy?: string;
  impByEmail?: string;
  typ: string;
  exp: number;
  iat: number;
  jti: string;
}

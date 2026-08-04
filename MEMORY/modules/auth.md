# Module: auth

**Status:** IN PROGRESS → COMPLETE (Phase 2, v0.2.0).
**Package:** `com.courier.modules.auth`
**Depends on:** `shared`. Deliberately **not** on `modules/company` — see *Company resolution*.

## Purpose

Identity and access: company-scoped user accounts, credential verification, JWT
issuance and rotation, session/device management, password lifecycle and email
verification.

## What already exists in `shared` (do not rebuild)

| Component | Location | Note |
|---|---|---|
| `JwtTokenProvider` | `shared/security` | Generates + validates access/refresh tokens |
| `JwtAuthenticationFilter` | `shared/security` | Builds principal from claims, no DB hit |
| `AuthenticatedUser` | `shared/security` | Principal record |
| `Roles` | `shared/security` | Role name constants |
| `SecurityConfig` | `shared/config` | Stateless chain, public route allowlist, 401/403 handlers |
| `AuditService` | `shared/audit` | Async, `REQUIRES_NEW` audit writer |

This module supplies the **user store** and the **use cases** that mint tokens
through the existing provider.

---

## Two decisions that changed this document (2026-07-22)

### 1. Company resolution — `companyId`, not `companyCode`

The original spec said *"resolve the company by `companyCode` → must be ACTIVE"*. That
requires the `companies` table, which belongs to `modules/company` (Phase 1, not yet
built). Rather than pre-empt that migration, auth depends on a **port**:

```java
public interface CompanyDirectoryPort {
    Optional<CompanyRef> findById(UUID companyId);
    Optional<CompanyRef> findByCode(String companyCode);
}
```

- **Now (Phase 2)** — `StandaloneCompanyDirectory` treats any company id that appears
  on a non-deleted user row as existing and active, and cannot resolve slugs.
- **Phase 1/later** — the company module ships a `CompanyDirectory` that reads
  `companies`, enforces `status = ACTIVE`, and resolves slugs. `@ConditionalOnMissingBean`
  on the standalone implementation means it is displaced automatically.

`LoginRequest` therefore carries `companyId` (UUID). When the company module lands,
`companyCode` becomes an accepted alternative — the DTO already reserves the field.

**What this preserves:** `UNIQUE (tenant_id, email)`, and the rule that the company
is bound *before* the user lookup, so the Hibernate company filter applies. Cross-company
login remains impossible.

### 2. Token storage — MySQL is the source of truth, Redis is an accelerator

The original spec kept refresh/reset/verification tokens in Redis only. Device
Management and Session Management need them queryable and durable, so:

| Store | Holds | Why |
|---|---|---|
| MySQL | `refresh_tokens`, `user_sessions`, `login_history`, `password_reset_tokens`, `email_verification_tokens` | Queryable, survives a Redis flush, supports "list my devices" and reuse detection |
| Redis | `auth:denylist:{jti}` only | O(1) check on the hot path of every authenticated request |

**Redis is not a hard dependency.** If Redis is unreachable the denylist degrades:
`RedisTokenRevocationChecker` logs at ERROR and returns "not revoked". This is
*fail-open by design* — the alternative is a total authentication outage when the
cache blinks. Blast radius is bounded by the 15-minute access-token TTL, and the
refresh token is revoked in MySQL regardless, so the session cannot be extended.

---

## Domain

```
User (aggregate root, CompanyOwnedEntity)
├── email             unique per company, lowercased on write
├── passwordHash      BCrypt(12), @JsonIgnore, never logged
├── firstName, lastName, phone
├── status            PENDING | ACTIVE | LOCKED | DISABLED
├── roles             Set<Role> (@ElementCollection -> user_roles)
├── emailVerified     boolean + emailVerifiedAt
├── passwordChangedAt for the optional expiry policy
├── lastLoginAt, lastLoginIp
├── failedAttempts    int
└── lockedUntil       Instant
```

`Role`: `PLATFORM_ADMIN`, `TENANT_ADMIN`, `BRANCH_MANAGER`, `OPERATOR`, `DRIVER`,
`CUSTOMER`. `@Enumerated(STRING)`.

`branchId` is **not** on `User` yet — it is a Phase 3 (`company`) concern and is
added by that module's migration. An unused column now would be a placeholder.

```
RefreshToken (CompanyOwnedEntity)      one row per issued refresh token
├── userId, sessionId, familyId
├── jti, tokenHash (SHA-256; the raw token is never stored)
├── expiresAt, revokedAt, revokedReason, replacedByJti
└── ipAddress, userAgent

UserSession (CompanyOwnedEntity)       one row per device/login
├── userId, deviceId, deviceName, deviceType
├── ipAddress, userAgent
├── createdAt, lastSeenAt, expiresAt, revokedAt, revokedReason
└── rememberMe

LoginHistory (CompanyOwnedEntity)      append-only, includes failures
├── userId (null when the email matched nobody), attemptedEmail
├── success, failureReason
├── ipAddress, userAgent, sessionId
└── occurredAt

PasswordResetToken / EmailVerificationToken (CompanyOwnedEntity)
├── userId, tokenHash (SHA-256), expiresAt, consumedAt
└── ipAddress
```

### Deliberate cross-company lookups

Reset and verification tokens are presented by **unauthenticated** callers, so no
company is bound and the Hibernate filter cannot narrow the query. Three repository
methods therefore look up by token hash across companies. Each is named
`findByTokenHash...` , carries a code comment, and is safe because:

- the token is 32 bytes of `SecureRandom`, so the hash is effectively unguessable;
- only the hash is stored, so a database leak does not yield usable tokens;
- the company is bound *from the row that was found*, before anything else happens.

This is the documented exception required by the invariant in `AI_CONTEXT.md`.

---

## Token design

| Token | TTL | Claims | Storage |
|---|---|---|---|
| Access | 15 min | `sub`, `tid`, `email`, `roles`, `typ=access`, `jti` | stateless; `jti` denylisted on logout |
| Refresh | 7 d, or 30 d with Remember Me | `sub`, `tid`, `typ=refresh`, `jti` | `refresh_tokens` row (hash only) |

**Rotation with reuse detection.** Every `/refresh` issues a new pair, marks the
presented row `revokedAt` with `replacedByJti`, and links both to the same
`familyId`. If a token is presented whose row is *already revoked*, that is a
replay: the entire family is revoked, every session for the user is closed, and
`REFRESH_TOKEN_REUSE_DETECTED` is audited. Not optional — it is what makes a
stolen refresh token self-limiting.

**Logout.** Revokes the refresh row and the session, and pushes the access `jti`
onto `auth:denylist:{jti}` with the token's remaining TTL.
`LogoutRequest.allDevices=true` revokes every session and refresh token for the user.

---

## REST API

All responses use the standard `ApiResponse<T>` envelope.

| Method | Path | Auth | Body | Success |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | public | `LoginRequest` | `200` `LoginResponse` |
| `POST` | `/api/v1/auth/logout` | bearer | `LogoutRequest` | `200` |
| `POST` | `/api/v1/auth/refresh` | public | `RefreshTokenRequest` | `200` `LoginResponse` |
| `POST` | `/api/v1/auth/forgot-password` | public | `ForgotPasswordRequest` | `200` always |
| `POST` | `/api/v1/auth/reset-password` | public | `ResetPasswordRequest` | `200` |
| `POST` | `/api/v1/auth/change-password` | bearer | `ChangePasswordRequest` | `200` |
| `POST` | `/api/v1/auth/verify-email` | public | `VerifyEmailRequest` | `200` |
| `GET` | `/api/v1/auth/me` | bearer | — | `200` `CurrentUserResponse` |

**There is no `/register` endpoint.** User creation is company onboarding, which is
Phase 1. Auth authenticates users that already exist. This is why the module can be
built and shipped without the company module.

### Business rules, per endpoint

**`POST /login`**
1. Bind `CompanyContext` from `companyId` after `CompanyDirectoryPort` confirms it exists
   and is active; otherwise `403 COMPANY_INACTIVE`.
2. Redis-independent throttle: `email + IP` attempts are counted in
   `login_history`; more than `app.auth.throttle-max-attempts` failures inside
   `throttle-window` → `429 RATE_LIMIT_EXCEEDED`. This is separate from the
   per-account lock so an attacker cannot cheaply lock a victim out.
3. Load the user by email within the company. **Not found and wrong password return
   the identical `401 INVALID_CREDENTIALS`**, and the BCrypt comparison runs against
   a dummy hash even when the user is absent, so there is no timing oracle.
4. `lockedUntil` in the future → `423 ACCOUNT_LOCKED`. Once it has passed, the lock
   auto-clears on the next successful verification.
5. `status = DISABLED` → `403 ACCOUNT_DISABLED`.
6. Wrong password → increment `failedAttempts`; at `lockout-threshold` (5) set
   `lockedUntil = now + lockout-duration` (15 min), audit `ACCOUNT_LOCKED`.
7. Unverified email → re-issue a verification token (at most one per
   `verification-resend-window`) and return `403 EMAIL_NOT_VERIFIED`.
8. Password older than `password-max-age` (disabled by default) → `403 PASSWORD_EXPIRED`.
9. Success: reset counters, set `lastLoginAt`/`lastLoginIp`, open a `UserSession`,
   issue the token pair, write `LoginHistory`, audit `LOGIN_SUCCESS`.
10. **Session cap** — if the user already has `max-concurrent-sessions` (5) live
    sessions, the least recently seen is revoked. That is the "expire old sessions"
    rule.

**`POST /refresh`** — validates `typ=refresh`, binds the company from `tid`, looks the
row up by hash, applies the rotation/reuse rules above, and touches
`UserSession.lastSeenAt`.

**`POST /forgot-password`** — **always returns 200** with the same message whether or
not the account exists; anything else is a user-enumeration oracle. Issues a
single-use token (hash stored, 15 min TTL) and invalidates any outstanding ones.

**`POST /reset-password`** — consumes the token, applies the password policy, rehashes,
revokes every refresh token and session (a password reset must log out attackers),
and **clears any account lock** — this is the "unlock account" path.

**`POST /change-password`** — requires the current password, forbids reusing it,
applies the policy, then revokes **every** session including the caller's and
denylists the access token in use. Keeping the current device signed in would need
a session id inside the access token, which it does not carry; rather than guess,
the contract is an explicit re-authentication. Clients should redirect to sign-in
on 200.

**`POST /verify-email`** — consumes the token, sets `emailVerified`, and promotes
`PENDING` → `ACTIVE`.

**`GET /me`** — profile of the token's subject, read from the database rather than
from claims, so a disabled or role-changed account is reflected immediately.

---

## Password policy

Configured under `app.auth.password`. Enforced by `PasswordPolicy` on reset and change:

- minimum 10 characters, maximum 128 (BCrypt truncates silently past 72 bytes —
  the max stops a long password from being quietly shortened);
- at least one letter and one digit;
- not equal to, or containing, the local part of the email;
- not in the bundled common-password list;
- must differ from the current password.

Rotation (`password-max-age`) defaults to **disabled**. NIST SP 800-63B advises
against forced periodic rotation; the mechanism exists for customers whose
compliance regime demands it.

---

## Security implementation

| Piece | Class |
|---|---|
| Custom `UserDetails` | `AuthUserDetails` (wraps `User`, exposes `companyId`) |
| `UserDetailsService` | `AuthUserDetailsService` — company-scoped; requires a bound `CompanyContext` |
| `AuthenticationProvider` | `DaoAuthenticationProvider` in `AuthSecurityConfig` |
| `AuthenticationManager` | `ProviderManager` bean, injected into `AuthService` |
| Authentication filter | `shared`'s `JwtAuthenticationFilter` (unchanged in shape) |
| Entry point / denied handler | `shared`'s `SecurityConfig` (unchanged) |
| Revocation check | `AccessTokenRevocationChecker` (SPI in `shared`), implemented here by `RedisTokenRevocationChecker` |

`AuthUserDetailsService.loadUserByUsername` deliberately takes only an email: the
company comes from `CompanyContext`, which the login use case binds first. It throws
if no company is bound rather than searching globally.

### The one `shared` extension

`JwtAuthenticationFilter` gained a revocation check, which `auth.md` always
anticipated. To avoid inverting the dependency (`shared` must not import `modules`),
`shared/security` defines the interface and a permissive default:

```java
public interface AccessTokenRevocationChecker { boolean isRevoked(String jti); }
```

`NoOpTokenRevocationChecker` is `@ConditionalOnMissingBean`; the auth module's Redis
implementation replaces it. `shared` still has zero compile-time knowledge of `auth`.

---

## Persistence — `V2__auth.sql`

`users`, `user_roles`, `refresh_tokens`, `user_sessions`, `login_history`,
`password_reset_tokens`, `email_verification_tokens`.

> **Migration number:** auth takes `V2` because it is being built before the company
> module. The company module will take `V3` and add the `companies` table plus the FK from
> `users.tenant_id`. Recorded here so Phase 1 does not collide.

Index rules (`tenant_id` leads every composite key):

- `users`: `UNIQUE (tenant_id, email)`, `KEY (tenant_id, status)`
- `refresh_tokens`: `UNIQUE (token_hash)`, `KEY (tenant_id, user_id, revoked_at)`,
  `KEY (family_id)`
- `user_sessions`: `KEY (tenant_id, user_id, revoked_at)`, `KEY (expires_at)`
- `login_history`: `KEY (tenant_id, attempted_email, occurred_at)` — the throttle
  query; `KEY (tenant_id, user_id, occurred_at)`
- `password_reset_tokens` / `email_verification_tokens`: `UNIQUE (token_hash)`,
  `KEY (tenant_id, user_id)`

No FK to `companies` yet — that table does not exist. Phase 1 adds it.

---

## Audit

`LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGOUT`, `LOGOUT_ALL_DEVICES`, `TOKEN_REFRESHED`,
`REFRESH_TOKEN_REUSE_DETECTED`, `TOKEN_REVOKED`, `PASSWORD_CHANGED`,
`PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`, `ACCOUNT_LOCKED`,
`ACCOUNT_UNLOCKED`, `EMAIL_VERIFIED`, `SESSION_REVOKED`.

IP and user agent are recorded. **Credentials, password hashes and raw tokens never
are** — audit details carry only ids, jtis and reasons.

---

## Notification

`NotificationPort` abstracts delivery of reset and verification links.
`LogOnlyNotificationSender` is `@Profile("!prod")` and writes the link at INFO for
local development. **Production has no default implementation on purpose** — the
context fails to start until a real sender is wired, rather than silently swallowing
password-reset emails.

---

## Configuration (`app.auth.*`)

| Key | Default | Meaning |
|---|---|---|
| `lockout-threshold` | 5 | Failures before the account locks |
| `lockout-duration` | 15m | How long the lock holds |
| `throttle-max-attempts` | 10 | Failures per email+IP before 429 |
| `throttle-window` | 15m | Throttle window |
| `max-concurrent-sessions` | 5 | Oldest session is revoked beyond this |
| `remember-me-duration` | 30d | Refresh TTL when Remember Me is set |
| `reset-token-ttl` | 15m | Password reset link lifetime |
| `verification-token-ttl` | 24h | Email verification link lifetime |
| `verification-resend-window` | 5m | Minimum gap between re-issued verification mails |
| `password.min-length` | 10 | |
| `password.max-age` | 0 (disabled) | Forced rotation, if a compliance regime requires it |

---

## Tests

Service-layer unit tests (`AuthServiceTest`, `PasswordServiceTest`,
`PasswordPolicyTest`, `TokenRotationTest`, `LoginThrottleTest`) cover:

- wrong company id → `401`, identical to a wrong password;
- missing user and wrong password are indistinguishable, and both run BCrypt;
- lockout at the threshold, auto-unlock after the window;
- refresh rotation invalidates the presented token;
- **reuse of a revoked refresh token revokes the whole family**;
- disabled / unverified / expired-password rejections;
- policy rejections: short, no digit, contains email local part, common password,
  same as current;
- forgot-password returns 200 for an unknown email and issues no token;
- session cap revokes the least recently seen session.

## Open questions

- SMTP/provider choice for `NotificationPort` — still open; prod must supply one.
- MFA — out of scope for v1. `User` has no `mfaEnabled` column; add it with the
  feature rather than now.
- Device fingerprinting is currently `deviceId` supplied by the client plus
  user-agent parsing. A spoofed `deviceId` only affects that user's own session
  list, so it is not a trust boundary.

---

## Amendment — 2026-07-29 (v0.12.0)

- **`CompanyDirectoryPort` replaced `TenantDirectoryPort`.** `findBySlug` +
  `supportsSlugLookup` collapsed into a single **`findByCode`**, and
  `StandaloneCompanyDirectory` was **deleted**. It could not enforce company status — it
  treated any id owning at least one user as active — and a fallback that silently ignores
  suspension is worse than a startup failure if the real bean goes missing.
  `CompanyDirectory` in `modules/company` is now the only implementation, and no longer
  needs `@Primary`.
- **JWT claim `tid` → `cid`.** The old spelling is still *read* by
  `JwtTokenProvider.companyClaim(...)` and never written: a refresh token minted before the
  rename is valid for seven days, and dropping the fallback signs those users out
  mid-session. **Delete the fallback once the longest refresh TTL has elapsed since the
  deploy** — it is listed under *Next Task*.
- **`TENANT_ADMIN` deleted** from `Roles` and the `Role` enum. `V12` rewrites the
  `user_roles` rows that carried it; a row that survives leaves its owner unable to sign
  in, because the enum constant no longer exists.
- **JWT gained two optional claims, `bid`/`hid`** (2026-07-30, for Shipment Booking's
  "book from my own branch, no picker" UI): the caller's own `branch_id`/`hub_id`, present
  only when the account is staffed at one. `auth.domain.User` — previously a thinner
  mapping of the shared `users` table than `company.User` — now also maps these two
  columns read-only; `JwtTokenProvider.generateAccessToken` gained an overload taking
  them (the 4-arg form still exists, delegates with nulls). Never trusted for
  authorization, same as every other claim — a client-side convenience only. Frontend:
  `AuthService.hydrate()` (rebuilds the session from the stored token alone, on every
  page load) previously had **no** way to recover `branchId`/`hubId` at all, since
  neither was ever a JWT claim before this and `applySession`'s copy from the login
  response lived only in the in-memory signal — a hard page reload silently lost both.
  That gap is why "Booking Branch defaults to the login branch" appeared to not work
  during Shipment Booking's own verification even after the backend fix landed; see
  `MEMORY/modules/shipment-booking.md`.
- **`provisionAdmin` now issues a temporary password** instead of an unusable one, returned
  once to the caller. See `MEMORY/modules/company.md` §*The first administrator now gets a
  temporary password* and decision 49 — the account stays `PENDING`, so the password alone
  opens nothing.
- **`provisionSuperAdmin`** — the third provisioning path. ACTIVE and pre-verified, because
  a platform operator is onboarded by another platform operator and there is nobody above
  them to recover the account if an email never arrives.
- **`NotificationPort.sendCompanyActivation(email, displayName, companyName, link)`** — the
  activation email. **The port is never given the password**, so no implementation can put
  a plaintext credential in a mailbox or a log.
- **`SuperAdminAccountService`** — create and list platform-tier accounts. It lives in
  `auth` because `users` and `user_roles` are auth's tables. `SUPER_ADMIN` only, *reads
  included*: the list of who holds the highest privilege on the platform is exactly the
  list a lesser account would most like to have.
- **`UserRepository.findAllByRoleIn` / `existsByEmailAcrossCompanies`** — two more
  deliberate cross-company queries, safe for the same reason `findPlatformUsersByEmail` is:
  the role predicate confines the result to accounts that are company-unbound by
  definition. `existsAnyUserForCompany` was deleted with the placeholder directory.

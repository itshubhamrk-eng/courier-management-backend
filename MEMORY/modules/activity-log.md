# User Activity & Audit Logging

**Status:** DONE, backend + frontend, migrations `V85`–`V87`. New package
`com.courier.shared.activity` (cross-cutting, sibling of `shared.audit`), extensions to
`modules.auth`'s existing `LoginHistory`/`UserSession`, frontend `features/activity-log/`.
Verified live end to end over HTTP and through the actual browser as a reset
`COMPANY_ADMIN` fixture (`loadtest.admin@example.com` / `LOADTEST01`) against real MySQL.

## What already existed, and why this module is additive, not a rewrite

Before writing a line of code, three pieces of exactly this brief were already live:

- **`shared.audit`** (`AuditLog`/`AuditService`/`AuditLogWriter`, `V1`) — an append-only,
  async (`REQUIRES_NEW`, own executor, never throws), company-aware curated trail. 184 call
  sites across 60 files already call `AuditService.record(...)` for login, money movement,
  permission grants, shipment/manifest/delivery lifecycle — most of requirement 5's module
  list, in Java rather than in a spreadsheet.
- **`LoginHistory`** (`modules.auth`, company-owned) — one row per login attempt, success or
  failure, with `failureReason`, `sessionId`, `ipAddress`, `userAgent`, already backing both
  a login throttle and a "recent activity" read.
- **`UserSession`** (`modules.auth`, company-owned) — one row per logged-in device: id,
  name, type, ip, user agent, last-seen, expiry, revocation + reason. This *is* "current
  active session."
- **`AUDIT_READ`/`AUDIT_SEARCH`/`AUDIT_EXPORT`** — seeded in the permission catalogue since
  `V6`, granted to `COMPANY_ADMIN` by `DefaultRoleCatalog`'s derivation, and never once
  consumed by a controller. Exactly the "responsibility list ahead of the code" gap this
  module closes — see `MEMORY/AI_CONTEXT.md`.

So the real gaps against the 13-requirement brief were narrower than it first reads:
1. Nothing captured activity **automatically** — every one of those 184 call sites is a
   developer having remembered to write the line. Requirement 4 asks for the opposite.
2. `AuditLog`'s `details` is one opaque JSON blob — no separate old/new value, no module/
   submodule, no request method/endpoint, no distinguishable status/error message.
3. `LOGOUT` and `SESSION_EXPIRED` were audited (`shared.audit`) but never written to
   `login_history`, so the login/logout *event log* requirement 1 asks for was half a table.
4. No screen existed for any of it.

## What was built

### `activity_logs` (`V85`) — the automatic trail

`ActivityLog` (`shared.activity.domain`) extends `BaseEntity`, not `CompanyOwnedEntity` —
same reasoning `AuditLog` already established: company isolation for reads is enforced
explicitly in `ActivityLogService`/`ActivityLogSpecifications` (pin the caller's own
company, override anything a query string tries to claim — AI_CONTEXT.md decision 27),
never left to a Hibernate filter that would fight a legitimately-null platform-tier row.

Columns: `company_id`, `user_id`, `username`, `module`, `submodule`, `action`,
`entity_type`, `entity_id`, `description`, `old_value`/`new_value` (JSON), `ip_address`,
`device`/`browser`/`os`, `session_id`, `request_method`, `api_endpoint`, `status`,
`error_message`, `occurred_at`. Indexed on `(company_id, occurred_at)`,
`(user_id, occurred_at)`, `(module, occurred_at)`, `(action, occurred_at)`,
`(entity_type, entity_id)`, `session_id`.

**`ActivityLoggingFilter`** (an `OncePerRequestFilter`, registered
`addFilterAfter(activityLoggingFilter, CompanyResolutionFilter.class)` — after the
principal and company are both bound) is requirement 4 in full: it writes one row per
meaningful authenticated call under `/api/v1/**`, with zero controller changes anywhere.
- **Module/submodule** — inferred from the URL's path segments, title-cased
  (`branch-wallet` -> `Branch Wallet`), singularised for `entityType` with real pluraliser
  handling (`Branches` -> `Branch`, not the naive `Branche` a bare "drop trailing s" gives —
  found by actually calling the endpoint, not by a unit test).
- **Action** — HTTP verb by default (`POST`->`CREATE`, `PUT`/`PATCH`->`UPDATE`,
  `DELETE`->`DELETE`, `GET` on a single-resource path -> `VIEW`), overridden by a keyword
  dictionary matched against the last meaningful path segment (`dispatch`, `receive`,
  `approve`, `cancel`, `assign`, `status`->`STATUS_CHANGE`, `out-scan`, `in-scan`, ...) —
  covers requirement 2's whole action vocabulary without per-endpoint annotation.
- **Deliberately does not log every GET.** A paginated list is polled far more than it is
  meaningful, and requirement 9 asks this module not to slow the app or store noise. A GET
  is logged only when its last path segment looks like one record's id — the same
  distinction `PermissionAction.READ` vs `SEARCH` already draws.
- **Request body capture** — only for JSON, only up to 4 KB, only run through
  `SensitiveDataMasker` first, stored as `newValue` when no service explicitly enriched the
  request. Every write endpoint is therefore automatically audited with *some* new-value
  detail, even the ones nobody went back to annotate.
- Excludes pre-auth endpoints (`/auth/login`, `/auth/refresh`, `/auth/forgot-password`,
  `/auth/reset-password`, `/auth/verify-email`, `/companies/register`) and its own read
  endpoints (`/activity-logs/**`, to avoid a screen logging its own traffic pointlessly) —
  everything else authenticated is in scope, including `/auth/logout` and `/auth/change-
  password`, which is why those show up in the trail alongside the dedicated login-history
  handling below.

**`ActivityContext`** (a plain, non-inheritable `ThreadLocal`, mirroring `CompanyContext`'s
own reasoning) is the escape hatch for the handful of places that know something the URL
can't express — a status transition's before/after, a permission grant's before/after set.
A service calls `ActivityContext.recordChange(module, submodule, description, entityType,
entityId, oldValue, newValue)` once, right next to the `AuditService.record(...)` call it
already makes; the filter picks it up after the response and clears it in a `finally`.
Wired into exactly three call sites, chosen to match the brief's own worked examples and
its test list, not to retrofit the whole codebase:
- `ManifestServiceImpl.dispatch` — `status: CREATED` -> `status: DISPATCHED`.
- `RolePermissionServiceImpl.assign` — full permission-code set before/after the grant.
- `ShipmentServiceImpl.cancel` — `status: <previous>` -> `status: CANCELLED` + reason.

**`SensitiveDataMasker`** replaces any key whose lower-cased name contains `password`,
`token`, `secret`, `otp`, `cvv`, `pin`, `authorization`, `apikey`, `cardnumber`,
`signature` or `credential` with `***MASKED***`, recursively through nested maps and JSON
objects — applied to every old/new value and every captured request body before it is
serialised, satisfying requirement 8's "never store credentials/tokens/secrets" without
requiring every future field name to be enumerated by hand. A body that fails to parse is
discarded rather than stored raw, on the same "never risk a leak" reasoning.

### `login_history` gains events, not a second table (`V86`)

Rather than a new `login_sessions`/`user_activity_events` table duplicating `user_id`/
`ip_address`/`session_id`/`occurred_at`, `login_history` (already company-owned, already
backing the throttle) grew:
- `event_type` (`LoginEventType`: `LOGIN_SUCCESS`/`LOGIN_FAILED`/`LOGOUT`/
  `SESSION_EXPIRED`) — backfilled from the existing `success` boolean.
- `device`/`browser`/`os` — parsed once at write time by `UserAgentParser` (see below).
- `logout_at` — filled in, best-effort, when a later `LOGOUT`/`SESSION_EXPIRED` row for the
  same `session_id` is written (`LoginAttemptService.recordSessionEnd` looks up the
  matching `LOGIN_SUCCESS` row and sets it). Never required for the event log itself to be
  complete — the `LOGOUT`/`SESSION_EXPIRED` row exists either way, `logout_at` is a
  convenience for "how long was this session open."

`LoginAttemptService` gained `recordLogout(...)` (called from `AuthService.logout`'s
single-device branch — the `allDevices` branch keeps its existing `LOGOUT_ALL_DEVICES`
audit action only, deliberately not exploded into one `login_history` row per revoked
session) and `recordSessionExpired(...)` (called from `AuthService.refresh` at the one
point a presented refresh token's session is found gone — past its TTL or revoked). Both
are `REQUIRES_NEW`, mirroring `recordSuccess`'s own reasoning: the event must survive
whatever the caller's own transaction does next. `AuditAction.SESSION_EXPIRED` is new
alongside them; `SESSION_REVOKED` already existed for the admin-initiated case.

### `user_sessions` gains `browser`/`os` (`V87`)

`device_id`/`device_name`/`device_type`/`ip_address`/`user_agent` already existed; only the
two parsed fields were missing for the User Activity screen's active-session list.
Populated once in `SessionService.openSessionLocked`, same place `deviceType` already is.

### `UserAgentParser`

A new, dependency-free `shared.useragent.UserAgentParser` — deliberately **not** shared
with `SessionService.DeviceInfo`'s existing private `inferType`, which predates this and is
covered by its own tests. Duplicating ~15 lines of device classification was cheaper and
lower-risk than a cross-module refactor of already-tested code for a cosmetic string. Used
by `LoginAttemptService`, `SessionService`, and `ActivityLoggingFilter` — three independent
call sites, one parsing rule.

## RBAC — no new permission codes

Requirement 11 names `ACTIVITY_LOG_VIEW`/`ACTIVITY_LOG_EXPORT` as examples. The catalogue
already has `AUDIT_READ`/`AUDIT_SEARCH`/`AUDIT_EXPORT` (seeded `V6`, module `AUDIT`,
`displayOrder 280`) meaning exactly the same thing, granted to `COMPANY_ADMIN` today and to
nobody else by default. Adding a parallel `ACTIVITY_LOG_*` set would be decision 55's
mistake in reverse — two catalogue entries for one right, guaranteed to drift. Every gate in
this module reads `hasRole('SUPER_ADMIN') or hasAuthority('AUDIT_READ'/'AUDIT_SEARCH'/
'AUDIT_EXPORT')` — `SUPER_ADMIN` explicit because platform-tier accounts hold no company
permission codes at all (they authorise on the JWT role, not on `user_company_roles`), and
without the `hasRole` arm a super admin would be unable to read a business trail across
every company the way `SuperAdminBoundaryTest`'s whole premise says they legitimately can.

## API

- `GET /api/v1/activity-logs` — paged, filtered by `userId`/`module`/`action`/`entityType`/
  `entityId`/`status`/`sessionId`/`dateFrom`/`dateTo`/free-text `search`. `AUDIT_SEARCH`.
- `GET /api/v1/activity-logs/{id}` — full detail, including old/new value.
  Cross-company read 404s (not 403s — same "don't confirm existence" reasoning
  `ResourceNotFoundException`'s own doc gives) unless the caller is platform-tier.
  `AUDIT_READ`.
- `GET /api/v1/activity-logs/export` — same filters, CSV, capped at 10,000 rows so one
  export cannot pull the whole table. `AUDIT_EXPORT`.
- `GET /api/v1/users/{userId}/activity` — the User Activity screen's one call: login
  history (last 20), recent activities (last 50), distinct modules touched, last-activity
  timestamp, active sessions. Lives in `modules.auth` (`UserActivityController`/
  `UserActivityService`), **not** `shared.activity` — it needs `LoginHistory`/
  `UserSession`, and `shared` must never import from `modules` (AI_CONTEXT.md decision 12).
  `AUDIT_READ`.

## Frontend

`features/activity-log/activity-log-list.ts` — filters (search, user autocomplete, module,
action, status, entity type/id, date range) over a table, `app-drawer` detail view with
pretty-printed old/new value JSON, CSV export button. `features/activity-log/
user-activity.ts` — a user autocomplete driving overview/active-sessions/login-history/
recent-activity cards from the one composed endpoint. Both single-file standalone
components in the established ticket-list/wallet-transaction-filter idiom — no new shared
component was needed beyond `ApiService.getBlob` (added for the CSV download; the codebase
had no prior binary-download call). Nav: **Administration -> Activity Log / User Activity**,
gated `AUDIT_SEARCH`/`AUDIT_READ`, `roles: ADMINS` (`SUPER_ADMIN` + `COMPANY_ADMIN`) —
same bridge every other permission-gated leaf uses.

## Verified live

Backend booted on a throwaway instance (`SERVER_PORT=8082`, profile `local`) against the
real dev database; `V85`–`V87` applied clean on top of `V84`. Exercised over curl and then
through the actual Angular dev server (port 4300, a scratch `proxy.conf.json` pointed at
8082 — removed after) as a real `COMPANY_ADMIN` fixture:
- A `GET` on a single branch produced exactly one `activity_logs` row: module `Branches`,
  action `VIEW`, entity type `Branch` (after the pluraliser fix), status `SUCCESS`, device/
  browser/os parsed from the real request.
- `GET /activity-logs` returned it, scoped to the caller's own company; the same request
  with a `BRANCH_MANAGER` token (no `AUDIT_SEARCH`) got a real `403`.
- `GET /activity-logs/{id}` returned the full record.
- Login, then logout, produced `LOGIN_SUCCESS` then `LOGOUT` rows in `login_history`, and
  the original `LOGIN_SUCCESS` row's `logout_at` was backfilled.
- `GET /users/{id}/activity` returned the composed summary — caught one real bug in the
  process: `findDistinctModuleByCompanyIdAndUserIdAndModuleIsNotNull` looked like a valid
  Spring Data derived projection but is not one — the segment between `find` and `By` is
  only ever a readability placeholder, never a field selector, so it returned full
  `ActivityLog` entities against a `List<String>` return type and threw
  `QueryTypeMismatchException`. Fixed with an explicit `@Query`. Not something any mock-
  based unit test would have caught; found by calling the endpoint against a real database.
- The full Angular UI: Activity Log list + detail drawer, and User Activity's active
  sessions / login history / recent activity, all rendering real data end to end,
  screenshotted.

## Tests

`UserAgentParserTest`, `SensitiveDataMaskerTest`, `ActivityContextTest`,
`ActivityLogServiceTest` (company isolation: a company caller is always pinned to their own
company regardless of what they request; a super admin's requested company is honoured;
cross-company reads 404). `LoginAttemptServiceTest` extended for event-type tagging,
device/browser/os parsing, `recordLogout` (closes out the session's own login row) and
`recordSessionExpired`. `ManifestServiceImplTest.dispatchHappyPath`,
`RolePermissionServiceImplTest.assignGrants` and
`ShipmentServiceImplTest.cancelSucceedsFromBooked` each gained an assertion that
`ActivityContext` was populated with the right old/new value — proving the three enrichment
call sites actually fire, not just that they compile.

## Explicitly not built (out of scope)

- **No automated test for `SESSION_EXPIRED` over a real HTTP refresh call** — the manual
  verification's revoked-via-logout scenario hit `INVALID_REFRESH_TOKEN` in
  `TokenIssuer.rotate` before ever reaching the `sessionService.findActive(...)` check
  `AuthService.refresh` guards; the code path is real and unit-tested at the
  `LoginAttemptService` level, but the specific "refresh token still validates, session row
  is separately gone" scenario needs a session-cap-eviction-style setup this session didn't
  build a fixture for.
- **`LOGOUT_ALL_DEVICES` does not write one `login_history` row per revoked session** — only
  its existing `AuditAction.LOGOUT_ALL_DEVICES` audit line. Scope call, not an oversight.
- **No retention/archival job** — `activity_logs` gained the same
  `deleteByOccurredAtBefore` a future sweep would call, mirroring `AuditLogRepository`'s
  existing unused one; both are still tracked in `MEMORY/BACKLOG.md`.
- **No frontend unit tests** — same accepted-gap precedent Freight Factor and Address
  Distance already set for a UI this size.

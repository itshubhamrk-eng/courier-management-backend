# Security incident detection and auto-ban

Built 2026-11-04/05 on branch `worktree-security-incidents` (spec:
`docs/superpowers/specs/2026-11-04-security-incident-detection-design.md`). Live-verified
2026-11-05 on a copy of the dev DB (`courier_secverify`, :8082 + :4300); see
`.superpowers/sdd/2026-11-04-security-incident-detection/task-12-report.md`.
**Not deployed.**

## Purpose

Detect account compromise and abuse, record each finding as a `security_incident`, and
ban the user automatically when the evidence is strong (stolen refresh token, correct
password from abroad, session used from abroad). A COMPANY_ADMIN reviews incidents at
`/security/incidents` and can Ban / Dismiss / Unban.

## Architecture

- Package `com.courier.modules.security` (domain `SecurityIncident`, `IncidentRule`,
  `GeoVerdict`; application `SecurityIncidentService`, `UserBanService`,
  `GeoCountryService`, `MaxMindCountryLookup`, `SecurityProperties`; `application/scan`
  scanner + rules; `api/SecurityIncidentController`).
- `SecurityIncidentService.raise(IncidentRequest)` runs in `REQUIRES_NEW`, locks the user
  row, dedups: no new incident while one for the same user+rule is OPEN or BANNED, or
  DISMISSED within the last 24 h (`DISMISSAL_QUIET_PERIOD`). Callers must not hold
  uncommitted writes/locks on that user's row, sessions or refresh tokens (deadlock).
  Incidents with no user (IP level) skip this dedup; the rule dedups itself.
- Severity: auto-ban rules => HIGH, everything else MEDIUM.
- Inline detection: refresh-token reuse (`TokenIssuer`), foreign login (`AuthService.login`).
- Scheduled detection: `SecurityScanJob` every `scan-interval` (PT2M) over a sliding window
  `[to - scan-window, to)`, `to = now - scan-lag` (10 min, 30 s); cursor row in
  `security_scan_cursor`; on first boot the cursor starts at now (history not scanned).
  Overlapping windows are safe because `raise` dedups. **Single instance only** (no
  ShedLock); two backends would race (dedup lock prevents duplicates per user, IP-level
  rules could double).

### Rules

| Rule | Where | Reads / measures | Auto-ban |
|---|---|---|---|
| REFRESH_TOKEN_REUSE | inline, `/auth/refresh` | an already-rotated refresh token presented again after the grace window (`grace-seconds` 10), or within it from an IP that matches neither the original nor the successor token | yes |
| FOREIGN_LOGIN_CORRECT_PASSWORD | inline, `/auth/login` | correct password from a country other than `geo.allowed-country` (IN); response is the same 403 `GEO_RESTRICTED` as a wrong password | yes |
| FOREIGN_TOKEN_USE | scanner | `refresh_tokens.ip_address` rows in the window with a FOREIGN verdict, >= `foreign-token-use.min-foreign-refreshes` (1) per user | yes |
| FOREIGN_LOGIN_ATTEMPT | inline, `/auth/login` | wrong password from abroad for a known user (writes `login_history` reason GEO_RESTRICTED, no failed-attempt increment, throttled per email+IP) | no |
| REVOKED_TOKEN_REPLAY | scanner | `REFRESH_TOKEN_REUSE_DETECTED` audit rows for revoked tokens with no successor (logout/evicted/banned), > `revoked-replay-threshold` (3) | no |
| SESSION_AGENT_CHANGE | scanner | refresh token issued with a browser/OS different from the `user_sessions` login browser/OS | no |
| SESSION_MULTI_IP | scanner | one session's refresh tokens from 2+ public IP prefixes (v4 /24, v6 /64; private/loopback/CGNAT ignored) | no |
| BULK_ACTIVITY | scanner | `activity_logs` cancels/exports/rate edits per user > `bulk-activity.threshold` (30); excludes `/calculate` and pricing-zone `/locations` | no |
| ACCESS_DENIED_BURST | scanner | `activity_logs` 403 rows with the canonical access-denied message per user > `access-denied.threshold` (20) | no |
| CREDENTIAL_ATTACK | scanner | `login_history` LOGIN_FAILED (incl. GEO_RESTRICTED): one IP failing on >= `credential-attack.distinct-accounts` (5) accounts (IP-level, no user), or one account with >= `failures-per-account` (10) failures | no |

Session rules see only refreshes (~every 15 min), so their sensitivity is low by design.

## Ban mechanics

`UserBanService.ban`: user -> DISABLED, all sessions + refresh tokens revoked, Redis key
`banned-user:<userId>` with TTL ~ access-token TTL (seen 1200 s). `JwtAuthenticationFilter`
checks that key on every authenticated request (one extra Redis `hasKey`), so an already
issued access token stops working immediately. If Redis is down the check fails open (the
access token lives until expiry, max 15 min). Unban: incident -> UNBANNED, user restored to
ACTIVE (PENDING if email unverified), key deleted; not reactivated if another BANNED
incident exists for the user. Platform-role targets (SUPER_ADMIN / PLATFORM_ADMIN) can be
manually banned only by a PLATFORM_ADMIN.

State transitions: dismiss only from OPEN; ban from OPEN or DISMISSED; unban only from
BANNED; anything else 422 `INVALID_STATE_TRANSITION`.

## Client IP

`AuthController` now derives the client IP with `ClientIpResolver`: `X-Real-IP` is trusted
only when the TCP peer is loopback or private (nginx), otherwise the peer address.
`X-Forwarded-For` is ignored there (was: first XFF entry, spoofable). Other callers
(`ActivityLoggingFilter`, `AuditService`) still use the old XFF derivation, which is why
the scanner reads `refresh_tokens.ip_address`, never `activity_logs.ip_address`, for geo.

## Settings (`courier.security.*`, env `COURIER_SECURITY_*`)

| Key | Default | Notes |
|---|---|---|
| grace-seconds | 10 | refresh rotation grace window |
| scan-enabled | true | `@ConditionalOnProperty` on the job |
| scan-interval | PT2M | |
| scan-window | PT10M | all thresholds are per window |
| scan-lag | PT30S | |
| revoked-replay-threshold | 3 | |
| bulk-activity.threshold | 30 | |
| access-denied.threshold | 20 | |
| credential-attack.distinct-accounts | 5 | |
| credential-attack.failures-per-account | 10 | |
| foreign-token-use.min-foreign-refreshes | 1 | open decision, see below |
| geo.enabled | false | |
| geo.db-path | blank | MaxMind GeoLite2-Country `.mmdb`; blank/unreadable => every lookup UNKNOWN (fail-open, logged WARN) |
| geo.allowed-country | IN | |

## Migrations

V139 `security_incident` (+ permission module `SECURITY_INCIDENT`, actions READ/SEARCH/UPDATE,
COMPANY_ADMIN backfill), V140 `security_scan_cursor` + `idx_refresh_tokens_jti`, V141
`refresh_tokens(created_at)` (+ a login_history index), V142 drops that login_history index,
V143 `activity_logs(status, occurred_at)` and `login_history(event_type, occurred_at)`,
V144 `resolution_note` -> VARCHAR(2000).
**Ordering:** V138 (document print log) is still uncommitted on the main checkout. Commit
and deploy V138 before V139-V144, in version order (see the 2026-10-17 Flyway crash-loop
note). Live verification used `SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS=*:missing` on a DB
copy only.

## API (`/api/v1/security/incidents`, wrapped in `ApiResponse`)

READ = `hasRole('PLATFORM_ADMIN') or hasAuthority('SECURITY_INCIDENT_READ')`; UPDATE = same
with `SECURITY_INCIDENT_UPDATE`. SUPER_ADMIN has no access unless granted.

| Method | Path | Notes |
|---|---|---|
| GET | `` | filters `status`, `severity`, `rule`, `userId`, `page`, `size` (<=100), newest first; bad enum 400 |
| GET | `/summary` | `{OPEN,BANNED,DISMISSED,UNBANNED}` |
| GET | `/{id}` | other company => 404; user name/email/roles/branch, evidence object + `evidenceUnreadable` |
| POST | `/{id}/ban`, `/{id}/dismiss`, `/{id}/unban` | body `{note?}` max 1000; actor = authenticated user |

Login error codes: 403 `GEO_RESTRICTED`, 429 `RATE_LIMIT_EXCEEDED` (frontend login page maps both).

## Frontend

`features/security-incidents` (list with filters, detail/report page with
`window.print()`), `securityIncidentGuard`, sidebar entry under Administration.
`TokenRefreshService`: single-flight refresh (parallel 401s share one
`POST /auth/refresh`; verified live: 7 parallel 401s -> 1 refresh -> 7 replays), and adopts a
refresh done by another tab. Without single-flight, normal parallel calls would trip
REFRESH_TOKEN_REUSE.

## Runbook

- **Unban a user:** Security Incidents -> open the BANNED incident -> Unban user (API:
  `POST /{id}/unban`). Do not just set `users.status` in SQL: the incident stays BANNED and
  the Redis key may still block for up to 20 min (`DEL banned-user:<uuid>`).
- **Enable geo in prod:** put GeoLite2-Country.mmdb on the EC2 box (MaxMind account,
  refresh monthly), set `COURIER_SECURITY_GEO_ENABLED=true` and
  `COURIER_SECURITY_GEO_DB_PATH=/path/GeoLite2-Country.mmdb`, check the boot log line
  "GeoLite2 country database loaded". nginx must send `X-Real-IP $remote_addr`.
- **Before V143 on prod (1.9 GB box):** `SELECT COUNT(*) FROM activity_logs` and
  `login_history`; a large table makes the index build slow and memory hungry; run off-peak.
- **Scanner:** one backend instance only. To pause: `COURIER_SECURITY_SCAN_ENABLED=false`.
- **Test .mmdb:** a tiny IPv4 GeoLite2-Country file can be written with a 100-line Python
  script (used in the 2026-11-05 verification; 203.0.113.0/24 -> US, 198.51.100.0/24 -> IN).

## Known limits / deferred

- Incident detail `userRoles` reads only the legacy `user_roles` enum table, so users with
  only company roles (`user_company_roles`, e.g. BOOKING_OPERATOR) show Roles "—".
- **UI confirm dialog does not submit (found 2026-11-05):** `IncidentActionDialog` uses
  `(ngSubmit)` on a `<form>` with no `[formGroup]`/NgForm, so the confirm button does a
  native form submit and reloads the page; Ban/Dismiss/Unban from the UI do nothing. API works.
- Expired-token branch of refresh still rolls back its family revoke (pre-existing).
- `refresh` has no inline geo check; FOREIGN_TOKEN_USE catches it on the next scan.
- Ban-as-password-oracle: after a foreign correct guess the user is DISABLED, visible as
  ACCOUNT_DISABLED from India. Timing side channel on the foreign branch not equalised.
- Banning an IP-level incident (no user) marks it BANNED with nobody banned (UI hides Ban).
- EXPORT actions are never logged today, so BULK_ACTIVITY only sees cancels/rate edits;
  custom-message 403s and denied list GETs are invisible to ACCESS_DENIED_BURST.
- Unknown-user foreign attempts raise no FOREIGN_LOGIN_ATTEMPT (they count toward CREDENTIAL_ATTACK).
- Scanner can raise a non-ban rule for a user it just banned in the same run (seen:
  SESSION_MULTI_IP next to FOREIGN_TOKEN_USE).
- No OPEN-count nav badge; nav link may show for COMPANY_ADMIN lacking the read code
  (lands on /unauthorized).
- Print stylesheet uses `position:absolute; top:0` on `.si-sheet`; no positioned ancestors
  were found on screen, but hidden content still takes space, so trailing blank pages are
  possible. Needs a human print preview.
- nginx `limit_req` on `/api/v1/auth/login` is a separate job.
- `ActivityLoggingFilter`/`AuditService` IP still from XFF (spoofable in activity/audit logs).

## Open decision

`foreign-token-use.min-foreign-refreshes`: design says 1 (any foreign refresh bans). A
corporate VPN, mis-geolocated carrier block or stale `.mmdb` can then falsely ban. 2 needs
a second foreign refresh in the same window. Product owner to decide before enabling geo
in prod.

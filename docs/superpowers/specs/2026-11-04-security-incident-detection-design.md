# Security Incident Detection, Auto-Ban and Report — Design

Date: 2026-11-04. Status: awaiting approval.

## Intent
Owner request: "if any unusual activity detected then ban that user and create a report of that user."
Add detection of abusive or stolen-token activity, a ban (user disabled, sessions revoked), and a per-user incident report an admin can read and download. A wrong auto-ban locks a real employee out of live shipment operations, so only unambiguous signals ban automatically; everything else is flagged for a human.

## Goals
- Detect: credential attack, cross-company/branch access attempts, stolen-token use.
- Auto-act on clear-cut signals: revoke sessions, set user `DISABLED`, write an incident with an evidence snapshot.
- Flag subtler signals for COMPANY_ADMIN review (Ban / Dismiss).
- Admin UI: incident list, incident report (screen + PDF), Ban / Dismiss / Unban.
- Reuse existing data only: `ActivityLog`, `LoginHistory`, `UserSession`, `RefreshToken`, Redis denylist, `AuditService`.

## Non-goals
- Impossible-travel (distance/time between two cities). Only the country check below is in scope.
- Browser/GPS location (`navigator.geolocation`): needs a user permission prompt and is trivially spoofed client-side, so it cannot enforce anything. Server-side IP geolocation is used.
- Banning IPs or devices at the edge/nginx; ML anomaly scoring.
- Notifying by email/WhatsApp (no real notification port in prod; incidents show in the UI only).
- Banning SUPER_ADMIN / PLATFORM_ADMIN accounts automatically (flag only).

## Anti-goals
- Locking out honest staff because their phone switched Wi-Fi to mobile data. A bare IP change never bans.
- Adding latency or failure risk to the request path.
- An auto-ban with no report, or a report with no way to undo the ban.

## Constraints (from the codebase)
- `UserStatus.DISABLED` exists; use it as the "ban". `LOCKED` + `lockedUntil` is the existing temporary login lockout and is left untouched.
- `ActivityLog` is append-only; never edited. New table is separate.
- New tables use `company_id`/`tenant_id` convention of existing tables; FKs go to `companies.company_id`.
- Reads isolated by company explicitly in service/specifications, not the Hibernate filter alone.
- New permission module needs a COMPANY_ADMIN backfill migration. Migration number: next free after V138 (check `flyway_schema_history`; commit in version order).
- Revocation must go through the `SessionService.revokeSession` proxy path (see its self-proxy note); no new revoke logic.
- JWT header size near limit: do not add claims to the JWT.

## Approaches considered
- A. Scheduled scanner over existing tables (chosen). Lag of a few minutes, zero request-path cost, reuses data.
- B. Inline per-request/login detection. Instant, but adds hot-path latency and risks instant false-positive lockouts.
- C. Report-only, admin always bans. Safest, but does not do what was asked.
Owner picked A.

## Design

### Data
New table `security_incident`:
`id`, `company_id`, `user_id`, `rule_code`, `severity` (HIGH auto-act / MEDIUM flag), `status` (OPEN, BANNED, DISMISSED, UNBANNED), `evidence` (JSON snapshot: counts, IPs, user agents, session/token ids, sample ActivityLog ids, time window), `auto_action` (NONE, SESSIONS_REVOKED, USER_DISABLED), `detected_at`, `resolved_by`, `resolved_at`, `resolution_note`.
Dedup: at most one OPEN incident per (user, rule) at a time.

### Rules (scanner runs every ~2 minutes)
Auto-act (HIGH):
1. `REFRESH_TOKEN_REUSE` — a refresh token already rotated (`replacedByJti` set) or revoked is presented again. Action: revoke the whole token family and session, set user DISABLED. Hooked where refresh is validated (a single write on reuse; the scan itself is not needed for this one) so theft is cut immediately.
2. (moved to flag-only, see rule 8)
3. (moved to flag-only, see rule 9)

Flag only (MEDIUM, admin decides):
4. `REVOKED_TOKEN_REPLAY` — repeated use of revoked/denylisted tokens by a user.
5. `SESSION_AGENT_CHANGE` — one session whose user-agent/browser/OS differs from the `UserSession` login fingerprint.
6. `SESSION_MULTI_IP` — one session active from two IPs in overlapping minutes.
7. `BULK_ACTIVITY` — abnormal volume of cancels, rate edits, or exports per user vs. a fixed threshold in a window.

8. `ACCESS_DENIED_BURST` — many `ActivityLog` rows with status denied (403 only; 404 is ignored because typos give 404) for one user in a window. `ActivityLog` has no target-company field, so foreign-company access cannot be proven; this is a flag, not a ban.
9. `CREDENTIAL_ATTACK` — failed logins from one IP across many accounts, or many failures on one account. Incident only, no user ban: the victim of a spray attack is the identified user, so banning would hand attackers a way to lock out any employee. The existing `LOCKED` lockout keeps protecting the account.

Thresholds and window sizes live in a properties class with defaults, tunable without code change.

### India-only access (IP geolocation)
- Country is resolved server-side from the client IP with a MaxMind GeoLite2-Country database file loaded in memory (no per-request network call). Owner must supply the free MaxMind download (account + license key); the DB path/key is configuration, not committed. Refreshed manually or by a periodic download job (builder's call).
- Real client IP: must come from the forwarded header nginx sets (trusted proxy only), never the raw socket address, or every request looks like the proxy. Builder verifies on both EC2 boxes.
- Login from a non-India IP is always rejected (403, no token issued). Private/loopback IPs (local dev, health checks) and unresolvable IPs are allowed and logged `UNKNOWN`; the check can be switched off by a property (default off in `local`/dev profile so local testing keeps working, on in prod).
- Ban rule `FOREIGN_LOGIN_CORRECT_PASSWORD` (HIGH, auto-ban): correct credentials presented from a non-India IP. Account set DISABLED, sessions revoked, incident written.
- Flag rule `FOREIGN_LOGIN_ATTEMPT` (MEDIUM): wrong password from a non-India IP. Blocked and reported, no ban (otherwise an attacker abroad could get any employee banned by typing wrong passwords).
- Ban rule `FOREIGN_TOKEN_USE` (HIGH, auto-ban): an existing access/refresh token used from a non-India IP. Sessions revoked, account DISABLED.
- Optional per-user "allowed abroad" flag so a traveling owner/admin is not banned; set by COMPANY_ADMIN. Not in the first cut unless owner asks (listed in Calls).
- Known limit: an Indian VPN/proxy exit passes the check.

### Ban
Ban = `User.status = DISABLED` + revoke all sessions + audit entry (`AuditService`). Unban = status ACTIVE, failed-attempt counters reset, incident `UNBANNED`. Auto-ban skips users with SUPER_ADMIN / PLATFORM_ADMIN roles (flagged instead).

### Report
Per-incident/per-user report: who (name, role, branch), rule, severity, timeline, evidence (IPs, user agents, counts, sample actions), actions taken, who resolved and when. On screen, plus PDF download.

### API and UI
Backend module `com.courier.modules.security`: scanner job, rule classes, `SecurityIncidentService`, controller `/api/v1/security/incidents` (list, get, ban, dismiss, unban, report PDF). New permission module `SECURITY_INCIDENT`, COMPANY_ADMIN-only, with backfill migration. Frontend `features/security-incidents/`: list (filter by status/severity), detail/report page, action buttons; sidebar entry COMPANY_ADMIN-only.

## Calls made while writing this (not yet approved by owner)
- Auto-ban is limited to rule 1 only. Rules 2/3 from the first draft became flag-only (8, 9), for the reasons above. Owner's "ban on unusual activity" is therefore met automatically for stolen tokens, and by one-click Ban for the rest.
- Rule 1 grace window: a reuse of a just-rotated token within 10 seconds from the same session, IP and browser is treated as a concurrent refresh (two tabs, retry), not theft. Outside that, it is reuse.
- Ban must cut live access tokens: the JWT filter only checks the token-id denylist, not user status. Ban adds the user's active token ids to the Redis denylist (and the session revoke), so cut-off is immediate, not up to the access-token lifetime. No filter change.
- Scanner is single-instance (prod is one EC2 box); no distributed lock. It keeps a cursor and starts from deploy time forward, never scanning old history, so old events cannot ban anyone.
- Dismiss suppresses the same (user, rule) for 24 hours.
- `security_incident.company_id` is nullable. Incidents with no resolved company, and flags on SUPER_ADMIN/PLATFORM_ADMIN users, are visible to PLATFORM_ADMIN. If the only COMPANY_ADMIN is banned, a PLATFORM_ADMIN can unban.
- Rules 5/6 use existing data: `UserSession` stores IP/UA/browser/OS at login; `ActivityLog` stores IP, device, browser, OS and session id per request. PDF is plain English, no special branding.

- Owner said "use geo location" after being asked to accept the wrong-password/correct-password split. Treated as: use server-side IP geolocation, and the split is my proposal, not confirmed. Owner should confirm or change it when approving.
- Per-user "allowed abroad" exemption is deferred.

## Left to the builder
Exact thresholds/windows defaults, PDF library choice (reuse what the app already uses), scheduler mechanism (existing `@Scheduled` pattern), table indexing, test fixtures, cross-company detection query specifics against `ActivityLog`.

## Verification
Unit tests per rule; live check on throwaway `:8082` backend (profile `local`) and `:4300` frontend, never the real `:4200` stack. Test data stays in dev DB.

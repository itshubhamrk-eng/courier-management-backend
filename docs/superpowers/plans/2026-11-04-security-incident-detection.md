# Security Incident Detection, Auto-Ban and Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect stolen-token use, foreign-country logins and other abuse; auto-ban only the unambiguous cases; give COMPANY_ADMIN an incident list and printable per-user report with Ban / Dismiss / Unban.

**Architecture:** New backend module `com.courier.modules.security` holds the incident table, ban service and a scheduled scanner that reads `ActivityLog`, `LoginHistory`, `UserSession`, `RefreshToken`. Two inline hooks (refresh-token reuse in `TokenIssuer`, foreign-country login in `AuthService.login`) act immediately. A user-level Redis ban key cuts live access tokens. Frontend adds `features/security-incidents/` with a browser-print report (same approach as DRS/Load Sheet printing; no PDF library).

**Tech Stack:** Spring Boot 3 / Java 21, JPA + Flyway (MySQL), Redis (`StringRedisTemplate`), MaxMind `com.maxmind.geoip2:geoip2` (GeoLite2-Country file), Angular (signals, OnPush, vitest).

**Spec:** `docs/superpowers/specs/2026-11-04-security-incident-detection-design.md`

## Global Constraints

- Ban = `UserStatus.DISABLED` + all sessions revoked via `SessionService.revokeAllSessions(userId, reason)` (called through the proxy, never `this.`) + user-level Redis ban key + `AuditService` entry. `LOCKED`/`lockedUntil` lockout is untouched.
- Auto-ban ONLY for: `REFRESH_TOKEN_REUSE`, `FOREIGN_LOGIN_CORRECT_PASSWORD`, `FOREIGN_TOKEN_USE`. All other rules create `OPEN` incidents with `auto_action = NONE`.
- Never auto-ban users holding role SUPER_ADMIN or PLATFORM_ADMIN (incident only). A user cannot ban themselves.
- Refresh reuse grace: reuse of a just-rotated token within 10 seconds, same session and same IP, is NOT theft.
- Dismiss suppresses the same (user, rule) for 24 hours. At most one `OPEN` incident per (user, rule).
- Scanner is single-instance (no distributed lock), keeps a cursor, first run starts at deploy time (never scans history).
- Geo: country allowed = `IN`; private/loopback/unresolvable IP => `UNKNOWN` => allowed; geo check property-switchable, default OFF in `local`/`dev`/`test`, ON in `prod`; missing or unreadable DB file must NOT stop boot (log warn, behave as disabled).
- `security_incident.company_id` nullable; FKs target `companies.company_id`, never `companies.id`.
- Do not add JWT claims (header size limit). New permission module `SECURITY_INCIDENT` is COMPANY_ADMIN-only and needs a backfill migration.
- Migration is `V139__security_incident.sql`. `V138` (print log) is still uncommitted in the working tree: commit V138 first, never deploy V139 before V138.
- Working tree contains unrelated uncommitted print-log work. `git add` explicit files only, never `git add -A`.
- Verify on a throwaway `:8082` backend (profile `local`) + `:4300` frontend. Never restart or touch the real backend port or `:4200`. Leave test rows in the dev DB.

## Review Focus

- Client spoofs `X-Forwarded-For: <Indian IP>` to pass the geo check: first header entry is client-controlled (`AuthController.clientIp` takes it today); must resolve the IP from nginx's `X-Real-IP` only when the socket peer is a private/loopback proxy. (Task 2)
- GeoLite2 file missing/corrupt/unconfigured, or IPv6 / IPv4-mapped (`::ffff:1.2.3.4`) / garbage IP string: app still boots and requests never 500; verdict `UNKNOWN`. (Task 3)
- Two browser tabs refresh the same token at once: second call is within grace and must NOT ban. (Task 6)
- Ban called twice, or on an already-DISABLED user, or an incident raised twice by consecutive scans: idempotent, one OPEN incident. (Task 4)
- Redis down during ban: DB ban + session revoke still succeed; failure logged, not thrown. (Task 5)
- Banned COMPANY_ADMIN is the company's only admin: PLATFORM_ADMIN can still list and unban. (Task 10)

---

### Task 1: Incident table, permission module, entity

**Files:**
- Create: `backend/src/main/resources/db/migration/V139__security_incident.sql`
- Create: `backend/src/main/java/com/courier/modules/security/domain/{SecurityIncident,SecurityIncidentRepository,IncidentRule,IncidentStatus,IncidentSeverity,AutoAction}.java`
- Modify: `backend/src/main/java/com/courier/modules/company/domain/PermissionModule.java` (add `SECURITY_INCIDENT("Security Incidents", <next order>)`)
- Test: `backend/src/test/java/com/courier/modules/security/domain/SecurityIncidentRepositoryTest.java`

**Interfaces:**
- Produces: `enum IncidentRule { REFRESH_TOKEN_REUSE, FOREIGN_LOGIN_CORRECT_PASSWORD, FOREIGN_TOKEN_USE, REVOKED_TOKEN_REPLAY, SESSION_AGENT_CHANGE, SESSION_MULTI_IP, BULK_ACTIVITY, ACCESS_DENIED_BURST, CREDENTIAL_ATTACK, FOREIGN_LOGIN_ATTEMPT; boolean autoBan() }` (autoBan true for the first three only); `enum IncidentStatus { OPEN, BANNED, DISMISSED, UNBANNED }`; `enum IncidentSeverity { HIGH, MEDIUM }`; `enum AutoAction { NONE, SESSIONS_REVOKED, USER_DISABLED }`; entity `SecurityIncident` (fields per spec: id, companyId nullable, userId nullable, rule, severity, status, evidence JSON string, autoAction, detectedAt, resolvedBy, resolvedAt, resolutionNote); repository methods `boolean existsByUserIdAndRuleAndStatus(UUID, IncidentRule, IncidentStatus)`, `boolean existsByUserIdAndRuleAndStatusAndResolvedAtAfter(UUID, IncidentRule, IncidentStatus, Instant)`, `Page<SecurityIncident> findAll(Specification, Pageable)`.
- Entity extends `BaseEntity` (not `CompanyOwnedEntity`), explicit company filtering in service, same reason as `ActivityLog`.

- [ ] **Step 1: Write failing repository test** `dedupQueriesFindOpenAndRecentlyDismissed`: save an OPEN incident for (u, REFRESH_TOKEN_REUSE) -> `existsByUserIdAndRuleAndStatus(u, rule, OPEN)` true, other rule false; save a DISMISSED one with `resolvedAt = now-1h` -> `...AndResolvedAtAfter(u, rule, DISMISSED, now-24h)` true, `now-30m` false.
- [ ] **Step 2: Run** `cd backend && mvn -q test -Dtest=SecurityIncidentRepositoryTest` — expect FAIL (types missing).
- [ ] **Step 3: Implement** migration (table + indexes on `(company_id, detected_at)`, `(user_id, rule_code, status)`), enums, entity, repository; add `SECURITY_INCIDENT` permission module and a COMPANY_ADMIN backfill statement in the same migration (copy the pattern the Business Customer migrations V105-V107 / V138 use to grant all actions of a new module).
- [ ] **Step 4: Run** the test — expect PASS; run `mvn -q test` to confirm no existing permission-module test breaks.
- [ ] **Step 5: Commit** `git add <the files above>`; message `feat(security): incident table, rules and permission module (V139)`.

---

### Task 2: Trusted client-IP resolver

**Files:**
- Create: `backend/src/main/java/com/courier/shared/security/ClientIpResolver.java`
- Modify: `backend/src/main/java/com/courier/modules/auth/api/AuthController.java:222` (replace private `clientIp` body with the resolver)
- Test: `backend/src/test/java/com/courier/shared/security/ClientIpResolverTest.java`

**Interfaces:**
- Produces: `@Component class ClientIpResolver { String resolve(HttpServletRequest request) }`.
- Rule: if `request.getRemoteAddr()` is loopback / site-local / link-local (the nginx proxy), return `X-Real-IP` when it is a valid IP literal, else the socket address; otherwise (direct connection) return `getRemoteAddr()` and ignore all forwarding headers. Result truncated to 45 chars.

- [ ] **Step 1: Write tests**: (a) remote `172.18.0.5` + `X-Real-IP: 49.36.1.1` + `X-Forwarded-For: 8.8.8.8, 49.36.1.1` => `49.36.1.1`; (b) remote `203.0.113.9` (public) + `X-Real-IP: 49.36.1.1` => `203.0.113.9`; (c) remote private + `X-Real-IP: not-an-ip` => remote address; (d) remote private, no headers => remote address.
- [ ] **Step 2: Run** `mvn -q test -Dtest=ClientIpResolverTest` — FAIL.
- [ ] **Step 3: Implement** `resolve`; validate IP literal without DNS lookups (use `InetAddresses`-style parsing, no `InetAddress.getByName` on hostnames). Replace `AuthController.clientIp` to delegate. Do not touch other callers (`ActivityLoggingFilter`, `AuditService`) in this task.
- [ ] **Step 4: Run** the test plus `mvn -q test -Dtest='Auth*Test'` — PASS.
- [ ] **Step 5: Commit** `fix(auth): derive client IP from trusted proxy header, not client-supplied X-Forwarded-For`.

---

### Task 3: Geo country service

**Files:**
- Modify: `backend/pom.xml` (add `com.maxmind.geoip2:geoip2`, pin a version at least two weeks old)
- Create: `backend/src/main/java/com/courier/modules/security/application/{SecurityProperties,GeoCountryService}.java`, `.../domain/GeoVerdict.java`
- Modify: `backend/src/main/resources/application.yml` (+ `application-prod.yml`): `courier.security.geo.{enabled,db-path,allowed-country}`; defaults `enabled: false`, `allowed-country: IN`; prod `enabled: true`, `db-path: ${GEO_DB_PATH:}`
- Test: `backend/src/test/java/com/courier/modules/security/application/GeoCountryServiceTest.java`

**Interfaces:**
- Produces: `enum GeoVerdict { ALLOWED, FOREIGN, UNKNOWN }`; `GeoCountryService#GeoVerdict check(String ip)`; `#Optional<String> countryOf(String ip)`; `SecurityProperties` (`@ConfigurationProperties("courier.security")`) exposing `geo`, `graceSeconds=10`, `scanInterval=PT2M`, and per-rule thresholds/windows used by Tasks 8-9 (names defined there).
- Behavior: disabled, null/blank/garbage IP, private/loopback, IPv4-mapped IPv6 handled (unwrap `::ffff:`), DB missing/unreadable => `UNKNOWN` (never throws). Reader is created once on startup in a try/catch.

- [ ] **Step 1: Write tests** with a stub lookup seam (constructor takes a `Function<InetAddress, Optional<String>>`-style `CountryLookup` so tests need no MaxMind file): `enabled=false` => UNKNOWN for any ip; `"10.0.0.1"`, `"127.0.0.1"`, `"::1"`, `"garbage"`, `null` => UNKNOWN; lookup returns `IN` => ALLOWED; returns `US` => FOREIGN; `"::ffff:49.36.1.1"` is unwrapped and looked up as IPv4; lookup throws => UNKNOWN.
- [ ] **Step 2: Run** `mvn -q test -Dtest=GeoCountryServiceTest` — FAIL.
- [ ] **Step 3: Implement** service + properties; production `CountryLookup` wraps `DatabaseReader` opened from `db-path` in `@PostConstruct` inside try/catch (log warn, leave lookup unset).
- [ ] **Step 4: Run** the test and `mvn -q test` boot-level test (existing context test must still start with no DB file) — PASS.
- [ ] **Step 5: Commit** `feat(security): IP country lookup service with fail-open behavior`.

---

### Task 4: Incident service and ban service

**Files:**
- Create: `backend/src/main/java/com/courier/modules/security/application/{SecurityIncidentService,UserBanService}.java`
- Modify: `backend/src/main/java/com/courier/modules/auth/domain/User.java` (add `disable()` / `reactivate()` only if no status mutator exists)
- Modify: `AuditAction.java` (add `USER_BANNED`, `USER_UNBANNED`, `SECURITY_INCIDENT_RAISED` if absent)
- Test: `backend/src/test/java/com/courier/modules/security/application/{SecurityIncidentServiceTest,UserBanServiceTest}.java`

**Interfaces:**
- Consumes: `SessionService.revokeAllSessions(UUID, String)`, `AuditService.record(...)`, `UserRepository`.
- Produces:
  - `UserBanService#void ban(UUID userId, String reason)` — sets DISABLED, revokes all sessions, calls `RevocableTokenStore.banUser` (Task 5), audits; idempotent. `#void unban(UUID userId)` — status ACTIVE, `failedAttempts=0`, `lockedUntil=null`, clears ban key, audits.
  - `record IncidentRequest(UUID companyId, UUID userId, IncidentRule rule, Map<String,Object> evidence)`.
  - `SecurityIncidentService#Optional<SecurityIncident> raise(IncidentRequest)` — returns empty if an OPEN incident exists for (user, rule) or a DISMISSED one resolved within 24h; otherwise saves (severity HIGH iff `rule.autoBan()`); if `rule.autoBan()` and user is eligible (not SUPER_ADMIN/PLATFORM_ADMIN) calls `ban`, sets `autoAction=USER_DISABLED`, status `BANNED`.
  - `#SecurityIncident ban(UUID incidentId, UUID actorId, String note)` (rejects `actorId == incident.userId`), `#dismiss(UUID, UUID, String)`, `#unban(UUID, UUID, String)` (status `UNBANNED`), `#Page<SecurityIncident> list(IncidentFilter, Pageable)`, `#SecurityIncident get(UUID)`. Company isolation: COMPANY_ADMIN sees own `company_id`; PLATFORM_ADMIN sees all including null-company.

- [ ] **Step 1: Write tests** (Mockito): auto-ban rule raises + bans + `autoAction=USER_DISABLED`; non-auto rule leaves user untouched; second `raise` for same open (user,rule) returns empty and saves nothing; raise within 24h of dismissal returns empty; SUPER_ADMIN target gets incident with `autoAction=NONE`, no ban; `ban` by the incident's own user throws `ForbiddenException`; `ban` on already DISABLED user does not throw; `unban` resets counters and sets UNBANNED; company A admin cannot `get` company B's incident (not-found).
- [ ] **Step 2: Run** `mvn -q test -Dtest='SecurityIncidentServiceTest,UserBanServiceTest'` — FAIL.
- [ ] **Step 3: Implement** both services per Interfaces; `raise` and ban run in one transaction; `evidence` serialized with the existing Jackson `ObjectMapper`.
- [ ] **Step 4: Run** the tests — PASS.
- [ ] **Step 5: Commit** `feat(security): incident service and user ban/unban`.

---

### Task 5: User-level ban key cuts live access tokens

**Files:**
- Modify: `backend/src/main/java/com/courier/modules/auth/application/TokenRevocationService.java` (add `banUser`/`unbanUser` to `RevocableTokenStore`), `backend/src/main/java/com/courier/modules/auth/infrastructure/RedisTokenRevocationChecker.java` (implement store + checker), the checker interface in `modules/auth/application/port`, `backend/src/main/java/com/courier/shared/security/JwtAuthenticationFilter.java:67-74`
- Test: `backend/src/test/java/com/courier/modules/auth/infrastructure/RedisTokenRevocationCheckerTest.java`, `backend/src/test/java/com/courier/shared/security/JwtAuthenticationFilterBanTest.java`

**Interfaces:**
- Produces: `RevocableTokenStore#void banUser(UUID userId, Duration ttl)`, `#void unbanUser(UUID userId)`; checker `boolean isUserBanned(UUID userId)`. Redis key `banned-user:<uuid>`, TTL = access-token lifetime. `UserBanService` (Task 4) calls these; failures logged, never thrown (fail-open, matching existing checker).
- Filter change (one extra Redis `hasKey` per authenticated request; deviates from spec's "no filter change" because access-token ids are not tracked per user, so a user-level key is the only way to cut live tokens): `isNotRevoked` also rejects when `isUserBanned(user.userId())`.

- [ ] **Step 1: Write tests**: `banUser` then `isUserBanned` true, `unbanUser` then false; filter with banned user principal does not set authentication; Redis exception => `isUserBanned` false (fail-open) and `banUser` does not throw.
- [ ] **Step 2: Run** `mvn -q test -Dtest='RedisTokenRevocationCheckerTest,JwtAuthenticationFilterBanTest'` — FAIL.
- [ ] **Step 3: Implement** per Interfaces; wire `UserBanService` to call the store.
- [ ] **Step 4: Run** both tests plus existing `JwtAuthenticationFilter*` tests — PASS.
- [ ] **Step 5: Commit** `feat(auth): user-level ban key invalidates live access tokens`.

---

### Task 6: Refresh-token reuse rule

**Files:**
- Modify: `backend/src/main/java/com/courier/modules/auth/application/TokenIssuer.java` (`rotate` / `handleReuse`, around lines 93-97)
- Test: `backend/src/test/java/com/courier/modules/auth/application/TokenIssuerReuseTest.java`

**Interfaces:**
- Consumes: `SecurityIncidentService#raise(IncidentRequest)`, `SecurityProperties#graceSeconds`.
- Behavior: when `stored.isRevoked()` and `stored.getReplacedByJti() != null` (a rotated token, not a logout/eviction revoke): if `now - stored.getRevokedAt() <= graceSeconds` AND same session AND `ipAddress` equals the stored token's `ipAddress` => throw the existing invalid-refresh error WITHOUT calling `handleReuse` and WITHOUT an incident (concurrent refresh); otherwise run existing `handleReuse(stored)` then `raise(REFRESH_TOKEN_REUSE, evidence{familyId, sessionId, presentedFromIp, originalIp, userAgent, revokedAt})`. Revokes caused by logout/session eviction (`replacedByJti == null`) keep today's behavior and raise no incident. `raise` runs in a new transaction (`REQUIRES_NEW`) so the throw does not roll back the ban.

- [ ] **Step 1: Write tests**: (a) rotated token reused after 60s from a different IP => incident `REFRESH_TOKEN_REUSE` raised, user banned, still throws `INVALID_REFRESH_TOKEN`; (b) reused after 3s, same session and IP => throws, no incident, no ban, family NOT destroyed; (c) reused after 3s from a different IP => incident; (d) revoked by logout (`replacedByJti == null`) and presented => throws, no incident.
- [ ] **Step 2: Run** `mvn -q test -Dtest=TokenIssuerReuseTest` — FAIL.
- [ ] **Step 3: Implement** the branch in `rotate`; keep the existing exception types.
- [ ] **Step 4: Run** the test and `mvn -q test -Dtest='Token*Test,Auth*Test'` — PASS.
- [ ] **Step 5: Commit** `feat(security): ban on refresh-token reuse with concurrent-refresh grace window`.

---

### Task 7: Foreign-country login rules

**Files:**
- Modify: `backend/src/main/java/com/courier/modules/auth/application/AuthService.java` (`login`, line 120), `ErrorCode` (add `GEO_RESTRICTED`, mapped to HTTP 403), `AuthController.java` (pass resolved IP already used)
- Test: `backend/src/test/java/com/courier/modules/auth/application/AuthServiceGeoLoginTest.java`

**Interfaces:**
- Consumes: `GeoCountryService#check(String)`, `SecurityIncidentService#raise`, existing password verification in `login`.
- Behavior: when `check(ip) == FOREIGN`, never issue tokens or open a session. Look up the user, verify the password (reuse the existing hash check, no lockout counter increment for foreign attempts): password correct => `raise(FOREIGN_LOGIN_CORRECT_PASSWORD, evidence{ip, country, userAgent, attemptedEmail})` (auto-bans); wrong password or unknown user => `raise(FOREIGN_LOGIN_ATTEMPT, ...)` only when a user was resolved. Both paths throw `ForbiddenException(ErrorCode.GEO_RESTRICTED, "Sign-in is not available from your location.")` — identical message so the response does not reveal whether the password was right. `UNKNOWN`/`ALLOWED` follow the existing flow untouched.

- [ ] **Step 1: Write tests**: foreign + correct password => user DISABLED, incident rule `FOREIGN_LOGIN_CORRECT_PASSWORD`, `GEO_RESTRICTED` thrown, no tokens; foreign + wrong password => user NOT disabled, `FOREIGN_LOGIN_ATTEMPT` incident, `failedAttempts` unchanged, same exception text; foreign + unknown email => exception, no incident; `ALLOWED` and `UNKNOWN` => existing login result unchanged; geo disabled => unchanged.
- [ ] **Step 2: Run** `mvn -q test -Dtest=AuthServiceGeoLoginTest` — FAIL.
- [ ] **Step 3: Implement** the geo branch at the start of `login`, before the normal credential flow.
- [ ] **Step 4: Run** the test and the full existing auth test suite (`mvn -q test -Dtest='Auth*Test'`) — PASS.
- [ ] **Step 5: Commit** `feat(security): block foreign-country logins, ban when credentials are correct`.

---

### Task 8: Scanner skeleton and session rules

**Files:**
- Create: `backend/src/main/java/com/courier/modules/security/application/scan/{SecurityScanJob,ScanRule,ScanCursor}.java`, `.../scan/rules/{SessionAgentChangeRule,SessionMultiIpRule,RevokedTokenReplayRule}.java`
- Create: `backend/src/main/resources/db/migration/V140__security_scan_cursor.sql` (single-row table `security_scan_cursor(id, last_scanned_at)`) — or fold into V139 if V139 is still uncommitted when this task starts
- Test: `backend/src/test/java/com/courier/modules/security/application/scan/*Test.java`

**Interfaces:**
- Produces: `interface ScanRule { IncidentRule code(); List<IncidentRequest> scan(Instant from, Instant to); }`; `SecurityScanJob` (`@Scheduled(fixedDelayString = "${courier.security.scan-interval}")`) iterates all `ScanRule` beans for `[cursor, now]`, calls `SecurityIncidentService#raise` per request, advances the cursor in the same transaction; a failing rule is caught and logged without stopping the others. First run with no cursor row inserts `now` and scans nothing.
- Rules use existing data only: `SESSION_AGENT_CHANGE` compares `ActivityLog.browser/os` per `sessionId` to the `UserSession` row; `SESSION_MULTI_IP` finds a session with 2+ distinct `ActivityLog.ipAddress` inside the window; `REVOKED_TOKEN_REPLAY` counts `ActivityLog`/`LoginHistory` rejections for revoked tokens above `SecurityProperties.revokedReplayThreshold`.

- [ ] **Step 1: Write tests** per rule with seeded `ActivityLog`/`UserSession` rows (positive and negative: same fingerprint => no request; changed browser => one request with evidence containing both values); job test: first run only sets the cursor and raises nothing; second run raises for rows after the cursor; one rule throwing does not block another.
- [ ] **Step 2: Run** `mvn -q test -Dtest='SecurityScanJobTest,SessionAgentChangeRuleTest,SessionMultiIpRuleTest,RevokedTokenReplayRuleTest'` — FAIL.
- [ ] **Step 3: Implement** job, cursor, three rules. All three rules produce non-auto-ban incidents.
- [ ] **Step 4: Run** the tests — PASS.
- [ ] **Step 5: Commit** `feat(security): scheduled scanner with session anomaly rules`.

---

### Task 9: Remaining scanner rules

**Files:**
- Create: `.../scan/rules/{BulkActivityRule,AccessDeniedBurstRule,CredentialAttackRule,ForeignTokenUseRule}.java`
- Modify: `SecurityProperties` (threshold/window fields: `bulkActivity.{threshold,window}`, `accessDenied.{threshold,window}`, `credentialAttack.{distinctAccounts,failuresPerAccount,window}`, `revokedReplayThreshold`)
- Test: matching `*RuleTest.java`

**Interfaces:**
- Consumes: `ScanRule` (Task 8), `GeoCountryService#check`.
- `BulkActivityRule`: per user, count of `ActivityLog` rows with action in {CANCEL, EXPORT, rate-edit actions} above threshold in window => `BULK_ACTIVITY` (builder reads actual action/module values from `ActivityLog` before choosing the match). `AccessDeniedBurstRule`: `status = FAILURE` with 403 in `errorMessage`/endpoint outcome (404 ignored) above threshold => `ACCESS_DENIED_BURST`. `CredentialAttackRule`: `LoginHistory` failures grouped by IP across many accounts, or per account beyond lockout => `CREDENTIAL_ATTACK` with `userId = null` for IP-level, never an auto-ban. `ForeignTokenUseRule`: distinct `ActivityLog.ipAddress` of authenticated rows in window where `check(ip) == FOREIGN` => `FOREIGN_TOKEN_USE` (auto-ban).

- [ ] **Step 1: Write tests**: below-threshold => none; above => one request with counts in evidence; 404s do not count toward denied burst; credential spray from one IP over many accounts => request with null `userId` and no ban; foreign IP in `ActivityLog` with geo stub FOREIGN => `FOREIGN_TOKEN_USE`; geo `UNKNOWN`/`ALLOWED` => none.
- [ ] **Step 2: Run** `mvn -q test -Dtest='*RuleTest'` — FAIL.
- [ ] **Step 3: Implement** the four rules and properties with sensible defaults (document them in `application.yml`).
- [ ] **Step 4: Run** the tests — PASS.
- [ ] **Step 5: Commit** `feat(security): volume, denied-access, credential-attack and foreign-token rules`.

---

### Task 10: Incident REST API

**Files:**
- Create: `backend/src/main/java/com/courier/modules/security/api/{SecurityIncidentController,dto/IncidentResponse,dto/IncidentActionRequest}.java`
- Test: `backend/src/test/java/com/courier/modules/security/api/SecurityIncidentControllerTest.java`

**Interfaces:**
- Consumes: `SecurityIncidentService`. Follows the controller/permission conventions in `PrintLogController` (permission code from the `SECURITY_INCIDENT` module: `_READ`, `_UPDATE`).
- Produces: `GET /api/v1/security/incidents?status=&severity=&rule=&page=` (READ), `GET /api/v1/security/incidents/{id}` (READ; response includes user name/role/branch, parsed evidence, timeline fields), `POST .../{id}/ban`, `.../dismiss`, `.../unban` with body `{ note: string }` (UPDATE). PLATFORM_ADMIN is allowed through role-based access for null-company incidents and platform-admin targets, in addition to the permission check (do not gate on role alone — see USER_READ permission fix precedent).

- [ ] **Step 1: Write tests** (MockMvc): COMPANY_ADMIN lists only own company; other-company id => 404; user without permission => 403; ban/dismiss/unban call the service with the caller as actor; PLATFORM_ADMIN can get and unban a null-company incident.
- [ ] **Step 2: Run** `mvn -q test -Dtest=SecurityIncidentControllerTest` — FAIL.
- [ ] **Step 3: Implement** controller and DTOs.
- [ ] **Step 4: Run** the test plus `mvn -q test` full suite — PASS.
- [ ] **Step 5: Commit** `feat(security): incident API`.

---

### Task 11: Frontend incident list and printable report

**Files:**
- Create: `frontend/src/app/features/security-incidents/{security-incident.service.ts,incident-list.ts,incident-detail.ts,security-incident.model.ts}` + `*.spec.ts`
- Modify: `frontend/src/app/app.routes.ts`, sidebar nav definition (find the file that holds the print-log / Users nav entries), permission constants for `SECURITY_INCIDENT`
- Interfaces: Consumes the Task 10 endpoints. Produces routes `/security/incidents` and `/security/incidents/:id`, sidebar group entry visible to COMPANY_ADMIN and PLATFORM_ADMIN only.

- [ ] **Step 1: Write failing specs**: service builds the right URLs/params; list renders rows and filters by status; detail shows Ban/Dismiss only when `OPEN`, Unban only when `BANNED`; clicking an action calls the service then reloads; lookup maps populated asynchronously are signals (OnPush gotcha).
- [ ] **Step 2: Run** `cd frontend && npx ng test --watch=false` — FAIL for the new specs.
- [ ] **Step 3: Implement** standalone OnPush components using signals; report view uses a print stylesheet and a "Print / Save as PDF" button calling `window.print()` (no new library); follow `print-log/print-history-dialog.ts` for dialog and service conventions.
- [ ] **Step 4: Run** `npx ng test --watch=false` and `npx ng build` — PASS, no new warnings.
- [ ] **Step 5: Commit** `feat(security): incident list, detail and printable report`.

---

### Task 12: Live verification and project memory

**Files:**
- Modify: `MEMORY/AI_CONTEXT.md`, `CHANGELOG.md`, create `MEMORY/modules/security-incidents.md`

- [ ] **Step 1: Boot** a throwaway backend on `:8082` with profile `local` (env vars per the local-dev notes, MySQL root `Root@1234`) and `ng serve` on `:4300`; geo enabled with a stub/test GeoLite2 file or a property override that forces a chosen country for a test IP.
- [ ] **Step 2: Exercise end to end** via the UI quick-fill login: (a) reuse an old refresh token via curl => user banned, incident visible, old access token returns 401; (b) forwarded `X-Real-IP` of a non-Indian address from a loopback caller with correct password => 403 `GEO_RESTRICTED`, user banned; same with wrong password => no ban; (c) spoofed `X-Forwarded-For: <Indian IP>` does not bypass; (d) admin Unban restores login; (e) Dismiss then re-trigger within 24h creates no new incident; (f) run the scanner once and confirm a session-anomaly incident appears. Expected: each outcome as listed, report prints cleanly.
- [ ] **Step 3: Record** the changes, migration numbers, settings (`courier.security.*`), and the MaxMind file requirement (`GEO_DB_PATH`) in memory docs. Leave test rows in the dev DB.
- [ ] **Step 4: Commit** `docs: record security incident detection module`.

## Deployment notes (not tasks)

- Prod needs the GeoLite2-Country file on the EC2 box and `GEO_DB_PATH` set before enabling geo; without it the check stays off (fail-open) by design.
- Deploy only after V138 is committed; V139 (and V140 if separate) follow in order.

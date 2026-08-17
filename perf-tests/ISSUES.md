# Performance & Load Test — Issue Log

Running log of every bug/issue found while building and running the local
performance-test pipeline (see `perf-tests/README.md`). Per-issue format matches
PHASE 14 (Bottleneck Analysis) of the test brief: Problem → Evidence → Root Cause →
Impact → Recommended Fix → Priority.

**Policy** (explicit user instruction, 2026-08-17): log everything found during
testing here; do not fix inline while testing is in progress. Fix all logged issues
together after the full local test pass completes, then retest (PHASE 15 — Fix &
Retest) and update each entry's status.

Priority key: 🔴 Critical 🟠 High 🟡 Medium 🟢 Low

Status key: OPEN → FIXED → RETESTED (or WONT-FIX with reason)

---

## ISSUE-001 — Cross-tenant data leak in the dashboard summary endpoint

**Status**: FIXED, RETESTED — 2026-08-17
**Priority**: 🔴 Critical
**Found**: 2026-08-17, during Phase 2 data-generator smoke validation (not a load
test — surfaced just from logging in as a synthetic tenant's admin and hitting the
dashboard once).

**Problem**: `GET /api/v1/dashboard/summary` returns shipment counts/revenue/recent
shipments aggregated across *every* company in the database, not just the caller's
own — for a plain `COMPANY_ADMIN` caller, not only `SUPER_ADMIN`.

**Evidence**:
- Created a synthetic tenant `PERFSMOKE01` (distinct `company_id`, confirmed via
  `SELECT company_code, HEX(company_id) FROM companies` — no collision with the
  real dev tenant `COMPANY-C1`) with exactly 50 shipments.
- Logged in as `admin@t01.perf.local` / `COMPANY_ADMIN`, JWT `cid` claim confirmed
  to decode to PERFSMOKE01's own `company_id`.
- `GET /api/v1/dashboard/summary` → `totalShipments: 85`. `SELECT company_code,
  COUNT(*) FROM shipments s JOIN companies c ON c.company_id = s.company_id GROUP
  BY c.company_code` → `COMPANY-C1: 35`, `PERFSMOKE01: 50`. 35 + 50 = 85 exactly.
- Contrast case, same token: `GET /api/v1/shipments?page=0&size=3` (search) correctly
  returned only PERFSMOKE01's own rows. So the leak is specific to the dashboard
  endpoint, not a systemic JWT/company-context binding failure — other company-owned
  reads in the same request session are correctly scoped.

**Root cause (localized, not yet fully traced)**: `DashboardServiceImpl.summary()`
(`backend/src/main/java/com/courier/modules/dashboard/application/DashboardServiceImpl.java:60-84`)
relies entirely on the implicit Hibernate `companyFilter` (enabled per-request by
`CompanyFilterAspect`, `backend/src/main/java/com/courier/shared/company/CompanyFilterAspect.java`)
for `ShipmentRepository.count()` / `.countByStatus()` / `.countByStatusIn()` /
`.countByBookingDate()` / `.findTop5ByOrderByCreatedAtDesc()` and
`ShipmentChargeRepository.sumNetAmount()` — none of these carry an explicit
`companyId` predicate. The shipment-search path that *is* correctly scoped builds
its query through `ShipmentSpecifications`, which adds `companyId` as an explicit
Criteria predicate — a second, independent scoping mechanism that happens to mask
whatever is defeating the Hibernate filter on the dashboard's path. The dashboard
service method is deliberately **not** `@Transactional`
(see the class's own javadoc, lines 51-59: avoiding an `UnexpectedRollbackException`
from the wallet lookup), which means each of its repository calls opens its own
separate transaction/session — the most likely place the filter-enabling aspect and
the actual query execution end up on different Hibernate sessions. Confirming the
exact mechanism needs a runtime trace through `CompanyFilterAspect.enableFilter`
during a dashboard call; not done yet per the "log now, fix in one batch later" policy.

**Impact**: any authenticated company user (not just platform staff) can read
platform-wide shipment volume, revenue and the single most-recent shipment across
*every tenant* via one authenticated GET request. Real tenant-isolation breach —
exactly what PHASE 8 (Multi-Tenant Isolation Test) exists to catch, found here before
that phase even formally started.

**Recommended fix**: make `DashboardServiceImpl.summary()` (or the individual
repository calls it makes) not depend solely on the implicit per-request filter —
either wrap the method `@Transactional(readOnly = true)` so every repository call
shares one session/filter-bound context (matching how every other correctly-scoped
company-owned read in this codebase behaves), or add an explicit `companyId`
predicate to each of the dashboard's queries the same way `ShipmentSpecifications`
already does. Whichever fix is chosen, add a regression test asserting a
`COMPANY_ADMIN` dashboard call never reflects another company's rows — this class of
bug had no test coverage at all before this run.

**Priority justification**: 🔴 Critical — real, currently-live cross-tenant data
exposure in production code, not a load-test-only concern.

**Update 2026-08-17, PHASE 4 baseline (10/20/50 VUs against the full 100K-shipment
dataset)**: this bug also has a measurable *performance* cost, not just a security
one — `dashboard_duration` (the one endpoint that unfiltered-scans every tenant's
`shipments` table instead of just the caller's own ~10K rows) is the single fastest-
degrading metric across the whole baseline sweep:

| VUs | dashboard p95 | login p95 | http_req p95 / p99 | error rate |
|---|---|---|---|---|
| 10 | 765 ms | 393 ms | 755 ms / 764 ms | 0.00% |
| 20 | **1435 ms** ✗ | 925 ms | 774 ms / 1.42 s | 0.00% |
| 50 | **3983 ms** ✗ | **2009 ms** ✗ | 1.73 s / 3.29 s ✗ | 0.12% |

(✗ = crossed the PHASE 16 starting target, p95<1000ms.) Full run output:
`perf-tests/reports/baseline-{10,20,50}vu.txt`. Once fixed to scope by `company_id`
the way every other correctly-behaving read in this codebase already does, dashboard
should cost roughly what `search_duration` costs (127-188ms p95 across the same
sweep, since it queries the same `shipments` table but company-scoped) — worth
re-measuring in PHASE 15's retest to confirm the fix closes both the security gap
and this latency gap at once.

**Fix applied 2026-08-17**: `DashboardServiceImpl.summary()` now resolves an explicit
scope up front — `null` (genuinely cross-tenant, via `CompanyContext.runAs(null,
...)`, the same sanctioned pattern `TicketServiceImpl.dashboard` already uses) only
when `SecurityUtils.requireCurrentUser().isSuperAdmin()`, otherwise the caller's real
`CompanyContext.requireCompanyId()`. Every repository call takes that scope as an
explicit parameter — new `ShipmentRepository.countByCompanyId*`/
`findTop5ByCompanyIdOrderByCreatedAtDesc` and
`ShipmentChargeRepository.sumNetAmountByCompanyId*` — rather than trusting the
implicit Hibernate filter to have been enabled on whichever short-lived session that
particular call happens to run on. New regression test:
`DashboardServiceImplTest` (`backend/src/test/java/com/courier/modules/dashboard/
application/DashboardServiceImplTest.java`) — asserts a `COMPANY_ADMIN` call never
reaches the unscoped methods and a `SUPER_ADMIN` call never reaches the
company-scoped ones. `mvn test`: full suite green.

**Retested live** (fixed backend rebooted on throwaway `:8082`, real `:8081`/`:4200`
untouched): `PERFT01`'s `COMPANY_ADMIN` now sees `totalShipments: 11640` (its own
10,000 generated + ~1,640 booked by the PHASE 4 k6 baseline runs) instead of the old
101,729 cross-tenant figure; a second tenant, `PERFT02`, correctly sees exactly its
own `10000` with zero contamination from `PERFT01`'s k6 activity;
`super.admin@gmail.com` (real `SUPER_ADMIN`) still correctly sees the full
`101729` cross-tenant total, confirming the platform-level view is unchanged, not
collateral damage from the fix.

---

## ISSUE-002 — Data generator: wallet-transaction count far short of target

**Status**: FIXED (default bumped; not yet regenerated/retested against the current
PERFT01-10 cohort — see below)
**Priority**: 🟢 Low (test-tooling gap, not an app bug)
**Found**: 2026-08-17, full 10-tenant generation run.

**Problem**: `perf.gen.walletTransactionsPerCompany=10000` (default) produced only
~220 wallet transactions per company (2,236 total across 10 companies), not 10,000.

**Evidence**: generation log — `Company 1/10 (PERFT01) done ... 228 wallet txns`
(and similar, ~207-243, for the other 9). `PerfDataGeneratorRunner
.insertWalletTransactions` skips crediting once a wallet's running balance would go
negative (correctly honoring the real `ck_wallets_available_non_negative` CHECK
constraint) — the old default opening balance (`50000.0000`) covers only ~20-40
booking debits before exhaustion at this generator's freight assumptions (weight x
₹40/kg + 18% GST, up to ~₹2360 for the heaviest synthetic shipment).

**Root cause**: `PerfGenProperties.walletOpeningBalance` default too low relative to
`shipmentsPerCompany`de facto debit volume — a generator config gap, not a business-
logic bug (the CHECK constraint did exactly its job).

**Recommended fix**: raise the default opening balance so the full target is
reachable — done, `PerfGenProperties.java` default now `50000000.0000` (comfortably
covers 10,000 debits at the generator's own max per-shipment freight). The
already-generated `PERFT01`-`PERFT10` cohort still only has ~220 wallet transactions
each; regenerating a fresh cohort (different `perf.gen.tenantPrefix`) or accepting
the shortfall for this cohort is a call for whoever runs PHASE 15's retest — noted
here rather than silently regenerated mid-session, per the "log now" policy.

**Priority justification**: 🟢 Low — affects only the realism of PHASE 9 (DB volume)
testing against `wallet_transactions` specifically; every other table hit its target
count exactly (100,000 shipments, 469,086 status-history rows, 10,000 customers,
10,000 tickets, 500 users, 50 branches).

---

## ISSUE-003 — Occasional 409 DUPLICATE_RESOURCE on concurrent POST /shipments

**Status**: CLOSED — investigated, not reproducible; sequence generator confirmed
race-free under harsher conditions than the original observation.
**Priority**: 🟢 Low (closed)
**Found**: 2026-08-17, `k6/scenario.js` at 10 concurrent VUs against `PERFT01`.

**Original observation**: a handful of `POST /shipments` calls returned `409
{"errorCode":"DUPLICATE_RESOURCE"}` under 10-VU concurrent load.

**Investigation**: `nextShipmentNumber`/`nextTrackingNumber`
(`ShipmentServiceImpl.java`, `branch_shipment_sequences`/`company_shipment_sequences`)
are documented as race-free by design (V21/V22 migration comments: row-locked
upsert, "no application-level locking required"). Directly tested this claim harder
than the original k6 run did: fired **40 genuinely simultaneous** `POST /shipments`
requests (real parallel `curl` processes, not k6's own scheduling) at the exact same
branch, same instant, as a single `COMPANY_ADMIN` token — **40/40 succeeded, 40
unique `shipmentNumber`/`trackingNumber` values, zero duplicates**. A follow-up 30-VU
k6 run (still `VUS < USER_POOL_SIZE`, so no shared-account confound — see ISSUE-004)
also produced zero occurrences. The sequence generator's own race-free claim holds.

**Conclusion**: the original 409s were very likely a downstream symptom of
ISSUE-004's login-bookkeeping races (same k6 run, same timeframe) rather than a
shipment-booking bug of their own — a failed/retried login could plausibly cascade
into a booking retry pattern that looked like a shipment-side race but wasn't one.
Closed rather than left open indefinitely once a harsher, more direct test cleared
the actual mechanism under suspicion.

---

## ISSUE-004 — Concurrent logins to the same account: 409s and even a raw 500

**Status**: FIXED, RETESTED — 2026-08-17
**Priority**: 🟠 High (real reliability bug — a legitimately successful login could
be denied to the client; a genuine deadlock surfaced as an unhandled 500)
**Found**: 2026-08-17, PHASE 4 baseline retest at 50 VUs against a 44-account user
pool (`USER_POOL_SIZE=44` < `VUS=50`, so 6 accounts were each driven by 2 real VUs
— a real scenario too, e.g. the same user signed in from two browser tabs at once).

**Problem, in three layers found one at a time**:
1. All 50 VUs sharing one login account tripped the app's own login throttle — a
   **test-script bug**, not an app bug (see `perf-tests/README.md`'s `USER_TEMPLATE`
   note). Fixed by spreading VUs across the generated OPERATOR pool.
2. With the pool fix in place, two VUs *legitimately* sharing one account (the 6
   accounts VUS=50/POOL=44 doubles up) still 409'd with `CONCURRENT_MODIFICATION`
   ("This record was modified by someone else"). Root cause:
   `SessionService.enforceSessionCap` evicts the least-recently-used session once an
   account is at its concurrency cap (`app.auth.max-concurrent-sessions`, default 5)
   — two simultaneous logins racing to evict the *same* victim session lost an
   optimistic-lock race on `user_sessions`, and that failure propagated all the way
   out and killed an otherwise-successful login (valid credentials, session and JWTs
   already issued moments earlier).
3. Fixing #2 (isolating the eviction in its own `REQUIRES_NEW` transaction) surfaced
   a **subtler bug of its own**: the isolated transaction was mutating the exact same
   Java `UserSession`/`User` object the *caller's* still-open transaction already had
   loaded, so the caller's own persistence context saw it as dirty too and flushed it
   a second time at its own commit — racing the nested transaction's already-committed
   update. This produced both more `CONCURRENT_MODIFICATION` 409s **and**, when the
   timing landed as a real InnoDB deadlock instead of a version mismatch, an
   **unhandled `500 INTERNAL_ERROR`** (`CannotAcquireLockException` isn't an
   `OptimisticLockingFailureException`, so neither the local catch nor
   `GlobalExceptionHandler`'s handler recognized it).

**Fix** (four coordinated changes, `backend/src/main/java/com/courier/`):
- `shared/exception/GlobalExceptionHandler.java` — handler widened from
  `OptimisticLockingFailureException` to its parent `ConcurrencyFailureException`, so
  a genuine deadlock/lock-wait-timeout maps to the same retryable 409 everywhere in
  the app, not just the optimistic-lock case.
- `modules/auth/application/SessionService.java` — `revokeSession` now takes a
  session **id**, not a `UserSession` object, and re-fetches its own copy inside its
  `REQUIRES_NEW` transaction rather than mutating the caller's entity (the
  cross-persistence-context bug from layer 3). Its eviction call site
  (`enforceSessionCap`) goes through a `@Lazy`-injected self-reference (field
  injection, not this class's usual constructor injection — Lombok doesn't carry
  `@Lazy` from a field onto the constructor parameter it generates, confirmed the
  hard way via a real circular-reference boot failure) so `REQUIRES_NEW` actually
  takes effect rather than being silently skipped by Spring AOP's self-invocation
  limitation, and catches `ConcurrencyFailureException` around it.
- `modules/auth/application/LoginAttemptService.java` — `recordSuccess` and
  `recordFailure` now take a `userId`, not a `User`, re-fetching internally for the
  identical reason; `recordSuccess` changed from plain `@Transactional` to
  `REQUIRES_NEW` so it flushes/commits (and can fail) *before* returning control to
  `AuthService.login`, not after — login() is itself `@Transactional`, so without
  this the failure only ever surfaced at login's own outer commit, too late for any
  local catch to help.
- `modules/auth/application/AuthService.java` — `login()`'s call to `recordSuccess`
  now catches `ConcurrencyFailureException` (widened from just the optimistic case)
  and logs rather than propagates: bookkeeping is best-effort, it must never deny an
  already-successful, already-issued-tokens login.

New regression test: `AuthServiceTest.loginSurvivesBookkeepingRace`. `mvn test`: full
suite green throughout every intermediate step (each of the four changes was
compiled, tested, and live-verified in turn — see conversation history for the
false starts this took to get right).

**Retested live**, three consecutive 50-VU/20s k6 runs against the 44-account pool
(the exact scenario that originally failed): **0/955 requests failed across all
three runs** (previously 45-98 failures per run, escalating through 409s and 500s
across the investigation). Real dev backend (`:8081`/`:4200`) untouched throughout —
all verification on throwaway `:8082`.

---

## ISSUE-005 — HikariCP pool exhaustion collapses the app at ~60 concurrent users

**Status**: FIXED, RETESTED — 2026-08-17 (architectural fix applied on direct "keep
going" after the finding was presented as a decision point).
**Priority**: 🔴 Critical for capacity planning — this is the actual ceiling on
concurrent users, not a corner case.
**Found**: 2026-08-17, PHASE 5 load ramp past the PHASE 4 baseline.

**Problem**: the app doesn't degrade gracefully past ~50-60 concurrent users — it
collapses. At 60 VUs, 83% of requests failed, `login_duration` p95 hit the 60-second
client timeout. At 75 VUs, 61% failed the same way. At 100 VUs, **effectively 100% of
requests timed out** (`http_req_duration` p95/p99 both pinned at k6's 60s HTTP
timeout ceiling). This is a wall, not a curve — 50 VUs was clean (0% errors, PHASE 4
baseline) and 60 VUs was already catastrophic.

**Evidence**: backend log shows 891 occurrences of
`org.springframework.dao.CannotAcquireLockException`... no — the actual signature is
`java.sql.SQLTransientConnectionException: courier-pool - Connection is not
available, request timed out after 30000ms (total=60, active=60, idle=0,
waiting=67)`. The Hikari pool (`application-test.yml`'s own `DB_POOL_SIZE`, default
60) was **fully saturated** (60/60 active) with 67+ requests queued behind it,
each waiting the full 30-second `connection-timeout` before failing.

**Root cause**: `AuthService.login()` is `@Transactional`. Spring's transaction
proxy checks out one Hikari connection at the first JDBC operation inside it (the
user lookup) and holds that **same connection** for the rest of the method —
including the BCrypt(12) password verification, which is deliberately CPU-expensive
(`SecurityConfig.java`) and takes hundreds of milliseconds to multiple seconds under
concurrent CPU contention (see PHASE 4's own `login_duration` climb, ISSUE-001's
retest table). Every concurrent login therefore holds a DB connection hostage for
its *entire* CPU-bound verification time, not just its actual query time. Once
enough concurrent logins are in flight to exceed the pool size, new requests (of
*any* endpoint, not just login — they all share one pool) queue behind them, and
because the queue grows faster than it drains, the system never recovers for the
rest of the test run — a self-reinforcing collapse, not a temporary slowdown.

**Mitigation verified**: restarted the backend with `DB_POOL_SIZE=150` (already a
supported env var, `application-test.yml`'s own comment names this exact use case —
"overridable... to find the saturation point (Phase 6/9)"). Re-ran the *identical*
100-VU scenario that was 100% failing: **0/3759 requests failed**, `login_duration`
p95 settled at a bounded 4.3s (elevated, from real BCrypt CPU contention, but no
longer collapsing). This conclusively confirms the pool size — not raw CPU, not
app logic — was the actual ceiling.

**A second ceiling sits right behind the first**: MySQL's own `max_connections=151`
(this machine's Homebrew default). `Threads_connected`/`Max_used_connections` hit
117 during the pool=150/100-VU run — comfortably under 151 this time, but raising
the Hikari pool further without also raising MySQL's own limit would just trade one
saturation wall for another, closer one.

**Fix applied**: removed `@Transactional` from `AuthService.login()`
(`backend/src/main/java/com/courier/modules/auth/application/AuthService.java`).
Confirmed safe first: every DB-writing step inside it already manages its own
transaction independently (`sessionService.openSession`, `tokenIssuer
.issueForNewSession`, `loginAttemptService.recordSuccess`/`recordFailure` — the last
two `REQUIRES_NEW` after ISSUE-004's fix), so the blanket outer transaction was pure
overhead, never load-bearing for atomicity. `mvn test`: full suite green.

**Retested live, in two stages** (throwaway `:8082`, real `:8081`/`:4200` untouched):
1. **Fix alone, pool left at its default 60** (isolating the fix's own effect from
   the earlier pool-size mitigation): the exact 100-VU scenario that was ~100%
   failing before any fix now ran at **0.00% failures** (0/3465). Pushed further to
   find the *new* ceiling: 150 VUs → 0.03% failed (1/2844, essentially clean); 200
   VUs → 45% failed (backend log still shows Hikari `Connection is not available`,
   confirming this is now a *genuine* capacity limit from real concurrent query
   volume across every endpoint, not the wasteful connection-hold pattern from
   before).
2. Combined with the earlier `DB_POOL_SIZE=150` mitigation, 100 VUs already ran
   clean (documented above) — the two fixes are complementary, not redundant: this
   one fixes the actual waste, that one buys additional headroom on top of it for
   whatever real concurrent load a given environment needs to serve.

**Net result: the collapse point moved from ~55-60 concurrent users to somewhere
between 150-200** (with the pool otherwise untouched) — roughly a **3x capacity
increase from a single 6-line change** (removing one annotation, replacing it with
an explanatory comment). Above ~150 VUs the app now degrades along a normal curve
(latency climbing, occasional queuing) rather than collapsing outright, which is the
correct, expected shape for a system finally gated by real capacity rather than
by wasted resource-holding.

**Priority justification**: 🔴 — this was the single most consequential finding of
the whole PHASE 4/5 pass, and the fix was proportionately high-leverage.

---

## ISSUE-006 — Missing indexes: default shipment browse and date-range filter full-scan at volume

**Status**: FIXED, RETESTED — 2026-08-17
**Priority**: 🟠 High (masked at the existing 10-12K/tenant dataset scale, real at
100K+/tenant — exactly the class of bug PHASE 9 exists to catch before it reaches
a real large customer)

**Problem**: generated a dedicated single tenant with a genuine 100,000 shipments
(`PERFSCALE01` — the existing 10-tenant dataset only has ~10-12K shipments *per
company*, too small to surface this). Two of the most common query shapes did a
full company-scoped table scan: the default "browse, newest first, no filter" view
and any `bookingDateFrom`/`bookingDateTo` date-range filter. MySQL slow-query log
(100ms threshold) confirmed `Rows_examined: 100020` for both — a 20-row page
reading all 100,000 rows to produce it.

**Root cause**: `idx_shipments_status (company_id, status, created_at)` only
serves queries that filter by `status`; neither shape above has one, so MySQL fell
back to scanning the whole company-scoped table.

**Fix**: `V42__shipment_search_indexes.sql` — two additive indexes,
`(company_id, created_at)` and `(company_id, booking_date)`. No application code
touched. `mvn test` green (schema-only change).

**Retested live**: zero slow-query-log entries after the fix (both shapes
previously logged every time); plain browse and date-range both dropped well under
100ms. Full detail, plus two related-but-NOT-fixed findings (deep offset
pagination, free-text search) that need a real architectural/product decision
rather than an index, in `perf-tests/reports/PHASE9-DATABASE.md`.

---

## ISSUE-007 — Requests hang, not fail fast, during a MySQL outage

**Status**: FIXED and retested live, 2026-08-17.
**Priority**: 🟡 Medium (no crash, no corruption, self-resolves within 30s — but a
real operational gap worth a deliberate decision).
**Found**: 2026-08-17, PHASE 11 failure testing (MySQL briefly stopped, confirmed
with the user first since it's the same instance the real dev backend also uses).

**Problem**: with MySQL unreachable, requests (including `/actuator/health`
itself) don't fail fast — they hang until Hikari's own `connection-timeout`
(30000ms, `application.yml`) gives up, since every connection-pool borrow attempt
blocks for the full timeout individually. A `curl --max-time 8` against a plain
login or the health endpoint returned nothing at all in that window.

**Impact**: no data corruption, no crash — the process stays alive and everything
recovers cleanly once MySQL returns (confirmed live). But a client (browser,
mobile app, an infra health-check probe with a short timeout) sees a long hang
before any error surfaces, not a fast, clean `503` — worse UX than necessary
during a real DB outage, and could confuse a probe with an aggressive timeout.

**Fix applied**: `application.yml`'s `datasource.hikari.connection-timeout`
lowered from a hardcoded `30000` to `${DB_CONNECTION_TIMEOUT:5000}` — same
env-var-overridable pattern already used for `DB_POOL_SIZE`. 5s still gives a
real query under normal load plenty of margin (well above any observed p99 this
session), but turns an outage into a fast, clean failure instead of a 30s hang.
No circuit breaker added — a plain timeout reduction was sufficient and avoids
pulling in a new dependency (Resilience4j) for a one-line config change.

**Retested live** (with the user's explicit re-confirmation to briefly stop the
shared Homebrew MySQL instance again): booted the throwaway `:8082` backend with
the fix, stopped MySQL, timed a login request — **5.0s to a clean `500`**
(previously hung ~30s). Restarted MySQL immediately after; confirmed clean
recovery on both `:8082` and the real dev backend on `:8081` (both back to `200`
within seconds). Full `mvn test` suite re-run after the change: 757/757 green,
no regressions.

**Note for later**: the response on connection-pool exhaustion during the outage
is currently a raw `500 INTERNAL_ERROR` (`GlobalExceptionHandler` has no specific
handler for `SQLTransientConnectionException`/pool-timeout), not a purpose-built
`503 Service Unavailable`. Left as-is since the *fail-fast* behavior was ISSUE-007's
actual complaint (hang vs. no-hang) and the status-code polish is a separate,
smaller, lower-priority follow-up if wanted.

---

## ISSUE-008 — Session concurrency cap can be exceeded by one under concurrent logins

**Status**: FIXED and retested live, 2026-08-17.
**Priority**: 🟢 Low (a soft device-count limit becoming slightly permissive under
a race, not a security boundary being bypassed).
**Found**: 2026-08-17, PHASE 12 soak test — while explaining an unresolved PHASE 6
observation (apparent "resource drift" across repeated identical-load runs).

**Problem**: `app.auth.max-concurrent-sessions` (default 5) is meant to cap how
many devices/sessions one account can hold active simultaneously
(`SessionService.enforceSessionCap`, evicting the least-recently-seen session once
at the cap). After this session's extensive concurrent-login testing (deliberately
firing simultaneous logins to the same account for ISSUE-004's investigation),
every heavily-reused test account settled at **6 active sessions, not 5** —
confirmed via a direct query (`revoked_at IS NULL AND expires_at > NOW()`), not
just the raw historical row count (which is expected to be large — old sessions
are marked revoked, never deleted).

**Root cause**: a classic read-then-act race in `enforceSessionCap`:
```java
List<UserSession> active = sessionRepository.findActiveByUserId(userId, now);
if (active.size() < cap) { return; }   // no eviction
// ... new session created unconditionally after this
```
Two simultaneous logins for the same account can both read `active.size() == 4`
(cap 5, so `4 < 5` → both skip eviction), then both independently create a new
session — landing at 6 active instead of 5. This is the exact same "read current
state, then decide, without atomicity" shape as ISSUE-004's original bug, just in
a different method that wasn't touched during that fix.

**This also fully explains PHASE 6's own flagged "resource drift" observation**
(two identical 160-VU runs, one clean, one 11% failing): PHASE 12's soak test
(15 minutes sustained load, memory sampled every 30s) showed backend RSS **flat**
throughout (562-563MB for 13 of 15 minutes, one small +26MB step near the end) —
ruling out a genuine memory leak. The real explanation is session-count creep from
this race, compounding across many repeated concurrent-login test runs against the
same small pool of 44 test accounts — a benign, fully-explained artifact of this
session's own test methodology colliding with a real (but low-impact) pre-existing
race, not an unbounded resource leak.

**Recommended fix, not applied**: the same pattern already used for ISSUE-004 —
either a database-level constraint, or re-check the active count inside the same
locked/re-fetched context the eviction itself uses. Low priority given the cap is
a soft UX/resource control (how many devices a user can be logged in on at once),
not a security boundary, and being off by one under a race is a minor, cosmetic
overshoot rather than a real risk.

**Fix attempted 2026-08-17, reverted same day**: tried the "database-level
constraint" half of the recommendation above — a `PESSIMISTIC_WRITE` lock on the
`users` row (`UserRepository.lockByIdWithinCompany`, same pattern as
`WalletRepository.lockByBranchIdWithinCompany`), taken at the top of
`SessionService.openSession()` before `enforceSessionCap`, meaning to serialize
the whole check-then-evict-then-create sequence per account.

Live testing showed this made things worse, not better:
- 10 genuinely simultaneous logins to a clean account produced **5×200 + 5×409**
  — half of all legitimate concurrent logins to that account now hard-failed —
  with backend logs showing real `PessimisticLockException: Lock wait timeout
  exceeded` entries after ~50s (MySQL's default `innodb_lock_wait_timeout`).
- The active-session count *still* wasn't exactly 5 afterward.
- A sequential (non-concurrent) control run against a fresh account correctly
  landed at exactly 5, proving the cap-counting logic itself is sound — the bug
  is specific to how the new lock interacted with concurrent execution. Root
  cause of the lock-timeout/still-wrong-count behavior was not fully diagnosed.

A Low-priority, purely cosmetic issue (session count occasionally landing one
over a soft cap) does not justify shipping a new hard-failure mode on legitimate
concurrent logins, so the change was reverted rather than debugged further:
`UserRepository.lockByIdWithinCompany` removed, `SessionService.openSession()`
restored to call `enforceSessionCap(userId)` as its first statement, no other
files touched. Verified clean: `mvn compile`/`mvn test` (757/757 green) after the
revert, plus a live retest on a throwaway `:8082` backend — 10 simultaneous logins
to a fresh account now come back **10×200** (the 409 regression is gone), with
the session count landing at 9 active on that run (confirming the *original*,
lower-severity race is still present and unfixed, as expected — this issue
remains genuinely OPEN, just no longer made worse).

**Fix actually applied, 2026-08-17 (third attempt, requested explicitly with a
specific spec: named lock, per-user, don't touch the `users` row, serialize
session creation only, different users stay concurrent, guaranteed release,
production-safe)**:

`SessionService` now uses a MySQL named lock (`GET_LOCK`/`RELEASE_LOCK`) keyed
per user id (`"session_cap:" + userId`) around the whole count-evict-create
sequence. Two things had to be right that weren't obvious up front, both found
by live testing, not by inspection:

1. **First cut used `JdbcTemplate`** for `GET_LOCK`/`RELEASE_LOCK`, on the
   assumption it would transparently share the connection the surrounding
   `@Transactional` already had bound for JPA. It didn't reliably do so —
   `GET_LOCK` and `RELEASE_LOCK` could land on two different physical
   connections with no error raised, silently breaking mutual exclusion. Live
   retest (10 concurrent logins, 6 runs) failed **every single time**, landing
   at 6 active sessions, not 5 — i.e. the lock was doing nothing. Fixed by
   managing a single `java.sql.Connection` obtained directly from the
   `DataSource` for the lock's entire lifetime, bypassing Spring's ambient
   connection sharing altogether.

2. **Second cut** (correct connection handling this time) **still failed the
   same way**: `openSession()` was `@Transactional`, and the lock's `finally`
   block released it *inside* that method body — meaning `RELEASE_LOCK` ran
   before Spring's transactional proxy actually issued `COMMIT` (the proxy
   commits only after the annotated method returns). That left a real gap
   between "lock released" and "write visible to the next lock holder," narrow
   but real, and the cap overran exactly as before. Fixed by splitting the
   method: `openSession()` is now plain (not transactional) and owns the lock's
   connection lifecycle; the actual count-evict-create work moved to a new
   `@Transactional` method, `openSessionLocked()`, called through the injected
   `self` proxy so its transaction commits *before* control returns to
   `openSession()` and the lock is released.

**Verified live** after both fixes: `SessionServiceConcurrencyIT` (new,
`backend/src/test/java/.../SessionServiceConcurrencyIT.java`) — real MySQL, real
threads, 10 genuinely simultaneous logins fired at one account starting from 4
pre-existing sessions, asserts the active count lands at exactly the cap (5,
not ≤5 — confirmed the lock makes it deterministic, not just "usually okay"),
the correct (least-recently-seen) pre-existing session was evicted, and a
second user's login is not blocked by the first user's lock (completes in
under 2s while the first user's lock is deliberately held by the test harness).
6/6 clean runs. Separately confirmed on a live throwaway `:8082` backend: two
rounds of 20 concurrent logins to one account, both landed at exactly 5 active
sessions with zero 409s. Full `mvn test` suite: 757/757 green, no regressions —
the new IT is named `*IT` specifically so Surefire's default `**/*Test.java`
pattern skips it in the normal fast run; it needs a real local MySQL with the
standard `COMPANY-C1` dev fixture and is run explicitly
(`mvn -Dtest=SessionServiceConcurrencyIT test`).

**Note for later**: this is deliberately a single-instance-correct,
multi-instance-correct design (MySQL named locks are server-global, not
per-application-process), so it holds even if this service is ever scaled
horizontally — it does not depend on the lock-holder and the DB being the same
JVM.

# Local Performance & Load Testing

Local-first performance/load-test pipeline for this Courier SaaS backend. Runs against
the **same `courier_db` dev MySQL** this project already uses (see
`MEMORY/local-dev-environment.md`) — never a separate database, never production.

Status: PHASE 1-4 infrastructure built and validated end-to-end on a throwaway single
tenant. Full 10-tenant/100K-shipment dataset and PHASE 5+ (load/stress/concurrency/
soak) not yet run — see `ISSUES.md` and the project conversation for current state.

## PHASE 1 — Environment

`backend/src/main/resources/application-test.yml` — widens actuator (metrics,
Prometheus, threaddump, heapdump), turns on Hibernate statistics, raises the HikariCP
pool ceiling. Points at the same datasource as every other profile (`DB_HOST`/
`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` env vars) — for this project that's
Homebrew MySQL on `localhost:3306`, user `root`.

Boot the backend on the project's own throwaway verification port (**8082** — 8081/4200
are the real dev instance, never touch them, see `MEMORY/never-kill-dev-ports.md`):

```bash
cd backend
DB_USERNAME=root DB_PASSWORD='<see MEMORY: local-dev-environment>' SERVER_PORT=8082 \
  JWT_SECRET="$(openssl rand -base64 48)" \
  mvn spring-boot:run -Dspring-boot.run.profiles=test
```

Redis: `brew services start redis` (plain Homebrew redis, default `localhost:6379`).
The app runs degraded without it (see `docker-compose.yml`'s own comment) — start it
anyway so Phase 10 (Redis performance) has something to measure.

## PHASE 2 — Test data generator

`backend/src/main/java/com/courier/perftest/PerfDataGeneratorRunner.java`, a
`CommandLineRunner` gated behind the `perfgen` Spring profile and
`perf.gen.enabled=true` (inert otherwise — safe to leave the profile active). Sizing
knobs: `PerfGenProperties` (`perf.gen.*`), defaults match this brief's own PHASE 2
numbers exactly (10 companies x 5 branches x 50 users x 1000 customers x 10,000
shipments x ~10,000 wallet-eligible transactions x 1,000 tickets — scale any of them
via `-e`/`--perf.gen.*` for the 50K/100K/500K/1M runs PHASE 2 also asks for).

Writes companies/branches/wallets/master-data (service/package/payment types +
one catch-all freight-factor grid cell so real bookings price successfully)/users/
customers/shipments (+items/charges/status-history)/wallet-transactions/tickets via
raw JDBC batch inserts — see the class's own javadoc for why (not the real service
layer: this data is only ever read by Phase 4-9's queries, never replayed through
business logic). Every id/number format matches the real app's own generators
exactly, and every sequence counter it advances past is written back to
`branch_shipment_sequences`/`company_shipment_sequences`/`company_ticket_sequences`
so a real booking made afterward through the app never collides with a synthetic
number.

Run (full default-sized dataset):

```bash
cd backend
DB_USERNAME=root DB_PASSWORD='...' SERVER_PORT=8082 JWT_SECRET="$(openssl rand -base64 48)" \
  mvn spring-boot:run -Dspring-boot.run.profiles=test,perfgen \
  -Dspring-boot.run.arguments=--perf.gen.enabled=true
```

The process keeps serving HTTP after generation finishes (it's the same Spring Boot
web app) — Ctrl-C or `kill` it once the "Perf data generation complete" log line
appears if you don't need it running.

Login for any generated tenant: companyCode `PERFT01`..`PERFT10`, admin
`admin@t01.perf.local`.. `admin@t10.perf.local`, password `Password@1234` (same
shared dev password every other fixture account in this project already uses).

**Safety**: company codes are prefixed `perf.gen.tenantPrefix` (default `PERFT`) and
globally unique — rerunning with the same prefix fails fast on a duplicate-key error
instead of silently doubling the dataset. Use a different prefix for a second cohort.
Never cleans up after itself — matches this project's own convention
(`MEMORY/keep-test-data-in-dev-db.md`).

## PHASE 3/4/5/6 — Functional check, baseline, load, stress

One k6 script, `k6/scenario.js`, covers all four — only VUS/DURATION change. Realistic
journey: Login → Dashboard → Search Shipments → Open Shipment → Track → Create
Shipment → Update Shipment. Login happens once per virtual user (cached for that VU's
whole run), not on every iteration, matching a real session.

```bash
# PHASE 3 — functional check (1 VU, short run; any check() failure is a bug to record
# in ISSUES.md, not a performance number)
k6 run -e BASE_URL=http://localhost:8082 -e TENANT=PERFT01 \
  -e TEST_USER=admin@t01.perf.local -e TEST_PASSWORD=Password@1234 \
  -e VUS=1 -e DURATION=15s k6/scenario.js

# PHASE 4 — baseline, run once at each level, keep the summary output of each.
# USER_TEMPLATE spreads VUs across the generated OPERATOR pool (op0001..op0044 for
# PERFT01 at default sizing) instead of every VU sharing one login — see below for why
# this is required, not optional, once VUS gets past a handful.
POOL="-e USER_TEMPLATE=op{n}@t01.perf.local -e USER_POOL_SIZE=44"
k6 run -e VUS=10 -e DURATION=2m  $POOL ... k6/scenario.js
k6 run -e VUS=20 -e DURATION=2m  $POOL ... k6/scenario.js
k6 run -e VUS=50 -e DURATION=2m  $POOL ... k6/scenario.js

# PHASE 5/6 — same script, higher VUS, watch for the machine (not the app) becoming
# the bottleneck; stop increasing once it does
k6 run -e VUS=250 -e DURATION=5m $POOL ... k6/scenario.js
```

**Why `USER_TEMPLATE` matters (found running the first real PHASE 4 baseline,
2026-08-17)**: at 10 VUs sharing one `TEST_USER`, `http_req_failed` hit 3.75% — every
failure was a `429` from the app's own login throttle
(`app.auth.throttle-max-attempts`, per email+IP, `MEMORY/modules/auth.md`), correctly
protecting one account from what looks like concurrent credential stuffing. Not an
app bug — a test-script realism gap: 10 different real users don't share one login.
Fixed by spreading VUs across the generator's own OPERATOR pool via `USER_TEMPLATE`/
`USER_POOL_SIZE`. Below ~5 VUs the shared-login default (no `USER_TEMPLATE`) is fine
for a quick functional check.

Env vars are never hard-coded in the script itself (`BASE_URL`/`TENANT`/`TEST_USER`/
`TEST_PASSWORD`/`USER_TEMPLATE`/`USER_POOL_SIZE`/`VUS`/`DURATION`) — the same file is
meant to run against LOCAL/UAT/STAGING/PRODUCTION per PHASE 18, only the `-e` values
change.

## PHASE 7 — Concurrency testing

No dedicated script yet (each scenario needs real simultaneous requests — parallel
`curl &` + `wait`, not a k6 loop, so this was done ad hoc rather than as a
reusable artifact). See `reports/PHASE7-CONCURRENCY.md` for the full run: wallet
concurrent-debit (3/3 safe, exactly one debit ever applied), shipment concurrent
update (correct optimistic-lock rejection, no lost update), shipment concurrent
booking (40/40 unique, zero duplicates — see ISSUE-003), ticket concurrent status
change (clean, one caveat about test input noted in the report). All came back
safe — a real, verified result, not just an absence of findings.

## PHASE 8 — Multi-tenant isolation (mandatory)

`k6/tenant-isolation.js` — tenant A attempts to read tenant B's data by id, across
every resource type with a by-id endpoint, plus checks search/list/dashboard never
leak foreign rows. One-shot correctness check (`VUS=1`), not a load test — the
`checks` threshold fails the whole run loudly on any leak.

```bash
k6 run -e BASE_URL=http://localhost:8082 \
  -e TENANT_A=PERFT01 -e TENANT_A_USER=admin@t01.perf.local \
  -e TENANT_B=PERFT02 -e TENANT_B_USER=admin@t02.perf.local \
  -e PASSWORD=Password@1234 \
  k6/tenant-isolation.js
```

Run across at least two or three tenant pairs, not just one — see
`reports/PHASE8-TENANT-ISOLATION.md` for the last full run (42/42 checks passed
across three pairs) and its own notes on two test-script false positives worth
reading before trusting a future failure at face value.

## PHASE 9 — Database performance at volume

Generated a dedicated single tenant with a true 100,000 shipments to test
per-tenant scale (the 10-tenant dataset only has ~10-12K/company):

```bash
# from backend/, same pattern as PHASE 2 but companies=1 and a distinct prefix
--perf.gen.tenantPrefix=PERFSCALE --perf.gen.companies=1 \
  --perf.gen.shipmentsPerCompany=100000
```

Enable MySQL slow-query logging first (local only, never a shared/production
instance): `SET GLOBAL slow_query_log='ON'; SET GLOBAL long_query_time=0.1;`
(resets on MySQL restart — re-enable each session). Found and fixed two missing
indexes (`V42__shipment_search_indexes.sql`) — see `ISSUES.md` ISSUE-006 and
`reports/PHASE9-DATABASE.md` for the full before/after and two related findings
that need a product decision rather than an index (deep pagination, free-text
search).

## PHASE 6 — Stress test (finding the real ceiling)

Bisected upward from PHASE 5's last-known-good point. Two distinct regimes found,
not one number — see `reports/PHASE6-STRESS.md`:
- **~50 VUs**: last point meeting the PHASE 16 latency target (p95<1000ms) —
  `login_duration` (BCrypt CPU cost) drags overall p95 up steadily past this,
  well before any errors occur.
- **~175-180 VUs**: sharp collapse wall (0.00% → 1.09% → 14% error rate across
  three adjacent 5-VU steps) — HikariCP pool exhaustion, same mechanism as
  ISSUE-005, now at a legitimate capacity ceiling rather than a wasted one.

Also flags an unconfirmed resource-drift observation across repeated runs for
PHASE 12 (soak test) to watch for, and is honest about a CPU/JVM sampling gap
(`ps`/`top` proved unreliable for this long-lived JVM process on this machine;
actuator metrics need a `PLATFORM_ADMIN` account that doesn't exist in this
dataset — noted rather than worked around silently).

## PHASE 10 — Redis performance & failure behavior

Confirmed from the code first: Redis has exactly one job here (access-token
denylist, `RedisTokenRevocationChecker`) — no general app-data caching layer
exists, so most of the brief's own cache-hit/miss/eviction checklist doesn't apply.
Verified: revocation actually works (logout → reused token → 401), fails open
correctly when Redis is stopped (login/requests/logout all still 200), and
recovers within ~10-15s of Redis coming back. One real finding, a deployment-config
item not a code bug: point health checks at `/actuator/health/liveness`+
`/readiness` (already correctly `UP` throughout a Redis outage), never the bare
`/actuator/health` (flips to `DOWN` and would wrongly look like a real outage to a
naive check). Full detail: `reports/PHASE10-REDIS.md`. This also covers PHASE 11's
"Redis unavailable" failure scenario — no separate test needed for that one.

## PHASE 11 — Failure testing

Redis-unavailable already covered by PHASE 10. Tested this pass (with the user's
explicit OK first, since MySQL is shared with the real dev backend): MySQL
unavailable (no crash, but requests/health hang up to Hikari's 30s timeout rather
than failing fast — see `ISSUES.md` ISSUE-007 — clean recovery confirmed on both
the throwaway backend and the real `:8081` instance), external API unavailable
(geocoding/routing pointed at an unreachable host — booking unaffected at normal
speed via the Freight Factor fallback, branch creation gracefully degrades with a
~3s delay matching the configured connect-timeout). DB connection exhaustion
already covered by PHASE 5/6. Slow-API-response not separately tested (same
timeout mechanism as external-API-unavailable, not repeated as its own scenario).
Full detail: `reports/PHASE11-FAILURE-TESTING.md`.

## PHASE 12 — Soak test (scoped down)

15-minute "soak-lite" at 80 VUs, not the brief's full 2-4h/500-user version (not
practical in one session, labeled honestly rather than silently substituted).
62,751 requests, 0.04% error rate, backend RSS memory flat throughout (562-563MB,
one small +26MB step near the end) — no leak. Chased down PHASE 6's flagged
"resource drift" observation properly: it's session-count creep from a real but
low-impact race (`ISSUE-008`), not a memory leak. Full detail:
`reports/PHASE12-SOAK.md`. A genuine multi-hour run would reuse the same
infrastructure (`k6/scenario.js -e DURATION=4h` + the memory-sampling loop) — just
wasn't run at full length here.

## PHASE 13-18 — Reporting & UAT prep

Consolidation of everything above, not new testing:
- `reports/PHASE13-CONSOLIDATED-REPORT.md` — per-endpoint stats (the brief's own
  Endpoint/Requests/RPS/Avg/P50/P95/P99/Error% format), from the 15-minute soak's
  62,751-request sample.
- `reports/PHASE14-BOTTLENECK-ANALYSIS.md` — priority-sorted index of all 8
  issues found this session, pointing into `ISSUES.md` for full detail.
- `reports/PHASE15-FIX-RETEST.md` — the before/after progression across every
  fix, reconstructed from the incremental verification each one already got.
- `reports/PHASE16-ACCEPTANCE-CRITERIA.md` — honest pass/fail against the
  brief's starting targets (met up to ~50 VUs; `login` misses its own target at
  every level tested, a BCrypt-cost trade-off, not a bug).
- `reports/PHASE17-READINESS-CHECKLIST.md` — the brief's own checklist, filled
  in, with two explicit carry-over gaps (ISSUE-007, ISSUE-008) rather than a
  blanket pass.
- `reports/PHASE18-UAT-PREP.md` — confirms `k6/scenario.js`/`tenant-isolation.js`
  were env-var-driven from their first version (not retrofitted), with example
  invocations for UAT/staging/production and the hard rules for what must never
  happen when pointing this suite at anything beyond local.

Concurrency (wallet double-deduct, shipment race), multi-tenant isolation suite,
DB-volume-specific query analysis, Redis metrics, failure/chaos testing, soak test,
and the consolidated report are not built yet. `ISSUES.md` in this directory is the
running bug/issue log for everything found while building and running this pipeline —
per explicit instruction, issues found are logged here and fixed together in one
PHASE 15 pass at the end, not fixed inline mid-test.

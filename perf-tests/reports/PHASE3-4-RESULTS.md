# PHASE 3/4 Results — Functional Check & Baseline

Run 2026-08-17 against the full generated dataset (10 tenants, 100,000 shipments,
469,086 status-history rows, 10,000 customers/tickets, 500 users, 50 branches — see
`perf-tests/README.md` PHASE 2). Target tenant: `PERFT01`. Backend: throwaway `:8082`
(`test` profile), real dev `:8081`/`:4200` untouched throughout. Raw k6 output for
each run: `baseline-10vu.txt` / `baseline-20vu.txt` / `baseline-50vu.txt` in this
directory.

## PHASE 3 — Functional check

1 VU, single pass, every step of the journey (Login → Dashboard → Search → Open →
Track → Create → Update): **all 7 checks passed** once two script-side issues (not
app bugs) were fixed — see below.

## PHASE 4 — Baseline (10 / 20 / 50 concurrent users)

| Metric (p95) | 10 VUs | 20 VUs | 50 VUs | Target |
|---|---|---|---|---|
| `http_req_duration` | 755 ms | 774 ms | **1.73 s** ✗ | <1000ms |
| `http_req_duration` (p99) | 764 ms | 1.42 s | **3.29 s** ✗ | <2000ms |
| `login_duration` | 393 ms | 925 ms | **2009 ms** ✗ | <1000ms |
| `dashboard_duration` | 765 ms | **1435 ms** ✗ | **3983 ms** ✗ | <1000ms |
| `search_duration` | 117 ms | 128 ms | 188 ms | <1000ms |
| `create_shipment_duration` | 70 ms | 59 ms | 45 ms | <1000ms |
| `http_req_failed` (error rate) | 0.00% | 0.00% | 0.12% | <1% |
| Throughput (`http_reqs/s`) | 8.7 | 17.0 | 39.4 | — |

**Reading this**: `search`/`create` stay fast and flat as concurrency rises — those
paths are healthy. `dashboard` degrades fastest and worst (765ms → 4.0s, a 5.2x
blowup 10→50 VUs) — direct consequence of `ISSUE-001` (cross-tenant leak): every
dashboard call scans all 100K shipments across every tenant instead of ~10K for its
own tenant, and that unfiltered scan gets slower as the whole dataset is under
concurrent write load too. `login` also degrades sharply (393ms → 2.0s) — BCrypt at
strength 12 is deliberately CPU-expensive (`SecurityConfig.java`), and this
machine's core count becomes the constraint once ~15-20 logins contend for CPU at
once; expected behavior for the cost factor chosen, not a bug, but a real local-
machine capacity ceiling worth remembering when reading PHASE 5/6 numbers later —
see "machine vs. app" note below.

**Two script bugs found and fixed while getting here** (not app bugs, both already
fixed in `perf-tests/k6/scenario.js`, documented in the script's own comments):
1. All VUs sharing one login account tripped the app's own login throttle
   (`app.auth.throttle-max-attempts`) — fixed via `USER_TEMPLATE`/`USER_POOL_SIZE`
   spreading VUs across the generated OPERATOR pool.
2. All VUs booking at one fixed branch regardless of which account was logged in hit
   ~80% `404 Branch not found` — `BranchServiceImpl.requireVisible` correctly refuses
   a booking at a branch the caller isn't staffed at. Fixed by reading each login's
   own `branchId` from `LoginResponse` and booking there.

**One new issue found, deferred per policy**: `ISSUE-003` — occasional (0.1-5%)
`409 DUPLICATE_RESOURCE` on concurrent `POST /shipments` at the same branch, not yet
root-caused (could be a real race in the shipment-number sequence generator, or an
unrelated constraint — undetermined). Flagged for PHASE 7's dedicated concurrency
test rather than chased down mid-baseline. See `ISSUES.md`.

**"Machine vs. app" note (PHASE 5's own instruction: stop increasing load when the
local machine becomes the bottleneck, not the application)**: not yet formally
separated here — CPU/RAM/JVM-heap/thread/connection-pool metrics were not sampled
*during* these three runs (only `perf-tests/README.md`'s actuator endpoints exist to
pull them; PHASE 4 says to capture CPU/RAM/heap/GC/threads/MySQL/Redis alongside the
k6 numbers, not done in this pass — noted as a gap, not silently skipped). Do this
before PHASE 5/6 push further: sample `/actuator/metrics/jvm.threads.live`,
`/actuator/metrics/hikaricp.connections.active`, `/actuator/prometheus`, and OS-level
`top`/`vm_stat` at each load level.

## PHASE 15 retest — after fixing ISSUE-001/ISSUE-004 (2026-08-17)

Same 10/20/50-VU sweep, same `PERFT01`, same throwaway `:8082`, run immediately after
the dashboard-scoping and login-concurrency fixes (`perf-tests/ISSUES.md`). Raw
output: `after-fix-{10,20,50}vu.txt`.

| Metric (p95) | 10 VUs (before → after) | 20 VUs (before → after) | 50 VUs (before → after) |
|---|---|---|---|
| `http_req_duration` | 755ms → **283ms** | 774ms → **342ms** | 1.73s → **416ms** |
| `http_req_duration` (p99) | 764ms → **306ms** | 1.42s → **492ms** | 3.29s → **1.26s** |
| `dashboard_duration` | 765ms → **295ms** | 1435ms ✗ → **444ms** | 3983ms ✗ → **1109ms** |
| `search_duration` | 117ms → 137ms | 128ms → 175ms | 188ms → 279ms |
| `create_shipment_duration` | 70ms → 90ms | 59ms → 62ms | 45ms → 47ms |
| `login_duration` | 393ms → 445ms | 925ms → 938ms | 2009ms ✗ → **2329ms ✗** |
| `http_req_failed` | 0.00% | 0.00% ✗→0.00% | 0.12% → **0.00%** |
| Throughput (req/s) | 8.7 → 9.3 | 17.0 → 18.6 | 39.4 → 44.2 |

**dashboard_duration dropped 2.6-3.6x at every load level** and stopped crossing the
1000ms target at 10/20 VUs (50 VUs is still marginal at 1109ms, but that's now the
*real* cost of a correctly-scoped query under load, not a symptom of scanning 9x more
rows than it should). **Error rate is 0.00% everywhere**, down from the login-race
failures previously seen at 50 VUs.

**`login_duration` is now the standalone worst metric** (2329ms p95 at 50 VUs, the
only one still failing its target) — and unlike the two fixed issues, this is
expected, not a bug: `BCryptPasswordEncoder(12)` (`SecurityConfig.java`) is
deliberately CPU-expensive, and this local machine's core count becomes the
constraint once enough concurrent logins contend for CPU at once. This is real
signal for PHASE 6 (stress test): **BCrypt cost factor 12 under concurrent login
load is likely to be the actual ceiling on this machine**, worth watching closely as
VUs increase further, and worth remembering as a machine-vs-app distinction — a
faster/more-cored production host would show a flatter curve here for the exact same
code.

## PHASE 5 — load ramp past 50 VUs

Continued 2026-08-17 straight after the PHASE 15 retest above (raw output:
`load-{60,75,100}vu.txt`, `load-100vu-pool150.txt`).

| VUs | Pool size | Result |
|---|---|---|
| 50 | 60 (default) | Clean — 0% errors (PHASE 4 baseline above) |
| 60 | 60 (default) | **83% failed** — `login_duration` p95 = 60s (client timeout) |
| 75 | 60 (default) | **61% failed** — same wall |
| 100 | 60 (default) | **~100% failed** — same wall, total collapse |
| 100 | **150** | Clean — **0% errors**, `login_duration` p95 = 4.3s (elevated, bounded) |

**The app does not degrade past ~50 concurrent users — it collapses**, and the wall
sits almost exactly at the configured Hikari pool size (default 60). Root cause,
mitigation, and the recommended real fix are written up in full in `ISSUES.md`
ISSUE-005 — short version: `AuthService.login()`'s `@Transactional` boundary holds a
DB connection through the CPU-bound BCrypt(12) password check, so concurrent logins
exhaust the pool far faster than their actual DB work would justify, and the queue
that builds behind a saturated pool never drains for the rest of a sustained run.
Raising `DB_POOL_SIZE` to 150 fully confirmed this (100 VUs went from ~100% failing
to 0% failing with no other change) but only moves the wall — MySQL's own
`max_connections` (151 on this machine) is the next one behind it.

**PHASE 5's own instruction — stop when the machine, not the app, becomes the
bottleneck — doesn't apply cleanly here**: this wasn't the test machine running out
of CPU/RAM (CPU sampled ~89% idle during the 100-VU collapse); it was `AuthService
.login()` holding a Hikari connection through BCrypt's CPU-bound work instead of
just its actual DB time. **Fixed** (see `ISSUES.md` ISSUE-005): removed the
method's blanket `@Transactional`.

### After the ISSUE-005 fix

| VUs | Pool size | Fix applied? | Result |
|---|---|---|---|
| 100 | 60 (default) | ❌ | ~100% failed (table above) |
| 100 | 150 | ❌ | Clean — mitigation only, root cause still present |
| 100 | 60 (default) | ✅ | **Clean — 0.00% failed (0/3465)** |
| 150 | 60 (default) | ✅ | **Clean — 0.03% failed (1/2844)** |
| 200 | 60 (default) | ✅ | 45% failed — genuine pool saturation now (real query volume, confirmed via Hikari log, not the wasteful hold-through-bcrypt pattern) |

**Collapse point moved from ~55-60 concurrent users to ~150-200** — roughly 3x —
from removing one annotation. Past ~150 VUs the app now degrades along a normal
curve instead of collapsing outright, which is the correct shape for a system
gated by real capacity rather than wasted connection-holding. This is now a genuine
PHASE 6/9 capacity-planning number (raise `DB_POOL_SIZE` + MySQL `max_connections`
together for whatever real concurrent load a target environment needs), not a bug
to chase further.

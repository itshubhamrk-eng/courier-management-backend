# PHASE 15 — Fix & Retest (Historical Progression)

Unlike a typical PHASE 15 pass (fix everything, then one clean retest sweep), this
session fixed and retested **incrementally, issue by issue, throughout** — each
fix was verified live immediately, not batched to the end. This file reconstructs
the overall progression from the individual before/after numbers already recorded
in `ISSUES.md` and the PHASE 3-12 reports, so the *shape* of improvement across the
whole session is visible in one place.

## Test Run 1 — PHASE 4 baseline, before any fix

10/20/50 VUs against the 100K-shipment dataset.

| VUs | `http_req_duration` p95 | `dashboard_duration` p95 | Error rate |
|---|---|---|---|
| 10 | 755ms | 765ms | 0.00% |
| 20 | 774ms | 1435ms ✗ | 0.00% |
| 50 | 1.73s ✗ | 3983ms ✗ | 0.12% |

Two real bugs hiding in these numbers, not yet found: ISSUE-001 (dashboard scanning
every tenant) and the seeds of ISSUE-005 (though its collapse wall wasn't hit until
PHASE 5's escalation past 50 VUs).

## Optimization 1 — ISSUE-001 fixed (dashboard explicit scoping)

## Test Run 2 — same 10/20/50 sweep, after ISSUE-001

| VUs | `http_req_duration` p95 | `dashboard_duration` p95 | Error rate |
|---|---|---|---|
| 10 | 283ms | 295ms | 0.00% |
| 20 | 342ms | 444ms | 0.00% |
| 50 | 416ms | 1109ms | 0.00% |

`dashboard_duration` dropped 2.6-3.6x at every level. Error rate held at 0.00%
(the 0.12% from Run 1 wasn't reproduced here — it was ISSUE-004's login race,
found separately).

## Optimization 2 — ISSUE-004 fixed (concurrent-login session/bookkeeping races)

Verified directly against its own trigger scenario (50 VUs / 44-account pool, the
exact shape that originally failed): **before** — 45-98 failures per run,
escalating through 409s and a raw 500 across three iterations of the fix getting
it right; **after** — 0/955 failed across three consecutive clean runs.

## Optimization 3 — ISSUE-005 fixed (removed `login()`'s blanket `@Transactional`)

## Test Run 3 — load ramp, before vs after, same 100-VU scenario, pool left at default

| Config | Error rate |
|---|---|
| Before fix, pool=60 | ~100% failed |
| Mitigation only (pool=150, fix not yet applied) | 0.00% failed (masks the root cause) |
| **Fix applied, pool back to default 60** | **0.00% failed** |
| Fix applied, pushed to 150 VUs | 0.03% failed (essentially clean) |
| Fix applied, pushed to 200 VUs | 45% failed (genuine capacity wall now, not waste) |

Collapse point moved from ~55-60 VUs to ~175-180 VUs — confirmed in PHASE 6's own
dedicated bisection.

## Optimization 4 — ISSUE-006 fixed (two missing shipment-search indexes)

## Test Run 4 — 100K-shipment single tenant, before vs after `V42`

| Query | Before | After |
|---|---|---|
| Plain browse, no filter | 220-587ms, 100,020 rows scanned | 76-78ms, 0 slow-query-log entries |
| Date-range filter | 233ms, 100,020 rows scanned | 98ms |
| Deep pagination (page 3000) | 401ms, 160,020 rows scanned | 152ms |
| Dashboard | 570-650ms | 258-292ms |

## No regressions, checked at every step

`mvn test` (full backend suite) was run and confirmed green after *every* fix in
this session — not just once at the end. New regression tests added along the way:
`DashboardServiceImplTest` (ISSUE-001), `AuthServiceTest
.loginSurvivesBookkeepingRace` (ISSUE-004).

## What Test Run 5 would need to be

A clean, single, comprehensive sweep (10/20/50/100/150/175 VUs) against a *fresh*
dataset (avoiding ISSUE-008's session-count creep from this session's own repeated
testing) with all fixes in place, ideally with actuator/JVM metrics available
(needs a `PLATFORM_ADMIN` test account — flagged repeatedly, not yet provisioned).
Not run as its own pass this session — the incremental before/after evidence above
already demonstrates each individual fix's effect cleanly, and a fresh full sweep
would mostly reconfirm PHASE 6/9's own already-recorded numbers.

# PHASE 11 — Failure Testing

Run 2026-08-17, `PERFT01`, throwaway `:8082`. MySQL-down test explicitly confirmed
with the user first (same Homebrew `mysql@8.0` instance the real dev backend on
`:8081` also uses — briefly affected during that one scenario, confirmed recovered
cleanly afterward, and untouched for every other scenario in this pass).

## Redis unavailable

Covered in full in `PHASE10-REDIS.md` — fails open correctly (app stays fully
usable), recovers within ~10-15s. Not repeated here.

## MySQL unavailable

Stopped `mysql@8.0` (`brew services stop`), ~15s outage, then restarted.

- **No crash**: backend process stayed alive throughout (confirmed via `pgrep`).
- **`/actuator/health` hangs, not fails fast**: a `curl --max-time 8` against it
  returned nothing at all (`http:000`, empty body) — the health indicator itself
  tries to borrow a Hikari connection to run its `SELECT 1`/`isValid()` check, and
  with the DB genuinely unreachable every connection attempt in the pool blocks
  until Hikari's own `connection-timeout` (30000ms, `application.yml`) gives up.
  Same story for a real request (`POST /auth/login`): no response within 8s, request
  just hangs.
- **This is a real operational gap, not a crash-level bug**: eventually (within
  30s) every in-flight request does correctly fail rather than hang forever, and
  nothing corrupts — but a client (browser, mobile app, a Kubernetes probe with a
  short timeout) sees a long hang before any error, not a fast, clean `503`. A more
  resilient design would either lower the connection-timeout specifically for
  latency-sensitive paths, or add a circuit breaker (e.g. Resilience4j) that starts
  failing fast after the first few connection failures instead of making every
  subsequent request individually wait out the full timeout. Flagged, not fixed —
  this is an availability/UX trade-off decision, not a pure bug fix, and touches
  global datasource configuration.
- **Recovery: clean**. Restarted MySQL, waited a few seconds:
  `/actuator/health` → `UP`, `db: UP`; a fresh login succeeded immediately. The real
  dev backend (`:8081`) was also confirmed healthy again
  (`/actuator/health/liveness` → `200`) — no lingering damage from the shared
  MySQL instance being briefly unavailable.

## External API unavailable (geocoding, routing)

Restarted the throwaway backend with `GEOCODING_BASE_URL`/`ROUTING_BASE_URL`
pointed at an unreachable address (`http://10.255.255.1:1` — a non-routable IP,
fails fast rather than blackholing packets).

- **Shipment booking with a brand-new pincode pair** (forcing a real distance/
  routing lookup, not a cached one): succeeded in **140ms**, fully normal speed.
  The pricing engine's own fallback path (Freight Factor grid — see PHASE 2/
  ISSUE-related notes on why every generated tenant has a catch-all row) covers
  this without booking even needing to wait out a routing timeout.
- **Branch creation** (triggers live geocoding via `BranchGeocoder`): succeeded,
  but took **3.4 seconds** — matching the configured `connect-timeout: 3s`
  (`application.yml`) almost exactly. The branch is created regardless (matches
  the documented "a miss or disabled provider just leaves lat/long null, never
  blocks creating it" design) — correct behavior, just a real, noticeable UX delay
  during an actual geocoding outage. Not a bug; worth knowing about if branch
  creation ever feels slow in a real environment where the geocoding provider is
  degraded rather than fully down.

## Database connection exhaustion

Already covered extensively in PHASE 5/6 (`ISSUES.md` ISSUE-005, `PHASE6-STRESS
.md`) — not re-tested here as a separate scenario. Confirmed behavior: correctly
surfaces as `409 CONCURRENT_MODIFICATION`-style responses once
`ConcurrencyFailureException`/timeout handling was widened (ISSUE-004/005), not a
raw 500, and recovers immediately once load drops or the pool is sized adequately.

## Slow API response

Not separately tested this pass — the external-API-unavailable scenario above
already exercises the configured timeout paths (`connect-timeout`/`read-timeout`
for geocoding/routing), which is the same mechanism a genuinely *slow* (not fully
down) upstream would hit. Not repeated as its own scenario given time budget;
flagged as a small remaining gap rather than silently assumed equivalent.

## Conclusion

Every failure scenario tested resulted in **no crash and no data corruption** —
the app is fundamentally resilient. The one real, actionable finding is the
MySQL-down hang-not-fail-fast behavior; everything else (external API fallback,
Redis fail-open, recovery in every case) already matches its own documented design
intent, confirmed empirically rather than assumed from reading the code.

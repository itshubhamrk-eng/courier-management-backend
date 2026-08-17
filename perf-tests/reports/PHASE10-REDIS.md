# PHASE 10 — Redis Performance & Failure Behavior

Run 2026-08-17 against `PERFT01`, throwaway `:8082`. Real dev instance untouched.

## Scope, confirmed from the code first

Redis has exactly one job in this app: `RedisTokenRevocationChecker` — an access-
token denylist (`auth:denylist:{jti}`, TTL-bounded to the token's own remaining
life). There is no general `@Cacheable`/`RedisTemplate` application-data caching
layer anywhere else in the codebase (confirmed by grep before testing) — so most of
the brief's own Redis checklist (cache hit/miss rate, cache invalidation, eviction
behavior under memory pressure) doesn't apply here; this app simply doesn't use
Redis as a cache. What follows is scoped to what Redis actually does in this app.

## Latency, memory, connections

- `PING` latency: 3-5ms (localhost, as expected).
- `used_memory`: 1.06MB, `maxmemory`: 0 (unbounded — acceptable locally; a real
  deployment should set a `maxmemory`/eviction policy, though the denylist's own
  per-key TTL already bounds growth to roughly "revocations in the last 15
  minutes," not indefinite accumulation).
- `connected_clients`: 4.

## Revocation — the actual feature — verified working

Login → `GET /auth/me` with the fresh token → `200`. Logout → same token reused →
**`401`**. Redis `DBSIZE` incremented by exactly one denylist entry per logout.
The one thing Redis is for in this app works correctly.

## Fail-open behavior — verified working, exactly as designed

Stopped Redis (`brew services stop redis`) mid-session. With Redis down:
- Login: still `200`, works normally.
- Authenticated requests (`/auth/me`, `/dashboard/summary`): still `200`.
- **Logout: still `200`** — the refresh token and session are revoked in MySQL
  regardless of Redis, so a logout is never blocked by a Redis outage (by design,
  per `RedisTokenRevocationChecker`'s own javadoc).
- `/actuator/health` (bare, aggregate): `DOWN` — `redis` component reports
  `QueryTimeoutException`.

**The app stays up and fully usable with Redis down** — exactly the "fails open,
not down" design intent, confirmed empirically rather than just read in a comment.

## One real finding: bare `/actuator/health` vs the actual k8s-style probes

The bare `/actuator/health` endpoint's top-level `status` flips to `DOWN` the
moment Redis is unreachable — even though the application is fully capable of
serving traffic in that state. A naive health check pointed at that bare endpoint
(rather than the dedicated groups) would incorrectly conclude the app itself is
down and could pull it out of rotation or trigger a restart, defeating the whole
point of the fail-open design.

**This is already handled correctly, provided the right endpoint is used**:
`management.endpoint.health.probes.enabled: true` is already set
(`application.yml`), which exposes separate `livenessState`/`readinessState`
indicators — confirmed both stayed `UP` throughout the Redis outage, since
Spring Boot's liveness/readiness groups don't include arbitrary component
indicators like `redis` by default. **Action item, not a code fix**: whoever
configures the UAT/staging/production deployment's health checks (Kubernetes
probes, load balancer health check, etc.) must point them at
`/actuator/health/liveness` and `/actuator/health/readiness` specifically — never
the bare `/actuator/health` — or a Redis blip will cause exactly the outage this
whole design exists to prevent. Worth a note in deployment docs, not a backend
change.

## Recovery timing

Restarted Redis after the outage. `redis-cli PING` responded immediately, but
`/actuator/health`'s `redis` component and the actual revocation check both took
**roughly 10-15 seconds** to catch up (Lettuce's own reconnection backoff) — during
that window the app correctly continued in the same fail-open degraded mode it was
already in, not a new or worse failure mode. Once fully reconnected, revocation
resumed working correctly (verified: a fresh logout after full recovery correctly
denylisted its token, reuse got `401` again). This is a reasonable, bounded
recovery characteristic, not a defect — flagged for awareness (a monitoring/alert
threshold should account for this ~15s window rather than paging on the very first
failed health check after a Redis restart), not something to fix.

## Conclusion

Redis's one real job (token revocation) works correctly, fails open correctly
under an outage, and recovers correctly (within ~15s) once restored. The only
actionable item is a deployment-configuration note (probe endpoint choice), not a
code change.

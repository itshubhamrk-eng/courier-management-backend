# PHASE 13 — Local Performance Report (Consolidated)

Primary dataset: the PHASE 12 soak run (80 VUs sustained, 15 minutes, `PERFT01`,
throwaway `:8082`, post-all-fixes) — the largest, most statistically meaningful
sample collected this pass (62,751 HTTP requests, ~10,449 full user-journey
iterations). Per-endpoint figures below are k6's own `Trend` metrics for each
named step of the journey (`k6/scenario.js`). `p99` is only available for the
overall `http_req_duration` aggregate in this run — k6's default summary doesn't
print `p99` for custom per-step trends unless explicitly requested via
`--summary-trend-stats`, not done this pass; noted rather than fabricated.

## Per-endpoint

```
POST /api/v1/auth/login
  Requests:      ~10,449 (one per iteration)
  Avg:           3,357 ms
  P50 (med):     3,542 ms
  P90:           3,703 ms
  P95:           3,745 ms
  Min / Max:     641 ms / 3,815 ms
  Note: dominated by BCrypt(12) CPU cost under 80-VU concurrent login contention
  (see ISSUE-005/PHASE 6) — not a query-time problem, a CPU-bound one.

GET /api/v1/dashboard/summary
  Requests:      ~10,449
  Avg:           719 ms
  P50 (med):     327 ms
  P90:           2,134 ms
  P95:           2,541 ms
  Min / Max:     54 ms / 3,447 ms
  Note: correctly company-scoped since ISSUE-001's fix; the wide p50→p95 spread
  reflects queueing behind the login-driven CPU contention above, not the
  dashboard's own query cost (PHASE 9 confirmed its underlying queries are fast).

GET /api/v1/shipments (search)
  Requests:      ~10,449
  Avg:           107 ms
  P50 (med):     66 ms
  P90:           249 ms
  P95:           365 ms
  Min / Max:     15 ms / 884 ms

POST /api/v1/shipments (create)
  Requests:      ~10,449
  Avg:           23 ms
  P50 (med):     15 ms
  P90:           37 ms
  P95:           52 ms
  Min / Max:     6 ms / 2,067 ms

Overall (all endpoints combined)
  Requests:      62,751
  RPS:           69.2 req/s
  Avg:           151 ms
  P90:           315 ms
  P95:           676 ms
  P99:           2.52 s
  Errors:        0.04% (28 / 62,751)
```

`GET /shipments/{id}`, `GET /shipments/track/{trackingNumber}`, and
`PUT /shipments/{id}` (update) are exercised by the same script but not captured
as named `Trend` metrics in `scenario.js` — they contribute to the overall
`http_req_duration` aggregate above but don't have their own row here. A gap worth
closing if this report needs to be regenerated (add `Trend`s for them, same
pattern as the four already there).

## Resource usage (the brief's own CPU/RAM/JVM/GC/threads/MySQL/Redis ask)

- **MySQL**: `Threads_connected` held flat at 66 throughout the 15-minute soak
  (Hikari pool default 60 + a handful of platform/admin connections) — no
  connection leak. `max_connections=151` (this machine's default) never
  approached.
- **Backend process RSS**: flat at 562-563MB for 13 of 15 minutes, one small
  +26MB step near the end — see PHASE 12 for the full memory profile.
- **JVM heap / GC / thread pool detail**: not captured — needs actuator metrics,
  which need a `PLATFORM_ADMIN` test account that doesn't exist in this dataset.
  Same gap flagged in PHASE 6/12, not silently worked around.
- **CPU**: `ps`/`top` proved unreliable for this long-lived process on this
  machine (returned `0.0%` even under visible load) — not reported rather than
  faked. The *symptom* of CPU saturation (login latency climbing steeply with
  concurrency while every other endpoint stays flat) is well-evidenced
  throughout PHASE 4-6 even without a raw CPU% number.
- **Redis**: not separately re-measured during this specific soak run — see
  PHASE 10 for its own dedicated latency/memory/connection numbers (3-5ms PING,
  1.06MB used, 4 connected clients), unrelated to load level since it's only used
  for token revocation, not per-request caching.

## Comparison to PHASE 16's own starting targets

| Target | This run | Met? |
|---|---|---|
| Average API < 500ms | 151ms overall (but 3,357ms for login specifically) | Partial — see PHASE 16 for the full per-endpoint breakdown |
| P95 < 1s | 676ms overall (but 2,541-3,745ms for dashboard/login) | Partial |
| P99 < 2s | 2.52s overall | ❌ |
| Error rate < 1% | 0.04% | ✅ |

Full pass/fail reasoning per endpoint, and what it would take to close the gap, is
in `PHASE16-ACCEPTANCE-CRITERIA.md`.

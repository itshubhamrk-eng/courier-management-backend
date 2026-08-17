# PHASE 6 — Stress Test

Run 2026-08-17, straight after PHASE 5's architectural fix (ISSUE-005). Target:
`PERFT01`, throwaway `:8082`, real `:8081`/`:4200` untouched. Bisected upward from
PHASE 5's last-known-clean point (150 VUs) to find the actual ceiling.

## Two distinct regimes, not one number

The brief asks for "maximum sustainable users" as if there's a single answer. There
isn't one here — there are two separate ceilings depending on what "sustainable"
means:

### Regime 1 — latency degrades continuously, well before any errors (CPU-bound)

| VUs | `http_req_duration` p95 | Error rate | Meets PHASE 16 target (p95<1000ms)? |
|---|---|---|---|
| 10 | 283ms | 0% | ✅ |
| 20 | 342ms | 0% | ✅ |
| 50 | 416ms | 0% | ✅ |
| 100 | 2.62s | 0% | ❌ |
| 150 | 4.98s | 0.03% | ❌ |
| 160 | 3.91s | 0% | ❌ |
| 175 | 4.94s | 0% | ❌ |

**If "sustainable" means meeting the PHASE 16 starting targets, the real ceiling is
~50 concurrent users**, not the much higher number below. Root cause: BCrypt(12)
login verification is deliberately CPU-expensive (`SecurityConfig.java`), and this
10-core machine's CPU becomes the constraint for *that specific step* well before
anything else struggles — every other metric (search, create, ticket ops) stays
fast throughout this entire range; it's specifically `login_duration` dragging the
overall `http_req_duration` p95 up. This is a real, physics-bound limit for this
machine's core count, not a bug — a faster/more-cored production host would push
this ceiling out for the identical code.

### Regime 2 — catastrophic collapse (pool exhaustion)

| VUs | Error rate | `http_req_duration` p95 | Throughput |
|---|---|---|---|
| 175 | 0.00% | 4.94s | 91.8 req/s |
| 180 | **1.09%** | 33.4s | 27.5 req/s |
| 185 | 14.02% | 30.9s | 18.1 req/s |
| 190 | 15.16% | 31.1s | 17.8 req/s |
| 200 | ~100% | 60s (client timeout) | 8.5 req/s |

**The wall sits at ~175-180 VUs** — a sharp transition, not a gradual one (0% → 1%
→ 14% between three adjacent data points). Confirmed same root mechanism as
ISSUE-005: backend log shows 556 occurrences of `courier-pool - Connection is not
available ... (total=60, active=60, idle=0, waiting=85)` during this exact run.
ISSUE-005's fix moved this wall from ~60 VUs to ~180 VUs (3x), but a *finite* pool
(60, this profile's default) still has *some* ceiling — this is now the correct,
expected kind of capacity limit (real concurrent query volume across every
endpoint, not one method wastefully hoarding a connection), and the practical fix
for a real deployment is sizing `DB_POOL_SIZE` (and MySQL's own `max_connections`,
151 on this machine) for the actual expected concurrent load — already
demonstrated working in PHASE 5 (pool=150 took a 100% failing 100-VU run to 0%).

## An observation, not fully chased down: apparent resource drift across repeated runs

Two back-to-back 160-VU runs, minutes apart, in the *same* long-lived backend
process (no restart between them): the first came back 0.00% failed, the second —
after several other stress runs had executed in between — came back 11.36% failed
at the identical VU count and identical dataset. This could be innocent (natural
run-to-run variance, background JIT warmup differences, OS scheduling noise) or
could be a genuine slow resource leak (connections, threads, sessions) accumulating
across a long-running process under repeated load. **Not diagnosed here** — this is
exactly PHASE 12's (soak test) job, and it's flagged specifically so that pass
knows to watch for it rather than assume a clean, deterministic ceiling.

## CPU/RAM/JVM sampling — attempted, not reliable, reported honestly

Tried to sample the backend process's CPU% live during a stress run via `ps`/`top`
on this machine; both consistently returned `0.0%` for a process that was visibly
under heavy load (elevated latencies, growing HikariCP wait queue) — almost
certainly a macOS `ps`/`top` quirk for a long-lived JVM process (decayed/average
CPU accounting, not instantaneous) rather than the process genuinely being idle.
Rather than report a number known to be wrong, this is left unmeasured. Actuator's
own `/actuator/metrics/*` endpoints (which would give an authoritative JVM/CPU/GC
read) are gated to `PLATFORM_ADMIN` (`SecurityConfig.java`) and no such account
exists in this dataset — creating one solely for observability wasn't done without
checking first. **If a future session needs real CPU/heap numbers**: either
provision a throwaway `PLATFORM_ADMIN` test account, or run with `jconsole`/`jcmd`
attached to the backend PID directly (`pgrep -f com.courier.CourierApplication`)
rather than relying on `ps`/`top`.

## Answers to the brief's own numbered questions (for this local environment)

- **Maximum sustainable users**: ~50 if held to the PHASE 16 latency target;
  ~175-180 if "sustainable" just means "not erroring."
- **Maximum requests/sec**: ~92 req/s, observed at 175 VUs just before the wall.
- **P95 degradation point**: starts climbing past target immediately after 50 VUs;
  crosses into catastrophic (30s+) territory at 180 VUs.
- **Error-rate threshold**: sharp transition at 180 VUs (0.00% → 1.09%), fully
  collapsed by 185 VUs (14%).
- **Database connection saturation**: confirmed, `total=60, active=60` (Hikari pool
  default) — MySQL's own `max_connections=151` is the next wall behind it, not yet
  reached in this pass (`Max_used_connections` peaked at 117 during the earlier
  pool=150 test).

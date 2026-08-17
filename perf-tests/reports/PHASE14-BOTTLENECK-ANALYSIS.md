# PHASE 14 — Bottleneck Analysis (Index)

Every issue below follows Problem → Evidence → Root Cause → Impact → Recommended
Fix → Priority in full in `perf-tests/ISSUES.md` — this file is the consolidated,
priority-sorted index the brief asks for, not a duplicate of the detail.

| # | Problem | Priority | Status |
|---|---|---|---|
| [ISSUE-001](../ISSUES.md#issue-001--cross-tenant-data-leak-in-the-dashboard-summary-endpoint) | Dashboard leaked every tenant's shipment data to any `COMPANY_ADMIN`, not just `SUPER_ADMIN` | 🔴 Critical | ✅ Fixed, retested |
| [ISSUE-005](../ISSUES.md#issue-005--hikaricp-pool-exhaustion-collapses-the-app-at-60-concurrent-users) | App collapses (not degrades) at ~55-60 concurrent users — login held a DB connection through CPU-bound BCrypt work, starving the pool | 🔴 Critical | ✅ Fixed, retested (~3x capacity gain) |
| [ISSUE-004](../ISSUES.md#issue-004--concurrent-logins-to-the-same-account-409s-and-even-a-raw-500) | Concurrent logins to the same account could 409 or even 500 an otherwise-successful login | 🟠 High | ✅ Fixed, retested |
| [ISSUE-006](../ISSUES.md#issue-006--missing-indexes-default-shipment-browse-and-date-range-filter-full-scan-at-volume) | Default shipment browse + date-range filter full-scan the whole tenant at 100K+ shipments | 🟠 High | ✅ Fixed, retested |
| [ISSUE-007](../ISSUES.md#issue-007--requests-hang-not-fail-fast-during-a-mysql-outage) | Requests hang (not fail fast) up to 30s during a MySQL outage | 🟡 Medium | 🚩 Flagged, not fixed |
| [ISSUE-003](../ISSUES.md#issue-003--occasional-409-duplicate_resource-on-concurrent-post-shipments) | Occasional 409 on concurrent shipment booking | — | ✅ Investigated, closed (not reproducible; sequence generator proven race-free) |
| [ISSUE-008](../ISSUES.md#issue-008--session-concurrency-cap-can-be-exceeded-by-one-under-concurrent-logins) | Session concurrency cap can land at 6 instead of 5 under concurrent logins | 🟢 Low | 🚩 Flagged, not fixed |
| [ISSUE-002](../ISSUES.md#issue-002--data-generator-wallet-transaction-count-far-short-of-target) | Perf-data-generator wallet-transaction count fell short of its own target | 🟢 Low | ✅ Fixed (generator default only, test-tooling not app code) |

## Two more findings, not "issues" but load-bearing for the numbers above

- **Free-text search and deep offset pagination** both do full/expensive scans at
  volume — deliberately **not** filed as fix-it issues, because the real fix for
  each is a product/architecture decision (FULLTEXT index or external search
  engine; keyset pagination), not a pure performance patch. See
  `PHASE9-DATABASE.md`.
- **Bare `/actuator/health` vs the k8s-style probe groups** — a deployment-config
  item (point health checks at `/actuator/health/liveness`+`/readiness`), not a
  code issue. See `PHASE10-REDIS.md`.

## Reading this table

🔴 Critical and 🟠 High are all fixed and retested live. 🟡 Medium and 🟢 Low are
flagged with full root cause and a recommended fix but deliberately left for a
human decision — both are trade-offs (availability-vs-latency tuning; a soft
device-count cap's exact atomicity) rather than unambiguous bugs, matching this
whole pass's own policy of fixing what's clearly wrong and flagging what needs a
judgment call.

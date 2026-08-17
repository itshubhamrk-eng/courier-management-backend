# PHASE 8 — Multi-Tenant Isolation Test

Run 2026-08-17 against the full generated dataset (10 tenants, `PERFT01`-`PERFT10`).
Script: `perf-tests/k6/tenant-isolation.js`. Backend: throwaway `:8082`, real
`:8081`/`:4200` untouched.

## Method

For each tenant pair (A = attacker, B = victim), logged in as both, resolved B's own
real resource ids (shipment, customer, branch, ticket, user) as B itself, then
attempted every read as A:
- **By-id GET** on each resource type — must be 404 or 403.
- **Search/list** on each resource type — must never contain B's ids.
- **Dashboard** — A's own totals must stay within one tenant's own scale, never
  anywhere near the full cross-tenant sum (this is ISSUE-001's own regression
  check, run live rather than only unit-tested).

Run across three independent tenant pairs for confidence it isn't pair-specific:
`PERFT01`↔`PERFT02`, `PERFT03`↔`PERFT05`, `PERFT07`↔`PERFT10`.

## Result

**42/42 checks passed across all three pairs (100%).** No cross-tenant leak found
for: shipments (by id, by tracking number, timeline, search), customers (by id,
search), branches (by id), tickets (by id, search), users (by id), dashboard
aggregates.

## One false positive along the way, worth recording

The first version of the tracking-number check flagged a "leak": tenant A could
`GET /shipments/track/{B's tracking number}` and get `200` back. Investigation
showed this was **not a leak** — `tracking_number` is unique *per company*
(`uk_shipments_company_tracking (company_id, tracking_number)`), not globally, and
the PHASE 2 data generator seeds every tenant's counter from 1 in the same month, so
two tenants legitimately produce the identical tracking-number string by coincidence
extremely often. The correctly-scoped query found tenant A's *own* shipment sharing
that string — not tenant B's. Fixed the check to compare the returned record's `id`
against B's actual id rather than just checking for a `200`, confirming the backend
was correct all along. A second false positive (the original dashboard check
compared tenant A's total against tenant B's, expecting inequality — but two
freshly-generated tenants with zero k6 traffic against them legitimately start at
the *same* count, 10,000 each) was fixed the same way: check against an absolute
per-tenant-scale ceiling instead of comparing two peers.

## Not covered — flagged, not silently skipped

- **Wallets**: `branch-wallet` endpoints are scoped to the caller's *own* branch by
  design, with no general by-id lookup exposed to any authenticated caller — the
  by-id attack shape this suite tests doesn't apply the same way. Not tested here.
- **Manifests, Crossings**: the PHASE 2 data generator doesn't create manifest or
  crossing rows (out of its own documented scope — see `perf-tests/README.md`), so
  no synthetic data exists to attempt a cross-tenant read against. `GET /manifests/
  {id}` is wired into the script and will run automatically once a future generator
  pass (or a k6-created manifest) gives it something to find.
- **Notifications, Reports**: not yet covered by this script — notifications are
  inherently self-scoped (a caller only ever lists their own), and no dedicated
  report-by-id endpoint was identified during this pass. Worth a second look if a
  reports module gains a shareable/by-id URL later.

## Conclusion

Backend-level tenant isolation holds for every resource type this suite could
reach, across three independent tenant pairs, after ISSUE-001's fix. This is real
evidence, not just an assumption carried over from that one earlier fix — Phase 8
was explicitly mandatory in the test brief precisely because assuming isolation
without testing it is how ISSUE-001 shipped in the first place.

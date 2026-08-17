# PHASE 9 — Database Performance at Volume

Run 2026-08-17. MySQL slow-query log enabled (`long_query_time=0.1`, 100ms
threshold) for this pass. Two data shapes tested:

1. The existing 10-tenant dataset (~10-12K shipments per tenant, 100K total) — see
   PHASE 3/4/5 for query timings at that scale, all fast, nothing slow-logged.
2. **A dedicated single tenant with a true 100,000 shipments** (`PERFSCALE01`,
   468,376 status-history rows), generated specifically for this phase — the
   existing 10-tenant dataset only has ~10-12K shipments *per company*, and
   company-scoped queries care about per-tenant scale, not the cross-tenant sum.
   This is the number that actually matters for "how does this feel for one large
   customer."

## Findings at 100K shipments in one tenant

| Query | Before (V41) | Rows examined | After (V42) |
|---|---|---|---|
| Plain browse, no filter, newest first | 220-587ms | **100,020** (full scan) | 76-78ms |
| Date-range filter (`bookingDateFrom`/`To`) | 233ms | **100,020** (full scan) | 98ms |
| Deep pagination (page 3000, `LIMIT 60000,20`) | 401ms | **160,020** | 152ms |
| Dashboard summary | 570-650ms | (9 small queries, none individually slow) | 258-292ms |
| Status filter (`status=DELIVERED`) | 78ms | 11,778 (index-covered) | unchanged — was already fine |
| Free-text search (`shipmentNumber`/`trackingNumber` LIKE) | 153ms | 100,000 (full scan) | unchanged — see below |

**Root cause, the first two rows**: `idx_shipments_status (company_id, status,
created_at)` only helps a query that filters by `status` — the two most common
browse shapes (default "newest first, no filter" and "booking date range") have
neither, so MySQL fell back to a plain company-scoped table scan of all 100,000
rows just to return 20. Confirmed via `EXPLAIN` before touching anything, and via
the slow-query log's own `Rows_examined` column, not just wall-clock timing.

**Fixed**: `V42__shipment_search_indexes.sql` — two purely additive indexes,
`(company_id, created_at)` and `(company_id, booking_date)`. No application code
changed. Applied cleanly in 0.4s against the 100K-row table. `mvn test`: full suite
green (schema-only change, no entity mapping touched).

**Retested live**: zero queries appeared in the slow-query log at all after the fix
(previously logging on every one of these calls). Plain browse and date-range both
now well under 100ms once warm; deep pagination and dashboard both improved
~2.5x/~2.2x though didn't cross the 100ms line themselves (see below — different,
architectural causes, not missing indexes).

## Two things *not* fixed, and why

**Deep OFFSET pagination (`LIMIT 60000,20`) is architecturally expensive, not just
under-indexed.** Even fully index-covered, MySQL still has to walk and discard the
first 60,000 matching rows before it can return the next 20 — `Rows_examined`
scales with the offset itself, not just the table size. The new index made this
scan much cheaper per row (152ms vs 401ms), but the shape of the cost is still
`O(offset)`. A genuine fix would be cursor/keyset pagination (`WHERE created_at <
:lastSeenValue ORDER BY created_at DESC LIMIT 20` instead of `OFFSET`), which is a
real API/frontend contract change, not a one-line index addition — flagged, not
applied here.

**Free-text search (`shipmentNumber`/`trackingNumber LIKE '%x%'`) cannot use a
B-tree index at all** — a leading `%` wildcard defeats index range-scanning by
definition, in any RDBMS. This is inherent to "search as you type" over arbitrary
substrings, not a missing-index bug. At 100K rows it's already a full scan (153ms);
this will scale linearly and become a real bottleneck well before 1M rows per
tenant. A real fix would be a `FULLTEXT` index (matches whole tokens, not
substrings — a behavior change worth confirming with whoever owns this UI) or an
external search index (Elasticsearch/Meilisearch/similar) if substring-anywhere
matching must be preserved exactly. Flagged, not applied — this is a product
decision (what kind of search behavior is actually wanted), not a pure performance
fix.

## Slow-query-log methodology note for future runs

`SET GLOBAL slow_query_log = 'ON'` and `SET GLOBAL long_query_time = 0.1` (this
project's local MySQL only — never point this at a shared/production instance).
Both reset on a MySQL restart; re-run before the next PHASE 9 pass rather than
assuming they're still active.

## Not yet done

500K and 1M-shipment single-tenant runs (this pass only went to 100K, per the
brief's own first number) — the generator supports this directly
(`--perf.gen.shipmentsPerCompany=500000`/`1000000`), not run yet due to time.
Customer search, reports, and aggregation queries at equivalent single-tenant
volume (10K+ customers in one tenant) also not separately profiled — the existing
10K-per-tenant customer count already showed no slow queries in this pass, but
that's the same scale tested, not a genuinely larger one.

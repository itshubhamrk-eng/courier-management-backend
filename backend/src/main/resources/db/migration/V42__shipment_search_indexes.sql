-- =============================================================================
-- V42 — Missing indexes for shipment search at volume
--
-- Found running PHASE 9 (local performance testing, 100K shipments in one company)
-- against the real search endpoint: the two most common browse patterns — "newest
-- first, no filter" and "booking date range" — both did a full company-scoped table
-- scan (MySQL slow-query log: Rows_examined 100020 for a 20-row page in both cases).
-- idx_shipments_status (company_id, status, created_at) only helps once a `status`
-- predicate is present; neither of these two shapes has one. Confirmed via EXPLAIN
-- before adding: MySQL picked whichever company_id-leading index happened to exist
-- (idx_shipments_manifest, arbitrarily) purely to narrow to the tenant, then
-- scanned/filtered every row of it.
--
-- Both indexes are purely additive — no behavior change, only query-plan choices —
-- so this is safe to apply without touching application code. See
-- perf-tests/reports/PHASE9-DATABASE.md for the full before/after measurement.
-- =============================================================================

CREATE INDEX idx_shipments_company_created ON shipments (company_id, created_at);

CREATE INDEX idx_shipments_company_booking_date ON shipments (company_id, booking_date);

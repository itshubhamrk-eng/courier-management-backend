-- =============================================================================
-- V141 — Indexes for the security scanner's cross-company window reads.
--
-- SecurityScanJob runs every 2 minutes and range-scans two tables by time with no company
-- predicate (it is cross-company by design): refresh_tokens.created_at (session anomaly
-- rules) and login_history.occurred_at (revoked-session replay). Their existing indexes all
-- lead with company_id, so those scans would read the whole table. activity_logs is not
-- scanned by these rules, so no index is added there.
--
-- Forward-only.
-- =============================================================================

CREATE INDEX idx_refresh_tokens_created_at ON refresh_tokens (created_at);
CREATE INDEX idx_login_history_occurred_at ON login_history (occurred_at);

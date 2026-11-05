-- =============================================================================
-- V140 — Security incident scanner: its single-row cursor, plus a jti index.
--
-- security_scan_cursor holds exactly one row (id = 1): the upper bound of the last
-- window SecurityScanJob scanned. Absent on first boot; the job then inserts "now" and
-- scans nothing, so history from before the feature existed is never swept.
--
-- idx_refresh_tokens_jti is unrelated to the cursor: RefreshTokenRepository#findByJti
-- (refresh-token reuse detection) had no index and table-scanned refresh_tokens. Plain,
-- non-unique on purpose.
--
-- Forward-only.
-- =============================================================================

CREATE TABLE security_scan_cursor (
    id              INT          NOT NULL,
    last_scanned_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_refresh_tokens_jti ON refresh_tokens (jti);

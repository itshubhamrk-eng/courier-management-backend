-- =============================================================================
-- V143 — Indexes for the Task 9 scanner rules' cross-company window reads.
--
-- SecurityScanJob runs every 2 minutes with no company predicate, and these tables'
-- existing indexes lead with company_id/user_id, so without these the reads below would
-- scan the whole table:
--   * login_history (event_type, occurred_at): CREDENTIAL_ATTACK reads LOGIN_FAILED rows
--     in the window (LoginHistoryRepository.findLoginFailuresByIp /
--     findAccountsWithLoginFailuresAtLeast).
--   * activity_logs (status, occurred_at): ACCESS_DENIED_BURST reads FAILURE rows in the
--     window (ActivityLogRepository.findFailureCountsByErrorMessage). FAILURE rows are a
--     small slice of the table, so the range stays short.
-- BULK_ACTIVITY needs no new index: idx_activity_logs_action_time (action, occurred_at) and
-- idx_activity_logs_module_time (module, occurred_at) already cover its two queries.
--
-- Forward-only.
-- =============================================================================

CREATE INDEX idx_login_history_event_time ON login_history (event_type, occurred_at);
CREATE INDEX idx_activity_logs_status_time ON activity_logs (status, occurred_at);

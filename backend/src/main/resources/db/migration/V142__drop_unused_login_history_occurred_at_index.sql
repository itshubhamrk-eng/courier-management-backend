-- V142 — drop idx_login_history_occurred_at (added in V141).
--
-- REVOKED_TOKEN_REPLAY no longer reads login_history (it counts REFRESH_TOKEN_REUSE_DETECTED
-- audit rows, covered by idx_audit_logs_action), so nothing ranges over login_history.occurred_at
-- across companies and the index would only add write cost. idx_refresh_tokens_created_at stays.
--
-- Forward-only.

DROP INDEX idx_login_history_occurred_at ON login_history;

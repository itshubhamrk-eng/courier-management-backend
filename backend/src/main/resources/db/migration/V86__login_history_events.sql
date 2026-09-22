-- =============================================================================
-- V86 — login_history gains LOGOUT/SESSION_EXPIRED events and parsed device info.
--
-- login_history already recorded every login attempt (success + failure, with
-- LoginFailureReason) since the original auth migration. This turns it into the full
-- LOGIN_SUCCESS/LOGIN_FAILED/LOGOUT/SESSION_EXPIRED event log the User Activity &
-- Audit Logging module needs, instead of a second table duplicating user_id/
-- ip_address/session_id/occurred_at:
--
--   - event_type classifies every existing and future row; backfilled from the
--     existing `success` boolean so no history is lost.
--   - device/browser/os are parsed once at write time (UserAgentParser), same as
--     user_sessions gains below in V87.
--   - logout_at is filled in later, by the LOGOUT/SESSION_EXPIRED row's own service
--     call, when that session's original LOGIN_SUCCESS row is still findable by
--     session_id — best-effort, never required for the event log itself to be
--     complete (the LOGOUT/SESSION_EXPIRED row exists either way).
--
-- Forward-only. V85 is the previous migration.
-- =============================================================================

ALTER TABLE login_history
    ADD COLUMN event_type VARCHAR(20) NOT NULL DEFAULT 'LOGIN_SUCCESS' AFTER success,
    ADD COLUMN device      VARCHAR(30) NULL AFTER user_agent,
    ADD COLUMN browser     VARCHAR(60) NULL AFTER device,
    ADD COLUMN os          VARCHAR(60) NULL AFTER browser,
    ADD COLUMN logout_at   TIMESTAMP(6) NULL AFTER occurred_at;

UPDATE login_history SET event_type = 'LOGIN_SUCCESS' WHERE success = TRUE;
UPDATE login_history SET event_type = 'LOGIN_FAILED'  WHERE success = FALSE;

ALTER TABLE login_history
    ALTER COLUMN event_type DROP DEFAULT;

CREATE INDEX idx_login_history_session ON login_history (session_id);

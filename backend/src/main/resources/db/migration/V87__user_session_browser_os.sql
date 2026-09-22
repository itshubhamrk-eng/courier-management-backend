-- =============================================================================
-- V87 — user_sessions gains parsed browser/os, for the User Activity screen's
-- "current active session" list. device_id/device_name/device_type already existed;
-- this only adds the two fields they were missing.
--
-- Forward-only. V86 is the previous migration.
-- =============================================================================

ALTER TABLE user_sessions
    ADD COLUMN browser VARCHAR(60) NULL AFTER device_type,
    ADD COLUMN os       VARCHAR(60) NULL AFTER browser;

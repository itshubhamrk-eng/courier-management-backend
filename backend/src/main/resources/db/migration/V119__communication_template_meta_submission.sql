-- =============================================================================
-- V119 — Meta (WhatsApp Cloud API) approval state on a communication template.
--
-- A WhatsApp template must be submitted to Meta and APPROVED before any business-initiated
-- message can use it. These columns record what the app learned from Meta when the admin pressed
-- "Submit to Meta" / "Check status". All NULL = never submitted through the app (a template
-- created by hand in WhatsApp Manager still works — the send path only blocks a template the app
-- *knows* is not approved).
--
-- Editing a submitted template's name or content clears these columns (the Meta record no longer
-- describes what the app would send), so the admin has to submit again.
--
-- DAILY_BUSINESS_SUMMARY is a new communication_template.event_type value (VARCHAR, no DDL needed):
-- the template behind the 09:00 IST owner summary. Seeded lazily per company, like the others.
-- =============================================================================

ALTER TABLE communication_template
    ADD COLUMN meta_template_id   VARCHAR(40)  NULL AFTER status,
    ADD COLUMN meta_status        VARCHAR(20)  NULL AFTER meta_template_id,
    ADD COLUMN meta_status_detail VARCHAR(500) NULL AFTER meta_status,
    ADD COLUMN meta_category      VARCHAR(20)  NULL AFTER meta_status_detail,
    ADD COLUMN meta_language      VARCHAR(10)  NULL AFTER meta_category,
    ADD COLUMN meta_submitted_at  TIMESTAMP(6) NULL AFTER meta_language;

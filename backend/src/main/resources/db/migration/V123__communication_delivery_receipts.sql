-- =============================================================================
-- V123 — WhatsApp delivery receipts (Meta webhook) on the send ledgers.
--
-- Meta reports each message's journey after our send succeeds: sent -> delivered -> read, or
-- failed. The webhook (POST /api/v1/webhooks/whatsapp) matches a receipt to a row by the
-- provider message id we stored at send time and records when it happened:
--   delivered_at   when the phone received it   (also moves status SENT -> DELIVERED)
--   read_at        when the recipient opened it (status stays DELIVERED)
-- Until now DELIVERED was modelled but unreachable (no webhook), so the dashboard's Delivered
-- count was always 0.
--
-- provider_message_id gets an index: a receipt arrives with nothing but that id, across every
-- company, so without it each receipt is a full scan of the ledger.
-- =============================================================================

ALTER TABLE communication_log
    ADD COLUMN delivered_at TIMESTAMP(6) NULL AFTER sent_at,
    ADD COLUMN read_at      TIMESTAMP(6) NULL AFTER delivered_at,
    ADD KEY ix_communication_log_provider_message (provider_message_id);

-- The daily business summary is a message too; its receipts land here.
ALTER TABLE daily_report_log
    ADD COLUMN delivered_at TIMESTAMP(6) NULL AFTER sent_at,
    ADD COLUMN read_at      TIMESTAMP(6) NULL AFTER delivered_at,
    ADD KEY ix_daily_report_log_provider_message (provider_message_id);

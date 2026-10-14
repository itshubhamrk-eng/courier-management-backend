-- =============================================================================
-- V118 — Daily business summary (WhatsApp), one row per (company, report day, recipient).
--
-- The summary itself is built from live shipment/wallet data every morning at 09:00 IST for the
-- PREVIOUS calendar day, then sent to the phone numbers a company admin lists in the WhatsApp
-- channel config (config_json keys dailyReportPhones / dailyReportTemplate — no new settings
-- table). This table is the ledger: the unique key is what stops a restart, a retry tick or a
-- second node from sending the same day's report to the same number twice, and attempt_count
-- caps retries of a FAILED send.
-- =============================================================================

CREATE TABLE daily_report_log (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    -- The business day the figures describe (yesterday, IST) — not the day it was sent.
    report_date DATE        NOT NULL,
    recipient   VARCHAR(30) NOT NULL,

    -- PENDING | SENT | FAILED
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    provider_message_id VARCHAR(150) NULL,
    error_message       VARCHAR(1000) NULL,
    attempt_count       INT NOT NULL DEFAULT 0,
    last_attempt_at     TIMESTAMP(6) NULL,
    sent_at             TIMESTAMP(6) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_report_log_company_day_recipient (company_id, report_date, recipient),
    KEY ix_daily_report_log_company_date (company_id, report_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Send ledger for the daily WhatsApp business summary';

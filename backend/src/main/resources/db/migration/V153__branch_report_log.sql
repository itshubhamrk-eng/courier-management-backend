-- Send ledger for the monthly per-branch statement sent over WhatsApp, one row per
-- (company, branch, period, recipient). The unique key stops a second click or a retry from
-- sending the same statement to the same number twice; delivery/read receipts from Meta land here
-- like they do on daily_report_log, so the Communication dashboard counts these sends too.
CREATE TABLE branch_report_log (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    branch_id   BINARY(16)  NOT NULL,
    period_from DATE        NOT NULL,
    period_to   DATE        NOT NULL,
    recipient   VARCHAR(30) NOT NULL,

    -- PENDING | SENT | DELIVERED | FAILED
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    provider_message_id VARCHAR(150) NULL,
    error_message       VARCHAR(1000) NULL,
    attempt_count       INT NOT NULL DEFAULT 0,
    last_attempt_at     TIMESTAMP(6) NULL,
    sent_at             TIMESTAMP(6) NULL,
    delivered_at        TIMESTAMP(6) NULL,
    read_at             TIMESTAMP(6) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_branch_report_log_company_branch_period_recipient (company_id, branch_id, period_from, period_to, recipient),
    KEY ix_branch_report_log_company_period (company_id, period_from, period_to),
    KEY ix_branch_report_log_provider_message (provider_message_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Send ledger for the monthly branch statement over WhatsApp';

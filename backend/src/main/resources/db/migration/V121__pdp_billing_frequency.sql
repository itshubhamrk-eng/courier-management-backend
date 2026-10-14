-- =============================================================================
-- V121 — Pickup-Delivery Partner billing frequency.
--
-- One optional row per partner. No row means MONTHLY (the default), so existing partners keep
-- working untouched. The daily statement job reads this to decide when a partner's cycle closes.
-- =============================================================================

CREATE TABLE pdp_billing_settings (
    id                BINARY(16)  NOT NULL,
    company_id        BINARY(16)  NOT NULL,
    branch_id         BINARY(16)  NOT NULL,
    billing_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_pdp_billing_setting_branch (company_id, branch_id),
    CONSTRAINT fk_pdp_billing_setting_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'How often a Pickup-Delivery Partner is billed (absent = MONTHLY)';

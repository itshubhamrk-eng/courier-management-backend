-- =============================================================================
-- V46 — Per-company Razorpay credentials
--
-- Wallet recharge (V?? finance module) has always gone through one platform-wide
-- Razorpay account, configured only via env vars (RAZORPAY_KEY_ID/KEY_SECRET). This
-- table lets a company bring its own Razorpay account instead: one row per company,
-- created lazily on first save (not seeded like company_settings_config — most
-- companies will never touch this and don't need an empty row).
--
-- key_secret_encrypted is AES-256-GCM ciphertext (EncryptedStringConverter), never
-- the plaintext secret and never returned to any client — only key_id (publishable)
-- and whether a secret is configured are ever exposed via the API.
-- =============================================================================

CREATE TABLE company_razorpay_config (
    id        BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    enabled              BOOLEAN       NOT NULL DEFAULT FALSE,
    key_id               VARCHAR(255)  NULL,
    key_secret_encrypted VARCHAR(1000) NULL,

    -- BaseEntity columns. deleted stays FALSE — soft delete does not apply here.
    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- One Razorpay config row per company.
    UNIQUE KEY uk_company_razorpay_config_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Per-company Razorpay credentials for wallet recharge (key_secret_encrypted is AES-GCM, never returned to a client)';

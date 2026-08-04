-- =============================================================================
-- V8 — Company Settings (Phase 3, Company Administration)
--
-- The wide, admin-tunable settings: one typed row per company across eight sections.
--
-- This is a NEW table, `company_settings_config`. It does NOT touch the existing
-- key/value `company_settings` table (V4), which holds plan-derived facts (feature
-- flags, quota limits) that provisioning seeds and permission gating reads. Two
-- deliberately separate concerns: "what the plan grants" vs "how the company wants
-- the app to behave".
--
-- Company-owned, one row per company (UNIQUE company_id), created on first access.
-- Soft delete does not apply — a company always has settings — so the row is never
-- deleted; the BaseEntity deleted columns exist only for shape and stay FALSE.
--
-- Forward-only. V2 auth, V3 subscription, V4 company, V5 roles, V6 permissions,
-- V7 users, V8 this.
-- =============================================================================

CREATE TABLE company_settings_config (
    id        BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    -- --- General / Regional
    company_name    VARCHAR(150) NULL,
    display_name    VARCHAR(100) NULL,
    support_email   VARCHAR(255) NULL,
    support_mobile  VARCHAR(20)  NULL,
    website         VARCHAR(255) NULL,
    language        VARCHAR(10)  NOT NULL DEFAULT 'en',
    timezone        VARCHAR(64)  NOT NULL DEFAULT 'Asia/Kolkata',
    currency        VARCHAR(3)   NOT NULL DEFAULT 'INR',
    country         VARCHAR(100) NULL,
    state           VARCHAR(100) NULL,
    city            VARCHAR(100) NULL,

    -- --- Shipment
    awb_prefix                       VARCHAR(20)  NULL,
    awb_running_number               BIGINT       NOT NULL DEFAULT 1,
    booking_prefix                   VARCHAR(20)  NULL,
    manifest_prefix                  VARCHAR(20)  NULL,
    default_service_type             VARCHAR(40)  NOT NULL DEFAULT 'STANDARD',
    default_package_type             VARCHAR(40)  NOT NULL DEFAULT 'PARCEL',
    weight_unit                      VARCHAR(10)  NOT NULL DEFAULT 'KG',
    dimension_unit                   VARCHAR(10)  NOT NULL DEFAULT 'CM',
    auto_generate_barcode            BOOLEAN      NOT NULL DEFAULT TRUE,
    allow_duplicate_reference_number BOOLEAN      NOT NULL DEFAULT FALSE,
    auto_assign_tracking_number      BOOLEAN      NOT NULL DEFAULT TRUE,

    -- --- Finance
    gst_percentage          DECIMAL(5, 2)  NULL DEFAULT 18.00,
    invoice_prefix          VARCHAR(20)    NULL,
    credit_limit            DECIMAL(19, 4) NULL,
    wallet_enabled          BOOLEAN        NOT NULL DEFAULT FALSE,
    cod_enabled             BOOLEAN        NOT NULL DEFAULT TRUE,
    online_payment_enabled  BOOLEAN        NOT NULL DEFAULT FALSE,
    auto_invoice_generation BOOLEAN        NOT NULL DEFAULT FALSE,

    -- --- Notification
    sms_enabled               BOOLEAN NOT NULL DEFAULT FALSE,
    email_enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    whatsapp_enabled          BOOLEAN NOT NULL DEFAULT FALSE,
    push_notification_enabled BOOLEAN NOT NULL DEFAULT FALSE,

    -- --- Security (stored per company; auth does not consume these yet)
    password_policy         VARCHAR(40) NULL DEFAULT 'STANDARD',
    session_timeout_minutes INT         NULL DEFAULT 30,
    max_login_attempts      INT         NULL DEFAULT 5,
    lock_duration_minutes   INT         NULL DEFAULT 15,
    otp_expiry_minutes      INT         NULL DEFAULT 5,

    -- --- Branding
    company_logo    VARCHAR(500) NULL,
    favicon         VARCHAR(500) NULL,
    primary_color   VARCHAR(9)   NULL DEFAULT '#1976D2',
    secondary_color VARCHAR(9)   NULL DEFAULT '#424242',
    theme           VARCHAR(10)  NOT NULL DEFAULT 'LIGHT',

    -- --- Document templates
    invoice_template  VARCHAR(100) NULL,
    label_template    VARCHAR(100) NULL,
    manifest_template VARCHAR(100) NULL,
    receipt_template  VARCHAR(100) NULL,

    -- BaseEntity columns. deleted stays FALSE — soft delete does not apply here.
    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- One settings row per company.
    UNIQUE KEY uk_company_settings_config_company (company_id),

    CONSTRAINT ck_company_settings_gst CHECK (gst_percentage IS NULL
        OR (gst_percentage >= 0 AND gst_percentage <= 100))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Per-company configurable behaviour settings (wide, one row per company)';

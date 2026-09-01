-- =============================================================================
-- V50 — Communication Center
--
-- Multi-channel (WhatsApp/SMS/Email) event-driven customer communication. Business
-- modules (Shipment Booking/Movement) never send messages themselves — they publish
-- ShipmentEvent records (see ShipmentEvent.Booked/Dispatched/ReceivedAtBranch/
-- OutForDelivery/Delivered/Cancelled), and this module's own listener does the rest:
-- find enabled channels -> load template -> build message -> send -> store result.
--
-- Three new tables, all company-owned (own id/company_id/soft-delete/version, the
-- project's usual shape):
--
--   communication_setting  — one row per (company, channel). The channel-level master
--                             switch and provider config for that channel. `enabled`
--                             here means "WhatsApp/SMS/Email is usable at all for this
--                             company" — separate from whether one specific event fires
--                             on that channel (that's communication_template.status,
--                             see below). config_json holds non-secret provider config
--                             (phoneNumberId/businessAccountId/apiUrl/senderId/fromName/
--                             fromEmail); secret_encrypted holds the one genuine secret
--                             per channel (WhatsApp access token / SMS API key),
--                             AES-256-GCM ciphertext via EncryptedStringConverter, the
--                             same converter company_razorpay_config (V46) already
--                             uses — never returned to a client, never logged.
--
--   communication_template — one row per (company, event_type, channel). Content with
--                             {{variable}} placeholders (see TemplateRenderer). `status`
--                             (ACTIVE/INACTIVE) is the actual per-event-per-channel
--                             on/off switch the brief's "Company Admin can enable/
--                             disable each channel per event" describes — an inactive
--                             template means that event never sends on that channel,
--                             independent of the channel's own master switch above.
--
--   communication_log      — one row per (shipment, event_type, channel) ever
--                             attempted — the retry ledger and duplicate-protection
--                             record in one. A retry updates attempt_count/status on
--                             the SAME row rather than inserting a new one, which is
--                             what "no duplicate sends unless explicitly retried"
--                             actually means here. last_attempt_at/next_retry_at back
--                             the retry sweep (CommunicationDispatchJob).
--
-- customers gains three preference columns (whatsapp/sms/email enabled, default TRUE —
-- opt-out, not opt-in). Effective sending is company channel enabled AND that event's
-- template ACTIVE on that channel AND the customer's own preference for that channel.
-- =============================================================================

CREATE TABLE communication_setting (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    -- WHATSAPP | SMS | EMAIL
    channel VARCHAR(20) NOT NULL,

    enabled  BOOLEAN      NOT NULL DEFAULT FALSE,
    -- WhatsApp: META_CLOUD_API. SMS: the vendor name, free text, never hardcoded in
    -- application code. Email: SMTP (platform mail infra, company only sets identity).
    provider VARCHAR(50)  NULL,

    -- Non-secret provider config as JSON: WhatsApp {phoneNumberId,businessAccountId},
    -- SMS {apiUrl,senderId}, Email {fromName,fromEmail}. Never a secret.
    config_json TEXT NULL,

    -- The one real secret per channel (WhatsApp access token / SMS API key). AES-256-GCM
    -- via EncryptedStringConverter — see V46's own comment for the exact shape. Email has
    -- none: platform SMTP credentials are env-configured, not per-company.
    secret_encrypted VARCHAR(2000) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_communication_setting_company_channel (company_id, channel)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Per-company, per-channel communication master switch + provider config';

CREATE TABLE communication_template (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    -- SHIPMENT_BOOKED | SHIPMENT_DISPATCHED | SHIPMENT_RECEIVED | OUT_FOR_DELIVERY |
    -- SHIPMENT_DELIVERED | SHIPMENT_CANCELLED | RTO_INITIATED | RTO_DELIVERED
    -- RTO_* are declared for architecture readiness only — no shipment-module writer
    -- for a return-to-origin flow exists yet (see ShipmentStatus's own doc comment on
    -- RETURNED being "declared but unwritten"), so no event ever fires for them today.
    event_type VARCHAR(30) NOT NULL,
    -- WHATSAPP | SMS | EMAIL
    channel    VARCHAR(20) NOT NULL,

    template_name VARCHAR(150) NOT NULL,
    -- Email only; NULL for WhatsApp/SMS.
    subject       VARCHAR(255) NULL,
    content       TEXT         NOT NULL,

    -- ACTIVE | INACTIVE — the per-event-per-channel on/off switch.
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_communication_template_company_event_channel (company_id, event_type, channel)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One customizable template per company/event/channel';

CREATE TABLE communication_log (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    shipment_id BINARY(16) NOT NULL,
    customer_id BINARY(16) NULL,

    event_type VARCHAR(30) NOT NULL,
    channel    VARCHAR(20) NOT NULL,
    recipient  VARCHAR(150) NOT NULL,
    template_id BINARY(16) NULL,

    -- PENDING | SENT | DELIVERED | FAILED | CANCELLED
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    provider_message_id VARCHAR(150) NULL,
    error_message        VARCHAR(1000) NULL,

    attempt_count   INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP(6) NULL,
    next_retry_at   TIMESTAMP(6) NULL,
    sent_at         TIMESTAMP(6) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- One log row ever, per shipment/event/channel — a retry updates this same row.
    UNIQUE KEY uk_communication_log_shipment_event_channel (shipment_id, event_type, channel),
    KEY ix_communication_log_company_status (company_id, status),
    KEY ix_communication_log_next_retry (status, next_retry_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One row per shipment/event/channel notification attempt, retried in place';

ALTER TABLE customers
    ADD COLUMN whatsapp_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Customer communication preference — opt-out, not opt-in',
    ADD COLUMN sms_enabled      BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Customer communication preference — opt-out, not opt-in',
    ADD COLUMN email_enabled    BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Customer communication preference — opt-out, not opt-in';

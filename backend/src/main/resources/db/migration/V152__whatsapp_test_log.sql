-- Send ledger for the "Send Test Message" button in Channel Settings, so test sends show on the
-- Communication dashboard and take Meta's delivery/read receipts like any other WhatsApp message.
CREATE TABLE whatsapp_test_log (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    recipient     VARCHAR(30)  NOT NULL,
    template_name VARCHAR(150) NULL,

    -- SENT | DELIVERED | FAILED
    status VARCHAR(20) NOT NULL DEFAULT 'SENT',

    provider_message_id VARCHAR(150) NULL,
    error_message       VARCHAR(1000) NULL,
    sent_at             TIMESTAMP(6) NULL,
    delivered_at        TIMESTAMP(6) NULL,
    read_at             TIMESTAMP(6) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY ix_whatsapp_test_log_company_created (company_id, created_at),
    KEY ix_whatsapp_test_log_provider_message (provider_message_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Send ledger for WhatsApp test messages';

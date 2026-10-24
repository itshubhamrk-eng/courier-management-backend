-- Failed delivery attempts (mobile app G12). One row per attempt that did not end in delivery.
-- The 3rd failed attempt returns the shipment to origin (RETURNED); attempts 1-2 send it back to
-- the delivery branch / hub (IN_SCAN or READY_FOR_MANIFEST) for the next DRS.
CREATE TABLE delivery_attempts (
    id               BINARY(16)   NOT NULL,
    company_id       BINARY(16)   NOT NULL,

    shipment_id      BINARY(16)   NOT NULL,
    attempt_no       INT          NOT NULL,
    reason           VARCHAR(30)  NOT NULL,
    remarks          VARCHAR(500) NULL,
    outcome          VARCHAR(20)  NOT NULL,
    delivery_user_id BINARY(16)   NULL,
    delivery_branch_id BINARY(16) NULL,
    drs_number       VARCHAR(20)  NULL,
    attempted_at     TIMESTAMP(6) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- Two requests racing for the same attempt number fail here instead of double-counting.
    UNIQUE KEY uk_delivery_attempts_shipment_no (company_id, shipment_id, attempt_no),
    KEY idx_delivery_attempts_attempted (company_id, attempted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Failed delivery attempts; 3rd failure returns the shipment to origin';

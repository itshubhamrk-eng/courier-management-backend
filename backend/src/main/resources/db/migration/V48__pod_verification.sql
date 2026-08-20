-- =============================================================================
-- V48 — POD Auto Verification
--
-- One row per AI verification run against an uploaded delivery photo/signature.
-- pod_document_id points at shipment_assets.id (V33) — no physical FK, same
-- cross-module-id convention as shipments.booking_branch_id / manifests.vehicle_id —
-- the asset itself is written by ShipmentService.attachPodAsset (shipment module owns
-- the document store), this module only records what the AI made of it.
--
-- pod_hash (SHA-256 of the uploaded photo bytes) is not in the brief's own field list
-- but is required to implement "Duplicate POD" detection honestly — flagging a photo
-- reused across shipments needs something to compare against.
--
-- review_remarks is likewise an addition: the brief's Manual Review section requires
-- "Add remarks" but the brief's own field list for the table omitted a column for it.
-- =============================================================================

CREATE TABLE pod_verification (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    shipment_id     BINARY(16) NOT NULL,
    pod_document_id BINARY(16) NULL,

    -- PASS | REVIEW | FAIL
    verification_status VARCHAR(20) NOT NULL,
    verification_score  INT NOT NULL,
    verification_reasons TEXT NULL,

    detected_receiver_name VARCHAR(255) NULL,
    detected_awb           VARCHAR(100) NULL,
    detected_date           VARCHAR(50) NULL,
    signature_detected      BOOLEAN NOT NULL DEFAULT FALSE,
    image_quality            VARCHAR(20) NULL,

    pod_hash CHAR(64) NULL,

    ai_provider VARCHAR(50) NOT NULL,
    ai_model    VARCHAR(50) NOT NULL,
    verified_at TIMESTAMP(6) NULL,

    reviewed_by      BINARY(16) NULL,
    reviewed_at      TIMESTAMP(6) NULL,
    review_remarks   VARCHAR(1000) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_pod_verification_shipment (company_id, shipment_id, created_at),
    KEY idx_pod_verification_status (company_id, verification_status),
    KEY idx_pod_verification_hash (company_id, pod_hash),

    CONSTRAINT chk_pod_verification_score CHECK (verification_score BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'POD Auto Verification: one AI verification run per POD upload attempt';

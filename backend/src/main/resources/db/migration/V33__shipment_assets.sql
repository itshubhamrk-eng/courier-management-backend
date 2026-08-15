-- =============================================================================
-- V33 — Shipment images move into their own table
--
-- On direct request: upload a shipment image during booking, show it on the shipment's
-- tracking/detail page. Rather than a single new column bolted onto `shipments`, a new
-- `shipment_assets` table holds every uploaded shipment image, `asset_type` distinguishing
-- what it was captured for — `BOOKING` (this feature) or `POD` (delivery proof, previously
-- two columns directly on `delivery_assignment`). One row per upload, immutable; the
-- newest row per (shipment, asset_type, kind) is what a read returns (see
-- ShipmentMapper's helper) — the same "current state is the newest row" shape
-- `delivery_assignment.photo_url`/`signature_url` used to carry directly, now generalised.
--
-- Existing POD photo/signature urls are copied out of `delivery_assignment` before those
-- two columns are dropped — a real migration of live data, not just new-feature scaffolding
-- alongside the old columns.
-- =============================================================================

CREATE TABLE shipment_assets (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    shipment_id BINARY(16) NOT NULL,

    -- BOOKING | POD
    asset_type VARCHAR(20) NOT NULL,
    -- PHOTO | SIGNATURE — for BOOKING always PHOTO
    kind       VARCHAR(20) NOT NULL,
    asset_url  VARCHAR(1000) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_shipment_assets_shipment (company_id, shipment_id, asset_type, kind),

    CONSTRAINT fk_shipment_assets_shipment FOREIGN KEY (shipment_id)
        REFERENCES shipments (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Shipment Booking: uploaded shipment images (BOOKING photo, POD photo/signature)';

-- Carry every existing POD capture forward before the source columns disappear.
INSERT INTO shipment_assets (id, company_id, shipment_id, asset_type, kind, asset_url,
                             created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), da.company_id, da.shipment_id, 'POD', 'PHOTO', da.photo_url,
       da.updated_at, da.updated_at, FALSE, 0
FROM delivery_assignment da
WHERE da.photo_url IS NOT NULL;

INSERT INTO shipment_assets (id, company_id, shipment_id, asset_type, kind, asset_url,
                             created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), da.company_id, da.shipment_id, 'POD', 'SIGNATURE', da.signature_url,
       da.updated_at, da.updated_at, FALSE, 0
FROM delivery_assignment da
WHERE da.signature_url IS NOT NULL;

ALTER TABLE delivery_assignment
    DROP COLUMN photo_url,
    DROP COLUMN signature_url;

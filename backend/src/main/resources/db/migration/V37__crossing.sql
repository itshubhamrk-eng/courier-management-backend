-- =============================================================================
-- V37 — Crossing
--
-- A shipment may route through an intermediate branch (a "crossing" hub) on its way
-- to the delivery branch, instead of travelling booking-branch -> delivery-branch
-- directly. Two additions:
--
--   shipments.current_location_id / next_location_id — the shipment's own position
--   in the network. Set once at booking time (ShipmentServiceImpl): current_location_id
--   is always the booking branch; next_location_id is the crossing branch when one was
--   picked, otherwise the delivery branch. Neither column is a physical FK — branch is
--   a different module (company), the same cross-module treatment booking_branch_id/
--   delivery_branch_id already get on this table (V17).
--
--   crossing_details — one row per shipment that opted into a crossing, owned by the
--   new com.courier.modules.crossing module. No physical FK to shipments either: this
--   is a new top-level module, not a submodule of Shipment Booking, so the same
--   cross-module rule applies. One row per shipment (not append-only) — a shipment
--   crosses through one hub at a time, mirroring the "current state, not ledger" split
--   delivery_assignment already draws in V19.
-- =============================================================================

ALTER TABLE shipments
    ADD COLUMN current_location_id BINARY(16) NULL AFTER manifest_id,
    ADD COLUMN next_location_id    BINARY(16) NULL AFTER current_location_id;

CREATE INDEX idx_shipments_next_location ON shipments (company_id, next_location_id);

CREATE TABLE crossing_details (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    shipment_id BINARY(16) NOT NULL,
    branch_id   BINARY(16) NOT NULL,

    -- PENDING | IN_TRANSIT | COMPLETED | CANCELLED
    status VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    charge DECIMAL(19,4) NOT NULL DEFAULT 0,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_crossing_details_company_shipment (company_id, shipment_id),
    KEY idx_crossing_details_branch_status (company_id, branch_id, status),

    CONSTRAINT ck_crossing_details_charge CHECK (charge >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Crossing: hub/branch a shipment transits through en route to delivery, one row per shipment';

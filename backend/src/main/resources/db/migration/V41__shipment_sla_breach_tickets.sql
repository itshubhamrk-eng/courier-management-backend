-- =============================================================================
-- V41 — Shipment lifecycle SLA: auto-raise a ticket when a shipment sits too
-- long in one stage without moving to the next.
--
-- Distinct from V40's ticket_sla_rules (that's how fast STAFF must respond to
-- a ticket already raised). This is about the SHIPMENT itself: booked but no
-- loading sheet, loading sheet but no THC, THC but no in-scan, in-scan but no
-- DRS, DRS but not delivered — each transition has its own company-configured
-- hour threshold. A scheduled sweep (ShipmentSlaSweepJob, hourly) compares the
-- shipment's current status age (from shipment_status_history) against the
-- company's thresholds and raises one ticket per shipment per stage, ever
-- (shipment_sla_breaches is the idempotency record, not a ledger).
-- =============================================================================

ALTER TABLE company_settings_config
    ADD COLUMN sla_breach_ticket_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN sla_booking_to_loading_sheet_hours INT     NOT NULL DEFAULT 24,
    ADD COLUMN sla_loading_sheet_to_thc_hours     INT     NOT NULL DEFAULT 24,
    ADD COLUMN sla_thc_to_inscan_hours            INT     NOT NULL DEFAULT 48,
    ADD COLUMN sla_inscan_to_drs_hours            INT     NOT NULL DEFAULT 12,
    ADD COLUMN sla_drs_to_delivery_hours          INT     NOT NULL DEFAULT 12;

-- A system-raised ticket has no human requester — every prior ticket did, so
-- this column was NOT NULL until now.
ALTER TABLE tickets
    MODIFY COLUMN created_by_user_id BINARY(16) NULL;

INSERT INTO ticket_categories (id, name, active, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), 'SLA Breach', TRUE, NOW(6), NOW(6), FALSE, 0
WHERE NOT EXISTS (SELECT 1 FROM ticket_categories WHERE name = 'SLA Breach');

CREATE TABLE shipment_sla_breaches (
    id           BINARY(16) NOT NULL,
    company_id   BINARY(16) NOT NULL,
    shipment_id  BINARY(16) NOT NULL,

    -- BOOKING_TO_LOADING_SHEET | LOADING_SHEET_TO_THC | THC_TO_INSCAN |
    -- INSCAN_TO_DRS | DRS_TO_DELIVERY
    stage         VARCHAR(40) NOT NULL,
    ticket_id     BINARY(16) NOT NULL,
    hours_elapsed INT NOT NULL,
    detected_at   TIMESTAMP(6) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_sla_breaches_shipment_stage (company_id, shipment_id, stage),
    KEY idx_shipment_sla_breaches_ticket (ticket_id),

    CONSTRAINT fk_shipment_sla_breaches_ticket FOREIGN KEY (ticket_id)
        REFERENCES tickets (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One row per shipment per SLA stage ever breached — idempotency for the sweep, not a ledger';

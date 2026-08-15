-- =============================================================================
-- V31 — DRS gets a real, unique, printable number
--
-- On direct request: "every drs should have a uniq number as DRS000001". Generated once
-- per bulk assignOutForDelivery call ("Generate DRS") and stamped on every
-- delivery_assignment row that call touches — a company-wide counter, same
-- LAST_INSERT_ID(expr) upsert idiom as company_shipment_sequences (V22)/
-- branch_shipment_sequences (V21), just its own table since a DRS run's pace has nothing
-- to do with either of those series. Format "DRS" + 6-digit zero-padded serial (DRS000001),
-- no date component — see ShipmentServiceImpl.nextDrsNumber.
--
-- Nullable: existing delivery_assignment rows predate this feature and stay unnumbered
-- (they were grouped into DRS Report runs purely by delivery user + branch + day, see
-- MEMORY/modules/shipment-movement.md — that grouping is unchanged, this column is an
-- added attribute on top of it, not a new grouping key).
-- =============================================================================

CREATE TABLE company_drs_sequences (
    company_id     BINARY(16) NOT NULL,
    sequence_value BIGINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (company_id)
);

ALTER TABLE delivery_assignment
    ADD COLUMN drs_number VARCHAR(20) NULL AFTER assigned_at;

-- =============================================================================
-- V65 — Shipment Insurance Applicable
--
-- Optional checkbox at booking time: when true, insurance is charged at 2% of freight
-- instead of the Pricing Engine's own rate-driven insurance figure (ShipmentServiceImpl
-- .copyCharge). Same shape as appointment_delivery (V60) — a flag on shipments, no new
-- column on shipment_charges (insurance_charge already exists there).
-- =============================================================================

ALTER TABLE shipments
    ADD COLUMN insurance_applicable BOOLEAN NOT NULL DEFAULT FALSE AFTER appointment_time_slot;

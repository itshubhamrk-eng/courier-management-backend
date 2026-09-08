-- =============================================================================
-- V58: branches gain a weight-based delivery commission, separate from the existing
-- drs_charge_per_qty (a fixed amount per item quantity). This one is
-- rate * chargeable weight in kg, with a minimum chargeable weight floor so a very
-- light shipment still earns at least the floor's worth of commission. Both editable
-- per branch. Defaults: 1.50/kg, 10.00 kg minimum.
-- =============================================================================

ALTER TABLE branches
    ADD COLUMN delivery_commission_rate_per_kg DECIMAL(10, 2) NOT NULL DEFAULT 1.50 AFTER drs_charge_per_qty,
    ADD COLUMN delivery_commission_min_weight_kg DECIMAL(10, 2) NOT NULL DEFAULT 10.00 AFTER delivery_commission_rate_per_kg;

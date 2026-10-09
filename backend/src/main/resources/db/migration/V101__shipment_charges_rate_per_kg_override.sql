-- =============================================================================
-- V101 — shipment_charges.rate_per_kg_override
--
-- The Rate/KG a company admin fixed on a booked shipment. Null = freight follows the
-- District Level Freight slab. Kept so an operator's later edit of the booking (weight,
-- pincode, ...) re-prices at the admin's rate instead of silently dropping it.
-- =============================================================================
ALTER TABLE shipment_charges
    ADD COLUMN rate_per_kg_override DECIMAL(19, 4) NULL AFTER applied_freight_factor;

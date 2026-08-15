-- =============================================================================
-- V24: shipment_charges gains other_charges — a manual, user-entered amount typed at
-- booking time (e.g. packing, handling extras) on top of the Pricing Engine's own
-- rate-driven lines. Unlike freight/fuel/gst/etc., nothing calculates this figure; the
-- booking desk types it, and it is added straight into net_amount.
-- =============================================================================

ALTER TABLE shipment_charges
    ADD COLUMN other_charges DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER round_off;

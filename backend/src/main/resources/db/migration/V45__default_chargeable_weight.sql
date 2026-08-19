-- =============================================================================
-- V45 — Company-level default chargeable weight
--
-- The item grid's per-row weight prefill (a booking-time UX default, not a pricing
-- floor — ChargeableWeightCalculator still computes MAX(actual, volumetric) with no
-- minimum) was hardcoded in the frontend. Made company-configurable, in the existing
-- Shipment section of `company_settings_config` (V8) alongside the other booking
-- defaults (default_service_type, default_package_type).
-- =============================================================================

ALTER TABLE company_settings_config
    ADD COLUMN default_chargeable_weight_kg DECIMAL(10, 3) NOT NULL DEFAULT 20.000
        AFTER auto_assign_tracking_number;

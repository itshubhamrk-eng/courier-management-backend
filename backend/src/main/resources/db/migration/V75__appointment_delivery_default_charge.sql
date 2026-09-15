-- =============================================================================
-- V75 — Company-level default Appointment Delivery charge
--
-- Shipment Booking's Appointment Charge field was manual-only (blank/zero start).
-- Direct request: give it a company-configurable default so the desk isn't typing the
-- same figure every time. Added to company_settings_config's Shipment section (V8), next
-- to default_chargeable_weight_kg. DECIMAL(19,4) — same money-shape as other charge/amount
-- columns in this table (credit_limit, eway_bill_mandatory_value).
-- =============================================================================

ALTER TABLE company_settings_config
    ADD COLUMN default_appointment_delivery_charge DECIMAL(19,4) NOT NULL DEFAULT 1000.0000
        AFTER default_chargeable_weight_kg;

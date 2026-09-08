-- =============================================================================
-- V64 — Company-level round-off rule for Shipment Booking's final amount
--
-- The pricing engine's round-off (RoundOffCalculator, applied to
-- PricingContext.totalBeforeRoundOff() before Net Amount) was a deployment-wide Spring
-- property (pricing.rounding-rule), defaulting to NEAREST_FIVE for every company alike —
-- PricingConfiguration's own javadoc already flagged this as "not per-company yet ...
-- no caller has asked for company-level overrides". One has now: Shipment Booking's
-- final amount should round to nearest 5, company-configurable. Added in the existing
-- Finance section of company_settings_config (V8), stored as plain VARCHAR (the same
-- treatment password_policy already gets) rather than the pricing module's own
-- RoundingRule enum, so company stays decoupled from pricing (pricing already depends
-- on company one-way, via CompanySettingsService).
-- =============================================================================

ALTER TABLE company_settings_config
    ADD COLUMN round_off_rule VARCHAR(20) NOT NULL DEFAULT 'NEAREST_FIVE'
        AFTER auto_invoice_generation;

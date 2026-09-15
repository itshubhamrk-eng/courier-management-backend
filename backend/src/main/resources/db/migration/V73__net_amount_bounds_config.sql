-- =============================================================================
-- V73 — Company-level Net Amount edit bounds for Shipment Booking
--
-- The booking form's Net Amount field (ChargeSummary, editable) is a typeable preview
-- override with no bound today, so an operator can type anything. Direct request: cap how
-- far it may move from the engine-computed amount — decrease and increase bounds,
-- percentage-based, company-configurable. Added to the existing Finance section of
-- company_settings_config (V8), next to round_off_rule (V64).
-- =============================================================================

ALTER TABLE company_settings_config
    ADD COLUMN net_amount_max_decrease_percent DECIMAL(5,2) NOT NULL DEFAULT 10.00
        AFTER round_off_rule,
    ADD COLUMN net_amount_max_increase_percent DECIMAL(5,2) NOT NULL DEFAULT 50.00
        AFTER net_amount_max_decrease_percent;

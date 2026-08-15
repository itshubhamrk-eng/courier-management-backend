-- =============================================================================
-- V25: branches gain four branch-level charge percentages, set at creation and
-- editable on update. Defaults: GST 18%, commission on other charges 20%, commission
-- on basic freight 10%, company service charge 10%.
-- =============================================================================

ALTER TABLE branches
    ADD COLUMN gst_percentage DECIMAL(5, 2) NOT NULL DEFAULT 18.00 AFTER remarks,
    ADD COLUMN commission_on_other_charges DECIMAL(5, 2) NOT NULL DEFAULT 20.00 AFTER gst_percentage,
    ADD COLUMN commission_on_basic_freight DECIMAL(5, 2) NOT NULL DEFAULT 10.00 AFTER commission_on_other_charges,
    ADD COLUMN company_service_charge_percentage DECIMAL(5, 2) NOT NULL DEFAULT 10.00 AFTER commission_on_basic_freight;

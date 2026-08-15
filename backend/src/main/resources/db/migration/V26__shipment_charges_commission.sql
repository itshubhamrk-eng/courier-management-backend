-- =============================================================================
-- V26: shipment_charges gains the branch commission breakup, computed at booking
-- time from the booking branch's own charge percentages (V25): branch commission
-- (on basic freight + the branch's share of other charges) and the company service
-- charge (on basic freight).
-- =============================================================================

ALTER TABLE shipment_charges
    ADD COLUMN branch_commission DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER other_charges,
    ADD COLUMN company_service_charge DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER branch_commission;

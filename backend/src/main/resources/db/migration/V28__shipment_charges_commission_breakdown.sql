-- =============================================================================
-- V28: shipment_charges' commission columns (V26) are broken out into the four
-- lines the business actually tracks: the branch's commission on basic freight,
-- the branch's commission on its share of other charges, the company's own
-- commission on basic freight (V26's "company service charge", renamed to match),
-- and the branch's total commission (the first two, summed and stored so a report
-- never has to re-derive it).
-- =============================================================================

ALTER TABLE shipment_charges
    CHANGE COLUMN branch_commission commission_on_basic_freight DECIMAL(19, 4) NOT NULL DEFAULT 0,
    CHANGE COLUMN company_service_charge company_commission_on_basic_freight DECIMAL(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN branch_commission_on_other_amount DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER commission_on_basic_freight,
    ADD COLUMN total_commission DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER company_commission_on_basic_freight;

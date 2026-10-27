-- total_commission is now the branch's own commission only (freight + other charges),
-- no longer including the company's cut (company_commission_on_basic_freight).
UPDATE shipment_charges
SET total_commission = commission_on_basic_freight + branch_commission_on_other_amount;

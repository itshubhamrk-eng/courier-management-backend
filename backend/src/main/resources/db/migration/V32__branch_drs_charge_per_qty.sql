-- =============================================================================
-- V32: branches gain a fifth branch-level charge, set at creation and editable on
-- update: drs_charge_per_qty, a fixed amount (not a percentage, unlike the other
-- four) debited from the delivery branch's wallet for every item quantity delivered
-- through DRS. Default 2.00.
-- =============================================================================

ALTER TABLE branches
    ADD COLUMN drs_charge_per_qty DECIMAL(10, 2) NOT NULL DEFAULT 2.00 AFTER company_service_charge_percentage;

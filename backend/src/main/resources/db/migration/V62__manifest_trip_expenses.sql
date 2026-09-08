ALTER TABLE manifests
    ADD COLUMN fuel_cost DECIMAL(12, 2) NULL AFTER remarks,
    ADD COLUMN driver_advance DECIMAL(12, 2) NULL AFTER fuel_cost,
    ADD COLUMN toll_amount DECIMAL(12, 2) NULL AFTER driver_advance,
    ADD COLUMN other_amount DECIMAL(12, 2) NULL AFTER toll_amount;

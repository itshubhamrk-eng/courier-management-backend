-- V80: Direct Company Delivery — a Load Sheet can now either assign a real Delivery
-- Branch (BRANCH_DELIVERY, the existing flow) or hand the shipment straight to a company
-- vehicle/driver with no delivery branch at all (DIRECT_COMPANY_DELIVERY). Every existing
-- manifest is BRANCH_DELIVERY (the only mode that ever existed), so the new column
-- backfills to that default.

ALTER TABLE manifests
    ADD COLUMN delivery_mode VARCHAR(30) NOT NULL DEFAULT 'BRANCH_DELIVERY' AFTER destination_city;

ALTER TABLE manifests
    MODIFY COLUMN delivery_branch_id BINARY(16) NULL;

ALTER TABLE delivery_assignment
    MODIFY COLUMN delivery_branch_id BINARY(16) NULL;

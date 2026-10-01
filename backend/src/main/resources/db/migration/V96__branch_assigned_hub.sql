-- A branch's own hub: Loading Sheet defaults to sending its shipments here.
-- Plain column, no FK, same as branches.manager_id (validated in the service).
ALTER TABLE branches
    ADD COLUMN assigned_hub_id BINARY(16) NULL AFTER manager_id;

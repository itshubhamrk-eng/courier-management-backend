-- V79: Load Sheet workflow. A shipment no longer gets its Delivery Branch persisted at
-- booking (V69 already made the column nullable and dropped the picker; delivery_branch_id
-- is now left NULL until a real Load Sheet assigns one) — see ShipmentServiceImpl.create/
-- update. Manifest (the "Load Sheet") gains the destination city it was created for, so
-- BOOKED shipments with no delivery branch yet can be matched by where they're going
-- (to_city) rather than by a branch that doesn't exist for them yet.

ALTER TABLE manifests
    ADD COLUMN destination_city VARCHAR(120) NULL AFTER delivery_branch_id;

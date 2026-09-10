-- =============================================================================
-- V69: Shipment Booking no longer needs a Delivery Branch picker — the operator picks
-- From City / To City instead, and delivery_branch_id is resolved server-side off the
-- destination pincode's branch_pincode_mapping (same lookup Destination Pincode already
-- auto-selected a branch from). A pincode with no mapping on file no longer blocks
-- booking, so delivery_branch_id must be nullable — it is filled in for real once the
-- shipment reaches Loading Sheet / THC generation.
-- =============================================================================

ALTER TABLE shipments
    MODIFY COLUMN delivery_branch_id BINARY(16) NULL;

ALTER TABLE shipments
    ADD COLUMN from_city VARCHAR(120) NULL AFTER delivery_branch_id,
    ADD COLUMN to_city VARCHAR(120) NULL AFTER from_city;

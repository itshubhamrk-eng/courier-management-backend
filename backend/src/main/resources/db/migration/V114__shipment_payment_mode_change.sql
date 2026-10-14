-- V114: an admin can flip a still-BOOKED shipment between PAID and TO_PAY (wallet debited or
-- refunded in the same transaction). These columns remember that it happened so Track
-- Shipment can highlight the shipment. All nullable: every existing shipment stays
-- "never changed". No physical FK on the previous mode — same treatment as the other
-- master-data ids on shipments.
ALTER TABLE shipments
    ADD COLUMN payment_mode_changed_from_id BINARY(16) NULL,
    ADD COLUMN payment_mode_changed_at      TIMESTAMP(6) NULL,
    ADD COLUMN payment_mode_changed_by      BINARY(16) NULL;

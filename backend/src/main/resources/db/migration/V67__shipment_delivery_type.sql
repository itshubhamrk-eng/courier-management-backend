-- =============================================================================
-- V67: Door Delivery / Office Delivery — choice at booking time, mirrors Appointment
-- Delivery (V60). DOOR may carry a manual, GST-free Door Delivery Charge; OFFICE never
-- charges extra, regardless of what was typed — enforced server-side, not just UX.
-- =============================================================================

ALTER TABLE shipments
    ADD COLUMN delivery_type VARCHAR(10) NOT NULL DEFAULT 'DOOR' AFTER appointment_time_slot;

ALTER TABLE shipment_charges
    ADD COLUMN door_delivery_charge DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER appointment_delivery_charge;

-- =============================================================================
-- V60: Appointment Delivery — optional at booking time. When checked, the operator
-- picks a delivery date and a free-text time slot (e.g. "1:00-2:00") and may charge an
-- Appointment Delivery Charge. That charge is deliberately GST-free, added straight
-- into net_amount alongside gst_amount rather than folded into it the way
-- other_charges' own GST is — direct user request ("charge should be without gst").
-- =============================================================================

ALTER TABLE shipments
    ADD COLUMN appointment_delivery BOOLEAN NOT NULL DEFAULT FALSE AFTER remarks,
    ADD COLUMN appointment_date DATE NULL AFTER appointment_delivery,
    ADD COLUMN appointment_time_slot VARCHAR(20) NULL AFTER appointment_date;

ALTER TABLE shipment_charges
    ADD COLUMN appointment_delivery_charge DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER other_charges;

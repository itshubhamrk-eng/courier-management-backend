-- =============================================================================
-- V99 — Company-level To-Pay charge
--
-- Direct request: a shipment booked with a To-Pay payment mode carries a flat extra
-- charge (50 by default), configured per company in the Shipment section of
-- company_settings_config, next to default_appointment_delivery_charge (V75).
-- Unlike that field this is not a prefill: the backend adds it itself on every To-Pay
-- booking and the booking form shows it read-only. GST applies to it. Set to 0 to turn
-- the charge off for a company.
--
-- shipment_charges gets the amount actually charged, frozen at booking time so a later
-- change to the setting never rewrites an old shipment's breakup.
-- =============================================================================

ALTER TABLE company_settings_config
    ADD COLUMN to_pay_charge DECIMAL(19,4) NOT NULL DEFAULT 50.0000
        AFTER default_appointment_delivery_charge;

ALTER TABLE shipment_charges
    ADD COLUMN to_pay_charge DECIMAL(19,4) NOT NULL DEFAULT 0
        AFTER door_delivery_charge;

-- Sum of ACTIVE `charges` module rows matched to a shipment's service type/weight/distance
-- at booking time (e.g. a "Hamali" KG-slab charge) — GST-inclusive, folded in alongside
-- freight/oda/insurance ahead of GST, unlike appointment_delivery_charge which stays
-- deliberately GST-free. See PricingEngine's ApplicableChargesCalculator.
ALTER TABLE shipment_charges
    ADD COLUMN applicable_charges DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER insurance_charge;

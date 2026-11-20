-- =============================================================================
-- V147: branches gain an ODA charge amount, credited to the delivery branch's wallet
-- (sub-type ODA) when a shipment with an ODA charge is delivered and its POD approved.
-- A fixed amount per shipment, set when the branch is created and editable later.
-- Default 0.00 = the branch earns nothing extra for ODA deliveries.
-- =============================================================================

ALTER TABLE branches
    ADD COLUMN oda_charge_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 AFTER delivery_commission_min_weight_kg;

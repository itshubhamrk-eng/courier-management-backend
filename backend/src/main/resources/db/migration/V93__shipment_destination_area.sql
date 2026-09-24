-- Persists which specific Area of a delivery pincode the operator picked at booking, when
-- that pincode maps to more than one (e.g. 413523 -> Kingaon, Osmanabad). Without this the
-- printed receipt re-derived the destination area from the pincode alone and always got
-- the pincode's single fixed "primary" area, not the one actually selected.
ALTER TABLE shipments
    ADD COLUMN destination_area_id BINARY(16) NULL AFTER delivery_pincode;

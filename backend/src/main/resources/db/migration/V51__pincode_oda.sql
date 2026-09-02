-- =============================================================================
-- V51 — Pincode ODA flag
--
-- master_pincodes gains oda_applicable: whether this pincode is an Out-of-Delivery-Area
-- location (extra handling / surcharge at booking), independent of `serviceable` — an ODA
-- pincode is still delivered to, just not on the standard network. Defaults false, so
-- every existing pincode keeps its current (non-ODA) meaning.
-- =============================================================================

ALTER TABLE master_pincodes
    ADD COLUMN oda_applicable BOOLEAN NOT NULL DEFAULT FALSE AFTER zone;

-- V113: shipments gain business_customer_id — the Business Customer a booking was made
-- for, when the operator used "Business Customer booking". It lets freight be priced from
-- that customer's pricing-zone rate card (customerpricing) instead of District Level
-- Freight, and lets the server re-derive that price on edit / Rate-KG recalculation without
-- trusting the client. Nullable and unreferenced: every existing shipment stays priced
-- exactly as before. No physical FK — same treatment as customer_addresses' geography ids.
ALTER TABLE shipments
    ADD COLUMN business_customer_id BINARY(16) NULL AFTER destination_area_id;

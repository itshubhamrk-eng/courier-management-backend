-- V133 — Business Customer: "rest of state" fallback zone
--
-- A fallback zone has no locations: it prices every destination that none of the customer's
-- other zones covers. At most one live fallback zone per customer (checked in the service,
-- soft delete makes a DB unique key unsafe, same as zone names).
ALTER TABLE customer_pricing_zones
    ADD COLUMN is_fallback BOOLEAN NOT NULL DEFAULT FALSE AFTER description;

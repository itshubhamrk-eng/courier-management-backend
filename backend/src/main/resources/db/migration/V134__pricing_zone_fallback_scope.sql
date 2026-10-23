-- V134 — split the V133 fallback flag into "rest of Maharashtra" and "out of Maharashtra"
--
-- fallback_scope NULL = ordinary zone with locations; REST_OF_STATE / OUT_OF_STATE = location-less
-- catch-all zone. At most one live zone per (customer, scope), checked in the service.
ALTER TABLE customer_pricing_zones
    ADD COLUMN fallback_scope VARCHAR(20) NULL AFTER description;
UPDATE customer_pricing_zones SET fallback_scope = 'REST_OF_STATE' WHERE is_fallback = TRUE;
ALTER TABLE customer_pricing_zones DROP COLUMN is_fallback;

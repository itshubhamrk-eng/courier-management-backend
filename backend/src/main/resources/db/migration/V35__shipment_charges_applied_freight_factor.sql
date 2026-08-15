-- =============================================================================
-- V35 — shipment_charges.applied_freight_factor
--
-- On direct request: booking's Freight Factor fallback (0.20.6/0.20.7) now surfaces the
-- matched grid cell's own factor and lets a desk raise it (never lower it) before the
-- freight/GST/net-amount are computed — see PricingEngineImpl.priceByDistanceAndWeight's
-- freightFactorOverride. This column persists whatever factor was actually applied, null
-- for every shipment that priced through the normal Route/Rate path (matched_route_id/
-- matched_rate_id both set instead).
-- =============================================================================

ALTER TABLE shipment_charges
    ADD COLUMN applied_freight_factor DECIMAL(19, 4) NULL AFTER matched_rate_id;

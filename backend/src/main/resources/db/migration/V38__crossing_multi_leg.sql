-- =============================================================================
-- V38 — Crossing: multiple hubs per shipment
--
-- V37 modelled one crossing branch per shipment (one `crossing_details` row). On direct
-- follow-up request: a shipment may cross through two or three hubs in sequence, not just
-- one — e.g. Pune -> Nashik (hub) -> Nagpur (hub) -> Raipur (delivery). `crossing_details`
-- becomes one row per hop instead of one row per shipment, ordered by `sequence_order`
-- (0-based). Existing rows are all leg 0 of a single-hop journey — the `DEFAULT 0` backfills
-- them correctly with no data migration needed.
--
-- The old `UNIQUE (company_id, shipment_id)` forced exactly one row per shipment; replaced
-- with `UNIQUE (company_id, shipment_id, sequence_order)` so a shipment may have several,
-- one per hop, but never two rows claiming the same position in its route.
-- =============================================================================

ALTER TABLE crossing_details
    ADD COLUMN sequence_order INT NOT NULL DEFAULT 0 AFTER branch_id;

ALTER TABLE crossing_details
    DROP INDEX uk_crossing_details_company_shipment,
    ADD UNIQUE KEY uk_crossing_details_company_shipment_seq (company_id, shipment_id, sequence_order);

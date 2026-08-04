-- =============================================================================
-- V15 — Route Management: transit hours + distance unit
--
-- Route already exists as one of the twelve Master Data lists (V11, master_routes) with
-- full CRUD, activate/deactivate, the branch-pair uniqueness rule and the ROUTE_MASTER_*
-- permissions. This migration only widens it to match the Route Management brief: transit
-- is expressed in whole days today, so a same-day lane that actually takes six hours has
-- nowhere to record that; distance carries an implicit unit that was never named on the
-- row itself.
--
-- No new table, no new permission codes: ROUTE_MASTER_VIEW/CREATE/UPDATE/DELETE/
-- ACTIVATE/DEACTIVATE already cover this list (V6, activate/deactivate added V11).
-- =============================================================================

ALTER TABLE master_routes
    ADD COLUMN transit_hours INT NOT NULL DEFAULT 0 AFTER transit_days,
    ADD COLUMN distance_unit VARCHAR(10) NOT NULL DEFAULT 'KM' AFTER distance_km;

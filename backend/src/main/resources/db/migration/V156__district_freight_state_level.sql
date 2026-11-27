-- =============================================================================
-- V156 — State-level District Level Freight
--
-- A rate row is keyed by EITHER a destination District (as before) OR a destination
-- State. Booking tries the district row first and falls back to the state row of the
-- district's state. Same six slabs + ODA for both levels.
--
-- district_id becomes nullable; state_id is new. Exactly one of them is set (CHECK).
-- MySQL treats NULLs as distinct in unique keys, so the existing district key and the
-- new state key coexist: a state row never collides on district, a district row never
-- collides on state. Existing rows are untouched (district set, state NULL).
-- =============================================================================

ALTER TABLE district_level_freight
    MODIFY COLUMN district_id BINARY(16) NULL,
    ADD COLUMN state_id BINARY(16) NULL AFTER district_id,
    ADD KEY idx_district_freight_state (company_id, state_id),
    ADD UNIQUE KEY uk_district_freight_state_combo (company_id, branch_id, state_id),
    ADD CONSTRAINT fk_district_freight_state FOREIGN KEY (state_id) REFERENCES master_states (id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT ck_district_freight_one_level CHECK ((district_id IS NULL) <> (state_id IS NULL));

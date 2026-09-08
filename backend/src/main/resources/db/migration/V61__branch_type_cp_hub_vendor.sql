-- =============================================================================
-- V61: BranchType replaced wholesale — direct user request. Old values
-- (HEAD_OFFICE, REGIONAL_OFFICE, BOOKING_BRANCH, DELIVERY_BRANCH,
-- BOOKING_DELIVERY_BRANCH) never carried a hard capability rule of their own (the
-- allow* flags already did that); new values are CP (channel partner), BRANCH, HUB,
-- VENDOR. Remap agreed with the user: HEAD_OFFICE/REGIONAL_OFFICE -> HUB (both were
-- office types overseeing/aggregating other branches), the three booking/delivery
-- variants -> BRANCH. No existing row becomes CP or VENDOR — those are new concepts
-- with no prior equivalent, set by hand going forward.
-- =============================================================================

UPDATE branches
SET branch_type = CASE branch_type
    WHEN 'HEAD_OFFICE' THEN 'HUB'
    WHEN 'REGIONAL_OFFICE' THEN 'HUB'
    WHEN 'BOOKING_BRANCH' THEN 'BRANCH'
    WHEN 'DELIVERY_BRANCH' THEN 'BRANCH'
    WHEN 'BOOKING_DELIVERY_BRANCH' THEN 'BRANCH'
    ELSE branch_type
END;

ALTER TABLE branches
    MODIFY COLUMN branch_type VARCHAR(30) NOT NULL DEFAULT 'BRANCH';

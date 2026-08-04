-- =============================================================================
-- V20 — Out Scan folds into Manifest Created, on direct request
--
-- V19 modelled Out Scan as its own state (MANIFEST_CREATED -> OUT_SCAN -> DISPATCHED),
-- with its own scan action and its own REST endpoint. After it shipped, the user asked
-- directly to collapse the two: "manifest created as outscan created" — adding a
-- shipment to a manifest already IS the out-scan milestone, no separate scan action
-- needed before Dispatch. MANIFEST_CREATED now transitions straight to DISPATCHED;
-- Dispatch's "at least one shipment" precondition reads MANIFEST_CREATED directly.
--
-- Real OUT_SCAN rows exist from live-verifying V19 before this change — folded back
-- into MANIFEST_CREATED, the same fold-back-on-rename pattern V19 itself used.
-- =============================================================================

UPDATE shipments SET status = 'MANIFEST_CREATED' WHERE status = 'OUT_SCAN';
UPDATE shipment_status_history SET status = 'MANIFEST_CREATED' WHERE status = 'OUT_SCAN';
UPDATE shipment_status_history SET previous_status = 'MANIFEST_CREATED' WHERE previous_status = 'OUT_SCAN';

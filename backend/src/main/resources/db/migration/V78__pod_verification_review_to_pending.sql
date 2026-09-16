-- =============================================================================
-- V78 — POD Auto Verification: REVIEW -> PENDING
--
-- Every delivery-app POD upload now lands PENDING regardless of AI score (the AI decision
-- of PASS/FAIL at upload time is removed — a human always makes that call via
-- POST /shipments/{id}/pod/review). REVIEW was the same "awaiting a human decision" state,
-- just previously reached only on a medium-confidence AI score; renaming it in place keeps
-- existing rows/history meaningful rather than losing them to a value the app no longer
-- writes.
-- =============================================================================

UPDATE pod_verification SET verification_status = 'PENDING' WHERE verification_status = 'REVIEW';

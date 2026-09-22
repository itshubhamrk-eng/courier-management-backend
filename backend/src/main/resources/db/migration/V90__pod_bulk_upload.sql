-- =============================================================================
-- V90 — Bulk POD Upload
--
-- Two columns on the existing pod_verification table (V48), not a new table: a bulk
-- upload produces the exact same row shape as a single verify() call, just auto-matched
-- to its shipment by reading the number off the photo instead of a human picking the
-- shipment first. See MEMORY/modules/pod-verification.md.
--
-- detected_shipment_number is a genuine content read off the image (real OCR/vision, not
-- a passthrough of a typed value) — separate from the existing detected_awb column, which
-- a non-OCR provider may echo from the claimed/known value. stamp_detected is likewise a
-- real content signal, distinct from the existing signature_detected column.
-- =============================================================================

ALTER TABLE pod_verification
    ADD COLUMN detected_shipment_number VARCHAR(100) NULL AFTER detected_awb,
    ADD COLUMN stamp_detected BOOLEAN NOT NULL DEFAULT FALSE AFTER signature_detected;

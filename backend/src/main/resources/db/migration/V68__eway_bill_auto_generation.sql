-- =============================================================================
-- V68 — E-Way Bill auto-generation (Part-A at booking, Part-B at Manifest dispatch)
--
-- Replaces V47's manual "type in a number you already got from the government portal,
-- we'll sanity-check the format" flow with real two-stage auto-generation through a
-- pluggable EwayBillProvider (see EwayBillProviderConfig — UnconfiguredEwayBillProvider
-- by default, a real GSP integration only when explicitly configured with credentials).
--
-- Status vocabulary changes from PENDING/UPLOADED/VALIDATED/INVALID to the auto-flow's
-- own stages: PART_A_PENDING/PART_A_GENERATED/PART_B_PENDING/GENERATED/FAILED.
-- NOT_REQUIRED/REQUIRED/EXPIRED/CANCELLED are unchanged. Existing rows are remapped:
--   PENDING, UPLOADED -> PART_A_PENDING (never made it to a confirmed state)
--   VALIDATED         -> GENERATED       (already had a confirmed, usable number)
--   INVALID           -> FAILED
--
-- New columns snapshot everything a Part-A/Part-B provider request needs (consignor/
-- consignee identity, product description, transport mode) directly on the row, plus
-- provider bookkeeping (name/reference/timestamps/last error/retry count) — see
-- EwayBill's own class doc for why this is a snapshot, not a live join back to the
-- shipment: an E-Way Bill is a legal document as issued, and it lets `retry` rebuild
-- the exact same request without this module depending back on modules.shipment
-- (which already depends on this module).
-- =============================================================================

ALTER TABLE eway_bill
    ADD COLUMN consignor_name      VARCHAR(150)  NULL AFTER document_date,
    ADD COLUMN consignor_address   VARCHAR(500)  NULL AFTER consignor_name,
    ADD COLUMN consignor_pincode   VARCHAR(10)   NULL AFTER consignor_address,
    ADD COLUMN consignor_gstin     VARCHAR(15)   NULL AFTER consignor_pincode,
    ADD COLUMN consignee_name      VARCHAR(150)  NULL AFTER consignor_gstin,
    ADD COLUMN consignee_address   VARCHAR(500)  NULL AFTER consignee_name,
    ADD COLUMN consignee_pincode   VARCHAR(10)   NULL AFTER consignee_address,
    ADD COLUMN consignee_gstin     VARCHAR(15)   NULL AFTER consignee_pincode,
    ADD COLUMN product_description VARCHAR(500)  NULL AFTER consignee_gstin,
    ADD COLUMN transport_mode      VARCHAR(20)   NULL AFTER vehicle_number,
    ADD COLUMN provider_name       VARCHAR(30)   NULL AFTER status,
    ADD COLUMN provider_reference  VARCHAR(100)  NULL AFTER provider_name,
    ADD COLUMN part_a_generated_at TIMESTAMP(6)  NULL AFTER provider_reference,
    ADD COLUMN part_b_generated_at TIMESTAMP(6)  NULL AFTER part_a_generated_at,
    -- Sanitized failure reason only — never a stack trace, never a credential/token.
    ADD COLUMN last_error          VARCHAR(1000) NULL AFTER part_b_generated_at,
    ADD COLUMN retry_count         INT NOT NULL DEFAULT 0 AFTER last_error;

UPDATE eway_bill
SET status = CASE status
    WHEN 'PENDING' THEN 'PART_A_PENDING'
    WHEN 'UPLOADED' THEN 'PART_A_PENDING'
    WHEN 'VALIDATED' THEN 'GENERATED'
    WHEN 'INVALID' THEN 'FAILED'
    ELSE status
END
WHERE status IN ('PENDING', 'UPLOADED', 'VALIDATED', 'INVALID');

ALTER TABLE eway_bill
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PART_A_PENDING'
        COMMENT 'NOT_REQUIRED | REQUIRED | PART_A_PENDING | PART_A_GENERATED | PART_B_PENDING | GENERATED | FAILED | EXPIRED | CANCELLED';

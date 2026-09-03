-- =============================================================================
-- V55: GST and PAN numbers on branches.
--
-- Branch-level, not company-level: a company can operate branches under different
-- GST registrations (state-wise GSTIN is normal for courier networks), so this is
-- deliberately separate from any company-level GST/PAN. Both optional — plenty of
-- branches (especially newly onboarded ones) don't have paperwork on file yet.
-- =============================================================================

ALTER TABLE branches
    ADD COLUMN gst_number VARCHAR(15) NULL AFTER remarks,
    ADD COLUMN pan_number VARCHAR(10) NULL AFTER gst_number;

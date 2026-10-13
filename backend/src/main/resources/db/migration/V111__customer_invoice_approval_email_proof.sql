-- =============================================================================
-- V111 — Business customer invoices: approval, e-mail to the customer, payment proof
--
-- A newly generated invoice (auto or manual) now waits for an admin's approval; approving it
-- e-mails the invoice to the business customer's e-mail address. Existing invoices (issued before
-- this migration) are APPROVED and NOT_SENT — nothing is e-mailed retroactively.
-- A payment (receipt) may carry a proof file (bank slip / UPI screenshot / cheque photo).
-- =============================================================================

ALTER TABLE customer_invoices
    -- PENDING_APPROVAL | APPROVED
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED' AFTER status,
    ADD COLUMN approved_at     TIMESTAMP(6) NULL AFTER approval_status,
    ADD COLUMN approved_by     BINARY(16)   NULL AFTER approved_at,
    -- NOT_SENT | SENT | FAILED | NO_EMAIL (customer has no e-mail address)
    ADD COLUMN email_status    VARCHAR(20)  NOT NULL DEFAULT 'NOT_SENT' AFTER approved_by,
    ADD COLUMN email_to        VARCHAR(255) NULL AFTER email_status,
    ADD COLUMN email_sent_at   TIMESTAMP(6) NULL AFTER email_to,
    ADD COLUMN email_error     VARCHAR(255) NULL AFTER email_sent_at,
    ADD KEY idx_cinv_company_approval (company_id, approval_status);

ALTER TABLE customer_invoice_payments
    ADD COLUMN proof_url  VARCHAR(500) NULL AFTER remarks,
    ADD COLUMN proof_name VARCHAR(255) NULL AFTER proof_url;

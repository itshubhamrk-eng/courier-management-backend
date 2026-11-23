-- A customer can dispute one billed shipment on an invoice. One dispute per line at a time
-- (OPEN, then RESOLVED); raising again after it is resolved starts a new one. History is in the audit log.
ALTER TABLE customer_invoice_lines
    ADD COLUMN dispute_status      VARCHAR(10)  NULL,
    ADD COLUMN dispute_reason      VARCHAR(500) NULL,
    ADD COLUMN dispute_raised_at   TIMESTAMP(6) NULL,
    ADD COLUMN dispute_raised_by   BINARY(16)   NULL,
    ADD COLUMN dispute_resolution  VARCHAR(500) NULL,
    ADD COLUMN dispute_resolved_at TIMESTAMP(6) NULL,
    ADD COLUMN dispute_resolved_by BINARY(16)   NULL;

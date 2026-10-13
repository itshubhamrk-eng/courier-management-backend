-- =============================================================================
-- V105 — Business Customer: extend customers / customer_addresses
--
-- Extends the existing customer master in place (no parallel table): contact person,
-- default payment mode, billing frequency and a billing address on `customers`; a
-- PICKUP / DELIVERY / BOTH usage on `customer_addresses`.
--
-- Existing rows: payment mode PAID (the project-wide default), billing frequency MONTHLY,
-- address usage BOTH — behaviour of every booking already made is unchanged.
-- =============================================================================

ALTER TABLE customers
    ADD COLUMN contact_person          VARCHAR(150) NULL AFTER last_name,
    -- CREDIT | PAID | TOPAY
    ADD COLUMN default_payment_mode    VARCHAR(10)  NOT NULL DEFAULT 'PAID' AFTER pan_number,
    -- WEEKLY | FIFTEEN_DAYS | MONTHLY
    ADD COLUMN billing_frequency       VARCHAR(15)  NOT NULL DEFAULT 'MONTHLY' AFTER default_payment_mode,
    ADD COLUMN billing_address_line1   VARCHAR(255) NULL AFTER billing_frequency,
    ADD COLUMN billing_address_line2   VARCHAR(255) NULL AFTER billing_address_line1,
    ADD COLUMN billing_city            VARCHAR(100) NULL AFTER billing_address_line2,
    ADD COLUMN billing_state           VARCHAR(100) NULL AFTER billing_city,
    ADD COLUMN billing_pincode         VARCHAR(10)  NULL AFTER billing_state;

ALTER TABLE customer_addresses
    -- PICKUP | DELIVERY | BOTH
    ADD COLUMN address_usage VARCHAR(10) NOT NULL DEFAULT 'BOTH' AFTER address_type;

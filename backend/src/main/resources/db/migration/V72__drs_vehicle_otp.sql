-- =============================================================================
-- V72: DRS ("Out For Delivery") gains the same trip-cost/OTP shape THC dispatch (V70)
-- already has. delivery_assignment has no separate DRS/batch row (see V31's comment) so
-- vehicle_id/fuel_cost/delivery_charge are stamped on every row one Generate DRS call
-- touches, same as drs_number already is. delivery_dispatch_otp is standalone (not a
-- delivery_assignment column) because the OTP challenge/response happens before those
-- rows exist — one row per company+delivery user, re-issued on every "Send OTP" click.
-- =============================================================================

ALTER TABLE delivery_assignment
    ADD COLUMN vehicle_id BINARY(16) NULL,
    ADD COLUMN fuel_cost DECIMAL(12,2) NULL,
    ADD COLUMN delivery_charge DECIMAL(12,2) NULL;

CREATE TABLE delivery_dispatch_otp (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    delivery_user_id BINARY(16) NOT NULL,
    otp_hash         VARCHAR(100) NULL,
    expires_at       TIMESTAMP(6) NULL,
    attempts         INT NOT NULL DEFAULT 0,
    verified_at      TIMESTAMP(6) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_dispatch_otp_company_user (company_id, delivery_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One re-issuable OTP challenge per company+delivery user, ahead of Generate DRS';

-- =============================================================================
-- V70: THC dispatch now requires the assigned driver to verify an OTP (sent to their
-- mobile via the existing Communication Center SMS channel) before the manifest can move
-- to DISPATCHED. dispatch_otp_hash is a BCrypt hash, never the raw code; the raw code
-- exists only in the SMS itself and briefly in memory while it is generated/verified.
-- =============================================================================

ALTER TABLE manifests
    ADD COLUMN dispatch_otp_hash VARCHAR(100) NULL,
    ADD COLUMN dispatch_otp_driver_id BINARY(16) NULL,
    ADD COLUMN dispatch_otp_expires_at TIMESTAMP NULL,
    ADD COLUMN dispatch_otp_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN dispatch_otp_verified_at TIMESTAMP NULL;

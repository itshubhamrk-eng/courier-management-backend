-- =============================================================================
-- V30 — Freight Factor
--
-- Company-level freight pricing grid, independent of Rate Master/Pricing Engine:
-- one row is a (distance range x weight range) cell carrying a multiplier ("factor").
-- Freight for a shipment = matched factor x weight. Distance is not stored here —
-- it is resolved on demand via the address_distance module (V29) at calculate time.
--
-- Company-owned: company_id NOT NULL. No code/name column — nothing external
-- references a row by code (unlike rate_master.rate_code, which shipments quote).
-- =============================================================================

CREATE TABLE freight_factor (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    -- Half-open [from_km, to_km), mirrors rate_master's weight-slab convention.
    from_km DECIMAL(10, 3) NOT NULL,
    to_km   DECIMAL(10, 3) NOT NULL,

    -- Half-open [from_weight, to_weight). Plain kg, no unit column — same
    -- convention pricing/distance already use for weight figures.
    from_weight DECIMAL(12, 3) NOT NULL,
    to_weight   DECIMAL(12, 3) NOT NULL,

    factor DECIMAL(19, 4) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_freight_factor_status (company_id, status),
    KEY idx_freight_factor_range (company_id, status, from_km, from_weight),

    CONSTRAINT ck_freight_factor_km CHECK (to_km > from_km AND from_km >= 0),
    CONSTRAINT ck_freight_factor_weight CHECK (to_weight > from_weight AND from_weight >= 0),
    CONSTRAINT ck_freight_factor_value CHECK (factor > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Company freight pricing grid: distance range x weight range -> factor; freight = factor * weight';
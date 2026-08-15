-- =============================================================================
-- V29 — Address-to-Address Distance
--
-- First table of the distance/freight-factor pricing work: one row is the
-- resolved road distance + travel time between two addresses of the SAME kind
-- (both branches, or both customers — address_type + from_id/to_id together
-- decide which table from_id/to_id point into; no FK, same "no cross-entity FK
-- until the data is stable" reasoning V9's own header gives for branches.manager_id).
--
-- Populated by a routing lookup (OSRM), not written by hand; this migration only
-- creates the table. Company-owned: company_id NOT NULL, leading the unique key.
-- =============================================================================

CREATE TABLE address_distance (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    -- BRANCH | CUSTOMER — which table from_id/to_id are rows of.
    address_type VARCHAR(20) NOT NULL,
    from_id      BINARY(16)  NOT NULL,
    to_id        BINARY(16)  NOT NULL,

    distance_km    DECIMAL(10, 3) NULL,
    distance_meter DECIMAL(12, 2) NULL,
    -- Travel time as returned by the routing lookup, in minutes.
    required_time_minutes DECIMAL(10, 2) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- One resolved distance per ordered pair, per company, per address kind.
    -- Ordered (not symmetric) on purpose: a routed distance A->B need not equal B->A.
    UNIQUE KEY uk_address_distance_pair (company_id, address_type, from_id, to_id),
    KEY idx_address_distance_from (company_id, address_type, from_id),
    KEY idx_address_distance_to (company_id, address_type, to_id),

    CONSTRAINT ck_address_distance_km CHECK (distance_km IS NULL OR distance_km >= 0),
    CONSTRAINT ck_address_distance_meter CHECK (distance_meter IS NULL OR distance_meter >= 0),
    CONSTRAINT ck_address_distance_time CHECK (required_time_minutes IS NULL OR required_time_minutes >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Resolved road distance + travel time between two addresses of the same kind';

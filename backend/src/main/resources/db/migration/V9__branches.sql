-- =============================================================================
-- V9 — Branch Management (Phase 4, Organization Structure)
--
-- A branch is a physical booking/delivery office of a company. Company-owned:
-- company_id NOT NULL, leading every composite index and unique key.
--
-- Forward-only. V2 auth, V3 subscription, V4 company, V5 roles, V6 permissions,
-- V7 users, V8 settings, V9 this.
--
-- NOT DONE HERE, and deliberately, following the project's "no cross-entity FK until
-- the data is stable" pattern:
--   * users.branch_id -> branches.id — the dev database already holds user rows whose
--     branch_id is a random test value, so the constraint would fail on boot. The
--     service validates placements instead. Add the FK once those rows are reconciled.
--   * branches.manager_id -> users.id — the manager is validated in the service to be a
--     user of the same company; the FK is left off for the same reason the
--     users.company_id FK still is.
-- =============================================================================

CREATE TABLE branches (
    id          BINARY(16)   NOT NULL,
    company_id   BINARY(16)   NOT NULL,

    branch_code VARCHAR(50)  NOT NULL,
    branch_name VARCHAR(150) NOT NULL,
    -- HEAD_OFFICE | REGIONAL_OFFICE | BOOKING_BRANCH | DELIVERY_BRANCH |
    -- BOOKING_DELIVERY_BRANCH
    branch_type VARCHAR(30)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    email            VARCHAR(255) NULL,
    mobile           VARCHAR(20)  NULL,
    alternate_mobile VARCHAR(20)  NULL,
    manager_id       BINARY(16)   NULL,

    address_line1 VARCHAR(255) NULL,
    address_line2 VARCHAR(255) NULL,
    country       VARCHAR(100) NULL,
    state         VARCHAR(100) NULL,
    city          VARCHAR(100) NULL,
    district      VARCHAR(100) NULL,
    taluka        VARCHAR(100) NULL,
    postal_code   VARCHAR(20)  NULL,
    -- WGS84, ~0.1 m precision.
    latitude      DECIMAL(9, 6) NULL,
    longitude     DECIMAL(9, 6) NULL,

    opening_time  TIME        NULL,
    closing_time  TIME        NULL,
    -- Uppercase CSV of MON..SUN.
    working_days  VARCHAR(40) NULL,

    allow_booking         BOOLEAN NOT NULL DEFAULT TRUE,
    allow_delivery        BOOLEAN NOT NULL DEFAULT TRUE,
    allow_pickup          BOOLEAN NOT NULL DEFAULT TRUE,
    allow_manifest        BOOLEAN NOT NULL DEFAULT TRUE,
    allow_cash_collection BOOLEAN NOT NULL DEFAULT TRUE,
    allow_wallet          BOOLEAN NOT NULL DEFAULT FALSE,

    remarks       VARCHAR(500) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- Per company, never global: two couriers may both have a "MAIN" branch. Not
    -- scoped by `deleted`, so a soft-deleted branch keeps its code and name reserved.
    UNIQUE KEY uk_branches_company_code (company_id, branch_code),
    UNIQUE KEY uk_branches_company_name (company_id, branch_name),

    KEY idx_branches_company_status (company_id, status),
    KEY idx_branches_company_type (company_id, branch_type),
    KEY idx_branches_manager (company_id, manager_id),
    -- The serviceability lookup the shipment module will run.
    KEY idx_branches_pincode (company_id, postal_code),

    CONSTRAINT ck_branches_latitude CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    CONSTRAINT ck_branches_longitude CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Company branches (booking / delivery offices)';

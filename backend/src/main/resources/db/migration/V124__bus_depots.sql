-- =============================================================================
-- V124 — MSRTC Bus Depot / Station master
--
-- A company-owned master of the bus depots and bus stations MSRTC cargo moves through.
-- A depot is optionally linked to ONE branch (any branch type, so a Pickup-Delivery
-- Partner "vendor" branch is assignable too) that handles it. branch_id is a real FK:
-- both tables are this company's, and a branch with depots cannot be hard-deleted.
-- Code and name are unique among live rows (checked in the service — soft delete must
-- not reserve them).
-- =============================================================================
CREATE TABLE bus_depots (
    id            BINARY(16)   NOT NULL,
    company_id    BINARY(16)   NOT NULL,
    depot_code    VARCHAR(30)  NOT NULL,
    depot_name    VARCHAR(150) NOT NULL,
    -- BUS_DEPOT | BUS_STATION
    depot_type    VARCHAR(20)  NOT NULL DEFAULT 'BUS_DEPOT',
    branch_id     BINARY(16)   NULL,
    address       VARCHAR(255) NULL,
    city          VARCHAR(100) NULL,
    district      VARCHAR(100) NULL,
    state         VARCHAR(100) NULL,
    pincode       VARCHAR(10)  NULL,
    contact_person VARCHAR(150) NULL,
    contact_mobile VARCHAR(20)  NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_bus_depots_company_status (company_id, status),
    KEY idx_bus_depots_branch (company_id, branch_id),
    CONSTRAINT fk_bus_depots_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'MSRTC bus depots / stations, optionally assigned to a branch or vendor';

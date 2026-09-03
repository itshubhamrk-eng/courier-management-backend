-- =============================================================================
-- V54 — District Level Freight
--
-- Rate setup only: From Station (a company's own Branch) + Destination District
-- (the global District master) + a fixed six-slab per-KG rate table, plus a
-- per-row ODA charge. Company-owned for real, same reasoning
-- branch_pincode_mapping (V53) already documents — Branch is a genuine
-- per-company entity, District is still the global master
-- (GlobalMasters.PLATFORM_COMPANY_ID) crossed into only to validate/display it.
--
-- Deliberately NOT wired into Shipment Booking, Commission or the Pricing Engine
-- in this migration — rate setup only. A shipment booking a later task wires up
-- would use the COMPLETE weight against exactly one of the six slabs below (no
-- progressive/tiered split across slabs); that rule lives in the entity as a
-- pure lookup method, not called from anywhere yet.
-- =============================================================================

CREATE TABLE district_level_freight (
    id          BINARY(16) NOT NULL,
    company_id  BINARY(16) NOT NULL,

    branch_id   BINARY(16) NOT NULL,
    district_id BINARY(16) NOT NULL,

    -- Per-KG rate for the COMPLETE weight once it falls in this slab.
    rate_1_to_15      DECIMAL(19,4) NOT NULL,
    rate_16_to_50     DECIMAL(19,4) NOT NULL,
    rate_51_to_100    DECIMAL(19,4) NOT NULL,
    rate_101_to_1000  DECIMAL(19,4) NOT NULL,
    rate_1001_to_1500 DECIMAL(19,4) NOT NULL,
    rate_1501_to_2000 DECIMAL(19,4) NOT NULL,

    oda_applicable BOOLEAN NOT NULL DEFAULT TRUE,
    oda_charge     DECIMAL(19,4) NOT NULL DEFAULT 250.0000,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_district_freight_combo (company_id, branch_id, district_id),
    KEY idx_district_freight_branch (company_id, branch_id),
    KEY idx_district_freight_district (company_id, district_id),
    KEY idx_district_freight_status (company_id, status),

    CONSTRAINT fk_district_freight_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_district_freight_district FOREIGN KEY (district_id) REFERENCES master_districts (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'District Level Freight rate setup — From Station + District + six weight slabs + ODA';

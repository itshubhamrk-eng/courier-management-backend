-- =============================================================================
-- V63 — Charge & Charge Settings (configuration only)
--
-- A new, standalone master/configuration module, independent of Rate Master, Freight
-- Factor, District Level Freight and Commission. A Charge is a named configuration
-- scoped to one Service Type; a Charge Setting is one FACTOR (flat/percentage) or SLAB
-- (KG/KM/BOTH banded) row under it. COMPANY_ADMIN-only, both reads and writes.
--
-- Not wired into Shipment Booking, freight, commission or wallet calculation yet — that
-- integration is separate, later work. Nothing existing changes shape.
-- =============================================================================

CREATE TABLE charges (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    -- No FK: a different module's table (com.courier.modules.master), validated in
    -- ChargeServiceImpl through ServiceTypeService, the same cross-module treatment
    -- rate_master.service_type_id already gets.
    service_type_id BINARY(16) NOT NULL,

    charge_name VARCHAR(150) NOT NULL,

    -- ACTIVE | INACTIVE
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- Does not mention `deleted`: a deleted charge's name stays reserved, the same
    -- treatment every other master/code identity in this project gets.
    UNIQUE KEY uk_charges_company_service_name (company_id, service_type_id, charge_name),
    KEY idx_charges_status (company_id, status),
    KEY idx_charges_service_type (company_id, service_type_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Charge configuration, scoped to one Service Type per company';

CREATE TABLE charge_settings (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    -- Real, same-module FK (unlike charges.service_type_id above): both tables belong to
    -- this module, so a physical constraint is the honest choice, the same reasoning
    -- master_states.country_id (V11) already documents. RESTRICT: a charge with live
    -- settings cannot be deleted (enforced first, in ChargeServiceImpl.delete, so the
    -- caller sees a clear 422 rather than a database error).
    charge_id BINARY(16) NOT NULL,

    -- FACTOR | SLAB
    charge_type VARCHAR(20) NOT NULL,
    -- BOTH | KG | KM — required when charge_type = SLAB, null for FACTOR
    charge_slab_type VARCHAR(10) NULL,

    from_km DECIMAL(12,3) NULL,
    to_km   DECIMAL(12,3) NULL,
    from_kg DECIMAL(12,3) NULL,
    to_kg   DECIMAL(12,3) NULL,

    charge_value      DECIMAL(19,4) NOT NULL,
    -- AMOUNT | PERCENTAGE
    charge_value_type VARCHAR(20) NOT NULL,

    -- AMOUNT | PERCENTAGE. Configuration only — no calculation engine reads this yet.
    commission_type  VARCHAR(20) NOT NULL,
    commission_value DECIMAL(19,4) NOT NULL,

    -- ACTIVE | INACTIVE
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_charge_settings_charge (company_id, charge_id, status),
    KEY idx_charge_settings_company (company_id),

    CONSTRAINT fk_charge_settings_charge FOREIGN KEY (charge_id) REFERENCES charges (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One FACTOR or SLAB row under a Charge';

-- Permission catalogue: new CHARGE module (112, between RATE_MASTER 110 and ROUTE_MASTER
-- 120), 9 rights (CREATE/READ/UPDATE/DELETE/SEARCH/IMPORT/EXPORT/ACTIVATE/DEACTIVATE) —
-- the same MASTER shape ROUTE_MASTER already has. Generated from DefaultPermissionCatalog
-- exactly as V6/V11/V12/V13/V16/V17/V47 were; catalogue total moves 231 -> 240,
-- DefaultPermissionCatalogTest asserts it. COMPANY_ADMIN needs no explicit
-- role_permissions row — its set derives from the whole catalogue, the same note
-- V16/V47 already give; no other role is granted CHARGE rights, since this module is
-- COMPANY_ADMIN-only by design (not yet consumed by any booking-desk workflow).
INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, NULL, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'CHARGE_CREATE' AS code, 'Create Charge' AS name, 'CHARGE' AS module, 'charges' AS resource, 'CREATE' AS action, 113 AS display_order
  UNION ALL SELECT 'CHARGE_READ', 'View Charge', 'CHARGE', 'charges', 'READ', 114
  UNION ALL SELECT 'CHARGE_UPDATE', 'Update Charge', 'CHARGE', 'charges', 'UPDATE', 115
  UNION ALL SELECT 'CHARGE_DELETE', 'Delete Charge', 'CHARGE', 'charges', 'DELETE', 116
  UNION ALL SELECT 'CHARGE_SEARCH', 'Search Charges', 'CHARGE', 'charges', 'SEARCH', 117
  UNION ALL SELECT 'CHARGE_EXPORT', 'Export Charges', 'CHARGE', 'charges', 'EXPORT', 118
  UNION ALL SELECT 'CHARGE_IMPORT', 'Import Charges', 'CHARGE', 'charges', 'IMPORT', 119
  UNION ALL SELECT 'CHARGE_ACTIVATE', 'Activate Charge', 'CHARGE', 'charges', 'ACTIVATE', 126
  UNION ALL SELECT 'CHARGE_DEACTIVATE', 'Deactivate Charge', 'CHARGE', 'charges', 'DEACTIVATE', 127
) AS d;

-- =============================================================================
-- V16 — Rate Master
--
-- Company-owned rate cards. A rate belongs to a Route (master_routes, V11) and prices one
-- weight slab for one combination of Route + Service Type + Package Type + Payment Mode
-- (all master.* catalogues, V11). Shipment Booking will consume this through
-- POST /api/v1/rates/calculate once it exists — see MEMORY/modules/rate-master.md.
--
-- No physical FK to route_id / service_type_id / package_type_id / payment_mode_id: they
-- are a different module's tables, validated in the service layer through Master's own
-- application service interfaces (RouteService / ServiceTypeService / PackageTypeService /
-- PaymentModeService) — the same cross-feature treatment customer_addresses' geography ids
-- and branches.manager_id already get.
--
-- Three permission rows are new: RATE_MASTER_ACTIVATE / RATE_MASTER_DEACTIVATE (the module
-- had CRUD+SEARCH+IMPORT+EXPORT+APPROVE from V6 but no lifecycle pair) and
-- RATE_MASTER_CALCULATE (a new PermissionAction — pricing a shipment is read-only but not
-- the same right as reading the rate card itself, so a booking desk can hold one without
-- the other). Generated from DefaultPermissionCatalog exactly as V6/V11/V12/V13 were;
-- catalogue total moves 219 -> 222, DefaultPermissionCatalogTest asserts it. Existing
-- companies keep the roles they already have; new companies pick up the three rights
-- automatically because DefaultRoleCatalog derives COMPANY_ADMIN's set from the catalogue,
-- and FINANCE_USER / BRANCH_MANAGER / BOOKING_OPERATOR were extended explicitly.
-- =============================================================================

CREATE TABLE rate_master (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    rate_code VARCHAR(50)  NOT NULL,
    rate_name VARCHAR(150) NOT NULL,

    route_id         BINARY(16) NOT NULL,
    service_type_id  BINARY(16) NOT NULL,
    package_type_id  BINARY(16) NOT NULL,
    payment_mode_id  BINARY(16) NOT NULL,

    -- The weight slab this rate prices, half-open [minimum_weight, maximum_weight) —
    -- same convention as master_weight_slabs.
    minimum_weight DECIMAL(12, 3) NOT NULL,
    maximum_weight DECIMAL(12, 3) NOT NULL,
    weight_unit    VARCHAR(10)    NOT NULL DEFAULT 'KG',

    base_rate              DECIMAL(19, 4) NOT NULL,
    -- Overage beyond maximum_weight: additional_weight_rate charged per
    -- additional_weight increment of extra weight.
    additional_weight      DECIMAL(12, 3) NOT NULL,
    additional_weight_rate DECIMAL(19, 4) NOT NULL,
    minimum_charge         DECIMAL(19, 4) NOT NULL DEFAULT 0,

    fuel_surcharge    DECIMAL(19, 4) NOT NULL DEFAULT 0,
    handling_charge   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    oda_charge        DECIMAL(19, 4) NOT NULL DEFAULT 0,
    insurance_charge  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    gst_percentage    DECIMAL(5, 2)  NOT NULL DEFAULT 0,

    effective_from DATE NOT NULL,
    -- NULL means open-ended.
    effective_to   DATE NULL,

    -- ACTIVE | INACTIVE
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- Never scoped by `deleted` — a shipment may end up quoting the code, so it stays
    -- reserved even after a soft delete, the same reasoning customers.customer_code and
    -- every master code already follow.
    UNIQUE KEY uk_rate_master_company_code (company_id, rate_code),

    -- The hot path: both the overlap check and the calculator look up every row for one
    -- Route + Service Type + Package Type + Payment Mode combination.
    KEY idx_rate_master_combo (company_id, route_id, service_type_id, package_type_id,
                                payment_mode_id, status),
    KEY idx_rate_master_status (company_id, status),
    KEY idx_rate_master_effective (company_id, effective_from, effective_to),

    CONSTRAINT ck_rate_master_weight_range CHECK (maximum_weight > minimum_weight),
    CONSTRAINT ck_rate_master_min_weight CHECK (minimum_weight >= 0),
    CONSTRAINT ck_rate_master_additional_weight CHECK (additional_weight > 0),
    CONSTRAINT ck_rate_master_additional_weight_rate CHECK (additional_weight_rate >= 0),
    CONSTRAINT ck_rate_master_base_rate CHECK (base_rate >= 0),
    CONSTRAINT ck_rate_master_minimum_charge CHECK (minimum_charge >= 0),
    CONSTRAINT ck_rate_master_fuel_surcharge CHECK (fuel_surcharge >= 0),
    CONSTRAINT ck_rate_master_handling_charge CHECK (handling_charge >= 0),
    CONSTRAINT ck_rate_master_oda_charge CHECK (oda_charge >= 0),
    CONSTRAINT ck_rate_master_insurance_charge CHECK (insurance_charge >= 0),
    CONSTRAINT ck_rate_master_gst_percentage CHECK (gst_percentage >= 0 AND gst_percentage <= 100),
    CONSTRAINT ck_rate_master_effective_range CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Rate Master: company rate cards, one weight slab per Route + Service Type + Package Type + Payment Mode';

INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, d.feature_flag, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'RATE_MASTER_ACTIVATE' AS code, 'Activate Rate Master' AS name, 'RATE_MASTER' AS module, 'rate-master' AS resource, 'ACTIVATE' AS action, 124 AS display_order, NULL AS feature_flag
  UNION ALL SELECT 'RATE_MASTER_DEACTIVATE', 'Deactivate Rate Master', 'RATE_MASTER', 'rate-master', 'DEACTIVATE', 125, NULL
  UNION ALL SELECT 'RATE_MASTER_CALCULATE', 'Calculate Rate', 'RATE_MASTER', 'rate-master', 'CALCULATE', 132, NULL
) AS d;

-- FINANCE_USER already held RATE_MASTER_CREATE/READ/UPDATE/DELETE (V6) — extend it with
-- the lifecycle and the calculator, same reasoning DefaultRoleCatalog documents.
-- BRANCH_MANAGER and BOOKING_OPERATOR already held RATE_MASTER_READ — extend both with
-- CALCULATE, since pricing a shipment happens at the counter, not only in the back office.
-- COMPANY_ADMIN needs no row here: its permission set is derived from the whole catalogue
-- (DefaultRoleCatalog.ALL_PERMISSION_CODES) and already covers every code inserted above.
INSERT INTO role_permissions (id, company_id, role_id, permission_id, permission_code,
                              created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')),
       r.company_id,
       r.id,
       p.id,
       p.permission_code,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM company_roles r
         JOIN (
             SELECT 'FINANCE_USER' AS role_code, 'RATE_MASTER_ACTIVATE' AS permission_code
             UNION ALL SELECT 'FINANCE_USER', 'RATE_MASTER_DEACTIVATE'
             UNION ALL SELECT 'FINANCE_USER', 'RATE_MASTER_CALCULATE'
             UNION ALL SELECT 'BRANCH_MANAGER', 'RATE_MASTER_CALCULATE'
             UNION ALL SELECT 'BOOKING_OPERATOR', 'RATE_MASTER_CALCULATE'
         ) grant_map ON grant_map.role_code = r.role_code
         JOIN permissions p ON p.permission_code = grant_map.permission_code
WHERE r.deleted = FALSE
  AND EXISTS (SELECT 1 FROM role_permissions existing
              WHERE existing.role_id = r.id AND existing.permission_code = 'RATE_MASTER_READ'
                AND existing.deleted = FALSE)
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.role_id = r.id AND x.permission_code = grant_map.permission_code
                    AND x.deleted = FALSE);

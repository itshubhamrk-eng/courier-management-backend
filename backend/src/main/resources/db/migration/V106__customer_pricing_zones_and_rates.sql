-- =============================================================================
-- V106 — Business Customer: pricing zones, zone locations, zone rates + slabs
--
-- A pricing zone belongs to ONE customer (customer_id FK, real FK: same module owns both
-- sides' lifecycle from here on, like customer_addresses). A zone groups districts and/or
-- cities. A zone has at most one live rate card: header charges + per-kg weight slabs.
--
-- district_id / city_id reference the global geography masters (another module's tables):
-- validated in the service, no physical FK — same treatment customer_addresses gives them.
-- Zone name uniqueness per customer and location uniqueness are enforced in the service
-- over NON-deleted rows (soft delete would otherwise reserve a name/location forever).
-- =============================================================================

CREATE TABLE customer_pricing_zones (
    id          BINARY(16)   NOT NULL,
    company_id  BINARY(16)   NOT NULL,
    customer_id BINARY(16)   NOT NULL,
    zone_name   VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_cpz_company_customer (company_id, customer_id),
    KEY idx_cpz_company_status (company_id, status),
    CONSTRAINT fk_cpz_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Pricing zones: named group of districts/cities for one business customer';

CREATE TABLE customer_zone_locations (
    id          BINARY(16) NOT NULL,
    company_id  BINARY(16) NOT NULL,
    zone_id     BINARY(16) NOT NULL,
    -- denormalised from the zone so "same location twice for one customer" is one query
    customer_id BINARY(16) NOT NULL,
    district_id BINARY(16) NOT NULL,
    -- NULL = the whole district
    city_id     BINARY(16) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_czl_company_zone (company_id, zone_id),
    KEY idx_czl_company_customer_district (company_id, customer_id, district_id),
    CONSTRAINT fk_czl_zone FOREIGN KEY (zone_id) REFERENCES customer_pricing_zones (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Districts / cities that belong to a customer pricing zone';

CREATE TABLE customer_zone_rates (
    id          BINARY(16) NOT NULL,
    company_id  BINARY(16) NOT NULL,
    zone_id     BINARY(16) NOT NULL,
    customer_id BINARY(16) NOT NULL,

    min_chargeable_weight DECIMAL(12, 3) NOT NULL DEFAULT 0,
    docket_charge   DECIMAL(12, 2) NOT NULL DEFAULT 0,
    mathadi_charge  DECIMAL(12, 2) NOT NULL DEFAULT 0,
    pack_charge     DECIMAL(12, 2) NOT NULL DEFAULT 0,
    oda_charge      DECIMAL(12, 2) NOT NULL DEFAULT 0,
    fuel_surcharge_percent DECIMAL(5, 2) NOT NULL DEFAULT 0,
    fov_percent     DECIMAL(5, 2) NOT NULL DEFAULT 0,

    effective_from DATE NULL,
    effective_to   DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_czr_company_zone (company_id, zone_id),
    KEY idx_czr_company_customer (company_id, customer_id),
    CONSTRAINT fk_czr_zone FOREIGN KEY (zone_id) REFERENCES customer_pricing_zones (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_czr_pct CHECK (fuel_surcharge_percent BETWEEN 0 AND 100
                                 AND fov_percent BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Rate card of one customer pricing zone: header charges (slabs in customer_zone_rate_slabs)';

CREATE TABLE customer_zone_rate_slabs (
    id          BINARY(16) NOT NULL,
    company_id  BINARY(16) NOT NULL,
    rate_id     BINARY(16) NOT NULL,
    -- half-open [min_weight, max_weight); max_weight NULL = no upper bound
    min_weight  DECIMAL(12, 3) NOT NULL,
    max_weight  DECIMAL(12, 3) NULL,
    rate_per_kg DECIMAL(12, 2) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_czrs_company_rate (company_id, rate_id),
    CONSTRAINT fk_czrs_rate FOREIGN KEY (rate_id) REFERENCES customer_zone_rates (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_czrs_range CHECK (min_weight >= 0 AND (max_weight IS NULL OR max_weight > min_weight)
                                    AND rate_per_kg >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Per-kg weight slabs of a customer zone rate';

-- --- permission catalogue: PRICING_ZONE (125) and CUSTOMER_RATE (127) modules ----------
-- MASTER shape (CREATE/READ/UPDATE/DELETE/SEARCH/IMPORT/EXPORT/ACTIVATE/DEACTIVATE).
-- COMPANY_ADMIN's set derives from the whole catalogue, so no role_permissions rows needed.
INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, NULL, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'PRICING_ZONE_CREATE' AS code, 'Create Pricing Zone' AS name, 'PRICING_ZONE' AS module, 'pricing-zones' AS resource, 'CREATE' AS action, 1250 AS display_order
  UNION ALL SELECT 'PRICING_ZONE_READ', 'View Pricing Zone', 'PRICING_ZONE', 'pricing-zones', 'READ', 1251
  UNION ALL SELECT 'PRICING_ZONE_UPDATE', 'Update Pricing Zone', 'PRICING_ZONE', 'pricing-zones', 'UPDATE', 1252
  UNION ALL SELECT 'PRICING_ZONE_DELETE', 'Delete Pricing Zone', 'PRICING_ZONE', 'pricing-zones', 'DELETE', 1253
  UNION ALL SELECT 'PRICING_ZONE_SEARCH', 'Search Pricing Zones', 'PRICING_ZONE', 'pricing-zones', 'SEARCH', 1254
  UNION ALL SELECT 'PRICING_ZONE_EXPORT', 'Export Pricing Zones', 'PRICING_ZONE', 'pricing-zones', 'EXPORT', 1255
  UNION ALL SELECT 'PRICING_ZONE_IMPORT', 'Import Pricing Zones', 'PRICING_ZONE', 'pricing-zones', 'IMPORT', 1256
  UNION ALL SELECT 'PRICING_ZONE_ACTIVATE', 'Activate Pricing Zone', 'PRICING_ZONE', 'pricing-zones', 'ACTIVATE', 1257
  UNION ALL SELECT 'PRICING_ZONE_DEACTIVATE', 'Deactivate Pricing Zone', 'PRICING_ZONE', 'pricing-zones', 'DEACTIVATE', 1258
  UNION ALL SELECT 'CUSTOMER_RATE_CREATE', 'Create Customer Rate', 'CUSTOMER_RATE', 'customer-rates', 'CREATE', 1270
  UNION ALL SELECT 'CUSTOMER_RATE_READ', 'View Customer Rate', 'CUSTOMER_RATE', 'customer-rates', 'READ', 1271
  UNION ALL SELECT 'CUSTOMER_RATE_UPDATE', 'Update Customer Rate', 'CUSTOMER_RATE', 'customer-rates', 'UPDATE', 1272
  UNION ALL SELECT 'CUSTOMER_RATE_DELETE', 'Delete Customer Rate', 'CUSTOMER_RATE', 'customer-rates', 'DELETE', 1273
  UNION ALL SELECT 'CUSTOMER_RATE_SEARCH', 'Search Customer Rates', 'CUSTOMER_RATE', 'customer-rates', 'SEARCH', 1274
  UNION ALL SELECT 'CUSTOMER_RATE_EXPORT', 'Export Customer Rates', 'CUSTOMER_RATE', 'customer-rates', 'EXPORT', 1275
  UNION ALL SELECT 'CUSTOMER_RATE_IMPORT', 'Import Customer Rates', 'CUSTOMER_RATE', 'customer-rates', 'IMPORT', 1276
  UNION ALL SELECT 'CUSTOMER_RATE_ACTIVATE', 'Activate Customer Rate', 'CUSTOMER_RATE', 'customer-rates', 'ACTIVATE', 1277
  UNION ALL SELECT 'CUSTOMER_RATE_DEACTIVATE', 'Deactivate Customer Rate', 'CUSTOMER_RATE', 'customer-rates', 'DEACTIVATE', 1278
) AS d;

-- --- menu: BUSINESS CUSTOMER group with Customer / Pricing Zone / Rate -----------------
SET @business_customer = UNHEX(REPLACE(UUID(), '-', ''));
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (@business_customer, NULL, 'business-customer', 'Business Customer', 'business_center', NULL, NULL,
        26, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES
  (UNHEX(REPLACE(UUID(), '-', '')), @business_customer, 'business-customers', 'Customer', 'contacts', '/business-customer/customers', 'CUSTOMER',
   1, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @business_customer, 'pricing-zones', 'Pricing Zone', 'map', '/business-customer/pricing-zones', 'PRICING_ZONE',
   2, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @business_customer, 'customer-rates', 'Rate', 'request_quote', '/business-customer/rates', 'CUSTOMER_RATE',
   3, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

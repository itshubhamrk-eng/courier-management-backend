-- =============================================================================
-- V115 — Pickup-Delivery Partner (PDP): branch type, permissions, menu, rate cards.
--
-- A PDP is a Branch of type PICKUP_DELIVERY_PARTNER (replaces the never-used VENDOR value;
-- no row is VENDOR today, the UPDATE is a safety net). A PDP only collects (pickup) and
-- delivers, and is paid from its OWN pickup and delivery rate cards — per-kg weight slabs
-- with a minimum chargeable weight — not from the branch commission fields.
--
-- pdp_rate_cards: one card per (partner branch, rate_type, district, validity window).
-- district_id NULL = every district (the partner's default card). district_id is a global
-- geography master id: validated in the service, no physical FK (same as customer zones).
-- =============================================================================

UPDATE branches SET branch_type = 'PICKUP_DELIVERY_PARTNER' WHERE branch_type = 'VENDOR';

CREATE TABLE pdp_rate_cards (
    id          BINARY(16)  NOT NULL,
    company_id  BINARY(16)  NOT NULL,
    branch_id   BINARY(16)  NOT NULL,
    rate_type   VARCHAR(20) NOT NULL,
    district_id BINARY(16)  NULL,

    min_chargeable_weight DECIMAL(12, 3) NOT NULL DEFAULT 0,
    mathadi_charge        DECIMAL(12, 2) NOT NULL DEFAULT 0,
    oda_charge            DECIMAL(12, 2) NOT NULL DEFAULT 0,
    -- PICKUP: paid when a pickup visit fails; DELIVERY: paid per failed delivery attempt
    failed_attempt_fee    DECIMAL(12, 2) NOT NULL DEFAULT 0,
    -- DELIVERY only: paid when the shipment is returned to origin
    return_fee            DECIMAL(12, 2) NOT NULL DEFAULT 0,

    effective_from DATE NULL,
    effective_to   DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_pdprc_company_branch (company_id, branch_id, rate_type),
    CONSTRAINT fk_pdprc_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_pdprc_type CHECK (rate_type IN ('PICKUP', 'DELIVERY'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Pickup / delivery rate card of a Pickup-Delivery Partner (slabs in pdp_rate_slabs)';

CREATE TABLE pdp_rate_slabs (
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
    KEY idx_pdprs_company_rate (company_id, rate_id),
    CONSTRAINT fk_pdprs_rate FOREIGN KEY (rate_id) REFERENCES pdp_rate_cards (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_pdprs_range CHECK (min_weight >= 0 AND (max_weight IS NULL OR max_weight > min_weight)
                                     AND rate_per_kg >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Per-kg weight slabs of a PDP rate card';

-- --- permission catalogue: PDP (205), PDP_RATE (206), PDP_BILLING (207) ----------------
INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, NULL, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'PDP_CREATE' AS code, 'Create Pickup-Delivery Partner' AS name, 'PDP' AS module, 'pdp' AS resource, 'CREATE' AS action, 1290 AS display_order
  UNION ALL SELECT 'PDP_READ', 'View Pickup-Delivery Partner', 'PDP', 'pdp', 'READ', 1291
  UNION ALL SELECT 'PDP_UPDATE', 'Update Pickup-Delivery Partner', 'PDP', 'pdp', 'UPDATE', 1292
  UNION ALL SELECT 'PDP_SEARCH', 'Search Pickup-Delivery Partner', 'PDP', 'pdp', 'SEARCH', 1293
  UNION ALL SELECT 'PDP_EXPORT', 'Export Pickup-Delivery Partner', 'PDP', 'pdp', 'EXPORT', 1294
  UNION ALL SELECT 'PDP_ACTIVATE', 'Activate Pickup-Delivery Partner', 'PDP', 'pdp', 'ACTIVATE', 1295
  UNION ALL SELECT 'PDP_DEACTIVATE', 'Deactivate Pickup-Delivery Partner', 'PDP', 'pdp', 'DEACTIVATE', 1296
  UNION ALL SELECT 'PDP_RATE_CREATE', 'Create Partner Rate', 'PDP_RATE', 'pdp-rates', 'CREATE', 1300
  UNION ALL SELECT 'PDP_RATE_READ', 'View Partner Rate', 'PDP_RATE', 'pdp-rates', 'READ', 1301
  UNION ALL SELECT 'PDP_RATE_UPDATE', 'Update Partner Rate', 'PDP_RATE', 'pdp-rates', 'UPDATE', 1302
  UNION ALL SELECT 'PDP_RATE_DELETE', 'Delete Partner Rate', 'PDP_RATE', 'pdp-rates', 'DELETE', 1303
  UNION ALL SELECT 'PDP_RATE_SEARCH', 'Search Partner Rate', 'PDP_RATE', 'pdp-rates', 'SEARCH', 1304
  UNION ALL SELECT 'PDP_RATE_IMPORT', 'Import Partner Rate', 'PDP_RATE', 'pdp-rates', 'IMPORT', 1305
  UNION ALL SELECT 'PDP_RATE_EXPORT', 'Export Partner Rate', 'PDP_RATE', 'pdp-rates', 'EXPORT', 1306
  UNION ALL SELECT 'PDP_RATE_ACTIVATE', 'Activate Partner Rate', 'PDP_RATE', 'pdp-rates', 'ACTIVATE', 1307
  UNION ALL SELECT 'PDP_RATE_DEACTIVATE', 'Deactivate Partner Rate', 'PDP_RATE', 'pdp-rates', 'DEACTIVATE', 1308
  UNION ALL SELECT 'PDP_BILLING_CREATE', 'Create Partner Billing', 'PDP_BILLING', 'pdp-billing', 'CREATE', 1310
  UNION ALL SELECT 'PDP_BILLING_READ', 'View Partner Billing', 'PDP_BILLING', 'pdp-billing', 'READ', 1311
  UNION ALL SELECT 'PDP_BILLING_UPDATE', 'Update Partner Billing', 'PDP_BILLING', 'pdp-billing', 'UPDATE', 1312
  UNION ALL SELECT 'PDP_BILLING_DELETE', 'Delete Partner Billing', 'PDP_BILLING', 'pdp-billing', 'DELETE', 1313
  UNION ALL SELECT 'PDP_BILLING_SEARCH', 'Search Partner Billing', 'PDP_BILLING', 'pdp-billing', 'SEARCH', 1314
  UNION ALL SELECT 'PDP_BILLING_EXPORT', 'Export Partner Billing', 'PDP_BILLING', 'pdp-billing', 'EXPORT', 1315
  UNION ALL SELECT 'PDP_BILLING_PRINT', 'Print Partner Billing', 'PDP_BILLING', 'pdp-billing', 'PRINT', 1316
) AS d;

-- Existing companies' COMPANY_ADMIN role holds explicit role_permissions rows (see V107):
-- grant the new codes. Never creates a role; a deliberately removed grant is left alone.
INSERT INTO role_permissions
    (id, company_id, role_id, permission_id, permission_code, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), r.company_id, r.id, p.id, p.permission_code, NOW(6), NOW(6), 0, 0
FROM company_roles r
JOIN permissions p ON p.module IN ('PDP', 'PDP_RATE', 'PDP_BILLING') AND p.deleted = 0
WHERE r.role_code = 'COMPANY_ADMIN' AND r.deleted = 0 AND r.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.company_id = r.company_id AND x.role_id = r.id AND x.permission_id = p.id);

-- --- menu: PICKUP DELIVERY PARTNER MANAGEMENT (separate top-level group) ----------------
SET @pdp_group = UNHEX(REPLACE(UUID(), '-', ''));
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (@pdp_group, NULL, 'pdp-management', 'Pickup Delivery Partner Management', 'handshake', NULL, NULL,
        27, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES
  (UNHEX(REPLACE(UUID(), '-', '')), @pdp_group, 'pdp-partners', 'Partners', 'groups', '/pdp/partners', 'PDP',
   1, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @pdp_group, 'pdp-rates', 'Partner Rates', 'request_quote', '/pdp/rates', 'PDP_RATE',
   2, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

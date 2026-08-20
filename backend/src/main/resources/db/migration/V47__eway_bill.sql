-- =============================================================================
-- V47 — E-Way Bill Management
--
-- Integrates E-Way Bill into Shipment Booking. Business rule: an invoice value over
-- the company's own configurable EWAY_BILL_MANDATORY_VALUE (default 50000.00) makes an
-- E-Way Bill mandatory before AWB generation; at or under it, an E-Way Bill is optional.
-- The threshold is never hardcoded in application code — see
-- company_settings_config.eway_bill_mandatory_value below.
--
-- shipments gains two columns:
--   invoice_value       — entered at booking time; the number the mandatory check reads.
--   eway_bill_required  — frozen at booking time from invoice_value vs. the threshold in
--                         effect that moment, so a later threshold change never silently
--                         reclassifies an already-booked shipment.
--
-- eway_bill is a new, standalone company-owned table (own id, own lifecycle), the same
-- shape every other module in this project follows: UUID PK, company_id, soft delete,
-- audit columns, optimistic locking. One shipment may carry more than one row over time
-- (a CANCELLED one re-issued), so there is no unique(company_id, shipment_id) — the
-- application layer takes "the newest, non-cancelled row" as the current one, the same
-- "newest row wins" precedent shipment_assets (V33) already set.
-- =============================================================================

ALTER TABLE company_settings_config
    ADD COLUMN eway_bill_mandatory_value DECIMAL(19, 4) NOT NULL DEFAULT 50000.0000
        COMMENT 'Invoice value above which an E-Way Bill is mandatory at booking';

ALTER TABLE shipments
    ADD COLUMN invoice_value      DECIMAL(19, 4) NULL,
    ADD COLUMN eway_bill_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE eway_bill (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    shipment_id BINARY(16) NOT NULL,

    -- Nullable until an actual number is issued/typed — a row may start life PENDING
    -- with no number yet, the same as document_url below.
    eway_bill_number VARCHAR(30) NULL,

    invoice_number VARCHAR(50)   NOT NULL,
    invoice_date   DATE          NOT NULL,
    invoice_value  DECIMAL(19,4) NOT NULL,

    -- INVOICE | BILL_OF_SUPPLY | DELIVERY_CHALLAN | OTHERS
    document_type   VARCHAR(20) NOT NULL DEFAULT 'INVOICE',
    document_number VARCHAR(50) NULL,
    document_date   DATE NULL,

    -- No FK: no Transporter/Vendor entity exists yet in this codebase to point at
    -- (PermissionModule.VENDOR is seeded, nothing implements it — the same
    -- "responsibility list ahead of the code" pattern this project has hit before).
    -- Free text for now; a real Vendor module can grow an FK onto this column later
    -- without a data migration, since the value is already the vendor's own identifier.
    transporter_id VARCHAR(50) NULL,
    vehicle_number VARCHAR(20) NULL,
    distance       INT NULL,

    valid_from  TIMESTAMP(6) NULL,
    valid_until TIMESTAMP(6) NULL,

    -- NOT_REQUIRED | REQUIRED | PENDING | UPLOADED | VALIDATED | INVALID | EXPIRED | CANCELLED
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- No file-storage backend beyond what Shipment Booking already wired (S3 seam,
    -- FileStoragePort) — reused as-is, not duplicated. URL is the source of truth,
    -- same honesty note shipment_documents (V17) already carries.
    document_url VARCHAR(1000) NULL,
    remarks      VARCHAR(500) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- MySQL/InnoDB treats each NULL as distinct in a unique index, so any number of
    -- number-less rows are still allowed alongside real, company-unique numbers.
    UNIQUE KEY uk_eway_bill_company_number (company_id, eway_bill_number),
    KEY idx_eway_bill_shipment (company_id, shipment_id, status, created_at),

    CONSTRAINT fk_eway_bill_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'E-Way Bill Management: one row per e-way bill raised against a shipment';

-- Permission catalogue: EWAY_BILL module (135, between SHIPMENT 130 and TRACKING 140),
-- 8 rights (CREATE/READ/UPDATE/SEARCH/EXPORT/UPLOAD/VALIDATE/CANCEL). "VIEW" from the
-- brief is seeded as READ — this catalogue has never used a "_VIEW" code, only "_READ"
-- (SHIPMENT_READ, CUSTOMER_READ, ...), so EWAY_BILL follows the same vocabulary rather
-- than being the one exception. VALIDATE/CANCEL are new PermissionAction values (23/24)
-- — ordinals are never persisted, only the name, so adding them is safe. Generated from
-- DefaultPermissionCatalog exactly as V6/V11/V12/V13/V16/V17 were; catalogue total moves
-- 223 -> 231, DefaultPermissionCatalogTest asserts it.
INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, NULL, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'EWAY_BILL_CREATE' AS code, 'Create E-Way Bill' AS name, 'EWAY_BILL' AS module, 'eway-bill' AS resource, 'CREATE' AS action, 136 AS display_order
  UNION ALL SELECT 'EWAY_BILL_READ', 'View E-Way Bill', 'EWAY_BILL', 'eway-bill', 'READ', 137
  UNION ALL SELECT 'EWAY_BILL_UPDATE', 'Update E-Way Bill', 'EWAY_BILL', 'eway-bill', 'UPDATE', 138
  UNION ALL SELECT 'EWAY_BILL_SEARCH', 'Search E-Way Bills', 'EWAY_BILL', 'eway-bill', 'SEARCH', 140
  UNION ALL SELECT 'EWAY_BILL_EXPORT', 'Export E-Way Bills', 'EWAY_BILL', 'eway-bill', 'EXPORT', 141
  UNION ALL SELECT 'EWAY_BILL_UPLOAD', 'Upload E-Way Bill Document', 'EWAY_BILL', 'eway-bill', 'UPLOAD', 146
  UNION ALL SELECT 'EWAY_BILL_VALIDATE', 'Validate E-Way Bill', 'EWAY_BILL', 'eway-bill', 'VALIDATE', 158
  UNION ALL SELECT 'EWAY_BILL_CANCEL', 'Cancel E-Way Bill', 'EWAY_BILL', 'eway-bill', 'CANCEL', 159
) AS d;

-- BRANCH_MANAGER and BOOKING_OPERATOR — the two roles that already hold SHIPMENT_CREATE
-- (V6) — are extended with every EWAY_BILL right, the same booking-desk reasoning V16/V17
-- used. COMPANY_ADMIN needs no row: ALL_PERMISSION_CODES already covers it.
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
             SELECT 'BRANCH_MANAGER' AS role_code, 'EWAY_BILL_CREATE' AS permission_code
             UNION ALL SELECT 'BRANCH_MANAGER', 'EWAY_BILL_READ'
             UNION ALL SELECT 'BRANCH_MANAGER', 'EWAY_BILL_UPDATE'
             UNION ALL SELECT 'BRANCH_MANAGER', 'EWAY_BILL_SEARCH'
             UNION ALL SELECT 'BRANCH_MANAGER', 'EWAY_BILL_EXPORT'
             UNION ALL SELECT 'BRANCH_MANAGER', 'EWAY_BILL_UPLOAD'
             UNION ALL SELECT 'BRANCH_MANAGER', 'EWAY_BILL_VALIDATE'
             UNION ALL SELECT 'BRANCH_MANAGER', 'EWAY_BILL_CANCEL'
             UNION ALL SELECT 'BOOKING_OPERATOR', 'EWAY_BILL_CREATE'
             UNION ALL SELECT 'BOOKING_OPERATOR', 'EWAY_BILL_READ'
             UNION ALL SELECT 'BOOKING_OPERATOR', 'EWAY_BILL_UPDATE'
             UNION ALL SELECT 'BOOKING_OPERATOR', 'EWAY_BILL_SEARCH'
             UNION ALL SELECT 'BOOKING_OPERATOR', 'EWAY_BILL_EXPORT'
             UNION ALL SELECT 'BOOKING_OPERATOR', 'EWAY_BILL_UPLOAD'
             UNION ALL SELECT 'BOOKING_OPERATOR', 'EWAY_BILL_VALIDATE'
             UNION ALL SELECT 'BOOKING_OPERATOR', 'EWAY_BILL_CANCEL'
         ) grant_map ON grant_map.role_code = r.role_code
         JOIN permissions p ON p.permission_code = grant_map.permission_code
WHERE r.deleted = FALSE
  AND EXISTS (SELECT 1 FROM role_permissions existing
              WHERE existing.role_id = r.id AND existing.permission_code = 'SHIPMENT_CREATE'
                AND existing.deleted = FALSE)
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.role_id = r.id AND x.permission_code = grant_map.permission_code
                    AND x.deleted = FALSE);

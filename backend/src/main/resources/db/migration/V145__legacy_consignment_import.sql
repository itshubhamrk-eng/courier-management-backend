-- Legacy consignment import: history-only shipments brought in from the old software's export.

CREATE TABLE legacy_import_batches (
    id             BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    file_hash      VARCHAR(64)  NOT NULL,
    new_count      INT          NOT NULL DEFAULT 0,
    existing_count INT          NOT NULL DEFAULT 0,
    invalid_count  INT          NOT NULL DEFAULT 0,
    status         VARCHAR(20)  NOT NULL DEFAULT 'COMMITTED',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_legacy_batches_company (company_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One row per committed legacy consignment import';

CREATE TABLE legacy_origin_branch (
    id         BINARY(16)   NOT NULL,
    company_id BINARY(16)   NOT NULL,
    origin_key VARCHAR(150) NOT NULL COMMENT 'From text, trimmed + upper-cased',
    branch_id  BINARY(16)   NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_legacy_origin_company_key (company_id, origin_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Remembered mapping of a legacy From text to one of our branches';

ALTER TABLE shipments
    ADD COLUMN legacy_import   BOOLEAN    NOT NULL DEFAULT FALSE,
    ADD COLUMN legacy_batch_id BINARY(16) NULL,
    ADD KEY idx_shipments_legacy (company_id, legacy_import);

-- permission catalogue: LEGACY_IMPORT (131), CREATE + READ only.
INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, NULL, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'LEGACY_IMPORT_CREATE' AS code, 'Import Legacy Consignments' AS name, 'LEGACY_IMPORT' AS module, 'legacy-import' AS resource, 'CREATE' AS action, 1310 AS display_order
  UNION ALL SELECT 'LEGACY_IMPORT_READ', 'View Legacy Imports', 'LEGACY_IMPORT', 'legacy-import', 'READ', 1311
) d
WHERE NOT EXISTS (SELECT 1 FROM permissions x WHERE x.permission_code = d.code);

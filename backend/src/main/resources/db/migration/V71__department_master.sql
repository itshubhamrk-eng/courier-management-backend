-- =============================================================================
-- V71 — Department Master
--
-- Adds `departments`: a company-defined organisational grouping (Sales, Operations,
-- Accounts...) used to narrow which of the company's own roles a new user may be given.
-- Same shape as `company_roles` in V4 — company-owned, code + name unique per company,
-- soft delete only.
--
-- `department_roles` is the join to `company_roles` a department offers, same shape
-- `role_permissions` (V6) already uses for role-to-permission grants: a real entity with
-- a denormalised `role_code`, not an @ManyToMany, so "what does this department offer"
-- is one indexed read.
--
-- `users.department_id` places a user in one department; `users.department` (the V7
-- free-text column) is left untouched for existing data and any screen that still reads
-- it — the two are independent columns, not a migration of one into the other.
--
-- Permission catalogue: new DEPARTMENT module (35, between USER 30 and ROLE 40), 8
-- rights (CREATE/READ/UPDATE/DELETE/SEARCH/EXPORT/ACTIVATE/DEACTIVATE) — the same shape
-- BRANCH already has. Generated from DefaultPermissionCatalog exactly as V6/V11/V12/V13/
-- V16/V17/V47/V63 were; catalogue total moves 240 -> 248, DefaultPermissionCatalogTest
-- asserts it. COMPANY_ADMIN needs no explicit role_permissions row — its set derives
-- from the whole catalogue, the same note V16/V47/V63 already give. BRANCH_MANAGER's own
-- DefaultRoleCatalog definition now lists DEPARTMENT_READ too, but that only reaches a
-- company created from here on — see that class's own note on why existing companies are
-- not backfilled.
-- =============================================================================

CREATE TABLE departments (
    id              BINARY(16)   NOT NULL,
    company_id      BINARY(16)   NOT NULL,

    department_code VARCHAR(50)  NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    description     VARCHAR(255) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_company_code (company_id, department_code),
    KEY idx_departments_company (company_id, status),

    CONSTRAINT fk_departments_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Per-company departments';


-- --- department_roles ---------------------------------------------------------

CREATE TABLE department_roles (
    id            BINARY(16) NOT NULL,
    company_id    BINARY(16) NOT NULL,

    department_id BINARY(16) NOT NULL,
    role_id       BINARY(16) NOT NULL,
    role_code     VARCHAR(50) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_department_roles_department_role (company_id, department_id, role_id),
    KEY idx_department_roles_department (company_id, department_id),
    KEY idx_department_roles_role (company_id, role_id),

    CONSTRAINT fk_department_roles_department
        FOREIGN KEY (department_id) REFERENCES departments (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    -- RESTRICT, not CASCADE: a role still offered by a department must not be
    -- removable out from under it — RoleServiceImpl.delete refuses first, with a
    -- readable message, so this is a backstop rather than the usual path.
    CONSTRAINT fk_department_roles_role
        FOREIGN KEY (role_id) REFERENCES company_roles (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Roles one department offers to a user placed in it';


-- --- users.department_id -------------------------------------------------------

ALTER TABLE users
    ADD COLUMN department_id BINARY(16) NULL AFTER department;

CREATE INDEX idx_users_company_department ON users (company_id, department_id);

ALTER TABLE users
    ADD CONSTRAINT fk_users_department
        FOREIGN KEY (department_id) REFERENCES departments (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;


-- --- permission catalogue: DEPARTMENT module ------------------------------------

INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, NULL, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'DEPARTMENT_CREATE' AS code, 'Create Department' AS name, 'DEPARTMENT' AS module, 'departments' AS resource, 'CREATE' AS action, 36 AS display_order
  UNION ALL SELECT 'DEPARTMENT_READ', 'View Department', 'DEPARTMENT', 'departments', 'READ', 37
  UNION ALL SELECT 'DEPARTMENT_UPDATE', 'Update Department', 'DEPARTMENT', 'departments', 'UPDATE', 38
  UNION ALL SELECT 'DEPARTMENT_DELETE', 'Delete Department', 'DEPARTMENT', 'departments', 'DELETE', 39
  UNION ALL SELECT 'DEPARTMENT_SEARCH', 'Search Departments', 'DEPARTMENT', 'departments', 'SEARCH', 40
  UNION ALL SELECT 'DEPARTMENT_EXPORT', 'Export Departments', 'DEPARTMENT', 'departments', 'EXPORT', 41
  UNION ALL SELECT 'DEPARTMENT_ACTIVATE', 'Activate Department', 'DEPARTMENT', 'departments', 'ACTIVATE', 49
  UNION ALL SELECT 'DEPARTMENT_DEACTIVATE', 'Deactivate Department', 'DEPARTMENT', 'departments', 'DEACTIVATE', 50
) AS d;

-- Menus a company role holds, one row per (role, leaf menu). A user's sidebar is the union of
-- these over their roles, minus whatever an admin unticked for that one user in
-- user_menu_grants (still deny-only). Replaces inferring a role's menus from permission
-- modules, which coupled every menu that shares a module (Tickets <-> Communication, ...).
CREATE TABLE role_menu (
    id           BINARY(16) NOT NULL,
    company_id   BINARY(16) NOT NULL,
    role_id      BINARY(16) NOT NULL,
    menu_item_id BINARY(16) NOT NULL,
    can_create   BOOLEAN    NOT NULL,
    can_read     BOOLEAN    NOT NULL,
    can_update   BOOLEAN    NOT NULL,
    can_delete   BOOLEAN    NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_role_menu_role_menu (role_id, menu_item_id),
    KEY idx_role_menu_company_role (company_id, role_id),
    CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES company_roles (id),
    CONSTRAINT fk_role_menu_menu FOREIGN KEY (menu_item_id) REFERENCES menu_items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Menu items a company role holds, with per-action ticks';

-- Backfill: reproduce today's inferred visibility so nobody loses a menu. A role holds a leaf
-- menu when it holds <module>_READ; the other flags copy <module>_CREATE/_UPDATE/_DELETE.
INSERT INTO role_menu (id, company_id, role_id, menu_item_id,
                       can_create, can_read, can_update, can_delete,
                       created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), r.company_id, r.id, m.id,
       EXISTS (SELECT 1 FROM role_permissions p WHERE p.role_id = r.id AND p.deleted = FALSE
               AND p.permission_code = CONCAT(m.permission_module, '_CREATE')),
       TRUE,
       EXISTS (SELECT 1 FROM role_permissions p WHERE p.role_id = r.id AND p.deleted = FALSE
               AND p.permission_code = CONCAT(m.permission_module, '_UPDATE')),
       EXISTS (SELECT 1 FROM role_permissions p WHERE p.role_id = r.id AND p.deleted = FALSE
               AND p.permission_code = CONCAT(m.permission_module, '_DELETE')),
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM company_roles r
JOIN menu_items m ON m.permission_module IS NOT NULL AND m.deleted = FALSE
WHERE r.deleted = FALSE
  AND EXISTS (SELECT 1 FROM role_permissions p WHERE p.role_id = r.id AND p.deleted = FALSE
              AND p.permission_code = CONCAT(m.permission_module, '_READ'));

-- Per-menu, per-user CRUD ticks for the User Permissions screen.
--
-- user_permission_overrides is keyed by permission (module), so several menus that share a
-- module (e.g. every Reports leaf -> REPORT) could not be ticked independently. This table
-- records what the admin ticked for each individual menu item. The module-level overrides
-- are still derived from it (a module's right is the union over its menus), so JWT
-- permissions and @PreAuthorize are unchanged. A menu with no row here simply follows the
-- user's module-level effective rights, exactly as before.
CREATE TABLE user_menu_grants (
    id           BINARY(16) NOT NULL,
    company_id   BINARY(16) NOT NULL,
    user_id      BINARY(16) NOT NULL,
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
    UNIQUE KEY uk_user_menu_grants_user_menu (company_id, user_id, menu_item_id),
    KEY idx_user_menu_grants_user (company_id, user_id),
    CONSTRAINT fk_user_menu_grants_menu FOREIGN KEY (menu_item_id) REFERENCES menu_items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'What an admin ticked for each menu item of one user';

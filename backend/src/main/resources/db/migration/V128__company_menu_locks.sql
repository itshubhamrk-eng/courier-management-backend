-- =============================================================================
-- V128 — Per-company menu locks, set by SUPER_ADMIN.
--
-- A row means: this company may not see that sidebar menu (a top-level group such as
-- 'msrtc', or one leaf id). No row = visible, so every existing company is unchanged.
-- This sits ABOVE the COMPANY_ADMIN's own role/user permission control: the company
-- admin cannot grant back what the platform has locked.
--
-- menu_code is the frontend navigation.config.ts node id (same convention as
-- menu_items.code) but is deliberately not an FK to menu_items: that catalogue does
-- not seed every sidebar group (e.g. MSRTC).
--
-- Platform-level table written by SUPER_ADMIN, who is tenant-unbound, so it carries an
-- explicit company_id and no tenant filter. Rows are hard-deleted (unlock = delete)
-- so the unique key never collides with a soft-deleted row.
--
-- Forward-only. V127 is the previous migration.
-- =============================================================================
CREATE TABLE company_menu_locks (
    id         BINARY(16)   NOT NULL,
    company_id BINARY(16)   NOT NULL,
    menu_code  VARCHAR(80)  NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_company_menu_locks_company_code (company_id, menu_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Sidebar menus a SUPER_ADMIN has hidden from one company';

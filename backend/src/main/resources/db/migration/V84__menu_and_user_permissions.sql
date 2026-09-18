-- =============================================================================
-- V84 — Menu hierarchy + per-user permission overrides.
--
-- Two new tables, both reusing the existing `permissions` catalogue rather than
-- inventing a second grant system:
--
--   1. menu_items — PLATFORM-LEVEL, no company_id, unlimited depth via a self-referencing
--      parent_id. Every company sees the same screens; only SUPER_ADMIN edits the
--      catalogue (same posture as `permissions`). A leaf optionally names a
--      `permission_module` — its CREATE/READ/UPDATE/DELETE checkboxes are literally that
--      module's `<MODULE>_CREATE`/`_READ`/`_UPDATE`/`_DELETE` rows in `permissions`. A
--      group node (no module) is pure hierarchy, not itself grantable.
--
--   2. user_permission_overrides — COMPANY-OWNED, mirrors `role_permissions` exactly
--      (same denormalised permission_code, same reasoning). Holds only the *deltas*
--      between what a user's role already grants and what an admin explicitly chose for
--      that one user — a checkbox left at the role default gets no row at all, so a
--      brand-new user with zero overrides already gets exactly their role's defaults,
--      and a later change to the role's own grants keeps flowing through for anyone who
--      never overrode that particular right.
--
-- Seeded with the real current menu hierarchy (mirrors frontend navigation.config.ts's
-- own node ids/titles/routes), not a placeholder example.
--
-- Forward-only. V83 is the previous migration.
-- =============================================================================

CREATE TABLE menu_items (
    id               BINARY(16)   NOT NULL,
    parent_id        BINARY(16)   NULL,

    code             VARCHAR(80)  NOT NULL,
    title            VARCHAR(150) NOT NULL,
    icon             VARCHAR(60)  NULL,
    route            VARCHAR(200) NULL,

    -- Null for a grouping node. One of the 28 PermissionModule names for a leaf.
    permission_module VARCHAR(30) NULL,

    display_order    INT          NOT NULL DEFAULT 0,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    is_system        BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_menu_items_code (code),
    KEY idx_menu_items_parent (parent_id, display_order),
    KEY idx_menu_items_module (permission_module),
    CONSTRAINT fk_menu_items_parent FOREIGN KEY (parent_id) REFERENCES menu_items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Menu/submenu hierarchy, unlimited depth';

CREATE TABLE user_permission_overrides (
    id              BINARY(16)   NOT NULL,
    company_id      BINARY(16)   NOT NULL,
    user_id         BINARY(16)   NOT NULL,
    permission_id   BINARY(16)   NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    granted         BOOLEAN      NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_permission_overrides_user_permission (company_id, user_id, permission_id),
    KEY idx_user_permission_overrides_user (company_id, user_id),
    CONSTRAINT fk_user_permission_overrides_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Per-user permission deltas on top of their role defaults';


-- --- seed: the real menu hierarchy -------------------------------------------
-- Mirrors frontend navigation.config.ts's own section/leaf ids, titles and routes.
-- Only leaves whose module already has real CRUD rows in `permissions` are linked;
-- a leaf with no catalogue module today (Freight Factor, Vehicles, Follow-ups, ...) is
-- left out rather than pointed at a module that would not actually gate it.

-- Administration
SET @administration = UNHEX(REPLACE(UUID(), '-', ''));
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (@administration, NULL, 'administration', 'Administration', 'admin_panel_settings', NULL, NULL,
        20, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES
  (UNHEX(REPLACE(UUID(), '-', '')), @administration, 'users', 'Users', 'group', '/users', 'USER',
   1, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @administration, 'departments', 'Departments', 'apartment', '/departments', 'DEPARTMENT',
   2, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @administration, 'roles', 'Roles', 'badge', '/roles', 'ROLE',
   3, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @administration, 'permissions', 'Permissions', 'key', '/permissions', 'PERMISSION',
   4, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @administration, 'branches', 'Branches', 'store', '/branches', 'BRANCH',
   5, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @administration, 'company-settings', 'Company Settings', 'tune', '/settings', 'SETTINGS',
   6, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

-- Customers (top-level leaf, no group in the frontend config either)
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (UNHEX(REPLACE(UUID(), '-', '')), NULL, 'customers', 'Customers', 'contacts', '/customers', 'CUSTOMER',
        25, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

-- Rate Master
SET @rate_master = UNHEX(REPLACE(UUID(), '-', ''));
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (@rate_master, NULL, 'rate-master', 'Rate Master', 'price_change', NULL, NULL,
        27, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (UNHEX(REPLACE(UUID(), '-', '')), @rate_master, 'rates', 'Rate Cards', 'request_quote', '/rates', 'RATE_MASTER',
        1, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

-- Masters
SET @masters = UNHEX(REPLACE(UUID(), '-', ''));
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (@masters, NULL, 'masters', 'Masters', 'inventory_2', NULL, NULL,
        30, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES
  (UNHEX(REPLACE(UUID(), '-', '')), @masters, 'global-pincodes', 'Pincode', 'markunread_mailbox', '/masters/pincodes', 'GLOBAL_MASTER',
   1, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @masters, 'vehicle-types', 'Vehicle Type', 'local_shipping', '/masters/vehicle-types', 'MASTER_DATA',
   2, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @masters, 'package-types', 'Package Type', 'inventory_2', '/masters/package-types', 'MASTER_DATA',
   3, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @masters, 'service-types', 'Service Type', 'bolt', '/masters/service-types', 'MASTER_DATA',
   4, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @masters, 'routes', 'Route', 'route', '/masters/routes', 'ROUTE_MASTER',
   5, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

-- Shipment Management (frontend's "Operations" section — named per the user's own example)
SET @shipment_mgmt = UNHEX(REPLACE(UUID(), '-', ''));
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (@shipment_mgmt, NULL, 'operations', 'Shipment Management', 'local_shipping', NULL, NULL,
        35, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES
  (UNHEX(REPLACE(UUID(), '-', '')), @shipment_mgmt, 'booking', 'Shipment Booking', 'add_box', '/shipments/new', 'SHIPMENT',
   1, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @shipment_mgmt, 'shipment-search', 'Shipment List', 'search', '/shipments', 'SHIPMENT',
   2, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @shipment_mgmt, 'track', 'Shipment Tracking', 'my_location', '/track', 'SHIPMENT',
   3, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @shipment_mgmt, 'pending-delivery', 'Pending Delivery', 'pending_actions', '/movement/pending-delivery', 'DELIVERY',
   5, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @shipment_mgmt, 'delivery', 'Delivery', 'task_alt', '/movement/delivery', 'DELIVERY',
   6, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

-- Manifest, nested under Shipment Management — the unlimited-depth case.
SET @manifest = UNHEX(REPLACE(UUID(), '-', ''));
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (@manifest, @shipment_mgmt, 'manifest-group', 'Manifest', 'qr_code_scanner', NULL, NULL,
        4, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES
  (UNHEX(REPLACE(UUID(), '-', '')), @manifest, 'manifest', 'Create Manifest', 'add_box', '/movement/loading-sheet', 'MANIFEST',
   1, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @manifest, 'manifest-list', 'Manifest List', 'list_alt', '/movement/manifests', 'MANIFEST',
   2, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @manifest, 'dispatch', 'Dispatch', 'outbound', '/movement/trip-hire-challan', 'MANIFEST',
   3, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @manifest, 'receive', 'In Scan', 'move_to_inbox', '/movement/in-scan', 'MANIFEST',
   4, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

-- Finance
SET @finance = UNHEX(REPLACE(UUID(), '-', ''));
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (@finance, NULL, 'finance', 'Finance', 'account_balance', NULL, NULL,
        40, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES
  (UNHEX(REPLACE(UUID(), '-', '')), @finance, 'branch-wallet', 'Branch Wallet', 'account_balance_wallet', '/finance/branch-wallet', 'WALLET',
   1, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @finance, 'payment', 'Payment', 'credit_card', '/finance/payment', 'PAYMENT',
   2, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @finance, 'invoice', 'Invoice', 'description', '/finance/invoice', 'INVOICE',
   3, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

-- Reports
SET @reports = UNHEX(REPLACE(UUID(), '-', ''));
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (@reports, NULL, 'reports', 'Reports', 'insights', NULL, NULL,
        45, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES
  (UNHEX(REPLACE(UUID(), '-', '')), @reports, 'booking-report', 'Booking Report', 'summarize', '/reports/bookings', 'REPORT',
   1, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @reports, 'delivery-report', 'Delivery Report', 'local_shipping', '/reports/deliveries', 'REPORT',
   2, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

-- Settings (top-level leaf)
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (UNHEX(REPLACE(UUID(), '-', '')), NULL, 'settings', 'Settings', 'settings', '/settings', 'SETTINGS',
        50, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

-- =============================================================================
-- V6 — Permission Management (Phase 3, Company Administration)
--
-- Permissions were a Java enum of 30 constants held in an @ElementCollection
-- (company_role_permissions). That was fine while the list was hard-coded; a
-- catalogue of 174 rights across 28 modules that operators must list, search and
-- extend is a table.
--
-- This migration therefore:
--   1. creates `permissions` — PLATFORM-LEVEL, no company_id. Every company sees the
--      same vocabulary; only SUPER_ADMIN writes it.
--   2. seeds all 174 system permissions, generated from DefaultPermissionCatalog so
--      the SQL and the Java cannot drift.
--   3. creates `role_permissions` — COMPANY-OWNED, replacing the element collection.
--      A grant now has its own id and audit columns, so "who gave this role the
--      right to cancel shipments, and when" is answerable.
--   4. carries every existing grant across, expanding the old coarse constants.
--   5. drops company_role_permissions.
--
-- Forward-only. V2 auth, V3 subscription, V4 company, V5 roles, V6 this.
-- =============================================================================

CREATE TABLE permissions (
    id                    BINARY(16)   NOT NULL,

    -- Derived as MODULE_ACTION and immutable: roles, grants and @PreAuthorize
    -- expressions all reference it.
    permission_code       VARCHAR(100) NOT NULL,
    permission_name       VARCHAR(150) NOT NULL,

    module                VARCHAR(30)  NOT NULL,
    -- URL spelling, so an authorisation filter can eventually map a request path to
    -- a permission without a lookup table.
    resource              VARCHAR(60)  NOT NULL,
    action                VARCHAR(20)  NOT NULL,
    description           VARCHAR(255) NULL,

    -- Seeded rows are read-only: they may not be edited or deleted.
    is_system_permission  BOOLEAN      NOT NULL DEFAULT FALSE,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order         INT          NOT NULL DEFAULT 0,

    -- Subscription feature this right depends on, or NULL when unconditional.
    -- Carried over from the enum: a plan without bulk booking must not be able to
    -- grant it, however the grant is attempted.
    required_feature_flag VARCHAR(50)  NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (permission_code),
    -- The pair is the code, so uniqueness on it prevents two rows claiming the same
    -- right under different spellings.
    UNIQUE KEY uk_permissions_module_action (module, action),
    KEY idx_permissions_module (module, display_order),
    KEY idx_permissions_status (status),
    KEY idx_permissions_resource (resource)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Platform permission catalogue';


-- --- seed: 174 system permissions -------------------------------------------
-- Generated from DefaultPermissionCatalog. Not the 28 x 15 cross product:
-- DASHBOARD_DELETE and TRACKING_APPROVE are not rights anyone can hold, and
-- seeding them would put meaningless rows in front of an operator.

INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, d.feature_flag, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'AUTH_READ' AS code, 'Read Authentication' AS name, 'AUTH' AS module, 'auth' AS resource, 'READ' AS action, 12 AS display_order, NULL AS feature_flag
  UNION ALL SELECT 'AUTH_SEARCH', 'Search Authentication', 'AUTH', 'auth', 'SEARCH', 15, NULL
  UNION ALL SELECT 'COMPANY_READ', 'Read Company', 'COMPANY', 'company', 'READ', 22, NULL
  UNION ALL SELECT 'COMPANY_UPDATE', 'Update Company', 'COMPANY', 'company', 'UPDATE', 23, NULL
  UNION ALL SELECT 'USER_CREATE', 'Create Users', 'USER', 'user', 'CREATE', 31, NULL
  UNION ALL SELECT 'USER_READ', 'Read Users', 'USER', 'user', 'READ', 32, NULL
  UNION ALL SELECT 'USER_UPDATE', 'Update Users', 'USER', 'user', 'UPDATE', 33, NULL
  UNION ALL SELECT 'USER_DELETE', 'Delete Users', 'USER', 'user', 'DELETE', 34, NULL
  UNION ALL SELECT 'USER_SEARCH', 'Search Users', 'USER', 'user', 'SEARCH', 35, NULL
  UNION ALL SELECT 'USER_EXPORT', 'Export Users', 'USER', 'user', 'EXPORT', 36, NULL
  UNION ALL SELECT 'USER_ASSIGN', 'Assign Users', 'USER', 'user', 'ASSIGN', 43, NULL
  UNION ALL SELECT 'USER_ACTIVATE', 'Activate Users', 'USER', 'user', 'ACTIVATE', 44, NULL
  UNION ALL SELECT 'USER_DEACTIVATE', 'Deactivate Users', 'USER', 'user', 'DEACTIVATE', 45, NULL
  UNION ALL SELECT 'ROLE_CREATE', 'Create Roles', 'ROLE', 'role', 'CREATE', 41, NULL
  UNION ALL SELECT 'ROLE_READ', 'Read Roles', 'ROLE', 'role', 'READ', 42, NULL
  UNION ALL SELECT 'ROLE_UPDATE', 'Update Roles', 'ROLE', 'role', 'UPDATE', 43, NULL
  UNION ALL SELECT 'ROLE_DELETE', 'Delete Roles', 'ROLE', 'role', 'DELETE', 44, NULL
  UNION ALL SELECT 'ROLE_SEARCH', 'Search Roles', 'ROLE', 'role', 'SEARCH', 45, NULL
  UNION ALL SELECT 'ROLE_ASSIGN', 'Assign Roles', 'ROLE', 'role', 'ASSIGN', 53, NULL
  UNION ALL SELECT 'ROLE_ACTIVATE', 'Activate Roles', 'ROLE', 'role', 'ACTIVATE', 54, NULL
  UNION ALL SELECT 'ROLE_DEACTIVATE', 'Deactivate Roles', 'ROLE', 'role', 'DEACTIVATE', 55, NULL
  UNION ALL SELECT 'PERMISSION_CREATE', 'Create Permissions', 'PERMISSION', 'permission', 'CREATE', 51, NULL
  UNION ALL SELECT 'PERMISSION_READ', 'Read Permissions', 'PERMISSION', 'permission', 'READ', 52, NULL
  UNION ALL SELECT 'PERMISSION_UPDATE', 'Update Permissions', 'PERMISSION', 'permission', 'UPDATE', 53, NULL
  UNION ALL SELECT 'PERMISSION_DELETE', 'Delete Permissions', 'PERMISSION', 'permission', 'DELETE', 54, NULL
  UNION ALL SELECT 'PERMISSION_SEARCH', 'Search Permissions', 'PERMISSION', 'permission', 'SEARCH', 55, NULL
  UNION ALL SELECT 'PERMISSION_ASSIGN', 'Assign Permissions', 'PERMISSION', 'permission', 'ASSIGN', 63, NULL
  UNION ALL SELECT 'BRANCH_CREATE', 'Create Branches', 'BRANCH', 'branch', 'CREATE', 61, NULL
  UNION ALL SELECT 'BRANCH_READ', 'Read Branches', 'BRANCH', 'branch', 'READ', 62, NULL
  UNION ALL SELECT 'BRANCH_UPDATE', 'Update Branches', 'BRANCH', 'branch', 'UPDATE', 63, NULL
  UNION ALL SELECT 'BRANCH_DELETE', 'Delete Branches', 'BRANCH', 'branch', 'DELETE', 64, NULL
  UNION ALL SELECT 'BRANCH_SEARCH', 'Search Branches', 'BRANCH', 'branch', 'SEARCH', 65, NULL
  UNION ALL SELECT 'BRANCH_EXPORT', 'Export Branches', 'BRANCH', 'branch', 'EXPORT', 66, NULL
  UNION ALL SELECT 'BRANCH_ACTIVATE', 'Activate Branches', 'BRANCH', 'branch', 'ACTIVATE', 74, NULL
  UNION ALL SELECT 'BRANCH_DEACTIVATE', 'Deactivate Branches', 'BRANCH', 'branch', 'DEACTIVATE', 75, NULL
  UNION ALL SELECT 'HUB_CREATE', 'Create Hubs', 'HUB', 'hub', 'CREATE', 71, NULL
  UNION ALL SELECT 'HUB_READ', 'Read Hubs', 'HUB', 'hub', 'READ', 72, NULL
  UNION ALL SELECT 'HUB_UPDATE', 'Update Hubs', 'HUB', 'hub', 'UPDATE', 73, NULL
  UNION ALL SELECT 'HUB_DELETE', 'Delete Hubs', 'HUB', 'hub', 'DELETE', 74, NULL
  UNION ALL SELECT 'HUB_SEARCH', 'Search Hubs', 'HUB', 'hub', 'SEARCH', 75, NULL
  UNION ALL SELECT 'HUB_EXPORT', 'Export Hubs', 'HUB', 'hub', 'EXPORT', 76, NULL
  UNION ALL SELECT 'HUB_ACTIVATE', 'Activate Hubs', 'HUB', 'hub', 'ACTIVATE', 84, NULL
  UNION ALL SELECT 'HUB_DEACTIVATE', 'Deactivate Hubs', 'HUB', 'hub', 'DEACTIVATE', 85, NULL
  UNION ALL SELECT 'CUSTOMER_CREATE', 'Create Customers', 'CUSTOMER', 'customer', 'CREATE', 81, NULL
  UNION ALL SELECT 'CUSTOMER_READ', 'Read Customers', 'CUSTOMER', 'customer', 'READ', 82, NULL
  UNION ALL SELECT 'CUSTOMER_UPDATE', 'Update Customers', 'CUSTOMER', 'customer', 'UPDATE', 83, NULL
  UNION ALL SELECT 'CUSTOMER_DELETE', 'Delete Customers', 'CUSTOMER', 'customer', 'DELETE', 84, NULL
  UNION ALL SELECT 'CUSTOMER_SEARCH', 'Search Customers', 'CUSTOMER', 'customer', 'SEARCH', 85, NULL
  UNION ALL SELECT 'CUSTOMER_IMPORT', 'Import Customers', 'CUSTOMER', 'customer', 'IMPORT', 87, 'bulkBooking'
  UNION ALL SELECT 'CUSTOMER_EXPORT', 'Export Customers', 'CUSTOMER', 'customer', 'EXPORT', 86, NULL
  UNION ALL SELECT 'CUSTOMER_ACTIVATE', 'Activate Customers', 'CUSTOMER', 'customer', 'ACTIVATE', 94, NULL
  UNION ALL SELECT 'CUSTOMER_DEACTIVATE', 'Deactivate Customers', 'CUSTOMER', 'customer', 'DEACTIVATE', 95, NULL
  UNION ALL SELECT 'ADDRESS_CREATE', 'Create Addresses', 'ADDRESS', 'address', 'CREATE', 91, NULL
  UNION ALL SELECT 'ADDRESS_READ', 'Read Addresses', 'ADDRESS', 'address', 'READ', 92, NULL
  UNION ALL SELECT 'ADDRESS_UPDATE', 'Update Addresses', 'ADDRESS', 'address', 'UPDATE', 93, NULL
  UNION ALL SELECT 'ADDRESS_DELETE', 'Delete Addresses', 'ADDRESS', 'address', 'DELETE', 94, NULL
  UNION ALL SELECT 'ADDRESS_SEARCH', 'Search Addresses', 'ADDRESS', 'address', 'SEARCH', 95, NULL
  UNION ALL SELECT 'PINCODE_CREATE', 'Create Pincodes', 'PINCODE', 'pincode', 'CREATE', 101, NULL
  UNION ALL SELECT 'PINCODE_READ', 'Read Pincodes', 'PINCODE', 'pincode', 'READ', 102, NULL
  UNION ALL SELECT 'PINCODE_UPDATE', 'Update Pincodes', 'PINCODE', 'pincode', 'UPDATE', 103, NULL
  UNION ALL SELECT 'PINCODE_DELETE', 'Delete Pincodes', 'PINCODE', 'pincode', 'DELETE', 104, NULL
  UNION ALL SELECT 'PINCODE_SEARCH', 'Search Pincodes', 'PINCODE', 'pincode', 'SEARCH', 105, NULL
  UNION ALL SELECT 'PINCODE_IMPORT', 'Import Pincodes', 'PINCODE', 'pincode', 'IMPORT', 107, NULL
  UNION ALL SELECT 'PINCODE_EXPORT', 'Export Pincodes', 'PINCODE', 'pincode', 'EXPORT', 106, NULL
  UNION ALL SELECT 'RATE_MASTER_CREATE', 'Create Rate Master', 'RATE_MASTER', 'rate-master', 'CREATE', 111, NULL
  UNION ALL SELECT 'RATE_MASTER_READ', 'Read Rate Master', 'RATE_MASTER', 'rate-master', 'READ', 112, NULL
  UNION ALL SELECT 'RATE_MASTER_UPDATE', 'Update Rate Master', 'RATE_MASTER', 'rate-master', 'UPDATE', 113, NULL
  UNION ALL SELECT 'RATE_MASTER_DELETE', 'Delete Rate Master', 'RATE_MASTER', 'rate-master', 'DELETE', 114, NULL
  UNION ALL SELECT 'RATE_MASTER_SEARCH', 'Search Rate Master', 'RATE_MASTER', 'rate-master', 'SEARCH', 115, NULL
  UNION ALL SELECT 'RATE_MASTER_IMPORT', 'Import Rate Master', 'RATE_MASTER', 'rate-master', 'IMPORT', 117, NULL
  UNION ALL SELECT 'RATE_MASTER_EXPORT', 'Export Rate Master', 'RATE_MASTER', 'rate-master', 'EXPORT', 116, NULL
  UNION ALL SELECT 'RATE_MASTER_APPROVE', 'Approve Rate Master', 'RATE_MASTER', 'rate-master', 'APPROVE', 118, NULL
  UNION ALL SELECT 'ROUTE_MASTER_CREATE', 'Create Route Master', 'ROUTE_MASTER', 'route-master', 'CREATE', 121, NULL
  UNION ALL SELECT 'ROUTE_MASTER_READ', 'Read Route Master', 'ROUTE_MASTER', 'route-master', 'READ', 122, NULL
  UNION ALL SELECT 'ROUTE_MASTER_UPDATE', 'Update Route Master', 'ROUTE_MASTER', 'route-master', 'UPDATE', 123, NULL
  UNION ALL SELECT 'ROUTE_MASTER_DELETE', 'Delete Route Master', 'ROUTE_MASTER', 'route-master', 'DELETE', 124, NULL
  UNION ALL SELECT 'ROUTE_MASTER_SEARCH', 'Search Route Master', 'ROUTE_MASTER', 'route-master', 'SEARCH', 125, NULL
  UNION ALL SELECT 'ROUTE_MASTER_IMPORT', 'Import Route Master', 'ROUTE_MASTER', 'route-master', 'IMPORT', 127, NULL
  UNION ALL SELECT 'ROUTE_MASTER_EXPORT', 'Export Route Master', 'ROUTE_MASTER', 'route-master', 'EXPORT', 126, NULL
  UNION ALL SELECT 'SHIPMENT_CREATE', 'Create Shipments', 'SHIPMENT', 'shipment', 'CREATE', 131, NULL
  UNION ALL SELECT 'SHIPMENT_READ', 'Read Shipments', 'SHIPMENT', 'shipment', 'READ', 132, NULL
  UNION ALL SELECT 'SHIPMENT_UPDATE', 'Update Shipments', 'SHIPMENT', 'shipment', 'UPDATE', 133, NULL
  UNION ALL SELECT 'SHIPMENT_DELETE', 'Delete Shipments', 'SHIPMENT', 'shipment', 'DELETE', 134, NULL
  UNION ALL SELECT 'SHIPMENT_SEARCH', 'Search Shipments', 'SHIPMENT', 'shipment', 'SEARCH', 135, NULL
  UNION ALL SELECT 'SHIPMENT_IMPORT', 'Import Shipments', 'SHIPMENT', 'shipment', 'IMPORT', 137, 'bulkBooking'
  UNION ALL SELECT 'SHIPMENT_EXPORT', 'Export Shipments', 'SHIPMENT', 'shipment', 'EXPORT', 136, NULL
  UNION ALL SELECT 'SHIPMENT_PRINT', 'Print Shipments', 'SHIPMENT', 'shipment', 'PRINT', 140, NULL
  UNION ALL SELECT 'SHIPMENT_ASSIGN', 'Assign Shipments', 'SHIPMENT', 'shipment', 'ASSIGN', 143, NULL
  UNION ALL SELECT 'TRACKING_CREATE', 'Create Tracking', 'TRACKING', 'tracking', 'CREATE', 141, NULL
  UNION ALL SELECT 'TRACKING_READ', 'Read Tracking', 'TRACKING', 'tracking', 'READ', 142, NULL
  UNION ALL SELECT 'TRACKING_SEARCH', 'Search Tracking', 'TRACKING', 'tracking', 'SEARCH', 145, NULL
  UNION ALL SELECT 'TRACKING_EXPORT', 'Export Tracking', 'TRACKING', 'tracking', 'EXPORT', 146, NULL
  UNION ALL SELECT 'MANIFEST_CREATE', 'Create Manifests', 'MANIFEST', 'manifest', 'CREATE', 151, NULL
  UNION ALL SELECT 'MANIFEST_READ', 'Read Manifests', 'MANIFEST', 'manifest', 'READ', 152, NULL
  UNION ALL SELECT 'MANIFEST_UPDATE', 'Update Manifests', 'MANIFEST', 'manifest', 'UPDATE', 153, NULL
  UNION ALL SELECT 'MANIFEST_DELETE', 'Delete Manifests', 'MANIFEST', 'manifest', 'DELETE', 154, NULL
  UNION ALL SELECT 'MANIFEST_SEARCH', 'Search Manifests', 'MANIFEST', 'manifest', 'SEARCH', 155, NULL
  UNION ALL SELECT 'MANIFEST_PRINT', 'Print Manifests', 'MANIFEST', 'manifest', 'PRINT', 160, NULL
  UNION ALL SELECT 'MANIFEST_EXPORT', 'Export Manifests', 'MANIFEST', 'manifest', 'EXPORT', 156, NULL
  UNION ALL SELECT 'PICKUP_CREATE', 'Create Pickups', 'PICKUP', 'pickup', 'CREATE', 161, NULL
  UNION ALL SELECT 'PICKUP_READ', 'Read Pickups', 'PICKUP', 'pickup', 'READ', 162, NULL
  UNION ALL SELECT 'PICKUP_UPDATE', 'Update Pickups', 'PICKUP', 'pickup', 'UPDATE', 163, NULL
  UNION ALL SELECT 'PICKUP_SEARCH', 'Search Pickups', 'PICKUP', 'pickup', 'SEARCH', 165, NULL
  UNION ALL SELECT 'PICKUP_ASSIGN', 'Assign Pickups', 'PICKUP', 'pickup', 'ASSIGN', 173, NULL
  UNION ALL SELECT 'PICKUP_PRINT', 'Print Pickups', 'PICKUP', 'pickup', 'PRINT', 170, NULL
  UNION ALL SELECT 'DELIVERY_CREATE', 'Create Deliveries', 'DELIVERY', 'delivery', 'CREATE', 171, NULL
  UNION ALL SELECT 'DELIVERY_READ', 'Read Deliveries', 'DELIVERY', 'delivery', 'READ', 172, NULL
  UNION ALL SELECT 'DELIVERY_UPDATE', 'Update Deliveries', 'DELIVERY', 'delivery', 'UPDATE', 173, NULL
  UNION ALL SELECT 'DELIVERY_SEARCH', 'Search Deliveries', 'DELIVERY', 'delivery', 'SEARCH', 175, NULL
  UNION ALL SELECT 'DELIVERY_ASSIGN', 'Assign Deliveries', 'DELIVERY', 'delivery', 'ASSIGN', 183, NULL
  UNION ALL SELECT 'DELIVERY_UPLOAD', 'Upload Deliveries', 'DELIVERY', 'delivery', 'UPLOAD', 181, NULL
  UNION ALL SELECT 'DRIVER_CREATE', 'Create Drivers', 'DRIVER', 'driver', 'CREATE', 181, NULL
  UNION ALL SELECT 'DRIVER_READ', 'Read Drivers', 'DRIVER', 'driver', 'READ', 182, NULL
  UNION ALL SELECT 'DRIVER_UPDATE', 'Update Drivers', 'DRIVER', 'driver', 'UPDATE', 183, NULL
  UNION ALL SELECT 'DRIVER_DELETE', 'Delete Drivers', 'DRIVER', 'driver', 'DELETE', 184, NULL
  UNION ALL SELECT 'DRIVER_SEARCH', 'Search Drivers', 'DRIVER', 'driver', 'SEARCH', 185, NULL
  UNION ALL SELECT 'DRIVER_EXPORT', 'Export Drivers', 'DRIVER', 'driver', 'EXPORT', 186, NULL
  UNION ALL SELECT 'DRIVER_ASSIGN', 'Assign Drivers', 'DRIVER', 'driver', 'ASSIGN', 193, NULL
  UNION ALL SELECT 'DRIVER_ACTIVATE', 'Activate Drivers', 'DRIVER', 'driver', 'ACTIVATE', 194, NULL
  UNION ALL SELECT 'DRIVER_DEACTIVATE', 'Deactivate Drivers', 'DRIVER', 'driver', 'DEACTIVATE', 195, NULL
  UNION ALL SELECT 'VEHICLE_CREATE', 'Create Vehicles', 'VEHICLE', 'vehicle', 'CREATE', 191, NULL
  UNION ALL SELECT 'VEHICLE_READ', 'Read Vehicles', 'VEHICLE', 'vehicle', 'READ', 192, NULL
  UNION ALL SELECT 'VEHICLE_UPDATE', 'Update Vehicles', 'VEHICLE', 'vehicle', 'UPDATE', 193, NULL
  UNION ALL SELECT 'VEHICLE_DELETE', 'Delete Vehicles', 'VEHICLE', 'vehicle', 'DELETE', 194, NULL
  UNION ALL SELECT 'VEHICLE_SEARCH', 'Search Vehicles', 'VEHICLE', 'vehicle', 'SEARCH', 195, NULL
  UNION ALL SELECT 'VEHICLE_EXPORT', 'Export Vehicles', 'VEHICLE', 'vehicle', 'EXPORT', 196, NULL
  UNION ALL SELECT 'VEHICLE_ASSIGN', 'Assign Vehicles', 'VEHICLE', 'vehicle', 'ASSIGN', 203, NULL
  UNION ALL SELECT 'VEHICLE_ACTIVATE', 'Activate Vehicles', 'VEHICLE', 'vehicle', 'ACTIVATE', 204, NULL
  UNION ALL SELECT 'VEHICLE_DEACTIVATE', 'Deactivate Vehicles', 'VEHICLE', 'vehicle', 'DEACTIVATE', 205, NULL
  UNION ALL SELECT 'VENDOR_CREATE', 'Create Vendors', 'VENDOR', 'vendor', 'CREATE', 201, NULL
  UNION ALL SELECT 'VENDOR_READ', 'Read Vendors', 'VENDOR', 'vendor', 'READ', 202, NULL
  UNION ALL SELECT 'VENDOR_UPDATE', 'Update Vendors', 'VENDOR', 'vendor', 'UPDATE', 203, NULL
  UNION ALL SELECT 'VENDOR_DELETE', 'Delete Vendors', 'VENDOR', 'vendor', 'DELETE', 204, NULL
  UNION ALL SELECT 'VENDOR_SEARCH', 'Search Vendors', 'VENDOR', 'vendor', 'SEARCH', 205, NULL
  UNION ALL SELECT 'VENDOR_EXPORT', 'Export Vendors', 'VENDOR', 'vendor', 'EXPORT', 206, NULL
  UNION ALL SELECT 'VENDOR_ACTIVATE', 'Activate Vendors', 'VENDOR', 'vendor', 'ACTIVATE', 214, NULL
  UNION ALL SELECT 'VENDOR_DEACTIVATE', 'Deactivate Vendors', 'VENDOR', 'vendor', 'DEACTIVATE', 215, NULL
  UNION ALL SELECT 'WALLET_READ', 'Read Wallet', 'WALLET', 'wallet', 'READ', 212, NULL
  UNION ALL SELECT 'WALLET_UPDATE', 'Update Wallet', 'WALLET', 'wallet', 'UPDATE', 213, NULL
  UNION ALL SELECT 'WALLET_SEARCH', 'Search Wallet', 'WALLET', 'wallet', 'SEARCH', 215, NULL
  UNION ALL SELECT 'WALLET_EXPORT', 'Export Wallet', 'WALLET', 'wallet', 'EXPORT', 216, NULL
  UNION ALL SELECT 'PAYMENT_CREATE', 'Create Payments', 'PAYMENT', 'payment', 'CREATE', 221, NULL
  UNION ALL SELECT 'PAYMENT_READ', 'Read Payments', 'PAYMENT', 'payment', 'READ', 222, NULL
  UNION ALL SELECT 'PAYMENT_UPDATE', 'Update Payments', 'PAYMENT', 'payment', 'UPDATE', 223, NULL
  UNION ALL SELECT 'PAYMENT_SEARCH', 'Search Payments', 'PAYMENT', 'payment', 'SEARCH', 225, NULL
  UNION ALL SELECT 'PAYMENT_EXPORT', 'Export Payments', 'PAYMENT', 'payment', 'EXPORT', 226, NULL
  UNION ALL SELECT 'PAYMENT_APPROVE', 'Approve Payments', 'PAYMENT', 'payment', 'APPROVE', 228, NULL
  UNION ALL SELECT 'PAYMENT_REJECT', 'Reject Payments', 'PAYMENT', 'payment', 'REJECT', 229, NULL
  UNION ALL SELECT 'PAYMENT_PRINT', 'Print Payments', 'PAYMENT', 'payment', 'PRINT', 230, NULL
  UNION ALL SELECT 'INVOICE_CREATE', 'Create Invoices', 'INVOICE', 'invoice', 'CREATE', 231, NULL
  UNION ALL SELECT 'INVOICE_READ', 'Read Invoices', 'INVOICE', 'invoice', 'READ', 232, NULL
  UNION ALL SELECT 'INVOICE_UPDATE', 'Update Invoices', 'INVOICE', 'invoice', 'UPDATE', 233, NULL
  UNION ALL SELECT 'INVOICE_SEARCH', 'Search Invoices', 'INVOICE', 'invoice', 'SEARCH', 235, NULL
  UNION ALL SELECT 'INVOICE_EXPORT', 'Export Invoices', 'INVOICE', 'invoice', 'EXPORT', 236, NULL
  UNION ALL SELECT 'INVOICE_PRINT', 'Print Invoices', 'INVOICE', 'invoice', 'PRINT', 240, NULL
  UNION ALL SELECT 'INVOICE_DOWNLOAD', 'Download Invoices', 'INVOICE', 'invoice', 'DOWNLOAD', 242, NULL
  UNION ALL SELECT 'INVOICE_APPROVE', 'Approve Invoices', 'INVOICE', 'invoice', 'APPROVE', 238, NULL
  UNION ALL SELECT 'REPORT_READ', 'Read Reports', 'REPORT', 'report', 'READ', 242, NULL
  UNION ALL SELECT 'REPORT_SEARCH', 'Search Reports', 'REPORT', 'report', 'SEARCH', 245, NULL
  UNION ALL SELECT 'REPORT_EXPORT', 'Export Reports', 'REPORT', 'report', 'EXPORT', 246, NULL
  UNION ALL SELECT 'REPORT_DOWNLOAD', 'Download Reports', 'REPORT', 'report', 'DOWNLOAD', 252, NULL
  UNION ALL SELECT 'REPORT_PRINT', 'Print Reports', 'REPORT', 'report', 'PRINT', 250, NULL
  UNION ALL SELECT 'DASHBOARD_READ', 'Read Dashboard', 'DASHBOARD', 'dashboard', 'READ', 252, NULL
  UNION ALL SELECT 'DASHBOARD_EXPORT', 'Export Dashboard', 'DASHBOARD', 'dashboard', 'EXPORT', 256, NULL
  UNION ALL SELECT 'SETTINGS_READ', 'Read Settings', 'SETTINGS', 'settings', 'READ', 262, NULL
  UNION ALL SELECT 'SETTINGS_UPDATE', 'Update Settings', 'SETTINGS', 'settings', 'UPDATE', 263, NULL
  UNION ALL SELECT 'NOTIFICATION_CREATE', 'Create Notifications', 'NOTIFICATION', 'notification', 'CREATE', 271, NULL
  UNION ALL SELECT 'NOTIFICATION_READ', 'Read Notifications', 'NOTIFICATION', 'notification', 'READ', 272, NULL
  UNION ALL SELECT 'NOTIFICATION_UPDATE', 'Update Notifications', 'NOTIFICATION', 'notification', 'UPDATE', 273, NULL
  UNION ALL SELECT 'NOTIFICATION_DELETE', 'Delete Notifications', 'NOTIFICATION', 'notification', 'DELETE', 274, NULL
  UNION ALL SELECT 'NOTIFICATION_SEARCH', 'Search Notifications', 'NOTIFICATION', 'notification', 'SEARCH', 275, NULL
  UNION ALL SELECT 'AUDIT_READ', 'Read Audit', 'AUDIT', 'audit', 'READ', 282, NULL
  UNION ALL SELECT 'AUDIT_SEARCH', 'Search Audit', 'AUDIT', 'audit', 'SEARCH', 285, NULL
  UNION ALL SELECT 'AUDIT_EXPORT', 'Export Audit', 'AUDIT', 'audit', 'EXPORT', 286, NULL
) d;


-- --- role_permissions --------------------------------------------------------

CREATE TABLE role_permissions (
    id            BINARY(16) NOT NULL,
    company_id     BINARY(16) NOT NULL,

    role_id       BINARY(16) NOT NULL,
    permission_id BINARY(16) NOT NULL,

    -- Denormalised copy of permissions.permission_code. Every authorisation decision
    -- needs the code, not the id, so this turns "what may this role do" into one
    -- indexed read instead of a join to a platform-level table on the hot path. Safe
    -- because the code is immutable.
    permission_code VARCHAR(100) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permissions_role_permission (company_id, role_id, permission_id),
    KEY idx_role_permissions_role (company_id, role_id),
    KEY idx_role_permissions_permission (company_id, permission_id),

    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id) REFERENCES company_roles (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    -- RESTRICT, not CASCADE: a permission still granted anywhere must not be
    -- removable, and the service refuses that deletion with a readable message.
    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_id) REFERENCES permissions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Permissions granted to a company role';


-- --- carry existing grants across --------------------------------------------
--
-- The old constants were coarser than the new ones: X_MANAGE meant create, update
-- and delete, so one old row becomes three. VIEW becomes READ, and SHIPMENT_VIEW
-- additionally gains SEARCH because listing was implied by it.
--
-- Two old constants have no equivalent and are dropped deliberately:
--   * API_ACCESS  — API access is a plan feature enforced by rate limiting, not a
--                   right a role holds. No seeded role had it granted.
--   * SCAN_CREATE — becomes TRACKING_CREATE plus TRACKING_READ.

INSERT INTO role_permissions (id, company_id, role_id, permission_id, permission_code,
                              created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')),
       r.company_id,
       r.id,
       p.id,
       p.permission_code,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM company_role_permissions crp
         JOIN company_roles r ON r.id = crp.role_id
         JOIN (SELECT 'COMPANY_VIEW' AS old_code, 'COMPANY_READ' AS new_code
               UNION ALL SELECT 'COMPANY_UPDATE', 'COMPANY_UPDATE'
               UNION ALL SELECT 'SETTINGS_VIEW', 'SETTINGS_READ'
               UNION ALL SELECT 'SETTINGS_UPDATE', 'SETTINGS_UPDATE'
               UNION ALL SELECT 'USER_VIEW', 'USER_READ'
               UNION ALL SELECT 'USER_MANAGE', 'USER_CREATE'
               UNION ALL SELECT 'USER_MANAGE', 'USER_UPDATE'
               UNION ALL SELECT 'USER_MANAGE', 'USER_DELETE'
               UNION ALL SELECT 'ROLE_VIEW', 'ROLE_READ'
               UNION ALL SELECT 'ROLE_MANAGE', 'ROLE_CREATE'
               UNION ALL SELECT 'ROLE_MANAGE', 'ROLE_UPDATE'
               UNION ALL SELECT 'ROLE_MANAGE', 'ROLE_DELETE'
               UNION ALL SELECT 'BRANCH_VIEW', 'BRANCH_READ'
               UNION ALL SELECT 'BRANCH_MANAGE', 'BRANCH_CREATE'
               UNION ALL SELECT 'BRANCH_MANAGE', 'BRANCH_UPDATE'
               UNION ALL SELECT 'BRANCH_MANAGE', 'BRANCH_DELETE'
               UNION ALL SELECT 'HUB_VIEW', 'HUB_READ'
               UNION ALL SELECT 'HUB_MANAGE', 'HUB_CREATE'
               UNION ALL SELECT 'HUB_MANAGE', 'HUB_UPDATE'
               UNION ALL SELECT 'HUB_MANAGE', 'HUB_DELETE'
               UNION ALL SELECT 'CUSTOMER_VIEW', 'CUSTOMER_READ'
               UNION ALL SELECT 'CUSTOMER_MANAGE', 'CUSTOMER_CREATE'
               UNION ALL SELECT 'CUSTOMER_MANAGE', 'CUSTOMER_UPDATE'
               UNION ALL SELECT 'CUSTOMER_MANAGE', 'CUSTOMER_DELETE'
               UNION ALL SELECT 'DRIVER_VIEW', 'DRIVER_READ'
               UNION ALL SELECT 'DRIVER_MANAGE', 'DRIVER_CREATE'
               UNION ALL SELECT 'DRIVER_MANAGE', 'DRIVER_UPDATE'
               UNION ALL SELECT 'DRIVER_MANAGE', 'DRIVER_DELETE'
               UNION ALL SELECT 'VEHICLE_VIEW', 'VEHICLE_READ'
               UNION ALL SELECT 'VEHICLE_MANAGE', 'VEHICLE_CREATE'
               UNION ALL SELECT 'VEHICLE_MANAGE', 'VEHICLE_UPDATE'
               UNION ALL SELECT 'VEHICLE_MANAGE', 'VEHICLE_DELETE'
               UNION ALL SELECT 'SHIPMENT_VIEW', 'SHIPMENT_READ'
               UNION ALL SELECT 'SHIPMENT_VIEW', 'SHIPMENT_SEARCH'
               UNION ALL SELECT 'SHIPMENT_CREATE', 'SHIPMENT_CREATE'
               UNION ALL SELECT 'SHIPMENT_UPDATE', 'SHIPMENT_UPDATE'
               UNION ALL SELECT 'SHIPMENT_ASSIGN', 'SHIPMENT_ASSIGN'
               UNION ALL SELECT 'SHIPMENT_CANCEL', 'SHIPMENT_DELETE'
               UNION ALL SELECT 'SCAN_CREATE', 'TRACKING_CREATE'
               UNION ALL SELECT 'SCAN_CREATE', 'TRACKING_READ'
               UNION ALL SELECT 'RATE_VIEW', 'RATE_MASTER_READ'
               UNION ALL SELECT 'RATE_MANAGE', 'RATE_MASTER_CREATE'
               UNION ALL SELECT 'RATE_MANAGE', 'RATE_MASTER_UPDATE'
               UNION ALL SELECT 'RATE_MANAGE', 'RATE_MASTER_DELETE'
               UNION ALL SELECT 'REPORT_VIEW', 'REPORT_READ'
               UNION ALL SELECT 'REPORT_VIEW', 'DASHBOARD_READ'
               UNION ALL SELECT 'REPORT_EXPORT', 'REPORT_EXPORT'
               UNION ALL SELECT 'BULK_BOOKING', 'SHIPMENT_IMPORT'
              ) map ON map.old_code = crp.permission
         JOIN permissions p ON p.permission_code = map.new_code
WHERE r.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.role_id = r.id AND x.permission_id = p.id);


-- The element collection is now redundant. Its data lives in role_permissions.
DROP TABLE company_role_permissions;

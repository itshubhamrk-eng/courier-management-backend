-- =============================================================================
-- V88 — Hub Operations: exceptions, out-scan, and the five permission verbs
-- PermissionModule.HUB was seeded (V6) without.
--
-- A Hub is a Branch with branch_type = 'HUB' (V61) — no new hub table. Load Sheet
-- creation, vehicle/driver assignment and dispatch at a hub reuse Manifest as-is;
-- in-scan/receive reuses ShipmentService.inScan + the crossing module's arriveAt.
-- The two tables below are the genuinely new persistence Hub Operations needs.
--
-- Existing companies keep the HUB_MANAGER role they already have — same rule V13
-- established: back-filling role_permissions here would silently widen a role an
-- administrator may have deliberately trimmed. New companies pick up the wider
-- HUB_MANAGER grant automatically, because DefaultRoleCatalog derives it from the
-- catalogue rather than listing it a second time. Existing HUB_MANAGER holders can
-- be re-granted the new codes by a COMPANY_ADMIN through Permission Management.
--
-- Forward-only. V87 is the previous migration in the working tree.
-- =============================================================================

CREATE TABLE shipment_exceptions (
    id                  BINARY(16)   NOT NULL,
    company_id          BINARY(16)   NOT NULL,

    shipment_id         BINARY(16)   NOT NULL,
    -- The hub (a Branch row) the exception was raised at — no physical FK, same
    -- cross-module treatment every id on Shipment/Manifest already gets.
    hub_branch_id       BINARY(16)   NOT NULL,

    exception_type      VARCHAR(30)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'OPEN',

    remarks             VARCHAR(500) NULL,
    raised_by           BINARY(16)   NULL,
    raised_at           TIMESTAMP(6) NOT NULL,

    resolved_by         BINARY(16)   NULL,
    resolved_at         TIMESTAMP(6) NULL,
    resolution_remarks  VARCHAR(500) NULL,

    -- The best-effort support ticket this exception raised, if TicketCategory
    -- "Shipment Issue" existed and was active at the time — mirrors
    -- ShipmentServiceImpl.raiseShortageTicket exactly, never blocks on failure.
    ticket_id           BINARY(16)   NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_shipment_exceptions_shipment (company_id, shipment_id, status),
    KEY idx_shipment_exceptions_hub (company_id, hub_branch_id, status, raised_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Hub Operations: Missing/Damaged/Short/Wrong Destination/Misrouted/On Hold, never auto-delivers';

CREATE TABLE hub_out_scans (
    id             BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    manifest_id    BINARY(16)   NOT NULL,
    shipment_id    BINARY(16)   NOT NULL,
    hub_branch_id  BINARY(16)   NOT NULL,

    scanned_by     BINARY(16)   NULL,
    scanned_at     TIMESTAMP(6) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- The duplicate-scan guard is this constraint, not a service-layer check —
    -- structural, same style as uk_delivery_assignment_company_shipment.
    UNIQUE KEY uk_hub_out_scans_manifest_shipment (company_id, manifest_id, shipment_id),
    KEY idx_hub_out_scans_hub (company_id, hub_branch_id, scanned_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One row per shipment out-scanned at a hub before its Load Sheet may dispatch';

-- --- seed: the 5 new HUB permission rows -------------------------------------
-- Generated from DefaultPermissionCatalog exactly as V6/V11/V12/V13 were.
-- HUB module base display_order is 70 (PermissionModule.HUB); offsets match
-- PermissionAction's new IN_SCAN(25)/OUT_SCAN(26)/SORT(27)/EXCEPTION_MANAGE(28),
-- and the pre-existing DISPATCH(18).

INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, d.feature_flag, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'HUB_DISPATCH' AS code, 'Dispatch from Hub' AS name, 'HUB' AS module, 'hub' AS resource, 'DISPATCH' AS action, 88 AS display_order, NULL AS feature_flag
  UNION ALL SELECT 'HUB_IN_SCAN', 'Hub In Scan', 'HUB', 'hub', 'IN_SCAN', 95, NULL
  UNION ALL SELECT 'HUB_OUT_SCAN', 'Hub Out Scan', 'HUB', 'hub', 'OUT_SCAN', 96, NULL
  UNION ALL SELECT 'HUB_SORT', 'Hub Sorting', 'HUB', 'hub', 'SORT', 97, NULL
  UNION ALL SELECT 'HUB_EXCEPTION_MANAGE', 'Manage Hub Exceptions', 'HUB', 'hub', 'EXCEPTION_MANAGE', 98, NULL
) AS d;

-- --- menu: Hub Operations section, between Shipment Management (35) and Finance (40)

SET @hub_ops = UNHEX(REPLACE(UUID(), '-', ''));
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES (@hub_ops, NULL, 'hub-operations', 'Hub Operations', 'hub', NULL, NULL,
        37, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
VALUES
  (UNHEX(REPLACE(UUID(), '-', '')), @hub_ops, 'hub-dashboard', 'Dashboard', 'dashboard', '/hub-operations/dashboard', 'HUB',
   1, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @hub_ops, 'hub-in-scan', 'In Scan', 'move_to_inbox', '/hub-operations/in-scan', 'HUB',
   2, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @hub_ops, 'hub-shipments', 'Shipments At Hub', 'inventory_2', '/hub-operations/shipments', 'HUB',
   3, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @hub_ops, 'hub-sorting', 'Sorting', 'sort', '/hub-operations/sorting', 'HUB',
   4, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @hub_ops, 'hub-load-sheet', 'Load Sheets', 'qr_code_scanner', '/hub-operations/load-sheet', 'MANIFEST',
   5, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @hub_ops, 'hub-out-scan', 'Out Scan', 'outbox', '/hub-operations/out-scan', 'HUB',
   6, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @hub_ops, 'hub-dispatch', 'Dispatch', 'outbound', '/hub-operations/dispatch', 'MANIFEST',
   7, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @hub_ops, 'hub-exceptions', 'Exceptions', 'report_problem', '/hub-operations/exceptions', 'HUB',
   8, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0),
  (UNHEX(REPLACE(UUID(), '-', '')), @hub_ops, 'hub-reports', 'Reports', 'summarize', '/hub-operations/reports', 'REPORT',
   9, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0);

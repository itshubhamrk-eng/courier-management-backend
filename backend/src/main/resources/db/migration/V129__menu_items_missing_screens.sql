-- User Permissions tree: list every company-side screen that already has a permission
-- module behind it. The tree is built from menu_items alone, and the sidebar (navigation.config.ts)
-- grew past it, so admins could not tick these screens for a user. Platform-only screens
-- (Platform, Companies, Subscription Plans, Platform Operators) are deliberately left out.
-- Codes match the frontend nav node ids. Idempotent: a code that already exists is skipped.

-- New grouping nodes: MSRTC, Follow-ups, Ticket Support, Communication Center.
-- MSRTC is a new grouping node; its leaves reuse SHIPMENT / MANIFEST like the sidebar does.
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), NULL, 'msrtc', 'MSRTC', 'directions_bus', NULL, NULL,
       52, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM menu_items x WHERE x.code = 'msrtc');

INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), NULL, g.code, g.title, g.icon, NULL, NULL,
       g.ord, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
    SELECT 'follow-up' AS code, 'Follow-ups' AS title, 'event_repeat' AS icon, 64 AS ord
    UNION ALL SELECT 'support', 'Ticket Support', 'support_agent', 65
    UNION ALL SELECT 'communication', 'Communication Center', 'forum', 66
) g
WHERE NOT EXISTS (SELECT 1 FROM menu_items x WHERE x.code = g.code);

INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), p.id, v.code, v.title, v.icon, v.route, v.module,
       v.ord, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
    SELECT 'administration' AS parent, 'permission-assign' AS code, 'Assign Permissions' AS title, 'rule' AS icon, '/permissions/assign' AS route, 'PERMISSION' AS module, 101 AS ord
    UNION ALL SELECT 'administration', 'user-permissions', 'User Permissions', 'checklist', '/permissions/users', 'MENU', 102
    UNION ALL SELECT 'administration', 'hubs', 'Hubs', 'hub', '/hubs', 'HUB', 103
    UNION ALL SELECT 'administration', 'activity-log', 'Activity Log', 'history', '/activity-logs', 'AUDIT', 104
    UNION ALL SELECT 'administration', 'user-activity', 'User Activity', 'person_search', '/activity-logs/users', 'AUDIT', 105

    UNION ALL SELECT 'rate-master', 'rate-calculator', 'Calculator', 'calculate', '/rates/calculator', 'RATE_MASTER', 101

    UNION ALL SELECT 'masters', 'payment-modes', 'Payment Mode', 'payments', '/masters/payment-modes', 'MASTER_DATA', 101
    UNION ALL SELECT 'masters', 'weight-slabs', 'Weight Slab', 'scale', '/masters/weight-slabs', 'MASTER_DATA', 102

    UNION ALL SELECT 'operations', 'out-for-delivery', 'Generate DRS', 'directions_run', '/movement/out-for-delivery', 'DELIVERY', 101

    UNION ALL SELECT 'msrtc', 'msrtc-booking', 'MSRTC Shipment Booking', 'add_box', '/msrtc/shipments/new', 'SHIPMENT', 1
    UNION ALL SELECT 'msrtc', 'msrtc-create-manifest', 'Create Manifest', 'qr_code_scanner', '/msrtc/create-manifest', 'MANIFEST', 2
    UNION ALL SELECT 'msrtc', 'msrtc-finalize-manifest', 'Finalize Manifest', 'outbound', '/msrtc/finalize-manifest', 'MANIFEST', 3
    UNION ALL SELECT 'msrtc', 'msrtc-in-scan', 'In Scan Manifest', 'move_to_inbox', '/msrtc/in-scan', 'MANIFEST', 4

    UNION ALL SELECT 'finance', 'hub-wallet', 'Hub Wallet', 'savings', '/finance/hub-wallet', 'WALLET', 101
    UNION ALL SELECT 'finance', 'wallet-transactions', 'Wallet Transactions', 'receipt', '/finance/branch-wallet/transactions', 'WALLET', 102
    UNION ALL SELECT 'finance', 'topup-requests', 'Top-up Requests', 'fact_check', '/finance/branch-wallet/topup-requests', 'WALLET', 103
    UNION ALL SELECT 'finance', 'wallet-recharge-report', 'Recharge Report', 'account_balance_wallet', '/finance/branch-wallet/recharge-report', 'WALLET', 104

    UNION ALL SELECT 'reports', 'commission-report', 'Commission Report', 'payments', '/reports/commissions', 'REPORT', 101
    UNION ALL SELECT 'reports', 'drs-report', 'DRS Report', 'directions_run', '/reports/drs', 'REPORT', 102
    UNION ALL SELECT 'reports', 'thc-report', 'Trip Hire Challan Report', 'outbound', '/reports/thc', 'REPORT', 103
    UNION ALL SELECT 'reports', 'branch-reports', 'Branch Reports', 'bar_chart', '/reports/branches', 'REPORT', 104
    UNION ALL SELECT 'reports', 'finance-reports', 'Finance Reports', 'analytics', '/reports/finance', 'REPORT', 105
    UNION ALL SELECT 'reports', 'vendor-audit-report', 'Vendor Audit Report', 'fact_check', '/reports/vendor-audit', 'REPORT', 106
    UNION ALL SELECT 'reports', 'customer-report', 'Customer Report', 'groups', '/reports/customers', 'REPORT', 107
    UNION ALL SELECT 'reports', 'exception-report', 'Shipment Exceptions', 'report_problem', '/reports/exceptions', 'REPORT', 108
    UNION ALL SELECT 'reports', 'cancelled-orders-report', 'Cancelled Orders', 'cancel', '/reports/cancelled-orders', 'REPORT', 109
    UNION ALL SELECT 'reports', 'bulk-tracking-report', 'Bulk Shipment Tracking', 'checklist', '/reports/bulk-tracking', 'REPORT', 110

    -- Screens with no permission module of their own: mapped to the closest existing module
    -- so each can be ticked per user (no new module / catalogue rows).
    UNION ALL SELECT 'masters', 'vehicles', 'Vehicles', 'local_shipping', '/masters/vehicles', 'VEHICLE', 103
    UNION ALL SELECT 'masters', 'branch-pincode-mapping', 'Pincode Branch Mapping', 'pin_drop', '/masters/pincode-branch-mapping', 'PINCODE', 104
    UNION ALL SELECT 'masters', 'branch-hub-assignment', 'Assign Hub to Branch', 'hub', '/masters/branch-hub-assignment', 'HUB', 105
    UNION ALL SELECT 'rate-master', 'freight-factor', 'Freight Factor', 'grid_on', '/freight-factors', 'RATE_MASTER', 102
    UNION ALL SELECT 'rate-master', 'district-level-freight', 'District Level Freight', 'map', '/district-level-freight', 'RATE_MASTER', 103
    UNION ALL SELECT 'rate-master', 'address-distance', 'Address Distance', 'social_distance', '/distances', 'RATE_MASTER', 104
    UNION ALL SELECT 'operations', 'pod-review', 'POD Review', 'fact_check', '/movement/pod-review', 'DELIVERY', 102
    UNION ALL SELECT 'operations', 'pod-company-upload', 'Upload POD (Company)', 'cloud_upload', '/movement/pod-company-upload', 'DELIVERY', 103
    UNION ALL SELECT 'operations', 'pod-bulk-upload', 'Bulk POD Upload', 'upload_file', '/movement/pod-bulk-upload', 'DELIVERY', 104
    UNION ALL SELECT 'msrtc', 'msrtc-divisions', 'Divisions', 'account_tree', '/msrtc/divisions', 'MASTER_DATA', 5
    UNION ALL SELECT 'msrtc', 'msrtc-bus-depots', 'Bus Depot / Station', 'warehouse', '/msrtc/bus-depots', 'MASTER_DATA', 6
    UNION ALL SELECT 'msrtc', 'msrtc-timetable', 'Bus Timetable', 'schedule', '/msrtc/timetable', 'MASTER_DATA', 7
    UNION ALL SELECT 'msrtc', 'msrtc-crossings', 'Crossings', 'alt_route', '/msrtc/crossings', 'MANIFEST', 8
    UNION ALL SELECT 'pdp-management', 'pdp-my-rates', 'My Rates', 'request_quote', '/pdp/my-rates', 'PDP_RATE', 101
    UNION ALL SELECT 'pdp-management', 'pdp-deliveries', 'Partner Deliveries', 'moving', '/pdp/deliveries', 'PDP', 102
    UNION ALL SELECT 'follow-up', 'follow-up-list', 'Follow-ups', 'checklist', '/follow-ups', 'CUSTOMER', 1
    UNION ALL SELECT 'follow-up', 'follow-up-new', 'Create Follow-up', 'add_box', '/follow-ups/new', 'CUSTOMER', 2
    UNION ALL SELECT 'support', 'support-tickets', 'Tickets', 'confirmation_number', '/support/tickets', 'NOTIFICATION', 1
    UNION ALL SELECT 'support', 'support-new-ticket', 'Raise Ticket', 'add_box', '/support/tickets/new', 'NOTIFICATION', 2
    UNION ALL SELECT 'support', 'support-dashboard', 'Support Dashboard', 'dashboard', '/support/dashboard', 'NOTIFICATION', 3
    UNION ALL SELECT 'support', 'support-sla-rules', 'SLA Rules', 'schedule', '/support/sla-rules', 'NOTIFICATION', 4
    UNION ALL SELECT 'communication', 'communication-dashboard', 'Dashboard', 'dashboard', '/communication/dashboard', 'NOTIFICATION', 1
    UNION ALL SELECT 'communication', 'communication-settings', 'Channel Settings', 'tune', '/communication/settings', 'NOTIFICATION', 2
    UNION ALL SELECT 'communication', 'communication-templates', 'Templates', 'description', '/communication/templates', 'NOTIFICATION', 3
    UNION ALL SELECT 'communication', 'communication-logs', 'Logs', 'history', '/communication/logs', 'NOTIFICATION', 4
) v
JOIN menu_items p ON p.code = v.parent AND p.deleted = FALSE
WHERE NOT EXISTS (SELECT 1 FROM menu_items x WHERE x.code = v.code);

-- Charges is a top-level leaf in the sidebar (no parent group).
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), NULL, 'charges', 'Charges', 'toll', '/charges', 'CHARGE',
       29, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM menu_items x WHERE x.code = 'charges');

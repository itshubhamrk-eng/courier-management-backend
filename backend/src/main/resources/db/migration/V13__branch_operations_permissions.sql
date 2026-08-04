-- V13 — the eight permission rows Branch operations were missing.
--
-- DefaultPermissionCatalog already carried these: MANIFEST_ASSIGN / MANIFEST_DISPATCH /
-- MANIFEST_RECEIVE (a manifest's life: load a vehicle onto it, send it, take the inbound
-- one in), DELIVERY_DISPATCH / DELIVERY_DELIVER (out for delivery, then closed against
-- the consignee), WALLET_RECHARGE (a branch topping up its own wallet), and MENU_READ /
-- MENU_ASSIGN (a manager staffing a branch decides which screens its staff can reach).
-- DefaultRoleCatalog's BRANCH_MANAGER, BOOKING_OPERATOR, DELIVERY_OPERATOR and ACCOUNTS
-- definitions already reference every one of them — the catalogue and the seeded roles
-- were written together, but this migration, the tripwire in DefaultPermissionCatalogTest
-- and the MENU module's Flyway row were not, and the test caught it: catalogue size 219
-- against a migration total of 211.
--
-- Generated from DefaultPermissionCatalog exactly as V6, V11 and V12 were; the catalogue's
-- total moves 211 -> 219 and DefaultPermissionCatalogTest asserts it.
--
-- Existing companies keep the roles they already have, following the rule V11 set: grants
-- are rows in role_permissions, and back-filling them here would silently widen a role an
-- administrator may have deliberately trimmed. New companies pick these up automatically,
-- because DefaultRoleCatalog derives each seeded role's permissions from the catalogue
-- rather than listing them a second time.

INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, d.feature_flag, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'MENU_READ' AS code, 'Read Menus' AS name, 'MENU' AS module, 'menu' AS resource, 'READ' AS action, 64 AS display_order, NULL AS feature_flag
  UNION ALL SELECT 'MENU_ASSIGN', 'Assign Menus', 'MENU', 'menu', 'ASSIGN', 65, NULL
  UNION ALL SELECT 'MANIFEST_ASSIGN', 'Assign Vehicle to Manifest', 'MANIFEST', 'manifest', 'ASSIGN', 157, NULL
  UNION ALL SELECT 'MANIFEST_DISPATCH', 'Dispatch Manifest', 'MANIFEST', 'manifest', 'DISPATCH', 158, NULL
  UNION ALL SELECT 'MANIFEST_RECEIVE', 'Receive Manifest', 'MANIFEST', 'manifest', 'RECEIVE', 159, NULL
  UNION ALL SELECT 'DELIVERY_DISPATCH', 'Out for Delivery', 'DELIVERY', 'delivery', 'DISPATCH', 189, NULL
  UNION ALL SELECT 'DELIVERY_DELIVER', 'Deliver Shipment', 'DELIVERY', 'delivery', 'DELIVER', 190, NULL
  UNION ALL SELECT 'WALLET_RECHARGE', 'Recharge Wallet', 'WALLET', 'wallet', 'RECHARGE', 217, NULL
) AS d;

-- Business Customer > Dashboard: gated on the existing CUSTOMER module (same as the Customer leaf),
-- listed first in the group for the User Permissions tree.
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), p.id, 'business-dashboard', 'Dashboard', 'dashboard',
       '/business-customer/dashboard', 'CUSTOMER', 0, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM menu_items p
WHERE p.code = 'business-customer' AND p.parent_id IS NULL AND p.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM menu_items x WHERE x.code = 'business-dashboard');

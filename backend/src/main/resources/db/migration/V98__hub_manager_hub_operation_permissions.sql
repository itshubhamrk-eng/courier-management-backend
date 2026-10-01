-- A company's HUB_MANAGER role seeded before the Hub Operations module (V88) can lack the hub
-- operation codes, most visibly HUB_DISPATCH: its hub users then get a Hub Operations menu with no
-- Dispatch entry. Grants the six hub-operation codes to every active HUB_MANAGER role missing them.
-- A grant that was deliberately removed (soft-deleted row) is left alone: the unique key sees the
-- row, so NOT EXISTS skips it. Never creates a role.
INSERT INTO role_permissions
    (id, company_id, role_id, permission_id, permission_code, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), r.company_id, r.id, p.id, p.permission_code, NOW(6), NOW(6), 0, 0
FROM company_roles r
JOIN permissions p ON p.permission_code IN
     ('HUB_READ', 'HUB_IN_SCAN', 'HUB_OUT_SCAN', 'HUB_SORT', 'HUB_DISPATCH', 'HUB_EXCEPTION_MANAGE')
    AND p.deleted = 0
WHERE r.role_code = 'HUB_MANAGER' AND r.deleted = 0 AND r.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.company_id = r.company_id AND x.role_id = r.id AND x.permission_id = p.id);

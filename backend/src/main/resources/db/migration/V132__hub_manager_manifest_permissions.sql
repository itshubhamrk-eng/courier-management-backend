-- A company's HUB_MANAGER role seeded before the catalogue gave it the manifest codes lacks
-- MANIFEST_CREATE/READ/UPDATE/DISPATCH. The Hub Dashboard and Load Sheet read manifests, so a hub
-- manager on such a role gets "Access denied" on GET /manifests and /hub-operations/dashboard even
-- though DefaultRoleCatalog grants them. Same shape as V98: grants the four codes to every active
-- HUB_MANAGER role missing them. A grant deliberately removed (soft-deleted row) is left alone: the
-- unique key sees the row, so NOT EXISTS skips it. Never creates a role.
INSERT INTO role_permissions
    (id, company_id, role_id, permission_id, permission_code, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), r.company_id, r.id, p.id, p.permission_code, NOW(6), NOW(6), 0, 0
FROM company_roles r
JOIN permissions p ON p.permission_code IN
     ('MANIFEST_CREATE', 'MANIFEST_READ', 'MANIFEST_UPDATE', 'MANIFEST_DISPATCH')
    AND p.deleted = 0
WHERE r.role_code = 'HUB_MANAGER' AND r.deleted = 0 AND r.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.company_id = r.company_id AND x.role_id = r.id AND x.permission_id = p.id);

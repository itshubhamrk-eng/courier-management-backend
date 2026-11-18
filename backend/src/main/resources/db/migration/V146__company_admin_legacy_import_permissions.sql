-- V145 added the LEGACY_IMPORT permission module. Grants its codes to every active COMPANY_ADMIN
-- role missing them (same pattern as V107/V98); a deliberately removed grant is left alone.
INSERT INTO role_permissions
    (id, company_id, role_id, permission_id, permission_code, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), r.company_id, r.id, p.id, p.permission_code, NOW(6), NOW(6), 0, 0
FROM company_roles r
JOIN permissions p ON p.module = 'LEGACY_IMPORT' AND p.deleted = 0
WHERE r.role_code = 'COMPANY_ADMIN' AND r.deleted = 0 AND r.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.company_id = r.company_id AND x.role_id = r.id AND x.permission_id = p.id);

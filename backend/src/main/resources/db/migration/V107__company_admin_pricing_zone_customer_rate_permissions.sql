-- V106 added the PRICING_ZONE and CUSTOMER_RATE permission modules. A company's COMPANY_ADMIN
-- role is seeded with explicit role_permissions rows when the company is created, and the login
-- JWT is built from those rows — so every company created before V106 lacks the 18 new codes and
-- its admin would see no Pricing Zone / Rate menu. Grants them to every active COMPANY_ADMIN role
-- missing them. A grant deliberately removed (soft-deleted row) is left alone: the unique key
-- sees the row, so NOT EXISTS skips it. Never creates a role. Same pattern as V98.
INSERT INTO role_permissions
    (id, company_id, role_id, permission_id, permission_code, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), r.company_id, r.id, p.id, p.permission_code, NOW(6), NOW(6), 0, 0
FROM company_roles r
JOIN permissions p ON p.module IN ('PRICING_ZONE', 'CUSTOMER_RATE') AND p.deleted = 0
WHERE r.role_code = 'COMPANY_ADMIN' AND r.deleted = 0 AND r.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.company_id = r.company_id AND x.role_id = r.id AND x.permission_id = p.id);

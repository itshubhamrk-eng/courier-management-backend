-- Users staffed at a HUB branch must hold the company's HUB_MANAGER role, or the Hub Operations
-- menu and routes (gated on it) never show. Hubs created before branch creation granted it hold
-- only BRANCH_MANAGER. Grants only where the company already has an active HUB_MANAGER role and
-- the user does not already hold it; never creates a role.
INSERT INTO user_company_roles
    (id, company_id, user_id, role_id, role_code, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), u.company_id, u.id, r.id, r.role_code, NOW(6), NOW(6), 0, 0
FROM users u
JOIN branches b ON b.id = u.branch_id AND b.branch_type = 'HUB' AND b.deleted = 0
JOIN company_roles r ON r.company_id = u.company_id AND r.role_code = 'HUB_MANAGER'
                    AND r.deleted = 0 AND r.status = 'ACTIVE'
WHERE u.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM user_company_roles x
                  WHERE x.company_id = u.company_id AND x.user_id = u.id AND x.role_id = r.id);

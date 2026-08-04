-- =============================================================================
-- V5 — Role Management (Phase 3, Company Administration)
--
-- Roles already exist: V4 created company_roles and seeded five per company. This
-- migration does NOT create a second roles table — two tables meaning "a role"
-- would drift within a release. It extends the existing one:
--
--   * role_type   what part of the business the role belongs to
--   * is_default  the role a new user receives when none is specified
--   * status      replaces the boolean is_active, so a third state can be added
--                 later without a second flag that contradicts the first
--
-- It then grows the seeded catalogue from five roles to eight, and backfills
-- every company that already exists.
--
-- Forward-only. V2 auth, V3 subscription, V4 company, V5 this.
-- =============================================================================

-- --- 1. new columns ----------------------------------------------------------

ALTER TABLE company_roles
    ADD COLUMN role_type  VARCHAR(20) NOT NULL DEFAULT 'OPERATIONS'
        COMMENT 'ADMINISTRATION | OPERATIONS | FINANCE | SUPPORT | READ_ONLY'
        AFTER description,
    ADD COLUMN is_default BOOLEAN     NOT NULL DEFAULT FALSE
        COMMENT 'Assigned to new users when none is specified. At most one per company.'
        AFTER system_role,
    ADD COLUMN status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE | INACTIVE'
        AFTER is_default;

-- Carry the old boolean across before dropping it. A role that was inactive must
-- not silently come back as ACTIVE.
UPDATE company_roles SET status = IF(is_active, 'ACTIVE', 'INACTIVE');

-- Type the five roles V4 seeded. Anything else (there should be nothing yet)
-- keeps the OPERATIONS default.
UPDATE company_roles SET role_type = 'ADMINISTRATION' WHERE role_code = 'COMPANY_ADMIN';
UPDATE company_roles SET role_type = 'READ_ONLY'      WHERE role_code = 'VIEWER';

-- --- 2. OPERATOR splits into BOOKING_OPERATOR and DELIVERY_OPERATOR ----------
--
-- Booking a parcel and delivering it are different desks with different rights;
-- one role covering both meant every counter clerk could also close deliveries.
-- The existing row is renamed rather than deleted and recreated, so the users
-- already holding it keep their assignment and their id stays stable.

UPDATE company_roles
SET role_code = 'BOOKING_OPERATOR',
    role_name = 'Booking Operator',
    description = 'Books shipments and registers customers at a counter.',
    is_default = TRUE
WHERE role_code = 'OPERATOR';

-- --- 3. drop the superseded flag --------------------------------------------

ALTER TABLE company_roles DROP COLUMN is_active;

-- --- 4. indexes --------------------------------------------------------------
-- idx_company_roles_company was (company_id, is_active); that column is gone.

DROP INDEX idx_company_roles_company ON company_roles;
CREATE INDEX idx_company_roles_company ON company_roles (company_id, status);
CREATE INDEX idx_company_roles_type ON company_roles (company_id, role_type);

-- --- 5. backfill the three new system roles for existing companies -----------
--
-- DELIVERY_OPERATOR, FINANCE_USER and CUSTOMER_SERVICE did not exist in V4. Every
-- company that was created before this migration needs them, or its admins would
-- see a catalogue that differs from a company created tomorrow.
--
-- UNHEX(REPLACE(UUID(),'-','')) produces the BINARY(16) primary key. These ids are
-- v4-random rather than the application's time-ordered v7, which costs a little
-- index locality on a handful of rows and is not worth a stored function.

INSERT INTO company_roles (id, company_id, role_code, role_name, description,
                           role_type, system_role, is_default, status,
                           created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')),
       c.company_id,
       d.role_code,
       d.role_name,
       d.description,
       d.role_type,
       TRUE,
       FALSE,
       'ACTIVE',
       UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6),
       FALSE,
       0
FROM companies c
         CROSS JOIN (SELECT 'DELIVERY_OPERATOR' AS role_code,
                            'Delivery Operator' AS role_name,
                            'Records pickup, transit and delivery scans on assigned shipments.' AS description,
                            'OPERATIONS' AS role_type
                     UNION ALL
                     SELECT 'FINANCE_USER',
                            'Finance User',
                            'Rates, invoicing and COD reconciliation. Reads operations, changes money.',
                            'FINANCE'
                     UNION ALL
                     SELECT 'CUSTOMER_SERVICE',
                            'Customer Service',
                            'Answers tracking queries and complaints; may cancel a booking on request.',
                            'SUPPORT') d
WHERE NOT EXISTS (SELECT 1
                  FROM company_roles r
                  WHERE r.company_id = c.company_id
                    AND r.role_code = d.role_code);

-- --- 6. permissions for the backfilled roles ---------------------------------
--
-- Kept in step with DefaultRoleCatalog. The two plan-gated permissions
-- (BULK_BOOKING, API_ACCESS) are deliberately absent from all three: none of these
-- roles is seeded with them, so no plan lookup is needed here.

INSERT INTO company_role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM company_roles r
         JOIN (SELECT 'DELIVERY_OPERATOR' AS role_code, 'COMPANY_VIEW' AS permission
               UNION ALL SELECT 'DELIVERY_OPERATOR', 'BRANCH_VIEW'
               UNION ALL SELECT 'DELIVERY_OPERATOR', 'CUSTOMER_VIEW'
               UNION ALL SELECT 'DELIVERY_OPERATOR', 'SHIPMENT_VIEW'
               UNION ALL SELECT 'DELIVERY_OPERATOR', 'SHIPMENT_UPDATE'
               UNION ALL SELECT 'DELIVERY_OPERATOR', 'SCAN_CREATE'

               UNION ALL SELECT 'FINANCE_USER', 'COMPANY_VIEW'
               UNION ALL SELECT 'FINANCE_USER', 'SETTINGS_VIEW'
               UNION ALL SELECT 'FINANCE_USER', 'BRANCH_VIEW'
               UNION ALL SELECT 'FINANCE_USER', 'HUB_VIEW'
               UNION ALL SELECT 'FINANCE_USER', 'CUSTOMER_VIEW'
               UNION ALL SELECT 'FINANCE_USER', 'SHIPMENT_VIEW'
               UNION ALL SELECT 'FINANCE_USER', 'RATE_VIEW'
               UNION ALL SELECT 'FINANCE_USER', 'RATE_MANAGE'
               UNION ALL SELECT 'FINANCE_USER', 'REPORT_VIEW'
               UNION ALL SELECT 'FINANCE_USER', 'REPORT_EXPORT'

               UNION ALL SELECT 'CUSTOMER_SERVICE', 'COMPANY_VIEW'
               UNION ALL SELECT 'CUSTOMER_SERVICE', 'BRANCH_VIEW'
               UNION ALL SELECT 'CUSTOMER_SERVICE', 'HUB_VIEW'
               UNION ALL SELECT 'CUSTOMER_SERVICE', 'CUSTOMER_VIEW'
               UNION ALL SELECT 'CUSTOMER_SERVICE', 'CUSTOMER_MANAGE'
               UNION ALL SELECT 'CUSTOMER_SERVICE', 'DRIVER_VIEW'
               UNION ALL SELECT 'CUSTOMER_SERVICE', 'SHIPMENT_VIEW'
               UNION ALL SELECT 'CUSTOMER_SERVICE', 'SHIPMENT_UPDATE'
               UNION ALL SELECT 'CUSTOMER_SERVICE', 'SHIPMENT_CANCEL'
               UNION ALL SELECT 'CUSTOMER_SERVICE', 'RATE_VIEW'
               UNION ALL SELECT 'CUSTOMER_SERVICE', 'REPORT_VIEW') p
              ON p.role_code = r.role_code
WHERE r.system_role = TRUE
  AND NOT EXISTS (SELECT 1
                  FROM company_role_permissions x
                  WHERE x.role_id = r.id
                    AND x.permission = p.permission);

-- A renamed OPERATOR keeps the permissions it had; BOOKING_OPERATOR additionally
-- gains BULK_BOOKING in the catalogue, but only where the company's plan allows it.
INSERT INTO company_role_permissions (role_id, permission)
SELECT r.id, 'BULK_BOOKING'
FROM company_roles r
         JOIN companies c ON c.company_id = r.company_id
         JOIN subscription_plans sp ON sp.id = c.subscription_plan_id
WHERE r.role_code = 'BOOKING_OPERATOR'
  AND JSON_EXTRACT(sp.feature_flags, '$.bulkBooking') = TRUE
  AND NOT EXISTS (SELECT 1
                  FROM company_role_permissions x
                  WHERE x.role_id = r.id
                    AND x.permission = 'BULK_BOOKING');

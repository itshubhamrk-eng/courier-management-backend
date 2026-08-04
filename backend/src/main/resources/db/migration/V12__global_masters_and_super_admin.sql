-- V12 — the geography masters become global, and the super admin gets its own rights.
--
-- Three unrelated-looking changes, one release:
--
--   1. The six geography lists (country, state, district, city, area, pincode) stop
--      being per-company. They are now one catalogue owned by the reserved platform id,
--      written only by a SUPER_ADMIN and read by anyone signed in.
--   2. TENANT_ADMIN is gone. A company is the single owner now, so two names for one
--      role could only drift; the rows that carried it become COMPANY_ADMIN.
--   3. Twenty-four new permission rows for the operations a super admin now has
--      endpoints for — company lifecycle, subscription lifecycle, global masters,
--      platform operators.
--
-- Why the geography tables keep their `company_id` column
-- ------------------------------------------------------
-- Because `(company_id, code)` is already unique, pinning every row to one owner makes
-- that key a *global* unique on code with no schema change at all — and it keeps the
-- Hibernate company filter switched on, so a code path that forgets to bind the platform
-- id returns nothing rather than everything. Dropping the column instead would need a
-- second entity hierarchy, and Java has one superclass: the shared head, repository,
-- specification and service that serve all twelve lists would have to be duplicated.
-- See `com.courier.modules.master.domain.GlobalMasters` for the full reasoning.

-- ---------------------------------------------------------------------------
-- 1. Merge the per-company geography into one catalogue.
-- ---------------------------------------------------------------------------
--
-- Order matters: parents before children, so a child is repointed at its parent's
-- survivor before that parent's duplicates are removed.
--
-- The survivor for a code is the oldest live row: `deleted ASC` first so a soft-deleted
-- row is never chosen over a live one carrying the same code, then `created_at`, then
-- `id` to break a tie deterministically. Losers are deleted outright rather than soft
-- deleted — they are duplicates being merged away, not records anyone withdrew, and a
-- soft-deleted loser would keep its code reserved and defeat the whole exercise.
--
-- The mapping tables are real tables, not TEMPORARY ones: MySQL cannot open a temporary
-- table twice in one statement, and every repoint below joins its map.

-- --- country ----------------------------------------------------------------

CREATE TABLE _v12_map_countries (
    old_id BINARY(16) NOT NULL PRIMARY KEY,
    new_id BINARY(16) NOT NULL,
    KEY idx_v12_map_countries_new (new_id)
) ENGINE = InnoDB;

INSERT INTO _v12_map_countries (old_id, new_id)
SELECT c.id,
       (SELECT s.id
          FROM master_countries s
         WHERE s.code = c.code
         ORDER BY s.deleted ASC, s.created_at ASC, s.id ASC
         LIMIT 1)
  FROM master_countries c;

UPDATE master_states st
  JOIN _v12_map_countries m ON st.country_id = m.old_id
   SET st.country_id = m.new_id;

DELETE FROM master_countries
 WHERE id IN (SELECT old_id FROM _v12_map_countries WHERE old_id <> new_id);

-- --- state ------------------------------------------------------------------

CREATE TABLE _v12_map_states (
    old_id BINARY(16) NOT NULL PRIMARY KEY,
    new_id BINARY(16) NOT NULL,
    KEY idx_v12_map_states_new (new_id)
) ENGINE = InnoDB;

INSERT INTO _v12_map_states (old_id, new_id)
SELECT c.id,
       (SELECT s.id
          FROM master_states s
         WHERE s.code = c.code
         ORDER BY s.deleted ASC, s.created_at ASC, s.id ASC
         LIMIT 1)
  FROM master_states c;

UPDATE master_districts d
  JOIN _v12_map_states m ON d.state_id = m.old_id
   SET d.state_id = m.new_id;

DELETE FROM master_states
 WHERE id IN (SELECT old_id FROM _v12_map_states WHERE old_id <> new_id);

-- --- district ---------------------------------------------------------------

CREATE TABLE _v12_map_districts (
    old_id BINARY(16) NOT NULL PRIMARY KEY,
    new_id BINARY(16) NOT NULL,
    KEY idx_v12_map_districts_new (new_id)
) ENGINE = InnoDB;

INSERT INTO _v12_map_districts (old_id, new_id)
SELECT c.id,
       (SELECT s.id
          FROM master_districts s
         WHERE s.code = c.code
         ORDER BY s.deleted ASC, s.created_at ASC, s.id ASC
         LIMIT 1)
  FROM master_districts c;

UPDATE master_cities ct
  JOIN _v12_map_districts m ON ct.district_id = m.old_id
   SET ct.district_id = m.new_id;

DELETE FROM master_districts
 WHERE id IN (SELECT old_id FROM _v12_map_districts WHERE old_id <> new_id);

-- --- city -------------------------------------------------------------------

CREATE TABLE _v12_map_cities (
    old_id BINARY(16) NOT NULL PRIMARY KEY,
    new_id BINARY(16) NOT NULL,
    KEY idx_v12_map_cities_new (new_id)
) ENGINE = InnoDB;

INSERT INTO _v12_map_cities (old_id, new_id)
SELECT c.id,
       (SELECT s.id
          FROM master_cities s
         WHERE s.code = c.code
         ORDER BY s.deleted ASC, s.created_at ASC, s.id ASC
         LIMIT 1)
  FROM master_cities c;

UPDATE master_areas a
  JOIN _v12_map_cities m ON a.city_id = m.old_id
   SET a.city_id = m.new_id;

DELETE FROM master_cities
 WHERE id IN (SELECT old_id FROM _v12_map_cities WHERE old_id <> new_id);

-- --- area -------------------------------------------------------------------

CREATE TABLE _v12_map_areas (
    old_id BINARY(16) NOT NULL PRIMARY KEY,
    new_id BINARY(16) NOT NULL,
    KEY idx_v12_map_areas_new (new_id)
) ENGINE = InnoDB;

INSERT INTO _v12_map_areas (old_id, new_id)
SELECT c.id,
       (SELECT s.id
          FROM master_areas s
         WHERE s.code = c.code
         ORDER BY s.deleted ASC, s.created_at ASC, s.id ASC
         LIMIT 1)
  FROM master_areas c;

UPDATE master_pincodes p
  JOIN _v12_map_areas m ON p.area_id = m.old_id
   SET p.area_id = m.new_id;

DELETE FROM master_areas
 WHERE id IN (SELECT old_id FROM _v12_map_areas WHERE old_id <> new_id);

-- --- pincode ----------------------------------------------------------------
-- The leaf: nothing points at it, so no map table is needed.

DELETE p FROM master_pincodes p
  JOIN (SELECT code,
               (SELECT s.id
                  FROM master_pincodes s
                 WHERE s.code = o.code
                 ORDER BY s.deleted ASC, s.created_at ASC, s.id ASC
                 LIMIT 1) AS keep_id
          FROM master_pincodes o
         GROUP BY o.code) k
    ON p.code = k.code AND p.id <> k.keep_id;

-- ---------------------------------------------------------------------------
-- 2. Resolve name collisions the merge created.
-- ---------------------------------------------------------------------------
--
-- Two companies could have used different codes for the same name — MH and MAHA both
-- called "Maharashtra". After the merge both survive, and `uk_master_*_name` now spans
-- one owner, so they collide.
--
-- The loser is renamed, not deleted. A duplicate *code* is unambiguously the same place
-- recorded twice; a duplicate *name* under two codes may well be two different places
-- that someone named carelessly, and deleting one would take an operator's data with it.
-- Suffixing the code makes both visible and lets a super admin merge them by hand.

UPDATE master_countries c
  JOIN (SELECT name, MIN(code) AS keep_code
          FROM master_countries GROUP BY name HAVING COUNT(*) > 1) d
    ON c.name = d.name AND c.code <> d.keep_code
   SET c.name = CONCAT(LEFT(c.name, 140), ' (', c.code, ')');

UPDATE master_states s
  JOIN (SELECT country_id, name, MIN(code) AS keep_code
          FROM master_states GROUP BY country_id, name HAVING COUNT(*) > 1) d
    ON s.country_id = d.country_id AND s.name = d.name AND s.code <> d.keep_code
   SET s.name = CONCAT(LEFT(s.name, 140), ' (', s.code, ')');

UPDATE master_districts t
  JOIN (SELECT state_id, name, MIN(code) AS keep_code
          FROM master_districts GROUP BY state_id, name HAVING COUNT(*) > 1) d
    ON t.state_id = d.state_id AND t.name = d.name AND t.code <> d.keep_code
   SET t.name = CONCAT(LEFT(t.name, 140), ' (', t.code, ')');

UPDATE master_cities c
  JOIN (SELECT district_id, name, MIN(code) AS keep_code
          FROM master_cities GROUP BY district_id, name HAVING COUNT(*) > 1) d
    ON c.district_id = d.district_id AND c.name = d.name AND c.code <> d.keep_code
   SET c.name = CONCAT(LEFT(c.name, 140), ' (', c.code, ')');

UPDATE master_areas a
  JOIN (SELECT city_id, name, MIN(code) AS keep_code
          FROM master_areas GROUP BY city_id, name HAVING COUNT(*) > 1) d
    ON a.city_id = d.city_id AND a.name = d.name AND a.code <> d.keep_code
   SET a.name = CONCAT(LEFT(a.name, 140), ' (', a.code, ')');

-- ---------------------------------------------------------------------------
-- 3. Hand the survivors to the platform.
-- ---------------------------------------------------------------------------
--
-- 00000000-0000-0000-0000-000000000001 — deliberately not a valid time-ordered UUID, so
-- it can never collide with a generated companyId and is recognisable on sight in a row
-- nobody expected. Mirrored by GlobalMasters.PLATFORM_COMPANY_ID.

UPDATE master_countries SET company_id = UNHEX('00000000000000000000000000000001');
UPDATE master_states    SET company_id = UNHEX('00000000000000000000000000000001');
UPDATE master_districts SET company_id = UNHEX('00000000000000000000000000000001');
UPDATE master_cities    SET company_id = UNHEX('00000000000000000000000000000001');
UPDATE master_areas     SET company_id = UNHEX('00000000000000000000000000000001');
UPDATE master_pincodes  SET company_id = UNHEX('00000000000000000000000000000001');

DROP TABLE _v12_map_countries;
DROP TABLE _v12_map_states;
DROP TABLE _v12_map_districts;
DROP TABLE _v12_map_cities;
DROP TABLE _v12_map_areas;

-- ---------------------------------------------------------------------------
-- 4. TENANT_ADMIN becomes COMPANY_ADMIN.
-- ---------------------------------------------------------------------------
--
-- The constant is gone from `Roles` and from the `Role` enum, so a row still carrying it
-- would fail to deserialise the next time its owner signed in. Rewritten rather than
-- deleted: the holder is a company administrator and always was — only the word changed.
--
-- INSERT IGNORE first, in case a user somehow held both spellings, then remove the old.

INSERT IGNORE INTO user_roles (user_id, role)
SELECT user_id, 'COMPANY_ADMIN' FROM user_roles WHERE role = 'TENANT_ADMIN';

DELETE FROM user_roles WHERE role = 'TENANT_ADMIN';

-- ---------------------------------------------------------------------------
-- 5. Permissions for what a super admin can now actually do.
-- ---------------------------------------------------------------------------
--
-- Twenty-four rows: the company lifecycle a super admin now has endpoints for, the three
-- commercial subscription acts, the shared geography catalogue, and platform operators.
-- The catalogue moves 187 -> 211.
--
-- Generated from DefaultPermissionCatalog, and DefaultPermissionCatalogTest asserts the
-- total matches this file, so the two cannot drift.
--
-- Existing companies are deliberately *not* back-filled with any of these grants. Every
-- one is a platform-operator right that no company role should hold — the opposite of
-- the usual "new module, widen COMPANY_ADMIN" reflex. New companies do not pick them up
-- either: DefaultRoleCatalog derives COMPANY_ADMIN's set from the catalogue, so the
-- exclusion lives there rather than here.

INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, d.feature_flag, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'COMPANY_CREATE' AS code, 'Create Company' AS name, 'COMPANY' AS module, 'company' AS resource, 'CREATE' AS action, 21 AS display_order, NULL AS feature_flag
  UNION ALL SELECT 'COMPANY_DELETE', 'Delete Company', 'COMPANY', 'company', 'DELETE', 24, NULL
  UNION ALL SELECT 'COMPANY_SEARCH', 'Search Company', 'COMPANY', 'company', 'SEARCH', 25, NULL
  UNION ALL SELECT 'COMPANY_EXPORT', 'Export Company', 'COMPANY', 'company', 'EXPORT', 26, NULL
  UNION ALL SELECT 'COMPANY_ACTIVATE', 'Activate Company', 'COMPANY', 'company', 'ACTIVATE', 34, NULL
  UNION ALL SELECT 'COMPANY_DEACTIVATE', 'Deactivate Company', 'COMPANY', 'company', 'DEACTIVATE', 35, NULL
  UNION ALL SELECT 'COMPANY_SUSPEND', 'Suspend Company', 'COMPANY', 'company', 'SUSPEND', 37, NULL
  UNION ALL SELECT 'SUBSCRIPTION_READ', 'Read Subscription', 'SUBSCRIPTION', 'subscription', 'READ', 24, NULL
  UNION ALL SELECT 'SUBSCRIPTION_SEARCH', 'Search Subscription', 'SUBSCRIPTION', 'subscription', 'SEARCH', 27, NULL
  UNION ALL SELECT 'SUBSCRIPTION_ASSIGN', 'Assign Subscription', 'SUBSCRIPTION', 'subscription', 'ASSIGN', 35, NULL
  UNION ALL SELECT 'SUBSCRIPTION_RENEW', 'Renew Subscription', 'SUBSCRIPTION', 'subscription', 'RENEW', 38, NULL
  UNION ALL SELECT 'SUBSCRIPTION_SUSPEND', 'Suspend Subscription', 'SUBSCRIPTION', 'subscription', 'SUSPEND', 39, NULL
  UNION ALL SELECT 'SUPER_ADMIN_USER_CREATE', 'Create Platform Operators', 'SUPER_ADMIN_USER', 'super-admin-user', 'CREATE', 25, NULL
  UNION ALL SELECT 'SUPER_ADMIN_USER_READ', 'Read Platform Operators', 'SUPER_ADMIN_USER', 'super-admin-user', 'READ', 26, NULL
  UNION ALL SELECT 'SUPER_ADMIN_USER_SEARCH', 'Search Platform Operators', 'SUPER_ADMIN_USER', 'super-admin-user', 'SEARCH', 29, NULL
  UNION ALL SELECT 'GLOBAL_MASTER_CREATE', 'Create Global Masters', 'GLOBAL_MASTER', 'global-master', 'CREATE', 108, NULL
  UNION ALL SELECT 'GLOBAL_MASTER_READ', 'Read Global Masters', 'GLOBAL_MASTER', 'global-master', 'READ', 109, NULL
  UNION ALL SELECT 'GLOBAL_MASTER_UPDATE', 'Update Global Masters', 'GLOBAL_MASTER', 'global-master', 'UPDATE', 110, NULL
  UNION ALL SELECT 'GLOBAL_MASTER_DELETE', 'Delete Global Masters', 'GLOBAL_MASTER', 'global-master', 'DELETE', 111, NULL
  UNION ALL SELECT 'GLOBAL_MASTER_SEARCH', 'Search Global Masters', 'GLOBAL_MASTER', 'global-master', 'SEARCH', 112, NULL
  UNION ALL SELECT 'GLOBAL_MASTER_EXPORT', 'Export Global Masters', 'GLOBAL_MASTER', 'global-master', 'EXPORT', 113, NULL
  UNION ALL SELECT 'GLOBAL_MASTER_IMPORT', 'Import Global Masters', 'GLOBAL_MASTER', 'global-master', 'IMPORT', 114, NULL
  UNION ALL SELECT 'GLOBAL_MASTER_ACTIVATE', 'Activate Global Masters', 'GLOBAL_MASTER', 'global-master', 'ACTIVATE', 121, NULL
  UNION ALL SELECT 'GLOBAL_MASTER_DEACTIVATE', 'Deactivate Global Masters', 'GLOBAL_MASTER', 'global-master', 'DEACTIVATE', 122, NULL
) d;

-- =============================================================================
-- V7 — User Management (Phase 3, Company Administration)
--
-- Users already exist: `users` was created in V2 for authentication. This migration
-- EXTENDS that table with the HR / profile fields the company context needs, rather
-- than adding a second user table that would drift from the login source of truth.
-- The `users` table is now a shared kernel — auth authenticates the person,
-- modules/company administers them, each with its own JPA entity over the same row.
--
-- It also adds `user_company_roles`: the many-to-many between a user and the
-- company's own permissioned roles (`company_roles`). This is NOT `user_roles` —
-- that table stays as auth's element collection of the JWT-authority Role enum.
--
-- Forward-only. V2 auth, V3 subscription, V4 company, V5 roles, V6 permissions, V7 this.
--
-- NOT DONE HERE: the FK users.company_id -> companies.company_id (still blocked by the
-- hand-inserted ops@acme.test row) and FKs for branch_id / hub_id (those modules do
-- not exist yet). reporting_manager_id is likewise left without a self-FK for now;
-- the service validates it points at a real user of the same company.
-- =============================================================================

ALTER TABLE users
    -- identity
    ADD COLUMN employee_code        VARCHAR(50)  NULL AFTER company_id,
    ADD COLUMN employee_id          VARCHAR(50)  NULL AFTER employee_code,
    -- name (first_name / last_name already exist)
    ADD COLUMN middle_name          VARCHAR(100) NULL AFTER first_name,
    ADD COLUMN display_name         VARCHAR(150) NULL AFTER last_name,
    -- contact (email / phone already exist; mobile is the HR-facing number)
    ADD COLUMN username             VARCHAR(100) NULL AFTER email,
    ADD COLUMN mobile               VARCHAR(20)  NULL AFTER username,
    ADD COLUMN alternate_mobile     VARCHAR(20)  NULL AFTER mobile,
    -- HR profile
    ADD COLUMN gender               VARCHAR(20)  NOT NULL DEFAULT 'UNSPECIFIED',
    ADD COLUMN date_of_birth        DATE         NULL,
    ADD COLUMN designation          VARCHAR(100) NULL,
    ADD COLUMN department           VARCHAR(100) NULL,
    ADD COLUMN joining_date         DATE         NULL,
    ADD COLUMN reporting_manager_id BINARY(16)   NULL,
    ADD COLUMN profile_image        VARCHAR(500) NULL,
    ADD COLUMN remarks              VARCHAR(500) NULL,
    -- placement — single columns, since a user belongs to at most one branch/hub
    ADD COLUMN branch_id            BINARY(16)   NULL,
    ADD COLUMN hub_id               BINARY(16)   NULL,
    -- admin hard-lock, distinct from the automatic failed-login lock (locked_until)
    ADD COLUMN is_locked            BOOLEAN      NOT NULL DEFAULT FALSE;

-- Username is GLOBALLY unique (the spec says "Username must be unique", without the
-- per-company qualifier it puts on email). MySQL allows repeated NULLs in a unique
-- index, so users without a username coexist.
ALTER TABLE users
    ADD CONSTRAINT uk_users_username UNIQUE (username),
    -- Employee code is unique per company: two companies may each have EMP001.
    ADD CONSTRAINT uk_users_company_employee_code UNIQUE (company_id, employee_code);

CREATE INDEX idx_users_company_branch ON users (company_id, branch_id);
CREATE INDEX idx_users_company_hub ON users (company_id, hub_id);


-- --- user_company_roles: user <-> company_roles many-to-many --------------------

CREATE TABLE user_company_roles (
    id          BINARY(16) NOT NULL,
    company_id   BINARY(16) NOT NULL,

    user_id     BINARY(16) NOT NULL,
    role_id     BINARY(16) NOT NULL,

    -- Denormalised from company_roles.role_code so a user's role list reads without a
    -- join. The code is immutable, so the copy cannot go stale.
    role_code   VARCHAR(50) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_company_roles_user_role (company_id, user_id, role_id),
    KEY idx_user_company_roles_user (company_id, user_id),
    KEY idx_user_company_roles_role (company_id, role_id),

    CONSTRAINT fk_user_company_roles_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    -- RESTRICT: roles are soft-deleted, so the row persists and this never fires; it is
    -- here to reject a hard delete of a role that is still assigned.
    CONSTRAINT fk_user_company_roles_role
        FOREIGN KEY (role_id) REFERENCES company_roles (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Company roles assigned to a user';

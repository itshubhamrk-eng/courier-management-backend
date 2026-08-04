-- =============================================================================
-- V2 — Authentication
--
-- NOTE ON ORDERING: auth was built before the company module, so it takes V2 and
-- the company module takes V3. There is therefore NO foreign key from
-- users.company_id to companies.id yet — that table does not exist. Phase 1 adds it.
-- Recorded in MEMORY/modules/auth.md so the two do not collide.
--
-- Every table here is company-owned: company_id is NOT NULL and leads every
-- composite index and unique constraint, per MEMORY/adr/ADR-001.md.
-- =============================================================================

CREATE TABLE users (
    id                  BINARY(16)   NOT NULL,
    company_id           BINARY(16)   NOT NULL,

    email               VARCHAR(255) NOT NULL,
    -- BCrypt output is 60 chars; 100 leaves room to migrate to argon2id later
    -- without a schema change.
    password_hash       VARCHAR(100) NOT NULL,

    first_name          VARCHAR(100) NULL,
    last_name           VARCHAR(100) NULL,
    phone               VARCHAR(30)  NULL,

    status              VARCHAR(20)  NOT NULL,

    email_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at   TIMESTAMP(6) NULL,

    -- Drives the optional expiry policy (app.auth.password.max-age, off by default).
    password_changed_at TIMESTAMP(6) NULL,

    last_login_at       TIMESTAMP(6) NULL,
    last_login_ip       VARCHAR(45)  NULL,

    -- Consecutive failures. Reset on any successful sign-in.
    failed_attempts     INT          NOT NULL DEFAULT 0,
    -- Null when unlocked. A past value means the lock has lapsed, which is what
    -- makes the lock self-clearing without a scheduled job.
    locked_until        TIMESTAMP(6) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- Per company, never global: the same person may hold accounts at two couriers.
    UNIQUE KEY uk_users_company_email (company_id, email),
    KEY idx_users_company_status (company_id, status),
    -- Supports the login lookup before the company filter narrows it.
    KEY idx_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Company-scoped user accounts';


-- Roles as an element collection. No company_id: isolation comes from the owning
-- user row, which is company-filtered, and the FK cascades on delete.
CREATE TABLE user_roles (
    user_id BINARY(16)  NOT NULL,
    role    VARCHAR(30) NOT NULL,

    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Roles held by a user';


CREATE TABLE refresh_tokens (
    id             BINARY(16)  NOT NULL,
    company_id      BINARY(16)  NOT NULL,

    user_id        BINARY(16)  NOT NULL,
    session_id     BINARY(16)  NOT NULL,
    -- Every token descended from one login shares this. A replay revokes the family.
    family_id      BINARY(16)  NOT NULL,

    jti            VARCHAR(64) NOT NULL,
    -- SHA-256 hex of the raw token. The token itself is never stored, so a dump of
    -- this table yields nothing usable.
    token_hash     VARCHAR(64) NOT NULL,

    expires_at     TIMESTAMP(6) NOT NULL,
    revoked_at     TIMESTAMP(6) NULL,
    revoked_reason VARCHAR(60)  NULL,
    -- Set on rotation, so a later replay can be traced to its successor.
    replaced_by_jti VARCHAR(64) NULL,

    ip_address     VARCHAR(45)  NULL,
    user_agent     VARCHAR(512) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- The refresh lookup. Unique because a hash collision would let one token
    -- redeem another.
    UNIQUE KEY idx_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user (company_id, user_id, revoked_at),
    KEY idx_refresh_tokens_family (family_id),
    KEY idx_refresh_tokens_expiry (expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Issued refresh tokens, stored as hashes';


CREATE TABLE user_sessions (
    id             BINARY(16)   NOT NULL,
    company_id      BINARY(16)   NOT NULL,

    user_id        BINARY(16)   NOT NULL,

    -- Client-supplied and unverified: display only, never a trust boundary.
    device_id      VARCHAR(128) NULL,
    device_name    VARCHAR(150) NULL,
    device_type    VARCHAR(30)  NULL,

    ip_address     VARCHAR(45)  NULL,
    user_agent     VARCHAR(512) NULL,

    -- Bumped on every refresh; drives least-recently-used eviction at the cap.
    last_seen_at   TIMESTAMP(6) NOT NULL,
    expires_at     TIMESTAMP(6) NOT NULL,
    revoked_at     TIMESTAMP(6) NULL,
    revoked_reason VARCHAR(60)  NULL,
    remember_me    BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_user_sessions_user (company_id, user_id, revoked_at),
    KEY idx_user_sessions_expiry (expires_at),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One row per logged-in device';


CREATE TABLE login_history (
    id              BINARY(16)   NOT NULL,
    company_id       BINARY(16)   NOT NULL,

    -- Null when the attempted email matched no account. The email is still stored
    -- so repeated probing of a non-existent address is visible.
    user_id         BINARY(16)   NULL,
    attempted_email VARCHAR(255) NOT NULL,

    success         BOOLEAN      NOT NULL,
    failure_reason  VARCHAR(40)  NULL,
    session_id      BINARY(16)   NULL,

    ip_address      VARCHAR(45)  NULL,
    user_agent      VARCHAR(512) NULL,
    occurred_at     TIMESTAMP(6) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- The throttle query runs on every login attempt; this index is what keeps it
    -- from becoming a table scan as the table grows.
    KEY idx_login_history_email_time (company_id, attempted_email, occurred_at),
    KEY idx_login_history_user_time (company_id, user_id, occurred_at)
    -- No FK on user_id: failed attempts for unknown emails legitimately have none.
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Every sign-in attempt, successful or not';


CREATE TABLE password_reset_tokens (
    id          BINARY(16)   NOT NULL,
    company_id   BINARY(16)   NOT NULL,

    user_id     BINARY(16)   NOT NULL,
    -- SHA-256 hex of 32 random bytes. Looked up across companies because the link is
    -- followed by an unauthenticated browser — see the repository javadoc.
    token_hash  VARCHAR(64)  NOT NULL,

    expires_at  TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    ip_address  VARCHAR(45)  NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY idx_reset_tokens_hash (token_hash),
    KEY idx_reset_tokens_user (company_id, user_id),
    CONSTRAINT fk_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Single-use password reset tokens';


CREATE TABLE email_verification_tokens (
    id          BINARY(16)   NOT NULL,
    company_id   BINARY(16)   NOT NULL,

    user_id     BINARY(16)   NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,

    expires_at  TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    ip_address  VARCHAR(45)  NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY idx_verification_tokens_hash (token_hash),
    KEY idx_verification_tokens_user (company_id, user_id),
    CONSTRAINT fk_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Single-use email verification tokens';

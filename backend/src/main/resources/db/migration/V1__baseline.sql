-- =============================================================================
-- V1 — Baseline
--
-- Foundation only. Business tables (companies, users, branches, shipments) arrive
-- with their own modules; see MEMORY/BACKLOG.md for the order.
--
-- Conventions enforced from here on (MEMORY/ARCHITECTURE.md §4):
--   * Primary keys are BINARY(16) UUIDv7 — 16 bytes instead of 36, and the
--     time-ordered prefix keeps InnoDB inserts at the right edge of the index.
--   * snake_case, plural table names.
--   * Every table carries created_at/by, updated_at/by, deleted/deleted_at/by,
--     and version.
--   * Every company-owned table gets company_id BINARY(16) NOT NULL, and it is the
--     LEADING column of every composite index and unique constraint.
-- =============================================================================

CREATE TABLE audit_logs (
    id            BINARY(16)   NOT NULL,

    action        VARCHAR(60)  NOT NULL,

    -- Nullable on purpose: the trail must also capture events that happen outside
    -- a company binding — failed logins, company creation itself, platform-admin work.
    company_id     BINARY(16)   NULL,

    -- Nullable for anonymous events such as a rejected login attempt.
    actor_id      BINARY(16)   NULL,
    actor_email   VARCHAR(255) NULL,

    entity_type   VARCHAR(100) NULL,
    entity_id     BINARY(16)   NULL,

    -- Free-form context. Must never contain credentials, tokens or full PII.
    details       JSON         NULL,

    -- IPv6 needs 45 characters.
    ip_address    VARCHAR(45)  NULL,
    user_agent    VARCHAR(512) NULL,

    -- Joins this row to the application log lines for the same request.
    request_id    VARCHAR(64)  NULL,

    occurred_at   TIMESTAMP(6) NOT NULL,
    success       BOOLEAN      NOT NULL DEFAULT TRUE,

    -- BaseEntity columns. Audit rows are append-only; the soft-delete columns
    -- exist only so every table has an identical shape.
    created_at    TIMESTAMP(6) NOT NULL,
    created_by    BINARY(16)   NULL,
    updated_at    TIMESTAMP(6) NOT NULL,
    updated_by    BINARY(16)   NULL,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at    TIMESTAMP(6) NULL,
    deleted_by    BINARY(16)   NULL,
    version       BIGINT       NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    -- "What happened in this company recently" — the default admin view.
    KEY idx_audit_logs_company_time (company_id, occurred_at),
    -- "What did this user do" — incident investigation.
    KEY idx_audit_logs_actor (actor_id, occurred_at),
    -- "History of this record" — shown on a shipment/branch detail page.
    KEY idx_audit_logs_entity (entity_type, entity_id),
    -- "All failed logins in the last hour" — security monitoring.
    KEY idx_audit_logs_action (action, occurred_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Append-only trail of security and business events';

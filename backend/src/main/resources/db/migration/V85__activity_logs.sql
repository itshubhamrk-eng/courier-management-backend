-- =============================================================================
-- V85 — User Activity & Audit Logging: the automatic activity trail.
--
-- A sibling of `audit_logs` (V1), not a replacement. `audit_logs` is the curated,
-- hand-called-out security/business trail 60+ services already write to. This table
-- is the automatic, request-shaped complement: ActivityLoggingFilter writes one row
-- per authenticated API call with no controller changes required anywhere, carrying
-- fields audit_logs has no room for — separate old/new value JSON, module/submodule,
-- request method + endpoint, status + error message.
--
-- Not company-filtered at the entity level (extends BaseEntity, like AuditLog) —
-- company isolation for reads is enforced explicitly in ActivityLogService, and
-- company_id stays nullable for platform-tier activity with no company binding.
--
-- Forward-only. V84 is the previous migration.
-- =============================================================================

CREATE TABLE activity_logs (
    id             BINARY(16)    NOT NULL,
    company_id     BINARY(16)    NULL,
    user_id        BINARY(16)    NULL,
    username       VARCHAR(255)  NULL,

    module         VARCHAR(100)  NULL,
    submodule      VARCHAR(100)  NULL,
    action         VARCHAR(60)   NOT NULL,
    entity_type    VARCHAR(100)  NULL,
    entity_id      VARCHAR(100)  NULL,
    description    VARCHAR(1000) NULL,

    old_value      JSON          NULL,
    new_value      JSON          NULL,

    ip_address     VARCHAR(45)   NULL,
    device         VARCHAR(30)   NULL,
    browser        VARCHAR(60)   NULL,
    os             VARCHAR(60)   NULL,
    session_id     BINARY(16)    NULL,

    request_method VARCHAR(10)   NULL,
    api_endpoint   VARCHAR(255)  NULL,
    status         VARCHAR(20)   NOT NULL,
    error_message  VARCHAR(500)  NULL,

    occurred_at    TIMESTAMP(6)  NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_activity_logs_company_time (company_id, occurred_at),
    KEY idx_activity_logs_user_time (user_id, occurred_at),
    KEY idx_activity_logs_module_time (module, occurred_at),
    KEY idx_activity_logs_action_time (action, occurred_at),
    KEY idx_activity_logs_entity (entity_type, entity_id),
    KEY idx_activity_logs_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Automatic, request-shaped activity trail — see AuditLog for the curated business/security trail';

-- =============================================================================
-- V139 — Security incident detection: the incident table and its permission module.
--
-- One row per detected incident (refresh-token reuse, foreign login with the right
-- password, ...). Not company-filtered at the entity level (extends BaseEntity, like
-- activity_logs) — company isolation for reads is explicit in the service, and
-- company_id / user_id stay nullable for incidents that cannot be tied to either
-- (e.g. a credential attack on an unknown username).
--
-- Permission module SECURITY_INCIDENT (READ, SEARCH, UPDATE = ban / dismiss / unban),
-- granted to every existing COMPANY_ADMIN role only.
--
-- Forward-only. V137 is the previous migration in this branch.
-- =============================================================================

CREATE TABLE security_incident (
    id              BINARY(16)   NOT NULL,
    company_id      BINARY(16)   NULL,
    user_id         BINARY(16)   NULL,
    rule_code       VARCHAR(40)  NOT NULL,
    -- HIGH | MEDIUM
    severity        VARCHAR(10)  NOT NULL,
    -- OPEN | BANNED | DISMISSED | UNBANNED
    status          VARCHAR(12)  NOT NULL,
    evidence        JSON         NULL,
    -- NONE | SESSIONS_REVOKED | USER_DISABLED
    auto_action     VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    detected_at     TIMESTAMP(6) NOT NULL,
    resolved_by     BINARY(16)   NULL,
    resolved_at     TIMESTAMP(6) NULL,
    resolution_note VARCHAR(500) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_security_incident_company_time (company_id, detected_at),
    KEY idx_security_incident_user_rule_status (user_id, rule_code, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Detected security incidents and what was done about them';

-- --- permission catalogue: SECURITY_INCIDENT (285) ---------------------------------------
INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, NULL, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'SECURITY_INCIDENT_READ' AS code, 'View Security Incident' AS name, 'SECURITY_INCIDENT' AS module, 'security-incidents' AS resource, 'READ' AS action, 2852 AS display_order
  UNION ALL SELECT 'SECURITY_INCIDENT_UPDATE', 'Resolve Security Incident', 'SECURITY_INCIDENT', 'security-incidents', 'UPDATE', 2853
  UNION ALL SELECT 'SECURITY_INCIDENT_SEARCH', 'Search Security Incidents', 'SECURITY_INCIDENT', 'security-incidents', 'SEARCH', 2855
) AS d;

-- The login JWT is built from role_permissions rows: grant the new codes to every existing
-- company's COMPANY_ADMIN role. Same pattern as V98 / V107 / V110.
INSERT INTO role_permissions
    (id, company_id, role_id, permission_id, permission_code, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), r.company_id, r.id, p.id, p.permission_code, NOW(6), NOW(6), 0, 0
FROM company_roles r
JOIN permissions p ON p.module = 'SECURITY_INCIDENT' AND p.deleted = 0
WHERE r.role_code = 'COMPANY_ADMIN' AND r.deleted = 0 AND r.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.company_id = r.company_id AND x.role_id = r.id AND x.permission_id = p.id);

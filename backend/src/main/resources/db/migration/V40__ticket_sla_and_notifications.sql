-- =============================================================================
-- V40 — Ticket Support Phase 2: SLA rules + in-app notifications
--
-- SLA rules are company-owned, keyed by priority (one rule per company per
-- priority tier — LOW/MEDIUM/HIGH/CRITICAL) rather than the full category x
-- priority x tenant x support-team matrix the spec describes: "support team"
-- doesn't exist as a concept in this codebase (Phase 1's own scoping decision),
-- and "tenant" is already the row's own company_id — priority is the dimension
-- that actually drives SLA in practice. A per-category override is a documented
-- gap, not an oversight.
--
-- Notifications are a plain company-owned table, generated synchronously by
-- TicketServiceImpl at the point of each action (assign/reply/status change/
-- etc.) plus a scheduled sweep (TicketSlaSweepJob) for the two that have no
-- single triggering action — SLA approaching / SLA breached.
-- =============================================================================

CREATE TABLE ticket_sla_rules (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    -- LOW | MEDIUM | HIGH | CRITICAL
    priority                 VARCHAR(20) NOT NULL,
    first_response_minutes   INT NOT NULL,
    resolution_minutes       INT NOT NULL,
    active                   BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_sla_rules_company_priority (company_id, priority)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ticket Support: company-configured SLA targets, one row per priority tier';

ALTER TABLE tickets
    ADD COLUMN sla_first_response_due_at TIMESTAMP(6) NULL AFTER first_response_at,
    ADD COLUMN sla_resolution_due_at     TIMESTAMP(6) NULL AFTER sla_first_response_due_at,
    ADD COLUMN sla_warning_notified      BOOLEAN NOT NULL DEFAULT FALSE AFTER sla_resolution_due_at,
    ADD COLUMN sla_breach_notified       BOOLEAN NOT NULL DEFAULT FALSE AFTER sla_warning_notified;

CREATE TABLE notifications (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    recipient_user_id BINARY(16) NOT NULL,
    -- TICKET_ASSIGNED | TICKET_REASSIGNED | TICKET_ESCALATED | NEW_REPLY |
    -- INTERNAL_UPDATE | STATUS_CHANGED | PRIORITY_CHANGED | TICKET_RESOLVED |
    -- TICKET_CLOSED | TICKET_REOPENED | SLA_APPROACHING | SLA_BREACHED
    type    VARCHAR(30) NOT NULL,
    title   VARCHAR(200) NOT NULL,
    message VARCHAR(500) NOT NULL,

    ticket_id BINARY(16) NULL,
    is_read   BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_notifications_recipient (company_id, recipient_user_id, created_at),
    KEY idx_notifications_recipient_unread (company_id, recipient_user_id, is_read),

    CONSTRAINT fk_notifications_ticket FOREIGN KEY (ticket_id)
        REFERENCES tickets (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ticket Support: in-app notification feed, one row per recipient per event';

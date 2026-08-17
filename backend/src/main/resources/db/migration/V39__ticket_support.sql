-- =============================================================================
-- V39 — Ticket Support module (Phase 1)
--
-- Full lifecycle (OPEN -> ASSIGNED -> IN_PROGRESS -> WAITING_FOR_USER/
-- WAITING_FOR_INTERNAL_TEAM -> RESOLVED -> CLOSED, with REOPENED), a conversation
-- thread (public replies + staff-only internal notes), attachments, and dedicated
-- status/assignment history tables for a queryable timeline UI (deliberately not the
-- generic free-text AuditLog, which still gets a parallel entry for every state change
-- for the platform-wide trail).
--
-- Categories/sub-categories are global (SUPER_ADMIN-managed), not company-owned — same
-- reasoning as any shared catalogue: one company's "Payment/Billing" is the same concept
-- as another's. Everything else here is company-owned (tickets, messages, attachments,
-- history), Hibernate-filtered like every other module.
--
-- No SLA columns, no notification tables — Phase 2, deferred on direct user decision
-- (no SLA/notification concept exists anywhere in this codebase yet).
-- =============================================================================

CREATE TABLE ticket_categories (
    id   BINARY(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_categories_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ticket Support: global category catalogue, SUPER_ADMIN-managed';

CREATE TABLE ticket_sub_categories (
    id          BINARY(16) NOT NULL,
    category_id BINARY(16) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_sub_categories_name (category_id, name),
    KEY idx_ticket_sub_categories_category (category_id),

    CONSTRAINT fk_ticket_sub_categories_category FOREIGN KEY (category_id)
        REFERENCES ticket_categories (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ticket Support: global sub-category catalogue, SUPER_ADMIN-managed';

-- Seed the spec's twelve top-level categories. No sub-categories seeded — SUPER_ADMIN
-- adds those as needed.
INSERT INTO ticket_categories (id, name, active, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), name, TRUE, NOW(6), NOW(6), FALSE, 0
FROM (
    SELECT 'Shipment Issue' AS name UNION ALL
    SELECT 'Pickup Issue' UNION ALL
    SELECT 'Delivery Issue' UNION ALL
    SELECT 'Tracking Issue' UNION ALL
    SELECT 'Payment/Billing' UNION ALL
    SELECT 'Wallet' UNION ALL
    SELECT 'Customer Issue' UNION ALL
    SELECT 'Branch/Hub Issue' UNION ALL
    SELECT 'User/Access Issue' UNION ALL
    SELECT 'Technical/System Issue' UNION ALL
    SELECT 'Integration Issue' UNION ALL
    SELECT 'Other'
) seed;

-- One row per company — the counter behind ticket numbers ("TKT-" + 6-digit serial),
-- same shape/contract as company_drs_sequences (native upsert only, never JPA).
CREATE TABLE company_ticket_sequences (
    company_id     BINARY(16) NOT NULL,
    sequence_value BIGINT NOT NULL,
    PRIMARY KEY (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ticket Support: per-company ticket-number counter';

CREATE TABLE tickets (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    ticket_number VARCHAR(20) NOT NULL,
    subject       VARCHAR(200) NOT NULL,
    description   TEXT NOT NULL,

    category_id     BINARY(16) NOT NULL,
    sub_category_id BINARY(16) NULL,

    priority VARCHAR(20) NOT NULL,
    status   VARCHAR(30) NOT NULL,

    -- No physical FK — cross-module references, same convention as
    -- shipments.booking_branch_id / crossing_details.
    related_shipment_id BINARY(16) NULL,
    related_customer_id BINARY(16) NULL,
    related_branch_id   BINARY(16) NULL,

    created_by_user_id BINARY(16) NOT NULL,
    assignee_user_id   BINARY(16) NULL,
    escalated          BOOLEAN NOT NULL DEFAULT FALSE,

    first_response_at TIMESTAMP(6) NULL,
    resolved_at        TIMESTAMP(6) NULL,
    closed_at          TIMESTAMP(6) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_tickets_company_number (company_id, ticket_number),
    KEY idx_tickets_company_status (company_id, status),
    KEY idx_tickets_company_priority (company_id, priority),
    KEY idx_tickets_company_assignee (company_id, assignee_user_id),
    KEY idx_tickets_company_created_by (company_id, created_by_user_id),
    KEY idx_tickets_company_branch (company_id, related_branch_id),
    KEY idx_tickets_related_shipment (related_shipment_id),
    KEY idx_tickets_created_at (company_id, created_at),

    CONSTRAINT fk_tickets_category FOREIGN KEY (category_id)
        REFERENCES ticket_categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_sub_category FOREIGN KEY (sub_category_id)
        REFERENCES ticket_sub_categories (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ticket Support: one support ticket';

CREATE TABLE ticket_messages (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    ticket_id        BINARY(16) NOT NULL,
    author_user_id   BINARY(16) NOT NULL,
    body             TEXT NOT NULL,
    is_internal_note BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_ticket_messages_ticket (company_id, ticket_id, created_at),

    CONSTRAINT fk_ticket_messages_ticket FOREIGN KEY (ticket_id)
        REFERENCES tickets (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ticket Support: conversation thread — public replies and staff-only internal notes';

CREATE TABLE ticket_attachments (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    ticket_id  BINARY(16) NOT NULL,
    -- NULL = attached at ticket creation, not to a specific reply.
    message_id BINARY(16) NULL,

    asset_url    VARCHAR(1000) NOT NULL,
    storage_key  VARCHAR(500) NOT NULL,
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NULL,
    size_bytes   BIGINT NOT NULL,
    uploaded_by_user_id BINARY(16) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_ticket_attachments_ticket (company_id, ticket_id),
    KEY idx_ticket_attachments_message (message_id),

    CONSTRAINT fk_ticket_attachments_ticket FOREIGN KEY (ticket_id)
        REFERENCES tickets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_attachments_message FOREIGN KEY (message_id)
        REFERENCES ticket_messages (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ticket Support: uploaded files, at creation or attached to a reply';

CREATE TABLE ticket_status_history (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    ticket_id       BINARY(16) NOT NULL,
    from_status     VARCHAR(30) NULL,
    to_status       VARCHAR(30) NOT NULL,
    changed_by_user_id BINARY(16) NOT NULL,
    remarks         VARCHAR(1000) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_ticket_status_history_ticket (company_id, ticket_id, created_at),

    CONSTRAINT fk_ticket_status_history_ticket FOREIGN KEY (ticket_id)
        REFERENCES tickets (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ticket Support: immutable status-change timeline, one row per transition';

CREATE TABLE ticket_assignment_history (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    ticket_id          BINARY(16) NOT NULL,
    assigned_to_user_id BINARY(16) NOT NULL,
    assigned_by_user_id BINARY(16) NOT NULL,
    -- ASSIGNED | REASSIGNED | ESCALATED
    action             VARCHAR(20) NOT NULL,
    remarks            VARCHAR(1000) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_ticket_assignment_history_ticket (company_id, ticket_id, created_at),

    CONSTRAINT fk_ticket_assignment_history_ticket FOREIGN KEY (ticket_id)
        REFERENCES tickets (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ticket Support: immutable assign/reassign/escalate timeline, one row per action';

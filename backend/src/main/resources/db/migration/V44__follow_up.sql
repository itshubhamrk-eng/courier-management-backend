-- =============================================================================
-- V44 — Follow-up Management module
--
-- A branch user's manual-action task list — distinct from Ticket Support (V39/V40):
-- a ticket is an external support issue with SLA/conversation/escalation, a follow-up
-- is an internal "call this customer back Thursday" reminder with a due date and a
-- reschedule flow. Deliberately not merged into Ticket — see MEMORY/modules/follow-up.md.
--
-- follow_up_history is one combined timeline table (creation/status-change/reschedule/
-- assignment/note), not the two separate history tables Ticket Support uses — the spec
-- calls for a single follow_up_history table.
--
-- notifications (V40) gains a nullable follow_up_id so this module reuses that same
-- in-app feed instead of building a second notification architecture.
-- =============================================================================

CREATE TABLE follow_up (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    branch_id BINARY(16) NOT NULL,

    -- CUSTOMER | SHIPMENT | DELIVERY | PAYMENT | EXCEPTION | GENERAL
    reference_type VARCHAR(20) NOT NULL,
    -- No physical FK — cross-module reference, same convention as
    -- tickets.related_shipment_id / shipments.booking_branch_id.
    reference_id   BINARY(16) NULL,
    customer_id    BINARY(16) NULL,
    shipment_id    BINARY(16) NULL,

    assigned_user_id BINARY(16) NULL,

    title       VARCHAR(200) NOT NULL,
    description TEXT NULL,

    follow_up_type VARCHAR(20) NOT NULL,
    -- LOW | MEDIUM | HIGH | URGENT
    priority       VARCHAR(20) NOT NULL,
    -- OPEN | IN_PROGRESS | COMPLETED | RESCHEDULED | CANCELLED
    status         VARCHAR(20) NOT NULL,

    due_date            TIMESTAMP(6) NOT NULL,
    next_follow_up_date TIMESTAMP(6) NULL,

    completed_at TIMESTAMP(6) NULL,
    completed_by BINARY(16) NULL,

    -- Idempotency flags for FollowUpSweepJob's own once-per-due-date notifications.
    overdue_notified   BOOLEAN NOT NULL DEFAULT FALSE,
    due_today_notified BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_follow_up_company_branch (company_id, branch_id),
    KEY idx_follow_up_company_status (company_id, status),
    KEY idx_follow_up_company_assignee (company_id, assigned_user_id),
    KEY idx_follow_up_company_due (company_id, due_date),
    KEY idx_follow_up_shipment (shipment_id),
    KEY idx_follow_up_customer (customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Follow-up Management: one branch operational follow-up task';

CREATE TABLE follow_up_history (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    follow_up_id BINARY(16) NOT NULL,
    -- CREATED | UPDATED | STATUS_CHANGED | RESCHEDULED | ASSIGNED | NOTE_ADDED
    action       VARCHAR(20) NOT NULL,
    from_status  VARCHAR(20) NULL,
    to_status    VARCHAR(20) NULL,

    previous_due_date TIMESTAMP(6) NULL,
    new_due_date      TIMESTAMP(6) NULL,
    assigned_to_user_id BINARY(16) NULL,
    note                VARCHAR(1000) NULL,

    -- Null for a system-raised entry (none exist yet — the sweep job only notifies,
    -- it never writes history).
    changed_by_user_id BINARY(16) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_follow_up_history_follow_up (company_id, follow_up_id, created_at),

    CONSTRAINT fk_follow_up_history_follow_up FOREIGN KEY (follow_up_id)
        REFERENCES follow_up (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Follow-up Management: immutable timeline — creation/status/reschedule/assignment/notes';

-- Reuse the existing in-app notification feed (V40) rather than build a second one.
ALTER TABLE notifications
    ADD COLUMN follow_up_id BINARY(16) NULL AFTER ticket_id;

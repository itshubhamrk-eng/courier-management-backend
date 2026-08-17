package com.courier.modules.support.domain;

/** What a {@link Notification} is about. Mirrors the spec's own event list, minus
 *  {@code TICKET_CREATED} (no single clear recipient without a company-admin
 *  directory lookup this module doesn't have — a documented gap, not an oversight). */
public enum NotificationType {
    TICKET_ASSIGNED,
    TICKET_REASSIGNED,
    TICKET_ESCALATED,
    NEW_REPLY,
    INTERNAL_UPDATE,
    STATUS_CHANGED,
    PRIORITY_CHANGED,
    TICKET_RESOLVED,
    TICKET_CLOSED,
    TICKET_REOPENED,
    SLA_APPROACHING,
    SLA_BREACHED
}

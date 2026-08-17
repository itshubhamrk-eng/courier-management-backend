package com.courier.modules.support.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * @param companyId       set by the service for every company-bound caller; null only for
 *                        a SUPER_ADMIN who did not narrow to one company
 * @param crossTenant     true only when the service has confirmed the caller is
 *                        SUPER_ADMIN and {@code companyId} is deliberately left open —
 *                        the one case where a null {@code companyId} means "every
 *                        company" instead of "fail closed, no rows"
 * @param search          matched against ticket number/subject (case-insensitive contains)
 * @param requesterOrAssignee non-admin scoping: when set, only tickets where this user is the
 *                            requester or the assignee are returned (branch scoping is layered
 *                            on top via {@code visibleBranchId})
 * @param visibleBranchId non-admin scoping: when set, also includes tickets raised for this branch
 */
public record TicketCriteria(
        UUID companyId,
        boolean crossTenant,
        TicketStatus status,
        TicketPriority priority,
        UUID categoryId,
        UUID subCategoryId,
        UUID relatedBranchId,
        UUID assigneeUserId,
        Instant createdFrom,
        Instant createdTo,
        String search,
        UUID requesterOrAssignee,
        UUID visibleBranchId
) {
    public static TicketCriteria none() {
        return new TicketCriteria(null, false, null, null, null, null, null, null, null, null, null, null, null);
    }

    public TicketCriteria scopedTo(UUID enforcedCompanyId) {
        return new TicketCriteria(enforcedCompanyId, false, status, priority, categoryId, subCategoryId,
                relatedBranchId, assigneeUserId, createdFrom, createdTo, search,
                requesterOrAssignee, visibleBranchId);
    }

    /** SUPER_ADMIN, no company named — every tenant, fails-open by design. */
    public TicketCriteria crossTenantScope() {
        return new TicketCriteria(companyId, true, status, priority, categoryId, subCategoryId,
                relatedBranchId, assigneeUserId, createdFrom, createdTo, search,
                requesterOrAssignee, visibleBranchId);
    }

    public TicketCriteria restrictedTo(UUID userId, UUID branchId) {
        return new TicketCriteria(companyId, crossTenant, status, priority, categoryId, subCategoryId,
                relatedBranchId, assigneeUserId, createdFrom, createdTo, search, userId, branchId);
    }
}

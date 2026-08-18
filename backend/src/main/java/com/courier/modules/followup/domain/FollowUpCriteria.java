package com.courier.modules.followup.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * @param companyId    set by the service for every caller — a follow-up has no genuinely
 *                      cross-tenant read (unlike Ticket Support's SUPER_ADMIN view), so
 *                      this is never null once the specification runs
 * @param overdue       true narrows to {@code dueDate < now} and non-terminal status
 * @param dueDate       narrows to follow-ups due on this calendar day (UTC)
 * @param search        matched against title (case-insensitive contains)
 * @param visibleBranchId non-admin scoping: restricts to this branch, or to being the
 *                        assignee/creator, layered on in {@link FollowUpSpecifications}
 */
public record FollowUpCriteria(
        UUID companyId,
        FollowUpStatus status,
        FollowUpPriority priority,
        FollowUpType type,
        UUID assignedUserId,
        Instant dueDate,
        boolean overdue,
        UUID customerId,
        UUID shipmentId,
        UUID branchId,
        String search,
        UUID visibleBranchId,
        UUID requesterOrAssignee
) {
    public static FollowUpCriteria none() {
        return new FollowUpCriteria(null, null, null, null, null, null, false, null, null, null, null, null, null);
    }

    public FollowUpCriteria scopedTo(UUID enforcedCompanyId) {
        return new FollowUpCriteria(enforcedCompanyId, status, priority, type, assignedUserId, dueDate, overdue,
                customerId, shipmentId, branchId, search, visibleBranchId, requesterOrAssignee);
    }

    public FollowUpCriteria restrictedTo(UUID userId, UUID branchId) {
        return new FollowUpCriteria(companyId, status, priority, type, assignedUserId, dueDate, overdue,
                customerId, shipmentId, this.branchId, search, branchId, userId);
    }
}

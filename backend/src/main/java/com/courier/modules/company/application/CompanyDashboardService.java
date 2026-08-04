package com.courier.modules.company.application;

import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyStatus;
import com.courier.modules.subscription.domain.SubscriptionPlan;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only views for the super admin console: one company's numbers, and the
 * platform's.
 *
 * <p>Separate from {@link CompanyService} on purpose. That service owns the company
 * lifecycle and every method on it changes or fetches one aggregate; this one crosses
 * into users, branches, roles and plans purely to count them. Folding the two together
 * would give the lifecycle service a dependency on four more repositories it has no
 * business writing to.
 *
 * <p><b>Every method requires {@code SUPER_ADMIN}</b>, enforced by a class-level
 * {@code @PreAuthorize} on the implementation.
 */
public interface CompanyDashboardService {

    /** Counts and subscription position for one company. */
    CompanyStatistics statisticsFor(UUID companyId);

    /** Platform-wide totals and the renewals worklist. */
    PlatformDashboard platformDashboard();

    /**
     * @param plan the company's current plan, for the quota comparison. Never null —
     *             a company always has one, and a statistics screen that silently
     *             omitted the ceilings would read as "unlimited".
     */
    record CompanyStatistics(Company company,
                             SubscriptionPlan plan,
                             Long daysToExpiry,
                             long userCount,
                             long activeUserCount,
                             long pendingUserCount,
                             long branchCount,
                             long activeBranchCount,
                             long roleCount) {
    }

    record PlatformDashboard(long companyCount,
                             Map<CompanyStatus, Long> companiesByStatus,
                             long expiredCount,
                             long expiringSoonCount,
                             long planCount,
                             long activePlanCount,
                             List<Renewal> upcomingRenewals) {
    }

    record Renewal(Company company, LocalDate endDate, long daysRemaining) {
    }
}

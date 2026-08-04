package com.courier.modules.company.application;

import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.company.domain.BranchStatus;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyCriteria;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.CompanySpecifications;
import com.courier.modules.company.domain.CompanyStatus;
import com.courier.modules.company.domain.CompanyUserRepository;
import com.courier.modules.company.domain.UserStatus;
import com.courier.modules.subscription.application.SubscriptionPlanService;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import com.courier.modules.subscription.domain.SubscriptionPlanCriteria;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The super admin console's read model.
 *
 * <p>Two things make this more than a pile of counts:
 *
 * <ul>
 *   <li><b>Company-owned counts run inside {@link CompanyContext#runAs}.</b> Users,
 *       branches and roles are company-owned, and a super admin's request carries no
 *       company, so the Hibernate filter would have nothing to apply — every count would
 *       silently be a platform-wide total presented as one company's. Binding the
 *       company explicitly is what makes the number mean what the screen says it does.</li>
 *   <li><b>Plan facts come from {@code SubscriptionPlanService}, never its repository.</b>
 *       The cross-feature rule: a feature may use another's application service, not its
 *       domain. A count therefore costs a one-row page rather than a {@code count(*)},
 *       which is the right price for keeping the boundary.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Roles.SUPER_ADMIN + "')")
public class CompanyDashboardServiceImpl implements CompanyDashboardService {

    private static final String ENTITY = "Company";

    /** How far ahead the renewals worklist looks. A month is one billing cycle. */
    private static final int RENEWAL_HORIZON_DAYS = 30;

    /** The worklist is a to-do list, not a report; an unbounded one is neither. */
    private static final int MAX_RENEWALS = 20;

    private final CompanyRepository companyRepository;
    private final CompanyUserRepository userRepository;
    private final BranchRepository branchRepository;
    private final CompanyRoleRepository roleRepository;
    private final SubscriptionPlanService subscriptionPlanService;

    @Override
    @Transactional(readOnly = true)
    public CompanyStatistics statisticsFor(UUID id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));

        SubscriptionPlan plan = subscriptionPlanService.getById(company.getSubscriptionPlanId());
        UUID owner = company.getCompanyId();

        // One binding for all six counts: switching per query would leave a window in
        // which two of them disagreed about which company was being counted.
        return CompanyContext.runAs(owner, () -> new CompanyStatistics(
                company,
                plan,
                daysToExpiry(company),
                userRepository.countByCompanyId(owner),
                userRepository.countByCompanyIdAndStatus(owner, UserStatus.ACTIVE),
                userRepository.countByCompanyIdAndStatus(owner, UserStatus.PENDING),
                branchRepository.countByCompanyId(owner),
                branchRepository.countByCompanyIdAndStatus(owner, BranchStatus.ACTIVE),
                roleRepository.countByCompanyId(owner)));
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformDashboard platformDashboard() {
        Map<CompanyStatus, Long> byStatus = new EnumMap<>(CompanyStatus.class);
        // Seed every status at zero. A client rendering a fixed row of tiles should not
        // have to invent the keys the grouped query happened not to return.
        for (CompanyStatus status : CompanyStatus.values()) {
            byStatus.put(status, 0L);
        }
        for (Object[] row : companyRepository.countGroupedByStatus()) {
            byStatus.put((CompanyStatus) row[0], (Long) row[1]);
        }

        long companyCount = byStatus.values().stream().mapToLong(Long::longValue).sum();

        LocalDate today = LocalDate.now();
        long expiredCount = companyRepository.countExpiringOnOrBefore(today);
        long expiringSoonCount =
                companyRepository.countExpiringOnOrBefore(today.plusDays(RENEWAL_HORIZON_DAYS));

        long planCount = subscriptionPlanService
                .search(SubscriptionPlanCriteria.none(), PageRequest.of(0, 1))
                .getTotalElements();
        long activePlanCount = subscriptionPlanService.listAssignable().size();

        return new PlatformDashboard(
                companyCount,
                byStatus,
                expiredCount,
                expiringSoonCount,
                planCount,
                activePlanCount,
                upcomingRenewals(today));
    }

    // -------------------------------------------------------------------- helpers

    /**
     * The renewals worklist: companies whose window ends within the horizon, plus those
     * already lapsed, soonest first.
     *
     * <p>Built from the existing {@code expiringBefore} search criterion rather than a
     * new query, so "which companies expire before X" has exactly one definition in the
     * system — the list screen and this tile can never drift apart.
     */
    private List<Renewal> upcomingRenewals(LocalDate today) {
        LocalDate horizon = today.plusDays(RENEWAL_HORIZON_DAYS);

        CompanyCriteria criteria = new CompanyCriteria(
                null, null, null, null, null, null, horizon, null, null, null);

        return companyRepository.findAll(CompanySpecifications.matching(criteria),
                        PageRequest.of(0, MAX_RENEWALS))
                .stream()
                .map(company -> {
                    LocalDate end = soonestEnd(company);
                    return new Renewal(company, end,
                            end == null ? 0L : ChronoUnit.DAYS.between(today, end));
                })
                .sorted(Comparator.comparing(Renewal::daysRemaining))
                .toList();
    }

    /**
     * Days until the company stops working — the sooner of the trial and subscription
     * ends, because that is the one that actually cuts them off. Negative once lapsed;
     * null when the company has neither date, which is a data problem worth seeing
     * rather than papering over with a zero.
     */
    private Long daysToExpiry(Company company) {
        LocalDate end = soonestEnd(company);
        return end == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), end);
    }

    private LocalDate soonestEnd(Company company) {
        LocalDate trial = company.getTrialEndDate();
        LocalDate subscription = company.getSubscriptionEndDate();
        if (trial == null) {
            return subscription;
        }
        if (subscription == null) {
            return trial;
        }
        return trial.isBefore(subscription) ? trial : subscription;
    }
}

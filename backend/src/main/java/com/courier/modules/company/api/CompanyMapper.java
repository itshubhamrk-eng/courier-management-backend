package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.AssignSubscriptionRequest;
import com.courier.modules.company.api.dto.CompanyResponse;
import com.courier.modules.company.api.dto.CompanySearchRequest;
import com.courier.modules.company.api.dto.CompanyStatisticsResponse;
import com.courier.modules.company.api.dto.CompanySummaryResponse;
import com.courier.modules.company.api.dto.CreateCompanyRequest;
import com.courier.modules.company.api.dto.PlatformDashboardResponse;
import com.courier.modules.company.api.dto.RenewSubscriptionRequest;
import com.courier.modules.company.api.dto.UpdateCompanyRequest;
import com.courier.modules.company.application.CompanyDashboardService;
import com.courier.modules.company.application.CompanyService;
import com.courier.modules.company.application.command.AssignSubscriptionCommand;
import com.courier.modules.company.application.command.CreateCompanyCommand;
import com.courier.modules.company.application.command.RenewSubscriptionCommand;
import com.courier.modules.company.application.command.UpdateCompanyCommand;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyCriteria;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import org.springframework.stereotype.Component;

/**
 * Translates between the wire contract and the application/domain types.
 *
 * <p>Hand-written: the project has no MapStruct dependency, and with flat records on
 * both sides a generated mapper would hide the one thing worth seeing — that
 * {@code companyId}, {@code status} and {@code companyCode} are never mapped *in* from a
 * request, because callers may not set them.
 *
 * <p>Lives in {@code api} because DTOs are the API's contract; application and domain
 * must not know they exist ({@code MEMORY/ARCHITECTURE.md} §1).
 */
@Component
public class CompanyMapper {

    public CreateCompanyCommand toCommand(CreateCompanyRequest request) {
        return new CreateCompanyCommand(
                request.companyCode(),
                request.companyName(),
                request.legalName(),
                request.displayName(),
                request.subscriptionPlanId(),
                request.email(),
                request.mobile(),
                request.alternateMobile(),
                request.website(),
                request.gstNumber(),
                request.panNumber(),
                request.cinNumber(),
                request.logo(),
                request.favicon(),
                request.addressLine1(),
                request.addressLine2(),
                request.country(),
                request.state(),
                request.city(),
                request.postalCode(),
                request.timezone(),
                request.currency(),
                request.language(),
                request.dateFormat(),
                request.timeFormat(),
                request.remarks(),
                request.subscriptionStartDate(),
                request.adminEmail(),
                request.adminFirstName(),
                request.adminLastName(),
                request.adminMobile());
    }

    public UpdateCompanyCommand toCommand(UpdateCompanyRequest request) {
        return new UpdateCompanyCommand(
                request.companyName(),
                request.legalName(),
                request.displayName(),
                request.subscriptionPlanId(),
                request.email(),
                request.mobile(),
                request.alternateMobile(),
                request.website(),
                request.gstNumber(),
                request.panNumber(),
                request.cinNumber(),
                request.logo(),
                request.favicon(),
                request.addressLine1(),
                request.addressLine2(),
                request.country(),
                request.state(),
                request.city(),
                request.postalCode(),
                request.timezone(),
                request.currency(),
                request.language(),
                request.dateFormat(),
                request.timeFormat(),
                request.remarks(),
                request.trialEndDate(),
                request.subscriptionStartDate(),
                request.subscriptionEndDate(),
                request.version());
    }

    public AssignSubscriptionCommand toCommand(AssignSubscriptionRequest request) {
        return new AssignSubscriptionCommand(
                request.subscriptionPlanId(),
                request.billingCycle(),
                request.periodsOrOne(),
                request.startDate(),
                request.endDate(),
                request.remarks());
    }

    public RenewSubscriptionCommand toCommand(RenewSubscriptionRequest request) {
        return new RenewSubscriptionCommand(
                request.subscriptionPlanId(),
                request.billingCycle(),
                request.periodsOrOne(),
                request.endDate(),
                request.remarks());
    }

    public CompanyStatisticsResponse toResponse(CompanyDashboardService.CompanyStatistics stats) {
        Company company = stats.company();
        SubscriptionPlan plan = stats.plan();
        return new CompanyStatisticsResponse(
                company.getId(),
                company.getCompanyId(),
                company.getCompanyCode(),
                company.getCompanyName(),
                company.getStatus(),
                plan.getId(),
                plan.getPlanCode(),
                plan.getPlanName(),
                company.getTrialEndDate(),
                company.getSubscriptionStartDate(),
                company.getSubscriptionEndDate(),
                stats.daysToExpiry(),
                stats.userCount(),
                stats.activeUserCount(),
                stats.pendingUserCount(),
                stats.branchCount(),
                stats.activeBranchCount(),
                stats.roleCount(),
                plan.getMaxUsers(),
                plan.getMaxBranches(),
                // The quota question a screen actually asks is "can they add one more",
                // which is what withinLimit answers — so it is negated here rather than
                // re-derived with a >= that would be off by one against every other
                // consumer of the same rule.
                !SubscriptionPlan.withinLimit(plan.getMaxUsers(), stats.userCount()),
                !SubscriptionPlan.withinLimit(plan.getMaxBranches(), stats.branchCount()));
    }

    public PlatformDashboardResponse toResponse(CompanyDashboardService.PlatformDashboard board) {
        return new PlatformDashboardResponse(
                board.companyCount(),
                board.companiesByStatus(),
                board.expiredCount(),
                board.expiringSoonCount(),
                board.planCount(),
                board.activePlanCount(),
                board.upcomingRenewals().stream()
                        .map(renewal -> new PlatformDashboardResponse.Renewal(
                                renewal.company().getId(),
                                renewal.company().getCompanyId(),
                                renewal.company().getCompanyCode(),
                                renewal.company().getCompanyName(),
                                renewal.company().getStatus(),
                                renewal.endDate(),
                                renewal.daysRemaining()))
                        .toList());
    }

    public CompanyCriteria toCriteria(CompanySearchRequest request) {
        CompanySearchRequest safe = request == null ? CompanySearchRequest.empty() : request;
        return new CompanyCriteria(
                safe.status(),
                safe.isActive(),
                safe.subscriptionPlanId(),
                safe.country(),
                safe.state(),
                safe.city(),
                safe.expiringBefore(),
                safe.createdFrom(),
                safe.createdTo(),
                safe.search());
    }

    /** Detail view. {@code provisioning} is null outside a creation response. */
    public CompanyResponse toResponse(Company company) {
        return toResponse(company, null);
    }

    public CompanyResponse toResponse(Company company,
                                      CompanyResponse.ProvisioningSummary provisioning) {
        return new CompanyResponse(
                company.getId(),
                company.getCompanyId(),
                company.getCompanyCode(),
                company.getCompanyName(),
                company.getLegalName(),
                company.getDisplayName(),
                company.getSubscriptionPlanId(),
                company.getStatus(),
                company.getTrialStartDate(),
                company.getTrialEndDate(),
                company.getSubscriptionStartDate(),
                company.getSubscriptionEndDate(),
                company.getEmail(),
                company.getMobile(),
                company.getAlternateMobile(),
                company.getWebsite(),
                company.getGstNumber(),
                company.getPanNumber(),
                company.getCinNumber(),
                company.getLogo(),
                company.getFavicon(),
                company.getAddressLine1(),
                company.getAddressLine2(),
                company.getCountry(),
                company.getState(),
                company.getCity(),
                company.getPostalCode(),
                company.getTimezone(),
                company.getCurrency(),
                company.getLanguage(),
                company.getDateFormat(),
                company.getTimeFormat(),
                company.isActive(),
                company.getRemarks(),
                company.getCreatedBy(),
                // The project's audit columns are created_at/updated_at; the API calls
                // them createdDate/updatedDate. Mapped here so neither name leaks.
                company.getCreatedAt(),
                company.getUpdatedBy(),
                company.getUpdatedAt(),
                company.getVersion(),
                provisioning);
    }

    public CompanyResponse toCreatedResponse(CompanyService.CreatedCompany created) {
        return toResponse(created.company(), new CompanyResponse.ProvisioningSummary(
                created.adminUserId(),
                created.adminEmail(),
                created.temporaryPassword(),
                created.activationEmailSent(),
                created.roleCount(),
                created.settingCount()));
    }

    public CompanySummaryResponse toSummary(Company company) {
        return new CompanySummaryResponse(
                company.getId(),
                company.getCompanyId(),
                company.getCompanyCode(),
                company.getCompanyName(),
                company.effectiveDisplayName(),
                company.getStatus(),
                company.isActive(),
                company.getSubscriptionPlanId(),
                company.getEmail(),
                company.getMobile(),
                company.getCity(),
                company.getState(),
                company.getTrialEndDate(),
                company.getSubscriptionEndDate(),
                company.getCreatedAt(),
                company.getVersion());
    }
}

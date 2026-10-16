package com.courier.modules.platformintegration.application;

import com.courier.modules.company.application.CompanyService;
import com.courier.modules.company.application.command.CreateCompanyCommand;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.company.domain.CompanyStatus;
import com.courier.modules.platformintegration.infrastructure.PlatformIntegrationProperties;
import com.courier.modules.subscription.application.SubscriptionPlanService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Creates and deactivates companies on the platform's behalf.
 *
 * <p>Company creation is a SUPER_ADMIN operation with audit and event side effects, and
 * those should not be re-implemented here. So the call runs under a fixed, company-less
 * SUPER_ADMIN principal and the original context is restored afterwards. The platform
 * token has already been verified by the security chain before this is reached.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "occpl.platform", name = "enabled", havingValue = "true")
public class PlatformTenantServiceImpl implements PlatformTenantService {

    /** Recorded as created-by on rows the platform provisions. */
    static final UUID PLATFORM_ACTOR = new UUID(0L, 0x0CC91L);

    private final CompanyService companyService;
    private final CompanyRepository companyRepository;
    private final SubscriptionPlanService planService;
    private final PlatformIntegrationProperties properties;

    @Override
    @Transactional
    public Result provision(UUID externalCompanyId, ProvisionCommand command) {
        return asPlatform(() -> {
            Company existing = companyRepository.findByExternalCompanyId(externalCompanyId).orElse(null);
            if (existing != null) {
                if (existing.getStatus() == CompanyStatus.INACTIVE) {
                    companyService.activate(existing.getId());
                }
                return new Result(existing.getCompanyId(), false);
            }
            requireText(command.companyCode(), "companyCode");
            requireText(command.companyName(), "companyName");
            requireText(command.adminEmail(), "adminEmail");
            String mobile = firstText(command.mobile(), properties.defaultMobile());
            if (mobile == null) {
                throw new BusinessRuleException("mobile is required to create a courier company");
            }
            UUID planId = planService.getByCode(properties.defaultPlanCode()).getId();

            Profile p = command.profile() == null ? Profile.EMPTY : command.profile();
            CompanyService.CreatedCompany created = companyService.create(new CreateCompanyCommand(
                    command.companyCode(), command.companyName(), null, null, planId,
                    command.adminEmail(), mobile, null, p.website(), p.gstNumber(), p.panNumber(), null,
                    // Logo and favicon links from the platform are signed and expire within a day, and the
                    // courier columns hold plain URLs of up to 500 characters: not stored here.
                    null, null,
                    p.addressLine1(), p.addressLine2(), p.country(), p.state(), p.city(), p.postalCode(),
                    null, null, null, null, null,
                    "Provisioned by OCCPL platform " + externalCompanyId, null,
                    command.adminEmail(), command.adminFirstName(), command.adminLastName(), mobile));

            Company company = created.company();
            company.setExternalCompanyId(externalCompanyId);
            companyRepository.save(company);
            log.info("Provisioned courier company {} for platform company {}",
                    company.getCompanyCode(), externalCompanyId);
            return new Result(company.getCompanyId(), true);
        });
    }

    @Override
    @Transactional
    public boolean deprovision(UUID externalCompanyId) {
        return asPlatform(() -> companyRepository.findByExternalCompanyId(externalCompanyId)
                .map(company -> {
                    companyService.deactivate(company.getId(), "Removed by OCCPL platform");
                    return true;
                })
                .orElse(false));
    }

    private static String firstText(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(field + " is required to create a courier company");
        }
    }

    private static <T> T asPlatform(java.util.function.Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext scoped = SecurityContextHolder.createEmptyContext();
        AuthenticatedUser actor = new AuthenticatedUser(PLATFORM_ACTOR, null, "platform@occpl",
                Set.of(Roles.SUPER_ADMIN), "platform");
        scoped.setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, actor.authorities()));
        SecurityContextHolder.setContext(scoped);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}

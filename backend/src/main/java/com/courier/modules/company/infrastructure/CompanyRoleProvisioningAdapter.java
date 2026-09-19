package com.courier.modules.company.infrastructure;

import com.courier.modules.auth.application.port.CompanyRoleProvisioningPort;
import com.courier.modules.company.application.BranchRoleProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The only implementation of {@link CompanyRoleProvisioningPort} — delegates straight to
 * {@link BranchRoleProvisioningService#ensureCompanyAdminRole}, the same ensure logic a
 * branch's {@code BRANCH_MANAGER} role already goes through, so admin provisioning and any
 * other future caller can never leave an account's {@code user_company_roles} row
 * unwritten in a different way.
 */
@Component
@RequiredArgsConstructor
public class CompanyRoleProvisioningAdapter implements CompanyRoleProvisioningPort {

    private final BranchRoleProvisioningService branchRoleProvisioningService;

    @Override
    public void ensureCompanyAdminRole(UUID companyId, UUID userId) {
        branchRoleProvisioningService.ensureCompanyAdminRole(companyId, userId);
    }
}

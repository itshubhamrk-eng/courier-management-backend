package com.courier.modules.auth.application.port;

import java.util.UUID;

/**
 * The auth module's seam for ensuring a newly-provisioned company admin holds the
 * company-owned, permissioned {@code COMPANY_ADMIN} role their JWT authority implies.
 *
 * <p>Without this, {@code UserProvisioningService.provisionAdmin} sets the account's auth
 * {@code Role.COMPANY_ADMIN} (the JWT {@code roles} claim) and stops there — the account
 * gets no {@code user_company_roles} row, so {@code UserPermissionService
 * .resolveEffectivePermissionCodes} (the JWT {@code permissions} claim, and every
 * {@code hasAuthority(...)} check) resolves to nothing for it, forever. Same class of gap
 * {@code BranchRoleProvisioningService} already closes for a branch account's
 * {@code BRANCH_MANAGER} role — see its own javadoc.
 *
 * <p>The only implementation is {@code CompanyRoleProvisioningAdapter} in
 * {@code modules/company}, delegating to {@code BranchRoleProvisioningService
 * #ensureCompanyAdminRole}, so there is exactly one place this is computed.
 */
public interface CompanyRoleProvisioningPort {

    /** Idempotent: safe to call whether or not the account already holds the role. */
    void ensureCompanyAdminRole(UUID companyId, UUID userId);
}

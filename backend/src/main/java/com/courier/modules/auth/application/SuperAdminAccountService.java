package com.courier.modules.auth.application;

import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Platform-tier accounts: the people who run the platform rather than a company.
 *
 * <p>Lives in {@code auth} because {@code users} and {@code user_roles} are auth's
 * tables — {@code modules/company} maps the same rows through its own entity but has no
 * business with the JWT-authority roles this reads and writes.
 *
 * <p><b>{@code SUPER_ADMIN} only</b>, reads included. The list of who holds the highest
 * privilege on the platform is exactly the list an attacker with a lesser account would
 * most like to have.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Roles.SUPER_ADMIN + "')")
public class SuperAdminAccountService {

    private final UserRepository userRepository;
    private final UserProvisioningService userProvisioningService;

    /**
     * Creates another super admin.
     *
     * <p>Uniqueness is checked <b>across every company</b>, not within one. Ordinary
     * accounts are unique per company because two unrelated businesses may both employ
     * a {@code priya@gmail.com}; a platform operator signs in with no company code at
     * all, and {@code AuthService} resolves their home company by finding the single
     * platform account with that address. A second one anywhere would make that lookup
     * ambiguous — and an ambiguous match is refused as a bad credential, so the newer
     * account would simply never be able to sign in. Better to refuse the creation.
     *
     * @param homeCompanyId the company the row is anchored to for storage, because
     *                      {@code users} has a non-null ownership column. It confers
     *                      nothing: a super admin already reaches every company. Null
     *                      means "the same one the calling super admin is anchored to".
     */
    @Transactional
    public UserProvisioningService.ProvisionedBranchUser create(String email,
                                                               String firstName,
                                                               String lastName,
                                                               String phone,
                                                               String password,
                                                               UUID homeCompanyId) {
        String normalised = User.normaliseEmail(email);

        // Runs with no company bound on purpose: the check must span companies, and the
        // Hibernate filter would otherwise narrow it to the caller's own.
        if (userRepository.existsByEmailAcrossCompanies(normalised)) {
            throw new DuplicateResourceException("User", "email", normalised);
        }

        UUID anchor = homeCompanyId != null ? homeCompanyId : callerCompanyId();

        UserProvisioningService.ProvisionedBranchUser created =
                userProvisioningService.provisionSuperAdmin(
                        new UserProvisioningService.NewSuperAdminCommand(
                                anchor, normalised, firstName, lastName, phone, password));

        log.warn("SUPER_ADMIN account {} created by {}", normalised, callerDescription());
        return created;
    }

    /**
     * Every platform-tier account, across all companies.
     *
     * <p>Includes {@code PLATFORM_ADMIN} as well as {@code SUPER_ADMIN}: the question
     * this screen answers is "who can act outside a single company", and omitting the
     * role that can impersonate any company would make the answer wrong in the one
     * direction that matters.
     */
    @Transactional(readOnly = true)
    public List<User> list() {
        return CompanyContext.runAs(null, () ->
                userRepository.findAllByRoleIn(EnumSet.of(Role.SUPER_ADMIN, Role.PLATFORM_ADMIN)));
    }

    private UUID callerCompanyId() {
        UUID caller = SecurityUtils.getCurrentUser()
                .map(AuthenticatedUser::companyId)
                .orElse(null);
        if (caller == null) {
            throw new BusinessRuleException(
                    "No home company could be derived for the new account; supply homeCompanyId.");
        }
        return caller;
    }

    private String callerDescription() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}

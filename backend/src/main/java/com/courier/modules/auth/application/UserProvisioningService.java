package com.courier.modules.auth.application;

import com.courier.modules.auth.domain.Role;

import java.util.Set;
import java.util.UUID;

/**
 * Creation of user accounts on behalf of another module.
 *
 * <p>This is the seam that lets {@code modules/company} create a company's first
 * administrator without touching the {@code users} table. The cross-feature rule in
 * {@code MEMORY/ARCHITECTURE.md} §1 permits depending on another feature's
 * <em>application service</em>, never on its domain or repositories.
 *
 * <p>Deliberately narrow: this is provisioning, not user management. There is no
 * update, no delete, no listing, and no public endpoint anywhere in {@code auth} that
 * reaches it. Full user administration is a later module.
 */
public interface UserProvisioningService {

    /**
     * Creates the first administrator of a freshly created company.
     *
     * <p>The account is created {@code PENDING} with a <b>temporary password</b> and an
     * activation email. The password is generated here, validated against the same
     * policy everyone else's is, and returned <em>once</em> — to the super admin who
     * created the company, in the create response. It is never logged, never audited,
     * never emailed and never readable again.
     *
     * <p>This is a change from the original design, where the initial password was 32
     * random bytes hashed and discarded. That was safer in the abstract and unusable in
     * practice: it made the activation email the sole path into a brand-new company, so
     * a bounced or filtered message left the customer with an account nobody could
     * enter and a super admin with nothing to hand them. The temporary password is the
     * same trade a branch account already makes, and it is bounded the same way — the
     * account is still {@code PENDING}, so the password alone opens nothing until the
     * activation link is followed.
     *
     * @return the created user's identity and its temporary password
     * @throws com.courier.shared.exception.DuplicateResourceException if the address is
     *         already registered within that company
     */
    ProvisionedUser provisionAdmin(NewAdminCommand command);

    /**
     * Creates a platform-tier {@code SUPER_ADMIN} account.
     *
     * <p>Unlike the other two, this account belongs to no company: it owns the platform.
     * {@code companyId} is therefore the home company the account is anchored to for
     * storage only — the {@code users} table has a non-null ownership column — and it
     * grants nothing over that company's data beyond what {@code SUPER_ADMIN} already
     * grants over every company's.
     *
     * <p>Created ACTIVE and pre-verified with a temporary password returned once: a
     * platform operator is onboarded by another platform operator, in person or over a
     * channel they already trust, and there is no company administrator above them to
     * fall back on if the email never arrives.
     *
     * @throws com.courier.shared.exception.DuplicateResourceException if the address is
     *         already registered within that company
     * @throws com.courier.shared.exception.BusinessRuleException if a supplied password
     *         fails the password policy
     */
    ProvisionedBranchUser provisionSuperAdmin(NewSuperAdminCommand command);

    /**
     * Creates the operating user of a freshly created branch.
     *
     * <p>Unlike {@link #provisionAdmin}, this account is <b>usable immediately</b>: it is
     * created ACTIVE with its email pre-verified, and the password is either the one the
     * administrator typed on the branch form or a generated one that is returned to that
     * administrator <em>once</em>, in the create response. This is a deliberate departure
     * from the "unusable password" rule that governs company administrators — a branch is
     * opened by a company admin who then hands the credentials over in person, and there is
     * no mailbox at a branch to receive a verification link.
     *
     * <p>The consequence is real and is the caller's to manage: a credential exists in an
     * API response. It is never logged, never audited and never returned again — a lost
     * password is reset, not recovered.
     *
     * @throws com.courier.shared.exception.DuplicateResourceException if the address is
     *         already registered within that company
     * @throws com.courier.shared.exception.BusinessRuleException if a supplied password
     *         fails the password policy
     */
    ProvisionedBranchUser provisionBranchUser(NewBranchUserCommand command);

    /**
     * @param companyId   the company's ownership key; the account is created bound to it
     * @param companyName shown in the activation email
     * @param email       login address, lowercased on write
     * @param firstName   may be null
     * @param lastName    may be null
     * @param phone       may be null
     * @param roles       roles to grant, typically {@code COMPANY_ADMIN} alone
     */
    record NewAdminCommand(UUID companyId,
                           String companyName,
                           String email,
                           String firstName,
                           String lastName,
                           String phone,
                           Set<Role> roles) {
    }

    /**
     * @param userId                id of the created account
     * @param email                 its login address
     * @param temporaryPassword     generated, policy-valid, and readable exactly once —
     *                              in the response to the call that created the account
     * @param activationEmailSent   whether the activation link was dispatched; false
     *                              means the account exists but the operator must resend
     */
    record ProvisionedUser(UUID userId,
                           String email,
                           String temporaryPassword,
                           boolean activationEmailSent) {
    }

    /**
     * @param companyId the home company the account is anchored to for storage; it
     *                  confers no authority over that company beyond what SUPER_ADMIN
     *                  already confers over all of them
     * @param password  the creating operator's choice; null means "generate and return"
     */
    record NewSuperAdminCommand(UUID companyId,
                                String email,
                                String firstName,
                                String lastName,
                                String phone,
                                String password) {
    }

    /**
     * @param companyId  the company's tenancy key; the account is created bound to it
     * @param email     login address, lowercased on write
     * @param firstName may be null
     * @param lastName  may be null
     * @param phone     may be null
     * @param password  the administrator's choice; null means "generate one and return it"
     * @param roles     roles to grant, typically {@code BRANCH_MANAGER} alone
     */
    record NewBranchUserCommand(UUID companyId,
                                String email,
                                String firstName,
                                String lastName,
                                String phone,
                                String password,
                                Set<Role> roles) {
    }

    /**
     * @param userId            id of the created account
     * @param email             its login address
     * @param temporaryPassword the generated password, or null when the administrator
     *                          supplied one — a password that was typed is not echoed back
     */
    record ProvisionedBranchUser(UUID userId, String email, String temporaryPassword) {

        public boolean passwordGenerated() {
            return temporaryPassword != null;
        }
    }
}

package com.courier.modules.auth.application;

import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Creates provisioned accounts for other modules.
 *
 * <p>Two properties matter here and are easy to get wrong:
 *
 * <ol>
 *   <li><b>The company is bound explicitly.</b> {@code CompanyEntityListener} stamps
 *       {@code company_id} from {@code CompanyContext} on persist, and a
 *       {@code SUPER_ADMIN} request has no company bound — so the write runs inside
 *       {@link CompanyContext#runAs}, which restores the previous binding afterwards.</li>
 *   <li><b>A generated password is returned once and never again.</b> It is built to
 *       satisfy the password policy by construction, validated against that policy
 *       anyway, and never logged, audited or emailed. The single copy travels back in
 *       the response to the call that created the account.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProvisioningServiceImpl implements UserProvisioningService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Alphabet for every generated password: no 0/O/1/l/I, because these are read off a
     * screen and typed by someone else. The four character classes are drawn separately so
     * the result satisfies the policy by construction rather than by retrying until it does.
     */
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIALS = "@#$%&*!?";
    private static final int GENERATED_PASSWORD_LENGTH = 14;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;

    @Override
    @Transactional
    public ProvisionedUser provisionAdmin(NewAdminCommand command) {
        UUID companyId = command.companyId();
        String email = User.normaliseEmail(command.email());

        return CompanyContext.runAs(companyId, () -> {
            if (userRepository.existsByEmail(email)) {
                throw new DuplicateResourceException("User", "email", email);
            }

            Set<Role> roles = command.roles() == null || command.roles().isEmpty()
                    ? EnumSet.of(Role.COMPANY_ADMIN)
                    : EnumSet.copyOf(command.roles());

            // Validated like any other password: a generated one that could not be set
            // through the normal change-password flow would be a silent policy fork.
            String temporaryPassword = generatePassword();
            passwordPolicy.validate(temporaryPassword, email, null, passwordEncoder::matches);

            User user = userRepository.save(User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(temporaryPassword))
                    .firstName(command.firstName())
                    .lastName(command.lastName())
                    .phone(command.phone())
                    // PENDING, not ACTIVE: the address is unproven until the activation
                    // link is followed, and an unverified admin must not be able to sign
                    // in. The temporary password alone therefore opens nothing — which
                    // is what makes returning it acceptable.
                    .status(UserStatus.PENDING)
                    .roles(roles)
                    .emailVerified(false)
                    .build());

            boolean emailSent = sendActivation(user, command.companyName());

            // No password in the log line, the audit detail, or anywhere else durable.
            log.info("Provisioned admin {} for company {} with roles {}",
                    user.getId(), companyId, roles);
            auditService.record(AuditAction.USER_CREATED, "User", user.getId(),
                    Map.of("email", email,
                            "companyId", companyId.toString(),
                            "roles", roles.stream().map(Enum::name).toList(),
                            "provisioned", true,
                            "passwordGenerated", true));

            return new ProvisionedUser(user.getId(), email, temporaryPassword, emailSent);
        });
    }

    @Override
    @Transactional
    public ProvisionedBranchUser provisionSuperAdmin(NewSuperAdminCommand command) {
        UUID companyId = command.companyId();
        String email = User.normaliseEmail(command.email());

        return CompanyContext.runAs(companyId, () -> {
            if (userRepository.existsByEmail(email)) {
                throw new DuplicateResourceException("User", "email", email);
            }

            boolean generated = command.password() == null || command.password().isBlank();
            String rawPassword = generated ? generatePassword() : command.password();
            passwordPolicy.validate(rawPassword, email, null, passwordEncoder::matches);

            User user = userRepository.save(User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .firstName(command.firstName())
                    .lastName(command.lastName())
                    .phone(command.phone())
                    // ACTIVE and pre-verified: a platform operator is onboarded by
                    // another platform operator, and there is nobody above them to
                    // recover the account if an activation email never arrives.
                    .status(UserStatus.ACTIVE)
                    .roles(EnumSet.of(Role.SUPER_ADMIN))
                    .emailVerified(true)
                    .emailVerifiedAt(Instant.now())
                    .build());

            log.info("Provisioned SUPER_ADMIN {} anchored to company {} ({} password)",
                    user.getId(), companyId, generated ? "generated" : "operator-set");
            auditService.record(AuditAction.SUPER_ADMIN_USER_CREATED, "User", user.getId(),
                    Map.of("email", email,
                            "companyId", companyId.toString(),
                            "passwordGenerated", generated));

            return new ProvisionedBranchUser(user.getId(), email, generated ? rawPassword : null);
        });
    }

    @Override
    @Transactional
    public ProvisionedBranchUser provisionBranchUser(NewBranchUserCommand command) {
        UUID companyId = command.companyId();
        String email = User.normaliseEmail(command.email());

        return CompanyContext.runAs(companyId, () -> {
            if (userRepository.existsByEmail(email)) {
                throw new DuplicateResourceException("User", "email", email);
            }

            Set<Role> roles = command.roles() == null || command.roles().isEmpty()
                    ? EnumSet.of(Role.BRANCH_MANAGER)
                    : EnumSet.copyOf(command.roles());

            boolean generated = command.password() == null || command.password().isBlank();
            String rawPassword = generated ? generatePassword() : command.password();
            // Validated whether typed or generated: a generated password that could not be
            // set through the normal change-password flow would be a silent policy fork.
            passwordPolicy.validate(rawPassword, email, null, passwordEncoder::matches);

            User user = userRepository.save(User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .firstName(command.firstName())
                    .lastName(command.lastName())
                    .phone(command.phone())
                    // ACTIVE and pre-verified, unlike a provisioned admin: the company
                    // administrator who opened the branch is the one proving the address,
                    // and a branch counter cannot follow a verification link.
                    .status(UserStatus.ACTIVE)
                    .roles(roles)
                    .emailVerified(true)
                    .emailVerifiedAt(Instant.now())
                    .build());

            // No password in the log line, the audit detail, or anywhere else durable.
            log.info("Provisioned branch user {} for company {} with roles {} ({} password)",
                    user.getId(), companyId, roles, generated ? "generated" : "administrator-set");
            auditService.record(AuditAction.USER_CREATED, "User", user.getId(),
                    Map.of("email", email,
                            "companyId", companyId.toString(),
                            "roles", roles.stream().map(Enum::name).toList(),
                            "provisioned", true,
                            "passwordGenerated", generated));

            return new ProvisionedBranchUser(user.getId(), email, generated ? rawPassword : null);
        });
    }

    /**
     * One character from each required class, the rest random, then shuffled — so the
     * classes are not always in the same positions.
     */
    private String generatePassword() {
        List<Character> chars = new ArrayList<>(GENERATED_PASSWORD_LENGTH);
        chars.add(pick(UPPER));
        chars.add(pick(LOWER));
        chars.add(pick(DIGITS));
        chars.add(pick(SPECIALS));
        String all = UPPER + LOWER + DIGITS + SPECIALS;
        while (chars.size() < GENERATED_PASSWORD_LENGTH) {
            chars.add(pick(all));
        }
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder(chars.size());
        chars.forEach(sb::append);
        return sb.toString();
    }

    private char pick(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }

    /**
     * A failed send must not roll back the account: the company and its administrator
     * still exist, and the link can be reissued. The caller is told so it can surface
     * the fact rather than silently promising an email that never arrived.
     */
    private boolean sendActivation(User user, String companyName) {
        try {
            emailVerificationService.issueCompanyActivation(user, companyName);
            return true;
        } catch (RuntimeException e) {
            log.error("Could not send the activation email for provisioned user {}; "
                    + "the account exists and the link must be reissued", user.getId(), e);
            return false;
        }
    }
}

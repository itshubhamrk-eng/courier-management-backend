package com.courier.modules.company.application;

import com.courier.modules.auth.application.PasswordPolicy;
import com.courier.modules.company.application.command.CreateUserCommand;
import com.courier.modules.company.application.command.UpdateUserCommand;
import com.courier.modules.company.application.event.UserEvent;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.DefaultRoleCatalog;
import com.courier.modules.company.domain.User;
import com.courier.modules.company.domain.UserCriteria;
import com.courier.modules.company.domain.CompanyUserRepository;
import com.courier.modules.company.domain.UserRole;
import com.courier.modules.company.domain.UserRoleRepository;
import com.courier.modules.company.domain.UserSpecifications;
import com.courier.modules.company.domain.UserStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Company user use cases.
 *
 * <p>Per-method {@code @PreAuthorize}: most writes are {@code COMPANY_ADMIN}; reads add
 * {@code SUPER_ADMIN} (all companies) and the branch/hub managers (their own placement,
 * scoped in code because a URL rule cannot say "their branch"). Self password change is
 * open to any authenticated user acting on their own id.
 *
 * <p><b>A branch manager may create and staff their own branch's users</b> — branch
 * responsibility #1 ("create branch users") and #2 ("assign menus", which today means
 * assigning the company role that governs a staff member's menus). {@code create},
 * {@code update}, {@code activate}, {@code deactivate}, {@code assignRole} and
 * {@code removeRole} therefore admit {@code BRANCH_MANAGER} too, gated by
 * {@link #BRANCH_WRITERS}, with the finer "your branch, and only the roles a branch may
 * hand out" enforced in code by {@link #requireManageableByCaller} and
 * {@link DefaultRoleCatalog#isBranchAssignable}. {@code delete}, {@code lock},
 * {@code unlock}, {@code resetPassword}, {@code assignBranch} and {@code assignHub} stay
 * {@code COMPANY_ADMIN}-only — locking a colleague out, resetting their password or
 * moving them to another branch is not "staffing a counter". See
 * {@code MEMORY/modules/branch.md} §"A branch manager staffs their own branch".
 *
 * <p>Company isolation is the project's usual two layers: the Hibernate filter, plus
 * {@code findByIdWithinCompany} on every single-row load, because a primary-key load
 * bypasses the filter. A foreign user's id returns 404, never 403.
 *
 * <p>Password rules are reused from auth's {@link PasswordPolicy} rather than
 * re-implemented — depending on another feature's <em>application</em> service is allowed,
 * and a second copy of the policy would inevitably diverge.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String ENTITY = "User";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String WRITERS = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String BRANCH_WRITERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "')";
    private static final String READERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.SUPER_ADMIN + "', '" + Roles.BRANCH_MANAGER + "', '" + Roles.HUB_MANAGER + "')";

    private final CompanyUserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final CompanyRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------- create

    @Override
    @Transactional
    @PreAuthorize(BRANCH_WRITERS)
    public CreatedUser create(CreateUserCommand command) {
        UUID companyId = requireCompany();
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();

        UUID branchId = command.branchId();
        UUID hubId = command.hubId();
        boolean branchManagerActor = isBranchManagerActor(caller);
        if (branchManagerActor) {
            UUID ownBranchId = requireOwnBranch(caller);
            if (branchId != null && !branchId.equals(ownBranchId)) {
                throw new ForbiddenException("You may only create users for your own branch.");
            }
            if (hubId != null) {
                throw new ForbiddenException("A branch manager cannot place a user at a hub.");
            }
            branchId = ownBranchId;
        }

        String email = User.normaliseEmail(command.email());
        String username = User.normaliseUsername(command.username());
        String employeeCode = User.normaliseEmployeeCode(command.employeeCode());

        requireEmailAvailable(companyId, email, null);
        requireUsernameAvailable(username, null);
        requireEmployeeCodeAvailable(companyId, employeeCode, null);

        boolean passwordGenerated = command.password() == null || command.password().isBlank();
        String rawPassword = passwordGenerated ? unusablePassword() : command.password();
        if (!passwordGenerated) {
            // Applies length, complexity and common-password rules. currentHash is null:
            // there is no prior password to compare against on a new account.
            passwordPolicy.validate(rawPassword, email, null, passwordEncoder::matches);
        }

        User user = User.builder()
                .employeeCode(employeeCode)
                .employeeId(command.employeeId())
                .firstName(command.firstName())
                .middleName(command.middleName())
                .lastName(command.lastName())
                .displayName(command.displayName())
                .email(email)
                .username(username)
                .mobile(command.mobile())
                .alternateMobile(command.alternateMobile())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .gender(command.gender())
                .dateOfBirth(command.dateOfBirth())
                .designation(command.designation())
                .department(command.department())
                .joiningDate(command.joiningDate())
                .reportingManagerId(command.reportingManagerId())
                .branchId(branchId)
                .hubId(hubId)
                .profileImage(command.profileImage())
                .remarks(command.remarks())
                // A generated password is unusable, so the account cannot log in until an
                // admin resets it — PENDING says exactly that. A supplied password means
                // the admin intends the account usable now.
                .status(passwordGenerated ? UserStatus.PENDING : UserStatus.ACTIVE)
                .build();

        validateReportingManager(companyId, user);
        user.applyInvariants();
        User saved = userRepository.save(user);

        List<String> roleCodes = assignRoles(companyId, saved, command.roleIds(), branchManagerActor);

        log.info("User {} ({}) created in company {} by {}",
                saved.getEmail(), saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.USER_CREATED, ENTITY, saved.getId(),
                Map.of("email", saved.getEmail(),
                        "employeeCode", String.valueOf(saved.getEmployeeCode()),
                        "roles", roleCodes,
                        "passwordGenerated", passwordGenerated));
        eventPublisher.publishEvent(new UserEvent.UserCreated(
                saved.getId(), companyId, saved.getEmail(),
                SecurityUtils.getCurrentUserId().orElse(null), false, Instant.now()));

        return new CreatedUser(saved, passwordGenerated, roleCodes);
    }

    // ------------------------------------------------------------------- update

    @Override
    @Transactional
    @PreAuthorize(BRANCH_WRITERS)
    public User update(UUID id, UpdateUserCommand command) {
        UUID companyId = requireCompany();
        User user = loadWritable(id, companyId);
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        requireManageableByCaller(caller, user);
        requireCurrentVersion(user, command.expectedVersion());

        UUID branchId = command.branchId();
        UUID hubId = command.hubId();
        if (isBranchManagerActor(caller)) {
            UUID ownBranchId = requireOwnBranch(caller);
            if (branchId != null && !branchId.equals(ownBranchId)) {
                throw new ForbiddenException("You may only keep a user at your own branch.");
            }
            if (hubId != null) {
                throw new ForbiddenException("A branch manager cannot place a user at a hub.");
            }
            branchId = ownBranchId;
        }

        Map<String, Object> before = snapshot(user);

        user.setFirstName(command.firstName());
        user.setMiddleName(command.middleName());
        user.setLastName(command.lastName());
        user.setDisplayName(command.displayName());
        user.setMobile(command.mobile());
        user.setAlternateMobile(command.alternateMobile());
        user.setGender(command.gender());
        user.setDateOfBirth(command.dateOfBirth());
        user.setDesignation(command.designation());
        user.setDepartment(command.department());
        user.setJoiningDate(command.joiningDate());
        user.setReportingManagerId(command.reportingManagerId());
        user.setBranchId(branchId);
        user.setHubId(hubId);
        user.setProfileImage(command.profileImage());
        user.setRemarks(command.remarks());

        validateReportingManager(companyId, user);
        user.applyInvariants();
        User saved = userRepository.save(user);

        Map<String, Object> changes = changeDetails(before, snapshot(saved));
        log.info("User {} updated in company {} by {} ({} field(s))",
                saved.getEmail(), companyId, currentActor(), changes.size());
        auditService.record(AuditAction.USER_UPDATED, ENTITY, saved.getId(), changes);
        eventPublisher.publishEvent(new UserEvent.UserUpdated(
                saved.getId(), companyId, changes.keySet(), Instant.now()));

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public void delete(UUID id) {
        UUID companyId = requireCompany();
        User user = loadWritable(id, companyId);
        requireNotSelf(user, "delete your own account");

        // Soft delete only, and deactivate alongside so a restored row is not silently
        // able to log in. Role assignments are left in place — they carry the company id
        // and are hidden by the user's own soft delete for every read that joins them.
        user.deactivate();
        user.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        userRepository.save(user);

        log.warn("User {} ({}) soft deleted in company {} by {}",
                user.getEmail(), user.getId(), companyId, currentActor());
        audit(AuditAction.DELETED, user, Map.of("email", user.getEmail()));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public java.util.Map<UUID, Integer> roleCounts(java.util.Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRoleRepository.findAllByUserIdIn(userIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(UserRole::getUserId,
                        java.util.stream.Collectors.reducing(0, r -> 1, Integer::sum)));
    }

    // -------------------------------------------------------------------- reads

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public User getById(UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();

        if (caller.isSuperAdmin() && CompanyContext.getCompanyId().isEmpty()) {
            // Platform-level read: no company bound, so load by primary key directly.
            return userRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
        }

        User user = loadWithinCompany(id, CompanyContext.requireCompanyId());
        requireVisibleTo(caller, user);
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Page<User> search(UserCriteria criteria, Pageable pageable) {
        UserCriteria safe = criteria == null ? UserCriteria.none() : criteria;
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();

        // A company admin (and any company-bound caller) is pinned to their own company:
        // a companyId in the query can only narrow, never widen.
        UserCriteria effective = CompanyContext.getCompanyId().map(safe::withCompanyId).orElse(safe);

        // Branch and hub managers see only their own placement. Their scope comes from
        // their own user row, never from the request.
        if (!caller.isSuperAdmin() && !caller.hasRole(Roles.COMPANY_ADMIN)) {
            effective = restrictToManagerScope(caller, effective);
        }

        return userRepository.findAll(UserSpecifications.matching(effective), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<UserRole> listRoles(UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        if (!(caller.isSuperAdmin() && CompanyContext.getCompanyId().isEmpty())) {
            User user = loadWithinCompany(id, CompanyContext.requireCompanyId());
            requireVisibleTo(caller, user);
        }
        return userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(id);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    @Transactional
    @PreAuthorize(BRANCH_WRITERS)
    public User activate(UUID id) {
        UUID companyId = requireCompany();
        User user = loadWritable(id, companyId);
        requireManageableByCaller(SecurityUtils.requireCurrentUser(), user);
        if (user.getStatus() == UserStatus.ACTIVE && !user.isLocked()) {
            return user;
        }
        user.activate();
        User saved = userRepository.save(user);
        audit(AuditAction.USER_ACTIVATED, saved, Map.of("email", saved.getEmail()));
        eventPublisher.publishEvent(new UserEvent.UserActivated(saved.getId(), companyId, Instant.now()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(BRANCH_WRITERS)
    public User deactivate(UUID id) {
        UUID companyId = requireCompany();
        User user = loadWritable(id, companyId);
        requireManageableByCaller(SecurityUtils.requireCurrentUser(), user);
        requireNotSelf(user, "deactivate your own account");
        if (user.getStatus() == UserStatus.DISABLED) {
            return user;
        }
        user.deactivate();
        User saved = userRepository.save(user);
        audit(AuditAction.USER_DEACTIVATED, saved, Map.of("email", saved.getEmail()));
        eventPublisher.publishEvent(new UserEvent.UserDeactivated(saved.getId(), companyId, Instant.now()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public User lock(UUID id, String reason) {
        UUID companyId = requireCompany();
        User user = loadWritable(id, companyId);
        requireNotSelf(user, "lock your own account");
        if (user.isLocked()) {
            return user;
        }
        user.lock();
        User saved = userRepository.save(user);
        audit(AuditAction.USER_LOCKED, saved,
                Map.of("email", saved.getEmail(), "reason", reason == null ? "" : reason));
        eventPublisher.publishEvent(new UserEvent.UserLocked(saved.getId(), companyId, reason, Instant.now()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public User unlock(UUID id) {
        UUID companyId = requireCompany();
        User user = loadWritable(id, companyId);
        if (!user.isLocked()) {
            return user;
        }
        user.unlock();
        User saved = userRepository.save(user);
        audit(AuditAction.USER_UNLOCKED, saved, Map.of("email", saved.getEmail()));
        eventPublisher.publishEvent(new UserEvent.UserUnlocked(saved.getId(), companyId, Instant.now()));
        return saved;
    }

    // ---------------------------------------------------------------- passwords

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public void resetPassword(UUID id, String newPassword, boolean mustChangeOnNextLogin) {
        UUID companyId = requireCompany();
        User user = loadWritable(id, companyId);

        passwordPolicy.validate(newPassword, user.getEmail(), user.getPasswordHash(),
                passwordEncoder::matches);
        user.setNewPassword(passwordEncoder.encode(newPassword));
        // An admin reset re-enables a PENDING account: the password is now known and set.
        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        audit(AuditAction.USER_PASSWORD_RESET, user,
                Map.of("email", user.getEmail(), "mustChange", mustChangeOnNextLogin));
        eventPublisher.publishEvent(new UserEvent.PasswordReset(
                user.getId(), companyId, mustChangeOnNextLogin, Instant.now()));
    }

    @Override
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void changePassword(UUID id, String currentPassword, String newPassword) {
        // Self-service only: a user may change their own password, nobody else's.
        UUID callerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new BusinessRuleException("No authenticated user."));
        if (!callerId.equals(id)) {
            throw new com.courier.shared.exception.ForbiddenException(
                    "You may only change your own password.");
        }
        User user = loadWithinCompany(id, CompanyContext.requireCompanyId());

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("The current password is incorrect.");
        }
        passwordPolicy.validate(newPassword, user.getEmail(), user.getPasswordHash(),
                passwordEncoder::matches);
        user.setNewPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        audit(AuditAction.USER_PASSWORD_RESET, user, Map.of("email", user.getEmail(), "self", true));
        eventPublisher.publishEvent(new UserEvent.PasswordReset(user.getId(),
                CompanyContext.requireCompanyId(), false, Instant.now()));
    }

    // -------------------------------------------------------------------- roles

    @Override
    @Transactional
    @PreAuthorize(BRANCH_WRITERS)
    public List<UserRole> assignRole(UUID id, UUID roleId) {
        UUID companyId = requireCompany();
        User user = loadWritable(id, companyId);
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        requireManageableByCaller(caller, user);
        CompanyRole role = roleRepository.findByIdWithinCompany(roleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
        if (!role.isActive()) {
            throw new BusinessRuleException(
                    "Role %s is inactive and cannot be assigned.".formatted(role.getRoleCode()));
        }
        if (isBranchManagerActor(caller)
                && !DefaultRoleCatalog.isBranchAssignable(role.getRoleCode(), role.isSystemRole())) {
            throw new ForbiddenException(
                    "A branch manager may not assign the role %s.".formatted(role.getRoleCode()));
        }

        if (!userRoleRepository.existsByUserIdAndRoleId(id, roleId)) {
            userRoleRepository.save(UserRole.assign(id, role));
            audit(AuditAction.USER_ROLE_ASSIGNED, user,
                    Map.of("email", user.getEmail(), "roleCode", role.getRoleCode()));
            eventPublisher.publishEvent(new UserEvent.RoleAssigned(
                    id, companyId, role.getRoleCode(), Instant.now()));
        }
        return userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(id);
    }

    @Override
    @Transactional
    @PreAuthorize(BRANCH_WRITERS)
    public void removeRole(UUID id, UUID roleId) {
        UUID companyId = requireCompany();
        User user = loadWritable(id, companyId);
        requireManageableByCaller(SecurityUtils.requireCurrentUser(), user);

        Optional<UserRole> assignment = userRoleRepository.findByUserIdAndRoleId(id, roleId);
        if (assignment.isEmpty()) {
            // Idempotent: removing a role the user does not hold is not an error.
            return;
        }
        UserRole userRole = assignment.get();
        userRole.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        userRoleRepository.save(userRole);

        audit(AuditAction.USER_ROLE_REMOVED, user,
                Map.of("email", user.getEmail(), "roleCode", userRole.getRoleCode()));
        eventPublisher.publishEvent(new UserEvent.RoleRemoved(
                id, companyId, userRole.getRoleCode(), Instant.now()));
    }

    // ----------------------------------------------------------------- placement

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public User assignBranch(UUID id, UUID branchId) {
        UUID companyId = requireCompany();
        User user = loadWritable(id, companyId);
        user.assignBranch(branchId);
        User saved = userRepository.save(user);
        audit(AuditAction.USER_BRANCH_ASSIGNED, saved,
                Map.of("email", saved.getEmail(), "branchId", String.valueOf(branchId)));
        eventPublisher.publishEvent(new UserEvent.BranchAssigned(saved.getId(), companyId, branchId, Instant.now()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public User assignHub(UUID id, UUID hubId) {
        UUID companyId = requireCompany();
        User user = loadWritable(id, companyId);
        user.assignHub(hubId);
        User saved = userRepository.save(user);
        audit(AuditAction.USER_HUB_ASSIGNED, saved,
                Map.of("email", saved.getEmail(), "hubId", String.valueOf(hubId)));
        eventPublisher.publishEvent(new UserEvent.HubAssigned(saved.getId(), companyId, hubId, Instant.now()));
        return saved;
    }

    // -------------------------------------------------------------------- helpers

    private List<String> assignRoles(UUID companyId, User user, List<UUID> roleIds,
                                     boolean branchManagerActor) {
        List<CompanyRole> roles = new ArrayList<>();
        if (roleIds == null || roleIds.isEmpty()) {
            // No role given: fall back to the company's default, so a new user is never
            // left with no way to do anything. The catalogue's one default role
            // (BOOKING_OPERATOR) is always branch-assignable, so this never needs the check
            // below.
            roleRepository.findDefaultRole(companyId).ifPresent(roles::add);
        } else {
            for (UUID roleId : roleIds) {
                CompanyRole role = roleRepository.findByIdWithinCompany(roleId, companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
                if (!role.isActive()) {
                    throw new BusinessRuleException(
                            "Role %s is inactive and cannot be assigned.".formatted(role.getRoleCode()));
                }
                if (branchManagerActor
                        && !DefaultRoleCatalog.isBranchAssignable(role.getRoleCode(), role.isSystemRole())) {
                    throw new ForbiddenException(
                            "A branch manager may not assign the role %s.".formatted(role.getRoleCode()));
                }
                roles.add(role);
            }
        }

        List<String> codes = new ArrayList<>();
        for (CompanyRole role : roles) {
            userRoleRepository.save(UserRole.assign(user.getId(), role));
            codes.add(role.getRoleCode());
        }
        return codes;
    }

    /** Load for a write, always within the caller's company. */
    private User loadWritable(UUID id, UUID companyId) {
        return loadWithinCompany(id, companyId);
    }

    private User loadWithinCompany(UUID id, UUID companyId) {
        return userRepository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    /**
     * A branch or hub manager may only see users at their own placement. 404, not 403:
     * telling them "this user exists but is not at your branch" leaks headcount.
     */
    private void requireVisibleTo(AuthenticatedUser caller, User user) {
        if (caller.isSuperAdmin() || caller.hasRole(Roles.COMPANY_ADMIN)) {
            return;
        }
        User self = self(caller);
        if (caller.hasRole(Roles.BRANCH_MANAGER)
                && Objects.equals(self.getBranchId(), user.getBranchId())
                && self.getBranchId() != null) {
            return;
        }
        if (caller.hasRole(Roles.HUB_MANAGER)
                && Objects.equals(self.getHubId(), user.getHubId())
                && self.getHubId() != null) {
            return;
        }
        throw new ResourceNotFoundException(ENTITY, user.getId());
    }

    /**
     * The write-side twin of {@link #requireVisibleTo}: a branch manager may only write a
     * user placed at the branch they manage. Unlike the read check this is 403, not 404 —
     * a colleague of the same company reached but not manageable is a permission answer,
     * the same distinction Branch itself draws for out-of-scope manage attempts (see
     * {@code MEMORY/modules/branch.md} §Security).
     */
    private void requireManageableByCaller(AuthenticatedUser caller, User target) {
        if (caller.isSuperAdmin() || caller.hasRole(Roles.COMPANY_ADMIN)) {
            return;
        }
        if (caller.hasRole(Roles.BRANCH_MANAGER)) {
            User self = self(caller);
            if (self.getBranchId() != null && Objects.equals(self.getBranchId(), target.getBranchId())) {
                return;
            }
        }
        throw new ForbiddenException("You may only manage users of your own branch.");
    }

    private boolean isBranchManagerActor(AuthenticatedUser caller) {
        return !caller.isSuperAdmin() && !caller.hasRole(Roles.COMPANY_ADMIN)
                && caller.hasRole(Roles.BRANCH_MANAGER);
    }

    /** The branch a branch-manager caller runs, or a refusal — never another company's. */
    private UUID requireOwnBranch(AuthenticatedUser caller) {
        UUID branchId = self(caller).getBranchId();
        if (branchId == null) {
            throw new BusinessRuleException(
                    "You are not placed at a branch, so you have no branch to staff.");
        }
        return branchId;
    }

    private UserCriteria restrictToManagerScope(AuthenticatedUser caller, UserCriteria criteria) {
        User self = self(caller);
        if (caller.hasRole(Roles.BRANCH_MANAGER) && self.getBranchId() != null) {
            return criteria.withBranchId(self.getBranchId());
        }
        if (caller.hasRole(Roles.HUB_MANAGER) && self.getHubId() != null) {
            return criteria.withHubId(self.getHubId());
        }
        // A manager with no placement sees nobody, rather than everybody.
        return criteria.withBranchId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }

    private User self(AuthenticatedUser caller) {
        return userRepository.findByIdWithinCompany(caller.userId(), CompanyContext.requireCompanyId())
                .orElseThrow(() -> new BusinessRuleException(
                        "Your own user record was not found in this company."));
    }

    private void validateReportingManager(UUID companyId, User user) {
        UUID managerId = user.getReportingManagerId();
        if (managerId == null) {
            return;
        }
        if (managerId.equals(user.getId())) {
            throw new BusinessRuleException("A user cannot report to themselves.");
        }
        // The manager must be a real user of the same company.
        userRepository.findByIdWithinCompany(managerId, companyId)
                .orElseThrow(() -> new BusinessRuleException(
                        "The reporting manager is not a user of this company."));
    }

    private void requireEmailAvailable(UUID companyId, String email, UUID excludeId) {
        if (userRepository.isEmailTaken(companyId, email, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "email", email);
        }
    }

    private void requireUsernameAvailable(String username, UUID excludeId) {
        if (userRepository.isUsernameTaken(username, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "username", username);
        }
    }

    private void requireEmployeeCodeAvailable(UUID companyId, String employeeCode, UUID excludeId) {
        if (userRepository.isEmployeeCodeTaken(companyId, employeeCode, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "employeeCode", employeeCode);
        }
    }

    private void requireNotSelf(User user, String action) {
        if (SecurityUtils.getCurrentUserId().map(user.getId()::equals).orElse(false)) {
            throw new BusinessRuleException("You cannot " + action + ".");
        }
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Users belong to a company, so this "
                        + "operation must be performed by a user of that company."));
    }

    private void requireCurrentVersion(User user, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(user.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(User.class, user.getId());
        }
    }

    private String unusablePassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void audit(AuditAction action, User user, Map<String, Object> details) {
        auditService.record(action, ENTITY, user.getId(), details);
    }

    private Map<String, Object> snapshot(User user) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("firstName", user.getFirstName());
        values.put("middleName", user.getMiddleName());
        values.put("lastName", user.getLastName());
        values.put("displayName", user.getDisplayName());
        values.put("mobile", user.getMobile());
        values.put("alternateMobile", user.getAlternateMobile());
        values.put("gender", user.getGender() == null ? null : user.getGender().name());
        values.put("dateOfBirth", String.valueOf(user.getDateOfBirth()));
        values.put("designation", user.getDesignation());
        values.put("department", user.getDepartment());
        values.put("joiningDate", String.valueOf(user.getJoiningDate()));
        values.put("reportingManagerId", String.valueOf(user.getReportingManagerId()));
        values.put("branchId", String.valueOf(user.getBranchId()));
        values.put("hubId", String.valueOf(user.getHubId()));
        values.put("profileImage", user.getProfileImage());
        values.put("remarks", user.getRemarks());
        return values;
    }

    private Map<String, Object> changeDetails(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        before.forEach((field, oldValue) -> {
            Object newValue = after.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> pair = new HashMap<>();
                pair.put("from", oldValue);
                pair.put("to", newValue);
                changes.put(field, pair);
            }
        });
        return changes;
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}

package com.courier.modules.company.application;

import com.courier.modules.auth.application.AuthProperties;
import com.courier.modules.auth.application.PasswordPolicy;
import com.courier.modules.company.application.command.CreateUserCommand;
import com.courier.modules.company.application.command.UpdateUserCommand;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.CompanyUserRepository;
import com.courier.modules.company.domain.RoleStatus;
import com.courier.modules.company.domain.RoleType;
import com.courier.modules.company.domain.User;
import com.courier.modules.company.domain.UserRole;
import com.courier.modules.company.domain.UserRoleRepository;
import com.courier.modules.company.domain.UserStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * User Management rules, with repositories, the encoder and the audit trail mocked.
 *
 * <p>{@code @PreAuthorize} is not exercised (it needs a proxy), but the self-guards read
 * the {@code SecurityContext}, so a caller principal is planted in each test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();

    @Mock private CompanyUserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private CompanyRoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private UserServiceImpl service;
    private CompanyRole defaultRole;

    @BeforeEach
    void setUp() {
        // A real PasswordPolicy with permissive defaults, so create/reset exercise it
        // without a mock returning nulls.
        AuthProperties props = new AuthProperties();
        PasswordPolicy passwordPolicy = new PasswordPolicy(props);

        service = new UserServiceImpl(userRepository, userRoleRepository, roleRepository,
                passwordEncoder, passwordPolicy, auditService, eventPublisher);

        CompanyContext.setCompanyId(TENANT);
        planted(Roles.COMPANY_ADMIN);

        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.isEmailTaken(any(), any(), any())).thenReturn(false);
        when(userRepository.isUsernameTaken(any(), any())).thenReturn(false);
        when(userRepository.isEmployeeCodeTaken(any(), any(), any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("ENCODED");
        when(userRoleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        defaultRole = role("BOOKING_OPERATOR");
        defaultRole.markAsDefault();
        when(roleRepository.findDefaultRole(TENANT)).thenReturn(Optional.of(defaultRole));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void planted(String... roles) {
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, TENANT, "admin@legacy.test", Set.of(roles), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private CompanyRole role(String code) {
        return role(code, false);
    }

    private CompanyRole role(String code, boolean systemRole) {
        CompanyRole role = CompanyRole.builder()
                .roleCode(code).roleName(code).roleType(RoleType.OPERATIONS)
                .status(RoleStatus.ACTIVE).systemRole(systemRole).build();
        role.setCompanyId(TENANT);
        return role;
    }

    /** Plants a branch manager, self-placed at {@code ownBranchId}. */
    private void plantedBranchManager(UUID ownBranchId) {
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, TENANT, "manager@legacy.test", Set.of(Roles.BRANCH_MANAGER), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));

        User self = existing("manager@legacy.test");
        self.setId(CALLER);
        self.setBranchId(ownBranchId);
        when(userRepository.findByIdWithinCompany(CALLER, TENANT)).thenReturn(Optional.of(self));
    }

    private CreateUserCommand createCommand(String email, String password, List<UUID> roleIds) {
        return new CreateUserCommand("emp001", null, "Asha", null, "Nair", null,
                email, "asha.nair", "+91 9876500001", null, password,
                null, null, "Clerk", "Ops", null, null, null, null, null, null, roleIds);
    }

    // ------------------------------------------------------------------- create

    @Test
    @DisplayName("create without a password yields a PENDING account with the default role")
    void createWithoutPassword() {
        UserService.CreatedUser created = service.create(createCommand("ASHA@Legacy.test", null, null));

        assertThat(created.user().getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(created.passwordGenerated()).isTrue();
        assertThat(created.user().getEmail()).isEqualTo("asha@legacy.test");
        assertThat(created.assignedRoleCodes()).containsExactly("BOOKING_OPERATOR");
        verify(auditService).record(eq(AuditAction.USER_CREATED), eq("User"), any(), any());
    }

    @Test
    @DisplayName("create with a password yields an ACTIVE account")
    void createWithPassword() {
        UserService.CreatedUser created =
                service.create(createCommand("ravi@legacy.test", "Str0ng!Pass99", null));

        assertThat(created.user().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.passwordGenerated()).isFalse();
        verify(passwordEncoder).encode("Str0ng!Pass99");
    }

    @Test
    @DisplayName("create rejects a duplicate email, username or employee code")
    void createRejectsDuplicates() {
        when(userRepository.isEmailTaken(eq(TENANT), eq("asha@legacy.test"), isNull())).thenReturn(true);
        assertThatThrownBy(() -> service.create(createCommand("asha@legacy.test", null, null)))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("email");

        when(userRepository.isEmailTaken(any(), any(), any())).thenReturn(false);
        when(userRepository.isUsernameTaken(eq("asha.nair"), isNull())).thenReturn(true);
        assertThatThrownBy(() -> service.create(createCommand("a@legacy.test", null, null)))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("username");

        when(userRepository.isUsernameTaken(any(), any())).thenReturn(false);
        when(userRepository.isEmployeeCodeTaken(eq(TENANT), eq("EMP001"), isNull())).thenReturn(true);
        assertThatThrownBy(() -> service.create(createCommand("b@legacy.test", null, null)))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("employeeCode");
    }

    @Test
    @DisplayName("explicit role ids are used and validated as active")
    void createWithExplicitRoles() {
        UUID roleId = UUID.randomUUID();
        CompanyRole finance = role("FINANCE_USER");
        when(roleRepository.findByIdWithinCompany(roleId, TENANT)).thenReturn(Optional.of(finance));

        UserService.CreatedUser created = service.create(
                createCommand("ravi@legacy.test", null, List.of(roleId)));

        assertThat(created.assignedRoleCodes()).containsExactly("FINANCE_USER");
        verify(roleRepository, never()).findDefaultRole(any());
    }

    @Test
    @DisplayName("an inactive role cannot be assigned at creation")
    void createRejectsInactiveRole() {
        UUID roleId = UUID.randomUUID();
        CompanyRole inactive = role("OLD");
        inactive.deactivate();
        when(roleRepository.findByIdWithinCompany(roleId, TENANT)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.create(createCommand("x@legacy.test", null, List.of(roleId))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("create without a bound company is refused")
    void createWithoutCompany() {
        CompanyContext.clear();
        assertThatThrownBy(() -> service.create(createCommand("x@legacy.test", null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("No company is bound");
    }

    // ------------------------------------------------------- branch manager staffing

    @Test
    @DisplayName("a branch manager creates a user for their own branch")
    void branchManagerCreatesOwnBranchUser() {
        UUID ownBranch = UUID.randomUUID();
        plantedBranchManager(ownBranch);

        UserService.CreatedUser created = service.create(
                new CreateUserCommand("emp002", null, "Ravi", null, "Rao", null,
                        "ravi@legacy.test", "ravi.rao", "+91 9876500002", null, null,
                        null, null, "Clerk", "Ops", null, null, ownBranch, null, null, null, null));

        assertThat(created.user().getBranchId()).isEqualTo(ownBranch);
    }

    @Test
    @DisplayName("a branch manager cannot create a user for another branch")
    void branchManagerCannotCreateForeignBranchUser() {
        UUID ownBranch = UUID.randomUUID();
        UUID otherBranch = UUID.randomUUID();
        plantedBranchManager(ownBranch);

        assertThatThrownBy(() -> service.create(
                new CreateUserCommand("emp002", null, "Ravi", null, "Rao", null,
                        "ravi@legacy.test", "ravi.rao", "+91 9876500002", null, null,
                        null, null, "Clerk", "Ops", null, null, otherBranch, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("your own branch");
    }

    @Test
    @DisplayName("a branch manager cannot place a new user at a hub")
    void branchManagerCannotCreateHubUser() {
        UUID ownBranch = UUID.randomUUID();
        plantedBranchManager(ownBranch);

        assertThatThrownBy(() -> service.create(
                new CreateUserCommand("emp002", null, "Ravi", null, "Rao", null,
                        "ravi@legacy.test", "ravi.rao", "+91 9876500002", null, null,
                        null, null, "Clerk", "Ops", null, null, null, UUID.randomUUID(), null, null, null)))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("hub");
    }

    @Test
    @DisplayName("a branch manager cannot write a user of another branch")
    void branchManagerCannotUpdateForeignBranchUser() {
        UUID ownBranch = UUID.randomUUID();
        plantedBranchManager(ownBranch);
        User foreign = existing("asha@legacy.test");
        foreign.setBranchId(UUID.randomUUID());
        when(userRepository.findByIdWithinCompany(foreign.getId(), TENANT)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.update(foreign.getId(), updateCommand("X", 2L)))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("your own branch");
        assertThatThrownBy(() -> service.activate(foreign.getId()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.deactivate(foreign.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("a branch manager may update a user of their own branch")
    void branchManagerUpdatesOwnBranchUser() {
        UUID ownBranch = UUID.randomUUID();
        plantedBranchManager(ownBranch);
        User own = existing("asha@legacy.test");
        own.setBranchId(ownBranch);
        when(userRepository.findByIdWithinCompany(own.getId(), TENANT)).thenReturn(Optional.of(own));

        User saved = service.update(own.getId(), updateCommand("Ashaa", 2L));

        assertThat(saved.getFirstName()).isEqualTo("Ashaa");
        assertThat(saved.getBranchId()).isEqualTo(ownBranch);
    }

    @Test
    @DisplayName("a branch manager may hand out a branch-staff role")
    void branchManagerAssignsBranchRole() {
        UUID ownBranch = UUID.randomUUID();
        plantedBranchManager(ownBranch);
        User own = existing("asha@legacy.test");
        own.setBranchId(ownBranch);
        UUID roleId = UUID.randomUUID();
        CompanyRole booking = role("BOOKING_OPERATOR", true);
        when(userRepository.findByIdWithinCompany(own.getId(), TENANT)).thenReturn(Optional.of(own));
        when(roleRepository.findByIdWithinCompany(roleId, TENANT)).thenReturn(Optional.of(booking));
        when(userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(own.getId())).thenReturn(List.of());

        service.assignRole(own.getId(), roleId);

        verify(userRoleRepository).save(any());
    }

    @Test
    @DisplayName("a branch manager may not hand out COMPANY_ADMIN")
    void branchManagerCannotAssignCompanyAdmin() {
        UUID ownBranch = UUID.randomUUID();
        plantedBranchManager(ownBranch);
        User own = existing("asha@legacy.test");
        own.setBranchId(ownBranch);
        UUID roleId = UUID.randomUUID();
        CompanyRole admin = role("COMPANY_ADMIN", true);
        when(userRepository.findByIdWithinCompany(own.getId(), TENANT)).thenReturn(Optional.of(own));
        when(roleRepository.findByIdWithinCompany(roleId, TENANT)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.assignRole(own.getId(), roleId))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("COMPANY_ADMIN");
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("a branch manager may hand out the company's own custom role")
    void branchManagerAssignsCustomRole() {
        UUID ownBranch = UUID.randomUUID();
        plantedBranchManager(ownBranch);
        User own = existing("asha@legacy.test");
        own.setBranchId(ownBranch);
        UUID roleId = UUID.randomUUID();
        CompanyRole custom = role("COUNTER_LEAD", false);
        when(userRepository.findByIdWithinCompany(own.getId(), TENANT)).thenReturn(Optional.of(own));
        when(roleRepository.findByIdWithinCompany(roleId, TENANT)).thenReturn(Optional.of(custom));
        when(userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(own.getId())).thenReturn(List.of());

        service.assignRole(own.getId(), roleId);

        verify(userRoleRepository).save(any());
    }

    // ------------------------------------------------------------------- update

    private User existing(String email) {
        User user = User.builder()
                .email(email).username("asha.nair").employeeCode("EMP001")
                .firstName("Asha").lastName("Nair").passwordHash("h").status(UserStatus.ACTIVE)
                .build();
        user.applyInvariants();
        user.setCompanyId(TENANT);
        user.setVersion(2L);
        return user;
    }

    private UpdateUserCommand updateCommand(String firstName, Long version) {
        return new UpdateUserCommand(firstName, null, "Nair", null, "+91 90000", null,
                null, null, "Manager", "Ops", null, null, null, null, null, null, version);
    }

    @Test
    @DisplayName("update applies changes when the version matches")
    void updateApplies() {
        User user = existing("asha@legacy.test");
        when(userRepository.findByIdWithinCompany(user.getId(), TENANT)).thenReturn(Optional.of(user));

        User saved = service.update(user.getId(), updateCommand("Ashaa", 2L));

        assertThat(saved.getFirstName()).isEqualTo("Ashaa");
        assertThat(saved.getDesignation()).isEqualTo("Manager");
    }

    @Test
    @DisplayName("update rejects a stale version")
    void updateStaleVersion() {
        User user = existing("asha@legacy.test");
        when(userRepository.findByIdWithinCompany(user.getId(), TENANT)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.update(user.getId(), updateCommand("X", 1L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("update loads within the company, so a foreign id is a 404")
    void updateCompanyScoped() {
        UUID foreign = UUID.randomUUID();
        when(userRepository.findByIdWithinCompany(foreign, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(foreign, updateCommand("X", 0L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).findById(any());
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    @DisplayName("deactivate is refused on your own account")
    void cannotDeactivateSelf() {
        User self = existing("admin@legacy.test");
        self.setId(CALLER);
        when(userRepository.findByIdWithinCompany(CALLER, TENANT)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.deactivate(CALLER))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("your own account");
    }

    @Test
    @DisplayName("lock and unlock flip the flag and status, and are idempotent")
    void lockUnlock() {
        User user = existing("asha@legacy.test");
        when(userRepository.findByIdWithinCompany(user.getId(), TENANT)).thenReturn(Optional.of(user));

        User locked = service.lock(user.getId(), "sharing creds");
        assertThat(locked.isLocked()).isTrue();
        assertThat(locked.getStatus()).isEqualTo(UserStatus.LOCKED);

        User unlocked = service.unlock(user.getId());
        assertThat(unlocked.isLocked()).isFalse();
        assertThat(unlocked.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("activate on an already-active, unlocked user is a no-op")
    void activateIdempotent() {
        User user = existing("asha@legacy.test");
        when(userRepository.findByIdWithinCompany(user.getId(), TENANT)).thenReturn(Optional.of(user));

        service.activate(user.getId());

        verify(userRepository, never()).save(any());
        verify(auditService, never()).record(eq(AuditAction.USER_ACTIVATED), any(), any(), any());
    }

    // ---------------------------------------------------------------- passwords

    @Test
    @DisplayName("reset re-enables a PENDING account and audits the reset")
    void resetPassword() {
        User user = existing("asha@legacy.test");
        user.setStatus(UserStatus.PENDING);
        when(userRepository.findByIdWithinCompany(user.getId(), TENANT)).thenReturn(Optional.of(user));

        service.resetPassword(user.getId(), "NewStr0ng!99", true);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(passwordEncoder).encode("NewStr0ng!99");
        verify(auditService).record(eq(AuditAction.USER_PASSWORD_RESET), any(), any(), any());
    }

    @Test
    @DisplayName("self change-password rejects acting on someone else")
    void changePasswordOnlySelf() {
        UUID otherId = UUID.randomUUID();
        assertThatThrownBy(() -> service.changePassword(otherId, "old", "NewStr0ng!99"))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("your own");
    }

    @Test
    @DisplayName("self change-password rejects a wrong current password")
    void changePasswordWrongCurrent() {
        User self = existing("admin@legacy.test");
        self.setId(CALLER);
        when(userRepository.findByIdWithinCompany(CALLER, TENANT)).thenReturn(Optional.of(self));
        when(passwordEncoder.matches("wrong", "h")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(CALLER, "wrong", "NewStr0ng!99"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("current password");
    }

    // -------------------------------------------------------------------- roles

    @Test
    @DisplayName("assigning a role is idempotent")
    void assignRoleIdempotent() {
        User user = existing("asha@legacy.test");
        UUID roleId = UUID.randomUUID();
        CompanyRole viewer = role("VIEWER");
        when(userRepository.findByIdWithinCompany(user.getId(), TENANT)).thenReturn(Optional.of(user));
        when(roleRepository.findByIdWithinCompany(roleId, TENANT)).thenReturn(Optional.of(viewer));
        when(userRoleRepository.existsByUserIdAndRoleId(user.getId(), roleId)).thenReturn(true);
        when(userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(user.getId()))
                .thenReturn(List.of(UserRole.assign(user.getId(), viewer)));

        service.assignRole(user.getId(), roleId);

        // Already held: no new row, no event.
        verify(userRoleRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("removing a role the user does not hold is a no-op")
    void removeRoleIdempotent() {
        User user = existing("asha@legacy.test");
        UUID roleId = UUID.randomUUID();
        when(userRepository.findByIdWithinCompany(user.getId(), TENANT)).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserIdAndRoleId(user.getId(), roleId)).thenReturn(Optional.empty());

        service.removeRole(user.getId(), roleId);

        verify(userRoleRepository, never()).save(any());
    }

    // ------------------------------------------------------------------- delete

    @Test
    @DisplayName("delete is soft, deactivates, and never hard-deletes")
    void deleteIsSoft() {
        User user = existing("asha@legacy.test");
        when(userRepository.findByIdWithinCompany(user.getId(), TENANT)).thenReturn(Optional.of(user));

        service.delete(user.getId());

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(userRepository).save(user);
        verify(userRepository, never()).delete(any(User.class));
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("you cannot delete your own account")
    void cannotDeleteSelf() {
        User self = existing("admin@legacy.test");
        self.setId(CALLER);
        when(userRepository.findByIdWithinCompany(CALLER, TENANT)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.delete(CALLER))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("your own account");
    }

    // -------------------------------------------------------------------- counts

    @Test
    @DisplayName("role counts are batched per user")
    void roleCounts() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        CompanyRole r = role("VIEWER");
        when(userRoleRepository.findAllByUserIdIn(any())).thenReturn(List.of(
                UserRole.assign(u1, r), UserRole.assign(u1, r), UserRole.assign(u2, r)));

        assertThat(service.roleCounts(List.of(u1, u2)))
                .containsEntry(u1, 2).containsEntry(u2, 1);
        assertThat(service.roleCounts(List.of())).isEmpty();
    }
}

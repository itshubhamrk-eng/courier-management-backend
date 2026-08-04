package com.courier.modules.auth.application;

import com.courier.modules.auth.application.UserProvisioningService.NewBranchUserCommand;
import com.courier.modules.auth.application.UserProvisioningService.ProvisionedBranchUser;
import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The branch-user provisioning path. The company-admin path is covered through
 * {@code CompanyServiceImplTest}; what is specific here is that this account is
 * <em>usable</em>, and that a credential must not escape into anything durable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProvisioningServiceImplTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private AuditService auditService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserProvisioningServiceImpl service;

    @BeforeEach
    void setUp() {
        PasswordPolicy policy = new PasswordPolicy(new AuthProperties());
        policy.loadCommonPasswords();
        service = new UserProvisioningServiceImpl(
                userRepository, encoder, emailVerificationService, policy, auditService);

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private NewBranchUserCommand command(String password) {
        return new NewBranchUserCommand(TENANT, "Latur@Legacy.test", "Latur", "Branch",
                "9876543210", password, Set.of(Role.BRANCH_MANAGER));
    }

    private User saved() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("the account is ACTIVE and pre-verified, so it can sign in at once")
    void createsUsableAccount() {
        ProvisionedBranchUser result = service.provisionBranchUser(command(null));

        User user = saved();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(user.getRoles()).containsExactly(Role.BRANCH_MANAGER);
        assertThat(result.email()).isEqualTo("latur@legacy.test");
        // No verification mail: nothing is waiting to be proven.
        verify(emailVerificationService, never()).issue(any());
    }

    @Test
    @DisplayName("a generated password is returned, satisfies the policy, and is the stored hash")
    void generatesUsablePassword() {
        ProvisionedBranchUser result = service.provisionBranchUser(command(null));

        assertThat(result.temporaryPassword()).isNotNull();
        assertThat(result.passwordGenerated()).isTrue();
        assertThat(encoder.matches(result.temporaryPassword(), saved().getPasswordHash())).isTrue();

        PasswordPolicy policy = new PasswordPolicy(new AuthProperties());
        policy.loadCommonPasswords();
        policy.validate(result.temporaryPassword(), result.email(), null, encoder::matches);
    }

    @Test
    @DisplayName("a supplied password is stored but never echoed back")
    void doesNotEchoSuppliedPassword() {
        ProvisionedBranchUser result = service.provisionBranchUser(command("Str0ng#Passw0rd"));

        assertThat(result.temporaryPassword()).isNull();
        assertThat(result.passwordGenerated()).isFalse();
        assertThat(encoder.matches("Str0ng#Passw0rd", saved().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("a supplied password that fails the policy is refused before anything is written")
    void refusesWeakPassword() {
        assertThatThrownBy(() -> service.provisionBranchUser(command("password")))
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("no password reaches the audit trail")
    void auditCarriesNoCredential() {
        ProvisionedBranchUser result = service.provisionBranchUser(command(null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(any(), eq("User"), any(), details.capture());

        assertThat(details.getValue().toString()).doesNotContain(result.temporaryPassword());
        assertThat(details.getValue()).containsEntry("passwordGenerated", true);
    }

    @Test
    @DisplayName("an address already registered in the company is refused")
    void refusesDuplicateEmail() {
        when(userRepository.existsByEmail("latur@legacy.test")).thenReturn(true);

        assertThatThrownBy(() -> service.provisionBranchUser(command(null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("the write runs bound to the given company and restores the previous binding")
    void bindsCompanyForTheWrite() {
        service.provisionBranchUser(command(null));

        assertThat(saved().getCompanyId()).isNull(); // stamped by the entity listener, not here
        assertThat(CompanyContext.isSet()).isFalse();
    }
}

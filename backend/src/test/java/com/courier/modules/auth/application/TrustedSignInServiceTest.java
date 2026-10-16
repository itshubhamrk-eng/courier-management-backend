package com.courier.modules.auth.application;

import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.application.port.UserPermissionsPort;
import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrustedSignInServiceTest {

    @Mock UserRepository userRepository;
    @Mock CompanyDirectoryPort companyDirectory;
    @Mock UserPermissionsPort permissions;
    @Mock SessionService sessionService;
    @Mock TokenIssuer tokenIssuer;
    @Mock JwtTokenProvider jwt;
    @Mock UserProvisioningService provisioning;
    @Mock LoginAttemptService attempts;

    TrustedSignInService service;
    UUID companyId = UUID.randomUUID();
    UserSession session = org.mockito.Mockito.mock(UserSession.class);
    TokenIssuer.TokenPair tokens = org.mockito.Mockito.mock(TokenIssuer.TokenPair.class);

    @BeforeEach
    void setUp() {
        service = new TrustedSignInService(userRepository, companyDirectory, permissions, sessionService, tokenIssuer,
                jwt, provisioning, attempts);
        when(companyDirectory.findById(companyId))
                .thenReturn(Optional.of(new CompanyDirectoryPort.CompanyRef(companyId, "Acme", true, null, null)));
        when(jwt.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(sessionService.openSession(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any())).thenReturn(session);
        when(tokenIssuer.issueForNewSession(any(), any(), any(), any(), any())).thenReturn(tokens);
        when(permissions.resolveEffectivePermissions(any(), any())).thenReturn(Set.of("X"));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private User user(UserStatus status, Role... roles) {
        User u = User.builder().email("asha@acme.test").passwordHash("h").status(status)
                .roles(EnumSet.copyOf(java.util.List.of(roles))).emailVerified(status == UserStatus.ACTIVE).build();
        u.setId(UUID.randomUUID());
        u.setCompanyId(companyId);
        return u;
    }

    private TrustedSignInService.Command command() {
        return new TrustedSignInService.Command(companyId, "Asha@Acme.test", "Asha", "Rao", EnumSet.of(Role.VIEWER),
                "1.2.3.4", "ua", SessionService.DeviceInfo.of(null, null, "1.2.3.4", "ua"));
    }

    @Test
    void signsInAnExistingActiveUserWithoutCreatingOne() {
        User existing = user(UserStatus.ACTIVE, Role.OPERATOR);
        when(userRepository.findByEmail("asha@acme.test")).thenReturn(Optional.of(existing));

        AuthService.AuthResult result = service.signIn(command());

        assertThat(result.user()).isSameAs(existing);
        assertThat(result.permissions()).containsExactly("X");
        verify(provisioning, never()).provisionPlatformUser(any());
    }

    @Test
    void createsTheUserOnFirstSignInWithTheGivenRoles() {
        User created = user(UserStatus.ACTIVE, Role.VIEWER);
        when(userRepository.findByEmail("asha@acme.test")).thenReturn(Optional.empty(), Optional.of(created));

        service.signIn(command());

        verify(provisioning).provisionPlatformUser(new UserProvisioningService.NewPlatformUserCommand(
                companyId, "asha@acme.test", "Asha", "Rao", EnumSet.of(Role.VIEWER)));
    }

    @Test
    void activatesAPendingUserBecauseThePlatformProvedTheAddress() {
        User pending = user(UserStatus.PENDING, Role.COMPANY_ADMIN);
        when(userRepository.findByEmail("asha@acme.test")).thenReturn(Optional.of(pending));
        when(userRepository.save(pending)).thenReturn(pending);

        service.signIn(command());

        assertThat(pending.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(pending.isEmailVerified()).isTrue();
    }

    @Test
    void neverSignsInPlatformScopedOrDisabledOrLockedAccounts() {
        for (User blocked : new User[] {user(UserStatus.ACTIVE, Role.SUPER_ADMIN), user(UserStatus.ACTIVE, Role.PLATFORM_ADMIN),
                user(UserStatus.DISABLED, Role.OPERATOR), user(UserStatus.LOCKED, Role.OPERATOR)}) {
            when(userRepository.findByEmail("asha@acme.test")).thenReturn(Optional.of(blocked));
            assertThatThrownBy(() -> service.signIn(command())).isInstanceOf(ForbiddenException.class);
        }
        verify(sessionService, never()).openSession(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void refusesAnInactiveCompany() {
        when(companyDirectory.findById(companyId))
                .thenReturn(Optional.of(new CompanyDirectoryPort.CompanyRef(companyId, "Acme", false, null, null)));
        assertThatThrownBy(() -> service.signIn(command())).isInstanceOf(ForbiddenException.class);
    }
}

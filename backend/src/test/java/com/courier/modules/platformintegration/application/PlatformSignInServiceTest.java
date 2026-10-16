package com.courier.modules.platformintegration.application;

import com.courier.modules.auth.application.AuthService;
import com.courier.modules.auth.application.SessionService;
import com.courier.modules.auth.application.TrustedSignInService;
import com.courier.modules.auth.domain.Role;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.platformintegration.infrastructure.PlatformIntegrationProperties;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Map;
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
class PlatformSignInServiceTest {

    private static final UUID PLATFORM_COMPANY = UUID.randomUUID();
    private static final UUID COURIER_COMPANY = UUID.randomUUID();

    @Mock JwtDecoder decoder;
    @Mock CompanyRepository companyRepository;
    @Mock CoreAccessClient core;
    @Mock TrustedSignInService trusted;

    PlatformSignInService service;

    @BeforeEach
    void setUp() {
        service = new PlatformSignInService(decoder, companyRepository, core, trusted,
                new PlatformIntegrationProperties(true, "iss", "jwks", "courier-api", "occpl-core-services", "STARTER",
                        null, "courier-web", "http://core", "http://token", "courier-service", "secret"));
        Company company = Company.builder().companyId(COURIER_COMPANY).build();
        when(companyRepository.findByExternalCompanyId(PLATFORM_COMPANY)).thenReturn(Optional.of(company));
        when(core.check(PLATFORM_COMPANY, "user-1")).thenReturn(new CoreAccessClient.Decision(true, "OK", Set.of("COMPANY_ADMIN")));
    }

    private static Jwt token(Map<String, Object> extra) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject("user-1")
                .issuedAt(Instant.now().minusSeconds(5)).expiresAt(Instant.now().plusSeconds(300))
                .claim("azp", "courier-web").claim("email", "Asha@Acme.test").claim("email_verified", true)
                .claim("name", "Asha Rao");
        extra.forEach(b::claim);
        return b.build();
    }

    private PlatformSignInService.Request request() {
        return new PlatformSignInService.Request("raw", PLATFORM_COMPANY, "1.2.3.4", "ua",
                SessionService.DeviceInfo.of(null, null, "1.2.3.4", "ua"));
    }

    @Test
    void signsInAnAllowedPersonWithTheirPlatformNameAndRole() {
        when(decoder.decode("raw")).thenReturn(token(Map.of()));
        AuthService.AuthResult expected = new AuthService.AuthResult(null, null, null, null, Set.of());
        when(trusted.signIn(any())).thenReturn(expected);

        assertThat(service.signIn(request())).isSameAs(expected);

        ArgumentCaptor<TrustedSignInService.Command> cmd = ArgumentCaptor.forClass(TrustedSignInService.Command.class);
        verify(trusted).signIn(cmd.capture());
        assertThat(cmd.getValue().companyId()).isEqualTo(COURIER_COMPANY);
        assertThat(cmd.getValue().email()).isEqualTo("Asha@Acme.test");
        assertThat(cmd.getValue().firstName()).isEqualTo("Asha");
        assertThat(cmd.getValue().lastName()).isEqualTo("Rao");
        assertThat(cmd.getValue().rolesIfNew()).containsExactly(Role.COMPANY_ADMIN);
    }

    @Test
    void otherMembersStartAsViewers() {
        assertThat(PlatformSignInService.rolesFor(Set.of("COMPANY_USER"))).containsExactly(Role.VIEWER);
        assertThat(PlatformSignInService.rolesFor(Set.of())).containsExactly(Role.VIEWER);
        assertThat(PlatformSignInService.rolesFor(null)).containsExactly(Role.VIEWER);
    }

    @Test
    void rejectsTokensThatAreInvalid() {
        when(decoder.decode("raw")).thenThrow(new JwtException("bad"));
        assertThatThrownBy(() -> service.signIn(request())).isInstanceOf(UnauthorizedException.class);
        verify(trusted, never()).signIn(any());
    }

    @Test
    void rejectsTokensFromAnotherClientServiceTokensAndUnverifiedEmails() {
        for (Map<String, Object> bad : new Map[] {Map.of("azp", "some-other-client"), Map.of("client_id", "occpl-core-services"),
                Map.of("email_verified", false), Map.of("email", "")}) {
            when(decoder.decode("raw")).thenReturn(token(bad));
            assertThatThrownBy(() -> service.signIn(request())).isInstanceOf(UnauthorizedException.class);
        }
        verify(trusted, never()).signIn(any());
    }

    @Test
    void refusesWhenThePlatformSaysNoOrTheCompanyIsUnknownWithTheSameAnswer() {
        when(decoder.decode("raw")).thenReturn(token(Map.of()));
        when(core.check(PLATFORM_COMPANY, "user-1")).thenReturn(CoreAccessClient.Decision.denied("NOT_A_MEMBER"));
        assertThatThrownBy(() -> service.signIn(request())).isInstanceOf(ForbiddenException.class)
                .hasMessage("Sign-in through the platform is not available for this account.");

        when(companyRepository.findByExternalCompanyId(PLATFORM_COMPANY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.signIn(request())).isInstanceOf(ForbiddenException.class)
                .hasMessage("Sign-in through the platform is not available for this account.");
        verify(trusted, never()).signIn(any());
    }

    @Test
    void reportsATemporaryFailureWhenThePlatformCannotBeReached() {
        when(decoder.decode("raw")).thenReturn(token(Map.of()));
        when(core.check(PLATFORM_COMPANY, "user-1")).thenThrow(new IllegalStateException("down"));
        assertThatThrownBy(() -> service.signIn(request())).isInstanceOf(BusinessRuleException.class);
        verify(trusted, never()).signIn(any());
    }
}

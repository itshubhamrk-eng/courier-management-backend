package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.SessionRefreshEvent;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.domain.IncidentRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionMultiIpRuleTest {

    @Mock RefreshTokenRepository refreshTokenRepository;

    final Instant from = Instant.parse("2026-11-05T10:00:00Z");
    final Instant to = Instant.parse("2026-11-05T10:10:00Z");
    final UUID company = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();

    private SessionRefreshEvent ev(UUID session, String ip) {
        return new SessionRefreshEvent(company, user, session, "ua", ip);
    }

    private SessionMultiIpRule rule() {
        return new SessionMultiIpRule(refreshTokenRepository);
    }

    @Test
    void codeIsSessionMultiIp() {
        assertThat(rule().code()).isEqualTo(IncidentRule.SESSION_MULTI_IP);
    }

    @Test
    void noEventsRaisesNothing() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of());

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void twoDistinctPrefixesRaiseWithBoundedEvidence() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to))
                .thenReturn(List.of(ev(sessionId, "1.1.1.1"), ev(sessionId, "2.2.2.2")));

        List<IncidentRequest> out = rule().scan(from, to);

        assertThat(out).hasSize(1);
        IncidentRequest r = out.get(0);
        assertThat(r.rule()).isEqualTo(IncidentRule.SESSION_MULTI_IP);
        assertThat(r.userId()).isEqualTo(user);
        assertThat(r.companyId()).isEqualTo(company);
        assertThat(r.evidence()).containsEntry("sessionId", sessionId.toString())
                .containsEntry("distinctPrefixCount", 2)
                .containsEntry("prefixes", List.of("1.1.1", "2.2.2"))
                .containsEntry("windowFrom", from.toString())
                .containsEntry("windowTo", to.toString());
    }

    @Test
    void sameSlash24IsOneNetwork() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to))
                .thenReturn(List.of(ev(sessionId, "1.1.1.1"), ev(sessionId, "1.1.1.99")));

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void privateAndLoopbackAddressesAreIgnored() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to))
                .thenReturn(List.of(ev(sessionId, "1.1.1.1"), ev(sessionId, "10.0.0.5"), ev(sessionId, "127.0.0.1")));

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void differentSessionsAreNotMixed() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to))
                .thenReturn(List.of(ev(sessionId, "1.1.1.1"), ev(UUID.randomUUID(), "2.2.2.2")));

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void evidenceListsAtMostFivePrefixes() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of(
                ev(sessionId, "1.1.1.1"), ev(sessionId, "2.2.2.2"), ev(sessionId, "3.3.3.3"),
                ev(sessionId, "4.4.4.4"), ev(sessionId, "5.5.5.5"), ev(sessionId, "6.6.6.6"),
                ev(sessionId, "7.7.7.7")));

        IncidentRequest r = rule().scan(from, to).get(0);

        assertThat(r.evidence()).containsEntry("distinctPrefixCount", 7);
        assertThat((List<?>) r.evidence().get("prefixes")).hasSize(5);
    }

    @Test
    void oneRequestPerUserWhenSeveralSessionsQualify() {
        UUID other = UUID.randomUUID();
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of(
                ev(sessionId, "1.1.1.1"), ev(sessionId, "2.2.2.2"),
                ev(other, "3.3.3.3"), ev(other, "4.4.4.4")));

        assertThat(rule().scan(from, to)).hasSize(1);
    }
}

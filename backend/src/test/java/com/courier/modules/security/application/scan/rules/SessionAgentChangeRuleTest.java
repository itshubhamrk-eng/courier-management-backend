package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.SessionRefreshEvent;
import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserSessionRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The browser/OS comparison runs the real {@code UserAgentParser} on real-shaped user
 * agent strings; only the repositories are mocked.
 */
@ExtendWith(MockitoExtension.class)
class SessionAgentChangeRuleTest {

    static final String CHROME_WIN = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    static final String FIREFOX_WIN = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0";
    static final String CHROME_LINUX = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock UserSessionRepository userSessionRepository;

    final Instant from = Instant.parse("2026-11-05T10:00:00Z");
    final Instant to = Instant.parse("2026-11-05T10:10:00Z");
    final UUID company = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();

    private SessionAgentChangeRule rule() {
        return new SessionAgentChangeRule(refreshTokenRepository, userSessionRepository);
    }

    private UserSession session(UUID owner, String browser, String os) {
        UserSession s = UserSession.builder().userId(owner).browser(browser).os(os).build();
        s.setId(sessionId);
        return s;
    }

    private SessionRefreshEvent event(String ua) {
        return new SessionRefreshEvent(company, user, sessionId, ua, "8.8.8.8");
    }

    @Test
    void codeIsSessionAgentChange() {
        assertThat(rule().code()).isEqualTo(IncidentRule.SESSION_AGENT_CHANGE);
    }

    @Test
    void sameFingerprintRaisesNothing() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of(event(CHROME_WIN)));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of(session(user, "Chrome", "Windows")));

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void changedBrowserRaisesOneRequestWithBothValues() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of(event(FIREFOX_WIN)));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of(session(user, "Chrome", "Windows")));

        List<IncidentRequest> out = rule().scan(from, to);

        assertThat(out).hasSize(1);
        IncidentRequest r = out.get(0);
        assertThat(r.rule()).isEqualTo(IncidentRule.SESSION_AGENT_CHANGE);
        assertThat(r.userId()).isEqualTo(user);
        assertThat(r.companyId()).isEqualTo(company);
        assertThat(r.evidence()).containsEntry("loginBrowser", "Chrome")
                .containsEntry("seenBrowser", "Firefox")
                .containsEntry("loginOs", "Windows")
                .containsEntry("seenOs", "Windows")
                .containsEntry("sessionId", sessionId.toString());
    }

    @Test
    void changedOsRaises() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of(event(CHROME_LINUX)));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of(session(user, "Chrome", "Windows")));

        assertThat(rule().scan(from, to)).hasSize(1);
    }

    @Test
    void userAgentInEvidenceIsTruncatedTo255() {
        String longUa = FIREFOX_WIN + " x".repeat(400);
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of(event(longUa)));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of(session(user, "Chrome", "Windows")));

        Object ua = rule().scan(from, to).get(0).evidence().get("userAgent");

        assertThat((String) ua).hasSize(255);
    }

    @Test
    void unparseableAgentIsNotTreatedAsAChange() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of(event(null)));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of(session(user, "Chrome", "Windows")));

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void sessionRowMissingIsSkipped() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of(event(FIREFOX_WIN)));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of());

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void sessionOwnedByAnotherUserIsSkipped() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of(event(FIREFOX_WIN)));
        when(userSessionRepository.findAllById(any()))
                .thenReturn(List.of(session(UUID.randomUUID(), "Chrome", "Windows")));

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void oneRequestPerUserEvenWithSeveralMismatchingEvents() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to))
                .thenReturn(List.of(event(FIREFOX_WIN), event(CHROME_LINUX)));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of(session(user, "Chrome", "Windows")));

        assertThat(rule().scan(from, to)).hasSize(1);
    }
}

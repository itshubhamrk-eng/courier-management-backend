package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserSessionRepository;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.shared.activity.domain.ActivityLogRepository;
import com.courier.shared.activity.domain.SessionFingerprint;
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

@ExtendWith(MockitoExtension.class)
class SessionAgentChangeRuleTest {

    @Mock ActivityLogRepository activityLogRepository;
    @Mock UserSessionRepository userSessionRepository;

    final Instant from = Instant.parse("2026-11-05T10:00:00Z");
    final Instant to = Instant.parse("2026-11-05T10:02:00Z");
    final UUID company = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();

    private SessionAgentChangeRule rule() {
        return new SessionAgentChangeRule(activityLogRepository, userSessionRepository);
    }

    private UserSession session(String browser, String os) {
        UserSession s = UserSession.builder().userId(user).browser(browser).os(os).build();
        s.setId(sessionId);
        return s;
    }

    @Test
    void codeIsSessionAgentChange() {
        assertThat(rule().code()).isEqualTo(IncidentRule.SESSION_AGENT_CHANGE);
    }

    @Test
    void sameFingerprintRaisesNothing() {
        when(activityLogRepository.findSessionFingerprints(from, to))
                .thenReturn(List.of(new SessionFingerprint(company, user, sessionId, "Chrome", "Windows")));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of(session("chrome", "Windows")));

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void changedBrowserRaisesOneRequestWithBothValues() {
        when(activityLogRepository.findSessionFingerprints(from, to))
                .thenReturn(List.of(new SessionFingerprint(company, user, sessionId, "Firefox", "Windows")));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of(session("Chrome", "Windows")));

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
        when(activityLogRepository.findSessionFingerprints(from, to))
                .thenReturn(List.of(new SessionFingerprint(company, user, sessionId, "Chrome", "Linux")));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of(session("Chrome", "Windows")));

        assertThat(rule().scan(from, to)).hasSize(1);
    }

    @Test
    void unknownOrMissingValuesAreNotTreatedAsAChange() {
        when(activityLogRepository.findSessionFingerprints(from, to))
                .thenReturn(List.of(new SessionFingerprint(company, user, sessionId, "Unknown", null)));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of(session("Chrome", "Windows")));

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void sessionRowMissingIsSkipped() {
        when(activityLogRepository.findSessionFingerprints(from, to))
                .thenReturn(List.of(new SessionFingerprint(company, user, sessionId, "Firefox", "Windows")));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of());

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void oneRequestPerUserEvenWithSeveralMismatchingRows() {
        when(activityLogRepository.findSessionFingerprints(from, to)).thenReturn(List.of(
                new SessionFingerprint(company, user, sessionId, "Firefox", "Windows"),
                new SessionFingerprint(company, user, sessionId, "Safari", "Windows")));
        when(userSessionRepository.findAllById(any())).thenReturn(List.of(session("Chrome", "Windows")));

        assertThat(rule().scan(from, to)).hasSize(1);
    }
}

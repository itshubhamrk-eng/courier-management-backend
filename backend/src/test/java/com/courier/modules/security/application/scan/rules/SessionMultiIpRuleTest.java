package com.courier.modules.security.application.scan.rules;

import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.shared.activity.domain.ActivityLogRepository;
import com.courier.shared.activity.domain.SessionIpSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionMultiIpRuleTest {

    @Mock ActivityLogRepository activityLogRepository;

    final Instant from = Instant.parse("2026-11-05T10:00:00Z");
    final Instant to = Instant.parse("2026-11-05T10:02:00Z");
    final UUID company = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();

    @Test
    void codeIsSessionMultiIp() {
        assertThat(new SessionMultiIpRule(activityLogRepository).code()).isEqualTo(IncidentRule.SESSION_MULTI_IP);
    }

    @Test
    void noMultiIpSessionRaisesNothing() {
        when(activityLogRepository.findMultiIpSessions(from, to)).thenReturn(List.of());

        assertThat(new SessionMultiIpRule(activityLogRepository).scan(from, to)).isEmpty();
    }

    @Test
    void sessionWithTwoIpsRaisesWithBoundedEvidence() {
        when(activityLogRepository.findMultiIpSessions(from, to))
                .thenReturn(List.of(new SessionIpSummary(company, user, sessionId, 2)));
        when(activityLogRepository.findDistinctIps(eq(sessionId), eq(from), eq(to), any()))
                .thenReturn(List.of("1.1.1.1", "2.2.2.2"));

        List<IncidentRequest> out = new SessionMultiIpRule(activityLogRepository).scan(from, to);

        assertThat(out).hasSize(1);
        IncidentRequest r = out.get(0);
        assertThat(r.rule()).isEqualTo(IncidentRule.SESSION_MULTI_IP);
        assertThat(r.userId()).isEqualTo(user);
        assertThat(r.companyId()).isEqualTo(company);
        assertThat(r.evidence()).containsEntry("sessionId", sessionId.toString())
                .containsEntry("distinctIpCount", 2L)
                .containsEntry("ips", List.of("1.1.1.1", "2.2.2.2"))
                .containsEntry("windowFrom", from.toString())
                .containsEntry("windowTo", to.toString());
    }

    @Test
    void oneRequestPerUserWhenSeveralSessionsQualify() {
        UUID other = UUID.randomUUID();
        when(activityLogRepository.findMultiIpSessions(from, to)).thenReturn(List.of(
                new SessionIpSummary(company, user, sessionId, 2),
                new SessionIpSummary(company, user, other, 3)));
        when(activityLogRepository.findDistinctIps(any(), eq(from), eq(to), any())).thenReturn(List.of("1.1.1.1"));

        assertThat(new SessionMultiIpRule(activityLogRepository).scan(from, to)).hasSize(1);
    }
}

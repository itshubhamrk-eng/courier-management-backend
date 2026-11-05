package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.LoginHistoryRepository;
import com.courier.modules.auth.domain.UserEventCount;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityProperties;
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
class RevokedTokenReplayRuleTest {

    @Mock LoginHistoryRepository loginHistoryRepository;

    final Instant from = Instant.parse("2026-11-05T10:00:00Z");
    final Instant to = Instant.parse("2026-11-05T10:02:00Z");
    final UUID company = UUID.randomUUID();
    final UUID user = UUID.randomUUID();

    private RevokedTokenReplayRule rule(int threshold) {
        SecurityProperties props = new SecurityProperties();
        props.setRevokedReplayThreshold(threshold);
        return new RevokedTokenReplayRule(loginHistoryRepository, props);
    }

    @Test
    void defaultThresholdIsFive() {
        assertThat(new SecurityProperties().getRevokedReplayThreshold()).isEqualTo(5);
    }

    @Test
    void codeIsRevokedTokenReplay() {
        assertThat(rule(5).code()).isEqualTo(IncidentRule.REVOKED_TOKEN_REPLAY);
    }

    @Test
    void queriesWithConfiguredThresholdAndRaisesForEachUserAboveIt() {
        when(loginHistoryRepository.findUsersWithRevokedSessionRefreshAbove(from, to, 3L))
                .thenReturn(List.of(new UserEventCount(company, user, 4)));

        List<IncidentRequest> out = rule(3).scan(from, to);

        assertThat(out).hasSize(1);
        IncidentRequest r = out.get(0);
        assertThat(r.rule()).isEqualTo(IncidentRule.REVOKED_TOKEN_REPLAY);
        assertThat(r.userId()).isEqualTo(user);
        assertThat(r.companyId()).isEqualTo(company);
        assertThat(r.evidence()).containsEntry("rejections", 4L)
                .containsEntry("threshold", 3)
                .containsEntry("windowFrom", from.toString())
                .containsEntry("windowTo", to.toString());
    }

    @Test
    void nobodyAboveThresholdRaisesNothing() {
        when(loginHistoryRepository.findUsersWithRevokedSessionRefreshAbove(from, to, 5L)).thenReturn(List.of());

        assertThat(rule(5).scan(from, to)).isEmpty();
    }
}

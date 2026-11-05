package com.courier.modules.security.application.scan.rules;

import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.shared.activity.domain.ActivityLogRepository;
import com.courier.shared.activity.domain.UserEndpointCount;
import com.courier.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessDeniedBurstRuleTest {

    @Mock ActivityLogRepository activityLogRepository;

    final Instant from = Instant.parse("2026-11-05T10:00:00Z");
    final Instant to = Instant.parse("2026-11-05T10:10:00Z");
    final UUID company = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final SecurityProperties props = new SecurityProperties();

    /** The only message both 403 paths (URL rule and @PreAuthorize) write into error_message. */
    static final String DENIED = "You do not have permission to perform this action";

    private AccessDeniedBurstRule rule() {
        return new AccessDeniedBurstRule(activityLogRepository, props);
    }

    private void stub(List<UserEndpointCount> rows) {
        when(activityLogRepository.findFailureCountsByErrorMessage(from, to, DENIED)).thenReturn(rows);
    }

    @Test
    void codeDefaultsAndMatchedMessage() {
        assertThat(rule().code()).isEqualTo(IncidentRule.ACCESS_DENIED_BURST);
        assertThat(IncidentRule.ACCESS_DENIED_BURST.autoBan()).isFalse();
        assertThat(props.getAccessDenied().getThreshold()).isEqualTo(20);
        assertThat(ErrorCode.ACCESS_DENIED.getDefaultMessage()).isEqualTo(DENIED);
    }

    @Test
    void emptyDataRaisesNothing() {
        stub(List.of());

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void atThresholdRaisesNothing() {
        stub(List.of(new UserEndpointCount(company, user, "POST", "/api/v1/rates", 20)));

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void aboveThresholdRaisesOneRequestWithCountsAndEndpoints() {
        stub(List.of(new UserEndpointCount(company, user, "POST", "/api/v1/rates", 15),
                new UserEndpointCount(company, user, "GET", "/api/v1/users/{id}", 6)));

        List<IncidentRequest> out = rule().scan(from, to);

        assertThat(out).singleElement().satisfies(r -> {
            assertThat(r.rule()).isEqualTo(IncidentRule.ACCESS_DENIED_BURST);
            assertThat(r.userId()).isEqualTo(user);
            assertThat(r.companyId()).isEqualTo(company);
            assertThat(r.evidence())
                    .containsEntry("denials", 21L)
                    .containsEntry("threshold", 20)
                    .containsEntry("distinctEndpoints", 2)
                    .containsEntry("topEndpoints", Map.of(
                            "POST /api/v1/rates", 15L, "GET /api/v1/users/{id}", 6L))
                    .containsEntry("windowFrom", from.toString())
                    .containsEntry("windowTo", to.toString());
        });
    }

    @Test
    void evidenceListsAtMostFiveEndpointsMostDeniedFirst() {
        List<UserEndpointCount> rows = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            rows.add(new UserEndpointCount(company, user, "GET", "/api/v1/e" + i + "/{id}", i * 2L));
        }
        stub(rows);

        Map<?, ?> top = (Map<?, ?>) rule().scan(from, to).get(0).evidence().get("topEndpoints");

        assertThat(top).hasSize(5);
        assertThat(top.keySet().iterator().next()).isEqualTo("GET /api/v1/e7/{id}");
    }
}

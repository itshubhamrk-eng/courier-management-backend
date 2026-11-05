package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.shared.activity.domain.ActivityLogRepository;
import com.courier.shared.activity.domain.UserActionCount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkActivityRuleTest {

    @Mock ActivityLogRepository activityLogRepository;
    @Mock UserRepository userRepository;

    final Instant from = Instant.parse("2026-11-05T10:00:00Z");
    final Instant to = Instant.parse("2026-11-05T10:10:00Z");
    final UUID company = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final SecurityProperties props = new SecurityProperties();

    private BulkActivityRule rule() {
        return new BulkActivityRule(activityLogRepository, userRepository, props);
    }

    private void stub(List<UserActionCount> byAction, List<UserActionCount> rateEdits) {
        when(activityLogRepository.findSuccessCountsByAction(from, to, BulkActivityRule.BULK_ACTIONS))
                .thenReturn(byAction);
        when(activityLogRepository.findSuccessCountsByModuleAndAction(from, to, BulkActivityRule.RATE_MODULES,
                BulkActivityRule.RATE_EDIT_ACTIONS, BulkActivityRule.EXCLUDED_ENDPOINTS))
                .thenReturn(rateEdits);
    }

    @Test
    void codeAndDefaults() {
        assertThat(rule().code()).isEqualTo(IncidentRule.BULK_ACTIVITY);
        assertThat(IncidentRule.BULK_ACTIVITY.autoBan()).isFalse();
        assertThat(props.getBulkActivity().getThreshold()).isEqualTo(30);
    }

    @Test
    void matchesTheValuesTheActivityFilterActuallyWrites() {
        assertThat(BulkActivityRule.BULK_ACTIONS).containsExactlyInAnyOrder("CANCEL", "EXPORT");
        assertThat(BulkActivityRule.RATE_MODULES)
                .containsExactlyInAnyOrder("Rates", "Customer Rates", "Pdp Rates", "Pricing Zones");
        assertThat(BulkActivityRule.RATE_EDIT_ACTIONS)
                .containsExactlyInAnyOrder("CREATE", "UPDATE", "DELETE", "ACTIVATE", "DEACTIVATE");
        // price lookups (logged as CREATE) and ordinary zone setup are not rate edits
        assertThat(BulkActivityRule.EXCLUDED_ENDPOINTS).containsExactlyInAnyOrder(
                "/api/v1/rates/calculate", "/api/v1/customer-rates/calculate", "/api/v1/pdp-rates/calculate",
                "/api/v1/pricing-zones/{id}/locations", "/api/v1/pricing-zones/{id}/locations/{locationId}");
    }

    @Test
    void emptyDataRaisesNothing() {
        stub(List.of(), List.of());

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void atThresholdRaisesNothing() {
        stub(List.of(new UserActionCount(company, user, "Shipments", "CANCEL", 20)),
                List.of(new UserActionCount(company, user, "Rates", "UPDATE", 10)));

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void aboveThresholdRaisesOneRequestWithBreakdown() {
        stub(List.of(new UserActionCount(company, user, "Shipments", "CANCEL", 20),
                        new UserActionCount(company, user, "Pickups", "CANCEL", 5)),
                List.of(new UserActionCount(company, user, "Rates", "UPDATE", 6)));

        List<IncidentRequest> out = rule().scan(from, to);

        assertThat(out).singleElement().satisfies(r -> {
            assertThat(r.rule()).isEqualTo(IncidentRule.BULK_ACTIVITY);
            assertThat(r.userId()).isEqualTo(user);
            assertThat(r.companyId()).isEqualTo(company);
            assertThat(r.evidence())
                    .containsEntry("actions", 31L)
                    .containsEntry("threshold", 30)
                    .containsEntry("byModuleAction", Map.of(
                            "Shipments CANCEL", 20L, "Pickups CANCEL", 5L, "Rates UPDATE", 6L))
                    .containsEntry("windowFrom", from.toString())
                    .containsEntry("windowTo", to.toString());
        });
    }

    @Test
    void usersAreCountedSeparately() {
        UUID other = UUID.randomUUID();
        stub(List.of(new UserActionCount(company, user, "Shipments", "CANCEL", 16),
                        new UserActionCount(company, other, "Shipments", "CANCEL", 16)),
                List.of());

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void incidentCompanyIsTheUsersHomeCompanyNotTheFirstRowsCompany() {
        UUID home = UUID.randomUUID();
        User u = User.builder().build();
        u.setId(user);
        u.setCompanyId(home);
        stub(List.of(new UserActionCount(company, user, "Shipments", "CANCEL", 31)), List.of());
        when(userRepository.findAllByIdIn(java.util.Set.of(user))).thenReturn(List.of(u));

        assertThat(rule().scan(from, to)).singleElement()
                .satisfies(r -> assertThat(r.companyId()).isEqualTo(home));
    }

    @Test
    void unknownUserFallsBackToTheRowsCompany() {
        stub(List.of(new UserActionCount(company, user, "Shipments", "CANCEL", 31)), List.of());

        assertThat(rule().scan(from, to)).singleElement()
                .satisfies(r -> assertThat(r.companyId()).isEqualTo(company));
    }
}

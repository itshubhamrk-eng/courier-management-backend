package com.courier.modules.subscription.api;

import com.courier.modules.subscription.api.dto.CreateSubscriptionPlanRequest;
import com.courier.modules.subscription.api.dto.SubscriptionPlanResponse;
import com.courier.modules.subscription.api.dto.SubscriptionPlanSummary;
import com.courier.modules.subscription.api.dto.UpdateSubscriptionPlanRequest;
import com.courier.modules.subscription.application.command.CreateSubscriptionPlanCommand;
import com.courier.modules.subscription.application.command.UpdateSubscriptionPlanCommand;
import com.courier.modules.subscription.domain.PlanType;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionPlanMapperTest {

    private final SubscriptionPlanMapper mapper = new SubscriptionPlanMapper();

    @Test
    @DisplayName("a create request maps field for field, preserving null quotas as unlimited")
    void mapsCreateRequest() {
        CreateSubscriptionPlanRequest request = new CreateSubscriptionPlanRequest(
                "PREMIUM_MONTHLY", "Premium", "Top tier", PlanType.PREMIUM,
                new BigDecimal("9999.0000"), new BigDecimal("99990.0000"), "INR", 7,
                null, 10, 5, null, 100, 40, 2000, 48000, null, 1200,
                Map.of("bulkBooking", true), true, 30);

        CreateSubscriptionPlanCommand command = mapper.toCommand(request);

        assertThat(command.planCode()).isEqualTo("PREMIUM_MONTHLY");
        assertThat(command.planType()).isEqualTo(PlanType.PREMIUM);
        assertThat(command.trialDays()).isEqualTo(7);
        // null must survive the hop: it is the wire form of "unlimited".
        assertThat(command.maxUsers()).isNull();
        assertThat(command.maxCustomers()).isNull();
        assertThat(command.storageLimitGb()).isNull();
        assertThat(command.maxBranches()).isEqualTo(10);
        assertThat(command.apiRateLimit()).isEqualTo(1200);
        assertThat(command.featureFlags()).containsEntry("bulkBooking", true);
        assertThat(command.active()).isTrue();
    }

    @Test
    @DisplayName("an update request carries the client's version through to the command")
    void mapsUpdateRequest() {
        UpdateSubscriptionPlanRequest request = new UpdateSubscriptionPlanRequest(
                "Premium", "Top tier", PlanType.PREMIUM,
                new BigDecimal("9999.0000"), new BigDecimal("99990.0000"), "INR", 7,
                null, 10, 5, null, 100, 40, 2000, 48000, null, 1200,
                Map.of(), 30, 4L);

        UpdateSubscriptionPlanCommand command = mapper.toCommand(request);

        assertThat(command.expectedVersion()).isEqualTo(4L);
        assertThat(command.maxUsers()).isNull();
        assertThat(command.displayOrder()).isEqualTo(30);
    }

    @Test
    @DisplayName("the response exposes the audit columns, the version and the unlimited flag")
    void mapsResponse() {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .planCode("ENTERPRISE")
                .planName("Enterprise")
                .planType(PlanType.ENTERPRISE)
                .monthlyPrice(new BigDecimal("199999.0000"))
                .yearlyPrice(new BigDecimal("1999990.0000"))
                .currency("INR")
                .trialDays(0)
                .active(true)
                .displayOrder(40)
                .build();
        plan.applyTypeInvariants();
        plan.setVersion(2L);

        SubscriptionPlanResponse response = mapper.toResponse(plan);

        assertThat(response.id()).isEqualTo(plan.getId());
        assertThat(response.unlimited()).isTrue();
        assertThat(response.maxUsers()).isNull();
        assertThat(response.isActive()).isTrue();
        assertThat(response.version()).isEqualTo(2L);
    }

    @Test
    @DisplayName("the summary drops quotas and flags but keeps what a list view renders")
    void mapsSummary() {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .planCode("BASIC")
                .planName("Basic")
                .planType(PlanType.BASIC)
                .monthlyPrice(new BigDecimal("999.0000"))
                .yearlyPrice(new BigDecimal("9990.0000"))
                .currency("INR")
                .trialDays(0)
                .maxUsers(5)
                .active(true)
                .displayOrder(10)
                .build();

        SubscriptionPlanSummary summary = mapper.toSummary(plan);

        assertThat(summary.planCode()).isEqualTo("BASIC");
        assertThat(summary.monthlyPrice()).isEqualByComparingTo("999");
        assertThat(summary.isActive()).isTrue();
        assertThat(summary.displayOrder()).isEqualTo(10);
    }
}

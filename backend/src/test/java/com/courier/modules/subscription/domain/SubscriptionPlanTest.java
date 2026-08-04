package com.courier.modules.subscription.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionPlanTest {

    private static SubscriptionPlan.SubscriptionPlanBuilder plan(PlanType type) {
        return SubscriptionPlan.builder()
                .planCode("standard_monthly")
                .planName("  Standard  ")
                .planType(type)
                .monthlyPrice(new BigDecimal("4999.0000"))
                .yearlyPrice(new BigDecimal("49990.0000"))
                .currency("inr")
                .trialDays(0)
                .maxUsers(25)
                .maxBranches(5);
    }

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        @DisplayName("code is uppercased, currency is uppercased, name is trimmed")
        void normalisesOnWrite() {
            SubscriptionPlan subject = plan(PlanType.STANDARD).build();

            subject.applyTypeInvariants();

            assertThat(subject.getPlanCode()).isEqualTo("STANDARD_MONTHLY");
            assertThat(subject.getCurrency()).isEqualTo("INR");
            assertThat(subject.getPlanName()).isEqualTo("Standard");
        }

        @Test
        @DisplayName("a missing currency falls back to the platform default")
        void defaultsCurrency() {
            SubscriptionPlan subject = plan(PlanType.STANDARD).currency("   ").build();

            subject.applyTypeInvariants();

            assertThat(subject.getCurrency()).isEqualTo(SubscriptionPlan.DEFAULT_CURRENCY);
        }

        @Test
        @DisplayName("null trialDays, displayOrder and featureFlags become safe defaults")
        void fillsNullDefaults() {
            SubscriptionPlan subject = plan(PlanType.STANDARD)
                    .trialDays(null)
                    .displayOrder(null)
                    .featureFlags(null)
                    .build();

            subject.applyTypeInvariants();

            assertThat(subject.getTrialDays()).isZero();
            assertThat(subject.getDisplayOrder()).isZero();
            assertThat(subject.getFeatureFlags()).isEmpty();
        }
    }

    @Nested
    @DisplayName("pricing rules")
    class Pricing {

        @Test
        @DisplayName("a negative price is rejected")
        void rejectsNegativePrice() {
            SubscriptionPlan subject = plan(PlanType.BASIC)
                    .monthlyPrice(new BigDecimal("-0.0001"))
                    .build();

            assertThatThrownBy(subject::applyTypeInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        @DisplayName("a negative yearly price is rejected too")
        void rejectsNegativeYearlyPrice() {
            SubscriptionPlan subject = plan(PlanType.BASIC)
                    .yearlyPrice(new BigDecimal("-1"))
                    .build();

            assertThatThrownBy(subject::applyTypeInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Yearly price");
        }

        @Test
        @DisplayName("a TRIAL plan cannot carry a monthly price")
        void trialCannotBePriced() {
            SubscriptionPlan subject = plan(PlanType.TRIAL).trialDays(14).build();

            assertThatThrownBy(subject::applyTypeInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("TRIAL plan cannot be priced");
        }

        @Test
        @DisplayName("a TRIAL plan cannot carry a yearly price either")
        void trialCannotBePricedYearly() {
            SubscriptionPlan subject = plan(PlanType.TRIAL)
                    .monthlyPrice(BigDecimal.ZERO)
                    .trialDays(14)
                    .build();

            assertThatThrownBy(subject::applyTypeInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("cannot be priced");
        }

        @Test
        @DisplayName("a free TRIAL with trial days is accepted, and 0.0000 counts as free")
        void freeTrialIsAccepted() {
            SubscriptionPlan subject = plan(PlanType.TRIAL)
                    .monthlyPrice(new BigDecimal("0.0000"))
                    .yearlyPrice(BigDecimal.ZERO)
                    .trialDays(14)
                    .build();

            subject.applyTypeInvariants();

            assertThat(subject.isChargeable()).isFalse();
            assertThat(subject.getTrialDays()).isEqualTo(14);
        }

        @Test
        @DisplayName("a TRIAL plan must grant at least one trial day")
        void trialNeedsDays() {
            SubscriptionPlan subject = plan(PlanType.TRIAL)
                    .monthlyPrice(BigDecimal.ZERO)
                    .yearlyPrice(BigDecimal.ZERO)
                    .trialDays(0)
                    .build();

            assertThatThrownBy(subject::applyTypeInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("at least one trial day");
        }
    }

    @Nested
    @DisplayName("quota rules")
    class Quotas {

        @Test
        @DisplayName("ENTERPRISE clears every quota to unlimited, even when values were supplied")
        void enterpriseIsUnlimited() {
            SubscriptionPlan subject = plan(PlanType.ENTERPRISE)
                    .maxUsers(25)
                    .maxBranches(5)
                    .maxHubs(3)
                    .maxCustomers(1000)
                    .maxDrivers(50)
                    .maxVehicles(20)
                    .maxDailyBookings(500)
                    .maxMonthlyBookings(12000)
                    .storageLimitGb(50)
                    .apiRateLimit(600)
                    .build();

            subject.applyTypeInvariants();

            assertThat(subject.isUnlimited()).isTrue();
            assertThat(subject.getMaxUsers()).isNull();
            assertThat(subject.getMaxBranches()).isNull();
            assertThat(subject.getMaxHubs()).isNull();
            assertThat(subject.getMaxCustomers()).isNull();
            assertThat(subject.getMaxDrivers()).isNull();
            assertThat(subject.getMaxVehicles()).isNull();
            assertThat(subject.getMaxDailyBookings()).isNull();
            assertThat(subject.getMaxMonthlyBookings()).isNull();
            assertThat(subject.getStorageLimitGb()).isNull();
            assertThat(subject.getApiRateLimit()).isNull();
        }

        @Test
        @DisplayName("a non-enterprise plan keeps the quotas it was given")
        void othersKeepQuotas() {
            SubscriptionPlan subject = plan(PlanType.PREMIUM).maxUsers(25).build();

            subject.applyTypeInvariants();

            assertThat(subject.isUnlimited()).isFalse();
            assertThat(subject.getMaxUsers()).isEqualTo(25);
        }

        @Test
        @DisplayName("null limit is unlimited; a set limit blocks at the ceiling")
        void withinLimitSemantics() {
            assertThat(SubscriptionPlan.withinLimit(null, Long.MAX_VALUE)).isTrue();
            assertThat(SubscriptionPlan.withinLimit(5, 4)).isTrue();
            assertThat(SubscriptionPlan.withinLimit(5, 5)).isFalse();
            assertThat(SubscriptionPlan.withinLimit(5, 6)).isFalse();
        }
    }

    @Nested
    @DisplayName("state")
    class State {

        @Test
        @DisplayName("feature flags read as false unless explicitly true")
        void featureFlagLookup() {
            SubscriptionPlan subject = plan(PlanType.STANDARD)
                    .featureFlags(Map.of("bulkBooking", true, "podImage", false))
                    .build();

            assertThat(subject.isFeatureEnabled("bulkBooking")).isTrue();
            assertThat(subject.isFeatureEnabled("podImage")).isFalse();
            assertThat(subject.isFeatureEnabled("neverHeardOfIt")).isFalse();
        }

        @Test
        @DisplayName("activate and deactivate flip the catalogue flag")
        void activation() {
            SubscriptionPlan subject = plan(PlanType.STANDARD).build();

            subject.deactivate();
            assertThat(subject.isActive()).isFalse();

            subject.activate();
            assertThat(subject.isActive()).isTrue();
        }

        @Test
        @DisplayName("soft delete marks the row without discarding it")
        void softDelete() {
            SubscriptionPlan subject = plan(PlanType.STANDARD).build();
            java.util.UUID actor = java.util.UUID.randomUUID();

            subject.softDelete(actor);

            assertThat(subject.isDeleted()).isTrue();
            assertThat(subject.getDeletedBy()).isEqualTo(actor);
            assertThat(subject.getDeletedAt()).isNotNull();
            assertThat(subject.getPlanCode()).isEqualTo("standard_monthly");
        }
    }
}

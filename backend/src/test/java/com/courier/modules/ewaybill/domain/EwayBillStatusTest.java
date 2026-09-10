package com.courier.modules.ewaybill.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EwayBillStatusTest {

    @Test
    @DisplayName("NOT_REQUIRED/REQUIRED may only move to PART_A_PENDING or CANCELLED")
    void initialTransitions() {
        assertThat(EwayBillStatus.NOT_REQUIRED.canTransitionTo(EwayBillStatus.PART_A_PENDING)).isTrue();
        assertThat(EwayBillStatus.NOT_REQUIRED.canTransitionTo(EwayBillStatus.CANCELLED)).isTrue();
        assertThat(EwayBillStatus.NOT_REQUIRED.canTransitionTo(EwayBillStatus.GENERATED)).isFalse();
        assertThat(EwayBillStatus.REQUIRED.canTransitionTo(EwayBillStatus.PART_A_PENDING)).isTrue();
        assertThat(EwayBillStatus.REQUIRED.canTransitionTo(EwayBillStatus.FAILED)).isFalse();
    }

    @Test
    @DisplayName("PART_A_PENDING may succeed, fail or be cancelled")
    void partAPendingTransitions() {
        assertThat(EwayBillStatus.PART_A_PENDING.canTransitionTo(EwayBillStatus.PART_A_GENERATED)).isTrue();
        assertThat(EwayBillStatus.PART_A_PENDING.canTransitionTo(EwayBillStatus.FAILED)).isTrue();
        assertThat(EwayBillStatus.PART_A_PENDING.canTransitionTo(EwayBillStatus.CANCELLED)).isTrue();
        assertThat(EwayBillStatus.PART_A_PENDING.canTransitionTo(EwayBillStatus.GENERATED)).isFalse();
    }

    @Test
    @DisplayName("PART_A_GENERATED moves on to PART_B_PENDING, or may expire/cancel before then")
    void partAGeneratedTransitions() {
        assertThat(EwayBillStatus.PART_A_GENERATED.canTransitionTo(EwayBillStatus.PART_B_PENDING)).isTrue();
        assertThat(EwayBillStatus.PART_A_GENERATED.canTransitionTo(EwayBillStatus.EXPIRED)).isTrue();
        assertThat(EwayBillStatus.PART_A_GENERATED.canTransitionTo(EwayBillStatus.CANCELLED)).isTrue();
        assertThat(EwayBillStatus.PART_A_GENERATED.canTransitionTo(EwayBillStatus.FAILED)).isFalse();
    }

    @Test
    @DisplayName("PART_B_PENDING may succeed, fail or be cancelled")
    void partBPendingTransitions() {
        assertThat(EwayBillStatus.PART_B_PENDING.canTransitionTo(EwayBillStatus.GENERATED)).isTrue();
        assertThat(EwayBillStatus.PART_B_PENDING.canTransitionTo(EwayBillStatus.FAILED)).isTrue();
        assertThat(EwayBillStatus.PART_B_PENDING.canTransitionTo(EwayBillStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("GENERATED may only expire or be cancelled")
    void generatedTransitions() {
        assertThat(EwayBillStatus.GENERATED.canTransitionTo(EwayBillStatus.EXPIRED)).isTrue();
        assertThat(EwayBillStatus.GENERATED.canTransitionTo(EwayBillStatus.CANCELLED)).isTrue();
        assertThat(EwayBillStatus.GENERATED.canTransitionTo(EwayBillStatus.PART_A_PENDING)).isFalse();
    }

    @Test
    @DisplayName("FAILED can be retried into either stage, depending on which one failed")
    void failedCanBeRetriedIntoEitherStage() {
        assertThat(EwayBillStatus.FAILED.canTransitionTo(EwayBillStatus.PART_A_PENDING)).isTrue();
        assertThat(EwayBillStatus.FAILED.canTransitionTo(EwayBillStatus.PART_B_PENDING)).isTrue();
        assertThat(EwayBillStatus.FAILED.canTransitionTo(EwayBillStatus.CANCELLED)).isTrue();
        assertThat(EwayBillStatus.FAILED.canTransitionTo(EwayBillStatus.GENERATED)).isFalse();
    }

    @Test
    @DisplayName("EXPIRED can only be retried into a fresh Part-A, or cancelled")
    void expiredCanOnlyRegeneratePartA() {
        assertThat(EwayBillStatus.EXPIRED.canTransitionTo(EwayBillStatus.PART_A_PENDING)).isTrue();
        assertThat(EwayBillStatus.EXPIRED.canTransitionTo(EwayBillStatus.CANCELLED)).isTrue();
        assertThat(EwayBillStatus.EXPIRED.canTransitionTo(EwayBillStatus.PART_B_PENDING)).isFalse();
    }

    @Test
    @DisplayName("CANCELLED is terminal — nothing transitions out of it")
    void cancelledIsTerminal() {
        assertThat(EwayBillStatus.CANCELLED.isTerminal()).isTrue();
        for (EwayBillStatus next : EwayBillStatus.values()) {
            assertThat(EwayBillStatus.CANCELLED.canTransitionTo(next)).isFalse();
        }
    }

    @Test
    @DisplayName("a self-transition is never legal")
    void selfTransitionRejected() {
        assertThat(EwayBillStatus.PART_A_PENDING.canTransitionTo(EwayBillStatus.PART_A_PENDING)).isFalse();
    }

    @Test
    @DisplayName("requireCanTransitionTo throws on an illegal move")
    void requireThrowsOnIllegalMove() {
        assertThatThrownBy(() -> EwayBillStatus.GENERATED.requireCanTransitionTo(EwayBillStatus.PART_A_PENDING))
                .isInstanceOf(BusinessRuleException.class);
    }
}

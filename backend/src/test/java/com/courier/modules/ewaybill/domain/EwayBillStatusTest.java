package com.courier.modules.ewaybill.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EwayBillStatusTest {

    @Test
    @DisplayName("PENDING may move to UPLOADED, VALIDATED, INVALID or CANCELLED")
    void pendingTransitions() {
        assertThat(EwayBillStatus.PENDING.canTransitionTo(EwayBillStatus.UPLOADED)).isTrue();
        assertThat(EwayBillStatus.PENDING.canTransitionTo(EwayBillStatus.VALIDATED)).isTrue();
        assertThat(EwayBillStatus.PENDING.canTransitionTo(EwayBillStatus.INVALID)).isTrue();
        assertThat(EwayBillStatus.PENDING.canTransitionTo(EwayBillStatus.CANCELLED)).isTrue();
        assertThat(EwayBillStatus.PENDING.canTransitionTo(EwayBillStatus.EXPIRED)).isFalse();
    }

    @Test
    @DisplayName("VALIDATED may only expire or be cancelled")
    void validatedTransitions() {
        assertThat(EwayBillStatus.VALIDATED.canTransitionTo(EwayBillStatus.EXPIRED)).isTrue();
        assertThat(EwayBillStatus.VALIDATED.canTransitionTo(EwayBillStatus.CANCELLED)).isTrue();
        assertThat(EwayBillStatus.VALIDATED.canTransitionTo(EwayBillStatus.PENDING)).isFalse();
        assertThat(EwayBillStatus.VALIDATED.canTransitionTo(EwayBillStatus.INVALID)).isFalse();
    }

    @Test
    @DisplayName("INVALID can be corrected back toward VALIDATED")
    void invalidCanBeRetried() {
        assertThat(EwayBillStatus.INVALID.canTransitionTo(EwayBillStatus.UPLOADED)).isTrue();
        assertThat(EwayBillStatus.INVALID.canTransitionTo(EwayBillStatus.VALIDATED)).isTrue();
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
        assertThat(EwayBillStatus.PENDING.canTransitionTo(EwayBillStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("requireCanTransitionTo throws on an illegal move")
    void requireThrowsOnIllegalMove() {
        assertThatThrownBy(() -> EwayBillStatus.VALIDATED.requireCanTransitionTo(EwayBillStatus.PENDING))
                .isInstanceOf(BusinessRuleException.class);
    }
}

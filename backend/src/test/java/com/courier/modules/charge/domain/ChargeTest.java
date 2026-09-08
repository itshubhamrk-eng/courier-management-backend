package com.courier.modules.charge.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargeTest {

    @Test
    @DisplayName("a blank name is refused")
    void blankNameRejected() {
        Charge charge = charge("   ");
        assertThatThrownBy(charge::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("charge name is required");
    }

    @Test
    @DisplayName("the name is trimmed")
    void nameIsTrimmed() {
        Charge charge = charge("  Fuel Surcharge  ");
        charge.applyInvariants();
        assertThat(charge.getChargeName()).isEqualTo("Fuel Surcharge");
    }

    @Test
    @DisplayName("no service type is refused")
    void serviceTypeRequired() {
        Charge charge = Charge.builder().chargeName("X").status(ChargeStatus.ACTIVE).build();
        assertThatThrownBy(charge::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("service type");
    }

    @Test
    @DisplayName("activate/deactivate flip status")
    void lifecycle() {
        Charge charge = charge("X");
        assertThat(charge.isActive()).isTrue();
        charge.deactivate();
        assertThat(charge.isActive()).isFalse();
        charge.activate();
        assertThat(charge.isActive()).isTrue();
    }

    private static Charge charge(String name) {
        return Charge.builder()
                .chargeName(name)
                .serviceTypeId(UUID.randomUUID())
                .status(ChargeStatus.ACTIVE)
                .build();
    }
}

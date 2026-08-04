package com.courier.modules.shipment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Every (from, to) pair, asserted legal or illegal — see {@code MEMORY/modules/shipment.md}'s
 * own "full state-machine matrix" test checklist, carried over from the pre-Customer-module
 * planning note into the module that actually shipped. */
class ShipmentStatusTest {

    @Test
    @DisplayName("the legal forward path books through to delivery")
    void forwardPath() {
        assertThat(ShipmentStatus.BOOKED.canTransitionTo(ShipmentStatus.MANIFEST_CREATED)).isTrue();
        assertThat(ShipmentStatus.MANIFEST_CREATED.canTransitionTo(ShipmentStatus.DISPATCHED)).isTrue();
        assertThat(ShipmentStatus.DISPATCHED.canTransitionTo(ShipmentStatus.IN_SCAN)).isTrue();
        assertThat(ShipmentStatus.IN_SCAN.canTransitionTo(ShipmentStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(ShipmentStatus.OUT_FOR_DELIVERY.canTransitionTo(ShipmentStatus.DELIVERED)).isTrue();
    }

    @Test
    @DisplayName("the return path runs from IN_SCAN or OUT_FOR_DELIVERY directly to RETURNED")
    void returnPath() {
        assertThat(ShipmentStatus.IN_SCAN.canTransitionTo(ShipmentStatus.RETURNED)).isTrue();
        assertThat(ShipmentStatus.OUT_FOR_DELIVERY.canTransitionTo(ShipmentStatus.RETURNED)).isTrue();
    }

    @Test
    @DisplayName("cancel is only legal, and only cancellable, before the shipment leaves the branch")
    void cancelOnlyBeforeDispatch() {
        Set<ShipmentStatus> cancellable = EnumSet.of(
                ShipmentStatus.BOOKED, ShipmentStatus.READY_FOR_MANIFEST, ShipmentStatus.MANIFEST_CREATED);

        for (ShipmentStatus status : ShipmentStatus.values()) {
            assertThat(status.isCancellable()).as(status.name()).isEqualTo(cancellable.contains(status));
        }
        assertThat(ShipmentStatus.BOOKED.canTransitionTo(ShipmentStatus.CANCELLED)).isTrue();
        assertThat(ShipmentStatus.DISPATCHED.canTransitionTo(ShipmentStatus.CANCELLED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"DELIVERED", "RETURNED", "CANCELLED"})
    @DisplayName("DELIVERED, RETURNED and CANCELLED are terminal — every transition is refused")
    void terminalStatesRefuseEverything(ShipmentStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (ShipmentStatus next : ShipmentStatus.values()) {
            assertThat(terminal.canTransitionTo(next)).as("%s -> %s", terminal, next).isFalse();
        }
    }

    @Test
    @DisplayName("no status transitions to itself, and null is always refused")
    void noSelfLoopsOrNullTargets() {
        for (ShipmentStatus status : ShipmentStatus.values()) {
            assertThat(status.canTransitionTo(status)).as(status.name()).isFalse();
            assertThat(status.canTransitionTo(null)).as(status.name()).isFalse();
        }
    }
}

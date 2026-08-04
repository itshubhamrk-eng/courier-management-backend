package com.courier.modules.company.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BranchTest {

    private static Branch.BranchBuilder branch() {
        return Branch.builder()
                .branchCode("  pune main ")
                .branchName("  Pune Main  ")
                .branchType(BranchType.BOOKING_DELIVERY_BRANCH)
                .status(BranchStatus.ACTIVE)
                .email("  PUNE@Legacy.test ");
    }

    @Test
    @DisplayName("code is uppercased with spaces to underscores, name trimmed, email lowered")
    void normalises() {
        Branch b = branch().build();
        b.applyInvariants();

        assertThat(b.getBranchCode()).isEqualTo("PUNE_MAIN");
        assertThat(b.getBranchName()).isEqualTo("Pune Main");
        assertThat(b.getEmail()).isEqualTo("pune@legacy.test");
    }

    @Test
    @DisplayName("working days are uppercased, de-duplicated and validated")
    void workingDays() {
        Branch b = branch().workingDays("mon, tue ,mon,wed").build();
        b.applyInvariants();
        assertThat(b.getWorkingDays()).isEqualTo("MON,TUE,WED");
    }

    @Test
    @DisplayName("an invalid working day is rejected")
    void badWorkingDay() {
        Branch b = branch().workingDays("MON,FUNDAY").build();
        assertThatThrownBy(b::applyInvariants)
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("FUNDAY");
    }

    @Test
    @DisplayName("closing must be after opening")
    void hours() {
        Branch b = branch().openingTime(LocalTime.of(18, 0)).closingTime(LocalTime.of(9, 0)).build();
        assertThatThrownBy(b::applyInvariants)
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Closing time");
    }

    @Test
    @DisplayName("coordinates out of range are rejected")
    void coordinates() {
        assertThatThrownBy(() -> branch().latitude(new BigDecimal("95")).build().applyInvariants())
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Latitude");
        assertThatThrownBy(() -> branch().longitude(new BigDecimal("-200")).build().applyInvariants())
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Longitude");
    }

    @Test
    @DisplayName("a branch must have a type")
    void requiresType() {
        Branch b = branch().branchType(null).build();
        assertThatThrownBy(b::applyInvariants)
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("type");
    }

    @Test
    @DisplayName("lifecycle and manager assignment")
    void lifecycle() {
        Branch b = branch().build();
        assertThat(b.isActive()).isTrue();

        b.deactivate();
        assertThat(b.getStatus()).isEqualTo(BranchStatus.INACTIVE);
        assertThat(b.isActive()).isFalse();

        b.activate();
        assertThat(b.isActive()).isTrue();

        UUID manager = UUID.randomUUID();
        b.assignManager(manager);
        assertThat(b.getManagerId()).isEqualTo(manager);
        b.assignManager(null);
        assertThat(b.getManagerId()).isNull();
    }

    @Test
    @DisplayName("soft delete marks the row without discarding it")
    void softDelete() {
        Branch b = branch().build();
        UUID actor = UUID.randomUUID();
        b.softDelete(actor);
        assertThat(b.isDeleted()).isTrue();
        assertThat(b.getDeletedBy()).isEqualTo(actor);
    }
}

package com.courier.modules.company.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyRoleTest {

    private static CompanyRole role() {
        return CompanyRole.builder()
                .roleCode("  night shift supervisor ")
                .roleName("  Night Shift Supervisor  ")
                .roleType(RoleType.OPERATIONS)
                .status(RoleStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("the code is uppercased and spaces become underscores")
    void normalisesCode() {
        // Codes are typed by humans in a form and referenced by machines afterwards.
        CompanyRole subject = role();

        subject.applyInvariants();

        assertThat(subject.getRoleCode()).isEqualTo("NIGHT_SHIFT_SUPERVISOR");
        assertThat(subject.getRoleName()).isEqualTo("Night Shift Supervisor");
    }

    @Test
    @DisplayName("null type, status and permissions become safe defaults")
    void fillsDefaults() {
        CompanyRole subject = CompanyRole.builder()
                .roleCode("X_ROLE")
                .roleName("X Role")
                .roleType(null)
                .status(null)
                .build();

        subject.applyInvariants();

        assertThat(subject.getRoleType()).isEqualTo(RoleType.OPERATIONS);
        assertThat(subject.getStatus()).isEqualTo(RoleStatus.ACTIVE);
    }

    @Test
    @DisplayName("activate and deactivate move the status, and isActive follows it")
    void lifecycle() {
        CompanyRole subject = role();

        assertThat(subject.isActive()).isTrue();

        subject.deactivate();
        assertThat(subject.getStatus()).isEqualTo(RoleStatus.INACTIVE);
        assertThat(subject.isActive()).isFalse();
        assertThat(RoleStatus.INACTIVE.isAssignable()).isFalse();

        subject.activate();
        assertThat(subject.isActive()).isTrue();
        assertThat(RoleStatus.ACTIVE.isAssignable()).isTrue();
    }

    @Test
    @DisplayName("the default flag can be set and cleared")
    void defaultFlag() {
        CompanyRole subject = role();
        assertThat(subject.isDefaultRole()).isFalse();

        subject.markAsDefault();
        assertThat(subject.isDefaultRole()).isTrue();

        subject.clearDefault();
        assertThat(subject.isDefaultRole()).isFalse();
    }

    @Test
    @DisplayName("soft delete marks the row without discarding it")
    void softDelete() {
        CompanyRole subject = role();
        UUID actor = UUID.randomUUID();

        subject.softDelete(actor);

        assertThat(subject.isDeleted()).isTrue();
        assertThat(subject.getDeletedBy()).isEqualTo(actor);
        assertThat(subject.getRoleName()).isEqualTo("  Night Shift Supervisor  ");
    }
}

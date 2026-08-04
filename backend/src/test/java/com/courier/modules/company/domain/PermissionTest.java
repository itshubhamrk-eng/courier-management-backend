package com.courier.modules.company.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionTest {

    private static Permission.PermissionBuilder permission() {
        return Permission.builder()
                .module(PermissionModule.SHIPMENT)
                .action(PermissionAction.CREATE)
                .status(PermissionStatus.ACTIVE);
    }

    @Test
    @DisplayName("the code is derived from module and action, never supplied")
    void codeIsDerived() {
        // A code that disagreed with its own module and action would be impossible to
        // reason about, so it is always computed.
        Permission subject = permission().permissionCode("SOMETHING_ELSE").build();

        subject.applyInvariants();

        assertThat(subject.getPermissionCode()).isEqualTo("SHIPMENT_CREATE");
        assertThat(Permission.codeFor(PermissionModule.RATE_MASTER, PermissionAction.IMPORT))
                .isEqualTo("RATE_MASTER_IMPORT");
    }

    @Test
    @DisplayName("name, resource and display order default sensibly")
    void defaults() {
        Permission subject = permission()
                .module(PermissionModule.RATE_MASTER)
                .action(PermissionAction.READ)
                .build();

        subject.applyInvariants();

        assertThat(subject.getPermissionName()).isEqualTo("Read Rate Master");
        // URL spelling: underscores become hyphens.
        assertThat(subject.getResource()).isEqualTo("rate-master");
        assertThat(subject.getDisplayOrder())
                .isEqualTo(PermissionModule.RATE_MASTER.displayOrder()
                        + PermissionAction.READ.displayOffset());
    }

    @Test
    @DisplayName("a supplied resource is lowercased and kept")
    void explicitResource() {
        Permission subject = permission().resource("  Shipments  ").build();

        subject.applyInvariants();

        assertThat(subject.getResource()).isEqualTo("shipments");
    }

    @Test
    @DisplayName("a permission without a module or action is rejected")
    void requiresModuleAndAction() {
        assertThatThrownBy(() -> permission().module(null).build().applyInvariants())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("module and an action");
    }

    @Test
    @DisplayName("a system permission is read-only")
    void systemPermissionIsReadOnly() {
        Permission subject = permission().systemPermission(true).build();
        subject.applyInvariants();

        assertThatThrownBy(subject::requireEditable)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    @DisplayName("a custom permission may be edited")
    void customPermissionIsEditable() {
        Permission subject = permission().systemPermission(false).build();
        subject.applyInvariants();

        subject.requireEditable();
    }

    @Test
    @DisplayName("an ungated permission is allowed regardless of the plan")
    void ungatedIsAlwaysAllowed() {
        Permission subject = permission().build();
        subject.applyInvariants();

        assertThat(subject.isPlanGated()).isFalse();
        assertThat(subject.isAllowedByPlan(null)).isTrue();
        assertThat(subject.isAllowedByPlan(Map.of())).isTrue();
    }

    @Test
    @DisplayName("a gated permission fails closed on a missing, false or non-boolean flag")
    void gatedFailsClosed() {
        Permission subject = permission()
                .action(PermissionAction.IMPORT)
                .requiredFeatureFlag("bulkBooking")
                .build();
        subject.applyInvariants();

        assertThat(subject.isPlanGated()).isTrue();
        assertThat(subject.isAllowedByPlan(null)).isFalse();
        assertThat(subject.isAllowedByPlan(Map.of())).isFalse();
        assertThat(subject.isAllowedByPlan(Map.of("bulkBooking", false))).isFalse();
        // A string "true" is not a boolean true — anything but Boolean.TRUE denies.
        assertThat(subject.isAllowedByPlan(Map.of("bulkBooking", "true"))).isFalse();
        assertThat(subject.isAllowedByPlan(Map.of("bulkBooking", true))).isTrue();

        Map<String, Object> nullValued = new HashMap<>();
        nullValued.put("bulkBooking", null);
        assertThat(subject.isAllowedByPlan(nullValued)).isFalse();
    }

    @Test
    @DisplayName("status controls grantability and can be flipped")
    void status() {
        Permission subject = permission().build();

        assertThat(subject.isActive()).isTrue();
        assertThat(PermissionStatus.ACTIVE.isGrantable()).isTrue();

        subject.deactivate();
        assertThat(subject.isActive()).isFalse();
        assertThat(PermissionStatus.INACTIVE.isGrantable()).isFalse();

        subject.activate();
        assertThat(subject.isActive()).isTrue();
    }

    @Test
    @DisplayName("mutating actions are distinguished from read-only ones")
    void mutatingActions() {
        assertThat(PermissionAction.READ.isMutating()).isFalse();
        assertThat(PermissionAction.SEARCH.isMutating()).isFalse();
        assertThat(PermissionAction.EXPORT.isMutating()).isFalse();
        assertThat(PermissionAction.CREATE.isMutating()).isTrue();
        assertThat(PermissionAction.DELETE.isMutating()).isTrue();
        assertThat(PermissionAction.APPROVE.isMutating()).isTrue();
    }
}

package com.courier.modules.auth.domain;

import com.courier.shared.security.Roles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    @DisplayName("every Role matches the Roles string constant of the same name")
    void enumMatchesConstants() {
        // Roles constants are used inside @PreAuthorize strings, which the compiler
        // cannot check. If the two ever diverge, an authorisation rule silently
        // matches nothing — this test is the only thing that would catch it.
        for (Role role : Role.values()) {
            assertThat(role.authority()).isEqualTo(Roles.ROLE_PREFIX + role.name());
        }
        assertThat(Role.OPERATOR.authority()).isEqualTo(Roles.AUTH_OPERATOR);
        assertThat(Role.PLATFORM_ADMIN.authority()).isEqualTo(Roles.AUTH_PLATFORM_ADMIN);
        assertThat(Role.SUPER_ADMIN.authority()).isEqualTo(Roles.AUTH_SUPER_ADMIN);
    }

    @Test
    @DisplayName("SUPER_ADMIN and PLATFORM_ADMIN are platform scoped, company roles are not")
    void platformScope() {
        assertThat(Role.SUPER_ADMIN.isPlatformScoped()).isTrue();
        assertThat(Role.PLATFORM_ADMIN.isPlatformScoped()).isTrue();
        assertThat(Role.CUSTOMER.isPlatformScoped()).isFalse();
    }

    @Test
    @DisplayName("SUPER_ADMIN is a distinct role, not an alias of PLATFORM_ADMIN")
    void superAdminIsItsOwnTier() {
        // The subscription plan catalogue is guarded by SUPER_ADMIN alone. If the two
        // were ever collapsed, every platform admin would silently gain write access
        // to the pricing of every company.
        assertThat(Role.SUPER_ADMIN).isNotEqualTo(Role.PLATFORM_ADMIN);
        assertThat(Roles.SUPER_ADMIN).isNotEqualTo(Roles.PLATFORM_ADMIN);
    }
}

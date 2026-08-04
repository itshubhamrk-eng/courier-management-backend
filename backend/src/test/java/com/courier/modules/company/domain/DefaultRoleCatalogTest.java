package com.courier.modules.company.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRoleCatalogTest {

    @Test
    @DisplayName("exactly the nine documented roles are seeded, with unique codes")
    void nineRoles() {
        assertThat(DefaultRoleCatalog.definitions())
                .extracting(DefaultRoleCatalog.RoleDefinition::code)
                .containsExactlyInAnyOrder("COMPANY_ADMIN", "BRANCH_MANAGER", "HUB_MANAGER",
                        "BOOKING_OPERATOR", "DELIVERY_OPERATOR", "ACCOUNTS", "FINANCE_USER",
                        "CUSTOMER_SERVICE", "VIEWER")
                .doesNotHaveDuplicates();
        assertThat(DefaultRoleCatalog.systemRoleCodes()).hasSize(9);
    }

    @Test
    @DisplayName("exactly one role is the default a new user receives")
    void oneDefaultRole() {
        // Two defaults would make "which role does a new user get" ambiguous; none
        // would leave user creation with nothing to fall back on.
        assertThat(DefaultRoleCatalog.definitions())
                .filteredOn(DefaultRoleCatalog.RoleDefinition::defaultRole)
                .extracting(DefaultRoleCatalog.RoleDefinition::code)
                .containsExactly("BOOKING_OPERATOR");
    }

    @Test
    @DisplayName("every role is typed, and the types match what the role actually does")
    void rolesAreTyped() {
        assertThat(DefaultRoleCatalog.definitions())
                .allSatisfy(d -> assertThat(d.type()).isNotNull());

        assertThat(definition("COMPANY_ADMIN").type()).isEqualTo(RoleType.ADMINISTRATION);
        assertThat(definition("FINANCE_USER").type()).isEqualTo(RoleType.FINANCE);
        assertThat(definition("ACCOUNTS").type()).isEqualTo(RoleType.FINANCE);
        assertThat(definition("CUSTOMER_SERVICE").type()).isEqualTo(RoleType.SUPPORT);
        assertThat(definition("VIEWER").type()).isEqualTo(RoleType.READ_ONLY);
        assertThat(definition("BOOKING_OPERATOR").type()).isEqualTo(RoleType.OPERATIONS);
    }

    @Test
    @DisplayName("ACCOUNTS runs the branch's money desk but never the road")
    void accountsIsScoped() {
        Set<String> accounts = definition("ACCOUNTS").permissions();

        assertThat(accounts).contains("WALLET_RECHARGE", "PAYMENT_CREATE", "INVOICE_CREATE");
        assertThat(accounts).doesNotContain("SHIPMENT_CREATE", "DELIVERY_DISPATCH",
                "DELIVERY_DELIVER", "MANIFEST_DISPATCH");
    }

    @Test
    @DisplayName("a branch manager may hand out only the branch-staff roles, plus any custom role")
    void branchAssignability() {
        assertThat(DefaultRoleCatalog.BRANCH_ROLE_CODES).containsExactlyInAnyOrder(
                "BRANCH_MANAGER", "BOOKING_OPERATOR", "DELIVERY_OPERATOR", "ACCOUNTS");

        for (String branchRole : DefaultRoleCatalog.BRANCH_ROLE_CODES) {
            assertThat(DefaultRoleCatalog.isBranchAssignable(branchRole, true))
                    .as("%s is branch-assignable", branchRole).isTrue();
        }
        assertThat(DefaultRoleCatalog.isBranchAssignable("COMPANY_ADMIN", true)).isFalse();
        assertThat(DefaultRoleCatalog.isBranchAssignable("HUB_MANAGER", true)).isFalse();
        assertThat(DefaultRoleCatalog.isBranchAssignable("FINANCE_USER", true)).isFalse();
        // A company's own custom (non-system) role is always assignable by a branch manager.
        assertThat(DefaultRoleCatalog.isBranchAssignable("COUNTER_LEAD", false)).isTrue();
    }

    @Test
    @DisplayName("booking and delivery are separated: neither can do the other's job")
    void operatorSplitIsMeaningful() {
        // The whole point of splitting OPERATOR: a counter clerk must not be able to
        // close deliveries, and a driver must not be able to create or price shipments.
        Set<String> booking = definition("BOOKING_OPERATOR").permissions();
        Set<String> delivery = definition("DELIVERY_OPERATOR").permissions();

        assertThat(booking).contains("SHIPMENT_CREATE", "RATE_MASTER_READ");
        assertThat(delivery).doesNotContain("SHIPMENT_CREATE", "RATE_MASTER_READ");
        assertThat(delivery).contains("TRACKING_CREATE", "SHIPMENT_UPDATE");
        // Neither may cancel; that is a supervisor or support decision.
        assertThat(booking).doesNotContain("SHIPMENT_DELETE");
        assertThat(delivery).doesNotContain("SHIPMENT_DELETE");
    }

    @Test
    @DisplayName("FINANCE_USER changes money but not operations")
    void financeIsScoped() {
        Set<String> finance = definition("FINANCE_USER").permissions();

        assertThat(finance).contains("RATE_MASTER_UPDATE", "REPORT_EXPORT");
        assertThat(finance).doesNotContain("SHIPMENT_CREATE", "SHIPMENT_DELETE",
                "USER_UPDATE", "ROLE_UPDATE");
    }

    @Test
    @DisplayName("COMPANY_ADMIN holds every permission that is a company's to hold")
    void adminHoldsEveryCompanyPermission() {
        // If a permission is added and the admin role is not updated, the company has
        // nobody who can use the new feature — so the role's set is derived, not listed.
        DefaultRoleCatalog.RoleDefinition admin = definition("COMPANY_ADMIN");

        List<String> companyScoped = DefaultPermissionCatalog.matrix().entrySet().stream()
                .filter(e -> e.getKey() != PermissionModule.SUBSCRIPTION
                        && e.getKey() != PermissionModule.SUPER_ADMIN_USER
                        && e.getKey() != PermissionModule.GLOBAL_MASTER)
                .flatMap(e -> e.getValue().stream()
                        .filter(a -> !(e.getKey() == PermissionModule.COMPANY
                                && PLATFORM_ONLY_COMPANY_ACTIONS.contains(a)))
                        .map(a -> Permission.codeFor(e.getKey(), a)))
                .toList();

        assertThat(admin.permissions()).containsAll(companyScoped);
    }

    /** Mirrors the exclusion in {@code DefaultRoleCatalog}. */
    private static final Set<PermissionAction> PLATFORM_ONLY_COMPANY_ACTIONS = Set.of(
            PermissionAction.CREATE, PermissionAction.DELETE, PermissionAction.SEARCH,
            PermissionAction.EXPORT, PermissionAction.ACTIVATE, PermissionAction.DEACTIVATE,
            PermissionAction.SUSPEND);

    @Test
    @DisplayName("no company role holds a platform operator's rights")
    void noCompanyRoleHoldsPlatformRights() {
        // The failure this guards against is quiet and total: COMPANY_ADMIN's set is
        // derived from the whole catalogue, so a platform-tier module added without an
        // exclusion would hand every company admin on the platform the ability to renew
        // their own subscription or rename a city for everybody else.
        assertThat(DefaultRoleCatalog.definitions()).allSatisfy(role ->
                assertThat(role.permissions()).as("permissions of %s", role.code())
                        .noneMatch(code -> code.startsWith("SUBSCRIPTION_")
                                || code.startsWith("SUPER_ADMIN_USER_")
                                || code.startsWith("GLOBAL_MASTER_")
                                || code.equals("COMPANY_CREATE")
                                || code.equals("COMPANY_DELETE")
                                || code.equals("COMPANY_SUSPEND")
                                || code.equals("COMPANY_ACTIVATE")
                                || code.equals("COMPANY_DEACTIVATE")));
    }

    @Test
    @DisplayName("VIEWER is read-only")
    void viewerIsReadOnly() {
        DefaultRoleCatalog.RoleDefinition viewer = definition("VIEWER");

        assertThat(viewer.permissions()).allSatisfy(permission ->
                assertThat(permission)
                        .doesNotContain("_MANAGE")
                        .doesNotContain("_CREATE")
                        .doesNotContain("_UPDATE")
                        .doesNotContain("_DELETE")
                        .doesNotContain("_ASSIGN"));
    }

    @Test
    @DisplayName("a plan-gated permission is withheld when the plan does not enable it")
    void planGatedPermissionsAreFiltered() {
        DefaultRoleCatalog.RoleDefinition branchManager = definition("BRANCH_MANAGER");
        assertThat(branchManager.permissions()).contains("SHIPMENT_IMPORT");

        Set<String> withoutFeature =
                DefaultRoleCatalog.permissionsFor(branchManager, Map.of("bulkBooking", false));
        Set<String> withFeature =
                DefaultRoleCatalog.permissionsFor(branchManager, Map.of("bulkBooking", true));

        assertThat(withoutFeature).doesNotContain("SHIPMENT_IMPORT");
        assertThat(withFeature).contains("SHIPMENT_IMPORT");
        // Ungated permissions are unaffected either way.
        assertThat(withoutFeature).contains("SHIPMENT_CREATE");
    }

    @Test
    @DisplayName("absent or null feature flags deny a gated permission rather than granting it")
    void missingFlagsDeny() {
        DefaultRoleCatalog.RoleDefinition admin = definition("COMPANY_ADMIN");

        assertThat(DefaultRoleCatalog.permissionsFor(admin, null)).doesNotContain("SHIPMENT_IMPORT");
        assertThat(DefaultRoleCatalog.permissionsFor(admin, Map.of())).doesNotContain("SHIPMENT_IMPORT");
        // A non-boolean value is not a truthy value.
        assertThat(DefaultRoleCatalog.permissionsFor(admin, Map.of("bulkBooking", "yes")))
                .doesNotContain("SHIPMENT_IMPORT");
    }

    @Test
    @DisplayName("the returned set is a fresh copy the caller may mutate")
    void returnsMutableCopy() {
        DefaultRoleCatalog.RoleDefinition viewer = definition("VIEWER");

        Set<String> granted = DefaultRoleCatalog.permissionsFor(viewer, Map.of());
        granted.add("SHIPMENT_CREATE");

        assertThat(viewer.permissions()).doesNotContain("SHIPMENT_CREATE");
    }

    private DefaultRoleCatalog.RoleDefinition definition(String code) {
        return DefaultRoleCatalog.definitions().stream()
                .filter(d -> d.code().equals(code))
                .findFirst()
                .orElseThrow();
    }
}

package com.courier.modules.company.application;

import com.courier.modules.company.domain.DefaultRoleCatalog;
import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.application.WalletServiceImpl;
import com.courier.modules.master.application.CountryServiceImpl;
import com.courier.modules.master.application.VehicleTypeServiceImpl;
import com.courier.shared.security.Roles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a super admin may <b>not</b> do.
 *
 * <p>Every other test in the suite asserts that something works. This one asserts that
 * something does not, which is the harder property to keep: a guard is removed by
 * loosening one annotation, and nothing else in the suite would notice.
 *
 * <p>The rule is not arbitrary. A platform operator creating a branch, booking a
 * shipment, adding a customer or recharging a wallet leaves a record indistinguishable
 * from one the company made itself. Months later nobody can say whether the company did
 * it or the vendor did it "to help", and for money that question is the whole point of
 * keeping a ledger.
 *
 * <p>These assertions read the {@code @PreAuthorize} expressions directly rather than
 * standing up a security context. The expression is a string the compiler never checks,
 * so reading the string is reading the actual rule — a mocked call that happens to throw
 * would prove far less.
 */
class SuperAdminBoundaryTest {

    @Nested
    @DisplayName("company operations")
    class CompanyOperations {

        @Test
        @DisplayName("creating a branch is COMPANY_ADMIN's, not a platform operator's")
        void branchCreationExcludesPlatformTier() {
            assertThat(guardOf(BranchServiceImpl.class, "create"))
                    .contains(Roles.COMPANY_ADMIN)
                    .doesNotContain(Roles.SUPER_ADMIN)
                    .doesNotContain(Roles.PLATFORM_ADMIN);
        }

        @Test
        @DisplayName("recharging, crediting and debiting a wallet exclude the platform tier")
        void walletMovementExcludesPlatformTier() {
            // Recharge is the one that needed saying out loud: it used to be a bare
            // isAuthenticated(), and a super admin was kept out only by not having a
            // branch — an accident, not a rule.
            for (String method : List.of("openRecharge", "completeRecharge")) {
                assertThat(guardOf(WalletServiceImpl.class, method))
                        .as("guard on WalletServiceImpl.%s", method)
                        .contains("!hasRole('" + Roles.SUPER_ADMIN + "')")
                        .contains("!hasRole('" + Roles.PLATFORM_ADMIN + "')");
            }

            for (String method : List.of("credit", "debit")) {
                assertThat(guardOf(WalletServiceImpl.class, method))
                        .as("guard on WalletServiceImpl.%s", method)
                        .contains(Roles.COMPANY_ADMIN)
                        .doesNotContain(Roles.SUPER_ADMIN);
            }
        }

        @Test
        @DisplayName("no wallet method lets a caller name the balance")
        void walletExposesNoBalanceSetter() {
            // Decision 35, restated as a test: the balance moves through the ledger or
            // not at all. A platform operator "fixing" a balance is the exact scenario.
            assertThat(Arrays.stream(WalletService.class.getMethods())
                    .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                    .map(Class::getSimpleName))
                    .doesNotContain("BigDecimal");
        }
    }

    @Nested
    @DisplayName("master data")
    class MasterData {

        @Test
        @DisplayName("a company's own catalogues stay COMPANY_ADMIN's")
        void companyOwnedMastersAreNotPlatformTier() {
            // The flip to global applies to geography and nothing else. If it ever
            // spreads to vehicle types, a company loses the ability to describe its own
            // fleet — and this fails.
            assertThat(guardOf(VehicleTypeServiceImpl.class, "create"))
                    .contains(Roles.COMPANY_ADMIN)
                    .doesNotContain(Roles.SUPER_ADMIN);
        }

        @Test
        @DisplayName("the shared geography is written only by a platform operator")
        void globalMastersAreSuperAdminOnly() {
            for (String method : List.of("create", "update", "delete", "activate", "deactivate")) {
                assertThat(guardOf(CountryServiceImpl.class, method))
                        .as("guard on CountryServiceImpl.%s", method)
                        .contains(Roles.SUPER_ADMIN)
                        .doesNotContain(Roles.COMPANY_ADMIN);
            }
            // ...and read by everyone, or a booking clerk cannot pick a destination.
            assertThat(guardOf(CountryServiceImpl.class, "search")).isEqualTo("isAuthenticated()");
        }
    }

    @Nested
    @DisplayName("seeded roles")
    class SeededRoles {

        @Test
        @DisplayName("no seeded company role can create a branch-level operational record")
        void companyRolesHoldNoPlatformRights() {
            assertThat(DefaultRoleCatalog.definitions()).allSatisfy(role ->
                    assertThat(role.permissions()).as("permissions of %s", role.code())
                            .noneMatch(code -> code.startsWith("SUBSCRIPTION_")
                                    || code.startsWith("SUPER_ADMIN_USER_")
                                    || code.startsWith("GLOBAL_MASTER_")));
        }
    }

    /**
     * The {@code @PreAuthorize} expression on a service method.
     *
     * <p>Matches on name alone: none of the methods examined here is overloaded, and
     * finding the annotation wherever the method actually is beats hard-coding parameter
     * lists that change for unrelated reasons.
     */
    private static String guardOf(Class<?> type, String methodName) {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .filter(candidate -> candidate.isAnnotationPresent(PreAuthorize.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "%s.%s has no @PreAuthorize — a service method with no guard is the "
                                .formatted(type.getSimpleName(), methodName)
                                + "failure this test exists to catch"));
        return method.getAnnotation(PreAuthorize.class).value();
    }
}

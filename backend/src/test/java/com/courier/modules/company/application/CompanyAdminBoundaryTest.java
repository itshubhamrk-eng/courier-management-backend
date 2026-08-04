package com.courier.modules.company.application;

import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.DefaultPermissionCatalog;
import com.courier.modules.company.domain.DefaultRoleCatalog;
import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionAction;
import com.courier.modules.company.domain.PermissionModule;
import com.courier.modules.finance.application.WalletServiceImpl;
import com.courier.modules.master.application.RouteServiceImpl;
import com.courier.modules.master.application.VehicleTypeServiceImpl;
import com.courier.modules.subscription.application.SubscriptionPlanServiceImpl;
import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.security.Roles;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The company administrator's boundary, in both directions.
 *
 * <p>The companion to {@code SuperAdminBoundaryTest}: that one keeps the platform out of a
 * company's operations, this one keeps a company out of the platform's. The rules being
 * asserted are the ones a customer contract rests on — a company admin runs their own
 * business completely and reaches nothing outside it, including their own subscription.
 *
 * <p>Like its companion these read the {@code @PreAuthorize} expressions and the seeded
 * catalogue directly rather than standing up a security context. The expression is a string
 * the compiler never checks, so reading the string is reading the actual rule.
 *
 * <p><b>What this test cannot cover, and why it is still worth having.</b> Authorisation
 * today is by JWT role authority; the permission catalogue this asserts against does not yet
 * gate any endpoint (the deferred "authorise on permissions" step). So the responsibility
 * half below is a statement of intent that becomes enforcement the day the wiring lands —
 * and a wrong entry found now is a wrong entry not shipped then. The refusal half is real
 * today: those guards are the ones actually consulted.
 */
class CompanyAdminBoundaryTest {

    /**
     * Every company-owned entity in the codebase. Listed rather than classpath-scanned:
     * the point is to fail when someone adds an entity and forgets it, and a scan would
     * quietly pass by finding whatever exists.
     */
    private static final List<Class<?>> COMPANY_OWNED_ENTITIES = List.of(
            com.courier.modules.company.domain.CompanyRole.class,
            com.courier.modules.company.domain.CompanySetting.class,
            com.courier.modules.company.domain.CompanySettings.class,
            com.courier.modules.company.domain.RolePermission.class,
            com.courier.modules.company.domain.UserRole.class,
            com.courier.modules.company.domain.User.class,
            com.courier.modules.company.domain.Branch.class,
            com.courier.modules.finance.domain.Wallet.class,
            com.courier.modules.finance.domain.WalletTransaction.class,
            com.courier.modules.master.domain.Country.class,
            com.courier.modules.master.domain.State.class,
            com.courier.modules.master.domain.District.class,
            com.courier.modules.master.domain.City.class,
            com.courier.modules.master.domain.Area.class,
            com.courier.modules.master.domain.Pincode.class,
            com.courier.modules.master.domain.VehicleType.class,
            com.courier.modules.master.domain.PackageType.class,
            com.courier.modules.master.domain.ServiceType.class,
            com.courier.modules.master.domain.PaymentMode.class,
            com.courier.modules.master.domain.WeightSlab.class,
            com.courier.modules.master.domain.Route.class,
            com.courier.modules.auth.domain.User.class,
            com.courier.modules.auth.domain.RefreshToken.class,
            com.courier.modules.auth.domain.UserSession.class,
            com.courier.modules.auth.domain.LoginHistory.class,
            com.courier.modules.auth.domain.PasswordResetToken.class,
            com.courier.modules.auth.domain.EmailVerificationToken.class);

    @Nested
    @DisplayName("what a company admin cannot do")
    class Refusals {

        @Test
        @DisplayName("creating and deleting a company are the platform's, not a customer's")
        void companyLifecycleExcludesCompanyAdmin() {
            // Class-level: CompanyServiceImpl carries one guard for every method it has,
            // create and delete included. A per-method loosening would show up here as a
            // method-level annotation that does not match.
            assertThat(classGuardOf(CompanyServiceImpl.class))
                    .contains(Roles.SUPER_ADMIN)
                    .doesNotContain(Roles.COMPANY_ADMIN);

            for (String method : List.of("create", "delete", "update", "suspend", "expire",
                    "activate", "deactivate")) {
                assertThat(anyGuardOn(CompanyServiceImpl.class, method))
                        .as("guard reaching CompanyServiceImpl.%s", method)
                        .doesNotContain(Roles.COMPANY_ADMIN);
            }
        }

        @Test
        @DisplayName("assigning, renewing and suspending a subscription exclude the company admin")
        void subscriptionManagementExcludesCompanyAdmin() {
            // A company that could renew its own subscription is not a customer any more.
            // These three are the acts decision 52 pulled out of PUT precisely so they are
            // separately auditable — and separately guardable.
            for (String method : List.of("assignSubscription", "renewSubscription",
                    "suspendSubscription")) {
                assertThat(anyGuardOn(CompanyServiceImpl.class, method))
                        .as("guard reaching CompanyServiceImpl.%s", method)
                        .contains(Roles.SUPER_ADMIN)
                        .doesNotContain(Roles.COMPANY_ADMIN);
            }
            // ...and the plan catalogue itself, which is where the prices live.
            assertThat(classGuardOf(SubscriptionPlanServiceImpl.class))
                    .contains(Roles.SUPER_ADMIN)
                    .doesNotContain(Roles.COMPANY_ADMIN);
        }

        @Test
        @DisplayName("no seeded company role holds a platform right")
        void seededRolesHoldNoPlatformRights() {
            Set<String> adminGrants = adminPermissions();

            assertThat(adminGrants)
                    .as("COMPANY_ADMIN must not be able to create or delete a company")
                    .doesNotContain(
                            Permission.codeFor(PermissionModule.COMPANY, PermissionAction.CREATE),
                            Permission.codeFor(PermissionModule.COMPANY, PermissionAction.DELETE));

            assertThat(adminGrants)
                    .as("COMPANY_ADMIN must hold nothing from a platform-only module")
                    .noneMatch(code -> code.startsWith("SUBSCRIPTION_")
                            || code.startsWith("GLOBAL_MASTER_")
                            || code.startsWith("SUPER_ADMIN_USER_"));
        }

        @Test
        @DisplayName("a company admin still reads and edits their own company record")
        void companyAdminKeepsItsOwnCompanyRecord() {
            // The mirror image of the rule above, and the reason PLATFORM_ONLY_COMPANY_ACTIONS
            // excludes actions rather than the whole COMPANY module. Excluding the module
            // would leave an admin unable to see the business they administer.
            assertThat(adminPermissions()).contains(
                    Permission.codeFor(PermissionModule.COMPANY, PermissionAction.READ),
                    Permission.codeFor(PermissionModule.COMPANY, PermissionAction.UPDATE));
        }
    }

    @Nested
    @DisplayName("cross-company isolation")
    class Isolation {

        @Test
        @DisplayName("every company-owned entity repeats the company filter and the soft-delete restriction")
        void everyCompanyOwnedEntityIsFiltered() {
            // Hibernate does not inherit @Filter from a @MappedSuperclass. An entity that
            // extends CompanyOwnedEntity and forgets to repeat it is not "slightly less
            // filtered" — it is unfiltered, and one company reads another's rows. Nothing
            // else in the suite would notice, which is why this is here.
            assertThat(COMPANY_OWNED_ENTITIES).allSatisfy(type -> {
                assertThat(CompanyOwnedEntity.class).as("%s ownership", type.getSimpleName())
                        .isAssignableFrom(type);
                assertThat(type.isAnnotationPresent(Entity.class))
                        .as("%s is an @Entity", type.getSimpleName()).isTrue();

                Filter filter = type.getAnnotation(Filter.class);
                assertThat(filter).as("%s repeats @Filter — without it the company "
                        + "discriminator is never applied", type.getSimpleName()).isNotNull();
                assertThat(filter.name()).isEqualTo(CompanyOwnedEntity.COMPANY_FILTER);
                assertThat(filter.condition()).contains("company_id");

                // Soft delete is a separate concern from isolation, and one entity opts
                // out of it deliberately: a company always has exactly one settings row,
                // created on first access and never deleted.
                if (!type.equals(com.courier.modules.company.domain.CompanySettings.class)) {
                    assertThat(type.getAnnotation(SQLRestriction.class))
                            .as("%s repeats @SQLRestriction", type.getSimpleName()).isNotNull();
                }
            });
        }

        @Test
        @DisplayName("the owner column is mapped exactly once, and it is called company_id")
        void ownerColumnIsMappedInOnePlace() {
            // Java and MySQL are joined here and nowhere else, so this is the assertion
            // that catches a well-meaning tidy-up that would break every company-owned
            // table at once.
            jakarta.persistence.Column column;
            try {
                column = CompanyOwnedEntity.class.getDeclaredField("companyId")
                        .getAnnotation(jakarta.persistence.Column.class);
            } catch (NoSuchFieldException e) {
                throw new AssertionError("CompanyOwnedEntity no longer declares companyId", e);
            }
            assertThat(column).isNotNull();
            assertThat(column.name()).isEqualTo("company_id");
            assertThat(column.nullable()).isFalse();
        }

        @Test
        @DisplayName("a single-row load of a company-owned record names the company explicitly")
        void singleRowLoadsAreScopedToTheCompany() {
            // A findById is a primary-key load and bypasses the Hibernate filter entirely,
            // so guessing an id would reach another company's row. Every company-owned
            // repository therefore offers a scoped load, and the services use it.
            assertThat(List.of(
                    com.courier.modules.company.domain.BranchRepository.class,
                    com.courier.modules.company.domain.CompanyRoleRepository.class,
                    com.courier.modules.company.domain.CompanyUserRepository.class))
                    .allSatisfy(repository -> assertThat(
                            Arrays.stream(repository.getDeclaredMethods()).map(Method::getName))
                            .as("%s offers a company-scoped single-row load",
                                    repository.getSimpleName())
                            .contains("findByIdWithinCompany"));
        }
    }

    @Nested
    @DisplayName("what a company admin does run")
    class Responsibilities {

        /**
         * The company administrator's remit, module by module. Everything a courier
         * business configures and operates for itself.
         *
         * <p>Some of these modules have no service behind them yet — the catalogue defines
         * their codes and {@code COMPANY_ADMIN} already holds them, so the day the module
         * ships its owner is not in question.
         */
        private static final List<PermissionModule> COMPANY_ADMIN_MODULES = List.of(
                PermissionModule.BRANCH,
                PermissionModule.USER,
                PermissionModule.ROLE,
                PermissionModule.PERMISSION,
                PermissionModule.ROUTE_MASTER,
                PermissionModule.RATE_MASTER,
                PermissionModule.VEHICLE,
                PermissionModule.DRIVER,
                PermissionModule.SETTINGS,
                PermissionModule.WALLET,
                PermissionModule.CUSTOMER,
                PermissionModule.ADDRESS,
                PermissionModule.SHIPMENT,
                PermissionModule.MANIFEST,
                PermissionModule.REPORT);

        @Test
        @DisplayName("COMPANY_ADMIN holds every right the catalogue defines in its own modules")
        void adminCoversItsResponsibilities() {
            Set<String> adminGrants = adminPermissions();

            assertThat(COMPANY_ADMIN_MODULES).allSatisfy(module -> {
                Set<PermissionAction> actions = DefaultPermissionCatalog.matrix().get(module);
                assertThat(actions).as("catalogue defines %s", module).isNotEmpty();
                assertThat(actions).allSatisfy(action -> assertThat(adminGrants)
                        .as("COMPANY_ADMIN holds %s", Permission.codeFor(module, action))
                        .contains(Permission.codeFor(module, action)));
            });
        }

        @Test
        @DisplayName("the modules a company admin actually operates today are guarded to COMPANY_ADMIN")
        void shippedModulesAreCompanyAdminWrites() {
            assertThat(guardOf(BranchServiceImpl.class, "create")).contains(Roles.COMPANY_ADMIN);
            assertThat(guardOf(RoleServiceImpl.class, "create")).contains(Roles.COMPANY_ADMIN);
            assertThat(classGuardOf(RolePermissionServiceImpl.class)).contains(Roles.COMPANY_ADMIN);
            assertThat(guardOf(UserServiceImpl.class, "create")).contains(Roles.COMPANY_ADMIN);
            assertThat(guardOf(CompanySettingsServiceImpl.class, "replace"))
                    .contains(Roles.COMPANY_ADMIN);
            assertThat(guardOf(WalletServiceImpl.class, "credit")).contains(Roles.COMPANY_ADMIN);
            // Routes and the fleet catalogue are the company's own master data — global
            // geography is not, and SuperAdminBoundaryTest holds that other end.
            assertThat(guardOf(RouteServiceImpl.class, "create")).contains(Roles.COMPANY_ADMIN);
            assertThat(guardOf(VehicleTypeServiceImpl.class, "create")).contains(Roles.COMPANY_ADMIN);
        }

        @Test
        @DisplayName("a branch comes with the role that runs it")
        void branchCreationCarriesItsManagerRole() {
            // The third of the three things branch creation makes. Asserted on the
            // catalogue rather than the flow, because the flow can only grant a role the
            // catalogue still defines.
            assertThat(DefaultRoleCatalog.systemRoleCodes())
                    .contains(DefaultRoleCatalog.BRANCH_MANAGER);
            assertThat(DefaultRoleCatalog.definitions())
                    .filteredOn(definition -> definition.code().equals(DefaultRoleCatalog.BRANCH_MANAGER))
                    .singleElement()
                    .satisfies(definition -> assertThat(definition.permissions())
                            .contains("BRANCH_READ", "SHIPMENT_CREATE", "CUSTOMER_CREATE"));
            // And CompanyRole must still be the type it is granted through.
            assertThat(CompanyRole.class).isAssignableTo(CompanyOwnedEntity.class);
        }
    }

    // -------------------------------------------------------------------- helpers

    /** The permission codes {@code COMPANY_ADMIN} is seeded with, before plan gating. */
    private static Set<String> adminPermissions() {
        return DefaultRoleCatalog.definitions().stream()
                .filter(definition -> definition.code().equals(DefaultRoleCatalog.COMPANY_ADMIN))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "DefaultRoleCatalog no longer defines COMPANY_ADMIN"))
                .permissions();
    }

    private static String classGuardOf(Class<?> type) {
        PreAuthorize guard = type.getAnnotation(PreAuthorize.class);
        if (guard == null) {
            throw new AssertionError(type.getSimpleName() + " has no class-level @PreAuthorize");
        }
        return guard.value();
    }

    /**
     * The guard that actually reaches a method: its own {@code @PreAuthorize} if it has
     * one, otherwise the class-level annotation. Written this way on purpose — a refusal
     * is broken either by loosening the class guard or by adding a looser method guard,
     * and only checking one of the two would miss half the ways it can go wrong.
     */
    private static String anyGuardOn(Class<?> type, String methodName) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .filter(candidate -> candidate.isAnnotationPresent(PreAuthorize.class))
                .findFirst()
                .map(method -> method.getAnnotation(PreAuthorize.class).value())
                .orElseGet(() -> classGuardOf(type));
    }

    /** As {@code SuperAdminBoundaryTest}: the method's own guard, and it must have one. */
    private static String guardOf(Class<?> type, String methodName) {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .filter(candidate -> candidate.isAnnotationPresent(PreAuthorize.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "%s.%s has no @PreAuthorize".formatted(type.getSimpleName(), methodName)));
        return method.getAnnotation(PreAuthorize.class).value();
    }
}

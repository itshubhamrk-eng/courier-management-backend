package com.courier.modules.company.application;

import com.courier.modules.auth.application.UserProvisioningService;
import com.courier.modules.auth.domain.Role;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionRepository;
import com.courier.modules.company.domain.RolePermission;
import com.courier.modules.company.domain.RolePermissionRepository;
import com.courier.modules.company.domain.CompanySetting;
import com.courier.modules.company.domain.CompanySettingKeys;
import com.courier.modules.company.domain.CompanySettingRepository;
import com.courier.modules.company.domain.DefaultRoleCatalog;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything a new company needs before its first user signs in: roles, permissions,
 * settings and the administrator account.
 *
 * <p>Split out of {@code CompanyServiceImpl} because it is a different job — that class
 * decides *whether* a company may be created, this one builds the world the company
 * lives in. It also keeps the transaction boundary readable: all of this runs inside
 * the caller's transaction, so a failure half way through leaves no company with three
 * of five roles.
 *
 * <p><b>The company is bound explicitly</b> with {@link CompanyContext#runAs}. Every entity
 * written here is company-owned, and {@code CompanyEntityListener} stamps {@code company_id}
 * from the context — which is empty on a {@code SUPER_ADMIN} request, since a super
 * admin has no company of their own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyProvisioningService {

    private final CompanyRoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CompanySettingRepository settingRepository;
    private final UserProvisioningService userProvisioningService;

    /**
     * @param roleCount    how many roles were seeded
     * @param settingCount how many settings were seeded
     * @param admin        the created administrator
     */
    public record ProvisioningResult(int roleCount,
                                     int settingCount,
                                     UserProvisioningService.ProvisionedUser admin) {
    }

    /**
     * Seeds roles and settings, then creates the administrator.
     *
     * <p>Order matters: the roles must exist before the admin is created, so that a
     * listener reacting to the new user finds a coherent company.
     *
     * @param adminEmail     login address for the first user
     * @param adminFirstName may be null
     * @param adminLastName  may be null
     * @param adminMobile    may be null
     */
    @Transactional
    public ProvisioningResult provision(Company company,
                                        SubscriptionPlan plan,
                                        String adminEmail,
                                        String adminFirstName,
                                        String adminLastName,
                                        String adminMobile) {

        int roleCount = CompanyContext.runAs(company.getCompanyId(), () -> seedRoles(plan));
        int settingCount = CompanyContext.runAs(company.getCompanyId(),
                () -> seedSettings(company, plan));

        UserProvisioningService.ProvisionedUser admin = userProvisioningService.provisionAdmin(
                new UserProvisioningService.NewAdminCommand(
                        company.getCompanyId(),
                        company.getCompanyName(),
                        adminEmail,
                        adminFirstName,
                        adminLastName,
                        adminMobile,
                        EnumSet.of(Role.COMPANY_ADMIN)));

        log.info("Provisioned company {}: {} roles, {} settings, admin {}",
                company.getCompanyCode(), roleCount, settingCount, admin.userId());

        return new ProvisioningResult(roleCount, settingCount, admin);
    }

    /**
     * Creates the five default roles. Permissions are filtered against the plan's
     * feature flags, so a role never starts with a right the subscription excludes.
     */
    private int seedRoles(SubscriptionPlan plan) {
        Map<String, Object> featureFlags = plan.getFeatureFlags();
        List<CompanyRole> roles = new ArrayList<>();

        for (DefaultRoleCatalog.RoleDefinition definition : DefaultRoleCatalog.definitions()) {
            roles.add(CompanyRole.builder()
                    .roleCode(definition.code())
                    .roleName(definition.name())
                    .description(definition.description())
                    .roleType(definition.type())
                    .systemRole(true)
                    // Exactly one catalogue entry carries this; the company may move it
                    // later through Role Management.
                    .defaultRole(definition.defaultRole())
                    .status(com.courier.modules.company.domain.RoleStatus.ACTIVE)
                    .build());
        }

        roleRepository.saveAll(roles);

        // Grants are rows now, not an element collection, so they are written after the
        // roles exist and can be audited individually later.
        List<RolePermission> grants = new ArrayList<>();
        for (DefaultRoleCatalog.RoleDefinition definition : DefaultRoleCatalog.definitions()) {
            CompanyRole role = roles.stream()
                    .filter(r -> r.getRoleCode().equals(definition.code()))
                    .findFirst()
                    .orElseThrow();

            Set<String> codes = DefaultRoleCatalog.permissionsFor(definition, featureFlags);
            for (Permission permission : permissionRepository.findAllByPermissionCodeIn(codes)) {
                grants.add(RolePermission.grant(role.getId(), permission));
            }
        }
        rolePermissionRepository.saveAll(grants);

        log.debug("Seeded {} roles with {} permission grants", roles.size(), grants.size());
        return roles.size();
    }

    /**
     * Creates the default settings: the company's own localisation choices, sensible
     * operational defaults, and one read-only row per plan quota and feature.
     *
     * <p>Quota rows carry an empty value when the plan is unlimited, matching the
     * plan's null-means-unlimited convention rather than inventing a sentinel.
     */
    private int seedSettings(Company company, SubscriptionPlan plan) {
        List<CompanySetting> settings = new ArrayList<>();

        addSetting(settings, CompanySettingKeys.TIMEZONE, company.getTimezone(),
                CompanySettingKeys.CATEGORY_LOCALISATION, false, "IANA timezone id");
        addSetting(settings, CompanySettingKeys.CURRENCY, company.getCurrency(),
                CompanySettingKeys.CATEGORY_LOCALISATION, false, "ISO-4217 currency");
        addSetting(settings, CompanySettingKeys.LANGUAGE, company.getLanguage(),
                CompanySettingKeys.CATEGORY_LOCALISATION, false, "UI language");
        addSetting(settings, CompanySettingKeys.DATE_FORMAT, company.getDateFormat(),
                CompanySettingKeys.CATEGORY_LOCALISATION, false, "Display date format");
        addSetting(settings, CompanySettingKeys.TIME_FORMAT, company.getTimeFormat(),
                CompanySettingKeys.CATEGORY_LOCALISATION, false, "Display time format");

        // Operational defaults. The AWB prefix is derived from the company code so
        // tracking numbers are readable from day one and unique per company.
        addSetting(settings, CompanySettingKeys.AWB_PREFIX, awbPrefix(company),
                CompanySettingKeys.CATEGORY_OPERATIONS, false, "Prefix for generated AWB numbers");
        addSetting(settings, CompanySettingKeys.COD_ENABLED, "true",
                CompanySettingKeys.CATEGORY_OPERATIONS, false, "Allow cash-on-delivery bookings");
        addSetting(settings, CompanySettingKeys.AUTO_ASSIGN_DRIVER, "false",
                CompanySettingKeys.CATEGORY_OPERATIONS, false, "Assign a driver automatically on booking");
        addSetting(settings, CompanySettingKeys.DEFAULT_DELIVERY_TYPE, "STANDARD",
                CompanySettingKeys.CATEGORY_OPERATIONS, false, "Delivery type pre-selected at booking");

        addSetting(settings, CompanySettingKeys.NOTIFY_ON_BOOKING, "true",
                CompanySettingKeys.CATEGORY_NOTIFICATION, false, "Notify the consignor on booking");
        addSetting(settings, CompanySettingKeys.NOTIFY_ON_DELIVERY, "true",
                CompanySettingKeys.CATEGORY_NOTIFICATION, false, "Notify the consignor on delivery");
        addSetting(settings, CompanySettingKeys.SUPPORT_EMAIL, company.getEmail(),
                CompanySettingKeys.CATEGORY_NOTIFICATION, false, "Reply-to address on notifications");

        // Plan-derived quotas: read-only in the settings UI. Empty value == unlimited.
        addLimit(settings, CompanySettingKeys.LIMIT_USERS, plan.getMaxUsers());
        addLimit(settings, CompanySettingKeys.LIMIT_BRANCHES, plan.getMaxBranches());
        addLimit(settings, CompanySettingKeys.LIMIT_HUBS, plan.getMaxHubs());
        addLimit(settings, CompanySettingKeys.LIMIT_CUSTOMERS, plan.getMaxCustomers());
        addLimit(settings, CompanySettingKeys.LIMIT_DRIVERS, plan.getMaxDrivers());
        addLimit(settings, CompanySettingKeys.LIMIT_VEHICLES, plan.getMaxVehicles());
        addLimit(settings, CompanySettingKeys.LIMIT_DAILY_BOOKINGS, plan.getMaxDailyBookings());
        addLimit(settings, CompanySettingKeys.LIMIT_MONTHLY_BOOKINGS, plan.getMaxMonthlyBookings());
        addLimit(settings, CompanySettingKeys.LIMIT_STORAGE_GB, plan.getStorageLimitGb());
        addLimit(settings, CompanySettingKeys.LIMIT_API_RATE, plan.getApiRateLimit());

        // One row per feature flag, so a company can see what its plan includes without
        // the UI having to fetch the plan itself.
        if (plan.getFeatureFlags() != null) {
            plan.getFeatureFlags().forEach((flag, value) -> addSetting(settings,
                    CompanySettingKeys.FEATURE_PREFIX + flag,
                    String.valueOf(value),
                    CompanySettingKeys.CATEGORY_FEATURES,
                    true,
                    "Included in the subscription plan"));
        }

        settingRepository.saveAll(settings);
        return settings.size();
    }

    private void addLimit(List<CompanySetting> target, String key, Integer limit) {
        addSetting(target, key,
                limit == null ? "" : String.valueOf(limit),
                CompanySettingKeys.CATEGORY_LIMITS,
                true,
                "From the subscription plan; empty means unlimited");
    }

    private void addSetting(List<CompanySetting> target,
                            String key,
                            String value,
                            String category,
                            boolean planDerived,
                            String description) {
        target.add(CompanySetting.builder()
                .settingKey(key)
                .settingValue(value)
                .category(category)
                .planDerived(planDerived)
                .description(description)
                .build());
    }

    /** First six alphanumeric characters of the company code, uppercased. */
    private String awbPrefix(Company company) {
        String cleaned = company.getCompanyCode().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return cleaned.length() <= 6 ? cleaned : cleaned.substring(0, 6);
    }
}

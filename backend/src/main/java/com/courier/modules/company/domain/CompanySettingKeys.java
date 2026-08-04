package com.courier.modules.company.domain;

/**
 * Keys of the settings seeded with every company.
 *
 * <p>Constants rather than string literals scattered across modules: a typo in a
 * setting key silently reads as "not configured", which is the worst possible failure
 * mode for a limit.
 *
 * <p>The {@code LIMIT_*} keys are copied from the subscription plan at creation time and
 * marked {@code planDerived}, so the settings screen can render them read-only. An empty
 * value means <b>unlimited</b>, matching the plan's null-means-unlimited convention.
 */
public final class CompanySettingKeys {

    // --- localisation
    public static final String TIMEZONE = "company.timezone";
    public static final String CURRENCY = "company.currency";
    public static final String LANGUAGE = "company.language";
    public static final String DATE_FORMAT = "company.dateFormat";
    public static final String TIME_FORMAT = "company.timeFormat";

    // --- operations
    public static final String AWB_PREFIX = "shipment.awbPrefix";
    public static final String COD_ENABLED = "shipment.codEnabled";
    public static final String AUTO_ASSIGN_DRIVER = "shipment.autoAssignDriver";
    public static final String DEFAULT_DELIVERY_TYPE = "shipment.defaultDeliveryType";

    // --- notifications
    public static final String NOTIFY_ON_BOOKING = "notification.onBooking";
    public static final String NOTIFY_ON_DELIVERY = "notification.onDelivery";
    public static final String SUPPORT_EMAIL = "notification.supportEmail";

    // --- plan-derived quotas. Empty value == unlimited.
    public static final String LIMIT_USERS = "limit.maxUsers";
    public static final String LIMIT_BRANCHES = "limit.maxBranches";
    public static final String LIMIT_HUBS = "limit.maxHubs";
    public static final String LIMIT_CUSTOMERS = "limit.maxCustomers";
    public static final String LIMIT_DRIVERS = "limit.maxDrivers";
    public static final String LIMIT_VEHICLES = "limit.maxVehicles";
    public static final String LIMIT_DAILY_BOOKINGS = "limit.maxDailyBookings";
    public static final String LIMIT_MONTHLY_BOOKINGS = "limit.maxMonthlyBookings";
    public static final String LIMIT_STORAGE_GB = "limit.storageGb";
    public static final String LIMIT_API_RATE = "limit.apiRatePerMinute";

    // --- categories
    public static final String CATEGORY_LOCALISATION = "LOCALISATION";
    public static final String CATEGORY_OPERATIONS = "OPERATIONS";
    public static final String CATEGORY_NOTIFICATION = "NOTIFICATION";
    public static final String CATEGORY_LIMITS = "LIMITS";
    public static final String CATEGORY_FEATURES = "FEATURES";

    /** Prefix for one row per subscription feature flag, e.g. {@code feature.bulkBooking}. */
    public static final String FEATURE_PREFIX = "feature.";

    private CompanySettingKeys() {
    }
}

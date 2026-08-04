package com.courier.modules.subscription.domain;

import com.courier.shared.domain.BaseEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A subscription plan: what a company gets, and how much of it.
 *
 * <p><b>Platform-level, not company-owned.</b> This entity extends {@link BaseEntity}
 * rather than {@code CompanyOwnedEntity} — the catalogue is shared by every company and
 * is written only by {@code SUPER_ADMIN}. That is also why its unique constraints are
 * global and not prefixed with {@code company_id}, which is the rule for company-owned
 * tables only (see {@code MEMORY/ARCHITECTURE.md} §4).
 *
 * <p><b>{@code null} means unlimited</b> for every quota field. There is no {@code -1}
 * sentinel: a forgotten guard around a sentinel silently evaluates
 * {@code current < -1} as "over quota" and blocks everything, whereas a forgotten
 * null-check throws immediately and is caught in test. Always compare quotas through
 * {@link #withinLimit(Integer, long)}.
 *
 * <p>The type invariants — a free {@code TRIAL}, an unlimited {@code ENTERPRISE} — are
 * enforced here in {@link #applyTypeInvariants()} rather than in the service, so no
 * caller can persist a plan that contradicts its own tier.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "subscription_plans",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_subscription_plans_code", columnNames = "plan_code"),
                @UniqueConstraint(name = "uk_subscription_plans_name", columnNames = "plan_name")
        },
        indexes = {
                @Index(name = "idx_subscription_plans_active_order",
                        columnList = "is_active, display_order"),
                @Index(name = "idx_subscription_plans_type", columnList = "plan_type")
        })
@SQLRestriction("deleted = false")
public class SubscriptionPlan extends BaseEntity {

    /** Quota value meaning "no ceiling". Spelled out so call sites read clearly. */
    public static final Integer UNLIMITED = null;

    /** Fallback when a request omits the currency. ISO-4217. */
    public static final String DEFAULT_CURRENCY = "INR";

    /**
     * Stable machine key, e.g. {@code STARTER_MONTHLY}. Uppercased on write and
     * immutable after creation: companies and invoices reference it, so re-pointing a
     * code at different terms would rewrite history.
     */
    @Column(name = "plan_code", nullable = false, updatable = false, length = 50)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * {@code @JdbcTypeCode(VARCHAR)} is load-bearing: since Hibernate 6.5 the MySQL
     * dialect maps a {@code STRING} enum to a <em>native</em> {@code enum(...)} column,
     * which would not match the {@code VARCHAR(20)} in {@code V3__subscription.sql} and
     * would fail {@code ddl-auto: validate} at startup. A native enum would also mean a
     * schema change every time a tier is added.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "plan_type", nullable = false, length = 20)
    private PlanType planType;

    /** {@code DECIMAL(19,4)} — money is never a {@code double}. */
    @Column(name = "monthly_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyPrice;

    @Column(name = "yearly_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal yearlyPrice;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = DEFAULT_CURRENCY;

    /** Days of free use granted on signup. Zero for plans without a trial period. */
    @Column(name = "trial_days", nullable = false)
    @Builder.Default
    private Integer trialDays = 0;

    // ------------------------------------------------------------------- quotas
    // Every field below: null == unlimited.

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_branches")
    private Integer maxBranches;

    @Column(name = "max_hubs")
    private Integer maxHubs;

    @Column(name = "max_customers")
    private Integer maxCustomers;

    @Column(name = "max_drivers")
    private Integer maxDrivers;

    @Column(name = "max_vehicles")
    private Integer maxVehicles;

    @Column(name = "max_daily_bookings")
    private Integer maxDailyBookings;

    @Column(name = "max_monthly_bookings")
    private Integer maxMonthlyBookings;

    @Column(name = "storage_limit_gb")
    private Integer storageLimitGb;

    /** Requests per minute, per company. */
    @Column(name = "api_rate_limit")
    private Integer apiRateLimit;

    // -------------------------------------------------------------------- flags

    /**
     * Feature toggles, e.g. {@code {"bulkBooking": true, "podImage": false}}.
     *
     * <p>Deliberately schemaless: features are added far more often than plans are,
     * and a boolean column per feature would mean a migration for each one. Stored in
     * a MySQL {@code JSON} column; Hibernate handles the conversion via
     * {@link SqlTypes#JSON}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_flags", columnDefinition = "JSON")
    @Builder.Default
    private Map<String, Object> featureFlags = new LinkedHashMap<>();

    /**
     * Whether the plan may be assigned to a company. Deactivating grandfathers existing
     * subscribers rather than cancelling them — it only removes the plan from the
     * catalogue offered to new companies.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Ascending sort key for pricing pages. Ties break on {@code planCode}. */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    // ---------------------------------------------------------------- behaviour

    public static String normaliseCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    public static String normaliseCurrency(String currency) {
        return currency == null || currency.isBlank()
                ? DEFAULT_CURRENCY
                : currency.trim().toUpperCase();
    }

    /**
     * Quota check every consumer must use.
     *
     * @param limit   the plan's ceiling, or {@code null} for unlimited
     * @param current how many the company already has
     * @return true when one more is allowed
     */
    public static boolean withinLimit(Integer limit, long current) {
        return limit == null || current < limit;
    }

    public boolean isUnlimited() {
        return planType.hasUnlimitedQuotas();
    }

    public boolean isFeatureEnabled(String flag) {
        return featureFlags != null && Boolean.TRUE.equals(featureFlags.get(flag));
    }

    /**
     * Normalises the row and enforces the invariants that follow from {@link #planType}.
     * Called on every create and update, before persisting.
     *
     * <p>{@code ENTERPRISE} quotas are <em>nulled</em> rather than rejected: an operator
     * filling in a number for an unlimited tier is expressing intent that the tier
     * overrides, and failing the request would only teach them to type zeroes.
     *
     * @throws BusinessRuleException when a rule cannot be resolved by normalisation
     */
    public void applyTypeInvariants() {
        this.planCode = normaliseCode(planCode);
        this.currency = normaliseCurrency(currency);
        this.planName = planName == null ? null : planName.trim();
        if (this.featureFlags == null) {
            this.featureFlags = new LinkedHashMap<>();
        }
        if (this.trialDays == null) {
            this.trialDays = 0;
        }
        if (this.displayOrder == null) {
            this.displayOrder = 0;
        }

        requireNonNegative(monthlyPrice, "Monthly price");
        requireNonNegative(yearlyPrice, "Yearly price");

        if (planType.requiresZeroPrice() && isChargeable()) {
            throw new BusinessRuleException(
                    "A TRIAL plan cannot be priced. Set monthlyPrice and yearlyPrice to 0.");
        }

        if (planType.requiresZeroPrice() && trialDays <= 0) {
            throw new BusinessRuleException("A TRIAL plan must grant at least one trial day.");
        }

        if (planType.hasUnlimitedQuotas()) {
            clearQuotas();
        }
    }

    /** True when either price is above zero. Scale-insensitive: {@code 0.0000} is free. */
    public boolean isChargeable() {
        return isPositive(monthlyPrice) || isPositive(yearlyPrice);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    private void clearQuotas() {
        this.maxUsers = UNLIMITED;
        this.maxBranches = UNLIMITED;
        this.maxHubs = UNLIMITED;
        this.maxCustomers = UNLIMITED;
        this.maxDrivers = UNLIMITED;
        this.maxVehicles = UNLIMITED;
        this.maxDailyBookings = UNLIMITED;
        this.maxMonthlyBookings = UNLIMITED;
        this.storageLimitGb = UNLIMITED;
        this.apiRateLimit = UNLIMITED;
    }

    private static void requireNonNegative(BigDecimal price, String label) {
        if (price == null) {
            throw new BusinessRuleException(label + " is required.");
        }
        if (price.signum() < 0) {
            throw new BusinessRuleException(label + " cannot be negative.");
        }
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}

package com.courier.modules.company.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * One configuration value for one company.
 *
 * <p>Key/value rather than a wide {@code company_settings} table with a column per
 * setting: settings are added constantly and by different modules, and a column per
 * setting means a migration each time. The category groups them for the settings UI.
 *
 * <p>Values are stored as strings and parsed by the reader. Anything structured enough
 * to need a schema belongs on the company row itself, not here.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "company_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_company_settings_company_key", columnNames = {"company_id", "setting_key"}),
        indexes = @Index(name = "idx_company_settings_company_category",
                columnList = "company_id, category"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class CompanySetting extends CompanyOwnedEntity {

    @Column(name = "setting_key", nullable = false, updatable = false, length = 100)
    private String settingKey;

    @Column(name = "setting_value", length = 500)
    private String settingValue;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /**
     * Seeded from the subscription plan rather than chosen by the company. Quotas are
     * the obvious case: a company must not raise its own user limit.
     */
    @Column(name = "plan_derived", nullable = false)
    @Builder.Default
    private boolean planDerived = false;

    @Column(name = "description", length = 255)
    private String description;
}

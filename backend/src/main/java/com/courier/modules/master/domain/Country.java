package com.courier.modules.master.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Root of the geography hierarchy: country -> state -> district -> city -> area -> pincode.
 *
 * <p>Company-owned like every other master list. A courier that only ships domestically will
 * hold one row here; one with a cross-border lane holds several. Keeping it per company
 * rather than platform-wide means a company can name and code its countries the way its
 * own paperwork does, and no company can edit another's.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "master_countries",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_master_countries_code",
                        columnNames = {"company_id", "code"}),
                @UniqueConstraint(name = "uk_master_countries_name",
                        columnNames = {"company_id", "name"})
        },
        indexes = @Index(name = "idx_master_countries_status", columnList = "company_id, status"))
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class Country extends MasterDataEntity {

    /** ISO 3166-1 alpha-2, e.g. {@code IN}. */
    @Column(name = "iso_code2", length = 2)
    private String isoCode2;

    /** ISO 3166-1 alpha-3, e.g. {@code IND}. */
    @Column(name = "iso_code3", length = 3)
    private String isoCode3;

    /** International dialling prefix, e.g. {@code +91}. */
    @Column(name = "dial_code", length = 8)
    private String dialCode;

    /** ISO 4217, e.g. {@code INR}. */
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Override
    protected void applySpecificInvariants() {
        this.isoCode2 = upperOrNull(isoCode2);
        this.isoCode3 = upperOrNull(isoCode3);
        this.currencyCode = upperOrNull(currencyCode);
        this.dialCode = blankToNull(dialCode);

        requireLength(isoCode2, 2, "ISO alpha-2 code");
        requireLength(isoCode3, 3, "ISO alpha-3 code");
        requireLength(currencyCode, 3, "Currency code");
    }

    private static void requireLength(String value, int length, String label) {
        if (value != null && value.length() != length) {
            throw new BusinessRuleException(
                    "%s must be exactly %d characters.".formatted(label, length));
        }
    }
}

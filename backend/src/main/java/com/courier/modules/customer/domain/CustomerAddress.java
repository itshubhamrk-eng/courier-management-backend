package com.courier.modules.customer.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One of a customer's addresses — pickup, delivery, or both. Company-owned in its own
 * right (not read through a JPA {@code @OneToMany}, matching how the master data
 * hierarchy relates parent and child by a plain {@code *_id} column rather than an
 * object graph — see {@code MasterDataEntity}).
 *
 * <p>{@code customerId} carries a real foreign key to {@code customers.id}: both tables
 * are written by this same module from day one, so there is no "orphan rows already in
 * the dev database" reason to defer it the way {@code branches.manager_id} was.
 * {@code countryId}/{@code stateId}/{@code districtId}/{@code cityId}/{@code areaId}/
 * {@code pincodeId} reference the global geography masters in a different module and are
 * validated in the service layer instead, the same treatment {@code branches.manager_id}
 * gets for a cross-module reference.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "customer_addresses",
        indexes = {
                @Index(name = "idx_cust_addr_company_customer", columnList = "company_id, customer_id"),
                @Index(name = "idx_cust_addr_company_status", columnList = "company_id, status"),
                @Index(name = "idx_cust_addr_pincode", columnList = "company_id, pincode_id")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class CustomerAddress extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "customer_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    @Builder.Default
    private AddressType addressType = AddressType.HOME;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "country_id", columnDefinition = "BINARY(16)")
    private UUID countryId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "state_id", columnDefinition = "BINARY(16)")
    private UUID stateId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "district_id", columnDefinition = "BINARY(16)")
    private UUID districtId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "city_id", columnDefinition = "BINARY(16)")
    private UUID cityId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "area_id", columnDefinition = "BINARY(16)")
    private UUID areaId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "pincode_id", columnDefinition = "BINARY(16)")
    private UUID pincodeId;

    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "landmark", length = 150)
    private String landmark;

    /** WGS84. {@code DECIMAL(9,6)} — ~0.1 m precision, enough for a rooftop. */
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "is_default_pickup", nullable = false)
    @Builder.Default
    private boolean defaultPickup = false;

    @Column(name = "is_default_delivery", nullable = false)
    @Builder.Default
    private boolean defaultDelivery = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    // ---------------------------------------------------------------- behaviour

    public boolean isActive() {
        return status == CustomerStatus.ACTIVE;
    }

    public void applyInvariants() {
        this.addressLine1 = trim(addressLine1);
        this.addressLine2 = trim(addressLine2);
        this.landmark = trim(landmark);

        if (addressType == null) {
            throw new BusinessRuleException("An address must have a type.");
        }
        if (status == null) {
            this.status = CustomerStatus.ACTIVE;
        }
        requireInRange(latitude, "Latitude", -90, 90);
        requireInRange(longitude, "Longitude", -180, 180);
    }

    /**
     * The key two addresses are compared on to reject a duplicate: same free-text lines
     * and the same pincode, case- and whitespace-insensitively. Deliberately not the
     * full set of geography ids — two addresses that differ only in a supplied city name
     * spelling but share pincode and lines are the same physical place.
     */
    public String duplicateKey() {
        return normalise(addressLine1) + '|' + normalise(addressLine2) + '|'
                + (pincodeId == null ? "" : pincodeId.toString());
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static void requireInRange(BigDecimal value, String label, int min, int max) {
        if (value == null) {
            return;
        }
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new BusinessRuleException(
                    "%s must be between %d and %d.".formatted(label, min, max));
        }
    }
}

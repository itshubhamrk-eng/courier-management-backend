package com.courier.modules.shipment.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 * The Pricing Engine's own charge breakup ({@code com.courier.modules.pricing.application
 * .PricingResult}), persisted verbatim at booking time — one row per shipment, so a later
 * rate or configuration change never reprices history. Re-booking (an edit while the
 * shipment is still {@code BOOKED}) replaces this row's figures; it never appends a second.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "shipment_charges",
        uniqueConstraints = @UniqueConstraint(name = "uk_shipment_charges_shipment",
                columnNames = {"company_id", "shipment_id"}))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class ShipmentCharge extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    @Column(name = "freight", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal freight = BigDecimal.ZERO;

    @Column(name = "fuel_charge", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal fuelCharge = BigDecimal.ZERO;

    @Column(name = "handling_charge", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal handlingCharge = BigDecimal.ZERO;

    @Column(name = "oda_charge", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal odaCharge = BigDecimal.ZERO;

    @Column(name = "insurance_charge", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal insuranceCharge = BigDecimal.ZERO;

    @Column(name = "gst_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal gstAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "round_off", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal roundOff = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal netAmount = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "matched_route_id", columnDefinition = "BINARY(16)")
    private UUID matchedRouteId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "matched_rate_id", columnDefinition = "BINARY(16)")
    private UUID matchedRateId;
}

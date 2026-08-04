package com.courier.modules.shipment.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
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
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The aggregate root of Shipment Booking: one row per booked shipment.
 *
 * <p>Sender/receiver are plain text on this row — name, address and contact number typed
 * at booking time, not a reference into the Customer module (no {@code Customer}/{@code
 * CustomerAddress} lookup at all here; the LR is printed from whatever the booking desk
 * typed). {@code pickupPincode}/{@code deliveryPincode} are typed alongside them and feed
 * the Pricing Engine directly — there is no address record left to resolve a pincode id
 * from.
 *
 * <p>{@code actualWeight}/{@code volumetricWeight}/{@code chargeableWeight} are computed
 * once at booking time from the item grid, using the Pricing Engine's own reusable
 * {@link com.courier.modules.pricing.domain.WeightCalculator}/{@link
 * com.courier.modules.pricing.domain.VolumetricCalculator}/{@link
 * com.courier.modules.pricing.domain.ChargeableWeightCalculator} — this module never
 * reimplements that arithmetic, only calls it once per item and sums the result. See
 * {@code ShipmentServiceImpl} and {@code MEMORY/modules/shipment-booking.md}.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "shipments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_shipments_company_number",
                        columnNames = {"company_id", "shipment_number"}),
                @UniqueConstraint(name = "uk_shipments_company_tracking",
                        columnNames = {"company_id", "tracking_number"})
        },
        indexes = {
                @Index(name = "idx_shipments_tracking", columnList = "tracking_number"),
                @Index(name = "idx_shipments_status", columnList = "company_id, status, created_at"),
                @Index(name = "idx_shipments_booking_branch",
                        columnList = "company_id, booking_branch_id, status"),
                @Index(name = "idx_shipments_delivery_branch",
                        columnList = "company_id, delivery_branch_id, status")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class Shipment extends CompanyOwnedEntity {

    @Column(name = "shipment_number", nullable = false, updatable = false, length = 30)
    private String shipmentNumber;

    @Column(name = "tracking_number", nullable = false, updatable = false, length = 30)
    private String trackingNumber;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "booking_branch_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID bookingBranchId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "delivery_branch_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID deliveryBranchId;

    /**
     * The manifest this shipment is currently scanned onto — set by Shipment Movement's
     * manifest creation, cleared by nothing (a delivered/returned/cancelled shipment
     * keeps its last manifest for history). No physical FK: manifest is a different
     * module, validated in {@code ShipmentMovementServiceImpl}.
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "manifest_id", columnDefinition = "BINARY(16)")
    private UUID manifestId;

    @Column(name = "pickup_pincode", nullable = false, length = 10)
    private String pickupPincode;

    @Column(name = "delivery_pincode", nullable = false, length = 10)
    private String deliveryPincode;

    @Column(name = "sender_name", nullable = false, length = 150)
    private String senderName;

    @Column(name = "sender_address", nullable = false, length = 500)
    private String senderAddress;

    @Column(name = "sender_contact", nullable = false, length = 20)
    private String senderContact;

    @Column(name = "receiver_name", nullable = false, length = 150)
    private String receiverName;

    @Column(name = "receiver_address", nullable = false, length = 500)
    private String receiverAddress;

    @Column(name = "receiver_contact", nullable = false, length = 20)
    private String receiverContact;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "service_type_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID serviceTypeId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "package_type_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID packageTypeId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "payment_mode_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID paymentModeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_type", nullable = false, length = 20)
    @Builder.Default
    private ShipmentType shipmentType = ShipmentType.NON_DOCUMENT;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "actual_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal actualWeight;

    @Column(name = "volumetric_weight", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal volumetricWeight = BigDecimal.ZERO;

    @Column(name = "chargeable_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal chargeableWeight;

    @Column(name = "declared_value", precision = 19, scale = 4)
    private BigDecimal declaredValue;

    @Column(name = "number_of_packages", nullable = false)
    @Builder.Default
    private Integer numberOfPackages = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ShipmentStatus status = ShipmentStatus.BOOKED;

    @Column(name = "remarks", length = 500)
    private String remarks;

    // ---------------------------------------------------------------- behaviour

    public boolean isEditable() {
        return status == ShipmentStatus.BOOKED;
    }

    /**
     * @throws BusinessRuleException the requested transition is not legal from the current
     *         status — {@code ShipmentStatus.canTransitionTo} is the single source of truth
     */
    public void transitionTo(ShipmentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessRuleException(
                    "Shipment %s cannot move from %s to %s.".formatted(shipmentNumber, status, next));
        }
        this.status = next;
    }

    public void applyInvariants() {
        this.remarks = blankToNull(remarks);
        if (bookingBranchId == null || deliveryBranchId == null) {
            throw new BusinessRuleException(
                    "A shipment needs both a booking branch and a delivery branch.");
        }
        this.senderName = blankToNull(senderName);
        this.senderAddress = blankToNull(senderAddress);
        this.senderContact = blankToNull(senderContact);
        this.receiverName = blankToNull(receiverName);
        this.receiverAddress = blankToNull(receiverAddress);
        this.receiverContact = blankToNull(receiverContact);
        if (senderName == null || senderAddress == null || senderContact == null) {
            throw new BusinessRuleException("A shipment needs sender name, address and contact number.");
        }
        if (receiverName == null || receiverAddress == null || receiverContact == null) {
            throw new BusinessRuleException("A shipment needs receiver name, address and contact number.");
        }
        if (pickupPincode == null || pickupPincode.isBlank() || deliveryPincode == null || deliveryPincode.isBlank()) {
            throw new BusinessRuleException("A shipment needs both a pickup and a delivery pincode.");
        }
        if (actualWeight == null || actualWeight.signum() <= 0) {
            throw new BusinessRuleException("Actual weight must be greater than zero.");
        }
        if (numberOfPackages == null || numberOfPackages < 1) {
            this.numberOfPackages = 1;
        }
        if (declaredValue != null && declaredValue.signum() < 0) {
            throw new BusinessRuleException("Declared value cannot be negative.");
        }
        if (shipmentType == null) {
            this.shipmentType = ShipmentType.NON_DOCUMENT;
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

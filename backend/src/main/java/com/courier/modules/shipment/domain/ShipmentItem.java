package com.courier.modules.shipment.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One packed item of a shipment. Company-owned in its own right and queried by
 * {@code shipmentId}, not a JPA {@code @OneToMany} — the same treatment
 * {@code customer.domain.CustomerAddress} gives its parent.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "shipment_items",
        indexes = @Index(name = "idx_shipment_items_shipment", columnList = "company_id, shipment_id"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class ShipmentItem extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    @Column(name = "item_name", nullable = false, length = 150)
    private String itemName;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal weight;

    @Column(name = "length_cm", precision = 10, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 10, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 10, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "declared_value", precision = 19, scale = 4)
    private BigDecimal declaredValue;

    @Column(name = "fragile", nullable = false)
    @Builder.Default
    private boolean fragile = false;

    @Column(name = "dangerous_goods", nullable = false)
    @Builder.Default
    private boolean dangerousGoods = false;

    public void applyInvariants() {
        this.itemName = itemName == null ? null : itemName.trim();
        if (itemName == null || itemName.isBlank()) {
            throw new BusinessRuleException("An item needs a name.");
        }
        if (quantity == null || quantity < 1) {
            this.quantity = 1;
        }
        if (weight == null || weight.signum() <= 0) {
            throw new BusinessRuleException("Item '%s' must have a weight greater than zero."
                    .formatted(itemName));
        }
        requirePositiveIfPresent(lengthCm, "Length");
        requirePositiveIfPresent(widthCm, "Width");
        requirePositiveIfPresent(heightCm, "Height");
        if (declaredValue != null && declaredValue.signum() < 0) {
            throw new BusinessRuleException("Item '%s' declared value cannot be negative."
                    .formatted(itemName));
        }
    }

    private static void requirePositiveIfPresent(BigDecimal value, String label) {
        if (value != null && value.signum() <= 0) {
            throw new BusinessRuleException("%s must be greater than zero.".formatted(label));
        }
    }
}

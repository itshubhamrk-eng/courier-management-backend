package com.courier.modules.ewaybill.domain;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One E-Way Bill raised against a shipment.
 *
 * <p>Business rule this module exists for: a shipment whose invoice value exceeds the
 * company's own configurable {@code CompanySettings.ewayBillMandatoryValue} (default
 * 50000.00) may not have its AWB generated until it carries a {@code VALIDATED} row here.
 * At or under the threshold, an E-Way Bill is optional — a shipment may still carry one
 * (any status), or none at all. See {@code ShipmentServiceImpl.create}/{@code update} for
 * where the gate is enforced, and {@code EwayBillProvider} for how {@code VALIDATED} is
 * reached.
 *
 * <p>No unique {@code (company_id, shipment_id)} constraint: a shipment may carry more
 * than one row over its life (a {@code CANCELLED} one re-issued) — the application layer
 * takes the newest non-deleted row as current, the same "newest row wins" precedent
 * {@code ShipmentAsset} (V33) already set.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "eway_bill",
        uniqueConstraints = @UniqueConstraint(name = "uk_eway_bill_company_number",
                columnNames = {"company_id", "eway_bill_number"}),
        indexes = @Index(name = "idx_eway_bill_shipment",
                columnList = "company_id, shipment_id, status, created_at"))
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class EwayBill extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    @Column(name = "eway_bill_number", length = 30)
    private String ewayBillNumber;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "invoice_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal invoiceValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    @Builder.Default
    private EwayBillDocumentType documentType = EwayBillDocumentType.INVOICE;

    @Column(name = "document_number", length = 50)
    private String documentNumber;

    @Column(name = "document_date")
    private LocalDate documentDate;

    /** Free text, not a physical FK — no Transporter/Vendor entity exists yet in this
     *  codebase (see the migration comment). */
    @Column(name = "transporter_id", length = 50)
    private String transporterId;

    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @Column(name = "distance")
    private Integer distance;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EwayBillStatus status = EwayBillStatus.PENDING;

    /** No file-storage backend beyond what Shipment Booking already wired — reused as-is.
     *  Source of truth, same honesty note {@code ShipmentDocument} carries. */
    @Column(name = "document_url", length = 1000)
    private String documentUrl;

    @Column(name = "remarks", length = 500)
    private String remarks;

    // ---------------------------------------------------------------- behaviour

    public boolean isValidNow() {
        return status == EwayBillStatus.VALIDATED
                && (validUntil == null || validUntil.isAfter(Instant.now()));
    }

    public void transitionTo(EwayBillStatus next) {
        status.requireCanTransitionTo(next);
        this.status = next;
    }

    public void applyInvariants() {
        this.ewayBillNumber = blankToNull(ewayBillNumber);
        this.invoiceNumber = blankToNull(invoiceNumber);
        this.documentNumber = blankToNull(documentNumber);
        this.transporterId = blankToNull(transporterId);
        this.vehicleNumber = blankToNull(vehicleNumber);
        this.remarks = blankToNull(remarks);
        if (invoiceNumber == null) {
            throw new BusinessRuleException("An E-Way Bill needs an invoice number.");
        }
        if (invoiceDate == null) {
            throw new BusinessRuleException("An E-Way Bill needs an invoice date.");
        }
        if (invoiceValue == null || invoiceValue.signum() <= 0) {
            throw new BusinessRuleException("Invoice value must be greater than zero.");
        }
        if (documentType == null) {
            this.documentType = EwayBillDocumentType.INVOICE;
        }
        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            throw new BusinessRuleException("E-Way Bill validity cannot end before it starts.");
        }
        if (status == null) {
            this.status = EwayBillStatus.PENDING;
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

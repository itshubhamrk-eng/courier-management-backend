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
 * One E-Way Bill raised against a shipment — auto-generated in two stages through
 * {@code EwayBillProvider}: Part-A at booking time (invoice/consignor/consignee), Part-B
 * at Manifest dispatch time (vehicle/transport), once the invoice value exceeds the
 * company's own configurable {@code CompanySettings.ewayBillMandatoryValue} (default
 * 50000.00). See {@link EwayBillStatus} for the full lifecycle.
 *
 * <p>Consignor/consignee/product fields are a snapshot taken at Part-A generation time,
 * not a live join to the shipment — an E-Way Bill is a legal document as issued, and a
 * later edit to the shipment's sender/receiver details must not silently reshape a
 * document already filed with the government. It also means a {@code retry} can rebuild
 * the exact same provider request from this row alone, without depending back on
 * {@code modules.shipment} (which already depends on this module — a reverse dependency
 * would be circular).
 *
 * <p>No unique {@code (company_id, shipment_id)} constraint: a shipment may carry more
 * than one row over its life (a {@code CANCELLED} one re-issued), so the application
 * layer takes the newest non-deleted, non-cancelled row as current — the same
 * "newest row wins" precedent {@code ShipmentAsset} (V33) already set. Idempotency for
 * the auto-generation path itself is enforced in {@code EwayBillServiceImpl} by
 * (shipment, invoice number), not by a DB constraint.
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

    // ------------------------------------------------------------- Part-A snapshot

    @Column(name = "consignor_name", length = 150)
    private String consignorName;

    @Column(name = "consignor_address", length = 500)
    private String consignorAddress;

    @Column(name = "consignor_pincode", length = 10)
    private String consignorPincode;

    @Column(name = "consignor_gstin", length = 15)
    private String consignorGstin;

    @Column(name = "consignee_name", length = 150)
    private String consigneeName;

    @Column(name = "consignee_address", length = 500)
    private String consigneeAddress;

    @Column(name = "consignee_pincode", length = 10)
    private String consigneePincode;

    @Column(name = "consignee_gstin", length = 15)
    private String consigneeGstin;

    @Column(name = "product_description", length = 500)
    private String productDescription;

    // ------------------------------------------------------------- Part-B / transport

    /** Free text, not a physical FK — no Transporter/Vendor entity exists yet in this
     *  codebase (see the migration comment). */
    @Column(name = "transporter_id", length = 50)
    private String transporterId;

    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @Column(name = "transport_mode", length = 20)
    private String transportMode;

    @Column(name = "distance")
    private Integer distance;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    // ------------------------------------------------------------- provider bookkeeping

    /** Which {@code EwayBillProvider} implementation produced {@link #ewayBillNumber} —
     *  informational only, never used to route a call. */
    @Column(name = "provider_name", length = 30)
    private String providerName;

    /** The provider's own correlation id for this E-Way Bill, distinct from
     *  {@link #ewayBillNumber} (the government-issued number itself) — some GSPs hand
     *  back an internal reference alongside the number. Never a credential. */
    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "part_a_generated_at")
    private Instant partAGeneratedAt;

    @Column(name = "part_b_generated_at")
    private Instant partBGeneratedAt;

    /** The provider's own failure reason, sanitized — never a stack trace, never a
     *  credential/token. Cleared on the next successful call. */
    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EwayBillStatus status = EwayBillStatus.PART_A_PENDING;

    /** No file-storage backend beyond what Shipment Booking already wired — reused as-is.
     *  Source of truth, same honesty note {@code ShipmentDocument} carries. */
    @Column(name = "document_url", length = 1000)
    private String documentUrl;

    @Column(name = "remarks", length = 500)
    private String remarks;

    // ---------------------------------------------------------------- behaviour

    public boolean isValidNow() {
        return status == EwayBillStatus.GENERATED
                && (validUntil == null || validUntil.isAfter(Instant.now()));
    }

    /** Part-A has already succeeded once a provider-issued number is on the row —
     *  independent of the current status, so a {@code FAILED} row that failed at Part-B
     *  is still distinguishable from one that never got past Part-A. */
    public boolean partAGenerated() {
        return ewayBillNumber != null && !ewayBillNumber.isBlank();
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
        this.transportMode = blankToNull(transportMode);
        this.consignorGstin = blankToNull(consignorGstin);
        this.consigneeGstin = blankToNull(consigneeGstin);
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
            this.status = EwayBillStatus.PART_A_PENDING;
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

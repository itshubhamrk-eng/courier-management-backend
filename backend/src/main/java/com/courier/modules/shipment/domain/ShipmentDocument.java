package com.courier.modules.shipment.domain;

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

import java.util.UUID;

/**
 * A reference to one uploaded document of a shipment (Invoice, E-Way Bill, Packing List,
 * LR Copy, POD).
 *
 * <p>There is no file-storage backend in this project yet, so {@code documentUrl} is the
 * source of truth and the caller supplies it — the same honesty note
 * {@code features/company}'s {@code CompanyLogo} component already carries for the
 * company logo. A real upload endpoint (multipart, object storage) is future work, not
 * invented here.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "shipment_documents",
        indexes = @Index(name = "idx_shipment_documents_shipment",
                columnList = "company_id, shipment_id, document_type"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class ShipmentDocument extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private ShipmentDocumentType documentType;

    @Column(name = "document_name", nullable = false, length = 255)
    private String documentName;

    @Column(name = "document_url", nullable = false, length = 1000)
    private String documentUrl;

    @Column(name = "remarks", length = 500)
    private String remarks;

    public void applyInvariants() {
        this.documentName = documentName == null ? null : documentName.trim();
        this.documentUrl = documentUrl == null ? null : documentUrl.trim();
        this.remarks = blankToNull(remarks);
        if (documentType == null) {
            throw new BusinessRuleException("A document needs a type.");
        }
        if (documentName == null || documentName.isBlank()) {
            throw new BusinessRuleException("A document needs a name.");
        }
        if (documentUrl == null || documentUrl.isBlank()) {
            throw new BusinessRuleException("A document needs a URL.");
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

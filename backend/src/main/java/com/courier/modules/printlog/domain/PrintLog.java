package com.courier.modules.printlog.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;
import java.util.UUID;

/** One print (or reprint) of a DRS, Loading Sheet or THC: who, when. The print count of a
 *  document is simply the number of rows for its {@code docType + docRef}. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "document_print_log")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class PrintLog extends CompanyOwnedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, updatable = false, length = 20)
    private PrintDocType docType;

    @Column(name = "doc_ref", nullable = false, updatable = false, length = 64)
    private String docRef;

    @Column(name = "doc_number", updatable = false, length = 50)
    private String docNumber;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "printed_by", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID printedBy;

    @Column(name = "printed_by_name", updatable = false, length = 200)
    private String printedByName;

    @Column(name = "printed_at", nullable = false, updatable = false)
    private Instant printedAt;
}

package com.courier.modules.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * One row per company — the counter behind ticket numbers ({@code "TKT-" + 6-digit
 * serial}). Same shape and same reason as {@code CompanyDrsSequence}: never read or
 * written through JPA directly, only through {@link CompanyTicketSequenceRepository}'s
 * native upsert.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "company_ticket_sequences")
public class CompanyTicketSequence {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "company_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID companyId;

    @Column(name = "sequence_value", nullable = false)
    private long sequenceValue;
}

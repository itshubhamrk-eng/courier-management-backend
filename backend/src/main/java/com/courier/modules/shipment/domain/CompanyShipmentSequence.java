package com.courier.modules.shipment.domain;

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
 * One row per company — the counter behind its tracking numbers ({@code
 * "<YYMM><7-digit serial>"}). Same shape and same reason as {@link BranchShipmentSequence},
 * just keyed by company instead of branch: never read or written through JPA directly,
 * only through {@link CompanyShipmentSequenceRepository}'s native upsert.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "company_shipment_sequences")
public class CompanyShipmentSequence {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "company_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID companyId;

    @Column(name = "sequence_value", nullable = false)
    private long sequenceValue;
}
